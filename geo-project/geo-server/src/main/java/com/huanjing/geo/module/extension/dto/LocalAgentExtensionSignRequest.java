package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalAgentExtensionSignRequest(
        @NotBlank String method,
        @NotBlank String path,
        @NotBlank String bodyHash
) {
}
