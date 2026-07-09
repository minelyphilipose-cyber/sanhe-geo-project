package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        List<String> failedSources = new ArrayList<>();
        for (PublishTaskRow row : candidates) {
            try {
                inserted += insertRecord(row);
            } catch (RuntimeException ex) {
                failedSources.add(row.sourceType() + ":" + row.sourceId());
            }
        }
        return new CompensationResult(false, candidates.size(), inserted, candidates.size() - inserted, failedSources);
    }

    private List<PublishTaskRow> loadCandidates(int limit) {
        return jdbcTemplate.query("""
                SELECT *
                  FROM (
                        SELECT 'distribution_task' AS source_type,
                               t.id AS source_id,
                               t.id AS distribution_task_id,
                               t.article_id,
                               t.project_id,
                               t.self_media_account_id,
                               COALESCE(sma.brand_id, ad.source_brand_id) AS brand_id,
                               t.target_kind,
                               CASE
                                 WHEN t.target_kind = 'brand_geo_site' THEN 'official_site'
                                 WHEN t.target_kind = 'industry_site' THEN 'industry_site'
                                 WHEN t.target_kind = 'forum_site' THEN 'forum_site'
                                 ELSE COALESCE(sma.platform, t.integration_method, t.target_kind)
                               END AS target_channel,
                               NULLIF(TRIM(t.published_url), '') AS published_url,
                               CASE
                                 WHEN NULLIF(TRIM(t.published_url), '') REGEXP '^https?://' THEN 'public_url'
                                 WHEN NULLIF(TRIM(t.published_url), '') IS NOT NULL THEN 'manage_url'
                                 ELSE 'missing'
                               END AS url_quality,
                               CASE
                                 WHEN NULLIF(TRIM(t.published_url), '') IS NOT NULL THEN 'distribution_tasks.published_url'
                                 WHEN NULLIF(TRIM(t.platform_article_id), '') IS NOT NULL THEN 'distribution_tasks.platform_article_id'
                                 WHEN NULLIF(TRIM(t.platform_publish_id), '') IS NOT NULL THEN 'distribution_tasks.platform_publish_id'
                                 ELSE 'distribution_tasks.status'
                               END AS url_source,
                               t.platform_article_id,
                               t.platform_publish_id,
                               CASE
                                 WHEN t.target_kind IN ('brand_geo_site', 'industry_site', 'forum_site') THEN 'distributed'
                                 ELSE t.status
                               END AS publish_status,
                               ad.title,
                               ad.cover_image_url AS cover_url,
                               t.response_payload AS raw_response,
                               COALESCE(t.published_at, t.finished_at, t.created_at) AS published_at,
                               COALESCE(t.published_at, t.finished_at, t.created_at) AS verified_at
                          FROM distribution_tasks t
                          LEFT JOIN self_media_account sma ON sma.id = t.self_media_account_id
                          LEFT JOIN article_draft ad ON ad.id = t.article_id
                         LEFT JOIN article_publish_record r
                                 ON r.source_type = 'distribution_task'
                                AND r.source_id = t.id
                         WHERE r.id IS NULL
                           AND t.target_kind <> 'brand_official_site'
                           AND (
                                 t.status = 'published'
                              OR (t.status = 'submitted' AND t.finished_at IS NOT NULL)
                              OR (t.review_status IN ('published', 'offline') AND t.finished_at IS NOT NULL)
                           )
                           AND (
                                 NULLIF(TRIM(t.published_url), '') IS NOT NULL
                              OR NULLIF(TRIM(t.platform_article_id), '') IS NOT NULL
                              OR NULLIF(TRIM(t.platform_publish_id), '') IS NOT NULL
                              OR t.status = 'published'
                              OR t.review_status IN ('published', 'offline')
                           )
                        UNION ALL
                        SELECT 'self_media_publish_schedule' AS source_type,
                               s.id AS source_id,
                               s.distribution_task_id,
                               s.article_id,
                               d.project_id,
                               s.self_media_account_id,
                               s.brand_id,
                               'self_media' AS target_kind,
                               s.platform AS target_channel,
                               NULLIF(TRIM(s.platform_published_url), '') AS published_url,
                               CASE
                                 WHEN NULLIF(TRIM(s.platform_published_url), '') REGEXP '^https?://' THEN 'public_url'
                                 WHEN NULLIF(TRIM(s.platform_published_url), '') IS NOT NULL THEN 'manage_url'
                                 ELSE 'missing'
                               END AS url_quality,
                               CASE
                                 WHEN NULLIF(TRIM(s.platform_published_url), '') IS NOT NULL THEN 'self_media_publish_schedule.platform_published_url'
                                 WHEN NULLIF(TRIM(s.platform_publish_id), '') IS NOT NULL THEN 'self_media_publish_schedule.platform_publish_id'
                                 ELSE 'self_media_publish_schedule.status'
                               END AS url_source,
                               d.platform_article_id,
                               s.platform_publish_id,
                               s.status AS publish_status,
                               ad.title,
                               ad.cover_image_url AS cover_url,
                               d.response_payload AS raw_response,
                               COALESCE(s.published_confirmed_at, s.updated_at, s.created_at) AS published_at,
                               COALESCE(s.published_confirmed_at, s.updated_at, s.created_at) AS verified_at
                          FROM self_media_publish_schedule s
                          LEFT JOIN distribution_tasks d ON d.id = s.distribution_task_id
                          LEFT JOIN article_draft ad ON ad.id = s.article_id
                          LEFT JOIN article_publish_record r
                                 ON r.source_type = 'self_media_publish_schedule'
                                AND r.source_id = s.id
                         WHERE r.id IS NULL
                           AND s.status IN ('published_confirmed', 'published_url_pending')
                           AND (
                                 NULLIF(TRIM(s.platform_published_url), '') IS NOT NULL
                              OR NULLIF(TRIM(s.platform_publish_id), '') IS NOT NULL
                              OR s.published_confirmed_at IS NOT NULL
                           )
                       ) candidate
                 ORDER BY COALESCE(verified_at, published_at), source_type, source_id
                LIMIT ?
                """, (rs, rowNum) -> new PublishTaskRow(
                rs.getString("source_type"),
                nullableLong(rs, "source_id"),
                nullableLong(rs, "distribution_task_id"),
                nullableLong(rs, "article_id"),
                nullableLong(rs, "project_id"),
                nullableLong(rs, "self_media_account_id"),
                nullableLong(rs, "brand_id"),
                rs.getString("target_kind"),
                rs.getString("target_channel"),
                rs.getString("published_url"),
                rs.getString("url_quality"),
                rs.getString("url_source"),
                rs.getString("platform_article_id"),
                rs.getString("platform_publish_id"),
                rs.getString("publish_status"),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("raw_response"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime(),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toLocalDateTime()
        ), limit);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private int insertRecord(PublishTaskRow row) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO article_publish_record (
                    article_id,
                    distribution_task_id,
                    project_id,
                    self_media_account_id,
                    brand_id,
                    source_type,
                    source_id,
                    target_kind,
                    target_channel,
                    published_url,
                    url_quality,
                    url_source,
                    platform_article_id,
                    platform_publish_id,
                    publish_status,
                    title,
                    cover_url,
                    raw_response,
                    published_at,
                    verified_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.articleId(),
                row.distributionTaskId(),
                row.projectId(),
                row.selfMediaAccountId(),
                row.brandId(),
                row.sourceType(),
                row.sourceId(),
                row.targetKind(),
                row.targetChannel(),
                row.publishedUrl(),
                row.urlQuality(),
                row.urlSource(),
                row.platformArticleId(),
                row.platformPublishId(),
                row.publishStatus(),
                row.title(),
                row.coverUrl(),
                row.rawResponse(),
                row.publishedAt(),
                row.verifiedAt()
        );
    }

    private record PublishTaskRow(String sourceType,
                                  Long sourceId,
                                  Long distributionTaskId,
                                  Long articleId,
                                  Long projectId,
                                  Long selfMediaAccountId,
                                  Long brandId,
                                  String targetKind,
                                  String targetChannel,
                                  String publishedUrl,
                                  String urlQuality,
                                  String urlSource,
                                  String platformArticleId,
                                  String platformPublishId,
                                  String publishStatus,
                                  String title,
                                  String coverUrl,
                                  String rawResponse,
                                  LocalDateTime publishedAt,
                                  LocalDateTime verifiedAt) {
    }

    public record CompensationResult(boolean dryRun,
                                     int candidates,
                                     int inserted,
                                     int skipped,
                                     List<String> failedSources) {
    }
}
