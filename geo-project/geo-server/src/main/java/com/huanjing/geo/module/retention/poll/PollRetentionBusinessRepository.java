package com.huanjing.geo.module.retention.poll;

import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedPollMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionException;
import com.huanjing.geo.module.retention.service.PollRetentionSliceLockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Repository
public class PollRetentionBusinessRepository {
    private static final Set<String> TERMINAL_BATCH = Set.of("finished", "finished_with_failures", "failed");
    private static final Set<String> TERMINAL_ATTEMPT = Set.of("SUCCEEDED", "FAILED", "ABANDONED");

    private final JdbcTemplate jdbc;
    private final PollRetentionSliceLockService sliceLockService;
    private final PollSummaryRecomputeService summaryService;
    private final MobileDashboardEntityJudgeService entityJudgeService;
    private final TransactionTemplate selectionTransaction;

    public PollRetentionBusinessRepository(JdbcTemplate jdbc,
                                           PollRetentionSliceLockService sliceLockService,
                                           PollSummaryRecomputeService summaryService,
                                           MobileDashboardEntityJudgeService entityJudgeService,
                                           PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.sliceLockService = sliceLockService;
        this.summaryService = summaryService;
        this.entityJudgeService = entityJudgeService;
        this.selectionTransaction = new TransactionTemplate(transactionManager);
        this.selectionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.selectionTransaction.setReadOnly(true);
        this.selectionTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public PollSlice selectNextPrimary(SelectionPolicy policy, PollSlice cursor) {
        return selectionTransaction.execute(status -> selectNextPrimaryInTransaction(policy, cursor));
    }

    public boolean preflightPrimary(PollSlice slice, int stuckBatchSealDays, LocalDate today) {
        Boolean result = selectionTransaction.execute(status -> {
            if (markerExists(slice) || !batchEligible(slice, stuckBatchSealDays, today, false)
                    || hasNonTerminalAttempt(slice)) return false;
            return summariesMatch(slice);
        });
        return Boolean.TRUE.equals(result);
    }

    public CleanupSelection selectCleanup(SelectionPolicy policy, int limit) {
        if (limit < 1 || limit > 20) return null;
        return selectionTransaction.execute(status -> selectCleanupInTransaction(policy, limit));
    }

    public PrimaryMutationResult mutatePrimary(LockedPollMutationContext locked,
                                               int stuckBatchSealDays,
                                               LocalDate today) {
        RetentionOperationContext operation = requireOperation(locked, "poll_slice");
        PollSlice slice = slice(operation);
        sliceLockService.lockSlice(slice.projectId(), slice.batchDate(), slice.questionTier());
        if (markerExists(slice)) throw stateChanged("Poll slice already has a permanent marker");
        lockAndRequireBatches(slice, stuckBatchSealDays, today);
        List<Long> resultIds = lockResultIds(slice);
        if (resultIds.isEmpty()) throw stateChanged("Poll slice no longer contains result rows");
        List<Long> attemptIds = lockAttemptIds(resultIds);
        requireTerminalAttempts(attemptIds);
        lockChildren(attemptIds, resultIds);
        List<Long> shardIds = lockShardIds(slice);
        List<Long> shardItemIds = lockShardItemIds(shardIds);
        if (!summariesMatch(slice)) throw stateChanged("Poll summaries no longer match the slice");
        SummarySnapshot summarySnapshot = snapshotSummaries(slice);

        List<Long> purgeableIds = loadPurgeableResultIds(slice);
        List<Long> preservedIds = resultIds.stream().filter(id -> !purgeableIds.contains(id)).toList();
        ChildSnapshot purgeableSnapshot = snapshotChildren(purgeableIds);
        ChildSnapshot preservedSnapshot = snapshotChildren(preservedIds);
        Map<String, Long> deleted = deleteResultIds(purgeableIds);
        deleted.put("poll_batch_shard_items", (long) deleteIds("poll_batch_shard_items", shardItemIds));
        deleted.put("poll_batch_shards", (long) deleteIds("poll_batch_shards", shardIds));

        if (!allSnapshotRowsDeleted(purgeableSnapshot)
                || countExisting("poll_results", preservedIds) != preservedIds.size()
                || !preservedSnapshot.equals(snapshotChildren(preservedIds))
                || countExisting("poll_batch_shards", shardIds) != 0
                || countExisting("poll_batch_shard_items", shardItemIds) != 0
                || !batchEligible(slice, stuckBatchSealDays, today, true)
                || !summarySnapshot.equals(snapshotSummaries(slice))) {
            throw postCheckFailed("Poll primary locked-scope post-check failed");
        }
        return new PrimaryMutationResult(slice, preservedIds, deleted);
    }

    public CleanupMutationResult mutateCleanup(LockedPollMutationContext locked) {
        RetentionOperationContext operation = requireOperation(locked, "poll_preserved_cleanup");
        List<Long> ids = locked.pollResultIds().stream().sorted().toList();
        if (ids.size() != operation.itemCount()) throw stateChanged("Poll cleanup item tree changed");
        String placeholders = placeholders(ids.size());
        List<LedgerRow> ledger = jdbc.query("""
                SELECT origin_operation_id, poll_result_id, project_id, batch_date, question_tier, cleanup_status
                  FROM data_retention_poll_preserved_result
                 WHERE poll_result_id IN (%s)
                 ORDER BY poll_result_id FOR UPDATE
                """.formatted(placeholders), (rs, rowNum) -> new LedgerRow(
                rs.getString("origin_operation_id"), rs.getLong("poll_result_id"), rs.getLong("project_id"),
                rs.getDate("batch_date").toLocalDate(), normalizeTier(rs.getString("question_tier")),
                rs.getString("cleanup_status")), ids.toArray());
        if (ledger.size() != ids.size()) throw stateChanged("Poll cleanup ledger rows changed");
        String originOperationId = ledger.get(0).originOperationId();
        PollSlice slice = slice(operation);
        for (int index = 0; index < ids.size(); index++) {
            LedgerRow row = ledger.get(index);
            if (row.pollResultId() != ids.get(index) || !originOperationId.equals(row.originOperationId())
                    || row.projectId() != slice.projectId() || !row.batchDate().equals(slice.batchDate())
                    || !row.questionTier().equals(slice.questionTier()) || !"preserved".equals(row.status())) {
                throw stateChanged("Poll cleanup ledger identity changed");
            }
        }
        requireOriginMarker(originOperationId, slice);
        sliceLockService.lockSlice(slice.projectId(), slice.batchDate(), slice.questionTier());
        requireOriginMarker(originOperationId, slice);
        List<Long> lockedResults = lockExactResultIds(ids);
        if (!lockedResults.equals(ids)) throw stateChanged("Poll cleanup result rows changed");
        List<Long> attemptIds = lockAttemptIds(ids);
        lockChildren(attemptIds, ids);
        ChildSnapshot cleanupSnapshot = snapshotChildren(ids);
        for (Long id : ids) {
            if (!isSuperseded(id)) throw stateChanged("Poll cleanup result is no longer superseded");
        }
        Map<String, Long> deleted = deleteResultIds(ids);
        if (!allSnapshotRowsDeleted(cleanupSnapshot) || !ledgerStillPreserved(originOperationId, ids)) {
            throw postCheckFailed("Poll cleanup locked-scope post-check failed");
        }
        return new CleanupMutationResult(originOperationId, slice, ids, deleted);
    }

    public EvidenceState inspectEvidence(RetentionOperationContext operation) {
        if (operation == null) return EvidenceState.MISSING;
        if ("poll_preserved_cleanup".equals(operation.operationType())) {
            List<Long> ids = operation.pollCleanupResultIds();
            if (ids.isEmpty()) return EvidenceState.MISSING;
            int present = countExisting("poll_results", ids);
            Integer cleaned = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_poll_preserved_result "
                            + "WHERE cleanup_operation_id = ? AND cleanup_status = 'cleaned'",
                    Integer.class, operation.operationId());
            if (present == 0 && cleaned != null && cleaned == ids.size()) return EvidenceState.POST;
            if (present == ids.size() && ledgerStillPreserved(null, ids)) return EvidenceState.PRE;
            return EvidenceState.MIXED;
        }
        PollSlice slice = slice(operation);
        Integer marker = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_purged_slice WHERE operation_id = ?",
                Integer.class, operation.operationId());
        if (Integer.valueOf(1).equals(marker)) return EvidenceState.POST;
        return markerExists(slice) ? EvidenceState.MIXED : EvidenceState.PRE;
    }

    private PollSlice selectNextPrimaryInTransaction(SelectionPolicy policy, PollSlice cursor) {
        StringBuilder sql = new StringBuilder("""
                SELECT pr.project_id, pr.batch_date, pr.question_tier
                  FROM poll_results pr
                 WHERE pr.batch_date <= ?
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_purged_slice marker
                        WHERE marker.domain = 'poll_results' AND marker.project_id = pr.project_id
                          AND marker.batch_date = pr.batch_date AND marker.question_tier = pr.question_tier
                          AND marker.status = 'purged')
                   AND NOT EXISTS (
                       SELECT 1 FROM data_retention_operation hold
                        WHERE hold.domain = 'poll_results' AND hold.operation_type = 'poll_slice'
                          AND hold.poll_project_id = pr.project_id AND hold.poll_batch_date = pr.batch_date
                          AND hold.poll_question_tier = pr.question_tier
                          AND hold.outcome_status IN ('pending','rolled_back','unknown','manual_review'))
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(policy.effectiveEndDate()));
        appendPolicy(sql, args, policy, "pr");
        if (cursor != null) {
            sql.append(" AND (pr.batch_date > ? OR (pr.batch_date = ? AND pr.project_id > ?) "
                    + "OR (pr.batch_date = ? AND pr.project_id = ? AND pr.question_tier > ?))");
            args.add(Date.valueOf(cursor.batchDate())); args.add(Date.valueOf(cursor.batchDate()));
            args.add(cursor.projectId()); args.add(Date.valueOf(cursor.batchDate()));
            args.add(cursor.projectId()); args.add(cursor.questionTier());
        }
        sql.append(" GROUP BY pr.project_id, pr.batch_date, pr.question_tier "
                + "ORDER BY pr.batch_date, pr.project_id, pr.question_tier LIMIT 1");
        List<PollSlice> rows = jdbc.query(sql.toString(), (rs, rowNum) -> new PollSlice(
                rs.getLong("project_id"), rs.getDate("batch_date").toLocalDate(),
                normalizeTier(rs.getString("question_tier"))), args.toArray());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private CleanupSelection selectCleanupInTransaction(SelectionPolicy policy, int limit) {
        StringBuilder base = new StringBuilder("""
                FROM data_retention_poll_preserved_result ledger
                JOIN poll_results old_result ON old_result.id = ledger.poll_result_id
                WHERE ledger.cleanup_status = 'preserved'
                  AND old_result.status = 'completed'
                  AND EXISTS (
                      SELECT 1 FROM poll_results newer
                       WHERE newer.project_id = old_result.project_id
                         AND newer.question_tier = old_result.question_tier
                         AND newer.status = 'completed' AND %s)
                  AND NOT EXISTS (
                      SELECT 1 FROM data_retention_batch_item held_item
                      JOIN data_retention_operation held_operation
                        ON held_operation.operation_id = held_item.operation_id
                       WHERE held_item.domain = 'poll_results'
                         AND held_item.business_key = ledger.poll_result_id
                         AND held_operation.operation_type = 'poll_preserved_cleanup'
                         AND held_operation.outcome_status IN ('pending','rolled_back','unknown','manual_review'))
                """.formatted(PollRetentionEligibilitySql.newerProjection("newer", "old_result")));
        List<Object> args = new ArrayList<>();
        appendPolicy(base, args, policy, "ledger");
        List<LedgerRow> first = jdbc.query("SELECT ledger.origin_operation_id, ledger.poll_result_id, "
                        + "ledger.project_id, ledger.batch_date, ledger.question_tier, ledger.cleanup_status "
                        + base + " ORDER BY ledger.origin_operation_id, ledger.poll_result_id LIMIT 1",
                (rs, rowNum) -> ledgerRow(rs), args.toArray());
        if (first.isEmpty()) return null;
        LedgerRow head = first.get(0);
        StringBuilder groupSql = new StringBuilder("SELECT ledger.origin_operation_id, ledger.poll_result_id, "
                + "ledger.project_id, ledger.batch_date, ledger.question_tier, ledger.cleanup_status " + base
                + " AND ledger.origin_operation_id = ? ORDER BY ledger.poll_result_id LIMIT ?");
        List<Object> groupArgs = new ArrayList<>(args);
        groupArgs.add(head.originOperationId()); groupArgs.add(limit);
        List<LedgerRow> group = jdbc.query(groupSql.toString(), (rs, rowNum) -> ledgerRow(rs), groupArgs.toArray());
        List<Long> ids = group.stream().map(LedgerRow::pollResultId).toList();
        return new CleanupSelection(head.originOperationId(),
                new PollSlice(head.projectId(), head.batchDate(), head.questionTier()), ids);
    }

    private void appendPolicy(StringBuilder sql, List<Object> args, SelectionPolicy policy, String alias) {
        if (policy.projectId() != null) { sql.append(" AND ").append(alias).append(".project_id = ?"); args.add(policy.projectId()); }
        if (policy.effectiveStartDate() != null) { sql.append(" AND ").append(alias).append(".batch_date >= ?"); args.add(Date.valueOf(policy.effectiveStartDate())); }
        if (policy.questionTier() != null) { sql.append(" AND ").append(alias).append(".question_tier = ?"); args.add(policy.questionTier()); }
        sql.append(" AND ").append(alias).append(".batch_date <= ?");
        args.add(Date.valueOf(policy.effectiveEndDate()));
    }

    private boolean markerExists(PollSlice slice) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_purged_slice
                 WHERE domain = 'poll_results' AND project_id = ? AND batch_date = ?
                   AND question_tier = ? AND status = 'purged'
                """, Integer.class, slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
        return count != null && count > 0;
    }

    private boolean batchEligible(PollSlice slice, int stuckDays, LocalDate today, boolean locked) {
        String suffix = locked ? " FOR UPDATE" : "";
        List<String> statuses = jdbc.queryForList("""
                SELECT status FROM poll_batches
                 WHERE project_id = ? AND batch_date = ? AND question_tier = ?
                 ORDER BY id
                """ + suffix, String.class, slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
        return !statuses.isEmpty() && statuses.stream().allMatch(TERMINAL_BATCH::contains);
    }

    private void lockAndRequireBatches(PollSlice slice, int stuckDays, LocalDate today) {
        if (!batchEligible(slice, stuckDays, today, true)) {
            throw stateChanged("Poll batch is missing or not terminal");
        }
    }

    private boolean hasNonTerminalAttempt(PollSlice slice) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM poll_invocation_attempts a
                JOIN poll_results pr ON pr.id = a.poll_result_id
                 WHERE pr.project_id = ? AND pr.batch_date = ? AND pr.question_tier = ?
                   AND a.status NOT IN ('SUCCEEDED','FAILED','ABANDONED')
                """, Integer.class, slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
        return count != null && count > 0;
    }

    private List<Long> lockResultIds(PollSlice slice) {
        return jdbc.queryForList("""
                SELECT id FROM poll_results
                 WHERE project_id = ? AND batch_date = ? AND question_tier = ?
                 ORDER BY id FOR UPDATE
                """, Long.class, slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
    }

    private List<Long> lockExactResultIds(List<Long> ids) {
        return jdbc.queryForList("SELECT id FROM poll_results WHERE id IN (" + placeholders(ids.size())
                + ") ORDER BY id FOR UPDATE", Long.class, ids.toArray());
    }

    private List<Long> lockAttemptIds(List<Long> resultIds) {
        if (resultIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT id FROM poll_invocation_attempts WHERE poll_result_id IN ("
                + placeholders(resultIds.size()) + ") ORDER BY id FOR UPDATE", Long.class, resultIds.toArray());
    }

    private void requireTerminalAttempts(List<Long> attemptIds) {
        if (attemptIds.isEmpty()) return;
        List<String> statuses = jdbc.queryForList("SELECT status FROM poll_invocation_attempts WHERE id IN ("
                + placeholders(attemptIds.size()) + ") ORDER BY id", String.class, attemptIds.toArray());
        if (statuses.stream().anyMatch(status -> !TERMINAL_ATTEMPT.contains(status))) {
            throw stateChanged("Poll invocation attempt is not terminal");
        }
    }

    private void lockChildren(List<Long> attemptIds, List<Long> resultIds) {
        if (!attemptIds.isEmpty()) {
            lockIdsByForeignKey("poll_provider_calls", "attempt_id", attemptIds);
            lockIdsByForeignKey("poll_search_sources", "attempt_id", attemptIds);
            lockIdsByForeignKey("poll_citations", "attempt_id", attemptIds);
        }
        if (!resultIds.isEmpty()) lockIdsByForeignKey("poll_result_entity_judge", "poll_result_id", resultIds);
    }

    private void lockIdsByForeignKey(String table, String column, List<Long> ids) {
        jdbc.queryForList("SELECT id FROM " + table + " WHERE " + column + " IN ("
                + placeholders(ids.size()) + ") ORDER BY id FOR UPDATE", Long.class, ids.toArray());
    }

    private List<Long> lockShardIds(PollSlice slice) {
        return jdbc.queryForList("""
                SELECT id FROM poll_batch_shards
                 WHERE project_id = ? AND batch_date = ? AND question_tier = ?
                 ORDER BY id FOR UPDATE
                """, Long.class, slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
    }

    private List<Long> lockShardItemIds(List<Long> shardIds) {
        if (shardIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT id FROM poll_batch_shard_items WHERE shard_id IN ("
                + placeholders(shardIds.size()) + ") ORDER BY id FOR UPDATE", Long.class, shardIds.toArray());
    }

    private List<Long> loadPurgeableResultIds(PollSlice slice) {
        return jdbc.queryForList("SELECT pr.id FROM poll_results pr WHERE pr.project_id = ? AND pr.batch_date = ? "
                        + "AND pr.question_tier = ? AND " + PollRetentionEligibilitySql.purgeable("pr")
                        + " ORDER BY pr.id", Long.class,
                slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier());
    }

    private Map<String, Long> deleteResultIds(List<Long> ids) {
        Map<String, Long> deleted = new LinkedHashMap<>();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<Long> chunk = ids.subList(offset, Math.min(ids.size(), offset + 500));
            String placeholders = placeholders(chunk.size());
            String attemptIds = "SELECT id FROM poll_invocation_attempts WHERE poll_result_id IN ("
                    + placeholders + ")";
            merge(deleted, "poll_citations", jdbc.update("DELETE FROM poll_citations WHERE attempt_id IN ("
                    + attemptIds + ")", chunk.toArray()));
            merge(deleted, "poll_search_sources", jdbc.update("DELETE FROM poll_search_sources WHERE attempt_id IN ("
                    + attemptIds + ")", chunk.toArray()));
            merge(deleted, "poll_provider_calls", jdbc.update("DELETE FROM poll_provider_calls WHERE attempt_id IN ("
                    + attemptIds + ")", chunk.toArray()));
            merge(deleted, "poll_invocation_attempts", jdbc.update("DELETE FROM poll_invocation_attempts "
                    + "WHERE poll_result_id IN (" + placeholders + ")", chunk.toArray()));
            merge(deleted, "poll_result_entity_judge", jdbc.update("DELETE FROM poll_result_entity_judge "
                    + "WHERE poll_result_id IN (" + placeholders + ")", chunk.toArray()));
            merge(deleted, "poll_results", jdbc.update("DELETE FROM poll_results WHERE id IN ("
                    + placeholders + ")", chunk.toArray()));
        }
        return deleted;
    }

    private ChildSnapshot snapshotChildren(List<Long> resultIds) {
        if (resultIds.isEmpty()) return ChildSnapshot.empty();
        List<Long> attempts = idsByForeignKey("poll_invocation_attempts", "poll_result_id", resultIds);
        return new ChildSnapshot(List.copyOf(resultIds), attempts,
                idsByForeignKey("poll_provider_calls", "attempt_id", attempts),
                idsByForeignKey("poll_search_sources", "attempt_id", attempts),
                idsByForeignKey("poll_citations", "attempt_id", attempts),
                idsByForeignKey("poll_result_entity_judge", "poll_result_id", resultIds));
    }

    private List<Long> idsByForeignKey(String table, String column, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT id FROM " + table + " WHERE " + column + " IN ("
                + placeholders(ids.size()) + ") ORDER BY id", Long.class, ids.toArray());
    }

    private boolean summariesMatch(PollSlice slice) {
        PollSummaryRecomputeService.SummaryVerification summary = summaryService.verifySlice(
                slice.projectId(), slice.batchDate(), slice.questionTier());
        MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification entity =
                entityJudgeService.verifySummarySlice(slice.projectId(), slice.batchDate(), slice.questionTier());
        return summary.keywordMatched() && summary.platformMatched() && entity.matched();
    }

    private SummarySnapshot snapshotSummaries(PollSlice slice) {
        Object[] args = {slice.projectId(), Date.valueOf(slice.batchDate()), slice.questionTier()};
        return new SummarySnapshot(
                jdbc.queryForList("SELECT * FROM poll_keyword_daily_summary "
                        + "WHERE project_id=? AND batch_date=? AND question_tier=? ORDER BY id", args),
                jdbc.queryForList("SELECT * FROM poll_platform_daily_summary "
                        + "WHERE project_id=? AND batch_date=? AND question_tier=? ORDER BY id", args),
                jdbc.queryForList("SELECT * FROM poll_entity_judge_daily_summary "
                        + "WHERE project_id=? AND batch_date=? AND question_tier=? ORDER BY id", args));
    }

    private boolean isSuperseded(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM poll_results old_result WHERE old_result.id = ? "
                        + "AND old_result.status = 'completed' AND EXISTS (SELECT 1 FROM poll_results newer "
                        + "WHERE newer.project_id = old_result.project_id "
                        + "AND newer.question_tier = old_result.question_tier AND newer.status = 'completed' AND "
                        + PollRetentionEligibilitySql.newerProjection("newer", "old_result") + ")",
                Integer.class, id);
        return Integer.valueOf(1).equals(count);
    }

    private void requireOriginMarker(String originOperationId, PollSlice slice) {
        List<String> markerOperations = jdbc.queryForList("""
                SELECT operation_id FROM data_retention_purged_slice
                 WHERE operation_id = ? AND domain = 'poll_results' AND project_id = ?
                   AND batch_date = ? AND question_tier = ? AND status = 'purged'
                 FOR UPDATE
                """, String.class, originOperationId, slice.projectId(), Date.valueOf(slice.batchDate()),
                slice.questionTier());
        if (markerOperations.size() != 1) throw stateChanged("Poll cleanup origin marker is missing");
    }

    private boolean ledgerStillPreserved(String originOperationId, List<Long> ids) {
        if (ids.isEmpty()) return false;
        List<Object> args = new ArrayList<>(ids);
        String originClause = "";
        if (originOperationId != null) { originClause = " AND origin_operation_id = ?"; args.add(originOperationId); }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_poll_preserved_result WHERE poll_result_id IN ("
                + placeholders(ids.size()) + ") AND cleanup_status = 'preserved' AND cleanup_operation_id IS NULL "
                + "AND cleaned_at IS NULL" + originClause, Integer.class, args.toArray());
        return count != null && count == ids.size();
    }

    private int deleteIds(String table, List<Long> ids) {
        return ids.isEmpty() ? 0 : jdbc.update("DELETE FROM " + table + " WHERE id IN ("
                + placeholders(ids.size()) + ")", ids.toArray());
    }

    private int countExisting(String table, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id IN ("
                + placeholders(ids.size()) + ")", Integer.class, ids.toArray());
        return count == null ? 0 : count;
    }

    private boolean allSnapshotRowsDeleted(ChildSnapshot snapshot) {
        return countExisting("poll_results", snapshot.resultIds()) == 0
                && countExisting("poll_invocation_attempts", snapshot.attemptIds()) == 0
                && countExisting("poll_provider_calls", snapshot.providerCallIds()) == 0
                && countExisting("poll_search_sources", snapshot.searchSourceIds()) == 0
                && countExisting("poll_citations", snapshot.citationIds()) == 0
                && countExisting("poll_result_entity_judge", snapshot.entityJudgeIds()) == 0;
    }

    private RetentionOperationContext requireOperation(LockedPollMutationContext locked, String type) {
        if (locked == null || locked.operation() == null || !type.equals(locked.operation().operationType())) {
            throw stateChanged("Poll mutation context is incomplete or has the wrong type");
        }
        return locked.operation();
    }

    private static PollSlice slice(RetentionOperationContext operation) {
        return new PollSlice(operation.pollProjectId(), operation.pollBatchDate(),
                normalizeTier(operation.pollQuestionTier()));
    }

    private static LedgerRow ledgerRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LedgerRow(rs.getString("origin_operation_id"), rs.getLong("poll_result_id"),
                rs.getLong("project_id"), rs.getDate("batch_date").toLocalDate(),
                normalizeTier(rs.getString("question_tier")), rs.getString("cleanup_status"));
    }

    private static String placeholders(int count) {
        if (count < 1) throw new IllegalArgumentException("placeholder count must be positive");
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static String normalizeTier(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void merge(Map<String, Long> values, String key, int count) {
        values.merge(key, (long) count, Long::sum);
    }

    private static DataRetentionExecutionException stateChanged(String message) {
        return new DataRetentionExecutionException(409, "POLL_SLICE_STATE_CHANGED", message);
    }

    private static DataRetentionExecutionException postCheckFailed(String message) {
        return new DataRetentionExecutionException(409, "POLL_POST_CHECK_FAILED", message);
    }

    public record SelectionPolicy(Long projectId, LocalDate effectiveStartDate, LocalDate effectiveEndDate,
                                  String questionTier) {
        public SelectionPolicy {
            if (effectiveEndDate == null || effectiveStartDate != null && effectiveStartDate.isAfter(effectiveEndDate)) {
                throw new IllegalArgumentException("poll selection date range is invalid");
            }
            questionTier = normalizeTier(questionTier);
        }
    }

    public record PollSlice(long projectId, LocalDate batchDate, String questionTier) {
        public PollSlice {
            if (projectId <= 0 || batchDate == null || questionTier == null || questionTier.isBlank()) {
                throw new IllegalArgumentException("poll slice identity is incomplete");
            }
            questionTier = normalizeTier(questionTier);
        }
    }

    public record CleanupSelection(String originOperationId, PollSlice slice, List<Long> pollResultIds) {
        public CleanupSelection { pollResultIds = List.copyOf(pollResultIds); }
    }

    public record PrimaryMutationResult(PollSlice slice, List<Long> preservedResultIds,
                                        Map<String, Long> deletedRows) {
        public PrimaryMutationResult {
            preservedResultIds = List.copyOf(preservedResultIds);
            deletedRows = Map.copyOf(deletedRows);
        }
        public Map<String, Object> metrics() {
            return Map.of("mutationKind", "poll_slice", "preservedResultCount", preservedResultIds.size(),
                    "deletedRows", deletedRows, "postCheckSchemaVersion", 1);
        }
    }

    public record CleanupMutationResult(String originOperationId, PollSlice slice, List<Long> pollResultIds,
                                        Map<String, Long> deletedRows) {
        public CleanupMutationResult {
            pollResultIds = List.copyOf(pollResultIds);
            deletedRows = Map.copyOf(deletedRows);
        }
        public Map<String, Object> metrics() {
            return Map.of("mutationKind", "poll_preserved_cleanup", "cleanedResultCount", pollResultIds.size(),
                    "deletedRows", deletedRows, "postCheckSchemaVersion", 1);
        }
    }

    private record LedgerRow(String originOperationId, long pollResultId, long projectId, LocalDate batchDate,
                             String questionTier, String status) { }

    private record ChildSnapshot(List<Long> resultIds, List<Long> attemptIds, List<Long> providerCallIds,
                                 List<Long> searchSourceIds, List<Long> citationIds, List<Long> entityJudgeIds) {
        private static ChildSnapshot empty() {
            return new ChildSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private record SummarySnapshot(List<Map<String, Object>> keywordRows,
                                   List<Map<String, Object>> platformRows,
                                   List<Map<String, Object>> entityJudgeRows) { }

    public enum EvidenceState { PRE, POST, MIXED, MISSING }
}
