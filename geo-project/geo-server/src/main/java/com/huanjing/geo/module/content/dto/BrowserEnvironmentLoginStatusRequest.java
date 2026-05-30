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
        String errorMessage
) {
}
