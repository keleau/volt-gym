package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltBookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 余位查询与预约创建。匿名可预约；带 token 时挂到当前会员。
 */
@RestController
@RequestMapping("/api/v1")
public class VoltBookingController {

    private final VoltBookingService bookingService;
    private final VoltAuthService authService;

    public VoltBookingController(VoltBookingService bookingService, VoltAuthService authService) {
        this.bookingService = bookingService;
        this.authService = authService;
    }

    @GetMapping("/availability")
    public Map<String, Object> availability(@RequestParam("venue") String venue,
                                            @RequestParam("program") String program,
                                            @RequestParam("date") String date) {
        return bookingService.availability(venue, program, date);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.currentMember(authorization);
        StoredResponse result = bookingService.createBooking(body, idempotencyKey, member);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.getBody();
        return ResponseEntity.status(result.getStatus()).body(payload);
    }
}
