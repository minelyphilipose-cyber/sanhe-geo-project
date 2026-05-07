package com.huanjing.geo.module.extension.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Extension-facing fill token consumption result.
 *
 * <p>The cookie fields are plaintext secrets for immediate extension use only. They must not be
 * logged, cached, or persisted by callers.</p>
 *
 * @param cookiesJson cookies as a JSON string. Expected format is a JSON array of cookie objects
 *                    that the extension parses before browser injection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtensionFillTokenConsumeResponse(
        Long taskTargetId,
        long expiresAt,
        String nonce,
        String platform,
        Integer credentialVersion,
        String cookiesJson,
        String userAgent,
        String requiredCookieCheckJson
) {
}
