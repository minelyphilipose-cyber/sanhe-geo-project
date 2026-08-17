package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.SweepSummary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class DataRetentionDetailCleanupRunRepository {
    private final JdbcTemplate jdbc;

    public DataRetentionDetailCleanupRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(String runId, String source, String version, Instant startedAt, Instant cutoff,
                         int maxRunSeconds) {
        List<ActiveRun> active = jdbc.query("""
                SELECT cleanup_run_id, heartbeat_at FROM data_retention_detail_cleanup_run
                 WHERE status='running' FOR UPDATE
                """, (rs, n) -> new ActiveRun(rs.getString(1),
                rs.getObject(2, LocalDateTime.class).toInstant(ZoneOffset.UTC)));
        if (!active.isEmpty()) {
            ActiveRun row = active.get(0);
            if (!row.heartbeatAt.isBefore(startedAt.minusSeconds(maxRunSeconds + 60L))) return false;
            jdbc.update("""
                    UPDATE data_retention_detail_cleanup_run
                       SET status='failed',finished_at=?,last_error_code='STALE_RUN_TAKEOVER',
                           last_error='stale running cleanup was fenced by a later sweep',heartbeat_at=?
                     WHERE cleanup_run_id=? AND status='running'
                    """, timestamp(startedAt), timestamp(startedAt), row.runId);
        }
        try {
            jdbc.update("""
                    INSERT INTO data_retention_detail_cleanup_run
                      (cleanup_run_id,trigger_source,status,application_version,retention_cutoff_at,
                       started_at,heartbeat_at)
                    VALUES (?,?,'running',?,?,?,?)
                    """, runId, source, version, timestamp(cutoff), timestamp(startedAt), timestamp(startedAt));
            return true;
        } catch (DuplicateKeyException busy) {
            return false;
        }
    }

    public void heartbeat(String runId, Instant at, SweepSummary summary) {
        int updated = jdbc.update("""
                UPDATE data_retention_detail_cleanup_run SET heartbeat_at=?,
                    candidate_operation_count=?,purged_operation_count=?,skipped_operation_count=?,
                    warning_candidate_count=?,purged_operationless_run_count=?,
                    purged_attempt_count=?,purged_item_count=?
                 WHERE cleanup_run_id=? AND status='running'
                """, timestamp(at), summary.candidateOperations(), summary.purgedOperations(),
                summary.skippedOperations(), summary.warningCandidates(),
                summary.purgedOperationlessRuns(), summary.purgedAttempts(), summary.purgedItems(), runId);
        if (updated != 1) throw new DataRetentionDetailCleanupException("CLEANUP_RUN_LOST", "cleanup run is no longer active");
    }

    public void succeed(String runId, Instant at, SweepSummary summary, long backlogAttempts,
                        long backlogItems, Instant oldestAnchor, long tableBytes, Long projectedBytes) {
        int updated = jdbc.update("""
                UPDATE data_retention_detail_cleanup_run SET status='succeeded',heartbeat_at=?,finished_at=?,
                    candidate_operation_count=?,purged_operation_count=?,skipped_operation_count=?,
                    warning_candidate_count=?,purged_operationless_run_count=?,purged_attempt_count=?,
                    purged_item_count=?,eligible_backlog_attempt_count=?,eligible_backlog_item_count=?,
                    oldest_unpurged_anchor_at=?,retention_table_bytes=?,projected_five_year_bytes=?
                 WHERE cleanup_run_id=? AND status='running'
                """, timestamp(at), timestamp(at), summary.candidateOperations(), summary.purgedOperations(),
                summary.skippedOperations(), summary.warningCandidates(), summary.purgedOperationlessRuns(),
                summary.purgedAttempts(), summary.purgedItems(), backlogAttempts, backlogItems,
                oldestAnchor == null ? null : timestamp(oldestAnchor), tableBytes, projectedBytes, runId);
        if (updated != 1) throw new DataRetentionDetailCleanupException("CLEANUP_RUN_LOST", "cleanup run finish lost ownership");
    }

    public void fail(String runId, Instant at, String code, Throwable error) {
        String detail = error == null ? code : error.getClass().getSimpleName() + ":" + safe(error.getMessage());
        jdbc.update("""
                UPDATE data_retention_detail_cleanup_run
                   SET status='failed',heartbeat_at=?,finished_at=?,last_error_code=?,last_error=?
                 WHERE cleanup_run_id=? AND status IN ('running','succeeded')
                """, timestamp(at), timestamp(at), code, detail, runId);
    }

    public RunEvidence latestSucceeded() {
        List<RunEvidence> rows = jdbc.query("""
                SELECT finished_at,eligible_backlog_attempt_count,eligible_backlog_item_count,
                       oldest_unpurged_anchor_at,retention_table_bytes,projected_five_year_bytes
                  FROM data_retention_detail_cleanup_run
                 WHERE status='succeeded' ORDER BY finished_at DESC,id DESC LIMIT 1
                """, (rs, n) -> new RunEvidence(instant(rs.getObject(1, LocalDateTime.class)),
                rs.getLong(2), rs.getLong(3), instant(rs.getObject(4, LocalDateTime.class)),
                nullableLong(rs.getObject(5)), nullableLong(rs.getObject(6))));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Instant activeHeartbeat() {
        List<LocalDateTime> rows = jdbc.queryForList("""
                SELECT heartbeat_at FROM data_retention_detail_cleanup_run
                 WHERE status='running' LIMIT 1
                """, LocalDateTime.class);
        return rows.isEmpty() ? null : instant(rows.get(0));
    }

    public CapacityWindow capacityWindow(Instant since) {
        List<CapacitySample> samples = jdbc.query("""
                SELECT finished_at,retention_table_bytes
                  FROM data_retention_detail_cleanup_run
                 WHERE status='succeeded' AND finished_at>=?
                 ORDER BY finished_at,id
                """, (rs, n) -> new CapacitySample(instant(rs.getObject(1, LocalDateTime.class)), rs.getLong(2)),
                timestamp(since));
        if (samples.size() < 2) return null;
        CapacitySample oldest=samples.get(0), latest=samples.get(samples.size()-1);
        return new CapacityWindow(oldest.at,oldest.bytes,latest.at,latest.bytes);
    }

    public CapacityPoint oldestCapacityPoint(Instant since) {
        List<CapacityPoint> rows=jdbc.query("""
                SELECT finished_at,retention_table_bytes
                  FROM data_retention_detail_cleanup_run
                 WHERE status='succeeded' AND finished_at>=?
                 ORDER BY finished_at,id LIMIT 1
                """,(rs,n)->new CapacityPoint(instant(rs.getObject(1,LocalDateTime.class)),rs.getLong(2)),timestamp(since));
        return rows.isEmpty()?null:rows.get(0);
    }

    private static String safe(String value) {
        if (value == null) return "none";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() <= 1900 ? normalized : normalized.substring(0, 1900);
    }
    private static LocalDateTime timestamp(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }

    private record ActiveRun(String runId, Instant heartbeatAt) { }
    private record CapacitySample(Instant at, long bytes) { }
    public record CapacityWindow(Instant oldestAt,long oldestBytes,Instant latestAt,long latestBytes) { }
    public record CapacityPoint(Instant at,long bytes) { }
    public record RunEvidence(Instant finishedAt, long eligibleAttempts, long eligibleItems,
                              Instant oldestAnchor, Long retentionBytes, Long projectedBytes) { }
}
