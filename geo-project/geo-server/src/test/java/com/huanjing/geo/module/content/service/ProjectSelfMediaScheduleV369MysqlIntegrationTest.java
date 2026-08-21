package com.huanjing.geo.module.content.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectSelfMediaScheduleV369MysqlIntegrationTest {
    private MySQLContainer<?> mysql;
    private DataSource dataSource;
    private boolean ready;

    @BeforeAll
    void migrateOnRealMysql() throws Exception {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        mysql = new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName("geo_schedule_it")
                .withUsername("geo")
                .withPassword("geo");
        mysql.start();
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        createPrerequisites();
        String migrationPath = Path.of("src/main/resources/db/migration")
                .toAbsolutePath().toString().replace('\\', '/');
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationPath)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("368"))
                .target(MigrationVersion.fromVersion("369"))
                .load().migrate();
        ready = true;
    }

    @AfterAll
    void stopMysql() {
        if (mysql != null) mysql.stop();
    }

    @BeforeEach
    void resetData() throws Exception {
        if (!ready) return;
        execute("SET FOREIGN_KEY_CHECKS=0");
        execute("TRUNCATE TABLE company_channel_quota_ledger");
        execute("TRUNCATE TABLE project_self_media_schedule_item");
        execute("TRUNCATE TABLE batch_article_generation_batch");
        execute("TRUNCATE TABLE project_self_media_schedule_batch");
        execute("TRUNCATE TABLE project");
        execute("TRUNCATE TABLE brand");
        execute("SET FOREIGN_KEY_CHECKS=1");
    }

    @Test
    void migrationCreatesClaimAndIdempotencyProtection() throws Exception {
        requireMysql();
        assertEquals(2, scalar("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'project_self_media_schedule_item'
                  AND column_name IN ('generation_claim_token', 'generation_claimed_at')
                """));
        assertEquals(1, scalar("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_batch'
                  AND index_name = 'uk_batch_article_generation_idempotency' AND non_unique = 0
                """));

        execute("INSERT INTO brand(id) VALUES (8)");
        execute("INSERT INTO project(id) VALUES (7)");
        execute("INSERT INTO brand(id) VALUES (18)");
        execute("INSERT INTO project(id) VALUES (17)");
        execute("INSERT INTO project_self_media_schedule_batch(id,project_id,brand_id,company_id,target_month) " +
                "VALUES (21,17,18,19,'2026-09')");
        execute(itemInsert(101, "planned"));
        assertEquals(1, updateClaim(101, "worker-a"));
        assertEquals(0, updateClaim(101, "worker-b"));

        execute("INSERT INTO batch_article_generation_batch(id,project_id,topic,idempotency_key) " +
                "VALUES (201,7,'topic','same-key')");
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO batch_article_generation_batch(id,project_id,topic,idempotency_key) " +
                        "VALUES (202,7,'topic','same-key')"));
    }

    @Test
    void reconciliationQueryPagesOnlyTerminalTimedOutAndOrphanReservations() throws Exception {
        requireMysql();
        execute("INSERT INTO brand(id) VALUES (18)");
        execute("INSERT INTO project(id) VALUES (17)");
        execute("INSERT INTO project_self_media_schedule_batch(id,project_id,brand_id,company_id,target_month) " +
                "VALUES (21,17,18,19,'2026-09')");
        execute(itemInsert(301, "planned"));
        execute(itemInsert(302, "schedule_failed"));
        execute("UPDATE project_self_media_schedule_item SET updated_at='2026-08-01 00:00:00' WHERE id=302");
        execute("INSERT INTO company_channel_quota_ledger(id,biz_type,biz_id,status) VALUES " +
                "(1,'project_self_media_plan','301','reserved')," +
                "(2,'project_self_media_plan','302','reserved')," +
                "(3,'project_self_media_plan','999','reserved')");
        assertEquals(2, scalar("""
                SELECT COUNT(*) FROM company_channel_quota_ledger l
                LEFT JOIN project_self_media_schedule_item i ON i.id=CAST(l.biz_id AS UNSIGNED)
                LEFT JOIN project_self_media_schedule_batch b ON b.id=i.batch_id
                WHERE l.biz_type='project_self_media_plan' AND l.status='reserved'
                  AND (i.id IS NULL OR i.status IN ('cancelled','skipped','generation_failed')
                    OR b.status IN ('failed','skipped')
                    OR (i.status='schedule_failed' AND i.updated_at<'2026-08-18 00:00:00'))
                """));
    }

    private void requireMysql() {
        Assumptions.assumeTrue(ready, "Docker is unavailable; real MySQL integration test skipped");
    }

    private void createPrerequisites() throws Exception {
        execute("CREATE TABLE brand(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        execute("CREATE TABLE project(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        execute("CREATE TABLE project_self_media_schedule_batch(" +
                "id BIGINT UNSIGNED PRIMARY KEY, project_id BIGINT NOT NULL, brand_id BIGINT NOT NULL," +
                "company_id BIGINT NOT NULL, target_month CHAR(7) NOT NULL, status VARCHAR(32) DEFAULT 'planned') ENGINE=InnoDB");
        execute("CREATE TABLE batch_article_generation_batch(" +
                "id BIGINT UNSIGNED PRIMARY KEY, project_id BIGINT NOT NULL, topic VARCHAR(1000) NOT NULL," +
                "total_count INT DEFAULT 1, status VARCHAR(32) DEFAULT 'pending', error_message VARCHAR(1000)) ENGINE=InnoDB");
        execute("CREATE TABLE company_channel_quota_ledger(" +
                "id BIGINT UNSIGNED PRIMARY KEY, biz_type VARCHAR(64), biz_id VARCHAR(64), status VARCHAR(16)) ENGINE=InnoDB");
    }

    private String itemInsert(long id, String status) {
        return "INSERT INTO project_self_media_schedule_item(" +
                "id,batch_id,project_id,brand_id,company_id,self_media_account_id,platform,status," +
                "planned_execution_at,planned_publish_at,generation_due_at,generation_idempotency_key," +
                "schedule_idempotency_key,quota_reservation_status) VALUES (" + id +
                ",21,17,18,19,20,'zhihu','" + status +
                "','2026-09-10 09:00:00','2026-09-10 10:00:00','2026-09-10 05:00:00','generation-" + id +
                "','schedule-" + id + "','reserved')";
    }

    private int updateClaim(long itemId, String token) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            return statement.executeUpdate("UPDATE project_self_media_schedule_item SET status='generating'," +
                    "generation_claim_token='" + token + "',generation_claimed_at=NOW() WHERE id=" + itemId +
                    " AND status='planned' AND COALESCE(next_generation_attempt_at,generation_due_at)<=NOW()");
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalar(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
