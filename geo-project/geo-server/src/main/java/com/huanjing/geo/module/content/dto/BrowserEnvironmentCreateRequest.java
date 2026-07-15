package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BrowserEnvironmentCreateRequest(
        @NotNull Long brandId,
        String provider,
        @NotBlank String environmentKey,
        @NotBlank String providerProfileId,
        String name,
        Long localAgentSessionId
) {
}
