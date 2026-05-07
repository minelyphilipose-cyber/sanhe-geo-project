package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExtensionCookieCaptureRequest(
        @NotNull Long brandId,
        @NotNull Long accountId,
        @NotBlank String platform,
        @NotBlank String extensionVersion,
        @NotBlank String installId,
        @NotNull Boolean operatorConfirmed,
        @NotBlank String confirmNonce,
        @NotBlank String cookiesJson,
        String userAgent,
        String requiredCookieCheckJson,
        String capturedFingerprintJson
) {
}
