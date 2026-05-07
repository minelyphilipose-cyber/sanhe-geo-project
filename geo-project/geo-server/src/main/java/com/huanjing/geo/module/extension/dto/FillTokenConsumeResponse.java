package com.huanjing.geo.module.extension.dto;

public record FillTokenConsumeResponse(
        Long accountId,
        Long brandId,
        Long operatorId,
        Long taskTargetId,
        long expiresAt,
        String nonce
) {
}
