package com.huanjing.geo.common.llm.pool;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LlmGatewayMetrics {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, ignored -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Long> snapshot() {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }
}
