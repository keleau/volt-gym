package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltBookingMapper;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltOtp;
import com.gym.gymmanagementsystem.pojo.VoltToken;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltCardOrderService;
import com.gym.gymmanagementsystem.service.VoltViewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VoltAuthServiceImpl implements VoltAuthService {

    private static final Logger log = LoggerFactory.getLogger(VoltAuthServiceImpl.class);
    private static final String PURPOSE_LOGIN = "login";
    private static final String PURPOSE_BIND = "bind";
    private static final String DEMO_MEMBER_ID = "MBR-DEMO";
    private static final String DEMO_PHONE = "13800006688";
    private static final String DEMO_OPENID = "demo-openid";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoltMemberMapper memberMapper;
    private final VoltBookingMapper bookingMapper;
    private final VoltViewService viewService;
    private final VoltCardOrderService cardOrderService;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${volt.auth.expose-otp:false}")
    private boolean exposeOtp;

    @Value("${volt.wx.app-id:}")
    private String wxAppId;

    @Value("${volt.wx.app-secret:}")
    private String wxAppSecret;

    @Value("${volt.wx.demo-fallback:true}")
    private boolean wxDemoFallback;

    public VoltAuthServiceImpl(VoltMemberMapper memberMapper, VoltBookingMapper bookingMapper,
                               VoltViewService viewService, VoltCardOrderService cardOrderService,
                               JdbcTemplate jdbcTemplate) {
        this.memberMapper = memberMapper;
        this.bookingMapper = bookingMapper;
        this.viewService = viewService;
        this.cardOrderService = cardOrderService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> sendOtp(String phone) {
        return sendOtp(phone, PURPOSE_LOGIN);
    }

    @Override
    public Map<String, Object> sendBindOtp(VoltMember member, String phone) {
        if (member != null && Boolean.TRUE.equals(member.getPhoneBound())
                && member.getPhone() != null && !member.getPhone().isBlank()) {
            throw ApiException.conflict("PHONE_ALREADY_BOUND", "当前微信账号已绑定手机号。");
        }
        return sendOtp(phone, PURPOSE_BIND);
    }

    private Map<String, Object> sendOtp(String phone, String purpose) {
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        VoltOtp latest = memberMapper.findLatestOtp(phone, purpose);
        long now = System.currentTimeMillis();
        if (latest != null && latest.getCreatedAt() != null) {
            long sentAt = latest.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long elapsed = now - sentAt;
            if (elapsed < VoltContract.OTP_RESEND_COOLDOWN_SECONDS * 1000L) {
                long wait = (VoltContract.OTP_RESEND_COOLDOWN_SECONDS * 1000L - elapsed) / 1000L + 1;
                throw new ApiException(429, "RATE_LIMITED", "验证码发送过于频繁，请 " + wait + " 秒后再试。");
            }
        }

        VoltOtp otp = new VoltOtp();
        otp.setPhone(phone);
        otp.setCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        otp.setPurpose(purpose);
        otp.setExpiresAt(now + VoltContract.OTP_TTL_SECONDS * 1000L);
        otp.setConsumed(false);
        otp.setAttempts(0);
        otp.setCreatedAt(LocalDateTime.now());
        memberMapper.insertOtp(otp);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sent", true);
        resp.put("expiresIn", VoltContract.OTP_TTL_SECONDS);
        resp.put("cooldown", VoltContract.OTP_RESEND_COOLDOWN_SECONDS);
        resp.put("channel", "sms");
        if (exposeOtp) {
            resp.put("devCode", otp.getCode());
        }
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> verifyOtp(String phone, String code) {
        verifyOtpCode(phone, code, PURPOSE_LOGIN);

        VoltMember member = memberMapper.findByPhone(phone);
        if (member == null) {
            member = createMember(phone, null, null, null, null);
        }
        claimGuestRecords(member);
        member = cardOrderService.claimForMember(member);

        String token = issueToken(member.getMemberId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("member", viewService.memberView(member));
        return resp;
    }

    private void verifyOtpCode(String phone, String code, String purpose) {
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        if (VoltContract.isBlank(code)) {
            throw ApiException.validation("请输入验证码。", Map.of("code", "请输入验证码"));
        }

        VoltOtp otp = memberMapper.findLatestOtp(phone, purpose);
        long now = System.currentTimeMillis();
        if (otp == null || Boolean.TRUE.equals(otp.getConsumed()) || otp.getExpiresAt() == null || otp.getExpiresAt() < now) {
            throw ApiException.validation("验证码不正确或已过期。", Map.of("code", "验证码不正确或已过期"));
        }
        if (!otp.getCode().equals(code.trim())) {
            memberMapper.increaseOtpAttempts(otp.getId());
            int attempts = (otp.getAttempts() == null ? 0 : otp.getAttempts()) + 1;
            if (attempts >= VoltContract.OTP_MAX_ATTEMPTS) {
                memberMapper.consumeOtp(otp.getId());
                throw ApiException.validation("验证码错误次数过多，请重新获取。", Map.of("code", "验证码错误次数过多，请重新获取"));
            }
            throw ApiException.validation("验证码不正确或已过期。", Map.of("code", "验证码不正确或已过期"));
        }
        if (memberMapper.consumeOtpIfUnused(otp.getId()) == 0) {
            throw ApiException.validation("验证码已使用，请重新获取。", Map.of("code", "验证码已使用，请重新获取"));
        }
    }

    @Override
    @Transactional
    public Map<String, Object> loginWithWechat(Map<String, Object> input) {
        String code = text(input == null ? null : input.get("code"));
        if (code == null) {
            throw ApiException.validation("缺少微信登录 code。", Map.of("code", "缺少微信登录 code"));
        }
        Map<String, Object> profile = map(input.get("profile"));
        String nickname = text(profile.get("nickname"));
        String avatarUrl = text(profile.get("avatarUrl"));

        String openid = null;
        VoltMember member = null;
        if (hasWxConfig()) {
            Map<String, Object> session = null;
            try {
                session = fetchWxSession(code);
            } catch (Exception ex) {
                log.warn("微信 jscode2session 调用失败", ex);
                if (!wxDemoFallback) {
                    throw new ApiException(502, "WX_LOGIN_FAILED", "微信登录服务暂时不可用，请稍后再试。");
                }
            }
            if (session != null) {
                openid = text(session.get("openid"));
                if (openid == null) {
                    String message = text(session.get("errmsg"));
                    throw new ApiException(401, "WX_LOGIN_FAILED",
                            message == null ? "微信登录失败，请重试。" : message);
                }
                String unionid = text(session.get("unionid"));
                member = memberMapper.findByOpenid(openid);
                if (member == null) {
                    member = createMember(null, openid, unionid, nickname, avatarUrl);
                }
            }
        }
        if (member == null) {
            if (!wxDemoFallback) {
                throw new ApiException(503, "WX_LOGIN_UNAVAILABLE", "微信登录未配置，请联系管理员。");
            }
            log.warn("未配置 volt.wx.app-id/app-secret，wx.login 落到演示会员 {}", DEMO_MEMBER_ID);
            openid = DEMO_OPENID;
            member = memberMapper.findById(DEMO_MEMBER_ID);
            if (member == null) {
                member = memberMapper.findByPhone(DEMO_PHONE);
            }
            if (member == null) {
                member = createMember(DEMO_PHONE, null, null, "阿电", "");
            }
        }

        if (openid == null || openid.isBlank()) {
            openid = resolveOpenid(member);
        }
        ensureLinked(openid, member.getMemberId());
        String activeId = memberMapper.findActiveMemberId(openid);
        List<VoltMember> linked = memberMapper.findMembersByOpenid(openid);
        boolean activeLinked = false;
        for (VoltMember item : linked) {
            if (item.getMemberId().equals(activeId)) {
                activeLinked = true;
                break;
            }
        }
        if (activeId == null || !activeLinked) {
            activeId = linked.isEmpty() ? member.getMemberId() : linked.get(0).getMemberId();
            activate(openid, activeId);
        }
        member = memberMapper.findById(activeId);
        if (member == null) {
            throw ApiException.unauthorized("会员档案不存在，请重新登录。");
        }
        claimGuestRecords(member);
        member = cardOrderService.claimForMember(member);
        return sessionResponse(member, openid);
    }

    @Override
    @Transactional
    public Map<String, Object> linkPhone(VoltMember member, String phone, String code) {
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        if (Boolean.TRUE.equals(member.getPhoneBound())
                && member.getPhone() != null && !member.getPhone().isBlank()) {
            throw ApiException.conflict("PHONE_ALREADY_BOUND", "当前微信账号已绑定手机号。");
        }
        verifyOtpCode(phone, code, PURPOSE_BIND);

        VoltMember existing = memberMapper.findByPhone(phone);
        if (existing != null && !existing.getMemberId().equals(member.getMemberId())) {
            return mergeIntoPhoneMember(member, existing);
        }

        try {
            memberMapper.updatePhone(member.getMemberId(), phone);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已绑定其他会员。");
        }
        member.setPhone(phone);
        member.setPhoneBound(true);
        // 绑定后把同手机号的官网游客预约/报名认领到当前会员，认领幂等。
        claimGuestRecords(member);
        VoltMember fresh = cardOrderService.claimForMember(member);
        String openid = resolveOpenid(member);
        if (openid != null) {
            ensureLinked(openid, member.getMemberId());
            activate(openid, member.getMemberId());
        }
        VoltMember finalMember = fresh == null ? member : fresh;
        Map<String, Object> resp = sessionResponse(finalMember, openid);
        resp.put("merged", false);
        resp.put("audience", audienceView(finalMember));
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> members(VoltMember member) {
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        String openid = resolveOpenid(member);
        List<VoltMember> linked = openid == null ? List.of(member) : memberMapper.findMembersByOpenid(openid);
        if (openid != null && linked.isEmpty()) {
            ensureLinked(openid, member.getMemberId());
            linked = memberMapper.findMembersByOpenid(openid);
        }
        if (linked.isEmpty()) {
            linked = List.of(member);
        }
        String activeId = openid == null ? member.getMemberId() : memberMapper.findActiveMemberId(openid);
        boolean activeLinked = false;
        for (VoltMember item : linked) {
            if (item.getMemberId().equals(activeId)) {
                activeLinked = true;
                break;
            }
        }
        if (activeId == null || !activeLinked) {
            activeId = member.getMemberId();
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("activeMemberId", activeId);
        resp.put("members", memberViews(linked));
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> addMember(VoltMember member, String phone, String code) {
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        String openid = resolveOpenid(member);
        if (openid == null) {
            throw ApiException.conflict("WECHAT_REQUIRED", "请先用微信登录后再添加会员卡。");
        }
        verifyOtpCode(phone, code, PURPOSE_LOGIN);
        VoltMember target = memberMapper.findByPhone(phone);
        if (target == null) {
            target = createMember(phone, null, null, "", "");
        } else if (target.getOpenid() != null && !target.getOpenid().isBlank()
                && !target.getOpenid().equals(openid)) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已绑定其他微信，请联系前台处理。");
        }
        ensureLinked(openid, target.getMemberId());
        activate(openid, target.getMemberId());
        claimGuestRecords(target);
        VoltMember fresh = cardOrderService.claimForMember(target);
        return sessionResponse(fresh == null ? target : fresh, openid);
    }

    @Override
    @Transactional
    public Map<String, Object> switchMember(VoltMember member, String memberId) {
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        if (VoltContract.isBlank(memberId)) {
            throw ApiException.validation("请选择要切换的会员卡。", Map.of("memberId", "请选择会员卡"));
        }
        String openid = resolveOpenid(member);
        if (openid == null) {
            throw ApiException.conflict("WECHAT_REQUIRED", "当前登录没有绑定微信，无法切换会员卡。");
        }
        VoltMember target = memberMapper.findById(memberId);
        if (target == null) {
            throw ApiException.notFound("会员卡不存在。");
        }
        if (memberMapper.countWechatMember(openid, memberId) == 0) {
            if (openid.equals(target.getOpenid())) {
                ensureLinked(openid, memberId);
            } else {
                throw new ApiException(403, "MEMBER_NOT_LINKED", "这张会员卡不在当前微信名下。");
            }
        }
        activate(openid, memberId);
        claimGuestRecords(target);
        VoltMember fresh = cardOrderService.claimForMember(target);
        return sessionResponse(fresh == null ? target : fresh, openid);
    }

    private String resolveOpenid(VoltMember member) {
        if (member == null) {
            return null;
        }
        String openid = memberMapper.findOpenidByMember(member.getMemberId());
        if (openid == null || openid.isBlank()) {
            openid = member.getOpenid();
        }
        return openid == null || openid.isBlank() ? null : openid;
    }

    private void ensureLinked(String openid, String memberId) {
        if (openid == null || openid.isBlank() || memberId == null || memberId.isBlank()) {
            return;
        }
        if (memberMapper.countWechatMember(openid, memberId) == 0) {
            memberMapper.insertWechatMember(openid, memberId, false, LocalDateTime.now());
        }
    }

    private void activate(String openid, String memberId) {
        if (openid == null || openid.isBlank() || memberId == null || memberId.isBlank()) {
            return;
        }
        memberMapper.clearWechatActive(openid);
        ensureLinked(openid, memberId);
        memberMapper.setWechatActive(openid, memberId);
    }

    private List<Map<String, Object>> memberViews(List<VoltMember> members) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (VoltMember item : members) {
            views.add(viewService.memberView(item));
        }
        return views;
    }

    private Map<String, Object> sessionResponse(VoltMember member, String openid) {
        List<VoltMember> linked = openid == null
                ? List.of(member)
                : memberMapper.findMembersByOpenid(openid);
        if (linked.isEmpty()) {
            linked = List.of(member);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", issueToken(member.getMemberId()));
        resp.put("activeMemberId", member.getMemberId());
        resp.put("member", viewService.memberView(member));
        resp.put("members", memberViews(linked));
        return resp;
    }

    /**
     * 绑定后给小程序一个明确身份：已开卡、只约过体验课，或两者都没有。
     * 体验课同时按会员和历史手机号查，避免合并后 member_phone 还没迁移时漏掉。
     */
    private Map<String, Object> audienceView(VoltMember member) {
        Map<String, Booking> candidates = new LinkedHashMap<>();
        for (Booking booking : bookingMapper.findByMember(member.getMemberId())) {
            candidates.put(booking.getBookingId(), booking);
        }
        String phone = member.getPhone();
        if (phone != null && !phone.isBlank()) {
            for (Booking booking : bookingMapper.findByPhone(phone)) {
                candidates.put(booking.getBookingId(), booking);
            }
        }

        boolean hasTrial = false;
        Map<String, Object> trial = null;
        for (Booking booking : candidates.values()) {
            if (!"PLAN-01".equals(booking.getPlanCode())) {
                continue;
            }
            if (!"locked".equals(booking.getStatus()) && !"confirmed".equals(booking.getStatus())
                    && !"checked-in".equals(booking.getStatus())) {
                continue;
            }
            hasTrial = true;
            if (trial == null) {
                Map<String, Object> view = viewService.bookingView(booking);
                trial = new LinkedHashMap<>();
                trial.put("date", view.get("date"));
                trial.put("startTime", view.get("startTime"));
                trial.put("programName", view.get("programName"));
                trial.put("venueName", view.get("venueName"));
                trial.put("status", view.get("status"));
                trial.put("entryCode", view.get("entryCode"));
            }
        }

        Map<String, Object> pending = null;
        for (Map<String, Object> order : cardOrderService.memberOrders(member)) {
            if ("pending".equals(order.get("status"))) {
                pending = order;
                break;
            }
        }

        int credits = member.getCredits() == null ? 0 : member.getCredits();
        int creditsTotal = member.getCreditsTotal() == null ? 0 : member.getCreditsTotal();
        boolean hasCard = creditsTotal > 0
                || (member.getPlanCode() != null && !member.getPlanCode().isBlank());

        Map<String, Object> audience = new LinkedHashMap<>();
        audience.put("type", hasCard ? "card" : hasTrial ? "trial" : "visitor");
        audience.put("phone", VoltContract.maskPhone(phone));
        audience.put("hasTrial", hasTrial);
        audience.put("hasCard", hasCard);
        audience.put("hasPendingCard", pending != null);
        audience.put("credits", credits);
        audience.put("creditsTotal", creditsTotal);
        audience.put("planName", member.getPlanName());
        audience.put("expiresAt", member.getExpiresAt() == null ? null : member.getExpiresAt().toString());
        audience.put("trial", trial);
        if (pending != null) {
            audience.put("pendingOrderNo", pending.get("orderNo"));
            audience.put("pendingPlanName", pending.get("planName"));
            audience.put("pendingCredits", pending.get("credits"));
        }
        return audience;
    }

    /**
     * 小程序微信会员绑定了一个已有手机号的会员（例如官网先购卡/登录过）：
     * 谁有次卡谁做主账号，迁移另一边数据；小程序拿到主账号的 token 或继续用原 token。
     */
    private Map<String, Object> mergeIntoPhoneMember(VoltMember wechat, VoltMember phoneMember) {
        String openid = resolveOpenid(wechat);
        if (phoneMember.getOpenid() != null && !phoneMember.getOpenid().isBlank()
                && openid != null && !phoneMember.getOpenid().equals(openid)) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已绑定其他微信，请联系前台处理。");
        }
        boolean wechatHasPass = wechat.getCredits() != null && wechat.getCredits() > 0;
        boolean phoneHasPass = phoneMember.getCredits() != null && phoneMember.getCredits() > 0;

        if (wechatHasPass) {
            // 微信账号已有次卡：保留微信会员（openid 身份可继续用于 wx.login），把手机号会员的权益和历史迁过来。
            String phone = phoneMember.getPhone();
            String source = phoneMember.getMemberId();
            if (phoneHasPass) {
                combineCredits(phoneMember, wechat);
            }
            moveMemberData(source, wechat.getMemberId());
            detachMember(source);
            jdbcTemplate.update("UPDATE volt_member SET phone = ?, phone_bound = 1 WHERE member_id = ?",
                    phone, wechat.getMemberId());
            wechat.setPhone(phone);
            wechat.setPhoneBound(true);
            claimGuestRecords(wechat);
            VoltMember fresh = cardOrderService.claimForMember(wechat);
            if (openid != null) {
                ensureLinked(openid, wechat.getMemberId());
                activate(openid, wechat.getMemberId());
                memberMapper.deleteWechatMember(openid, source);
            }
            VoltMember finalMember = fresh == null ? wechat : fresh;
            Map<String, Object> resp = sessionResponse(finalMember, openid);
            resp.put("merged", true);
            resp.put("audience", audienceView(finalMember));
            return resp;
        }

        // 微信账号还没有次卡：手机号会员做主账号，把微信身份挂过去并换发 token。
        String source = wechat.getMemberId();
        String target = phoneMember.getMemberId();
        moveMemberData(source, target);
        detachMember(source);
        jdbcTemplate.update("UPDATE volt_member SET openid = ?, unionid = ?, "
                + "avatar_url = CASE WHEN avatar_url = '' THEN ? ELSE avatar_url END, "
                + "nickname = CASE WHEN nickname = '' OR nickname LIKE '电%' THEN ? ELSE nickname END, "
                + "phone_bound = 1 WHERE member_id = ?",
                wechat.getOpenid(), wechat.getUnionid(), wechat.getAvatarUrl(),
                wechat.getNickname(), target);
        phoneMember.setOpenid(wechat.getOpenid());
        phoneMember.setUnionid(wechat.getUnionid());
        if (phoneMember.getAvatarUrl() == null || phoneMember.getAvatarUrl().isBlank()) {
            phoneMember.setAvatarUrl(wechat.getAvatarUrl());
        }
        String nickname = phoneMember.getNickname();
        if (nickname == null || nickname.isBlank() || nickname.startsWith("电")) {
            phoneMember.setNickname(wechat.getNickname());
        }
        phoneMember.setPhoneBound(true);

        claimGuestRecords(phoneMember);
        VoltMember fresh = cardOrderService.claimForMember(phoneMember);
        if (openid != null) {
            ensureLinked(openid, target);
            activate(openid, target);
            memberMapper.deleteWechatMember(openid, source);
        }
        VoltMember finalMember = fresh == null ? phoneMember : fresh;
        Map<String, Object> resp = sessionResponse(finalMember, openid);
        resp.put("merged", true);
        resp.put("audience", audienceView(finalMember));
        return resp;
    }

    private void combineCredits(VoltMember source, VoltMember target) {
        int credits = source.getCredits() == null ? 0 : source.getCredits();
        int total = source.getCreditsTotal() == null ? 0 : source.getCreditsTotal();
        if (credits <= 0 && total <= 0) {
            return;
        }
        java.time.LocalDate sourceExpiry = source.getExpiresAt();
        java.time.LocalDate targetExpiry = target.getExpiresAt();
        boolean useSourcePlan = sourceExpiry != null
                && (targetExpiry == null || sourceExpiry.isAfter(targetExpiry));
        java.time.LocalDate expiry = useSourcePlan ? sourceExpiry : targetExpiry;
        jdbcTemplate.update(
                "UPDATE volt_member SET credits = credits + ?, credits_total = credits_total + ?, "
                        + "expires_at = ? WHERE member_id = ?",
                credits, total, expiry, target.getMemberId());
        if (useSourcePlan) {
            jdbcTemplate.update("UPDATE volt_member SET plan_code = ?, plan_name = ? WHERE member_id = ?",
                    source.getPlanCode(), source.getPlanName(), target.getMemberId());
            target.setPlanCode(source.getPlanCode());
            target.setPlanName(source.getPlanName());
        }
        target.setCredits((target.getCredits() == null ? 0 : target.getCredits()) + credits);
        target.setCreditsTotal((target.getCreditsTotal() == null ? 0 : target.getCreditsTotal()) + total);
        target.setExpiresAt(expiry);
    }

    private void moveMemberData(String source, String target) {
        jdbcTemplate.update("UPDATE volt_booking SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_event_signup SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_challenge_join SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_record SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE IGNORE volt_credit_ledger SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_message SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_card_order SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("UPDATE volt_invite SET inviter_member_id = ? WHERE inviter_member_id = ?", target, source);
        jdbcTemplate.update("UPDATE IGNORE volt_invite SET invitee_member_id = ? WHERE invitee_member_id = ?", target, source);
        jdbcTemplate.update("DELETE FROM volt_invite WHERE invitee_member_id = ?", source);
        jdbcTemplate.update("UPDATE IGNORE volt_waitlist SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("DELETE FROM volt_waitlist WHERE member_id = ?", source);
        jdbcTemplate.update("UPDATE IGNORE volt_body_log SET member_id = ? WHERE member_id = ?", target, source);
        jdbcTemplate.update("DELETE FROM volt_body_log WHERE member_id = ?", source);
        jdbcTemplate.update("INSERT IGNORE INTO volt_subscription (member_id) VALUES (?)", target);
        jdbcTemplate.update("DELETE FROM volt_subscription WHERE member_id = ?", source);
        jdbcTemplate.update("DELETE FROM volt_token WHERE member_id = ?", source);
    }

    private void detachMember(String memberId) {
        jdbcTemplate.update("UPDATE volt_member SET openid = NULL, unionid = NULL, phone = NULL, "
                + "phone_bound = 0, credits = 0, credits_total = 0, plan_code = '', plan_name = '', "
                + "expires_at = NULL, nickname = CONCAT('已合并-', member_id) WHERE member_id = ?", memberId);
    }

    @Override
    public VoltMember currentMember(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw ApiException.unauthorized("登录状态无效，请重新登录。");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw ApiException.unauthorized("登录状态无效，请重新登录。");
        }
        // 数据库只保存 token 的 SHA-256，明文只返回给客户端一次。
        VoltToken stored = memberMapper.findToken(sha256(token));
        if (stored == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        if (stored.getExpiresAt() != null && stored.getExpiresAt() < System.currentTimeMillis()) {
            throw ApiException.unauthorized("登录状态已过期，请重新登录。");
        }
        VoltMember member = memberMapper.findById(stored.getMemberId());
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        return member;
    }

    @Override
    public VoltMember requireMember(String authorization) {
        VoltMember member = currentMember(authorization);
        if (member == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录。");
        }
        return member;
    }

    private String issueToken(String memberId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String rawToken = sb.toString();
        VoltToken token = new VoltToken();
        token.setToken(sha256(rawToken));
        token.setMemberId(memberId);
        token.setExpiresAt(System.currentTimeMillis() + VoltContract.TOKEN_TTL_DAYS * 24L * 3600_000L);
        token.setCreatedAt(LocalDateTime.now());
        memberMapper.insertToken(token);
        return rawToken;
    }

    private boolean hasWxConfig() {
        return wxAppId != null && !wxAppId.isBlank() && wxAppSecret != null && !wxAppSecret.isBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchWxSession(String code) {
        // 微信该接口返回 text/plain，但 body 是 JSON；不能直接按 Map 反序列化。
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.weixin.qq.com")
                        .path("/sns/jscode2session")
                        .queryParam("appid", wxAppId)
                        .queryParam("secret", wxAppSecret)
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("微信登录响应解析失败", ex);
        }
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> input) {
            Map<String, Object> result = new LinkedHashMap<>();
            input.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private VoltMember createMember(String phone, String openid, String unionid,
                                    String nickname, String avatarUrl) {
        String seed = phone != null && !phone.isBlank() ? phone
                : openid != null && !openid.isBlank() ? openid
                : String.valueOf(System.nanoTime());
        VoltMember member = new VoltMember();
        member.setMemberId(nextMemberId());
        member.setMemberNo(memberNoOf(seed));
        member.setNickname(resolveNickname(nickname, phone, seed));
        member.setPhone(phone);
        member.setPhoneBound(phone != null && !phone.isBlank());
        member.setAvatarUrl(avatarUrl == null || avatarUrl.length() > 255 ? "" : avatarUrl);
        member.setOpenid(openid);
        member.setUnionid(unionid);
        member.setGoal("增肌 · 体能");
        member.setHeightCm(0);
        member.setWeightKg(BigDecimal.ZERO);
        member.setCredits(0);
        member.setCreditsTotal(0);
        member.setPlanCode("");
        member.setPlanName("");
        member.setExpiresAt(null);
        member.setTier("VOLT MEMBER");
        member.setReferralCode(referralCode());
        member.setJoinedAt(LocalDate.now());
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException ex) {
            VoltMember existing = phone != null && !phone.isBlank()
                    ? memberMapper.findByPhone(phone)
                    : memberMapper.findByOpenid(openid);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
        return member;
    }

    private String resolveNickname(String nickname, String phone, String seed) {
        if (nickname != null && !nickname.isBlank()) {
            String trimmed = nickname.trim();
            return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
        }
        if (phone != null && phone.length() >= 2) {
            return "电" + phone.substring(phone.length() - 2);
        }
        return "电友" + seed.substring(Math.max(0, seed.length() - 4));
    }

    private String nextMemberId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = String.format("MBR-%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
            if (memberMapper.findById(candidate) == null) {
                return candidate;
            }
        }
        return "MBR-" + System.currentTimeMillis();
    }

    private String memberNoOf(String seed) {
        return String.format("M%010d", Math.abs((long) seed.hashCode() % 10_000_000_000L));
    }

    private String referralCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("VLT");
            for (int i = 0; i < 4; i++) {
                sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            String candidate = sb.toString();
            Integer used = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM volt_member WHERE referral_code = ?", Integer.class, candidate);
            if (used == null || used == 0) {
                return candidate;
            }
        }
        throw new ApiException(500, "SERVER_ERROR", "生成邀请码失败，请重试。");
    }

    private void claimGuestRecords(VoltMember member) {
        if (member == null || member.getPhone() == null || member.getPhone().isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE volt_booking SET member_id = ? WHERE member_phone = ? AND (member_id IS NULL OR member_id = '')",
                member.getMemberId(), member.getPhone());
        jdbcTemplate.update(
                "UPDATE volt_event_signup SET member_id = ? WHERE member_phone = ? AND (member_id IS NULL OR member_id = '')",
                member.getMemberId(), member.getPhone());
        jdbcTemplate.update(
                "UPDATE volt_challenge_join SET member_id = ? WHERE member_phone = ? AND (member_id IS NULL OR member_id = '')",
                member.getMemberId(), member.getPhone());
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
