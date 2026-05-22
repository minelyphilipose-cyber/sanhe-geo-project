package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtensionTaskPublishReportRequest(
        @Size(max = 64)
        String action,
        @Size(max = 512)
        String href,
        @Size(max = 64)
        String platform,
        @Size(max = 256)
        String detectedText,
        @Size(max = 64)
        @Pattern(regexp = "COOKIE_MISSING|LOGIN_REQUIRED|PAGE_CHANGED|FILL_FAILED|PUBLISH_BUTTON_NOT_FOUND|TASK_EXPIRED|UNKNOWN")
        String errorCode,
        @Size(max = 512)
        String errorMessage
) {
}
