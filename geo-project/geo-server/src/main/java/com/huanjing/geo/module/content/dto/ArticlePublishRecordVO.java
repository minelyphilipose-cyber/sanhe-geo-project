package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.ArticlePublishRecord;

import java.time.LocalDateTime;

public record ArticlePublishRecordVO(Long id,
                                     String targetKind,
                                     String targetChannel,
                                     String publishStatus,
                                     String publishedUrl,
                                     String urlQuality,
                                     String title,
                                     LocalDateTime publishedAt,
                                     LocalDateTime verifiedAt) {

    public static ArticlePublishRecordVO from(ArticlePublishRecord record) {
        return new ArticlePublishRecordVO(
                record.getId(),
                record.getTargetKind(),
                record.getTargetChannel(),
                record.getPublishStatus(),
                record.getPublishedUrl(),
                record.getUrlQuality(),
                record.getTitle(),
                record.getPublishedAt(),
                record.getVerifiedAt()
        );
    }
}
