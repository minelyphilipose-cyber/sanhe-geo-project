package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunRequest;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunResponse;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimItemVO;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataRetentionSlimDryRunService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final String DOMAIN_ALL = "all";
    private static final String DOMAIN_PRESALE_AI_CALL = "presale_ai_call";
    private static final String DOMAIN_PRESALE_JUDGE_RAW = "presale_judge_raw";
    private static final String DOMAIN_ARTICLE_GENERATION_TASK = "article_generation_task";
    private static final String DOMAIN_DISTRIBUTION_PAYLOAD = "distribution_payload";

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;

    public DataRetentionSlimDryRunResponse dryRun(DataRetentionSlimDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        String domain = normalizeDomain(request.getDomain());
        int limit = normalizeLimit(request.getLimitPerDomain());
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "endDate must be greater than or equal to startDate");
        }

        DataRetentionSlimDryRunResponse response = new DataRetentionSlimDryRunResponse();
        response.setDomain(domain);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setLimitPerDomain(limit);
        Long runId = auditService.startRun("slim_payload", "dry_run", startDate, endDate, Map.of(
                "domain", domain,
                "limitPerDomain", limit
        ));
        response.setRetentionRunId(runId);
        try {
            List<DataRetentionSlimItemVO> items = new ArrayList<>();
            if (includes(domain, DOMAIN_PRESALE_AI_CALL)) {
                items.addAll(loadPresaleAiCallCandidates(startDate, endDate, limit));
            }
            if (includes(domain, DOMAIN_PRESALE_JUDGE_RAW)) {
                items.addAll(loadPresaleJudgeRawCandidates(startDate, endDate, limit));
            }
            if (includes(domain, DOMAIN_ARTICLE_GENERATION_TASK)) {
                items.addAll(loadArticleGenerationTaskCandidates(startDate, endDate, limit));
            }
            if (includes(domain, DOMAIN_DISTRIBUTION_PAYLOAD)) {
                items.addAll(loadDistributionPayloadCandidates(startDate, endDate, limit));
            }
            response.setItems(items);
            summarize(response);
            auditService.finishRun(runId, "succeeded", response.getCandidateCount(), response.getEligibleCount(),
                    response.getBlockedCount(), response.getWarningCount(), metrics(response), null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(), response.getEligibleCount(),
                    response.getBlockedCount(), response.getWarningCount() + 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private List<DataRetentionSlimItemVO> loadPresaleAiCallCandidates(LocalDate startDate, LocalDate endDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.version_id,
                       v.report_id,
                       c.stage,
                       c.model_id_snapshot,
                       c.call_status,
                       c.retry_count,
                       c.created_at,
                       v.generation_status,
                       EXISTS (
                         SELECT 1
                           FROM llm_usage_daily_summary s
                          WHERE s.usage_date = DATE(c.created_at)
                            AND s.report_id = v.report_id
                            AND s.stage = COALESCE(c.stage, '')
                            AND ((s.model_id_snapshot IS NULL AND c.model_id_snapshot IS NULL)
                                  OR s.model_id_snapshot = c.model_id_snapshot)
                            AND s.call_status = COALESCE(c.call_status, '')
                       ) AS summary_covered,
                       CHAR_LENGTH(COALESCE(c.raw_response, '')) AS raw_len,
                       CHAR_LENGTH(COALESCE(c.request_prompt_content, '')) AS prompt_len
                  FROM presale_ai_call c
                  JOIN presale_report_version v ON v.id = c.version_id
                 WHERE c.payload_purged_at IS NULL
                   AND (NULLIF(TRIM(c.raw_response), '') IS NOT NULL
                        OR NULLIF(TRIM(c.request_prompt_content), '') IS NOT NULL)
                """);
        List<Object> args = appendDateFilter(sql, "c.created_at", startDate, endDate);
        sql.append(" ORDER BY c.created_at ASC, c.id ASC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            List<String> blocked = new ArrayList<>();
            String generationStatus = rs.getString("generation_status");
            String callStatus = rs.getString("call_status");
            boolean summaryCovered = rs.getBoolean("summary_covered");
            if (!summaryCovered) {
                blocked.add("llm_usage_daily_summary_not_covered");
            }
            if (!isPresaleTerminal(generationStatus)) {
                blocked.add("presale_report_version_not_terminal");
            }
            if (isRetryCandidate(callStatus, rs.getObject("retry_count", Integer.class))) {
                blocked.add("call_still_retryable_or_running");
            }
            DataRetentionSlimItemVO item = item(DOMAIN_PRESALE_AI_CALL, "presale_ai_call",
                    rs.getLong("id"), rs.getLong("version_id"), callStatus, rs.getTimestamp("created_at"), null, blocked);
            List<String> fields = new ArrayList<>();
            if (rs.getInt("raw_len") > 0) {
                fields.add("raw_response");
            }
            if (rs.getInt("prompt_len") > 0) {
                fields.add("request_prompt_content");
            }
            item.setFields(fields);
            item.setMetrics(Map.of(
                    "reportId", rs.getLong("report_id"),
                    "stage", nullToEmpty(rs.getString("stage")),
                    "modelIdSnapshot", nullToEmpty(rs.getString("model_id_snapshot")),
                    "generationStatus", nullToEmpty(generationStatus),
                    "summaryCovered", summaryCovered
            ));
            return item;
        }, args.toArray());
    }

    private List<DataRetentionSlimItemVO> loadPresaleJudgeRawCandidates(LocalDate startDate, LocalDate endDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT j.id,
                       j.version_id,
                       j.prompt_result_id,
                       j.judge_status,
                       j.updated_at,
                       v.generation_status,
                       EXISTS (
                         SELECT 1
                           FROM llm_usage_daily_summary s
                          WHERE s.report_id = v.report_id
                            AND s.usage_date = DATE(j.created_at)
                       ) AS summary_covered,
                       CHAR_LENGTH(COALESCE(j.raw_judge_response, '')) AS raw_len
                  FROM presale_ai_prompt_judge_result j
                  JOIN presale_report_version v ON v.id = j.version_id
                 WHERE j.raw_purged_at IS NULL
                   AND NULLIF(TRIM(j.raw_judge_response), '') IS NOT NULL
                """);
        List<Object> args = appendDateFilter(sql, "j.updated_at", startDate, endDate);
        sql.append(" ORDER BY j.updated_at ASC, j.id ASC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            List<String> blocked = new ArrayList<>();
            String generationStatus = rs.getString("generation_status");
            boolean summaryCovered = rs.getBoolean("summary_covered");
            if (!summaryCovered) {
                blocked.add("llm_usage_daily_summary_not_covered");
            }
            if (!isPresaleTerminal(generationStatus)) {
                blocked.add("presale_report_version_not_terminal");
            }
            DataRetentionSlimItemVO item = item(DOMAIN_PRESALE_JUDGE_RAW, "presale_ai_prompt_judge_result",
                    rs.getLong("id"), rs.getLong("prompt_result_id"), rs.getString("judge_status"),
                    rs.getTimestamp("updated_at"), null, blocked);
            item.setFields(List.of("raw_judge_response"));
            item.setMetrics(Map.of(
                    "versionId", rs.getLong("version_id"),
                    "generationStatus", nullToEmpty(generationStatus),
                    "summaryCovered", summaryCovered,
                    "rawLength", rs.getInt("raw_len")
            ));
            return item;
        }, args.toArray());
    }

    private List<DataRetentionSlimItemVO> loadArticleGenerationTaskCandidates(LocalDate startDate, LocalDate endDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id,
                       t.batch_id,
                       t.project_id,
                       t.article_type,
                       COALESCE(NULLIF(t.channel_sub_code, ''), t.channel_group_code) AS target_channel,
                       t.status,
                       t.created_at,
                       t.finished_at,
                       EXISTS (
                         SELECT 1
                           FROM article_generation_daily_summary s
                          WHERE s.generation_date = DATE(t.created_at)
                            AND s.project_id = t.project_id
                            AND s.article_type = t.article_type
                            AND ((s.target_channel IS NULL AND COALESCE(NULLIF(t.channel_sub_code, ''), t.channel_group_code) IS NULL)
                                  OR s.target_channel = COALESCE(NULLIF(t.channel_sub_code, ''), t.channel_group_code))
                            AND s.status = t.status
                       ) AS summary_covered,
                       CHAR_LENGTH(COALESCE(CAST(t.prompt_snapshot AS CHAR), '')) AS prompt_len,
                       CHAR_LENGTH(COALESCE(CAST(t.input_snapshot AS CHAR), '')) AS input_len,
                       CHAR_LENGTH(COALESCE(CAST(t.response_snapshot AS CHAR), '')) AS response_len
                  FROM batch_article_generation_task t
                 WHERE t.snapshot_purged_at IS NULL
                   AND (t.prompt_snapshot IS NOT NULL OR t.input_snapshot IS NOT NULL OR t.response_snapshot IS NOT NULL)
                """);
        List<Object> args = appendDateFilter(sql, "COALESCE(t.finished_at, t.updated_at, t.created_at)", startDate, endDate);
        sql.append(" ORDER BY COALESCE(t.finished_at, t.updated_at, t.created_at) ASC, t.id ASC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            List<String> blocked = new ArrayList<>();
            String status = rs.getString("status");
            boolean summaryCovered = rs.getBoolean("summary_covered");
            if (!summaryCovered) {
                blocked.add("article_generation_daily_summary_not_covered");
            }
            if (!isArticleTaskTerminal(status)) {
                blocked.add("article_generation_task_not_terminal");
            }
            DataRetentionSlimItemVO item = item(DOMAIN_ARTICLE_GENERATION_TASK, "batch_article_generation_task",
                    rs.getLong("id"), rs.getLong("batch_id"), status, rs.getTimestamp("created_at"),
                    rs.getTimestamp("finished_at"), blocked);
            List<String> fields = new ArrayList<>();
            if (rs.getInt("prompt_len") > 0) {
                fields.add("prompt_snapshot");
            }
            if (rs.getInt("input_len") > 0) {
                fields.add("input_snapshot");
            }
            if (rs.getInt("response_len") > 0) {
                fields.add("response_snapshot");
            }
            item.setFields(fields);
            item.setMetrics(Map.of(
                    "projectId", rs.getLong("project_id"),
                    "articleType", nullToEmpty(rs.getString("article_type")),
                    "targetChannel", nullToEmpty(rs.getString("target_channel")),
                    "summaryCovered", summaryCovered
            ));
            return item;
        }, args.toArray());
    }

    private List<DataRetentionSlimItemVO> loadDistributionPayloadCandidates(LocalDate startDate, LocalDate endDate, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id,
                       t.article_id,
                       t.project_id,
                       t.status,
                       t.review_status,
                       t.failure_kind,
                       t.error_message,
                       t.created_at,
                       t.finished_at,
                       EXISTS (
                         SELECT 1
                           FROM article_publish_record r
                          WHERE r.source_type = 'distribution_task'
                            AND r.source_id = t.id
                       ) AS has_publish_record,
                       CHAR_LENGTH(COALESCE(CAST(t.request_payload AS CHAR), '')) AS request_len,
                       CHAR_LENGTH(COALESCE(CAST(t.fill_payload AS CHAR), '')) AS fill_len,
                       CHAR_LENGTH(COALESCE(CAST(t.response_payload AS CHAR), '')) AS response_len
                  FROM distribution_tasks t
                 WHERE t.payload_purged_at IS NULL
                   AND (t.request_payload IS NOT NULL OR t.fill_payload IS NOT NULL OR t.response_payload IS NOT NULL)
                """);
        List<Object> args = appendDateFilter(sql, "COALESCE(t.finished_at, t.published_at, t.created_at)", startDate, endDate);
        sql.append(" ORDER BY COALESCE(t.finished_at, t.published_at, t.created_at) ASC, t.id ASC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            List<String> blocked = new ArrayList<>();
            String status = rs.getString("status");
            String failureKind = rs.getString("failure_kind");
            String errorMessage = rs.getString("error_message");
            boolean hasPublishRecord = rs.getBoolean("has_publish_record");
            boolean terminal = isDistributionTerminal(status, rs.getString("review_status"));
            boolean hasFailureTrace = StringUtils.hasText(failureKind) || StringUtils.hasText(errorMessage);
            if (!terminal) {
                blocked.add("distribution_task_not_terminal");
            }
            if ("published".equalsIgnoreCase(status) && !hasPublishRecord) {
                blocked.add("article_publish_record_missing");
            }
            if ("failed".equalsIgnoreCase(status) && !hasFailureTrace) {
                blocked.add("failure_trace_missing");
            }
            DataRetentionSlimItemVO item = item(DOMAIN_DISTRIBUTION_PAYLOAD, "distribution_tasks",
                    rs.getLong("id"), rs.getLong("article_id"), status, rs.getTimestamp("created_at"),
                    rs.getTimestamp("finished_at"), blocked);
            List<String> fields = new ArrayList<>();
            if (rs.getInt("request_len") > 0) {
                fields.add("request_payload");
            }
            if (rs.getInt("fill_len") > 0) {
                fields.add("fill_payload");
            }
            if (rs.getInt("response_len") > 0) {
                fields.add("response_payload");
            }
            item.setFields(fields);
            item.setMetrics(Map.of(
                    "projectId", rs.getLong("project_id"),
                    "reviewStatus", nullToEmpty(rs.getString("review_status")),
                    "hasPublishRecord", hasPublishRecord,
                    "hasFailureTrace", hasFailureTrace
            ));
            return item;
        }, args.toArray());
    }

    private DataRetentionSlimItemVO item(String domain,
                                         String tableName,
                                         Long id,
                                         Long parentId,
                                         String status,
                                         Timestamp createdAt,
                                         Timestamp finishedAt,
                                         List<String> blockedReasons) {
        DataRetentionSlimItemVO item = new DataRetentionSlimItemVO();
        item.setDomain(domain);
        item.setTableName(tableName);
        item.setSourceId(id);
        item.setParentId(parentId);
        item.setStatus(status);
        item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        item.setFinishedAt(finishedAt == null ? null : finishedAt.toLocalDateTime());
        item.setBlockedReasons(blockedReasons);
        item.setEligible(blockedReasons == null || blockedReasons.isEmpty());
        return item;
    }

    private List<Object> appendDateFilter(StringBuilder sql, String columnExpression, LocalDate startDate, LocalDate endDate) {
        List<Object> args = new ArrayList<>();
        if (startDate != null) {
            sql.append("   AND ").append(columnExpression).append(" >= ?\n");
            args.add(Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            sql.append("   AND ").append(columnExpression).append(" < ?\n");
            args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        }
        return args;
    }

    private void summarize(DataRetentionSlimDryRunResponse response) {
        int candidate = response.getItems().size();
        int eligible = (int) response.getItems().stream().filter(item -> Boolean.TRUE.equals(item.getEligible())).count();
        int blocked = candidate - eligible;
        response.setCandidateCount(candidate);
        response.setEligibleCount(eligible);
        response.setBlockedCount(blocked);
        response.setWarningCount(blocked);
    }

    private Map<String, Object> metrics(DataRetentionSlimDryRunResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("domain", response.getDomain());
        metrics.put("limitPerDomain", response.getLimitPerDomain());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("eligibleCount", response.getEligibleCount());
        metrics.put("blockedCount", response.getBlockedCount());
        metrics.put("warningCount", response.getWarningCount());
        return metrics;
    }

    private boolean includes(String selectedDomain, String candidateDomain) {
        return DOMAIN_ALL.equals(selectedDomain) || candidateDomain.equals(selectedDomain);
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return DOMAIN_ALL;
        }
        String value = domain.trim().toLowerCase(Locale.ROOT);
        if (List.of(DOMAIN_ALL, DOMAIN_PRESALE_AI_CALL, DOMAIN_PRESALE_JUDGE_RAW,
                DOMAIN_ARTICLE_GENERATION_TASK, DOMAIN_DISTRIBUTION_PAYLOAD).contains(value)) {
            return value;
        }
        throw new BizException(400, "Unsupported slim dry-run domain: " + domain);
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private boolean isPresaleTerminal(String status) {
        return "DONE".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    private boolean isRetryCandidate(String callStatus, Integer retryCount) {
        String status = callStatus == null ? "" : callStatus.trim().toUpperCase(Locale.ROOT);
        if (List.of("PENDING", "QUEUED", "RUNNING", "RETRYING").contains(status)) {
            return true;
        }
        return "FAILED".equals(status) && retryCount != null && retryCount > 0;
    }

    private boolean isArticleTaskTerminal(String status) {
        return List.of("success", "failed").contains(status == null ? "" : status.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isDistributionTerminal(String status, String reviewStatus) {
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        String normalizedReview = reviewStatus == null ? "" : reviewStatus.trim().toLowerCase(Locale.ROOT);
        return "published".equals(normalizedStatus)
                || "failed".equals(normalizedStatus)
                || ("submitted".equals(normalizedStatus)
                && List.of("published", "rejected", "offline").contains(normalizedReview));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
