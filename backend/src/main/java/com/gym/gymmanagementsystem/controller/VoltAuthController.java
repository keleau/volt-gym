package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 官网会员登录：验证码发送与校验。
 * 短信通道尚未接入：本地联调用 volt.auth.expose-otp=true 让响应带上 devCode。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class VoltAuthController {

    private final VoltAuthService authService;

    public VoltAuthController(VoltAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/send")
    public Map<String, Object> sendOtp(@RequestBody Map<String, Object> body) {
        return authService.sendOtp(text(body.get("phone")));
    }

    @PostMapping("/phone/send")
    public Map<String, Object> sendBindOtp(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.requireMember(authorization);
        return authService.sendBindOtp(member, text(body.get("phone")));
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verifyOtp(@RequestBody Map<String, Object> body) {
        return authService.verifyOtp(text(body.get("phone")), text(body.get("code")));
    }

    @PostMapping("/wx/login")
    public Map<String, Object> wxLogin(@RequestBody Map<String, Object> body) {
        return authService.loginWithWechat(body);
    }

    @PostMapping("/link-phone")
    public Map<String, Object> linkPhone(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.requireMember(authorization);
        return authService.linkPhone(member, text(body.get("phone")), text(body.get("code")));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
