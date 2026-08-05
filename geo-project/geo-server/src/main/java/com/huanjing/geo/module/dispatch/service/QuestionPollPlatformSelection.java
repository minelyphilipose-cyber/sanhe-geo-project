package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.UsageScene;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies the question-poll routing contract once per channel: an enabled Web profile wins;
 * otherwise the enabled native STANDARD_CHAT profile is used.
 */
final class QuestionPollPlatformSelection {

    private static final Comparator<AiPlatformConfig> STABLE_ORDER = Comparator
            .comparing(AiPlatformConfig::getPriorityLevel,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(AiPlatformConfig::getId, Comparator.nullsLast(Long::compareTo));

    private QuestionPollPlatformSelection() {
    }

    static List<AiPlatformConfig> preferredEnabled(List<AiPlatformConfig> rows) {
        return preferred(rows, true);
    }

    static List<AiPlatformConfig> preferredForOptions(List<AiPlatformConfig> rows) {
        return preferred(rows, false);
    }

    static boolean supportsQuestionPollScene(AiPlatformConfig config) {
        if (config == null || !StringUtils.hasText(config.getUsageScene())) {
            return false;
        }
        return UsageScene.STANDARD_CHAT.name().equalsIgnoreCase(config.getUsageScene())
                || UsageScene.QUESTION_POLL_WEB.name().equalsIgnoreCase(config.getUsageScene());
    }

    private static List<AiPlatformConfig> preferred(List<AiPlatformConfig> rows, boolean eligibleOnly) {
        Map<String, List<AiPlatformConfig>> byChannel = new LinkedHashMap<>();
        if (rows != null) {
            rows.stream()
                    .filter(QuestionPollPlatformSelection::supportsQuestionPollScene)
                    .sorted(STABLE_ORDER)
                    .forEach(row -> byChannel.computeIfAbsent(channel(row), ignored -> new ArrayList<>()).add(row));
        }
        return byChannel.values().stream()
                .map(group -> select(group, eligibleOnly))
                .filter(java.util.Objects::nonNull)
                .sorted(STABLE_ORDER)
                .toList();
    }

    private static AiPlatformConfig select(List<AiPlatformConfig> group, boolean eligibleOnly) {
        AiPlatformConfig enabledWeb = first(group, true, true);
        if (enabledWeb != null) {
            return enabledWeb;
        }
        AiPlatformConfig enabledNative = first(group, false, true);
        if (enabledNative != null) {
            return enabledNative;
        }
        if (eligibleOnly) {
            return null;
        }
        AiPlatformConfig configuredWeb = first(group, true, false);
        return configuredWeb != null ? configuredWeb : first(group, false, false);
    }

    private static AiPlatformConfig first(List<AiPlatformConfig> group, boolean web, boolean requireEnabled) {
        return group.stream()
                .filter(row -> isWeb(row) == web)
                .filter(row -> !requireEnabled || (Boolean.TRUE.equals(row.getEnabled())
                        && Boolean.TRUE.equals(row.getEnabledForQuestionPoll())))
                .findFirst()
                .orElse(null);
    }

    private static boolean isWeb(AiPlatformConfig config) {
        if (!StringUtils.hasText(config.getIntegrationType())) {
            return false;
        }
        try {
            return IntegrationType.valueOf(config.getIntegrationType().trim().toUpperCase(Locale.ROOT)).isWebSearch();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String channel(AiPlatformConfig config) {
        String value = StringUtils.hasText(config.getChannelCode())
                ? config.getChannelCode() : config.getPlatformCode();
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
