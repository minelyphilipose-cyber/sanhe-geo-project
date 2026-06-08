package com.huanjing.geo.module.content.schedule;

import java.time.LocalDateTime;

public record PublishCheckResult(
        Outcome outcome,
        String platformPublishedUrl,
        SelfMediaPlatformPublishStatus platformStatus,
        String diagnosticsJson,
        String failureCode,
        String failureMessage,
        LocalDateTime nextAttemptAt
) {
    public enum Outcome {
        PUBLISHED,
        UNKNOWN,
        FAILED,
        RETRY
    }

    public static PublishCheckResult published(String platformPublishedUrl, String diagnosticsJson) {
        return new PublishCheckResult(
                Outcome.PUBLISHED,
                platformPublishedUrl,
                SelfMediaPlatformPublishStatus.PUBLISHED,
                diagnosticsJson,
                null,
                null,
                null
        );
    }

    public static PublishCheckResult unknown(String diagnosticsJson) {
        return new PublishCheckResult(Outcome.UNKNOWN, null, SelfMediaPlatformPublishStatus.UNKNOWN,
                diagnosticsJson, null, null, null);
    }

    public static PublishCheckResult failed(String failureCode, String failureMessage, String diagnosticsJson) {
        return new PublishCheckResult(Outcome.FAILED, null, SelfMediaPlatformPublishStatus.FAILED,
                diagnosticsJson, failureCode, failureMessage, null);
    }

    public static PublishCheckResult retryable(String failureCode,
                                               String failureMessage,
                                               String diagnosticsJson,
                                               LocalDateTime nextAttemptAt) {
        return new PublishCheckResult(Outcome.RETRY, null, SelfMediaPlatformPublishStatus.UNKNOWN,
                diagnosticsJson, failureCode, failureMessage, nextAttemptAt);
    }

    public PublishCheckResult withPlatformStatus(SelfMediaPlatformPublishStatus status) {
        return new PublishCheckResult(
                outcome,
                platformPublishedUrl,
                status == null ? SelfMediaPlatformPublishStatus.UNKNOWN : status,
                diagnosticsJson,
                failureCode,
                failureMessage,
                nextAttemptAt
        );
    }
}
