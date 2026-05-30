package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalAgentPairingIntentRequest(
        @NotBlank String codeHash,
        @NotBlank String deviceSecretHash,
        String helperName
) {
}
