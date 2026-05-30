package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.extension.entity.LocalAgentSession;

import java.time.LocalDateTime;

public record LocalAgentSessionVO(
        Long id,
        Long brandId,
        String helperName,
        String status,
        LocalDateTime boundAt,
        LocalDateTime lastSeenAt,
        LocalDateTime expiresAt
) {
    public static LocalAgentSessionVO from(LocalAgentSession row) {
        return new LocalAgentSessionVO(
                row.getId(),
                row.getBrandId(),
                row.getHelperName(),
                row.getStatus(),
                row.getBoundAt(),
                row.getLastSeenAt(),
                row.getExpiresAt()
        );
    }
}
