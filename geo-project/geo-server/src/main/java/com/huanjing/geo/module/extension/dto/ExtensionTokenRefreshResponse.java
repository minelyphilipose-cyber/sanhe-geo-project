package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record ExtensionTokenRefreshResponse(
        String token,
        boolean renewed,
        LocalDateTime expiresAt,
        Long sessionId
) {
}
