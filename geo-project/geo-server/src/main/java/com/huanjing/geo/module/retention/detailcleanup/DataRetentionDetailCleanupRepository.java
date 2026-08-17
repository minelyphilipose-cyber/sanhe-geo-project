package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.CandidateResult;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.UnknownCommitClassification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DataRetentionDetailCleanupRepository {
    private static final List<String> FINAL_RUN_STATUSES = List.of("succeeded", "failed", "skipped");
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private final JdbcTemplate jdbc;

    public DataRetentionDetailCleanupRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> findOperationCandidates(Instant cutoff, int limit, int maxAttempts) {
        return findOperationCandidateRows(cutoff,limit,maxAttempts).stream().map(OperationCandidate::operationId).toList();
    }

    public List<OperationCandidate> findOperationCandidateRows(Instant cutoff,int limit,int maxAttempts) {
        return jdbc.query("""
                SELECT o.operation_id,o.detail_retention_anchor_at
                  FROM data_retention_operation o
                 WHERE o.detail_purged_at IS NULL
                   AND o.outcome_status IN ('committed','closed_no_mutation')
                   AND o.detail_retention_anchor_at <= ?
                   AND (EXISTS (SELECT 1 FROM data_retention_batch_attempt a WHERE a.operation_id=o.operation_id)
                        OR EXISTS (SELECT 1 FROM data_retention_batch_item i WHERE i.operation_id=o.operation_id))
                 ORDER BY ((SELECT COUNT(*) FROM data_retention_batch_attempt a
                             WHERE a.operation_id=o.operation_id) > ?) ASC,
                          o.detail_retention_anchor_at, o.operation_id
                 LIMIT ?
                """,(rs,n)->new OperationCandidate(rs.getString(1),instant(rs.getObject(2,LocalDateTime.class))),
                UTC_MILLIS.format(cutoff),maxAttempts,limit);
    }

    public List<Long> findRunIdsForOperation(String operationId) {
        return jdbc.queryForList("""
                SELECT run_group_id FROM data_retention_operation_run
                 WHERE operation_id=? ORDER BY run_group_id
                """, Long.class, operationId);
    }

    public List<Long> findOperationlessRunCandidates(Instant cutoff, int limit, int maxAttempts) {
        return findOperationlessCandidateRows(cutoff,limit,maxAttempts).stream().map(OperationlessCandidate::runId).toList();
    }

    public List<OperationlessCandidate> findOperationlessCandidateRows(Instant cutoff,int limit,int maxAttempts) {
        return jdbc.query("""
                SELECT r.id,r.finished_at
                  FROM data_retention_run r
                 WHERE r.status IN ('succeeded','failed','skipped')
                   AND r.finished_at IS NOT NULL AND r.attempt_rollup_completed_at IS NOT NULL
                   AND r.finished_at <= ?
                   AND EXISTS (SELECT 1 FROM data_retention_batch_attempt a
                                WHERE a.run_group_id=r.id AND a.operation_id IS NULL)
                 ORDER BY ((SELECT COUNT(*) FROM data_retention_batch_attempt a
                             WHERE a.run_group_id=r.id AND a.operation_id IS NULL) > ?) ASC,
                          r.finished_at, r.id
                 LIMIT ?
                """,(rs,n)->new OperationlessCandidate(rs.getLong(1),instant(rs.getObject(2,LocalDateTime.class))),
                UTC_MILLIS.format(cutoff),maxAttempts,limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResult purgeOperation(String operationId, Instant cutoff, int maxAttempts) {
        return purgeOperation(null,operationId,cutoff,maxAttempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResult purgeOperation(String cleanupRunId,String operationId,Instant cutoff,int maxAttempts) {
        requireActiveCleanupRun(cleanupRunId);
        List<OperationRow> operations = jdbc.query("""
                SELECT operation_id, operation_type, item_count, outcome_status,
                       outcome_finalization_source, outcome_finalization_reason,
                       detail_retention_anchor_at, detail_purged_at, detail_purged_item_count
                  FROM data_retention_operation WHERE operation_id=? FOR UPDATE
                """, (rs, n) -> new OperationRow(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, LocalDateTime.class),
                instant(rs.getObject(8, LocalDateTime.class)), nullableLong(rs.getObject(9))), operationId);
        if (operations.size() != 1) return CandidateResult.skipped("OPERATION_MISSING");
        OperationRow operation = operations.get(0);
        if (operation.purgedAt != null) return CandidateResult.skipped("ALREADY_PURGED");
        if (operation.purgedItemCount != null || operation.anchor == null
                || operation.anchor.toInstant(ZoneOffset.UTC).isAfter(cutoff)
                || !("committed".equals(operation.outcome) || "closed_no_mutation".equals(operation.outcome))) {
            return CandidateResult.skipped("ELIGIBILITY_CHANGED");
        }

        List<RelationRow> relations = jdbc.query("""
                SELECT x.run_group_id, x.detail_purged_attempt_count,
                       r.status, r.finished_at, r.attempt_rollup_completed_at, r.attempt_count,
                       r.operationless_attempt_purged_count
                  FROM data_retention_operation_run x
                  JOIN data_retention_run r ON r.id=x.run_group_id AND r.domain=x.domain
                 WHERE x.operation_id=? ORDER BY x.run_group_id FOR UPDATE
                """, (rs, n) -> new RelationRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                instant(rs.getObject(4, LocalDateTime.class)), instant(rs.getObject(5, LocalDateTime.class)),
                rs.getLong(6), rs.getLong(7)), operationId);
        if (relations.isEmpty()) invariant("OPERATION_RELATION_MISSING", operationId);

        // Lock the ordinary recovery gates before detail rows. Any active/unresolved gate blocks purge.
        List<Long> reconciliationIds = jdbc.queryForList("""
                SELECT id FROM data_retention_unknown_outcome_reconciliation
                 WHERE operation_id=? ORDER BY id FOR UPDATE
                """, Long.class, operationId);
        List<Long> failureGroupIds = jdbc.queryForList("""
                SELECT id FROM data_retention_failure_group
                 WHERE operation_id=? ORDER BY id FOR UPDATE
                """, Long.class, operationId);
        jdbc.queryForList("""
                SELECT i.id FROM data_retention_failure_group_item i
                JOIN data_retention_failure_group g ON g.id=i.group_id AND g.domain=i.domain
                 WHERE g.operation_id=? ORDER BY i.id FOR UPDATE
                """,Long.class,operationId);
        jdbc.queryForList("""
                SELECT k.business_key FROM data_retention_failure_active_key k
                JOIN data_retention_failure_group g ON g.id=k.group_id AND g.domain=k.domain
                 WHERE g.operation_id=? ORDER BY k.business_key FOR UPDATE
                """,String.class,operationId);
        long unresolvedReconciliations = count("SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation WHERE operation_id=? AND status <> 'resolved'", operationId);
        long activeFailureGroups = count("SELECT COUNT(*) FROM data_retention_failure_group WHERE operation_id=? AND status='active'", operationId);
        if (unresolvedReconciliations != 0 || activeFailureGroups != 0) return CandidateResult.skipped("RECOVERY_ACTIVE");

        List<AttemptRow> attempts = jdbc.query("""
                SELECT id, run_group_id, status FROM data_retention_batch_attempt
                 WHERE operation_id=? ORDER BY id FOR UPDATE
                """, (rs, n) -> new AttemptRow(rs.getLong(1), rs.getLong(2), rs.getString(3)), operationId);
        List<Long> itemIds = jdbc.queryForList("""
                SELECT id FROM data_retention_batch_item WHERE operation_id=? ORDER BY id FOR UPDATE
                """, Long.class, operationId);
        if (attempts.isEmpty() && itemIds.isEmpty()) invariant("ZERO_TREE_WITHOUT_MARKER", operationId);
        if (attempts.size() > maxAttempts) return CandidateResult.warning("OPERATION_ATTEMPT_LIMIT_EXCEEDED");
        requirePhysicalItemContract(operation, itemIds.size());
        if (attempts.stream().anyMatch(a -> "prepared".equals(a.status) || "running".equals(a.status))) {
            return CandidateResult.skipped("NONTERMINAL_ATTEMPT");
        }
        requirePermanentProof(operation);

        Map<Long, Long> snapshotByRun = new LinkedHashMap<>();
        for (AttemptRow attempt : attempts) snapshotByRun.merge(attempt.runId, 1L, Long::sum);
        for (RelationRow relation : relations) {
            if (!FINAL_RUN_STATUSES.contains(relation.status) || relation.finishedAt == null || relation.rollupAt == null) {
                return CandidateResult.skipped("RUN_NOT_FINAL");
            }
            if (relation.purgedCount != 0) invariant("PURGE_COUNT_BEFORE_MARKER", operationId);
            requireRunConservation(relation);
        }

        int deletedItems = jdbc.update("DELETE FROM data_retention_batch_item WHERE operation_id=?", operationId);
        int deletedAttempts = jdbc.update("DELETE FROM data_retention_batch_attempt WHERE operation_id=?", operationId);
        if (deletedItems != itemIds.size() || deletedAttempts != attempts.size()) invariant("DELETE_COUNT_MISMATCH", operationId);
        for (RelationRow relation : relations) {
            long snapshot = snapshotByRun.getOrDefault(relation.runId, 0L);
            int updated = jdbc.update("""
                    UPDATE data_retention_operation_run SET detail_purged_attempt_count=?
                     WHERE operation_id=? AND run_group_id=? AND detail_purged_attempt_count=0
                    """, snapshot, operationId, relation.runId);
            if (updated != 1) invariant("OPERATION_RUN_PURGE_COUNT_RACE", operationId);
        }
        if (count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE operation_id=?", operationId) != 0
                || count("SELECT COUNT(*) FROM data_retention_batch_item WHERE operation_id=?", operationId) != 0) {
            invariant("POST_CHECK_DETAIL_REMAINS", operationId);
        }
        int marked = jdbc.update("""
                UPDATE data_retention_operation
                   SET detail_purged_at=UTC_TIMESTAMP(3), detail_purged_item_count=?, updated_at=UTC_TIMESTAMP(3)
                 WHERE operation_id=? AND detail_purged_at IS NULL AND detail_purged_item_count IS NULL
                   AND detail_retention_anchor_at=? AND outcome_status=?
                """, deletedItems, operationId, operation.anchor, operation.outcome);
        if (marked != 1) invariant("MARKER_GUARD_FAILED", operationId);
        for(RelationRow relation:relations) requireRunConservation(relation);
        return CandidateResult.purged(deletedAttempts, deletedItems);
    }

    /**
     * Resolves a lost commit acknowledgement without replaying DELETE first. The operation row is the
     * lifecycle marker and is locked before the rollup/detail evidence, matching the WP5/WP8 lock order.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UnknownCommitClassification classifyOperationUnknownCommit(String operationId) {
        List<OperationRow> operations = jdbc.query("""
                SELECT operation_id, operation_type, item_count, outcome_status,
                       outcome_finalization_source, outcome_finalization_reason,
                       detail_retention_anchor_at, detail_purged_at, detail_purged_item_count
                  FROM data_retention_operation WHERE operation_id=? FOR UPDATE
                """, (rs, n) -> new OperationRow(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, LocalDateTime.class),
                instant(rs.getObject(8, LocalDateTime.class)), nullableLong(rs.getObject(9))), operationId);
        if (operations.size() != 1) invariant("UNKNOWN_COMMIT_OPERATION_MISSING", operationId);
        OperationRow operation = operations.get(0);

        List<RelationRow> relations = jdbc.query("""
                SELECT x.run_group_id, x.detail_purged_attempt_count,
                       r.status, r.finished_at, r.attempt_rollup_completed_at, r.attempt_count,
                       r.operationless_attempt_purged_count
                  FROM data_retention_operation_run x
                  JOIN data_retention_run r ON r.id=x.run_group_id AND r.domain=x.domain
                 WHERE x.operation_id=? ORDER BY x.run_group_id FOR UPDATE
                """, (rs, n) -> new RelationRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                instant(rs.getObject(4, LocalDateTime.class)), instant(rs.getObject(5, LocalDateTime.class)),
                rs.getLong(6), rs.getLong(7)), operationId);
        if (relations.isEmpty()) invariant("UNKNOWN_COMMIT_RELATION_MISSING", operationId);

        List<Long> attemptIds = jdbc.queryForList("""
                SELECT id FROM data_retention_batch_attempt WHERE operation_id=? ORDER BY id FOR UPDATE
                """, Long.class, operationId);
        List<Long> itemIds = jdbc.queryForList("""
                SELECT id FROM data_retention_batch_item WHERE operation_id=? ORDER BY id FOR UPDATE
                """, Long.class, operationId);

        boolean markerPresent = operation.purgedAt != null && operation.purgedItemCount != null;
        boolean markerAbsent = operation.purgedAt == null && operation.purgedItemCount == null;
        if (markerPresent && attemptIds.isEmpty() && itemIds.isEmpty()) {
            long expectedItems = expectedPhysicalItemCount(operation);
            if (operation.anchor == null
                    || operation.purgedAt.isBefore(operation.anchor.toInstant(ZoneOffset.UTC).plus(365, java.time.temporal.ChronoUnit.DAYS))
                    || operation.purgedItemCount != expectedItems) {
                invariant("UNKNOWN_COMMIT_MARKER_MISMATCH", operationId);
            }
            long purgedAttempts = 0;
            for (RelationRow relation : relations) {
                requireRunConservation(relation);
                purgedAttempts += relation.purgedCount;
            }
            requirePermanentProof(operation);
            return UnknownCommitClassification.committed(purgedAttempts, operation.purgedItemCount);
        }
        if (markerAbsent && relations.stream().allMatch(relation -> relation.purgedCount == 0)
                && (!attemptIds.isEmpty() || !itemIds.isEmpty())) {
            requirePhysicalItemContract(operation, itemIds.size());
            for (RelationRow relation : relations) requireRunConservation(relation);
            return UnknownCommitClassification.rolledBack();
        }
        invariant("UNKNOWN_COMMIT_MIXED_STATE", operationId);
        throw new IllegalStateException("unreachable");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResult purgeOperationlessRun(long runId, Instant cutoff, int maxAttempts) {
        return purgeOperationlessRun(null,runId,cutoff,maxAttempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateResult purgeOperationlessRun(String cleanupRunId,long runId,Instant cutoff,int maxAttempts) {
        requireActiveCleanupRun(cleanupRunId);
        List<RunRow> runs = jdbc.query("""
                SELECT id,status,finished_at,attempt_rollup_completed_at,attempt_count,
                       operationless_attempt_purged_count
                  FROM data_retention_run WHERE id=? FOR UPDATE
                """, (rs, n) -> new RunRow(rs.getLong(1), rs.getString(2), instant(rs.getObject(3, LocalDateTime.class)),
                instant(rs.getObject(4, LocalDateTime.class)), rs.getLong(5), rs.getLong(6)), runId);
        if (runs.size() != 1) return CandidateResult.skipped("RUN_MISSING");
        RunRow run = runs.get(0);
        if (!FINAL_RUN_STATUSES.contains(run.status) || run.finishedAt == null || run.rollupAt == null
                || run.finishedAt.isAfter(cutoff)) return CandidateResult.skipped("RUN_ELIGIBILITY_CHANGED");
        List<AttemptRow> attempts = jdbc.query("""
                SELECT id,run_group_id,status FROM data_retention_batch_attempt
                 WHERE run_group_id=? AND operation_id IS NULL ORDER BY id FOR UPDATE
                """, (rs, n) -> new AttemptRow(rs.getLong(1), rs.getLong(2), rs.getString(3)), runId);
        if (attempts.isEmpty()) return CandidateResult.skipped("ALREADY_PURGED");
        if (attempts.size() > maxAttempts) return CandidateResult.warning("OPERATIONLESS_ATTEMPT_LIMIT_EXCEEDED");
        if (attempts.stream().anyMatch(a -> "prepared".equals(a.status) || "running".equals(a.status))) {
            return CandidateResult.skipped("NONTERMINAL_ATTEMPT");
        }
        requireRunConservation(new RelationRow(run.id, 0, run.status, run.finishedAt, run.rollupAt,
                run.attemptCount, run.operationlessPurged));
        int deleted = jdbc.update("DELETE FROM data_retention_batch_attempt WHERE run_group_id=? AND operation_id IS NULL", runId);
        if (deleted != attempts.size()) invariant("DELETE_COUNT_MISMATCH", Long.toString(runId));
        int updated = jdbc.update("""
                UPDATE data_retention_run
                   SET operationless_attempt_purged_count=operationless_attempt_purged_count+?
                 WHERE id=? AND operationless_attempt_purged_count=?
                """, deleted, runId, run.operationlessPurged);
        if (updated != 1 || count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id=? AND operation_id IS NULL", runId) != 0) {
            invariant("OPERATIONLESS_POST_CHECK_FAILED", Long.toString(runId));
        }
        requireRunConservation(new RelationRow(run.id,0,run.status,run.finishedAt,run.rollupAt,
                run.attemptCount,run.operationlessPurged+deleted));
        return CandidateResult.purged(deleted, 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean advanceRunWaterline(long runId, Instant cutoff) {
        List<RunRow> runs = jdbc.query("""
                SELECT id,status,finished_at,attempt_rollup_completed_at,attempt_count,
                       operationless_attempt_purged_count
                  FROM data_retention_run WHERE id=? FOR UPDATE
                """, (rs, n) -> new RunRow(rs.getLong(1), rs.getString(2), instant(rs.getObject(3, LocalDateTime.class)),
                instant(rs.getObject(4, LocalDateTime.class)), rs.getLong(5), rs.getLong(6)), runId);
        if (runs.size() != 1) return false;
        RunRow run = runs.get(0);
        if (!FINAL_RUN_STATUSES.contains(run.status) || run.finishedAt == null || run.rollupAt == null) return false;
        requireRunConservation(new RelationRow(run.id, 0, run.status, run.finishedAt, run.rollupAt,
                run.attemptCount, run.operationlessPurged));
        if (!run.finishedAt.isAfter(cutoff)
                && count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id=? AND operation_id IS NULL", runId) != 0) {
            return false;
        }
        EligibleBacklog operationDetail = eligibleBacklog(cutoff, runId);
        if (operationDetail.attempts != 0 || operationDetail.items != 0) return false;
        return jdbc.update("""
                UPDATE data_retention_run
                   SET attempt_detail_purged_at=GREATEST(
                       COALESCE(attempt_detail_purged_at,'1000-01-01'),UTC_TIMESTAMP(3))
                 WHERE id=?
                """, runId) == 1;
    }

    private void requirePhysicalItemContract(OperationRow operation, long physicalItemCount) {
        if (physicalItemCount != expectedPhysicalItemCount(operation)) {
            invariant("ITEM_CONTRACT_MISMATCH", operation.id);
        }
        if ("poll_preserved_cleanup".equals(operation.type) && count("""
                SELECT COUNT(*) FROM data_retention_batch_item item
                JOIN data_retention_poll_preserved_result ledger
                  ON ledger.cleanup_operation_id=item.operation_id
                 AND ledger.poll_result_id=item.business_key
                 AND ledger.cleanup_status='cleaned' AND ledger.cleaned_at IS NOT NULL
                JOIN data_retention_operation o ON o.operation_id=item.operation_id
                 WHERE item.operation_id=? AND ledger.domain=o.domain
                   AND ledger.project_id=o.poll_project_id
                   AND ledger.batch_date=o.poll_batch_date
                   AND ledger.question_tier=o.poll_question_tier
                """, operation.id) != physicalItemCount) {
            invariant("ITEM_CONTRACT_MISMATCH", operation.id);
        }
    }

    private long expectedPhysicalItemCount(OperationRow operation) {
        if ("poll_slice".equals(operation.type)) {
            if (operation.itemCount != 1) invariant("ITEM_CONTRACT_MISMATCH", operation.id);
            return 0;
        }
        if (("website_batch".equals(operation.type) || "poll_preserved_cleanup".equals(operation.type))
                && operation.itemCount >= 1 && operation.itemCount <= 20) {
            return operation.itemCount;
        }
        invariant("ITEM_CONTRACT_MISMATCH", operation.id);
        throw new IllegalStateException("unreachable");
    }

    private void requirePermanentProof(OperationRow operation) {
        long proofs;
        if ("closed_no_mutation".equals(operation.outcome)) {
            boolean terminal = "terminal_attempt".equals(operation.finalizationSource)
                    && "verified_no_mutation".equals(operation.finalizationReason);
            proofs = terminal ? 1 : count("""
                    SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation
                     WHERE operation_id=? AND status='resolved' AND decision='closed_no_mutation'
                    """, operation.id);
        } else if ("website_batch".equals(operation.type)) {
            proofs = count("SELECT COUNT(*) FROM data_retention_batch_commit WHERE operation_id=?", operation.id);
        } else if ("poll_slice".equals(operation.type)) {
            proofs = count("""
                    SELECT COUNT(*) FROM data_retention_purged_slice marker
                    JOIN data_retention_operation o ON o.operation_id=marker.operation_id
                     WHERE marker.operation_id=? AND marker.status='purged'
                       AND marker.domain=o.domain AND marker.project_id=o.poll_project_id
                       AND marker.batch_date=o.poll_batch_date
                       AND marker.question_tier=o.poll_question_tier
                    """, operation.id);
        } else if ("poll_preserved_cleanup".equals(operation.type)) {
            proofs = count("""
                    SELECT COUNT(*) FROM data_retention_poll_preserved_result ledger
                    JOIN data_retention_operation o ON o.operation_id=ledger.cleanup_operation_id
                     WHERE ledger.cleanup_operation_id=?
                       AND ledger.cleanup_status='cleaned' AND ledger.cleaned_at IS NOT NULL
                       AND ledger.domain=o.domain AND ledger.project_id=o.poll_project_id
                       AND ledger.batch_date=o.poll_batch_date
                       AND ledger.question_tier=o.poll_question_tier
                    """, operation.id);
            if (proofs == operation.itemCount) return;
        } else {
            proofs = 0;
        }
        if (proofs != 1) invariant("PERMANENT_PROOF_MISMATCH", operation.id);
    }

    private void requireRunConservation(RelationRow relation) {
        long current = count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id=?", relation.runId);
        long purged = count("SELECT COALESCE(SUM(detail_purged_attempt_count),0) FROM data_retention_operation_run WHERE run_group_id=?", relation.runId);
        if (current + purged + relation.operationlessPurged != relation.attemptCount) {
            invariant("RUN_ROLLUP_MISMATCH", Long.toString(relation.runId));
        }
    }

    public long invariantViolationCount() {
        long operationShape = count("""
                SELECT COUNT(*) FROM data_retention_operation o
                 WHERE (o.detail_purged_at IS NOT NULL AND (
                           EXISTS (SELECT 1 FROM data_retention_batch_attempt a WHERE a.operation_id=o.operation_id)
                        OR EXISTS (SELECT 1 FROM data_retention_batch_item i WHERE i.operation_id=o.operation_id)))
                    OR ((o.detail_purged_at IS NULL) <> (o.detail_purged_item_count IS NULL))
                    OR (o.detail_purged_at IS NULL AND o.detail_purged_item_count IS NULL
                        AND NOT EXISTS (SELECT 1 FROM data_retention_batch_attempt a WHERE a.operation_id=o.operation_id)
                        AND NOT EXISTS (SELECT 1 FROM data_retention_batch_item i WHERE i.operation_id=o.operation_id)
                        AND NOT EXISTS (SELECT 1 FROM data_retention_operation_run x
                                         WHERE x.operation_id=o.operation_id AND x.detail_purged_attempt_count<>0))
                    OR (o.detail_purged_at IS NULL AND EXISTS (
                        SELECT 1 FROM data_retention_operation_run x
                         WHERE x.operation_id=o.operation_id AND x.detail_purged_attempt_count<>0))
                    OR (o.detail_purged_at IS NOT NULL
                        AND o.detail_purged_at < TIMESTAMPADD(DAY,365,o.detail_retention_anchor_at))
                """);
        long runRollup = count("""
                SELECT COUNT(*) FROM data_retention_run r
                 WHERE r.attempt_rollup_completed_at IS NOT NULL
                   AND r.attempt_count <> (
                       (SELECT COUNT(*) FROM data_retention_batch_attempt a WHERE a.run_group_id=r.id)
                       + COALESCE((SELECT SUM(x.detail_purged_attempt_count)
                                     FROM data_retention_operation_run x WHERE x.run_group_id=r.id),0)
                       + r.operationless_attempt_purged_count)
                """);
        long markerRelationShape = count("""
                SELECT COUNT(*) FROM data_retention_operation o
                 WHERE o.detail_purged_at IS NOT NULL
                   AND EXISTS (
                       SELECT 1 FROM data_retention_operation_run x
                        WHERE x.operation_id=o.operation_id
                          AND EXISTS (SELECT 1 FROM data_retention_batch_attempt a
                                       WHERE a.operation_id=x.operation_id AND a.run_group_id=x.run_group_id)
                   )
                """);
        return operationShape + runRollup + markerRelationShape;
    }

    public long eligibleAttemptBacklog(Instant cutoff) {
        return eligibleBacklog(cutoff, 0).attempts;
    }

    public long eligibleItemBacklog(Instant cutoff) {
        return eligibleBacklog(cutoff, 0).items;
    }

    private EligibleBacklog eligibleBacklog(Instant cutoff, long requiredRunId) {
        return jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM((SELECT COUNT(*) FROM data_retention_batch_attempt a
                                  WHERE a.operation_id=o.operation_id)),0) AS attempt_backlog,
                    COALESCE(SUM((SELECT COUNT(*) FROM data_retention_batch_item i
                                  WHERE i.operation_id=o.operation_id)),0) AS item_backlog
                  FROM data_retention_operation o
                 WHERE o.detail_purged_at IS NULL AND o.detail_purged_item_count IS NULL
                   AND o.outcome_status IN ('committed','closed_no_mutation')
                   AND o.outcome_finalized_at IS NOT NULL
                   AND o.outcome_finalization_source IS NOT NULL
                   AND o.outcome_finalization_reason IS NOT NULL
                   AND o.detail_retention_anchor_at IS NOT NULL
                   AND o.detail_retention_anchor_at<=?
                   AND (?=0 OR EXISTS (
                       SELECT 1 FROM data_retention_operation_run required_run
                        WHERE required_run.operation_id=o.operation_id AND required_run.run_group_id=?))
                   AND EXISTS (SELECT 1 FROM data_retention_operation_run relation_present
                                WHERE relation_present.operation_id=o.operation_id)
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_operation_run x
                       JOIN data_retention_run r ON r.id=x.run_group_id AND r.domain=x.domain
                        WHERE x.operation_id=o.operation_id AND (
                              r.status NOT IN ('succeeded','failed','skipped')
                           OR r.finished_at IS NULL OR r.attempt_rollup_completed_at IS NULL
                           OR r.attempt_count<>(
                               (SELECT COUNT(*) FROM data_retention_batch_attempt run_attempt
                                 WHERE run_attempt.run_group_id=r.id)
                               + COALESCE((SELECT SUM(run_relation.detail_purged_attempt_count)
                                             FROM data_retention_operation_run run_relation
                                            WHERE run_relation.run_group_id=r.id),0)
                               + r.operationless_attempt_purged_count)))
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_batch_attempt nonterminal
                        WHERE nonterminal.operation_id=o.operation_id
                          AND nonterminal.status IN ('prepared','running'))
                   AND (SELECT COUNT(*) FROM data_retention_batch_attempt attempt_budget
                         WHERE attempt_budget.operation_id=o.operation_id)<=1000
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_unknown_outcome_reconciliation reconciliation
                        WHERE reconciliation.operation_id=o.operation_id
                          AND (reconciliation.status<>'resolved' OR reconciliation.decision<>o.outcome_status))
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_failure_group failure_group
                        WHERE failure_group.operation_id=o.operation_id AND failure_group.status='active')
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_failure_active_key active_key
                       JOIN data_retention_failure_group active_group
                         ON active_group.id=active_key.group_id AND active_group.domain=active_key.domain
                        WHERE active_group.operation_id=o.operation_id)
                   AND (
                       (o.operation_type='poll_slice' AND o.item_count=1
                        AND (SELECT COUNT(*) FROM data_retention_batch_item poll_item
                              WHERE poll_item.operation_id=o.operation_id)=0)
                       OR
                       (o.operation_type IN ('website_batch','poll_preserved_cleanup')
                        AND o.item_count BETWEEN 1 AND 20
                        AND (SELECT COUNT(*) FROM data_retention_batch_item typed_item
                              WHERE typed_item.operation_id=o.operation_id)=o.item_count))
                   AND (
                       (o.outcome_status='closed_no_mutation' AND (
                           (o.outcome_finalization_source='terminal_attempt'
                            AND o.outcome_finalization_reason='verified_no_mutation')
                           OR (o.outcome_finalization_source='reconciliation'
                               AND o.outcome_finalization_reason='verified_no_mutation' AND EXISTS (
                               SELECT 1 FROM data_retention_unknown_outcome_reconciliation closed_proof
                                WHERE closed_proof.operation_id=o.operation_id
                                  AND closed_proof.status='resolved'
                                  AND closed_proof.decision='closed_no_mutation'))))
                       OR
                       (o.outcome_status='committed' AND (
                           (o.operation_type='website_batch'
                            AND o.outcome_finalization_source='website_commit'
                            AND o.outcome_finalization_reason='committed'
                            AND (SELECT COUNT(*) FROM data_retention_batch_commit website_proof
                                  WHERE website_proof.operation_id=o.operation_id)=1)
                           OR
                           (o.operation_type='poll_slice'
                            AND o.outcome_finalization_source='poll_marker'
                            AND o.outcome_finalization_reason='purged'
                            AND (SELECT COUNT(*) FROM data_retention_purged_slice poll_proof
                                  WHERE poll_proof.operation_id=o.operation_id
                                    AND poll_proof.domain=o.domain
                                    AND poll_proof.project_id=o.poll_project_id
                                    AND poll_proof.batch_date=o.poll_batch_date
                                    AND poll_proof.question_tier=o.poll_question_tier
                                    AND poll_proof.status='purged')=1)
                           OR
                           (o.operation_type='poll_preserved_cleanup'
                            AND o.outcome_finalization_source='poll_cleanup'
                            AND o.outcome_finalization_reason='cleaned'
                            AND (SELECT COUNT(*) FROM data_retention_poll_preserved_result cleanup_proof
                                  WHERE cleanup_proof.cleanup_operation_id=o.operation_id
                                    AND cleanup_proof.cleanup_status='cleaned'
                                    AND cleanup_proof.cleaned_at IS NOT NULL
                                    AND cleanup_proof.domain=o.domain
                                    AND cleanup_proof.project_id=o.poll_project_id
                                    AND cleanup_proof.batch_date=o.poll_batch_date
                                    AND cleanup_proof.question_tier=o.poll_question_tier
                                    AND EXISTS (SELECT 1 FROM data_retention_batch_item cleanup_item
                                                 WHERE cleanup_item.operation_id=o.operation_id
                                                   AND cleanup_item.business_key=cleanup_proof.poll_result_id))=o.item_count))))
                """, (rs, n) -> new EligibleBacklog(rs.getLong("attempt_backlog"), rs.getLong("item_backlog")),
                UTC_MILLIS.format(cutoff), requiredRunId, requiredRunId);
    }

    public Instant oldestUnpurgedAnchor() {
        LocalDateTime value = jdbc.queryForObject("""
                SELECT MIN(anchor_at) FROM (
                    SELECT MIN(o.detail_retention_anchor_at) anchor_at
                      FROM data_retention_operation o
                     WHERE o.detail_purged_at IS NULL
                       AND EXISTS (SELECT 1 FROM data_retention_batch_attempt a WHERE a.operation_id=o.operation_id)
                    UNION ALL
                    SELECT MIN(r.finished_at) FROM data_retention_run r
                     WHERE EXISTS (SELECT 1 FROM data_retention_batch_attempt a
                                    WHERE a.run_group_id=r.id AND a.operation_id IS NULL)
                ) anchors
                """, LocalDateTime.class);
        return instant(value);
    }

    public long retentionTableBytes() {
        return count("""
                SELECT COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name IN
                  ('data_retention_run','data_retention_operation','data_retention_operation_run',
                   'data_retention_batch_attempt','data_retention_batch_item','data_retention_batch_commit',
                   'data_retention_purged_slice','data_retention_failure_group',
                   'data_retention_failure_group_item','data_retention_failure_active_key',
                   'data_retention_failure_regroup_history','data_retention_unknown_outcome_reconciliation',
                   'data_retention_dynamic_switch_change','poll_audit_purge_runs',
                   'data_retention_poll_preserved_result','data_retention_detail_cleanup_run')
                """);
    }

    public boolean migrationReady() {
        return count("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND (
                    (table_name='data_retention_operation' AND column_name='detail_purged_item_count') OR
                    (table_name='data_retention_operation_run' AND column_name='detail_purged_attempt_count') OR
                    (table_name='data_retention_run' AND column_name='operationless_attempt_purged_count'))
                """) == 3 && count("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name='data_retention_detail_cleanup_run'
                """) == 1;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void requireActiveCleanupRun(String cleanupRunId){
        if(cleanupRunId==null)return;
        if(count("""
                SELECT COUNT(*) FROM data_retention_detail_cleanup_run
                 WHERE cleanup_run_id=? AND status='running' AND active_slot=1
                """,cleanupRunId)!=1){
            throw new DataRetentionDetailCleanupException("CLEANUP_RUN_LOST","cleanup run active ownership was fenced");
        }
    }

    private static LocalDateTime timestamp(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static void invariant(String code, String identity) {
        throw new DataRetentionDetailCleanupException(code, code + ":" + identity, true);
    }

    private record OperationRow(String id, String type, long itemCount, String outcome,
                                String finalizationSource, String finalizationReason,
                                LocalDateTime anchor, Instant purgedAt, Long purgedItemCount) { }
    private record RelationRow(long runId, long purgedCount, String status, Instant finishedAt,
                               Instant rollupAt, long attemptCount, long operationlessPurged) { }
    private record AttemptRow(long id, long runId, String status) { }
    private record RunRow(long id, String status, Instant finishedAt, Instant rollupAt,
                           long attemptCount, long operationlessPurged) { }
    private record EligibleBacklog(long attempts, long items) { }
    public record OperationCandidate(String operationId,Instant effectiveAnchor) { }
    public record OperationlessCandidate(long runId,Instant effectiveAnchor) { }
}
