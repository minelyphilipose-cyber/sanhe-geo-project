package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebsitePublishedContentCleanupJdbcTest {

    private JdbcTemplate jdbcTemplate;
    private WebsitePublishedContentCleanupService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:website_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        createSchema();

        DataRetentionRunAuditService auditService = mock(DataRetentionRunAuditService.class);
        when(auditService.startRun(anyString(), anyString(), any(), any(), any())).thenReturn(91L);
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        service = new WebsitePublishedContentCleanupService(
                jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                auditService,
                properties,
                mock(CurrentUserService.class)
        );
    }

    @Test
    void cleansSuccessfulOwnedTargetTasksWithoutDependingOnUrls() {
        insertArticle(101L, "distributed", "brand_geo_site", null, "missing", "submitted");
        insertArticle(102L, "distributed", "industry_site",
                "https://industry.example/articles/102", "verified_public_url", "submitted");
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks
                    (id, article_id, target_kind, status, request_payload, fill_payload, response_payload)
                VALUES (1002, 102, 'mp_account', 'submitted', '{}', '{}', '{}')
                """);
        insertArticle(103L, "distributed", "forum_site",
                "https://forum.example/manage/103", "manage_url", "submitted");
        insertArticle(104L, "distributed", "forum_site",
                "https://forum.example/articles/104", "pending_review_url", "submitted");
        jdbcTemplate.update("""
                INSERT INTO self_media_publish_schedule (id, article_id, status)
                VALUES (2004, 104, 'publish_unknown')
                """);
        insertArticle(105L, "approved", "brand_geo_site",
                "https://agent.example/articles/105", "verified_public_url", "submitted");
        insertArticle(106L, "unpublished", "industry_site",
                "https://industry.example/articles/106", "verified_public_url", "submitted");
        insertArticle(107L, "distributed", "industry_site", "https://", "verified_public_url", "submitted");
        insertArticle(108L, "distributed", "brand_geo_site",
                "https://agent.example/articles/108", "verified_public_url", "submitted");
        jdbcTemplate.update("""
                UPDATE article_publish_record
                   SET published_at = DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
                 WHERE article_id = 108
                """);
        insertArticle(109L, "deleted", "brand_geo_site",
                "https://agent.example/articles/109", "verified_public_url", "submitted");
        insertArticle(110L, "deleted", "brand_geo_site",
                "https://agent.example/articles/110", "verified_public_url", "submitted");
        jdbcTemplate.update("""
                UPDATE article_draft_version
                   SET content_purged_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP)
                 WHERE article_id = 110
                """);
        insertArticle(111L, "distributed", "industry_site",
                "https://industry.example/articles/111", "public_url", "submitted");
        insertArticle(112L, "distributed", "forum_site",
                "https://forum.example/thread-shared", "public_url", "submitted");
        insertArticle(113L, "distributed", "forum_site",
                "https://forum.example/thread-shared", "unverified_public_url", "submitted");
        insertArticle(114L, "distributed", "forum_site", null, "missing", "failed");

        WebsitePublishedContentCleanupService.CleanupBatchResult dryRun =
                service.runScheduled(0, 100, null, true, "jdbc dry run");

        assertEquals(24, dryRun.retentionHours());
        assertEquals(7, dryRun.candidateCount());
        assertEquals(101L, dryRun.items().get(0).articleId());
        assertEquals(103L, dryRun.items().get(1).articleId());
        assertEquals(107L, dryRun.items().get(2).articleId());
        assertEquals(110L, dryRun.items().get(3).articleId());
        assertEquals(111L, dryRun.items().get(4).articleId());
        assertEquals(112L, dryRun.items().get(5).articleId());
        assertEquals(113L, dryRun.items().get(6).articleId());

        WebsitePublishedContentCleanupService.CleanupBatchResult executed =
                service.runScheduled(0, 100, null, false, "jdbc execute");

        assertEquals(7, executed.cleanedCount());
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 101", String.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE article_id = 101 AND content_markdown IS NOT NULL
                """, Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE article_id = 101
                   AND (request_payload IS NOT NULL OR fill_payload IS NOT NULL OR response_payload IS NOT NULL)
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_publish_record
                 WHERE article_id = 101
                   AND publish_status = 'distributed'
                   AND published_url IS NULL
                """, Integer.class));
        assertEquals("distributed", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 102", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 103", String.class));
        assertEquals("distributed", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 104", String.class));
        assertEquals("approved", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 105", String.class));
        assertEquals("unpublished", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 106", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 107", String.class));
        assertEquals("distributed", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 108", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 111", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 112", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 113", String.class));
        assertEquals("distributed", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 114", String.class));
        assertEquals("deleted", jdbcTemplate.queryForObject(
                "SELECT status FROM article_draft WHERE id = 109", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE article_id = 109
                   AND content_markdown IS NOT NULL
                """, Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE article_id = 110
                   AND content_markdown IS NOT NULL
                """, Integer.class));
        assertEquals(7, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE article_id IN (102, 104, 105, 106, 108, 109, 114)
                   AND content_markdown IS NOT NULL
                """, Integer.class));
    }

    private void insertArticle(long articleId,
                               String status,
                               String targetKind,
                               String url,
                               String urlQuality,
                               String taskStatus) {
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, project_id, status, updated_at)
                VALUES (?, 501, ?, CURRENT_TIMESTAMP)
                """, articleId, status);
        jdbcTemplate.update("""
                INSERT INTO article_draft_version
                    (id, article_id, content_markdown, content_purged_at)
                VALUES (?, ?, '# body', NULL)
                """, articleId * 10, articleId);
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks
                    (id, article_id, target_kind, status, request_payload, fill_payload, response_payload)
                VALUES (?, ?, ?, ?, '{"body":"large"}', '{"fill":"large"}', '{"result":"ok"}')
                """, articleId * 100, articleId, targetKind, taskStatus);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record (
                    id, article_id, source_type, source_id, target_kind, publish_status,
                    url_quality, published_url, published_at, verified_at, created_at, raw_response
                ) VALUES (
                    ?, ?, 'distribution_task', ?, ?, 'distributed',
                    ?, ?, DATEADD('DAY', -2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, '{"raw":"large"}'
                )
                """, articleId * 1000, articleId, articleId * 100, targetKind, urlQuality, url);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT PRIMARY KEY,
                    project_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_draft_version (
                    id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    content_markdown CLOB,
                    content_purged_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_publish_record (
                    id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    source_type VARCHAR(32) NOT NULL,
                    source_id BIGINT NOT NULL,
                    target_kind VARCHAR(32),
                    publish_status VARCHAR(32) NOT NULL,
                    url_quality VARCHAR(32),
                    published_url VARCHAR(1000),
                    published_at TIMESTAMP,
                    verified_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    raw_response CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE distribution_tasks (
                    id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    request_payload CLOB,
                    fill_payload CLOB,
                    response_payload CLOB,
                    payload_purged_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE self_media_publish_schedule (
                    id BIGINT PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL
                )
                """);
    }
}
