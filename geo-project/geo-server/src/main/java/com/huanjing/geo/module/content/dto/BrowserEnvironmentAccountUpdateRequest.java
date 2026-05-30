package com.huanjing.geo.module.content.dto;

public record BrowserEnvironmentAccountUpdateRequest(
        String expectedPlatformAccountId,
        String expectedAccountName,
        String loginStatus
) {
}
