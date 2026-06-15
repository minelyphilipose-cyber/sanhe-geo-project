package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.extension.entity.ExtensionSession;

import java.time.LocalDateTime;

public record ExtensionSessionVO(
        Long id,
        Long brandId,
        Long operatorId,
        String installId,
        String environmentKey,
        String providerProfileId,
        String extensionVersion,
        String userAgent,
        String status,
        LocalDateTime boundAt,
        LocalDateTime lastSeenAt,
        LocalDateTime expiresAt
) {
    public static ExtensionSessionVO from(ExtensionSession row) {
        return new ExtensionSessionVO(
                row.getId(),
                row.getBrandId(),
                row.getOperatorId(),
                row.getInstallId(),
                row.getEnvironmentKey(),
                row.getProviderProfileId(),
                row.getExtensionVersion(),
                row.getUserAgent(),
                row.getStatus(),
                row.getBoundAt(),
                row.getLastSeenAt(),
                row.getExpiresAt()
        );
    }
}
