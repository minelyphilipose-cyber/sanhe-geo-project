package com.huanjing.geo.module.presale.generate.l3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

final class SignalKeyLabelMap {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("coverage_rate", "覆盖率"),
            Map.entry("covered_prompts", "已覆盖查询数"),
            Map.entry("total_prompts", "查询总数"),
            Map.entry("high_value_covered", "已覆盖高价值问题数"),
            Map.entry("high_value_total", "高价值问题总数"),
            Map.entry("uncovered_rate", "未覆盖率"),
            Map.entry("top_competitor_coverage_rate", "头部竞品覆盖率"),
            Map.entry("industry_avg_overall", "行业均值"),
            Map.entry("overall_score", "综合得分"),
            Map.entry("top1_overall", "行业第一得分"),
            Map.entry("affected_platform_count", "涉及平台数"),
            Map.entry("affected_platforms_text", "涉及平台"),
            Map.entry("key_topic", "主要话题"),
            Map.entry("negative_count", "负面提及数"),
            Map.entry("negative_evidence_count", "真实负面证据数"),
            Map.entry("missed_count", "缺失场景数"),
            Map.entry("missed_scenes_text", "缺失场景列表"),
            Map.entry("weak_platforms_text", "弱势平台"),
            Map.entry("strong_platforms_text", "强势平台"),
            Map.entry("sentiment_score", "情感分"),
            Map.entry("positive_count", "正面提及数"),
            Map.entry("neutral_count", "中性提及数"),
            Map.entry("covered_platform_count", "覆盖平台数"),
            Map.entry("uncovered_platform_count", "未覆盖平台数"),
            Map.entry("uncovered_platforms_text", "未覆盖平台"),
            Map.entry("effective_platforms", "有效平台数"),
            Map.entry("degraded_count", "降级平台数"),
            Map.entry("degraded_platforms_text", "降级平台"),
            Map.entry("dominant_platform_name", "主导平台"),
            Map.entry("dominant_count", "主导平台首推次数"),
            Map.entry("total_primary", "首推总次数"),
            Map.entry("dominant_ratio", "主导平台占比")
    );

    private static final Set<String> PERCENT_KEYS = Set.of(
            "coverage_rate", "uncovered_rate", "top_competitor_coverage_rate", "dominant_ratio"
    );

    private SignalKeyLabelMap() {
    }

    static String format(String key, Object value) {
        String label = LABELS.getOrDefault(key, key);
        String formatted = formatValue(value);
        if (PERCENT_KEYS.contains(key) && formatted != null && !formatted.endsWith("%")) {
            formatted = formatted + "%";
        }
        return label + ":" + formatted;
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString()).setScale(0, RoundingMode.HALF_UP);
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
