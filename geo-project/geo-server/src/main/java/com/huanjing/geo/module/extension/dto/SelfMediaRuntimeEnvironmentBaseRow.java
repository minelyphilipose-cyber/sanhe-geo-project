package com.huanjing.geo.module.extension.dto;

import lombok.Data;

@Data
public class SelfMediaRuntimeEnvironmentBaseRow {
    private Long brandId;
    private String brandName;
    private String platform;
    private Long selfMediaAccountId;
    private String accountName;
    private String platformAccountId;
    private Long browserEnvironmentId;
    private String environmentName;
    private String environmentKey;
    private String providerProfileId;
    private Long browserEnvironmentAccountId;
    private String loginStatus;
    private String expectedAccountName;
    private String expectedPlatformAccountId;
}
