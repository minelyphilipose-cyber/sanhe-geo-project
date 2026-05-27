package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_INTERNAL_ERROR;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_OPERATOR_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;

@Service
@RequiredArgsConstructor
public class FillTokenService {

    public static final String REQUIRED_PLACEHOLDER = "__REQUIRED_FILL_TOKEN_HMAC_SECRET__";
    private static final String TOKEN_PREFIX = "ft.";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String FILL_TOKEN_MARKER_VALID = "1";
    private static final String FILL_TOKEN_MARKER_CONSUMING_PREFIX = "consuming:";
    /*
     * Cross-store guard: Redis marker and MySQL task state cannot share a transaction.
     * 30s is intentionally much longer than a normal conditional DB update and short
     * enough that a failed consume attempt does not block a user for the full token TTL.
     */
    private static final Duration CONSUMING_TTL = Duration.ofSeconds(30);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ExtensionProperties properties;
    private final ExtensionRedisStore redisStore;
    private final ExtensionVersionService versionService;
    private final ExtensionAuditSupport auditSupport;
    private final Clock clock = Clock.systemUTC();
    private byte[] decodedSecret;

    @PostConstruct
    void validateSecret() {
        String secret = properties.getFillToken().getHmacSecret();
        if (!StringUtils.hasText(secret) || REQUIRED_PLACEHOLDER.equals(secret.trim())) {
            throw new IllegalStateException(
                    "geo.extension.fill-token.hmac-secret is required. Generate via: openssl rand -base64 32"
            );
        }
        try {
            decodedSecret = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("geo.extension.fill-token.hmac-secret must be base64-encoded", ex);
        }
        if (decodedSecret.length < 32) {
            throw new IllegalStateException("geo.extension.fill-token.hmac-secret must decode to at least 32 bytes");
        }
    }

    public FillTokenIssueResponse issue(
            Long accountId,
            Long brandId,
            Long operatorId,
            Long taskTargetId,
            String platform,
            String extensionVersion
    ) {
        if (accountId == null || brandId == null || operatorId == null || taskTargetId == null) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token issue context is required");
        }
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(extensionVersion)) {
            throw new BizException(FILL_TOKEN_INVALID, "extension platform and version are required");
        }
        versionService.requireSupported(platform, extensionVersion);
        return issueInternalWithoutVersionCheck(accountId, brandId, operatorId, taskTargetId);
    }

    public FillTokenIssueResponse issueInternalWithoutVersionCheck(
            Long accountId,
            Long brandId,
            Long operatorId,
            Long taskTargetId
    ) {
        if (accountId == null || brandId == null || operatorId == null || taskTargetId == null) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token issue context is required");
        }
        long issuedAt = clock.instant().getEpochSecond();
        long expiresAt = issuedAt + properties.getFillToken().getTtlSeconds();
        String nonce = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(properties.getFillToken().getTtlSeconds());
        boolean issueReserved = redisStore.tryLock(fillTokenTaskKey(taskTargetId), nonce, ttl);
        if (!issueReserved) {
            throw new BizException(TASK_STATE_CONFLICT, "active fill token already issued for task");
        }
        try {
            FillTokenPayload payload = new FillTokenPayload(
                    FillTokenPayload.CURRENT_VERSION,
                    accountId,
                    brandId,
                    operatorId,
                    taskTargetId,
                    expiresAt,
                    issuedAt,
                    nonce
            );
            String payloadPart = BASE64_URL_ENCODER.encodeToString(payload.canonicalString().getBytes(StandardCharsets.UTF_8));
            String signaturePart = BASE64_URL_ENCODER.encodeToString(hmac(payload.canonicalString()));
            String token = TOKEN_PREFIX + payloadPart + "." + signaturePart;
            redisStore.set(fillTokenKey(token), FILL_TOKEN_MARKER_VALID, ttl);
            auditSupport.record(
                    "FILL_TOKEN_ISSUE",
                    AuditResult.SUCCESS,
                    AuditMode.SYNC,
                    true,
                    operatorId,
                    brandId,
                    accountId,
                    taskTargetId,
                    null,
                    "FILL_TOKEN",
                    nonce,
                    null,
                    null,
                    Map.of("expiresAt", expiresAt, "issuedAt", issuedAt)
            );
            return new FillTokenIssueResponse(token, expiresAt, nonce);
        } catch (RuntimeException ex) {
            redisStore.releaseLock(fillTokenTaskKey(taskTargetId), nonce);
            throw ex;
        }
    }

    public FillTokenConsumeResponse consume(String token, Long expectedOperatorId) {
        return consume(token, expectedOperatorId, null);
    }

    public FillTokenConsumeResponse consume(String token, Long expectedOperatorId, Long extensionSessionId) {
        FillTokenPayload payload = verify(token);
        if (expectedOperatorId != null && payload.op() != expectedOperatorId) {
            auditFillConsumeDenied(payload, extensionSessionId, FILL_TOKEN_OPERATOR_MISMATCH, "OPERATOR_MISMATCH");
            throw new BizException(FILL_TOKEN_OPERATOR_MISMATCH, "fill token operator mismatch");
        }
        reserveConsume(token, payload);
        completeConsume(token, payload);
        auditSupport.record(
                "FILL_TOKEN_CONSUME",
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                true,
                payload.op(),
                payload.bid(),
                payload.aid(),
                payload.tid(),
                extensionSessionId,
                "FILL_TOKEN",
                payload.n(),
                null,
                null,
                Map.of("expiresAt", payload.exp(), "issuedAt", payload.iat())
        );
        return new FillTokenConsumeResponse(
                payload.aid(),
                payload.bid(),
                payload.op(),
                payload.tid(),
                payload.exp(),
                payload.n()
        );
    }

    public FillTokenPayload verify(String token) {
        if (!StringUtils.hasText(token) || !token.startsWith(TOKEN_PREFIX)) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token invalid");
        }
        String[] parts = token.substring(TOKEN_PREFIX.length()).split("\\.", -1);
        if (parts.length != 2) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token invalid");
        }
        String canonical;
        byte[] providedSignature;
        try {
            canonical = new String(BASE64_URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            providedSignature = BASE64_URL_DECODER.decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token invalid", ex);
        }
        byte[] expectedSignature = hmac(canonical);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new BizException(FILL_TOKEN_INVALID, "fill token signature invalid");
        }
        FillTokenPayload payload = FillTokenPayload.parseCanonical(canonical);
        if (payload.exp() <= clock.instant().getEpochSecond()) {
            releaseTaskGuard(payload);
            throw new BizException(FILL_TOKEN_USED_OR_EXPIRED, "fill token used or expired");
        }
        return payload;
    }

    private String fillTokenKey(String token) {
        return "fill_token:" + HashSupport.sha256Hex(token);
    }

    private String fillTokenTaskKey(Long taskId) {
        return "fill_token_task:" + taskId;
    }

    public void reserveConsume(String token, FillTokenPayload payload) {
        String marker = FILL_TOKEN_MARKER_CONSUMING_PREFIX + payload.n();
        boolean reserved = redisStore.compareAndSet(fillTokenKey(token), FILL_TOKEN_MARKER_VALID, marker, CONSUMING_TTL);
        if (!reserved) {
            releaseTaskGuardIfTokenMarkerMissing(token, payload);
            auditFillConsumeDenied(payload, null, FILL_TOKEN_USED_OR_EXPIRED, "USED_OR_EXPIRED");
            throw new BizException(FILL_TOKEN_USED_OR_EXPIRED, "fill token used or expired");
        }
    }

    public void completeConsume(String token, FillTokenPayload payload) {
        boolean completed = redisStore.compareAndSet(
                fillTokenKey(token),
                FILL_TOKEN_MARKER_CONSUMING_PREFIX + payload.n(),
                "consumed",
                Duration.ofSeconds(1)
        );
        if (completed) {
            redisStore.getAndDelete(fillTokenKey(token));
            releaseTaskGuard(payload);
        }
    }

    public void restoreConsume(String token, FillTokenPayload payload) {
        redisStore.compareAndSet(
                fillTokenKey(token),
                FILL_TOKEN_MARKER_CONSUMING_PREFIX + payload.n(),
                FILL_TOKEN_MARKER_VALID,
                Duration.ofSeconds(Math.max(1, payload.exp() - clock.instant().getEpochSecond()))
        );
    }

    private void releaseTaskGuardIfTokenMarkerMissing(String token, FillTokenPayload payload) {
        if (redisStore.get(fillTokenKey(token)) == null) {
            releaseTaskGuard(payload);
        }
    }

    private void releaseTaskGuard(FillTokenPayload payload) {
        redisStore.releaseLock(fillTokenTaskKey(payload.tid()), payload.n());
    }

    private byte[] hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secretBytes(), HMAC_ALG));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new BizException(EXTENSION_INTERNAL_ERROR, "fill token signing failed", ex);
        }
    }

    private byte[] secretBytes() {
        if (decodedSecret == null) {
            validateSecret();
        }
        return decodedSecret;
    }

    private void auditFillConsumeDenied(FillTokenPayload payload, Long extensionSessionId, int errorCode, String reason) {
        auditSupport.record(
                "FILL_TOKEN_CONSUME",
                AuditResult.DENIED,
                AuditMode.SYNC,
                true,
                payload.op(),
                payload.bid(),
                payload.aid(),
                payload.tid(),
                extensionSessionId,
                "FILL_TOKEN",
                payload.n(),
                String.valueOf(errorCode),
                reason,
                Map.of("expiresAt", payload.exp(), "issuedAt", payload.iat(), "reason", reason)
        );
    }
}
