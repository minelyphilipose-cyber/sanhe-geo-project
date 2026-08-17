package com.huanjing.geo.module.retention.website;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedWebsiteMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class WebsiteRetentionBusinessRepository {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String NON_BLOCKING_SELF_MEDIA = "'published_confirmed','cancelled'";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate selectionTransaction;

    public WebsiteRetentionBusinessRepository(JdbcTemplate jdbc,
                                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.selectionTransaction = new TransactionTemplate(transactionManager);
        this.selectionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.selectionTransaction.setReadOnly(true);
        this.selectionTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Selection selectInitial(Instant cutoff, int limit) {
        Selection selected = selectionTransaction.execute(status -> {
            Long upperBound = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM article_draft WHERE id > 0", Long.class);
            long safeUpperBound = upperBound == null ? 0 : upperBound;
            return new Selection(safeUpperBound,
                    safeUpperBound == 0 ? List.of() : selectCandidates(cutoff, safeUpperBound, limit));
        });
        if (selected == null) {
            throw new DataRetentionExecutionException(503, "WEBSITE_SELECTION_INVALID",
                    "Website selection transaction returned no result");
        }
        return selected;
    }

    public List<Long> selectCandidates(Instant cutoff, long upperBound, int limit) {
        if (cutoff == null || upperBound < 1 || limit < 1 || limit > 20) {
            throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                    "Website selector bounds are invalid");
        }
        LocalDateTime wallCutoff = LocalDateTime.ofInstant(cutoff, BUSINESS_ZONE);
        return jdbc.queryForList(candidateSql(), Long.class, Timestamp.valueOf(wallCutoff), upperBound, limit);
    }

    public MutationResult mutate(LockedWebsiteMutationContext context, Instant businessTime) {
        if (context == null || context.articleIds().isEmpty() || businessTime == null) {
            throw stateChanged("Website mutation context is incomplete");
        }
        List<Long> ids = context.articleIds();
        String placeholders = placeholders(ids.size());
        Object[] idArgs = ids.toArray();
        List<Long> articles = jdbc.queryForList("""
                SELECT id FROM article_draft WHERE id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders), Long.class, idArgs);
        if (!articles.equals(ids)) {
            throw stateChanged("Website article rows changed after operation preparation");
        }
        List<Long> versionIds = jdbc.queryForList("""
                SELECT id FROM article_draft_version WHERE article_id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders), Long.class, idArgs);
        List<Long> publishIds = jdbc.queryForList("""
                SELECT id FROM article_publish_record WHERE article_id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders), Long.class, idArgs);
        List<Long> taskIds = jdbc.queryForList("""
                SELECT id FROM distribution_tasks WHERE article_id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders), Long.class, idArgs);
        List<Long> scheduleIds = jdbc.queryForList("""
                SELECT id FROM self_media_publish_schedule WHERE article_id IN (%s) ORDER BY id FOR UPDATE
                """.formatted(placeholders), Long.class, idArgs);
        List<Map<String, Object>> articleEvidence = snapshot("article_draft", "id, project_id", articles);
        List<Map<String, Object>> versionEvidence = snapshot(
                "article_draft_version", "id, article_id", versionIds);
        List<Map<String, Object>> publishEvidence = snapshot("article_publish_record", """
                id, article_id, source_type, source_id, target_kind, publish_status,
                published_url, url_quality, published_at, verified_at
                """, publishIds);
        List<Map<String, Object>> taskEvidence = snapshot("distribution_tasks", """
                id, article_id, project_id, target_kind, status, published_url
                """, taskIds);
        List<Map<String, Object>> selfMediaEvidence = snapshot(
                "self_media_publish_schedule", "id, article_id, status", scheduleIds);

        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.ofInstant(
                context.operation().websiteCutoff(), BUSINESS_ZONE));
        List<Object> eligibilityArgs = new ArrayList<>();
        eligibilityArgs.addAll(ids);
        eligibilityArgs.add(cutoff);
        Integer eligible = jdbc.queryForObject(lockedEligibilitySql(placeholders), Integer.class,
                eligibilityArgs.toArray());
        if (eligible == null || eligible != ids.size()) {
            throw stateChanged("Website batch eligibility changed while rows were locked");
        }

        List<Long> hotVersionIds = versionIds.isEmpty() ? List.of() : jdbc.queryForList("""
                SELECT id FROM article_draft_version
                 WHERE id IN (%s) AND NULLIF(TRIM(content_markdown), '') IS NOT NULL ORDER BY id
                """.formatted(placeholders(versionIds.size())), Long.class, versionIds.toArray());
        List<Long> hotTaskIds = taskIds.isEmpty() ? List.of() : jdbc.queryForList("""
                SELECT dt.id FROM distribution_tasks dt
                 WHERE dt.id IN (%s)
                   AND (dt.request_payload IS NOT NULL OR dt.fill_payload IS NOT NULL OR dt.response_payload IS NOT NULL)
                   AND %s
                 ORDER BY dt.id
                """.formatted(placeholders(taskIds.size()), successfulTaskSql("dt")), Long.class, taskIds.toArray());
        List<Long> hotPublishIds = publishIds.isEmpty() ? List.of() : jdbc.queryForList("""
                SELECT r.id FROM article_publish_record r
                 WHERE r.id IN (%s) AND r.raw_response IS NOT NULL AND %s ORDER BY r.id
                """.formatted(placeholders(publishIds.size()), successfulRecordSql("r")),
                Long.class, publishIds.toArray());

        Timestamp mutationTime = Timestamp.valueOf(LocalDateTime.ofInstant(businessTime, BUSINESS_ZONE));
        int bodyRows = updateWithIds(hotVersionIds, """
                UPDATE article_draft_version
                   SET content_markdown = NULL, content_purged_at = ?
                 WHERE id IN (%s) AND NULLIF(TRIM(content_markdown), '') IS NOT NULL
                """, mutationTime);
        int payloadRows = updateWithIds(hotTaskIds, """
                UPDATE distribution_tasks
                   SET request_payload = NULL, fill_payload = NULL, response_payload = NULL,
                       payload_purged_at = ?
                 WHERE id IN (%s)
                   AND (request_payload IS NOT NULL OR fill_payload IS NOT NULL OR response_payload IS NOT NULL)
                """, mutationTime);
        int publishRows = updateWithIds(hotPublishIds, """
                UPDATE article_publish_record SET raw_response = NULL
                 WHERE id IN (%s) AND raw_response IS NOT NULL
                """, null);
        List<Object> articleUpdateArgs = new ArrayList<>();
        articleUpdateArgs.add(mutationTime);
        articleUpdateArgs.addAll(ids);
        int articleRows = jdbc.update("""
                UPDATE article_draft SET status = 'deleted', updated_at = ? WHERE id IN (%s)
                """.formatted(placeholders), articleUpdateArgs.toArray());

        requirePostState(hotVersionIds, "article_draft_version",
                "content_markdown IS NULL AND content_purged_at IS NOT NULL");
        requirePostState(hotTaskIds, "distribution_tasks",
                "request_payload IS NULL AND fill_payload IS NULL AND response_payload IS NULL AND payload_purged_at IS NOT NULL");
        requirePostState(hotPublishIds, "article_publish_record", "raw_response IS NULL");
        Integer deleted = jdbc.queryForObject("SELECT COUNT(*) FROM article_draft WHERE id IN (%s) AND status = 'deleted'"
                .formatted(placeholders), Integer.class, idArgs);
        if (deleted == null || deleted != ids.size()
                || countExisting("article_draft_version", versionIds) != versionIds.size()
                || countExisting("article_publish_record", publishIds) != publishIds.size()
                || countExisting("distribution_tasks", taskIds) != taskIds.size()
                || countExisting("self_media_publish_schedule", scheduleIds) != scheduleIds.size()
                || !articleEvidence.equals(snapshot("article_draft", "id, project_id", articles))
                || !versionEvidence.equals(snapshot("article_draft_version", "id, article_id", versionIds))
                || !publishEvidence.equals(snapshot("article_publish_record", """
                        id, article_id, source_type, source_id, target_kind, publish_status,
                        published_url, url_quality, published_at, verified_at
                        """, publishIds))
                || !taskEvidence.equals(snapshot("distribution_tasks", """
                        id, article_id, project_id, target_kind, status, published_url
                        """, taskIds))
                || !selfMediaEvidence.equals(snapshot(
                        "self_media_publish_schedule", "id, article_id, status", scheduleIds))) {
            throw new DataRetentionExecutionException(409, "WEBSITE_POST_CHECK_FAILED",
                    "Website locked-row post-check failed");
        }
        return new MutationResult(ids.size(), bodyRows, payloadRows, publishRows, articleRows);
    }

    public EvidenceState inspectEvidence(RetentionOperationContext operation) {
        if (operation == null || operation.websiteArticleIds().isEmpty()) {
            return EvidenceState.MISSING;
        }
        List<Long> ids = operation.websiteArticleIds();
        String placeholders = placeholders(ids.size());
        Object[] idArgs = ids.toArray();
        Integer present = jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_draft WHERE id IN (" + placeholders + ")",
                Integer.class, idArgs);
        if (present == null || present != ids.size()) return EvidenceState.MISSING;
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.ofInstant(operation.websiteCutoff(), BUSINESS_ZONE));
        List<Object> eligibleArgs = new ArrayList<>();
        eligibleArgs.addAll(ids);
        eligibleArgs.add(cutoff);
        Integer eligible = jdbc.queryForObject(lockedEligibilitySql(placeholders), Integer.class,
                eligibleArgs.toArray());
        if (eligible != null && eligible == ids.size()) return EvidenceState.PRE;
        Integer post = jdbc.queryForObject("""
                SELECT COUNT(*) FROM article_draft a
                 WHERE a.id IN (%s) AND a.status = 'deleted'
                   AND NOT %s
                   AND EXISTS (SELECT 1 FROM article_publish_record evidence_r
                                WHERE evidence_r.article_id = a.id AND %s)
                   AND NOT EXISTS (SELECT 1 FROM distribution_tasks active_dt
                                   WHERE active_dt.article_id = a.id AND %s)
                   AND NOT EXISTS (SELECT 1 FROM self_media_publish_schedule active_sm
                                   WHERE active_sm.article_id = a.id
                                     AND active_sm.status NOT IN (%s))
                """.formatted(placeholders, actualTargetSql("a"), successfulRecordSql("evidence_r"),
                activeTaskSql("active_dt"), NON_BLOCKING_SELF_MEDIA), Integer.class, idArgs);
        if (post != null && post == ids.size()) return EvidenceState.POST;
        Integer activeHolds = jdbc.queryForObject("""
                SELECT COUNT(*) FROM article_draft a
                 WHERE a.id IN (%s)
                   AND (EXISTS (SELECT 1 FROM distribution_tasks active_dt
                                WHERE active_dt.article_id = a.id AND %s)
                        OR EXISTS (SELECT 1 FROM self_media_publish_schedule active_sm
                                   WHERE active_sm.article_id = a.id
                                     AND active_sm.status NOT IN (%s)))
                """.formatted(placeholders, activeTaskSql("active_dt"), NON_BLOCKING_SELF_MEDIA),
                Integer.class, idArgs);
        return activeHolds != null && activeHolds > 0 ? EvidenceState.INELIGIBLE : EvidenceState.MIXED;
    }

    private String candidateSql() {
        return WebsiteRetentionEligibilitySql.candidateIdsSql();
    }

    private static String eligibilityPredicate(String articleAlias) {
        return WebsiteRetentionEligibilitySql.eligibilityPredicate(articleAlias);
    }

    private static String lockedEligibilitySql(String placeholders) {
        return WebsiteRetentionEligibilitySql.lockedEligibilitySql(placeholders);
    }

    private static String actualTargetSql(String articleAlias) {
        return WebsiteRetentionEligibilitySql.actualTargetSql(articleAlias);
    }

    private static String successfulTaskSql(String alias) {
        return WebsiteRetentionEligibilitySql.successfulTaskSql(alias);
    }

    private static String successfulRecordSql(String alias) {
        return WebsiteRetentionEligibilitySql.successfulRecordSql(alias);
    }

    private static String activeTaskSql(String alias) {
        return WebsiteRetentionEligibilitySql.activeTaskSql(alias);
    }

    private int updateWithIds(List<Long> ids, String sqlTemplate, Timestamp timestamp) {
        if (ids.isEmpty()) return 0;
        String sql = sqlTemplate.formatted(placeholders(ids.size()));
        List<Object> args = new ArrayList<>();
        if (timestamp != null) args.add(timestamp);
        args.addAll(ids);
        return jdbc.update(sql, args.toArray());
    }

    private void requirePostState(List<Long> ids, String table, String predicate) {
        if (ids.isEmpty()) return;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id IN ("
                + placeholders(ids.size()) + ") AND " + predicate, Integer.class, ids.toArray());
        if (count == null || count != ids.size()) {
            throw new DataRetentionExecutionException(409, "WEBSITE_POST_CHECK_FAILED",
                    "Website locked-row post-check failed for " + table);
        }
    }

    private int countExisting(String table, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id IN ("
                + placeholders(ids.size()) + ")", Integer.class, ids.toArray());
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> snapshot(String table, String columns, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT " + columns + " FROM " + table + " WHERE id IN ("
                + placeholders(ids.size()) + ") ORDER BY id", ids.toArray());
    }

    private static String placeholders(int count) {
        if (count < 1) throw new IllegalArgumentException("placeholder count must be positive");
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static DataRetentionExecutionException stateChanged(String message) {
        return new DataRetentionExecutionException(409, "WEBSITE_BATCH_STATE_CHANGED", message);
    }

    public record Selection(long upperBound, List<Long> articleIds) {
        public Selection {
            articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        }
    }

    public record MutationResult(int articleCount,
                                 int bodyRowsPurged,
                                 int payloadRowsPurged,
                                 int publishRawRowsSlimmed,
                                 int articleRowsTransitioned) {
        public Map<String, Object> metrics() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("bodyRowsPurged", bodyRowsPurged);
            values.put("payloadRowsPurged", payloadRowsPurged);
            values.put("publishRawRowsSlimmed", publishRawRowsSlimmed);
            values.put("articleRowsTransitioned", articleRowsTransitioned);
            values.put("postCheckSchemaVersion", 1);
            return values;
        }
    }

    public enum EvidenceState { PRE, POST, MIXED, MISSING, INELIGIBLE }
}
