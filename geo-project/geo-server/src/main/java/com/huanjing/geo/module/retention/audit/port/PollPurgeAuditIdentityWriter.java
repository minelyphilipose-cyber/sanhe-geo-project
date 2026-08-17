package com.huanjing.geo.module.retention.audit.port;

import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;

@Component
public class PollPurgeAuditIdentityWriter {

    private final JdbcTemplate jdbc;

    public PollPurgeAuditIdentityWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markSucceeded(long pollAuditRunId,
                              long retentionRunId,
                              String operationId,
                              long projectId,
                              LocalDate batchDate,
                              String questionTier,
                              String affectedRowsJson,
                              LocalDateTime completedAt) {
        requireWritableTransaction();
        if (pollAuditRunId <= 0 || retentionRunId <= 0 || operationId == null || operationId.isBlank()
                || projectId <= 0 || batchDate == null || questionTier == null || questionTier.isBlank()
                || affectedRowsJson == null || affectedRowsJson.isBlank() || completedAt == null) {
            throw error("poll purge audit identity is incomplete");
        }
        int changed = jdbc.update("""
                UPDATE poll_audit_purge_runs a
                  JOIN data_retention_purged_slice p
                    ON p.operation_id = a.operation_id
                   AND p.retention_run_id = a.retention_run_id
                   AND p.domain = a.retention_domain
                   SET a.status = 'SUCCEEDED', a.affected_rows_json = ?, a.error_message = NULL,
                       a.completed_at = ?, a.updated_at = ?
                 WHERE a.id = ?
                   AND a.retention_run_id = ?
                   AND a.operation_id = ?
                   AND a.retention_domain = ?
                   AND p.project_id = ?
                   AND p.batch_date = ?
                   AND p.question_tier = ?
                   AND p.status = 'purged'
                   AND a.status = 'RUNNING'
                """, affectedRowsJson, completedAt, completedAt,
                pollAuditRunId, retentionRunId, operationId, POLL_DOMAIN,
                projectId, java.sql.Date.valueOf(batchDate), questionTier.trim().toUpperCase(java.util.Locale.ROOT));
        if (changed != 1) {
            throw error("poll purge audit identity or permanent slice marker does not match");
        }
    }

    private void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new DataRetentionAuditException(DataRetentionAuditErrors.MARKER_TRANSACTION_REQUIRED,
                    "poll purge audit identity verification requires an active writable transaction");
        }
    }

    private DataRetentionAuditException error(String message) {
        return new DataRetentionAuditException(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH, message);
    }
}
