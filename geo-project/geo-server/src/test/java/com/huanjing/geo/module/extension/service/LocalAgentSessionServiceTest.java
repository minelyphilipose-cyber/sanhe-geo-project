package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.dto.LocalAgentExtensionSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAgentSessionServiceTest {

    private static final String EMPTY_BODY_HASH = "e3b0c44298fc1c149afbf4c8996fb924"
            + "27ae41e4649b934ca495991b7852b855";

    private LocalAgentSessionMapper sessionMapper;
    private CurrentUserService currentUserService;
    private LocalAgentSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(LocalAgentSessionMapper.class);
        currentUserService = mock(CurrentUserService.class);
        service = new LocalAgentSessionService(
                sessionMapper,
                mock(ExtensionRedisStore.class),
                currentUserService,
                new ObjectMapper()
        );
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
}
