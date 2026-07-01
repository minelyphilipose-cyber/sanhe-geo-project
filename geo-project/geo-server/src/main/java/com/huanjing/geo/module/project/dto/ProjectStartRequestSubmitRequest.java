package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStartRequestSubmitRequest {
    @Size(max = 64)
    private String requestId;

    @Size(max = 500)
    private String remark;
}
