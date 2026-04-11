package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectStatusUpdateRequest {
    @NotBlank
    private String status;
}
