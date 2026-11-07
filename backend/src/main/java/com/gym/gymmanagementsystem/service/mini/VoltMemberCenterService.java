package com.gym.gymmanagementsystem.service.mini;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.dto.CreditLedgerEntry;
import com.gym.gymmanagementsystem.dto.CreditLedgerView;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltSessionMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;
import com.gym.gymmanagementsystem.service.VoltCardOrderService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页 / 我的 / 预约台账 / 消息 / 邀请 / 次卡流水。
 */
@Service
public class VoltMemberCenterService {

    private final VoltMiniSupport support;
    private final JdbcTemplate jdbcTemplate;
    private final VoltCatalogMapper catalogMapper;
    private final VoltSessionMapper sessionMapper;
    private final VoltViewService viewService;
    private final VoltCardOrderService cardOrderService;

    @Value("${volt.demo.pay-enabled:false}")
    private boolean demoPayEnabled;

    public VoltMemberCenterService(VoltMiniSupport support,
                                   JdbcTemplate jdbcTemplate,
                                   VoltCatalogMapper catalogMapper,
                                   VoltSessionMapper sessionMapper,
                                   VoltViewService viewService,
                                   VoltCardOrderService cardOrderService) {
        this.support = support;
        this.jdbcTemplate = jdbcTemplate;
        this.catalogMapper = catalogMapper;
        this.sessionMapper = sessionMapper;
        this.viewService = viewService;
        this.cardOrderService = cardOrderService;
    }

    public Map<String, Object> home(VoltMember member) {
        member = cardOrderService.claimForMember(member);
        support.expireLocks(member.getMemberId());
        List<Booking> active = support.activeBookings(member);
        LocalDate today = LocalDate.now();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("today", today.toString());
        resp.put("member", viewService.memberView(member));
        resp.put("stats", support.stats(member.getMemberId()));
        resp.put("nextBooking", support.nextBooking(active));
        resp.put("todayBooking", support.todayBooking(active));
        resp.put("week", support.week(member));
        resp.put("programs", support.programViews());
        resp.put("venues", support.venueViews(sessionMapper.findBetween(today, today)));
        resp.put("brand", support.brand());
        resp.put("messagesUnread", support.unreadMessages(member.getMemberId()));
        resp.put("subscriptions", support.subscriptions(member.getMemberId()));
        resp.put("cardOrders", cardOrderService.memberOrders(member));
        resp.put("trial", support.trialInfo(member));
        resp.put("demoPayEnabled", demoPayEnabled);
        return resp;
    }

    public Map<String, Object> account(VoltMember member) {
        member = cardOrderService.claimForMember(member);
        support.expireLocks(member.getMemberId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("member", viewService.memberView(member));
        resp.put("stats", support.stats(member.getMemberId()));
        resp.put("brand", support.brand());
        resp.put("plans", support.planViews());
        resp.put("subscriptions", support.subscriptions(member.getMemberId()));
        resp.put("messagesUnread", support.unreadMessages(member.getMemberId()));
        resp.put("invites", inviteSummary(member));
        // 「我的」只需要数量，不组装完整预约视图；避免一次请求触发几十条场次/课程查询。
        resp.put("counts", bookingCounts(member));
        resp.put("nextBooking", support.nextBooking(support.activeBookings(member)));
        resp.put("venues", support.venueViews(sessionMapper.findBetween(LocalDate.now(), LocalDate.now())));
        resp.put("cardOrders", cardOrderService.memberOrders(member));
        resp.put("trial", support.trialInfo(member));
        resp.put("demoPayEnabled", demoPayEnabled);
        return resp;
    }

    private Map<String, Object> bookingCounts(VoltMember member) {
        LocalDate today = LocalDate.now();
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_booking WHERE member_id = ? OR (member_id IS NULL AND member_phone = ?)",
                Integer.class, member.getMemberId(), member.getPhone());
        Integer upcoming = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_booking b JOIN volt_session s ON s.session_id = b.session_id "
                        + "WHERE (b.member_id = ? OR (b.member_id IS NULL AND b.member_phone = ?)) "
                        + "AND b.status IN ('locked', 'confirmed') "
                        + "AND (s.session_date > ? OR (s.session_date = ? AND s.start_time > ?))",
                Integer.class, member.getMemberId(), member.getPhone(), today, today, LocalTime.now());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("upcoming", upcoming == null ? 0 : upcoming);
        counts.put("waitlist", support.waitlistRows(member.getMemberId()).size());
        counts.put("history", Math.max((total == null ? 0 : total) - (upcoming == null ? 0 : upcoming), 0));
        return counts;
    }

    public Map<String, Object> bookings(VoltMember member) {
        support.expireLocks(member.getMemberId());
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> upcoming = new ArrayList<>();
        List<Map<String, Object>> history = new ArrayList<>();
        for (Booking booking : support.allBookings(member)) {
            VoltSession session = sessionMapper.findById(booking.getSessionId());
            boolean future = session != null
                    && session.getSessionDate().atTime(session.getStartTime()).plusMinutes(30).isAfter(now);
            boolean active = "locked".equals(booking.getStatus()) || "confirmed".equals(booking.getStatus());
            Map<String, Object> view = viewService.bookingView(booking);
            if (future && active) {
                upcoming.add(view);
            } else {
                history.add(view);
            }
        }
        upcoming.sort((a, b) -> String.valueOf(a.get("date") + " " + a.get("time"))
                .compareTo(String.valueOf(b.get("date") + " " + b.get("time"))));

        List<Map<String, Object>> waitlists = new ArrayList<>();
        for (Map<String, Object> row : support.waitlistRows(member.getMemberId())) {
            VoltSession session = sessionMapper.findById(String.valueOf(row.get("sessionId")));
            if (session == null) {
                continue;
            }
            waitlists.add(support.waitlistResult(session, row));
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("upcoming", upcoming.size());
        counts.put("waitlist", waitlists.size());
        counts.put("history", history.size());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("upcoming", upcoming);
        resp.put("waitlists", waitlists);
        resp.put("history", history);
        resp.put("counts", counts);
        resp.put("member", viewService.memberView(member));
        return resp;
    }

    public Map<String, Object> messages(VoltMember member) {
        List<Map<String, Object>> messages = jdbcTemplate.query(
                "SELECT message_id AS id, msg_type AS type, title, body, action, read_flag, created_at "
                        + "FROM volt_message WHERE member_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("id", rs.getString("id"));
                    message.put("type", rs.getString("type"));
                    message.put("title", rs.getString("title"));
                    message.put("body", rs.getString("body"));
                    message.put("action", rs.getString("action"));
                    message.put("read", rs.getInt("read_flag") == 1);
                    message.put("createdAt", rs.getLong("created_at"));
                    return message;
                }, member.getMemberId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("messages", messages);
        resp.put("unread", support.unreadMessages(member.getMemberId()));
        resp.put("subscriptions", support.subscriptions(member.getMemberId()));
        return resp;
    }

    public Map<String, Object> markMessageRead(VoltMember member, String messageId) {
        if ("all".equals(messageId)) {
            jdbcTemplate.update("UPDATE volt_message SET read_flag = 1 WHERE member_id = ?", member.getMemberId());
        } else {
            jdbcTemplate.update("UPDATE volt_message SET read_flag = 1 WHERE message_id = ? AND member_id = ?",
                    messageId, member.getMemberId());
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("unread", support.unreadMessages(member.getMemberId()));
        return resp;
    }

    public Map<String, Object> updateSubscriptions(VoltMember member, Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new ApiException(400, "BAD_KEY", "没有需要更新的订阅项。");
        }
        support.ensureSubscription(member.getMemberId());
        boolean updated = false;
        // 小程序当前发送 { key, value }；官网/新版按契约发送布尔对象 { classReminder: false }。
        if (input.containsKey("key")) {
            updated = updateSubscription(member.getMemberId(), support.text(input.get("key")),
                    Boolean.parseBoolean(String.valueOf(input.get("value"))));
        } else {
            if (input.containsKey("classReminder")) {
                updated |= updateSubscription(member.getMemberId(), "classReminder",
                        Boolean.parseBoolean(String.valueOf(input.get("classReminder"))));
            }
            if (input.containsKey("classChange")) {
                updated |= updateSubscription(member.getMemberId(), "classChange",
                        Boolean.parseBoolean(String.valueOf(input.get("classChange"))));
            }
            if (input.containsKey("waitlist")) {
                updated |= updateSubscription(member.getMemberId(), "waitlist",
                        Boolean.parseBoolean(String.valueOf(input.get("waitlist"))));
            }
        }
        if (!updated) {
            throw new ApiException(400, "BAD_KEY", "未知的订阅类型。");
        }
        return support.subscriptions(member.getMemberId());
    }

    private boolean updateSubscription(String memberId, String key, boolean value) {
        String column = switch (key == null ? "" : key) {
            case "classReminder" -> "class_reminder";
            case "classChange" -> "class_change";
            case "waitlist" -> "waitlist";
            default -> null;
        };
        if (column == null) {
            return false;
        }
        jdbcTemplate.update("UPDATE volt_subscription SET " + column + " = ? WHERE member_id = ?",
                value ? 1 : 0, memberId);
        return true;
    }

    public Map<String, Object> invite(String ref, String programNo, VoltMember member) {
        Program program = programNo == null ? null : catalogMapper.findProgram(programNo);
        if (program == null) {
            List<Program> programs = catalogMapper.findPrograms();
            program = programs.isEmpty() ? null : programs.get(0);
        }
        if (program == null) {
            throw ApiException.notFound("邀请课程不存在。");
        }
        String inviter = null;
        if (ref != null && !ref.isBlank()) {
            inviter = jdbcTemplate.query(
                    "SELECT nickname FROM volt_member WHERE referral_code = ? LIMIT 1",
                    (rs, rowNum) -> rs.getString(1), ref)
                    .stream().findFirst().orElse(null);
        } else if (member != null) {
            inviter = member.getNickname();
        }
        String referralCode = ref != null && !ref.isBlank()
                ? ref
                : member != null ? member.getReferralCode() : "";
        List<String> benefits = List.of(
                program.getProgramName() + "体验课 1 节",
                "InBody 体测 1 次",
                "教练 1 对 1 训练建议",
                "三店通用 · 不办年卡");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("inviterName", inviter == null ? "一位 VOLT 会员" : inviter);
        resp.put("isSelf", member != null && referralCode.equals(member.getReferralCode()));
        resp.put("program", viewService.programView(program));
        resp.put("trialPrice", VoltMiniSupport.DEMO_TRIAL_PRICE);
        resp.put("benefits", benefits);
        resp.put("referralCode", referralCode);
        resp.put("brand", support.brand());
        return resp;
    }

    public Map<String, Object> inviteMe(VoltMember member) {
        Map<String, Object> resp = inviteSummary(member);
        resp.put("opens", 0);
        resp.put("referralCode", member.getReferralCode());
        return resp;
    }

    public Map<String, Object> acceptInvite(String ref, VoltMember member) {
        String code = support.text(ref);
        if (code == null) {
            throw ApiException.validation("邀请码不能为空。", Map.of("ref", "邀请码不能为空"));
        }
        String inviterId = jdbcTemplate.query(
                "SELECT member_id FROM volt_member WHERE referral_code = ? LIMIT 1",
                (rs, rowNum) -> rs.getString(1), code)
                .stream().findFirst().orElse(null);
        if (inviterId == null) {
            throw ApiException.notFound("邀请码不存在或已失效。");
        }
        if (inviterId.equals(member.getMemberId())) {
            throw ApiException.validation("不能绑定自己的邀请。", Map.of("ref", "不能绑定自己的邀请"));
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM volt_invite WHERE invitee_member_id = ?",
                Integer.class, member.getMemberId());
        Map<String, Object> resp = new LinkedHashMap<>();
        if (exists != null && exists > 0) {
            resp.put("bound", true);
            resp.put("already", true);
            return resp;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO volt_invite (invite_id, ref_code, inviter_member_id, invitee_member_id, "
                            + "status, created_at) VALUES (?, ?, ?, ?, 'registered', NOW())",
                    VoltContract.uniqueId("IV"), code, inviterId, member.getMemberId());
        } catch (DuplicateKeyException ex) {
            resp.put("bound", true);
            resp.put("already", true);
            return resp;
        }
        resp.put("bound", true);
        resp.put("already", false);
        return resp;
    }

    public CreditLedgerView creditLedger(VoltMember member) {
        List<CreditLedgerEntry> entries = jdbcTemplate.query(
                "SELECT ledger_id, booking_id, record_id, change_amount, balance_after, reason, note, "
                        + "DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS created_at "
                        + "FROM volt_credit_ledger WHERE member_id = ? ORDER BY created_at DESC, ledger_id DESC",
                (rs, rowNum) -> {
                    CreditLedgerEntry entry = new CreditLedgerEntry();
                    entry.setId(rs.getString("ledger_id"));
                    entry.setBookingId(rs.getString("booking_id"));
                    entry.setRecordId(rs.getString("record_id"));
                    entry.setChangeAmount(rs.getInt("change_amount"));
                    entry.setBalanceAfter(rs.getInt("balance_after"));
                    entry.setReason(rs.getString("reason"));
                    entry.setNote(rs.getString("note"));
                    entry.setCreatedAt(rs.getString("created_at"));
                    return entry;
                }, member.getMemberId());
        CreditLedgerView resp = new CreditLedgerView();
        resp.setEntries(entries);
        resp.setCredits(member.getCredits());
        return resp;
    }

    private Map<String, Object> inviteSummary(VoltMember member) {
        List<Map<String, Object>> records = jdbcTemplate.query(
                "SELECT m.nickname AS name, DATE_FORMAT(i.created_at, '%Y-%m-%d') AS date, i.status "
                        + "FROM volt_invite i JOIN volt_member m ON m.member_id = i.invitee_member_id "
                        + "WHERE i.inviter_member_id = ? ORDER BY i.created_at DESC LIMIT 20",
                (rs, rowNum) -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("name", rs.getString("name"));
                    record.put("date", rs.getString("date"));
                    record.put("status", "trial".equals(rs.getString("status")) ? "已体验" : "已注册");
                    return record;
                }, member.getMemberId());
        boolean demoFallback = false;
        if (records.isEmpty() && "MBR-DEMO".equals(member.getMemberId())) {
            demoFallback = true;
            LocalDate today = LocalDate.now();
            records.add(inviteRecord("小林", today.minusDays(6), "已体验"));
            records.add(inviteRecord("Momo", today.minusDays(19), "已体验"));
            records.add(inviteRecord("阿哲", today.minusDays(2), "已注册"));
        }
        int invitedCount = demoFallback ? 2 : support.count(
                "SELECT COUNT(*) FROM volt_invite WHERE inviter_member_id = ?", member.getMemberId());
        int trialCount = demoFallback ? 1 : support.count(
                "SELECT COUNT(*) FROM volt_invite WHERE inviter_member_id = ? AND status = 'trial'",
                member.getMemberId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", member.getReferralCode());
        resp.put("invitedCount", invitedCount);
        resp.put("trialCount", trialCount);
        resp.put("records", records);
        return resp;
    }

    private Map<String, Object> inviteRecord(String name, LocalDate date, String status) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", name);
        record.put("date", date.toString());
        record.put("status", status);
        return record;
    }
}
