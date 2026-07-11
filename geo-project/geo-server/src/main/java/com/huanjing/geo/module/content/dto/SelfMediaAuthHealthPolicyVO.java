package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.SelfMediaAuthHealthPolicy;

import java.time.LocalDateTime;

public record SelfMediaAuthHealthPolicyVO(
        Long id,
        String platformCode,
        Boolean enabled,
        Integer reverifyIntervalDays,
        Integer warningDays,
        Integer credentialReferenceDays,
        String credentialExpiryMode,
        Boolean alertEnabled,
        String defaultRecipientRole,
        Integer version,
        LocalDateTime updatedAt
) {
    public static SelfMediaAuthHealthPolicyVO from(SelfMediaAuthHealthPolicy row) {
        return new SelfMediaAuthHealthPolicyVO(row.getId(), row.getPlatformCode(), row.getEnabled(),
                row.getReverifyIntervalDays(), row.getWarningDays(), row.getCredentialReferenceDays(),
                row.getCredentialExpiryMode(), row.getAlertEnabled(), row.getDefaultRecipientRole(),
                row.getVersion(), row.getUpdatedAt());
    }
}
