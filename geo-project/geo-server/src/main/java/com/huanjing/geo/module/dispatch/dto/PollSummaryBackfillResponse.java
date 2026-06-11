package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PollSummaryBackfillResponse {

    private Boolean dryRun;
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String questionTier;
    private Integer limit;
    private Boolean hasMore = false;
    private LocalDate nextCursorBatchDate;
    private Long nextCursorProjectId;
    private String nextCursorQuestionTier;
    private Long retentionRunId;
    private Integer candidateSlices = 0;
    private Integer recomputedSlices = 0;
    private Integer skippedSlices = 0;
    private Integer failedSlices = 0;
    private Long sourceRows = 0L;
    private Long keywordSummaryRows = 0L;
    private Long platformSummaryRows = 0L;
    private Long keywordZombieDeleted = 0L;
    private Long platformZombieDeleted = 0L;
    private List<PollSummaryBackfillSliceVO> slices = new ArrayList<>();
}
