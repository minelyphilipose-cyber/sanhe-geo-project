package com.huanjing.geo.module.extension.dto;

public record RuntimeReadinessQuery(
        Long brandId,
        Long operatorId,
        Long localAgentSessionId,
        Long browserEnvironmentId,
        String platform,
        String requiredHelperFeature,
        String requiredExtensionFeature
) {
}
