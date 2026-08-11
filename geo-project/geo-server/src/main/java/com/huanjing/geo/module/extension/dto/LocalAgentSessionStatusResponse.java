package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record LocalAgentSessionStatusResponse(
        Long sessionId,
        LocalDateTime expiresAt
) {
}
