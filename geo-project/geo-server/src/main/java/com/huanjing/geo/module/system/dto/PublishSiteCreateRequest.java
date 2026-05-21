package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class PublishSiteCreateRequest {
    @NotBlank
    private String siteName;
    @NotBlank
    private String siteCode;
    @NotBlank
    private String domain;
    private String iconUrl;
    private List<String> industryTags;
    @NotBlank
    private String tier;
    @NotBlank
    private String status;
    @NotBlank
    private String integrationMethod;
    private String apiEndpoint;
    private String httpMethod;
    private String authType;
    private String credentialRef;
    private String apiCredential;
    private String requestHeaderTemplate;
    private String requestBodyTemplate;
    private String responseUrlPath;
    private String contentConstraints;
    private String currentHealthStatus;
    private String remark;
}
