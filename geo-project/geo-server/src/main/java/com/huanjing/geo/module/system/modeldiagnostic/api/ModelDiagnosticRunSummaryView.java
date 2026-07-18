package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticRunSummary;

import java.time.LocalDateTime;

public record ModelDiagnosticRunSummaryView(Long id,
                                            String sessionId,
                                            Integer turnNo,
                                            Long platformConfigId,
                                            String platformCode,
                                            String channelCode,
                                            String platformName,
                                            String requestedModelId,
                                            String responseModelId,
                                            String diagnosticMode,
                                            String testMode,
                                            String status,
                                            String conclusion,
                                            String errorCategory,
                                            String errorCode,
                                            Long durationMs,
                                            Integer sourceCount,
                                            Integer validSourceCount,
                                            Integer citationCount,
                                            Integer validCitationCount,
                                            LocalDateTime completedAt,
                                            LocalDateTime createdAt) {

    public static ModelDiagnosticRunSummaryView from(ModelDiagnosticRunSummary summary) {
        return new ModelDiagnosticRunSummaryView(
                summary.getId(), summary.getSessionId(), summary.getTurnNo(),
                summary.getPlatformConfigId(), summary.getPlatformCode(),
                summary.getChannelCode(), summary.getPlatformName(),
                summary.getRequestedModelId(), summary.getResponseModelId(),
                summary.getDiagnosticMode(), summary.getTestMode(), summary.getStatus(),
                summary.getConclusion(), summary.getErrorCategory(), summary.getErrorCode(),
                summary.getDurationMs(), summary.getSourceCount(), summary.getValidSourceCount(),
                summary.getCitationCount(), summary.getValidCitationCount(),
                summary.getCompletedAt(), summary.getCreatedAt());
    }
}
