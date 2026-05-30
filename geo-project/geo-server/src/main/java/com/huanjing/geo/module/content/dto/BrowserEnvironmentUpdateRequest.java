package com.huanjing.geo.module.content.dto;

public record BrowserEnvironmentUpdateRequest(
        String providerProfileId,
        String name,
        String status,
        String lastErrorCode,
        String lastErrorMessage
) {
}
