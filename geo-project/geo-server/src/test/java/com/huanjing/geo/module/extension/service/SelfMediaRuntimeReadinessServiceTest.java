package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessQuery;
import com.huanjing.geo.module.extension.dto.RuntimeReadinessResult;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfMediaRuntimeReadinessServiceTest {

    @Test
    void loggedInWithoutVerifiedStillBlocksFillReadiness() {
        ExtensionRuntimeStatusMapper extensionMapper = mock(ExtensionRuntimeStatusMapper.class);
        LocalAgentRuntimeStatusMapper helperMapper = mock(LocalAgentRuntimeStatusMapper.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, null, 20L, "toutiao", "claim", "fill");
        ExtensionRuntimeStatus extension = new ExtensionRuntimeStatus();
        extension.setId(1L);
        extension.setLoginStatus("logged_in");
        extension.setExtensionVersion("0.1.7");
        extension.setCapabilitiesJson("{\"fill\":true}");
        extension.setLastSeenAt(LocalDateTime.now());
        LocalAgentRuntimeStatus helper = new LocalAgentRuntimeStatus();
        helper.setId(2L);
        helper.setAdspowerApiOk(true);
        helper.setRunningTaskCount(0);
        helper.setCapacity(1);
        helper.setHelperVersion("0.1.7");
        helper.setCapabilitiesJson("{\"claim\":true}");
        helper.setLastSeenAt(LocalDateTime.now());
        when(extensionMapper.selectLatestByEnvironmentAndPlatform(20L, "toutiao")).thenReturn(List.of(extension));
        when(helperMapper.selectRecentByOperatorId(99L, 1)).thenReturn(List.of(helper));
        SelfMediaRuntimeReadinessService service = new SelfMediaRuntimeReadinessService(extensionMapper, helperMapper, properties, new ObjectMapper());

        RuntimeReadinessResult result = service.evaluate(query);

        assertFalse(result.ready());
        assertTrue(result.blockedReasons().contains(SelfMediaRuntimeReadinessService.ACCOUNT_NOT_VERIFIED));
    }

    @Test
    void helperReadinessPrefersCurrentSessionAndChecksCapabilities() {
        ExtensionRuntimeStatusMapper extensionMapper = mock(ExtensionRuntimeStatusMapper.class);
        LocalAgentRuntimeStatusMapper helperMapper = mock(LocalAgentRuntimeStatusMapper.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        properties.getGate().setMinHelperVersion("0.1.7");
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, 55L, 20L, "toutiao", "claim", "fill");
        ExtensionRuntimeStatus extension = new ExtensionRuntimeStatus();
        extension.setId(1L);
        extension.setLoginStatus("verified");
        extension.setExtensionVersion("0.1.7");
        extension.setCapabilitiesJson("{\"fill\":true}");
        extension.setLastSeenAt(LocalDateTime.now());
        LocalAgentRuntimeStatus helper = new LocalAgentRuntimeStatus();
        helper.setId(2L);
        helper.setAdspowerApiOk(true);
        helper.setRunningTaskCount(0);
        helper.setCapacity(1);
        helper.setHelperVersion("0.1.6");
        helper.setCapabilitiesJson("{\"claim\":false}");
        helper.setLastSeenAt(LocalDateTime.now());
        when(extensionMapper.selectLatestByEnvironmentAndPlatform(20L, "toutiao")).thenReturn(List.of(extension));
        when(helperMapper.selectLatestBySessionId(55L)).thenReturn(helper);
        SelfMediaRuntimeReadinessService service = new SelfMediaRuntimeReadinessService(extensionMapper, helperMapper, properties, new ObjectMapper());

        RuntimeReadinessResult result = service.evaluate(query);

        assertFalse(result.ready());
        assertTrue(result.blockedReasons().contains(SelfMediaRuntimeReadinessService.HELPER_VERSION_TOO_LOW));
        assertTrue(result.blockedReasons().contains(SelfMediaRuntimeReadinessService.HELPER_CAPABILITY_UNSUPPORTED));
    }
}
