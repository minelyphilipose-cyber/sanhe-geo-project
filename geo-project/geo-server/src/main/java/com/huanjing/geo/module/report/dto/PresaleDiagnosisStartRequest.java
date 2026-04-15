package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresaleDiagnosisStartRequest {
    @NotNull
    private Long projectId;
    private Long questionSetId;
    private String remark;
}
