package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record LocalAgentPairingApproveResponse(
        Long sessionId,
        Long brandId,
        LocalDateTime expiresAt
) {
}
