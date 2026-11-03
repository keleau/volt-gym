package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

@Data
public class Plan {
    private String planCode;
    private String planName;
    private String planEn;
    private Integer price;
    private String unitText;
    private Boolean featured;
    private String pointsJson;
    private String ctaText;
    private Integer sortOrder;
}
