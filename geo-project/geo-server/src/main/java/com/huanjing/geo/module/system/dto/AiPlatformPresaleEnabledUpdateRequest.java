package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiPlatformPresaleEnabledUpdateRequest {
    @NotNull
    private Boolean enabledForPresale;
}
