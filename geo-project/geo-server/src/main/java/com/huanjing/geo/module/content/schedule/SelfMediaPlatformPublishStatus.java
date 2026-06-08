package com.huanjing.geo.module.content.schedule;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public enum SelfMediaPlatformPublishStatus {
    DRAFT,
    SCHEDULED,
    REVIEWING,
    PUBLISHED,
    FAILED,
    CANCELLED,
    UNKNOWN;

    private static final Set<String> SCHEDULED_SIGNALS = Set.of("scheduled", "定时发布中", "已定时", "将于");
    private static final Set<String> REVIEWING_SIGNALS = Set.of("reviewing", "审核中", "待审核");
    private static final Set<String> PUBLISHED_SIGNALS = Set.of("published", "已发布", "发布成功");
    private static final Set<String> FAILED_SIGNALS = Set.of("failed", "未通过", "发布失败", "审核失败");
    private static final Set<String> CANCELLED_SIGNALS = Set.of("cancelled", "已取消", "取消发布");

    public static SelfMediaPlatformPublishStatus fromSignal(String signal) {
        if (!StringUtils.hasText(signal)) {
            return UNKNOWN;
        }
        String normalized = signal.trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, PUBLISHED_SIGNALS)) {
            return PUBLISHED;
        }
        if (containsAny(normalized, SCHEDULED_SIGNALS)) {
            return SCHEDULED;
        }
        if (containsAny(normalized, REVIEWING_SIGNALS)) {
            return REVIEWING;
        }
        if (containsAny(normalized, FAILED_SIGNALS)) {
            return FAILED;
        }
        if (containsAny(normalized, CANCELLED_SIGNALS)) {
            return CANCELLED;
        }
        if (normalized.contains("draft") || normalized.contains("草稿")) {
            return DRAFT;
        }
        return UNKNOWN;
    }

    private static boolean containsAny(String value, Set<String> candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
