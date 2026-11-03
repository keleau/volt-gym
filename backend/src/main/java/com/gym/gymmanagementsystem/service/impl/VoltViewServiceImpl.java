package com.gym.gymmanagementsystem.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gym.gymmanagementsystem.service.VoltViewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Challenge;
import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.CommunityEvent;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoltViewServiceImpl implements VoltViewService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public VoltViewServiceImpl(VoltCatalogMapper catalogMapper,
                                VoltSessionMapper sessionMapper,
                                ObjectMapper objectMapper) {
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> venueView(Venue venue, Integer todaySlots) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", venue.getVenueCode());
        view.put("name", venue.getVenueName());
        view.put("en", venue.getVenueEn());
        view.put("address", venue.getAddress());
        view.put("metro", venue.getMetro());
        view.put("hours", venue.getHours());
        view.put("imageUrl", venue.getImageUrl());
        view.put("latitude", venue.getLatitude());
        view.put("longitude", venue.getLongitude());
        view.put("todaySlots", todaySlots == null ? 0 : todaySlots);
        return view;
    }

    @Override
    public Map<String, Object> programView(Program program) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("intensity", program.getIntensity());
        meta.put("duration", program.getDurationMin() + " MIN");
        meta.put("kcal", program.getKcal() + " KCAL");

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("no", program.getProgramNo());
        view.put("name", program.getProgramName());
        view.put("en", program.getProgramEn());
        view.put("desc", program.getProgramDesc());
        view.put("meta", meta);
        view.put("image", program.getImageUrl());
        view.put("imageUrl", program.getImageUrl());
        return view;
    }

    @Override
    public Map<String, Object> planView(Plan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", plan.getPlanCode());
        view.put("name", plan.getPlanName());
        view.put("en", plan.getPlanEn());
        view.put("price", plan.getPrice());
        view.put("unit", plan.getUnitText());
        view.put("featured", Boolean.TRUE.equals(plan.getFeatured()));
        view.put("points", parsePoints(plan.getPointsJson()));
        view.put("cta", plan.getCtaText());
        return view;
    }

    @Override
    public Map<String, Object> coachView(Coach coach) {
        List<String> venueCodes = splitCodes(coach.getVenueCodes());

        List<String> programNos = new ArrayList<>();
        List<String> programNames = new ArrayList<>();
        if (!VoltContract.isBlank(coach.getProgramNo())) {
            programNos.add(coach.getProgramNo());
            Program program = catalogMapper.findProgram(coach.getProgramNo());
            if (program != null) {
                programNames.add(program.getProgramName());
            }
        }

        List<String> venueNames = new ArrayList<>();
        for (String venueCode : venueCodes) {
            Venue venue = catalogMapper.findVenue(venueCode);
            if (venue != null) {
                venueNames.add(venue.getVenueName());
            }
        }

        VoltSession next = sessionMapper.findNextByCoach(coach.getCoachId(), LocalDate.now(), LocalTime.now());
        String nextAvailableAt = null;
        Integer slotsLeft = 0;
        if (next != null) {
            nextAvailableAt = next.getSessionDate() + "T" + next.getStartTime().format(HH_MM) + ":00+08:00";
            slotsLeft = left(next);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", coach.getCoachId());
        view.put("name", coach.getCoachName());
        view.put("title", coach.getTitleText());
        view.put("avatarUrl", coach.getAvatarUrl());
        view.put("bio", coach.getBio());
        view.put("programNos", programNos);
        view.put("venueCodes", venueCodes);
        view.put("programs", programNames);
        view.put("venues", venueNames);
        view.put("nextAvailableAt", nextAvailableAt);
        view.put("slotsLeft", slotsLeft);
        return view;
    }

    @Override
    public Map<String, Object> sessionView(VoltSession session) {
        return sessionView(session, catalogMapper.findVenue(session.getVenueCode()),
                catalogMapper.findProgram(session.getProgramNo()),
                catalogMapper.findCoach(session.getCoachId()));
    }

    @Override
    public List<Map<String, Object>> sessionViews(List<VoltSession> sessions) {
        Map<String, Venue> venues = new java.util.HashMap<>();
        for (Venue venue : catalogMapper.findVenues()) {
            venues.put(venue.getVenueCode(), venue);
        }
        Map<String, Program> programs = new java.util.HashMap<>();
        for (Program program : catalogMapper.findPrograms()) {
            programs.put(program.getProgramNo(), program);
        }
        Map<String, Coach> coaches = new java.util.HashMap<>();
        for (Coach coach : catalogMapper.findCoaches()) {
            coaches.put(coach.getCoachId(), coach);
        }
        List<Map<String, Object>> views = new ArrayList<>(sessions.size());
        for (VoltSession session : sessions) {
            views.add(sessionView(session, venues.get(session.getVenueCode()),
                    programs.get(session.getProgramNo()), coaches.get(session.getCoachId())));
        }
        return views;
    }

    private Map<String, Object> sessionView(VoltSession session, Venue venue, Program program, Coach coach) {
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int booked = session.getBooked() == null ? 0 : session.getBooked();
        int left = Math.max(capacity - booked, 0);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", session.getSessionId());
        view.put("code", session.getSessionCode());
        view.put("date", session.getSessionDate().toString());
        view.put("dateShort", VoltContract.dateShort(session.getSessionDate()));
        view.put("dayCn", VoltContract.dayCn(session.getSessionDate()));
        view.put("startTime", session.getStartTime().format(HH_MM));
        view.put("endTime", session.getEndTime().format(HH_MM));
        view.put("time", session.getStartTime().format(HH_MM));
        view.put("venueCode", session.getVenueCode());
        view.put("venueName", venue == null ? "" : venue.getVenueName());
        view.put("venueImage", venue == null ? "" : venue.getImageUrl());
        view.put("programNo", session.getProgramNo());
        view.put("programCode", session.getProgramNo());
        view.put("programName", program == null ? "" : program.getProgramName());
        view.put("programEn", program == null ? "" : program.getProgramEn());
        view.put("programImage", program == null ? "" : program.getImageUrl());
        view.put("intensity", program == null ? "" : program.getIntensity());
        view.put("durationMin", program == null ? 0 : program.getDurationMin());
        view.put("kcal", program == null ? 0 : program.getKcal());
        view.put("coachId", session.getCoachId());
        view.put("coachName", coach == null ? "" : coach.getCoachName());
        view.put("coach", coach == null ? "" : coach.getCoachName());
        view.put("capacity", capacity);
        view.put("left", left);
        view.put("booked", booked);
        view.put("soldOut", left <= 0);
        return view;
    }

    @Override
    public Map<String, Object> bookingView(Booking booking) {
        VoltSession session = sessionMapper.findById(booking.getSessionId());

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", booking.getBookingId());
        view.put("orderNo", booking.getOrderNo());
        view.put("entryCode", booking.getEntryCode());
        view.put("memberId", booking.getMemberId());
        view.put("sessionId", booking.getSessionId());
        view.put("status", booking.getStatus());
        view.put("seatNo", booking.getSeatNo());
        long lockRemainingMs = 0L;
        if ("locked".equals(booking.getStatus()) && booking.getLockExpiresAt() != null) {
            lockRemainingMs = Math.max(0L, booking.getLockExpiresAt() - System.currentTimeMillis());
        }
        view.put("lockExpiresAt", booking.getLockExpiresAt());
        view.put("lockRemainingMs", lockRemainingMs);
        view.put("lockText", lockRemainingMs > 0 ? formatLock(lockRemainingMs) : "");
        view.put("reminderOptIn", Boolean.TRUE.equals(booking.getReminderOptIn()));
        view.put("source", booking.getSource());
        view.put("planCode", booking.getPlanCode());
        view.put("planName", booking.getPlanName());
        view.put("amount", booking.getAmount());
        view.put("name", booking.getMemberName());
        view.put("phone", VoltContract.maskPhone(booking.getMemberPhone()));
        if (session != null) {
            // 场次字段只在这里定义一次，预约视图直接复用场次视图。
            view.putAll(sessionView(session));
            view.put("id", booking.getBookingId());
            view.put("sessionId", booking.getSessionId());
        }
        LocalDateTime now = LocalDateTime.now();
        boolean locked = "locked".equals(booking.getStatus());
        boolean confirmed = "confirmed".equals(booking.getStatus());
        LocalDateTime start = session == null ? null : session.getSessionDate().atTime(session.getStartTime());
        boolean canCancel = locked || (confirmed && start != null
                && start.minusHours(VoltContract.CANCEL_WINDOW_HOURS).isAfter(now));
        boolean canCheckin = confirmed && start != null
                && session.getSessionDate().equals(now.toLocalDate())
                && !now.isBefore(start.minusMinutes(VoltContract.CHECKIN_WINDOW_MINUTES))
                && !now.isAfter(start.plusMinutes(VoltContract.CHECKIN_WINDOW_MINUTES));
        view.put("canCancel", canCancel);
        view.put("cancelHint", canCancel
                ? "开课前 " + VoltContract.CANCEL_WINDOW_HOURS + " 小时可取消"
                : "距开课不足 " + VoltContract.CANCEL_WINDOW_HOURS + " 小时，请联系门店");
        view.put("canCheckin", canCheckin);
        view.put("createdAt", VoltContract.isoDateTime(booking.getCreatedAt()));
        return view;
    }

    @Override
    public Map<String, Object> eventView(CommunityEvent event, boolean mySigned) {
        int cap = event.getCapCount() == null ? 0 : event.getCapCount();
        int signed = event.getSignedCount() == null ? 0 : event.getSignedCount();
        int remaining = Math.max(cap - signed, 0);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", event.getEventId());
        view.put("title", event.getTitle());
        view.put("day", event.getDayText());
        view.put("month", event.getMonthText());
        view.put("tag", event.getTagText());
        view.put("desc", event.getDescText());
        view.put("time", event.getTimeText());
        view.put("venueCode", event.getVenueCode());
        view.put("venueName", event.getVenueName());
        view.put("cap", cap);
        view.put("signed", signed);
        view.put("remaining", remaining);
        view.put("soldOut", remaining <= 0);
        view.put("mySigned", mySigned);
        return view;
    }

    @Override
    public Map<String, Object> challengeView(Challenge challenge, boolean joinedByMe) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", challenge.getChallengeId());
        view.put("title", challenge.getTitle());
        view.put("desc", challenge.getDescText());
        view.put("sessions", challenge.getTargetSessions());
        view.put("days", challenge.getDays());
        view.put("joined", challenge.getJoinedCount());
        view.put("percent", challenge.getPercent());
        view.put("joinedByMe", joinedByMe);
        view.put("myProgress", 0);
        return view;
    }

    @Override
    public Map<String, Object> memberView(VoltMember member) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", member.getMemberId());
        view.put("nickname", member.getNickname());
        view.put("avatarUrl", member.getAvatarUrl() == null ? "" : member.getAvatarUrl());
        view.put("avatarText", member.getNickname() == null || member.getNickname().isBlank()
                ? "电"
                : member.getNickname().substring(0, 1));
        view.put("phone", VoltContract.maskPhone(member.getPhone()));
        view.put("phoneBound", Boolean.TRUE.equals(member.getPhoneBound()));
        view.put("goal", member.getGoal());
        view.put("heightCm", member.getHeightCm());
        view.put("weightKg", member.getWeightKg());
        view.put("bmi", bmi(member.getHeightCm(), member.getWeightKg()));
        view.put("credits", member.getCredits());
        view.put("creditsTotal", member.getCreditsTotal());
        view.put("creditPercent", creditPercent(member.getCredits(), member.getCreditsTotal()));
        view.put("planCode", member.getPlanCode());
        view.put("planName", member.getPlanName());
        view.put("expiresAt", member.getExpiresAt() == null ? null : member.getExpiresAt().toString());
        view.put("tier", member.getTier());
        view.put("referralCode", member.getReferralCode());
        view.put("joinedAt", member.getJoinedAt() == null ? null : member.getJoinedAt().toString());
        return view;
    }

    private int left(VoltSession session) {
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int booked = session.getBooked() == null ? 0 : session.getBooked();
        return Math.max(capacity - booked, 0);
    }

    private Double bmi(Integer heightCm, BigDecimal weightKg) {
        if (heightCm == null || heightCm <= 0 || weightKg == null || weightKg.doubleValue() <= 0) {
            return null;
        }
        double meters = heightCm / 100.0;
        double value = weightKg.doubleValue() / (meters * meters);
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatLock(long milliseconds) {
        long total = Math.max(0L, milliseconds / 1000L);
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    private Integer creditPercent(Integer credits, Integer creditsTotal) {
        if (credits == null || creditsTotal == null || creditsTotal <= 0) {
            return 0;
        }
        return (int) Math.round(credits * 100.0 / creditsTotal);
    }

    private List<String> parsePoints(String json) {
        if (VoltContract.isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> splitCodes(String codes) {
        if (VoltContract.isBlank(codes)) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String code : codes.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
}
