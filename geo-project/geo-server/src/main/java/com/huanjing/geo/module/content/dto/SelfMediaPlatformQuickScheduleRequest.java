package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelfMediaPlatformQuickScheduleRequest {
    @NotNull
    private Long articleId;

    @NotBlank
    private String platform;

    private Boolean replaceNextScheduled;
}
