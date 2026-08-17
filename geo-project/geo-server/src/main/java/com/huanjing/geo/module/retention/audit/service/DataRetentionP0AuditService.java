package com.huanjing.geo.module.retention.audit.service;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder.CanonicalValue;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AuditOnlyReconciliationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedPollMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollRecoveryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollRetryScope;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ReconciliationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ReconciliationPrepareCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ReconciliationResolutionCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RelationType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunRecoveryState;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RecoveryAttemptState;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedWebsiteMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteLeafFailureCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteSplitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteSplitResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteRetryScope;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteRecoveryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.DeferredRetryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditClock;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec.ValidatedEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;
import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WEBSITE_DOMAIN;

@Service
public class DataRetentionP0AuditService {

    private static final String RUN_SELECT = """
            SELECT id, trigger_id, trigger_source, domain, mode, status, requested_by, request_reason,
                   approval_ref, ticket_ref, scope_json, config_fingerprint_schema_version,
                   config_fingerprint, policy_version, application_version,
                   retention_window_start, retention_window_end, started_at
              FROM data_retention_run
            """;
    private static final List<String> SUPPORTED_SOURCES = List.of("manual", "scheduler", "canary", "retry", "reconcile");
    private static final List<String> SUPPORTED_MODES = List.of("dry_run", "execute");
    private static final ObjectMapper REPLAY_JSON = new ObjectMapper();
    private static final Object RECEIPT_RESOURCE_KEY = DataRetentionP0AuditService.class.getName() + ".receipts";

    private final JdbcTemplate jdbc;
    private final DataRetentionCanonicalEncoder encoder;
    private final DataRetentionAuditJsonCodec json;
    private final DataRetentionApplicationVersionProvider versionProvider;
    private final DataRetentionAuditClock clock;
    private final TransactionTemplate requiresNew;

    public DataRetentionP0AuditService(JdbcTemplate jdbc,
                                       DataRetentionCanonicalEncoder encoder,
                                       DataRetentionAuditJsonCodec json,
                                       DataRetentionApplicationVersionProvider versionProvider,
                                       DataRetentionAuditClock clock,
                                       PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.json = json;
        this.versionProvider = versionProvider;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RetentionRunContext startRun(RetentionRunStartCommand command) {
        String writerVersion = versionProvider.currentVersion();
        ValidatedRunCommand validated = validateRun(command);
        try {
            return requireResult(requiresNew.execute(status -> insertRun(validated, writerVersion)));
        } catch (DuplicateKeyException duplicate) {
            return requireResult(requiresNew.execute(status -> replayRun(validated, writerVersion)));
        }
    }

    public RetentionRunContext recordSkippedRun(RetentionRunStartCommand command, String stableReason) {
        String writerVersion = versionProvider.currentVersion();
        ValidatedRunCommand validated = validateRun(command);
        if (!"scheduler".equals(validated.triggerSource()) || !"execute".equals(validated.mode())
                || !"lease_busy".equals(stableReason)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "skipped-run writer only accepts scheduler execute lease_busy");
        }
        try {
            return requireResult(requiresNew.execute(status -> insertSkippedRun(validated, writerVersion)));
        } catch (DuplicateKeyException duplicate) {
            return requireResult(requiresNew.execute(status -> replayRun(validated, writerVersion)));
        }
    }

    private RetentionRunContext insertSkippedRun(ValidatedRunCommand command, String writerVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        Instant now = clock.now();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO data_retention_run (
                      domain, mode, status, retention_window_start, retention_window_end,
                      candidate_count, affected_count, skipped_count, warning_count,
                      metrics_json, error_message, started_at, created_at, updated_at,
                      trigger_id, trigger_source, requested_by, request_reason, approval_ref, ticket_ref,
                      scope_json, config_fingerprint, config_fingerprint_schema_version, policy_version,
                      application_version, attempt_count, succeeded_attempt_count, failed_attempt_count,
                      skipped_attempt_count, unknown_attempt_count, finished_at, attempt_rollup_completed_at
                    ) VALUES (?, ?, 'skipped', ?, ?, 0, 0, 0, 0, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                              0, 0, 0, 0, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setString(index++, command.contract().domain());
            statement.setString(index++, command.mode());
            statement.setObject(index++, sqlDate(command.retentionWindowStart()));
            statement.setObject(index++, sqlDate(command.retentionWindowEnd()));
            statement.setObject(index++, timestamp(command.startedAt()));
            statement.setObject(index++, timestamp(now));
            statement.setObject(index++, timestamp(now));
            statement.setString(index++, command.triggerId());
            statement.setString(index++, command.triggerSource());
            statement.setLong(index++, command.requestedBy());
            statement.setString(index++, command.requestReason());
            statement.setString(index++, command.approvalRef());
            statement.setString(index++, command.ticketRef());
            statement.setString(index++, command.scopeJson());
            statement.setBytes(index++, command.contract().configFingerprint());
            statement.setInt(index++, command.contract().configFingerprintSchemaVersion());
            statement.setString(index++, command.contract().policyVersion());
            statement.setString(index++, writerVersion);
            statement.setObject(index++, timestamp(now));
            statement.setObject(index, timestamp(now));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                "failed to read skipped retention run id");
        return new RetentionRunContext(id.longValue(), command.triggerId(), command.triggerSource(), command.mode(),
                command.contract(), writerVersion, writerVersion, RunReplayStatus.TERMINAL_REPLAY,
                "skipped", command.startedAt());
    }

    public RetentionRunSnapshot lookupRun(RetentionRunStartCommand command) {
        String writerVersion = versionProvider.currentVersion();
        ValidatedRunCommand validated = validateRun(command);
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE trigger_id = ? AND domain = ?",
                RUN_ROW_MAPPER, validated.triggerId(), validated.contract().domain());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || !rows.get(0).sameRequest(validated)) {
            throw error(DataRetentionAuditErrors.RUN_IDEMPOTENCY_CONFLICT,
                    "existing run command does not match exact lookup");
        }
        RunRow row = rows.get(0);
        RunReplayStatus replay = "running".equals(row.status())
                ? RunReplayStatus.RUNNING_REPLAY : RunReplayStatus.TERMINAL_REPLAY;
        return snapshot(row, writerVersion, replay);
    }

    public List<RetentionRunSnapshot> findRecoverableSchedulerRuns(String domain) {
        if (!List.of(WEBSITE_DOMAIN, POLL_DOMAIN).contains(domain)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "unsupported scheduler recovery domain");
        }
        String writerVersion = versionProvider.currentVersion();
        List<RunRow> rows = jdbc.query(RUN_SELECT + """
                 WHERE domain = ? AND trigger_source = 'scheduler' AND mode = 'execute'
                   AND status = 'running' AND finished_at IS NULL AND attempt_rollup_completed_at IS NULL
                 ORDER BY started_at, id
                """, RUN_ROW_MAPPER, domain);
        return rows.stream().map(row -> snapshot(row, writerVersion, RunReplayStatus.RUNNING_REPLAY)).toList();
    }

    public List<RetentionRunSnapshot> findRecoverableDeferredRetryRuns(String domain) {
        if (!List.of(WEBSITE_DOMAIN, POLL_DOMAIN).contains(domain)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "unsupported deferred retry recovery domain");
        }
        String writerVersion = versionProvider.currentVersion();
        List<RunRow> rows = jdbc.query(RUN_SELECT + """
                 WHERE domain = ? AND trigger_source = 'retry' AND mode = 'execute'
                   AND status = 'running' AND finished_at IS NULL AND attempt_rollup_completed_at IS NULL
                 ORDER BY started_at, id
                """, RUN_ROW_MAPPER, domain);
        List<RetentionRunSnapshot> result = new ArrayList<>();
        Map<String, Long> operationRuns = new LinkedHashMap<>();
        for (RunRow row : rows) {
            Map<String, Object> scope = parseObject(row.scopeJson());
            Object rawInvocation = scope.get("executionInvocation");
            if (!(rawInvocation instanceof Map<?, ?> invocation)) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "running retry run lacks canonical execution provenance");
            }
            if (!"scheduler_deferred_retry".equals(invocation.get("kind"))) {
                if ("manual_retry".equals(invocation.get("kind"))) continue;
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "running retry run has unknown execution provenance");
            }
            String operationId = invocation.get("relatedOperationId") instanceof String value ? value : null;
            Number originRunId = invocation.get("originRunId") instanceof Number value ? value : null;
            if (operationId == null || originRunId == null || originRunId.longValue() <= 0
                    || !"recovery_not_started".equals(row.requestReason())
                    || !Objects.equals("retention-recovery:" + operationId, row.ticketRef())
                    || row.approvalRef() != null) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "stored deferred retry provenance is invalid");
            }
            OperationRow operation = readOperation(normalizeUuid(operationId, "related operation id"));
            requireContract(operation.contract(), row.contract());
            if (!operation.domain().equals(domain) || operation.originRunId() != originRunId.longValue()) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "stored deferred retry operation provenance does not match");
            }
            Long prior = operationRuns.putIfAbsent(operationId, row.id());
            if (prior != null) {
                throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                        "multiple running deferred retry runs reference one operation");
            }
            result.add(snapshot(row, writerVersion, RunReplayStatus.RUNNING_REPLAY));
        }
        return List.copyOf(result);
    }

    public List<DeferredRetryOperation> findDeferredRetryableOperations(String domain, int limit) {
        if (!List.of(WEBSITE_DOMAIN, POLL_DOMAIN).contains(domain) || limit < 1 || limit > 100) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "deferred retry lookup is invalid");
        }
        List<OperationRow> operations = jdbc.query("SELECT o.* FROM data_retention_operation o " + """
                 JOIN data_retention_batch_attempt a ON a.operation_id = o.operation_id
                WHERE o.domain = ? AND o.outcome_status = 'rolled_back'
                  AND o.outcome_finalized_at IS NULL AND o.detail_purged_at IS NULL
                  AND a.id = (SELECT MAX(latest.id) FROM data_retention_batch_attempt latest
                               WHERE latest.operation_id = o.operation_id)
                  AND a.status = 'skipped'
                  AND a.last_error LIKE 'recovery_not_started:%'
                  AND JSON_EXTRACT(a.metrics_json, '$.executionBudgetUsage.schemaVersion') = 1
                  AND JSON_EXTRACT(a.metrics_json, '$.executionBudgetUsage.committedTransactionCount') = 0
                  AND JSON_EXTRACT(a.metrics_json, '$.executionBudgetUsage.chargedCandidateItems') = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM data_retention_operation_run x
                    JOIN data_retention_run retry_run ON retry_run.id = x.run_group_id
                    WHERE x.operation_id = o.operation_id AND x.relation_type = 'retry'
                      AND retry_run.status = 'running' AND retry_run.trigger_source = 'retry'
                  )
                ORDER BY a.finished_at, o.operation_id
                LIMIT ?
                """, OPERATION_ROW_MAPPER, domain, limit);
        List<DeferredRetryOperation> result = new ArrayList<>();
        for (OperationRow operation : operations) {
            RunRow origin = readOriginRun(operation);
            requireContract(operation.contract(), rebuildOriginContract(origin));
            result.add(new DeferredRetryOperation(operation.operationId(), operation.originRunId(), operation.domain(),
                    operation.contract(), parseObject(origin.scopeJson())));
        }
        return List.copyOf(result);
    }

    public RetentionRunSnapshot readRun(long runId) {
        String writerVersion = versionProvider.currentVersion();
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE id = ?", RUN_ROW_MAPPER, runId);
        if (rows.size() != 1) return null;
        RunRow row = rows.get(0);
        RunReplayStatus replay = "running".equals(row.status())
                ? RunReplayStatus.RUNNING_REPLAY : RunReplayStatus.TERMINAL_REPLAY;
        return snapshot(row, writerVersion, replay);
    }

    public RetentionRunRecoveryState readRecoveryState(long runId) {
        RetentionRunSnapshot snapshot = readRun(runId);
        if (snapshot == null) return null;
        List<AttemptBudgetRow> attempts = jdbc.query("""
                SELECT id, operation_id, attempt_type, status, metrics_json FROM data_retention_batch_attempt
                 WHERE run_group_id = ? ORDER BY sequence_no, id
                """, (rs, n) -> new AttemptBudgetRow(rs.getLong("id"), rs.getString("operation_id"),
                rs.getString("attempt_type"), rs.getString("status"), rs.getString("metrics_json")), runId);
        long transactions = 0;
        long candidates = 0;
        boolean terminal = true;
        List<RecoveryAttemptState> recoveryAttempts = new ArrayList<>();
        for (AttemptBudgetRow attempt : attempts) {
            if ("prepared".equals(attempt.status()) || "running".equals(attempt.status())) terminal = false;
            try {
                JsonNode metrics = attempt.metricsJson() == null ? null : REPLAY_JSON.readTree(attempt.metricsJson());
                JsonNode usageNode = metrics == null ? null : metrics.path("executionBudgetUsage");
                ExecutionBudgetUsage usage = null;
                if (usageNode != null && !usageNode.isMissingNode()) {
                    if (usageNode.path("schemaVersion").asInt(-1) != 1
                            || !usageNode.path("committedTransactionCount").canConvertToLong()
                            || !usageNode.path("chargedCandidateItems").canConvertToLong()) {
                        throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt budget usage is invalid");
                    }
                    usage = new ExecutionBudgetUsage(1,
                            usageNode.path("committedTransactionCount").asLong(),
                            usageNode.path("chargedCandidateItems").asLong());
                    transactions = Math.addExact(transactions, usage.committedTransactionCount());
                    candidates = Math.addExact(candidates, usage.chargedCandidateItems());
                }
                if (attempt.operationId() != null && isTerminal(attempt.status()) && usage == null) {
                    throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                            "terminal mutation attempt is missing budget usage");
                }
                recoveryAttempts.add(new RecoveryAttemptState(attempt.id(), attempt.operationId(),
                        attemptType(attempt.attemptType()), attemptStatus(attempt.status()),
                        metric(metrics, "candidateCount"), metric(metrics, "affectedCount"),
                        metric(metrics, "skippedCount"), metric(metrics, "warningCount"), usage));
            } catch (DataRetentionAuditException error) {
                throw error;
            } catch (Exception error) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt budget usage is invalid", error);
            }
        }
        Integer operations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?", Integer.class, runId);
        return new RetentionRunRecoveryState(snapshot, transactions, candidates,
                attempts.isEmpty() && Integer.valueOf(0).equals(operations), terminal, recoveryAttempts);
    }

    private static long metric(JsonNode metrics, String key) {
        if (metrics == null) return 0;
        JsonNode value = metrics.path(key);
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt summary metric is invalid");
        }
        return value.asLong();
    }

    private static AttemptType attemptType(String value) {
        return Arrays.stream(AttemptType.values()).filter(type -> type.databaseValue().equals(value)).findFirst()
                .orElseThrow(() -> error(DataRetentionAuditErrors.CONTEXT_INVALID, "stored attempt type is invalid"));
    }

    private static AttemptStatus attemptStatus(String value) {
        return Arrays.stream(AttemptStatus.values()).filter(status -> status.databaseValue().equals(value)).findFirst()
                .orElseThrow(() -> error(DataRetentionAuditErrors.CONTEXT_INVALID, "stored attempt status is invalid"));
    }

    private RetentionRunSnapshot snapshot(RunRow row, String writerVersion, RunReplayStatus replay) {
        Map<String, Long> counts = readRunCounts(row.id());
        Map<String, Object> scope = parseObject(row.scopeJson());
        RetentionRunStartCommand exactCommand = new RetentionRunStartCommand(
                row.triggerId(), row.triggerSource(), row.mode(), row.requestedBy() == null ? 0 : row.requestedBy(),
                row.requestReason(), row.approvalRef(), row.ticketRef(), row.contract(), scope,
                row.retentionWindowStart(), row.retentionWindowEnd(), row.startedAt());
        return new RetentionRunSnapshot(row.context(writerVersion, replay), exactCommand, counts.get("candidate_count"),
                counts.get("affected_count"), counts.get("skipped_count"), counts.get("warning_count"),
                scope);
    }

    private Map<String, Long> readRunCounts(long runId) {
        return jdbc.queryForObject("""
                SELECT candidate_count, affected_count, skipped_count, warning_count
                  FROM data_retention_run WHERE id = ?
                """, (rs, n) -> Map.of(
                "candidate_count", rs.getLong("candidate_count"),
                "affected_count", rs.getLong("affected_count"),
                "skipped_count", rs.getLong("skipped_count"),
                "warning_count", rs.getLong("warning_count")), runId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String value) {
        try {
            if (value == null) return Map.of();
            return REPLAY_JSON.readValue(value, LinkedHashMap.class);
        } catch (Exception error) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "stored run scope is invalid JSON", error);
        }
    }

    public RetentionOperationContext prepareWebsiteOrigin(RetentionRunContext run,
                                                           WebsiteOperationCommand command,
                                                           RetentionAttemptCommand firstAttempt) {
        requireCurrentWriter(run);
        CanonicalValue scope = encoder.websiteScope(command, run.compatibilityContract());
        if (run.replayStatus() == RunReplayStatus.TERMINAL_REPLAY || !"running".equals(run.status())) {
            return requireResult(requiresNew.execute(status -> recoverOrigin(run, scope.sha256(), firstAttempt,
                    "website_batch", command)));
        }
        List<Long> articleIds = command.articleIds().stream().sorted().toList();
        try {
            return requireResult(requiresNew.execute(status -> createOrigin(run, "website_batch", scope,
                    articleIds.size(), command.cutoff(), command.upperBoundBusinessKey(), null, null, null, null,
                    firstAttempt, articleIds)));
        } catch (DuplicateKeyException duplicate) {
            return requireResult(requiresNew.execute(status -> recoverOrigin(run, scope.sha256(), firstAttempt,
                    "website_batch", command)));
        }
    }

    public RetentionOperationContext preparePollOrigin(RetentionRunContext run,
                                                        PollOperationCommand command,
                                                        RetentionAttemptCommand firstAttempt) {
        requireCurrentWriter(run);
        CanonicalValue scope = encoder.pollScope(command, run.compatibilityContract());
        if (run.replayStatus() == RunReplayStatus.TERMINAL_REPLAY || !"running".equals(run.status())) {
            return requireResult(requiresNew.execute(status -> recoverOrigin(run, scope.sha256(), firstAttempt,
                    "poll_slice", command)));
        }
        String tier = command.questionTier().trim().toUpperCase(Locale.ROOT);
        try {
            return requireResult(requiresNew.execute(status -> createOrigin(run, "poll_slice", scope,
                    1, null, null, command.projectId(), command.batchDate(), tier,
                    command.retentionCutoffDate(), firstAttempt, List.of())));
        } catch (DuplicateKeyException duplicate) {
            return requireResult(requiresNew.execute(status -> recoverOrigin(run, scope.sha256(), firstAttempt,
                    "poll_slice", command)));
        }
    }

    public RetentionOperationContext preparePollCleanupOrigin(RetentionRunContext run,
                                                               PollCleanupOperationCommand command,
                                                               RetentionAttemptCommand firstAttempt) {
        requireCurrentWriter(run);
        CanonicalValue scope = encoder.pollCleanupScope(command, run.compatibilityContract());
        return requireResult(requiresNew.execute(status -> {
            List<OperationRow> replay = jdbc.query(
                    OPERATION_SELECT + " WHERE domain = ? AND batch_key_hash = ? FOR UPDATE",
                    OPERATION_ROW_MAPPER, run.compatibilityContract().domain(), scope.sha256());
            if (!replay.isEmpty()) {
                return recoverOrigin(run, scope.sha256(), firstAttempt,
                        "poll_preserved_cleanup", command);
            }
            validateFreshPollCleanupOwnership(command);
            String tier = normalizeTier(command.questionTier());
            List<Long> ids = command.pollResultIds().stream().sorted().toList();
            return createOrigin(run, "poll_preserved_cleanup", scope, ids.size(), null, null,
                    command.projectId(), command.batchDate(), tier, command.retentionCutoffDate(),
                    firstAttempt, ids);
        }));
    }

    public RetentionAttemptContext prepareRelatedAttempt(RetentionRunContext run,
                                                          String operationId,
                                                          RelationType relationType,
                                                          RetentionAttemptCommand command) {
        requireCurrentWriter(run);
        String id = normalizeUuid(operationId, "operation id");
        return requireResult(requiresNew.execute(status -> {
            OperationRow operation = lockOperation(id);
            requireDetailPresent(operation);
            validateRelatedAttemptContract(operation, run, relationType, command);
            RunRow runRow = lockRun(run.runId());
            requireRunContext(run, runRow);
            if (relationType == RelationType.RETRY) {
                return prepareRetry(run, operation, command);
            }
            if (relationType != RelationType.RECONCILE) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "related attempt must be retry or reconcile");
            }
            ensureRelation(operation, run, relationType);
            return createAttemptLocked(run, operation, command, false);
        }));
    }

    public RetentionAttemptContext createOperationlessAttempt(RetentionRunContext run,
                                                               RetentionAttemptCommand command) {
        requireCurrentWriter(run);
        if (command == null || command.attemptType() == null || command.attemptType().operationRequired()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "operationless attempt type is invalid");
        }
        return requireResult(requiresNew.execute(status -> {
            RunRow runRow = lockRun(run.runId());
            requireRunContext(run, runRow);
            return createAttemptLocked(run, null, command, false);
        }));
    }

    public void markAttemptRunning(long attemptId) {
        requiresNew.executeWithoutResult(status -> transitionAttempt(attemptId, null, AttemptStatus.RUNNING,
                Map.of(), null, null, clock.now()));
    }

    public void completeAttempt(long attemptId, RetentionAttemptResult result) {
        if (result == null || result.status() == null || !result.status().terminal()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt result must be terminal");
        }
        requireNonNegative(result.candidateCount(), result.affectedCount(), result.skippedCount(), result.warningCount());
        requiresNew.executeWithoutResult(status -> transitionAttempt(attemptId, result, result.status(),
                attemptMetrics(result), result.stableErrorCode(), result.errorMessage(), result.eventTime()));
    }

    public void recoverAttempt(long attemptId, RetentionAttemptResult result) {
        if (result == null || result.status() == null || !result.status().terminal()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "recovery result must be terminal");
        }
        requireNonNegative(result.candidateCount(), result.affectedCount(), result.skippedCount(), result.warningCount());
        requiresNew.executeWithoutResult(status -> recoverAttemptLocked(attemptId, result));
    }

    private void recoverAttemptLocked(long attemptId, RetentionAttemptResult result) {
        AttemptIdentity identity = readAttemptIdentity(attemptId);
        OperationRow operation = identity.operationId() == null ? null : lockOperation(identity.operationId());
        if (operation == null) lockRun(identity.runId());
        AttemptRow attempt = lockAttempt(attemptId);
        if (isTerminal(attempt.status())) {
            transitionAttempt(attemptId, result, result.status(), attemptMetrics(result),
                    result.stableErrorCode(), result.errorMessage(), result.eventTime());
            return;
        }
        if (operation == null) {
            if (result.status() != AttemptStatus.SKIPPED) {
                throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                        "operationless recovery may only skip a prepared attempt");
            }
        } else if (result.status() == AttemptStatus.SUCCEEDED) {
            requirePermanentMarker(operation.operationId(), operation.domain());
            if (!"committed".equals(operation.outcomeStatus())) {
                throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                        "marker recovery requires committed operation outcome");
            }
        } else if (result.status() == AttemptStatus.SKIPPED) {
            if (!List.of("pending", "rolled_back").contains(operation.outcomeStatus())
                    || result.metrics() == null
                    || !new ExecutionBudgetUsage(1, 0, 0).equals(result.metrics().get("executionBudgetUsage"))
                    || !"recovery_not_started".equals(result.stableErrorCode())) {
                throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                        "skipped mutation recovery requires zero usage and recovery_not_started");
            }
        } else if (!List.of(AttemptStatus.UNKNOWN, AttemptStatus.MANUAL_REVIEW, AttemptStatus.FAILED)
                .contains(result.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "unsupported attempt recovery outcome");
        }
        Instant eventTime = eventTime(result.eventTime());
        completeAttemptGuarded(attemptId, eventTime,
                json.writeStableObject(attemptMetrics(result), true),
                combineError(result.stableErrorCode(), result.errorMessage()), result.status().databaseValue());
        if (operation != null) {
            if (result.status() == AttemptStatus.SKIPPED) {
                requireOne(jdbc.update("""
                        UPDATE data_retention_operation SET outcome_status = 'rolled_back', updated_at = ?
                         WHERE operation_id = ? AND outcome_status IN ('pending','rolled_back')
                           AND outcome_finalized_at IS NULL AND detail_purged_at IS NULL
                        """, timestamp(clock.now()), operation.operationId()),
                        DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                        "skipped recovery cannot roll back operation");
            } else {
                updateOperationForTerminal(operation.operationId(), result.status(), eventTime);
            }
        }
    }

    public void finishRun(long runId, RunBusinessSummary summary) {
        String writerVersion = versionProvider.currentVersion();
        requiresNew.executeWithoutResult(status -> finishRunLocked(runId, summary, writerVersion));
    }

    public ReconciliationContext prepareReconciliation(ReconciliationPrepareCommand command) {
        Objects.requireNonNull(command, "command");
        versionProvider.currentVersion();
        requireCurrentWriter(command.run());
        ValidatedEvidence evidence = json.validateEvidence(command.evidence());
        return requireResult(requiresNew.execute(status -> prepareReconciliationLocked(command, evidence)));
    }

    public ReconciliationContext resolveReconciliation(ReconciliationResolutionCommand command) {
        Objects.requireNonNull(command, "command");
        versionProvider.currentVersion();
        ValidatedEvidence evidence = json.validateEvidence(command.evidence());
        return requireResult(requiresNew.execute(status -> resolveReconciliationLocked(command, evidence)));
    }

    public ReconciliationContext recordAuditOnlyReconciliation(AuditOnlyReconciliationCommand command) {
        Objects.requireNonNull(command, "command");
        String writerVersion = versionProvider.currentVersion();
        ValidatedEvidence evidence = json.validateEvidence(command.evidence());
        return requireResult(requiresNew.execute(status -> auditOnlyLocked(command, evidence, writerVersion)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TrustedMutationReceipt commitWebsiteMutation(WebsiteCommitCommand command) {
        requireWritableTransaction();
        String writerVersion = versionProvider.currentVersion();
        RetentionOperationContext context = Objects.requireNonNull(command.operation(), "operation");
        OperationRow operation = lockOperation(context.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, context);
        requireRelation(operation.operationId(), command.runId(), operation.domain());
        AttemptRow attempt = lockAttemptAfterOperation(command.attemptId(), operation.operationId());
        if (attempt.runId() != command.runId()) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "website attempt does not belong to marker run");
        }
        if (!"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "website attempt is not active");
        }
        Instant eventTime = eventTime(command.committedAt());
        try {
            jdbc.update("""
                    INSERT INTO data_retention_batch_commit (
                      operation_id, run_group_id, domain, batch_key_hash, hash_schema_version,
                      item_count, cutoff_at, upper_bound_business_key,
                      config_fingerprint_schema_version, config_fingerprint, policy_version,
                      committed_application_version, committed_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, operation.operationId(), command.runId(), operation.domain(), operation.batchKeyHash(),
                    operation.hashSchemaVersion(), operation.itemCount(), timestamp(operation.cutoffAt()),
                    operation.upperBoundBusinessKey(), operation.contract().configFingerprintSchemaVersion(),
                    operation.contract().configFingerprint(), operation.contract().policyVersion(), writerVersion,
                    timestamp(eventTime), timestamp(clock.now()));
        } catch (DuplicateKeyException duplicate) {
            throw error(DataRetentionAuditErrors.OPERATION_ALREADY_COMMITTED,
                    "website retention operation already has a commit marker", duplicate);
        }
        finalizeCommitted(operation, "website_commit", "marker_committed", eventTime);
        ExecutionBudgetUsage usage = new ExecutionBudgetUsage(1, 1, operation.itemCount());
        Map<String, Object> trustedMetrics = new LinkedHashMap<>(command.metrics());
        trustedMetrics.put("executionBudgetUsage", usageMetrics(usage));
        completeAttemptGuarded(attempt.id(), eventTime,
                summaryMetrics(operation.itemCount(), operation.itemCount(), 0, 0,
                        trustedMetrics), null, "succeeded");
        TrustedMutationReceipt receipt = new TrustedMutationReceipt(command.runId(), operation.operationId(), attempt.id(),
                "website_commit:" + operation.operationId(), usage);
        registerReceipt(receipt);
        return receipt;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedWebsiteMutationContext lockWebsiteMutation(RetentionOperationContext context,
                                                             long runId,
                                                             long attemptId) {
        requireWritableTransaction();
        versionProvider.currentVersion();
        RetentionOperationContext expected = Objects.requireNonNull(context, "operation context");
        OperationRow operation = lockOperation(expected.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, expected);
        requireRelation(operation.operationId(), runId, operation.domain());
        if (!WEBSITE_DOMAIN.equals(operation.domain()) || !"website_batch".equals(operation.operationType())
                || !"pending".equals(operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website mutation operation is not pending");
        }
        Integer unresolved = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation
                 WHERE operation_id = ? AND status <> 'resolved' FOR UPDATE
                """, Integer.class, operation.operationId());
        if (!Integer.valueOf(0).equals(unresolved)) {
            throw error(DataRetentionAuditErrors.OPERATION_UNRESOLVED,
                    "website operation has unresolved reconciliation evidence");
        }
        Long activeGroupId = lockActiveWebsiteFailureGroup(operation);
        AttemptRow attempt = lockAttemptAfterOperation(attemptId, operation.operationId());
        if (attempt.runId() != runId || !"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "website attempt is not running in the requested run");
        }
        List<Long> articleIds = lockWebsiteItems(operation);
        validateActiveWebsiteFailureGroup(activeGroupId, articleIds);
        return new LockedWebsiteMutationContext(
                operation.context(versionProvider.currentVersion(), OperationReplayStatus.PREPARE_REPLAY,
                        attempt.context(), articleIds),
                runId, attemptId, articleIds, activeGroupId);
    }

    public TrustedTerminalUsageReceipt openOrRefreshWebsiteLeafFailure(WebsiteLeafFailureCommand command) {
        Objects.requireNonNull(command, "command");
        return requireResult(requiresNew.execute(status -> openOrRefreshWebsiteLeafFailureLocked(command)));
    }

    public WebsiteSplitResult finalizeWebsiteSplitParent(WebsiteSplitCommand command) {
        Objects.requireNonNull(command, "command");
        return requireResult(requiresNew.execute(status -> finalizeWebsiteSplitParentLocked(command)));
    }

    public WebsiteRetryScope readWebsiteRetryScope(String operationId) {
        String normalized = normalizeUuid(operationId, "operation id");
        return requireResult(requiresNew.execute(status -> {
            OperationRow operation = lockOperation(normalized);
            requireDetailPresent(operation);
            if (!WEBSITE_DOMAIN.equals(operation.domain()) || !"rolled_back".equals(operation.outcomeStatus())) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "website operation is not eligible for exact retry");
            }
            Long groupId = lockActiveWebsiteFailureGroup(operation);
            if (groupId == null) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "website exact retry requires an active failure group");
            }
            List<Long> articleIds = lockWebsiteItems(operation);
            validateActiveWebsiteFailureGroup(groupId, articleIds);
            LatestAttempt latest = latestAttempt(operation.operationId(), operation.originRunId(), true);
            if (latest.id() == null) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "website exact retry has no predecessor attempt");
            }
            return new WebsiteRetryScope(
                    operation.context(versionProvider.currentVersion(), OperationReplayStatus.PREPARE_REPLAY,
                            null, articleIds),
                    latest.id(), articleIds);
        }));
    }

    public WebsiteRecoveryOperation readWebsiteRecoveryOperation(String operationId,
                                                                 long runId,
                                                                 long attemptId) {
        String normalized = normalizeUuid(operationId, "operation id");
        return requireResult(requiresNew.execute(status -> {
            OperationRow operation = readOperation(normalized);
            requireDetailPresent(operation);
            requireRelation(operation.operationId(), runId, operation.domain());
            AttemptRow attempt = lockAttemptAfterOperation(attemptId, operation.operationId());
            if (attempt.runId() != runId) {
                throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                        "website recovery attempt belongs to another run");
            }
            List<Long> articleIds = jdbc.queryForList("""
                    SELECT business_key FROM data_retention_batch_item
                     WHERE operation_id = ? AND run_group_id = ? AND domain = ?
                     ORDER BY sequence_no, id
                    """, Long.class, operation.operationId(), operation.originRunId(), operation.domain());
            if (articleIds.size() != operation.itemCount()) {
                throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                        "website recovery item tree is incomplete");
            }
            List<Map<String, Object>> markers = jdbc.queryForList("""
                    SELECT operation_id, run_group_id, domain, batch_key_hash, hash_schema_version,
                           item_count, cutoff_at, upper_bound_business_key,
                           config_fingerprint_schema_version, config_fingerprint, policy_version
                      FROM data_retention_batch_commit WHERE operation_id = ?
                    """, operation.operationId());
            String markerState = markers.isEmpty() ? "absent"
                    : markers.size() == 1 && websiteMarkerMatches(operation, runId, markers.get(0))
                    ? "exact" : "mismatch";
            OperationReplayStatus replay = List.of("committed", "closed_no_mutation")
                    .contains(operation.outcomeStatus())
                    ? OperationReplayStatus.TERMINAL_REPLAY : OperationReplayStatus.PREPARE_REPLAY;
            return new WebsiteRecoveryOperation(
                    operation.context(versionProvider.currentVersion(), replay, attempt.context(), articleIds),
                    attempt.context(), markerState);
        }));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedPollMutationContext lockPollMutation(RetentionOperationContext context,
                                                       long runId,
                                                       long attemptId) {
        requireWritableTransaction();
        versionProvider.currentVersion();
        RetentionOperationContext expected = Objects.requireNonNull(context, "operation context");
        OperationRow operation = lockOperation(expected.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, expected);
        requireRelation(operation.operationId(), runId, operation.domain());
        if (!POLL_DOMAIN.equals(operation.domain())
                || !List.of("poll_slice", "poll_preserved_cleanup").contains(operation.operationType())
                || !"pending".equals(operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "poll mutation operation is not pending");
        }
        Integer unresolved = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation
                 WHERE operation_id = ? AND status <> 'resolved' FOR UPDATE
                """, Integer.class, operation.operationId());
        if (!Integer.valueOf(0).equals(unresolved)) {
            throw error(DataRetentionAuditErrors.OPERATION_UNRESOLVED,
                    "poll operation has unresolved reconciliation evidence");
        }
        AttemptRow attempt = lockAttemptAfterOperation(attemptId, operation.operationId());
        if (attempt.runId() != runId || !"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "poll attempt is not running in the requested run");
        }
        List<Long> resultIds = "poll_preserved_cleanup".equals(operation.operationType())
                ? lockPollCleanupItems(operation) : List.of();
        return new LockedPollMutationContext(
                operation.context(versionProvider.currentVersion(), OperationReplayStatus.PREPARE_REPLAY,
                        attempt.context(), resultIds), runId, attemptId, resultIds);
    }

    public PollRetryScope readPollRetryScope(String operationId) {
        String normalized = normalizeUuid(operationId, "operation id");
        return requireResult(requiresNew.execute(status -> {
            OperationRow operation = lockOperation(normalized);
            requireDetailPresent(operation);
            if (!POLL_DOMAIN.equals(operation.domain())
                    || !List.of("poll_slice", "poll_preserved_cleanup").contains(operation.operationType())
                    || !"rolled_back".equals(operation.outcomeStatus())) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "poll operation is not eligible for exact retry");
            }
            List<Long> resultIds = "poll_preserved_cleanup".equals(operation.operationType())
                    ? lockPollCleanupItems(operation) : List.of();
            LatestAttempt latest = latestAttempt(operation.operationId(), operation.originRunId(), true);
            if (latest.id() == null) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "poll exact retry has no predecessor attempt");
            }
            return new PollRetryScope(operation.context(versionProvider.currentVersion(),
                    OperationReplayStatus.PREPARE_REPLAY, null, resultIds), latest.id(), resultIds);
        }));
    }

    public PollRecoveryOperation readPollRecoveryOperation(String operationId,
                                                           long runId,
                                                           long attemptId) {
        String normalized = normalizeUuid(operationId, "operation id");
        return requireResult(requiresNew.execute(status -> {
            OperationRow operation = readOperation(normalized);
            requireDetailPresent(operation);
            requireRelation(operation.operationId(), runId, operation.domain());
            if (!POLL_DOMAIN.equals(operation.domain())
                    || !List.of("poll_slice", "poll_preserved_cleanup").contains(operation.operationType())) {
                throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                        "poll recovery operation type is invalid");
            }
            AttemptRow attempt = lockAttemptAfterOperation(attemptId, operation.operationId());
            if (attempt.runId() != runId) {
                throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                        "poll recovery attempt belongs to another run");
            }
            List<Long> resultIds = "poll_preserved_cleanup".equals(operation.operationType())
                    ? readPollCleanupItems(operation) : List.of();
            String markerState = "poll_slice".equals(operation.operationType())
                    ? pollSliceMarkerState(operation, runId)
                    : pollCleanupMarkerState(operation, resultIds);
            OperationReplayStatus replay = List.of("committed", "closed_no_mutation")
                    .contains(operation.outcomeStatus())
                    ? OperationReplayStatus.TERMINAL_REPLAY : OperationReplayStatus.PREPARE_REPLAY;
            return new PollRecoveryOperation(operation.context(versionProvider.currentVersion(), replay,
                    attempt.context(), resultIds), attempt.context(), markerState);
        }));
    }

    public void validateTrustedTerminalUsageReceipt(TrustedTerminalUsageReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        requiresNew.executeWithoutResult(status -> {
            if (!TrustedTerminalUsageReceipt.WEBSITE_LEAF_QUARANTINE_V1.equals(receipt.receiptType())) {
                throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                        "terminal usage receipt type is unsupported");
            }
            OperationRow operation = lockOperation(receipt.operationId());
            Long groupId = lockActiveWebsiteFailureGroup(operation);
            AttemptRow attempt = lockAttemptAfterOperation(receipt.attemptId(), operation.operationId());
            List<Long> articleIds = lockWebsiteItems(operation);
            validateActiveWebsiteFailureGroup(groupId, articleIds);
            ExecutionBudgetUsage expectedUsage = new ExecutionBudgetUsage(1, 0, operation.itemCount());
            requireLeafTerminalMetrics(attempt.metricsJson(), operation.itemCount());
            String digest = terminalReceiptDigest(attempt.id(), operation.itemCount(), groupId, articleIds,
                    attempt.metricsJson());
            if (attempt.runId() != receipt.runId()
                    || !"failed".equals(attempt.status())
                    || !"rolled_back".equals(operation.outcomeStatus())
                    || groupId == null
                    || !"failed".equals(receipt.terminalStatus())
                    || !expectedUsage.equals(receipt.usage())
                    || !digest.equals(receipt.persistedFactDigest())) {
                throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                        "terminal usage receipt does not match persisted leaf facts");
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveWebsiteFailureGroup(LockedWebsiteMutationContext context,
                                           long resolvedBy,
                                           String reason,
                                           Instant resolvedAt) {
        requireWritableTransaction();
        if (context == null || context.activeFailureGroupId() == null || resolvedBy <= 0) {
            return;
        }
        Instant eventTime = eventTime(resolvedAt);
        int itemChanged = jdbc.update("""
                UPDATE data_retention_failure_group_item
                   SET item_status = 'resolved', resolved_at = ?, resolved_by = ?,
                       resolution_reason = ?, updated_at = ?
                 WHERE group_id = ? AND domain = ? AND item_status = 'active'
                """, timestamp(eventTime), resolvedBy, safeFailureText(reason, 500), timestamp(clock.now()),
                context.activeFailureGroupId(), WEBSITE_DOMAIN);
        if (itemChanged != context.articleIds().size()) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website active failure group item count changed");
        }
        int keys = jdbc.update("DELETE FROM data_retention_failure_active_key WHERE group_id = ? AND domain = ?",
                context.activeFailureGroupId(), WEBSITE_DOMAIN);
        if (keys != context.articleIds().size()) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website active failure keys changed");
        }
        requireOne(jdbc.update("""
                UPDATE data_retention_failure_group
                   SET status = 'resolved', closed_at = ?, closed_by = ?, close_reason = ?, updated_at = ?
                 WHERE id = ? AND domain = ? AND operation_id = ? AND status = 'active'
                """, timestamp(eventTime), resolvedBy, safeFailureText(reason, 500), timestamp(clock.now()),
                context.activeFailureGroupId(), WEBSITE_DOMAIN, context.operation().operationId()),
                DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                "website active failure group cannot be resolved");
    }

    private TrustedTerminalUsageReceipt openOrRefreshWebsiteLeafFailureLocked(
            WebsiteLeafFailureCommand command) {
        versionProvider.currentVersion();
        RetentionOperationContext context = Objects.requireNonNull(command.operation(), "operation");
        OperationRow operation = lockOperation(context.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, context);
        requireRelation(operation.operationId(), command.runId(), operation.domain());
        if (!WEBSITE_DOMAIN.equals(operation.domain()) || operation.itemCount() < 1 || operation.itemCount() > 5
                || !"pending".equals(operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website leaf operation is not eligible for quarantine");
        }
        Long groupId = lockActiveWebsiteFailureGroup(operation);
        AttemptRow attempt = lockAttemptAfterOperation(command.attemptId(), operation.operationId());
        if (attempt.runId() != command.runId() || !"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "website leaf attempt is not running");
        }
        List<Long> articleIds = lockWebsiteItems(operation);
        validateActiveWebsiteFailureGroup(groupId, articleIds);
        Integer markerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_retention_batch_commit WHERE operation_id = ? FOR UPDATE",
                Integer.class, operation.operationId());
        if (!Integer.valueOf(0).equals(markerCount)) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "committed website operation cannot be quarantined");
        }
        Instant eventTime = eventTime(command.failedAt());
        String failureCode = requireFailureToken(command.failureCode());
        String failureText = safeFailureText(command.errorMessage(), 2000);
        if (groupId == null) {
            KeyHolder groupKeys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO data_retention_failure_group (
                          domain, source_run_group_id, operation_id, status, item_count,
                          failure_code, last_error, opened_at, created_at, updated_at
                        ) VALUES (?, ?, ?, 'active', ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, WEBSITE_DOMAIN);
                statement.setLong(2, command.runId());
                statement.setString(3, operation.operationId());
                statement.setInt(4, operation.itemCount());
                statement.setString(5, failureCode);
                statement.setString(6, failureText);
                statement.setObject(7, timestamp(eventTime));
                statement.setObject(8, timestamp(clock.now()));
                statement.setObject(9, timestamp(clock.now()));
                return statement;
            }, groupKeys);
            Number createdGroupId = groupKeys.getKey();
            if (createdGroupId == null) {
                throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                        "website failure group identity was not returned");
            }
            groupId = createdGroupId.longValue();
            int sequence = 0;
            for (Long articleId : articleIds) {
                KeyHolder itemKeys = new GeneratedKeyHolder();
                int itemSequence = ++sequence;
                long finalGroupId = groupId;
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO data_retention_failure_group_item (
                              group_id, domain, business_key, sequence_no, item_status, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, 'active', ?, ?)
                            """, Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, finalGroupId);
                    statement.setString(2, WEBSITE_DOMAIN);
                    statement.setString(3, Long.toString(articleId));
                    statement.setInt(4, itemSequence);
                    statement.setObject(5, timestamp(clock.now()));
                    statement.setObject(6, timestamp(clock.now()));
                    return statement;
                }, itemKeys);
                Number groupItemId = itemKeys.getKey();
                if (groupItemId == null) {
                    throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                            "website failure group item identity was not returned");
                }
                jdbc.update("""
                        INSERT INTO data_retention_failure_active_key (
                          domain, business_key, group_id, group_item_id, created_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """, WEBSITE_DOMAIN, Long.toString(articleId), groupId, groupItemId.longValue(),
                        timestamp(clock.now()));
            }
        } else {
            requireOne(jdbc.update("""
                    UPDATE data_retention_failure_group
                       SET failure_code = ?, last_error = ?, updated_at = ?
                     WHERE id = ? AND operation_id = ? AND status = 'active'
                    """, failureCode, failureText, timestamp(clock.now()), groupId, operation.operationId()),
                    DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website active failure group cannot be refreshed");
        }
        ExecutionBudgetUsage usage = new ExecutionBudgetUsage(1, 0, operation.itemCount());
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("failureDisposition", "DATA_SPLIT_ELIGIBLE");
        extension.put("transientRetryCount", Math.max(0, command.transientRetryCount()));
        extension.put("executionBudgetUsage", usageMetrics(usage));
        String terminalMetrics = summaryMetrics(operation.itemCount(), 0, operation.itemCount(), 1, extension);
        completeAttemptGuarded(attempt.id(), eventTime, terminalMetrics,
                combineError(failureCode, failureText), "failed");
        requireOne(jdbc.update("""
                UPDATE data_retention_operation SET outcome_status = 'rolled_back', updated_at = ?
                 WHERE operation_id = ? AND outcome_status = 'pending'
                   AND outcome_finalized_at IS NULL AND detail_purged_at IS NULL
                """, timestamp(clock.now()), operation.operationId()),
                DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                "website leaf operation cannot be rolled back");
        String digest = terminalReceiptDigest(attempt.id(), operation.itemCount(), groupId, articleIds,
                terminalMetrics);
        return new TrustedTerminalUsageReceipt(
                TrustedTerminalUsageReceipt.WEBSITE_LEAF_QUARANTINE_V1,
                command.runId(), operation.operationId(), attempt.id(), "failed", usage, digest);
    }

    private WebsiteSplitResult finalizeWebsiteSplitParentLocked(WebsiteSplitCommand command) {
        requireCurrentWriter(command.run());
        RetentionOperationContext expected = Objects.requireNonNull(command.parentOperation(), "parent operation");
        OperationRow parent = lockOperation(expected.operationId());
        requireDetailPresent(parent);
        requireOperationContext(parent, expected);
        requireRelation(parent.operationId(), command.run().runId(), parent.domain());
        if (!WEBSITE_DOMAIN.equals(parent.domain()) || !"pending".equals(parent.outcomeStatus())
                || (parent.itemCount() != 20 && parent.itemCount() != 10)) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website split parent shape is invalid");
        }
        Long activeGroup = lockActiveWebsiteFailureGroup(parent);
        if (activeGroup != null) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website split parent already has an active failure group");
        }
        AttemptRow parentAttempt = lockAttemptAfterOperation(command.parentAttemptId(), parent.operationId());
        if (parentAttempt.runId() != command.run().runId() || !"running".equals(parentAttempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "website split parent attempt is not running");
        }
        List<Long> parentItems = lockWebsiteItems(parent);
        Integer markerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_retention_batch_commit WHERE operation_id = ? FOR UPDATE",
                Integer.class, parent.operationId());
        if (!Integer.valueOf(0).equals(markerCount)) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "committed website operation cannot be split");
        }
        int midpoint = parentItems.size() / 2;
        List<Long> leftItems = List.copyOf(parentItems.subList(0, midpoint));
        List<Long> rightItems = List.copyOf(parentItems.subList(midpoint, parentItems.size()));
        Instant eventTime = eventTime(command.failedAt());
        String failureCode = requireFailureToken(command.failureCode());
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("failureDisposition", "DATA_SPLIT_ELIGIBLE");
        extension.put("transientRetryCount", Math.max(0, command.transientRetryCount()));
        extension.put("executionBudgetUsage", usageMetrics(new ExecutionBudgetUsage(1, 0, 0)));
        completeAttemptGuarded(parentAttempt.id(), eventTime,
                summaryMetrics(0, 0, 0, 1, extension),
                combineError(failureCode, safeFailureText(command.errorMessage(), 2000)), "failed");

        RetentionOperationContext left = createWebsiteSplitChild(command.run(), parent, parentAttempt.id(),
                "left", leftItems);
        RetentionOperationContext right = createWebsiteSplitChild(command.run(), parent, parentAttempt.id(),
                "right", rightItems);
        List<Long> union = new ArrayList<>(left.websiteArticleIds());
        union.addAll(right.websiteArticleIds());
        if (!union.equals(parentItems)
                || left.websiteArticleIds().stream().anyMatch(right.websiteArticleIds()::contains)) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website split children do not form an exact parent partition");
        }
        requireOne(jdbc.update("""
                UPDATE data_retention_operation
                   SET outcome_status = 'closed_no_mutation', outcome_finalized_at = ?,
                       outcome_finalization_source = 'terminal_attempt',
                       outcome_finalization_reason = 'verified_no_mutation',
                       detail_retention_anchor_at = ?, updated_at = ?
                 WHERE operation_id = ? AND outcome_status = 'pending'
                   AND outcome_finalized_at IS NULL AND detail_retention_anchor_at IS NULL
                   AND detail_purged_at IS NULL
                """, timestamp(eventTime), timestamp(eventTime), timestamp(clock.now()), parent.operationId()),
                DataRetentionAuditErrors.OPERATION_ALREADY_FINALIZED,
                "website split parent cannot be closed");
        return new WebsiteSplitResult(List.of(left, right));
    }

    private RetentionOperationContext createWebsiteSplitChild(RetentionRunContext run,
                                                               OperationRow parent,
                                                               long parentAttemptId,
                                                               String side,
                                                               List<Long> articleIds) {
        WebsiteOperationCommand command = new WebsiteOperationCommand(
                articleIds, parent.cutoffAt(), parent.upperBoundBusinessKey());
        CanonicalValue scope = encoder.websiteScope(command, run.compatibilityContract());
        String identitySeed = parent.operationId() + ":" + parentAttemptId + ":" + side + ":1";
        String operationId = UUID.nameUUIDFromBytes(("operation:" + identitySeed)
                .getBytes(StandardCharsets.UTF_8)).toString();
        String attemptRequestId = UUID.nameUUIDFromBytes(("attempt:" + identitySeed)
                .getBytes(StandardCharsets.UTF_8)).toString();
        Instant now = clock.now();
        RetentionCompatibilityContract contract = run.compatibilityContract();
        jdbc.update("""
                INSERT INTO data_retention_operation (
                  operation_id, origin_run_group_id, domain, operation_type, batch_key_hash,
                  hash_schema_version, item_count, cutoff_at, upper_bound_business_key,
                  config_fingerprint_schema_version, config_fingerprint, policy_version,
                  created_application_version, outcome_status, created_at, updated_at
                ) VALUES (?, ?, ?, 'website_batch', ?, 1, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
                """, operationId, run.runId(), contract.domain(), scope.sha256(), articleIds.size(),
                timestamp(parent.cutoffAt()), parent.upperBoundBusinessKey(),
                contract.configFingerprintSchemaVersion(), contract.configFingerprint(), contract.policyVersion(),
                run.currentWriterVersion(), timestamp(now), timestamp(now));
        insertRelation(operationId, run.runId(), contract, RelationType.ORIGIN, now);
        OperationRow child = lockOperation(operationId);
        RetentionAttemptCommand attemptCommand = new RetentionAttemptCommand(
                attemptRequestId, AttemptType.SPLIT, null, parentAttemptId,
                Map.of("splitSide", side, "splitSchemaVersion", 1,
                        "parentOperationId", parent.operationId()));
        RetentionAttemptContext firstAttempt = createAttemptLocked(run, child, attemptCommand, true);
        int sequence = 0;
        for (Long articleId : articleIds) {
            jdbc.update("""
                    INSERT INTO data_retention_batch_item (
                      run_group_id, first_attempt_id, operation_id, domain, business_key, sequence_no, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, run.runId(), firstAttempt.attemptId(), operationId, contract.domain(), articleId,
                    ++sequence, timestamp(now));
        }
        return child.context(run.currentWriterVersion(), OperationReplayStatus.CREATED, firstAttempt, articleIds);
    }

    private List<Long> lockWebsiteItems(OperationRow operation) {
        List<Long> articleIds = jdbc.queryForList("""
                SELECT business_key FROM data_retention_batch_item
                 WHERE operation_id = ? AND run_group_id = ? AND domain = ?
                 ORDER BY sequence_no, id FOR UPDATE
                """, Long.class, operation.operationId(), operation.originRunId(), operation.domain());
        if (articleIds.size() != operation.itemCount()
                || articleIds.stream().anyMatch(id -> id == null || id <= 0)
                || articleIds.stream().distinct().count() != articleIds.size()) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website operation item tree is incomplete");
        }
        CanonicalValue rebuilt = encoder.websiteScope(
                new WebsiteOperationCommand(articleIds, operation.cutoffAt(), operation.upperBoundBusinessKey()),
                operation.contract());
        if (!Arrays.equals(rebuilt.sha256(), operation.batchKeyHash())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website operation item tree hash does not match");
        }
        return articleIds;
    }

    private List<Long> lockPollCleanupItems(OperationRow operation) {
        List<Long> ids = jdbc.queryForList("""
                SELECT business_key FROM data_retention_batch_item
                 WHERE operation_id = ? AND run_group_id = ? AND domain = ?
                 ORDER BY sequence_no, id FOR UPDATE
                """, Long.class, operation.operationId(), operation.originRunId(), operation.domain());
        validatePollCleanupItems(operation, ids);
        return ids;
    }

    private List<Long> readPollCleanupItems(OperationRow operation) {
        List<Long> ids = jdbc.queryForList("""
                SELECT business_key FROM data_retention_batch_item
                 WHERE operation_id = ? AND run_group_id = ? AND domain = ?
                 ORDER BY sequence_no, id
                """, Long.class, operation.operationId(), operation.originRunId(), operation.domain());
        validatePollCleanupItems(operation, ids);
        return ids;
    }

    private void validatePollCleanupItems(OperationRow operation, List<Long> ids) {
        if (ids.size() != operation.itemCount() || ids.isEmpty() || ids.size() > 20
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "poll cleanup operation item tree is incomplete");
        }
        String placeholders = placeholders(ids.size());
        List<String> origins = jdbc.queryForList("""
                SELECT DISTINCT origin_operation_id
                  FROM data_retention_poll_preserved_result
                 WHERE poll_result_id IN (%s)
                   AND project_id = ? AND batch_date = ? AND question_tier = ?
                """.formatted(placeholders), String.class, concatArgs(ids,
                operation.pollProjectId(), sqlDate(operation.pollBatchDate()), operation.pollQuestionTier()));
        if (origins.size() != 1) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "poll cleanup items do not belong to one origin marker");
        }
        CanonicalValue rebuilt = encoder.pollCleanupScope(new PollCleanupOperationCommand(
                origins.get(0), operation.pollProjectId(), operation.pollBatchDate(),
                operation.pollQuestionTier(), operation.pollRetentionCutoffDate(), ids), operation.contract());
        if (!Arrays.equals(rebuilt.sha256(), operation.batchKeyHash())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "poll cleanup operation item tree hash does not match");
        }
    }

    private String pollSliceMarkerState(OperationRow operation, long runId) {
        List<Map<String, Object>> markers = jdbc.queryForList("""
                SELECT operation_id, retention_run_id, domain, project_id, batch_date, question_tier, status
                  FROM data_retention_purged_slice WHERE operation_id = ?
                """, operation.operationId());
        if (markers.isEmpty()) return "absent";
        if (markers.size() != 1) return "mismatch";
        Map<String, Object> marker = markers.get(0);
        if (!(marker.get("retention_run_id") instanceof Number markerRunId)
                || !(marker.get("project_id") instanceof Number markerProjectId)) return "mismatch";
        return operation.operationId().equals(marker.get("operation_id"))
                && markerRunId.longValue() == runId
                && operation.domain().equals(marker.get("domain"))
                && Objects.equals(operation.pollProjectId(), markerProjectId.longValue())
                && Objects.equals(sqlDate(operation.pollBatchDate()), marker.get("batch_date"))
                && Objects.equals(operation.pollQuestionTier(), marker.get("question_tier"))
                && "purged".equals(marker.get("status")) ? "exact" : "mismatch";
    }

    private String pollCleanupMarkerState(OperationRow operation, List<Long> ids) {
        String placeholders = placeholders(ids.size());
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT poll_result_id, cleanup_status, cleanup_operation_id, cleaned_at
                  FROM data_retention_poll_preserved_result
                 WHERE poll_result_id IN (%s)
                 ORDER BY poll_result_id
                """.formatted(placeholders), ids.toArray());
        if (rows.size() != ids.size()) return "mismatch";
        boolean exact = true;
        boolean absent = true;
        for (int index = 0; index < ids.size(); index++) {
            Map<String, Object> row = rows.get(index);
            if (((Number) row.get("poll_result_id")).longValue() != ids.get(index)) return "mismatch";
            exact &= "cleaned".equals(row.get("cleanup_status"))
                    && operation.operationId().equals(row.get("cleanup_operation_id"))
                    && row.get("cleaned_at") != null;
            absent &= "preserved".equals(row.get("cleanup_status"))
                    && row.get("cleanup_operation_id") == null && row.get("cleaned_at") == null;
        }
        return exact ? "exact" : absent ? "absent" : "mismatch";
    }

    private boolean websiteMarkerMatches(OperationRow operation, long runId, Map<String, Object> marker) {
        return operation.operationId().equals(marker.get("operation_id"))
                && ((Number) marker.get("run_group_id")).longValue() == runId
                && operation.domain().equals(marker.get("domain"))
                && Arrays.equals(operation.batchKeyHash(), (byte[]) marker.get("batch_key_hash"))
                && ((Number) marker.get("hash_schema_version")).intValue() == operation.hashSchemaVersion()
                && ((Number) marker.get("item_count")).intValue() == operation.itemCount()
                && Objects.equals(timestamp(operation.cutoffAt()), marker.get("cutoff_at"))
                && ((Number) marker.get("upper_bound_business_key")).longValue()
                == operation.upperBoundBusinessKey()
                && ((Number) marker.get("config_fingerprint_schema_version")).intValue()
                == operation.contract().configFingerprintSchemaVersion()
                && Arrays.equals(operation.contract().configFingerprint(),
                (byte[]) marker.get("config_fingerprint"))
                && operation.contract().policyVersion().equals(marker.get("policy_version"));
    }

    private Long lockActiveWebsiteFailureGroup(OperationRow operation) {
        List<Long> groups = jdbc.queryForList("""
                SELECT id FROM data_retention_failure_group
                 WHERE operation_id = ? AND domain = ? AND status = 'active'
                 ORDER BY id FOR UPDATE
                """, Long.class, operation.operationId(), WEBSITE_DOMAIN);
        if (groups.size() > 1) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website operation has multiple active failure groups");
        }
        return groups.isEmpty() ? null : groups.get(0);
    }

    private void validateActiveWebsiteFailureGroup(Long groupId, List<Long> articleIds) {
        if (groupId == null) {
            return;
        }
        List<String> groupKeys = jdbc.queryForList("""
                SELECT business_key FROM data_retention_failure_group_item
                 WHERE group_id = ? AND domain = ? AND item_status = 'active'
                 ORDER BY sequence_no, id FOR UPDATE
                """, String.class, groupId, WEBSITE_DOMAIN);
        List<String> activeKeys = jdbc.queryForList("""
                SELECT business_key FROM data_retention_failure_active_key
                 WHERE group_id = ? AND domain = ? ORDER BY business_key FOR UPDATE
                """, String.class, groupId, WEBSITE_DOMAIN);
        List<String> expected = articleIds.stream().map(String::valueOf).toList();
        if (!groupKeys.equals(expected)
                || !activeKeys.equals(expected.stream().sorted().toList())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "website active failure group does not match operation items");
        }
    }

    private String terminalReceiptDigest(long attemptId,
                                         int itemCount,
                                         Long groupId,
                                         List<Long> articleIds,
                                         String terminalMetrics) {
        String facts = TrustedTerminalUsageReceipt.WEBSITE_LEAF_QUARANTINE_V1 + "\n"
                + attemptId + "\nfailed\n" + itemCount + "\n" + groupId + "\n"
                + articleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
                + "\n" + terminalMetrics;
        return DataRetentionCanonicalEncoder.hex(DataRetentionCanonicalEncoder.sha256(
                facts.getBytes(StandardCharsets.UTF_8)));
    }

    private void requireLeafTerminalMetrics(String metricsJson, int itemCount) {
        try {
            JsonNode metrics = REPLAY_JSON.readTree(metricsJson);
            JsonNode usage = metrics.path("executionBudgetUsage");
            if (!metrics.isObject()
                    || metrics.path("candidateCount").asLong(-1) != itemCount
                    || metrics.path("affectedCount").asLong(-1) != 0
                    || metrics.path("skippedCount").asLong(-1) != itemCount
                    || metrics.path("warningCount").asLong(-1) != 1
                    || !"DATA_SPLIT_ELIGIBLE".equals(metrics.path("failureDisposition").asText())
                    || usage.path("schemaVersion").asInt(-1) != 1
                    || usage.path("committedTransactionCount").asLong(-1) != 0
                    || usage.path("chargedCandidateItems").asLong(-1) != itemCount) {
                throw new IllegalArgumentException("terminal metrics mismatch");
            }
        } catch (Exception invalid) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "website leaf terminal metrics do not match persisted usage", invalid);
        }
    }

    private String requireFailureToken(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "website failure code is not a stable ASCII token");
        }
        return value;
    }

    private String safeFailureText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TrustedMutationReceipt commitPollMutation(PollCommitCommand command) {
        requireWritableTransaction();
        versionProvider.currentVersion();
        RetentionOperationContext context = Objects.requireNonNull(command.operation(), "operation");
        OperationRow operation = lockOperation(context.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, context);
        requireRelation(operation.operationId(), command.runId(), operation.domain());
        if (!POLL_DOMAIN.equals(operation.domain()) || !"poll_slice".equals(operation.operationType())
                || !Objects.equals(operation.pollProjectId(), command.projectId())
                || !Objects.equals(operation.pollBatchDate(), command.batchDate())
                || !Objects.equals(operation.pollQuestionTier(), normalizeTier(command.questionTier()))) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH, "poll marker tuple does not match operation");
        }
        AttemptRow attempt = lockAttemptAfterOperation(command.attemptId(), operation.operationId());
        if (attempt.runId() != command.runId()) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "poll attempt does not belong to marker run");
        }
        if (!"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "poll attempt is not active");
        }
        Instant eventTime = eventTime(command.committedAt());
        try {
            jdbc.update("""
                    INSERT INTO data_retention_purged_slice (
                      domain, project_id, batch_date, question_tier, status, purged_at,
                      retention_run_id, operation_id, metrics_json, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'purged', ?, ?, ?, ?, ?, ?)
                    """, operation.domain(), command.projectId(), Date.valueOf(command.batchDate()),
                    normalizeTier(command.questionTier()), timestamp(eventTime), command.runId(), operation.operationId(),
                    json.writeStableObject(command.metrics(), true), timestamp(clock.now()), timestamp(clock.now()));
        } catch (DuplicateKeyException duplicate) {
            throw error(DataRetentionAuditErrors.OPERATION_ALREADY_COMMITTED,
                    "poll retention slice already has a permanent marker", duplicate);
        }
        List<Long> preservedIds = command.preservedResultIds().stream().sorted().toList();
        if (preservedIds.stream().anyMatch(id -> id == null || id <= 0)
                || preservedIds.stream().distinct().count() != preservedIds.size()) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "poll preserved result ids are invalid");
        }
        for (Long pollResultId : preservedIds) {
            jdbc.update("""
                    INSERT INTO data_retention_poll_preserved_result (
                      origin_operation_id, poll_result_id, domain, project_id, batch_date,
                      question_tier, preserved_at, cleanup_status, created_at, updated_at
                    ) VALUES (?, ?, 'poll_results', ?, ?, ?, ?, 'preserved', ?, ?)
                    """, operation.operationId(), pollResultId, command.projectId(),
                    Date.valueOf(command.batchDate()), normalizeTier(command.questionTier()),
                    timestamp(eventTime), timestamp(clock.now()), timestamp(clock.now()));
        }
        finalizeCommitted(operation, "poll_marker", "marker_committed", eventTime);
        ExecutionBudgetUsage usage = new ExecutionBudgetUsage(1, 1, 1);
        Map<String, Object> trustedMetrics = new LinkedHashMap<>(command.metrics() == null ? Map.of() : command.metrics());
        trustedMetrics.put("executionBudgetUsage", usageMetrics(usage));
        completeAttemptGuarded(attempt.id(), eventTime,
                summaryMetrics(1, 1, 0, 0, trustedMetrics), null, "succeeded");
        TrustedMutationReceipt receipt = new TrustedMutationReceipt(command.runId(), operation.operationId(), attempt.id(),
                "poll_slice:" + operation.pollProjectId() + ":" + operation.pollBatchDate()
                        + ":" + operation.pollQuestionTier(), usage);
        registerReceipt(receipt);
        return receipt;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TrustedMutationReceipt commitPollCleanup(PollCleanupCommitCommand command) {
        requireWritableTransaction();
        versionProvider.currentVersion();
        RetentionOperationContext context = Objects.requireNonNull(command.operation(), "operation");
        OperationRow operation = lockOperation(context.operationId());
        requireDetailPresent(operation);
        requireOperationContext(operation, context);
        requireRelation(operation.operationId(), command.runId(), operation.domain());
        if (!POLL_DOMAIN.equals(operation.domain())
                || !"poll_preserved_cleanup".equals(operation.operationType())) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "poll cleanup commit operation type is invalid");
        }
        AttemptRow attempt = lockAttemptAfterOperation(command.attemptId(), operation.operationId());
        if (attempt.runId() != command.runId() || !"running".equals(attempt.status())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "poll cleanup attempt is not active in the requested run");
        }
        List<Long> storedIds = lockPollCleanupItems(operation);
        List<Long> commandIds = command.pollResultIds().stream().sorted().toList();
        if (!storedIds.equals(commandIds)) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "poll cleanup commit ids do not match operation items");
        }
        Instant eventTime = eventTime(command.committedAt());
        String placeholders = placeholders(storedIds.size());
        List<Object> args = new ArrayList<>();
        args.add(operation.operationId());
        args.add(timestamp(eventTime));
        args.add(timestamp(clock.now()));
        args.addAll(storedIds);
        args.add(normalizeUuid(command.originOperationId(), "poll cleanup origin operation id"));
        int updated = jdbc.update("""
                UPDATE data_retention_poll_preserved_result
                   SET cleanup_status = 'cleaned', cleanup_operation_id = ?, cleaned_at = ?, updated_at = ?
                 WHERE poll_result_id IN (%s)
                   AND origin_operation_id = ?
                   AND cleanup_status = 'preserved'
                   AND cleanup_operation_id IS NULL AND cleaned_at IS NULL
                """.formatted(placeholders), args.toArray());
        if (updated != storedIds.size()) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "poll cleanup ledger receipt changed concurrently");
        }
        finalizeCommitted(operation, "poll_cleanup", "preserved_results_cleaned", eventTime);
        ExecutionBudgetUsage usage = new ExecutionBudgetUsage(1, 1, storedIds.size());
        Map<String, Object> trustedMetrics = new LinkedHashMap<>(command.metrics());
        trustedMetrics.put("executionBudgetUsage", usageMetrics(usage));
        completeAttemptGuarded(attempt.id(), eventTime,
                summaryMetrics(storedIds.size(), storedIds.size(), 0, 0, trustedMetrics), null, "succeeded");
        TrustedMutationReceipt receipt = new TrustedMutationReceipt(command.runId(), operation.operationId(),
                attempt.id(), "poll_cleanup:" + operation.operationId(), usage);
        registerReceipt(receipt);
        return receipt;
    }

    private Map<String, Object> usageMetrics(ExecutionBudgetUsage usage) {
        return Map.of("schemaVersion", usage.schemaVersion(),
                "committedTransactionCount", usage.committedTransactionCount(),
                "chargedCandidateItems", usage.chargedCandidateItems());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateAndConsumeTrustedReceipt(TrustedMutationReceipt receipt) {
        requireWritableTransaction();
        if (receipt == null) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH, "trusted mutation receipt is missing");
        }
        Object resource = TransactionSynchronizationManager.getResource(RECEIPT_RESOURCE_KEY);
        if (!(resource instanceof ReceiptRegistry registry) || !registry.consume(receipt)) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "trusted mutation receipt was not produced by the current transaction or was already consumed");
        }
    }

    private void registerReceipt(TrustedMutationReceipt receipt) {
        requireWritableTransaction();
        ReceiptRegistry registry;
        Object existing = TransactionSynchronizationManager.getResource(RECEIPT_RESOURCE_KEY);
        if (existing instanceof ReceiptRegistry current) {
            registry = current;
        } else {
            registry = new ReceiptRegistry();
            TransactionSynchronizationManager.bindResource(RECEIPT_RESOURCE_KEY, registry);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(RECEIPT_RESOURCE_KEY);
                }
            });
        }
        registry.add(receipt);
    }

    private RetentionRunContext insertRun(ValidatedRunCommand command, String writerVersion) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO data_retention_run (
                      domain, mode, status, retention_window_start, retention_window_end,
                      candidate_count, affected_count, skipped_count, warning_count,
                      metrics_json, error_message, started_at, created_at, updated_at,
                      trigger_id, trigger_source, requested_by, request_reason, approval_ref, ticket_ref,
                      scope_json, config_fingerprint, config_fingerprint_schema_version, policy_version,
                      application_version, attempt_count, succeeded_attempt_count, failed_attempt_count,
                      skipped_attempt_count, unknown_attempt_count
                    ) VALUES (?, ?, 'running', ?, ?, 0, 0, 0, 0, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setString(index++, command.contract().domain());
            statement.setString(index++, command.mode());
            setDate(statement, index++, command.retentionWindowStart());
            setDate(statement, index++, command.retentionWindowEnd());
            statement.setObject(index++, timestamp(command.startedAt()));
            statement.setObject(index++, timestamp(clock.now()));
            statement.setObject(index++, timestamp(clock.now()));
            statement.setString(index++, command.triggerId());
            statement.setString(index++, command.triggerSource());
            statement.setLong(index++, command.requestedBy());
            statement.setString(index++, command.requestReason());
            setNullableString(statement, index++, command.approvalRef());
            setNullableString(statement, index++, command.ticketRef());
            statement.setString(index++, command.scopeJson());
            statement.setBytes(index++, command.contract().configFingerprint());
            statement.setInt(index++, command.contract().configFingerprintSchemaVersion());
            statement.setString(index++, command.contract().policyVersion());
            statement.setString(index, writerVersion);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION, "failed to read created retention run id");
        }
        return new RetentionRunContext(id.longValue(), command.triggerId(), command.triggerSource(), command.mode(),
                command.contract(), writerVersion, writerVersion, RunReplayStatus.CREATED, "running", command.startedAt());
    }

    private RetentionRunContext replayRun(ValidatedRunCommand command, String writerVersion) {
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE trigger_id = ? AND domain = ? FOR UPDATE",
                RUN_ROW_MAPPER, command.triggerId(), command.contract().domain());
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.RUN_IDEMPOTENCY_CONFLICT, "run identity conflict did not resolve to one row");
        }
        RunRow row = rows.get(0);
        if (!row.sameRequest(command)) {
            throw error(DataRetentionAuditErrors.RUN_IDEMPOTENCY_CONFLICT, "existing run command does not match replay");
        }
        validateStoredVersion(row.applicationVersion());
        RunReplayStatus replay = "running".equals(row.status())
                ? RunReplayStatus.RUNNING_REPLAY : RunReplayStatus.TERMINAL_REPLAY;
        return row.context(writerVersion, replay);
    }

    private RetentionOperationContext createOrigin(RetentionRunContext run,
                                                    String operationType,
                                                    CanonicalValue scope,
                                                    int itemCount,
                                                    Instant cutoff,
                                                    Long upperBound,
                                                    Long pollProjectId,
                                                    LocalDate pollBatchDate,
                                                    String pollTier,
                                                    LocalDate pollCutoff,
                                                    RetentionAttemptCommand attempt,
                                                    List<Long> articleIds) {
        RunRow runRow = lockRun(run.runId());
        requireRunContext(run, runRow);
        String operationId = UUID.randomUUID().toString();
        Instant now = clock.now();
        RetentionCompatibilityContract contract = run.compatibilityContract();
        jdbc.update("""
                INSERT INTO data_retention_operation (
                  operation_id, origin_run_group_id, domain, operation_type, batch_key_hash,
                  hash_schema_version, item_count, cutoff_at, upper_bound_business_key,
                  poll_project_id, poll_batch_date, poll_question_tier, poll_retention_cutoff_date,
                  config_fingerprint_schema_version, config_fingerprint, policy_version,
                  created_application_version, outcome_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
                """, operationId, run.runId(), contract.domain(), operationType, scope.sha256(), itemCount,
                timestamp(cutoff), upperBound, pollProjectId, sqlDate(pollBatchDate), pollTier, sqlDate(pollCutoff),
                contract.configFingerprintSchemaVersion(), contract.configFingerprint(), contract.policyVersion(),
                run.currentWriterVersion(), timestamp(now), timestamp(now));
        insertRelation(operationId, run.runId(), contract, RelationType.ORIGIN, now);
        OperationRow operation = lockOperation(operationId);
        RetentionAttemptContext first = createAttemptLocked(run, operation, attempt, true);
        int sequence = 0;
        for (Long articleId : articleIds) {
            jdbc.update("""
                    INSERT INTO data_retention_batch_item (
                      run_group_id, first_attempt_id, operation_id, domain, business_key, sequence_no, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, run.runId(), first.attemptId(), operationId, contract.domain(), articleId, ++sequence,
                    timestamp(now));
        }
        Integer originCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_operation_run
                 WHERE operation_id = ? AND relation_type = 'origin' AND run_group_id = ?
                """, Integer.class, operationId, run.runId());
        if (!Integer.valueOf(1).equals(originCount)) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION, "origin relation validation failed");
        }
        return operation.context(run.currentWriterVersion(), OperationReplayStatus.CREATED, first, articleIds);
    }

    private RetentionOperationContext recoverOrigin(RetentionRunContext run,
                                                     byte[] scopeHash,
                                                     RetentionAttemptCommand firstAttempt,
                                                     String expectedOperationType,
                                                     Object operationCommand) {
        List<OperationRow> operations = jdbc.query(OPERATION_SELECT + " WHERE domain = ? AND batch_key_hash = ? FOR UPDATE",
                OPERATION_ROW_MAPPER, run.compatibilityContract().domain(), scopeHash);
        if (operations.size() != 1) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT, "operation scope conflict is ambiguous");
        }
        OperationRow operation = operations.get(0);
        RunRow runRow = lockRun(run.runId());
        if ("pending".equals(operation.outcomeStatus())) {
            requireRunContext(run, runRow);
        } else {
            requireRunIdentity(run, runRow);
        }
        if (operation.originRunId() != run.runId()) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT, "operation scope belongs to a different origin run");
        }
        if (!expectedOperationType.equals(operation.operationType())
                || !List.of("pending", "committed", "closed_no_mutation").contains(operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT,
                    "existing origin operation type or outcome cannot be replayed");
        }
        requireContract(operation.contract(), run.compatibilityContract());
        validateAttemptCommand(firstAttempt, operation, true);
        Integer originCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_operation_run
                 WHERE operation_id = ? AND run_group_id = ? AND relation_type = 'origin'
                """, Integer.class, operation.operationId(), run.runId());
        List<AttemptRow> attempts = findAttemptByRequest(operation.operationId(), run.runId(), firstAttempt.attemptRequestId(), true);
        List<BatchItemRow> items = jdbc.query("""
                SELECT run_group_id, first_attempt_id, domain, business_key, sequence_no
                  FROM data_retention_batch_item
                 WHERE operation_id = ? ORDER BY sequence_no
                """, (rs, rowNum) -> new BatchItemRow(rs.getLong("run_group_id"),
                rs.getLong("first_attempt_id"), rs.getString("domain"), rs.getLong("business_key"),
                rs.getInt("sequence_no")), operation.operationId());
        AttemptRow recoveredAttempt = attempts.size() == 1 ? attempts.get(0) : null;
        boolean commandMatches = recoveredAttempt != null && recoveredAttempt.matches(firstAttempt)
                && recoveredAttempt.attemptNo() == 1 && recoveredAttempt.runId() == run.runId()
                && operation.operationId().equals(recoveredAttempt.operationId())
                && operation.domain().equals(recoveredAttempt.domain());
        boolean scopeMatches = recoveredAttempt != null
                && originScopeMatches(operation, operationCommand, items, recoveredAttempt.id());
        if (!Integer.valueOf(1).equals(originCount) || !commandMatches || !scopeMatches) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT, "existing origin operation tree is incomplete");
        }
        OperationReplayStatus replayStatus = "pending".equals(operation.outcomeStatus())
                ? OperationReplayStatus.PREPARE_REPLAY : OperationReplayStatus.TERMINAL_REPLAY;
        return operation.context(run.currentWriterVersion(), replayStatus, recoveredAttempt.context(),
                items.stream().map(BatchItemRow::businessKey).toList());
    }

    private boolean originScopeMatches(OperationRow operation,
                                       Object operationCommand,
                                       List<BatchItemRow> storedItems,
                                       long firstAttemptId) {
        if (operation.hashSchemaVersion() != DataRetentionCanonicalEncoder.SCHEMA_VERSION) {
            return false;
        }
        for (int index = 0; index < storedItems.size(); index++) {
            BatchItemRow item = storedItems.get(index);
            if (item.runId() != operation.originRunId() || item.firstAttemptId() != firstAttemptId
                    || !operation.domain().equals(item.domain()) || item.sequenceNo() != index + 1) {
                return false;
            }
        }
        List<Long> businessKeys = storedItems.stream().map(BatchItemRow::businessKey).toList();
        CanonicalValue canonical;
        if (operationCommand instanceof WebsiteOperationCommand website) {
            if (storedItems.size() != operation.itemCount()
                    || !businessKeys.equals(website.articleIds().stream().sorted().toList())
                    || !Objects.equals(operation.cutoffAt(), clock.truncate(website.cutoff()))
                    || !Objects.equals(operation.upperBoundBusinessKey(), website.upperBoundBusinessKey())) {
                return false;
            }
            canonical = encoder.websiteScope(new WebsiteOperationCommand(businessKeys, operation.cutoffAt(),
                    operation.upperBoundBusinessKey()), operation.contract());
        } else if (operationCommand instanceof PollOperationCommand poll) {
            if (!storedItems.isEmpty() || operation.itemCount() != 1
                    || !Objects.equals(operation.pollProjectId(), poll.projectId())
                    || !Objects.equals(operation.pollBatchDate(), poll.batchDate())
                    || !Objects.equals(operation.pollQuestionTier(), normalizeTier(poll.questionTier()))
                    || !Objects.equals(operation.pollRetentionCutoffDate(), poll.retentionCutoffDate())) {
                return false;
            }
            canonical = encoder.pollScope(new PollOperationCommand(operation.pollProjectId(), operation.pollBatchDate(),
                    operation.pollQuestionTier(), operation.pollRetentionCutoffDate()), operation.contract());
        } else if (operationCommand instanceof PollCleanupOperationCommand cleanup) {
            if (storedItems.size() != operation.itemCount()
                    || !businessKeys.equals(cleanup.pollResultIds().stream().sorted().toList())
                    || !Objects.equals(operation.pollProjectId(), cleanup.projectId())
                    || !Objects.equals(operation.pollBatchDate(), cleanup.batchDate())
                    || !Objects.equals(operation.pollQuestionTier(), normalizeTier(cleanup.questionTier()))
                    || !Objects.equals(operation.pollRetentionCutoffDate(), cleanup.retentionCutoffDate())) {
                return false;
            }
            canonical = encoder.pollCleanupScope(new PollCleanupOperationCommand(
                    cleanup.originOperationId(), operation.pollProjectId(), operation.pollBatchDate(),
                    operation.pollQuestionTier(), operation.pollRetentionCutoffDate(), businessKeys),
                    operation.contract());
        } else {
            return false;
        }
        return Arrays.equals(operation.batchKeyHash(), canonical.sha256());
    }

    private void validateFreshPollCleanupOwnership(PollCleanupOperationCommand command) {
        List<Long> ids = command.pollResultIds().stream().sorted().toList();
        if (ids.isEmpty()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "poll cleanup item list is empty");
        }
        String placeholders = placeholders(ids.size());
        List<CleanupLedgerIdentity> ledger = jdbc.query("""
                SELECT origin_operation_id, poll_result_id, project_id, batch_date, question_tier, cleanup_status
                  FROM data_retention_poll_preserved_result
                 WHERE poll_result_id IN (%s)
                 ORDER BY poll_result_id
                 FOR UPDATE
                """.formatted(placeholders), (rs, rowNum) -> new CleanupLedgerIdentity(
                rs.getString("origin_operation_id"), rs.getLong("poll_result_id"),
                rs.getLong("project_id"), toLocalDate(rs.getDate("batch_date")),
                rs.getString("question_tier"), rs.getString("cleanup_status")), ids.toArray());
        if (ledger.size() != ids.size()) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT,
                    "poll cleanup ledger selection changed before ownership preparation");
        }
        String origin = normalizeUuid(command.originOperationId(), "poll cleanup origin operation id");
        String tier = normalizeTier(command.questionTier());
        for (int index = 0; index < ids.size(); index++) {
            CleanupLedgerIdentity row = ledger.get(index);
            if (row.pollResultId() != ids.get(index) || !origin.equals(row.originOperationId())
                    || row.projectId() != command.projectId()
                    || !Objects.equals(row.batchDate(), command.batchDate())
                    || !Objects.equals(normalizeTier(row.questionTier()), tier)
                    || !"preserved".equals(row.cleanupStatus())) {
                throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT,
                        "poll cleanup ledger identity or state changed before ownership preparation");
            }
        }
        List<Object> holdArgs = new ArrayList<>(ids);
        Integer holds = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM data_retention_batch_item i
                  JOIN data_retention_operation o ON o.operation_id = i.operation_id
                 WHERE i.domain = 'poll_results'
                   AND i.business_key IN (%s)
                   AND o.operation_type = 'poll_preserved_cleanup'
                   AND o.outcome_status IN ('pending','rolled_back','unknown','manual_review')
                """.formatted(placeholders), Integer.class, holdArgs.toArray());
        if (!Integer.valueOf(0).equals(holds)) {
            throw error(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT,
                    "poll cleanup result is already owned by an unresolved operation");
        }
    }

    private RetentionAttemptContext prepareRetry(RetentionRunContext run,
                                                  OperationRow operation,
                                                  RetentionAttemptCommand command) {
        List<AttemptRow> replay = findAttemptByRequest(operation.operationId(), run.runId(), command.attemptRequestId(), true);
        Integer relationCount = relationCount(operation.operationId(), run.runId(), RelationType.RETRY);
        if ("pending".equals(operation.outcomeStatus())) {
            if (relationCount == 1 && replay.size() == 1
                    && retryReplayMatches(replay.get(0), run, operation, command)) {
                return replay.get(0).context();
            }
            throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                    "pending retry operation is missing its relation or attempt");
        }
        if (!"rolled_back".equals(operation.outcomeStatus()) || relationCount != 0 || !replay.isEmpty()) {
            throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                    "retry operation is not in the complete rolled-back state");
        }
        int changed = jdbc.update("""
                UPDATE data_retention_operation
                   SET outcome_status = 'pending', updated_at = ?
                 WHERE operation_id = ? AND outcome_status = 'rolled_back' AND detail_purged_at IS NULL
                """, timestamp(clock.now()), operation.operationId());
        requireOne(changed, DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                "retry operation state changed concurrently");
        ensureRelation(operation, run, RelationType.RETRY);
        return createAttemptLocked(run, operation.withOutcome("pending"), command, false);
    }

    private boolean retryReplayMatches(AttemptRow attempt,
                                       RetentionRunContext run,
                                       OperationRow operation,
                                       RetentionAttemptCommand command) {
        Long predecessorId = jdbc.queryForObject("""
                SELECT MAX(id) FROM data_retention_batch_attempt
                 WHERE operation_id = ? AND attempt_no = ?
                """, Long.class, operation.operationId(), attempt.attemptNo() - 1);
        return attempt.matches(command) && Objects.equals(predecessorId, command.expectedLatestAttemptId())
                && "prepared".equals(attempt.status())
                && attempt.runId() == run.runId() && operation.operationId().equals(attempt.operationId())
                && operation.domain().equals(attempt.domain()) && attempt.attemptNo() >= 2
                && attempt.sequenceNo() >= 1 && attempt.startedAt() == null && attempt.finishedAt() == null;
    }

    private void validateRelatedAttemptContract(OperationRow operation,
                                                RetentionRunContext run,
                                                RelationType relationType,
                                                RetentionAttemptCommand command) {
        if (run == null || command == null || relationType == null) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "related attempt command is incomplete");
        }
        requireContract(operation.contract(), run.compatibilityContract());
        RunRow originRun = readOriginRun(operation);
        RetentionCompatibilityContract rebuilt = rebuildOriginContract(originRun);
        requireContract(operation.contract(), rebuilt);
        requireContract(run.compatibilityContract(), rebuilt);
        RunRow currentRun = validateRunEffectiveConfig(run.runId(), rebuilt);
        if (relationType == RelationType.RETRY) {
            if (!"retry".equals(currentRun.triggerSource()) || command.attemptType() != AttemptType.RETRY
                    || !List.of("rolled_back", "pending").contains(operation.outcomeStatus())) {
                throw error(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT,
                        "retry run, attempt type or operation outcome is invalid");
            }
            return;
        }
        if (relationType != RelationType.RECONCILE || !"reconcile".equals(currentRun.triggerSource())
                || command.attemptType() != AttemptType.RECONCILE
                || !List.of("unknown", "manual_review").contains(operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "reconcile run, attempt type or operation outcome is invalid");
        }
    }

    private RunRow validateRunEffectiveConfig(long runId, RetentionCompatibilityContract contract) {
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE id = ?", RUN_ROW_MAPPER, runId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED, "related run is missing");
        }
        requireContract(rebuildOriginContract(rows.get(0)), contract);
        return rows.get(0);
    }

    private RunRow readOriginRun(OperationRow operation) {
        List<RunRow> origins = jdbc.query(RUN_SELECT + " WHERE id = ?", RUN_ROW_MAPPER, operation.originRunId());
        if (origins.size() != 1) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED,
                    "operation origin run is missing");
        }
        return origins.get(0);
    }

    private RetentionCompatibilityContract rebuildOriginContract(RunRow originRun) {
        try {
            JsonNode scope = REPLAY_JSON.readTree(originRun.scopeJson());
            JsonNode effectiveConfig = scope == null ? null : scope.get("effectiveConfig");
            JsonNode schemaVersion = scope == null ? null : scope.get("configFingerprintSchemaVersion");
            JsonNode fingerprint = scope == null ? null : scope.get("configFingerprint");
            if (effectiveConfig == null || !effectiveConfig.isObject() || schemaVersion == null
                    || schemaVersion.asInt(-1) != originRun.fingerprintSchemaVersion()
                    || fingerprint == null || !fingerprint.isTextual()) {
                throw new IllegalArgumentException("origin effective config envelope is incomplete");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> values = REPLAY_JSON.convertValue(effectiveConfig, Map.class);
            CanonicalValue rebuilt = encoder.fingerprintFromEffectiveConfig(originRun.domain(), values);
            String expectedHex = DataRetentionCanonicalEncoder.hex(originRun.fingerprint());
            if (!expectedHex.equals(fingerprint.asText()) || !Arrays.equals(rebuilt.sha256(), originRun.fingerprint())) {
                throw new IllegalArgumentException("origin effective config fingerprint is inconsistent");
            }
            return originRun.contract();
        } catch (Exception failure) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED,
                    "origin run effective config cannot reproduce its fingerprint", failure);
        }
    }

    private void ensureRelation(OperationRow operation, RetentionRunContext run, RelationType type) {
        int count = relationCount(operation.operationId(), run.runId(), type);
        if (count == 1) {
            return;
        }
        if (count != 0) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH, "operation relation type is inconsistent");
        }
        insertRelation(operation.operationId(), run.runId(), operation.contract(), type, clock.now());
    }

    private void insertRelation(String operationId, long runId, RetentionCompatibilityContract contract,
                                RelationType relationType, Instant linkedAt) {
        jdbc.update("""
                INSERT INTO data_retention_operation_run (
                  operation_id, run_group_id, domain, config_fingerprint_schema_version,
                  config_fingerprint, policy_version, relation_type, linked_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, operationId, runId, contract.domain(), contract.configFingerprintSchemaVersion(),
                contract.configFingerprint(), contract.policyVersion(), relationType.databaseValue(),
                timestamp(linkedAt), timestamp(clock.now()));
    }

    private RetentionAttemptContext createAttemptLocked(RetentionRunContext run,
                                                         OperationRow operation,
                                                         RetentionAttemptCommand command,
                                                         boolean originFirst) {
        validateAttemptCommand(command, operation, originFirst);
        String operationId = operation == null ? null : operation.operationId();
        List<AttemptRow> replay = findAttemptByRequest(operationId, run.runId(), command.attemptRequestId(), operation != null);
        if (!replay.isEmpty()) {
            if (replay.size() == 1 && replay.get(0).matches(command)) {
                return replay.get(0).context();
            }
            throw error(DataRetentionAuditErrors.ATTEMPT_SEQUENCE_CONFLICT, "attempt request id conflicts with existing data");
        }
        if (operation != null) {
            RunRow runRow = lockRun(run.runId());
            requireRunContext(run, runRow);
        }
        LatestAttempt latest = latestAttempt(operationId, run.runId(), operation != null);
        if (!Objects.equals(latest.id(), command.expectedLatestAttemptId())) {
            throw error(DataRetentionAuditErrors.ATTEMPT_SEQUENCE_CONFLICT, "expected latest attempt id is stale");
        }
        int sequenceNo = nextSequence(run.runId());
        int attemptNo = operation == null ? sequenceNo : latest.attemptNo() + 1;
        String scopeJson = attemptScope(command);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO data_retention_batch_attempt (
                      run_group_id, parent_attempt_id, operation_id, sequence_no, attempt_no,
                      domain, attempt_type, status, scope_json, metrics_json, last_error,
                      started_at, finished_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'prepared', ?, NULL, NULL, NULL, NULL, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, run.runId());
            setNullableLong(statement, 2, command.lineageParentAttemptId());
            setNullableString(statement, 3, operationId);
            statement.setInt(4, sequenceNo);
            statement.setInt(5, attemptNo);
            statement.setString(6, run.compatibilityContract().domain());
            statement.setString(7, command.attemptType().databaseValue());
            statement.setString(8, scopeJson);
            statement.setObject(9, timestamp(clock.now()));
            statement.setObject(10, timestamp(clock.now()));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION, "failed to read created attempt id");
        }
        return new RetentionAttemptContext(id.longValue(), run.runId(), operationId, sequenceNo, attemptNo,
                command.attemptType(), AttemptStatus.PREPARED, normalizeUuid(command.attemptRequestId(), "attempt request id"));
    }

    private void transitionAttempt(long attemptId,
                                   RetentionAttemptResult result,
                                   AttemptStatus target,
                                   Map<String, Object> metrics,
                                   String errorCode,
                                   String errorMessage,
                                   Instant requestedEventTime) {
        AttemptIdentity identity = readAttemptIdentity(attemptId);
        if (identity.operationId() != null) {
            lockOperation(identity.operationId());
        } else {
            lockRun(identity.runId());
        }
        AttemptRow attempt = lockAttempt(attemptId);
        if (target == AttemptStatus.RUNNING) {
            if ("running".equals(attempt.status())) {
                return;
            }
            requireOne(jdbc.update("""
                    UPDATE data_retention_batch_attempt
                       SET status = 'running', started_at = ?, updated_at = ?
                     WHERE id = ? AND status = 'prepared'
                    """, timestamp(clock.now()), timestamp(clock.now()), attemptId),
                    DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "attempt cannot enter running state");
            return;
        }
        String metricsJson = json.writeStableObject(metrics, true);
        String lastError = combineError(errorCode, errorMessage);
        if (isTerminal(attempt.status())) {
            Instant replayEventTime = requestedEventTime == null ? attempt.finishedAt() : eventTime(requestedEventTime);
            if (attempt.sameTerminal(target, metricsJson, lastError, replayEventTime)) {
                return;
            }
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "terminal attempt is immutable");
        }
        Instant eventTime = eventTime(requestedEventTime);
        if (!allowedTransition(attempt.status(), target)) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "attempt terminal transition is invalid");
        }
        if (identity.operationId() != null && target == AttemptStatus.SUCCEEDED) {
            OperationRow operation = readOperation(identity.operationId());
            requirePermanentMarker(identity.operationId(), identity.domain());
            if (!"committed".equals(operation.outcomeStatus())) {
                throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                        "succeeded attempt requires a committed operation outcome");
            }
        }
        completeAttemptGuarded(attemptId, eventTime, metricsJson, lastError, target.databaseValue());
        if (identity.operationId() != null) {
            updateOperationForTerminal(identity.operationId(), target, eventTime);
        }
    }

    private void finishRunLocked(long runId, RunBusinessSummary summary, String writerVersion) {
        RunRow run = lockRun(runId);
        validateStoredVersion(run.applicationVersion());
        if (!"running".equals(run.status())) {
            throw error(DataRetentionAuditErrors.RUN_ALREADY_FINISHED, "retention run is already terminal");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) total FROM data_retention_batch_attempt WHERE run_group_id = ? GROUP BY status",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        counts.put(rs.getString("status"), rs.getLong("total")), runId);
        if (counts.getOrDefault("prepared", 0L) > 0 || counts.getOrDefault("running", 0L) > 0) {
            throw error(DataRetentionAuditErrors.RUN_HAS_NON_TERMINAL_ATTEMPT,
                    "retention run contains non-terminal attempts");
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long succeeded = counts.getOrDefault("succeeded", 0L);
        long failed = counts.getOrDefault("failed", 0L);
        long skipped = counts.getOrDefault("skipped", 0L);
        long unknown = counts.getOrDefault("unknown", 0L) + counts.getOrDefault("manual_review", 0L);
        boolean auditOnly = total == 0 && isAuditOnlyRun(runId);
        String finalStatus;
        if (failed > 0 || unknown > 0) {
            finalStatus = "failed";
        } else if (succeeded > 0 || auditOnly) {
            finalStatus = "succeeded";
        } else if (total > 0 && skipped == total) {
            finalStatus = "skipped";
        } else if (total == 0 && !List.of("retry", "reconcile").contains(run.triggerSource())) {
            finalStatus = "skipped";
        } else {
            throw error(DataRetentionAuditErrors.RUN_HAS_NON_TERMINAL_ATTEMPT,
                    "zero-attempt retry or reconcile run is not complete");
        }
        RunBusinessSummary safe = summary == null ? new RunBusinessSummary(0, 0, 0, 0, Map.of()) : summary;
        requireNonNegative(safe.candidateCount(), safe.affectedCount(), safe.skippedCount(), safe.warningCount());
        Map<String, Long> proven = aggregateBusinessCounts(runId);
        if (safe.candidateCount() != proven.get("candidateCount")
                || safe.affectedCount() != proven.get("affectedCount")
                || safe.skippedCount() != proven.get("skippedCount")
                || safe.warningCount() != proven.get("warningCount")) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "run business summary is not proven by persisted attempt metrics");
        }
        int changed = jdbc.update("""
                UPDATE data_retention_run
                   SET status = ?, candidate_count = ?, affected_count = ?, skipped_count = ?, warning_count = ?,
                       metrics_json = ?, attempt_count = ?, succeeded_attempt_count = ?, failed_attempt_count = ?,
                       skipped_attempt_count = ?, unknown_attempt_count = ?, finished_at = ?,
                       attempt_rollup_completed_at = ?, updated_at = ?
                 WHERE id = ? AND status = 'running' AND finished_at IS NULL AND attempt_rollup_completed_at IS NULL
                """, finalStatus, safe.candidateCount(), safe.affectedCount(), safe.skippedCount(), safe.warningCount(),
                json.writeStableObject(safe.metrics(), true), total, succeeded, failed, skipped, unknown,
                timestamp(clock.now()), timestamp(clock.now()), timestamp(clock.now()), runId);
        requireOne(changed, DataRetentionAuditErrors.CONCURRENT_MODIFICATION, "retention run finish changed concurrently");
    }

    private Map<String, Long> aggregateBusinessCounts(long runId) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (String key : List.of("candidateCount", "affectedCount", "skippedCount", "warningCount")) {
            Long total = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(metrics_json, ?)) AS UNSIGNED)), 0)
                      FROM data_retention_batch_attempt
                     WHERE run_group_id = ?
                       AND metrics_json IS NOT NULL
                       AND JSON_TYPE(JSON_EXTRACT(metrics_json, ?)) IN ('INTEGER','UNSIGNED INTEGER')
                    """, Long.class, "$." + key, runId, "$." + key);
            Integer missing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM data_retention_batch_attempt
                     WHERE run_group_id = ?
                       AND (metrics_json IS NULL OR JSON_EXTRACT(metrics_json, ?) IS NULL
                            OR JSON_TYPE(JSON_EXTRACT(metrics_json, ?)) NOT IN ('INTEGER','UNSIGNED INTEGER'))
                    """, Integer.class, runId, "$." + key, "$." + key);
            if (!Integer.valueOf(0).equals(missing)) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                        "attempt metrics do not contain a complete typed run-summary proof");
            }
            totals.put(key, total == null ? 0L : total);
        }
        return totals;
    }

    private ReconciliationContext prepareReconciliationLocked(ReconciliationPrepareCommand command,
                                                               ValidatedEvidence evidence) {
        if (command == null || command.run() == null || command.requestedBy() <= 0
                || isBlank(command.markerState()) || isBlank(command.businessState())
                || isBlank(command.decision())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "reconciliation prepare command is incomplete");
        }
        String reconciliationId = normalizeUuid(command.reconciliationId(), "reconciliation id");
        OperationRow operation = lockOperation(normalizeUuid(command.operationId(), "operation id"));
        requireDetailPresent(operation);
        RunRow run = lockRun(command.run().runId());
        requireRunContext(command.run(), run);
        if (!"reconcile".equals(run.triggerSource())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "reconciliation must use a reconcile run");
        }
        requireContract(operation.contract(), command.run().compatibilityContract());
        requireRelation(operation.operationId(), command.run().runId(), operation.domain());
        List<ReconciliationRow> existing = findReconciliation(reconciliationId, true);
        if (!existing.isEmpty()) {
            if (existing.size() == 1 && existing.get(0).matches(command, evidence)) {
                return existing.get(0).context(command.run());
            }
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "reconciliation id conflicts with existing data");
        }
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> reconciliationInsert(connection, command, evidence, keys));
        } catch (DuplicateKeyException duplicate) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "reconciliation identity already exists", duplicate);
        } catch (DataAccessException ex) {
            throw mapEvidenceWriteFailure(ex);
        }
        List<ReconciliationRow> inserted = findReconciliation(reconciliationId, false);
        if (inserted.size() != 1) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                    "created reconciliation could not be read back");
        }
        return inserted.get(0).context(command.run());
    }

    private ReconciliationContext resolveReconciliationLocked(ReconciliationResolutionCommand command,
                                                               ValidatedEvidence evidence) {
        if (command == null || command.resolvedBy() <= 0 || isBlank(command.decision())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "reconciliation resolution command is incomplete");
        }
        OperationRow operation = lockOperation(normalizeUuid(command.operationId(), "operation id"));
        List<ReconciliationRow> rows = findReconciliation(normalizeUuid(command.reconciliationId(), "reconciliation id"), true);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "reconciliation does not exist");
        }
        ReconciliationRow row = rows.get(0);
        if (!row.operationId().equals(operation.operationId()) || !row.decision().equals(command.decision())
                || !Arrays.equals(row.evidenceHash(), evidence.sha256Digest())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "reconciliation resolution does not match prepare");
        }
        Instant resolvedAt = eventTime(command.resolvedAt());
        if ("resolved".equals(row.status())) {
            if (row.sameResolution(command, resolvedAt)) {
                return row.context(null);
            }
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "resolved reconciliation is immutable");
        }
        applyReconciliationDecision(operation, command.decision(), command.resolutionReason(), resolvedAt);
        requireOne(jdbc.update("""
                UPDATE data_retention_unknown_outcome_reconciliation
                   SET status = 'resolved', resolution_reason = ?, resolved_by = ?, resolved_at = ?, updated_at = ?
                 WHERE reconciliation_id = ? AND status = 'pending'
                """, command.resolutionReason(), command.resolvedBy(), timestamp(resolvedAt), timestamp(clock.now()),
                command.reconciliationId()), DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                "reconciliation resolution changed concurrently");
        return new ReconciliationContext(row.id(), row.reconciliationId(), row.operationId(), row.runId(),
                "resolved", row.decision(), row.evidenceHash(), null);
    }

    private ReconciliationContext auditOnlyLocked(AuditOnlyReconciliationCommand command,
                                                  ValidatedEvidence evidence,
                                                  String writerVersion) {
        if (command == null || command.requestedBy() <= 0 || command.resolvedBy() <= 0
                || isBlank(command.markerState()) || isBlank(command.businessState())
                || isBlank(command.decision()) || isBlank(command.resolutionReason())
                || command.resolvedAt() == null) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "audit-only reconciliation command is incomplete");
        }
        OperationRow operation = lockOperation(normalizeUuid(command.operationId(), "operation id"));
        if (operation.detailPurgedAt() == null || !List.of("committed", "closed_no_mutation").contains(operation.outcomeStatus())
                || !decisionMatchesOutcome(command.decision(), operation.outcomeStatus())) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "audit-only reconciliation conflicts with the final operation outcome");
        }
        ValidatedRunCommand runCommand = validateRun(command.runStart());
        requireContract(operation.contract(), runCommand.contract());
        RunRow run = findRunForUpdate(runCommand.triggerId(), runCommand.contract().domain());
        RetentionRunContext runContext;
        boolean createdRun;
        if (run == null) {
            runContext = insertRun(runCommand, writerVersion);
            createdRun = true;
        } else {
            if (!run.sameRequest(runCommand)) {
                throw error(DataRetentionAuditErrors.RUN_IDEMPOTENCY_CONFLICT, "audit-only run command conflicts");
            }
            if (!List.of("running", "succeeded").contains(run.status())) {
                throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                        "failed or skipped audit-only run cannot accept audit data");
            }
            runContext = run.context(writerVersion, "running".equals(run.status())
                    ? RunReplayStatus.RUNNING_REPLAY : RunReplayStatus.TERMINAL_REPLAY);
            createdRun = false;
        }
        List<ReconciliationRow> replay = findReconciliation(normalizeUuid(command.reconciliationId(), "reconciliation id"), true);
        int relationCount = relationCount(operation.operationId(), runContext.runId(), RelationType.RECONCILE);
        Integer attempts = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?",
                Integer.class, runContext.runId());
        if (!Integer.valueOf(0).equals(attempts)) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "audit-only run cannot contain attempts");
        }
        if (!createdRun) {
            if (relationCount == 1 && replay.size() == 1 && replay.get(0).runId() == runContext.runId()
                    && replay.get(0).matches(command, evidence, runContext.runId())
                    && ("running".equals(run.status()) || "succeeded".equals(run.status()))) {
                return replay.get(0).context(runContext);
            }
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "existing audit-only run is not a complete replay triple");
        }
        if (relationCount != 0 || !replay.isEmpty()) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "new audit-only transaction started with conflicting permanent data");
        }
        insertRelation(operation.operationId(), runContext.runId(), operation.contract(), RelationType.RECONCILE, clock.now());
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> auditOnlyInsert(connection, command, evidence, runContext, keys));
        } catch (DuplicateKeyException duplicate) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "audit-only reconciliation identity already exists", duplicate);
        } catch (DataAccessException ex) {
            throw mapEvidenceWriteFailure(ex);
        }
        List<ReconciliationRow> inserted = findReconciliation(command.reconciliationId(), false);
        if (inserted.size() != 1) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                    "created audit-only reconciliation could not be read back");
        }
        return inserted.get(0).context(runContext);
    }

    private OperationRow lockOperation(String operationId) {
        List<OperationRow> rows = jdbc.query(OPERATION_SELECT + " WHERE operation_id = ? FOR UPDATE",
                OPERATION_ROW_MAPPER, operationId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH, "retention operation does not exist");
        }
        return rows.get(0);
    }

    private OperationRow readOperation(String operationId) {
        List<OperationRow> rows = jdbc.query(OPERATION_SELECT + " WHERE operation_id = ?",
                OPERATION_ROW_MAPPER, operationId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "retention operation does not exist");
        }
        return rows.get(0);
    }

    private RunRow lockRun(long runId) {
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE id = ? FOR UPDATE", RUN_ROW_MAPPER, runId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run does not exist");
        }
        return rows.get(0);
    }

    private AttemptRow lockAttempt(long attemptId) {
        List<AttemptRow> rows = jdbc.query(ATTEMPT_SELECT + " WHERE id = ? FOR UPDATE", ATTEMPT_ROW_MAPPER, attemptId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "retention attempt does not exist");
        }
        return rows.get(0);
    }

    private AttemptRow lockAttemptAfterOperation(long attemptId, String operationId) {
        AttemptRow attempt = lockAttempt(attemptId);
        if (!operationId.equals(attempt.operationId())) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH, "attempt does not belong to operation");
        }
        return attempt;
    }

    private AttemptIdentity readAttemptIdentity(long attemptId) {
        List<AttemptIdentity> rows = jdbc.query("""
                SELECT id, run_group_id, operation_id, domain FROM data_retention_batch_attempt WHERE id = ?
                """, (rs, rowNum) -> new AttemptIdentity(rs.getLong("id"), rs.getLong("run_group_id"),
                rs.getString("operation_id"), rs.getString("domain")), attemptId);
        if (rows.size() != 1) {
            throw error(DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT, "retention attempt does not exist");
        }
        return rows.get(0);
    }

    private ValidatedRunCommand validateRun(RetentionRunStartCommand command) {
        if (command == null || command.compatibilityContract() == null || command.startedAt() == null) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run command is incomplete");
        }
        String triggerId = normalizeUuid(command.triggerId(), "trigger id");
        String source = requireToken(command.triggerSource(), SUPPORTED_SOURCES, "trigger source");
        String mode = requireToken(command.mode(), SUPPORTED_MODES, "run mode");
        if (command.requestedBy() <= 0 || command.requestReason() == null || command.requestReason().isBlank()
                || command.requestReason().length() > 500) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run requester or reason is invalid");
        }
        if ("execute".equals(mode) && List.of("manual", "canary", "retry", "reconcile").contains(source)
                && isBlank(command.approvalRef()) && isBlank(command.ticketRef())) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention execute run requires approval or ticket reference");
        }
        RetentionCompatibilityContract contract = validateContract(command.compatibilityContract());
        Map<String, Object> scope = command.scope() == null ? Map.of() : new LinkedHashMap<>(command.scope());
        validateRunScopeContract(scope, contract);
        return new ValidatedRunCommand(triggerId, source, mode, command.requestedBy(), command.requestReason().trim(),
                blankToNull(command.approvalRef()), blankToNull(command.ticketRef()), contract,
                json.writeStableObject(scope, false), command.retentionWindowStart(), command.retentionWindowEnd(),
                eventTime(command.startedAt()));
    }

    private void validateRunScopeContract(Map<String, Object> scope, RetentionCompatibilityContract contract) {
        Object effective = scope.get("effectiveConfig");
        if (!(effective instanceof Map<?, ?> rawConfig)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "run scope must contain effectiveConfig");
        }
        Map<String, Object> effectiveConfig = new LinkedHashMap<>();
        rawConfig.forEach((key, value) -> {
            if (!(key instanceof String text)) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "effectiveConfig keys must be strings");
            }
            effectiveConfig.put(text, value);
        });
        CanonicalValue rebuilt = encoder.fingerprintFromEffectiveConfig(contract.domain(), effectiveConfig);
        if (!Arrays.equals(rebuilt.sha256(), contract.configFingerprint())) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED,
                    "run effectiveConfig does not reproduce the compatibility fingerprint");
        }
        Object suppliedVersion = scope.get("configFingerprintSchemaVersion");
        if (!(suppliedVersion instanceof Number version)
                || version.intValue() != contract.configFingerprintSchemaVersion()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "run scope fingerprint schema version is missing or inconsistent");
        }
        Object suppliedHash = scope.get("configFingerprint");
        if (!DataRetentionCanonicalEncoder.hex(contract.configFingerprint()).equals(suppliedHash)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "run scope fingerprint digest is missing or inconsistent");
        }
    }

    private RetentionCompatibilityContract validateContract(RetentionCompatibilityContract contract) {
        if (!List.of(WEBSITE_DOMAIN, POLL_DOMAIN).contains(contract.domain())
                || contract.configFingerprintSchemaVersion() != 1
                || contract.configFingerprint() == null || contract.configFingerprint().length != 32) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED, "unsupported retention compatibility contract");
        }
        String expectedPolicy = WEBSITE_DOMAIN.equals(contract.domain())
                ? DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION
                : DataRetentionCanonicalEncoder.POLL_POLICY_VERSION;
        if (!expectedPolicy.equals(contract.policyVersion())) {
            throw error(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED, "unsupported retention policy version");
        }
        return contract;
    }

    private void requireRunContext(RetentionRunContext context, RunRow row) {
        requireRunIdentity(context, row);
        if (!"running".equals(row.status()) || context.replayStatus() == RunReplayStatus.TERMINAL_REPLAY) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run context is not writable");
        }
    }

    private void requireRunIdentity(RetentionRunContext context, RunRow row) {
        if (context.runId() != row.id()
                || !Objects.equals(context.triggerId(), row.triggerId())
                || !Objects.equals(context.triggerSource(), row.triggerSource())
                || !Objects.equals(context.mode(), row.mode())
                || !Objects.equals(context.status(), row.status())
                || !Objects.equals(context.runCreatedByVersion(), row.applicationVersion())) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run context is stale");
        }
        requireContract(row.contract(), context.compatibilityContract());
    }

    private void requireCurrentWriter(RetentionRunContext context) {
        String current = versionProvider.currentVersion();
        if (context == null || !current.equals(context.currentWriterVersion())) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "retention run context was created by a different service invocation");
        }
    }

    private void requireContract(RetentionCompatibilityContract stored, RetentionCompatibilityContract supplied) {
        if (stored == null || supplied == null || !Objects.equals(stored.domain(), supplied.domain())
                || stored.configFingerprintSchemaVersion() != supplied.configFingerprintSchemaVersion()
                || !Arrays.equals(stored.configFingerprint(), supplied.configFingerprint())
                || !Objects.equals(stored.policyVersion(), supplied.policyVersion())) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "retention compatibility contract does not match");
        }
    }

    private void requireOperationContext(OperationRow row, RetentionOperationContext context) {
        requireContract(row.contract(), context.compatibilityContract());
        if (context.replayStatus() == OperationReplayStatus.TERMINAL_REPLAY
                || !row.operationId().equals(context.operationId())
                || row.originRunId() != context.originRunId()
                || !row.operationType().equals(context.operationType())
                || !Arrays.equals(row.batchKeyHash(), context.batchKeyHash())
                || row.hashSchemaVersion() != context.hashSchemaVersion()
                || row.itemCount() != context.itemCount()
                || !Objects.equals(row.cutoffAt(), context.websiteCutoff())
                || !Objects.equals(row.upperBoundBusinessKey(), context.upperBoundBusinessKey())
                || !Objects.equals(row.pollProjectId(), context.pollProjectId())
                || !Objects.equals(row.pollBatchDate(), context.pollBatchDate())
                || !Objects.equals(row.pollQuestionTier(), context.pollQuestionTier())
                || !Objects.equals(row.pollRetentionCutoffDate(), context.pollRetentionCutoffDate())
                || !Objects.equals(row.createdApplicationVersion(), context.operationCreatedByVersion())) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "retention marker command does not match operation scope");
        }
    }

    private void requireDetailPresent(OperationRow operation) {
        if (operation.detailPurgedAt() != null) {
            throw error(DataRetentionAuditErrors.OPERATION_DETAIL_PURGED, "operation detail has been permanently purged");
        }
    }

    private void requireRelation(String operationId, long runId, String domain) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_operation_run
                 WHERE operation_id = ? AND run_group_id = ? AND domain = ?
                """, Integer.class, operationId, runId, domain);
        if (!Integer.valueOf(1).equals(count)) {
            throw error(DataRetentionAuditErrors.OPERATION_CONTRACT_MISMATCH,
                    "operation-run relation is missing");
        }
    }

    private int relationCount(String operationId, long runId, RelationType relationType) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_operation_run
                 WHERE operation_id = ? AND run_group_id = ? AND relation_type = ?
                """, Integer.class, operationId, runId, relationType.databaseValue());
        return count == null ? 0 : count;
    }

    private int nextSequence(long runId) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM data_retention_batch_attempt WHERE run_group_id = ?
                """, Integer.class, runId);
        return Objects.requireNonNull(next);
    }

    private LatestAttempt latestAttempt(String operationId, long runId, boolean operationBound) {
        List<LatestAttempt> rows;
        if (operationBound) {
            rows = jdbc.query("""
                    SELECT id, attempt_no FROM data_retention_batch_attempt
                     WHERE operation_id = ? ORDER BY attempt_no DESC LIMIT 1
                    """, (rs, rowNum) -> new LatestAttempt(rs.getLong(1), rs.getInt(2)), operationId);
        } else {
            rows = jdbc.query("""
                    SELECT id, attempt_no FROM data_retention_batch_attempt
                     WHERE run_group_id = ? AND operation_id IS NULL ORDER BY sequence_no DESC LIMIT 1
                    """, (rs, rowNum) -> new LatestAttempt(rs.getLong(1), rs.getInt(2)), runId);
        }
        return rows.isEmpty() ? new LatestAttempt(null, 0) : rows.get(0);
    }

    private List<AttemptRow> findAttemptByRequest(String operationId, long runId, String requestId, boolean operationBound) {
        String normalized = normalizeUuid(requestId, "attempt request id");
        if (operationBound) {
            return jdbc.query(ATTEMPT_SELECT + """
                     WHERE operation_id = ?
                       AND JSON_UNQUOTE(JSON_EXTRACT(scope_json, '$.audit.attemptRequestId')) = ?
                    """, ATTEMPT_ROW_MAPPER, operationId, normalized);
        }
        return jdbc.query(ATTEMPT_SELECT + """
                 WHERE run_group_id = ? AND operation_id IS NULL
                   AND JSON_UNQUOTE(JSON_EXTRACT(scope_json, '$.audit.attemptRequestId')) = ?
                """, ATTEMPT_ROW_MAPPER, runId, normalized);
    }

    private String attemptScope(RetentionAttemptCommand command) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("attemptRequestId", normalizeUuid(command.attemptRequestId(), "attempt request id"));
        audit.put("schemaVersion", 1);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("audit", audit);
        envelope.put("domainScope", command.domainScope() == null ? Map.of() : command.domainScope());
        return json.writeStableObject(envelope, false);
    }

    private void validateAttemptCommand(RetentionAttemptCommand command, OperationRow operation, boolean originFirst) {
        if (command == null || command.attemptType() == null) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt command is incomplete");
        }
        normalizeUuid(command.attemptRequestId(), "attempt request id");
        if (command.attemptType().operationRequired() != (operation != null)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt operation identity shape is invalid");
        }
        if (originFirst && command.expectedLatestAttemptId() != null) {
            throw error(DataRetentionAuditErrors.ATTEMPT_SEQUENCE_CONFLICT,
                    "origin first attempt cannot specify an expected latest attempt");
        }
    }

    private void requirePermanentMarker(String operationId, String domain) {
        Integer count;
        if (WEBSITE_DOMAIN.equals(domain)) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_batch_commit WHERE operation_id = ?",
                    Integer.class, operationId);
        } else {
            OperationRow operation = readOperation(operationId);
            if ("poll_preserved_cleanup".equals(operation.operationType())) {
                count = jdbc.queryForObject("""
                        SELECT CASE WHEN COUNT(*) = ? THEN COUNT(*) ELSE 0 END
                          FROM data_retention_poll_preserved_result
                         WHERE cleanup_operation_id = ? AND cleanup_status = 'cleaned' AND cleaned_at IS NOT NULL
                        """, Integer.class, operation.itemCount(), operationId);
                count = count != null && count == operation.itemCount() ? 1 : 0;
            } else {
                count = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_purged_slice WHERE operation_id = ?",
                        Integer.class, operationId);
            }
        }
        if (!Integer.valueOf(1).equals(count)) {
            throw error(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH,
                    "succeeded attempt requires a permanent marker");
        }
    }

    private void updateOperationForTerminal(String operationId, AttemptStatus status, Instant eventTime) {
        OperationRow operation = readOperation(operationId);
        if (status == AttemptStatus.FAILED) {
            if (List.of("unknown", "manual_review").contains(operation.outcomeStatus())) {
                return;
            }
            requireOne(jdbc.update("""
                    UPDATE data_retention_operation SET outcome_status = 'rolled_back', updated_at = ?
                     WHERE operation_id = ? AND outcome_status = 'pending' AND detail_purged_at IS NULL
                    """, timestamp(clock.now()), operationId),
                    DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "failed attempt cannot move operation to rolled_back");
        } else if (status == AttemptStatus.UNKNOWN) {
            if ("unknown".equals(operation.outcomeStatus())) {
                return;
            }
            requireOne(jdbc.update("""
                    UPDATE data_retention_operation SET outcome_status = ?, updated_at = ?
                     WHERE operation_id = ? AND outcome_status = 'pending' AND detail_purged_at IS NULL
                    """, status.databaseValue(), timestamp(clock.now()), operationId),
                    DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "unresolved attempt cannot move operation outcome");
        } else if (status == AttemptStatus.MANUAL_REVIEW) {
            if ("manual_review".equals(operation.outcomeStatus())) {
                return;
            }
            requireOne(jdbc.update("""
                    UPDATE data_retention_operation SET outcome_status = 'manual_review', updated_at = ?
                     WHERE operation_id = ? AND outcome_status IN ('pending','unknown') AND detail_purged_at IS NULL
                    """, timestamp(clock.now()), operationId),
                    DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "manual-review attempt cannot move operation outcome");
        } else if (status == AttemptStatus.SUCCEEDED) {
            requireOne(jdbc.update("""
                    UPDATE data_retention_operation
                       SET detail_retention_anchor_at = GREATEST(detail_retention_anchor_at, ?), updated_at = ?
                     WHERE operation_id = ? AND outcome_status = 'committed' AND detail_purged_at IS NULL
                    """, timestamp(eventTime), timestamp(clock.now()), operationId),
                    DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                    "succeeded attempt cannot advance the operation anchor");
        }
    }

    private void finalizeCommitted(OperationRow operation, String source, String reason, Instant eventTime) {
        int changed = jdbc.update("""
                UPDATE data_retention_operation
                   SET outcome_status = 'committed', outcome_finalized_at = ?, outcome_finalization_source = ?,
                       outcome_finalization_reason = ?, detail_retention_anchor_at = ?, updated_at = ?
                 WHERE operation_id = ? AND outcome_status = 'pending' AND outcome_finalized_at IS NULL
                   AND detail_retention_anchor_at IS NULL AND detail_purged_at IS NULL
                """, timestamp(eventTime), source, reason, timestamp(eventTime), timestamp(clock.now()),
                operation.operationId());
        requireOne(changed, DataRetentionAuditErrors.OPERATION_ALREADY_FINALIZED,
                "retention operation cannot be finalized as committed");
    }

    private void completeAttemptGuarded(long attemptId, Instant eventTime, String metricsJson,
                                        String lastError, String status) {
        int changed = jdbc.update("""
                UPDATE data_retention_batch_attempt
                   SET status = ?, metrics_json = ?, last_error = ?, finished_at = ?, updated_at = ?
                 WHERE id = ? AND status IN ('prepared','running')
                """, status, metricsJson, lastError, timestamp(eventTime), timestamp(clock.now()), attemptId);
        requireOne(changed, DataRetentionAuditErrors.ATTEMPT_STATE_CONFLICT,
                "retention attempt terminal state changed concurrently");
    }

    private void applyReconciliationDecision(OperationRow operation, String decision, String reason, Instant eventTime) {
        requireDetailPresent(operation);
        if ("committed".equals(decision)) {
            requirePermanentMarker(operation.operationId(), operation.domain());
            finalizeResolution(operation, "committed", reason, eventTime);
        } else if ("closed_no_mutation".equals(decision)) {
            finalizeResolution(operation, "closed_no_mutation", reason, eventTime);
        } else if ("safe_to_retry".equals(decision)) {
            requireOne(jdbc.update("""
                    UPDATE data_retention_operation SET outcome_status = 'rolled_back', updated_at = ?
                     WHERE operation_id = ? AND outcome_status IN ('unknown','manual_review')
                       AND outcome_finalized_at IS NULL AND detail_purged_at IS NULL
                    """, timestamp(clock.now()), operation.operationId()),
                    DataRetentionAuditErrors.OPERATION_UNRESOLVED, "operation is not eligible for safe retry");
        } else if ("manual_review".equals(decision) || "automation_stopped".equals(decision)) {
            jdbc.update("""
                    UPDATE data_retention_operation SET outcome_status = 'manual_review', updated_at = ?
                     WHERE operation_id = ? AND outcome_status IN ('unknown','manual_review') AND detail_purged_at IS NULL
                    """, timestamp(clock.now()), operation.operationId());
        } else {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, "unsupported reconciliation decision");
        }
    }

    private void finalizeResolution(OperationRow operation, String outcome, String reason, Instant eventTime) {
        if (isBlank(reason)) {
            throw error(DataRetentionAuditErrors.RECONCILIATION_CONFLICT,
                    "final reconciliation decision requires a stable reason");
        }
        requireOne(jdbc.update("""
                UPDATE data_retention_operation
                   SET outcome_status = ?, outcome_finalized_at = ?, outcome_finalization_source = 'reconciliation',
                       outcome_finalization_reason = ?, detail_retention_anchor_at = ?, updated_at = ?
                 WHERE operation_id = ? AND outcome_status IN ('unknown','manual_review')
                   AND outcome_finalized_at IS NULL AND detail_purged_at IS NULL
                """, outcome, timestamp(eventTime), reason, timestamp(eventTime), timestamp(clock.now()),
                operation.operationId()), DataRetentionAuditErrors.OPERATION_UNRESOLVED,
                "operation cannot be finalized by reconciliation");
    }

    private boolean isAuditOnlyRun(long runId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM data_retention_unknown_outcome_reconciliation r
                  JOIN data_retention_operation_run x
                    ON x.operation_id = r.operation_id AND x.run_group_id = r.run_group_id AND x.domain = r.domain
                  JOIN data_retention_operation o ON o.operation_id = r.operation_id
                 WHERE r.run_group_id = ? AND r.status = 'resolved'
                   AND x.relation_type = 'reconcile' AND o.detail_purged_at IS NOT NULL
                """, Integer.class, runId);
        return Integer.valueOf(1).equals(count);
    }

    private void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw error(DataRetentionAuditErrors.MARKER_TRANSACTION_REQUIRED,
                    "retention marker writer requires an active writable transaction");
        }
    }

    private DataRetentionAuditException mapEvidenceWriteFailure(DataAccessException failure) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof java.sql.SQLException sql && sql.getErrorCode() == 3819) {
                return error(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID,
                        "MySQL rejected the stored evidence JSON representation", failure);
            }
            cursor = cursor.getCause();
        }
        return error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                "retention reconciliation persistence failed closed", failure);
    }

    private void requireNonNegative(long... values) {
        if (Arrays.stream(values).anyMatch(value -> value < 0)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention run summary counts must be non-negative");
        }
    }

    private Map<String, Object> attemptMetrics(RetentionAttemptResult result) {
        return summaryMetricMap(result.candidateCount(), result.affectedCount(), result.skippedCount(),
                result.warningCount(), result.metrics());
    }

    private String summaryMetrics(long candidate, long affected, long skipped, long warnings,
                                  Map<String, Object> extension) {
        return json.writeStableObject(summaryMetricMap(candidate, affected, skipped, warnings, extension), false);
    }

    private Map<String, Object> summaryMetricMap(long candidate, long affected, long skipped, long warnings,
                                                  Map<String, Object> extension) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateCount", candidate);
        result.put("affectedCount", affected);
        result.put("skippedCount", skipped);
        result.put("warningCount", warnings);
        if (extension != null) {
            extension.forEach((key, value) -> {
                if (result.containsKey(key)) {
                    throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                            "attempt metrics cannot override core summary counts");
                }
                result.put(key, value);
            });
        }
        return result;
    }

    private Instant eventTime(Instant requested) {
        Instant value = clock.truncate(requested == null ? clock.now() : requested);
        if (value.isAfter(clock.now().plusSeconds(300))) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention event time exceeds future tolerance");
        }
        return value;
    }

    private String combineError(String code, String message) {
        String combined = isBlank(code) ? message : (isBlank(message) ? code : code + ": " + message);
        return json.truncateUtf8(combined, 2048);
    }

    private static boolean allowedTransition(String current, AttemptStatus target) {
        return ("prepared".equals(current) && target == AttemptStatus.SKIPPED)
                || ("running".equals(current) && List.of(AttemptStatus.SUCCEEDED, AttemptStatus.FAILED,
                AttemptStatus.UNKNOWN, AttemptStatus.MANUAL_REVIEW).contains(target));
    }

    private static boolean isTerminal(String status) {
        return List.of("succeeded", "failed", "skipped", "unknown", "manual_review").contains(status);
    }

    private static boolean decisionMatchesOutcome(String decision, String outcome) {
        return ("committed".equals(decision) && "committed".equals(outcome))
                || ("closed_no_mutation".equals(decision) && "closed_no_mutation".equals(outcome));
    }

    private static String requireToken(String value, List<String> permitted, String label) {
        if (value == null || !permitted.contains(value)) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, label + " is invalid");
        }
        return value;
    }

    private static String normalizeUuid(String value, String label) {
        if (value == null || !value.equals(value.toLowerCase(Locale.ROOT))) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, label + " must be a canonical lowercase UUID");
        }
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, label + " is invalid", ex);
        }
    }

    private static String normalizeTier(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "poll question tier is invalid");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static void validateStoredVersion(String value) {
        if (value == null || value.isBlank() || value.length() > 64
                || value.chars().anyMatch(character -> character < 0x20 || character > 0x7e)) {
            throw error(DataRetentionAuditErrors.APPLICATION_VERSION_INVALID,
                    "stored retention run application version is invalid");
        }
    }

    private static void requireOne(int changed, String code, String message) {
        if (changed != 1) {
            throw error(code, message);
        }
    }

    private static <T> T requireResult(T result) {
        if (result == null) {
            throw error(DataRetentionAuditErrors.CONCURRENT_MODIFICATION,
                    "retention audit transaction did not return a result");
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static LocalDateTime timestamp(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String placeholders(int count) {
        if (count < 1 || count > 20) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "retention item count is outside 1..20");
        }
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static Object[] concatArgs(List<Long> ids, Object... tail) {
        List<Object> args = new ArrayList<>(ids.size() + tail.length);
        args.addAll(ids);
        args.addAll(Arrays.asList(tail));
        return args.toArray();
    }

    private static DataRetentionAuditException error(String code, String message) {
        return new DataRetentionAuditException(code, message);
    }

    private static DataRetentionAuditException error(String code, String message, Throwable cause) {
        return new DataRetentionAuditException(code, message, cause);
    }

    private static final String OPERATION_COLUMNS = """
            o.operation_id, o.origin_run_group_id, o.domain, o.operation_type, o.batch_key_hash,
            o.hash_schema_version, o.item_count, o.cutoff_at, o.upper_bound_business_key,
            o.poll_project_id, o.poll_batch_date, o.poll_question_tier, o.poll_retention_cutoff_date,
            o.config_fingerprint_schema_version, o.config_fingerprint, o.policy_version,
            o.created_application_version, o.outcome_status, o.outcome_finalized_at,
            o.detail_retention_anchor_at, o.detail_purged_at
            """;
    private static final String OPERATION_SELECT = "SELECT " + OPERATION_COLUMNS + " FROM data_retention_operation o";

    private static final String ATTEMPT_SELECT = """
            SELECT id, run_group_id, parent_attempt_id, operation_id, sequence_no, attempt_no,
                   domain, attempt_type, status, scope_json, metrics_json, last_error,
                   started_at, finished_at
              FROM data_retention_batch_attempt
            """;

    private static final RowMapper<RunRow> RUN_ROW_MAPPER = (rs, rowNum) -> new RunRow(
            rs.getLong("id"), rs.getString("trigger_id"), rs.getString("trigger_source"),
            rs.getString("domain"), rs.getString("mode"), rs.getString("status"),
            nullableLong(rs, "requested_by"), rs.getString("request_reason"), rs.getString("approval_ref"),
            rs.getString("ticket_ref"), rs.getString("scope_json"),
            rs.getInt("config_fingerprint_schema_version"), rs.getBytes("config_fingerprint"),
            rs.getString("policy_version"), rs.getString("application_version"),
            toLocalDate(rs.getDate("retention_window_start")),
            toLocalDate(rs.getDate("retention_window_end")),
            toInstant(rs.getObject("started_at", LocalDateTime.class)));

    private static final RowMapper<OperationRow> OPERATION_ROW_MAPPER = (rs, rowNum) -> new OperationRow(
            rs.getString("operation_id"), rs.getLong("origin_run_group_id"), rs.getString("domain"),
            rs.getString("operation_type"), rs.getBytes("batch_key_hash"), rs.getInt("hash_schema_version"),
            rs.getInt("item_count"), toInstant(rs.getObject("cutoff_at", LocalDateTime.class)),
            nullableLong(rs, "upper_bound_business_key"), nullableLong(rs, "poll_project_id"),
            toLocalDate(rs.getDate("poll_batch_date")), rs.getString("poll_question_tier"),
            toLocalDate(rs.getDate("poll_retention_cutoff_date")),
            new RetentionCompatibilityContract(rs.getString("domain"),
                    rs.getInt("config_fingerprint_schema_version"), rs.getBytes("config_fingerprint"),
                    rs.getString("policy_version")), rs.getString("created_application_version"),
            rs.getString("outcome_status"), toInstant(rs.getObject("outcome_finalized_at", LocalDateTime.class)),
            toInstant(rs.getObject("detail_retention_anchor_at", LocalDateTime.class)),
            toInstant(rs.getObject("detail_purged_at", LocalDateTime.class)));

    private static final RowMapper<AttemptRow> ATTEMPT_ROW_MAPPER = (rs, rowNum) -> new AttemptRow(
            rs.getLong("id"), rs.getLong("run_group_id"), nullableLong(rs, "parent_attempt_id"),
            rs.getString("operation_id"), rs.getInt("sequence_no"), rs.getInt("attempt_no"),
            rs.getString("domain"), rs.getString("attempt_type"), rs.getString("status"),
            rs.getString("scope_json"), rs.getString("metrics_json"), rs.getString("last_error"),
            toInstant(rs.getObject("started_at", LocalDateTime.class)),
            toInstant(rs.getObject("finished_at", LocalDateTime.class)));

    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ValidatedRunCommand(String triggerId, String triggerSource, String mode, long requestedBy,
                                       String requestReason, String approvalRef, String ticketRef,
                                       RetentionCompatibilityContract contract, String scopeJson,
                                       LocalDate retentionWindowStart, LocalDate retentionWindowEnd,
                                       Instant startedAt) {
    }

    private record RunRow(long id, String triggerId, String triggerSource, String domain, String mode, String status,
                          Long requestedBy, String requestReason, String approvalRef, String ticketRef, String scopeJson,
                          int fingerprintSchemaVersion, byte[] fingerprint, String policyVersion,
                          String applicationVersion, LocalDate retentionWindowStart,
                          LocalDate retentionWindowEnd, Instant startedAt) {

        RetentionCompatibilityContract contract() {
            return new RetentionCompatibilityContract(domain, fingerprintSchemaVersion, fingerprint, policyVersion);
        }

        boolean sameRequest(ValidatedRunCommand command) {
            return triggerId.equals(command.triggerId()) && triggerSource.equals(command.triggerSource())
                    && domain.equals(command.contract().domain()) && mode.equals(command.mode())
                    && Objects.equals(requestedBy, command.requestedBy()) && Objects.equals(requestReason, command.requestReason())
                    && Objects.equals(approvalRef, command.approvalRef()) && Objects.equals(ticketRef, command.ticketRef())
                    && jsonSemanticallyEqual(scopeJson, command.scopeJson())
                    && Objects.equals(retentionWindowStart, command.retentionWindowStart())
                    && Objects.equals(retentionWindowEnd, command.retentionWindowEnd())
                    && fingerprintSchemaVersion == command.contract().configFingerprintSchemaVersion()
                    && Arrays.equals(fingerprint, command.contract().configFingerprint())
                    && policyVersion.equals(command.contract().policyVersion());
        }

        RetentionRunContext context(String writerVersion, RunReplayStatus replay) {
            return new RetentionRunContext(id, triggerId, triggerSource, mode, contract(), applicationVersion,
                    writerVersion, replay, status, startedAt);
        }
    }

    private record OperationRow(String operationId, long originRunId, String domain, String operationType,
                                byte[] batchKeyHash, int hashSchemaVersion, int itemCount, Instant cutoffAt,
                                Long upperBoundBusinessKey, Long pollProjectId, LocalDate pollBatchDate,
                                String pollQuestionTier, LocalDate pollRetentionCutoffDate,
                                RetentionCompatibilityContract contract, String createdApplicationVersion,
                                String outcomeStatus, Instant outcomeFinalizedAt, Instant detailRetentionAnchorAt,
                                Instant detailPurgedAt) {

        OperationRow withOutcome(String outcome) {
            return new OperationRow(operationId, originRunId, domain, operationType, batchKeyHash,
                    hashSchemaVersion, itemCount, cutoffAt, upperBoundBusinessKey, pollProjectId, pollBatchDate,
                    pollQuestionTier, pollRetentionCutoffDate, contract, createdApplicationVersion, outcome,
                    outcomeFinalizedAt, detailRetentionAnchorAt, detailPurgedAt);
        }

        RetentionOperationContext context(String currentWriter,
                                          OperationReplayStatus replayStatus,
                                          RetentionAttemptContext attempt,
                                          List<Long> ids) {
            return new RetentionOperationContext(operationId, originRunId, operationType, batchKeyHash,
                    hashSchemaVersion, itemCount, cutoffAt, upperBoundBusinessKey, pollProjectId, pollBatchDate,
                    pollQuestionTier, pollRetentionCutoffDate, contract, createdApplicationVersion, currentWriter,
                    replayStatus, outcomeStatus, attempt, ids);
        }
    }

    private record AttemptRow(long id, long runId, Long parentAttemptId, String operationId, int sequenceNo,
                              int attemptNo, String domain, String attemptType, String status, String scopeJson,
                              String metricsJson, String lastError, Instant startedAt, Instant finishedAt) {

        boolean matches(RetentionAttemptCommand command) {
            return attemptType.equals(command.attemptType().databaseValue())
                    && Objects.equals(parentAttemptId, command.lineageParentAttemptId())
                    && requestId().equals(normalizeUuid(command.attemptRequestId(), "attempt request id"))
                    && jsonSemanticallyEqual(scopeJson, attemptScopeForComparison(command));
        }

        private static String attemptScopeForComparison(RetentionAttemptCommand command) {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("attemptRequestId", normalizeUuid(command.attemptRequestId(), "attempt request id"));
            audit.put("schemaVersion", 1);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("audit", audit);
            envelope.put("domainScope", command.domainScope() == null ? Map.of() : command.domainScope());
            try {
                return REPLAY_JSON.writeValueAsString(envelope);
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw error(DataRetentionAuditErrors.CONTEXT_INVALID, "attempt scope is not JSON serializable", failure);
            }
        }

        String requestId() {
            int marker = scopeJson.indexOf("attemptRequestId");
            if (marker < 0) {
                return "";
            }
            int colon = scopeJson.indexOf(':', marker);
            int firstQuote = scopeJson.indexOf('"', colon);
            int secondQuote = scopeJson.indexOf('"', firstQuote + 1);
            return scopeJson.substring(firstQuote + 1, secondQuote);
        }

        RetentionAttemptContext context() {
            return new RetentionAttemptContext(id, runId, operationId, sequenceNo, attemptNo,
                    Arrays.stream(AttemptType.values()).filter(type -> type.databaseValue().equals(attemptType))
                            .findFirst().orElseThrow(),
                    Arrays.stream(AttemptStatus.values()).filter(value -> value.databaseValue().equals(status))
                            .findFirst().orElseThrow(), requestId());
        }

        boolean sameTerminal(AttemptStatus expected, String metrics, String error, Instant eventTime) {
            return status.equals(expected.databaseValue()) && jsonSemanticallyEqual(metricsJson, metrics)
                    && Objects.equals(lastError, error) && Objects.equals(finishedAt, eventTime);
        }
    }

    private record AttemptBudgetRow(long id, String operationId, String attemptType,
                                    String status, String metricsJson) { }

    private static final class ReceiptRegistry {
        private final java.util.Set<TrustedMutationReceipt> active =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        void add(TrustedMutationReceipt receipt) { active.add(receipt); }
        boolean consume(TrustedMutationReceipt receipt) { return active.remove(receipt); }
    }

    private record AttemptIdentity(long id, long runId, String operationId, String domain) {
    }

    private record LatestAttempt(Long id, int attemptNo) {
    }

    private record BatchItemRow(long runId, long firstAttemptId, String domain, long businessKey, int sequenceNo) {
    }

    private record CleanupLedgerIdentity(String originOperationId, long pollResultId, long projectId,
                                         LocalDate batchDate, String questionTier, String cleanupStatus) {
    }

    private record ReconciliationRow(long id, String reconciliationId, String operationId, long runId,
                                     String domain, String markerState, String businessState, String decision,
                                     String status, byte[] evidenceHash, long requestedBy, String resolutionReason,
                                     Long resolvedBy, Instant resolvedAt) {

        boolean matches(ReconciliationPrepareCommand command, ValidatedEvidence evidence) {
            return operationId.equals(command.operationId()) && runId == command.run().runId()
                    && markerState.equals(command.markerState()) && businessState.equals(command.businessState())
                    && decision.equals(command.decision()) && requestedBy == command.requestedBy()
                    && Arrays.equals(evidenceHash, evidence.sha256Digest());
        }

        boolean matches(AuditOnlyReconciliationCommand command, ValidatedEvidence evidence, long expectedRunId) {
            return operationId.equals(command.operationId()) && runId == expectedRunId
                    && markerState.equals(command.markerState())
                    && businessState.equals(command.businessState()) && decision.equals(command.decision())
                    && requestedBy == command.requestedBy() && "resolved".equals(status)
                    && Objects.equals(resolutionReason, command.resolutionReason())
                    && Objects.equals(resolvedBy, command.resolvedBy())
                    && Objects.equals(resolvedAt, command.resolvedAt().truncatedTo(ChronoUnit.MILLIS))
                    && Arrays.equals(evidenceHash, evidence.sha256Digest());
        }

        boolean sameResolution(ReconciliationResolutionCommand command, Instant eventTime) {
            return Objects.equals(resolutionReason, command.resolutionReason())
                    && Objects.equals(resolvedBy, command.resolvedBy()) && Objects.equals(resolvedAt, eventTime);
        }

        ReconciliationContext context(RetentionRunContext runContext) {
            return new ReconciliationContext(id, reconciliationId, operationId, runId, status, decision,
                    evidenceHash, runContext);
        }
    }

    private List<ReconciliationRow> findReconciliation(String reconciliationId, boolean forUpdate) {
        String sql = """
                SELECT id, reconciliation_id, operation_id, run_group_id, domain, marker_state, business_state,
                       decision, status, evidence_hash, requested_by, resolution_reason, resolved_by, resolved_at
                  FROM data_retention_unknown_outcome_reconciliation WHERE reconciliation_id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, rowNum) -> new ReconciliationRow(rs.getLong("id"),
                rs.getString("reconciliation_id"), rs.getString("operation_id"), rs.getLong("run_group_id"),
                rs.getString("domain"), rs.getString("marker_state"), rs.getString("business_state"),
                rs.getString("decision"), rs.getString("status"), rs.getBytes("evidence_hash"),
                rs.getLong("requested_by"), rs.getString("resolution_reason"),
                nullableLong(rs, "resolved_by"),
                toInstant(rs.getObject("resolved_at", LocalDateTime.class))), reconciliationId);
    }

    private PreparedStatement reconciliationInsert(java.sql.Connection connection,
                                                   ReconciliationPrepareCommand command,
                                                   ValidatedEvidence evidence,
                                                   KeyHolder ignored) throws java.sql.SQLException {
        PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO data_retention_unknown_outcome_reconciliation (
                  reconciliation_id, operation_id, run_group_id, domain, marker_state, business_state,
                  decision, status, evidence_hash, evidence_json, requested_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, command.reconciliationId());
        statement.setString(2, command.operationId());
        statement.setLong(3, command.run().runId());
        statement.setString(4, command.run().compatibilityContract().domain());
        statement.setString(5, command.markerState());
        statement.setString(6, command.businessState());
        statement.setString(7, command.decision());
        statement.setBytes(8, evidence.sha256Digest());
        statement.setString(9, evidence.diagnosticJson());
        statement.setLong(10, command.requestedBy());
        statement.setObject(11, timestamp(clock.now()));
        statement.setObject(12, timestamp(clock.now()));
        return statement;
    }

    private PreparedStatement auditOnlyInsert(java.sql.Connection connection,
                                              AuditOnlyReconciliationCommand command,
                                              ValidatedEvidence evidence,
                                              RetentionRunContext run,
                                              KeyHolder ignored) throws java.sql.SQLException {
        PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO data_retention_unknown_outcome_reconciliation (
                  reconciliation_id, operation_id, run_group_id, domain, marker_state, business_state,
                  decision, status, evidence_hash, evidence_json, requested_by, resolution_reason,
                  resolved_by, resolved_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'resolved', ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, command.reconciliationId());
        statement.setString(2, command.operationId());
        statement.setLong(3, run.runId());
        statement.setString(4, run.compatibilityContract().domain());
        statement.setString(5, command.markerState());
        statement.setString(6, command.businessState());
        statement.setString(7, command.decision());
        statement.setBytes(8, evidence.sha256Digest());
        statement.setString(9, evidence.diagnosticJson());
        statement.setLong(10, command.requestedBy());
        statement.setString(11, command.resolutionReason());
        statement.setLong(12, command.resolvedBy());
        statement.setObject(13, timestamp(eventTime(command.resolvedAt())));
        statement.setObject(14, timestamp(clock.now()));
        statement.setObject(15, timestamp(clock.now()));
        return statement;
    }

    private RunRow findRunForUpdate(String triggerId, String domain) {
        List<RunRow> rows = jdbc.query(RUN_SELECT + " WHERE trigger_id = ? AND domain = ? FOR UPDATE",
                RUN_ROW_MAPPER, triggerId, domain);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean jsonSemanticallyEqual(String left, String right) {
        try {
            JsonNode leftNode = left == null ? null : REPLAY_JSON.readTree(left);
            JsonNode rightNode = right == null ? null : REPLAY_JSON.readTree(right);
            return Objects.equals(leftNode, rightNode);
        } catch (Exception ex) {
            throw error(DataRetentionAuditErrors.CONTEXT_INVALID,
                    "stored retention run scope is not valid JSON", ex);
        }
    }
}
