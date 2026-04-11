package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyCreateRequest {
    @NotBlank
    private String companyName;
    private String industry;
    private String city;
    @NotBlank
    private String ownerType;
    private Long partnerId;
    private Long salesOwnerId;
    private String referralSource;
    private String status;
    private String remark;
}
