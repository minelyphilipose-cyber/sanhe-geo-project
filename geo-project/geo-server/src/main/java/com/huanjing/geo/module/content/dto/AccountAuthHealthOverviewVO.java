package com.huanjing.geo.module.content.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AccountAuthHealthOverviewVO(
        LocalDateTime generatedAt,
        Summary summary,
        List<RiskItem> riskItems,
        List<AlertGroup> alertGroups,
        List<TrendBucket> trendBuckets
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
            int dueInThirtyDays
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
            String severity,
            LocalDateTime expiresAt,
            Long daysUntilExpiry,
            String expirySource,
            String expirySourceLabel,
            String actionRoute,
            String actionLabel,
            String actionHint
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
