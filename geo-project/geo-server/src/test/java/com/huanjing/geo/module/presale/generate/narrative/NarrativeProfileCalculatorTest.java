package com.huanjing.geo.module.presale.generate.narrative;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativeProfileCalculatorTest {

    private NarrativeProfileCalculator calculator;

    @BeforeEach
    void setUp() {
        NarrativeConfigService configService = mock(NarrativeConfigService.class);
        when(configService.load(any())).thenReturn(NarrativeConfigService.NarrativeConfigSnapshot.builder()
                .configVersion("v1")
                .bandRules(List.of())
                .lexicon(NarrativeConfigService.IndustryLexicon.builder()
                        .customerTerm("客户")
                        .conversionTerm("转化")
                        .industryShort("行业")
                        .fallback(true)
                        .build())
                .build());
        calculator = new NarrativeProfileCalculator(configService);
    }

    @Test
    void compressedIndustry_nearTop1UsesHighPriorityLeaderInsteadOfMiddle() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 65.0);
        ComputedSnapshotDTO computed = computedWithScores(60.0, 70.0, 70.0);

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getBand()).isEqualTo(NarrativeProfile.Band.LEADER);
        assertThat(profile.getDiagnostics()).containsEntry("band_reason", "leader_high_priority");
    }

    @Test
    void leaderRatioUsesLeaderGateAndDowngradesWhenMentionCoverageBelowSixtyFive() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 65.0);
        ComputedSnapshotDTO computed = computedWithScores(60.0, 60.0, 60.0);

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getBand()).isEqualTo(NarrativeProfile.Band.MIDDLE);
        assertThat(profile.getDiagnostics()).containsEntry("band_reason", "leader_ratio_gate_downgraded");
    }

    @Test
    void strongRatioButMentionCoverageGateFails_downgradesToMiddleNotFallback() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(68.0, 20.0, 45.0);

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getBand()).isEqualTo(NarrativeProfile.Band.MIDDLE);
        assertThat(profile.getFallback()).isFalse();
        assertThat(profile.getDiagnostics()).containsEntry("band_reason", "strong_ratio_gate_downgraded");
    }

    @Test
    void neutralShareTriggersSentimentThinButNotNegativePressure() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        raw.setSentimentDetail(SentimentDetail.builder()
                .positiveCount(5)
                .neutralCount(90)
                .negativeCount(5)
                .topKeywords(List.of(SentimentDetail.SentimentKeyword.builder()
                        .keyword("一般")
                        .sentiment(SentimentDetail.Sentiment.NEUTRAL)
                        .build()))
                .negativeEvidence(List.of(SentimentDetail.NegativeEvidence.builder()
                        .sentiment(SentimentDetail.Sentiment.NEUTRAL)
                        .snippet("价格偏高")
                        .build()))
                .build());
        ComputedSnapshotDTO computed = computedWithScores(55.0, 50.0, 50.0);

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getArchetypePrimary()).isNotEqualTo(NarrativeProfile.Archetype.NEGATIVE_PRESSURE);
        assertThat(profile.getDisplayFlags().getShowNegativeBox()).isFalse();
        assertThat(profile.getFindingTiers()).anyMatch(item -> "SENTIMENT_THIN".equals(item.getCode()));
    }

    @Test
    void pollutedNegativeRuleIsSuppressedWhenTrueNegativeIsFalse() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        raw.setSentimentDetail(SentimentDetail.builder()
                .positiveCount(5)
                .neutralCount(90)
                .negativeCount(5)
                .topKeywords(List.of(SentimentDetail.SentimentKeyword.builder()
                        .keyword("一般")
                        .sentiment(SentimentDetail.Sentiment.NEUTRAL)
                        .build()))
                .negativeEvidence(List.of(SentimentDetail.NegativeEvidence.builder()
                        .sentiment(SentimentDetail.Sentiment.NEUTRAL)
                        .snippet("价格偏高")
                        .build()))
                .build());
        ComputedSnapshotDTO computed = computedWithScores(55.0, 50.0, 50.0);
        computed.setOptimizationFindings(List.of(OptimizationFinding.builder()
                .ruleCode(RuleCodes.RULE_NEGATIVE_EVIDENCE)
                .priority(OptimizationFinding.Priority.HIGH)
                .build()));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getFindingTiers()).noneMatch(item -> "NEGATIVE_PRESSURE".equals(item.getDedupeKey()));
        assertThat(profile.getDisplayFlags().getShowNegativeBox()).isFalse();
    }

    @Test
    void comparisonPreferenceWithoutRecommendationEvidenceUsesSoftMetric() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        raw.setCompetitors(List.of(Competitor.builder()
                .name("竞品")
                .targetPreferredCount(1)
                .competitorPreferredCount(4)
                .competitorPreferredRate(80.0)
                .build()));
        ComputedSnapshotDTO computed = computedWithScores(55.0, 50.0, 50.0);

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getDisplayFlags().getAllowCompetitorOvertakeClaim()).isFalse();
        assertThat(profile.getDisplayFlags().getComparisonMetric())
                .isEqualTo(NarrativeProfile.ComparisonMetric.COMPARISON_PREFERENCE);
        assertThat(profile.getFindingTiers()).anyMatch(item -> "COMPETITOR_OVERTAKE_SOFT".equals(item.getCode()));
    }

    @Test
    void recommendationMissingWithCompetitorCoverageAllowsStrongOvertakeClaim() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(45.0, 50.0, 50.0);
        computed.setSceneCompetitorPressure(pressure(2, 3));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getDisplayFlags().getAllowCompetitorOvertakeClaim()).isTrue();
        assertThat(profile.getDisplayFlags().getComparisonMetric())
                .isEqualTo(NarrativeProfile.ComparisonMetric.RECOMMENDATION_PRESENCE);
    }

    @Test
    void highAbsenceWithoutStrictSuppressionUsesUrgentButNoStrongCompetitorClaim() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(45.0, 50.0, 50.0);
        computed.setSceneCompetitorPressure(pressure(0, 9));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getCompetitorStory().getTier()).isEqualTo(NarrativeProfile.CompetitorStoryTier.T1);
        assertThat(profile.getCompetitorStory().getClientAbsentCount()).isEqualTo(9);
        assertThat(profile.getCompetitorStory().getAbsenceRatio()).isEqualTo(1D);
        assertThat(profile.getCompetitorStory().getTitle()).contains("9/9");
        assertThat(profile.getCompetitorStory().getTitle()).doesNotContain("报的是对手", "没有你");
        assertThat(profile.getCompetitorStory().getLandingCopy()).doesNotContain("指给别人");
        assertThat(profile.getDisplayFlags().getAllowCompetitorOvertakeClaim()).isFalse();
    }

    @Test
    void noAbsenceUsesPositiveT4Story() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(45.0, 50.0, 50.0);
        computed.setSceneCompetitorPressure(pressureWithAbsent(0, 4, 0));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getCompetitorStory().getTier()).isEqualTo(NarrativeProfile.CompetitorStoryTier.T4);
        assertThat(profile.getCompetitorStory().getClientAbsentCount()).isZero();
        assertThat(profile.getCompetitorStory().getTitle()).contains("已在多数");
    }

    @Test
    void strongBandCapsCompetitorStoryAtT2WhenPressureRatioWouldBeT1() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(70.0, 70.0, 70.0);
        computed.setSceneCompetitorPressure(pressure(2, 3));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getBand()).isEqualTo(NarrativeProfile.Band.STRONG);
        assertThat(profile.getCompetitorStory().getTier()).isEqualTo(NarrativeProfile.CompetitorStoryTier.T2);
        assertThat(profile.getCompetitorStory().getTitle()).doesNotContain("报的是对手", "没有你");
        assertThat(profile.getCompetitorStory().getLandingCopy()).doesNotContain("指给别人");
    }

    @Test
    void leaderBandCapsCompetitorStoryAtT3WhenPressureRatioWouldBeT1() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 65.0);
        ComputedSnapshotDTO computed = computedWithScores(60.0, 70.0, 70.0);
        computed.setSceneCompetitorPressure(pressure(2, 3));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getBand()).isEqualTo(NarrativeProfile.Band.LEADER);
        assertThat(profile.getCompetitorStory().getTier()).isEqualTo(NarrativeProfile.CompetitorStoryTier.T3);
        assertThat(profile.getCompetitorStory().getTitle()).doesNotContain("报的是对手", "没有你");
        assertThat(profile.getCompetitorStory().getLandingCopy()).doesNotContain("指给别人");
    }

    @Test
    void heatmapAverageSkipsIntentWithoutSamples() {
        RawSnapshotDTO raw = rawWithBenchmark(55.0, 90.0);
        ComputedSnapshotDTO computed = computedWithScores(55.0, 50.0, 50.0);
        computed.setPlatformIntentBreakdown(List.of(
                cell("RECOMMENDATION", 10, null, 10),
                cell("INQUIRY", null, null, null),
                cell("SCENARIO", null, null, null)
        ));

        NarrativeProfile profile = calculator.compute(raw, computed);

        assertThat(profile.getHeatmapPattern()).isEqualTo(NarrativeProfile.HeatmapPattern.RECO_EMERGING);
    }

    private RawSnapshotDTO rawWithBenchmark(double avg, double top1) {
        return RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder().industry("口腔医疗").brandName("测试品牌").build())
                .benchmarksFrozen(BenchmarksFrozen.builder()
                        .industryAvg(ScoreSet.builder().overall(avg).build())
                        .top1(ScoreSet.builder().overall(top1).build())
                        .build())
                .build();
    }

    private ComputedSnapshotDTO computedWithScores(double overall, double mention, double coverage) {
        ComputedSnapshotDTO computed = ComputedSnapshotDTO.builder()
                .scores(Scores.builder()
                        .overall(overall)
                        .mention(mention)
                        .coverage(coverage)
                        .sentiment(50.0)
                        .ranking(null)
                        .build())
                .platformIntentBreakdown(List.of(
                        cell("RECOMMENDATION", 0, null, 10),
                        cell("RECOMMENDATION", 10, null, 10),
                        cell("INQUIRY", 30, null, 10),
                        cell("SCENARIO", 30, null, 10),
                        cell("COGNITIVE", null, 70, null),
                        cell("COMPARISON", null, 45, null)
                ))
                .sceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().missingQueries(List.of()).build())
                        .build())
                .optimizationFindings(List.of())
                .build();
        return computed;
    }

    private PlatformIntentCell cell(String intent, Integer mentionRate, Integer judgeScore, Integer promptCount) {
        return PlatformIntentCell.builder()
                .platformCode("kimi")
                .intentCode(intent)
                .intentLabel(intent)
                .mentionRate(mentionRate)
                .judgeScore(judgeScore)
                .platformPromptCount(promptCount)
                .judgeSampleCount(judgeScore == null ? null : 1)
                .build();
    }

    private SceneCompetitorPressure pressure(int suppressed, int total) {
        return pressureWithAbsent(suppressed, total, total);
    }

    private SceneCompetitorPressure pressureWithAbsent(int suppressed, int total, int absent) {
        List<SceneCompetitorPressure.Item> items = java.util.stream.IntStream.range(0, total)
                .mapToObj(index -> SceneCompetitorPressure.Item.builder()
                        .promptCode("REC-" + (index + 1))
                        .query("附近推荐" + (index + 1))
                        .intent("推荐型")
                        .targetMentionedPlatformCount(index < absent ? 0 : 1)
                        .platformsEvaluated(3)
                        .suppressed(index < suppressed)
                        .competitors(List.of(SceneCompetitorPressure.CompetitorPressure.builder()
                                .name("竞品")
                                .mentionedPlatformCount(index < suppressed ? 2 : 1)
                                .build()))
                        .build())
                .toList();
        return SceneCompetitorPressure.builder()
                .hvRecoTotal(total)
                .suppressedSceneCount(suppressed)
                .topSuppressingCompetitor("竞品")
                .items(items)
                .build();
    }
}
