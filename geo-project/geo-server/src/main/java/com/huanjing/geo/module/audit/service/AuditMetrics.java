package com.huanjing.geo.module.audit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuditMetrics {

    private final Counter dbFailureCounter;
    private final Counter fileFailureCounter;
    private final Counter lostCounter;
    private final Counter executorRejectedCounter;

    public AuditMetrics(MeterRegistry meterRegistry) {
        this.dbFailureCounter = Counter.builder("audit_db_failure_total")
                .description("Total audit database write failures")
                .register(meterRegistry);
        this.fileFailureCounter = Counter.builder("audit_file_failure_total")
                .description("Total audit file write failures")
                .register(meterRegistry);
        this.lostCounter = Counter.builder("audit_lost_total")
                .description("Total audit events lost after both database and file writes failed")
                .register(meterRegistry);
        this.executorRejectedCounter = Counter.builder("audit_executor_rejected_total")
                .description("Total audit executor rejected tasks that fell back to caller thread")
                .register(meterRegistry);
    }

    public void incrementDbFailure() {
        dbFailureCounter.increment();
    }

    /**
     * Increments whenever the file-write step fails. This is also incremented when the
     * database write has already failed; in that higher-severity case {@code audit_lost_total}
     * is incremented as well.
     */
    public void incrementFileFailure() {
        fileFailureCounter.increment();
    }

    public void incrementLost() {
        lostCounter.increment();
    }

    public void incrementExecutorRejected() {
        executorRejectedCounter.increment();
    }
}
