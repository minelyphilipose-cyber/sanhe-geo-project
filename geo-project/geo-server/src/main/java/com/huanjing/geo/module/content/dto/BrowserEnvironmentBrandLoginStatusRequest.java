package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;

public record BrowserEnvironmentBrandLoginStatusRequest(
        Long selfMediaAccountId,
        @NotBlank String platform,
        String actualPlatformAccountId,
        String actualAccountName,
        @NotBlank String loginStatus,
        String errorCode,
        String errorMessage,
        Long loginVerificationId,
        String environmentKey,
        Boolean authoritative,
        String reportSource,
        String currentUrl,
        String extensionVersion,
        String protocolVersion
) {
    public BrowserEnvironmentBrandLoginStatusRequest(Long selfMediaAccountId,
                                                     String platform,
                                                     String actualPlatformAccountId,
                                                     String actualAccountName,
                                                     String loginStatus,
                                                     String errorCode,
                                                     String errorMessage) {
        this(selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, null, null, null, null, null, null, null);
    }

    public BrowserEnvironmentBrandLoginStatusRequest(Long selfMediaAccountId,
                                                     String platform,
                                                     String actualPlatformAccountId,
                                                     String actualAccountName,
                                                     String loginStatus,
                                                     String errorCode,
                                                     String errorMessage,
                                                     Long loginVerificationId) {
        this(selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, loginVerificationId, null, null, null, null, null, null);
    }

    public BrowserEnvironmentBrandLoginStatusRequest(Long selfMediaAccountId,
                                                     String platform,
                                                     String actualPlatformAccountId,
                                                     String actualAccountName,
                                                     String loginStatus,
                                                     String errorCode,
                                                     String errorMessage,
                                                     Long loginVerificationId,
                                                     String environmentKey) {
        this(selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, loginVerificationId, environmentKey, null,
                null, null, null, null);
    }

    public BrowserEnvironmentBrandLoginStatusRequest(Long selfMediaAccountId,
                                                     String platform,
                                                     String actualPlatformAccountId,
                                                     String actualAccountName,
                                                     String loginStatus,
                                                     String errorCode,
                                                     String errorMessage,
                                                     Long loginVerificationId,
                                                     String environmentKey,
                                                     Boolean authoritative) {
        this(selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, loginVerificationId, environmentKey, authoritative,
                null, null, null, null);
    }
}
