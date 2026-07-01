package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStartRequestRejectRequest {
    @Size(max = 64)
    private String rejectReasonCode;

    @Size(max = 500)
    private String rejectReasonText;
}
