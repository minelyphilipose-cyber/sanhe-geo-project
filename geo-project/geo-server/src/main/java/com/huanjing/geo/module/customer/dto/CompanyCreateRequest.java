package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyCreateRequest {
    @NotBlank
    private String companyName;
    private String contactName;
    private String contactPhone;
    private String industry;
    private String businessDirection;
    private String competitors;
    private String officialWebsite;
    private String officialAccount;
    private String videoAccount;
    private String douyinAccount;
    private String city;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String serviceArea;
    private String ownerType;
    private String sourceType;
    private Long partnerId;
    private Long salesOwnerId;
    private String referralSource;
    private String status;
    private String remark;
}
