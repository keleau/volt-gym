package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.Map;

public interface VoltCommunityService {

    Map<String, Object> community(VoltMember member);

    Map<String, Object> joinEvent(String eventId, Map<String, Object> input, String idempotencyKey, VoltMember member);

    Map<String, Object> joinChallenge(Map<String, Object> input, String idempotencyKey, VoltMember member);
}
