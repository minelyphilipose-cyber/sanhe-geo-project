package com.huanjing.geo.common.llm.pool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "geo.llm.pool")
public class LlmPoolProperties {
    private boolean enabled = false;
    private int globalConcurrency = 36;
    private long leaseMs = 600_000L;
    private long leaseRenewIntervalMs = 30_000L;
    private long leaseSafetyMs = 60_000L;
    private long shutdownGraceMs = 30_000L;
    private long permitWaitTimeoutMs = 120_000L;
    private long permitRetryIntervalMs = 200L;
    private boolean blockingAcquireFailFastEnabled = false;
    private Set<String> blockingAcquireFailFastFeatures = new LinkedHashSet<>();
    private String permitKeyPrefix = "geo:llm:permit";
    private int circuitBreakerFailureThreshold = 5;
    private long circuitBreakerOpenDurationMs = 60_000L;
    private Map<String, Integer> featureConcurrency = new LinkedHashMap<>(Map.of(
            "monitoring", 8,
            "article", 4,
            "presale", 8,
            "baseline", 16,
            "draft", 4,
            "generic", 4
    ));

    public void setGlobalConcurrency(int globalConcurrency) {
        this.globalConcurrency = Math.max(1, globalConcurrency);
    }

    public void setLeaseMs(long leaseMs) {
        this.leaseMs = Math.max(60_000L, leaseMs);
    }

    public void setLeaseRenewIntervalMs(long leaseRenewIntervalMs) {
        this.leaseRenewIntervalMs = Math.max(1_000L, leaseRenewIntervalMs);
    }

    public void setLeaseSafetyMs(long leaseSafetyMs) {
        this.leaseSafetyMs = Math.max(1_000L, leaseSafetyMs);
    }

    public void setShutdownGraceMs(long shutdownGraceMs) {
        this.shutdownGraceMs = Math.max(0L, shutdownGraceMs);
    }

    public void setPermitWaitTimeoutMs(long permitWaitTimeoutMs) {
        this.permitWaitTimeoutMs = Math.max(0L, permitWaitTimeoutMs);
    }

    public void setPermitRetryIntervalMs(long permitRetryIntervalMs) {
        this.permitRetryIntervalMs = Math.max(10L, permitRetryIntervalMs);
    }

    public void setBlockingAcquireFailFastFeatures(Set<String> blockingAcquireFailFastFeatures) {
        Set<String> normalized = new LinkedHashSet<>();
        if (blockingAcquireFailFastFeatures != null) {
            blockingAcquireFailFastFeatures.forEach(feature -> {
                if (feature != null && !feature.isBlank()) {
                    normalized.add(feature.trim().toLowerCase());
                }
            });
        }
        this.blockingAcquireFailFastFeatures = normalized;
    }

    public boolean isBlockingAcquireFailFastEnabledFor(String feature) {
        if (!blockingAcquireFailFastEnabled) {
            return false;
        }
        return blockingAcquireFailFastFeatures.contains(normalizedFeature(feature));
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = Math.max(1, circuitBreakerFailureThreshold);
    }

    public void setCircuitBreakerOpenDurationMs(long circuitBreakerOpenDurationMs) {
        this.circuitBreakerOpenDurationMs = Math.max(1_000L, circuitBreakerOpenDurationMs);
    }

    public void setFeatureConcurrency(Map<String, Integer> featureConcurrency) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (featureConcurrency != null) {
            featureConcurrency.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && value > 0) {
                    normalized.put(key.trim().toLowerCase(), value);
                }
            });
        }
        this.featureConcurrency = normalized;
    }

    public int featureLimit(String feature) {
        return featureConcurrency.getOrDefault(normalizedFeature(feature), 0);
    }

    private String normalizedFeature(String feature) {
        if (feature == null || feature.isBlank()) {
            return "generic";
        }
        return feature.trim().toLowerCase();
    }
}
