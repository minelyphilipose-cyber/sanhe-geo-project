package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.Size;

public record ExtensionTaskPublishReportRequest(
        @Size(max = 64)
        String action,
        @Size(max = 512)
        String href,
        @Size(max = 64)
        String platform,
        @Size(max = 256)
        String detectedText
) {
}
