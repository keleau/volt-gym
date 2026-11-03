package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoltOtp {
    private Long id;
    private String phone;
    private String code;
    private String purpose;
    private Long expiresAt;
    private Boolean consumed;
    private Integer attempts;
    private LocalDateTime createdAt;
}
