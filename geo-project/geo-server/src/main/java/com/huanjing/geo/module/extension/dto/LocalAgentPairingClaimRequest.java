package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalAgentPairingClaimRequest(
        @NotBlank String pairingCode,
        @NotBlank String deviceSecretHash
) {
}
