package com.gym.gymmanagementsystem.controller;

import com.gym.gymmanagementsystem.common.ApiException;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.service.VoltAuthService;
import com.gym.gymmanagementsystem.service.VoltCardOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 官网购卡登记与店员开卡。
 * 店员接口用 X-Staff-Key 保护；未配置 VOLT_STAFF_KEY 时一律拒绝。
 */
@RestController
@RequestMapping("/api/v1")
public class VoltCardOrderController {

    private final VoltCardOrderService cardOrderService;
    private final VoltAuthService authService;

    @Value("${volt.staff.key:}")
    private String staffKey;

    public VoltCardOrderController(VoltCardOrderService cardOrderService, VoltAuthService authService) {
        this.cardOrderService = cardOrderService;
        this.authService = authService;
    }

    @PostMapping("/card-orders")
    public Map<String, Object> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.currentMember(authorization);
        return cardOrderService.create(body, member);
    }

    @GetMapping("/member/card-orders")
    public Map<String, Object> mine(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        VoltMember member = authService.requireMember(authorization);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orders", cardOrderService.memberOrders(member));
        return resp;
    }

    @GetMapping("/staff/card-orders")
    public Map<String, Object> staffOrders(
            @RequestHeader(value = "X-Staff-Key", required = false) String key,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "status", required = false) String status) {
        requireStaff(key);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orders", cardOrderService.staffOrders(phone, status));
        return resp;
    }

    @PostMapping("/staff/card-orders/{orderNo}/activate")
    public Map<String, Object> activate(
            @RequestHeader(value = "X-Staff-Key", required = false) String key,
            @RequestHeader(value = "X-Staff-Name", required = false) String staffName,
            @PathVariable("orderNo") String orderNo) {
        requireStaff(key);
        return cardOrderService.activate(orderNo, staffName);
    }

    @PostMapping("/demo/card-orders/{orderNo}/pay")
    public Map<String, Object> demoPay(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("orderNo") String orderNo) {
        VoltMember member = authService.requireMember(authorization);
        return cardOrderService.demoPay(orderNo, member);
    }

    private void requireStaff(String key) {
        if (staffKey == null || staffKey.isBlank()) {
            throw new ApiException(503, "STAFF_DISABLED", "店员开卡未配置，请先在 .env 设置 VOLT_STAFF_KEY。");
        }
        if (key == null || !MessageDigest.isEqual(
                staffKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("店员密钥不正确。");
        }
    }
}
