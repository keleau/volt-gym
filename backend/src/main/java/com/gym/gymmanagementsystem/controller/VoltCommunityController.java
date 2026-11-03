package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltCommunityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 社区聚合、活动报名、挑战加入。匿名可参与；带 token 时按会员身份记账。
 */
@RestController
@RequestMapping("/api/v1")
public class VoltCommunityController {

    private final VoltCommunityService communityService;
    private final VoltAuthService authService;

    public VoltCommunityController(VoltCommunityService communityService, VoltAuthService authService) {
        this.communityService = communityService;
        this.authService = authService;
    }

    @GetMapping("/community")
    public Map<String, Object> community(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return communityService.community(authService.currentMember(authorization));
    }

    @PostMapping("/events/{eventId}/signups")
    public Map<String, Object> joinEvent(
            @PathVariable("eventId") String eventId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.currentMember(authorization);
        return communityService.joinEvent(eventId, body, idempotencyKey, member);
    }

    @PostMapping("/challenges/current/join")
    public Map<String, Object> joinChallenge(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.currentMember(authorization);
        return communityService.joinChallenge(body, idempotencyKey, member);
    }
}
