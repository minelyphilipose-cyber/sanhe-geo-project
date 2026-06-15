package com.huanjing.geo.module.content.vo;

import java.time.LocalDateTime;
import java.util.List;

public record SelfMediaAutomationReadinessVO(
        Long brandId,
        String status,
        boolean ready,
        LocalAgent localAgent,
        BrowserEnvironment browserEnvironment,
        ExtensionBinding extensionBinding,
        List<AccountReadiness> accounts,
        List<Issue> issues
) {
    public record LocalAgent(
            boolean bound,
            boolean online,
            Long sessionId,
            String helperName,
            LocalDateTime lastSeenAt,
            LocalDateTime expiresAt
    ) {
    }

    public record BrowserEnvironment(
            boolean configured,
            boolean active,
            Long id,
            String environmentKey,
            String providerProfileId,
            String name
    ) {
    }

    public record ExtensionBinding(
            boolean bound,
            boolean online,
            Long sessionId,
            String environmentKey,
            String providerProfileId,
            String extensionVersion,
            String expectedVersion,
            boolean versionSupported,
            LocalDateTime lastSeenAt,
            LocalDateTime expiresAt
    ) {
    }

    public record AccountReadiness(
            Long selfMediaAccountId,
            String platform,
            String accountName,
            boolean bindingConfigured,
            Long browserEnvironmentAccountId,
            String loginStatus,
            boolean loginReady,
            String issueCode,
            String issueMessage
    ) {
    }

    public record Issue(
            String code,
            String level,
            String title,
            String action,
            String actionKey
    ) {
    }
}
