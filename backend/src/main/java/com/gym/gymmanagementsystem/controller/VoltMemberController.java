package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltMemberService;
import com.gym.gymmanagementsystem.service.mini.VoltMemberCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会员台账与资料：官网会员中心与小程序「我的」共用。
 */
@RestController
@RequestMapping("/api/v1/member")
public class VoltMemberController {

    private final VoltMemberService memberService;
    private final VoltMemberCenterService memberCenterService;
    private final VoltAuthService authService;

    public VoltMemberController(VoltMemberService memberService,
                                VoltMemberCenterService memberCenterService,
                                VoltAuthService authService) {
        this.memberService = memberService;
        this.memberCenterService = memberCenterService;
        this.authService = authService;
    }

    @GetMapping("/bookings")
    public Map<String, Object> bookings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.requireMember(authorization);
        return memberCenterService.bookings(member);
    }

    @PatchMapping("/me")
    public Map<String, Object> updateMe(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.requireMember(authorization);
        return memberService.updateProfile(member, body);
    }
}
