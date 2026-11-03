package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.VoltMember;

import java.util.Map;

public interface VoltMemberService {

    Map<String, Object> updateProfile(VoltMember member, Map<String, Object> patch);
}
