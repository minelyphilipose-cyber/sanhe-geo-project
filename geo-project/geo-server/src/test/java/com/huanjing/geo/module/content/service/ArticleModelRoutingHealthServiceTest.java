package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.llm.health.ArticleModelHealthPolicy;
import com.huanjing.geo.common.llm.measurement.LlmCallObservation;
import com.huanjing.geo.common.llm.measurement.LlmCallObservationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleModelRoutingHealthServiceTest {

    @Test
    void healthLatencyUsesHttpDurationInsteadOfPermitWaitAndTotalDuration() {
        LlmCallObservationMapper mapper = mock(LlmCallObservationMapper.class);
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectRecentForFeature(eq("qwen"), eq("article"), any(), anyInt()))
                .thenReturn(List.of(
                        success(now.minusMinutes(1), 260_000L, 50_000L),
                        success(now.minusMinutes(2), 250_000L, 55_000L),
                        success(now.minusMinutes(3), 240_000L, 60_000L)
                ));
        ArticleModelRoutingHealthService service = new ArticleModelRoutingHealthService(mapper);

        Map<String, ArticleModelHealthPolicy.Evaluation> evaluations = service.assess(List.of("qwen"));

        ArticleModelHealthPolicy.Evaluation evaluation = evaluations.get("qwen");
        assertThat(evaluation.level()).isEqualTo(ArticleModelHealthPolicy.HealthLevel.HEALTHY);
        assertThat(evaluation.p90DurationMs()).isEqualTo(60_000L);
    }

    private LlmCallObservation success(LocalDateTime occurredAt, long totalMs, long httpMs) {
        LlmCallObservation observation = new LlmCallObservation();
        observation.setStatus("success");
        observation.setOccurredAt(occurredAt);
        observation.setTotalMs(totalMs);
        observation.setHttpMs(httpMs);
        return observation;
    }
}
