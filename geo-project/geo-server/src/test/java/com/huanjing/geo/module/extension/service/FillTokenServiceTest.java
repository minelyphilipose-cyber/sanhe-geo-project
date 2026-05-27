package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_INVALID;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_OPERATOR_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FillTokenServiceTest {

    private ExtensionProperties properties;
    private ExtensionRedisStore redisStore;
    private ExtensionVersionService versionService;
    private ExtensionAuditSupport auditSupport;
    private FillTokenService fillTokenService;

    @BeforeEach
    void setUp() {
        properties = new ExtensionProperties();
        properties.getFillToken().setHmacSecret(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        ));
        redisStore = mock(ExtensionRedisStore.class);
        versionService = mock(ExtensionVersionService.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        fillTokenService = new FillTokenService(properties, redisStore, versionService, auditSupport);
        fillTokenService.validateSecret();
        when(redisStore.tryLock(any(), any(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void canonicalStringIsStable() {
        FillTokenPayload payload = new FillTokenPayload(1, 20, 10, 99, 30, 200, 100, "nonce");
        assertEquals("1|20|10|99|30|200|100|nonce", payload.canonicalString());
        assertEquals(payload, FillTokenPayload.parseCanonical(payload.canonicalString()));
    }

    @Test
    void issuedTokenCarriesSignedPayloadAndConsumesOnce() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        when(redisStore.compareAndSet(any(), eq("1"), any(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);
        when(redisStore.compareAndSet(any(), org.mockito.ArgumentMatchers.startsWith("consuming:"), eq("consumed"), any(Duration.class)))
                .thenReturn(true);

        FillTokenConsumeResponse response = fillTokenService.consume(token, 99L);

        assertEquals(20L, response.accountId());
        assertEquals(10L, response.brandId());
        assertEquals(99L, response.operatorId());
        verify(redisStore).releaseLock(any(), any());
        assertEquals(FILL_TOKEN_USED_OR_EXPIRED,
                assertThrows(BizException.class, () -> fillTokenService.consume(token, 99L)).getCode());
    }

    @Test
    void publicIssueRequiresExtensionVersion() {
        BizException ex = assertThrows(BizException.class,
                () -> fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", null));

        assertEquals(FILL_TOKEN_INVALID, ex.getCode());
        verify(redisStore, never()).set(any(), any(), any());
    }

    @Test
    void internalIssueSkipsVersionCheck() {
        fillTokenService.issueInternalWithoutVersionCheck(20L, 10L, 99L, 30L);

        verify(versionService, never()).requireSupported(any(), any());
        verify(redisStore).tryLock(eq("fill_token_task:30"), any(), any(Duration.class));
        verify(redisStore).set(any(), eq("1"), any());
        verify(auditSupport).record(
                eq("FILL_TOKEN_ISSUE"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(null),
                eq("FILL_TOKEN"),
                any(),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void tamperedTokenFailsSignatureCheck() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        BizException ex = assertThrows(BizException.class, () -> fillTokenService.verify(tampered));

        assertEquals(FILL_TOKEN_INVALID, ex.getCode());
    }

    @Test
    void expiredTokenFailsBeforeRedisConsume() {
        properties.getFillToken().setTtlSeconds(-1);
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();

        BizException ex = assertThrows(BizException.class, () -> fillTokenService.consume(token, 99L));

        assertEquals(FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(redisStore).releaseLock(eq("fill_token_task:30"), any());
    }

    @Test
    void payloadIsNotStoredInRedisMarker() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        String payloadPart = token.split("\\.")[1];
        String canonical = new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);

        assertTrue(canonical.contains("|20|10|99|30|"));
        verify(redisStore).set(any(), eq("1"), any());
    }

    @Test
    void issueRejectsWhenTaskAlreadyHasActiveToken() {
        when(redisStore.tryLock(eq("fill_token_task:30"), any(), any(Duration.class))).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3"));

        assertEquals(com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        verify(redisStore, never()).set(any(), any(), any());
    }

    @Test
    void operatorMismatchIsDeniedBeforeRedisConsume() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();

        BizException ex = assertThrows(BizException.class, () -> fillTokenService.consume(token, 100L));

        assertEquals(FILL_TOKEN_OPERATOR_MISMATCH, ex.getCode());
        assertNotEquals(100L, fillTokenService.verify(token).op());
    }

    @Test
    void reserveFailureReleasesTaskGuardOnlyWhenTokenMarkerMissing() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        FillTokenPayload payload = fillTokenService.verify(token);
        when(redisStore.compareAndSet(any(), eq("1"), any(), any(Duration.class))).thenReturn(false);
        when(redisStore.get(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> fillTokenService.reserveConsume(token, payload));

        assertEquals(FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(redisStore).releaseLock(eq("fill_token_task:30"), eq(payload.n()));
    }

    @Test
    void reserveFailureKeepsTaskGuardWhenTokenIsBeingConsumed() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        FillTokenPayload payload = fillTokenService.verify(token);
        when(redisStore.compareAndSet(any(), eq("1"), any(), any(Duration.class))).thenReturn(false);
        when(redisStore.get(any())).thenReturn("consuming:" + payload.n());

        BizException ex = assertThrows(BizException.class, () -> fillTokenService.reserveConsume(token, payload));

        assertEquals(FILL_TOKEN_USED_OR_EXPIRED, ex.getCode());
        verify(redisStore, never()).releaseLock(eq("fill_token_task:30"), eq(payload.n()));
    }

    @Test
    void restoreKeepsTaskGuardWhenTokenReturnsToValid() {
        String token = fillTokenService.issue(20L, 10L, 99L, 30L, "chrome", "1.2.3").fillToken();
        FillTokenPayload payload = fillTokenService.verify(token);

        fillTokenService.restoreConsume(token, payload);

        verify(redisStore, never()).releaseLock(eq("fill_token_task:30"), eq(payload.n()));
    }
}
