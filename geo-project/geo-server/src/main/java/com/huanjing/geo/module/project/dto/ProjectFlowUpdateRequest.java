package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectFlowUpdateRequest {
    @NotBlank
    private String status;
    @NotBlank
    private String stage;
}
