package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandOfferingRequest {
    @NotBlank
    @Size(max = 128)
    private String offeringName;
    @Size(max = 500)
    private String offeringAliases;
    @Size(max = 500)
    private String targetUsers;
    private String offeringIntro;
    @Size(max = 1000)
    private String qualificationDescription;
    @Size(max = 500)
    private String remark;
    @NotBlank
    private String status;
    @NotNull
    private Integer priority;
    @Size(max = 500)
    private String useScenarios;
    @Size(max = 32)
    private String medicalIndustryCode;
    @Size(max = 64)
    private String medicalCategoryCode;
    @Size(max = 128)
    private String medicalCategoryName;
    @Size(max = 500)
    private String qualificationRef;
    private Boolean medicalProjectEnabled;
}
