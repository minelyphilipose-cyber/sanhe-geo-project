package com.huanjing.geo.module.mobiledashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobileDashboardWechatConfigRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url is too long")
        String url
) {
}
