package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionBindResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.mapper.ExtensionSessionMapper;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_UNAUTHORIZED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionSessionServiceTest {

    private ExtensionSessionMapper sessionMapper;
    private ExtensionVersionService versionService;
    private ExtensionAuditSupport auditSupport;
    private SysUserMapper sysUserMapper;
    private BrandAccessService brandAccessService;
    private CurrentUserService currentUserService;
    private ExtensionSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ExtensionSessionMapper.class);
        versionService = mock(ExtensionVersionService.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        sysUserMapper = mock(SysUserMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        currentUserService = mock(CurrentUserService.class);
        service = new ExtensionSessionService(
                sessionMapper,
                new ExtensionProperties(),
                versionService,
                auditSupport,
                sysUserMapper,
                brandAccessService,
                currentUserService
        );
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-08T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createBoundSessionStoresOnlyHashes() {
        ExtensionBindResponse response = service.createBoundSession(10L, 99L, "install-1", "geo_b", "profile-1", "fp", "1.2.3", "ua");

        ArgumentCaptor<ExtensionSession> captor = ArgumentCaptor.forClass(ExtensionSession.class);
        verify(sessionMapper).insert(captor.capture());
        ExtensionSession inserted = captor.getValue();
        assertTrue(response.token().startsWith("ext."));
        assertEquals(HashSupport.sha256Hex(response.token()), inserted.getTokenLookupHash());
        assertNotEquals(response.token(), inserted.getTokenHash());
        assertFalse(inserted.getTokenHash().contains(response.token()));
        assertEquals("SHA-256", inserted.getTokenHashAlg());
        assertEquals("geo_b", inserted.getEnvironmentKey());
        assertEquals("profile-1", inserted.getProviderProfileId());
        assertEquals(inserted.getBoundAt().plusDays(30), response.expiresAt());
    }

    @Test
    void createBoundSessionAuditsAsyncSoDbLockDoesNotBlockBindResponse() {
        ExtensionBindResponse response = service.createBoundSession(10L, 99L, "install-1", "geo_b", "profile-1", "fp", "1.2.3", "ua");

        verify(auditSupport).record(
                eq("EXTENSION_BIND"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.ASYNC),
                eq(true),
                eq(99L),
                eq(10L),
                eq(null),
                eq(null),
                eq(response.sessionId()),
                eq("EXTENSION_SESSION"),
                eq(String.valueOf(response.sessionId())),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void samePlaintextHasSameLookupButDifferentSaltedHash() {
        String plaintext = "ext.same-token";
        String salt1 = "00112233445566778899aabbccddeeff";
        String salt2 = "ffeeddccbbaa99887766554433221100";

        assertEquals(HashSupport.sha256Hex(plaintext), HashSupport.sha256Hex(plaintext));
        assertNotEquals(
                HashSupport.saltedSha256Hex(salt1, plaintext),
                HashSupport.saltedSha256Hex(salt2, plaintext)
        );
    }

    @Test
    void existingSevenDaySessionRenewsOnUseWithoutWaitingForExpiryWindow() {
        ExtensionBindResponse response = service.createBoundSession(10L, 99L, "install-1", "geo_b", "profile-1", null, "1.2.3", "ua");
        ExtensionSession session = sessionFromInsert(response.token());
        session.setId(1L);
        session.setExpiresAt(LocalDateTime.of(2026, 10, 14, 4, 0));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);
        when(sessionMapper.renewActiveExpiry(eq(1L), any(), any(), any())).thenReturn(1);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew(response.token(), "1.2.4", "ua2");

        assertTrue(refresh.renewed());
        assertEquals(LocalDateTime.of(2026, 11, 7, 4, 0), refresh.expiresAt());
        assertNull(refresh.token());
        verify(sessionMapper, never()).revokeActive(any(), any(), any());
    }

    @Test
    void slidingRenewExtendsTheExistingSessionWithoutRotatingToken() {
        ExtensionBindResponse response = service.createBoundSession(10L, 99L, "install-1", "geo_b", "profile-1", null, "1.2.3", "ua");
        ExtensionSession session = sessionFromInsert(response.token());
        session.setId(1L);
        session.setExpiresAt(LocalDateTime.of(2026, 10, 8, 16, 0));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);
        when(sessionMapper.renewActiveExpiry(eq(1L), any(), any(), any())).thenReturn(1);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew(response.token(), "1.2.4", "ua2");

        assertTrue(refresh.renewed());
        assertNull(refresh.token());
        assertEquals(1L, refresh.sessionId());
        assertEquals(LocalDateTime.of(2026, 11, 7, 4, 0), refresh.expiresAt());
        verify(sessionMapper).renewActiveExpiry(eq(1L), any(), any(), eq(refresh.expiresAt()));
        verify(sessionMapper, never()).revokeActive(any(), any(), any());
    }

    @Test
    void sameUtcDayDoesNotRenewAgainEvenAtMidnightBoundary() {
        ExtensionSession session = session();
        session.setExpiresAt(LocalDateTime.of(2026, 11, 7, 0, 0));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew("ext.token", "1.2.4", "ua");

        assertFalse(refresh.renewed());
        assertEquals(session.getExpiresAt(), refresh.expiresAt());
        verify(sessionMapper, never()).renewActiveExpiry(any(), any(), any(), any());
    }

    @Test
    void nextUtcDayRenewsEvenWithTwentyNineDaysRemaining() {
        ExtensionSession session = session();
        session.setId(1L);
        session.setExpiresAt(LocalDateTime.of(2026, 11, 7, 4, 0));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-09T04:00:00Z"), ZoneOffset.UTC));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);
        when(sessionMapper.renewActiveExpiry(eq(1L), any(), any(), any())).thenReturn(1);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew("ext.token", "1.2.4", "ua");

        assertTrue(refresh.renewed());
        assertEquals(LocalDateTime.of(2026, 11, 8, 4, 0), refresh.expiresAt());
    }

    @Test
    void expiryResponseUsesDatabaseSecondPrecision() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-08T04:00:00.876625Z"), ZoneOffset.UTC));
        ExtensionSession session = session();
        session.setId(1L);
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);
        when(sessionMapper.renewActiveExpiry(eq(1L), any(), any(), any())).thenReturn(1);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew("ext.token", "1.2.4", "ua");

        assertEquals(LocalDateTime.of(2026, 11, 7, 4, 0), refresh.expiresAt());
    }

    @Test
    void concurrentRenewalReturnsLatestExpiryWithoutRotatingToken() {
        ExtensionSession session = session();
        session.setId(1L);
        session.setExpiresAt(LocalDateTime.of(2026, 10, 14, 4, 0));
        ExtensionSession latest = session();
        latest.setExpiresAt(LocalDateTime.of(2026, 11, 7, 4, 0));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);
        when(sessionMapper.selectById(1L)).thenReturn(latest);

        ExtensionTokenRefreshResponse refresh = service.validateAndMaybeRenew("ext.token", "1.2.4", "ua");

        assertFalse(refresh.renewed());
        assertNull(refresh.token());
        assertEquals(latest.getExpiresAt(), refresh.expiresAt());
    }

    @Test
    void expiredAuthorizationCannotBeRenewed() {
        ExtensionSession session = session();
        session.setExpiresAt(LocalDateTime.of(2026, 10, 8, 4, 0));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);

        BizException error = assertThrows(BizException.class, () -> service.validateAndMaybeRenew("ext.token", "1.2.4", "ua"));

        assertEquals(EXTENSION_UNAUTHORIZED, error.getCode());
        verify(sessionMapper, never()).renewActiveExpiry(any(), any(), any(), any());
    }

    @Test
    void expiredSessionIsRejectedByEveryAuthenticatedEndpoint() {
        ExtensionSession session = session();
        session.setExpiresAt(LocalDateTime.of(2026, 10, 8, 3, 59, 59));
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);

        BizException ex = assertThrows(BizException.class, () -> service.requireActiveSession("ext.token"));

        assertEquals(EXTENSION_UNAUTHORIZED, ex.getCode());
        assertEquals("extension token expired", ex.getMessage());
    }

    @Test
    void invalidStoredHashAlgorithmFailsDefensively() {
        ExtensionSession session = session();
        session.setTokenHashAlg("UNKNOWN");
        when(sessionMapper.selectActiveByLookupHash(session.getTokenLookupHash())).thenReturn(session);

        assertThrows(IllegalStateException.class, () -> service.requireActiveSession("ext.token"));
    }

    @Test
    void missingSessionIsUnauthorized() {
        BizException ex = assertThrows(BizException.class, () -> service.requireActiveSession("ext.missing"));
        assertEquals(EXTENSION_UNAUTHORIZED, ex.getCode());
    }

    @Test
    void revokeByOtherOperatorWithoutGlobalRoleIsDeniedAndAudited() {
        ExtensionSession session = session();
        session.setId(1L);
        session.setOperatorId(10L);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        SysUser requester = new SysUser();
        requester.setId(20L);
        requester.setRole("operator");
        requester.setIsActive(true);
        when(sysUserMapper.selectById(20L)).thenReturn(requester);

        BizException ex = assertThrows(BizException.class, () -> service.revoke(1L, 20L));

        assertEquals(EXTENSION_DENIED, ex.getCode());
        verify(auditSupport).record(
                eq("EXTENSION_TOKEN_REVOKE"),
                eq(AuditResult.DENIED),
                eq(AuditMode.SYNC),
                eq(true),
                eq(20L),
                eq(null),
                eq(null),
                eq(null),
                eq(1L),
                eq("EXTENSION_SESSION"),
                eq("1"),
                eq(String.valueOf(EXTENSION_DENIED)),
                eq("REVOKE_PERMISSION_DENIED"),
                any()
        );
    }

    @Test
    void revokeByGlobalRoleManagerSucceeds() {
        ExtensionSession session = session();
        session.setId(1L);
        session.setOperatorId(10L);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        SysUser requester = new SysUser();
        requester.setId(20L);
        requester.setRole("manager");
        requester.setIsActive(true);
        when(sysUserMapper.selectById(20L)).thenReturn(requester);

        service.revoke(1L, 20L);

        verify(sessionMapper).revokeActive(eq(1L), any(), eq(20L));
        verify(auditSupport).record(
                eq("EXTENSION_TOKEN_REVOKE"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(true),
                eq(20L),
                eq(null),
                eq(null),
                eq(null),
                eq(1L),
                eq("EXTENSION_SESSION"),
                eq("1"),
                eq(null),
                eq(null),
                any()
        );
    }

    private ExtensionSession sessionFromInsert(String token) {
        ArgumentCaptor<ExtensionSession> captor = ArgumentCaptor.forClass(ExtensionSession.class);
        verify(sessionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private ExtensionSession session() {
        ExtensionSession session = new ExtensionSession();
        session.setTokenLookupHash(HashSupport.sha256Hex("ext.token"));
        session.setTokenSalt("00112233445566778899aabbccddeeff");
        session.setTokenHash(HashSupport.saltedSha256Hex(session.getTokenSalt(), "ext.token"));
        session.setTokenHashAlg("SHA-256");
        session.setStatus("active");
        session.setExpiresAt(LocalDateTime.of(2026, 10, 9, 4, 0));
        return session;
    }
}
