package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChallengeJoin {
    private String joinId;
    private String challengeId;
    private String memberId;
    private String memberPhone;
    private String memberNo;
    private LocalDateTime createdAt;
}
