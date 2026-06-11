package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticlePublishRecordCompensationService {

    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    @Transactional
    public CompensationResult backfillPublishedTasks(int requestedLimit, boolean dryRun) {
        currentUserService.ensurePermission("dispatch.task.release");
        currentUserService.requireCurrentUser();
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<PublishTaskRow> candidates = loadCandidates(limit);
        if (dryRun || candidates.isEmpty()) {
            return new CompensationResult(dryRun, candidates.size(), 0, candidates.size(), List.of());
        }
        int inserted = 0;
        List<Long> failedTaskIds = new ArrayList<>();
        for (PublishTaskRow row : candidates) {
            try {
                inserted += insertRecord(row);
            } catch (RuntimeException ex) {
                failedTaskIds.add(row.distributionTaskId());
            }
        }
        return new CompensationResult(false, candidates.size(), inserted, candidates.size() - inserted, failedTaskIds);
    }

    private List<PublishTaskRow> loadCandidates(int limit) {
        return jdbcTemplate.query("""
                SELECT t.id AS distribution_task_id,
                       t.article_id,
                       t.project_id,
                       t.target_kind,
                       COALESCE(sma.platform, t.integration_method, t.target_kind) AS target_channel,
                       t.published_url,
                       t.platform_article_id,
                       t.platform_publish_id,
                       t.status AS publish_status,
                       COALESCE(t.published_at, t.finished_at, t.created_at) AS published_at
                FROM distribution_tasks t
                LEFT JOIN self_media_account sma ON sma.id = t.self_media_account_id
                LEFT JOIN article_publish_record r
                       ON r.source_type = 'distribution_task'
                      AND r.source_id = t.id
                WHERE r.id IS NULL
                  AND t.published_url IS NOT NULL
                  AND TRIM(t.published_url) <> ''
                  AND (
                        t.status = 'published'
                     OR (t.status = 'submitted' AND t.finished_at IS NOT NULL)
                     OR (t.review_status IN ('published', 'offline') AND t.finished_at IS NOT NULL)
                  )
                ORDER BY COALESCE(t.published_at, t.finished_at, t.created_at), t.id
                LIMIT ?
                """, (rs, rowNum) -> new PublishTaskRow(
                rs.getLong("distribution_task_id"),
                rs.getLong("article_id"),
                rs.getLong("project_id"),
                rs.getString("target_kind"),
                rs.getString("target_channel"),
                rs.getString("published_url"),
                rs.getString("platform_article_id"),
                rs.getString("platform_publish_id"),
                rs.getString("publish_status"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime()
        ), limit);
    }

    private int insertRecord(PublishTaskRow row) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO article_publish_record (
                    article_id,
                    distribution_task_id,
                    project_id,
                    source_type,
                    source_id,
                    target_kind,
                    target_channel,
                    published_url,
                    platform_article_id,
                    platform_publish_id,
                    publish_status,
                    published_at
                ) VALUES (?, ?, ?, 'distribution_task', ?, ?, ?, ?, ?, ?, ?)
                """,
                row.articleId(),
                row.distributionTaskId(),
                row.projectId(),
                row.distributionTaskId(),
                row.targetKind(),
                row.targetChannel(),
                row.publishedUrl(),
                row.platformArticleId(),
                row.platformPublishId(),
                row.publishStatus(),
                row.publishedAt()
        );
    }

    private record PublishTaskRow(Long distributionTaskId,
                                  Long articleId,
                                  Long projectId,
                                  String targetKind,
                                  String targetChannel,
                                  String publishedUrl,
                                  String platformArticleId,
                                  String platformPublishId,
                                  String publishStatus,
                                  LocalDateTime publishedAt) {
    }

    public record CompensationResult(boolean dryRun,
                                     int candidates,
                                     int inserted,
                                     int skipped,
                                     List<Long> failedTaskIds) {
    }
}
