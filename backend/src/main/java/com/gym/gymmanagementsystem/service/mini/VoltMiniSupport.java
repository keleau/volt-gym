package com.gym.gymmanagementsystem.service.mini;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltBookingMapper;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小程序聚合接口的共享查询与视图工具：记录、预约/候补、消息、目录视图、ID 和格式化。
 * 具体业务编排在 VoltMemberCenterService / VoltArchiveService / VoltCheckinService / VoltScheduleService。
 */
@Component
public class VoltMiniSupport {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM/dd");
    static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    static final String[] WEEK_CN = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    static final String[] WEEK_SHORT = {"日", "一", "二", "三", "四", "五", "六"};
    static final int DEMO_TRIAL_PRICE = 49;

    private final JdbcTemplate jdbcTemplate;
    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final VoltBookingMapper bookingMapper;
    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;

    public VoltMiniSupport(JdbcTemplate jdbcTemplate,
                           VoltCatalogMapper catalogMapper,
                           VoltSessionMapper sessionMapper,
                           VoltBookingMapper bookingMapper,
                           VoltMemberMapper memberMapper,
                           VoltViewService viewService) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.bookingMapper = bookingMapper;
        this.memberMapper = memberMapper;
        this.viewService = viewService;
    }

    public static final String RECORD_COLUMNS = "record_id AS id, session_id AS sessionId, mode, source, "
            + "DATE_FORMAT(record_date, '%Y-%m-%d') AS date, TIME_FORMAT(record_time, '%H:%i') AS time, "
            + "TIME_FORMAT(start_time, '%H:%i') AS startTime, program_no AS programNo, program_no AS programCode, "
            + "program_name AS programName, program_en AS programEn, "
            + "(SELECT intensity FROM volt_program p WHERE p.program_no = volt_record.program_no LIMIT 1) AS intensity, "
            + "coach_id AS coachId, coach_name AS coachName, coach_name AS coach, venue_code AS venueCode, "
            + "venue_name AS venueName, "
            + "(SELECT image_url FROM volt_venue v WHERE v.venue_code = volt_record.venue_code LIMIT 1) AS venueImage, "
            + "duration_min AS durationMin, kcal, checked_at AS checkedAt, NULL AS distanceMeters";

    public Map<String, Object> recordRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", rs.getString("id"));
        record.put("sessionId", rs.getString("sessionId"));
        record.put("mode", rs.getString("mode"));
        record.put("source", rs.getString("source"));
        record.put("date", rs.getString("date"));
        record.put("time", rs.getString("time"));
        record.put("startTime", rs.getString("startTime"));
        record.put("programNo", rs.getString("programNo"));
        record.put("programCode", rs.getString("programCode"));
        record.put("programName", rs.getString("programName"));
        record.put("programEn", rs.getString("programEn"));
        record.put("intensity", rs.getString("intensity"));
        record.put("coachId", rs.getString("coachId"));
        record.put("coachName", rs.getString("coachName"));
        record.put("coach", rs.getString("coach"));
        record.put("venueCode", rs.getString("venueCode"));
        record.put("venueName", rs.getString("venueName"));
        record.put("venueImage", rs.getString("venueImage"));
        record.put("durationMin", rs.getInt("durationMin"));
        record.put("kcal", rs.getInt("kcal"));
        record.put("checkedAt", rs.getLong("checkedAt"));
        record.put("distanceMeters", rs.getObject("distanceMeters"));
        return record;
    }

    public Map<String, Object> waitlistRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("sessionId", rs.getString("sessionId"));
        row.put("position", rs.getInt("position"));
        row.put("status", rs.getString("status"));
        row.put("createdAt", rs.getLong("createdAt"));
        return row;
    }

    public List<Map<String, Object>> records(String memberId) {
        return jdbcTemplate.query(
                "SELECT " + RECORD_COLUMNS + " FROM volt_record WHERE member_id = ? ORDER BY checked_at DESC",
                (rs, rowNum) -> recordRow(rs), memberId);
    }

    public List<Map<String, Object>> bodyLogs(String memberId) {
        return jdbcTemplate.query(
                "SELECT body_log_id AS id, DATE_FORMAT(log_date, '%Y-%m-%d') AS date, height_cm AS heightCm, "
                        + "weight_kg AS weightKg FROM volt_body_log WHERE member_id = ? ORDER BY log_date",
                (rs, rowNum) -> {
                    Map<String, Object> log = new LinkedHashMap<>();
                    log.put("id", rs.getString("id"));
                    log.put("date", rs.getString("date"));
                    log.put("heightCm", rs.getInt("heightCm"));
                    log.put("weightKg", rs.getBigDecimal("weightKg"));
                    return log;
                }, memberId);
    }

    public Map<String, Object> stats(String memberId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int totalSessions = count("SELECT COUNT(*) FROM volt_record WHERE member_id = ?", memberId);
        int totalMinutes = sum("SELECT COALESCE(SUM(duration_min), 0) FROM volt_record WHERE member_id = ?", memberId);
        int totalKcal = sum("SELECT COALESCE(SUM(kcal), 0) FROM volt_record WHERE member_id = ?", memberId);
        int weekSessions = count(
                "SELECT COUNT(*) FROM volt_record WHERE member_id = ? AND record_date BETWEEN ? AND ?",
                memberId, java.sql.Date.valueOf(weekStart), java.sql.Date.valueOf(today));
        int weekMinutes = sum(
                "SELECT COALESCE(SUM(duration_min), 0) FROM volt_record WHERE member_id = ? AND record_date BETWEEN ? AND ?",
                memberId, java.sql.Date.valueOf(weekStart), java.sql.Date.valueOf(today));
        int weekKcal = sum(
                "SELECT COALESCE(SUM(kcal), 0) FROM volt_record WHERE member_id = ? AND record_date BETWEEN ? AND ?",
                memberId, java.sql.Date.valueOf(weekStart), java.sql.Date.valueOf(today));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("weekSessions", weekSessions);
        stats.put("weekKcal", weekKcal);
        stats.put("weekMinutes", weekMinutes);
        stats.put("weekHours", round1(weekMinutes / 60.0));
        stats.put("totalSessions", totalSessions);
        stats.put("totalKcal", totalKcal);
        stats.put("totalHours", round1(totalMinutes / 60.0));
        stats.put("streakWeeks", streakWeeks(memberId));
        return stats;
    }

    public int streakWeeks(String memberId) {
        List<java.sql.Date> weeks = jdbcTemplate.query(
                "SELECT DISTINCT DATE_SUB(record_date, INTERVAL WEEKDAY(record_date) DAY) AS week_start "
                        + "FROM volt_record WHERE member_id = ? ORDER BY week_start DESC",
                (rs, rowNum) -> rs.getDate("week_start"), memberId);
        if (weeks.isEmpty()) {
            return 0;
        }
        Set<LocalDate> set = new LinkedHashSet<>();
        for (java.sql.Date date : weeks) {
            set.add(date.toLocalDate());
        }
        LocalDate current = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!set.contains(current)) {
            current = current.minusWeeks(1);
            if (!set.contains(current)) {
                return 0;
            }
        }
        int streak = 0;
        while (set.contains(current)) {
            streak++;
            current = current.minusWeeks(1);
        }
        return streak;
    }

    public List<Map<String, Object>> week(VoltMember member) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Set<String> recordDates = new LinkedHashSet<>();
        for (Map<String, Object> record : records(member.getMemberId())) {
            recordDates.add(String.valueOf(record.get("date")));
        }
        Set<String> bookedDates = new LinkedHashSet<>();
        for (Booking booking : activeBookings(member)) {
            VoltSession session = sessionMapper.findById(booking.getSessionId());
            if (session != null) {
                bookedDates.add(session.getSessionDate().toString());
            }
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            String iso = date.toString();
            String status = "rest";
            if (recordDates.contains(iso)) {
                status = "done";
            } else if (bookedDates.contains(iso) && iso.compareTo(today.toString()) >= 0) {
                status = "booked";
            }
            if (iso.equals(today.toString())) {
                status = recordDates.contains(iso) ? "done" : "today";
            }
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("iso", iso);
            day.put("day", date.format(MM_DD));
            day.put("cn", WEEK_SHORT[date.getDayOfWeek().getValue() % 7]);
            day.put("isToday", iso.equals(today.toString()));
            day.put("status", status);
            days.add(day);
        }
        return days;
    }

    public List<Booking> allBookings(VoltMember member) {
        List<Booking> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Booking booking : bookingMapper.findByMember(member.getMemberId())) {
            if (seen.add(booking.getBookingId())) {
                list.add(booking);
            }
        }
        if (member.getPhone() != null) {
            for (Booking booking : bookingMapper.findByPhone(member.getPhone())) {
                if (seen.add(booking.getBookingId())) {
                    list.add(booking);
                }
            }
        }
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    public List<Booking> activeBookings(VoltMember member) {
        List<Booking> list = new ArrayList<>();
        for (Booking booking : allBookings(member)) {
            if ("locked".equals(booking.getStatus()) || "confirmed".equals(booking.getStatus())) {
                list.add(booking);
            }
        }
        list.sort((a, b) -> {
            VoltSession first = sessionMapper.findById(a.getSessionId());
            VoltSession second = sessionMapper.findById(b.getSessionId());
            if (first == null || second == null) {
                return 0;
            }
            return first.getSessionDate().atTime(first.getStartTime())
                    .compareTo(second.getSessionDate().atTime(second.getStartTime()));
        });
        return list;
    }

    public Map<String, Object> nextBooking(List<Booking> active) {
        LocalDateTime now = LocalDateTime.now();
        for (Booking booking : active) {
            VoltSession session = sessionMapper.findById(booking.getSessionId());
            if (session == null) {
                continue;
            }
            if (session.getSessionDate().atTime(session.getStartTime()).plusMinutes(30).isBefore(now)) {
                continue;
            }
            return viewService.bookingView(booking);
        }
        return null;
    }

    public Map<String, Object> todayBooking(List<Booking> active) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Booking best = null;
        LocalDateTime bestStart = null;
        for (Booking booking : active) {
            if (!"confirmed".equals(booking.getStatus())) {
                continue;
            }
            VoltSession session = sessionMapper.findById(booking.getSessionId());
            if (session == null || !session.getSessionDate().equals(today)) {
                continue;
            }
            LocalDateTime start = session.getSessionDate().atTime(session.getStartTime());
            // 已经结束的今日课程不再作为「今日课程」展示
            if (start.plusMinutes(30).isBefore(now)) {
                continue;
            }
            if (bestStart == null || start.isBefore(bestStart)) {
                best = booking;
                bestStart = start;
            }
        }
        return best == null ? null : viewService.bookingView(best);
    }

    public List<Booking> todayConfirmedBookings(VoltMember member, LocalDate date) {
        List<Booking> list = new ArrayList<>();
        for (Booking booking : allBookings(member)) {
            if (!"confirmed".equals(booking.getStatus())) {
                continue;
            }
            VoltSession session = sessionMapper.findById(booking.getSessionId());
            if (session != null && session.getSessionDate().equals(date)) {
                list.add(booking);
            }
        }
        list.sort((a, b) -> {
            VoltSession first = sessionMapper.findById(a.getSessionId());
            VoltSession second = sessionMapper.findById(b.getSessionId());
            return first.getStartTime().compareTo(second.getStartTime());
        });
        return list;
    }

    public Map<String, Object> bookingResult(Booking booking, boolean duplicate) {
        VoltSession session = sessionMapper.findById(booking.getSessionId());
        Map<String, Object> sessionSummary = new LinkedHashMap<>();
        sessionSummary.put("id", booking.getSessionId());
        sessionSummary.put("left", leftOf(session));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("booking", viewService.bookingView(booking));
        resp.put("session", sessionSummary);
        resp.put("duplicate", duplicate);
        return resp;
    }

    public Booking requireBooking(VoltMember member, String bookingId) {
        Booking booking = bookingMapper.findById(bookingId);
        if (booking == null) {
            throw ApiException.notFound("预约不存在。");
        }
        if (booking.getMemberId() != null && !booking.getMemberId().equals(member.getMemberId())) {
            throw ApiException.unauthorized("登录状态无效，请重新登录。");
        }
        if (booking.getMemberId() == null
                && (member.getPhone() == null || !member.getPhone().equals(booking.getMemberPhone()))) {
            throw ApiException.unauthorized("登录状态无效，请重新登录。");
        }
        return booking;
    }

    /**
     * 锁位过期会释放座位并递补候补，必须在独立事务里提交；
     * 否则调用方后续业务失败回滚时，已释放的座位会跟着被回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireLocks(String memberId) {
        if (memberId == null) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT booking_id, session_id FROM volt_booking "
                        + "WHERE member_id = ? AND status = 'locked' AND lock_expires_at IS NOT NULL AND lock_expires_at < ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookingId", rs.getString("booking_id"));
                    row.put("sessionId", rs.getString("session_id"));
                    return row;
                }, memberId, System.currentTimeMillis());
        for (Map<String, Object> row : rows) {
            int changed = jdbcTemplate.update(
                    "UPDATE volt_booking SET status = 'expired' WHERE booking_id = ? AND status = 'locked'",
                    row.get("bookingId"));
            if (changed == 0) {
                continue;
            }
            sessionMapper.decreaseBooked(String.valueOf(row.get("sessionId")));
            promoteWaitlist(String.valueOf(row.get("sessionId")));
        }
    }

    public String promoteWaitlist(String sessionId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Map<String, Object> next = jdbcTemplate.query(
                    "SELECT waitlist_id AS id, member_id AS memberId, position, status, "
                            + "UNIX_TIMESTAMP(created_at) * 1000 AS createdAt "
                            + "FROM volt_waitlist WHERE session_id = ? AND status = 'waiting' "
                            + "ORDER BY created_at, waitlist_id LIMIT 1",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("memberId", rs.getString("memberId"));
                        row.put("position", rs.getInt("position"));
                        row.put("status", rs.getString("status"));
                        row.put("createdAt", rs.getLong("createdAt"));
                        return row;
                    }, sessionId).stream().findFirst().orElse(null);
            if (next == null) {
                return null;
            }
            String waitlistId = String.valueOf(next.get("id"));
            int claimed = jdbcTemplate.update(
                    "UPDATE volt_waitlist SET status = 'converted', updated_at = NOW() "
                            + "WHERE waitlist_id = ? AND status = 'waiting'", waitlistId);
            if (claimed == 0) {
                continue;
            }
            VoltMember member = memberMapper.findById(String.valueOf(next.get("memberId")));
            boolean eligible = member != null
                    && member.getPhone() != null && !member.getPhone().isBlank()
                    && member.getCredits() != null && member.getCredits() > 0
                    && (member.getExpiresAt() == null || !member.getExpiresAt().isBefore(LocalDate.now()));
            if (!eligible) {
                jdbcTemplate.update(
                        "UPDATE volt_waitlist SET status = 'expired', updated_at = NOW() "
                                + "WHERE waitlist_id = ? AND status = 'converted'", waitlistId);
                continue;
            }
            if (sessionMapper.increaseBooked(sessionId) == 0) {
                jdbcTemplate.update(
                        "UPDATE volt_waitlist SET status = 'waiting', updated_at = NOW() "
                                + "WHERE waitlist_id = ? AND status = 'converted'", waitlistId);
                return null;
            }
            VoltSession session = sessionMapper.findById(sessionId);
            if (session == null) {
                sessionMapper.decreaseBooked(sessionId);
                jdbcTemplate.update(
                        "UPDATE volt_waitlist SET status = 'expired', updated_at = NOW() "
                                + "WHERE waitlist_id = ? AND status = 'converted'", waitlistId);
                return null;
            }
            Booking booking = new Booking();
            String orderNo = nextOrderNo();
            booking.setBookingId(nextBookingId());
            booking.setOrderNo(orderNo);
            booking.setEntryCode(orderNo);
            booking.setMemberId(member.getMemberId());
            booking.setSessionId(sessionId);
            booking.setStatus("locked");
            booking.setSeatNo(session.getBooked() == null ? 1 : session.getBooked());
            booking.setLockExpiresAt(System.currentTimeMillis() + VoltContract.LOCK_MINUTES * 60_000L);
            booking.setReminderOptIn(true);
            booking.setSource("wx");
            booking.setPlanCode(member.getPlanCode());
            booking.setPlanName(member.getPlanName());
            booking.setAmount(0);
            booking.setMemberName(member.getNickname());
            booking.setMemberPhone(member.getPhone());
            booking.setCreatedAt(LocalDateTime.now());
            try {
                bookingMapper.insert(booking);
            } catch (RuntimeException ex) {
                jdbcTemplate.update(
                        "UPDATE volt_waitlist SET status = 'waiting', updated_at = NOW() "
                                + "WHERE waitlist_id = ? AND status = 'converted'", waitlistId);
                sessionMapper.decreaseBooked(sessionId);
                throw ex;
            }
            jdbcTemplate.update(
                    "UPDATE volt_waitlist SET status = 'converted', updated_at = NOW() WHERE waitlist_id = ?",
                    waitlistId);
            pushMessage(member.getMemberId(), "waitlist", "候补递补 · " + programName(sessionId),
                    "有空位，已为你锁定 10 分钟，请尽快确认。", "/pages/bookings/index");
            return waitlistId;
        }
        return null;
    }

    public Map<String, Object> createClassRecord(VoltMember member, VoltSession session, Booking booking, Venue venue) {
        Program program = session == null ? null : catalogMapper.findProgram(session.getProgramNo());
        Coach coach = session == null ? null : catalogMapper.findCoach(session.getCoachId());
        LocalTime nowTime = LocalTime.now().withNano(0);
        String id = VoltContract.uniqueId("REC");
        long checkedAt = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO volt_record (record_id, member_id, session_id, booking_id, mode, source, record_date, "
                        + "record_time, start_time, program_no, program_name, program_en, coach_id, coach_name, venue_code, "
                        + "venue_name, duration_min, kcal, checked_at, created_at) VALUES (?, ?, ?, ?, 'class', 'scan', ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                id, member.getMemberId(), session == null ? "" : session.getSessionId(), booking.getBookingId(),
                java.sql.Date.valueOf(LocalDate.now()), nowTime, session == null ? nowTime : session.getStartTime(),
                session == null ? "" : session.getProgramNo(),
                program == null ? "" : program.getProgramName(),
                program == null ? "" : program.getProgramEn(),
                coach == null ? "" : coach.getCoachId(),
                coach == null ? "" : coach.getCoachName(),
                venue.getVenueCode(), venue.getVenueName(),
                program == null ? 60 : program.getDurationMin(),
                program == null ? 0 : program.getKcal(), checkedAt);
        return jdbcTemplate.query("SELECT " + RECORD_COLUMNS + " FROM volt_record WHERE record_id = ?",
                (rs, rowNum) -> recordRow(rs), id).stream().findFirst().orElseThrow();
    }

    public Map<String, Object> createFreeRecord(VoltMember member, Venue venue) {
        LocalTime nowTime = LocalTime.now().withNano(0);
        String id = VoltContract.uniqueId("REC");
        long checkedAt = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO volt_record (record_id, member_id, mode, source, record_date, record_time, start_time, "
                        + "program_no, program_name, program_en, coach_id, coach_name, venue_code, venue_name, duration_min, "
                        + "kcal, checked_at, created_at) VALUES (?, ?, 'free', 'scan', ?, ?, ?, 'FREE', '自由训练', "
                        + "'FREE TRAIN', '', '场馆教练', ?, ?, 45, 320, ?, NOW())",
                id, member.getMemberId(), java.sql.Date.valueOf(LocalDate.now()), nowTime, nowTime,
                venue.getVenueCode(), venue.getVenueName(), checkedAt);
        return jdbcTemplate.query("SELECT " + RECORD_COLUMNS + " FROM volt_record WHERE record_id = ?",
                (rs, rowNum) -> recordRow(rs), id).stream().findFirst().orElseThrow();
    }

    public Map<String, Object> checkinResponse(String mode, Venue venue, Map<String, Object> booking,
                                                Map<String, Object> record, VoltMember member, Integer distance) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mode", mode);
        resp.put("venue", viewService.venueView(venue, 0));
        if (booking != null) {
            resp.put("booking", booking);
        }
        if (record != null) {
            resp.put("record", record);
        }
        resp.put("member", viewService.memberView(member));
        resp.put("stats", stats(member.getMemberId()));
        resp.put("locationLabel", locationLabel(distance));
        return resp;
    }

    public String locationLabel(Integer distanceMeters) {
        if (distanceMeters == null) {
            return "场馆码已校验";
        }
        if (distanceMeters <= 2000) {
            return "定位已校验 · 距场馆 " + Math.max(10, Math.round(distanceMeters)) + " m";
        }
        return "定位超出范围 · 已采用场馆码校验";
    }

    public Map<String, Object> waitlistResult(VoltSession session, Map<String, Object> row) {
        Map<String, Object> view = viewService.sessionView(session);
        view.put("id", row.get("id"));
        view.put("sessionId", session.getSessionId());
        view.put("position", row.get("position"));
        view.put("status", row.get("status"));
        view.put("createdAt", row.get("createdAt"));
        return view;
    }

    public List<Map<String, Object>> waitlistRows(String memberId) {
        return jdbcTemplate.query(
                "SELECT waitlist_id AS id, session_id AS sessionId, position, status, "
                        + "UNIX_TIMESTAMP(created_at) * 1000 AS createdAt FROM volt_waitlist "
                        + "WHERE member_id = ? AND status = 'waiting' ORDER BY position, created_at",
                (rs, rowNum) -> waitlistRow(rs), memberId);
    }

    public Map<String, Object> brand() {
        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("name", "VOLT");
        brand.put("cnName", "电压健身");
        brand.put("phone", "400-900-6688");
        brand.put("trialPrice", DEMO_TRIAL_PRICE);
        return brand;
    }

    public Map<String, Object> subscriptions(String memberId) {
        ensureSubscription(memberId);
        return jdbcTemplate.queryForObject(
                "SELECT class_reminder, class_change, waitlist FROM volt_subscription WHERE member_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> sub = new LinkedHashMap<>();
                    sub.put("classReminder", rs.getInt("class_reminder") == 1);
                    sub.put("classChange", rs.getInt("class_change") == 1);
                    sub.put("waitlist", rs.getInt("waitlist") == 1);
                    return sub;
                }, memberId);
    }

    public void ensureSubscription(String memberId) {
        jdbcTemplate.update("INSERT IGNORE INTO volt_subscription (member_id) VALUES (?)", memberId);
    }

    public int unreadMessages(String memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_message WHERE member_id = ? AND read_flag = 0", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public void pushMessage(String memberId, String type, String title, String body, String action) {
        jdbcTemplate.update(
                "INSERT INTO volt_message (message_id, member_id, msg_type, title, body, action, read_flag, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, ?)",
                VoltContract.uniqueId("MSG"),
                memberId, type, title, body, action, System.currentTimeMillis());
    }

    public List<Map<String, Object>> programViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (Program program : catalogMapper.findPrograms()) {
            views.add(viewService.programView(program));
        }
        return views;
    }

    public List<Map<String, Object>> planViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (Plan plan : catalogMapper.findPlans()) {
            views.add(viewService.planView(plan));
        }
        return views;
    }

    public List<Map<String, Object>> coachViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (Coach coach : catalogMapper.findCoaches()) {
            views.add(viewService.coachView(coach));
        }
        return views;
    }

    public List<Map<String, Object>> venueViews(List<VoltSession> sessions) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<Map<String, Object>> views = new ArrayList<>();
        for (Venue venue : catalogMapper.findVenues()) {
            int slots = 0;
            for (VoltSession session : sessions) {
                if (session.getSessionDate().equals(today)
                        && venue.getVenueCode().equals(session.getVenueCode())
                        && session.getStartTime().isAfter(now)
                        && session.getCapacity() > session.getBooked()) {
                    slots++;
                }
            }
            views.add(viewService.venueView(venue, slots));
        }
        return views;
    }

    public Map<String, Object> minimalMember() {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("credits", 0);
        member.put("creditsTotal", 0);
        member.put("creditPercent", 0);
        return member;
    }

    public int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public int sum(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    public String programName(String sessionId) {
        VoltSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            return "训练";
        }
        Program program = catalogMapper.findProgram(session.getProgramNo());
        return program == null ? "训练" : program.getProgramName();
    }

    public String nextBookingId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "BK-" + compactDate() + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
            if (bookingMapper.findById(candidate) == null) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成预约编号失败，请重试。");
    }

    public String nextOrderNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "V" + compactDate() + String.format("%05d", ThreadLocalRandom.current().nextInt(100_000));
            if (bookingMapper.countByOrderNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成预约单号失败，请重试。");
    }

    public String compactDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    public String relativeDay(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "今天";
        }
        if (date.equals(today.plusDays(1))) {
            return "明天";
        }
        if (date.equals(today.plusDays(2))) {
            return "后天";
        }
        return (date.getMonthValue()) + " 月 " + date.getDayOfMonth() + " 日 " + WEEK_CN[date.getDayOfWeek().getValue() % 7];
    }

    /** 剩余座位：容量/已约都可能为空，统一在这里做空值保护。 */
    public int leftOf(VoltSession session) {
        if (session == null) {
            return 0;
        }
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int booked = session.getBooked() == null ? 0 : session.getBooked();
        return Math.max(capacity - booked, 0);
    }

    public int percent(Integer left, Integer total) {
        if (left == null || total == null || total <= 0) {
            return 0;
        }
        return (int) Math.round(left * 100.0 / total);
    }

    public double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    public String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
