package com.huanjing.geo.module.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Extension-facing fill token consumption result.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtensionFillTokenConsumeResponse(
        Long taskTargetId,
        long expiresAt,
        String nonce,
        String fillPayload
) {
}
