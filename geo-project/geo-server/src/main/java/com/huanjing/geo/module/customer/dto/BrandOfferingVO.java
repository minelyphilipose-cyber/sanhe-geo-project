package com.huanjing.geo.module.customer.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BrandOfferingVO {
    private Long id;
    private Long brandId;
    private String offeringName;
    private List<String> offeringAliases;
    private String targetUsers;
    private String offeringIntro;
    private String qualificationDescription;
    private String remark;
    private String status;
    private Integer priority;
    private String useScenarios;
    private String medicalIndustryCode;
    private String medicalCategoryCode;
    private String medicalCategoryName;
    private String qualificationRef;
    private Boolean medicalProjectEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
