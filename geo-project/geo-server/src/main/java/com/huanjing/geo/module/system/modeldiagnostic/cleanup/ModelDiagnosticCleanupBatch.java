package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

public record ModelDiagnosticCleanupBatch(int runsDeleted, int sessionsDeleted) {

    public boolean incomplete(int batchSize) {
        return runsDeleted >= batchSize || sessionsDeleted >= batchSize;
    }
}
