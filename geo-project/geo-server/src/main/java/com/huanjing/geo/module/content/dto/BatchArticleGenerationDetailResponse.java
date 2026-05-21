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
                       String tone,
                       String contentStyle,
                       String contentAngle,
                       String audiencePerspective,
                       String status,
                       String qualityStatus,
                       String errorMessage,
                       LocalDateTime startedAt,
                       LocalDateTime finishedAt) {
    }
}
