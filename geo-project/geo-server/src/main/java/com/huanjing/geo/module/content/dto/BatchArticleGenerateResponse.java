package com.huanjing.geo.module.content.dto;

import java.util.List;

public record BatchArticleGenerateResponse(
        Long batchId,
        Integer totalCount,
        String status,
        Boolean allocationChanged,
        Boolean customSkipped,
        List<Notice> notices
) {
    public BatchArticleGenerateResponse(Long batchId, Integer totalCount, String status) {
        this(batchId, totalCount, status, false, false, List.of());
    }

    public record Notice(String type, String level, String message, List<Item> items) {
    }

    public record Item(
            String topic,
            String channelGroupCode,
            String channelSubCode,
            Long templateId,
            String templateName,
            Integer requestedCount,
            String reason,
            List<TemplateCount> before,
            List<TemplateCount> after
    ) {
    }

    public record TemplateCount(Long templateId, String templateName, Integer count) {
    }
}
