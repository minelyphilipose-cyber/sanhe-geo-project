package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportInterceptRequest {
    @NotBlank
    private String reason;
}
