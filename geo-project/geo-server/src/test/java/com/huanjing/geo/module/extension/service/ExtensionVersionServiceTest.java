package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.extension.config.ExtensionProperties;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckResponse;
import com.huanjing.geo.module.extension.entity.ExtensionVersionConfig;
import com.huanjing.geo.module.extension.mapper.ExtensionVersionConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.EXTENSION_VERSION_TOO_LOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtensionVersionServiceTest {

    private ExtensionVersionConfigMapper mapper;
    private ExtensionVersionRejectAuditService rejectAuditService;
    private ExtensionVersionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExtensionVersionConfigMapper.class);
        rejectAuditService = new ExtensionVersionRejectAuditService();
        service = new ExtensionVersionService(mapper, new ExtensionProperties(), rejectAuditService);
    }

    @Test
    void recommendedVersionReturnsWarningWithoutRejecting() {
        when(mapper.selectActiveByPlatform("chrome")).thenReturn(config("1.0.0", "1.5.0", "2.0.0"));

        ExtensionVersionCheckResponse response = service.check("chrome", "1.2.0");

        assertTrue(response.supported());
        assertFalse(response.upgradeRequired());
        assertTrue(response.upgradeRecommended());
        assertNotNull(response.warning());
    }

    @Test
    void versionBelowMinimumRejects() {
        when(mapper.selectActiveByPlatform("chrome")).thenReturn(config("1.5.0", "2.0.0", "2.0.0"));

        BizException ex = assertThrows(BizException.class, () -> service.checkOrThrow("chrome", "1.2.0"));

        assertEquals(EXTENSION_VERSION_TOO_LOW, ex.getCode());
    }

    private ExtensionVersionConfig config(String min, String recommended, String latest) {
        ExtensionVersionConfig config = new ExtensionVersionConfig();
        config.setPlatform("chrome");
        config.setMinVersion(min);
        config.setRecommendedVersion(recommended);
        config.setLatestVersion(latest);
        config.setDownloadUrl("https://example.test/ext.zip");
        return config;
    }
}
