package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
