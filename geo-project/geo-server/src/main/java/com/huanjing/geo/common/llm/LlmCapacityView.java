package com.huanjing.geo.common.llm;

import com.huanjing.geo.common.llm.pool.LlmExecutionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmCapacityView {
    private final LlmExecutionGateway executionGateway;

    public Long activePlatformCount(String platformCode) {
        return executionGateway.activePlatformCount(platformCode);
    }

    public Long activeGlobalCount() {
        return executionGateway.activeGlobalCount();
    }

    public Long activeFeatureCount(String feature) {
        return executionGateway.activeFeatureCount(feature);
    }

    public Long activeWaiterCount() {
        return executionGateway.activeWaiterCount();
    }
}
