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

    /**
     * Optional execution window for browser/helper fill tasks.
     * When absent, the system derives execution time from the platform publish window.
     */
    private LocalDateTime executionWindowStart;

    private LocalDateTime executionWindowEnd;

    private String scheduleStrategy;

    private Integer minIntervalMinutes;

    /**
     * Automatic monthly planning may use a second daily slot when the task count
     * exceeds the available publish-day count. The daily hard limit remains two.
     */
    private Boolean allowSecondDailySchedule;
}
