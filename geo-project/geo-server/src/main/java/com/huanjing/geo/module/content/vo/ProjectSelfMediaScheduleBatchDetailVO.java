package com.huanjing.geo.module.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectSelfMediaScheduleBatchDetailVO {
    private ProjectSelfMediaScheduleBatchVO batch;
    private List<Item> items = new ArrayList<>();

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
    }
}
