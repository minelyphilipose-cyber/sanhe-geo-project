package com.huanjing.geo.module.system.modeldiagnostic.recovery;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticStateRecoveryJobTest {

    @Test
    void startupDrainsBatchesAndScheduledScanRunsOnce() {
        ModelDiagnosticStateRecoveryService service =
                mock(ModelDiagnosticStateRecoveryService.class);
        when(service.recoverExpiredBatch(ModelDiagnosticStateRecoveryJob.BATCH_SIZE))
                .thenReturn(new ModelDiagnosticRecoveryBatch(100, 80),
                        new ModelDiagnosticRecoveryBatch(2, 2),
                        new ModelDiagnosticRecoveryBatch(0, 0));
        ModelDiagnosticStateRecoveryJob job = new ModelDiagnosticStateRecoveryJob(service);

        job.recoverOnStartup();
        job.recoverScheduled();

        verify(service, times(3))
                .recoverExpiredBatch(ModelDiagnosticStateRecoveryJob.BATCH_SIZE);
    }
}
