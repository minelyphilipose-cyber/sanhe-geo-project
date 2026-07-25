package com.huanjing.geo.common.llm.health;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleModelHealthPolicyTest {

    @Test
    void recentInfrastructureFailureStartsCooldown() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 12, 0);

        ArticleModelHealthPolicy.Evaluation evaluation = ArticleModelHealthPolicy.evaluate(
                List.of(
                        sample(false, true, null, now.minusMinutes(2)),
                        sample(true, false, 60_000L, now.minusMinutes(3))
                ),
                now
        );

        assertThat(evaluation.level()).isEqualTo(ArticleModelHealthPolicy.HealthLevel.COOLDOWN);
        assertThat(evaluation.routingBlocked()).isTrue();
    }

    @Test
    void recentWindowKeepsHighFailurePlatformAvailableAsHalfOpenProbe() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 12, 0);
        List<ArticleModelHealthPolicy.Sample> samples = List.of(
                sample(false, true, null, now.minusMinutes(20)),
                sample(false, true, null, now.minusMinutes(22)),
                sample(false, true, null, now.minusMinutes(24)),
                sample(true, false, 40_000L, now.minusMinutes(25)),
                sample(true, false, 50_000L, now.minusMinutes(26))
        );

        ArticleModelHealthPolicy.Evaluation evaluation = ArticleModelHealthPolicy.evaluate(samples, now);

        assertThat(evaluation.level()).isEqualTo(ArticleModelHealthPolicy.HealthLevel.HIGH_FAILURE);
        assertThat(evaluation.routingBlocked()).isFalse();
        assertThat(evaluation.routingWeightPercent()).isEqualTo(10);
    }

    @Test
    void articleLatencyUsesLongFormP90InsteadOfThirtySecondThreshold() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 25, 12, 0);
        List<ArticleModelHealthPolicy.Sample> normalSamples = new ArrayList<>();
        normalSamples.add(sample(true, false, 60_000L, now.minusMinutes(1)));
        normalSamples.add(sample(true, false, 80_000L, now.minusMinutes(2)));
        normalSamples.add(sample(true, false, 100_000L, now.minusMinutes(3)));
        List<ArticleModelHealthPolicy.Sample> slowSamples = new ArrayList<>(normalSamples);
        slowSamples.add(sample(true, false, 210_000L, now.minusMinutes(4)));

        ArticleModelHealthPolicy.Evaluation normal = ArticleModelHealthPolicy.evaluate(normalSamples, now);
        ArticleModelHealthPolicy.Evaluation slow = ArticleModelHealthPolicy.evaluate(slowSamples, now);

        assertThat(normal.level()).isEqualTo(ArticleModelHealthPolicy.HealthLevel.HEALTHY);
        assertThat(slow.level()).isEqualTo(ArticleModelHealthPolicy.HealthLevel.SLOW_RESPONSE);
        assertThat(slow.routingWeightPercent()).isEqualTo(50);
    }

    private ArticleModelHealthPolicy.Sample sample(boolean success,
                                                   boolean infrastructureFailure,
                                                   Long durationMs,
                                                   LocalDateTime occurredAt) {
        return new ArticleModelHealthPolicy.Sample(
                success,
                infrastructureFailure,
                durationMs,
                occurredAt
        );
    }
}
