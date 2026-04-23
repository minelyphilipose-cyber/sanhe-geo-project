package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoresCalculator {

    private static final double W_MENTION = 0.30;
    private static final double W_RANKING = 0.25;
    private static final double W_SENTIMENT = 0.15;
    private static final double W_COVERAGE = 0.30;

    public Scores compute(RawSnapshotDTO raw, SceneAndIntentResult scenes, RankingStats rankingStats) {
        List<PlatformBreakdown> platforms = raw == null || raw.getPlatformBreakdown() == null
                ? List.of() : raw.getPlatformBreakdown();

        int sumMention = platforms.stream().mapToInt(p -> safeInt(p.getMentionCount())).sum();
        int sumTests = platforms.stream().mapToInt(p -> safeInt(p.getTotalTests())).sum();
        double mention = sumTests == 0 ? 0.0 : (sumMention * 100.0 / sumTests);

        RankingStats stats = rankingStats == null ? new RankingStats(0, 0, 0, 0, 0, 0) : rankingStats;
        double ranking;
        if (stats.total() == 0) {
            ranking = 0.0;
        } else {
            double sumScore = stats.count1() * 90.0
                    + stats.count2() * 80.0
                    + stats.count3() * 60.0
                    + stats.count4() * 40.0
                    + stats.count5() * 20.0
                    + stats.countGe6() * 0.0;
            ranking = sumScore / stats.total();
        }

        int sumPos = platforms.stream()
                .mapToInt(p -> p.getSentimentDistribution() == null ? 0 : safeInt(p.getSentimentDistribution().getPositive()))
                .sum();
        int sumNeu = platforms.stream()
                .mapToInt(p -> p.getSentimentDistribution() == null ? 0 : safeInt(p.getSentimentDistribution().getNeutral()))
                .sum();
        int sumNeg = platforms.stream()
                .mapToInt(p -> p.getSentimentDistribution() == null ? 0 : safeInt(p.getSentimentDistribution().getNegative()))
                .sum();
        int totalSent = sumPos + sumNeu + sumNeg;
        double sentiment = totalSent == 0 ? 0.0
                : ((sumPos * 1.0 + sumNeu * 0.5) / totalSent * 100.0);

        double coverage = computeCoverage(scenes);

        double overall = mention * W_MENTION
                + ranking * W_RANKING
                + sentiment * W_SENTIMENT
                + coverage * W_COVERAGE;

        return Scores.builder()
                .overall(overall)
                .mention(mention)
                .ranking(ranking)
                .sentiment(sentiment)
                .coverage(coverage)
                .weights(Scores.Weights.builder()
                        .mention(W_MENTION)
                        .ranking(W_RANKING)
                        .sentiment(W_SENTIMENT)
                        .coverage(W_COVERAGE)
                        .build())
                .build();
    }

    private double computeCoverage(SceneAndIntentResult scenes) {
        if (scenes == null || scenes.sceneCoverage() == null) {
            return 0.0;
        }
        var sc = scenes.sceneCoverage();
        double numerator = safeInt(sc.getHighValue() == null ? null : sc.getHighValue().getCovered()) * 2.0
                + safeInt(sc.getMidValue() == null ? null : sc.getMidValue().getCovered()) * 1.5
                + safeInt(sc.getLowValue() == null ? null : sc.getLowValue().getCovered()) * 1.0;
        double denominator = safeInt(sc.getHighValue() == null ? null : sc.getHighValue().getTotal()) * 2.0
                + safeInt(sc.getMidValue() == null ? null : sc.getMidValue().getTotal()) * 1.5
                + safeInt(sc.getLowValue() == null ? null : sc.getLowValue().getTotal()) * 1.0;
        return denominator == 0 ? 0.0 : (numerator / denominator * 100.0);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}

