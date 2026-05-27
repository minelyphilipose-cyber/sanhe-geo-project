package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.service.BatchArticleQualityChecker;

import java.util.List;

public record ArticleTemplatePreviewResponse(
        String title,
        String contentMarkdown,
        String promptSnapshot,
        String inputSnapshot,
        Long templateId,
        Long templateVersionId,
        String templateName,
        String channelGroupCode,
        String channelSubCode,
        String contentStyle,
        String topicAsQuestion,
        String qualityStatus,
        List<BatchArticleQualityChecker.Issue> qualityIssues,
        List<String> unresolvedVariables,
        String modelPlatformCode,
        String modelId,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Long durationMs
) {
}
