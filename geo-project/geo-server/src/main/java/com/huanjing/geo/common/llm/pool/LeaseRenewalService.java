package com.huanjing.geo.common.llm.pool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaseRenewalService {
    private final RedisLlmPermitStore permitStore;
    private final LlmPoolProperties properties;
    private final LlmGatewayMetrics metrics;
    private final Map<String, LeaseToken> tokens = new ConcurrentHashMap<>();
    private ScheduledExecutorService executorService;

    @PostConstruct
    public void start() {
        executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "llm-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(1_000L, properties.getLeaseRenewIntervalMs());
        executorService.scheduleWithFixedDelay(this::renewAllSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void register(Collection<? extends LeaseToken> newTokens) {
        for (LeaseToken token : newTokens) {
            tokens.put(token.member(), token);
        }
    }

    public void unregister(Collection<? extends LeaseToken> oldTokens) {
        for (LeaseToken token : oldTokens) {
            tokens.remove(token.member());
        }
    }

    public List<LeaseToken> snapshotTokens() {
        return List.copyOf(tokens.values());
    }

    public List<LeaseToken> snapshotTokensByScope(String... scopes) {
        if (scopes == null || scopes.length == 0) {
            return snapshotTokens();
        }
        java.util.Set<String> expected = java.util.Set.of(scopes);
        return tokens.values().stream()
                .filter(token -> expected.contains(token.scope()))
                .toList();
    }

    private void renewAllSafely() {
        try {
            renewAll();
        } catch (Exception ex) {
            log.warn("LLM lease renewal scan failed", ex);
        }
    }

    private void renewAll() {
        if (tokens.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long leaseUntil = now + properties.getLeaseMs();
        for (LeaseToken token : List.copyOf(tokens.values())) {
            boolean renewed = permitStore.renew(
                    token.key(),
                    token.member(),
                    now,
                    leaseUntil,
                    properties.getLeaseSafetyMs()
            );
            if (!renewed) {
                tokens.remove(token.member());
                metrics.increment("llm_permit_renew_lost_total");
                log.warn("LLM lease renewal lost token, scope={}, platformCode={}, member={}",
                        token.scope(), token.platformCode(), token.member());
                continue;
            }
            tokens.put(token.member(), token.renewUntil(leaseUntil));
        }
    }

    @PreDestroy
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
