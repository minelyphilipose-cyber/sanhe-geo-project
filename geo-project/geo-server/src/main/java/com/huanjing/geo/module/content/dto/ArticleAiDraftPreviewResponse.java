package com.huanjing.geo.module.content.dto;

public record ArticleAiDraftPreviewResponse(
        String title,
        String contentMarkdown,
        String promptSnapshot,
        String inputSnapshot,
        String modelResponseSnapshot,
        String modelPlatformCode,
        String modelId,
        String modelName
) {
}
