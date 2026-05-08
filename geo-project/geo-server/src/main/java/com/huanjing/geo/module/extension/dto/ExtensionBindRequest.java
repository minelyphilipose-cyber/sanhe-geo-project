package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtensionBindRequest(
        @NotBlank String bindCode,
        Long brandId,
        @NotBlank String installId,
        String deviceFingerprint,
        String extensionVersion
) {
}
