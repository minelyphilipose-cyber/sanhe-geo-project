package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobileDashboardSessionTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void parseAcceptsIssuedMobileDashboardSession() {
        MobileDashboardSessionTokenService service = new MobileDashboardSessionTokenService(SECRET, 7200);

        MobileDashboardSessionTokenService.IssuedSession issued = service.issue(5L, 11L);
        MobileDashboardSessionTokenService.SessionClaims claims = service.parse(issued.token());

        assertThat(claims.shareId()).isEqualTo(5L);
        assertThat(claims.projectId()).isEqualTo(11L);
    }

    @Test
    void parseRejectsWrongTokenType() {
        MobileDashboardSessionTokenService service = new MobileDashboardSessionTokenService(SECRET, 7200);
        String token = Jwts.builder()
                .subject("5")
                .claim("type", "report_share")
                .claim("shareId", 5L)
                .claim("projectId", 11L)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        BizException ex = assertThrows(BizException.class, () -> service.parse(token));

        assertThat(ex.getMessage()).contains("Invalid mobile dashboard session");
    }

    @Test
    void parseRejectsExpiredSession() throws InterruptedException {
        MobileDashboardSessionTokenService service = new MobileDashboardSessionTokenService(SECRET, -1);
        String token = service.issue(5L, 11L).token();

        BizException ex = assertThrows(BizException.class, () -> service.parse(token));

        assertThat(ex.getMessage()).contains("expired");
    }
}
