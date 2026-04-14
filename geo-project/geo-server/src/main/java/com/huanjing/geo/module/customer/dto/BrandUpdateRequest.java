package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandUpdateRequest {
    @NotBlank
    private String brandName;
    @NotBlank
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
    private String wechat;
    private String description;
    private String businessIntro;
    private String standardBrandStatement;
    private String businessStandardStatement;
    private String forbiddenPhrases;
    @NotBlank
    private String status;
    private String versionChangeReason;
}
