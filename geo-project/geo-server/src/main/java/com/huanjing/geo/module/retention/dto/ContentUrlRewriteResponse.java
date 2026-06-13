package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContentUrlRewriteResponse {
    private Boolean dryRun = true;
    private Long retentionRunId;
    private Integer limit;
    private Long articleId;
    private Long versionId;
    private Integer candidateCount = 0;
    private Integer changedRowCount = 0;
    private Integer rewrittenUrlCount = 0;
    private Integer orphanUrlCount = 0;
    private Integer skippedCount = 0;
    private Integer failedCount = 0;
    private Integer rearchiveRequiredCount = 0;
    private List<String> scannedFields = new ArrayList<>();
    private List<ContentUrlRewriteItemVO> items = new ArrayList<>();
}
