package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dto.WebsitePublishedCleanupRequest;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes hot article bodies after an owned website has durably accepted the
 * publication. The publish record and public URL remain as permanent evidence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebsitePublishedContentCleanupService {

    private static final int DEFAULT_RETENTION_HOURS = 24;
    private static final int MIN_RETENTION_HOURS = 24;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final String WEBSITE_TARGET_KIND_SQL =
            "'brand_geo_site','industry_site','forum_site'";
    private static final String UNAMBIGUOUS_TERMINAL_DISTRIBUTION_STATUS_SQL =
            "'confirmed','published','failed','cancelled'";
    private static final String NON_BLOCKING_SELF_MEDIA_STATUS_SQL =
            "'published_confirmed','cancelled'";
    private static final String WEBSITE_EVIDENCE_QUALITY_SQL =
            "'verified_public_url','pending_review_url'";
    private static final String WEBSITE_SUCCESS_RECORD_SQL = trustedWebsiteRecordSql("r");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DataRetentionRunAuditService auditService;
    private final DataRetentionProperties retentionProperties;
    private final CurrentUserService currentUserService;

    public CleanupBatchResult dryRun(WebsitePublishedCleanupRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        WebsitePublishedCleanupRequest safeRequest =
                request == null ? new WebsitePublishedCleanupRequest() : request;
        return runScheduled(
                safeRequest.getRetentionHours(),
                safeRequest.getLimit(),
                safeRequest.getCursorArticleId(),
                true,
                safeRequest.getReason()
        );
    }

    public CleanupBatchResult cleanup(WebsitePublishedCleanupRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        WebsitePublishedCleanupRequest safeRequest =
                request == null ? new WebsitePublishedCleanupRequest() : request;
        return runScheduled(
                safeRequest.getRetentionHours(),
                safeRequest.getLimit(),
                safeRequest.getCursorArticleId(),
                false,
                safeRequest.getReason()
        );
    }

    public CleanupBatchResult runScheduled(Integer retentionHours,
                                           Integer requestedLimit,
                                           Long cursorArticleId,
                                           boolean dryRun,
                                           String reason) {
        if (!dryRun && !retentionProperties.getWebsitePublishedCleanup().isExecuteEnabled()) {
            throw new BizException(403,
                    "Website published cleanup execute is disabled by "
                            + "geo.retention.website-published-cleanup.execute-enabled");
        }
        if (!dryRun && !StringUtils.hasText(reason)) {
            throw new BizException(400, "reason is required for website published content cleanup");
        }
        int effectiveRetentionHours = retentionHours == null
                ? DEFAULT_RETENTION_HOURS
                : Math.max(MIN_RETENTION_HOURS, retentionHours);
        int limit = normalizeLimit(requestedLimit);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(effectiveRetentionHours);
        Map<String, Object> startMetrics = new LinkedHashMap<>();
        startMetrics.put("retentionHours", effectiveRetentionHours);
        startMetrics.put("limit", limit);
        startMetrics.put("cursorArticleId", cursorArticleId);
        startMetrics.put("reason", normalizeReason(reason));

        Long runId = auditService.startRun(
                "website_published_content",
                dryRun ? "dry_run" : "execute",
                null,
                null,
                startMetrics
        );
        try {
            List<Candidate> loaded = validatePublicEvidence(
                    loadCandidates(cutoff, cursorArticleId, limit + 1));
            boolean hasMore = loaded.size() > limit;
            List<Candidate> candidates = hasMore ? loaded.subList(0, limit) : loaded;
            Long nextCursor = candidates.isEmpty() ? null : candidates.get(candidates.size() - 1).articleId();
            List<CleanupItem> items = new ArrayList<>(candidates.size());
            int cleaned = 0;
            int skipped = 0;
            int failed = 0;
            long bodyRows = 0;
            long payloadRows = 0;
            long publishRecordRows = 0;
            for (Candidate candidate : candidates) {
                if (!candidate.publicEvidenceValid()) {
                    skipped++;
                    items.add(CleanupItem.blocked(candidate, "invalid_public_url"));
                    continue;
                }
                if (dryRun) {
                    items.add(CleanupItem.pending(candidate));
                    continue;
                }
                try {
                    CleanupMutation mutation = transactionTemplate.execute(
                            status -> cleanupArticle(candidate.articleId(), cutoff));
                    if (mutation == null || mutation.totalAffectedRows() == 0) {
                        skipped++;
                        items.add(CleanupItem.skipped(candidate, "article_state_changed_or_already_clean"));
                        continue;
                    }
                    cleaned++;
                    bodyRows += mutation.bodyRows();
                    payloadRows += mutation.payloadRows();
                    publishRecordRows += mutation.publishRecordRows();
                    items.add(CleanupItem.cleaned(candidate, mutation));
                } catch (RuntimeException ex) {
                    failed++;
                    items.add(CleanupItem.failed(candidate, trimError(ex.getMessage())));
                    log.warn("Website published content cleanup failed, articleId={}",
                            candidate.articleId(), ex);
                }
            }
            CleanupBatchResult result = new CleanupBatchResult(
                    runId,
                    dryRun,
                    effectiveRetentionHours,
                    candidates.size(),
                    cleaned,
                    skipped,
                    failed,
                    bodyRows,
                    payloadRows,
                    publishRecordRows,
                    hasMore,
                    nextCursor,
                    List.copyOf(items)
            );
            auditService.finishRun(
                    runId,
                    failed > 0 ? "failed" : "succeeded",
                    candidates.size(),
                    dryRun ? candidates.size() - skipped : cleaned,
                    skipped,
                    failed,
                    metrics(result),
                    failed > 0 ? "One or more website article cleanups failed" : null
            );
            return result;
        } catch (RuntimeException ex) {
            auditService.finishRun(
                    runId,
                    "failed",
                    0,
                    0,
                    0,
                    1,
                    startMetrics,
                    trimError(ex.getMessage())
            );
            throw ex;
        }
    }

    private List<Candidate> loadCandidates(LocalDateTime cutoff, Long cursorArticleId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS article_id,
                       a.project_id,
                       a.status AS article_status,
                       pr.last_published_at,
                       (
                         SELECT COUNT(*)
                           FROM article_draft_version v
                          WHERE v.article_id = a.id
                            AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL
                       ) AS body_row_count,
                       (
                         SELECT COALESCE(SUM(OCTET_LENGTH(v.content_markdown)), 0)
                           FROM article_draft_version v
                          WHERE v.article_id = a.id
                            AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL
                       ) AS body_bytes,
                       (
                         SELECT COUNT(*)
                           FROM distribution_tasks dt
                          WHERE dt.article_id = a.id
                            AND dt.payload_purged_at IS NULL
                            AND (dt.request_payload IS NOT NULL
                                 OR dt.fill_payload IS NOT NULL
                                 OR dt.response_payload IS NOT NULL)
                            AND EXISTS (
                                SELECT 1
                                  FROM article_publish_record r
                                 WHERE r.source_type = 'distribution_task'
                                   AND r.source_id = dt.id
                                   AND r.article_id = a.id
                                   AND %s
                            )
                       ) AS payload_row_count,
                       (
                         SELECT COALESCE(SUM(
                                  OCTET_LENGTH(COALESCE(CAST(dt.request_payload AS CHAR), ''))
                                + OCTET_LENGTH(COALESCE(CAST(dt.fill_payload AS CHAR), ''))
                                + OCTET_LENGTH(COALESCE(CAST(dt.response_payload AS CHAR), ''))
                                ), 0)
                           FROM distribution_tasks dt
                          WHERE dt.article_id = a.id
                            AND dt.payload_purged_at IS NULL
                            AND EXISTS (
                                SELECT 1
                                  FROM article_publish_record r
                                 WHERE r.source_type = 'distribution_task'
                                   AND r.source_id = dt.id
                                   AND r.article_id = a.id
                                   AND %s
                            )
                       ) AS payload_bytes
                 FROM article_draft a
                  JOIN (
                        SELECT r.article_id,
                               MAX(COALESCE(r.published_at, r.verified_at, r.created_at)) AS last_published_at
                          FROM article_publish_record r
                         WHERE %s
                         GROUP BY r.article_id
                       ) pr ON pr.article_id = a.id
                 WHERE pr.last_published_at <= ?
                   AND %s
                   AND NOT EXISTS (
                       SELECT 1
                         FROM distribution_tasks active_dt
                        WHERE active_dt.article_id = a.id
                          AND %s
                   )
                   AND NOT EXISTS (
                        SELECT 1
                          FROM self_media_publish_schedule active_sm
                         WHERE active_sm.article_id = a.id
                          AND active_sm.status NOT IN (%s)
                   )
                   AND (
                        COALESCE(a.status, '') <> 'deleted'
                        OR EXISTS (
                            SELECT 1
                              FROM article_draft_version hot_v
                             WHERE hot_v.article_id = a.id
                               AND NULLIF(TRIM(hot_v.content_markdown), '') IS NOT NULL
                        )
                        OR EXISTS (
                            SELECT 1
                              FROM distribution_tasks hot_dt
                             WHERE hot_dt.article_id = a.id
                               AND hot_dt.payload_purged_at IS NULL
                               AND (hot_dt.request_payload IS NOT NULL
                                    OR hot_dt.fill_payload IS NOT NULL
                                    OR hot_dt.response_payload IS NOT NULL)
                               AND EXISTS (
                                   SELECT 1
                                     FROM article_publish_record r
                                    WHERE r.source_type = 'distribution_task'
                                      AND r.source_id = hot_dt.id
                                      AND r.article_id = a.id
                                      AND %s
                               )
                        )
                        OR EXISTS (
                            SELECT 1
                              FROM article_publish_record r
                             WHERE r.article_id = a.id
                               AND r.raw_response IS NOT NULL
                               AND %s
                        )
                   )
                """.formatted(
                WEBSITE_SUCCESS_RECORD_SQL,
                WEBSITE_SUCCESS_RECORD_SQL,
                WEBSITE_SUCCESS_RECORD_SQL,
                eligibleArticleStatusPredicate("a"),
                activeDistributionPredicate("active_dt"),
                NON_BLOCKING_SELF_MEDIA_STATUS_SQL,
                WEBSITE_SUCCESS_RECORD_SQL,
                WEBSITE_SUCCESS_RECORD_SQL
        ));
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(cutoff));
        if (cursorArticleId != null) {
            sql.append("   AND a.id > ?\n");
            args.add(cursorArticleId);
        }
        sql.append(" ORDER BY a.id ASC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Candidate(
                rs.getLong("article_id"),
                rs.getLong("project_id"),
                rs.getString("article_status"),
                rs.getTimestamp("last_published_at").toLocalDateTime(),
                rs.getInt("body_row_count"),
                rs.getLong("body_bytes"),
                rs.getInt("payload_row_count"),
                rs.getLong("payload_bytes"),
                false
        ), args.toArray());
    }

    private List<Candidate> validatePublicEvidence(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(candidates.size(), "?"));
        List<Object> args = candidates.stream().map(Candidate::articleId).map(Object.class::cast).toList();
        Map<Long, List<String>> urlsByArticle = new HashMap<>();
        jdbcTemplate.query("""
                SELECT r.article_id, r.published_url
                  FROM article_publish_record r
                 WHERE r.article_id IN (%s)
                   AND %s
                """.formatted(placeholders, WEBSITE_SUCCESS_RECORD_SQL), rs -> {
            urlsByArticle.computeIfAbsent(rs.getLong("article_id"), ignored -> new ArrayList<>())
                    .add(rs.getString("published_url"));
        }, args.toArray());
        Set<Long> validArticleIds = new HashSet<>();
        urlsByArticle.forEach((articleId, urls) -> {
            if (!urls.isEmpty() && urls.stream().allMatch(this::isValidPublicHttpUrl)) {
                validArticleIds.add(articleId);
            }
        });
        return candidates.stream()
                .map(candidate -> candidate.withPublicEvidenceValid(
                        validArticleIds.contains(candidate.articleId())))
                .toList();
    }

    private CleanupMutation cleanupArticle(Long articleId, LocalDateTime cutoff) {
        List<Long> articleLock = jdbcTemplate.queryForList(
                "SELECT id FROM article_draft WHERE id = ? FOR UPDATE",
                Long.class,
                articleId
        );
        if (articleLock.isEmpty()) {
            return CleanupMutation.none();
        }
        jdbcTemplate.queryForList(
                "SELECT id FROM article_publish_record WHERE article_id = ? ORDER BY id FOR UPDATE",
                Long.class,
                articleId
        );
        jdbcTemplate.queryForList(
                "SELECT id FROM distribution_tasks WHERE article_id = ? ORDER BY id FOR UPDATE",
                Long.class,
                articleId
        );
        jdbcTemplate.queryForList(
                "SELECT id FROM self_media_publish_schedule WHERE article_id = ? ORDER BY id FOR UPDATE",
                Long.class,
                articleId
        );
        List<PublishedEvidence> evidence = jdbcTemplate.query("""
                SELECT r.published_url,
                       COALESCE(r.published_at, r.verified_at, r.created_at) AS effective_published_at
                  FROM article_publish_record r
                 WHERE r.article_id = ?
                   AND %s
                 ORDER BY r.id
                """.formatted(WEBSITE_SUCCESS_RECORD_SQL), (rs, rowNum) -> new PublishedEvidence(
                rs.getString("published_url"),
                rs.getTimestamp("effective_published_at").toLocalDateTime()
        ), articleId);
        if (!validEvidenceOlderThanCutoff(evidence, cutoff)) {
            return CleanupMutation.none();
        }
        Integer eligible = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                 FROM article_draft a
                 WHERE a.id = ?
                   AND %s
                   AND NOT EXISTS (
                       SELECT 1
                         FROM distribution_tasks dt
                        WHERE dt.article_id = a.id
                          AND %s
                   )
                   AND NOT EXISTS (
                        SELECT 1
                          FROM self_media_publish_schedule sm
                         WHERE sm.article_id = a.id
                          AND sm.status NOT IN (%s)
                   )
                """.formatted(
                eligibleArticleStatusPredicate("a"),
                activeDistributionPredicate("dt"),
                NON_BLOCKING_SELF_MEDIA_STATUS_SQL
        ), Integer.class, articleId);
        if (eligible == null || eligible != 1) {
            return CleanupMutation.none();
        }

        int bodyRows = jdbcTemplate.update("""
                UPDATE article_draft_version
                   SET content_markdown = NULL,
                       content_purged_at = CURRENT_TIMESTAMP
                 WHERE article_id = ?
                   AND NULLIF(TRIM(content_markdown), '') IS NOT NULL
                """, articleId);
        int payloadRows = jdbcTemplate.update("""
                UPDATE distribution_tasks
                   SET request_payload = NULL,
                       fill_payload = NULL,
                       response_payload = NULL,
                       payload_purged_at = CURRENT_TIMESTAMP
                 WHERE distribution_tasks.article_id = ?
                   AND distribution_tasks.payload_purged_at IS NULL
                   AND (distribution_tasks.request_payload IS NOT NULL
                        OR distribution_tasks.fill_payload IS NOT NULL
                        OR distribution_tasks.response_payload IS NOT NULL)
                   AND EXISTS (
                       SELECT 1
                         FROM article_publish_record r
                        WHERE r.source_type = 'distribution_task'
                          AND r.source_id = distribution_tasks.id
                          AND r.article_id = distribution_tasks.article_id
                          AND %s
                   )
                """.formatted(WEBSITE_SUCCESS_RECORD_SQL), articleId);
        int publishRecordRows = jdbcTemplate.update("""
                UPDATE article_publish_record
                   SET raw_response = NULL
                 WHERE article_publish_record.article_id = ?
                   AND article_publish_record.raw_response IS NOT NULL
                   AND %s
                """.formatted(websiteEvidenceRecordSql("article_publish_record")), articleId);
        int articleRows = jdbcTemplate.update("""
                UPDATE article_draft
                   SET status = 'deleted',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                   AND COALESCE(status, '') <> 'deleted'
                """, articleId);
        return new CleanupMutation(bodyRows, payloadRows, publishRecordRows, articleRows);
    }

    private int normalizeLimit(Integer requestedLimit) {
        int value = requestedLimit == null || requestedLimit <= 0 ? DEFAULT_LIMIT : requestedLimit;
        return Math.min(value, MAX_LIMIT);
    }

    private boolean validEvidenceOlderThanCutoff(List<PublishedEvidence> evidence, LocalDateTime cutoff) {
        if (evidence.isEmpty() || evidence.stream().anyMatch(item -> !isValidPublicHttpUrl(item.publishedUrl()))) {
            return false;
        }
        LocalDateTime latestPublishedAt = evidence.stream()
                .map(PublishedEvidence::publishedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return latestPublishedAt != null && !latestPublishedAt.isAfter(cutoff);
    }

    private boolean isValidPublicHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String activeDistributionPredicate(String taskAlias) {
        return """
                (
                    %1$s.status NOT IN (%2$s)
                    AND NOT (
                        %1$s.status = 'submitted'
                        AND %1$s.target_kind IN (%3$s)
                        AND EXISTS (
                            SELECT 1
                              FROM article_publish_record submitted_r
                             WHERE submitted_r.source_type = 'distribution_task'
                               AND submitted_r.source_id = %1$s.id
                               AND submitted_r.article_id = %1$s.article_id
                               AND submitted_r.target_kind IN (%3$s)
                               AND submitted_r.publish_status = 'distributed'
                               AND submitted_r.url_quality IN (%4$s)
                               AND (
                                    LOWER(TRIM(submitted_r.published_url)) LIKE 'http://%%'
                                    OR LOWER(TRIM(submitted_r.published_url)) LIKE 'https://%%'
                               )
                               AND NOT EXISTS (
                                   SELECT 1
                                     FROM article_publish_record duplicate_r
                                    WHERE duplicate_r.article_id <> submitted_r.article_id
                                      AND NULLIF(TRIM(duplicate_r.published_url), '') IS NOT NULL
                                      AND LOWER(TRIM(duplicate_r.published_url))
                                          = LOWER(TRIM(submitted_r.published_url))
                               )
                        )
                    )
                )
                """.formatted(
                taskAlias,
                UNAMBIGUOUS_TERMINAL_DISTRIBUTION_STATUS_SQL,
                WEBSITE_TARGET_KIND_SQL,
                WEBSITE_EVIDENCE_QUALITY_SQL
        );
    }

    private static String websiteEvidenceRecordSql(String alias) {
        return """
                %1$s.target_kind IN (%2$s)
                AND %1$s.publish_status = 'distributed'
                AND %1$s.url_quality IN (%3$s)
                """.formatted(alias, WEBSITE_TARGET_KIND_SQL, WEBSITE_EVIDENCE_QUALITY_SQL);
    }

    private static String trustedWebsiteRecordSql(String alias) {
        return """
                %1$s
                AND NOT EXISTS (
                    SELECT 1
                      FROM article_publish_record duplicate_r
                     WHERE duplicate_r.article_id <> %2$s.article_id
                       AND NULLIF(TRIM(duplicate_r.published_url), '') IS NOT NULL
                       AND LOWER(TRIM(duplicate_r.published_url))
                           = LOWER(TRIM(%2$s.published_url))
                )
                """.formatted(websiteEvidenceRecordSql(alias), alias);
    }

    private static String eligibleArticleStatusPredicate(String articleAlias) {
        return """
                (
                    %1$s.status IN ('distributed', 'published')
                    OR (
                        %1$s.status = 'deleted'
                        AND (
                            EXISTS (
                                SELECT 1
                                  FROM article_draft_version purged_v
                                 WHERE purged_v.article_id = %1$s.id
                                   AND purged_v.content_purged_at IS NOT NULL
                            )
                            OR EXISTS (
                                SELECT 1
                                  FROM distribution_tasks purged_dt
                                 WHERE purged_dt.article_id = %1$s.id
                                   AND purged_dt.payload_purged_at IS NOT NULL
                            )
                        )
                    )
                )
                """.formatted(articleAlias);
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private Map<String, Object> metrics(CleanupBatchResult result) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("retentionHours", result.retentionHours());
        metrics.put("candidateCount", result.candidateCount());
        metrics.put("cleanedCount", result.cleanedCount());
        metrics.put("skippedCount", result.skippedCount());
        metrics.put("failedCount", result.failedCount());
        metrics.put("bodyRows", result.bodyRows());
        metrics.put("payloadRows", result.payloadRows());
        metrics.put("publishRecordRows", result.publishRecordRows());
        metrics.put("hasMore", result.hasMore());
        metrics.put("nextCursorArticleId", result.nextCursorArticleId());
        return metrics;
    }

    private record Candidate(Long articleId,
                             Long projectId,
                             String articleStatus,
                             LocalDateTime lastPublishedAt,
                             int bodyRowCount,
                             long bodyBytes,
                             int payloadRowCount,
                             long payloadBytes,
                             boolean publicEvidenceValid) {
        private Candidate withPublicEvidenceValid(boolean valid) {
            return new Candidate(articleId, projectId, articleStatus, lastPublishedAt,
                    bodyRowCount, bodyBytes, payloadRowCount, payloadBytes, valid);
        }
    }

    private record PublishedEvidence(String publishedUrl, LocalDateTime publishedAt) {
    }

    private record CleanupMutation(int bodyRows,
                                   int payloadRows,
                                   int publishRecordRows,
                                   int articleRows) {
        private static CleanupMutation none() {
            return new CleanupMutation(0, 0, 0, 0);
        }

        private int totalAffectedRows() {
            return bodyRows + payloadRows + publishRecordRows + articleRows;
        }
    }

    public record CleanupItem(Long articleId,
                              Long projectId,
                              String articleStatus,
                              LocalDateTime lastPublishedAt,
                              int bodyRowCount,
                              long bodyBytes,
                              int payloadRowCount,
                              long payloadBytes,
                              String result,
                              int purgedBodyRows,
                              int purgedPayloadRows,
                              int slimmedPublishRecordRows,
                              String errorMessage) {
        private static CleanupItem pending(Candidate candidate) {
            return from(candidate, "pending", 0, 0, 0, null);
        }

        private static CleanupItem blocked(Candidate candidate, String errorMessage) {
            return from(candidate, "blocked", 0, 0, 0, errorMessage);
        }

        private static CleanupItem cleaned(Candidate candidate, CleanupMutation mutation) {
            return from(candidate, "cleaned", mutation.bodyRows(), mutation.payloadRows(),
                    mutation.publishRecordRows(), null);
        }

        private static CleanupItem skipped(Candidate candidate, String errorMessage) {
            return from(candidate, "skipped", 0, 0, 0, errorMessage);
        }

        private static CleanupItem failed(Candidate candidate, String errorMessage) {
            return from(candidate, "failed", 0, 0, 0, errorMessage);
        }

        private static CleanupItem from(Candidate candidate,
                                        String result,
                                        int purgedBodyRows,
                                        int purgedPayloadRows,
                                        int slimmedPublishRecordRows,
                                        String errorMessage) {
            return new CleanupItem(
                    candidate.articleId(),
                    candidate.projectId(),
                    candidate.articleStatus(),
                    candidate.lastPublishedAt(),
                    candidate.bodyRowCount(),
                    candidate.bodyBytes(),
                    candidate.payloadRowCount(),
                    candidate.payloadBytes(),
                    result,
                    purgedBodyRows,
                    purgedPayloadRows,
                    slimmedPublishRecordRows,
                    errorMessage
            );
        }
    }

    public record CleanupBatchResult(Long retentionRunId,
                                     boolean dryRun,
                                     int retentionHours,
                                     int candidateCount,
                                     int cleanedCount,
                                     int skippedCount,
                                     int failedCount,
                                     long bodyRows,
                                     long payloadRows,
                                     long publishRecordRows,
                                     boolean hasMore,
                                     Long nextCursorArticleId,
                                     List<CleanupItem> items) {
    }
}
