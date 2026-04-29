package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandOfficialSiteCreateRequest {
    @NotBlank
    private String siteName;
    private String siteDomain;
    @NotBlank
    private String cmsFrameworkCode;
    @NotBlank
    private String tenantKey;
    @NotBlank
    private String apiEndpoint;
    private String authType;
    @NotBlank
    private String credentials;
    private String remark;
}
