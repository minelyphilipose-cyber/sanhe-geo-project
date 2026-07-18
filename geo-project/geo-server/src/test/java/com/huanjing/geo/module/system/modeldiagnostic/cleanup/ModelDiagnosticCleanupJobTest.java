package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticCleanupJobTest {

    @Test
    void unavailableDistributedLockSkipsEveryDatabaseMutation() {
        ModelDiagnosticCleanupService cleanupService = mock(ModelDiagnosticCleanupService.class);
        ModelDiagnosticCleanupLockService lockService =
                mock(ModelDiagnosticCleanupLockService.class);
        when(lockService.tryAcquire(any())).thenReturn(false);
        ModelDiagnosticCleanupJob job =
                new ModelDiagnosticCleanupJob(
                        cleanupService, lockService, 30, 500, 20, "Asia/Shanghai");

        job.cleanupScheduled();

        verify(cleanupService, never()).cleanupExpiredBatch(any(), any(Integer.class));
        verify(lockService, never()).release(any());
    }

    @Test
    void cleanupIsBoundedAndAlwaysReleasesOwnedLock() {
        ModelDiagnosticCleanupService cleanupService = mock(ModelDiagnosticCleanupService.class);
        ModelDiagnosticCleanupLockService lockService =
                mock(ModelDiagnosticCleanupLockService.class);
        when(lockService.tryAcquire(any())).thenReturn(true);
        when(cleanupService.cleanupExpiredBatch(any(), eq(500)))
                .thenReturn(new ModelDiagnosticCleanupBatch(500, 0))
                .thenReturn(new ModelDiagnosticCleanupBatch(3, 1));
        ModelDiagnosticCleanupJob job =
                new ModelDiagnosticCleanupJob(
                        cleanupService, lockService, 30, 500, 20, "Asia/Shanghai");

        job.cleanupScheduled();

        verify(cleanupService, times(2)).cleanupExpiredBatch(any(), eq(500));
        verify(lockService).release(any());
    }
}
