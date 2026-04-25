package com.huanjing.geo.module.presale.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleRenderTokenService {
    private static final String KEY_PREFIX = "presale:export:render_token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenIssueResult issue(Long exportId, Long reportId, Long versionId, Duration ttl) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = tokenId + "." + UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + tokenId,
                    objectMapper.writeValueAsString(TokenPayload.builder()
                            .exportId(exportId)
                            .reportId(reportId)
                            .versionId(versionId)
                            .build()), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Issue render token failed", ex);
        }
        return new TokenIssueResult(tokenId, token);
    }

    public TokenPayload resolve(String token) {
        String tokenId = tokenId(token);
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + tokenId);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, TokenPayload.class);
        } catch (Exception ex) {
            log.warn("Invalid presale render token payload, tokenId={}", tokenId, ex);
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
            log.warn("Invalidate presale render token failed, tokenId={}", tokenId, ex);
        }
    }

    private String tokenId(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        return dot > 0 ? token.substring(0, dot) : token;
    }

    public record TokenIssueResult(String tokenId, String token) {}

    @Data
    @Builder
    public static class TokenPayload {
        private Long exportId;
        private Long reportId;
        private Long versionId;
    }
}