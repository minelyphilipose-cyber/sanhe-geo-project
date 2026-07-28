package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PollRetentionDryRunRequest {
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String questionTier;
    private LocalDate cursorBatchDate;
    private Long cursorProjectId;
    private String cursorQuestionTier;
    private Integer hotRetentionDays;
    private Integer stuckBatchSealDays;
    private Integer limit;
    private String reason;
}
