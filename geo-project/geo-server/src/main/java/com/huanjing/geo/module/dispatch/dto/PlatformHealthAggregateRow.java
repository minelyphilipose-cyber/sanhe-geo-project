package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformHealthAggregateRow {
    private String platformCode;
    private Long invocationCount;
    private Long successCount;
    private Long failureCount;
    private Long rateLimitedCount;
    private Long permitBusyCount;
    private Long circuitOpenCount;
    private Long slowResponseCount;
    private Long avgDurationMs;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
}
