package com.huanjing.geo.module.system.modeldiagnostic.recovery;

import com.huanjing.geo.module.system.modeldiagnostic.audit.ModelDiagnosticOperationAuditService;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticStateRecoveryService {

    private static final int MAX_BATCH_SIZE = 500;

    private final AiModelDiagnosticRunMapper runMapper;
    private final ModelDiagnosticOperationAuditService operationAuditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticRecoveryBatch recoverExpiredBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        List<Long> runIds = runMapper.selectExpiredRunningIds(limit);
        int abandoned = 0;
        for (Long runId : runIds) {
            int updated = runMapper.markAbandonedIfExpired(runId);
            abandoned += updated;
            if (updated == 1) {
                AiModelDiagnosticRun run = runMapper.selectById(runId);
                operationAuditService.recordTerminal(run);
            }
        }
        return new ModelDiagnosticRecoveryBatch(runIds.size(), abandoned);
    }
}
