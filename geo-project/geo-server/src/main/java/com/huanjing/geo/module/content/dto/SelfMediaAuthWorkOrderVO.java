package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrder;

import java.time.LocalDateTime;

public record SelfMediaAuthWorkOrderVO(
        Long id,
        Long brandId,
        Long browserEnvironmentId,
        String status,
        String priority,
        Long ownerUserId,
        String ownerName,
        String issueSummary,
        String latestNote,
        LocalDateTime nextFollowUpAt,
        LocalDateTime resolvedAt,
        String resolutionMode,
        String resolutionReason,
        LocalDateTime updatedAt
) {
    public static SelfMediaAuthWorkOrderVO from(SelfMediaAuthWorkOrder row) {
        if (row == null) return null;
        return from(row, null);
    }

    public static SelfMediaAuthWorkOrderVO from(SelfMediaAuthWorkOrder row, String ownerName) {
        if (row == null) return null;
        return new SelfMediaAuthWorkOrderVO(row.getId(), row.getBrandId(), row.getBrowserEnvironmentId(),
                row.getStatus(), row.getPriority(), row.getOwnerUserId(), ownerName,
                row.getIssueSummary(), row.getLatestNote(), row.getNextFollowUpAt(), row.getResolvedAt(),
                row.getResolutionMode(), row.getResolutionReason(),
                row.getUpdatedAt());
    }
}
