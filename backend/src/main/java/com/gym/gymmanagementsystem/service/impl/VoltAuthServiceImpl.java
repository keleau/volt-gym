package com.gym.gymmanagementsystem.service.impl;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.common.VoltContract;
import com.gym.gymmanagementsystem.mapper.VoltMemberMapper;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltOtp;
import com.gym.gymmanagementsystem.pojo.VoltToken;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltViewService;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VoltAuthServiceImpl implements VoltAuthService {

    private static final Logger log = LoggerFactory.getLogger(VoltAuthServiceImpl.class);
    private static final String PURPOSE_LOGIN = "login";
    private static final String DEMO_MEMBER_ID = "MBR-DEMO";
    private static final String DEMO_PHONE = "13800006688";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoltMemberMapper memberMapper;
    private final VoltViewService viewService;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient = RestClient.create();

    @Value("${volt.auth.expose-otp:false}")
    private boolean exposeOtp;

    @Value("${volt.wx.app-id:}")
    private String wxAppId;

    @Value("${volt.wx.app-secret:}")
    private String wxAppSecret;

    @Value("${volt.wx.demo-fallback:true}")
    private boolean wxDemoFallback;

    public VoltAuthServiceImpl(VoltMemberMapper memberMapper, VoltViewService viewService,
                               JdbcTemplate jdbcTemplate) {
        this.memberMapper = memberMapper;
        this.viewService = viewService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> sendOtp(String phone) {
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        VoltOtp latest = memberMapper.findLatestOtp(phone, PURPOSE_LOGIN);
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
        otp.setPurpose(PURPOSE_LOGIN);
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
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        if (VoltContract.isBlank(code)) {
            throw ApiException.validation("请输入验证码。", Map.of("code", "请输入验证码"));
        }

        VoltOtp otp = memberMapper.findLatestOtp(phone, PURPOSE_LOGIN);
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

        VoltMember member = memberMapper.findByPhone(phone);
        if (member == null) {
            member = createMember(phone, null, null, null, null);
        }
        claimGuestRecords(member);

        String token = issueToken(member.getMemberId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("member", viewService.memberView(member));
        return resp;
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
                String openid = text(session.get("openid"));
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
            member = memberMapper.findById(DEMO_MEMBER_ID);
            if (member == null) {
                member = memberMapper.findByPhone(DEMO_PHONE);
            }
            if (member == null) {
                member = createMember(DEMO_PHONE, null, null, "阿电", "");
            }
        }
        claimGuestRecords(member);
        String token = issueToken(member.getMemberId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", token);
        resp.put("member", viewService.memberView(member));
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> linkPhone(VoltMember member, String phone) {
        if (!VoltContract.isPhone(phone)) {
            throw ApiException.validation("请输入正确的 11 位手机号。", Map.of("phone", "请输入正确的 11 位手机号"));
        }
        try {
            memberMapper.updatePhone(member.getMemberId(), phone);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已绑定其他会员。");
        }
        // 绑定后把同手机号的官网游客预约/报名认领到当前会员，认领幂等。
        claimGuestRecords(memberMapper.findById(member.getMemberId()));
        VoltMember fresh = memberMapper.findById(member.getMemberId());
        return viewService.memberView(fresh == null ? member : fresh);
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
        Map<String, Object> body = restClient.get()
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
                .body(Map.class);
        return body == null ? Map.of() : body;
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
