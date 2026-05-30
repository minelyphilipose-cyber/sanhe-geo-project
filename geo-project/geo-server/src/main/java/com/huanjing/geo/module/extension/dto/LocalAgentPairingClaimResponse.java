package com.huanjing.geo.module.extension.dto;

import java.time.LocalDateTime;

public record LocalAgentPairingClaimResponse(
        Long sessionId,
        Long brandId,
        Long operatorId,
        String accessToken,
        String hmacSecret,
        LocalDateTime expiresAt
) {
}
