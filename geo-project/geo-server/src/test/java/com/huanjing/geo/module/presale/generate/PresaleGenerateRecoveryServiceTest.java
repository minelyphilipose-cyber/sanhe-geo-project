package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleGenerateRecoveryServiceTest {
    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private PresaleGenerateOrchestrator orchestrator;

    private PresaleGenerateRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new PresaleGenerateRecoveryService(versionMapper, reportMapper, orchestrator);
        ReflectionTestUtils.setField(recoveryService, "enabled", true);
        ReflectionTestUtils.setField(recoveryService, "runningTimeoutMs", 1L);
        ReflectionTestUtils.setField(recoveryService, "batchSize", 50);
        ReflectionTestUtils.setField(recoveryService, "maxConcurrentReports", 1);
        ReflectionTestUtils.setField(recoveryService, "queueDispatchBatchSize", 10);
    }

    @Test
    void shouldNotDispatchQueuedVersionWhenCapacityIsFull() {
        PresaleReportVersion queued = version(10L, 20L, PresaleGenerateStatus.QUEUED.name());
        queued.setCreatedBy(30L);
        when(versionMapper.countRunningGenerations()).thenReturn(1);
        when(versionMapper.selectStaleRunning(any(), anyInt())).thenReturn(List.of());

        int recovered = recoveryService.recoverOnce();

        assertEquals(0, recovered);
        verify(orchestrator, never()).triggerGenerate(10L, 30L, false);
        verify(versionMapper, never()).selectQueuedForDispatch(anyInt());
        verify(versionMapper, never()).markStaleRunningFailed(any(), any());
    }

    @Test
    void shouldFailStaleRunningVersionAndSyncLatestReport() {
        PresaleReportVersion running = version(11L, 21L, PresaleGenerateStatus.RUNNING.name());
        running.setGenerationStage("BATCH1");
        running.setGenerationAttempt(3L);
        when(versionMapper.countRunningGenerations()).thenReturn(1);
        when(versionMapper.selectStaleRunning(any(), anyInt())).thenReturn(List.of(running));
        when(versionMapper.markStaleRunningAttemptFailed(eq(11L), eq(3L), any())).thenReturn(1);

        int recovered = recoveryService.recoverOnce();

        assertEquals(1, recovered);
        verify(orchestrator, never()).triggerGenerate(any(), any(), eq(false));
        verify(versionMapper).markStaleRunningAttemptFailed(
                eq(11L), eq(3L), eq("Generation worker heartbeat timed out"));

        ArgumentCaptor<PresaleReport> captor = ArgumentCaptor.forClass(PresaleReport.class);
        verify(reportMapper).update(captor.capture(), any());
        assertEquals(PresaleGenerateStatus.FAILED.name(), captor.getValue().getStatus());
    }

    @Test
    void shouldDispatchQueuedVersionWhenCapacityAvailable() {
        PresaleReportVersion queued = version(12L, 22L, PresaleGenerateStatus.QUEUED.name());
        queued.setCreatedBy(32L);
        when(versionMapper.selectStaleRunning(any(), anyInt())).thenReturn(List.of());
        when(versionMapper.countRunningGenerations()).thenReturn(0);
        when(versionMapper.selectQueuedForDispatch(anyInt())).thenReturn(List.of(queued));

        int changed = recoveryService.recoverOnce();

        assertEquals(1, changed);
        verify(orchestrator).triggerGenerate(12L, 32L, false);
    }

    private PresaleReportVersion version(Long versionId, Long reportId, String status) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setReportId(reportId);
        version.setGenerationStatus(status);
        version.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        return version;
    }
}
