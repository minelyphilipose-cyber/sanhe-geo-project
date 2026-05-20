package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandUpdateRequest {
    @NotBlank
    private String industry;
    @NotBlank
    private String brandName;
    @Size(max = 128)
    private String brandShortName;
    @NotBlank
    private String brandSlug;
    private String mainBusiness;
    @Size(max = 500)
    private String coreProducts;
    @Size(max = 255)
    private String brandPositioning;
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
    @Size(max = 300)
    private String brandQualificationDescription;
    @Size(max = 300)
    private String brandCaseDescription;
    private String forbiddenPhrases;
    private String geoSiteCode;
    private String geoSiteStatus;
    private String industrySiteName;
    private String industrySiteCode;
    @NotBlank
    private String status;
    private String versionChangeReason;
}
