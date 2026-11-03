package com.gym.gymmanagementsystem.service;

import com.gym.gymmanagementsystem.pojo.Booking;
import com.gym.gymmanagementsystem.pojo.Challenge;
import com.gym.gymmanagementsystem.pojo.Coach;
import com.gym.gymmanagementsystem.pojo.CommunityEvent;
import com.gym.gymmanagementsystem.pojo.Plan;
import com.gym.gymmanagementsystem.pojo.Program;
import com.gym.gymmanagementsystem.pojo.Venue;
import com.gym.gymmanagementsystem.pojo.VoltMember;
import com.gym.gymmanagementsystem.pojo.VoltSession;

import java.util.List;
import java.util.Map;

/**
 * 契约视图：数据库行 → docs/shared-backend-api-final.md 里的 JSON 结构。
 */
public interface VoltViewService {

    Map<String, Object> venueView(Venue venue, Integer todaySlots);

    Map<String, Object> programView(Program program);

    Map<String, Object> planView(Plan plan);

    Map<String, Object> coachView(Coach coach);

    Map<String, Object> sessionView(VoltSession session);

    /** 批量组装场次视图：目录只查一次，避免按场次逐条查门店/课程/教练。 */
    List<Map<String, Object>> sessionViews(List<VoltSession> sessions);

    Map<String, Object> bookingView(Booking booking);

    Map<String, Object> eventView(CommunityEvent event, boolean mySigned);

    Map<String, Object> memberView(VoltMember member);

    Map<String, Object> challengeView(Challenge challenge, boolean joinedByMe);
}
