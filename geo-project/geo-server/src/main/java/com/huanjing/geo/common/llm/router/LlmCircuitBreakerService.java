package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
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
            return state.halfOpenProbe.compareAndSet(false, true);
        }
        return false;
    }

    public void recordSuccess(String platformCode) {
        BreakerState state = states.get(platformCode);
        if (state != null) {
            state.failures.set(0);
            state.openedAtMillis = 0L;
            state.halfOpenProbe.set(false);
        }
    }

    public void recordFailure(String platformCode) {
        BreakerState state = states.computeIfAbsent(platformCode, ignored -> new BreakerState());
        int failures = state.failures.incrementAndGet();
        if (state.halfOpenProbe.getAndSet(false)
                || failures >= properties.getCircuitBreakerFailureThreshold()) {
            state.openedAtMillis = System.currentTimeMillis();
        }
    }

    public void releaseProbe(String platformCode) {
        BreakerState state = states.get(platformCode);
        if (state != null) {
            state.halfOpenProbe.set(false);
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
                            item.put("state", breakerState(state, now));
                            item.put("openedAtMillis", state.openedAtMillis);
                            return item;
                        },
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private String breakerState(BreakerState state, long now) {
        if (state.openedAtMillis <= 0L) {
            return "CLOSED";
        }
        if (now - state.openedAtMillis <= properties.getCircuitBreakerOpenDurationMs()) {
            return "OPEN";
        }
        return state.halfOpenProbe.get() ? "HALF_OPEN" : "PROBE_READY";
    }

    // TODO: move breaker state to Redis before relying on circuit state across multi-instance deployments.
    private static final class BreakerState {
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
        private volatile long openedAtMillis;
    }
}
