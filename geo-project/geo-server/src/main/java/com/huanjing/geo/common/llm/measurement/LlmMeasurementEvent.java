package com.huanjing.geo.common.llm.measurement;

import com.huanjing.geo.common.llm.LlmGovernanceStack;
import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.common.llm.LlmWaitSemantics;

import java.time.LocalDateTime;

public record LlmMeasurementEvent(LlmCallMeasurementContext context,
                                  String feature,
                                  String platformCode,
                                  String platformName,
                                  String modelId,
                                  String modelName,
                                  LlmGovernanceStack governanceStack,
                                  LlmRoutingStrategy routingStrategy,
                                  LlmWaitSemantics waitSemantics,
                                  String status,
                                  LlmErrorCategory errorCategory,
                                  Integer httpStatusCode,
                                  String providerErrorCode,
                                  Long retryAfterMs,
                                  String failureKind,
                                  Integer requestCount,
                                  Long waitMs,
                                  Long httpMs,
                                  Long totalMs,
                                  Integer promptTokens,
                                  Integer completionTokens,
                                  LocalDateTime occurredAt) {

    public LlmMeasurementEvent {
        context = context == null ? LlmCallMeasurementContext.empty() : context;
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        status = status == null ? "unknown" : status;
    }
}
