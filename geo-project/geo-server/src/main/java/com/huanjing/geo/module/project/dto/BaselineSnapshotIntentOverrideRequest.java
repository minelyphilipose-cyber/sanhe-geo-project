package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BaselineSnapshotIntentOverrideRequest {
    @NotNull
    private Long keywordResultId;
    @NotBlank
    private String intentType;
}
