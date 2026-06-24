package com.huanjing.geo.common.llm.measurement;

public record LlmCallMeasurementContext(String runId,
                                        Long customerId,
                                        Long projectId,
                                        LlmObservationScope scope,
                                        String normalizedPromptHash) {

    public static LlmCallMeasurementContext empty() {
        return new LlmCallMeasurementContext(null, null, null, null, null);
    }

    public LlmCallMeasurementContext {
        runId = trimToNull(runId);
        normalizedPromptHash = trimToNull(normalizedPromptHash);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
