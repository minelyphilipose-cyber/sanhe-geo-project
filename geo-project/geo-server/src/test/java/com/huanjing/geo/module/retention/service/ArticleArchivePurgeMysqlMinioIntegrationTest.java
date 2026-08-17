package com.huanjing.geo.module.retention.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.storage.MinioObjectStorageAdapter;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeRequest;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices.ExternalMysql;
import com.huanjing.geo.module.system.service.CurrentUserService;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleArchivePurgeMysqlMinioIntegrationTest {

    private static final String REQUIRED = "retention.article-it.required";
    private static final String MINIO_ENDPOINT = "RETENTION_MINIO_IT_ENDPOINT";
    private static final String MINIO_ACCESS_KEY = "RETENTION_MINIO_IT_ACCESS_KEY";
    private static final String MINIO_SECRET_KEY = "RETENTION_MINIO_IT_SECRET_KEY";
    private static final String MINIO_REGION = "RETENTION_MINIO_IT_REGION";
    private static final String BUCKET_PREFIX = "geo-retention-article-minio-it-";
    private static final long PROJECT_ID = 9_910_000_001L;
    private static final long ARTICLE_ID = 9_910_000_002L;
    private static final long VERSION_ID = 9_910_000_003L;
    private static final String BODY = "# Article retention MinIO acceptance\n\nverified body\n";

    private ExternalMysql mysql;
    private JdbcTemplate jdbc;
    private MinioClient minioClient;
    private MinioObjectStorageAdapter storage;
    private String bucket;
    private boolean ready;

    @BeforeAll
    void configureInfrastructure() throws Exception {
        if (!RetentionExternalTestServices.mysqlConfigured() || !minioConfigured()) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException(
                        "Required article MySQL/MinIO integration infrastructure is unavailable");
            }
            return;
        }
        mysql = RetentionExternalTestServices.createMysql("geo_retention_article_it_");
        jdbc = new JdbcTemplate(mysql.dataSource());

        String endpoint = requiredEnvironment(MINIO_ENDPOINT);
        String accessKey = requiredEnvironment(MINIO_ACCESS_KEY);
        String secretKey = requiredEnvironment(MINIO_SECRET_KEY);
        String region = environmentOrDefault(MINIO_REGION, "us-east-1");
        bucket = BUCKET_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
        storage = new MinioObjectStorageAdapter(minioClient);
        ReflectionTestUtils.setField(storage, "endpoint", endpoint);
        ReflectionTestUtils.setField(storage, "publicEndpoint", endpoint);
        ReflectionTestUtils.setField(storage, "accessKey", accessKey);
        ReflectionTestUtils.setField(storage, "secretKey", secretKey);
        ReflectionTestUtils.setField(storage, "bucket", bucket);
        ReflectionTestUtils.setField(storage, "region", region);
        ready = true;
    }

    @BeforeEach
    void installFixture() {
        Assumptions.assumeTrue(ready, "MySQL and MinIO integration infrastructure is unavailable");
        jdbc.execute("DROP TABLE IF EXISTS self_media_publish_schedule");
        jdbc.execute("DROP TABLE IF EXISTS distribution_tasks");
        jdbc.execute("DROP TABLE IF EXISTS article_publish_record");
        jdbc.execute("DROP TABLE IF EXISTS article_draft_version");
        jdbc.execute("DROP TABLE IF EXISTS article_draft");
        jdbc.execute("DROP TABLE IF EXISTS data_retention_run");
        jdbc.execute("""
                CREATE TABLE data_retention_run (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  domain VARCHAR(64) NOT NULL,
                  mode VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  retention_window_start DATE NULL,
                  retention_window_end DATE NULL,
                  candidate_count BIGINT NOT NULL DEFAULT 0,
                  affected_count BIGINT NOT NULL DEFAULT 0,
                  skipped_count BIGINT NOT NULL DEFAULT 0,
                  warning_count BIGINT NOT NULL DEFAULT 0,
                  metrics_json JSON NULL,
                  error_message VARCHAR(2000) NULL,
                  started_at DATETIME(3) NOT NULL,
                  finished_at DATETIME(3) NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE article_draft (
                  id BIGINT NOT NULL PRIMARY KEY,
                  project_id BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  current_version_no INT NOT NULL,
                  published_at DATETIME(3) NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE article_draft_version (
                  id BIGINT NOT NULL PRIMARY KEY,
                  article_id BIGINT NOT NULL,
                  version_no INT NOT NULL,
                  content_markdown LONGTEXT NULL,
                  content_object_key VARCHAR(512) NULL,
                  content_checksum CHAR(64) NULL,
                  content_archived_at DATETIME(3) NULL,
                  content_purged_at DATETIME(3) NULL,
                  created_at DATETIME(3) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE article_publish_record (
                  id BIGINT NOT NULL PRIMARY KEY,
                  article_id BIGINT NOT NULL,
                  publish_status VARCHAR(32) NOT NULL,
                  published_at DATETIME(3) NULL,
                  created_at DATETIME(3) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE distribution_tasks (
                  id BIGINT NOT NULL PRIMARY KEY,
                  article_id BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE self_media_publish_schedule (
                  id BIGINT NOT NULL PRIMARY KEY,
                  article_id BIGINT NOT NULL,
                  status VARCHAR(64) NOT NULL
                )
                """);

        LocalDateTime old = LocalDateTime.now().minusDays(100);
        jdbc.update("""
                INSERT INTO article_draft
                  (id, project_id, status, current_version_no, published_at)
                VALUES (?, ?, 'published', 1, ?)
                """, ARTICLE_ID, PROJECT_ID, old);
        jdbc.update("""
                INSERT INTO article_draft_version
                  (id, article_id, version_no, content_markdown, created_at)
                VALUES (?, ?, 1, ?, ?)
                """, VERSION_ID, ARTICLE_ID, BODY, old);
        jdbc.update("""
                INSERT INTO article_publish_record
                  (id, article_id, publish_status, published_at, created_at)
                VALUES (?, ?, 'published', ?, ?)
                """, 9_910_000_004L, ARTICLE_ID, old, old);
    }

    @AfterAll
    void releaseInfrastructure() throws Exception {
        Exception cleanupFailure = null;
        try {
            if (storage != null && bucket != null) {
                if (!bucket.startsWith(BUCKET_PREFIX)) {
                    throw new IllegalStateException("Refusing to delete unexpected MinIO bucket");
                }
                storage.deletePrefix("");
                minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            cleanupFailure = ex;
        } finally {
            if (mysql != null) {
                mysql.close();
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @Test
    void archivesToRealMinioThenPurgesOnlyTheDatabaseBodyAfterGracePeriod() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getArticleArchive().setExecuteEnabled(true);
        properties.getArticlePurge().setExecuteEnabled(true);
        DataRetentionRunAuditService audit = new DataRetentionRunAuditService(jdbc, new ObjectMapper());

        ArticleRetentionDryRunService archiveService = new ArticleRetentionDryRunService(
                jdbc, mock(CurrentUserService.class), audit, storage, properties);
        ArticleArchiveDryRunRequest archiveRequest = new ArticleArchiveDryRunRequest();
        archiveRequest.setProjectId(PROJECT_ID);
        archiveRequest.setMinPublishedAgeDays(90);
        archiveRequest.setLimit(10);
        archiveRequest.setReason("required local MySQL MinIO article archive acceptance");

        ArticleArchiveDryRunResponse archive = archiveService.runScheduled(archiveRequest, false);

        assertThat(archive.getCandidateCount()).isEqualTo(1);
        assertThat(archive.getEligibleCount()).isEqualTo(1);
        assertThat(archive.getArchivedCount()).isEqualTo(1);
        assertThat(archive.getFailedCount()).isZero();
        String objectKey = archive.getItems().get(0).getPlannedObjectKey();
        assertThat(objectKey).isEqualTo(
                "archive/article/" + PROJECT_ID + "/" + ARTICLE_ID + "/v1.md");
        assertThat(storage.readBytes(objectKey)).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE id = ?
                   AND content_markdown = ?
                   AND content_object_key = ?
                   AND content_checksum IS NOT NULL
                   AND content_archived_at IS NOT NULL
                   AND content_purged_at IS NULL
                """, Integer.class, VERSION_ID, BODY, objectKey)).isEqualTo(1);

        jdbc.update("""
                UPDATE article_draft_version
                   SET content_archived_at = UTC_TIMESTAMP(3) - INTERVAL 25 HOUR
                 WHERE id = ?
                """, VERSION_ID);

        ArticleBodyPurgeService purgeService = new ArticleBodyPurgeService(
                jdbc, mock(CurrentUserService.class), storage, audit, properties);
        ArticleBodyPurgeRequest purgeRequest = new ArticleBodyPurgeRequest();
        purgeRequest.setProjectId(PROJECT_ID);
        purgeRequest.setRetentionDays(90);
        purgeRequest.setArchiveGraceHours(24);
        purgeRequest.setLimit(10);
        purgeRequest.setReason("required local MySQL MinIO article purge acceptance");

        ArticleBodyPurgeResponse purge = purgeService.runScheduled(purgeRequest, false);

        assertThat(purge.getCandidateCount()).isEqualTo(1);
        assertThat(purge.getEligibleCount()).isEqualTo(1);
        assertThat(purge.getPurgedCount()).isEqualTo(1);
        assertThat(purge.getFailedCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM article_draft_version
                 WHERE id = ?
                   AND content_markdown IS NULL
                   AND content_object_key = ?
                   AND content_checksum IS NOT NULL
                   AND content_archived_at IS NOT NULL
                   AND content_purged_at IS NOT NULL
                """, Integer.class, VERSION_ID, objectKey)).isEqualTo(1);
        assertThat(storage.readBytes(objectKey)).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_run
                 WHERE status = 'succeeded'
                   AND domain IN ('article_body_archive', 'article_body_purge')
                   AND candidate_count = 1
                   AND affected_count = 1
                """, Integer.class)).isEqualTo(2);
    }

    private boolean minioConfigured() {
        return hasText(System.getenv(MINIO_ENDPOINT))
                && hasText(System.getenv(MINIO_ACCESS_KEY))
                && hasText(System.getenv(MINIO_SECRET_KEY));
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (!hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
