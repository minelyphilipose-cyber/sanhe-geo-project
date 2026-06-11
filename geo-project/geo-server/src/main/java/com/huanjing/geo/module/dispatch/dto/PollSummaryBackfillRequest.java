package com.huanjing.geo.module.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PollSummaryBackfillRequest {

    private Long projectId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String questionTier;

    private LocalDate cursorBatchDate;

    private Long cursorProjectId;

    private String cursorQuestionTier;

    private Boolean dryRun = true;

    private Integer limit = 100;
}
