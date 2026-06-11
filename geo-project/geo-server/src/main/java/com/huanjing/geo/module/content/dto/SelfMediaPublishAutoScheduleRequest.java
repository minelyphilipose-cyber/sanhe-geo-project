package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SelfMediaPublishAutoScheduleRequest {
    @NotNull
    private Long brandId;

    @NotEmpty
    private List<Long> articleIds;

    @NotEmpty
    private List<Long> selfMediaAccountIds;

    /**
     * Format: yyyy-MM.
     */
    @NotNull
    private String targetMonth;

    private String scheduleStrategy;

    private Boolean includeAdjustedWorkdays;
}
