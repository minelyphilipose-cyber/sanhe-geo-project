package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
public class MobileDashboardSessionTokenService {

    private static final String TOKEN_TYPE = "mobile_dashboard";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SecretKey key;
    @Getter
    private final long ttlSeconds;

    public MobileDashboardSessionTokenService(
            @Value("${geo.jwt.secret}") String secret,
            @Value("${geo.mobile-dashboard.session-ttl-seconds:7200}") long ttlSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedSession issue(Long shareId, Long projectId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String token = Jwts.builder()
                .subject(String.valueOf(shareId))
                .claim("type", TOKEN_TYPE)
                .claim("shareId", shareId)
                .claim("projectId", projectId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedSession(token, LocalDateTime.ofInstant(expiresAt, ZONE));
    }

    public SessionClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
                throw new BizException(401, "Invalid mobile dashboard session");
            }
            Long shareId = claims.get("shareId", Number.class).longValue();
            Long projectId = claims.get("projectId", Number.class).longValue();
            return new SessionClaims(shareId, projectId);
        } catch (ExpiredJwtException e) {
            throw new BizException(401, "Mobile dashboard session expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid mobile dashboard session: {}", e.getMessage());
            throw new BizException(401, "Invalid mobile dashboard session");
        }
    }

    public record IssuedSession(String token, LocalDateTime expiresAt) {
    }

    public record SessionClaims(Long shareId, Long projectId) {
    }
}
