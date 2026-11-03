package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VoltMember {
    private String memberId;
    private String memberNo;
    private String nickname;
    private String phone;
    private Boolean phoneBound;
    private String avatarUrl;
    private String openid;
    private String unionid;
    private String goal;
    private Integer heightCm;
    private BigDecimal weightKg;
    private Integer credits;
    private Integer creditsTotal;
    private String planCode;
    private String planName;
    private LocalDate expiresAt;
    private String tier;
    private String referralCode;
    private LocalDate joinedAt;
}
