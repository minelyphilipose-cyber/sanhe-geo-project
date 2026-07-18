package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticCleanupService {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final AiModelDiagnosticRunMapper runMapper;
    private final AiModelDiagnosticSessionMapper sessionMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticCleanupBatch cleanupExpiredBatch(
            LocalDateTime cutoff, int requestedLimit) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff is required");
        }
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        int runsDeleted = runMapper.deleteExpiredBatch(cutoff, limit);
        int sessionsDeleted = sessionMapper.deleteEmptyExpiredBatch(cutoff, limit);
        return new ModelDiagnosticCleanupBatch(runsDeleted, sessionsDeleted);
    }
}
