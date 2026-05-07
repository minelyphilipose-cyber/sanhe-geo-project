package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtensionVersionCheckRequest(
        @NotBlank String platform,
        @NotBlank String currentVersion
) {
}
