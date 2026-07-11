package com.huanjing.geo.module.content.dto;

import com.huanjing.geo.module.content.entity.SelfMediaLoginVerification;

import java.time.LocalDateTime;

public record SelfMediaLoginVerificationVO(
        Long id,
        Long brandId,
        Long selfMediaAccountId,
        Long browserEnvironmentId,
        Long browserEnvironmentAccountId,
        String platform,
        String expectedAccountName,
        String expectedPlatformAccountId,
        String status,
        String resultCode,
        String resultMessage,
        String actualAccountName,
        String actualPlatformAccountId,
        LocalDateTime requestedAt,
        LocalDateTime reportedAt,
        LocalDateTime expiresAt
) {
    public static SelfMediaLoginVerificationVO from(SelfMediaLoginVerification row) {
        return new SelfMediaLoginVerificationVO(row.getId(), row.getBrandId(), row.getSelfMediaAccountId(),
                row.getBrowserEnvironmentId(), row.getBrowserEnvironmentAccountId(), row.getPlatform(),
                row.getExpectedAccountName(), row.getExpectedPlatformAccountId(), row.getStatus(),
                row.getResultCode(), row.getResultMessage(), row.getActualAccountName(),
                row.getActualPlatformAccountId(), row.getRequestedAt(), row.getReportedAt(), row.getExpiresAt());
    }
}
