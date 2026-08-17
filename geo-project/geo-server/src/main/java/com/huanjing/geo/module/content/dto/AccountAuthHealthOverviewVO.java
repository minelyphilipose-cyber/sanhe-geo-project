package com.huanjing.geo.module.content.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AccountAuthHealthOverviewVO(
        LocalDateTime generatedAt,
        Summary summary,
        List<RiskItem> riskItems,
        List<BrandEnvironmentGroup> brandEnvironments,
        List<AlertGroup> alertGroups,
        List<TrendBucket> trendBuckets,
        List<ScanBatch> latestScans
) {
    public record Summary(
            int totalTargets,
            int normalCount,
            int expiringCount,
            int expiredCount,
            int missingCount,
            int unknownCount,
            int openAlertCount,
            int highPriorityCount,
            int dueInSevenDays,
            int dueInThirtyDays,
            int explicitLoginIssueCount,
            int reverifyOverdueCount,
            int referenceWindowNormalCount
    ) {
    }

    public record RiskItem(
            String targetType,
            Long targetId,
            String targetKey,
            String displayName,
            String platform,
            String platformLabel,
            Long brandId,
            String brandName,
            String companyName,
            Long ownerUserId,
            String ownerName,
            String riskStatus,
            String loginFactStatus,
            String reverifyStatus,
            String severity,
            Long browserEnvironmentId,
            String environmentKey,
            String environmentName,
            String environmentStatus,
            LocalDateTime expiresAt,
            Long daysUntilExpiry,
            String expirySource,
            String expirySourceLabel,
            LocalDateTime lastLoginVerifiedAt,
            LocalDateTime lastLoginCheckedAt,
            String lastLoginCheckResult,
            String actionRoute,
            String actionLabel,
            String actionHint
    ) {
    }

    public record BrandEnvironmentGroup(
            Long brandId,
            String brandName,
            String companyName,
            Long ownerUserId,
            String ownerName,
            Long browserEnvironmentId,
            String environmentKey,
            String environmentName,
            String environmentStatus,
            int accountCount,
            int confirmedLoginCount,
            int loginIssueCount,
            int unverifiedCount,
            int reverifyOverdueCount,
            int reverifyDueSoonCount,
            LocalDateTime lastLoginSeenAt,
            String actionRoute
    ) {
    }

    public record ScanBatch(
            Long id,
            String scanType,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Integer totalCount,
            Integer successCount,
            Integer failureCount,
            Integer skippedCount,
            String errorSummary
    ) {
    }

    public record AlertGroup(
            String groupKey,
            String targetType,
            String issueCode,
            String severity,
            int count,
            LocalDateTime latestCreatedAt,
            String title,
            String sampleMessage,
            String actionRoute,
            String actionLabel
    ) {
    }

    public record TrendBucket(
            LocalDate date,
            int selfMediaCount,
            int forumCount,
            int totalCount
    ) {
    }
}
