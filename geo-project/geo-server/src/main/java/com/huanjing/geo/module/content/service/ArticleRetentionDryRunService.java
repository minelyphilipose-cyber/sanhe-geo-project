package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunItemVO;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.retention.service.DataRetentionRunAuditService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
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
public class ArticleRetentionDryRunService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final int DEFAULT_MIN_PUBLISHED_AGE_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;

    public ArticleArchiveDryRunResponse dryRunArchive(ArticleArchiveDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        LocalDate startDate = request.getPublishedStartDate();
        LocalDate endDate = request.getPublishedEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(400, "publishedEndDate must be greater than or equal to publishedStartDate");
        }
        int limit = normalizeLimit(request.getLimit());
        int minAgeDays = normalizeMinAge(request.getMinPublishedAgeDays());
        ArticleArchiveDryRunResponse response = new ArticleArchiveDryRunResponse();
        response.setProjectId(request.getProjectId());
        response.setPublishedStartDate(startDate);
        response.setPublishedEndDate(endDate);
        response.setMinPublishedAgeDays(minAgeDays);
        response.setLimit(limit);

        Map<String, Object> startMetrics = new LinkedHashMap<>();
        startMetrics.put("projectId", request.getProjectId());
        startMetrics.put("limit", limit);
        startMetrics.put("minPublishedAgeDays", minAgeDays);
        Long runId = auditService.startRun("article_body_archive", "dry_run", startDate, endDate, startMetrics);
        response.setRetentionRunId(runId);
        try {
            List<ArticleArchiveDryRunItemVO> items = loadCandidates(request.getProjectId(), startDate, endDate, minAgeDays, limit);
            response.setItems(items);
            summarize(response);
            auditService.finishRun(runId, "succeeded", response.getCandidateCount(), response.getEligibleCount(),
                    response.getBlockedCount(), response.getBlockedCount(), metrics(response), null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(), response.getEligibleCount(),
                    response.getBlockedCount(), response.getBlockedCount() + 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private List<ArticleArchiveDryRunItemVO> loadCandidates(Long projectId,
                                                            LocalDate startDate,
                                                            LocalDate endDate,
                                                            int minAgeDays,
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
                       COALESCE(pr.publish_record_count, 0) AS publish_record_count
                  FROM article_draft a
                  JOIN article_draft_version v
                    ON v.article_id = a.id
                   AND v.version_no = a.current_version_no
                  LEFT JOIN (
                        SELECT article_id,
                               COUNT(*) AS publish_record_count,
                               MAX(published_at) AS max_published_at,
                               MAX(created_at) AS max_created_at
                          FROM article_publish_record
                         WHERE publish_status IN ('published', 'success', 'completed')
                         GROUP BY article_id
                  ) pr ON pr.article_id = a.id
                 WHERE a.status IN ('published', 'distributed')
                   AND v.content_purged_at IS NULL
                   AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
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
        sql.append("""
                 ORDER BY effective_published_at ASC, a.id ASC
                 LIMIT ?
                """);
        args.add(limit);
        LocalDateTime hotCutoff = LocalDateTime.now().minusDays(minAgeDays);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String body = rs.getString("content_markdown");
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String checksum = sha256Hex(bodyBytes);
            Timestamp publishedAtTs = rs.getTimestamp("effective_published_at");
            LocalDateTime publishedAt = publishedAtTs == null ? null : publishedAtTs.toLocalDateTime();
            int publishRecordCount = rs.getInt("publish_record_count");
            List<String> blocked = new ArrayList<>();
            if (publishRecordCount <= 0) {
                blocked.add("article_publish_record_missing");
            }
            if (publishedAt == null) {
                blocked.add("published_time_missing");
            } else if (publishedAt.isAfter(hotCutoff)) {
                blocked.add("hot_retention_window_not_elapsed");
            }
            if (!"published".equalsIgnoreCase(rs.getString("article_status"))
                    && !"distributed".equalsIgnoreCase(rs.getString("article_status"))) {
                blocked.add("article_not_published");
            }
            Integer currentVersionNo = rs.getObject("current_version_no", Integer.class);
            Integer versionNo = rs.getObject("version_no", Integer.class);
            if (currentVersionNo == null || versionNo == null || !currentVersionNo.equals(versionNo)) {
                blocked.add("not_current_final_version");
            }
            ArticleArchiveDryRunItemVO item = new ArticleArchiveDryRunItemVO();
            item.setArticleId(rs.getLong("article_id"));
            item.setVersionId(rs.getLong("version_id"));
            item.setProjectId(rs.getLong("project_id"));
            item.setVersionNo(versionNo);
            item.setArticleStatus(rs.getString("article_status"));
            item.setPublishedAt(publishedAt);
            item.setContentArchivedAt(toLocalDateTime(rs.getTimestamp("content_archived_at")));
            item.setContentPurgedAt(toLocalDateTime(rs.getTimestamp("content_purged_at")));
            item.setPublishRecordCount(publishRecordCount);
            item.setContentBytes((long) bodyBytes.length);
            item.setContentChecksum(checksum);
            item.setPlannedObjectKey(plannedObjectKey(item.getProjectId(), item.getArticleId(), item.getVersionNo()));
            item.setBlockedReasons(blocked);
            item.setEligible(blocked.isEmpty());
            item.setMetrics(Map.of(
                    "existingObjectKey", nullToEmpty(rs.getString("content_object_key")),
                    "existingChecksum", nullToEmpty(rs.getString("content_checksum")),
                    "minPublishedAgeDays", minAgeDays
            ));
            return item;
        }, args.toArray());
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
        metrics.put("limit", response.getLimit());
        metrics.put("minPublishedAgeDays", response.getMinPublishedAgeDays());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("eligibleCount", response.getEligibleCount());
        metrics.put("blockedCount", response.getBlockedCount());
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
