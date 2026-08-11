package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.dto.LocalAgentExtensionSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalAgentSessionServiceTest {

    private static final String EMPTY_BODY_HASH = "e3b0c44298fc1c149afbf4c8996fb924"
            + "27ae41e4649b934ca495991b7852b855";

    private LocalAgentSessionMapper sessionMapper;
    private CurrentUserService currentUserService;
    private ExtensionRedisStore redisStore;
    private LocalAgentSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(LocalAgentSessionMapper.class);
        currentUserService = mock(CurrentUserService.class);
        redisStore = mock(ExtensionRedisStore.class);
        service = new LocalAgentSessionService(
                sessionMapper,
                redisStore,
                currentUserService,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void pairingCreatesAccountWideSessionAndReturnsNullBrand() throws Exception {
        String pairingCode = "ABCD-1234";
        String codeHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(pairingCode.getBytes(StandardCharsets.UTF_8)));
        String deviceSecretHash = "a".repeat(64);
        service.registerPairingIntent(new LocalAgentPairingIntentRequest(
                codeHash, deviceSecretHash, "production-helper"));
        ArgumentCaptor<String> intentJson = ArgumentCaptor.forClass(String.class);
        verify(redisStore).set(anyString(), intentJson.capture(), any());
        when(redisStore.getAndDelete(anyString())).thenReturn(intentJson.getValue());
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        doAnswer(invocation -> {
            LocalAgentSession row = invocation.getArgument(0);
            row.setId(5L);
            return 1;
        }).when(sessionMapper).insert(any(LocalAgentSession.class));

        var response = service.approvePairing(new LocalAgentPairingApproveRequest(pairingCode));

        ArgumentCaptor<LocalAgentSession> session = ArgumentCaptor.forClass(LocalAgentSession.class);
        verify(sessionMapper).insert(session.capture());
        assertNull(session.getValue().getBrandId());
        assertNull(response.brandId());
        assertEquals(5L, response.sessionId());
        verify(sessionMapper).revokeActiveByOperatorId(eq(20L), any(), eq(20L));
    }

    @Test
    void signRequestAllowsAdspowerHelperPath() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        when(sessionMapper.selectById(9L)).thenReturn(activeSession(9L, 20L));

        LocalAgentSignResponse response = service.signRequest(9L, new LocalAgentSignRequest(
                "GET",
                "/v1/adspower/profiles?page=1&pageSize=50",
                EMPTY_BODY_HASH
        ));

        Map<String, String> headers = response.headers();
        assertEquals("helper.session.9", headers.get("X-Geo-Helper-Access"));
        assertTrue(headers.containsKey("X-Geo-Helper-Signature"));
    }

    @Test
    void signRequestAllowsEncodedQueryContainingPublishedUrlAndDiagnostics() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        when(sessionMapper.selectById(9L)).thenReturn(activeSession(9L, 20L));

        LocalAgentSignResponse response = service.signRequest(9L, new LocalAgentSignRequest(
                "POST",
                "/api/v1/local-agent/self-media-schedules/209/publish-checks/published"
                        + "?platformPublishedUrl=https%3A%2F%2Fwww.xiaohongshu.com%2Fexplore%2Fabc123"
                        + "%3Fxsec_token%3Dtoken%253D%26xsec_source%3Dpc_creatormng"
                        + "&diagnosticsJson=%7B%22textSample%22%3A%22title...body%22%7D",
                EMPTY_BODY_HASH
        ));

        Map<String, String> headers = response.headers();
        assertEquals("helper.session.9", headers.get("X-Geo-Helper-Access"));
        assertTrue(headers.containsKey("X-Geo-Helper-Signature"));
    }

    @Test
    void signRequestRejectsUnsupportedHelperPath() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        when(sessionMapper.selectById(9L)).thenReturn(activeSession(9L, 20L));

        BizException ex = assertThrows(BizException.class, () -> service.signRequest(9L, new LocalAgentSignRequest(
                "GET",
                "/v1/admin/private",
                EMPTY_BODY_HASH
        )));

        assertEquals(400, ex.getCode());
        assertEquals("unsupported local helper path", ex.getMessage());
    }

    @Test
    void signRequestRejectsInvalidHelperPathTraversal() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        when(sessionMapper.selectById(9L)).thenReturn(activeSession(9L, 20L));

        BizException ex = assertThrows(BizException.class, () -> service.signRequest(9L, new LocalAgentSignRequest(
                "GET",
                "/v1/adspower/../settings",
                EMPTY_BODY_HASH
        )));

        assertEquals(400, ex.getCode());
        assertEquals("invalid local helper path", ex.getMessage());
    }

    @Test
    void extensionSigningTargetsTheHelperSessionRequestedByTheCallingBrowser() {
        when(sessionMapper.selectById(12L)).thenReturn(activeSession(12L, 20L));
        ExtensionSession extensionSession = new ExtensionSession();
        extensionSession.setOperatorId(20L);

        LocalAgentSignResponse response = service.signRequestForExtension(extensionSession, new LocalAgentExtensionSignRequest(
                "GET",
                "/v1/extension/tasks/next?environmentKey=env-1&platform=toutiao",
                EMPTY_BODY_HASH,
                12L
        ));

        assertEquals("helper.session.12", response.headers().get("X-Geo-Helper-Access"));
    }

    @Test
    void extensionSigningRejectsAHelperSessionOwnedByAnotherOperator() {
        when(sessionMapper.selectById(12L)).thenReturn(activeSession(12L, 99L));
        ExtensionSession extensionSession = new ExtensionSession();
        extensionSession.setOperatorId(20L);

        BizException ex = assertThrows(BizException.class, () -> service.signRequestForExtension(
                extensionSession,
                new LocalAgentExtensionSignRequest("GET", "/v1/extension/tasks", EMPTY_BODY_HASH, 12L)
        ));

        assertEquals(403, ex.getCode());
    }

    @Test
    void listSessionsOnlyRequestsUnexpiredActiveRows() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator(20L));
        when(sessionMapper.selectActiveByOperatorId(eq(20L), any(LocalDateTime.class)))
                .thenReturn(List.of(activeSession(9L, 20L)));

        var sessions = service.listActiveSessions();

        assertEquals(1, sessions.size());
        verify(sessionMapper).selectActiveByOperatorId(eq(20L), any(LocalDateTime.class));
    }

    @Test
    void verifiedSignedRequestRenewsSessionInsideThreshold() throws Exception {
        LocalAgentSession session = activeSession(9L, 20L);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessionMapper.selectById(9L)).thenReturn(session);
        when(redisStore.tryLock(anyString(), anyString(), any())).thenReturn(true);
        when(sessionMapper.renewActiveExpiry(eq(9L), any(), any(), any())).thenReturn(1);

        SignedRequest request = signedRequest(session, "/api/v1/local-agent/session/status");
        LocalAgentSession verified = service.verifySignedRequest(
                "GET",
                request.path(),
                EMPTY_BODY_HASH,
                request.helperAccess(),
                request.timestamp(),
                request.nonce(),
                request.signature(),
                "test-agent"
        );

        ArgumentCaptor<LocalDateTime> renewedExpiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionMapper).renewActiveExpiry(eq(9L), any(), any(), renewedExpiry.capture());
        assertTrue(renewedExpiry.getValue().isAfter(LocalDateTime.now().plusDays(29)));
        assertEquals(renewedExpiry.getValue(), verified.getExpiresAt());
    }

    @Test
    void verifiedSignedRequestDoesNotRenewSessionOutsideThreshold() throws Exception {
        LocalAgentSession session = activeSession(9L, 20L);
        session.setExpiresAt(LocalDateTime.now().plusDays(8));
        when(sessionMapper.selectById(9L)).thenReturn(session);
        when(redisStore.tryLock(anyString(), anyString(), any())).thenReturn(true);

        SignedRequest request = signedRequest(session, "/api/v1/local-agent/session/status");
        service.verifySignedRequest(
                "GET",
                request.path(),
                EMPTY_BODY_HASH,
                request.helperAccess(),
                request.timestamp(),
                request.nonce(),
                request.signature(),
                "test-agent"
        );

        verify(sessionMapper, never()).renewActiveExpiry(any(), any(), any(), any());
    }

    @Test
    void expiredSignedSessionIsRejectedBeforeReplayLockOrRenewal() {
        LocalAgentSession session = activeSession(9L, 20L);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(sessionMapper.selectById(9L)).thenReturn(session);

        BizException ex = assertThrows(BizException.class, () -> service.verifySignedRequest(
                "GET",
                "/api/v1/local-agent/session/status",
                EMPTY_BODY_HASH,
                "helper.session.9",
                String.valueOf(System.currentTimeMillis() / 1_000),
                "expired-session-nonce",
                "unused-signature",
                "test-agent"
        ));

        assertEquals(401, ex.getCode());
        assertEquals("local agent session expired", ex.getMessage());
        verify(redisStore, never()).tryLock(anyString(), anyString(), any());
        verify(sessionMapper, never()).touchActive(any(), any(), any());
        verify(sessionMapper, never()).renewActiveExpiry(any(), any(), any(), any());
    }

    private SysUser operator(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private LocalAgentSession activeSession(Long id, Long operatorId) {
        LocalAgentSession session = new LocalAgentSession();
        session.setId(id);
        session.setOperatorId(operatorId);
        session.setStatus("active");
        session.setHmacSecret("test-secret");
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        return session;
    }

    private SignedRequest signedRequest(LocalAgentSession session, String path) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1_000);
        String nonce = "nonce-" + System.nanoTime();
        String helperAccess = "helper.session." + session.getId();
        String canonical = "GET\n" + path + "\n" + EMPTY_BODY_HASH + "\n"
                + timestamp + "\n" + nonce + "\n" + helperAccess;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(session.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return new SignedRequest(path, helperAccess, timestamp, nonce, signature);
    }

    private record SignedRequest(
            String path,
            String helperAccess,
            String timestamp,
            String nonce,
            String signature
    ) {
    }
}
