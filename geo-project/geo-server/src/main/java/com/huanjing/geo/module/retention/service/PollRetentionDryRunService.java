package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunSliceVO;
import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAiPlatformCatalog;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PollRetentionDryRunService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_HOT_RETENTION_DAYS = 14;
    private static final int DEFAULT_STUCK_BATCH_SEAL_DAYS = 7;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final int MAX_PAGES = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final PollSummaryRecomputeService pollSummaryRecomputeService;
    private final MobileDashboardEntityJudgeService mobileDashboardEntityJudgeService;
    private final DataRetentionProperties retentionProperties;
    private final DataRetentionP0AuditService typedAudit;
    private final DataRetentionCanonicalEncoder canonicalEncoder;
    private final Clock clock;

    @Autowired
    public PollRetentionDryRunService(JdbcTemplate jdbcTemplate,
                                      CurrentUserService currentUserService,
                                      PollSummaryRecomputeService pollSummaryRecomputeService,
                                      MobileDashboardEntityJudgeService mobileDashboardEntityJudgeService,
                                      DataRetentionProperties retentionProperties,
                                      DataRetentionP0AuditService typedAudit,
                                      DataRetentionCanonicalEncoder canonicalEncoder) {
        this(jdbcTemplate, currentUserService, pollSummaryRecomputeService, mobileDashboardEntityJudgeService,
                retentionProperties, typedAudit, canonicalEncoder, Clock.systemUTC());
    }

    PollRetentionDryRunService(JdbcTemplate jdbcTemplate,
                               CurrentUserService currentUserService,
                               PollSummaryRecomputeService pollSummaryRecomputeService,
                               MobileDashboardEntityJudgeService mobileDashboardEntityJudgeService,
                               DataRetentionProperties retentionProperties,
                               DataRetentionP0AuditService typedAudit,
                               DataRetentionCanonicalEncoder canonicalEncoder,
                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.pollSummaryRecomputeService = pollSummaryRecomputeService;
        this.mobileDashboardEntityJudgeService = mobileDashboardEntityJudgeService;
        this.retentionProperties = retentionProperties;
        this.typedAudit = typedAudit;
        this.canonicalEncoder = canonicalEncoder;
        this.clock = clock;
    }

    public PollRetentionDryRunResponse dryRun(PollRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        Long requestedBy = currentUserService.requireCurrentUser().getId();
        return runTypedDryRun(request, requestedBy, 1);
    }

    public PollRetentionDryRunResponse purge(PollRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        throw new BizException(409, "Poll purge mutation is only available through the execute coordinator");
    }

    public PollRetentionDryRunResponse runScheduled(PollRetentionDryRunRequest request,
                                                    boolean dryRun,
                                                    long requestedBy) {
        if (!dryRun && !retentionProperties.getPollResults().isExecuteEnabled()) {
            throw new BizException(403,
                    "Poll result purge execute is disabled by geo.retention.poll-results.execute-enabled");
        }
        if (!dryRun) {
            throw new BizException(409, "Scheduled poll mutation is only available through the execute coordinator");
        }
        return runTypedDryRun(request, requestedBy, 1);
    }

    public PollRetentionDryRunResponse runScheduledDryRun(PollRetentionDryRunRequest request,
                                                           int maxPages,
                                                           long requestedBy) {
        if (requestedBy <= 0 || maxPages < 1 || maxPages > MAX_PAGES) {
            throw new IllegalArgumentException("Poll scheduled dry-run policy is invalid");
        }
        return runTypedDryRun(request, requestedBy, maxPages);
    }

    private PollRetentionDryRunResponse runTypedDryRun(PollRetentionDryRunRequest request,
                                                        long requestedBy,
                                                        int maxPages) {
        PollRetentionDryRunRequest safe = request == null ? new PollRetentionDryRunRequest() : request;
        int requestedHotDays = normalizePositive(safe.getHotRetentionDays(), DEFAULT_HOT_RETENTION_DAYS);
        int stuckDays = normalizePositive(safe.getStuckBatchSealDays(), DEFAULT_STUCK_BATCH_SEAL_DAYS);
        int limit = normalizeLimit(safe.getLimit());
        String tier = normalizeTierFilter(safe.getQuestionTier());
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate cutoff = today.minusDays(requestedHotDays);
        LocalDate start = safe.getStartDate();
        LocalDate end = safe.getEndDate() == null || safe.getEndDate().isAfter(cutoff) ? cutoff : safe.getEndDate();
        if (start != null && start.isAfter(end)) {
            throw new BizException(400, "No poll retention candidates after hot-window clamp");
        }
        Cursor cursor = normalizeCursor(safe);
        Instant startedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var fingerprint = canonicalEncoder.pollFingerprint(requestedHotDays, stuckDays);
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", DataRetentionCanonicalEncoder.SCHEMA_VERSION, fingerprint.sha256(),
                DataRetentionCanonicalEncoder.POLL_POLICY_VERSION);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("effectiveConfig", canonicalEncoder.pollEffectiveConfig(requestedHotDays, stuckDays));
        scope.put("configFingerprintSchemaVersion", contract.configFingerprintSchemaVersion());
        scope.put("configFingerprint", DataRetentionCanonicalEncoder.hex(contract.configFingerprint()));
        scope.put("dryRunSchemaVersion", 1);
        scope.put("pageLimit", limit);
        scope.put("maxPages", maxPages);
        scope.put("projectId", safe.getProjectId());
        scope.put("questionTier", tier);
        scope.put("effectiveStartDate", start == null ? null : start.toString());
        scope.put("effectiveEndDate", end.toString());
        scope.put("startsAfterCursor", cursor != null);
        RetentionRunContext run = typedAudit.startRun(new RetentionRunStartCommand(
                UUID.randomUUID().toString(), maxPages == 1 ? "manual" : "scheduler", "dry_run", requestedBy,
                normalizeReason(safe.getReason()), null, null, contract, scope, start, end, startedAt));

        PollRetentionDryRunResponse response = new PollRetentionDryRunResponse();
        response.setRetentionRunId(run.runId());
        response.setDryRun(true);
        response.setSimulationOnly(requestedHotDays < DEFAULT_HOT_RETENTION_DAYS);
        response.setProjectId(safe.getProjectId());
        response.setStartDate(start);
        response.setEndDate(end);
        response.setQuestionTier(tier);
        response.setHotRetentionDays(requestedHotDays);
        response.setStuckBatchSealDays(stuckDays);
        response.setLimit(limit);
        response.setReason(normalizeReason(safe.getReason()));
        response.setCutoffDate(cutoff);
        Long predecessor = null;
        long warnings = 0;
        try {
            for (int page = 1; page <= maxPages; page++) {
                RetentionAttemptContext attempt = typedAudit.createOperationlessAttempt(run,
                        new RetentionAttemptCommand(UUID.randomUUID().toString(), AttemptType.PAGE,
                                predecessor, predecessor, Map.of("dryRunPageSchemaVersion", 1,
                                "pageNumber", page, "pageLimit", limit)));
                typedAudit.markAttemptRunning(attempt.attemptId());
                List<CandidateSlice> loaded;
                try {
                    loaded = loadCandidateSlices(safe.getProjectId(), start, end, tier, cursor, limit + 1);
                    boolean hasMore = loaded.size() > limit;
                    List<CandidateSlice> candidates = hasMore ? loaded.subList(0, limit) : loaded;
                    for (CandidateSlice candidate : candidates) {
                        PollRetentionDryRunSliceVO slice = evaluateSlice(candidate, today, stuckDays);
                        slice.setAction(Boolean.TRUE.equals(slice.getEligible()) ? "purge_poll_detail" : "blocked");
                        slice.setResult(Boolean.TRUE.equals(slice.getEligible()) ? "pending" : "blocked");
                        response.getSlices().add(slice);
                    }
                    long pageSkipped = response.getSlices().stream()
                            .skip(response.getSlices().size() - candidates.size())
                            .filter(slice -> !Boolean.TRUE.equals(slice.getEligible()))
                            .count();
                    long pageWarnings = response.getSlices().stream()
                            .skip(response.getSlices().size() - candidates.size())
                            .mapToLong(slice -> slice.getWarnings().size())
                            .sum();
                    warnings += pageWarnings;
                    typedAudit.completeAttempt(attempt.attemptId(), new RetentionAttemptResult(
                            AttemptStatus.SUCCEEDED, candidates.size(), 0, pageSkipped, pageWarnings,
                            Map.of("dryRunPageSchemaVersion", 1, "pageNumber", page, "hasMore", hasMore),
                            null, null, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
                    predecessor = attempt.attemptId();
                    response.setHasMore(hasMore);
                    if (!hasMore || candidates.isEmpty()) break;
                    CandidateSlice last = candidates.get(candidates.size() - 1);
                    cursor = new Cursor(last.batchDate(), last.projectId(), last.questionTier());
                    response.setNextCursorBatchDate(last.batchDate());
                    response.setNextCursorProjectId(last.projectId());
                    response.setNextCursorQuestionTier(last.questionTier());
                } catch (RuntimeException failure) {
                    warnings++;
                    typedAudit.completeAttempt(attempt.attemptId(), new RetentionAttemptResult(
                            AttemptStatus.FAILED, 0, 0, 0, 1,
                            Map.of("dryRunPageSchemaVersion", 1, "pageNumber", page),
                            "POLL_DRY_RUN_FAILED", "Poll dry-run page read failed",
                            clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
                    throw failure;
                }
            }
        } catch (RuntimeException failure) {
            summarize(response);
            typedAudit.finishRun(run.runId(), new RunBusinessSummary(response.getCandidateSlices(), 0,
                    response.getBlockedSlices(), warnings, Map.of("dryRunSchemaVersion", 1, "failed", true)));
            throw failure;
        }
        summarize(response);
        typedAudit.finishRun(run.runId(), new RunBusinessSummary(response.getCandidateSlices(), 0,
                response.getBlockedSlices(), warnings, Map.of("dryRunSchemaVersion", 1,
                "hasMore", Boolean.TRUE.equals(response.getHasMore()))));
        return response;
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

    private PollRetentionDryRunSliceVO evaluateSlice(CandidateSlice candidate,
                                                     LocalDate today,
                                                     int stuckBatchSealDays) {
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
