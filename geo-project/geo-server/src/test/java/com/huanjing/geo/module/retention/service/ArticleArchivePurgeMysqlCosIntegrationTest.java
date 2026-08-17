package com.huanjing.geo.module.retention.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.CosObjectStorageAdapter;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.common.storage.StorageProperties;
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
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleArchivePurgeMysqlCosIntegrationTest {

    private static final String REQUIRED = "retention.article-cos-it.required";
    private static final String COS_REGION = "RETENTION_COS_IT_REGION";
    private static final String COS_BUCKET = "RETENTION_COS_IT_BUCKET";
    private static final String COS_ENDPOINT = "RETENTION_COS_IT_ENDPOINT";
    private static final String COS_SECRET_ID = "RETENTION_COS_IT_SECRET_ID";
    private static final String COS_SECRET_KEY = "RETENTION_COS_IT_SECRET_KEY";
    private static final String COS_PREFIX = "RETENTION_COS_IT_PREFIX";
    private static final String REQUIRED_PREFIX_ROOT = "geo-retention-it/";
    private static final long PROJECT_ID = 9_920_000_001L;
    private static final long ARTICLE_ID = 9_920_000_002L;
    private static final long VERSION_ID = 9_920_000_003L;
    private static final String BODY = "# Article retention COS acceptance\n\nverified body\n";

    private ExternalMysql mysql;
    private JdbcTemplate jdbc;
    private CosObjectStorageAdapter cosStorage;
    private ObjectStorageService prefixedStorage;
    private String runPrefix;
    private boolean ready;

    @BeforeAll
    void configureInfrastructure() throws Exception {
        if (!RetentionExternalTestServices.mysqlConfigured() || !cosConfigured()) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException(
                        "Required article MySQL/COS integration infrastructure is unavailable");
            }
            return;
        }
        mysql = RetentionExternalTestServices.createMysql("geo_retention_article_cos_it_");
        jdbc = new JdbcTemplate(mysql.dataSource());

        StorageProperties properties = new StorageProperties();
        properties.setProvider(StorageProperties.Provider.COS);
        StorageProperties.Cos cos = properties.getCos();
        cos.setRegion(requiredEnvironment(COS_REGION));
        cos.setBucket(requiredEnvironment(COS_BUCKET));
        cos.setSecretId(requiredEnvironment(COS_SECRET_ID));
        cos.setSecretKey(requiredEnvironment(COS_SECRET_KEY));
        cos.setEndpoint(environmentOrNull(COS_ENDPOINT));

        runPrefix = safeRunPrefix(requiredEnvironment(COS_PREFIX));
        cosStorage = new CosObjectStorageAdapter(properties);
        prefixedStorage = new PrefixingObjectStorageService(cosStorage, runPrefix);
        ready = true;
    }

    @BeforeEach
    void installFixture() {
        Assumptions.assumeTrue(ready, "MySQL and COS integration infrastructure is unavailable");
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
                """, 9_920_000_004L, ARTICLE_ID, old, old);
    }

    @AfterAll
    void releaseInfrastructure() throws Exception {
        Exception cleanupFailure = null;
        try {
            if (cosStorage != null && runPrefix != null) {
                requireSafeGeneratedPrefix(runPrefix);
                cosStorage.deletePrefix(runPrefix);
                if (!cosStorage.listObjects(runPrefix, 1).isEmpty()) {
                    throw new IllegalStateException("COS integration prefix is not empty after cleanup");
                }
            }
        } catch (Exception ex) {
            cleanupFailure = ex;
        } finally {
            shutdownCosClient();
            if (mysql != null) {
                mysql.close();
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @Test
    void archivesToRealCosThenPurgesOnlyTheDatabaseBodyAndCleansTheExactTestPrefix() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getArticleArchive().setExecuteEnabled(true);
        properties.getArticlePurge().setExecuteEnabled(true);
        DataRetentionRunAuditService audit = new DataRetentionRunAuditService(jdbc, new ObjectMapper());

        ArticleRetentionDryRunService archiveService = new ArticleRetentionDryRunService(
                jdbc, mock(CurrentUserService.class), audit, prefixedStorage, properties);
        ArticleArchiveDryRunRequest archiveRequest = new ArticleArchiveDryRunRequest();
        archiveRequest.setProjectId(PROJECT_ID);
        archiveRequest.setMinPublishedAgeDays(90);
        archiveRequest.setLimit(10);
        archiveRequest.setReason("required MySQL COS article archive acceptance");

        ArticleArchiveDryRunResponse archive = archiveService.runScheduled(archiveRequest, false);

        assertThat(archive.getCandidateCount()).isEqualTo(1);
        assertThat(archive.getEligibleCount()).isEqualTo(1);
        assertThat(archive.getArchivedCount()).isEqualTo(1);
        assertThat(archive.getFailedCount()).isZero();
        String objectKey = archive.getItems().get(0).getPlannedObjectKey();
        String physicalObjectKey = runPrefix + objectKey;
        assertThat(objectKey).isEqualTo(
                "archive/article/" + PROJECT_ID + "/" + ARTICLE_ID + "/v1.md");
        assertThat(prefixedStorage.readBytes(objectKey)).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(sha256(prefixedStorage.readBytes(objectKey)))
                .isEqualTo(jdbc.queryForObject(
                        "SELECT content_checksum FROM article_draft_version WHERE id = ?",
                        String.class, VERSION_ID));
        ObjectStorageService.ObjectStat stat = cosStorage.stat(physicalObjectKey);
        assertThat(stat.size()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8).length);
        assertThat(stat.etag()).isNotBlank();

        jdbc.update("""
                UPDATE article_draft_version
                   SET content_archived_at = UTC_TIMESTAMP(3) - INTERVAL 25 HOUR
                 WHERE id = ?
                """, VERSION_ID);

        ArticleBodyPurgeService purgeService = new ArticleBodyPurgeService(
                jdbc, mock(CurrentUserService.class), prefixedStorage, audit, properties);
        ArticleBodyPurgeRequest purgeRequest = new ArticleBodyPurgeRequest();
        purgeRequest.setProjectId(PROJECT_ID);
        purgeRequest.setRetentionDays(90);
        purgeRequest.setArchiveGraceHours(24);
        purgeRequest.setLimit(10);
        purgeRequest.setReason("required MySQL COS article purge acceptance");

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
        assertThat(prefixedStorage.readBytes(objectKey)).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_run
                 WHERE status = 'succeeded'
                   AND domain IN ('article_body_archive', 'article_body_purge')
                   AND candidate_count = 1
                   AND affected_count = 1
                """, Integer.class)).isEqualTo(2);

        cosStorage.deletePrefix(runPrefix);
        assertThat(cosStorage.listObjects(runPrefix, 1)).isEmpty();
        assertThatThrownBy(() -> cosStorage.readBytes(physicalObjectKey))
                .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

    private boolean cosConfigured() {
        return hasText(System.getenv(COS_REGION))
                && hasText(System.getenv(COS_BUCKET))
                && hasText(System.getenv(COS_SECRET_ID))
                && hasText(System.getenv(COS_SECRET_KEY))
                && hasText(System.getenv(COS_PREFIX));
    }

    static String safeRunPrefix(String configuredRoot) {
        String normalized = configuredRoot.trim().replace('\\', '/');
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        if (!normalized.startsWith(REQUIRED_PREFIX_ROOT)
                || normalized.startsWith("/")
                || normalized.contains("..")
                || normalized.length() > 256) {
            throw new IllegalStateException(
                    COS_PREFIX + " must be a relative prefix under " + REQUIRED_PREFIX_ROOT);
        }
        String generated = normalized + "run-" + UUID.randomUUID().toString().replace("-", "") + "/";
        requireSafeGeneratedPrefix(generated);
        return generated;
    }

    static void requireSafeGeneratedPrefix(String prefix) {
        if (prefix == null
                || !prefix.startsWith(REQUIRED_PREFIX_ROOT)
                || !prefix.matches("^geo-retention-it/(?:[A-Za-z0-9._-]+/)*run-[a-f0-9]{32}/$")) {
            throw new IllegalStateException("Refusing to clean an unsafe COS integration prefix");
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (!hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private String environmentOrNull(String name) {
        String value = System.getenv(name);
        return hasText(value) ? value.trim() : null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void shutdownCosClient() {
        if (cosStorage == null) {
            return;
        }
        COSClient client = (COSClient) ReflectionTestUtils.getField(cosStorage, "cosClient");
        if (client != null) {
            client.shutdown();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class PrefixingObjectStorageService implements ObjectStorageService {
        private final ObjectStorageService delegate;
        private final String prefix;

        private PrefixingObjectStorageService(ObjectStorageService delegate, String prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public void putBytes(String objectKey, byte[] bytes, String contentType) {
            delegate.putBytes(physical(objectKey), bytes, contentType);
        }

        @Override
        public byte[] readBytes(String objectKey) {
            return delegate.readBytes(physical(objectKey));
        }

        @Override
        public InputStream openStream(String objectKey) {
            return delegate.openStream(physical(objectKey));
        }

        @Override
        public ObjectStat stat(String objectKey) {
            ObjectStat stat = delegate.stat(physical(objectKey));
            return new ObjectStat(objectKey, stat.size(), stat.etag());
        }

        @Override
        public List<ObjectItem> listObjects(String objectPrefix, int limit) {
            return delegate.listObjects(physical(objectPrefix), limit).stream()
                    .map(item -> new ObjectItem(
                            item.objectKey().substring(prefix.length()), item.size(), item.lastModified()))
                    .toList();
        }

        @Override
        public String presignedGetUrl(String objectKey, int ttlSeconds) {
            return delegate.presignedGetUrl(physical(objectKey), ttlSeconds);
        }

        @Override
        public void delete(String objectKey) {
            delegate.delete(physical(objectKey));
        }

        @Override
        public void deletePrefix(String objectPrefix) {
            delegate.deletePrefix(physical(objectPrefix));
        }

        private String physical(String objectKey) {
            String value = objectKey == null ? "" : objectKey;
            if (value.startsWith("/") || value.contains("..") || value.contains("\\")) {
                throw new IllegalArgumentException("Unsafe logical object key");
            }
            return prefix + value;
        }
    }
}
