package com.huanjing.geo.module.retention.website;

final class WebsiteRetentionEligibilitySql {
    private static final String WEBSITE_TARGETS = "'brand_geo_site','industry_site','forum_site'";
    private static final String SUCCESS_TASK_STATUSES = "'submitted','confirmed','published'";
    private static final String TERMINAL_TASK_STATUSES = "'confirmed','published','failed','cancelled'";
    private static final String NON_BLOCKING_SELF_MEDIA = "'published_confirmed','cancelled'";

    private WebsiteRetentionEligibilitySql() {
    }

    static String candidateIdsSql() {
        return """
                SELECT a.id
                  FROM article_draft a
                 WHERE %s
                   AND a.id <= ?
                 ORDER BY a.id
                 LIMIT ?
                """.formatted(candidateWhereSql("a"));
    }

    static String candidateWhereSql(String articleAlias) {
        return """
                %s
                AND NOT EXISTS (
                    SELECT 1 FROM data_retention_failure_active_key failure_key
                     WHERE failure_key.domain = 'website_published_content'
                       AND failure_key.business_key = CAST(%s.id AS CHAR)
                )
                AND NOT EXISTS (
                    SELECT 1
                      FROM data_retention_batch_item held_item
                      JOIN data_retention_operation held_operation
                        ON held_operation.operation_id = held_item.operation_id
                     WHERE held_item.domain = 'website_published_content'
                       AND held_item.business_key = %s.id
                       AND held_operation.outcome_status IN (
                           'pending','rolled_back','unknown','manual_review'
                       )
                )
                """.formatted(eligibilityPredicate(articleAlias), articleAlias, articleAlias);
    }

    static String eligibilityPredicate(String articleAlias) {
        return """
                EXISTS (
                    SELECT 1 FROM article_publish_record evidence_r
                     WHERE evidence_r.article_id = %1$s.id AND %2$s
                     GROUP BY evidence_r.article_id
                    HAVING MAX(COALESCE(evidence_r.published_at, evidence_r.verified_at, evidence_r.created_at)) <= ?
                )
                AND (
                    %1$s.status IN ('distributed','published')
                    OR (%1$s.status = 'deleted' AND %3$s)
                )
                AND NOT EXISTS (
                    SELECT 1 FROM distribution_tasks active_dt
                     WHERE active_dt.article_id = %1$s.id AND %4$s
                )
                AND NOT EXISTS (
                    SELECT 1 FROM self_media_publish_schedule active_sm
                     WHERE active_sm.article_id = %1$s.id
                       AND active_sm.status NOT IN (%5$s)
                )
                AND (%1$s.status <> 'deleted' OR %3$s)
                """.formatted(articleAlias, successfulRecordSql("evidence_r"), actualTargetSql(articleAlias),
                activeTaskSql("active_dt"), NON_BLOCKING_SELF_MEDIA);
    }

    static String lockedEligibilitySql(String placeholders) {
        return "SELECT COUNT(*) FROM article_draft a WHERE a.id IN (" + placeholders + ") AND "
                + eligibilityPredicate("a");
    }

    static String actualTargetSql(String articleAlias) {
        return """
                (
                    EXISTS (SELECT 1 FROM article_draft_version hot_v
                             WHERE hot_v.article_id = %1$s.id
                               AND NULLIF(TRIM(hot_v.content_markdown), '') IS NOT NULL)
                    OR EXISTS (SELECT 1 FROM distribution_tasks hot_dt
                                WHERE hot_dt.article_id = %1$s.id
                                  AND (hot_dt.request_payload IS NOT NULL
                                       OR hot_dt.fill_payload IS NOT NULL
                                       OR hot_dt.response_payload IS NOT NULL)
                                  AND %2$s)
                    OR EXISTS (SELECT 1 FROM article_publish_record hot_r
                                WHERE hot_r.article_id = %1$s.id
                                  AND hot_r.raw_response IS NOT NULL AND %3$s)
                )
                """.formatted(articleAlias, successfulTaskSql("hot_dt"), successfulRecordSql("hot_r"));
    }

    static String successfulTaskSql(String alias) {
        return """
                %1$s.target_kind IN (%2$s)
                AND %1$s.status IN (%3$s)
                AND EXISTS (SELECT 1 FROM article_publish_record task_r
                             WHERE task_r.source_type = 'distribution_task'
                               AND task_r.source_id = %1$s.id
                               AND task_r.article_id = %1$s.article_id
                               AND task_r.target_kind = %1$s.target_kind
                               AND %4$s)
                """.formatted(alias, WEBSITE_TARGETS, SUCCESS_TASK_STATUSES, successfulRecordCoreSql("task_r"));
    }

    static String successfulRecordSql(String alias) {
        return successfulRecordCoreSql(alias) + " AND EXISTS (SELECT 1 FROM distribution_tasks evidence_dt"
                + " WHERE evidence_dt.id = " + alias + ".source_id"
                + " AND evidence_dt.article_id = " + alias + ".article_id"
                + " AND evidence_dt.target_kind = " + alias + ".target_kind"
                + " AND evidence_dt.status IN (" + SUCCESS_TASK_STATUSES + "))";
    }

    static String activeTaskSql(String alias) {
        return """
                %1$s.status NOT IN (%2$s)
                AND NOT (
                    %1$s.status = 'submitted' AND %1$s.target_kind IN (%3$s)
                    AND EXISTS (SELECT 1 FROM article_publish_record submitted_r
                                 WHERE submitted_r.source_id = %1$s.id
                                   AND submitted_r.article_id = %1$s.article_id
                                   AND submitted_r.target_kind = %1$s.target_kind
                                   AND %4$s)
                )
                """.formatted(alias, TERMINAL_TASK_STATUSES, WEBSITE_TARGETS,
                successfulRecordCoreSql("submitted_r"));
    }

    private static String successfulRecordCoreSql(String alias) {
        return alias + ".target_kind IN (" + WEBSITE_TARGETS + ")"
                + " AND " + alias + ".publish_status = 'distributed'"
                + " AND " + alias + ".source_type = 'distribution_task'";
    }
}
