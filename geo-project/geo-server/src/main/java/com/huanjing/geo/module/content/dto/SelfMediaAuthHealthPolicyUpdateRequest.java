package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SelfMediaAuthHealthPolicyUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(365) Integer reverifyIntervalDays,
        @NotNull @Min(0) @Max(90) Integer warningDays,
        @Min(1) @Max(730) Integer credentialReferenceDays,
        @NotBlank String credentialExpiryMode,
        @NotNull Boolean alertEnabled,
        String defaultRecipientRole,
        @NotNull Integer version,
        @NotBlank String changeReason
) {
}
