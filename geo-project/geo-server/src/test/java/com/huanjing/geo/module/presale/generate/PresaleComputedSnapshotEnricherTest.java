package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleComputedSnapshotEnricherTest {

    @Mock
    private PlatformIntentBreakdownBuilder builder;

    @Mock
    private PlatformIntentBreakdownValidator validator;

    @Test
    void shouldAcceptInputWrappedFixtureShape() {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();

        String wrapped = """
                {
                  "input": {
                    "raw": {
                      "platform_breakdown": [
                        {
                          "platform_code": "kimi",
                          "platform_name": "Kimi",
                          "total_tests": 1,
                          "mention_count": 1,
                          "mention_rate": 100,
                          "avg_ranking": 1.0,
                          "primary_recommendation_count": 1,
                          "sentiment_distribution": {
                            "positive": 1,
                            "neutral": 0,
                            "negative": 0
                          },
                          "is_degraded": false
                        }
                      ]
                    },
                    "computed": {
                      "intent_breakdown": [
                        {
                          "category": "推荐型",
                          "business_value": "高",
                          "total_prompts": 1,
                          "covered_prompts": 1,
                          "coverage_rate": 100.0,
                          "avg_ranking": 1.0
                        }
                      ]
                    }
                  }
                }
                """;

        final String[] out = new String[1];
        assertThatNoException().isThrownBy(() ->
                out[0] = enricher.enrichAndValidate(1L, wrapped, "{}", true));
        assertThat(out[0]).contains("platform_intent_breakdown");
    }

    @Test
    void shouldAcceptCurrentFixtureFromResources() {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();
        String fixture;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("fixtures/01-mock-sample-v1.2.json")) {
            assertThat(is).isNotNull();
            fixture = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final String[] out = new String[1];
        assertThatNoException().isThrownBy(() ->
                out[0] = enricher.enrichAndValidate(1L, fixture, "{}", true));
        assertThat(out[0]).contains("platform_intent_breakdown");
    }

    @Test
    void shouldAcceptCurrentFixtureWithRealBuilderPath() {
        PresaleAiPromptResultMapper mapper = org.mockito.Mockito.mock(PresaleAiPromptResultMapper.class);
        org.mockito.Mockito.when(mapper.selectIntentSamplesByVersionId(ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        org.mockito.Mockito.when(mapper.selectTemplateIntentStats())
                .thenReturn(List.of(
                        templateRow("推荐型", 200),
                        templateRow("对比型", 200),
                        templateRow("问题型", 200),
                        templateRow("认知型", 200),
                        templateRow("场景型", 200)
                ));
        PlatformIntentBreakdownBuilder realBuilder = new PlatformIntentBreakdownBuilder(mapper);
        PlatformIntentBreakdownValidator realValidator = new PlatformIntentBreakdownValidator();
        PresaleComputedSnapshotEnricher enricher = new PresaleComputedSnapshotEnricher(
                new ObjectMapper(), realBuilder, realValidator);

        String fixture;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("fixtures/01-mock-sample-v1.2.json")) {
            assertThat(is).isNotNull();
            fixture = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final String[] out = new String[1];
        assertThatNoException().isThrownBy(() ->
                out[0] = enricher.enrichAndValidate(1L, fixture, "{}", true));
        assertThat(out[0]).contains("intent_breakdown");
        assertThat(out[0]).contains("platform_intent_breakdown");
    }

    private PresaleComputedSnapshotEnricher createEnricherWithMocks() {
        PresaleComputedSnapshotEnricher enricher = new PresaleComputedSnapshotEnricher(
                new ObjectMapper(), builder, validator);
        when(builder.build(
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(true)
        )).thenReturn(new PlatformIntentBreakdownBuilder.BuildResult(
                List.of(PlatformIntentCell.builder()
                        .platformCode("kimi")
                        .intentCode("RECOMMENDATION")
                        .intentLabel("推荐型")
                        .mentionCount(1)
                        .mentionRate(100)
                        .totalPrompts(1)
                        .platformPromptCount(1)
                        .build()),
                java.util.Map.of(
                        "RECOMMENDATION", 1,
                        "COMPARISON", 1,
                        "INQUIRY", 1,
                        "COGNITIVE", 1,
                        "SCENARIO", 1
                )));
        doNothing().when(validator).validate(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
        );
        return enricher;
    }

    private PromptTemplateIntentStatRow templateRow(String label, int count) {
        PromptTemplateIntentStatRow row = new PromptTemplateIntentStatRow();
        row.setIntentLabel(label);
        row.setHasCompetitorVar(0);
        row.setTemplateCount(count);
        return row;
    }
}
