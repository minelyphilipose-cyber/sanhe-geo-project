package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.builders.BrandAwarenessLowBuilder;
import com.huanjing.geo.module.presale.ruleengine.builders.CompareGapBuilder;
import com.huanjing.geo.module.presale.ruleengine.builders.CoverageLowRecommendBuilder;
import com.huanjing.geo.module.presale.ruleengine.builders.LowSentimentScoreBuilder;
import com.huanjing.geo.module.presale.ruleengine.builders.NegativeEvidenceBuilder;
import com.huanjing.geo.module.presale.ruleengine.persist.PresaleOptimizationRule;
import com.huanjing.geo.module.presale.ruleengine.persist.PresaleOptimizationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresaleRuleEngineExecutorTest {

    private PresaleOptimizationRuleService ruleService;
    private RuleExpressionEvaluator evaluator;
    private EvidenceDataBuilderRegistry registry;
    private PresaleRuleEngineExecutor executor;

    @BeforeEach
    void setUp() {
        ruleService = Mockito.mock(PresaleOptimizationRuleService.class);
        evaluator = new RuleExpressionEvaluator();
        // 只注册本测试用得到的 Builders
        registry = new EvidenceDataBuilderRegistry(Arrays.asList(
                new CoverageLowRecommendBuilder(),
                new BrandAwarenessLowBuilder(),
                new CompareGapBuilder(),
                new LowSentimentScoreBuilder(),
                new NegativeEvidenceBuilder()
        ));
        executor = new PresaleRuleEngineExecutor(ruleService, evaluator, registry);
    }

    @Test
    void execute_emptyRuleTable_returnsEmptyResult() {
        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(Collections.emptyList());

        RuleEngineResult result = executor.execute(new RawSnapshotDTO(), new ComputedSnapshotDTO());

        assertThat(result.getFindings()).isEmpty();
        assertThat(result.getHitCount()).isEqualTo(0);
        assertThat(result.getEvaluatedRuleCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void execute_brandAwarenessLow_hitsOnLowOverall() {
        PresaleOptimizationRule rule = rule(
                1, RuleCodes.RULE_BRAND_AWARENESS_LOW, "基础设施", "HIGH",
                "#l2.scores.overall < 50", 102);
        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(List.of(rule));

        Scores scores = new Scores(); scores.setOverall(42.0);
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO(); l2.setScores(scores);

        RuleEngineResult result = executor.execute(new RawSnapshotDTO(), l2);

        assertThat(result.getFindings()).hasSize(1);
        OptimizationFinding f = result.getFindings().get(0);
        assertThat(f.getFindingId()).isEqualTo("F001");
        assertThat(f.getRuleCode()).isEqualTo(RuleCodes.RULE_BRAND_AWARENESS_LOW);
        assertThat(f.getPriority()).isEqualTo(OptimizationFinding.Priority.HIGH);
        assertThat(f.getEvidenceData()).containsEntry("overall_score", 42L);
    }

    @Test
    void execute_coverageLowRecommend_highValueSceneCoverageExpressionHits() {
        PresaleOptimizationRule rule = rule(
                1, RuleCodes.RULE_COVERAGE_LOW_RECOMMEND, "基础设施", "HIGH",
                "#l2.sceneCoverage.highValue.coverageRate < 80", 101);
        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(List.of(rule));

        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setSceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(SceneCoverageGroup.builder()
                        .total(22)
                        .covered(8)
                        .coverageRate(36.3636)
                        .build())
                .build());

        RuleEngineResult result = executor.execute(new RawSnapshotDTO(), l2);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFindings()).hasSize(1);
        OptimizationFinding f = result.getFindings().get(0);
        assertThat(f.getRuleCode()).isEqualTo(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND);
        assertThat(f.getEvidenceData()).containsEntry("coverage_rate", 36L);
        assertThat(f.getEvidenceData()).containsEntry("total_prompts", 22);
        assertThat(f.getEvidenceData()).containsEntry("covered_prompts", 8);
        assertThat(f.getEvidenceData()).containsEntry("missed_count", 14);
        assertThat(f.getEvidenceData()).doesNotContainKey("top_competitor_coverage_rate");
    }

    @Test
    void execute_sortsByPriorityThenSortOrder() {
        // LOW + sort_order 100 的规则应排在 HIGH + sort_order 200 的规则后面
        PresaleOptimizationRule lowPri = rule(
                1, RuleCodes.RULE_LOW_SENTIMENT_SCORE, "关系建设", "MEDIUM",
                "#l2.scores.sentiment < 60", 100);
        PresaleOptimizationRule highPri = rule(
                2, RuleCodes.RULE_BRAND_AWARENESS_LOW, "基础设施", "HIGH",
                "#l2.scores.overall < 50", 200);

        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(Arrays.asList(lowPri, highPri));

        Scores scores = new Scores();
        scores.setOverall(40.0);
        scores.setSentiment(50.0);
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO(); l2.setScores(scores);

        SentimentDetail sd = new SentimentDetail();
        sd.setPositiveCount(5); sd.setNeutralCount(5); sd.setNegativeCount(3);
        RawSnapshotDTO l1 = new RawSnapshotDTO(); l1.setSentimentDetail(sd);

        RuleEngineResult result = executor.execute(l1, l2);

        assertThat(result.getFindings()).hasSize(2);
        // HIGH 优先,所以 BRAND_AWARENESS_LOW 应在前
        assertThat(result.getFindings().get(0).getRuleCode()).isEqualTo(RuleCodes.RULE_BRAND_AWARENESS_LOW);
        assertThat(result.getFindings().get(1).getRuleCode()).isEqualTo(RuleCodes.RULE_LOW_SENTIMENT_SCORE);
        // 且 finding_id 按分配顺序递增(命中顺序 = 规则加载顺序,与最终排序无关)
        assertThat(result.getFindings().stream().map(OptimizationFinding::getFindingId))
                .containsOnly("F001", "F002");
    }

    @Test
    void execute_parseError_isCaughtAndReported() {
        PresaleOptimizationRule brokenRule = rule(
                1, RuleCodes.RULE_BRAND_AWARENESS_LOW, "基础设施", "HIGH",
                "#l2.scores.overall < < 50", 100);
        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(List.of(brokenRule));

        RuleEngineResult result = executor.execute(new RawSnapshotDTO(), new ComputedSnapshotDTO());

        assertThat(result.getFindings()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRuleCode()).isEqualTo(RuleCodes.RULE_BRAND_AWARENESS_LOW);
        assertThat(result.getErrors().get(0).getErrorType())
                .isEqualTo(RuleEvaluationError.ErrorType.PARSE);
    }

    @Test
    void execute_unknownRuleCode_producesBuilderMissingError() {
        // 规则命中但 Registry 里没对应 Builder → 作为 BUILDER_MISSING 错误上报,不产出 finding
        // (Codex P1·E r1 修复:避免静默吞掉,保证可观测性)
        PresaleOptimizationRule orphanRule = rule(
                1, "RULE_UNKNOWN_XYZ", "基础设施", "HIGH",
                "true", 100);
        Mockito.when(ruleService.loadEnabledRulesOrdered()).thenReturn(List.of(orphanRule));

        RuleEngineResult result = executor.execute(new RawSnapshotDTO(), new ComputedSnapshotDTO());

        assertThat(result.getFindings()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRuleCode()).isEqualTo("RULE_UNKNOWN_XYZ");
        assertThat(result.getErrors().get(0).getErrorType())
                .isEqualTo(RuleEvaluationError.ErrorType.BUILDER_MISSING);
        assertThat(result.getEvaluatedRuleCount()).isEqualTo(1);
    }

    @Test
    void execute_nullL1OrL2_throwsIllegalArgument() {
        try {
            executor.execute(null, new ComputedSnapshotDTO());
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("L1");
        }
    }

    private PresaleOptimizationRule rule(int id, String code, String category, String priority,
                                         String expr, int sortOrder) {
        PresaleOptimizationRule r = new PresaleOptimizationRule();
        r.setId((long) id);
        r.setRuleCode(code);
        r.setRuleName(code);
        r.setCategory(category);
        r.setDefaultPriority(priority);
        r.setTriggerExpression(expr);
        r.setTitleTemplate("t");
        r.setDescriptionTemplate("d");
        r.setEvidenceTemplate("e");
        r.setEnabled(true);
        r.setSortOrder(sortOrder);
        return r;
    }
}
