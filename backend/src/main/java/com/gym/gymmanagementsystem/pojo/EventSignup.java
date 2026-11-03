package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventSignup {
    private String signupId;
    private String eventId;
    private String memberId;
    private String memberName;
    private String memberPhone;
    private String signupNo;
    private LocalDateTime createdAt;
}
