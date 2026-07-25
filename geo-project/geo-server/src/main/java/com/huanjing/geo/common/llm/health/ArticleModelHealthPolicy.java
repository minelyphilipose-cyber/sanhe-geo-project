package com.huanjing.geo.common.llm.health;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class ArticleModelHealthPolicy {
    public static final int WINDOW_SIZE = 20;
    public static final Duration LOOKBACK = Duration.ofHours(2);
    public static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(10);
    public static final long SLOW_RESPONSE_THRESHOLD_MS = 180_000L;

    private static final int MIN_FAILURE_RATE_SAMPLE = 5;
    private static final int MIN_LATENCY_SAMPLE = 3;
    private static final double HIGH_FAILURE_RATE = 0.40D;

    private ArticleModelHealthPolicy() {
    }

    public static Evaluation evaluate(List<Sample> samples, LocalDateTime now) {
        LocalDateTime evaluatedAt = now == null ? LocalDateTime.now() : now;
        LocalDateTime lookbackStart = evaluatedAt.minus(LOOKBACK);
        List<Sample> window = samples == null
                ? List.of()
                : samples.stream()
                        .filter(sample -> sample != null
                                && sample.occurredAt() != null
                                && !sample.occurredAt().isBefore(lookbackStart))
                        .sorted(Comparator.comparing(Sample::occurredAt).reversed())
                        .limit(WINDOW_SIZE)
                        .toList();
        if (window.isEmpty()) {
            return Evaluation.noData();
        }

        LocalDateTime latestInfrastructureFailureAt = window.stream()
                .filter(Sample::infrastructureFailure)
                .map(Sample::occurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        boolean coolingDown = latestInfrastructureFailureAt != null
                && !latestInfrastructureFailureAt.isBefore(evaluatedAt.minus(FAILURE_COOLDOWN));

        long failureCount = window.stream().filter(Sample::infrastructureFailure).count();
        boolean highFailure = window.size() >= MIN_FAILURE_RATE_SAMPLE
                && (double) failureCount / window.size() >= HIGH_FAILURE_RATE;

        List<Long> successfulDurations = window.stream()
                .filter(Sample::success)
                .map(Sample::durationMs)
                .filter(duration -> duration != null && duration >= 0L)
                .sorted()
                .toList();
        long p90DurationMs = percentile90(successfulDurations);
        boolean slowResponse = successfulDurations.size() >= MIN_LATENCY_SAMPLE
                && p90DurationMs >= SLOW_RESPONSE_THRESHOLD_MS;

        HealthLevel level;
        if (coolingDown) {
            level = HealthLevel.COOLDOWN;
        } else if (highFailure) {
            level = HealthLevel.HIGH_FAILURE;
        } else if (slowResponse) {
            level = HealthLevel.SLOW_RESPONSE;
        } else {
            level = HealthLevel.HEALTHY;
        }
        return new Evaluation(
                level,
                window.size(),
                (int) failureCount,
                p90DurationMs,
                latestInfrastructureFailureAt
        );
    }

    private static long percentile90(List<Long> sortedValues) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(sortedValues.size() * 0.90D) - 1);
        return sortedValues.get(index);
    }

    public enum HealthLevel {
        NO_DATA,
        HEALTHY,
        SLOW_RESPONSE,
        HIGH_FAILURE,
        COOLDOWN
    }

    public record Sample(boolean success,
                         boolean infrastructureFailure,
                         Long durationMs,
                         LocalDateTime occurredAt) {
    }

    public record Evaluation(HealthLevel level,
                             int sampleCount,
                             int infrastructureFailureCount,
                             long p90DurationMs,
                             LocalDateTime latestInfrastructureFailureAt) {
        public static Evaluation noData() {
            return new Evaluation(HealthLevel.NO_DATA, 0, 0, 0L, null);
        }

        public static Evaluation healthy() {
            return new Evaluation(HealthLevel.HEALTHY, 0, 0, 0L, null);
        }

        public boolean routingBlocked() {
            return level == HealthLevel.COOLDOWN;
        }

        public int routingWeightPercent() {
            return switch (level) {
                case HIGH_FAILURE -> 10;
                case SLOW_RESPONSE -> 50;
                default -> 100;
            };
        }

        public String healthStatus() {
            return switch (level) {
                case COOLDOWN, HIGH_FAILURE -> "high_failure";
                case SLOW_RESPONSE -> "slow_response";
                case NO_DATA, HEALTHY -> "normal";
            };
        }
    }
}
