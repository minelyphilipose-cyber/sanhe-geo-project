package com.huanjing.geo.module.content.douyin;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinOpenPlatformProperties;
import com.huanjing.geo.module.content.vo.DouyinCapabilityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinAuthorizationServiceTest {
    private DouyinOpenPlatformProperties properties;
    private DouyinAuthorizationService service;

    @BeforeEach
    void setUp() {
        properties = new DouyinOpenPlatformProperties();
        properties.setFrontendCallbackUrl("http://localhost:5173/admin/content/execution");
        service = new DouyinAuthorizationService(properties);
    }

    @Test
    void capabilityReportsFingerprintBrowserAsOnlyActiveChannel() {
        DouyinCapabilityVO capability = service.capability();

        assertTrue(capability.isEnabled());
        assertEquals("fingerprint_browser", capability.getMode());
        assertTrue(capability.getDescription().contains("OAuth 已停用"));
        assertTrue(capability.getDescription().contains("指纹浏览器"));
        assertTrue(capability.getReadinessChecks().stream()
                .anyMatch(check -> "oauth_retired".equals(check.getCode()) && "ok".equals(check.getStatus())));
    }

    @Test
    void buildAuthUrlReturnsGoneAndDoesNotCreateOAuthState() {
        BizException exception = assertThrows(BizException.class, () -> service.buildAuthUrl(10L, 20L));

        assertEquals(410, exception.getCode());
        assertEquals(410, exception.getHttpStatus());
        assertEquals(DouyinAuthorizationService.RETIRED_MESSAGE, exception.getMessage());
    }

    @Test
    void handleCallbackReturnsGoneAndCannotPersistOAuthAccount() {
        BizException exception = assertThrows(BizException.class, () -> service.handleCallback("code", "state"));

        assertEquals(410, exception.getCode());
        assertEquals(410, exception.getHttpStatus());
    }

    @Test
    void legacyErrorRedirectAlwaysReportsOAuthRetirement() {
        String redirect = service.errorRedirect("access_denied", "denied");

        assertTrue(redirect.contains("platform=douyin"));
        assertTrue(redirect.contains("douyinAuth=oauth_retired"));
        assertTrue(redirect.contains("errorCode=oauth_retired"));
    }
}
