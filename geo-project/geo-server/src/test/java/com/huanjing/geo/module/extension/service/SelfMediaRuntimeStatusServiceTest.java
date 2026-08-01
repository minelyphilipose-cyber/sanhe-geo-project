package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.extension.dto.ExtensionRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.entity.LocalAgentBrowserMetricSample;
import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.mapper.ExtensionRuntimeStatusMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentBrowserMetricSampleMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentRuntimeStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SelfMediaRuntimeStatusServiceTest {

    private ExtensionRuntimeStatusMapper extensionRuntimeStatusMapper;
    private LocalAgentRuntimeStatusMapper localAgentRuntimeStatusMapper;
    private LocalAgentBrowserMetricSampleMapper localAgentBrowserMetricSampleMapper;
    private SelfMediaRuntimeStatusService service;

    @BeforeEach
    void setUp() {
        extensionRuntimeStatusMapper = mock(ExtensionRuntimeStatusMapper.class);
        localAgentRuntimeStatusMapper = mock(LocalAgentRuntimeStatusMapper.class);
        localAgentBrowserMetricSampleMapper = mock(LocalAgentBrowserMetricSampleMapper.class);
        service = new SelfMediaRuntimeStatusService(
                extensionRuntimeStatusMapper,
                localAgentRuntimeStatusMapper,
                localAgentBrowserMetricSampleMapper,
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
                null,
                null,
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
                null,
                null,
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

    @Test
    void localAgentReportPersistsBrowserObservationMetrics() throws Exception {
        LocalAgentSession session = new LocalAgentSession();
        session.setId(7L);
        session.setOperatorId(99L);
        ObjectMapper objectMapper = new ObjectMapper();
        LocalDateTime observedAt = LocalDateTime.of(2026, 7, 30, 9, 30);
        LocalAgentRuntimeStatusReportRequest request = new LocalAgentRuntimeStatusReportRequest(
                "machine-1",
                "prod",
                "0.1.12",
                "1",
                "geo-local-helper",
                true,
                "http://localhost:50325",
                0,
                1,
                objectMapper.readTree("[\"toutiao\"]"),
                objectMapper.readTree("{\"browserLifecycle\":{\"version\":2,\"observation\":true,\"tabCleanup\":false}}"),
                "observing",
                objectMapper.readTree("""
                        {
                          "summary": {"totalObservedTargetCount": 4, "registryRevision": 8},
                          "environments": [{
                            "browserEnvironmentId": 12,
                            "environmentKey": "env-1",
                            "providerProfileId": "profile-1",
                            "browserSessionEpoch": "epoch-1",
                            "observationStatus": "ok",
                            "observedAt": "2026-07-30T01:30:00Z",
                            "lastSuccessfulObservedAt": "2026-07-30T01:30:00Z",
                            "helperUptimeSeconds": 3600,
                            "taskVolume": {
                              "retainedTaskCount": 10,
                              "activeTaskCount": 1,
                              "throughputSinceHelperBoot": {
                                "claimedTotal": 20,
                                "executionClaimedTotal": 12,
                                "executionStartedTotal": 11,
                                "publishCheckClaimedTotal": 8,
                                "publishCheckStartedTotal": 7,
                                "completedTotal": 17,
                                "failedTotal": 3
                              }
                            },
                            "totalTargetCount": 4,
                            "managedTargetCount": 2,
                            "operatorTargetCount": 1,
                            "unknownTargetCount": 1,
                            "processMetrics": {"rssBytes": 2048, "cpuPercent": 12.5, "handleCount": 30},
                            "cdpStepLatencyMs": {
                              "connectMs": 11,
                              "browserGetVersionMs": 12,
                              "browserPagesMs": 13
                            },
                            "errorCounts": {
                              "networkEnableTimeout": 1,
                              "cdpDisconnected": 2,
                              "extensionInjectionError": 3,
                              "pageTimeout": 4,
                              "cdpProtocolTimeout": 5
                            }
                          }, {
                            "browserEnvironmentId": 13,
                            "environmentKey": "env-2",
                            "providerProfileId": "profile-2",
                            "browserSessionEpoch": "epoch-2",
                            "observationStatus": "failed",
                            "observedAt": "2026-07-30T01:31:00Z",
                            "lastSuccessfulObservedAt": "2026-07-30T01:30:00Z",
                            "failedProbeDurationMs": 20000,
                            "totalTargetCount": 99,
                            "processMetrics": {"rssBytes": 9999, "cpuPercent": 99, "handleCount": 99},
                            "cdpStepLatencyMs": {"connectMs": 99}
                          }]
                        }
                        """),
                observedAt,
                "boot-1",
                17L,
                null,
                null
        );

        service.reportLocalAgent(session, request);

        ArgumentCaptor<LocalAgentRuntimeStatus> captor = ArgumentCaptor.forClass(LocalAgentRuntimeStatus.class);
        verify(localAgentRuntimeStatusMapper).insert(captor.capture());
        LocalAgentRuntimeStatus persisted = captor.getValue();
        assertEquals("observing", persisted.getRuntimeState());
        assertEquals("boot-1", persisted.getHelperBootId());
        assertEquals(17L, persisted.getPolicyVersion());
        assertEquals(observedAt, persisted.getLastCleanupAt());
        ArgumentCaptor<LocalAgentBrowserMetricSample> sampleCaptor =
                ArgumentCaptor.forClass(LocalAgentBrowserMetricSample.class);
        verify(localAgentBrowserMetricSampleMapper, times(2)).insertIdempotent(sampleCaptor.capture());
        LocalAgentBrowserMetricSample sample = sampleCaptor.getAllValues().stream()
                .filter(value -> "profile-1".equals(value.getProviderProfileId()))
                .findFirst()
                .orElseThrow();
        assertEquals("profile-1", sample.getProviderProfileId());
        assertEquals("ok", sample.getObservationStatus());
        assertEquals(12L, sample.getBrowserEnvironmentId());
        assertEquals(3600L, sample.getHelperUptimeSeconds());
        assertEquals(2048L, sample.getProcessRssBytes());
        assertEquals(12.5, sample.getProcessCpuPercent());
        assertEquals(11, sample.getCdpConnectMs());
        assertEquals(1, sample.getNetworkEnableTimeoutCount());
        assertEquals(5, sample.getCdpProtocolTimeoutCount());
        assertEquals(20L, sample.getClaimedTotal());
        assertEquals(17L, sample.getCompletedTotal());

        LocalAgentBrowserMetricSample failed = sampleCaptor.getAllValues().stream()
                .filter(value -> "profile-2".equals(value.getProviderProfileId()))
                .findFirst()
                .orElseThrow();
        assertEquals("failed", failed.getObservationStatus());
        assertEquals(20000, failed.getFailedProbeDurationMs());
        assertNull(failed.getTotalTargetCount());
        assertNull(failed.getProcessRssBytes());
        assertNull(failed.getCdpConnectMs());
    }
}
