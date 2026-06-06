package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;

import java.util.List;

final class RuleEvidenceSupport {

    private RuleEvidenceSupport() {
    }

    static IntentBreakdown intent(RuleBuildInput input, String category) {
        return intent(input, category, null);
    }

    static IntentBreakdown intent(RuleBuildInput input, String category, String businessValue) {
        ComputedSnapshotDTO l2 = input == null ? null : input.getL2();
        List<IntentBreakdown> list = l2 == null ? null : l2.getIntentBreakdown();
        if (list == null) return null;
        for (IntentBreakdown item : list) {
            if (item == null) continue;
            if (!category.equals(item.getCategory())) continue;
            if (businessValue != null && !businessValue.equals(item.getBusinessValue())) continue;
            return item;
        }
        return null;
    }

    static int total(IntentBreakdown item) {
        return item == null || item.getTotalPrompts() == null ? 0 : item.getTotalPrompts();
    }

    static int covered(IntentBreakdown item) {
        return item == null || item.getCoveredPrompts() == null ? 0 : item.getCoveredPrompts();
    }

    static long rate(IntentBreakdown item) {
        return Math.round(item == null || item.getCoverageRate() == null ? 0.0 : item.getCoverageRate());
    }

    static SceneCompetitorPressure pressure(RuleBuildInput input) {
        ComputedSnapshotDTO l2 = input == null ? null : input.getL2();
        return l2 == null ? null : l2.getSceneCompetitorPressure();
    }

    static int hvRecoTotal(SceneCompetitorPressure pressure) {
        return pressure == null || pressure.getHvRecoTotal() == null ? 0 : pressure.getHvRecoTotal();
    }

    static List<SceneCompetitorPressure.Item> pressureItems(SceneCompetitorPressure pressure) {
        return pressure == null || pressure.getItems() == null ? List.of() : pressure.getItems();
    }

    static boolean targetAbsent(SceneCompetitorPressure.Item item) {
        Integer count = item == null ? null : item.getTargetMentionedPlatformCount();
        return count == null || count <= 0;
    }

    static int absentCount(SceneCompetitorPressure pressure) {
        int count = 0;
        for (SceneCompetitorPressure.Item item : pressureItems(pressure)) {
            if (targetAbsent(item)) count++;
        }
        return count;
    }

    static int competitorPresentAbsentCount(SceneCompetitorPressure pressure) {
        int count = 0;
        for (SceneCompetitorPressure.Item item : pressureItems(pressure)) {
            if (targetAbsent(item) && hasCompetitorPresent(item)) count++;
        }
        return count;
    }

    static String firstAbsentQuery(SceneCompetitorPressure pressure) {
        for (SceneCompetitorPressure.Item item : pressureItems(pressure)) {
            if (targetAbsent(item) && item.getQuery() != null && !item.getQuery().isBlank()) {
                return item.getQuery();
            }
        }
        return "";
    }

    static String topCompetitorInDisplaySet(SceneCompetitorPressure pressure) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (SceneCompetitorPressure.Item item : pressureItems(pressure)) {
            if (!targetAbsent(item) || item.getCompetitors() == null) continue;
            for (SceneCompetitorPressure.CompetitorPressure competitor : item.getCompetitors()) {
                if (competitor == null || competitor.getName() == null || competitor.getName().isBlank()) continue;
                int mentioned = competitor.getMentionedPlatformCount() == null ? 0 : competitor.getMentionedPlatformCount();
                if (mentioned <= 0) continue;
                counts.merge(competitor.getName(), mentioned, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry.comparingByKey()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    static int topCompetitorMentionedPlatforms(SceneCompetitorPressure pressure) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (SceneCompetitorPressure.Item item : pressureItems(pressure)) {
            if (!targetAbsent(item) || item.getCompetitors() == null) continue;
            for (SceneCompetitorPressure.CompetitorPressure competitor : item.getCompetitors()) {
                if (competitor == null || competitor.getName() == null || competitor.getName().isBlank()) continue;
                int mentioned = competitor.getMentionedPlatformCount() == null ? 0 : competitor.getMentionedPlatformCount();
                if (mentioned <= 0) continue;
                counts.merge(competitor.getName(), mentioned, Integer::sum);
            }
        }
        return counts.values().stream().max(Integer::compareTo).orElse(0);
    }

    static int sentimentSampleCount(RuleBuildInput input) {
        SentimentDetail detail = input == null || input.getL1() == null ? null : input.getL1().getSentimentDetail();
        if (detail == null) return 0;
        return safe(detail.getPositiveCount()) + safe(detail.getNeutralCount()) + safe(detail.getNegativeCount());
    }

    private static boolean hasCompetitorPresent(SceneCompetitorPressure.Item item) {
        if (item == null || item.getCompetitors() == null) return false;
        for (SceneCompetitorPressure.CompetitorPressure competitor : item.getCompetitors()) {
            int mentioned = competitor == null || competitor.getMentionedPlatformCount() == null
                    ? 0 : competitor.getMentionedPlatformCount();
            if (mentioned > 0) return true;
        }
        return false;
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
