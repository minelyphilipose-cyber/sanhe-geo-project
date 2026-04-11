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
    private String website;
    private String phone;
    private String wechat;
    private String description;
    private String standardBrandStatement;
    private String forbiddenPhrases;
    @NotBlank
    private String status;
}
