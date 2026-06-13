package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ArticleArchiveDryRunResponse {
    private Long retentionRunId;
    private Boolean dryRun = true;
    private Long projectId;
    private LocalDate publishedStartDate;
    private LocalDate publishedEndDate;
    private Integer minPublishedAgeDays;
    private Integer limit;
    private Integer candidateCount = 0;
    private Integer eligibleCount = 0;
    private Integer blockedCount = 0;
    private Integer archivedCount = 0;
    private Integer skippedCount = 0;
    private Integer failedCount = 0;
    private Long estimatedBytes = 0L;
    private List<ArticleArchiveDryRunItemVO> items = new ArrayList<>();
}
