package com.huanjing.geo.module.dispatch.websearch;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptDeadlinePolicyTest {

    private final AttemptDeadlinePolicy policy = new AttemptDeadlinePolicy();

    @Test
    void calculatesDeadlineOnceFromTheWholeAttemptBudget() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 14, 10, 0);

        LocalDateTime deadline = policy.calculate(
                createdAt,
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(15),
                Duration.ofSeconds(5));

        assertThat(deadline).isEqualTo(createdAt.plusSeconds(110));
    }

    @Test
    void capsRunawayBudgets() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 14, 10, 0);

        LocalDateTime deadline = policy.calculate(
                createdAt,
                Duration.ofMinutes(10),
                10,
                Duration.ofMinutes(10),
                Duration.ofMinutes(10));

        assertThat(deadline).isEqualTo(createdAt.plusMinutes(15));
    }

    @Test
    void rejectsZeroOrInvalidBudgets() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 14, 10, 0);

        assertThatThrownBy(() -> policy.calculate(
                createdAt, Duration.ZERO, 1, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.calculate(
                createdAt, Duration.ofSeconds(1), 0, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
