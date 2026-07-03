package com.huanjing.geo.module.extension.service;

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
        RuntimeReadinessQuery query = new RuntimeReadinessQuery(10L, 99L, 20L, "toutiao", "claim", "fill");
        ExtensionRuntimeStatus extension = new ExtensionRuntimeStatus();
        extension.setId(1L);
        extension.setLoginStatus("logged_in");
        extension.setLastSeenAt(LocalDateTime.now());
        LocalAgentRuntimeStatus helper = new LocalAgentRuntimeStatus();
        helper.setId(2L);
        helper.setAdspowerApiOk(true);
        helper.setRunningTaskCount(0);
        helper.setCapacity(1);
        helper.setLastSeenAt(LocalDateTime.now());
        when(extensionMapper.selectLatestByEnvironmentAndPlatform(20L, "toutiao")).thenReturn(List.of(extension));
        when(helperMapper.selectRecentByOperatorId(99L, 1)).thenReturn(List.of(helper));
        SelfMediaRuntimeReadinessService service = new SelfMediaRuntimeReadinessService(extensionMapper, helperMapper, properties);

        RuntimeReadinessResult result = service.evaluate(query);

        assertFalse(result.ready());
        assertTrue(result.blockedReasons().contains(SelfMediaRuntimeReadinessService.ACCOUNT_NOT_VERIFIED));
    }
}
