package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportGenerateRequest {
    @NotNull
    private Long projectId;
    @NotBlank
    private String reportType;
}
