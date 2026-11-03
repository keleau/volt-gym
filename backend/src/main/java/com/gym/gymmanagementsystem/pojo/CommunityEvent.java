package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CommunityEvent {
    private String eventId;
    private String title;
    private String dayText;
    private String monthText;
    private String tagText;
    private String descText;
    private String timeText;
    private LocalDate eventDate;
    private String venueCode;
    private String venueName;
    private Integer capCount;
    private Integer signedCount;
    private Integer sortOrder;
}
