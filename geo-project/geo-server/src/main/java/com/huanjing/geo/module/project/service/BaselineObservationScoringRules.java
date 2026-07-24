package com.huanjing.geo.module.project.service;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BaselineObservationScoringRules {
    private static final List<String> POSITIVE_WORDS = List.of("推荐", "靠谱", "优秀", "领先", "专业", "好评", "值得", "首选");
    private static final List<String> NEGATIVE_WORDS = List.of("不推荐", "差评", "投诉", "风险", "负面", "不靠谱", "谨慎", "骗子", "欺诈", "违规", "价格高", "售后差");
    private static final List<String> NO_AWARENESS_WORDS = List.of("不了解", "没有了解", "不清楚", "无认知", "不知道");
    private static final List<String> INFO_MISSING_WORDS = List.of("未找到", "没有检索到", "信息不足", "资料有限", "无法确认");

    private BaselineObservationScoringRules() {
    }

    static BaselineObservationScoringResult score(String responseText,
                                                  String questionIntentType,
                                                  String brandName,
                                                  List<String> aliases) {
        BaselineObservationScoringResult result = new BaselineObservationScoringResult();
        String response = responseText == null ? "" : responseText;
        String lower = response.toLowerCase(Locale.ROOT);
        String mentionType = resolveMentionType(response, brandName, aliases);
        boolean mentioned = !"NONE".equals(mentionType);
        boolean negative = containsAny(lower, NEGATIVE_WORDS);
        boolean positive = containsAny(lower, POSITIVE_WORDS) && !negative;
        boolean noAwareness = containsAny(lower, NO_AWARENESS_WORDS);
        boolean infoMissing = containsAny(lower, INFO_MISSING_WORDS);

        result.setMentioned(mentioned);
        result.setMentionType(mentionType);
        result.setRecommended(mentioned
                && BaselineReportSnapshotRules.INTENT_RECOMMENDATION.equals(questionIntentType)
                && containsAny(lower, POSITIVE_WORDS));
        result.setRankingPosition(null);
        result.setSentiment(resolveSentiment(mentioned, positive, negative, noAwareness || infoMissing));
        result.setImpressionState(resolveImpressionState(mentioned, positive, negative, noAwareness, infoMissing));
        result.setJudgeEvidence(buildEvidence(brandName, aliases, mentionType));
        return result;
    }

    static List<CompetitorHit> findCompetitorHits(String responseText, List<CompetitorName> competitors) {
        if (!StringUtils.hasText(responseText) || competitors == null || competitors.isEmpty()) {
            return List.of();
        }
        List<CompetitorHit> hits = new ArrayList<>();
        for (CompetitorName competitor : competitors) {
            for (String term : competitor.matchTerms()) {
                int start = responseText.indexOf(term);
                if (start >= 0) {
                    int count = countOccurrences(responseText, term);
                    hits.add(new CompetitorHit(competitor.id(), competitor.name(), term, competitor.tracked(), start,
                            start + term.length(), count));
                    break;
                }
            }
        }
        return hits;
    }

    private static String resolveMentionType(String response, String brandName, List<String> aliases) {
        if (StringUtils.hasText(brandName) && response.contains(brandName.trim())) {
            return "BRAND_EXACT";
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias) && response.contains(alias.trim())) {
                    return "BRAND_ALIAS";
                }
            }
        }
        return "NONE";
    }

    private static String resolveSentiment(boolean mentioned, boolean positive, boolean negative, boolean missingAwareness) {
        if (!mentioned || missingAwareness) {
            return "UNKNOWN";
        }
        if (negative) {
            return "NEGATIVE";
        }
        if (positive) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private static String resolveImpressionState(boolean mentioned,
                                                 boolean positive,
                                                 boolean negative,
                                                 boolean noAwareness,
                                                 boolean infoMissing) {
        if (noAwareness) {
            return "NO_AWARENESS";
        }
        if (infoMissing || !mentioned) {
            return "INFO_MISSING";
        }
        if (negative) {
            return "NEGATIVE";
        }
        if (positive) {
            return "POSITIVE";
        }
        return "NEUTRAL";
    }

    private static String buildEvidence(String brandName, List<String> aliases, String mentionType) {
        if ("BRAND_EXACT".equals(mentionType)) {
            return "命中品牌名称: " + brandName;
        }
        if ("BRAND_ALIAS".equals(mentionType) && aliases != null && !aliases.isEmpty()) {
            return "命中品牌别名";
        }
        return "未命中品牌名称或别名";
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String text, String term) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) >= 0) {
            count++;
            index += term.length();
        }
        return count;
    }

    record CompetitorName(Long id, String name, List<String> aliases, boolean tracked) {
        List<String> matchTerms() {
            List<String> terms = new ArrayList<>();
            if (StringUtils.hasText(name)) {
                terms.add(name.trim());
            }
            if (aliases != null) {
                aliases.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .filter(alias -> !terms.contains(alias))
                        .forEach(terms::add);
            }
            return terms.stream()
                    .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                    .toList();
        }
    }

    record CompetitorHit(Long id, String name, String rawText, boolean tracked, int startOffset, int endOffset, int mentionCount) {
    }
}
