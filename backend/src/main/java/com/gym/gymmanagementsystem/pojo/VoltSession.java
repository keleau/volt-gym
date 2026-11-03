package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class VoltSession {
    private String sessionId;
    private String sessionCode;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueCode;
    private String programNo;
    private String coachId;
    private Integer capacity;
    private Integer booked;
    private String status;
}
