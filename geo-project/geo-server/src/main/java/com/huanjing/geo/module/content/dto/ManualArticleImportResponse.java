package com.huanjing.geo.module.content.dto;

import java.util.List;

public record ManualArticleImportResponse(
        String format,
        String title,
        String suggestedTitle,
        String titleConfidence,
        String contentMarkdown,
        List<ImportWarning> warnings,
        ImportStats stats
) {
    public record ImportWarning(String code, String message, Integer count) {
    }

    public record ImportStats(
            int characters,
            int paragraphs,
            int headings,
            int tables,
            int omittedImages
    ) {
    }
}
