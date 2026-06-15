package com.huanjing.geo.module.content.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SelfMediaAutomationOverviewVO {
    private LocalDateTime generatedAt;
    private QueueOverview queue;
    private LocalExecutionOverview localExecution;
    private List<StatusCount> statusCounts;
    private List<PlatformCount> platformCounts;
    private List<FailureCodeCount> failureCodeCounts;
    private List<PlatformCapability> platformCapabilities;
    private ThirdPartySubjectPoolOverview thirdPartySubjectPool;

    @Data
    @Builder
    public static class QueueOverview {
        private long activeTotal;
        private long dueScheduleExecution;
        private long duePublishCheck;
        private long runningTotal;
        private long lockedRunning;
        private long failedTotal;
        private long manualRequired;
        private long publishUnknown;
    }

    @Data
    @Builder
    public static class LocalExecutionOverview {
        private long onlineAgents;
        private long activeSessions;
        private int assumedCapacityPerAgent;
        private long estimatedCapacity;
        private long runningLoad;
        private long waitingForLocalAgent;
        private String capacityStatus;
        private String message;
    }

    @Data
    @Builder
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    @Builder
    public static class PlatformCount {
        private String platform;
        private long activeCount;
        private long failedCount;
        private long dueCount;
    }

    @Data
    @Builder
    public static class FailureCodeCount {
        private String code;
        private String label;
        private Boolean retryable;
        private String actionKey;
        private String actionLabel;
        private String actionKind;
        private long count;
    }

    @Data
    @Builder
    public static class PlatformCapability {
        private String platform;
        private String displayName;
        private String publishChannel;
        private String strategy;
        private boolean scheduleReady;
        private String readinessCode;
        private String readinessMessage;
        private boolean requiresLocalAgent;
    }

    @Data
    @Builder
    public static class ThirdPartySubjectPoolOverview {
        private long sourceTotal;
        private long readySourceTotal;
        private long missingCoverageTotal;
        private long emptyCandidateTotal;
        private List<ThirdPartySubjectPoolSource> sources;
    }

    @Data
    @Builder
    public static class ThirdPartySubjectPoolSource {
        private Long sourceBrandId;
        private String sourceBrandName;
        private List<String> coverableIndustries;
        private int candidateCount;
        private int excludedCount;
        private String nextCandidateBrandName;
        private String status;
        private String message;
    }
}
