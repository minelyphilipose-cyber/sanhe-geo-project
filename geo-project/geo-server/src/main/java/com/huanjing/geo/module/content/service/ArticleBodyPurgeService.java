package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.constant.ArticlePublishRecordStatusPolicy;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunItemVO;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeRequest;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.service.DataRetentionRunAuditService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleBodyPurgeService {

    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int DEFAULT_ARCHIVE_GRACE_HOURS = 24;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final String TERMINAL_DISTRIBUTION_STATUS_SQL =
            "'submitted','confirmed','published','failed','cancelled'";
    private static final String ACTIVE_SELF_MEDIA_STATUS_SQL =
            "'pending','filling','filled_verified','scheduling','scheduled','publish_due',"
                    + "'checking_publish_result','published_url_pending','publish_unknown','cancel_pending_platform'";

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final ObjectStorageService objectStorageService;
    private final DataRetentionRunAuditService auditService;
    private final DataRetentionProperties retentionProperties;

    public ArticleBodyPurgeResponse dryRun(ArticleBodyPurgeRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        return run(request, true);
    }

    public ArticleBodyPurgeResponse purge(ArticleBodyPurgeRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        if (!retentionProperties.getArticlePurge().isExecuteEnabled()) {
            throw new BizException(403,
                    "Article body purge execute is disabled by geo.retention.article-purge.execute-enabled");
        }
        return run(request, false);
    }

    public ArticleBodyPurgeResponse runScheduled(ArticleBodyPurgeRequest request, boolean dryRun) {
        if (!dryRun && !retentionProperties.getArticlePurge().isExecuteEnabled()) {
            throw new BizException(403,
                    "Article body purge execute is disabled by geo.retention.article-purge.execute-enabled");
        }
        return run(request, dryRun);
    }

    private ArticleBodyPurgeResponse run(ArticleBodyPurgeRequest request, boolean dryRun) {
        ArticleBodyPurgeRequest safeRequest = request == null ? new ArticleBodyPurgeRequest() : request;
        if (!dryRun && !StringUtils.hasText(safeRequest.getReason())) {
            throw new BizException(400, "reason is required for article body purge");
        }
        int requestedRetentionDays = normalizePositive(safeRequest.getRetentionDays(), DEFAULT_RETENTION_DAYS);
        int requestedArchiveGraceHours =
                normalizePositive(safeRequest.getArchiveGraceHours(), DEFAULT_ARCHIVE_GRACE_HOURS);
        boolean simulationOnly = dryRun
                && (requestedRetentionDays < DEFAULT_RETENTION_DAYS
                || requestedArchiveGraceHours < DEFAULT_ARCHIVE_GRACE_HOURS);
        int retentionDays = dryRun
                ? requestedRetentionDays
                : Math.max(requestedRetentionDays, DEFAULT_RETENTION_DAYS);
        int archiveGraceHours = dryRun
                ? requestedArchiveGraceHours
                : Math.max(requestedArchiveGraceHours, DEFAULT_ARCHIVE_GRACE_HOURS);
        int limit = normalizeLimit(safeRequest.getLimit());

        ArticleBodyPurgeResponse response = new ArticleBodyPurgeResponse();
        response.setDryRun(dryRun);
        response.setSimulationOnly(simulationOnly);
        response.setProjectId(safeRequest.getProjectId());
        response.setRetentionDays(retentionDays);
        response.setArchiveGraceHours(archiveGraceHours);
        response.setLimit(limit);
        response.setReason(normalizeReason(safeRequest.getReason()));

        Long runId = auditService.startRun("article_body_purge", dryRun ? "dry_run" : "execute",
                null, null, startMetrics(response));
        response.setRetentionRunId(runId);
        try {
            List<Candidate> loaded = loadCandidates(safeRequest, retentionDays, archiveGraceHours, limit + 1);
            boolean hasMore = loaded.size() > limit;
            List<Candidate> candidates = hasMore ? loaded.subList(0, limit) : loaded;
            response.setHasMore(hasMore);
            if (!candidates.isEmpty()) {
                response.setNextCursorVersionId(candidates.get(candidates.size() - 1).item().getVersionId());
            }
            response.setItems(candidates.stream().map(Candidate::item).toList());
            summarize(response);
            if (!dryRun) {
                execute(candidates, response);
            }
            String runStatus = response.getFailedCount() > 0 ? "failed" : "succeeded";
            auditService.finishRun(runId, runStatus, response.getCandidateCount(),
                    dryRun ? response.getEligibleCount() : response.getPurgedCount(),
                    response.getBlockedCount() + response.getSkippedCount(),
                    response.getBlockedCount() + response.getFailedCount(), metrics(response),
                    response.getFailedCount() > 0 ? "One or more article body purges failed" : null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(), response.getPurgedCount(),
                    response.getBlockedCount() + response.getSkippedCount(),
                    response.getBlockedCount() + response.getFailedCount() + 1,
                    metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private List<Candidate> loadCandidates(ArticleBodyPurgeRequest request,
                                           int retentionDays,
                                           int archiveGraceHours,
                                           int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS article_id,
                       a.project_id,
                       a.status AS article_status,
                       a.current_version_no,
                       COALESCE(a.published_at, pr.max_published_at, pr.max_created_at) AS effective_published_at,
                       v.id AS version_id,
                       v.version_no,
                       v.content_markdown,
                       v.content_object_key,
                       v.content_checksum,
                       v.content_archived_at,
                       v.content_purged_at,
                       v.created_at AS version_created_at,
                       COALESCE(pr.publish_record_count, 0) AS publish_record_count,
                       (
                         SELECT COUNT(*)
                           FROM distribution_tasks dt
                          WHERE dt.article_id = a.id
                            AND dt.status NOT IN (%s)
                       ) AS active_distribution_task_count,
                       (
                         SELECT COUNT(*)
                           FROM self_media_publish_schedule sm
                          WHERE sm.article_id = a.id
                            AND sm.status IN (%s)
                       ) AS active_self_media_schedule_count
                  FROM article_draft_version v
                  JOIN article_draft a ON a.id = v.article_id
                  LEFT JOIN (
                        SELECT article_id,
                               COUNT(*) AS publish_record_count,
                               MAX(published_at) AS max_published_at,
                               MAX(created_at) AS max_created_at
                          FROM article_publish_record
                         WHERE publish_status IN (%s)
                         GROUP BY article_id
                  ) pr ON pr.article_id = a.id
                 WHERE NULLIF(TRIM(v.content_markdown), '') IS NOT NULL
                   AND NULLIF(TRIM(v.content_object_key), '') IS NOT NULL
                   AND NULLIF(TRIM(v.content_checksum), '') IS NOT NULL
                   AND v.content_archived_at IS NOT NULL
                   AND v.content_purged_at IS NULL
                   AND v.content_archived_at <= ?
                   AND (
                        ((v.version_no <> a.current_version_no OR COALESCE(a.status, '') = 'deleted')
                         AND v.created_at <= ?)
                        OR
                        (v.version_no = a.current_version_no
                         AND COALESCE(a.status, '') <> 'deleted'
                         AND COALESCE(a.published_at, pr.max_published_at, pr.max_created_at) <= ?)
                   )
                """.formatted(
                TERMINAL_DISTRIBUTION_STATUS_SQL,
                ACTIVE_SELF_MEDIA_STATUS_SQL,
                ArticlePublishRecordStatusPolicy.ARCHIVE_DELIVERED_STATUS_SQL));
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(LocalDateTime.now().minusHours(archiveGraceHours)));
        LocalDateTime hotCutoff = LocalDateTime.now().minusDays(retentionDays);
        args.add(Timestamp.valueOf(hotCutoff));
        args.add(Timestamp.valueOf(hotCutoff));
        if (request.getProjectId() != null) {
            sql.append("   AND a.project_id = ?\n");
            args.add(request.getProjectId());
        }
        if (request.getCursorVersionId() != null) {
            sql.append("   AND v.id > ?\n");
            args.add(request.getCursorVersionId());
        }
        sql.append(" ORDER BY v.id ASC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ArticleArchiveDryRunItemVO item = new ArticleArchiveDryRunItemVO();
            item.setArticleId(rs.getLong("article_id"));
            item.setVersionId(rs.getLong("version_id"));
            item.setProjectId(rs.getLong("project_id"));
            item.setVersionNo(rs.getInt("version_no"));
            Integer currentVersionNo = rs.getObject("current_version_no", Integer.class);
            boolean currentVersion = currentVersionNo != null && currentVersionNo.equals(item.getVersionNo());
            item.setCurrentVersion(currentVersion);
            item.setArticleStatus(rs.getString("article_status"));
            item.setContentArchivedAt(rs.getTimestamp("content_archived_at").toLocalDateTime());
            item.setContentPurgedAt(toLocalDateTime(rs.getTimestamp("content_purged_at")));
            item.setPublishedAt(toLocalDateTime(rs.getTimestamp("effective_published_at")));
            item.setPublishRecordCount(rs.getInt("publish_record_count"));
            item.setActiveDistributionTaskCount(rs.getInt("active_distribution_task_count"));
            item.setActiveSelfMediaScheduleCount(rs.getInt("active_self_media_schedule_count"));
            String body = rs.getString("content_markdown");
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            item.setContentBytes((long) bodyBytes.length);
            item.setContentChecksum(rs.getString("content_checksum"));
            item.setPlannedObjectKey(rs.getString("content_object_key"));
            List<String> blocked = verifyCandidate(item, bodyBytes);
            if (currentVersion && !"deleted".equalsIgnoreCase(item.getArticleStatus())) {
                if (item.getPublishRecordCount() == null || item.getPublishRecordCount() <= 0) {
                    blocked.add("article_publish_record_missing");
                }
                if (item.getActiveDistributionTaskCount() != null && item.getActiveDistributionTaskCount() > 0) {
                    blocked.add("active_distribution_task");
                }
                if (item.getActiveSelfMediaScheduleCount() != null && item.getActiveSelfMediaScheduleCount() > 0) {
                    blocked.add("active_self_media_schedule");
                }
            }
            item.setBlockedReasons(blocked);
            item.setEligible(blocked.isEmpty());
            item.setAction(blocked.isEmpty() ? "purge_db_body" : "blocked");
            item.setResult(blocked.isEmpty() ? "pending" : "blocked");
            item.setMetrics(Map.of(
                    "objectKey", item.getPlannedObjectKey(),
                    "archiveChecksum", item.getContentChecksum(),
                    "dbChecksum", sha256Hex(bodyBytes)
            ));
            return new Candidate(item, body);
        }, args.toArray());
    }

    private List<String> verifyCandidate(ArticleArchiveDryRunItemVO item, byte[] bodyBytes) {
        List<String> blocked = new ArrayList<>();
        String dbChecksum = sha256Hex(bodyBytes);
        if (!dbChecksum.equalsIgnoreCase(item.getContentChecksum())) {
            blocked.add("db_body_checksum_changed_after_archive");
            return blocked;
        }
        try {
            byte[] archived = objectStorageService.readBytes(item.getPlannedObjectKey());
            if (!sha256Hex(archived).equalsIgnoreCase(item.getContentChecksum())) {
                blocked.add("archive_checksum_mismatch");
            }
        } catch (Exception ex) {
            blocked.add("archive_object_unavailable");
        }
        return blocked;
    }

    private void execute(List<Candidate> candidates, ArticleBodyPurgeResponse response) {
        int purged = 0;
        int skipped = 0;
        int failed = 0;
        for (Candidate candidate : candidates) {
            ArticleArchiveDryRunItemVO item = candidate.item();
            if (!Boolean.TRUE.equals(item.getEligible())) {
                item.setResult("blocked");
                continue;
            }
            try {
                byte[] archived = objectStorageService.readBytes(item.getPlannedObjectKey());
                if (!sha256Hex(archived).equalsIgnoreCase(item.getContentChecksum())) {
                    item.setResult("failed");
                    item.setErrorMessage("archive_checksum_mismatch");
                    failed++;
                    continue;
                }
                int updated = jdbcTemplate.update("""
                        UPDATE article_draft_version
                           SET content_markdown = NULL,
                               content_purged_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                           AND content_markdown = ?
                           AND content_object_key = ?
                           AND content_checksum = ?
                           AND content_purged_at IS NULL
                        """, item.getVersionId(), candidate.body(), item.getPlannedObjectKey(),
                        item.getContentChecksum());
                if (updated == 1) {
                    item.setResult("purged");
                    item.setContentPurgedAt(LocalDateTime.now());
                    purged++;
                } else {
                    item.setResult("skipped");
                    item.setErrorMessage("article_version_state_changed");
                    skipped++;
                }
            } catch (Exception ex) {
                item.setResult("failed");
                item.setErrorMessage(trimError(ex.getMessage()));
                failed++;
                log.warn("Article body purge failed, versionId={}", item.getVersionId(), ex);
            }
        }
        response.setPurgedCount(purged);
        response.setSkippedCount(skipped);
        response.setFailedCount(failed);
    }

    private void summarize(ArticleBodyPurgeResponse response) {
        response.setCandidateCount(response.getItems().size());
        int eligible = (int) response.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEligible()))
                .count();
        response.setEligibleCount(eligible);
        response.setBlockedCount(response.getCandidateCount() - eligible);
        response.setEstimatedBytes(response.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEligible()))
                .mapToLong(item -> item.getContentBytes() == null ? 0L : item.getContentBytes())
                .sum());
    }

    private Map<String, Object> startMetrics(ArticleBodyPurgeResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", response.getProjectId());
        metrics.put("simulationOnly", response.getSimulationOnly());
        metrics.put("retentionDays", response.getRetentionDays());
        metrics.put("archiveGraceHours", response.getArchiveGraceHours());
        metrics.put("limit", response.getLimit());
        if (response.getReason() != null) {
            metrics.put("reason", response.getReason());
        }
        return metrics;
    }

    private Map<String, Object> metrics(ArticleBodyPurgeResponse response) {
        Map<String, Object> metrics = startMetrics(response);
        metrics.put("hasMore", response.getHasMore());
        metrics.put("nextCursorVersionId", response.getNextCursorVersionId());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("eligibleCount", response.getEligibleCount());
        metrics.put("blockedCount", response.getBlockedCount());
        metrics.put("purgedCount", response.getPurgedCount());
        metrics.put("skippedCount", response.getSkippedCount());
        metrics.put("failedCount", response.getFailedCount());
        metrics.put("estimatedBytes", response.getEstimatedBytes());
        return metrics;
    }

    private int normalizePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(value, MAX_LIMIT);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String trimError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record Candidate(ArticleArchiveDryRunItemVO item, String body) {
    }
}
