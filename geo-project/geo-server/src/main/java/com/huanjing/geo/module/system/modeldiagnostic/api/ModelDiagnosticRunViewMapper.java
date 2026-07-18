package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ModelDiagnosticRunViewMapper {

    private final ObjectMapper objectMapper;

    public ModelDiagnosticRunView toView(AiModelDiagnosticRun run) {
        Map<String, String> capabilities = new LinkedHashMap<>();
        capabilities.put("authentication", run.getAuthenticationStatus());
        capabilities.put("generation", run.getGenerationStatus());
        capabilities.put("webSearch", run.getWebSearchStatus());
        capabilities.put("sourceParsing", run.getSourceParsingStatus());
        capabilities.put("citationParsing", run.getCitationParsingStatus());
        Map<String, String> error = null;
        if (run.getErrorCategory() != null || run.getErrorCode() != null
                || run.getErrorMessage() != null) {
            error = new LinkedHashMap<>();
            error.put("category", run.getErrorCategory());
            error.put("code", run.getErrorCode());
            error.put("message", run.getErrorMessage());
        }
        return new ModelDiagnosticRunView(
                run.getId(), run.getSessionId(), run.getTurnNo(), run.getPlatformConfigId(),
                run.getPlatformName(), run.getDiagnosticMode(), run.getTestMode(),
                run.getStatus(), run.getConclusion(), run.getConclusionReason(),
                run.getUserMessage(), run.getAnswer(), run.getProviderRequestId(),
                run.getRequestedModelId(), run.getResponseModelId(), run.getHttpStatus(),
                run.getDurationMs(), run.getResponseMode(), run.getPromptTokens(),
                run.getCompletionTokens(), run.getTotalTokens(), run.getWebSearchCallCount(),
                run.getSearchStatus(), run.getSourceCount(),
                run.getValidSourceCount(), run.getCitationCount(), run.getValidCitationCount(),
                Collections.unmodifiableMap(capabilities), read(run.getSearchEvidenceJson(), true),
                read(run.getSourcesJson(), true), read(run.getCitationsJson(), true),
                read(run.getUsageJson(), false), run.getSanitizedRequest(),
                run.getSanitizedResponse(), error == null ? null : Collections.unmodifiableMap(error),
                run.getStartedAt(), run.getCompletedAt(), run.getCreatedAt());
    }

    private JsonNode read(String value, boolean arrayDefault) {
        if (value == null || value.isBlank()) {
            return arrayDefault ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return arrayDefault ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
        }
    }
}
