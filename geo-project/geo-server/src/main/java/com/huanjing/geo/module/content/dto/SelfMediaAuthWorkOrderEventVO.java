package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.SelfMediaAuthWorkOrderEvent;

import java.time.LocalDateTime;

public record SelfMediaAuthWorkOrderEventVO(
        Long id,
        String action,
        String fromStatus,
        String toStatus,
        String note,
        Long operatorId,
        String operatorName,
        LocalDateTime createdAt
) {
    public static SelfMediaAuthWorkOrderEventVO from(SelfMediaAuthWorkOrderEvent row) {
        return from(row, null);
    }

    public static SelfMediaAuthWorkOrderEventVO from(SelfMediaAuthWorkOrderEvent row, String operatorName) {
        return new SelfMediaAuthWorkOrderEventVO(row.getId(), row.getAction(), row.getFromStatus(),
                row.getToStatus(), row.getNote(), row.getOperatorId(), operatorName, row.getCreatedAt());
    }
}
