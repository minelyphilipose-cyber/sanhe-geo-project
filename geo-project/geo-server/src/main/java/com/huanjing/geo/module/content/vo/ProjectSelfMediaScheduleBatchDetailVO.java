package com.huanjing.geo.module.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectSelfMediaScheduleBatchDetailVO {
    private ProjectSelfMediaScheduleBatchVO batch;
    private List<Item> items = new ArrayList<>();
    private List<FailureSummary> failureSummaries = new ArrayList<>();
    private List<StatusRule> statusRules = new ArrayList<>();
    private BatchActionPreview actionPreview;

    @Data
    public static class FailureSummary {
        private String code;
        private String label;
        private String category;
        private Integer count;
        private Boolean retryable;
        private String actionHint;
        private String firstMessage;
        private String groupCode;
        private String groupLabel;
        private String operatorAction;
    }

    @Data
    public static class StatusRule {
        private String status;
        private String label;
        private String meaning;
        private List<String> allowedActions = new ArrayList<>();
        private String operatorHint;
    }

    @Data
    public static class BatchActionPreview {
        private Integer retryFailedCount;
        private Integer retryAbnormalCount;
        private Integer manualCount;
        private Integer rescheduleNextMonthCount;
        private Integer ignoreCount;
        private Integer unableCount;
        private String nextMonth;
        private List<String> messages = new ArrayList<>();
    }

    @Data
    public static class Item {
        private Long generationBatchId;
        private Long generationTaskId;
        private Long sourceBrandId;
        private String sourceBrandName;
        private Long subjectBrandId;
        private String subjectBrandName;
        private Long subjectProjectId;
        private String generationStatus;
        private String generationErrorMessage;
        private String generationTopic;
        private String generationArticleType;
        private LocalDateTime generationCreatedAt;
        private LocalDateTime generationUpdatedAt;
        private LocalDateTime generationStartedAt;
        private LocalDateTime generationFinishedAt;
        private Long articleId;
        private String articleTitle;
        private Long selfMediaAccountId;
        private String selfMediaAccountName;
        private String platform;
        private Long scheduleId;
        private String scheduleStatus;
        private LocalDateTime plannedPublishAt;
        private String queueKind;
        private Integer attemptCount;
        private Integer maxAttempts;
        private LocalDateTime lastAttemptAt;
        private LocalDateTime nextAttemptAt;
        private LocalDateTime lockedUntil;
        private String scheduleFailureCode;
        private String scheduleFailureMessage;
        private String claimDiagnosticCode;
        private String claimDiagnosticMessage;
        private String failureGroupCode;
        private String failureGroupLabel;
        private String operatorActionHint;
        private List<String> allowedActions = new ArrayList<>();
        private Boolean autoCompensationAvailable;
        private Integer autoCompensationRemaining;
    }
}
