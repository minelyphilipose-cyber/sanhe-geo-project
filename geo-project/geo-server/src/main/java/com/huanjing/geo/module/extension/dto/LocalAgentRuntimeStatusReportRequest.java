package com.huanjing.geo.module.extension.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record LocalAgentRuntimeStatusReportRequest(
        String machineId,
        String activeProfile,
        String helperVersion,
        String protocolVersion,
        String helperName,
        Boolean adspowerApiOk,
        String adspowerApiBase,
        Integer runningTaskCount,
        Integer capacity,
        JsonNode supportedPlatforms,
        JsonNode capabilities,
        String lastErrorCode,
        String lastErrorMessage
) {
}
