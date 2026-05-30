package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalAgentPairingApproveRequest(
        @NotBlank String pairingCode
) {
}
