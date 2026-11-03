package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Booking {
    private String bookingId;
    private String orderNo;
    private String entryCode;
    private String memberId;
    private String sessionId;
    private String status;
    private Integer seatNo;
    private Long lockExpiresAt;
    private Boolean reminderOptIn;
    private String source;
    private String planCode;
    private String planName;
    private Integer amount;
    private String memberName;
    private String memberPhone;
    private LocalDateTime createdAt;
}
