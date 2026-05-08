package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.BindCodeCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.BIND_CODE_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.BIND_RATE_LIMIT_EXCEEDED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_INTERNAL_ERROR;

@Service
@RequiredArgsConstructor
public class ExtensionBindCodeService {

    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration RATE_LIMIT_TTL = Duration.ofMinutes(5);

    private final ExtensionProperties properties;
    private final ExtensionRedisStore redisStore;
    private final BrandAccessService brandAccessService;
    private final ObjectMapper objectMapper;
    private final ExtensionAuditSupport auditSupport;
    private final Clock clock = Clock.systemUTC();

    public BindCodeCreateResponse create(Long brandId, Long operatorId) {
        brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.MANAGE);
        String code = generateCode();
        long expiresAt = clock.instant().getEpochSecond() + properties.getBindCode().getTtlSeconds();
        BindCodePayload payload = new BindCodePayload(brandId, operatorId, expiresAt);
        redisStore.set(bindCodeKey(code), toJson(payload), Duration.ofSeconds(properties.getBindCode().getTtlSeconds()));
        auditSupport.record(
                "EXTENSION_BIND_CODE_CREATE",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                operatorId,
                brandId,
                null,
                null,
                null,
                "BIND_CODE",
                null,
                null,
                null,
                Map.of("expiresAt", expiresAt)
        );
        return new BindCodeCreateResponse(displayCode(code), brandId, operatorId, properties.getBindCode().getTtlSeconds());
    }

    public BindCodePayload consume(String code, Long expectedBrandId, String ipAddress) {
        String normalized = normalizeCode(code);
        try {
            if (expectedBrandId != null) {
                enforceBrandRateLimit(expectedBrandId, ipAddress);
            }
            enforceIpRateLimit(ipAddress);
        } catch (BizException ex) {
            auditSupport.record(
                    "BIND_RATE_LIMIT_EXCEEDED",
                    AuditResult.DENIED,
                    AuditMode.SYNC,
                    true,
                    null,
                    expectedBrandId,
                    null,
                    null,
                    null,
                    "BIND_CODE",
                    null,
                    String.valueOf(ex.getCode()),
                    ex.getMessage(),
                    Map.of("ipAddress", StringUtils.hasText(ipAddress) ? ipAddress : "unknown")
            );
            throw ex;
        }
        String json = redisStore.getAndDelete(bindCodeKey(normalized));
        if (!StringUtils.hasText(json)) {
            auditBindConsumeDenied(expectedBrandId, null, ipAddress, "INVALID_OR_EXPIRED");
            throw new BizException(BIND_CODE_INVALID, "bind code invalid or expired");
        }
        BindCodePayload payload = fromJson(json);
        if (expectedBrandId != null && !payload.brandId().equals(expectedBrandId)) {
            auditBindConsumeDenied(expectedBrandId, payload.operatorId(), ipAddress, "BRAND_MISMATCH");
            throw new BizException(BIND_CODE_INVALID, "bind code brand mismatch");
        }
        if (expectedBrandId == null) {
            enforceBrandRateLimit(payload.brandId(), ipAddress);
        }
        if (payload.expiresAt() <= clock.instant().getEpochSecond()) {
            auditBindConsumeDenied(payload.brandId(), payload.operatorId(), ipAddress, "EXPIRED");
            throw new BizException(BIND_CODE_INVALID, "bind code invalid or expired");
        }
        brandAccessService.requireBrandAccess(payload.brandId(), payload.operatorId(), BrandAccessAction.MANAGE);
        auditSupport.record(
                "BIND_CODE_CONSUME",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                payload.operatorId(),
                payload.brandId(),
                null,
                null,
                null,
                "BIND_CODE",
                null,
                null,
                null,
                Map.of("ipAddress", StringUtils.hasText(ipAddress) ? ipAddress : "unknown", "expiresAt", payload.expiresAt())
        );
        return payload;
    }

    private void enforceBrandRateLimit(Long brandId, String ipAddress) {
        long window = clock.instant().getEpochSecond() / 300;
        long brandAttempts = redisStore.incrementWithTtl(
                "bind_attempts:brand:" + brandId + ":" + window,
                RATE_LIMIT_TTL
        );
        if (brandAttempts > properties.getBindCode().getBrandRateLimitPer5min()) {
            auditRateLimitDenied(brandId, ipAddress, "BRAND", brandAttempts);
            throw new BizException(BIND_RATE_LIMIT_EXCEEDED, "bind code attempts exceeded");
        }
    }

    private void enforceIpRateLimit(String ipAddress) {
        long window = clock.instant().getEpochSecond() / 300;
        String ip = StringUtils.hasText(ipAddress) ? ipAddress : "unknown";
        long ipAttempts = redisStore.incrementWithTtl("bind_attempts:ip:" + ip + ":" + window, RATE_LIMIT_TTL);
        if (ipAttempts > properties.getBindCode().getIpRateLimitPer5min()) {
            auditRateLimitDenied(null, ipAddress, "IP", ipAttempts);
            throw new BizException(BIND_RATE_LIMIT_EXCEEDED, "bind code attempts exceeded");
        }
    }

    private String generateCode() {
        int length = properties.getBindCode().getLength();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CROCKFORD.charAt(SECURE_RANDOM.nextInt(CROCKFORD.length())));
        }
        return builder.toString();
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(BIND_CODE_INVALID, "bind code is required");
        }
        String normalized = code.replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != properties.getBindCode().getLength()) {
            throw new BizException(BIND_CODE_INVALID, "bind code invalid");
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (CROCKFORD.indexOf(normalized.charAt(i)) < 0) {
                throw new BizException(BIND_CODE_INVALID, "bind code invalid");
            }
        }
        return normalized;
    }

    private String displayCode(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    private String bindCodeKey(String code) {
        return "bind_code:" + code;
    }

    private String toJson(BindCodePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(EXTENSION_INTERNAL_ERROR, "bind code payload serialization failed", ex);
        }
    }

    private BindCodePayload fromJson(String json) {
        try {
            return objectMapper.readValue(json, BindCodePayload.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(BIND_CODE_INVALID, "bind code payload invalid", ex);
        }
    }

    private void auditBindConsumeDenied(Long brandId, Long operatorId, String ipAddress, String reason) {
        auditSupport.record(
                "BIND_CODE_CONSUME",
                AuditResult.DENIED,
                AuditMode.SYNC,
                true,
                operatorId,
                brandId,
                null,
                null,
                null,
                "BIND_CODE",
                null,
                String.valueOf(BIND_CODE_INVALID),
                reason,
                Map.of("reason", reason, "ipAddress", StringUtils.hasText(ipAddress) ? ipAddress : "unknown")
        );
    }

    private void auditRateLimitDenied(Long brandId, String ipAddress, String dimension, long attempts) {
        auditSupport.record(
                "BIND_RATE_LIMIT_EXCEEDED",
                AuditResult.DENIED,
                AuditMode.SYNC,
                true,
                null,
                brandId,
                null,
                null,
                null,
                "BIND_CODE",
                null,
                String.valueOf(BIND_RATE_LIMIT_EXCEEDED),
                "RATE_LIMIT_" + dimension,
                Map.of(
                        "dimension", dimension,
                        "attempts", attempts,
                        "ipAddress", StringUtils.hasText(ipAddress) ? ipAddress : "unknown"
                )
        );
    }

    public record BindCodePayload(Long brandId, Long operatorId, long expiresAt) {
    }
}
