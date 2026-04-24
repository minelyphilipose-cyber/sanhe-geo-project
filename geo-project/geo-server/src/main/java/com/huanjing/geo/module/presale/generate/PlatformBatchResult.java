package com.huanjing.geo.module.presale.generate;

public record PlatformBatchResult(
        String platformCode,
        PlatformStatus status,
        int processed,
        int failed,
        int progressDelta,
        Throwable errorCause
) {
    public static PlatformBatchResult degraded(String code, Throwable cause) {
        return new PlatformBatchResult(code, PlatformStatus.DEGRADED, 0, 0, 0, cause);
    }
}
