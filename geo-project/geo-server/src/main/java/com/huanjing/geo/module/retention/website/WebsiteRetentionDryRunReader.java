package com.huanjing.geo.module.retention.website;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class WebsiteRetentionDryRunReader {
    private final JdbcTemplate jdbc;

    public WebsiteRetentionDryRunReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DryRunPage readPage(Instant cutoff, Long cursorArticleId, int limit) {
        if (cutoff == null || cursorArticleId != null && cursorArticleId < 1 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Website dry-run page bounds are invalid");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS article_id,
                       a.project_id,
                       a.status AS article_status,
                       (SELECT MAX(COALESCE(last_r.published_at, last_r.verified_at, last_r.created_at))
                          FROM article_publish_record last_r
                         WHERE last_r.article_id = a.id AND %s) AS last_published_at,
                       (SELECT COUNT(*) FROM article_draft_version v
                         WHERE v.article_id = a.id
                           AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL) AS body_row_count,
                       (SELECT COALESCE(SUM(OCTET_LENGTH(v.content_markdown)), 0)
                          FROM article_draft_version v
                         WHERE v.article_id = a.id
                           AND NULLIF(TRIM(v.content_markdown), '') IS NOT NULL) AS body_bytes,
                       (SELECT COUNT(*) FROM distribution_tasks dt
                         WHERE dt.article_id = a.id
                           AND (dt.request_payload IS NOT NULL OR dt.fill_payload IS NOT NULL
                                OR dt.response_payload IS NOT NULL)
                           AND %s) AS payload_row_count,
                       (SELECT COALESCE(SUM(
                                  OCTET_LENGTH(COALESCE(CAST(dt.request_payload AS CHAR), ''))
                                + OCTET_LENGTH(COALESCE(CAST(dt.fill_payload AS CHAR), ''))
                                + OCTET_LENGTH(COALESCE(CAST(dt.response_payload AS CHAR), ''))), 0)
                          FROM distribution_tasks dt
                         WHERE dt.article_id = a.id
                           AND (dt.request_payload IS NOT NULL OR dt.fill_payload IS NOT NULL
                                OR dt.response_payload IS NOT NULL)
                           AND %s) AS payload_bytes
                  FROM article_draft a
                 WHERE %s
                """.formatted(
                WebsiteRetentionEligibilitySql.successfulRecordSql("last_r"),
                WebsiteRetentionEligibilitySql.successfulTaskSql("dt"),
                WebsiteRetentionEligibilitySql.successfulTaskSql("dt"),
                WebsiteRetentionEligibilitySql.candidateWhereSql("a")));
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(LocalDateTime.ofInstant(
                cutoff, WebsiteRetentionBusinessRepository.BUSINESS_ZONE)));
        if (cursorArticleId != null) {
            sql.append(" AND a.id > ?\n");
            args.add(cursorArticleId);
        }
        sql.append(" ORDER BY a.id LIMIT ?");
        args.add(limit + 1);
        List<DryRunCandidate> loaded = jdbc.query(sql.toString(), (rs, rowNum) -> new DryRunCandidate(
                rs.getLong("article_id"),
                rs.getLong("project_id"),
                rs.getString("article_status"),
                rs.getTimestamp("last_published_at").toLocalDateTime(),
                rs.getInt("body_row_count"),
                rs.getLong("body_bytes"),
                rs.getInt("payload_row_count"),
                rs.getLong("payload_bytes")
        ), args.toArray());
        boolean hasMore = loaded.size() > limit;
        List<DryRunCandidate> candidates = hasMore ? List.copyOf(loaded.subList(0, limit)) : List.copyOf(loaded);
        Long nextCursor = candidates.isEmpty() ? null : candidates.get(candidates.size() - 1).articleId();
        return new DryRunPage(candidates, hasMore, nextCursor);
    }

    public record DryRunPage(List<DryRunCandidate> candidates, boolean hasMore, Long nextCursorArticleId) {
        public DryRunPage {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record DryRunCandidate(Long articleId,
                                  Long projectId,
                                  String articleStatus,
                                  LocalDateTime lastPublishedAt,
                                  int bodyRowCount,
                                  long bodyBytes,
                                  int payloadRowCount,
                                  long payloadBytes) {
    }
}
