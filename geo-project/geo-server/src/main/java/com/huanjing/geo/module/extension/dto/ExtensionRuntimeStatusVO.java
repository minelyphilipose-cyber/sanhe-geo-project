package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;

import java.time.LocalDateTime;

public record ExtensionRuntimeStatusVO(
        Long id,
        String installId,
        Long extensionSessionId,
        Long browserEnvironmentId,
        Long browserEnvironmentAccountId,
        Long brandId,
        String platform,
        String environmentKey,
        String providerProfileId,
        String extensionVersion,
        String protocolVersion,
        String detectedPlatform,
        String loginStatus,
        String runtimeStage,
        LocalDateTime lastSeenAt
) {
    public static ExtensionRuntimeStatusVO from(ExtensionRuntimeStatus row) {
        if (row == null) {
            return null;
        }
        return new ExtensionRuntimeStatusVO(
                row.getId(),
                row.getInstallId(),
                row.getExtensionSessionId(),
                row.getBrowserEnvironmentId(),
                row.getBrowserEnvironmentAccountId(),
                row.getBrandId(),
                row.getPlatform(),
                row.getEnvironmentKey(),
                row.getProviderProfileId(),
                row.getExtensionVersion(),
                row.getProtocolVersion(),
                row.getDetectedPlatform(),
                row.getLoginStatus(),
                row.getRuntimeStage(),
                row.getLastSeenAt()
        );
    }
}
