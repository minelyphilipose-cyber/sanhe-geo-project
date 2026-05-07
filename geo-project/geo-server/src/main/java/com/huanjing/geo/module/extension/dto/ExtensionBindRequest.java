package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExtensionBindRequest(
        @NotBlank String bindCode,
        @NotNull Long brandId,
        @NotBlank String installId,
        String deviceFingerprint,
        String extensionVersion
) {
}
