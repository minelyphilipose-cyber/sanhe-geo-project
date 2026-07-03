package com.huanjing.geo.module.extension.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ExtensionRuntimeStatusReportRequest(
        String installId,
        String environmentKey,
        String providerProfileId,
        String platform,
        String extensionVersion,
        String protocolVersion,
        String currentUrl,
        String detectedPlatform,
        String detectedAccountName,
        String detectedPlatformAccountId,
        String loginStatus,
        String runtimeStage,
        LocalDateTime runtimeStageAt,
        String runtimeStageMessage,
        JsonNode capabilities,
        Long lastTaskId,
        String lastErrorCode,
        String lastErrorMessage
) {
}
