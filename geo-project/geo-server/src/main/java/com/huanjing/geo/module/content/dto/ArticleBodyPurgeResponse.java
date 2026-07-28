package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArticleBodyPurgeResponse {
    private Long retentionRunId;
    private Boolean dryRun;
    private Boolean simulationOnly = false;
    private Long projectId;
    private Integer retentionDays;
    private Integer archiveGraceHours;
    private Integer limit;
    private String reason;
    private Boolean hasMore = false;
    private Long nextCursorVersionId;
    private Integer candidateCount = 0;
    private Integer eligibleCount = 0;
    private Integer blockedCount = 0;
    private Integer purgedCount = 0;
    private Integer skippedCount = 0;
    private Integer failedCount = 0;
    private Long estimatedBytes = 0L;
    private List<ArticleArchiveDryRunItemVO> items = new ArrayList<>();
}
