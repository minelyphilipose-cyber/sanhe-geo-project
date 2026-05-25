package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BatchArticlePublishJobSummary {
    private Long jobId;
    private String jobName;
    private String publishMode;
    private String status;
    private LocalDateTime scheduledAt;
    private Integer intervalMinutes;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
