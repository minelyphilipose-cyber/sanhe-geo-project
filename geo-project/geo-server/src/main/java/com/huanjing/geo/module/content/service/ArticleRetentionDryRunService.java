package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.constant.ArticlePublishRecordStatusPolicy;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunItemVO;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleRetentionDryRunService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final int DEFAULT_MIN_PUBLISHED_AGE_DAYS = 90;
    private static final String CONTENT_TYPE_MARKDOWN = "text/markdown; charset=utf-8";
    private static final String TERMINAL_DISTRIBUTION_STATUS_SQL =
            "'submitted','confirmed','published','failed','cancelled'";
    private static final String ACTIVE_SELF_MEDIA_STATUS_SQL =
            "'pending','filling','filled_verified','scheduling','scheduled','publish_due',"
                    + "'checking_publish_result','published_url_pending','publish_unknown','cancel_pending_platform'";

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;
    private final ObjectStorageService objectStorageService;
    private final DataRetentionProperties retentionProperties;

    public ArticleArchiveDryRunResponse dryRunArchive(ArticleArchiveDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        request.setDryRun(true);
        return runArchive(request);
    }

    public ArticleArchiveDryRunResponse archive(ArticleArchiveDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        return runArchive(request);
    }

    public ArticleArchiveDryRunResponse runScheduled(ArticleArchiveDryRunRequest request, boolean dryRun) {
        request.setDryRun(dryRun);
        return runArchive(request);
    }

    private ArticleArchiveDryRunResponse runArchive(ArticleArchiveDryRunRequest request) {
        boolean dryRun = request.getDryRun() == null || Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun && !retentionProperties.getArticleArchive().isExecuteEnabled()) {
            throw new BizException(403, "Article archive execute is disabled by geo.retention.article-archive.execute-enabled");
        }
        if (!dryRun && !StringUtils.hasText(request.getReason())) {
            throw new BizException(400, "reason is required for article archive");
        }
        LocalDate startDate = request.getPublishedStartDate();
        LocalDate endDate = request.getPublishedEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "publishedEndDate must be greater than or equal to publishedStartDate");
        }
        int limit = normalizeLimit(request.getLimit());
        int requestedMinAgeDays = normalizeMinAge(request.getMinPublishedAgeDays());
        boolean simulationOnly = dryRun && requestedMinAgeDays < DEFAULT_MIN_PUBLISHED_AGE_DAYS;
        int minAgeDays = dryRun
                ? requestedMinAgeDays
                : Math.max(requestedMinAgeDays, DEFAULT_MIN_PUBLISHED_AGE_DAYS);
        ArticleArchiveDryRunResponse response = new ArticleArchiveDryRunResponse();
        response.setDryRun(dryRun);
        response.setSimulationOnly(simulationOnly);
        response.setProjectId(request.getProjectId());
        response.setPublishedStartDate(startDate);
        response.setPublishedEndDate(endDate);
        response.setMinPublishedAgeDays(minAgeDays);
        response.setLimit(limit);
        response.setReason(normalizeReason(request.getReason()));

        Map<String, Object> startMetrics = new LinkedHashMap<>();
        startMetrics.put("projectId", request.getProjectId());
        startMetrics.put("simulationOnly", simulationOnly);
        startMetrics.put("limit", limit);
        startMetrics.put("minPublishedAgeDays", minAgeDays);
        if (response.getReason() != null) {
            startMetrics.put("reason", response.getReason());
        }
        Long runId = auditService.startRun("article_body_archive", dryRun ? "dry_run" : "execute", startDate, endDate, startMetrics);
        response.setRetentionRunId(runId);
        try {
            List<ArticleArchiveCandidate> loaded = loadCandidates(
                    request.getProjectId(), startDate, endDate, minAgeDays,
                    request.getCursorVersionId(), limit + 1);
            boolean hasMore = loaded.size() > limit;
            List<ArticleArchiveCandidate> candidates = hasMore ? loaded.subList(0, limit) : loaded;
            response.setHasMore(hasMore);
            if (!candidates.isEmpty()) {
                response.setNextCursorVersionId(candidates.get(candidates.size() - 1).item().getVersionId());
            }
            response.setItems(candidates.stream().map(ArticleArchiveCandidate::item).toList());
            summarize(response);
            if (!dryRun) {
                executeArchive(candidates, response);
            }
            String runStatus = response.getFailedCount() > 0 ? "failed" : "succeeded";
            auditService.finishRun(runId, runStatus, response.getCandidateCount(),
                    dryRun ? response.getEligibleCount() : response.getArchivedCount(),
                    dryRun ? response.getBlockedCount() : response.getSkippedCount() + response.getBlockedCount(),
                    response.getBlockedCount() + response.getFailedCount(), metrics(response),
                    response.getFailedCount() > 0 ? "One or more article archives failed" : null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(),
                    dryRun ? response.getEligibleCount() : response.getArchivedCount(),
                    dryRun ? response.getBlockedCount() : response.getSkippedCount() + response.getBlockedCount(),
                    response.getBlockedCount() + response.getFailedCount() + 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private List<ArticleArchiveCandidate> loadCandidates(Long projectId,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         int minAgeDays,
                                                         Long cursorVersionId,
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
                  FROM article_draft a
                  JOIN article_draft_version v
                    ON v.article_id = a.id
                  LEFT JOIN (
                        SELECT article_id,
                               COUNT(*) AS publish_record_count,
                               MAX(published_at) AS max_published_at,
                               MAX(created_at) AS max_created_at
                          FROM article_publish_record
                         WHERE publish_status IN (%s)
                         GROUP BY article_id
                  ) pr ON pr.article_id = a.id
                 WHERE v.content_object_key IS NULL
                   AND v.content_archived_at IS NULL
                   AND v.content_purged_at IS NULL
                   AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL
                   AND (
                        ((v.version_no <> a.current_version_no OR COALESCE(a.status, '') = 'deleted')
                         AND v.created_at <= ?)
                        OR
                        (v.version_no = a.current_version_no
                         AND COALESCE(a.status, '') <> 'deleted'
                         AND COALESCE(a.published_at, pr.max_published_at, pr.max_created_at) <= ?)
                   )
                """);
        List<Object> args = new ArrayList<>();
        LocalDateTime hotCutoff = LocalDateTime.now().minusDays(minAgeDays);
        args.add(Timestamp.valueOf(hotCutoff));
        args.add(Timestamp.valueOf(hotCutoff));
        if (projectId != null) {
            sql.append("   AND a.project_id = ?\n");
            args.add(projectId);
        }
        if (startDate != null) {
            sql.append("   AND COALESCE(a.published_at, pr.max_published_at, pr.max_created_at) >= ?\n");
            args.add(Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            sql.append("   AND COALESCE(a.published_at, pr.max_published_at, pr.max_created_at) < ?\n");
            args.add(Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));
        }
        if (cursorVersionId != null) {
            sql.append("   AND v.id > ?\n");
            args.add(cursorVersionId);
        }
        sql.append("""
                 ORDER BY v.id ASC
                 LIMIT ?
                """);
        sql = new StringBuilder(sql.toString().formatted(
                TERMINAL_DISTRIBUTION_STATUS_SQL,
                ACTIVE_SELF_MEDIA_STATUS_SQL,
                ArticlePublishRecordStatusPolicy.ARCHIVE_DELIVERED_STATUS_SQL));
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String body = rs.getString("content_markdown");
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String checksum = sha256Hex(bodyBytes);
            Timestamp publishedAtTs = rs.getTimestamp("effective_published_at");
            LocalDateTime publishedAt = publishedAtTs == null ? null : publishedAtTs.toLocalDateTime();
            int publishRecordCount = rs.getInt("publish_record_count");
            int activeDistributionTaskCount = rs.getInt("active_distribution_task_count");
            int activeSelfMediaScheduleCount = rs.getInt("active_self_media_schedule_count");
            List<String> blocked = new ArrayList<>();
            Integer currentVersionNo = rs.getObject("current_version_no", Integer.class);
            Integer versionNo = rs.getObject("version_no", Integer.class);
            boolean currentVersion = currentVersionNo != null && currentVersionNo.equals(versionNo);
            if (currentVersion && !"deleted".equalsIgnoreCase(rs.getString("article_status"))) {
                if (publishRecordCount <= 0) {
                    blocked.add("article_publish_record_missing");
                }
                if (publishedAt == null) {
                    blocked.add("published_time_missing");
                }
                if (activeDistributionTaskCount > 0) {
                    blocked.add("active_distribution_task");
                }
                if (activeSelfMediaScheduleCount > 0) {
                    blocked.add("active_self_media_schedule");
                }
            }
            ArticleArchiveDryRunItemVO item = new ArticleArchiveDryRunItemVO();
            item.setArticleId(rs.getLong("article_id"));
            item.setVersionId(rs.getLong("version_id"));
            item.setProjectId(rs.getLong("project_id"));
            item.setVersionNo(versionNo);
            item.setCurrentVersion(currentVersion);
            item.setArticleStatus(rs.getString("article_status"));
            item.setPublishedAt(publishedAt);
            item.setContentArchivedAt(toLocalDateTime(rs.getTimestamp("content_archived_at")));
            item.setContentPurgedAt(toLocalDateTime(rs.getTimestamp("content_purged_at")));
            item.setPublishRecordCount(publishRecordCount);
            item.setActiveDistributionTaskCount(activeDistributionTaskCount);
            item.setActiveSelfMediaScheduleCount(activeSelfMediaScheduleCount);
            item.setContentBytes((long) bodyBytes.length);
            item.setContentChecksum(checksum);
            item.setPlannedObjectKey(plannedObjectKey(item.getProjectId(), item.getArticleId(), item.getVersionNo()));
            item.setBlockedReasons(blocked);
            item.setEligible(blocked.isEmpty());
            item.setAction(blocked.isEmpty() ? "archive_body" : "blocked");
            item.setResult(blocked.isEmpty() ? "pending" : "blocked");
            item.setMetrics(Map.of(
                    "existingObjectKey", nullToEmpty(rs.getString("content_object_key")),
                    "existingChecksum", nullToEmpty(rs.getString("content_checksum")),
                    "versionCreatedAt", rs.getTimestamp("version_created_at").toLocalDateTime(),
                    "minPublishedAgeDays", minAgeDays
            ));
            return new ArticleArchiveCandidate(item, body, bodyBytes, rs.getString("content_object_key"), rs.getString("content_checksum"));
        }, args.toArray());
    }

    private void executeArchive(List<ArticleArchiveCandidate> candidates, ArticleArchiveDryRunResponse response) {
        int archived = 0;
        int skipped = 0;
        int failed = 0;
        for (ArticleArchiveCandidate candidate : candidates) {
            ArticleArchiveDryRunItemVO item = candidate.item();
            if (!Boolean.TRUE.equals(item.getEligible())) {
                skipped++;
                item.setResult("blocked");
                continue;
            }
            try {
                ArchiveResult result = archiveOne(candidate);
                item.setResult(result.result());
                item.setErrorMessage(result.errorMessage());
                if ("archived".equals(result.result())) {
                    archived++;
                } else if ("skipped".equals(result.result())) {
                    skipped++;
                } else {
                    failed++;
                }
            } catch (Exception ex) {
                failed++;
                item.setResult("failed");
                item.setErrorMessage(trimError(ex.getMessage()));
                log.warn("Article body archive failed, versionId={}, articleId={}",
                        item.getVersionId(), item.getArticleId(), ex);
            }
        }
        response.setArchivedCount(archived);
        response.setSkippedCount(skipped);
        response.setFailedCount(failed);
    }

    private ArchiveResult archiveOne(ArticleArchiveCandidate candidate) {
        ArticleArchiveDryRunItemVO item = candidate.item();
        if (StringUtils.hasText(candidate.existingObjectKey())) {
            return verifyExistingArchive(candidate);
        }
        String objectKey = item.getPlannedObjectKey();
        objectStorageService.putBytes(objectKey, candidate.bodyBytes(), CONTENT_TYPE_MARKDOWN);
        String readbackChecksum = sha256Hex(objectStorageService.readBytes(objectKey));
        if (!item.getContentChecksum().equalsIgnoreCase(readbackChecksum)) {
            return new ArchiveResult("failed", "archive_readback_checksum_mismatch");
        }
        int updated = jdbcTemplate.update("""
                UPDATE article_draft_version
                   SET content_object_key = ?,
                       content_checksum = ?,
                       content_archived_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                   AND content_object_key IS NULL
                   AND content_archived_at IS NULL
                   AND content_purged_at IS NULL
                   AND content_markdown = ?
                   AND EXISTS (
                        SELECT 1
                          FROM article_draft a
                         WHERE a.id = article_draft_version.article_id
                   )
                """,
                objectKey, item.getContentChecksum(), item.getVersionId(), candidate.body());
        if (updated == 1) {
            item.setContentArchivedAt(LocalDateTime.now());
            item.setMetrics(Map.of(
                    "objectKey", objectKey,
                    "checksum", item.getContentChecksum(),
                    "bytes", item.getContentBytes()
            ));
            return new ArchiveResult("archived", null);
        }
        if (isAlreadyArchived(item.getVersionId(), objectKey, item.getContentChecksum())) {
            return new ArchiveResult("skipped", "already_archived_by_concurrent_run");
        }
        return new ArchiveResult("failed", "article_version_state_changed_after_object_write");
    }

    private boolean isAlreadyArchived(Long versionId, String objectKey, String checksum) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM article_draft_version
                 WHERE id = ?
                   AND content_object_key = ?
                   AND content_checksum = ?
                   AND content_archived_at IS NOT NULL
                """, Integer.class, versionId, objectKey, checksum);
        return count != null && count > 0;
    }

    private ArchiveResult verifyExistingArchive(ArticleArchiveCandidate candidate) {
        ArticleArchiveDryRunItemVO item = candidate.item();
        byte[] bytes;
        try {
            bytes = objectStorageService.readBytes(candidate.existingObjectKey());
        } catch (Exception ex) {
            return new ArchiveResult("failed", "existing_archive_object_unavailable");
        }
        String readbackChecksum = sha256Hex(bytes);
        String expectedChecksum = StringUtils.hasText(candidate.existingChecksum())
                ? candidate.existingChecksum()
                : item.getContentChecksum();
        if (!expectedChecksum.equalsIgnoreCase(readbackChecksum)) {
            return new ArchiveResult("failed", "existing_archive_checksum_mismatch");
        }
        item.setMetrics(Map.of(
                "objectKey", candidate.existingObjectKey(),
                "checksum", readbackChecksum,
                "bytes", bytes.length
        ));
        return new ArchiveResult("skipped", "already_archived");
    }

    private void summarize(ArticleArchiveDryRunResponse response) {
        int candidate = response.getItems().size();
        int eligible = (int) response.getItems().stream().filter(item -> Boolean.TRUE.equals(item.getEligible())).count();
        long bytes = response.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEligible()))
                .mapToLong(item -> item.getContentBytes() == null ? 0L : item.getContentBytes())
                .sum();
        response.setCandidateCount(candidate);
        response.setEligibleCount(eligible);
        response.setBlockedCount(candidate - eligible);
        response.setEstimatedBytes(bytes);
    }

    private Map<String, Object> metrics(ArticleArchiveDryRunResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectId", response.getProjectId());
        metrics.put("simulationOnly", response.getSimulationOnly());
        if (response.getReason() != null) {
            metrics.put("reason", response.getReason());
        }
        metrics.put("limit", response.getLimit());
        metrics.put("hasMore", response.getHasMore());
        metrics.put("nextCursorVersionId", response.getNextCursorVersionId());
        metrics.put("minPublishedAgeDays", response.getMinPublishedAgeDays());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("eligibleCount", response.getEligibleCount());
        metrics.put("blockedCount", response.getBlockedCount());
        metrics.put("archivedCount", response.getArchivedCount());
        metrics.put("skippedCount", response.getSkippedCount());
        metrics.put("failedCount", response.getFailedCount());
        metrics.put("estimatedBytes", response.getEstimatedBytes());
        return metrics;
    }

    private String plannedObjectKey(Long projectId, Long articleId, Integer versionNo) {
        return "archive/article/%s/%s/v%s.md".formatted(projectId, articleId, versionNo);
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private int normalizeMinAge(Integer minPublishedAgeDays) {
        if (minPublishedAgeDays == null) {
            return DEFAULT_MIN_PUBLISHED_AGE_DAYS;
        }
        return Math.max(0, minPublishedAgeDays);
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record ArticleArchiveCandidate(ArticleArchiveDryRunItemVO item,
                                           String body,
                                           byte[] bodyBytes,
                                           String existingObjectKey,
                                           String existingChecksum) {
    }

    private record ArchiveResult(String result, String errorMessage) {
    }
}
