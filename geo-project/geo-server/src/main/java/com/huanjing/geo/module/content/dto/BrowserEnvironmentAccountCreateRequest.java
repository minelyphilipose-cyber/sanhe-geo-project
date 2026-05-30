package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;

public record BrowserEnvironmentAccountCreateRequest(
        @NotNull Long browserEnvironmentId,
        @NotNull Long selfMediaAccountId,
        String expectedPlatformAccountId,
        String expectedAccountName
) {
}
