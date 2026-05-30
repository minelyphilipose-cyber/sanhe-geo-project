package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;

import java.time.LocalDateTime;

public record BrowserEnvironmentAccountVO(
        Long id,
        Long brandId,
        Long browserEnvironmentId,
        String environmentKey,
        String provider,
        String providerProfileId,
        Long selfMediaAccountId,
        String platform,
        String expectedPlatformAccountId,
        String expectedAccountName,
        String loginStatus,
        LocalDateTime lastVerifiedAt,
        LocalDateTime lastLoginSeenAt,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BrowserEnvironmentAccountVO from(BrowserEnvironmentAccount row, BrowserEnvironment environment) {
        if (row == null) return null;
        return new BrowserEnvironmentAccountVO(
                row.getId(),
                row.getBrandId(),
                row.getBrowserEnvironmentId(),
                environment == null ? null : environment.getEnvironmentKey(),
                environment == null ? null : environment.getProvider(),
                environment == null ? null : environment.getProviderProfileId(),
                row.getSelfMediaAccountId(),
                row.getPlatform(),
                row.getExpectedPlatformAccountId(),
                row.getExpectedAccountName(),
                row.getLoginStatus(),
                row.getLastVerifiedAt(),
                row.getLastLoginSeenAt(),
                row.getLastErrorCode(),
                row.getLastErrorMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
