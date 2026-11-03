package com.gym.gymmanagementsystem.mapper;

import com.gym.gymmanagementsystem.pojo.Challenge;
import com.gym.gymmanagementsystem.pojo.ChallengeJoin;
import com.gym.gymmanagementsystem.pojo.CommunityEvent;
import com.gym.gymmanagementsystem.pojo.EventSignup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoltCommunityMapper {

    List<CommunityEvent> findEvents();

    CommunityEvent findEvent(@Param("eventId") String eventId);

    /** 报名占位；满员时返回 0，用于并发兜底。 */
    int increaseEventSigned(@Param("eventId") String eventId);

    EventSignup findSignup(@Param("eventId") String eventId, @Param("phone") String phone);

    List<String> findSignedEventIds(@Param("phone") String phone);

    int insertSignup(EventSignup signup);

    Challenge findCurrentChallenge();

    Challenge findChallenge(@Param("challengeId") String challengeId);

    ChallengeJoin findChallengeJoin(@Param("challengeId") String challengeId, @Param("phone") String phone);

    int insertChallengeJoin(ChallengeJoin join);

    int increaseChallengeJoined(@Param("challengeId") String challengeId);
}
