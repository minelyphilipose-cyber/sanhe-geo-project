package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DispatchAlertVO {
    private Long id;
    private String alertCode;
    private Long taskId;
    private Long projectId;
    private String projectName;
    private String dedupeKey;
    private String severity;
    private String status;
    private String title;
    private String content;
    private Integer retryCount;
    private String contextJson;
    private Integer groupCount;
    private Integer openGroupCount;
    private List<DispatchAlertVO> detailAlerts;
    private List<PlatformFailureSummary> platformFailures;
    private Integer expectedResultCount;
    private Integer failedCount;
    private Double failureRate;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private LocalDateTime createdAt;

    @Data
    public static class PlatformFailureSummary {
        private Long platformId;
        private String platformCode;
        private String platformName;
        private Integer expectedCount;
        private Integer completedCount;
        private Integer failedCount;
        private Double failureRate;
        private Integer requestCount;
        private List<FailureReasonSummary> reasons;
    }

    @Data
    public static class FailureReasonSummary {
        private String errorCode;
        private String errorMessage;
        private Integer count;
    }
}
