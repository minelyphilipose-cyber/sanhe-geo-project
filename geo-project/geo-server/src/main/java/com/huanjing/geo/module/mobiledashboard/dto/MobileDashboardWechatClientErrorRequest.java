package com.huanjing.geo.module.mobiledashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MobileDashboardWechatClientErrorRequest(
        @NotBlank
        @Pattern(
                regexp = "script_load|config|check_api|share_data",
                message = "unsupported WeChat JS-SDK error stage"
        )
        String stage,
        @NotBlank
        @Pattern(
                regexp = "script_load_failed|timeout|invalid_signature|invalid_url_domain|permission_denied|api_unavailable|sdk_error|unknown",
                message = "unsupported WeChat JS-SDK error code"
        )
        String code
) {
}
