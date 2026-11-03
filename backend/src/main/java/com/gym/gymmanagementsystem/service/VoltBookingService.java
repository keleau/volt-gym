package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.Map;

public interface VoltBookingService {

    Map<String, Object> availability(String venueCode, String programNo, String date);

    /** 返回首次创建或幂等重放的 HTTP 状态与响应体。 */
    StoredResponse createBooking(Map<String, Object> input, String idempotencyKey, VoltMember member);
}
