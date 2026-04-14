package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictItemStatusUpdateRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;
}

