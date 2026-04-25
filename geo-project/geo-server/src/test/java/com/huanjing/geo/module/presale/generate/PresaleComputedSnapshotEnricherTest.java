package com.huanjing.geo.module.presale.generate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.RoiSimulation;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.generate.calc.RoiCalculator;
import com.huanjing.geo.module.presale.generate.calc.SceneAndIntentResult;
import com.huanjing.geo.module.presale.generate.calc.SceneCoverageCalculator;
import com.huanjing.geo.module.presale.generate.calc.ScoresCalculator;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.ruleengine.PresaleRuleEngineExecutor;
import com.huanjing.geo.module.presale.ruleengine.RuleEngineResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleComputedSnapshotEnricherTest {

    @Mock
    private PlatformIntentBreakdownBuilder builder;
    @Mock
    private PlatformIntentBreakdownValidator validator;
    @Mock
    private SceneCoverageCalculator sceneCoverageCalculator;
    @Mock
    private ScoresCalculator scoresCalculator;
    @Mock
    private PresaleRuleEngineExecutor ruleEngineExecutor;
    @Mock
    private RoiCalculator roiCalculator;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;

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
        org.mockito.Mockito.when(mapper.selectIntentSamplesByVersionId(anyLong())).thenReturn(List.of());
        org.mockito.Mockito.when(mapper.selectTemplateIntentStats(org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of(
                        templateRow("推荐型", 200),
                        templateRow("对比型", 200),
                        templateRow("问题型", 200),
                        templateRow("认知型", 200),
                        templateRow("场景型", 200)
                ));
        org.mockito.Mockito.when(mapper.selectList(any())).thenReturn(List.of());

        PlatformIntentBreakdownBuilder realBuilder = new PlatformIntentBreakdownBuilder(mapper);
        PlatformIntentBreakdownValidator realValidator = new PlatformIntentBreakdownValidator();

        SceneCoverageCalculator sceneCalc = org.mockito.Mockito.mock(SceneCoverageCalculator.class);
        ScoresCalculator scoresCalc = org.mockito.Mockito.mock(ScoresCalculator.class);
        PresaleRuleEngineExecutor engine = org.mockito.Mockito.mock(PresaleRuleEngineExecutor.class);
        RoiCalculator roiCalc = org.mockito.Mockito.mock(RoiCalculator.class);
        org.mockito.Mockito.when(sceneCalc.compute(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Integer> totals = inv.getArgument(2, Map.class);
            return sceneAndIntentFromTotals(totals);
        });
        org.mockito.Mockito.when(scoresCalc.compute(any(), any(), any())).thenReturn(defaultScores());
        org.mockito.Mockito.when(engine.execute(any(), any())).thenReturn(defaultRuleResult());
        org.mockito.Mockito.when(roiCalc.compute(any(), any())).thenReturn(defaultRoi());

        PresaleComputedSnapshotEnricher enricher = new PresaleComputedSnapshotEnricher(
                new ObjectMapper(), realBuilder, realValidator, sceneCalc, scoresCalc, engine, roiCalc, mapper);

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

    @Test
    void enrich_fillsScoresField() throws Exception {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();
        String out = enricher.enrichAndValidate(1L, minimalWrappedRaw(), "{}", true);
        JsonNode node = new ObjectMapper().readTree(out);

        assertThat(node.path("scores").path("overall").asDouble()).isEqualTo(88.0);
        assertThat(node.path("scores").path("weights").path("mention").asDouble()).isEqualTo(0.30);
    }

    @Test
    void enrich_fillsSceneCoverageField() throws Exception {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();
        String out = enricher.enrichAndValidate(1L, minimalWrappedRaw(), "{}", true);
        JsonNode node = new ObjectMapper().readTree(out);

        assertThat(node.path("scene_coverage").path("high_value").path("total").asInt()).isEqualTo(1);
        assertThat(node.path("scene_coverage").path("high_value").path("covered").asInt()).isEqualTo(1);
    }

    @Test
    void enrich_invokesRuleEngineAfterScoresAndSceneCoverage() {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();
        when(ruleEngineExecutor.execute(any(), any())).thenAnswer(inv -> {
            ComputedSnapshotDTO l2 = inv.getArgument(1, ComputedSnapshotDTO.class);
            assertNotNull(l2.getScores());
            assertNotNull(l2.getSceneCoverage());
            return defaultRuleResult();
        });

        enricher.enrichAndValidate(1L, minimalWrappedRaw(), "{}", true);

        InOrder inOrder = org.mockito.Mockito.inOrder(sceneCoverageCalculator, scoresCalculator, ruleEngineExecutor, roiCalculator);
        inOrder.verify(sceneCoverageCalculator).compute(anyLong(), any(), any(), any());
        inOrder.verify(scoresCalculator).compute(any(), any(), any());
        inOrder.verify(ruleEngineExecutor).execute(any(), any());
        inOrder.verify(roiCalculator).compute(any(), any());
    }

    @Test
    void enrich_logsWarnWhenFindingsEmpty() {
        PresaleComputedSnapshotEnricher enricher = createEnricherWithMocks();
        when(ruleEngineExecutor.execute(any(), any())).thenReturn(
                RuleEngineResult.builder()
                        .findings(List.of())
                        .evaluatedRuleCount(1)
                        .hitCount(0)
                        .errors(List.of())
                        .build()
        );

        Logger logger = (Logger) LoggerFactory.getLogger(PresaleComputedSnapshotEnricher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            enricher.enrichAndValidate(1L, minimalWrappedRaw(), "{}", true);
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("No optimization rule triggered for versionId=")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private PresaleComputedSnapshotEnricher createEnricherWithMocks() {
        PresaleComputedSnapshotEnricher enricher = new PresaleComputedSnapshotEnricher(
                new ObjectMapper(),
                builder,
                validator,
                sceneCoverageCalculator,
                scoresCalculator,
                ruleEngineExecutor,
                roiCalculator,
                aiPromptResultMapper
        );

        lenient().when(builder.build(anyLong(), any(), any(), anyBoolean()))
                .thenReturn(new PlatformIntentBreakdownBuilder.BuildResult(
                        List.of(PlatformIntentCell.builder()
                                .platformCode("kimi")
                                .intentCode("RECOMMENDATION")
                                .intentLabel("推荐型")
                                .mentionCount(1)
                                .mentionRate(100)
                                .totalPrompts(1)
                                .platformPromptCount(1)
                                .build()),
                        Map.of(
                                "RECOMMENDATION", 1,
                                "COMPARISON", 0,
                                "INQUIRY", 0,
                                "COGNITIVE", 0,
                                "SCENARIO", 0
                        )));
        doNothing().when(validator).validate(any(), any(), any());
        lenient().when(sceneCoverageCalculator.compute(anyLong(), any(), any(), any())).thenReturn(defaultSceneAndIntent());
        lenient().when(scoresCalculator.compute(any(), any(), any())).thenReturn(defaultScores());
        lenient().when(ruleEngineExecutor.execute(any(), any())).thenReturn(defaultRuleResult());
        lenient().when(roiCalculator.compute(any(), any())).thenReturn(defaultRoi());
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(rankingRow(1), rankingRow(2)));
        return enricher;
    }

    private String minimalWrappedRaw() {
        return """
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
                      ],
                      "test_summary": {
                        "degraded_platforms": []
                      },
                      "competitors": [
                        {"name": "Claude"}
                      ]
                    }
                  }
                }
                """;
    }

    private SceneAndIntentResult defaultSceneAndIntent() {
        return sceneAndIntentFromTotals(Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        ));
    }

    private SceneAndIntentResult sceneAndIntentFromTotals(Map<String, Integer> totals) {
        return new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().total(1).covered(1).coverageRate(100.0).build())
                        .midValue(SceneCoverageGroup.builder().total(0).covered(0).coverageRate(0.0).build())
                        .lowValue(SceneCoverageGroup.builder().total(0).covered(0).coverageRate(0.0).build())
                        .build(),
                List.of(
                        IntentBreakdown.builder()
                                .category("推荐型")
                                .businessValue("高")
                                .totalPrompts(totals.getOrDefault("RECOMMENDATION", 0))
                                .coveredPrompts(1)
                                .coverageRate(totals.getOrDefault("RECOMMENDATION", 0) == 0 ? 0.0 : 100.0)
                                .avgRanking(1.0)
                                .build(),
                        IntentBreakdown.builder()
                                .category("对比型")
                                .businessValue("高")
                                .totalPrompts(totals.getOrDefault("COMPARISON", 0))
                                .coveredPrompts(0)
                                .coverageRate(0.0)
                                .avgRanking(null)
                                .build(),
                        IntentBreakdown.builder()
                                .category("问题型")
                                .businessValue("中")
                                .totalPrompts(totals.getOrDefault("INQUIRY", 0))
                                .coveredPrompts(0)
                                .coverageRate(0.0)
                                .avgRanking(null)
                                .build(),
                        IntentBreakdown.builder()
                                .category("认知型")
                                .businessValue("中")
                                .totalPrompts(totals.getOrDefault("COGNITIVE", 0))
                                .coveredPrompts(0)
                                .coverageRate(0.0)
                                .avgRanking(null)
                                .build(),
                        IntentBreakdown.builder()
                                .category("场景型")
                                .businessValue("低")
                                .totalPrompts(totals.getOrDefault("SCENARIO", 0))
                                .coveredPrompts(0)
                                .coverageRate(0.0)
                                .avgRanking(null)
                                .build()
                )
        );
    }

    private Scores defaultScores() {
        return Scores.builder()
                .overall(88.0)
                .mention(90.0)
                .ranking(80.0)
                .sentiment(85.0)
                .coverage(95.0)
                .weights(Scores.Weights.builder()
                        .mention(0.30)
                        .ranking(0.25)
                        .sentiment(0.15)
                        .coverage(0.30)
                        .build())
                .build();
    }

    private RuleEngineResult defaultRuleResult() {
        OptimizationFinding finding = new OptimizationFinding();
        finding.setFindingId("F001");
        finding.setRuleCode("RULE_X");
        finding.setPriority(OptimizationFinding.Priority.HIGH);
        return RuleEngineResult.builder()
                .findings(List.of(finding))
                .evaluatedRuleCount(1)
                .hitCount(1)
                .errors(List.of())
                .build();
    }

    private RoiSimulation defaultRoi() {
        return RoiSimulation.builder()
                .currentScore(88.0)
                .targetScore(95.0)
                .estimatedUpliftPercent(7.95)
                .estimatedExposureMultiplier(1.8)
                .phases(List.of(
                        RoiSimulation.RoiPhase.builder().phaseNo(1).durationLabel("M1").targetScore(90.0).upliftFromPrevious(2.0).completedOptimizationCount(0).totalOptimizationCount(1).build(),
                        RoiSimulation.RoiPhase.builder().phaseNo(2).durationLabel("M2-3").targetScore(93.0).upliftFromPrevious(3.0).completedOptimizationCount(0).totalOptimizationCount(0).build(),
                        RoiSimulation.RoiPhase.builder().phaseNo(3).durationLabel("M4-6").targetScore(95.0).upliftFromPrevious(2.0).completedOptimizationCount(0).totalOptimizationCount(0).build()
                ))
                .build();
    }

    private PromptTemplateIntentStatRow templateRow(String label, int count) {
        PromptTemplateIntentStatRow row = new PromptTemplateIntentStatRow();
        row.setIntentLabel(label);
        row.setHasCompetitorVar(0);
        row.setTemplateCount(count);
        return row;
    }

    private PresaleAiPromptResult rankingRow(int ranking) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setBatchNo(1);
        row.setRanking(ranking);
        return row;
    }
}
