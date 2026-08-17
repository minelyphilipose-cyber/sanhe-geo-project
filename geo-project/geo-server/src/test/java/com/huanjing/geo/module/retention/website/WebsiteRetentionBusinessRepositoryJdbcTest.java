package com.huanjing.geo.module.retention.website;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedWebsiteMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebsiteRetentionBusinessRepositoryJdbcTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private WebsiteRetentionBusinessRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wp6_business;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        repository = new WebsiteRetentionBusinessRepository(jdbc, transactionManager);
        createSchema();
    }

    @Test
    void deletedPayloadReheatUsesActualColumnsAndMutatesTheLockedBatch() {
        insertDeletedPayloadOnly(101, true);
        insertDeletedPayloadOnly(102, false);

        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        WebsiteRetentionBusinessRepository.Selection selected = repository.selectInitial(cutoff, 20);

        assertEquals(List.of(101L), selected.articleIds());
        RetentionOperationContext operation = operation(cutoff, selected.upperBound(), 101L);
        WebsiteRetentionBusinessRepository.MutationResult result = transaction.execute(status -> repository.mutate(
                new LockedWebsiteMutationContext(operation, 1, 11, List.of(101L), null), Instant.now()));

        assertEquals(1, result.articleCount());
        assertEquals(1, result.payloadRowsPurged());
        assertEquals("deleted", jdbc.queryForObject(
                "SELECT status FROM article_draft WHERE id = 101", String.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE article_id = 101
                   AND (request_payload IS NOT NULL OR fill_payload IS NOT NULL OR response_payload IS NOT NULL)
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE article_id = 101 AND payload_purged_at IS NOT NULL
                """, Integer.class));
        assertEquals(WebsiteRetentionBusinessRepository.EvidenceState.POST,
                repository.inspectEvidence(operation));
        assertEquals(List.of(), repository.selectCandidates(cutoff, selected.upperBound(), 20));
    }

    @Test
    void reviewPollWritebackClearsMarkerAndMakesDeletedPayloadEligibleAgain() {
        insertDeletedPayloadOnly(103, true);
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        WebsiteRetentionBusinessRepository.Selection selected = repository.selectInitial(cutoff, 20);
        RetentionOperationContext operation = operation(cutoff, selected.upperBound(), 103L);
        transaction.executeWithoutResult(status -> repository.mutate(
                new LockedWebsiteMutationContext(operation, 1, 11, List.of(103L), null), Instant.now()));

        jdbc.update("UPDATE distribution_tasks SET response_payload = ?, payload_purged_at = NULL WHERE article_id = ?",
                "{\"review_status\":\"under_review\"}", 103L);

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM distribution_tasks"
                + " WHERE article_id = 103 AND response_payload IS NOT NULL AND payload_purged_at IS NULL",
                Integer.class));
        assertEquals(List.of(103L), repository.selectCandidates(cutoff, selected.upperBound(), 20));

        WebsiteRetentionBusinessRepository.MutationResult reheated = transaction.execute(status -> repository.mutate(
                new LockedWebsiteMutationContext(operation, 1, 12, List.of(103L), null), Instant.now()));
        assertEquals(1, reheated.payloadRowsPurged());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM distribution_tasks"
                + " WHERE article_id = 103 AND response_payload IS NULL AND payload_purged_at IS NOT NULL",
                Integer.class));
    }

    private RetentionOperationContext operation(Instant cutoff, long upperBound, long articleId) {
        return new RetentionOperationContext("00000000-0000-0000-0000-000000000001", 1,
                "website_batch", new byte[32], 1, 1, cutoff, upperBound,
                null, null, null, null,
                new RetentionCompatibilityContract("website_published_content", 1, new byte[32],
                        "website-published-v1"), "test", "test", OperationReplayStatus.CREATED,
                "pending", null, List.of(articleId));
    }

    private void insertDeletedPayloadOnly(long articleId, boolean hotPayload) {
        jdbc.update("INSERT INTO article_draft (id, status, updated_at) VALUES (?, 'deleted', CURRENT_TIMESTAMP)",
                articleId);
        jdbc.update("INSERT INTO article_draft_version (id, article_id, content_markdown, content_purged_at)"
                + " VALUES (?, ?, NULL, CURRENT_TIMESTAMP)", articleId * 10, articleId);
        jdbc.update("""
                INSERT INTO distribution_tasks (
                  id, article_id, target_kind, status, request_payload, fill_payload,
                  response_payload, payload_purged_at
                ) VALUES (?, ?, 'brand_geo_site', 'submitted', ?, NULL, NULL, CURRENT_TIMESTAMP)
                """, articleId * 100, articleId, hotPayload ? "{\"late\":true}" : null);
        jdbc.update("""
                INSERT INTO article_publish_record (
                  id, article_id, source_type, source_id, target_kind, publish_status,
                  published_at, verified_at, created_at, raw_response
                ) VALUES (?, ?, 'distribution_task', ?, 'brand_geo_site', 'distributed',
                          DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """, articleId * 1000, articleId, articleId * 100);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE article_draft (id BIGINT PRIMARY KEY, project_id BIGINT,"
                + " status VARCHAR(32), updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE article_draft_version (id BIGINT PRIMARY KEY, article_id BIGINT,"
                + " content_markdown CLOB, content_purged_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE distribution_tasks (id BIGINT PRIMARY KEY, article_id BIGINT,"
                + " project_id BIGINT, target_kind VARCHAR(64), status VARCHAR(32), published_url VARCHAR(500),"
                + " request_payload CLOB, fill_payload CLOB, response_payload CLOB, payload_purged_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE article_publish_record (id BIGINT PRIMARY KEY, article_id BIGINT,"
                + " source_type VARCHAR(64), source_id BIGINT, target_kind VARCHAR(64), publish_status VARCHAR(32),"
                + " published_url VARCHAR(500), url_quality VARCHAR(64), published_at TIMESTAMP,"
                + " verified_at TIMESTAMP, created_at TIMESTAMP, raw_response CLOB)");
        jdbc.execute("CREATE TABLE self_media_publish_schedule (id BIGINT PRIMARY KEY, article_id BIGINT,"
                + " status VARCHAR(32))");
        jdbc.execute("CREATE TABLE data_retention_failure_active_key (domain VARCHAR(64), business_key VARCHAR(128))");
        jdbc.execute("CREATE TABLE data_retention_batch_item (operation_id VARCHAR(36), domain VARCHAR(64),"
                + " business_key BIGINT)");
        jdbc.execute("CREATE TABLE data_retention_operation (operation_id VARCHAR(36), outcome_status VARCHAR(32))");
    }
}
