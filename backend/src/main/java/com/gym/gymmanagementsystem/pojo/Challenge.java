package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Challenge {
    private String challengeId;
    private String title;
    private String descText;
    private Integer targetSessions;
    private Integer days;
    private Integer joinedCount;
    private Integer percent;
    private LocalDate weekStart;
    private String status;
}
