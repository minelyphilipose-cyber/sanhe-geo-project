package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class ThirdPartySubjectPoolBrandRow {
    private Long brandId;
    private String brandName;
    private String industry;
    private String complianceIndustryCode;
    private Boolean allowThirdPartyPromotion;
    private Long companyId;
    private String companyName;
    private String companyStatus;
    private Boolean hasActivePackage;
    private Long subjectProjectId;
}
