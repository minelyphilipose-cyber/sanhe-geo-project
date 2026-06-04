package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record ExtensionTaskListItemResponse(
        Long taskId,
        String platform,
        String status,
        String publishUrl,
        String title,
        LocalDateTime createdAt,
        LocalDateTime fillTokenIssuedAt,
        LocalDateTime expiresAt,
        Long selfMediaAccountId,
        Long browserEnvironmentId,
        Long browserEnvironmentAccountId,
        String environmentKey,
        String environmentProvider,
        String providerProfileId
) {
}
