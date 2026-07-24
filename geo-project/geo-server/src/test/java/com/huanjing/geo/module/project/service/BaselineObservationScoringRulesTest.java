package com.huanjing.geo.module.project.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineObservationScoringRulesTest {

    @Test
    void resolveCellState_distinguishesNoDataInsufficientUnstableAndStableCells() {
        assertThat(BaselineReportSnapshotRules.resolveCellState(3, 0, 0))
                .isEqualTo(BaselineReportSnapshotRules.CELL_STATE_NO_DATA);
        assertThat(BaselineReportSnapshotRules.resolveCellState(3, 1, 1))
                .isEqualTo(BaselineReportSnapshotRules.CELL_STATE_INSUFFICIENT_SAMPLE);
        assertThat(BaselineReportSnapshotRules.resolveCellState(3, 3, 1))
                .isEqualTo(BaselineReportSnapshotRules.CELL_STATE_UNSTABLE_PARTIAL);
        assertThat(BaselineReportSnapshotRules.resolveCellState(3, 3, 0))
                .isEqualTo(BaselineReportSnapshotRules.CELL_STATE_STABLE_ABSENT);
        assertThat(BaselineReportSnapshotRules.resolveCellState(3, 3, 3))
                .isEqualTo(BaselineReportSnapshotRules.CELL_STATE_STABLE_PRESENT);
    }

    @Test
    void aggregateRules_excludeFailedAndInsufficientSamplesFromRateDenominator() {
        assertThat(BaselineCanonicalAggregateRules.includeInRateDenominator(3, 0)).isFalse();
        assertThat(BaselineCanonicalAggregateRules.includeInRateDenominator(3, 1)).isFalse();
        assertThat(BaselineCanonicalAggregateRules.includeInRateDenominator(3, 2)).isTrue();

        assertThat(BaselineCanonicalAggregateRules.isCovered(3, 1, 1)).isFalse();
        assertThat(BaselineCanonicalAggregateRules.isCovered(3, 2, 1)).isTrue();
        assertThat(BaselineCanonicalAggregateRules.isCovered(3, 3, 1)).isFalse();
        assertThat(BaselineCanonicalAggregateRules.isCovered(3, 3, 2)).isTrue();
    }

    @Test
    void aggregateRules_mapIntentToHeatmapMetricKind() {
        assertThat(BaselineCanonicalAggregateRules.metricKindForIntent(BaselineReportSnapshotRules.INTENT_AWARENESS))
                .isEqualTo(BaselineCanonicalAggregateRules.METRIC_AWARENESS);
        assertThat(BaselineCanonicalAggregateRules.metricKindForIntent(BaselineReportSnapshotRules.INTENT_COMPARISON))
                .isEqualTo(BaselineCanonicalAggregateRules.METRIC_FAVORABILITY);
        assertThat(BaselineCanonicalAggregateRules.metricKindForIntent(BaselineReportSnapshotRules.INTENT_RECOMMENDATION))
                .isEqualTo(BaselineCanonicalAggregateRules.METRIC_MENTION_RATE);
        assertThat(BaselineCanonicalAggregateRules.metricKindForIntent(BaselineReportSnapshotRules.INTENT_PROBLEM))
                .isEqualTo(BaselineCanonicalAggregateRules.METRIC_MENTION_RATE);
        assertThat(BaselineCanonicalAggregateRules.metricKindForIntent(BaselineReportSnapshotRules.INTENT_SCENE))
                .isEqualTo(BaselineCanonicalAggregateRules.METRIC_MENTION_RATE);
    }

    @Test
    void aggregateRules_favorabilityUsesFavorableSamplesInsteadOfMentionSamples() {
        assertThat(BaselineCanonicalAggregateRules.isMetricPositive(
                3, 3, 3, 3, 1, BaselineCanonicalAggregateRules.METRIC_FAVORABILITY
        )).isFalse();
        assertThat(BaselineCanonicalAggregateRules.isMetricPositive(
                3, 3, 1, 1, 2, BaselineCanonicalAggregateRules.METRIC_FAVORABILITY
        )).isTrue();
        assertThat(BaselineCanonicalAggregateRules.isMetricPositive(
                3, 3, 2, 0, 0, BaselineCanonicalAggregateRules.METRIC_MENTION_RATE
        )).isTrue();
    }

    @Test
    void aggregateRules_awarenessUsesSubstantiveAwarenessInsteadOfMentionSamples() {
        assertThat(BaselineCanonicalAggregateRules.isMetricPositive(
                3, 3, 3, 1, 0, BaselineCanonicalAggregateRules.METRIC_AWARENESS
        )).isFalse();
        assertThat(BaselineCanonicalAggregateRules.isMetricPositive(
                3, 3, 1, 2, 0, BaselineCanonicalAggregateRules.METRIC_AWARENESS
        )).isTrue();
    }

    @Test
    void aggregateRules_returnRealWilsonBand() {
        BaselineCanonicalAggregateRules.Band band = BaselineCanonicalAggregateRules.wilsonBand(2, 3);
        assertThat(band.method()).isEqualTo("WILSON_95");
        assertThat(band.low()).isBetween(0D, 1D);
        assertThat(band.high()).isBetween(0D, 1D);
        assertThat(band.low()).isLessThan(band.high());

        BaselineCanonicalAggregateRules.Band noSample = BaselineCanonicalAggregateRules.wilsonBand(0, 0);
        assertThat(noSample.method()).isEqualTo("NO_SAMPLE");
        assertThat(noSample.low()).isZero();
        assertThat(noSample.high()).isZero();
    }

    @Test
    void versionPolicy_expandsBundledVersionsIntoCanonicalMetaDimensions() {
        assertThat(BaselineCanonicalVersionPolicy.expandAlgorithmVersions())
                .containsEntry("mention", BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION)
                .containsEntry("recommendation", BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION)
                .containsEntry("ranking", BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION)
                .containsEntry("sentiment", BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION)
                .containsEntry("impression", BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION)
                .containsEntry("highlight", BaselineCanonicalVersionPolicy.HIGHLIGHT_ALGORITHM_VERSION)
                .containsEntry("competitor_normalization", BaselineCanonicalVersionPolicy.COMPETITOR_NORMALIZATION_VERSION)
                .containsEntry("coverage", BaselineCanonicalVersionPolicy.CANONICAL_AGGREGATE_VERSION)
                .containsEntry("band", BaselineCanonicalVersionPolicy.CANONICAL_AGGREGATE_VERSION)
                .hasSize(9);
    }

    @Test
    void score_marksRecommendationOnlyInRecommendationIntent() {
        BaselineObservationScoringResult recommendation = BaselineObservationScoringRules.score(
                "三合星链比较专业，值得推荐。",
                BaselineReportSnapshotRules.INTENT_RECOMMENDATION,
                "三合星链",
                List.of("三合")
        );
        assertThat(recommendation.isMentioned()).isTrue();
        assertThat(recommendation.isRecommended()).isTrue();
        assertThat(recommendation.getMentionType()).isEqualTo("BRAND_EXACT");
        assertThat(recommendation.getSentiment()).isEqualTo("POSITIVE");

        BaselineObservationScoringResult problem = BaselineObservationScoringRules.score(
                "三合星链比较专业，值得推荐。",
                BaselineReportSnapshotRules.INTENT_PROBLEM,
                "三合星链",
                List.of("三合")
        );
        assertThat(problem.isRecommended()).isFalse();
    }

    @Test
    void score_keepsUnknownSentimentOutOfNonMentionedResponses() {
        BaselineObservationScoringResult result = BaselineObservationScoringRules.score(
                "没有检索到这个品牌的可靠资料。",
                BaselineReportSnapshotRules.INTENT_AWARENESS,
                "三合星链",
                List.of()
        );
        assertThat(result.isMentioned()).isFalse();
        assertThat(result.getSentiment()).isEqualTo("UNKNOWN");
        assertThat(result.getImpressionState()).isEqualTo("INFO_MISSING");
        assertThat(result.getMentionType()).isEqualTo("NONE");
    }

    @Test
    void score_marksMentionedButUnknownBrandKnowledgeAsNoAwareness() {
        BaselineObservationScoringResult result = BaselineObservationScoringRules.score(
                "我不了解三合星链, 资料有限, 无法确认它的服务能力。",
                BaselineReportSnapshotRules.INTENT_AWARENESS,
                "三合星链",
                List.of()
        );
        assertThat(result.isMentioned()).isTrue();
        assertThat(result.getSentiment()).isEqualTo("UNKNOWN");
        assertThat(result.getImpressionState()).isEqualTo("NO_AWARENESS");
        assertThat(result.getMentionType()).isEqualTo("BRAND_EXACT");
    }

    @Test
    void findCompetitorHits_writesTrackedFlagFromVerifiedSource() {
        List<BaselineObservationScoringRules.CompetitorHit> hits = BaselineObservationScoringRules.findCompetitorHits(
                "回答同时提到了微悦和微悦的案例。",
                List.of(new BaselineObservationScoringRules.CompetitorName(7L, "微悦", List.of(), true))
        );
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.id()).isEqualTo(7L);
            assertThat(hit.name()).isEqualTo("微悦");
            assertThat(hit.rawText()).isEqualTo("微悦");
            assertThat(hit.tracked()).isTrue();
            assertThat(hit.mentionCount()).isEqualTo(2);
            assertThat(hit.startOffset()).isEqualTo(7);
        });
    }

    @Test
    void findCompetitorHits_matchesCompetitorAliasesButKeepsCanonicalName() {
        List<BaselineObservationScoringRules.CompetitorHit> hits = BaselineObservationScoringRules.findCompetitorHits(
                "回答提到了小米米家和本地安装服务。",
                List.of(new BaselineObservationScoringRules.CompetitorName(8L, "米家", List.of("小米米家", "米家智能"), true))
        );
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.id()).isEqualTo(8L);
            assertThat(hit.name()).isEqualTo("米家");
            assertThat(hit.rawText()).isEqualTo("小米米家");
            assertThat(hit.startOffset()).isEqualTo(5);
            assertThat(hit.endOffset()).isEqualTo(9);
        });
    }
}
