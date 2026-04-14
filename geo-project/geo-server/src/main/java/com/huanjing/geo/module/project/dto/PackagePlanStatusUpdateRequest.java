package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PackagePlanStatusUpdateRequest {
    @NotNull
    private Boolean enabled;
}
