package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class PollRetentionDryRunResponse {
    private Long retentionRunId;
    private Boolean dryRun = true;
    private Boolean simulationOnly = false;
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String questionTier;
    private Integer hotRetentionDays;
    private Integer stuckBatchSealDays;
    private Integer limit;
    private String reason;
    private LocalDate cutoffDate;
    private Boolean hasMore = false;
    private LocalDate nextCursorBatchDate;
    private Long nextCursorProjectId;
    private String nextCursorQuestionTier;
    private Integer candidateSlices = 0;
    private Integer eligibleSlices = 0;
    private Integer blockedSlices = 0;
    private Integer warningCount = 0;
    private Long pollResultRows = 0L;
    private Long shardRows = 0L;
    private Long shardItemRows = 0L;
    private Long invocationAttemptRows = 0L;
    private Long providerCallRows = 0L;
    private Long searchSourceRows = 0L;
    private Long citationRows = 0L;
    private Long entityJudgeRows = 0L;
    private Integer purgedSlices = 0;
    private Integer failedSlices = 0;
    private Map<String, Long> deletedRows = new java.util.LinkedHashMap<>();
    private List<PollRetentionDryRunSliceVO> slices = new ArrayList<>();
}
