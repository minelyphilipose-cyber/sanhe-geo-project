package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.SelfMediaLoginObservation;

import java.time.LocalDateTime;

public record SelfMediaLoginObservationVO(
        Long id,
        Long brandId,
        Long selfMediaAccountId,
        Long browserEnvironmentId,
        Long loginVerificationId,
        String platform,
        String reportSource,
        String observationResult,
        String resultCode,
        String resultMessage,
        String actualAccountName,
        String currentUrl,
        String extensionVersion,
        String protocolVersion,
        Boolean authoritative,
        Long operatorId,
        LocalDateTime createdAt
) {
    public static SelfMediaLoginObservationVO from(SelfMediaLoginObservation row) {
        return new SelfMediaLoginObservationVO(row.getId(), row.getBrandId(), row.getSelfMediaAccountId(),
                row.getBrowserEnvironmentId(), row.getLoginVerificationId(), row.getPlatform(),
                row.getReportSource(), row.getObservationResult(), row.getResultCode(), row.getResultMessage(),
                row.getActualAccountName(), row.getCurrentUrl(), row.getExtensionVersion(), row.getProtocolVersion(),
                row.getAuthoritative(), row.getOperatorId(), row.getCreatedAt());
    }
}
