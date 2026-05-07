package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record FillTokenConsumeRequest(
        @NotBlank String fillToken,
        String platform,
        String extensionVersion
) {
}
