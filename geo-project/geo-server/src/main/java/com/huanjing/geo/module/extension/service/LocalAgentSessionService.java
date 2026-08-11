package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentExtensionSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSessionVO;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LocalAgentSessionService {
    private static final int PAIRING_TTL_SECONDS = 300;
    private static final int SESSION_TTL_DAYS = 30;
    private static final int SESSION_RENEW_THRESHOLD_DAYS = 7;
    private static final String TOKEN_PREFIX = "helper.";
    private static final String HELPER_ACCESS_PREFIX = "helper.session.";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_MAX_SKEW_SECONDS = 300;
    private static final Pattern HEX_64 = Pattern.compile("^[a-f0-9]{64}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final LocalAgentSessionMapper sessionMapper;
    private final ExtensionRedisStore redisStore;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public LocalAgentPairingIntentResponse registerPairingIntent(LocalAgentPairingIntentRequest request) {
        String codeHash = requireHexHash(request.codeHash(), "codeHash is invalid");
        String deviceSecretHash = requireHexHash(request.deviceSecretHash(), "deviceSecretHash is invalid");
        PairingIntentPayload payload = new PairingIntentPayload(
                deviceSecretHash,
                trimToNull(request.helperName()),
                now().plusSeconds(PAIRING_TTL_SECONDS)
        );
        redisStore.set(intentKey(codeHash), toJson(payload), Duration.ofSeconds(PAIRING_TTL_SECONDS));
        return new LocalAgentPairingIntentResponse(PAIRING_TTL_SECONDS);
    }

    @Transactional
    public LocalAgentPairingApproveResponse approvePairing(LocalAgentPairingApproveRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        String codeHash = pairingCodeHash(request.pairingCode());
        String intentJson = redisStore.getAndDelete(intentKey(codeHash));
        if (!StringUtils.hasText(intentJson)) {
            throw new BizException(404, "pairing code invalid or expired");
        }
        PairingIntentPayload intent = fromJson(intentJson, PairingIntentPayload.class);
        TokenMaterial token = newToken();
        String hmacSecret = randomTokenMaterial(32);
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plusDays(SESSION_TTL_DAYS);

        sessionMapper.revokeActiveByOperatorId(operator.getId(), now, operator.getId());

        LocalAgentSession row = new LocalAgentSession();
        // New pairings are account-wide. Brand access and environment ownership are checked per execution.
        row.setBrandId(null);
        row.setOperatorId(operator.getId());
        row.setAccessTokenLookupHash(sha256Hex(token.plaintext()));
        row.setAccessTokenHash(saltedSha256Hex(token.saltHex(), token.plaintext()));
        row.setAccessTokenHashAlg("SHA-256");
        row.setAccessTokenSalt(token.saltHex());
        row.setHmacSecret(hmacSecret);
        row.setDeviceSecretHash(intent.deviceSecretHash());
        row.setHelperName(intent.helperName());
        row.setStatus("active");
        row.setBoundAt(now);
        row.setLastSeenAt(null);
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        sessionMapper.insert(row);

        ClaimPayload claim = new ClaimPayload(
                row.getId(),
                row.getOperatorId(),
                token.plaintext(),
                hmacSecret,
                expiresAt,
                intent.deviceSecretHash()
        );
        redisStore.set(claimKey(codeHash), toJson(claim), Duration.ofSeconds(PAIRING_TTL_SECONDS));
        return new LocalAgentPairingApproveResponse(row.getId(), row.getBrandId(), row.getExpiresAt());
    }

    public LocalAgentPairingClaimResponse claimPairing(LocalAgentPairingClaimRequest request, String userAgent) {
        String codeHash = pairingCodeHash(request.pairingCode());
        String claimJson = redisStore.getAndDelete(claimKey(codeHash));
        if (!StringUtils.hasText(claimJson)) {
            throw new BizException(404, "pairing is not approved or has expired");
        }
        ClaimPayload claim = fromJson(claimJson, ClaimPayload.class);
        String deviceSecretHash = requireHexHash(request.deviceSecretHash(), "deviceSecretHash is invalid");
        if (!claim.deviceSecretHash().equals(deviceSecretHash)) {
            throw new BizException(403, "pairing device mismatch");
        }
        sessionMapper.touchActive(claim.sessionId(), now(), userAgent);
        return new LocalAgentPairingClaimResponse(
                claim.sessionId(),
                null,
                claim.operatorId(),
                claim.accessToken(),
                claim.hmacSecret(),
                claim.expiresAt()
        );
    }

    public List<LocalAgentSessionVO> listActiveSessions() {
        SysUser operator = currentUserService.requireCurrentUser();
        return sessionMapper.selectActiveByOperatorId(operator.getId(), now()).stream()
                .map(LocalAgentSessionVO::from)
                .toList();
    }

    public LocalAgentSignResponse signRequest(Long sessionId, LocalAgentSignRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        LocalAgentSession session = requireActiveSessionById(sessionId);
        requireSessionOwner(session, operator.getId());
        return signRequestForSession(session, request.method(), request.path(), request.bodyHash());
    }

    public LocalAgentSignResponse signRequestForExtension(ExtensionSession extensionSession,
                                                          LocalAgentExtensionSignRequest request) {
        if (extensionSession == null || extensionSession.getOperatorId() == null) {
            throw new BizException(401, "extension session is required");
        }
        LocalAgentSession session;
        if (request.localAgentSessionId() != null) {
            session = requireActiveSessionById(request.localAgentSessionId());
            requireSessionOwner(session, extensionSession.getOperatorId());
        } else {
            session = sessionMapper.selectActiveByOperatorId(extensionSession.getOperatorId(), now()).stream()
                    .filter(item -> item.getLastSeenAt() != null)
                    .findFirst()
                    .orElseThrow(() -> new BizException(404, "未找到当前账号已配对的本地助手会话"));
        }
        return signRequestForSession(session, request.method(), request.path(), request.bodyHash());
    }

    public LocalAgentSession verifySignedRequest(String methodValue,
                                                 String pathValue,
                                                 String bodyHashValue,
                                                 String helperAccessValue,
                                                 String timestampValue,
                                                 String nonceValue,
                                                 String signatureValue,
                                                 String userAgent) {
        String method = requireMethod(methodValue);
        String path = requirePath(pathValue);
        String bodyHash = requireHexHash(bodyHashValue, "bodyHash is invalid");
        String helperAccess = requireText(helperAccessValue, "helper access is required");
        if (!helperAccess.startsWith(HELPER_ACCESS_PREFIX)) {
            throw new BizException(401, "invalid local helper access");
        }
        Long sessionId;
        try {
            sessionId = Long.parseLong(helperAccess.substring(HELPER_ACCESS_PREFIX.length()));
        } catch (NumberFormatException ex) {
            throw new BizException(401, "invalid local helper access");
        }
        LocalAgentSession session = requireActiveSessionById(sessionId);
        String timestamp = requireText(timestampValue, "timestamp is required");
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new BizException(401, "invalid local helper timestamp");
        }
        long nowSeconds = clock.instant().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > SIGNATURE_MAX_SKEW_SECONDS) {
            throw new BizException(401, "local helper signature expired");
        }
        String nonce = requireText(nonceValue, "nonce is required");
        if (!redisStore.tryLock(nonceKey(sessionId, nonce), timestamp, Duration.ofSeconds(SIGNATURE_MAX_SKEW_SECONDS))) {
            throw new BizException(401, "local helper nonce replayed");
        }
        String signature = requireText(signatureValue, "signature is required");
        String canonical = canonical(method, path, bodyHash, timestamp, nonce, helperAccess);
        String expected = hmacSha256Base64Url(session.getHmacSecret(), canonical);
        if (!constantTimeEquals(signature, expected)) {
            throw new BizException(401, "local helper signature invalid");
        }
        LocalDateTime verifiedAt = now();
        sessionMapper.touchActive(sessionId, verifiedAt, userAgent);
        renewActiveSessionIfDue(session, verifiedAt);
        return session;
    }

    private void renewActiveSessionIfDue(LocalAgentSession session, LocalDateTime verifiedAt) {
        LocalDateTime renewBefore = verifiedAt.plusDays(SESSION_RENEW_THRESHOLD_DAYS);
        if (session.getExpiresAt().isAfter(renewBefore)) {
            return;
        }
        LocalDateTime renewedExpiresAt = verifiedAt.plusDays(SESSION_TTL_DAYS);
        int updated = sessionMapper.renewActiveExpiry(
                session.getId(),
                verifiedAt,
                renewBefore,
                renewedExpiresAt
        );
        if (updated > 0) {
            session.setExpiresAt(renewedExpiresAt);
            return;
        }
        LocalAgentSession latest = sessionMapper.selectById(session.getId());
        if (latest != null
                && "active".equals(latest.getStatus())
                && latest.getExpiresAt() != null
                && latest.getExpiresAt().isAfter(verifiedAt)) {
            session.setExpiresAt(latest.getExpiresAt());
        }
    }

    private LocalAgentSignResponse signRequestForSession(LocalAgentSession session,
                                                         String methodValue,
                                                         String pathValue,
                                                         String bodyHashValue) {
        String method = requireMethod(methodValue);
        String path = requirePath(pathValue);
        String bodyHash = requireHexHash(bodyHashValue, "bodyHash is invalid");
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String helperAccess = HELPER_ACCESS_PREFIX + session.getId();
        String canonical = canonical(method, path, bodyHash, timestamp, nonce, helperAccess);
        String signature = hmacSha256Base64Url(session.getHmacSecret(), canonical);
        return new LocalAgentSignResponse(Map.of(
                "X-Geo-Helper-Access", helperAccess,
                "X-Geo-Helper-Timestamp", timestamp,
                "X-Geo-Helper-Nonce", nonce,
                "X-Geo-Helper-Signature", signature
        ));
    }

    @Transactional
    public void revoke(Long sessionId) {
        SysUser operator = currentUserService.requireCurrentUser();
        LocalAgentSession session = requireActiveSessionById(sessionId);
        requireSessionOwner(session, operator.getId());
        sessionMapper.revokeActive(sessionId, now(), operator.getId());
    }

    private void requireSessionOwner(LocalAgentSession session, Long operatorId) {
        if (session == null || operatorId == null || !operatorId.equals(session.getOperatorId())) {
            throw new BizException(403, "local agent session does not belong to current operator");
        }
    }

    private LocalAgentSession requireActiveSessionById(Long sessionId) {
        if (sessionId == null) throw new BizException(400, "sessionId is required");
        LocalAgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"active".equals(session.getStatus())) {
            throw new BizException(404, "local agent session not found");
        }
        if (!session.getExpiresAt().isAfter(now())) {
            throw new BizException(401, "local agent session expired");
        }
        return session;
    }

    private String requireMethod(String value) {
        String method = requireText(value, "method is required").toUpperCase(Locale.ROOT);
        if (!method.equals("GET") && !method.equals("POST")) {
            throw new BizException(400, "unsupported local helper method");
        }
        return method;
    }

    private String requirePath(String value) {
        String path = requireText(value, "path is required");
        String pathOnly = path;
        int queryIndex = pathOnly.indexOf('?');
        if (queryIndex >= 0) {
            pathOnly = pathOnly.substring(0, queryIndex);
        }
        if (!pathOnly.startsWith("/v1/poc/")
                && !pathOnly.startsWith("/v1/extension/")
                && !pathOnly.startsWith("/v1/adspower/")
                && !pathOnly.startsWith("/api/v1/local-agent/")) {
            throw new BizException(400, "unsupported local helper path");
        }
        if (pathOnly.contains("..") || path.contains("\n") || path.contains("\r")) {
            throw new BizException(400, "invalid local helper path");
        }
        return path;
    }

    private String requireHexHash(String value, String message) {
        String text = requireText(value, message).toLowerCase(Locale.ROOT);
        if (!HEX_64.matcher(text).matches()) {
            throw new BizException(400, message);
        }
        return text;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    private String pairingCodeHash(String pairingCode) {
        String normalized = requireText(pairingCode, "pairingCode is required")
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() < 8 || normalized.length() > 16) {
            throw new BizException(400, "pairingCode is invalid");
        }
        return sha256Hex(normalized);
    }

    private String canonical(String method, String path, String bodyHash, String timestamp, String nonce, String helperAccess) {
        return method + "\n" + path + "\n" + bodyHash + "\n" + timestamp + "\n" + nonce + "\n" + helperAccess;
    }

    private TokenMaterial newToken() {
        String plaintext = randomTokenMaterial(32);
        String saltHex = HexFormat.of().formatHex(randomBytes(16));
        return new TokenMaterial(plaintext, saltHex);
    }

    private String randomTokenMaterial(int length) {
        return TOKEN_PREFIX + BASE64_URL_ENCODER.encodeToString(randomBytes(length));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String hmacSha256Base64Url(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return leftBytes.length == rightBytes.length && MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String saltedSha256Hex(String saltHex, String plaintext) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] token = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[salt.length + token.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(token, 0, combined, salt.length, token.length);
        return sha256Hex(combined);
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "local agent payload serialization failed", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "local agent payload invalid", ex);
        }
    }

    private String intentKey(String codeHash) {
        return "local_agent:pairing:intent:" + codeHash;
    }

    private String claimKey(String codeHash) {
        return "local_agent:pairing:claim:" + codeHash;
    }

    private String nonceKey(Long sessionId, String nonce) {
        return "local_agent:request_nonce:" + sessionId + ":" + nonce;
    }

    private record PairingIntentPayload(String deviceSecretHash, String helperName, LocalDateTime expiresAt) {
    }

    private record ClaimPayload(Long sessionId,
                                Long operatorId,
                                String accessToken,
                                String hmacSecret,
                                LocalDateTime expiresAt,
                                String deviceSecretHash) {
    }

    private record TokenMaterial(String plaintext, String saltHex) {
    }
}
