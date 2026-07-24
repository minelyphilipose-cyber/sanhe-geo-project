package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class BaselineReportSnapshotRules {
    public static final String INTENT_RECOMMENDATION = "RECOMMENDATION";
    public static final String INTENT_COMPARISON = "COMPARISON";
    public static final String INTENT_PROBLEM = "PROBLEM";
    public static final String INTENT_AWARENESS = "AWARENESS";
    public static final String INTENT_SCENE = "SCENE";
    public static final String CELL_STATE_NO_DATA = "NO_DATA";
    public static final String CELL_STATE_INSUFFICIENT_SAMPLE = "INSUFFICIENT_SAMPLE";
    public static final String CELL_STATE_UNSTABLE_PARTIAL = "UNSTABLE_PARTIAL";
    public static final String CELL_STATE_STABLE_ABSENT = "STABLE_ABSENT";
    public static final String CELL_STATE_STABLE_PRESENT = "STABLE_PRESENT";

    public static final Set<String> VALID_INTENT_TYPES = Set.of(
            INTENT_RECOMMENDATION,
            INTENT_COMPARISON,
            INTENT_PROBLEM,
            INTENT_AWARENESS,
            INTENT_SCENE
    );

    private BaselineReportSnapshotRules() {
    }

    public static String mapValueTier(String sourceTier) {
        if (!StringUtils.hasText(sourceTier)) {
            throw new BizException(400, "问题缺少 A/B/C 价值分层");
        }
        return switch (sourceTier.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> "HIGH";
            case "B" -> "MID";
            case "C" -> "LOW";
            default -> throw new BizException(400, "不支持的问题价值分层: " + sourceTier);
        };
    }

    public static String normalizeIntentType(String intentType) {
        if (!StringUtils.hasText(intentType)) {
            throw new BizException(400, "意图类型不能为空");
        }
        String normalized = intentType.trim().toUpperCase(Locale.ROOT);
        if (!VALID_INTENT_TYPES.contains(normalized)) {
            throw new BizException(400, "不支持的意图类型: " + intentType);
        }
        return normalized;
    }

    public static String normalizeValueTier(String valueTier) {
        if (!StringUtils.hasText(valueTier)) {
            throw new BizException(400, "价值分层不能为空");
        }
        String normalized = valueTier.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HIGH", "MID", "LOW" -> normalized;
            default -> throw new BizException(400, "不支持的报告价值分层: " + valueTier);
        };
    }

    public static String classifyIntent(String questionText, String sceneCode, String brandName) {
        String text = normalizeText(questionText);
        String scene = normalizeText(sceneCode);
        String brand = normalizeText(brandName);

        if (containsAny(text, "对比", "比较", "相比", "区别", "pk", "vs", "哪家更", "哪个更")) {
            return INTENT_COMPARISON;
        }
        if ("comparison".equals(scene) || "compare".equals(scene)) {
            return INTENT_COMPARISON;
        }
        if (containsAny(text, "推荐", "哪家好", "哪家靠谱", "哪一个好", "哪个靠谱", "怎么选", "选择哪家", "首选", "榜单")) {
            return INTENT_RECOMMENDATION;
        }
        if ("recommendation".equals(scene) || "decision".equals(scene)) {
            return INTENT_RECOMMENDATION;
        }
        if (StringUtils.hasText(brand) && text.contains(brand)
                && containsAny(text, "怎么样", "是什么", "正规吗", "靠谱吗", "了解", "评价", "口碑", "好不好")) {
            return INTENT_AWARENESS;
        }
        if ("brand".equals(scene) || "awareness".equals(scene)) {
            return INTENT_AWARENESS;
        }
        if (containsAny(text, "预算", "适合", "场景", "准备", "计划", "想要", "需要", "已经", "如果", "能不能")) {
            return INTENT_SCENE;
        }
        if ("scene".equals(scene) || "scenario".equals(scene)) {
            return INTENT_SCENE;
        }
        return INTENT_PROBLEM;
    }

    public static String normalizeMentionType(String mentionType) {
        if (!StringUtils.hasText(mentionType)) {
            return "NONE";
        }
        String normalized = mentionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NONE", "BRAND_EXACT", "BRAND_ALIAS", "SITE_ONLY", "CONTACT_ONLY", "COMPETITOR_ONLY", "INVALID" -> normalized;
            default -> throw new BizException(400, "不支持的提及类型: " + mentionType);
        };
    }

    public static String resolveCellState(int expectedSamples, int successSamples, int positiveSamples) {
        if (successSamples <= 0) {
            return CELL_STATE_NO_DATA;
        }
        if (successSamples < Math.min(2, expectedSamples)) {
            return CELL_STATE_INSUFFICIENT_SAMPLE;
        }
        if (positiveSamples <= 0) {
            return CELL_STATE_STABLE_ABSENT;
        }
        if (positiveSamples >= successSamples) {
            return CELL_STATE_STABLE_PRESENT;
        }
        return CELL_STATE_UNSTABLE_PARTIAL;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
