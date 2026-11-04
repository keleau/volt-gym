package com.gym.gymmanagementsystem.seed;

import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 启动时把库表、静态目录数据、场次与本周挑战补齐，脚本幂等，可重复执行。
 * 场次覆盖今天起 60 天，与预约窗口一致。
 */
@Component
public class VoltDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VoltDataSeeder.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SESSION_DAYS = 60;
    private static final int CAPACITY = 16;

    private static final String DEMO_MEMBER_ID = "MBR-DEMO";
    private static final String DEMO_PHONE = "13800006688";
    private static final String[] DEMO_TIMES = {"19:30", "07:30", "20:45", "12:20"};
    private static final int[] DEMO_OFFSETS = {2, 4, 7, 9, 12, 14, 17, 20, 23, 27, 30, 33, 37, 40, 44, 47, 51, 54};

    /** 每个课程每天的两节课；顺序即场次序号。 */
    private static final Map<String, List<String[]>> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("P01", List.<String[]>of(new String[]{"12:00", "13:00"}, new String[]{"19:30", "20:30"}));
        SLOTS.put("P02", List.<String[]>of(new String[]{"10:00", "10:45"}, new String[]{"18:30", "19:15"}));
        SLOTS.put("P03", List.<String[]>of(new String[]{"08:00", "09:00"}, new String[]{"19:00", "20:00"}));
        SLOTS.put("P04", List.<String[]>of(new String[]{"12:30", "13:20"}, new String[]{"20:00", "20:50"}));
        SLOTS.put("P05", List.<String[]>of(new String[]{"09:00", "10:00"}, new String[]{"21:00", "22:00"}));
        SLOTS.put("P06", List.<String[]>of(new String[]{"08:00", "08:55"}, new String[]{"15:00", "15:55"}));
    }

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final VoltCatalogMapper catalogMapper;

    public VoltDataSeeder(DataSource dataSource, JdbcTemplate jdbcTemplate, VoltCatalogMapper catalogMapper) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.catalogMapper = catalogMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 脚本文件是 UTF-8，必须显式指定编码：按平台默认编码读会把中文写坏
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, utf8("db/schema.sql"));
            ScriptUtils.executeSqlScript(connection, utf8("db/seed.sql"));
        } catch (SQLException ex) {
            throw new IllegalStateException("初始化 VOLT 库表失败", ex);
        }
        ensureMemberSchema();
        // 旧库的 PLAN-01 还叫“首次接入包”，与官网体验课文案对齐。
        jdbcTemplate.update("UPDATE volt_plan SET plan_name = '首次体验课', plan_en = 'FIRST TRIAL', "
                + "unit_text = '单次' WHERE plan_code = 'PLAN-01'");
        int sessions = seedSessions();
        seedChallenge();
        seedDemoData();
        log.info("VOLT 种子数据就绪：场次 {} 条，演示会员 {} / {}", sessions, DEMO_MEMBER_ID, DEMO_PHONE);
    }

    private EncodedResource utf8(String path) {
        return new EncodedResource(new ClassPathResource(path), StandardCharsets.UTF_8);
    }

    /** 兼容已存在的旧库：schema.sql 只会建新表，已有表需要补列。 */
    private void ensureMemberSchema() {
        ensureColumn("volt_member", "avatar_url",
                "avatar_url VARCHAR(255) NOT NULL DEFAULT '' AFTER phone_bound");
        ensureColumn("volt_member", "openid", "openid VARCHAR(64) NULL AFTER avatar_url");
        ensureColumn("volt_member", "unionid", "unionid VARCHAR(64) NULL AFTER openid");
        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'volt_member' AND column_name = 'phone'",
                String.class);
        if ("NO".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE volt_member MODIFY COLUMN phone VARCHAR(20) NULL");
        }
        ensureUniqueIndex("volt_member", "uk_member_openid", "openid");
        // 旧库可能已有重复/空邀请码，不在这里强行加唯一索引；新库由 schema.sql 保证。
    }

    private void ensureColumn(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
        }
    }

    private void ensureUniqueIndex(String table, String indexName, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD UNIQUE KEY " + indexName + " (" + column + ")");
        }
    }

    private int seedSessions() {
        List<Venue> venues = catalogMapper.findVenues();
        List<Program> programs = catalogMapper.findPrograms();
        Map<String, String> coachOfProgram = new LinkedHashMap<>();
        for (Coach coach : catalogMapper.findCoaches()) {
            if (coach.getProgramNo() != null && !coach.getProgramNo().isBlank()) {
                coachOfProgram.put(coach.getProgramNo(), coach.getCoachId());
            }
        }
        if (venues.isEmpty() || programs.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        List<Object[]> batch = new ArrayList<>();
        int pairIndex = 0;
        for (Venue venue : venues) {
            for (Program program : programs) {
                String coachId = coachOfProgram.getOrDefault(program.getProgramNo(), "C01");
                List<String[]> slots = SLOTS.getOrDefault(program.getProgramNo(),
                        List.<String[]>of(new String[]{"19:30", "20:30"}));
                for (int dayIndex = 0; dayIndex < SESSION_DAYS; dayIndex++) {
                    LocalDate date = today.plusDays(dayIndex);
                    for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                        String[] slot = slots.get(slotIndex);
                        // 场次编码按（门店 × 课程 × 时段）编号，保证同一天的 id 不撞号
                        String sessionCode = String.format("S%03d", pairIndex * slots.size() + slotIndex + 1);
                        String sessionId = sessionCode + "-" + date.format(COMPACT);
                        int booked = seededBooked(venue.getVenueCode(), program.getProgramNo(), dayIndex, pairIndex, slotIndex);
                        batch.add(new Object[]{
                                sessionId,
                                sessionCode,
                                java.sql.Date.valueOf(date),
                                LocalTime.parse(slot[0]),
                                LocalTime.parse(slot[1]),
                                venue.getVenueCode(),
                                program.getProgramNo(),
                                coachId,
                                CAPACITY,
                                booked,
                                "open"
                        });
                    }
                }
                pairIndex++;
            }
        }

        int[] result = jdbcTemplate.batchUpdate(
                "INSERT IGNORE INTO volt_session (session_id, session_code, session_date, start_time, end_time, "
                        + "venue_code, program_no, coach_id, capacity, booked, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
        int inserted = 0;
        for (int affected : result) {
            if (affected > 0) {
                inserted++;
            }
        }
        return inserted;
    }

    /** 番禺 + 深度恢复固定满员，用于验证 SLOT_FULL 分支；其余按日期散开。 */
    private int seededBooked(String venueCode, String programNo, int dayIndex, int pairIndex, int slotIndex) {
        if ("V-03".equals(venueCode) && "P06".equals(programNo)) {
            return CAPACITY;
        }
        int raw = (dayIndex * 7 + pairIndex * 3 + slotIndex * 11) % 17;
        return Math.min(raw, CAPACITY);
    }

    private void seedChallenge() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        jdbcTemplate.update(
                "INSERT INTO volt_challenge (challenge_id, title, desc_text, target_sessions, days, joined_count, "
                        + "percent, week_start, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), desc_text = VALUES(desc_text), "
                        + "target_sessions = VALUES(target_sessions), days = VALUES(days), percent = VALUES(percent), "
                        + "week_start = VALUES(week_start), status = 'active'",
                "CH-3X-WEEK",
                "本周练满 3 次",
                "完成后解锁「电流不断」限定贴纸，月底门店自取。",
                3,
                7,
                128,
                68,
                java.sql.Date.valueOf(monday),
                "active");
        jdbcTemplate.update("DELETE FROM volt_token WHERE expires_at < ?", System.currentTimeMillis());
        jdbcTemplate.update("DELETE FROM volt_otp WHERE expires_at < ?", System.currentTimeMillis());
        jdbcTemplate.update("DELETE FROM volt_idempotency WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
    }

    private void seedDemoData() {
        seedDemoMember();
        seedDemoRecords();
        seedDemoBodyLogs();
        seedDemoMessages();
        jdbcTemplate.update("INSERT IGNORE INTO volt_subscription (member_id) VALUES (?)", DEMO_MEMBER_ID);
        seedDemoNextBooking();
    }

    private void seedDemoMember() {
        LocalDate today = LocalDate.now();
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_member WHERE member_id = ?", Integer.class, DEMO_MEMBER_ID);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE volt_member SET credits = 8, credits_total = 10, plan_code = 'PLAN-03', "
                            + "plan_name = '10 次充电卡', expires_at = ?, "
                            + "goal = CASE WHEN goal IS NULL OR goal = '' THEN '增肌 · 体能' ELSE goal END, "
                            + "height_cm = CASE WHEN height_cm = 0 THEN 178 ELSE height_cm END, "
                            + "weight_kg = CASE WHEN weight_kg = 0 THEN 74.2 ELSE weight_kg END "
                            + "WHERE member_id = ? AND credits_total = 0",
                    java.sql.Date.valueOf(today.plusDays(180)), DEMO_MEMBER_ID);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO volt_member (member_id, member_no, nickname, phone, phone_bound, goal, height_cm, "
                        + "weight_kg, credits, credits_total, plan_code, plan_name, expires_at, tier, referral_code, joined_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                DEMO_MEMBER_ID, "M0000002049", "阿电", DEMO_PHONE, 1, "增肌 · 体能", 178, 74.2,
                8, 10, "PLAN-03", "10 次充电卡", java.sql.Date.valueOf(today.plusDays(180)),
                "VOLT MEMBER", "VLT8K3", java.sql.Date.valueOf(today.minusDays(120)));
    }

    private void seedDemoRecords() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_record WHERE member_id = ?", Integer.class, DEMO_MEMBER_ID);
        if (count != null && count > 0) {
            return;
        }
        List<Program> programs = catalogMapper.findPrograms();
        List<Venue> venues = catalogMapper.findVenues();
        Map<String, Coach> coaches = new LinkedHashMap<>();
        for (Coach coach : catalogMapper.findCoaches()) {
            coaches.put(coach.getProgramNo(), coach);
        }
        if (programs.isEmpty() || venues.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (int i = 0; i < DEMO_OFFSETS.length; i++) {
            LocalDate date = today.minusDays(DEMO_OFFSETS[i]);
            LocalTime time = LocalTime.parse(DEMO_TIMES[i % DEMO_TIMES.length]);
            Program program = programs.get(i % programs.size());
            Venue venue = venues.get(i % venues.size());
            Coach coach = coaches.get(program.getProgramNo());
            long checkedAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            jdbcTemplate.update(
                    "INSERT IGNORE INTO volt_record (record_id, member_id, mode, source, record_date, record_time, "
                            + "start_time, program_no, program_name, program_en, coach_id, coach_name, venue_code, "
                            + "venue_name, duration_min, kcal, checked_at, created_at) "
                            + "VALUES (?, ?, 'class', 'scan', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    String.format("REC-DEMO-%02d", i + 1),
                    DEMO_MEMBER_ID,
                    java.sql.Date.valueOf(date),
                    time,
                    time,
                    program.getProgramNo(),
                    program.getProgramName(),
                    program.getProgramEn(),
                    coach == null ? "" : coach.getCoachId(),
                    coach == null ? "场馆教练" : coach.getCoachName(),
                    venue.getVenueCode(),
                    venue.getVenueName(),
                    program.getDurationMin(),
                    program.getKcal(),
                    checkedAt,
                    LocalDateTime.of(date, time));
        }
    }

    private void seedDemoBodyLogs() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_body_log WHERE member_id = ?", Integer.class, DEMO_MEMBER_ID);
        if (count != null && count > 0) {
            return;
        }
        double[] weights = {76.4, 76.0, 75.6, 75.4, 75.0, 74.8, 74.5, 74.2};
        LocalDate today = LocalDate.now();
        for (int i = 0; i < weights.length; i++) {
            LocalDate date = today.minusWeeks(weights.length - 1 - i);
            jdbcTemplate.update(
                    "INSERT IGNORE INTO volt_body_log (body_log_id, member_id, log_date, height_cm, weight_kg, created_at) "
                            + "VALUES (?, ?, ?, 178, ?, ?)",
                    String.format("BODY-DEMO-%02d", i + 1),
                    DEMO_MEMBER_ID,
                    java.sql.Date.valueOf(date),
                    weights[i],
                    LocalDateTime.of(date, LocalTime.of(8, 0)));
        }
    }

    private void seedDemoMessages() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_message WHERE member_id = ?", Integer.class, DEMO_MEMBER_ID);
        if (count != null && count > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Object[][] data = {
                {"reminder", "预约成功 · 高压燃脂", "明天 19:30 天河工场，开课前 60 分钟提醒你。", "/pages/bookings/index", 0, 1},
                {"checkin", "训练记录已更新", "上一次进场记录已写入训练档案，消耗 620 kcal。", "/pages/archive/index", 0, 26},
                {"system", "次卡余额提醒", "你的 10 次充电卡还剩 8 次，有效期至年底。", "/pages/pass/index", 1, 50},
                {"change", "课程变更 · 光影搏击", "本周三晚课程由阿 K 继续带练，场地不变。", "/pages/schedule/index", 1, 74},
                {"waitlist", "候补已退出", "你已退出深度恢复的候补队列。", "/pages/bookings/index?tab=waitlist", 1, 96}
        };
        for (int i = 0; i < data.length; i++) {
            Object[] item = data[i];
            jdbcTemplate.update(
                    "INSERT INTO volt_message (message_id, member_id, msg_type, title, body, action, read_flag, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    String.format("MSG-DEMO-%02d", i + 1),
                    DEMO_MEMBER_ID,
                    item[0], item[1], item[2], item[3], item[4],
                    now - ((Number) item[5]).longValue() * 3600_000L);
        }
    }

    private void seedDemoNextBooking() {
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_booking b JOIN volt_session s ON s.session_id = b.session_id "
                        + "WHERE b.member_id = ? AND b.status IN ('locked', 'confirmed') "
                        + "AND (s.session_date > CURDATE() OR (s.session_date = CURDATE() AND s.start_time > CURTIME()))",
                Integer.class, DEMO_MEMBER_ID);
        if (active != null && active > 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.session_id AS sid, s.booked AS booked, "
                        + "v.venue_name AS venue_name, p.program_name AS program_name, c.coach_name AS coach_name "
                        + "FROM volt_session s "
                        + "JOIN volt_venue v ON v.venue_code = s.venue_code "
                        + "JOIN volt_program p ON p.program_no = s.program_no "
                        + "LEFT JOIN volt_coach c ON c.coach_id = s.coach_id "
                        + "WHERE s.status = 'open' AND s.booked < s.capacity "
                        + "AND (s.session_date > CURDATE() OR (s.session_date = CURDATE() AND s.start_time > CURTIME())) "
                        + "ORDER BY s.session_date, s.start_time LIMIT 1");
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> session = rows.get(0);
        String sessionId = String.valueOf(session.get("sid"));
        int updated = jdbcTemplate.update(
                "UPDATE volt_session SET booked = booked + 1 WHERE session_id = ? AND booked < capacity", sessionId);
        if (updated == 0) {
            return;
        }
        Map<String, Object> member = jdbcTemplate.queryForMap(
                "SELECT nickname, phone, plan_code, plan_name FROM volt_member WHERE member_id = ?", DEMO_MEMBER_ID);
        String compact = LocalDate.now().format(COMPACT);
        String bookingId = "BK-DEMO-" + compact + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
        String orderNo = "VDEMO" + compact + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        jdbcTemplate.update(
                "INSERT INTO volt_booking (booking_id, order_no, entry_code, member_id, session_id, status, seat_no, "
                        + "lock_expires_at, reminder_opt_in, source, plan_code, plan_name, amount, member_name, member_phone, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'confirmed', ?, NULL, 1, 'wx', ?, ?, 0, ?, ?, ?)",
                bookingId, orderNo, orderNo, DEMO_MEMBER_ID, sessionId,
                ((Number) session.get("booked")).intValue() + 1,
                member.get("plan_code"), member.get("plan_name"),
                member.get("nickname"), member.get("phone"), LocalDateTime.now());
    }
}
