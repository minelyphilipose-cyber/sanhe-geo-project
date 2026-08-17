package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class DataRetentionDynamicSwitchChangeRepository {

    private static final String COLUMNS = """
            id, change_id, switch_key, version, enabled, status, changed_at,
            requested_by, request_source, reason, approval_ref, approved_by, approved_at,
            ticket_ref, redis_observed_version, redis_observed_enabled, last_error,
            applied_at, failed_at, created_at, updated_at
            """;
    private static final RowMapper<ChangeRow> ROW_MAPPER = DataRetentionDynamicSwitchChangeRepository::mapRow;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNew;

    public DataRetentionDynamicSwitchChangeRepository(JdbcTemplate jdbc,
                                                       PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ChangeRow createRequested(CreateCommand command, Long observedRedisVersion) {
        Objects.requireNonNull(command, "command");
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return requiresNew.execute(status -> createRequestedInTransaction(command, observedRedisVersion));
            } catch (DuplicateKeyException conflict) {
                last = conflict;
                Optional<ChangeRow> replay = findByChangeId(command.changeId());
                if (replay.isPresent()) {
                    ensureSameCommand(replay.get(), command);
                    return replay.get();
                }
            } catch (TransientDataAccessException conflict) {
                last = conflict;
            }
        }
        throw new DynamicSwitchException(503, "VERSION_ALLOCATION_CONFLICT",
                "Could not allocate dynamic switch version", last);
    }

    public Optional<ChangeRow> findByChangeId(String changeId) {
        List<ChangeRow> rows = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE change_id = ?",
                ROW_MAPPER, DataRetentionDynamicSwitchModels.canonicalChangeId(changeId));
        if (rows.size() > 1) throw inconsistent("Duplicate change_id");
        return rows.stream().findFirst();
    }

    public Optional<ChangeRow> findLatest(SwitchKey switchKey) {
        List<ChangeRow> rows = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE switch_key = ?"
                        + " ORDER BY version DESC LIMIT 1",
                ROW_MAPPER, switchKey.databaseValue());
        return rows.stream().findFirst();
    }

    public List<ChangeRow> findRequested(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("invalid limit");
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE status = 'requested'"
                        + " ORDER BY created_at, id LIMIT ?",
                ROW_MAPPER, limit);
    }

    public List<RequestedBacklog> summarizeRequested() {
        return jdbc.query("""
                SELECT switch_key, enabled, COUNT(*) AS requested_count, MIN(created_at) AS oldest_at
                  FROM data_retention_dynamic_switch_change
                 WHERE status = 'requested'
                 GROUP BY switch_key, enabled
                """, (rs, rowNum) -> new RequestedBacklog(
                SwitchKey.fromDatabaseValue(rs.getString("switch_key")), rs.getBoolean("enabled"),
                rs.getLong("requested_count"),
                rs.getObject("oldest_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
    }

    public Optional<Payload> findExpectedAppliedPayload(ChangeRow target) {
        List<ChangeRow> rows = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change"
                        + " WHERE switch_key = ? AND version < ? AND status = 'applied'"
                        + " ORDER BY version DESC LIMIT 1",
                ROW_MAPPER, target.switchKey().databaseValue(), target.version());
        return rows.stream().findFirst().map(ChangeRow::payload);
    }

    public ChangeRow approve(String changeId, long approverId, String approvalRef, Instant approvalTime) {
        return requiresNew.execute(status -> {
            ChangeRow row = findByChangeIdForUpdate(changeId);
            row.assertConsistent();
            if (row.enabled() && row.status() == ChangeStatus.APPLIED
                    && Objects.equals(row.approvalRef(), approvalRef)
                    && Objects.equals(row.approvedBy(), approverId)) {
                return row;
            }
            if (!row.enabled() || row.status() != ChangeStatus.REQUESTED) {
                throw conflict("APPROVAL_NOT_ALLOWED", "Change is not an awaiting enable request");
            }
            if (!Objects.equals(row.approvalRef(), approvalRef)) {
                throw conflict("APPROVAL_REF_MISMATCH", "approvalRef does not match the request");
            }
            if (row.requestedBy() == approverId) {
                throw new DynamicSwitchException(403, "SELF_APPROVAL_FORBIDDEN", "Requester cannot approve their own enable");
            }
            if (row.approvedBy() != null || row.approvedAt() != null) {
                if (Objects.equals(row.approvedBy(), approverId)) return row;
                throw conflict("ALREADY_APPROVED", "Enable was approved by another operator");
            }
            if (approvalTime.isBefore(row.changedAt())) {
                throw new DynamicSwitchException(503, "REDIS_TIME_ROLLBACK", "Redis time is before request time");
            }
            if (approvalTime.isAfter(row.changedAt().plusMillis(
                    DataRetentionDynamicSwitchModels.ENABLE_APPROVAL_EXPIRY_MILLIS))) {
                failGuarded(row, "approval_expired", approvalTime);
                return findByChangeIdForUpdate(changeId);
            }
            int updated = jdbc.update("""
                    UPDATE data_retention_dynamic_switch_change
                       SET approved_by = ?, approved_at = ?, last_error = NULL
                     WHERE id = ? AND status = 'requested' AND enabled = 1
                       AND approval_ref = ? AND approval_ref IS NOT NULL
                       AND approved_by IS NULL AND approved_at IS NULL
                    """, approverId, utcTimestamp(approvalTime), row.id(), approvalRef);
            if (updated != 1) throw conflict("APPROVAL_RACE", "Enable approval changed concurrently");
            return findByChangeIdForUpdate(changeId);
        });
    }

    public ChangeRow finalizeApplied(ChangeRow target, Payload observed, Instant appliedAt) {
        return finalizeTerminal(target, observed, ChangeStatus.APPLIED, null, appliedAt);
    }

    public ChangeRow finalizeFailed(ChangeRow target, Payload observed, String reason, Instant failedAt) {
        return finalizeTerminal(target, observed, ChangeStatus.FAILED, reason, failedAt);
    }

    public ChangeRow expireApprovedEnable(ChangeRow target, Payload observed, Instant failedAt) {
        return requiresNew.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE data_retention_dynamic_switch_change
                       SET status = 'failed', failed_at = ?, applied_at = NULL,
                           redis_observed_version = ?, redis_observed_enabled = ?, last_error = 'enable_not_applied'
                     WHERE id = ? AND status = 'requested' AND enabled = 1
                       AND approved_by = ? AND approved_at = ?
                    """, utcTimestamp(failedAt), observed == null ? null : observed.version(),
                    observed == null ? null : observed.enabled(), target.id(), target.approvedBy(),
                    utcTimestamp(target.approvedAt()));
            ChangeRow replay = findByChangeIdForUpdate(target.changeId());
            if (updated == 0 && replay.status() != ChangeStatus.FAILED) {
                throw conflict("TERMINAL_CONFLICT", "Approved enable changed concurrently");
            }
            return replay;
        });
    }

    public ChangeRow replaceStaleDisableAtomically(ChangeRow stale, Payload observed,
                                                   CreateCommand compensation) {
        Objects.requireNonNull(stale, "stale");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(compensation, "compensation");
        if (stale.enabled() || compensation.enabled() || stale.switchKey() != compensation.switchKey()) {
            throw new IllegalArgumentException("matching disable commands are required");
        }
        return requiresNew.execute(status -> {
            List<ChangeRow> replayRows = jdbc.query("SELECT " + COLUMNS
                            + " FROM data_retention_dynamic_switch_change WHERE change_id = ? FOR UPDATE",
                    ROW_MAPPER, compensation.changeId());
            if (!replayRows.isEmpty()) {
                ChangeRow replay = replayRows.get(0);
                ensureSameCommand(replay, compensation);
                return replay;
            }

            List<ChangeRow> latestRows = jdbc.query("SELECT " + COLUMNS
                            + " FROM data_retention_dynamic_switch_change WHERE switch_key = ?"
                            + " ORDER BY version DESC LIMIT 1 FOR UPDATE",
                    ROW_MAPPER, stale.switchKey().databaseValue());
            ChangeRow latest = latestRows.stream().findFirst()
                    .orElseThrow(() -> inconsistent("Stale disable lost its audit chain"));
            ChangeRow lockedStale = findByChangeIdForUpdate(stale.changeId());
            if (lockedStale.status() == ChangeStatus.APPLIED) {
                return lockedStale;
            }
            if (latest.status() == ChangeStatus.REQUESTED && !latest.enabled()
                    && latest.id() != lockedStale.id() && latest.version() > observed.version()) {
                failStaleIfRequested(lockedStale, observed, compensation.changedAt());
                return latest;
            }

            if (latest.status() == ChangeStatus.REQUESTED && latest.id() != lockedStale.id()) {
                if (!latest.enabled() && latest.version() <= observed.version()) {
                    failStaleIfRequested(latest, observed, compensation.changedAt());
                } else {
                    int superseded = jdbc.update("""
                            UPDATE data_retention_dynamic_switch_change
                               SET status = 'failed', failed_at = ?, last_error = 'superseded_by_disable'
                             WHERE id = ? AND status = 'requested' AND version = ?
                            """, utcTimestamp(compensation.changedAt()), latest.id(), latest.version());
                    if (superseded != 1) throw conflict("SUPERSEDE_RACE", "Latest request changed concurrently");
                }
            }
            failStaleIfRequested(lockedStale, observed, compensation.changedAt());

            long baseVersion = Math.max(Math.max(latest.version(), observed.version()), lockedStale.version());
            if (baseVersion == Long.MAX_VALUE) {
                throw new DynamicSwitchException(503, "VERSION_EXHAUSTED", "Dynamic switch version exhausted");
            }
            jdbc.update("""
                    INSERT INTO data_retention_dynamic_switch_change (
                        change_id, switch_key, version, enabled, status, changed_at,
                        requested_by, request_source, reason, approval_ref, ticket_ref,
                        redis_observed_version, redis_observed_enabled
                    ) VALUES (?, ?, ?, 0, 'requested', ?, ?, ?, ?, NULL, ?, ?, ?)
                    """, compensation.changeId(), compensation.switchKey().databaseValue(), baseVersion + 1,
                    utcTimestamp(compensation.changedAt()), compensation.requestedBy(),
                    compensation.source().databaseValue(), compensation.reason(), compensation.ticketRef(),
                    observed.version(), observed.enabled());
            return findByChangeIdForUpdate(compensation.changeId());
        });
    }

    public void recordUncertain(ChangeRow target, Payload observed, String error) {
        requiresNew.executeWithoutResult(status -> {
            int updated = jdbc.update("""
                    UPDATE data_retention_dynamic_switch_change
                       SET redis_observed_version = ?, redis_observed_enabled = ?, last_error = ?
                     WHERE id = ? AND status = 'requested'
                    """, observed == null ? null : observed.version(),
                    observed == null ? null : observed.enabled(), safeError(error), target.id());
            if (updated == 0) {
                ChangeRow replay = findByChangeIdForUpdate(target.changeId());
                if (!replay.terminal()) throw conflict("GUARDED_UPDATE_FAILED", "Uncertain-state update lost");
            }
        });
    }

    private void failStaleIfRequested(ChangeRow stale, Payload observed, Instant failedAt) {
        if (stale.status() == ChangeStatus.FAILED) return;
        int updated = jdbc.update("""
                UPDATE data_retention_dynamic_switch_change
                   SET status = 'failed', failed_at = ?, applied_at = NULL,
                       redis_observed_version = ?, redis_observed_enabled = ?, last_error = 'stale_target'
                 WHERE id = ? AND status = 'requested' AND enabled = 0 AND version = ?
                """, utcTimestamp(failedAt), observed.version(), observed.enabled(), stale.id(), stale.version());
        if (updated != 1) throw conflict("STALE_REPLACEMENT_RACE", "Stale disable changed concurrently");
    }

    private ChangeRow createRequestedInTransaction(CreateCommand command, Long observedRedisVersion) {
        List<ChangeRow> byId = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE change_id = ? FOR UPDATE",
                ROW_MAPPER, command.changeId());
        if (!byId.isEmpty()) {
            ChangeRow row = byId.get(0);
            ensureSameCommand(row, command);
            return row;
        }
        List<ChangeRow> latestRows = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE switch_key = ?"
                        + " ORDER BY version DESC LIMIT 1 FOR UPDATE",
                ROW_MAPPER, command.switchKey().databaseValue());
        ChangeRow latest = latestRows.stream().findFirst().orElse(null);
        if (latest != null) latest.assertConsistent();
        if (latest != null && latest.status() == ChangeStatus.REQUESTED) {
            if (command.enabled()) {
                throw conflict("REQUEST_IN_FLIGHT", "A dynamic switch request is already in flight");
            }
            int superseded = jdbc.update("""
                    UPDATE data_retention_dynamic_switch_change
                       SET status = 'failed', failed_at = ?, last_error = 'superseded_by_disable'
                     WHERE id = ? AND status = 'requested' AND version = ?
                    """, utcTimestamp(command.changedAt()), latest.id(), latest.version());
            if (superseded != 1) throw conflict("SUPERSEDE_RACE", "Latest request changed concurrently");
        }
        long mysqlVersion = latest == null ? 0 : latest.version();
        long redisVersion = observedRedisVersion == null ? 0 : observedRedisVersion;
        long base = Math.max(mysqlVersion, redisVersion);
        if (base == Long.MAX_VALUE) {
            throw new DynamicSwitchException(503, "VERSION_EXHAUSTED", "Dynamic switch version exhausted");
        }
        long version = base + 1;
        jdbc.update("""
                INSERT INTO data_retention_dynamic_switch_change (
                    change_id, switch_key, version, enabled, status, changed_at,
                    requested_by, request_source, reason, approval_ref, ticket_ref
                ) VALUES (?, ?, ?, ?, 'requested', ?, ?, ?, ?, ?, ?)
                """, command.changeId(), command.switchKey().databaseValue(), version, command.enabled(),
                utcTimestamp(command.changedAt()), command.requestedBy(), command.source().databaseValue(),
                command.reason(), command.approvalRef(), command.ticketRef());
        return findByChangeIdForUpdate(command.changeId());
    }

    private ChangeRow finalizeTerminal(ChangeRow target, Payload observed, ChangeStatus terminal,
                                       String error, Instant terminalAt) {
        return requiresNew.execute(status -> {
            String sql = terminal == ChangeStatus.APPLIED ? """
                    UPDATE data_retention_dynamic_switch_change
                       SET status = 'applied', applied_at = ?, failed_at = NULL,
                           redis_observed_version = ?, redis_observed_enabled = ?, last_error = NULL
                     WHERE id = ? AND status = 'requested'
                    """ : """
                    UPDATE data_retention_dynamic_switch_change
                       SET status = 'failed', failed_at = ?, applied_at = NULL,
                           redis_observed_version = ?, redis_observed_enabled = ?, last_error = ?
                     WHERE id = ? AND status = 'requested'
                    """;
            int updated;
            if (terminal == ChangeStatus.APPLIED) {
                updated = jdbc.update(sql, utcTimestamp(terminalAt), observed == null ? null : observed.version(),
                        observed == null ? null : observed.enabled(), target.id());
            } else {
                updated = jdbc.update(sql, utcTimestamp(terminalAt), observed == null ? null : observed.version(),
                        observed == null ? null : observed.enabled(), safeError(error), target.id());
            }
            ChangeRow replay = findByChangeIdForUpdate(target.changeId());
            if (updated == 0 && replay.status() != terminal) {
                throw conflict("TERMINAL_CONFLICT", "Change reached a different terminal outcome");
            }
            replay.assertConsistent();
            return replay;
        });
    }

    private void failGuarded(ChangeRow row, String reason, Instant failedAt) {
        int updated = jdbc.update("""
                UPDATE data_retention_dynamic_switch_change
                   SET status = 'failed', failed_at = ?, last_error = ?
                 WHERE id = ? AND status = 'requested' AND enabled = 1
                   AND changed_at = ? AND approved_by IS NULL AND approved_at IS NULL
                """, utcTimestamp(failedAt), safeError(reason), row.id(), utcTimestamp(row.changedAt()));
        if (updated != 1) throw conflict("APPROVAL_RACE", "Enable request changed concurrently");
    }

    private ChangeRow findByChangeIdForUpdate(String changeId) {
        List<ChangeRow> rows = jdbc.query("SELECT " + COLUMNS
                        + " FROM data_retention_dynamic_switch_change WHERE change_id = ? FOR UPDATE",
                ROW_MAPPER, DataRetentionDynamicSwitchModels.canonicalChangeId(changeId));
        if (rows.size() != 1) throw new DynamicSwitchException(404, "CHANGE_NOT_FOUND", "Dynamic switch change not found");
        return rows.get(0);
    }

    private void ensureSameCommand(ChangeRow row, CreateCommand command) {
        row.assertConsistent();
        if (row.requestedBy() != command.requestedBy()
                || row.switchKey() != command.switchKey()
                || row.enabled() != command.enabled()
                || row.source() != command.source()
                || !row.reason().equals(command.reason())
                || !Objects.equals(row.approvalRef(), command.approvalRef())
                || !Objects.equals(row.ticketRef(), command.ticketRef())) {
            throw conflict("CHANGE_ID_CONFLICT", "changeId is already bound to another command");
        }
    }

    private static ChangeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        ChangeRow row = new ChangeRow(
                rs.getLong("id"), rs.getString("change_id"), SwitchKey.fromDatabaseValue(rs.getString("switch_key")),
                rs.getLong("version"), rs.getBoolean("enabled"), ChangeStatus.fromDatabaseValue(rs.getString("status")),
                instant(rs, "changed_at"), rs.getLong("requested_by"),
                RequestSource.fromDatabaseValue(rs.getString("request_source")), rs.getString("reason"),
                rs.getString("approval_ref"), nullableLong(rs, "approved_by"), instantNullable(rs, "approved_at"),
                rs.getString("ticket_ref"), nullableLong(rs, "redis_observed_version"),
                nullableBoolean(rs, "redis_observed_enabled"), rs.getString("last_error"),
                instantNullable(rs, "applied_at"), instantNullable(rs, "failed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
        row.assertConsistent();
        return row;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        if (value == null) throw inconsistent("Missing timestamp " + column);
        return value.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static Instant instantNullable(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime utcTimestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    static String safeError(String value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        int chars = 0;
        for (int offset = 0; offset < value.length() && chars < 2000; ) {
            int codePoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            int nextBytes = next.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + nextBytes > 2048 || chars + next.length() > 2000) break;
            out.append(next);
            bytes += nextBytes;
            chars += next.length();
            offset += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private static DynamicSwitchException conflict(String code, String message) {
        return new DynamicSwitchException(409, code, message);
    }

    private static DynamicSwitchException inconsistent(String message) {
        return new DynamicSwitchException(503, "AUDIT_INCONSISTENT", message);
    }

    public record CreateCommand(String changeId, SwitchKey switchKey, boolean enabled, Instant changedAt,
                                long requestedBy, RequestSource source, String reason,
                                String approvalRef, String ticketRef) {
        public CreateCommand {
            changeId = DataRetentionDynamicSwitchModels.canonicalChangeId(changeId);
            Objects.requireNonNull(switchKey, "switchKey");
            Objects.requireNonNull(source, "source");
            changedAt = Objects.requireNonNull(changedAt, "changedAt").truncatedTo(ChronoUnit.MILLIS);
            if (requestedBy <= 0) throw new DynamicSwitchException(400, "INVALID_REQUESTER", "requestedBy must be positive");
            reason = normalizeRequired(reason, 500, "reason");
            approvalRef = normalizeOptional(approvalRef, 128, "approvalRef");
            ticketRef = normalizeOptional(ticketRef, 128, "ticketRef");
            if (enabled && approvalRef == null) throw new DynamicSwitchException(400, "APPROVAL_REF_REQUIRED", "approvalRef is required for enable");
            if (!enabled && approvalRef != null) throw new DynamicSwitchException(400, "APPROVAL_REF_FORBIDDEN", "approvalRef is forbidden for disable");
        }
    }

    public record ChangeRow(long id, String changeId, SwitchKey switchKey, long version, boolean enabled,
                            ChangeStatus status, Instant changedAt, long requestedBy, RequestSource source,
                            String reason, String approvalRef, Long approvedBy, Instant approvedAt,
                            String ticketRef, Long redisObservedVersion, Boolean redisObservedEnabled,
                            String lastError, Instant appliedAt, Instant failedAt, Instant createdAt, Instant updatedAt) {
        public Payload payload() { return new Payload(changeId, version, enabled, changedAt); }
        public boolean terminal() { return status != ChangeStatus.REQUESTED; }
        public void assertConsistent() {
            if (version < 1 || requestedBy <= 0 || reason == null || reason.isBlank()) throw inconsistent("Invalid immutable change fields");
            if ((approvedBy == null) != (approvedAt == null)) throw inconsistent("Approval fact is half populated");
            if (enabled && approvalRef == null) throw inconsistent("Enable is missing approval_ref");
            if (!enabled && (approvalRef != null || approvedBy != null)) throw inconsistent("Disable contains approval facts");
            if (approvedAt != null && approvedAt.isBefore(changedAt)) throw inconsistent("Approval predates request");
            if (approvedBy != null && approvedBy == requestedBy) throw inconsistent("Enable is self approved");
            if (status == ChangeStatus.REQUESTED && (appliedAt != null || failedAt != null)) throw inconsistent("Requested row has terminal time");
            if (status == ChangeStatus.APPLIED && (appliedAt == null || failedAt != null)) throw inconsistent("Applied row times are invalid");
            if (status == ChangeStatus.APPLIED && enabled && approvedBy == null) throw inconsistent("Applied enable lacks approval facts");
            if (status == ChangeStatus.FAILED && (failedAt == null || appliedAt != null)) throw inconsistent("Failed row times are invalid");
        }
    }

    public record RequestedBacklog(SwitchKey switchKey, boolean enabled, long count, Instant oldestAt) {
    }

    static String normalizeRequired(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new DynamicSwitchException(400, "INVALID_" + field.toUpperCase(), field + " is required and exceeds its limit");
        }
        return normalized;
    }

    static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new DynamicSwitchException(400, "INVALID_" + field.toUpperCase(), field + " exceeds its limit");
        }
        return normalized;
    }
}
