package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BatchArticleGenerationDetailResponse(Long batchId,
                                                   Long projectId,
                                                   String topic,
                                                   String topicAsQuestion,
                                                   String status,
                                                   Integer totalCount,
                                                   Integer successCount,
                                                   Integer failedCount,
                                                   Integer warningCount,
                                                   LocalDateTime createdAt,
                                                   LocalDateTime startedAt,
                                                   LocalDateTime finishedAt,
                                                   List<Task> tasks) {

    public record Task(Long taskId,
                       Long articleId,
                       Integer rowNo,
                       Integer articleIndexInBatch,
                       String articleType,
                       String articleTypeCode,
                       String channelGroupCode,
                       String channelSubCode,
                       String tone,
                       String contentStyle,
                       String agentSiteModule,
                       String contentAngle,
                       String audiencePerspective,
                       Long promptTemplateId,
                       Long promptTemplateVersionId,
                       String allocationMode,
                       String templateSource,
                       String status,
                       String qualityStatus,
                       String errorMessage,
                       LocalDateTime startedAt,
                       LocalDateTime finishedAt) {
    }
}
