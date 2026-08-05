package com.huanjing.geo.module.retention.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunSliceVO;
import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.dispatch.websearch.purge.PollAuditPurgeService;
import com.huanjing.geo.module.dispatch.websearch.purge.PollPurgeRequest;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAiPlatformCatalog;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PollRetentionDryRunService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_HOT_RETENTION_DAYS = 14;
    private static final int DEFAULT_STUCK_BATCH_SEAL_DAYS = 7;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PollRetentionSliceLockService sliceLockService;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;
    private final PollAuditPurgeService pollAuditPurgeService;
    private final PollSummaryRecomputeService pollSummaryRecomputeService;
    private final MobileDashboardEntityJudgeService mobileDashboardEntityJudgeService;
    private final DataRetentionProperties retentionProperties;
    private final ObjectMapper objectMapper;

    public PollRetentionDryRunResponse dryRun(PollRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        Long requestedBy = currentUserService.requireCurrentUser().getId();
        return run(request, true, requestedBy);
    }

    public PollRetentionDryRunResponse purge(PollRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        Long requestedBy = currentUserService.requireCurrentUser().getId();
        if (!retentionProperties.getPollResults().isExecuteEnabled()) {
            throw new BizException(403,
                    "Poll result purge execute is disabled by geo.retention.poll-results.execute-enabled");
        }
        return run(request, false, requestedBy);
    }

    public PollRetentionDryRunResponse runScheduled(PollRetentionDryRunRequest request,
                                                    boolean dryRun,
                                                    long requestedBy) {
        if (!dryRun && !retentionProperties.getPollResults().isExecuteEnabled()) {
            throw new BizException(403,
                    "Poll result purge execute is disabled by geo.retention.poll-results.execute-enabled");
        }
        return run(request, dryRun, requestedBy);
    }

    private PollRetentionDryRunResponse run(PollRetentionDryRunRequest request,
                                            boolean dryRun,
                                            long requestedBy) {
        PollRetentionDryRunRequest safeRequest = request == null ? new PollRetentionDryRunRequest() : request;
        if (!dryRun && !StringUtils.hasText(safeRequest.getReason())) {
            throw new BizException(400, "reason is required for poll result purge");
        }
        int requestedHotRetentionDays =
                normalizePositive(safeRequest.getHotRetentionDays(), DEFAULT_HOT_RETENTION_DAYS);
        boolean simulationOnly = dryRun && requestedHotRetentionDays < DEFAULT_HOT_RETENTION_DAYS;
        int hotRetentionDays = dryRun
                ? requestedHotRetentionDays
                : Math.max(requestedHotRetentionDays, DEFAULT_HOT_RETENTION_DAYS);
        int stuckBatchSealDays = normalizePositive(safeRequest.getStuckBatchSealDays(), DEFAULT_STUCK_BATCH_SEAL_DAYS);
        int limit = normalizeLimit(safeRequest.getLimit());
        String questionTier = normalizeTierFilter(safeRequest.getQuestionTier());
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate cutoffDate = today.minusDays(hotRetentionDays);
        LocalDate startDate = safeRequest.getStartDate();
        LocalDate endDate = safeRequest.getEndDate() == null || safeRequest.getEndDate().isAfter(cutoffDate)
                ? cutoffDate
                : safeRequest.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "No poll retention candidates: endDate is before startDate after hot-window clamp");
        }

        PollRetentionDryRunResponse response = new PollRetentionDryRunResponse();
        response.setDryRun(dryRun);
        response.setSimulationOnly(simulationOnly);
        response.setProjectId(safeRequest.getProjectId());
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setQuestionTier(questionTier);
        response.setHotRetentionDays(hotRetentionDays);
        response.setStuckBatchSealDays(stuckBatchSealDays);
        response.setLimit(limit);
        response.setReason(normalizeReason(safeRequest.getReason()));
        response.setCutoffDate(cutoffDate);

        Long runId = auditService.startRun("poll_results", dryRun ? "dry_run" : "execute",
                startDate, endDate, startMetrics(response));
        response.setRetentionRunId(runId);
        try {
            Cursor cursor = normalizeCursor(safeRequest);
            List<CandidateSlice> loaded = loadCandidateSlices(
                    safeRequest.getProjectId(), startDate, endDate, questionTier, cursor, limit + 1);
            boolean hasMore = loaded.size() > limit;
            List<CandidateSlice> candidates = hasMore ? loaded.subList(0, limit) : loaded;
            response.setHasMore(hasMore);
            if (hasMore && !candidates.isEmpty()) {
                CandidateSlice last = candidates.get(candidates.size() - 1);
                response.setNextCursorBatchDate(last.batchDate());
                response.setNextCursorProjectId(last.projectId());
                response.setNextCursorQuestionTier(last.questionTier());
            }
            for (CandidateSlice candidate : candidates) {
                PollRetentionDryRunSliceVO slice = evaluateSliceWithLock(candidate, today, stuckBatchSealDays);
                slice.setAction(Boolean.TRUE.equals(slice.getEligible()) ? "purge_poll_detail" : "blocked");
                slice.setResult(Boolean.TRUE.equals(slice.getEligible()) ? "pending" : "blocked");
                if (!dryRun && Boolean.TRUE.equals(slice.getEligible())) {
                    purgeSlice(candidate, slice, today, stuckBatchSealDays, runId, requestedBy,
                            safeRequest.getReason());
                }
                response.getSlices().add(slice);
            }
            summarize(response);
            long affected = dryRun
                    ? response.getPollResultRows()
                    : response.getDeletedRows().getOrDefault("poll_results", 0L);
            String runStatus = response.getFailedSlices() > 0 ? "failed" : "succeeded";
            auditService.finishRun(runId, runStatus, response.getCandidateSlices(), affected,
                    response.getBlockedSlices(), response.getWarningCount(), metrics(response),
                    response.getFailedSlices() > 0 ? "One or more poll retention slices failed" : null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateSlices(), response.getPollResultRows(),
                    response.getBlockedSlices(), response.getWarningCount() + 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private List<CandidateSlice> loadCandidateSlices(Long projectId,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     String questionTier,
                                                     Cursor cursor,
                                                     int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT pr.project_id,
                       pr.batch_date,
                       pr.question_tier,
                       COUNT(*) AS source_rows
                 FROM poll_results pr
                 WHERE NOT EXISTS (
                       SELECT 1
                         FROM data_retention_purged_slice purged
                        WHERE purged.domain = 'poll_results'
                          AND purged.project_id = pr.project_id
                          AND purged.batch_date = pr.batch_date
                          AND purged.question_tier = pr.question_tier
                          AND purged.status = 'purged'
                 )
                """);
        List<Object> args = new ArrayList<>();
        if (projectId != null) {
            sql.append("   AND pr.project_id = ?\n");
            args.add(projectId);
        }
        if (startDate != null) {
            sql.append("   AND pr.batch_date >= ?\n");
            args.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("   AND pr.batch_date <= ?\n");
            args.add(Date.valueOf(endDate));
        }
        if (StringUtils.hasText(questionTier)) {
            sql.append("   AND pr.question_tier = ?\n");
            args.add(questionTier);
        }
        if (cursor != null) {
            sql.append("""
                   AND (
                         pr.batch_date > ?
                      OR (pr.batch_date = ? AND pr.project_id > ?)
                      OR (pr.batch_date = ? AND pr.project_id = ? AND pr.question_tier > ?)
                   )
                    """);
            args.add(Date.valueOf(cursor.batchDate()));
            args.add(Date.valueOf(cursor.batchDate()));
            args.add(cursor.projectId());
            args.add(Date.valueOf(cursor.batchDate()));
            args.add(cursor.projectId());
            args.add(cursor.questionTier());
        }
        sql.append("""
                 GROUP BY pr.project_id, pr.batch_date, pr.question_tier
                 ORDER BY pr.batch_date ASC, pr.project_id ASC, pr.question_tier ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CandidateSlice(
                rs.getLong("project_id"),
                rs.getDate("batch_date").toLocalDate(),
                normalizeTier(rs.getString("question_tier")),
                rs.getLong("source_rows")
        ), args.toArray());
    }

    private PollRetentionDryRunSliceVO evaluateSliceWithLock(CandidateSlice candidate,
                                                             LocalDate today,
                                                             int stuckBatchSealDays) {
        return transactionTemplate.execute(status -> {
            lockSliceForUpdate(candidate.projectId(), candidate.batchDate(), candidate.questionTier());
            PollRetentionDryRunSliceVO slice = new PollRetentionDryRunSliceVO();
            slice.setProjectId(candidate.projectId());
            slice.setBatchDate(candidate.batchDate());
            slice.setQuestionTier(candidate.questionTier());
            slice.setPollResultRows(countPollResults(candidate));
            slice.setLatestLiveResultRows(countLatestLiveResults(candidate));
            slice.setShardRows(countShards(candidate));
            slice.setShardItemRows(countShardItems(candidate));
            applyDetailCounts(slice, candidate);
            applyPurgedGate(slice, candidate);
            applyBatchSealGate(slice, candidate, today, stuckBatchSealDays);
            applyReconciliationGate(slice, candidate);
            applyEntityJudgeReconciliationGate(slice, candidate);
            applyStaticGates(slice);
            slice.setEligible(slice.getBlockedReasons().isEmpty());
            return slice;
        });
    }

    private void purgeSlice(CandidateSlice candidate,
                            PollRetentionDryRunSliceVO responseSlice,
                            LocalDate today,
                            int stuckBatchSealDays,
                            Long retentionRunId,
                            Long requestedBy,
                            String reason) {
        Map<String, Long> deletedRows = new LinkedHashMap<>();
        PollPurgeRequest auditRequest = new PollPurgeRequest(
                candidate.projectId(),
                requestedBy,
                reason,
                toJson(Map.of(
                        "projectId", candidate.projectId(),
                        "batchDate", candidate.batchDate(),
                        "questionTier", candidate.questionTier(),
                        "retentionRunId", retentionRunId
                )));
        try {
            Long purgeAuditRunId = pollAuditPurgeService.execute(auditRequest, () -> {
                PollRetentionDryRunSliceVO refreshed =
                        evaluateSliceWithLock(candidate, today, stuckBatchSealDays);
                if (!Boolean.TRUE.equals(refreshed.getEligible())) {
                    throw new BizException(409,
                            "Poll retention slice is no longer eligible: "
                                    + String.join(",", refreshed.getBlockedReasons()));
                }
                deleteSliceRows(candidate, refreshed, retentionRunId, deletedRows);
                return toJson(deletedRows);
            });
            responseSlice.setPurgeAuditRunId(purgeAuditRunId);
            responseSlice.setDeletedRows(new LinkedHashMap<>(deletedRows));
            responseSlice.setResult("purged");
        } catch (Exception ex) {
            responseSlice.setResult("failed");
            responseSlice.setErrorMessage(trimError(ex.getMessage()));
        }
    }

    private void deleteSliceRows(CandidateSlice candidate,
                                 PollRetentionDryRunSliceVO refreshed,
                                 Long retentionRunId,
                                 Map<String, Long> deletedRows) {
        List<Long> purgeableResultIds = loadPurgeablePollResultIds(candidate);
        long expectedPurgeableRows = Math.max(0L,
                refreshed.getPollResultRows() - refreshed.getLatestLiveResultRows());
        if (purgeableResultIds.size() != expectedPurgeableRows) {
            throw new BizException(409, "Poll result latest projection changed during purge");
        }
        for (int offset = 0; offset < purgeableResultIds.size(); offset += 500) {
            List<Long> ids = purgeableResultIds.subList(
                    offset, Math.min(offset + 500, purgeableResultIds.size()));
            deletePollResultChunk(ids, deletedRows);
        }
        Object[] args = sliceArgs(candidate);
        deletedRows.put("poll_batch_shard_items", (long) jdbcTemplate.update("""
                DELETE i
                  FROM poll_batch_shard_items i
                  JOIN poll_batch_shards s ON s.id = i.shard_id
                 WHERE s.project_id = ?
                   AND s.batch_date = ?
                   AND s.question_tier = ?
                """, args));
        deletedRows.put("poll_batch_shards", (long) jdbcTemplate.update("""
                DELETE FROM poll_batch_shards
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, args));
        long pollResultsDeleted = deletedRows.getOrDefault("poll_results", 0L);
        if (pollResultsDeleted != expectedPurgeableRows) {
            throw new BizException(409, "Poll result row count changed during purge");
        }
        jdbcTemplate.update("""
                INSERT INTO data_retention_purged_slice (
                  domain, project_id, batch_date, question_tier, status,
                  retention_run_id, metrics_json
                ) VALUES ('poll_results', ?, ?, ?, 'purged', ?, ?)
                """, candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier(),
                retentionRunId, toJson(deletedRows));
    }

    private List<Long> loadPurgeablePollResultIds(CandidateSlice candidate) {
        return jdbcTemplate.queryForList("""
                SELECT pr.id
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date = ?
                   AND pr.question_tier = ?
                   AND (
                        COALESCE(pr.status, '') <> 'completed'
                        OR EXISTS (
                           SELECT 1
                             FROM poll_results newer
                            WHERE newer.project_id = pr.project_id
                              AND newer.question_tier = pr.question_tier
                              AND newer.status = 'completed'
                               AND %s
                              AND (
                                    (pr.keyword_result_id IS NOT NULL
                                     AND newer.keyword_result_id = pr.keyword_result_id)
                                 OR (pr.keyword_result_id IS NULL
                                     AND newer.keyword_result_id IS NULL
                                     AND LOWER(TRIM(COALESCE(newer.keyword_text_snapshot, '')))
                                         = LOWER(TRIM(COALESCE(pr.keyword_text_snapshot, ''))))
                              )
                              AND (
                                    newer.batch_date > pr.batch_date
                                 OR (newer.batch_date = pr.batch_date
                                     AND COALESCE(newer.updated_at, newer.created_at)
                                         > COALESCE(pr.updated_at, pr.created_at))
                                 OR (newer.batch_date = pr.batch_date
                                     AND COALESCE(newer.updated_at, newer.created_at)
                                         = COALESCE(pr.updated_at, pr.created_at)
                                     AND newer.id > pr.id)
                              )
                        )
                   )
                 ORDER BY pr.id ASC
                """.formatted(latestResultChannelMatchSql("newer", "pr")),
                Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()),
                candidate.questionTier());
    }

    static String latestResultChannelMatchSql(String newerAlias, String currentAlias) {
        return MobileDashboardAiPlatformCatalog.canonicalSqlExpression(rawChannelSql(newerAlias))
                + " <=> "
                + MobileDashboardAiPlatformCatalog.canonicalSqlExpression(rawChannelSql(currentAlias));
    }

    private static String rawChannelSql(String alias) {
        return "COALESCE(NULLIF(TRIM(" + alias + ".channel_code), ''), "
                + alias + ".platform_code)";
    }

    private void deletePollResultChunk(List<Long> ids, Map<String, Long> deletedRows) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", ids.stream().map(ignored -> "?").toList());
        Object[] args = ids.toArray();
        mergeDeleted(deletedRows, "poll_citations", jdbcTemplate.update("""
                DELETE c
                  FROM poll_citations c
                  JOIN poll_invocation_attempts a ON a.id = c.attempt_id
                 WHERE a.poll_result_id IN (%s)
                """.formatted(placeholders), args));
        mergeDeleted(deletedRows, "poll_search_sources", jdbcTemplate.update("""
                DELETE s
                  FROM poll_search_sources s
                  JOIN poll_invocation_attempts a ON a.id = s.attempt_id
                 WHERE a.poll_result_id IN (%s)
                """.formatted(placeholders), args));
        mergeDeleted(deletedRows, "poll_provider_calls", jdbcTemplate.update("""
                DELETE c
                  FROM poll_provider_calls c
                  JOIN poll_invocation_attempts a ON a.id = c.attempt_id
                 WHERE a.poll_result_id IN (%s)
                """.formatted(placeholders), args));
        mergeDeleted(deletedRows, "poll_invocation_attempts", jdbcTemplate.update("""
                DELETE FROM poll_invocation_attempts
                 WHERE poll_result_id IN (%s)
                """.formatted(placeholders), args));
        mergeDeleted(deletedRows, "poll_result_entity_judge", jdbcTemplate.update("""
                DELETE FROM poll_result_entity_judge
                 WHERE poll_result_id IN (%s)
                """.formatted(placeholders), args));
        mergeDeleted(deletedRows, "poll_results", jdbcTemplate.update("""
                DELETE FROM poll_results
                 WHERE id IN (%s)
                """.formatted(placeholders), args));
    }

    private void mergeDeleted(Map<String, Long> deletedRows, String table, int count) {
        deletedRows.merge(table, (long) count, Long::sum);
    }

    private Object[] sliceArgs(CandidateSlice candidate) {
        return new Object[]{
                candidate.projectId(),
                Date.valueOf(candidate.batchDate()),
                candidate.questionTier()
        };
    }

    private void applyPurgedGate(PollRetentionDryRunSliceVO slice, CandidateSlice candidate) {
        Long purged = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM data_retention_purged_slice
                 WHERE domain = 'poll_results'
                   AND project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                   AND status = 'purged'
                """, Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        if (purged != null && purged > 0) {
            slice.getBlockedReasons().add("slice_already_purged");
        }
    }

    private void applyBatchSealGate(PollRetentionDryRunSliceVO slice,
                                    CandidateSlice candidate,
                                    LocalDate today,
                                    int stuckBatchSealDays) {
        BatchGate gate = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS batch_rows,
                       SUM(CASE WHEN status IN ('finished', 'finished_with_failures', 'failed') THEN 0 ELSE 1 END) AS non_terminal_rows,
                       SUM(CASE WHEN status IN ('planning', 'ready', 'running')
                                  AND batch_date <= ?
                                THEN 1 ELSE 0 END) AS stale_non_terminal_rows
                  FROM poll_batches
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, (rs, rowNum) -> new BatchGate(
                rs.getLong("batch_rows"),
                rs.getLong("non_terminal_rows"),
                rs.getLong("stale_non_terminal_rows")
        ), Date.valueOf(today.minusDays(stuckBatchSealDays)),
                candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        if (gate == null) {
            slice.getBlockedReasons().add("poll_batch_missing");
            return;
        }
        slice.setBatchRows(gate.batchRows());
        slice.setNonTerminalBatchRows(gate.nonTerminalRows());
        slice.setStaleNonTerminalBatchRows(gate.staleNonTerminalRows());
        if (gate.batchRows() == 0) {
            slice.getBlockedReasons().add("poll_batch_missing");
        }
        if (gate.nonTerminalRows() > 0) {
            slice.getBlockedReasons().add("poll_batch_not_sealed");
        }
        if (gate.staleNonTerminalRows() > 0) {
            slice.getWarnings().add("stale_non_terminal_batch_requires_manual_resolution");
        }
    }

    private void applyReconciliationGate(PollRetentionDryRunSliceVO slice, CandidateSlice candidate) {
        PollSummaryRecomputeService.SummaryVerification verification =
                pollSummaryRecomputeService.verifySlice(
                        candidate.projectId(), candidate.batchDate(), candidate.questionTier());
        slice.setKeywordSummaryRows((long) verification.keywordSummaryCount());
        slice.setKeywordSummarySourceRows(verification.keywordSummarySourceRowCount());
        slice.setPlatformSummaryRows((long) verification.platformSummaryCount());
        slice.setPlatformSummarySourceRows(verification.platformSummarySourceRowCount());
        if (!verification.keywordMatched()) {
            slice.getBlockedReasons().add("keyword_summary_reconciliation_failed");
        }
        if (!verification.platformMatched()) {
            slice.getBlockedReasons().add("platform_summary_reconciliation_failed");
        }
    }

    private void applyStaticGates(PollRetentionDryRunSliceVO slice) {
        // Code-level rollout gates are intentionally explicit in dry-run output.
        // They become configurable before execute is enabled.
        Map<String, Object> readPathGate = new LinkedHashMap<>();
        readPathGate.put("gate", "poll_detail_read_path_switched");
        readPathGate.put("passed", true);
        Map<String, Object> businessGate = new LinkedHashMap<>();
        businessGate.put("gate", "business_visibility_policy_recorded");
        businessGate.put("passed", true);
        slice.getFreezeGates().add(readPathGate);
        slice.getFreezeGates().add(businessGate);
    }

    private long countPollResults(CandidateSlice candidate) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM poll_results
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        return count == null ? 0L : count;
    }

    private long countLatestLiveResults(CandidateSlice candidate) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date = ?
                   AND pr.question_tier = ?
                   AND pr.status = 'completed'
                   AND NOT EXISTS (
                         SELECT 1
                           FROM poll_results newer
                          WHERE newer.project_id = pr.project_id
                            AND newer.question_tier = pr.question_tier
                            AND newer.status = 'completed'
                            AND %s
                            AND (
                                  (pr.keyword_result_id IS NOT NULL
                                   AND newer.keyword_result_id = pr.keyword_result_id)
                               OR (pr.keyword_result_id IS NULL
                                   AND newer.keyword_result_id IS NULL
                                   AND LOWER(TRIM(COALESCE(newer.keyword_text_snapshot, '')))
                                       = LOWER(TRIM(COALESCE(pr.keyword_text_snapshot, ''))))
                            )
                            AND (
                                  newer.batch_date > pr.batch_date
                               OR (newer.batch_date = pr.batch_date
                                   AND COALESCE(newer.updated_at, newer.created_at)
                                       > COALESCE(pr.updated_at, pr.created_at))
                               OR (newer.batch_date = pr.batch_date
                                   AND COALESCE(newer.updated_at, newer.created_at)
                                       = COALESCE(pr.updated_at, pr.created_at)
                                   AND newer.id > pr.id)
                            )
                   )
                """.formatted(latestResultChannelMatchSql("newer", "pr")),
                Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()),
                candidate.questionTier());
        return count == null ? 0L : count;
    }

    private long countShards(CandidateSlice candidate) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM poll_batch_shards
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        return count == null ? 0L : count;
    }

    private long countShardItems(CandidateSlice candidate) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM poll_batch_shard_items i
                  JOIN poll_batch_shards s ON s.id = i.shard_id
                 WHERE s.project_id = ?
                   AND s.batch_date = ?
                   AND s.question_tier = ?
                """, Long.class, candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        return count == null ? 0L : count;
    }

    private void applyDetailCounts(PollRetentionDryRunSliceVO slice, CandidateSlice candidate) {
        DetailCounts counts = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*)
                     FROM poll_invocation_attempts a
                     JOIN poll_results pr ON pr.id = a.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?) AS attempt_rows,
                  (SELECT COUNT(*)
                     FROM poll_invocation_attempts a
                     JOIN poll_results pr ON pr.id = a.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?
                      AND a.status NOT IN ('SUCCEEDED', 'FAILED', 'ABANDONED')) AS non_terminal_attempt_rows,
                  (SELECT COUNT(*)
                     FROM poll_provider_calls c
                     JOIN poll_invocation_attempts a ON a.id = c.attempt_id
                     JOIN poll_results pr ON pr.id = a.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?) AS provider_call_rows,
                  (SELECT COUNT(*)
                     FROM poll_search_sources s
                     JOIN poll_invocation_attempts a ON a.id = s.attempt_id
                     JOIN poll_results pr ON pr.id = a.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?) AS search_source_rows,
                  (SELECT COUNT(*)
                     FROM poll_citations c
                     JOIN poll_invocation_attempts a ON a.id = c.attempt_id
                     JOIN poll_results pr ON pr.id = a.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?) AS citation_rows,
                  (SELECT COUNT(*)
                     FROM poll_result_entity_judge j
                     JOIN poll_results pr ON pr.id = j.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?) AS entity_judge_rows,
                  (SELECT COUNT(*)
                     FROM poll_result_entity_judge j
                     JOIN poll_results pr ON pr.id = j.poll_result_id
                    WHERE pr.project_id = ?
                      AND pr.batch_date = ?
                      AND pr.question_tier = ?
                      AND j.judge_status = 'success') AS successful_entity_judge_rows
                """, (rs, rowNum) -> new DetailCounts(
                rs.getLong("attempt_rows"),
                rs.getLong("non_terminal_attempt_rows"),
                rs.getLong("provider_call_rows"),
                rs.getLong("search_source_rows"),
                rs.getLong("citation_rows"),
                rs.getLong("entity_judge_rows"),
                rs.getLong("successful_entity_judge_rows")
        ), repeatedSliceArgs(candidate, 7));
        if (counts == null) {
            return;
        }
        slice.setInvocationAttemptRows(counts.attemptRows());
        slice.setNonTerminalAttemptRows(counts.nonTerminalAttemptRows());
        slice.setProviderCallRows(counts.providerCallRows());
        slice.setSearchSourceRows(counts.searchSourceRows());
        slice.setCitationRows(counts.citationRows());
        slice.setEntityJudgeRows(counts.entityJudgeRows());
        slice.setSuccessfulEntityJudgeRows(counts.successfulEntityJudgeRows());
        if (counts.nonTerminalAttemptRows() > 0) {
            slice.getBlockedReasons().add("poll_invocation_attempt_not_terminal");
        }
    }

    private void applyEntityJudgeReconciliationGate(PollRetentionDryRunSliceVO slice,
                                                     CandidateSlice candidate) {
        MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification verification =
                mobileDashboardEntityJudgeService.verifySummarySlice(
                        candidate.projectId(), candidate.batchDate(), candidate.questionTier());
        slice.setEntityJudgeSummaryRows(verification.summaryRowCount());
        slice.setEntityJudgeSummarySuccessRows(verification.summarySuccessRowCount());
        if (!verification.matched()) {
            slice.getBlockedReasons().add("entity_judge_summary_reconciliation_failed");
        }
    }

    private Object[] repeatedSliceArgs(CandidateSlice candidate, int repetitions) {
        List<Object> args = new ArrayList<>(repetitions * 3);
        for (int i = 0; i < repetitions; i++) {
            args.add(candidate.projectId());
            args.add(Date.valueOf(candidate.batchDate()));
            args.add(candidate.questionTier());
        }
        return args.toArray();
    }

    private void lockSliceForUpdate(Long projectId, LocalDate batchDate, String questionTier) {
        sliceLockService.lockSlice(projectId, batchDate, questionTier);
    }

    private void summarize(PollRetentionDryRunResponse response) {
        response.setCandidateSlices(response.getSlices().size());
        response.setEligibleSlices((int) response.getSlices().stream().filter(slice -> Boolean.TRUE.equals(slice.getEligible())).count());
        response.setBlockedSlices(response.getCandidateSlices() - response.getEligibleSlices());
        response.setWarningCount(response.getSlices().stream().mapToInt(slice -> slice.getWarnings().size()).sum());
        response.setPollResultRows(response.getSlices().stream().mapToLong(slice -> slice.getPollResultRows() == null ? 0L : slice.getPollResultRows()).sum());
        response.setShardRows(response.getSlices().stream().mapToLong(slice -> slice.getShardRows() == null ? 0L : slice.getShardRows()).sum());
        response.setShardItemRows(response.getSlices().stream().mapToLong(slice -> slice.getShardItemRows() == null ? 0L : slice.getShardItemRows()).sum());
        response.setInvocationAttemptRows(response.getSlices().stream()
                .mapToLong(slice -> slice.getInvocationAttemptRows() == null ? 0L : slice.getInvocationAttemptRows())
                .sum());
        response.setProviderCallRows(response.getSlices().stream()
                .mapToLong(slice -> slice.getProviderCallRows() == null ? 0L : slice.getProviderCallRows())
                .sum());
        response.setSearchSourceRows(response.getSlices().stream()
                .mapToLong(slice -> slice.getSearchSourceRows() == null ? 0L : slice.getSearchSourceRows())
                .sum());
        response.setCitationRows(response.getSlices().stream()
                .mapToLong(slice -> slice.getCitationRows() == null ? 0L : slice.getCitationRows())
                .sum());
        response.setEntityJudgeRows(response.getSlices().stream()
                .mapToLong(slice -> slice.getEntityJudgeRows() == null ? 0L : slice.getEntityJudgeRows())
                .sum());
        response.setPurgedSlices((int) response.getSlices().stream()
                .filter(slice -> "purged".equals(slice.getResult()))
                .count());
        response.setFailedSlices((int) response.getSlices().stream()
                .filter(slice -> "failed".equals(slice.getResult()))
                .count());
        Map<String, Long> deletedRows = new LinkedHashMap<>();
        for (PollRetentionDryRunSliceVO slice : response.getSlices()) {
            slice.getDeletedRows().forEach((table, count) -> deletedRows.merge(table, count, Long::sum));
        }
        response.setDeletedRows(deletedRows);
    }

    private Map<String, Object> startMetrics(PollRetentionDryRunResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", response.getProjectId());
        metrics.put("simulationOnly", response.getSimulationOnly());
        if (response.getReason() != null) {
            metrics.put("reason", response.getReason());
        }
        metrics.put("questionTier", response.getQuestionTier());
        metrics.put("hotRetentionDays", response.getHotRetentionDays());
        metrics.put("stuckBatchSealDays", response.getStuckBatchSealDays());
        metrics.put("cutoffDate", response.getCutoffDate());
        metrics.put("limit", response.getLimit());
        metrics.put("hasMore", response.getHasMore());
        metrics.put("nextCursorBatchDate", response.getNextCursorBatchDate());
        metrics.put("nextCursorProjectId", response.getNextCursorProjectId());
        metrics.put("nextCursorQuestionTier", response.getNextCursorQuestionTier());
        return metrics;
    }

    private Map<String, Object> metrics(PollRetentionDryRunResponse response) {
        Map<String, Object> metrics = startMetrics(response);
        metrics.put("candidateSlices", response.getCandidateSlices());
        metrics.put("eligibleSlices", response.getEligibleSlices());
        metrics.put("blockedSlices", response.getBlockedSlices());
        metrics.put("warningCount", response.getWarningCount());
        metrics.put("pollResultRows", response.getPollResultRows());
        metrics.put("shardRows", response.getShardRows());
        metrics.put("shardItemRows", response.getShardItemRows());
        metrics.put("invocationAttemptRows", response.getInvocationAttemptRows());
        metrics.put("providerCallRows", response.getProviderCallRows());
        metrics.put("searchSourceRows", response.getSearchSourceRows());
        metrics.put("citationRows", response.getCitationRows());
        metrics.put("entityJudgeRows", response.getEntityJudgeRows());
        metrics.put("purgedSlices", response.getPurgedSlices());
        metrics.put("failedSlices", response.getFailedSlices());
        metrics.put("deletedRows", response.getDeletedRows());
        return metrics;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize poll retention metrics", ex);
        }
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private Cursor normalizeCursor(PollRetentionDryRunRequest request) {
        boolean hasAny = request.getCursorBatchDate() != null
                || request.getCursorProjectId() != null
                || StringUtils.hasText(request.getCursorQuestionTier());
        if (!hasAny) {
            return null;
        }
        if (request.getCursorBatchDate() == null
                || request.getCursorProjectId() == null
                || !StringUtils.hasText(request.getCursorQuestionTier())) {
            throw new BizException(400, "cursorBatchDate, cursorProjectId and cursorQuestionTier must be provided together");
        }
        return new Cursor(
                request.getCursorBatchDate(),
                request.getCursorProjectId(),
                normalizeTier(request.getCursorQuestionTier())
        );
    }

    private int normalizePositive(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private String normalizeTierFilter(String questionTier) {
        return StringUtils.hasText(questionTier) ? normalizeTier(questionTier) : null;
    }

    private String normalizeTier(String questionTier) {
        return StringUtils.hasText(questionTier) ? questionTier.trim().toUpperCase(Locale.ROOT) : "A";
    }

    private record CandidateSlice(Long projectId, LocalDate batchDate, String questionTier, long sourceRows) {
    }

    private record Cursor(LocalDate batchDate, Long projectId, String questionTier) {
    }

    private record BatchGate(long batchRows, long nonTerminalRows, long staleNonTerminalRows) {
    }

    private record DetailCounts(long attemptRows,
                                long nonTerminalAttemptRows,
                                long providerCallRows,
                                long searchSourceRows,
                                long citationRows,
                                long entityJudgeRows,
                                long successfulEntityJudgeRows) {
    }

}
