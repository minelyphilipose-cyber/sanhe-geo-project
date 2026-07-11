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
        Long loginVerificationId
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
                loginStatus, errorCode, errorMessage, null);
    }
}
