package com.huanjing.geo.module.dispatch.websearch.purge;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollAuditPurgeRun;
import com.huanjing.geo.module.dispatch.mapper.PollAuditPurgeRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PollAuditPurgeAuditWriter {

    private final PollAuditPurgeRunMapper mapper;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollAuditPurgeRun prepareForRetentionOperation(PollPurgeRequest request,
                                                          LocalDateTime now,
                                                          long retentionRunId,
                                                          String operationId,
                                                          String retentionDomain) {
        validateRetentionIdentity(retentionRunId, operationId, retentionDomain);
        java.util.List<Long> existingIds = jdbc.queryForList("""
                SELECT id FROM poll_audit_purge_runs
                 WHERE operation_id = ? AND retention_run_id = ? AND retention_domain = ?
                 ORDER BY id FOR UPDATE
                """, Long.class, operationId, retentionRunId, retentionDomain);
        if (existingIds.size() > 1) {
            throw new BizException(409, "Poll retention operation has duplicate legacy audits");
        }
        if (existingIds.size() == 1) {
            PollAuditPurgeRun existing = loadRetentionRun(existingIds.get(0));
            if (existing == null || !java.util.Objects.equals(existing.getProjectId(), request.projectId())
                    || !java.util.Objects.equals(existing.getPurgeReason(), request.reason())
                    || !java.util.Objects.equals(existing.getScopeJson(), request.scopeJson())
                    || !java.util.List.of("PREPARED", "RUNNING", "FAILED", "SUCCEEDED")
                    .contains(existing.getStatus())) {
                throw new BizException(409, "Poll retention legacy audit replay does not match its operation");
            }
            return existing;
        }
        int inserted = jdbc.update("""
                INSERT INTO poll_audit_purge_runs (
                    project_id, requested_by, purge_reason, scope_json, status,
                    audit_committed_at, retention_run_id, operation_id, retention_domain,
                    created_at, updated_at
                ) VALUES (?, ?, ?, CAST(? AS JSON), 'PREPARED', ?, ?, ?, ?, ?, ?)
                """, request.projectId(), request.requestedBy(), request.reason(), request.scopeJson(), now,
                retentionRunId, operationId, retentionDomain, now, now);
        if (inserted != 1) {
            throw new BizException(500, "Failed to commit retention purge audit before deletion");
        }
        Long id = jdbc.queryForObject("""
                SELECT id FROM poll_audit_purge_runs
                 WHERE operation_id = ? AND retention_run_id = ? AND retention_domain = ?
                """, Long.class, operationId, retentionRunId, retentionDomain);
        if (id == null) {
            throw new BizException(500, "Failed to reload retention purge audit");
        }
        return loadRetentionRun(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollAuditPurgeRun prepare(PollPurgeRequest request, LocalDateTime now) {
        return prepare(request, now, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollAuditPurgeRun prepare(PollPurgeRequest request,
                                     LocalDateTime now,
                                     Long retentionRunId,
                                     String operationId,
                                     String retentionDomain) {
        validateRetentionIdentity(retentionRunId, operationId, retentionDomain);
        PollAuditPurgeRun run = new PollAuditPurgeRun();
        run.setProjectId(request.projectId());
        run.setRequestedBy(request.requestedBy());
        run.setPurgeReason(request.reason());
        run.setScopeJson(request.scopeJson());
        run.setStatus("PREPARED");
        run.setAuditCommittedAt(now);
        run.setRetentionRunId(retentionRunId);
        run.setOperationId(operationId);
        run.setRetentionDomain(retentionDomain);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        if (mapper.insert(run) != 1) {
            throw new BizException(500, "Failed to commit purge audit before deletion");
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long runId, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("RUNNING");
        update.setStartedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markRunningInMutation(Long runId,
                                      long retentionRunId,
                                      String operationId,
                                      LocalDateTime now) {
        int changed = jdbc.update("""
                UPDATE poll_audit_purge_runs
                   SET status = 'RUNNING', started_at = COALESCE(started_at, ?),
                       completed_at = NULL, error_message = NULL, updated_at = ?
                 WHERE id = ? AND retention_run_id = ? AND operation_id = ?
                   AND retention_domain = 'poll_results' AND status IN ('PREPARED','FAILED')
                """, now, now, runId, retentionRunId, operationId);
        if (changed == 0) {
            Integer running = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM poll_audit_purge_runs
                     WHERE id = ? AND retention_run_id = ? AND operation_id = ?
                       AND retention_domain = 'poll_results' AND status = 'RUNNING'
                    """, Integer.class, runId, retentionRunId, operationId);
            if (!Integer.valueOf(1).equals(running)) {
                throw new BizException(409, "Poll retention legacy audit cannot enter RUNNING");
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long runId, String affectedRowsJson, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("SUCCEEDED");
        update.setAffectedRowsJson(affectedRowsJson);
        update.setCompletedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long runId, String errorMessage, LocalDateTime now) {
        PollAuditPurgeRun update = new PollAuditPurgeRun();
        update.setId(runId);
        update.setStatus("FAILED");
        update.setErrorMessage(truncate(errorMessage));
        update.setCompletedAt(now);
        update.setUpdatedAt(now);
        requireUpdated(mapper.updateById(update), runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetentionFailed(Long runId,
                                    long retentionRunId,
                                    String operationId,
                                    String errorMessage,
                                    LocalDateTime now) {
        int changed = jdbc.update("""
                UPDATE poll_audit_purge_runs
                   SET status = 'FAILED', error_message = ?, completed_at = ?, updated_at = ?
                 WHERE id = ? AND retention_run_id = ? AND operation_id = ?
                   AND retention_domain = 'poll_results' AND status IN ('PREPARED','RUNNING')
                """, truncate(errorMessage), now, now, runId, retentionRunId, operationId);
        if (changed == 0) {
            Integer failed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM poll_audit_purge_runs
                     WHERE id = ? AND retention_run_id = ? AND operation_id = ?
                       AND retention_domain = 'poll_results' AND status = 'FAILED'
                    """, Integer.class, runId, retentionRunId, operationId);
            if (!Integer.valueOf(1).equals(failed)) {
                throw new BizException(409, "Poll retention legacy audit cannot enter FAILED");
            }
        }
    }

    private void requireUpdated(int rows, Long runId) {
        if (rows != 1) {
            throw new BizException(409, "Purge audit run changed concurrently: " + runId);
        }
    }

    private PollAuditPurgeRun loadRetentionRun(long id) {
        return jdbc.queryForObject("""
                SELECT id, project_id, requested_by, purge_reason, scope_json, status,
                       affected_rows_json, error_message, audit_committed_at, started_at,
                       completed_at, retention_run_id, operation_id, retention_domain,
                       created_at, updated_at
                  FROM poll_audit_purge_runs WHERE id = ?
                """, (rs, rowNum) -> {
            PollAuditPurgeRun run = new PollAuditPurgeRun();
            run.setId(rs.getLong("id"));
            run.setProjectId(nullableLong(rs.getObject("project_id")));
            run.setRequestedBy(nullableLong(rs.getObject("requested_by")));
            run.setPurgeReason(rs.getString("purge_reason"));
            run.setScopeJson(rs.getString("scope_json"));
            run.setStatus(rs.getString("status"));
            run.setAffectedRowsJson(rs.getString("affected_rows_json"));
            run.setErrorMessage(rs.getString("error_message"));
            run.setAuditCommittedAt(toLocalDateTime(rs.getTimestamp("audit_committed_at")));
            run.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
            run.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
            run.setRetentionRunId(nullableLong(rs.getObject("retention_run_id")));
            run.setOperationId(rs.getString("operation_id"));
            run.setRetentionDomain(rs.getString("retention_domain"));
            run.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
            run.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
            return run;
        }, id);
    }

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown purge failure";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private void validateRetentionIdentity(Long retentionRunId, String operationId, String retentionDomain) {
        int present = (retentionRunId == null ? 0 : 1)
                + (operationId == null ? 0 : 1)
                + (retentionDomain == null ? 0 : 1);
        if (present != 0 && present != 3) {
            throw new BizException(400, "Purge audit retention identity must be fully absent or fully present");
        }
    }
}
