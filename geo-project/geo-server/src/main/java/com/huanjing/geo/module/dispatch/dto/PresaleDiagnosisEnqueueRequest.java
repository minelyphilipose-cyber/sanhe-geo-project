package com.huanjing.geo.module.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresaleDiagnosisEnqueueRequest {
    @NotNull
    private Long projectId;
    private String remark;
}
