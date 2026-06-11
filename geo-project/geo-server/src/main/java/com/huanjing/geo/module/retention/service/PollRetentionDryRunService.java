package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunSliceVO;
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
    private static final int DEFAULT_HOT_RETENTION_DAYS = 120;
    private static final int DEFAULT_STUCK_BATCH_SEAL_DAYS = 7;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;

    public PollRetentionDryRunResponse dryRun(PollRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        int hotRetentionDays = normalizePositive(request.getHotRetentionDays(), DEFAULT_HOT_RETENTION_DAYS);
        int stuckBatchSealDays = normalizePositive(request.getStuckBatchSealDays(), DEFAULT_STUCK_BATCH_SEAL_DAYS);
        int limit = normalizeLimit(request.getLimit());
        String questionTier = normalizeTierFilter(request.getQuestionTier());
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate cutoffDate = today.minusDays(hotRetentionDays);
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() == null || request.getEndDate().isAfter(cutoffDate)
                ? cutoffDate
                : request.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "No poll retention candidates: endDate is before startDate after hot-window clamp");
        }

        PollRetentionDryRunResponse response = new PollRetentionDryRunResponse();
        response.setProjectId(request.getProjectId());
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setQuestionTier(questionTier);
        response.setHotRetentionDays(hotRetentionDays);
        response.setStuckBatchSealDays(stuckBatchSealDays);
        response.setLimit(limit);
        response.setCutoffDate(cutoffDate);

        Long runId = auditService.startRun("poll_results", "dry_run", startDate, endDate, startMetrics(response));
        response.setRetentionRunId(runId);
        try {
            Cursor cursor = normalizeCursor(request);
            List<CandidateSlice> loaded = loadCandidateSlices(request.getProjectId(), startDate, endDate, questionTier, cursor, limit + 1);
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
                response.getSlices().add(slice);
            }
            summarize(response);
            auditService.finishRun(runId, "succeeded", response.getCandidateSlices(), response.getPollResultRows(),
                    response.getBlockedSlices(), response.getWarningCount(), metrics(response), null);
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
                 WHERE 1 = 1
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
            slice.setShardRows(countShards(candidate));
            slice.setShardItemRows(countShardItems(candidate));
            applyPurgedGate(slice, candidate);
            applyBatchSealGate(slice, candidate, today, stuckBatchSealDays);
            applyReconciliationGate(slice, candidate);
            applyFreezeGate(slice, candidate);
            applyStaticGates(slice);
            slice.setEligible(slice.getBlockedReasons().isEmpty());
            return slice;
        });
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
        if (gate.nonTerminalRows() > gate.staleNonTerminalRows()) {
            slice.getBlockedReasons().add("poll_batch_not_sealed");
        }
        if (gate.staleNonTerminalRows() > 0) {
            slice.getWarnings().add("stale_batch_would_be_failed_by_safety_valve");
        }
    }

    private void applyReconciliationGate(PollRetentionDryRunSliceVO slice, CandidateSlice candidate) {
        SummaryGate keyword = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS summary_rows,
                       COALESCE(SUM(source_row_count), 0) AS summary_source_rows
                  FROM poll_keyword_daily_summary
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, (rs, rowNum) -> new SummaryGate(rs.getLong("summary_rows"), rs.getLong("summary_source_rows")),
                candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        SummaryGate platform = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS summary_rows,
                       COALESCE(SUM(source_row_count), 0) AS summary_source_rows
                  FROM poll_platform_daily_summary
                 WHERE project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                """, (rs, rowNum) -> new SummaryGate(rs.getLong("summary_rows"), rs.getLong("summary_source_rows")),
                candidate.projectId(), Date.valueOf(candidate.batchDate()), candidate.questionTier());
        if (keyword != null) {
            slice.setKeywordSummaryRows(keyword.summaryRows());
            slice.setKeywordSummarySourceRows(keyword.summarySourceRows());
        }
        if (platform != null) {
            slice.setPlatformSummaryRows(platform.summaryRows());
            slice.setPlatformSummarySourceRows(platform.summarySourceRows());
        }
        long liveRows = slice.getPollResultRows() == null ? 0L : slice.getPollResultRows();
        if (liveRows > 0 && (keyword == null || keyword.summaryRows() == 0 || keyword.summarySourceRows() != liveRows)) {
            slice.getBlockedReasons().add("keyword_summary_reconciliation_failed");
        }
        if (liveRows > 0 && (platform == null || platform.summaryRows() == 0 || platform.summarySourceRows() != liveRows)) {
            slice.getBlockedReasons().add("platform_summary_reconciliation_failed");
        }
    }

    private void applyFreezeGate(PollRetentionDryRunSliceVO slice, CandidateSlice candidate) {
        for (DetailReportType reportType : enabledDetailReportTypes(candidate.batchDate())) {
            boolean frozen = isReportFrozen(candidate.projectId(), reportType.reportType(), reportType.periodKey());
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("reportType", reportType.reportType());
            gate.put("periodKey", reportType.periodKey());
            gate.put("frozen", frozen);
            slice.getFreezeGates().add(gate);
            if (!frozen) {
                slice.getBlockedReasons().add("report_freeze_missing:" + reportType.reportType() + ":" + reportType.periodKey());
            }
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

    private boolean isReportFrozen(Long projectId, String reportType, String periodKey) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM report_period_freeze
                 WHERE project_id = ?
                   AND report_type = ?
                   AND period_key = ?
                   AND status = 'FROZEN'
                """, Long.class, projectId, reportType, periodKey);
        return count != null && count > 0;
    }

    private List<DetailReportType> enabledDetailReportTypes(LocalDate batchDate) {
        return List.of(new DetailReportType("quarterly", quarterKey(batchDate)));
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

    private void lockSliceForUpdate(Long projectId, LocalDate batchDate, String questionTier) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO data_retention_recompute_slice_lock (
                  domain, project_id, batch_date, question_tier
                ) VALUES ('poll_results', ?, ?, ?)
                """, projectId, Date.valueOf(batchDate), questionTier);
        Long lockId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM data_retention_recompute_slice_lock
                 WHERE domain = 'poll_results'
                   AND project_id = ?
                   AND batch_date = ?
                   AND question_tier = ?
                 FOR UPDATE
                """, Long.class, projectId, Date.valueOf(batchDate), questionTier);
        if (lockId == null) {
            throw new IllegalStateException("Poll retention slice lock row not found");
        }
    }

    private void summarize(PollRetentionDryRunResponse response) {
        response.setCandidateSlices(response.getSlices().size());
        response.setEligibleSlices((int) response.getSlices().stream().filter(slice -> Boolean.TRUE.equals(slice.getEligible())).count());
        response.setBlockedSlices(response.getCandidateSlices() - response.getEligibleSlices());
        response.setWarningCount(response.getSlices().stream().mapToInt(slice -> slice.getWarnings().size()).sum());
        response.setPollResultRows(response.getSlices().stream().mapToLong(slice -> slice.getPollResultRows() == null ? 0L : slice.getPollResultRows()).sum());
        response.setShardRows(response.getSlices().stream().mapToLong(slice -> slice.getShardRows() == null ? 0L : slice.getShardRows()).sum());
        response.setShardItemRows(response.getSlices().stream().mapToLong(slice -> slice.getShardItemRows() == null ? 0L : slice.getShardItemRows()).sum());
    }

    private Map<String, Object> startMetrics(PollRetentionDryRunResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", response.getProjectId());
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
        return metrics;
    }

    private String quarterKey(LocalDate date) {
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;
        // Must stay identical to ReportPeriodFreezeService.parseQuarter format: yyyyQn.
        return date.getYear() + "Q" + quarter;
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

    private record SummaryGate(long summaryRows, long summarySourceRows) {
    }

    private record DetailReportType(String reportType, String periodKey) {
    }
}
