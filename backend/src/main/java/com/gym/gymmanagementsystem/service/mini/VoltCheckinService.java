package com.gym.gymmanagementsystem.service.mini;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltBookingMapper;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.IdempotencyService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进场信息、扫码核销与自由训练记录。
 * 预约锁位/确认不扣次；只有这里核销成功才扣 1 次，并写 credit_ledger 负数流水。
 */
@Service
public class VoltCheckinService {

    private static final String SCAN_ENDPOINT = "POST /checkins/scan";
    private static final String FREE_ENDPOINT = "POST /checkins/free";

    private final VoltMiniSupport support;
    private final JdbcTemplate jdbcTemplate;
    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final VoltBookingMapper bookingMapper;
    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;
    private final IdempotencyService idempotencyService;

    public VoltCheckinService(VoltMiniSupport support,
                              JdbcTemplate jdbcTemplate,
                              VoltCatalogMapper catalogMapper,
                              VoltSessionMapper sessionMapper,
                              VoltBookingMapper bookingMapper,
                              VoltMemberMapper memberMapper,
                              VoltViewService viewService,
                              IdempotencyService idempotencyService) {
        this.support = support;
        this.jdbcTemplate = jdbcTemplate;
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.bookingMapper = bookingMapper;
        this.memberMapper = memberMapper;
        this.viewService = viewService;
        this.idempotencyService = idempotencyService;
    }

    public Map<String, Object> checkinInfo(VoltMember member) {
        support.expireLocks(member.getMemberId());
        LocalDate today = LocalDate.now();
        Map<String, Object> todayRecord = jdbcTemplate.query(
                "SELECT " + VoltMiniSupport.RECORD_COLUMNS + " FROM volt_record WHERE member_id = ? AND record_date = ? "
                        + "ORDER BY checked_at DESC LIMIT 1",
                (rs, rowNum) -> support.recordRow(rs), member.getMemberId(), java.sql.Date.valueOf(today))
                .stream().findFirst().orElse(null);
        List<Map<String, Object>> recent = support.records(member.getMemberId());
        if (recent.size() > 5) {
            recent = new ArrayList<>(recent.subList(0, 5));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("member", viewService.memberView(member));
        resp.put("todayBooking", support.todayBooking(support.activeBookings(member)));
        resp.put("todayRecord", todayRecord);
        resp.put("stats", support.stats(member.getMemberId()));
        resp.put("recentRecords", recent);
        resp.put("venues", support.venueViews(sessionMapper.findBetween(today, today)));
        resp.put("brand", support.brand());
        return resp;
    }

    @Transactional
    public Map<String, Object> scanCheckin(VoltMember member, Map<String, Object> input, String idempotencyKey) {
        String venueCode = support.text(input == null ? null : input.get("venueCode"));
        Integer distanceMeters = support.integer(input == null ? null : input.get("distanceMeters"));
        Venue venue = venueCode == null ? null : catalogMapper.findVenue(venueCode);
        if (venue == null) {
            throw new ApiException(400, "BAD_VENUE", "不是有效的 VOLT 场馆码。");
        }
        String scope = "scan|" + member.getMemberId() + "|" + venueCode;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, SCAN_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        support.expireLocks(member.getMemberId());
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Booking> todayBookings = support.todayConfirmedBookings(member, today);
        // 同一天同馆可能有多个 confirmed 预约，扫码时选离当前时间最近的一场，
        // 避免总核销到最早（甚至已经结束）的课程。
        Booking booking = null;
        long nearestMinutes = Long.MAX_VALUE;
        for (Booking item : todayBookings) {
            VoltSession session = sessionMapper.findById(item.getSessionId());
            if (session == null || !venueCode.equals(session.getVenueCode())) {
                continue;
            }
            LocalDateTime start = session.getSessionDate().atTime(session.getStartTime());
            long distance = Math.abs(java.time.Duration.between(now, start).toMinutes());
            if (distance < nearestMinutes) {
                nearestMinutes = distance;
                booking = item;
            }
        }
        if (booking == null) {
            // 没有待核销预约：今天该馆已经上过课则幂等返回，否则提示自由训练/换馆。
            Map<String, Object> already = findClassRecord(member.getMemberId(), today, venueCode, null);
            if (already != null) {
                Map<String, Object> resp = support.checkinResponse("already", venue, null, already, member, distanceMeters);
                idempotencyService.save(scope, idempotencyKey, SCAN_ENDPOINT, 200, resp);
                return resp;
            }
            Map<String, Object> resp = !todayBookings.isEmpty()
                    ? support.checkinResponse("wrong-venue", venue, viewService.bookingView(todayBookings.get(0)),
                            null, member, distanceMeters)
                    : support.checkinResponse("free-ready", venue, null, null, member, distanceMeters);
            idempotencyService.save(scope, idempotencyKey, SCAN_ENDPOINT, 200, resp);
            return resp;
        }

        // 同一预约重复扫码幂等；按 booking_id 判断，避免同一天多节课被“该馆已签到”误挡。
        Map<String, Object> already = findClassRecord(member.getMemberId(), today, venueCode, booking.getBookingId());
        if (already != null) {
            Map<String, Object> resp = support.checkinResponse("already", venue,
                    viewService.bookingView(booking), already, member, distanceMeters);
            idempotencyService.save(scope, idempotencyKey, SCAN_ENDPOINT, 200, resp);
            return resp;
        }

        // 条件核销：同一预约并发扫码时只有一条能把 confirmed 改成 checked-in。
        if (bookingMapper.markCheckedInIfConfirmed(booking.getBookingId()) == 0) {
            Map<String, Object> current = findClassRecord(member.getMemberId(), today, venueCode, booking.getBookingId());
            Map<String, Object> resp = support.checkinResponse("already", venue,
                    viewService.bookingView(bookingMapper.findById(booking.getBookingId())),
                    current, member, distanceMeters);
            idempotencyService.save(scope, idempotencyKey, SCAN_ENDPOINT, 200, resp);
            return resp;
        }
        // 扣次用条件更新，余额不足时整个事务回滚，核销状态一并撤销。
        int deducted = jdbcTemplate.update(
                "UPDATE volt_member SET credits = credits - 1 WHERE member_id = ? AND credits > 0",
                member.getMemberId());
        if (deducted == 0) {
            throw ApiException.conflict("NO_CREDITS", "充电次数不足，本次无法核销。");
        }

        VoltSession session = sessionMapper.findById(booking.getSessionId());
        Map<String, Object> record = support.createClassRecord(member, session, booking, venue);
        VoltMember fresh = memberMapper.findById(member.getMemberId());
        insertLedger(fresh, booking.getBookingId(), String.valueOf(record.get("id")), -1, "CHECKIN",
                "进场核销 · " + support.programName(session.getSessionId()));
        support.pushMessage(member.getMemberId(), "checkin", "进场成功 · " + support.programName(session.getSessionId()),
                "已核销 1 次充电，" + support.locationLabel(distanceMeters) + "。本次消耗 " + record.get("kcal") + " kcal。",
                "/pages/archive/index");
        Map<String, Object> resp = support.checkinResponse("class", venue,
                viewService.bookingView(bookingMapper.findById(booking.getBookingId())), record, fresh, distanceMeters);
        idempotencyService.save(scope, idempotencyKey, SCAN_ENDPOINT, 200, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> freeCheckin(VoltMember member, Map<String, Object> input, String idempotencyKey) {
        String venueCode = support.text(input == null ? null : input.get("venueCode"));
        Integer distanceMeters = support.integer(input == null ? null : input.get("distanceMeters"));
        Venue venue = venueCode == null ? null : catalogMapper.findVenue(venueCode);
        if (venue == null) {
            throw new ApiException(400, "BAD_VENUE", "不是有效的 VOLT 场馆码。");
        }
        String scope = "free|" + member.getMemberId() + "|" + venueCode;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, FREE_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        // 锁住会员行，串行化同日同馆自由训练的重复检查。
        jdbcTemplate.queryForObject(
                "SELECT member_id FROM volt_member WHERE member_id = ? FOR UPDATE",
                String.class, member.getMemberId());
        LocalDate today = LocalDate.now();
        Map<String, Object> existing = jdbcTemplate.query(
                "SELECT " + VoltMiniSupport.RECORD_COLUMNS + " FROM volt_record "
                        + "WHERE member_id = ? AND record_date = ? AND venue_code = ? AND mode = 'free' "
                        + "ORDER BY checked_at DESC LIMIT 1",
                (rs, rowNum) -> support.recordRow(rs), member.getMemberId(), java.sql.Date.valueOf(today), venueCode)
                .stream().findFirst().orElse(null);
        if (existing != null) {
            Map<String, Object> resp = support.checkinResponse("free", venue, null, existing, member, distanceMeters);
            resp.put("duplicate", true);
            idempotencyService.save(scope, idempotencyKey, FREE_ENDPOINT, 200, resp);
            return resp;
        }
        Map<String, Object> record = support.createFreeRecord(member, venue);
        support.pushMessage(member.getMemberId(), "checkin", "自由训练已记录",
                "今天在" + venue.getVenueName() + "完成 45 分钟自由训练，不消耗充电次数。", "/pages/archive/index");
        VoltMember fresh = memberMapper.findById(member.getMemberId());
        Map<String, Object> resp = support.checkinResponse("free", venue, null, record, fresh, distanceMeters);
        idempotencyService.save(scope, idempotencyKey, FREE_ENDPOINT, 200, resp);
        return resp;
    }

    private Map<String, Object> findClassRecord(String memberId, LocalDate date, String venueCode, String bookingId) {
        if (bookingId != null) {
            return jdbcTemplate.query(
                    "SELECT " + VoltMiniSupport.RECORD_COLUMNS + " FROM volt_record "
                            + "WHERE member_id = ? AND record_date = ? AND venue_code = ? "
                            + "AND mode = 'class' AND booking_id = ? ORDER BY checked_at DESC LIMIT 1",
                    (rs, rowNum) -> support.recordRow(rs), memberId, java.sql.Date.valueOf(date), venueCode, bookingId)
                    .stream().findFirst().orElse(null);
        }
        return jdbcTemplate.query(
                "SELECT " + VoltMiniSupport.RECORD_COLUMNS + " FROM volt_record "
                        + "WHERE member_id = ? AND record_date = ? AND venue_code = ? AND mode = 'class' "
                        + "ORDER BY checked_at DESC LIMIT 1",
                (rs, rowNum) -> support.recordRow(rs), memberId, java.sql.Date.valueOf(date), venueCode)
                .stream().findFirst().orElse(null);
    }

    private void insertLedger(VoltMember member, String bookingId, String recordId, int change,
                              String reason, String note) {
        jdbcTemplate.update(
                "INSERT INTO volt_credit_ledger (ledger_id, member_id, booking_id, record_id, change_amount, "
                        + "balance_after, reason, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                VoltContract.uniqueId("LED"),
                member.getMemberId(), bookingId, recordId, change,
                member.getCredits() == null ? 0 : member.getCredits(), reason, note);
    }
}
