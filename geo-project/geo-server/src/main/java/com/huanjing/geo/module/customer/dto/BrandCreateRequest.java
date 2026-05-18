package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BrandCreateRequest {
    @NotNull
    private Long companyId;
    @NotBlank
    private String industry;
    @NotBlank
    private String brandName;
    private String brandSlug;
    private String mainBusiness;
    private String serviceArea;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String website;
    private String officialAccount;
    private String videoAccount;
    private String douyinAccount;
    private String phone;
    private String publicPhone;
    private String publicAddress;
    private String wechat;
    private String description;
    private String businessIntro;
    private String standardBrandStatement;
    private String businessStandardStatement;
    private String forbiddenPhrases;
    private String geoSiteCode;
    private String geoSiteStatus;
    private String industrySiteName;
    private String industrySiteCode;
    private String status;
    private String versionChangeReason;
}
