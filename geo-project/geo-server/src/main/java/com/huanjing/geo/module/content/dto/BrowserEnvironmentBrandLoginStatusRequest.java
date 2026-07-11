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
        Long loginVerificationId
) {
    public BrowserEnvironmentBrandLoginStatusRequest(Long selfMediaAccountId,
                                                     String platform,
                                                     String actualPlatformAccountId,
                                                     String actualAccountName,
                                                     String loginStatus,
                                                     String errorCode,
                                                     String errorMessage) {
        this(selfMediaAccountId, platform, actualPlatformAccountId, actualAccountName,
                loginStatus, errorCode, errorMessage, null);
    }
}
