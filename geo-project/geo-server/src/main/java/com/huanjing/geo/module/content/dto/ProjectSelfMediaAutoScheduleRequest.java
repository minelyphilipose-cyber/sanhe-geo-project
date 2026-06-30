package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class ProjectSelfMediaAutoScheduleRequest {
    private List<Long> articleIds;

    private List<Long> selfMediaAccountIds;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "targetMonth must use yyyy-MM format")
    private String targetMonth;

    private String scheduleStrategy;

    private Boolean includeAdjustedWorkdays;
}
