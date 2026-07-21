package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KeywordGroupQuestionPollingUpdateRequest {
    @NotNull(message = "pollingEnabled is required")
    private Boolean pollingEnabled;
}
