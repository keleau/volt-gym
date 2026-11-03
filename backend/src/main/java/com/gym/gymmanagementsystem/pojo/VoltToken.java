package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoltToken {
    private String token;
    private String memberId;
    private Long expiresAt;
    private LocalDateTime createdAt;
}
