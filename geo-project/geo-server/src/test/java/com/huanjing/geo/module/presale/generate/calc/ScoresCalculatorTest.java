package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoresCalculatorTest {

    private final ScoresCalculator calculator = new ScoresCalculator();

    @Test
    void happyPath_computesFiveDimensionsAndOverall() {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(List.of(
                platform(5, 10, 3, 1, 1),
                platform(3, 10, 1, 2, 2)
        ));
        SceneAndIntentResult scenes = new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().covered(4).total(5).build())
                        .midValue(SceneCoverageGroup.builder().covered(2).total(4).build())
                        .lowValue(SceneCoverageGroup.builder().covered(1).total(2).build())
                        .build(),
                List.of()
        );
        RankingStats rankingStats = new RankingStats(2, 1, 1, 0, 0, 0);

        var scores = calculator.compute(raw, scenes, rankingStats);

        assertEquals(40.0, scores.getMention(), 0.0001);
        assertEquals(80.0, scores.getRanking(), 0.0001);
        assertEquals(55.0, scores.getSentiment(), 0.0001);
        assertEquals(66.6667, scores.getCoverage(), 0.01);
        assertEquals(60.25, scores.getOverall(), 0.01);
    }

    @Test
    void allZeroMention_mentionIsZero() {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(List.of(
                platform(0, 10, 0, 0, 0),
                platform(0, 10, 0, 0, 0)
        ));
        SceneAndIntentResult scenes = new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().covered(0).total(1).build())
                        .midValue(SceneCoverageGroup.builder().covered(0).total(1).build())
                        .lowValue(SceneCoverageGroup.builder().covered(0).total(1).build())
                        .build(),
                List.of()
        );

        var scores = calculator.compute(raw, scenes, new RankingStats(0, 0, 1, 0, 0, 0));
        assertEquals(0.0, scores.getMention(), 0.0001);
    }

    @Test
    void mentionScore_weightsDoubaoAsDoublePlatform() {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(List.of(
                platform("doubao", 4, 10, 0, 0, 0),
                platform("kimi", 2, 10, 0, 0, 0)
        ));
        SceneAndIntentResult scenes = new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder().build(),
                List.of()
        );

        var scores = calculator.compute(raw, scenes, new RankingStats(0, 0, 0, 0, 0, 0));

        assertEquals(33.3333, scores.getMention(), 0.01);
    }

    @Test
    void allMentionedAtRank1_rankingIsNinetyNotHundred() {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(List.of(platform(1, 1, 1, 0, 0)));
        SceneAndIntentResult scenes = new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().covered(1).total(1).build())
                        .midValue(SceneCoverageGroup.builder().covered(1).total(1).build())
                        .lowValue(SceneCoverageGroup.builder().covered(1).total(1).build())
                        .build(),
                List.of()
        );

        var scores = calculator.compute(raw, scenes, new RankingStats(10, 0, 0, 0, 0, 0));
        assertEquals(90.0, scores.getRanking(), 0.0001);
    }

    @Test
    void upperBoundCap_perfectDataOverallIsNinetySevenPointFive() {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(List.of(
                platform(10, 10, 10, 0, 0)
        ));
        SceneAndIntentResult scenes = new SceneAndIntentResult(
                ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder().covered(2).total(2).build())
                        .midValue(SceneCoverageGroup.builder().covered(2).total(2).build())
                        .lowValue(SceneCoverageGroup.builder().covered(2).total(2).build())
                        .build(),
                List.of()
        );

        var scores = calculator.compute(raw, scenes, new RankingStats(5, 0, 0, 0, 0, 0));

        assertEquals(100.0, scores.getMention(), 0.0001);
        assertEquals(90.0, scores.getRanking(), 0.0001);
        assertEquals(100.0, scores.getSentiment(), 0.0001);
        assertEquals(100.0, scores.getCoverage(), 0.0001);
        assertEquals(97.5, scores.getOverall(), 0.0001);
        assertEquals(0.30, scores.getWeights().getMention(), 0.0001);
        assertEquals(0.25, scores.getWeights().getRanking(), 0.0001);
        assertEquals(0.15, scores.getWeights().getSentiment(), 0.0001);
        assertEquals(0.30, scores.getWeights().getCoverage(), 0.0001);
    }

    private PlatformBreakdown platform(int mentionCount, int totalTests, int pos, int neu, int neg) {
        return platform(null, mentionCount, totalTests, pos, neu, neg);
    }

    private PlatformBreakdown platform(String platformCode, int mentionCount, int totalTests, int pos, int neu, int neg) {
        return PlatformBreakdown.builder()
                .platformCode(platformCode)
                .mentionCount(mentionCount)
                .totalTests(totalTests)
                .sentimentDistribution(PlatformBreakdown.SentimentDistribution.builder()
                        .positive(pos)
                        .neutral(neu)
                        .negative(neg)
                        .build())
                .build();
    }
}
