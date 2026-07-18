package com.huanjing.geo.module.system.modeldiagnostic.recovery;

import com.huanjing.geo.module.system.modeldiagnostic.audit.ModelDiagnosticOperationAuditService;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticStateRecoveryServiceTest {

    @Test
    void recoveryCountsOnlyRowsWonByConditionalAbandonment() {
        AiModelDiagnosticRunMapper mapper = mock(AiModelDiagnosticRunMapper.class);
        when(mapper.selectExpiredRunningIds(100)).thenReturn(List.of(11L, 12L));
        when(mapper.markAbandonedIfExpired(11L)).thenReturn(1);
        when(mapper.markAbandonedIfExpired(12L)).thenReturn(0);
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(11L);
        abandoned.setStatus("ABANDONED");
        when(mapper.selectById(11L)).thenReturn(abandoned);
        ModelDiagnosticOperationAuditService auditService =
                mock(ModelDiagnosticOperationAuditService.class);
        ModelDiagnosticStateRecoveryService service =
                new ModelDiagnosticStateRecoveryService(mapper, auditService);

        ModelDiagnosticRecoveryBatch result = service.recoverExpiredBatch(100);

        assertEquals(2, result.scanned());
        assertEquals(1, result.abandoned());
        verify(mapper).markAbandonedIfExpired(11L);
        verify(mapper).markAbandonedIfExpired(12L);
        verify(auditService).recordTerminal(abandoned);
    }

    @Test
    void recoveryClampsBatchSize() {
        AiModelDiagnosticRunMapper mapper = mock(AiModelDiagnosticRunMapper.class);
        when(mapper.selectExpiredRunningIds(500)).thenReturn(List.of());
        ModelDiagnosticStateRecoveryService service =
                new ModelDiagnosticStateRecoveryService(
                        mapper, mock(ModelDiagnosticOperationAuditService.class));

        service.recoverExpiredBatch(10_000);

        verify(mapper).selectExpiredRunningIds(500);
    }
}
