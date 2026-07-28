package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class PollRetentionDryRunSliceVO {
    private Long projectId;
    private LocalDate batchDate;
    private String questionTier;
    private Boolean eligible;
    private String action;
    private String result;
    private String errorMessage;
    private Long purgeAuditRunId;
    private List<String> blockedReasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Long pollResultRows = 0L;
    private Long latestLiveResultRows = 0L;
    private Long shardRows = 0L;
    private Long shardItemRows = 0L;
    private Long invocationAttemptRows = 0L;
    private Long nonTerminalAttemptRows = 0L;
    private Long providerCallRows = 0L;
    private Long searchSourceRows = 0L;
    private Long citationRows = 0L;
    private Long entityJudgeRows = 0L;
    private Long successfulEntityJudgeRows = 0L;
    private Long entityJudgeSummaryRows = 0L;
    private Long entityJudgeSummarySuccessRows = 0L;
    private Long batchRows = 0L;
    private Long nonTerminalBatchRows = 0L;
    private Long staleNonTerminalBatchRows = 0L;
    private Long keywordSummaryRows = 0L;
    private Long keywordSummarySourceRows = 0L;
    private Long platformSummaryRows = 0L;
    private Long platformSummarySourceRows = 0L;
    private List<Map<String, Object>> freezeGates = new ArrayList<>();
    private Map<String, Long> deletedRows = new java.util.LinkedHashMap<>();
}
