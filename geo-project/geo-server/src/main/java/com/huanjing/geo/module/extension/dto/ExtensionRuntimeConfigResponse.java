package com.huanjing.geo.module.extension.dto;

import java.util.List;

public record ExtensionRuntimeConfigResponse(
        Long brandId,
        String helperBase,
        String selectionStatus,
        RuntimeEnvironmentConfig selected,
        List<RuntimeEnvironmentConfig> candidates
) {
    public record RuntimeEnvironmentConfig(
            Long browserEnvironmentAccountId,
            Long browserEnvironmentId,
            String environmentKey,
            String environmentName,
            String provider,
            String providerProfileId,
            Long selfMediaAccountId,
            String platform,
            String expectedPlatformAccountId,
            String expectedAccountName,
            String loginStatus
    ) {
    }
}
