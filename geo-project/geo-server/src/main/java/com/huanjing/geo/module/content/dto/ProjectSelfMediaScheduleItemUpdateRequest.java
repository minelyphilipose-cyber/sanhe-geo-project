package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSelfMediaScheduleItemUpdateRequest {
    private Long selfMediaAccountId;
    @NotNull
    private LocalDateTime plannedExecutionAt;
    @NotNull
    private LocalDateTime plannedPublishAt;
}
