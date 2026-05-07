package com.huanjing.geo.module.content.credential.dto;

public record CookieCredentialCaptureCommand(
        Long selfMediaAccountId,
        String cookiesJson,
        String userAgent,
        String capturedFingerprintJson,
        String requiredCookieCheckJson,
        Long capturedBy
) {
}
