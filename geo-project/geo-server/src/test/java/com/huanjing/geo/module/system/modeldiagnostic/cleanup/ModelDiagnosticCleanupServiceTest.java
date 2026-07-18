package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticCleanupServiceTest {

    @Test
    void cleanupDeletesRunsBeforeEmptySessionsAndClampsBatchSize() {
        AiModelDiagnosticRunMapper runMapper = mock(AiModelDiagnosticRunMapper.class);
        AiModelDiagnosticSessionMapper sessionMapper = mock(AiModelDiagnosticSessionMapper.class);
        ModelDiagnosticCleanupService service =
                new ModelDiagnosticCleanupService(runMapper, sessionMapper);
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 15, 0, 0);
        when(runMapper.deleteExpiredBatch(cutoff, 1_000)).thenReturn(4);
        when(sessionMapper.deleteEmptyExpiredBatch(cutoff, 1_000)).thenReturn(2);

        ModelDiagnosticCleanupBatch result =
                service.cleanupExpiredBatch(cutoff, 10_000);

        assertEquals(4, result.runsDeleted());
        assertEquals(2, result.sessionsDeleted());
        verify(runMapper).deleteExpiredBatch(cutoff, 1_000);
        verify(sessionMapper).deleteEmptyExpiredBatch(cutoff, 1_000);
    }

    @Test
    void cleanupRequiresExplicitCutoff() {
        ModelDiagnosticCleanupService service = new ModelDiagnosticCleanupService(
                mock(AiModelDiagnosticRunMapper.class),
                mock(AiModelDiagnosticSessionMapper.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.cleanupExpiredBatch(null, 100));
    }
}
