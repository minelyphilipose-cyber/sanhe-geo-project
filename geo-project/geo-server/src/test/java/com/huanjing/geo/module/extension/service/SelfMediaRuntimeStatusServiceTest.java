package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SelfMediaRuntimeStatusServiceTest {

    private ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private SelfMediaRuntimeStatusService service;

    @BeforeEach
    void setUp() {
        extensionRuntimeStatusMapper = mock(ExtensionRuntimeStatusMapper.class);
        localAgentRuntimeStatusMapper = mock(LocalAgentRuntimeStatusMapper.class);
        service = new SelfMediaRuntimeStatusService(
                extensionRuntimeStatusMapper,
                localAgentRuntimeStatusMapper,
                mock(BrowserEnvironmentMapper.class),
                mock(BrowserEnvironmentAccountMapper.class),
                new ObjectMapper()
        );
    }

    @Test
    void extensionReportRequiresProviderProfileIdBeforeInsert() {
        ExtensionSession session = new ExtensionSession();
        session.setExtensionVersion("1.0.0");
        ExtensionRuntimeStatusReportRequest request = new ExtensionRuntimeStatusReportRequest(
                "install-1",
                "env-1",
                null,
                "toutiao",
                "1.0.0",
                "1",
                null,
                null,
                null,
                null,
                "verified",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        BizException ex = assertThrows(BizException.class, () -> service.reportExtension(session, request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("PROVIDER_PROFILE_ID_REQUIRED", ((Map<?, ?>) ex.getData()).get("code"));
        verify(extensionRuntimeStatusMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void extensionReportRequiresInstallIdBeforeInsert() {
        ExtensionSession session = new ExtensionSession();
        session.setExtensionVersion("1.0.0");
        ExtensionRuntimeStatusReportRequest request = new ExtensionRuntimeStatusReportRequest(
                null,
                "env-1",
                "profile-1",
                "toutiao",
                "1.0.0",
                "1",
                null,
                null,
                null,
                null,
                "verified",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        BizException ex = assertThrows(BizException.class, () -> service.reportExtension(session, request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("INSTALL_ID_REQUIRED", ((Map<?, ?>) ex.getData()).get("code"));
        verify(extensionRuntimeStatusMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void extensionReportRejectsAClonedSessionBoundToAnotherProviderProfile() {
        ExtensionSession session = new ExtensionSession();
        session.setExtensionVersion("0.1.9");
        session.setProviderProfileId("adspower-original");
        session.setEnvironmentKey("env-original");
        ExtensionRuntimeStatusReportRequest request = new ExtensionRuntimeStatusReportRequest(
                "install-cloned",
                "env-cloned",
                "adspower-cloned",
                "toutiao",
                "0.1.9",
                "1",
                null,
                null,
                null,
                null,
                "unknown",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        BizException ex = assertThrows(BizException.class, () -> service.reportExtension(session, request));

        assertEquals(409, ex.getHttpStatus());
        assertEquals("EXTENSION_SESSION_PROVIDER_MISMATCH", ((Map<?, ?>) ex.getData()).get("code"));
        verify(extensionRuntimeStatusMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void localAgentReportRequiresMachineIdAndActiveProfileBeforeInsert() {
        LocalAgentSession session = new LocalAgentSession();
        session.setId(1L);
        session.setOperatorId(99L);
        LocalAgentRuntimeStatusReportRequest missingMachineId = new LocalAgentRuntimeStatusReportRequest(
                null,
                "default",
                "1.0.0",
                "1",
                "helper",
                true,
                "http://local.adspower",
                0,
                1,
                null,
                null,
                null,
                null
        );
        LocalAgentRuntimeStatusReportRequest missingActiveProfile = new LocalAgentRuntimeStatusReportRequest(
                "machine-1",
                null,
                "1.0.0",
                "1",
                "helper",
                true,
                "http://local.adspower",
                0,
                1,
                null,
                null,
                null,
                null
        );

        BizException machineEx = assertThrows(BizException.class, () -> service.reportLocalAgent(session, missingMachineId));
        BizException profileEx = assertThrows(BizException.class, () -> service.reportLocalAgent(session, missingActiveProfile));

        assertEquals("MACHINE_ID_REQUIRED", ((Map<?, ?>) machineEx.getData()).get("code"));
        assertEquals("ACTIVE_PROFILE_REQUIRED", ((Map<?, ?>) profileEx.getData()).get("code"));
        verify(localAgentRuntimeStatusMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
