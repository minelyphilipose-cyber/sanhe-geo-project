package com.huanjing.geo.common.llm.pool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.llm.pool")
public class LlmPoolProperties {
    private boolean enabled = false;
    private int globalConcurrency = 8;
    private long leaseMs = 600_000L;
    private long leaseRenewIntervalMs = 30_000L;
    private long leaseSafetyMs = 60_000L;
    private long shutdownGraceMs = 30_000L;
    private long permitWaitTimeoutMs = 120_000L;
    private long permitRetryIntervalMs = 200L;
    private String permitKeyPrefix = "geo:llm:permit";
    private int circuitBreakerFailureThreshold = 5;
    private long circuitBreakerOpenDurationMs = 60_000L;

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

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = Math.max(1, circuitBreakerFailureThreshold);
    }

    public void setCircuitBreakerOpenDurationMs(long circuitBreakerOpenDurationMs) {
        this.circuitBreakerOpenDurationMs = Math.max(1_000L, circuitBreakerOpenDurationMs);
    }
}
