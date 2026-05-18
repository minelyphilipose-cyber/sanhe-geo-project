package com.huanjing.geo.common.llm.pool;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmExecutionGateway {
    private final RedisLlmPermitStore permitStore;
    private final LlmPoolProperties properties;
    private final LeaseRenewalService renewalService;
    private final LlmGatewayMetrics metrics;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final String instanceId = resolveInstanceId();

    public LlmExecutionPermit acquire(String feature, AiPlatformConfig platformConfig) {
        if (!properties.isEnabled()) {
            return new LlmExecutionPermit(List.of(), UUID.randomUUID().toString(), feature, System.currentTimeMillis(), this);
        }
        if (shuttingDown.get()) {
            throw new LlmPermitUnavailableException(LlmPermitScope.GLOBAL, null);
        }
        if (platformConfig == null || !StringUtils.hasText(platformConfig.getPlatformCode())) {
            throw new IllegalArgumentException("platformCode is required for LLM permit");
        }
        String requestId = UUID.randomUUID().toString();
        String member = instanceId + ":" + Thread.currentThread().getId() + ":" + requestId;
        long now = System.currentTimeMillis();
        long leaseUntil = now + properties.getLeaseMs();
        List<LlmPermitToken> tokens = new ArrayList<>(3);
        String normalizedFeature = normalizedFeature(feature);

        LlmPermitToken global = new LlmPermitToken(
                globalKey(),
                member + ":global",
                LlmPermitScope.GLOBAL.name(),
                null,
                leaseUntil
        );
        if (!permitStore.acquire(global.key(), global.member(), properties.getGlobalConcurrency(), now, leaseUntil, properties.getLeaseSafetyMs())) {
            metrics.increment("llm_permit_acquire_busy_total:global");
            throw new LlmPermitUnavailableException(LlmPermitScope.GLOBAL, null);
        }
        tokens.add(global);

        int featureLimit = properties.featureLimit(normalizedFeature);
        if (featureLimit > 0) {
            LlmPermitToken featureToken = new LlmPermitToken(
                    featureKey(normalizedFeature),
                    member + ":feature:" + normalizedFeature,
                    LlmPermitScope.FEATURE.name(),
                    normalizedFeature,
                    leaseUntil
            );
            if (!permitStore.acquire(featureToken.key(), featureToken.member(), featureLimit, now, leaseUntil, properties.getLeaseSafetyMs())) {
                releaseTokens(tokens);
                metrics.increment("llm_permit_acquire_busy_total:feature:" + normalizedFeature);
                throw new LlmPermitUnavailableException(LlmPermitScope.FEATURE, normalizedFeature);
            }
            tokens.add(featureToken);
        }

        String platformCode = platformConfig.getPlatformCode().trim();
        int platformLimit = platformConfig.getConcurrencyLimit() == null || platformConfig.getConcurrencyLimit() <= 0
                ? 1
                : platformConfig.getConcurrencyLimit();
        LlmPermitToken platform = new LlmPermitToken(
                platformKey(platformCode),
                member + ":platform:" + platformCode,
                LlmPermitScope.PLATFORM.name(),
                platformCode,
                leaseUntil
        );
        if (!permitStore.acquire(platform.key(), platform.member(), platformLimit, now, leaseUntil, properties.getLeaseSafetyMs())) {
            releaseTokens(tokens);
            metrics.increment("llm_permit_acquire_busy_total:platform");
            throw new LlmPermitUnavailableException(LlmPermitScope.PLATFORM, platformCode);
        }
        tokens.add(platform);

        renewalService.register(tokens);
        metrics.increment("llm_permit_acquire_success_total:global");
        if (featureLimit > 0) {
            metrics.increment("llm_permit_acquire_success_total:feature:" + normalizedFeature);
        }
        metrics.increment("llm_permit_acquire_success_total:platform");
        return new LlmExecutionPermit(tokens, requestId, feature, now, this);
    }

    public LlmExecutionPermit acquireBlocking(String feature, AiPlatformConfig platformConfig) {
        long waitTimeoutMs = Math.max(0L, properties.getPermitWaitTimeoutMs());
        long retryIntervalMs = Math.max(10L, properties.getPermitRetryIntervalMs());
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        LlmPermitUnavailableException lastBusy = null;

        while (true) {
            try {
                return acquire(feature, platformConfig);
            } catch (LlmPermitUnavailableException ex) {
                lastBusy = ex;
                if (waitTimeoutMs <= 0L || System.currentTimeMillis() >= deadline) {
                    throw ex;
                }
                long sleepMs = Math.min(retryIntervalMs, Math.max(1L, deadline - System.currentTimeMillis()));
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw lastBusy;
                }
            }
        }
    }

    void release(LlmExecutionPermit permit) {
        List<LlmPermitToken> tokens = permit.tokens();
        renewalService.unregister(tokens);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            LlmPermitToken token = tokens.get(i);
            boolean removed = permitStore.release(
                    token.key(),
                    token.member(),
                    System.currentTimeMillis(),
                    properties.getLeaseSafetyMs()
            );
            if (removed) {
                metrics.increment("llm_permit_release_total:" + token.scope().toLowerCase());
            } else {
                metrics.increment("llm_permit_release_already_gone_total:" + token.scope().toLowerCase());
            }
        }
    }

    public Long activeGlobalCount() {
        return permitStore.activeCount(globalKey());
    }

    public Long activePlatformCount(String platformCode) {
        return permitStore.activeCount(platformKey(platformCode));
    }

    public Long activeFeatureCount(String feature) {
        return permitStore.activeCount(featureKey(normalizedFeature(feature)));
    }

    public Map<String, Long> activeFeatureCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        properties.getFeatureConcurrency().keySet().forEach(feature ->
                counts.put(feature, activeFeatureCount(feature))
        );
        return counts;
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        long deadline = System.currentTimeMillis() + properties.getShutdownGraceMs();
        while (!activeLlmTokens().isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        for (LeaseToken token : activeLlmTokens()) {
            permitStore.release(token.key(), token.member(), System.currentTimeMillis(), properties.getLeaseSafetyMs());
        }
    }

    private List<LeaseToken> activeLlmTokens() {
        return renewalService.snapshotTokensByScope(
                LlmPermitScope.GLOBAL.name(),
                LlmPermitScope.FEATURE.name(),
                LlmPermitScope.PLATFORM.name()
        );
    }

    private String globalKey() {
        return properties.getPermitKeyPrefix() + ":global";
    }

    private String featureKey(String feature) {
        return properties.getPermitKeyPrefix() + ":feature:" + normalizedFeature(feature);
    }

    private String platformKey(String platformCode) {
        return properties.getPermitKeyPrefix() + ":platform:" + platformCode;
    }

    private String normalizedFeature(String feature) {
        if (!StringUtils.hasText(feature)) {
            return "generic";
        }
        return feature.trim().toLowerCase();
    }

    private void releaseTokens(List<LlmPermitToken> tokens) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            LlmPermitToken token = tokens.get(i);
            permitStore.release(token.key(), token.member(), System.currentTimeMillis(), properties.getLeaseSafetyMs());
        }
    }

    private static String resolveInstanceId() {
        String runtime = ManagementFactory.getRuntimeMXBean().getName();
        if (StringUtils.hasText(runtime)) {
            return runtime.replaceAll("[^A-Za-z0-9_.-]", "_");
        }
        return UUID.randomUUID().toString();
    }
}
