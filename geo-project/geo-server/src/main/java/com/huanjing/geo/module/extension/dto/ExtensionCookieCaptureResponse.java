package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record ExtensionCookieCaptureResponse(
        Long credentialId,
        Long accountId,
        Long brandId,
        String platform,
        Integer version,
        LocalDateTime capturedAt,
        String status
) {
}
