package com.gym.gymmanagementsystem.pojo;

import lombok.Data;

@Data
public class Program {
    private String programNo;
    private String programName;
    private String programEn;
    private String programDesc;
    private String intensity;
    private Integer durationMin;
    private Integer kcal;
    private String imageUrl;
    private Integer sortOrder;
}
