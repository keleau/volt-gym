package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CardOrder {
    private String cardOrderId;
    private String orderNo;
    private String memberId;
    private String memberName;
    private String memberPhone;
    private String planCode;
    private String planName;
    private Integer amount;
    private Integer credits;
    private Integer validDays;
    private String venueCode;
    private String note;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private String activatedBy;
    private LocalDateTime appliedAt;
}
