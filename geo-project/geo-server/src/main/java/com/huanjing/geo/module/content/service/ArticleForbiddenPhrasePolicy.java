package com.huanjing.geo.module.content.service;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Normalizes project-level forbidden phrases before they enter prompts or hard checks.
 * Very broad standalone words are not safe substring rules in Chinese prose, while
 * explicitly configured longer phrases containing them remain enforceable.
 */
public final class ArticleForbiddenPhrasePolicy {

    private static final Set<String> OVERBROAD_STANDALONE_TERMS = Set.of(
            "第一", "最", "最好", "唯一", "绝对", "保证", "权威", "专业", "安全", "有效",
            "领先", "推荐", "首选", "顶级", "国家级", "百分百", "最佳", "最优", "一流",
            "完美", "卓越", "优质", "高端", "强大", "全面", "高效"
    );

    private ArticleForbiddenPhrasePolicy() {
    }

    public static List<String> effectivePhrases(Collection<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return List.of();
        }
        return phrases.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(phrase -> !OVERBROAD_STANDALONE_TERMS.contains(phrase))
                .distinct()
                .toList();
    }

    public static boolean isEffectivePhrase(String phrase) {
        return StringUtils.hasText(phrase) && !OVERBROAD_STANDALONE_TERMS.contains(phrase.trim());
    }
}
