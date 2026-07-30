package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticlePublishRecordCompensationServiceTest {

    @Test
    void backfillPublishedTasks_normalizesOwnedSourceChannelsAndSkipsBrandOfficialSite() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks
                    (id, article_id, project_id, target_kind, integration_method, published_url, platform_article_id,
                     platform_publish_id, status, review_status, published_at, finished_at, created_at, self_media_account_id)
                VALUES
                    (1, 101, 201, 'brand_geo_site', 'brand_geo_site', 'https://agent.example/a/1', 'a1', NULL,
                     'submitted', NULL, NULL, TIMESTAMP '2026-06-01 10:00:00', TIMESTAMP '2026-06-01 09:00:00', NULL),
                    (2, 102, 201, 'industry_site', 'industry_news_site', 'https://industry.example/a/2', 'a2', NULL,
                     'submitted', NULL, NULL, TIMESTAMP '2026-06-02 10:00:00', TIMESTAMP '2026-06-02 09:00:00', NULL),
                    (3, 103, 201, 'forum_site', 'discuz', 'https://forum.example/t/3', 'a3', NULL,
                     'published', NULL, TIMESTAMP '2026-06-03 10:00:00', TIMESTAMP '2026-06-03 10:00:00', TIMESTAMP '2026-06-03 09:00:00', NULL),
                    (4, 104, 201, 'brand_official_site', 'official_cms', 'https://www.example/a/4', 'a4', NULL,
                     'submitted', NULL, NULL, TIMESTAMP '2026-06-04 10:00:00', TIMESTAMP '2026-06-04 09:00:00', NULL)
                """);
        ArticlePublishRecordCompensationService service =
                new ArticlePublishRecordCompensationService(jdbcTemplate, currentUserService());

        ArticlePublishRecordCompensationService.CompensationResult result = service.backfillPublishedTasks(100, false);

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM article_publish_record", Long.class)).isEqualTo(3L);
        assertThat(row(jdbcTemplate, 1L))
                .containsEntry("target_channel", "official_site")
                .containsEntry("publish_status", "distributed");
        assertThat(row(jdbcTemplate, 2L))
                .containsEntry("target_channel", "industry_site")
                .containsEntry("publish_status", "distributed");
        assertThat(row(jdbcTemplate, 3L))
                .containsEntry("target_channel", "forum_site")
                .containsEntry("publish_status", "distributed");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_publish_record
                 WHERE source_type = 'distribution_task'
                   AND source_id = 4
                """, Long.class)).isZero();
    }

    @Test
    void backfillPublishedTasks_repairsExistingWechatScheduleRecords() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, project_id, source_brand_id, title, cover_image_url)
                VALUES (2197, 7001, 5, '阜阳找价格实在服务靠谱的电动车？', 'https://cdn.example/cover-209.jpg')
                """);
        jdbcTemplate.update("""
                INSERT INTO self_media_account (id, brand_id, platform)
                VALUES (33, 5, 'wechat_mp')
                """);
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks
                    (id, article_id, project_id, target_kind, integration_method, published_url, platform_article_id,
                     platform_publish_id, status, review_status, published_at, finished_at, created_at, self_media_account_id,
                     response_payload)
                VALUES
                    (1638, 2197, NULL, 'self_media', 'wechat_mp', 'http://mp.weixin.qq.com/s/old',
                     'ADmwqirvKYn', '2247483717', 'published', NULL,
                     TIMESTAMP '2026-07-03 17:55:07', TIMESTAMP '2026-07-03 17:55:07',
                     TIMESTAMP '2026-07-03 17:50:00', 33, '{"ok":true}')
                """);
        jdbcTemplate.update("""
                INSERT INTO self_media_publish_schedule
                    (id, distribution_task_id, article_id, self_media_account_id, brand_id, platform,
                     platform_published_url, platform_publish_id, status, published_confirmed_at, updated_at, created_at)
                VALUES
                    (209, 1638, 2197, 33, 5, 'wechat_mp',
                     'http://mp.weixin.qq.com/s?__biz=test-209', '2247483717', 'published_confirmed',
                     TIMESTAMP '2026-07-03 17:55:07', TIMESTAMP '2026-07-03 17:55:05',
                     TIMESTAMP '2026-07-03 17:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (article_id, distribution_task_id, project_id, self_media_account_id, brand_id, source_type, source_id,
                     target_kind, target_channel, published_url, publish_status)
                VALUES
                    (2197, 1638, NULL, NULL, NULL, 'self_media_publish_schedule', 209,
                     'self_media', 'wechat_mp', 'http://mp.weixin.qq.com/s?__biz=test-209', 'published_confirmed')
                """);
        ArticlePublishRecordCompensationService service =
                new ArticlePublishRecordCompensationService(jdbcTemplate, currentUserService());

        ArticlePublishRecordCompensationService.CompensationResult result = service.backfillPublishedTasks(100, false);

        assertThat(result.inserted()).isPositive();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT project_id,
                       self_media_account_id,
                       brand_id,
                       title,
                       cover_url,
                       platform_article_id,
                       platform_publish_id
                  FROM article_publish_record
                 WHERE source_type = 'self_media_publish_schedule'
                   AND source_id = 209
                """))
                .containsEntry("project_id", 7001L)
                .containsEntry("self_media_account_id", 33L)
                .containsEntry("brand_id", 5L)
                .containsEntry("title", "阜阳找价格实在服务靠谱的电动车？")
                .containsEntry("cover_url", "https://cdn.example/cover-209.jpg")
                .containsEntry("platform_article_id", "ADmwqirvKYn")
                .containsEntry("platform_publish_id", "2247483717");
    }

    @Test
    void backfillPublishedTasksDoesNotDowngradeExplicitForumEvidence() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO distribution_tasks
                    (id, article_id, project_id, target_kind, integration_method, published_url,
                     status, finished_at, created_at)
                VALUES
                    (7, 107, 201, 'forum_site', 'discuz_http',
                     'https://bbs.ahv.cc/thread-18861-1-1.html',
                     'submitted', TIMESTAMP '2026-07-30 10:00:00',
                     TIMESTAMP '2026-07-30 09:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (article_id, distribution_task_id, project_id, source_type, source_id,
                     target_kind, target_channel, published_url, url_quality, publish_status)
                VALUES
                    (107, 7, 201, 'distribution_task', 7,
                     'forum_site', 'forum_site',
                     'https://bbs.ahv.cc/thread-18861-1-1.html',
                     'pending_review_url', 'distributed')
                """);
        ArticlePublishRecordCompensationService service =
                new ArticlePublishRecordCompensationService(jdbcTemplate, currentUserService());

        service.backfillPublishedTasks(100, false);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT url_quality
                  FROM article_publish_record
                 WHERE source_type = 'distribution_task'
                   AND source_id = 7
                """, String.class)).isEqualTo("pending_review_url");
    }

    private static Map<String, Object> row(JdbcTemplate jdbcTemplate, Long sourceId) {
        return jdbcTemplate.queryForMap("""
                SELECT target_channel, publish_status
                  FROM article_publish_record
                 WHERE source_type = 'distribution_task'
                   AND source_id = ?
                """, sourceId);
    }

    private static CurrentUserService currentUserService() {
        CurrentUserService service = mock(CurrentUserService.class);
        when(service.requireCurrentUser()).thenReturn(new SysUser());
        return service;
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return new JdbcTemplate(dataSource);
    }

    private static void createTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE distribution_tasks (
                    id BIGINT,
                    article_id BIGINT,
                    project_id BIGINT,
                    target_kind VARCHAR(64),
                    integration_method VARCHAR(64),
                    published_url VARCHAR(1000),
                    platform_article_id VARCHAR(128),
                    platform_publish_id VARCHAR(128),
                    status VARCHAR(32),
                    review_status VARCHAR(32),
                    published_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    created_at TIMESTAMP,
                    self_media_account_id BIGINT,
                    response_payload JSON
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE self_media_account (
                    id BIGINT,
                    brand_id BIGINT,
                    platform VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    source_brand_id BIGINT,
                    title VARCHAR(255),
                    cover_image_url VARCHAR(1000)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE self_media_publish_schedule (
                    id BIGINT,
                    distribution_task_id BIGINT,
                    article_id BIGINT,
                    self_media_account_id BIGINT,
                    brand_id BIGINT,
                    platform VARCHAR(64),
                    platform_published_url VARCHAR(1000),
                    platform_publish_id VARCHAR(128),
                    status VARCHAR(32),
                    published_confirmed_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_publish_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    article_id BIGINT,
                    distribution_task_id BIGINT,
                    project_id BIGINT,
                    self_media_account_id BIGINT,
                    brand_id BIGINT,
                    source_type VARCHAR(32),
                    source_id BIGINT,
                    target_kind VARCHAR(64),
                    target_channel VARCHAR(64),
                    published_url VARCHAR(1000),
                    url_quality VARCHAR(32),
                    url_source VARCHAR(64),
                    platform_article_id VARCHAR(128),
                    platform_publish_id VARCHAR(128),
                    publish_status VARCHAR(32),
                    title VARCHAR(255),
                    cover_url VARCHAR(1000),
                    raw_response JSON,
                    published_at TIMESTAMP,
                    verified_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_article_publish_source
                    ON article_publish_record (source_type, source_id)
                """);
    }
}
