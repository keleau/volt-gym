package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.dto.CreditLedgerView;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.mini.VoltArchiveService;
import com.gym.gymmanagementsystem.service.mini.VoltCheckinService;
import com.gym.gymmanagementsystem.service.mini.VoltMemberCenterService;
import com.gym.gymmanagementsystem.service.mini.VoltScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信小程序页面所需的聚合接口。契约见 docs/shared-backend-api-final.md。
 */
@RestController
@RequestMapping("/api/v1")
public class VoltMiniController {

    private final VoltMemberCenterService memberCenterService;
    private final VoltScheduleService scheduleService;
    private final VoltCheckinService checkinService;
    private final VoltArchiveService archiveService;
    private final VoltAuthService authService;

    public VoltMiniController(VoltMemberCenterService memberCenterService,
                              VoltScheduleService scheduleService,
                              VoltCheckinService checkinService,
                              VoltArchiveService archiveService,
                              VoltAuthService authService) {
        this.memberCenterService = memberCenterService;
        this.scheduleService = scheduleService;
        this.checkinService = checkinService;
        this.archiveService = archiveService;
        this.authService = authService;
    }

    @GetMapping("/catalog/schedule")
    public Map<String, Object> schedule(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return scheduleService.schedule(authService.currentMember(authorization));
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> session(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return scheduleService.sessionDetail(sessionId, authService.currentMember(authorization));
    }

    @GetMapping("/member/home")
    public Map<String, Object> home(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.home(authService.requireMember(authorization));
    }

    @GetMapping("/member/account")
    public Map<String, Object> account(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.account(authService.requireMember(authorization));
    }

    @GetMapping("/member/archive")
    public Map<String, Object> archive(
            @RequestParam(value = "month", required = false) String month,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return archiveService.archive(authService.requireMember(authorization), month);
    }

    @GetMapping("/member/checkin")
    public Map<String, Object> checkin(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return checkinService.checkinInfo(authService.requireMember(authorization));
    }

    @PostMapping("/member/body-logs")
    public Map<String, Object> bodyLog(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return archiveService.addBodyLog(authService.requireMember(authorization), body);
    }

    @GetMapping("/member/messages")
    public Map<String, Object> messages(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.messages(authService.requireMember(authorization));
    }

    @PostMapping("/member/messages/{messageId}/read")
    public Map<String, Object> readMessage(
            @PathVariable("messageId") String messageId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.markMessageRead(authService.requireMember(authorization), messageId);
    }

    @PatchMapping("/member/subscriptions")
    public Map<String, Object> subscriptions(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.updateSubscriptions(authService.requireMember(authorization), body);
    }

    @GetMapping("/member/credits/ledger")
    public CreditLedgerView creditLedger(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.creditLedger(authService.requireMember(authorization));
    }

    @PostMapping("/sessions/{sessionId}/bookings")
    public ResponseEntity<Map<String, Object>> createBooking(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Map<String, Object> resp = scheduleService.createBooking(
                authService.requireMember(authorization), sessionId, body, idempotencyKey);
        boolean duplicate = Boolean.TRUE.equals(resp.get("duplicate"));
        return ResponseEntity.status(duplicate ? HttpStatus.OK : HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/bookings/{bookingId}/confirm")
    public Map<String, Object> confirm(
            @PathVariable("bookingId") String bookingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return scheduleService.confirmBooking(authService.requireMember(authorization), bookingId, idempotencyKey);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public Map<String, Object> cancel(
            @PathVariable("bookingId") String bookingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return scheduleService.cancelBooking(authService.requireMember(authorization), bookingId, idempotencyKey);
    }

    @PostMapping("/sessions/{sessionId}/waitlist")
    public Map<String, Object> joinWaitlist(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return scheduleService.joinWaitlist(authService.requireMember(authorization), sessionId, idempotencyKey);
    }

    @DeleteMapping("/waitlist/{waitlistId}")
    public Map<String, Object> cancelWaitlist(
            @PathVariable("waitlistId") String waitlistId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return scheduleService.cancelWaitlist(authService.requireMember(authorization), waitlistId);
    }

    @PostMapping("/checkins/scan")
    public Map<String, Object> scan(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return checkinService.scanCheckin(authService.requireMember(authorization), body, idempotencyKey);
    }

    @PostMapping("/checkins/free")
    public Map<String, Object> free(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return checkinService.freeCheckin(authService.requireMember(authorization), body, idempotencyKey);
    }

    @GetMapping("/invites/{ref}")
    public Map<String, Object> invite(
            @PathVariable("ref") String ref,
            @RequestParam(value = "program", required = false) String program,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.invite(ref, program, authService.currentMember(authorization));
    }

    @GetMapping("/invites/me")
    public Map<String, Object> inviteMe(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.inviteMe(authService.requireMember(authorization));
    }

    @PostMapping("/invites/{ref}/accept")
    public Map<String, Object> acceptInvite(
            @PathVariable("ref") String ref,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return memberCenterService.acceptInvite(ref, authService.requireMember(authorization));
    }
}
