package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

@Data
public class Venue {
    private String venueCode;
    private String venueName;
    private String venueEn;
    private String address;
    private String metro;
    private String hours;
    private String imageUrl;
    private Double latitude;
    private Double longitude;
    private Integer sortOrder;
}
