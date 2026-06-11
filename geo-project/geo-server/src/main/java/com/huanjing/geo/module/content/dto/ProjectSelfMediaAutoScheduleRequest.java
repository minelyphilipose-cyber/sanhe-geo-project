package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProjectSelfMediaAutoScheduleRequest {
    private List<Long> articleIds;

    private List<Long> selfMediaAccountIds;

    @NotBlank
    private String targetMonth;

    private String scheduleStrategy;

    private Boolean includeAdjustedWorkdays;
}
