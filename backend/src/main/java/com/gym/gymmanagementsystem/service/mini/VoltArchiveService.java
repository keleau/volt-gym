package com.gym.gymmanagementsystem.service.mini;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 训练档案、趋势图数据、身体数据。
 */
@Service
public class VoltArchiveService {

    private final VoltMiniSupport support;
    private final JdbcTemplate jdbcTemplate;
    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;

    public VoltArchiveService(VoltMiniSupport support,
                              JdbcTemplate jdbcTemplate,
                              VoltMemberMapper memberMapper,
                              VoltViewService viewService) {
        this.support = support;
        this.jdbcTemplate = jdbcTemplate;
        this.memberMapper = memberMapper;
        this.viewService = viewService;
    }

    public Map<String, Object> archive(VoltMember member, String monthText) {
        LocalDate today = LocalDate.now();
        String month = normalizeMonth(monthText, today);
        boolean filterByMonth = monthText != null && !monthText.isBlank();
        List<Map<String, Object>> allRecords = support.records(member.getMemberId());
        List<Map<String, Object>> allBodyLogs = support.bodyLogs(member.getMemberId());
        // 趋势图固定看最近 8 周，不能被月份筛选截断；筛选只影响列表和日历。
        List<Map<String, Object>> records = filterByMonth
                ? allRecords.stream().filter(item -> String.valueOf(item.get("date")).startsWith(month)).toList()
                : allRecords;
        List<Map<String, Object>> bodyLogs = filterByMonth
                ? allBodyLogs.stream().filter(item -> String.valueOf(item.get("date")).startsWith(month)).toList()
                : allBodyLogs;
        Map<String, Object> stats = support.stats(member.getMemberId());

        List<Map<String, Object>> sessionTrend = new ArrayList<>();
        List<Map<String, Object>> durationTrend = new ArrayList<>();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 7; i >= 0; i--) {
            LocalDate weekStart = currentMonday.minusWeeks(i);
            int count = 0;
            int duration = 0;
            for (Map<String, Object> record : allRecords) {
                LocalDate date = LocalDate.parse(String.valueOf(record.get("date")));
                if (date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).equals(weekStart)) {
                    count++;
                    duration += ((Number) record.get("durationMin")).intValue();
                }
            }
            Map<String, Object> sessionPoint = new LinkedHashMap<>();
            sessionPoint.put("label", weekStart.format(VoltMiniSupport.MM_DD));
            sessionPoint.put("value", count);
            sessionTrend.add(sessionPoint);
            Map<String, Object> durationPoint = new LinkedHashMap<>();
            durationPoint.put("label", weekStart.format(VoltMiniSupport.MM_DD));
            durationPoint.put("value", duration);
            durationTrend.add(durationPoint);
        }

        List<Map<String, Object>> weightTrend = new ArrayList<>();
        for (Map<String, Object> log : allBodyLogs) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", String.valueOf(log.get("date")).substring(5).replace("-", "/"));
            point.put("value", log.get("weightKg"));
            weightTrend.add(point);
        }
        if (weightTrend.size() > 8) {
            weightTrend = new ArrayList<>(weightTrend.subList(weightTrend.size() - 8, weightTrend.size()));
        }

        Set<String> dates = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            dates.add(String.valueOf(record.get("date")));
        }

        Map<String, Object> credits = new LinkedHashMap<>();
        credits.put("left", member.getCredits());
        credits.put("total", member.getCreditsTotal());
        credits.put("percent", support.percent(member.getCredits(), member.getCreditsTotal()));
        credits.put("planName", member.getPlanName());
        credits.put("expiresAt", member.getExpiresAt() == null ? null : member.getExpiresAt().toString());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("member", viewService.memberView(member));
        resp.put("stats", stats);
        resp.put("records", records);
        resp.put("recordDates", new ArrayList<>(dates));
        resp.put("bodyLogs", bodyLogs);
        resp.put("sessionTrend", sessionTrend);
        resp.put("durationTrend", durationTrend);
        resp.put("weightTrend", weightTrend);
        resp.put("programs", support.programViews());
        resp.put("credits", credits);
        resp.put("month", month);
        return resp;
    }

    @Transactional
    public Map<String, Object> addBodyLog(VoltMember member, Map<String, Object> input) {
        Integer heightInput = support.integer(input == null ? null : input.get("heightCm"));
        int heightCm = heightInput == null
                ? (member.getHeightCm() == null ? 0 : member.getHeightCm())
                : heightInput;
        BigDecimal weightValue = support.decimal(input == null ? null : input.get("weightKg"));
        if (weightValue == null) {
            throw ApiException.validation("请输入体重。", Map.of("weightKg", "请输入体重"));
        }
        if (heightCm < VoltContract.HEIGHT_MIN_CM || heightCm > VoltContract.HEIGHT_MAX_CM) {
            String message = "身高需在 " + VoltContract.HEIGHT_MIN_CM + "-" + VoltContract.HEIGHT_MAX_CM + " cm 之间。";
            throw ApiException.validation(message, Map.of("heightCm", message));
        }
        if (weightValue.compareTo(BigDecimal.valueOf(VoltContract.WEIGHT_MIN_KG)) < 0
                || weightValue.compareTo(BigDecimal.valueOf(VoltContract.WEIGHT_MAX_KG)) > 0) {
            String message = "体重需在 " + VoltContract.WEIGHT_MIN_KG + "-" + VoltContract.WEIGHT_MAX_KG + " kg 之间。";
            throw ApiException.validation(message, Map.of("weightKg", message));
        }
        LocalDate date = LocalDate.now();
        jdbcTemplate.update("UPDATE volt_member SET height_cm = ?, weight_kg = ? WHERE member_id = ?",
                heightCm, weightValue, member.getMemberId());
        jdbcTemplate.update(
                "INSERT INTO volt_body_log (body_log_id, member_id, log_date, height_cm, weight_kg, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE height_cm = VALUES(height_cm), "
                        + "weight_kg = VALUES(weight_kg), created_at = NOW()",
                VoltContract.uniqueId("BODY"),
                member.getMemberId(), java.sql.Date.valueOf(date), heightCm, weightValue);
        VoltMember fresh = memberMapper.findById(member.getMemberId());
        Map<String, Object> bodyLog = support.bodyLogs(member.getMemberId()).stream()
                .filter(item -> String.valueOf(item.get("date")).equals(date.toString()))
                .findFirst().orElse(null);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("member", viewService.memberView(fresh));
        resp.put("bodyLog", bodyLog);
        resp.put("logs", support.bodyLogs(member.getMemberId()));
        return resp;
    }

    private String normalizeMonth(String monthText, LocalDate today) {
        if (monthText == null || monthText.isBlank()) {
            return today.format(VoltMiniSupport.YYYY_MM);
        }
        try {
            return YearMonth.parse(monthText.trim()).toString();
        } catch (DateTimeParseException ex) {
            throw ApiException.validation("月份格式不正确。", Map.of("month", "月份格式应为 YYYY-MM"));
        }
    }
}
