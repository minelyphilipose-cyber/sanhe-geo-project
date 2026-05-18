package com.huanjing.geo.common.llm.pool;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.llm.router.LlmCircuitBreakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class LlmPoolMonitoringController {
    private final LlmPoolProperties properties;
    private final LlmGatewayMetrics metrics;
    private final LlmExecutionGateway gateway;
    private final LeaseRenewalService leaseRenewalService;
    private final LlmCircuitBreakerService circuitBreakerService;

    @GetMapping("/llm-pool")
    public R<Map<String, Object>> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("globalConcurrency", properties.getGlobalConcurrency());
        data.put("activeGlobal", gateway.activeGlobalCount());
        data.put("featureConcurrency", properties.getFeatureConcurrency());
        data.put("activeFeatures", gateway.activeFeatureCounts());
        data.put("trackedLeases", leaseRenewalService.snapshotTokens().size());
        data.put("counters", metrics.snapshot());
        data.put("circuitBreakers", circuitBreakerService.snapshot());
        return R.ok(data);
    }
}
