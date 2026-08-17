package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;

public record BrowserEnvironmentLoginStatusRequest(
        @NotBlank String environmentKey,
        Long selfMediaAccountId,
        @NotBlank String platform,
        String actualPlatformAccountId,
        String actualAccountName,
        @NotBlank String loginStatus,
        String errorCode,
        String errorMessage,
        Long loginVerificationId,
        Boolean authoritative,
        String reportSource,
        String currentUrl,
        String extensionVersion,
        String protocolVersion
) {
    public BrowserEnvironmentLoginStatusRequest(String environmentKey,
                                                Long selfMediaAccountId,
                                                String platform,
                                                String actualPlatformAccountId,
                                                String actualAccountName,
                                                String loginStatus,
                                                String errorCode,
                                                String errorMessage) {
        this(environmentKey, selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, null, null, null, null, null, null);
    }

    public BrowserEnvironmentLoginStatusRequest(String environmentKey,
                                                Long selfMediaAccountId,
                                                String platform,
                                                String actualPlatformAccountId,
                                                String actualAccountName,
                                                String loginStatus,
                                                String errorCode,
                                                String errorMessage,
                                                Long loginVerificationId) {
        this(environmentKey, selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, loginVerificationId, null, null, null, null, null);
    }

    public BrowserEnvironmentLoginStatusRequest(String environmentKey,
                                                Long selfMediaAccountId,
                                                String platform,
                                                String actualPlatformAccountId,
                                                String actualAccountName,
                                                String loginStatus,
                                                String errorCode,
                                                String errorMessage,
                                                Long loginVerificationId,
                                                Boolean authoritative) {
        this(environmentKey, selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, loginVerificationId, authoritative,
                null, null, null, null);
    }
}
