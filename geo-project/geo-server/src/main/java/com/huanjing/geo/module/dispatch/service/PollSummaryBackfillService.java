package com.huanjing.geo.module.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillRequest;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillResponse;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillSliceVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PollSummaryBackfillService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final PollSummaryRecomputeService pollSummaryRecomputeService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public PollSummaryBackfillResponse backfill(PollSummaryBackfillRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        SysUser operator = currentUserService.requireCurrentUser();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        if (startDate == null || endDate == null) {
            throw new BizException(400, "startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BizException(400, "endDate must be greater than or equal to startDate");
        }

        boolean dryRun = request.getDryRun() == null || request.getDryRun();
        int limit = normalizeLimit(request.getLimit());
        String questionTier = normalizeTierFilter(request.getQuestionTier());
        Cursor cursor = normalizeCursor(request);
        List<CandidateSlice> loaded = loadCandidateSlices(request.getProjectId(), startDate, endDate, questionTier, cursor, limit + 1);
        boolean hasMore = loaded.size() > limit;
        List<CandidateSlice> candidates = hasMore ? loaded.subList(0, limit) : loaded;

        PollSummaryBackfillResponse response = baseResponse(request.getProjectId(), startDate, endDate, questionTier, dryRun, limit);
        response.setHasMore(hasMore);
        response.setCandidateSlices(candidates.size());
        if (hasMore && !candidates.isEmpty()) {
            CandidateSlice last = candidates.get(candidates.size() - 1);
            response.setNextCursorBatchDate(last.batchDate());
            response.setNextCursorProjectId(last.projectId());
            response.setNextCursorQuestionTier(last.questionTier());
        }
        Long retentionRunId = dryRun ? null : createRetentionRun(operator.getId(), request, response);
        response.setRetentionRunId(retentionRunId);
        try {
            for (CandidateSlice candidate : candidates) {
                if (dryRun) {
                    appendDryRunSlice(response, candidate);
                } else {
                    recomputeCandidate(response, candidate);
                }
            }
            if (retentionRunId != null) {
                finishRetentionRun(retentionRunId, response);
            }
            return response;
        } catch (Exception ex) {
            if (retentionRunId != null) {
                failRetentionRun(retentionRunId, response, ex);
            }
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
                       COUNT(*) AS source_row_count
                  FROM poll_results pr
                 WHERE pr.batch_date >= ?
                   AND pr.batch_date <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(startDate));
        args.add(Date.valueOf(endDate));
        if (projectId != null) {
            sql.append("   AND pr.project_id = ?\n");
            args.add(projectId);
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
                rs.getString("question_tier"),
                rs.getLong("source_row_count")
        ), args.toArray());
    }

    private void appendDryRunSlice(PollSummaryBackfillResponse response, CandidateSlice candidate) {
        PollSummaryBackfillSliceVO slice = new PollSummaryBackfillSliceVO();
        slice.setProjectId(candidate.projectId());
        slice.setBatchDate(candidate.batchDate());
        slice.setQuestionTier(candidate.questionTier());
        slice.setSourceRowCount(candidate.sourceRowCount());
        slice.setDryRun(true);
        response.getSlices().add(slice);
        response.setSourceRows(response.getSourceRows() + candidate.sourceRowCount());
    }

    private void recomputeCandidate(PollSummaryBackfillResponse response, CandidateSlice candidate) {
        PollSummaryBackfillSliceVO slice = new PollSummaryBackfillSliceVO();
        slice.setProjectId(candidate.projectId());
        slice.setBatchDate(candidate.batchDate());
        slice.setQuestionTier(candidate.questionTier());
        slice.setDryRun(false);
        try {
            PollSummaryRecomputeService.RecomputeResult result = pollSummaryRecomputeService.recomputeSlice(
                    candidate.projectId(), candidate.batchDate(), candidate.questionTier());
            slice.setSkipped(result.skipped());
            slice.setSkipReason(result.skipReason());
            slice.setSourceRowCount((long) result.sourceRowCount());
            slice.setKeywordSummaryCount(result.keywordSummaryCount());
            slice.setPlatformSummaryCount(result.platformSummaryCount());
            slice.setKeywordZombieDeleted(result.keywordZombieDeleted());
            slice.setPlatformZombieDeleted(result.platformZombieDeleted());
            response.setSourceRows(response.getSourceRows() + result.sourceRowCount());
            response.setKeywordSummaryRows(response.getKeywordSummaryRows() + result.keywordSummaryCount());
            response.setPlatformSummaryRows(response.getPlatformSummaryRows() + result.platformSummaryCount());
            response.setKeywordZombieDeleted(response.getKeywordZombieDeleted() + result.keywordZombieDeleted());
            response.setPlatformZombieDeleted(response.getPlatformZombieDeleted() + result.platformZombieDeleted());
            if (result.skipped()) {
                response.setSkippedSlices(response.getSkippedSlices() + 1);
            } else {
                response.setRecomputedSlices(response.getRecomputedSlices() + 1);
            }
        } catch (Exception ex) {
            slice.setFailed(true);
            slice.setErrorMessage(ex.getMessage());
            slice.setSourceRowCount(candidate.sourceRowCount());
            response.setFailedSlices(response.getFailedSlices() + 1);
        }
        response.getSlices().add(slice);
    }

    private PollSummaryBackfillResponse baseResponse(Long projectId,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     String questionTier,
                                                     boolean dryRun,
                                                     int limit) {
        PollSummaryBackfillResponse response = new PollSummaryBackfillResponse();
        response.setDryRun(dryRun);
        response.setProjectId(projectId);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setQuestionTier(questionTier);
        response.setLimit(limit);
        return response;
    }

    private Long createRetentionRun(Long operatorId, PollSummaryBackfillRequest request, PollSummaryBackfillResponse response) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO data_retention_run (
                      domain, mode, status, retention_window_start, retention_window_end,
                      candidate_count, affected_count, skipped_count, warning_count, metrics_json,
                      approved_by, approved_at, started_at
                    ) VALUES (
                      'poll_summary_backfill', 'execute', 'running', ?, ?,
                      ?, 0, 0, 0, ?, ?, ?, CURRENT_TIMESTAMP
                    )
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setDate(1, Date.valueOf(response.getStartDate()));
            ps.setDate(2, Date.valueOf(response.getEndDate()));
            ps.setLong(3, response.getCandidateSlices());
            ps.setString(4, metricsJson(request, response));
            ps.setLong(5, operatorId);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(500, "Failed to create retention run audit record");
        }
        return key.longValue();
    }

    private void finishRetentionRun(Long retentionRunId, PollSummaryBackfillResponse response) {
        String status = response.getFailedSlices() > 0 ? "failed" : "succeeded";
        jdbcTemplate.update("""
                UPDATE data_retention_run
                   SET status = ?,
                       affected_count = ?,
                       skipped_count = ?,
                       warning_count = ?,
                       metrics_json = ?,
                       error_message = ?,
                       finished_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                status,
                response.getRecomputedSlices(),
                response.getSkippedSlices(),
                response.getFailedSlices(),
                metricsJson(null, response),
                errorSummary(response),
                retentionRunId);
    }

    private void failRetentionRun(Long retentionRunId, PollSummaryBackfillResponse response, Exception ex) {
        jdbcTemplate.update("""
                UPDATE data_retention_run
                   SET status = 'failed',
                       affected_count = ?,
                       skipped_count = ?,
                       warning_count = ?,
                       metrics_json = ?,
                       error_message = ?,
                       finished_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                response.getRecomputedSlices(),
                response.getSkippedSlices(),
                response.getFailedSlices() + 1,
                metricsJson(null, response),
                trimError(ex.getMessage()),
                retentionRunId);
    }

    private String errorSummary(PollSummaryBackfillResponse response) {
        if (response.getFailedSlices() == null || response.getFailedSlices() == 0) {
            return null;
        }
        return "poll summary backfill failed slices: " + response.getFailedSlices();
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "poll summary backfill failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private String metricsJson(PollSummaryBackfillRequest request, PollSummaryBackfillResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", response.getProjectId());
        metrics.put("questionTier", response.getQuestionTier());
        metrics.put("limit", response.getLimit());
        metrics.put("hasMore", response.getHasMore());
        metrics.put("nextCursorBatchDate", response.getNextCursorBatchDate());
        metrics.put("nextCursorProjectId", response.getNextCursorProjectId());
        metrics.put("nextCursorQuestionTier", response.getNextCursorQuestionTier());
        metrics.put("candidateSlices", response.getCandidateSlices());
        metrics.put("recomputedSlices", response.getRecomputedSlices());
        metrics.put("skippedSlices", response.getSkippedSlices());
        metrics.put("failedSlices", response.getFailedSlices());
        metrics.put("sourceRows", response.getSourceRows());
        metrics.put("keywordSummaryRows", response.getKeywordSummaryRows());
        metrics.put("platformSummaryRows", response.getPlatformSummaryRows());
        metrics.put("keywordZombieDeleted", response.getKeywordZombieDeleted());
        metrics.put("platformZombieDeleted", response.getPlatformZombieDeleted());
        if (request != null) {
            metrics.put("cursorBatchDate", request.getCursorBatchDate());
            metrics.put("cursorProjectId", request.getCursorProjectId());
            metrics.put("cursorQuestionTier", request.getCursorQuestionTier());
        }
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException ex) {
            throw new BizException("Failed to serialize backfill metrics", ex);
        }
    }

    private Cursor normalizeCursor(PollSummaryBackfillRequest request) {
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
                request.getCursorQuestionTier().trim().toUpperCase(Locale.ROOT)
        );
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private static String normalizeTierFilter(String questionTier) {
        return StringUtils.hasText(questionTier) ? questionTier.trim().toUpperCase(Locale.ROOT) : null;
    }

    private record CandidateSlice(Long projectId, LocalDate batchDate, String questionTier, long sourceRowCount) {
    }

    private record Cursor(LocalDate batchDate, Long projectId, String questionTier) {
    }
}
