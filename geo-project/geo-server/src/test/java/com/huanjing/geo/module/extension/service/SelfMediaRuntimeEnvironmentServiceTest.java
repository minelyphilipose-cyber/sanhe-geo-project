package com.huanjing.geo.module.extension.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.extension.config.SelfMediaRuntimeProperties;
import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentBaseRow;
import com.huanjing.geo.module.extension.dto.SelfMediaRuntimeEnvironmentVO;
import com.huanjing.geo.module.extension.entity.ExtensionRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.SelfMediaRuntimeEnvironmentMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SelfMediaRuntimeEnvironmentServiceTest {

    @Test
    void pagesAndFiltersRuntimeEnvironmentReadinessFromBatchedSnapshots() {
        SelfMediaRuntimeEnvironmentMapper environmentMapper = mock(SelfMediaRuntimeEnvironmentMapper.class);
        ExtensionRuntimeStatusMapper extensionMapper = mock(ExtensionRuntimeStatusMapper.class);
        LocalAgentRuntimeStatusMapper helperMapper = mock(LocalAgentRuntimeStatusMapper.class);
        ExtensionRuntimeStatusMapper readinessExtensionMapper = mock(ExtensionRuntimeStatusMapper.class);
        LocalAgentRuntimeStatusMapper readinessHelperMapper = mock(LocalAgentRuntimeStatusMapper.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        SelfMediaRuntimeReadinessService readinessService =
                new SelfMediaRuntimeReadinessService(readinessExtensionMapper, readinessHelperMapper, properties, new ObjectMapper());
        SelfMediaRuntimeEnvironmentService service =
                new SelfMediaRuntimeEnvironmentService(environmentMapper, extensionMapper, helperMapper, readinessService, properties);

        SelfMediaRuntimeEnvironmentBaseRow readyRow = baseRow(10L, 20L, "toutiao");
        SelfMediaRuntimeEnvironmentBaseRow blockedRow = baseRow(11L, 21L, "zhihu");
        when(environmentMapper.selectRuntimeEnvironmentRows(null, null, null)).thenReturn(List.of(readyRow, blockedRow));
        when(extensionMapper.selectRecentByEnvironmentIds(List.of(20L, 21L), 6)).thenReturn(List.of(verifiedExtension(20L, "toutiao")));
        when(helperMapper.selectLatestByBrandIds(List.of(10L, 11L))).thenReturn(List.of(readyHelper(10L, 100L)));

        Page<SelfMediaRuntimeEnvironmentVO> readyPage = service.pageRuntimeEnvironments(null, null, true, null, null, 1, 20);
        Page<SelfMediaRuntimeEnvironmentVO> blockedPage =
                service.pageRuntimeEnvironments(null, null, null, SelfMediaRuntimeReadinessService.EXTENSION_NOT_SEEN, null, 1, 20);

        assertEquals(1, readyPage.getTotal());
        assertTrue(readyPage.getRecords().get(0).readiness().ready());
        assertEquals("brand_latest_helper", readyPage.getRecords().get(0).readiness().scope());
        assertEquals(100L, readyPage.getRecords().get(0).helper().sessionId());
        assertEquals(1, blockedPage.getTotal());
        assertFalse(blockedPage.getRecords().get(0).readiness().ready());
        assertTrue(blockedPage.getRecords().get(0).readiness().blockedReasons()
                .contains(SelfMediaRuntimeReadinessService.EXTENSION_NOT_SEEN));
        verify(extensionMapper, never()).selectLatestByEnvironmentAndPlatform(20L, "toutiao");
        verify(helperMapper, never()).selectLatestByBrandId(10L);
        verifyNoInteractions(readinessExtensionMapper, readinessHelperMapper);
    }

    @Test
    void usesLatestBrandHelperAsOverviewScopeWhenMultipleHelpersExist() {
        SelfMediaRuntimeEnvironmentMapper environmentMapper = mock(SelfMediaRuntimeEnvironmentMapper.class);
        ExtensionRuntimeStatusMapper extensionMapper = mock(ExtensionRuntimeStatusMapper.class);
        LocalAgentRuntimeStatusMapper helperMapper = mock(LocalAgentRuntimeStatusMapper.class);
        SelfMediaRuntimeProperties properties = new SelfMediaRuntimeProperties();
        SelfMediaRuntimeReadinessService readinessService =
                new SelfMediaRuntimeReadinessService(mock(ExtensionRuntimeStatusMapper.class),
                        mock(LocalAgentRuntimeStatusMapper.class),
                        properties,
                        new ObjectMapper());
        SelfMediaRuntimeEnvironmentService service =
                new SelfMediaRuntimeEnvironmentService(environmentMapper, extensionMapper, helperMapper, readinessService, properties);

        when(environmentMapper.selectRuntimeEnvironmentRows(10L, "toutiao", null))
                .thenReturn(List.of(baseRow(10L, 20L, "toutiao")));
        when(extensionMapper.selectRecentByEnvironmentIds(anyList(), eq(6))).thenReturn(List.of(verifiedExtension(20L, "toutiao")));
        LocalAgentRuntimeStatus latestHelper = readyHelper(10L, 201L);
        LocalAgentRuntimeStatus olderHelper = readyHelper(10L, 202L);
        olderHelper.setAdspowerApiOk(false);
        when(helperMapper.selectLatestByBrandIds(List.of(10L))).thenReturn(List.of(latestHelper, olderHelper));

        Page<SelfMediaRuntimeEnvironmentVO> page =
                service.pageRuntimeEnvironments(10L, "toutiao", null, null, null, 1, 20);

        assertEquals(1, page.getTotal());
        SelfMediaRuntimeEnvironmentVO row = page.getRecords().get(0);
        assertEquals(201L, row.helper().sessionId());
        assertEquals("brand_latest_helper", row.readiness().scope());
        assertTrue(row.readiness().ready());
    }

    private SelfMediaRuntimeEnvironmentBaseRow baseRow(Long brandId, Long environmentId, String platform) {
        SelfMediaRuntimeEnvironmentBaseRow row = new SelfMediaRuntimeEnvironmentBaseRow();
        row.setBrandId(brandId);
        row.setBrandName("品牌" + brandId);
        row.setPlatform(platform);
        row.setBrowserEnvironmentId(environmentId);
        row.setEnvironmentName("环境" + environmentId);
        row.setProviderProfileId("profile-" + environmentId);
        row.setSelfMediaAccountId(environmentId + 1000);
        row.setAccountName("账号" + environmentId);
        row.setLoginStatus("verified");
        return row;
    }

    private ExtensionRuntimeStatus verifiedExtension(Long environmentId, String platform) {
        ExtensionRuntimeStatus status = new ExtensionRuntimeStatus();
        status.setId(environmentId + 100);
        status.setBrowserEnvironmentId(environmentId);
        status.setPlatform(platform);
        status.setDetectedPlatform(platform);
        status.setLoginStatus("verified");
        status.setExtensionVersion("0.1.7");
        status.setCapabilitiesJson("{\"fill\":true}");
        status.setLastSeenAt(LocalDateTime.now());
        return status;
    }

    private LocalAgentRuntimeStatus readyHelper(Long brandId, Long sessionId) {
        LocalAgentRuntimeStatus status = new LocalAgentRuntimeStatus();
        status.setId(sessionId + 100);
        status.setBrandId(brandId);
        status.setSessionId(sessionId);
        status.setOperatorId(900L + brandId);
        status.setAdspowerApiOk(true);
        status.setRunningTaskCount(0);
        status.setCapacity(2);
        status.setHelperVersion("0.1.7");
        status.setCapabilitiesJson("{\"claim\":true}");
        status.setLastSeenAt(LocalDateTime.now());
        return status;
    }
}
