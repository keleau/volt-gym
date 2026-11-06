package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltBookingMapper;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.IdempotencyService;
import com.gym.gymmanagementsystem.service.VoltBookingService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VoltBookingServiceImpl implements VoltBookingService {

    private static final String ENDPOINT = "POST /bookings";
    private static final String TRIAL_PLAN_CODE = "PLAN-01";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final VoltBookingMapper bookingMapper;
    private final VoltViewService viewService;
    private final IdempotencyService idempotencyService;

    public VoltBookingServiceImpl(VoltCatalogMapper catalogMapper,
                                  VoltSessionMapper sessionMapper,
                                  VoltBookingMapper bookingMapper,
                                  VoltViewService viewService,
                                  IdempotencyService idempotencyService) {
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.bookingMapper = bookingMapper;
        this.viewService = viewService;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public Map<String, Object> availability(String venueCode, String programNo, String dateText) {
        Venue venue = venueCode == null ? null : catalogMapper.findVenue(venueCode);
        if (venue == null) {
            throw ApiException.notFound("门店不存在或已下线。");
        }
        Program program = programNo == null ? null : catalogMapper.findProgram(programNo);
        if (program == null) {
            throw ApiException.notFound("课程不存在或已下线。");
        }
        LocalDate date = parseDate(dateText);
        requireWithinWindow(date);

        List<VoltSession> sessions = sessionMapper.findByDateVenueProgram(date, venueCode, programNo);
        LocalDateTime now = LocalDateTime.now();
        boolean today = date.equals(now.toLocalDate());
        int capacity = 0;
        int booked = 0;
        List<VoltSession> openSessions = new ArrayList<>();
        for (VoltSession session : sessions) {
            // 今天已经开课的场次不再计入可约余位，否则深夜会把“已结束”显示成“还有位”
            if (today && !session.getStartTime().isAfter(now.toLocalTime())) {
                continue;
            }
            capacity += session.getCapacity() == null ? 0 : session.getCapacity();
            booked += session.getBooked() == null ? 0 : session.getBooked();
            openSessions.add(session);
        }
        List<Map<String, Object>> sessionViews = viewService.sessionViews(openSessions);
        int remaining = Math.max(capacity - booked, 0);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("venueCode", venue.getVenueCode());
        resp.put("venueName", venue.getVenueName());
        resp.put("programNo", program.getProgramNo());
        resp.put("programName", program.getProgramName());
        resp.put("date", date.toString());
        resp.put("capacity", capacity);
        resp.put("booked", booked);
        resp.put("remaining", remaining);
        resp.put("soldOut", remaining <= 0);
        resp.put("sessions", sessionViews);
        return resp;
    }

    @Override
    public Map<String, Object> trialAvailability(String venueCode, String dateText) {
        Venue venue = venueCode == null ? null : catalogMapper.findVenue(venueCode);
        if (venue == null) {
            throw ApiException.notFound("门店不存在或已下线。");
        }
        LocalDate date = parseDate(dateText);
        requireWithinWindow(date);
        LocalDateTime now = LocalDateTime.now();
        List<VoltSession> sessions = new ArrayList<>();
        int capacity = 0;
        int booked = 0;
        for (VoltSession session : sessionMapper.findByDate(date)) {
            if (!venueCode.equals(session.getVenueCode())) {
                continue;
            }
            if (date.equals(now.toLocalDate()) && !session.getStartTime().isAfter(now.toLocalTime())) {
                continue;
            }
            capacity += session.getCapacity() == null ? 0 : session.getCapacity();
            booked += session.getBooked() == null ? 0 : session.getBooked();
            sessions.add(session);
        }
        int remaining = Math.max(capacity - booked, 0);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("venueCode", venue.getVenueCode());
        resp.put("venueName", venue.getVenueName());
        resp.put("date", date.toString());
        resp.put("trial", true);
        resp.put("capacity", capacity);
        resp.put("booked", booked);
        resp.put("remaining", remaining);
        resp.put("soldOut", remaining <= 0);
        resp.put("sessions", viewService.sessionViews(sessions));
        return resp;
    }

    @Override
    @Transactional
    public StoredResponse createBooking(Map<String, Object> input, String idempotencyKey, VoltMember member) {
        String name = text(input.get("name"));
        String phone = text(input.get("phone"));
        String planCode = text(input.get("planCode"));
        String sessionId = text(input.get("sessionId"));
        String venueCode = text(input.get("venueCode"));
        String programNo = text(input.get("programNo"));
        String dateText = text(input.get("date"));
        boolean autoConfirm = bool(input.get("autoConfirm"), true);
        boolean reminderOptIn = bool(input.get("reminderOptIn"), true);

        boolean trialRequest = TRIAL_PLAN_CODE.equals(planCode);

        if (member != null) {
            if (!trialRequest) {
                name = member.getNickname();
                if (member.getPlanCode() != null && !member.getPlanCode().isBlank()) {
                    planCode = member.getPlanCode();
                }
            } else if (name == null || name.isBlank()) {
                name = member.getNickname();
            }
            phone = member.getPhone();
            if (phone == null || phone.isBlank()) {
                throw ApiException.validation("请先绑定手机号再预约。", java.util.Map.of("phone", "请先绑定手机号"));
            }
        }

        // 幂等作用域包含会员/手机号与本次场次，同一个键不能跨会员或跨场次重放。
        String scope = "booking|" + (member != null ? member.getMemberId() : phone) + "|"
                + sessionId + "|" + venueCode + "|" + programNo + "|" + dateText;
        StoredResponse replay = idempotencyService.find(scope, idempotencyKey, ENDPOINT);
        if (replay != null) {
            return replay;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        if (name == null) {
            fields.put("name", "请填写姓名");
        }
        if (!VoltContract.isPhone(phone)) {
            fields.put("phone", "请输入正确的 11 位手机号");
        }
        Plan plan = planCode == null ? null : catalogMapper.findPlan(planCode);
        if (plan == null) {
            fields.put("planCode", "请选择次卡方案");
        }
        if (sessionId == null) {
            if (venueCode == null) {
                fields.put("venueCode", "请选择门店");
            }
            if (programNo == null) {
                fields.put("programNo", "请选择课程");
            }
            if (dateText == null) {
                fields.put("date", "请选择日期");
            }
        }
        if (!fields.isEmpty()) {
            throw ApiException.validation("请检查填写内容。", fields);
        }

        VoltSession session = resolveSession(sessionId, venueCode, programNo, dateText);
        // 同一场次的重复校验、占座和插入串行执行；官网与小程序共用同一把行锁。
        VoltSession locked = sessionMapper.findByIdForUpdate(session.getSessionId());
        if (locked == null || !"open".equals(locked.getStatus())) {
            throw ApiException.notFound("场次不存在或已下线。");
        }
        session = locked;

        Booking existing = bookingMapper.findActiveByPhoneAndSession(phone, session.getSessionId());
        if (existing != null) {
            throw ApiException.conflict("DUPLICATE_BOOKING", "你已经预约过这一场了。");
        }
        if ("PLAN-01".equals(plan.getPlanCode()) && bookingMapper.countTrialsByPhone(phone) > 0) {
            throw ApiException.conflict("TRIAL_ALREADY_BOOKED", "该手机号已预约过首次体验课。");
        }
        if (member != null && !trialRequest) {
            if (member.getCredits() == null || member.getCredits() <= 0) {
                throw ApiException.conflict("NO_CREDITS", "充电次数已用完，续卡后再预约。");
            }
            if (member.getExpiresAt() != null && member.getExpiresAt().isBefore(LocalDate.now())) {
                throw ApiException.conflict("PASS_EXPIRED", "次卡已过期，请续卡。");
            }
        }

        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int booked = session.getBooked() == null ? 0 : session.getBooked();
        if (booked >= capacity) {
            throw ApiException.conflict("SLOT_FULL", "这场已经约满，换一天或换一家门店试试。");
        }

        if (sessionMapper.increaseBooked(session.getSessionId()) == 0) {
            throw ApiException.conflict("SLOT_FULL", "这场已经约满，换一天或换一家门店试试。");
        }
        VoltSession fresh = sessionMapper.findById(session.getSessionId());
        int seatNo = fresh.getBooked() == null ? 0 : fresh.getBooked();

        String orderNo = nextOrderNo();
        Booking booking = new Booking();
        booking.setBookingId(nextBookingId());
        booking.setOrderNo(orderNo);
        booking.setEntryCode(orderNo);
        booking.setMemberId(member == null ? null : member.getMemberId());
        booking.setSessionId(session.getSessionId());
        booking.setStatus(autoConfirm ? "confirmed" : "locked");
        booking.setSeatNo(seatNo);
        booking.setLockExpiresAt(autoConfirm ? null : System.currentTimeMillis() + VoltContract.LOCK_MINUTES * 60_000L);
        booking.setReminderOptIn(reminderOptIn);
        booking.setSource("web");
        booking.setPlanCode(plan.getPlanCode());
        booking.setPlanName(plan.getPlanName());
        booking.setAmount(member == null || trialRequest ? amountOf(plan) : 0);
        booking.setMemberName(name);
        booking.setMemberPhone(phone);
        booking.setCreatedAt(LocalDateTime.now());
        bookingMapper.insert(booking);

        Map<String, Object> sessionSummary = new LinkedHashMap<>();
        sessionSummary.put("id", fresh.getSessionId());
        int freshCapacity = fresh.getCapacity() == null ? 0 : fresh.getCapacity();
        int freshBooked = fresh.getBooked() == null ? 0 : fresh.getBooked();
        sessionSummary.put("left", Math.max(freshCapacity - freshBooked, 0));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("booking", viewService.bookingView(booking));
        resp.put("session", sessionSummary);

        idempotencyService.save(scope, idempotencyKey, ENDPOINT, 201, resp);
        return new StoredResponse(201, resp);
    }

    private VoltSession resolveSession(String sessionId, String venueCode, String programNo, String dateText) {
        LocalDate today = LocalDate.now();
        if (sessionId != null) {
            VoltSession session = sessionMapper.findById(sessionId);
            if (session == null || !"open".equals(session.getStatus())) {
                throw ApiException.notFound("场次不存在或已下线。");
            }
            if (session.getSessionDate().isBefore(today)
                    || session.getSessionDate().isAfter(today.plusDays(VoltContract.BOOKING_WINDOW_DAYS))) {
                throw new ApiException(400, "DATE_OUT_OF_RANGE", "只能预约 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次。",
                        Map.of("date", "只能预约 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次"));
            }
            if (session.getSessionDate().atTime(session.getStartTime()).isBefore(LocalDateTime.now())) {
                throw ApiException.conflict("PAST_SESSION", "这场已经开始了，换一场试试。");
            }
            // 满员判断放到重复校验之后：已经占掉最后一个座位的人再次提交，应看到“已预约过”而不是“已满”
            return session;
        }

        if (catalogMapper.findVenue(venueCode) == null) {
            throw ApiException.notFound("门店不存在或已下线。");
        }
        if (catalogMapper.findProgram(programNo) == null) {
            throw ApiException.notFound("课程不存在或已下线。");
        }
        LocalDate date = parseDate(dateText);
        requireWithinWindow(date);

        VoltSession session = sessionMapper.findFirstOpen(date, today, venueCode, programNo, LocalTime.now());
        if (session == null) {
            List<VoltSession> daySessions = sessionMapper.findByDateVenueProgram(date, venueCode, programNo);
            boolean allStarted = date.equals(today) && !daySessions.isEmpty()
                    && daySessions.stream().allMatch(item -> !item.getStartTime().isAfter(LocalTime.now()));
            if (allStarted) {
                throw ApiException.conflict("PAST_SESSION", "这一天的课已经开始了，换一天吧。");
            }
            throw ApiException.conflict("SLOT_FULL", "这一天已经约满，换一天或换一家门店试试。");
        }
        return session;
    }

    private void requireWithinWindow(LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate last = today.plusDays(VoltContract.BOOKING_WINDOW_DAYS);
        if (date.isBefore(today) || date.isAfter(last)) {
            String message = "只能预约 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次";
            throw new ApiException(400, "DATE_OUT_OF_RANGE", message, Map.of("date", message));
        }
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            throw ApiException.validation("请选择日期。", Map.of("date", "请选择日期"));
        }
        try {
            return LocalDate.parse(dateText.trim(), DATE);
        } catch (DateTimeParseException ex) {
            throw ApiException.validation("日期格式不正确。", Map.of("date", "日期格式不正确"));
        }
    }

    private Integer amountOf(Plan plan) {
        return plan.getPrice();
    }

    private String nextBookingId() {
        // 用随机后缀而不是“当天计数 + 1”：并发下单时计数会算出同一个 id 撞主键
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "BK-" + VoltContract.compactDate(LocalDate.now())
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10_000));
            if (bookingMapper.findById(candidate) == null) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成预约编号失败，请重试。");
    }

    private String nextOrderNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "V" + VoltContract.compactDate(LocalDate.now())
                    + String.format("%05d", ThreadLocalRandom.current().nextInt(0, 100_000));
            if (bookingMapper.countByOrderNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成预约单号失败，请重试。");
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
