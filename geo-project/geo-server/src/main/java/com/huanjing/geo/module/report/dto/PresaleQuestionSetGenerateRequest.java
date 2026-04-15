package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresaleQuestionSetGenerateRequest {
    @NotNull
    private Long projectId;
    private Boolean regenerate;
}
