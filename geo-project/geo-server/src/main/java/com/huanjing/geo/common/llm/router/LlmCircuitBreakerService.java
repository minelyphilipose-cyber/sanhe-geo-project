package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmCircuitBreakerService {
    private final LlmPoolProperties properties;
    private final ConcurrentHashMap<String, BreakerState> states = new ConcurrentHashMap<>();

    public boolean allowRequest(String platformCode) {
        BreakerState state = states.get(platformCode);
        if (state == null) {
            return true;
        }
        long openedAt = state.openedAtMillis;
        if (openedAt <= 0L) {
            return true;
        }
        if (System.currentTimeMillis() - openedAt > properties.getCircuitBreakerOpenDurationMs()) {
            state.openedAtMillis = 0L;
            state.failures.set(0);
            return true;
        }
        return false;
    }

    public void recordSuccess(String platformCode) {
        BreakerState state = states.get(platformCode);
        if (state != null) {
            state.failures.set(0);
            state.openedAtMillis = 0L;
        }
    }

    public void recordFailure(String platformCode) {
        BreakerState state = states.computeIfAbsent(platformCode, ignored -> new BreakerState());
        if (state.failures.incrementAndGet() >= properties.getCircuitBreakerFailureThreshold()) {
            state.openedAtMillis = System.currentTimeMillis();
        }
    }

    public Map<String, Map<String, Object>> snapshot() {
        long now = System.currentTimeMillis();
        return states.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            BreakerState state = entry.getValue();
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("failureCount", state.failures.get());
                            item.put("open", state.openedAtMillis > 0
                                    && now - state.openedAtMillis <= properties.getCircuitBreakerOpenDurationMs());
                            item.put("openedAtMillis", state.openedAtMillis);
                            return item;
                        },
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    // TODO: move breaker state to Redis before relying on circuit state across multi-instance deployments.
    private static final class BreakerState {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile long openedAtMillis;
    }
}
