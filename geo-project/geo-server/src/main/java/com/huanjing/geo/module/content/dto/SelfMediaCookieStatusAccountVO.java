package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;

public record SelfMediaCookieStatusAccountVO(
        Long accountId,
        String platform,
        String accountName,
        String platformAccountId,
        String accountStatus,
        String credentialStatus,
        LocalDateTime lastCapturedAt,
        Boolean canStartFill,
        String reason
) {
}
