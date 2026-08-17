package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SystemAlertSnoozeRequest(
        @NotNull @Min(1) @Max(168) Integer hours
) {
}
