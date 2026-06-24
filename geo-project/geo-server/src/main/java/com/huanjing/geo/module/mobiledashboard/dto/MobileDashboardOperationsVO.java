package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MobileDashboardOperationsVO {
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private JudgeHealth judgeHealth = new JudgeHealth();
    private ApiErrorStats apiErrorStats = new ApiErrorStats();
    private LlmUsageStats llmUsage = new LlmUsageStats();
    private List<ShareRisk> shareRisks = new ArrayList<>();

    @Data
    public static class JudgeHealth {
        private Long expectedCount = 0L;
        private Long successCount = 0L;
        private Integer coveragePercent = 0;
        private Boolean coverageReady = false;
        private Long failedCount = 0L;
        private LocalDateTime lastRecomputedAt;
    }

    @Data
    public static class ApiErrorStats {
        private Long total = 0L;
        private Long failed = 0L;
        private Integer errorRatePercent = 0;
        private List<ApiEndpointStats> endpoints = new ArrayList<>();
    }

    @Data
    public static class ApiEndpointStats {
        private String eventType;
        private Long total;
        private Long failed;
        private Integer errorRatePercent;
        private String latestFailReason;
    }

    @Data
    public static class LlmUsageStats {
        private Long totalCalls = 0L;
        private Long successCalls = 0L;
        private Long failedCalls = 0L;
        private Long totalTokens = 0L;
        private BigDecimal estimatedCost = BigDecimal.ZERO;
        private String currency = "CNY";
        private Boolean estimated = true;
    }

    @Data
    public static class ShareRisk {
        private Long shareId;
        private String tokenPrefix;
        private Long totalAccess;
        private Long distinctIpCount;
        private Long failedAccess;
        private LocalDateTime lastAccessAt;
        private Boolean suspicious;
    }
}
