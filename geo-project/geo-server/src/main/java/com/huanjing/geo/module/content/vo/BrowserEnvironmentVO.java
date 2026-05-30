package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.BrowserEnvironment;

import java.time.LocalDateTime;

public record BrowserEnvironmentVO(
        Long id,
        Long brandId,
        String provider,
        String environmentKey,
        String providerProfileId,
        String name,
        String status,
        LocalDateTime lastStartedAt,
        LocalDateTime lastStoppedAt,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BrowserEnvironmentVO from(BrowserEnvironment row) {
        if (row == null) return null;
        return new BrowserEnvironmentVO(
                row.getId(),
                row.getBrandId(),
                row.getProvider(),
                row.getEnvironmentKey(),
                row.getProviderProfileId(),
                row.getName(),
                row.getStatus(),
                row.getLastStartedAt(),
                row.getLastStoppedAt(),
                row.getLastErrorCode(),
                row.getLastErrorMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
