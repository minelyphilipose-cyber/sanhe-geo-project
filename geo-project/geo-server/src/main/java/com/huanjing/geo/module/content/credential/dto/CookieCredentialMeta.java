package com.huanjing.geo.module.content.credential.dto;

import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;

import java.time.LocalDateTime;

public record CookieCredentialMeta(
        Long id,
        Long selfMediaAccountId,
        Long brandId,
        String platform,
        Integer version,
        String masterKeyId,
        String cipherAlg,
        String cookieIvBase64,
        String aadContext,
        String userAgent,
        String capturedFingerprintJson,
        String requiredCookieCheckJson,
        Long capturedBy,
        LocalDateTime capturedAt,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        LocalDateTime destroyedAt,
        LocalDateTime createdAt
) {
    public static CookieCredentialMeta from(SelfMediaCookieCredential credential) {
        if (credential == null) {
            return null;
        }
        return new CookieCredentialMeta(
                credential.getId(),
                credential.getSelfMediaAccountId(),
                credential.getBrandId(),
                credential.getPlatform(),
                credential.getVersion(),
                credential.getMasterKeyId(),
                credential.getCipherAlg(),
                credential.getCookieIvBase64(),
                credential.getAadContext(),
                credential.getUserAgent(),
                credential.getCapturedFingerprintJson(),
                credential.getRequiredCookieCheckJson(),
                credential.getCapturedBy(),
                credential.getCapturedAt(),
                credential.getValidFrom(),
                credential.getValidUntil(),
                credential.getDestroyedAt(),
                credential.getCreatedAt()
        );
    }
}
