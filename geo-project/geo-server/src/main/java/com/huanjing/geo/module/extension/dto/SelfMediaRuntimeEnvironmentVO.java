package com.huanjing.geo.module.extension.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record SelfMediaRuntimeEnvironmentVO(
        Long brandId,
        String brandName,
        String platform,
        Long selfMediaAccountId,
        String accountName,
        String platformAccountId,
        Long browserEnvironmentId,
        String environmentName,
        String environmentKey,
        String providerProfileId,
        Long browserEnvironmentAccountId,
        String loginStatus,
        String expectedAccountName,
        String expectedPlatformAccountId,
        ExtensionStatus extension,
        HelperStatus helper,
        ReadinessStatus readiness
) {
    public record ExtensionStatus(
            String installId,
            String extensionVersion,
            String protocolVersion,
            LocalDateTime lastSeenAt,
            String runtimeStage,
            String runtimeStageMessage,
            String lastErrorCode,
            String lastErrorMessage
    ) {
    }

    public record HelperStatus(
            Long sessionId,
            String machineId,
            String activeProfile,
            String helperVersion,
            String protocolVersion,
            Boolean adspowerApiOk,
            Integer runningTaskCount,
            Integer capacity,
            String runtimeState,
            JsonNode resourceMetrics,
            LocalDateTime lastCleanupAt,
            String helperBootId,
            Long policyVersion,
            LocalDateTime lastSeenAt,
            String lastErrorCode,
            String lastErrorMessage
    ) {
    }

    public record ReadinessStatus(
            boolean ready,
            List<String> blockedReasons,
            Integer retryAfterSeconds,
            String gateMode,
            String scope
    ) {
    }
}
