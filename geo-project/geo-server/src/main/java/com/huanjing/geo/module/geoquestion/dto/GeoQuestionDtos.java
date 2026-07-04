package com.huanjing.geo.module.geoquestion.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class GeoQuestionDtos {
    @Data
    public static class CustomerSearchItem {
        private Long companyId;
        private String companyName;
        private Long brandId;
        private String brandName;
        private String industry;
        private String packageName;
        private Boolean activeBinding;
    }

    @Data
    public static class CreateOrGetWorkorderRequest {
        private Long companyId;
        private Long projectId;
    }

    @Data
    public static class WorkorderVO {
        private Long id;
        private Long companyId;
        private String companyName;
        private Long brandId;
        private String brandName;
        private Long projectId;
        private String projectName;
        private String packageName;
        private String status;
        private String partnerReviewStatus;
        private String partnerReviewReturnReason;
        private LocalDateTime partnerReviewSubmittedAt;
        private LocalDateTime partnerReviewReturnedAt;
        private LocalDateTime partnerReviewHqSubmittedAt;
        private LocalDateTime partnerReviewUpdatedAt;
        private Integer targetA;
        private Integer targetB;
        private Integer targetC;
        private QuotaSnapshot quota;
    }

    @Data
    public static class WorkorderListItemVO {
        private Long id;
        private Long companyId;
        private Long projectId;
        private String projectName;
        private String workorderNo;
        private String packageName;
        private String status;
        private String partnerReviewStatus;
        private String partnerReviewReturnReason;
        private LocalDateTime partnerReviewSubmittedAt;
        private LocalDateTime partnerReviewReturnedAt;
        private LocalDateTime partnerReviewHqSubmittedAt;
        private LocalDateTime partnerReviewUpdatedAt;
        private Integer targetA;
        private Integer targetB;
        private Integer targetC;
        private Integer countA;
        private Integer countB;
        private Integer countC;
        private Integer countTotal;
        private Integer batchCount;
        private String latestBatchStatus;
        private LocalDateTime latestBatchAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class QuotaSnapshot {
        private Long companyId;
        private Long projectId;
        private Long workorderId;
        private String packageName;
        private Integer quotaA;
        private Integer quotaB;
        private Integer quotaC;
        private Integer quotaTotal;
        private Integer activeUsedA;
        private Integer activeUsedB;
        private Integer activeUsedC;
        private Integer activeUsedTotal;
        private Integer workorderCountA;
        private Integer workorderCountB;
        private Integer workorderCountC;
        private Integer workorderCountTotal;
        private Integer runningReservedA;
        private Integer runningReservedB;
        private Integer runningReservedC;
        private Integer runningReservedTotal;
        private Integer remainingA;
        private Integer remainingB;
        private Integer remainingC;
        private Integer remainingTotal;
    }

    @Data
    public static class ProfileVO {
        private Long companyId;
        private Long projectId;
        private String projectName;
        private String companyName;
        private String brandName;
        private String brandRelation;
        private List<String> coreBusiness;
        private String targetRegion;
        private String industry;
        private String targetCustomer;
        private String coreAdvantage;
        private String benchmarkSpecs;
        private List<Map<String, Object>> competitors;
        private List<Map<String, Object>> coreNeeds;
    }

    @Data
    public static class DraftSaveRequest {
        private Long workorderId;
        private String profileJson;
        private Boolean syncToCustomerProfile;
        private String validationStatus;
    }

    @Data
    public static class DraftVO {
        private Long workorderId;
        private String profileJson;
        private Boolean syncToCustomerProfile;
        private String validationStatus;
        private LocalDateTime autoSavedAt;
    }

    @Data
    public static class ProviderVO {
        private Long id;
        private String platformCode;
        private String platformName;
        private String modelId;
        private String modelName;
    }

    @Data
    public static class BatchStartRequest {
        private Long workorderId;
        private Integer batchA;
        private Integer batchB;
        private Integer batchC;
        private Long modelConfigId;
        private String modelProvider;
        private String modelId;
        private String modelName;
        private Map<String, Integer> sceneWeights;
        private BigDecimal temperature;
        private Boolean partnerCoreOnly;
    }

    @Data
    public static class PartnerCoreQuestionGenerateRequest {
        private Integer count;
    }

    @Data
    public static class QuestionBatchDeleteRequest {
        private List<Long> ids;
    }

    @Data
    public static class BatchVO {
        private Long id;
        private Long workorderId;
        private String batchNo;
        private Integer requestA;
        private Integer requestB;
        private Integer requestC;
        private Integer actualA;
        private Integer actualB;
        private Integer actualC;
        private String batchType;
        private String modelName;
        private String status;
        private String progressJson;
        private String errorMessage;
        private String llmResponseSnapshot;
        private Integer replaceCountTotal;
        private Boolean partialFlag;
        private Boolean cancelRequested;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private List<BatchLogVO> logs;
    }

    @Data
    public static class BatchLogVO {
        private String eventCode;
        private String message;
        private LocalDateTime createdAt;
    }

    @Data
    public static class QuestionVO {
        private Long id;
        private Long batchId;
        private String questionText;
        private String sceneCode;
        private String tier;
        private String priority;
        private String monitorFrequency;
        private BigDecimal scoreRelevance;
        private BigDecimal scoreIntent;
        private BigDecimal scoreCompetition;
        private BigDecimal scoreConversion;
        private BigDecimal scoreCoverage;
        private BigDecimal totalScore;
        private String relatedNeedText;
        private String designReason;
        private String status;
        private Integer replaceCount;
    }

    @Data
    public static class ReviewVO {
        private WorkorderVO workorder;
        private List<BatchVO> batches;
        private List<QuestionVO> questions;
    }

    @Data
    public static class QuestionPageVO {
        private List<QuestionVO> records;
        private Long total;
        private Long current;
        private Long size;
        private Long pages;
    }

    @Data
    public static class RegenerateQuestionRequest {
        private String reason;
    }

    @Data
    public static class RegenerateQuestionVO {
        private QuestionVO question;
        private Boolean softWarning;
        private String warningMessage;
    }

    @Data
    public static class QuestionUpdateRequest {
        private String questionText;
        private String sceneCode;
        private String tier;
        private String priority;
        private String monitorFrequency;
        private BigDecimal scoreRelevance;
        private BigDecimal scoreIntent;
        private BigDecimal scoreCompetition;
        private BigDecimal scoreConversion;
        private BigDecimal scoreCoverage;
        private BigDecimal totalScore;
        private String relatedNeedText;
        private String designReason;
    }

    @Data
    public static class ManualQuestionCreateRequest {
        private List<ManualQuestionItemRequest> items;
        private String manualReason;
    }

    @Data
    public static class ManualQuestionItemRequest {
        private String questionText;
        private String sceneCode;
        private String tier;
        private String priority;
        private String monitorFrequency;
        private BigDecimal scoreRelevance;
        private BigDecimal scoreIntent;
        private BigDecimal scoreCompetition;
        private BigDecimal scoreConversion;
        private BigDecimal scoreCoverage;
        private BigDecimal totalScore;
        private String relatedNeedText;
        private String designReason;
    }

    @Data
    public static class CommitRequest {
        private String versionLabel;
    }

    @Data
    public static class PartnerReviewReturnRequest {
        private String reason;
    }
}
