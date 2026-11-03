package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.VoltCatalogService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoltCatalogServiceImpl implements VoltCatalogService {

    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final VoltViewService viewService;

    public VoltCatalogServiceImpl(VoltCatalogMapper catalogMapper, VoltSessionMapper sessionMapper,
                                  VoltViewService viewService) {
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.viewService = viewService;
    }

    @Override
    public Map<String, Object> listVenues() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> venues = new ArrayList<>();
        for (Venue venue : catalogMapper.findVenues()) {
            int slots = catalogMapper.countTodaySlots(venue.getVenueCode(), today, LocalTime.now());
            venues.add(viewService.venueView(venue, slots));
        }
        return named("venues", venues);
    }

    @Override
    public Map<String, Object> listPrograms() {
        List<Map<String, Object>> programs = new ArrayList<>();
        for (Program program : catalogMapper.findPrograms()) {
            programs.add(viewService.programView(program));
        }
        return named("programs", programs);
    }

    @Override
    public Map<String, Object> listPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();
        for (Plan plan : catalogMapper.findPlans()) {
            plans.add(viewService.planView(plan));
        }
        return named("plans", plans);
    }

    @Override
    public Map<String, Object> listCoaches(String venueCode, String programNo) {
        List<Map<String, Object>> coaches = new ArrayList<>();
        for (Coach coach : catalogMapper.findCoaches()) {
            if (venueCode != null && !containsCode(coach.getVenueCodes(), venueCode)) {
                continue;
            }
            if (programNo != null && !programNo.equals(coach.getProgramNo())) {
                continue;
            }
            coaches.add(viewService.coachView(coach));
        }
        return named("coaches", coaches);
    }

    @Override
    public Map<String, Object> listSessions(String venueCode, String dateText, String coachId,
                                            String programNo, String fromText, String toText) {
        LocalDate today = LocalDate.now();
        LocalDate last = today.plusDays(VoltContract.BOOKING_WINDOW_DAYS);
        LocalDate date = parseDate(dateText, "date");
        LocalDate from = parseDate(fromText, "from");
        LocalDate to = parseDate(toText, "to");
        if (date != null && (date.isBefore(today) || date.isAfter(last))) {
            String message = "只能查看 " + VoltContract.BOOKING_WINDOW_DAYS + " 天内的场次";
            throw new ApiException(400, "DATE_OUT_OF_RANGE", message, java.util.Map.of("date", message));
        }
        // 未传日期时只返回一周，避免一次拉出 60 天 × 全部门店/课程的超大列表。
        if (from == null && date == null) {
            from = today;
        }
        if (to == null && date == null) {
            to = from.plusDays(6).isAfter(last) ? last : from.plusDays(6);
        }
        if (from != null && from.isBefore(today)) {
            from = today;
        }
        if (to != null && to.isAfter(last)) {
            to = last;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.validation("日期区间不正确。", java.util.Map.of("from", "开始日期不能晚于结束日期"));
        }

        List<VoltSession> sessions = sessionMapper.findFiltered(from, to, date, venueCode, coachId, programNo);
        return named("sessions", viewService.sessionViews(sessions));
    }

    private boolean containsCode(String codes, String expected) {
        if (codes == null || expected == null) {
            return false;
        }
        for (String code : codes.split(",")) {
            if (expected.equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    private LocalDate parseDate(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw ApiException.validation("日期格式不正确。", java.util.Map.of(field, "日期格式应为 YYYY-MM-DD"));
        }
    }

    private Map<String, Object> named(String key, List<?> value) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put(key, value);
        return resp;
    }
}
