package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DouyinImageTextPublishSnapshot(
        int schemaVersion,
        String contentKind,
        String title,
        String descriptionBase,
        String topicRegionText,
        String topicIndustryText,
        String topicQuery,
        String regionSourceField,
        String industrySourceValue,
        String locationQuery,
        List<Long> imageMaterialIds,
        int expectedImageCount,
        String publishMode,
        LocalDateTime createdAt
) {
    public DouyinImageTextPublishSnapshot {
        imageMaterialIds = imageMaterialIds == null ? List.of() : List.copyOf(imageMaterialIds);
    }

    /**
     * Final platform text is composed on the server from immutable snapshot data.
     * The browser extension must not reconstruct business content.
     */
    public String finalDescription() {
        String base = removeTrailingTopicLines(descriptionBase, topicQuery);
        String topic = topicQuery == null ? "" : topicQuery.trim();
        if (topic.isEmpty()) {
            return base;
        }
        return base.isEmpty() ? topic : base + "\n" + topic;
    }

    private static String removeTrailingTopicLines(String value, String topicQuery) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        String topic = topicQuery == null ? "" : topicQuery.trim();
        if (topic.isEmpty()) {
            return normalized;
        }
        String[] lines = normalized.split("\\n", -1);
        int end = lines.length;
        while (end > 0 && topic.equals(lines[end - 1].trim())) {
            end--;
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, end)).trim();
    }
}
