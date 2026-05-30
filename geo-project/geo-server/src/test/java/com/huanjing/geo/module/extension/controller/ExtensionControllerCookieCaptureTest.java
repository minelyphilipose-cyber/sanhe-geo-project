package com.huanjing.geo.module.extension.controller;

import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureRequest;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.service.ExtensionCookieCaptureService;
import com.huanjing.geo.module.extension.service.ExtensionSessionService;
import com.huanjing.geo.module.extension.service.ExtensionVersionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtensionControllerCookieCaptureTest {

    @Test
    void cookieCaptureRequiresSessionAndPlatformVersion() {
        ExtensionSessionService sessionService = mock(ExtensionSessionService.class);
        ExtensionVersionService versionService = mock(ExtensionVersionService.class);
        ExtensionCookieCaptureService captureService = mock(ExtensionCookieCaptureService.class);
        ExtensionSession session = new ExtensionSession();
        session.setId(77L);
        session.setOperatorId(99L);
        when(sessionService.requireActiveSession("ext.token")).thenReturn(session);
        ExtensionController controller = new ExtensionController(null, sessionService, versionService,
                null, null, captureService, null, null, null, null, null);

        controller.captureCookies("ext.token", request());

        verify(sessionService).requireActiveSession("ext.token");
        verify(versionService).requireSupported("toutiao", "0.1.0");
        verify(captureService).capture(request(), 99L, 77L);
    }

    private ExtensionCookieCaptureRequest request() {
        return new ExtensionCookieCaptureRequest(10L, 20L, "toutiao", "0.1.0", "install-1",
                true, "nonce-1", "[]", null, null, null);
    }
}
