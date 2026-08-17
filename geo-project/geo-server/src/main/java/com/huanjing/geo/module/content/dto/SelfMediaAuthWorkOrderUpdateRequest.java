package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record SelfMediaAuthWorkOrderUpdateRequest(
        String status,
        String priority,
        @Size(max = 1000) String note,
        LocalDateTime nextFollowUpAt,
        String resolutionMode,
        String resolutionReason,
        List<Long> affectedAccountIds
) {
    public SelfMediaAuthWorkOrderUpdateRequest(String status,
                                               String priority,
                                               String note,
                                               LocalDateTime nextFollowUpAt) {
        this(status, priority, note, nextFollowUpAt, null, null, List.of());
    }

    public SelfMediaAuthWorkOrderUpdateRequest(String status,
                                               String priority,
                                               String note,
                                               LocalDateTime nextFollowUpAt,
                                               String resolutionMode) {
        this(status, priority, note, nextFollowUpAt, resolutionMode, null, List.of());
    }
}
