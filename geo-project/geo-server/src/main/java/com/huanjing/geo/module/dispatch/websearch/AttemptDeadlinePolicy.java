package com.huanjing.geo.module.dispatch.websearch;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class AttemptDeadlinePolicy {

    static final Duration HARD_CAP = Duration.ofMinutes(15);

    public LocalDateTime calculate(LocalDateTime createdAt,
                                   Duration perCallTimeout,
                                   int maximumPhysicalCalls,
                                   Duration retryBackoffBudget,
                                   Duration safetyMargin) {
        Objects.requireNonNull(createdAt, "createdAt");
        requireNonNegative(perCallTimeout, "perCallTimeout");
        requireNonNegative(retryBackoffBudget, "retryBackoffBudget");
        requireNonNegative(safetyMargin, "safetyMargin");
        if (maximumPhysicalCalls < 1) {
            throw new IllegalArgumentException("maximumPhysicalCalls must be at least 1");
        }

        Duration requestedBudget;
        try {
            requestedBudget = perCallTimeout.multipliedBy(maximumPhysicalCalls)
                    .plus(retryBackoffBudget)
                    .plus(safetyMargin);
        } catch (ArithmeticException ex) {
            requestedBudget = HARD_CAP;
        }
        Duration effectiveBudget = requestedBudget.compareTo(HARD_CAP) > 0 ? HARD_CAP : requestedBudget;
        if (effectiveBudget.isZero()) {
            throw new IllegalArgumentException("attempt budget must be greater than zero");
        }
        return createdAt.plus(effectiveBudget);
    }

    private void requireNonNegative(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
