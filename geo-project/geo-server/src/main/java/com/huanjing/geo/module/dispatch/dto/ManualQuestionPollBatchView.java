package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ManualQuestionPollBatchView {
    private Long batchId;
    private Long projectId;
    private String projectName;
    private LocalDate batchDate;
    private Integer batchNo;
    private String questionTier;
    private String triggerType;
    private String status;
    private Integer questionLimit;
    private Integer platformCount;
    private Integer shardCount;
    private Integer terminalShardCount;
    private Integer failedShardCount;
    private Integer resultCount;
    private Integer completedCount;
    private Integer failedCount;
    private Integer searchConfirmedCount;
    private Integer confirmedCitationExposureCount;
    private LocalDateTime triggeredAt;
    private LocalDateTime finishedAt;
    private List<PlatformProgress> platforms = new ArrayList<>();
    private List<ResultDetail> results = new ArrayList<>();

    @Data
    public static class PlatformProgress {
        private Long platformId;
        private String platformCode;
        private String channelCode;
        private String platformName;
        private Integer shardCount;
        private Integer readyCount;
        private Integer runningCount;
        private Integer completedShardCount;
        private Integer failedShardCount;
        private Integer expectedCount;
        private Integer completedCount;
        private Integer failedCount;
        private Integer resourceWaitCount;
    }

    @Data
    public static class ResultDetail {
        private Long pollResultId;
        private Long platformId;
        private String platformCode;
        private String platformName;
        private String question;
        private String status;
        private String resultCode;
        private Integer requestCount;
        private Long responseTimeMs;
        private Boolean executionFinalized;
        private String searchStatus;
        private Boolean searchTriggered;
        private Boolean confirmedCitationExposure;
        private String answer;
        private String errorCategory;
        private String errorMessage;
        private Long latencyMs;
        private List<SourceDetail> sources = new ArrayList<>();
        private List<CitationDetail> citations = new ArrayList<>();
    }

    @Data
    public static class SourceDetail {
        private Long sourceId;
        private Integer rankNo;
        private String title;
        private String url;
        private String domain;
        private Boolean brandMatched;
        private String brandMatchStrength;
    }

    @Data
    public static class CitationDetail {
        private Integer citationIndex;
        private Long sourceId;
        private String sourceTitle;
        private String sourceUrl;
        private Integer answerStart;
        private Integer answerEnd;
        private String confidence;
        private String validationStatus;
    }
}
