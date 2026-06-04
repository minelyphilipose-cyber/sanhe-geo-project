package com.huanjing.geo.module.content.schedule;

import java.time.LocalDateTime;

public record ScheduleExecutionResult(
        boolean success,
        String platformScheduleId,
        String diagnosticsJson,
        String failureCode,
        String failureMessage,
        LocalDateTime nextAttemptAt
) {
    public static ScheduleExecutionResult scheduled(String platformScheduleId, String diagnosticsJson) {
        return new ScheduleExecutionResult(true, platformScheduleId, diagnosticsJson, null, null, null);
    }

    public static ScheduleExecutionResult failed(String failureCode, String failureMessage, String diagnosticsJson) {
        return new ScheduleExecutionResult(false, null, diagnosticsJson, failureCode, failureMessage, null);
    }

    public static ScheduleExecutionResult retryable(String failureCode,
                                                    String failureMessage,
                                                    String diagnosticsJson,
                                                    LocalDateTime nextAttemptAt) {
        return new ScheduleExecutionResult(false, null, diagnosticsJson, failureCode, failureMessage, nextAttemptAt);
    }
}
