package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SelfMediaPublishScheduleCreateRequest {
    @NotNull
    private Long brandId;

    @NotEmpty
    private List<Long> articleIds;

    @NotEmpty
    private List<Long> selfMediaAccountIds;

    @NotNull
    private LocalDateTime windowStart;

    @NotNull
    private LocalDateTime windowEnd;

    private String scheduleStrategy;

    private Integer minIntervalMinutes;
}
