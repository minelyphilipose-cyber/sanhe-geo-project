package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PollSummaryBackfillSliceVO {

    private Long projectId;
    private LocalDate batchDate;
    private String questionTier;
    private Long sourceRowCount = 0L;
    private Boolean dryRun = true;
    private Boolean skipped = false;
    private String skipReason;
    private Boolean failed = false;
    private String errorMessage;
    private Integer keywordSummaryCount = 0;
    private Integer platformSummaryCount = 0;
    private Integer keywordZombieDeleted = 0;
    private Integer platformZombieDeleted = 0;
}
