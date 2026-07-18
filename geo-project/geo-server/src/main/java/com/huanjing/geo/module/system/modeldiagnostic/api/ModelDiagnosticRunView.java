package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

public record ModelDiagnosticRunView(Long runId,
                                     String sessionId,
                                     Integer turnNo,
                                     Long platformConfigId,
                                     String platformName,
                                     String diagnosticMode,
                                     String testMode,
                                     String status,
                                     String conclusion,
                                     String conclusionReason,
                                     String userMessage,
                                     String assistantMessage,
                                     String providerRequestId,
                                     String requestedModelId,
                                     String responseModelId,
                                     Integer httpStatus,
                                     Long durationMs,
                                     String responseMode,
                                     Integer promptTokens,
                                     Integer completionTokens,
                                     Integer totalTokens,
                                     Integer webSearchCallCount,
                                     String searchStatus,
                                     Integer sourceCount,
                                     Integer validSourceCount,
                                     Integer citationCount,
                                     Integer validCitationCount,
                                     Map<String, String> capabilities,
                                     JsonNode searchEvidence,
                                     JsonNode sources,
                                     JsonNode citations,
                                     JsonNode usage,
                                     String sanitizedRequest,
                                     String sanitizedResponse,
                                     Map<String, String> error,
                                     LocalDateTime startedAt,
                                     LocalDateTime completedAt,
                                     LocalDateTime createdAt) {
}
