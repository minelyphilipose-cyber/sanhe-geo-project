package com.huanjing.geo.common.llm.measurement;

import com.huanjing.geo.common.llm.LlmGovernanceStack;

public record LlmCapacitySignal(LlmCallMeasurementContext context,
                                String feature,
                                String platformCode,
                                LlmGovernanceStack governanceStack,
                                LlmErrorCategory errorCategory,
                                long globalActive,
                                long featureActive,
                                long platformActive,
                                long permitWaiters,
                                long legacyConcurrencyWaiters) {

    public LlmCapacitySignal {
        context = context == null ? LlmCallMeasurementContext.empty() : context;
    }
}
