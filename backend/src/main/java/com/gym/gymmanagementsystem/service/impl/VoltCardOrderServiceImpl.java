package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltCardOrderMapper;
import com.gym.gymmanagementsystem.mapper.VoltCatalogMapper;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.CardOrder;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltCardOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoltCardOrderServiceImpl implements VoltCardOrderService {

    /** 官网可登记的卡：PLAN-02 = 单次，PLAN-03 = 10 次；PLAN-01 是体验课，不走购卡。 */
    private static final Map<String, int[]> CARD_PLANS = Map.of(
            "PLAN-02", new int[]{1, 30},
            "PLAN-03", new int[]{10, 180});

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final VoltCardOrderMapper cardOrderMapper;
    private final VoltCatalogMapper catalogMapper;
    private final VoltMemberMapper memberMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${volt.demo.pay-enabled:false}")
    private boolean demoPayEnabled;

    public VoltCardOrderServiceImpl(VoltCardOrderMapper cardOrderMapper,
                                    VoltCatalogMapper catalogMapper,
                                    VoltMemberMapper memberMapper,
                                    JdbcTemplate jdbcTemplate) {
        this.cardOrderMapper = cardOrderMapper;
        this.catalogMapper = catalogMapper;
        this.memberMapper = memberMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> input, VoltMember member) {
        String name = text(input.get("name"));
        String phone = text(input.get("phone"));
        String planCode = text(input.get("planCode"));
        String venueCode = text(input.get("venueCode"));
        String note = text(input.get("note"));

        if (member != null) {
            if (member.getNickname() != null && !member.getNickname().isBlank()) {
                name = member.getNickname();
            }
            if (member.getPhone() != null && !member.getPhone().isBlank()) {
                phone = member.getPhone();
            }
        }
        if (name == null) {
            throw ApiException.validation("请填写姓名。", Map.of("name", "请填写姓名"));
        }
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        if (note != null && note.length() > 200) {
            throw ApiException.validation("备注不能超过 200 字。", Map.of("note", "备注不能超过 200 字"));
        }

        Plan plan = planCode == null ? null : catalogMapper.findPlan(planCode);
        if (plan == null || !CARD_PLANS.containsKey(plan.getPlanCode())) {
            throw ApiException.validation("请选择可登记次卡。", Map.of("planCode", "请选择可登记次卡"));
        }
        Venue venue = venueCode == null ? null : catalogMapper.findVenue(venueCode);
        if (venueCode != null && venue == null) {
            throw ApiException.validation("门店不存在或已下线。", Map.of("venueCode", "门店不存在或已下线"));
        }
        if (cardOrderMapper.countPending(phone, planCode) > 0) {
            throw ApiException.conflict("DUPLICATE_CARD_ORDER", "该手机号已有同款待开卡登记，到店付款开卡即可。");
        }

        VoltMember bound = memberMapper.findByPhone(phone);
        CardOrder order = new CardOrder();
        order.setCardOrderId(VoltContract.uniqueId("CD"));
        order.setOrderNo(nextOrderNo());
        order.setMemberId(bound == null ? null : bound.getMemberId());
        order.setMemberName(name);
        order.setMemberPhone(phone);
        order.setPlanCode(plan.getPlanCode());
        order.setPlanName(plan.getPlanName());
        order.setAmount(plan.getPrice() == null ? 0 : plan.getPrice());
        order.setCredits(CARD_PLANS.get(plan.getPlanCode())[0]);
        order.setValidDays(CARD_PLANS.get(plan.getPlanCode())[1]);
        order.setVenueCode(venueCode == null ? "" : venueCode);
        order.setNote(note == null ? "" : note);
        order.setStatus("pending");
        order.setCreatedAt(LocalDateTime.now());
        cardOrderMapper.insert(order);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("order", view(order));
        resp.put("message", "登记成功，到店付款后由店员开卡。");
        return resp;
    }

    @Override
    @Transactional
    public List<Map<String, Object>> memberOrders(VoltMember member) {
        if (member == null || member.getPhone() == null || member.getPhone().isBlank()) {
            return List.of();
        }
        claimForMember(member);
        cardOrderMapper.attachMember(member.getMemberId(), member.getPhone());
        List<Map<String, Object>> views = new ArrayList<>();
        for (CardOrder order : cardOrderMapper.findByPhone(member.getPhone())) {
            views.add(view(order));
        }
        return views;
    }

    @Override
    public List<Map<String, Object>> staffOrders(String phone, String status) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (CardOrder order : cardOrderMapper.findForStaff(phone, status)) {
            views.add(view(order));
        }
        return views;
    }

    @Override
    @Transactional
    public Map<String, Object> activate(String orderNo, String staffName) {
        CardOrder order = orderNo == null ? null : cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw ApiException.notFound("购卡登记不存在。");
        }
        boolean already = !"pending".equals(order.getStatus());
        if (already && !"activated".equals(order.getStatus())) {
            throw ApiException.conflict("CARD_ORDER_CLOSED", "该登记已关闭，不能再开卡。");
        }
        if (!already) {
            cardOrderMapper.activate(orderNo, staffName == null || staffName.isBlank() ? "staff" : staffName);
            order = cardOrderMapper.findByOrderNo(orderNo);
        }

        // 购卡单的归属以手机号为准：手机号唯一，避免会员换绑后把权益发给旧 member_id。
        VoltMember member = memberMapper.findByPhone(order.getMemberPhone());
        if (member == null && order.getMemberId() != null && !order.getMemberId().isBlank()) {
            member = memberMapper.findById(order.getMemberId());
        }
        if (member != null) {
            claimForMember(member);
            order = cardOrderMapper.findByOrderNo(orderNo);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("already", already);
        resp.put("order", view(order));
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> demoPay(String orderNo, VoltMember member) {
        if (!demoPayEnabled) {
            throw new ApiException(403, "DEMO_PAY_DISABLED", "演示支付未开启。");
        }
        if (member == null || member.getPhone() == null || member.getPhone().isBlank()) {
            throw ApiException.unauthorized("请先登录并绑定手机号。");
        }
        CardOrder order = orderNo == null ? null : cardOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw ApiException.notFound("购卡登记不存在。");
        }
        if (!member.getPhone().equals(order.getMemberPhone())) {
            throw new ApiException(403, "FORBIDDEN", "这张购卡登记不属于当前手机号。");
        }
        Map<String, Object> resp = activate(orderNo, "demo-pay");
        resp.put("demo", true);
        return resp;
    }

    @Override
    @Transactional
    public VoltMember claimForMember(VoltMember member) {
        if (member == null || member.getPhone() == null || member.getPhone().isBlank()) {
            return member;
        }
        cardOrderMapper.attachMember(member.getMemberId(), member.getPhone());
        List<CardOrder> pending = cardOrderMapper.findUnappliedForPhone(member.getPhone());
        if (pending.isEmpty()) {
            return member;
        }

        LocalDate today = LocalDate.now();
        int currentCredits = member.getCredits() == null ? 0 : member.getCredits();
        int currentTotal = member.getCreditsTotal() == null ? 0 : member.getCreditsTotal();
        LocalDate currentExpiry = member.getExpiresAt();
        String planCode = member.getPlanCode();
        String planName = member.getPlanName();

        for (CardOrder order : pending) {
            int credits = order.getCredits() == null ? 0 : order.getCredits();
            int days = order.getValidDays() == null ? 0 : order.getValidDays();
            LocalDate base = currentExpiry != null && !currentExpiry.isBefore(today)
                    ? currentExpiry
                    : today;
            LocalDate expiry = base.plusDays(days);
            jdbcTemplate.update(
                    "UPDATE volt_member SET credits = credits + ?, credits_total = credits_total + ?, "
                            + "plan_code = ?, plan_name = ?, expires_at = ? WHERE member_id = ?",
                    credits, credits, order.getPlanCode(), order.getPlanName(),
                    expiry, member.getMemberId());
            currentCredits += credits;
            currentTotal += credits;
            currentExpiry = expiry;
            planCode = order.getPlanCode();
            planName = order.getPlanName();
            jdbcTemplate.update(
                    "INSERT INTO volt_credit_ledger (ledger_id, member_id, booking_id, record_id, "
                            + "change_amount, balance_after, reason, note, created_at) "
                            + "VALUES (?, ?, NULL, NULL, ?, ?, 'CARD', ?, NOW())",
                    VoltContract.uniqueId("CL"), member.getMemberId(), credits, currentCredits,
                    "购卡开卡 " + order.getOrderNo());
            cardOrderMapper.markApplied(order.getCardOrderId(), member.getMemberId());
        }

        // 事务里 MyBatis 一级缓存可能拿到更新前对象，返回值直接用手上累计后的字段。
        member.setCredits(currentCredits);
        member.setCreditsTotal(currentTotal);
        member.setPlanCode(planCode);
        member.setPlanName(planName);
        member.setExpiresAt(currentExpiry);
        return member;
    }

    private Map<String, Object> view(CardOrder order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderNo", order.getOrderNo());
        view.put("name", order.getMemberName());
        view.put("phone", VoltContract.maskPhone(order.getMemberPhone()));
        view.put("planCode", order.getPlanCode());
        view.put("planName", order.getPlanName());
        view.put("amount", order.getAmount());
        view.put("credits", order.getCredits());
        view.put("validDays", order.getValidDays());
        view.put("venueCode", order.getVenueCode());
        if (order.getVenueCode() != null && !order.getVenueCode().isBlank()) {
            Venue venue = catalogMapper.findVenue(order.getVenueCode());
            view.put("venueName", venue == null ? order.getVenueCode() : venue.getVenueName());
        }
        view.put("note", order.getNote());
        view.put("status", order.getStatus());
        view.put("applied", order.getAppliedAt() != null);
        view.put("createdAt", format(order.getCreatedAt()));
        view.put("activatedAt", format(order.getActivatedAt()));
        return view;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }

    private String nextOrderNo() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "C" + VoltContract.compactDate(LocalDate.now())
                    + String.format("%05d", java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 100_000));
            if (cardOrderMapper.countByOrderNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成购卡登记号失败，请重试。");
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
