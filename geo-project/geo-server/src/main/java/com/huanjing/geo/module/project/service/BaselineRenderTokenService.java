package com.huanjing.geo.module.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineRenderTokenService {
    private static final String KEY_PREFIX = "baseline:export:render_token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenIssueResult issue(Long exportId, Long projectId, Long baselineId, Duration ttl) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String secret = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String token = tokenId + "." + secret;
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + tokenId,
                    objectMapper.writeValueAsString(TokenPayload.builder()
                            .exportId(exportId)
                            .projectId(projectId)
                            .baselineId(baselineId)
                            .secretHash(sha256Base64(secret))
                            .build()), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Issue baseline render token failed", ex);
        }
        return new TokenIssueResult(tokenId, token);
    }

    public TokenPayload resolve(String token) {
        TokenParts parts = parseToken(token);
        if (parts == null) {
            return null;
        }
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + parts.tokenId());
        if (raw == null) {
            return null;
        }
        try {
            TokenPayload payload = objectMapper.readValue(raw, TokenPayload.class);
            if (!secretMatches(parts.secret(), payload.getSecretHash())) {
                log.warn("Baseline render token secret mismatch, tokenId={}", parts.tokenId());
                return null;
            }
            return payload;
        } catch (Exception ex) {
            log.warn("Invalid baseline render token payload, tokenId={}", parts.tokenId(), ex);
            return null;
        }
    }

    public void invalidate(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + tokenId);
        } catch (Exception ex) {
            log.warn("Invalidate baseline render token failed, tokenId={}", tokenId, ex);
        }
    }

    private boolean secretMatches(String secret, String expectedHash) {
        if (secret == null || secret.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        byte[] actual = sha256Base64(secret).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private String sha256Base64(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    private TokenParts parseToken(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            return null;
        }
        return new TokenParts(token.substring(0, dot), token.substring(dot + 1));
    }

    public record TokenIssueResult(String tokenId, String token) {
    }

    private record TokenParts(String tokenId, String secret) {
    }

    @Data
    @Builder
    public static class TokenPayload {
        private Long exportId;
        private Long projectId;
        private Long baselineId;
        private String secretHash;
    }
}
