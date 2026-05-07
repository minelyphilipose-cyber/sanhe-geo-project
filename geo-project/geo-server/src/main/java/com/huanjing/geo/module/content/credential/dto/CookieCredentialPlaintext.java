package com.huanjing.geo.module.content.credential.dto;

public record CookieCredentialPlaintext(
        Long selfMediaAccountId,
        Long brandId,
        String platform,
        Integer version,
        String cookiesJson,
        String userAgent,
        String requiredCookieCheckJson
) {
}
