package com.huanjing.geo.module.presale.service;

import lombok.Data;

@Data
public class PresalePromptTraceRow {
    private Long promptResultId;
    private Long reportId;
    private Long versionId;
    private Integer versionNo;
    private Integer batchNo;
    private String platformCode;
    private String platformName;
    private String category;
    private String competitorName;
    private String requestPromptContent;
    private Integer isMentioned;
    private Integer ranking;
    private String sentiment;
    private String mentionedCompetitors;
    private String sceneAdvantages;
    private String topKeywordsJson;
    private String negativeEvidenceJson;
    private String queryPromptContent;
    private String queryRawResponse;
    private String queryCallStatus;
    private String queryFailureReason;
    private Integer queryDurationMs;
    private String queryModelName;
    private Boolean queryModelSnapshotInferred;
    private String queryContractVersion;
    private String searchEvidenceJson;
    private String analyzePromptContent;
    private String analyzeRawResponse;
    private String analyzeCallStatus;
    private String analyzeFailureReason;
    private Integer analyzeDurationMs;
    private String analyzeModelName;
    private Boolean analyzeModelSnapshotInferred;
}
