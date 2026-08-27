package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCircuitBreakerServiceTest {

    @Test
    void snapshotExposesFailureCountAndOpenState() {
        LlmPoolProperties properties = new LlmPoolProperties();
        properties.setCircuitBreakerFailureThreshold(2);
        properties.setCircuitBreakerOpenDurationMs(60_000L);
        LlmCircuitBreakerService service = new LlmCircuitBreakerService(properties);

        service.recordFailure("openai");
        service.recordFailure("openai");

        Map<String, Map<String, Object>> snapshot = service.snapshot();

        assertEquals(2, snapshot.get("openai").get("failureCount"));
        assertEquals(true, snapshot.get("openai").get("open"));
        assertTrue((Long) snapshot.get("openai").get("openedAtMillis") > 0L);
    }

    @Test
    void openBreakerAllowsOnlyOneHalfOpenProbe() throws Exception {
        LlmPoolProperties properties = new LlmPoolProperties();
        properties.setCircuitBreakerFailureThreshold(1);
        properties.setCircuitBreakerOpenDurationMs(1_000L);
        LlmCircuitBreakerService service = new LlmCircuitBreakerService(properties);
        service.recordFailure("monitoring:kimi:primary");

        assertFalse(service.allowRequest("monitoring:kimi:primary"));
        Thread.sleep(1_050L);
        assertTrue(service.allowRequest("monitoring:kimi:primary"));
        assertFalse(service.allowRequest("monitoring:kimi:primary"));

        service.recordSuccess("monitoring:kimi:primary");
        assertTrue(service.allowRequest("monitoring:kimi:primary"));
    }

    @Test
    void breakerKeysAreIsolatedByFeatureAndChannel() {
        LlmPoolProperties properties = new LlmPoolProperties();
        properties.setCircuitBreakerFailureThreshold(1);
        LlmCircuitBreakerService service = new LlmCircuitBreakerService(properties);

        service.recordFailure("mobile_judge:deepseek:primary");

        assertFalse(service.allowRequest("mobile_judge:deepseek:primary"));
        assertTrue(service.allowRequest("monitoring:deepseek:primary"));
        assertTrue(service.allowRequest("mobile_judge:deepseek:backup_key"));
    }
}
