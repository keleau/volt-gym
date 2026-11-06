package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.common.StoredResponse;
import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.Map;

public interface VoltBookingService {

    Map<String, Object> availability(String venueCode, String programNo, String date);

    /** 体验课按门店 + 日期查所有课程的可用时段，不要求先选课程。 */
    Map<String, Object> trialAvailability(String venueCode, String date);

    /** 返回首次创建或幂等重放的 HTTP 状态与响应体。 */
    StoredResponse createBooking(Map<String, Object> input, String idempotencyKey, VoltMember member);
}
