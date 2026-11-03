package com.gym.gymmanagementsystem.service.mini;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltBookingMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.IdempotencyService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课表、锁位/确认/取消、候补和锁位超时处理。
 */
@Service
public class VoltScheduleService {

    private static final Logger log = LoggerFactory.getLogger(VoltScheduleService.class);
    private static final String LOCK_ENDPOINT = "POST /sessions/{id}/bookings";
    private static final String CONFIRM_ENDPOINT = "POST /bookings/{id}/confirm";
    private static final String CANCEL_ENDPOINT = "POST /bookings/{id}/cancel";
    private static final String WAITLIST_ENDPOINT = "POST /sessions/{id}/waitlist";

    private final VoltMiniSupport support;
    private final JdbcTemplate jdbcTemplate;
    private final VoltSessionMapper sessionMapper;
    private final VoltBookingMapper bookingMapper;
    private final VoltViewService viewService;
    private final IdempotencyService idempotencyService;

    public VoltScheduleService(VoltMiniSupport support,
                               JdbcTemplate jdbcTemplate,
                               VoltSessionMapper sessionMapper,
                               VoltBookingMapper bookingMapper,
                               VoltViewService viewService,
                               IdempotencyService idempotencyService) {
        this.support = support;
        this.jdbcTemplate = jdbcTemplate;
        this.sessionMapper = sessionMapper;
        this.bookingMapper = bookingMapper;
        this.viewService = viewService;
        this.idempotencyService = idempotencyService;
    }

    public Map<String, Object> schedule(VoltMember member) {
        support.expireLocks(member == null ? null : member.getMemberId());
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(6);
        List<VoltSession> sessions = sessionMapper.findBetween(today, end);
        Map<String, Booking> bookingBySession = new HashMap<>();
        Map<String, Map<String, Object>> waitlistBySession = new HashMap<>();
        if (member != null) {
            for (Booking booking : support.activeBookings(member)) {
                bookingBySession.putIfAbsent(booking.getSessionId(), booking);
            }
            for (Map<String, Object> row : support.waitlistRows(member.getMemberId())) {
                waitlistBySession.putIfAbsent(String.valueOf(row.get("sessionId")), row);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> sessionViews = viewService.sessionViews(sessions);
        for (int i = 0; i < sessions.size(); i++) {
            VoltSession session = sessions.get(i);
            Map<String, Object> view = sessionViews.get(i);
            Booking booking = bookingBySession.get(session.getSessionId());
            Map<String, Object> wait = waitlistBySession.get(session.getSessionId());
            view.put("time", session.getStartTime().format(VoltMiniSupport.HH_MM));
            view.put("isPast", session.getSessionDate().atTime(session.getStartTime()).isBefore(now));
            view.put("myStatus", booking != null ? booking.getStatus() : wait != null ? "waitlisted" : "");
            view.put("myBookingId", booking != null ? booking.getBookingId() : "");
            view.put("myLockExpiresAt", booking != null && booking.getLockExpiresAt() != null
                    ? booking.getLockExpiresAt() : 0L);
            view.put("myWaitlistId", wait != null ? wait.get("id") : "");
            view.put("waitlistPosition", wait != null ? wait.get("position") : 0);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("iso", date.toString());
            day.put("cn", VoltMiniSupport.WEEK_CN[date.getDayOfWeek().getValue() % 7]);
            day.put("short", date.format(VoltMiniSupport.MM_DD));
            day.put("isToday", i == 0);
            day.put("relative", support.relativeDay(date));
            day.put("tag", i == 0 ? "TODAY" : i == 1 ? "明天" : i == 2 ? "后天"
                    : VoltMiniSupport.WEEK_CN[date.getDayOfWeek().getValue() % 7]);
            days.add(day);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("days", days);
        resp.put("sessions", sessionViews);
        resp.put("venues", support.venueViews(sessions));
        resp.put("member", member == null ? support.minimalMember() : viewService.memberView(member));
        resp.put("programs", support.programViews());
        resp.put("coaches", support.coachViews());
        return resp;
    }

    public Map<String, Object> sessionDetail(String sessionId, VoltMember member) {
        VoltSession session = sessionMapper.findById(sessionId);
        if (session == null || !"open".equals(session.getStatus())) {
            throw ApiException.notFound("课次不存在或已下架。");
        }
        Map<String, Object> view = viewService.sessionView(session);
        view.put("time", session.getStartTime().format(VoltMiniSupport.HH_MM));
        view.put("isPast", session.getSessionDate().atTime(session.getStartTime()).isBefore(LocalDateTime.now()));
        view.put("myStatus", "");
        view.put("myBookingId", "");
        view.put("myLockExpiresAt", 0L);
        view.put("myWaitlistId", "");
        view.put("waitlistPosition", 0);
        if (member != null) {
            support.expireLocks(member.getMemberId());
            Booking booking = support.allBookings(member).stream()
                    .filter(item -> sessionId.equals(item.getSessionId()))
                    .findFirst().orElse(null);
            Map<String, Object> wait = support.waitlistRows(member.getMemberId()).stream()
                    .filter(item -> sessionId.equals(String.valueOf(item.get("sessionId"))))
                    .findFirst().orElse(null);
            view.put("myStatus", booking != null ? booking.getStatus() : wait != null ? "waitlisted" : "");
            view.put("myBookingId", booking != null ? booking.getBookingId() : "");
            view.put("myLockExpiresAt", booking != null && booking.getLockExpiresAt() != null
                    ? booking.getLockExpiresAt() : 0L);
            view.put("myWaitlistId", wait != null ? wait.get("id") : "");
            view.put("waitlistPosition", wait != null ? wait.get("position") : 0);
        }
        return view;
    }

    @Transactional
    public Map<String, Object> createBooking(VoltMember member, String sessionId,
                                             Map<String, Object> input, String idempotencyKey) {
        String scope = "lock|" + member.getMemberId() + "|" + sessionId;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, LOCK_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        support.expireLocks(member.getMemberId());
        if (member.getPhone() == null || member.getPhone().isBlank()) {
            throw ApiException.validation("请先绑定手机号再预约。", java.util.Map.of("phone", "请先绑定手机号"));
        }
        // 锁住场次行：同一场次的重复检查和占座串行执行，避免并发下生成两张有效预约。
        VoltSession session = sessionMapper.findByIdForUpdate(sessionId);
        if (session == null || !"open".equals(session.getStatus())) {
            throw ApiException.notFound("场次不存在或已下线。");
        }
        if (session.getSessionDate().isBefore(LocalDate.now())
                || session.getSessionDate().isAfter(LocalDate.now().plusDays(VoltContract.BOOKING_WINDOW_DAYS))) {
            throw new ApiException(400, "DATE_OUT_OF_RANGE",
                    "只能预约 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次。",
                    java.util.Map.of("date", "只能预约 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次"));
        }
        if (session.getSessionDate().atTime(session.getStartTime()).isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("PAST_SESSION", "这场已经开始了，换一场试试。");
        }
        Booking existing = bookingMapper.findActiveByMemberAndSession(member.getMemberId(), sessionId);
        if (existing == null && member.getPhone() != null) {
            existing = bookingMapper.findActiveByPhoneAndSession(member.getPhone(), sessionId);
        }
        if (existing != null) {
            Map<String, Object> resp = support.bookingResult(existing, true);
            idempotencyService.save(scope, idempotencyKey, LOCK_ENDPOINT, 200, resp);
            return resp;
        }
        if (member.getCredits() == null || member.getCredits() <= 0) {
            throw ApiException.conflict("NO_CREDITS", "充电次数已用完，续卡后再预约。");
        }
        if (member.getExpiresAt() != null && member.getExpiresAt().isBefore(LocalDate.now())) {
            throw ApiException.conflict("PASS_EXPIRED", "次卡已过期，请续卡。");
        }
        if (sessionMapper.increaseBooked(sessionId) == 0) {
            throw ApiException.conflict("SLOT_FULL", "这场已经约满，换一天或换一家门店试试。");
        }
        VoltSession fresh = sessionMapper.findById(sessionId);
        Booking booking = new Booking();
        String orderNo = support.nextOrderNo();
        booking.setBookingId(support.nextBookingId());
        booking.setOrderNo(orderNo);
        booking.setEntryCode(orderNo);
        booking.setMemberId(member.getMemberId());
        booking.setSessionId(sessionId);
        booking.setStatus("locked");
        booking.setSeatNo(fresh.getBooked() == null ? 1 : fresh.getBooked());
        booking.setLockExpiresAt(System.currentTimeMillis() + VoltContract.LOCK_MINUTES * 60_000L);
        booking.setReminderOptIn(input == null || input.get("reminderOptIn") == null
                || Boolean.parseBoolean(String.valueOf(input.get("reminderOptIn"))));
        booking.setSource("wx");
        booking.setPlanCode(member.getPlanCode());
        booking.setPlanName(member.getPlanName());
        booking.setAmount(0);
        booking.setMemberName(member.getNickname());
        booking.setMemberPhone(member.getPhone());
        booking.setCreatedAt(LocalDateTime.now());
        bookingMapper.insert(booking);
        jdbcTemplate.update(
                "UPDATE volt_waitlist SET status = 'converted', updated_at = NOW() "
                        + "WHERE member_id = ? AND session_id = ? AND status = 'waiting'",
                member.getMemberId(), sessionId);

        Map<String, Object> sessionSummary = new LinkedHashMap<>();
        sessionSummary.put("id", sessionId);
        sessionSummary.put("left", support.leftOf(fresh));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("booking", viewService.bookingView(booking));
        resp.put("session", sessionSummary);
        resp.put("duplicate", false);
        idempotencyService.save(scope, idempotencyKey, LOCK_ENDPOINT, 201, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> confirmBooking(VoltMember member, String bookingId, String idempotencyKey) {
        String scope = "confirm|" + member.getMemberId() + "|" + bookingId;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, CONFIRM_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        Booking booking = support.requireBooking(member, bookingId);
        if ("confirmed".equals(booking.getStatus())) {
            Map<String, Object> view = viewService.bookingView(booking);
            idempotencyService.save(scope, idempotencyKey, CONFIRM_ENDPOINT, 200, view);
            return view;
        }
        if (!"locked".equals(booking.getStatus())) {
            throw ApiException.conflict("BAD_STATE", "当前预约无法确认。");
        }
        if (booking.getLockExpiresAt() != null && booking.getLockExpiresAt() < System.currentTimeMillis()) {
            support.expireLocks(member.getMemberId());
            throw ApiException.conflict("LOCK_EXPIRED", "锁位已超时，请重新选择课次。");
        }
        VoltSession session = sessionMapper.findById(booking.getSessionId());
        if (session == null || session.getSessionDate().atTime(session.getStartTime()).isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("PAST_SESSION", "这场已经开始了，无法确认。");
        }
        // 条件更新：两个并发确认只有一个能改状态，另一个按幂等返回当前预约。
        if (bookingMapper.confirmIfLocked(bookingId) == 0) {
            Booking current = bookingMapper.findById(bookingId);
            if (current != null && "confirmed".equals(current.getStatus())) {
                Map<String, Object> view = viewService.bookingView(current);
                idempotencyService.save(scope, idempotencyKey, CONFIRM_ENDPOINT, 200, view);
                return view;
            }
            throw ApiException.conflict("LOCK_EXPIRED", "锁位已超时，请重新选择课次。");
        }
        Booking fresh = bookingMapper.findById(bookingId);
        support.pushMessage(member.getMemberId(), "reminder", "预约成功 · " + support.programName(fresh.getSessionId()),
                "开课前 60 分钟会通过微信服务通知提醒你。", "/pages/bookings/index");
        Map<String, Object> view = viewService.bookingView(fresh);
        idempotencyService.save(scope, idempotencyKey, CONFIRM_ENDPOINT, 200, view);
        return view;
    }

    @Transactional
    public Map<String, Object> cancelBooking(VoltMember member, String bookingId, String idempotencyKey) {
        String scope = "cancel|" + member.getMemberId() + "|" + bookingId;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, CANCEL_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        Booking booking = support.requireBooking(member, bookingId);
        if ("cancelled".equals(booking.getStatus())) {
            Map<String, Object> view = viewService.bookingView(booking);
            idempotencyService.save(scope, idempotencyKey, CANCEL_ENDPOINT, 200, view);
            return view;
        }
        if (!"locked".equals(booking.getStatus()) && !"confirmed".equals(booking.getStatus())) {
            throw ApiException.conflict("BAD_STATE", "这节预约已经无法取消。");
        }
        VoltSession session = sessionMapper.findById(booking.getSessionId());
        if ("confirmed".equals(booking.getStatus()) && session != null
                && session.getSessionDate().atTime(session.getStartTime())
                .minusHours(VoltContract.CANCEL_WINDOW_HOURS).isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("CANCEL_WINDOW_CLOSED",
                    "距开课不足 " + VoltContract.CANCEL_WINDOW_HOURS + " 小时，请联系门店。");
        }
        // 条件更新：并发取消只有一个请求能释放座位并触发递补。
        if (bookingMapper.cancelIfActive(bookingId) == 0) {
            Booking current = bookingMapper.findById(bookingId);
            if (current != null && "cancelled".equals(current.getStatus())) {
                Map<String, Object> view = viewService.bookingView(current);
                idempotencyService.save(scope, idempotencyKey, CANCEL_ENDPOINT, 200, view);
                return view;
            }
            throw ApiException.conflict("BAD_STATE", "这节预约已经无法取消。");
        }
        sessionMapper.decreaseBooked(booking.getSessionId());
        support.pushMessage(member.getMemberId(), "system", "预约已取消 · 名额已释放",
                "本次预约已取消，未扣充电次数。", "/pages/schedule/index");
        String promoted = support.promoteWaitlist(booking.getSessionId());
        Booking fresh = bookingMapper.findById(bookingId);
        Map<String, Object> view = viewService.bookingView(fresh);
        view.put("promotedWaitlistId", promoted);
        VoltSession freshSession = sessionMapper.findById(booking.getSessionId());
        if (freshSession != null) {
            Map<String, Object> sessionSummary = new LinkedHashMap<>();
            sessionSummary.put("id", freshSession.getSessionId());
            sessionSummary.put("left", support.leftOf(freshSession));
            view.put("session", sessionSummary);
        }
        idempotencyService.save(scope, idempotencyKey, CANCEL_ENDPOINT, 200, view);
        return view;
    }

    @Transactional
    public Map<String, Object> joinWaitlist(VoltMember member, String sessionId, String idempotencyKey) {
        String scope = "waitlist|" + member.getMemberId() + "|" + sessionId;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, WAITLIST_ENDPOINT);
        if (replay != null && replay.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) replay.getBody();
            return body;
        }
        VoltSession session = sessionMapper.findByIdForUpdate(sessionId);
        if (session == null || !"open".equals(session.getStatus())) {
            throw ApiException.notFound("课次不存在。");
        }
        if (session.getSessionDate().atTime(session.getStartTime()).isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("PAST_SESSION", "这场已经开始了，不能加入候补。");
        }
        if (support.leftOf(session) > 0) {
            throw ApiException.conflict("HAS_SEAT", "还有空位，可以直接预约。");
        }
        Map<String, Object> existing = jdbcTemplate.query(
                "SELECT waitlist_id AS id, session_id AS sessionId, position, status, "
                        + "UNIX_TIMESTAMP(created_at) * 1000 AS createdAt "
                        + "FROM volt_waitlist WHERE member_id = ? AND session_id = ? LIMIT 1",
                (rs, rowNum) -> support.waitlistRow(rs), member.getMemberId(), sessionId)
                .stream().findFirst().orElse(null);
        if (existing != null && "waiting".equals(existing.get("status"))) {
            Map<String, Object> resp = support.waitlistResult(session, existing);
            idempotencyService.save(scope, idempotencyKey, WAITLIST_ENDPOINT, 200, resp);
            return resp;
        }
        Integer waiting = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_waitlist WHERE session_id = ? AND status = 'waiting'",
                Integer.class, sessionId);
        int position = (waiting == null ? 0 : waiting) + 1;
        if (existing != null) {
            jdbcTemplate.update("UPDATE volt_waitlist SET status = 'waiting', position = ?, updated_at = NOW() "
                    + "WHERE waitlist_id = ?", position, existing.get("id"));
            existing.put("status", "waiting");
            existing.put("position", position);
        } else {
            String id = VoltContract.uniqueId("WL");
            jdbcTemplate.update(
                    "INSERT INTO volt_waitlist (waitlist_id, member_id, session_id, position, status, created_at) "
                            + "VALUES (?, ?, ?, ?, 'waiting', NOW())",
                    id, member.getMemberId(), sessionId, position);
            existing = new LinkedHashMap<>();
            existing.put("id", id);
            existing.put("sessionId", sessionId);
            existing.put("position", position);
            existing.put("status", "waiting");
            existing.put("createdAt", System.currentTimeMillis());
        }
        support.pushMessage(member.getMemberId(), "waitlist", "已进入候补 · " + support.programName(sessionId),
                "当前第 " + position + " 位，有空位会通过微信服务通知提醒。", "/pages/bookings/index?tab=waitlist");
        Map<String, Object> resp = support.waitlistResult(session, existing);
        idempotencyService.save(scope, idempotencyKey, WAITLIST_ENDPOINT, 200, resp);
        return resp;
    }

    @Transactional
    public Map<String, Object> cancelWaitlist(VoltMember member, String waitlistId) {
        Map<String, Object> row = jdbcTemplate.query(
                "SELECT waitlist_id AS id, session_id AS sessionId, position, status, "
                        + "UNIX_TIMESTAMP(created_at) * 1000 AS createdAt "
                        + "FROM volt_waitlist WHERE waitlist_id = ? AND member_id = ?",
                (rs, rowNum) -> support.waitlistRow(rs), waitlistId, member.getMemberId())
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("候补记录不存在。"));
        if ("cancelled".equals(row.get("status"))) {
            // 网络重试或用户重复点击：已经退出候补时按幂等成功返回。
            return row;
        }
        if (!"waiting".equals(row.get("status"))) {
            throw ApiException.conflict("BAD_STATE", "该候补已结束。");
        }
        int changed = jdbcTemplate.update(
                "UPDATE volt_waitlist SET status = 'cancelled', updated_at = NOW() "
                        + "WHERE waitlist_id = ? AND member_id = ? AND status = 'waiting'",
                waitlistId, member.getMemberId());
        if (changed == 0) {
            Map<String, Object> current = jdbcTemplate.query(
                    "SELECT waitlist_id AS id, session_id AS sessionId, position, status, "
                            + "UNIX_TIMESTAMP(created_at) * 1000 AS createdAt "
                            + "FROM volt_waitlist WHERE waitlist_id = ? AND member_id = ?",
                    (rs, rowNum) -> support.waitlistRow(rs), waitlistId, member.getMemberId())
                    .stream().findFirst().orElse(null);
            if (current != null && "cancelled".equals(current.get("status"))) {
                return current;
            }
            throw ApiException.conflict("BAD_STATE", "该候补已结束。");
        }
        row.put("status", "cancelled");
        return row;
    }

    /**
     * 每 60 秒处理一次锁位超时：释放座位并按 FIFO 递给下一位候补。
     * 预约本身不扣次；候补递补只生成新的 10 分钟锁位，仍不扣次。
     */
    @Scheduled(initialDelay = 30_000L, fixedDelay = 60_000L)
    @Transactional
    public void expireStaleLocks() {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT booking_id, session_id FROM volt_booking "
                        + "WHERE status = 'locked' AND lock_expires_at IS NOT NULL AND lock_expires_at < ? "
                        + "ORDER BY lock_expires_at LIMIT 100",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookingId", rs.getString("booking_id"));
                    row.put("sessionId", rs.getString("session_id"));
                    return row;
                }, System.currentTimeMillis());
        for (Map<String, Object> row : rows) {
            int changed = jdbcTemplate.update(
                    "UPDATE volt_booking SET status = 'expired' WHERE booking_id = ? AND status = 'locked'",
                    row.get("bookingId"));
            if (changed == 0) {
                continue;
            }
            sessionMapper.decreaseBooked(String.valueOf(row.get("sessionId")));
            String promotedWaitlistId = support.promoteWaitlist(String.valueOf(row.get("sessionId")));
            log.info("锁位超时释放 booking={} session={} promoted={}", row.get("bookingId"),
                    row.get("sessionId"), promotedWaitlistId);
        }
    }
}
