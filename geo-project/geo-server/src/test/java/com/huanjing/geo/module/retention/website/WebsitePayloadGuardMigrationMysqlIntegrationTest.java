package com.huanjing.geo.module.retention.website;

import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices.ExternalMysql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebsitePayloadGuardMigrationMysqlIntegrationTest {
    private static final String REQUIRED = "retention.website-it.required";
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/"
            + "V366__guard_distribution_payload_purge_marker.sql").toAbsolutePath();

    private MySQLContainer<?> mysql;
    private ExternalMysql externalMysql;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private boolean ready;

    @BeforeAll
    void startMysql() throws Exception {
        if (RetentionExternalTestServices.mysqlConfigured()) {
            externalMysql = RetentionExternalTestServices.createMysql("geo_retention_wp6_it_");
            dataSource = externalMysql.dataSource();
            jdbc = new JdbcTemplate(dataSource);
            ready = true;
            return;
        }
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { docker = false; }
        if (!docker) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException("Required WP6 MySQL integration infrastructure is unavailable");
            }
            return;
        }
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("geo_retention_wp6_it").withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        mysql.start();
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        ready = true;
    }

    @BeforeEach
    void resetSchema() {
        Assumptions.assumeTrue(ready, "Docker is unavailable");
        jdbc.execute("DROP TABLE IF EXISTS distribution_tasks");
        jdbc.execute("""
                CREATE TABLE distribution_tasks (
                  id BIGINT PRIMARY KEY,
                  request_payload JSON NULL,
                  fill_payload JSON NULL,
                  response_payload JSON NULL,
                  payload_purged_at DATETIME(3) NULL
                ) ENGINE=InnoDB
                """);
    }

    @AfterAll
    void stopMysql() throws Exception {
        if (mysql != null) mysql.stop();
        if (externalMysql != null) externalMysql.close();
    }

    @Test
    void repairsLegacyMarkerAndRejectsPayloadReheatWithoutAtomicReset() throws Exception {
        jdbc.update("""
                INSERT INTO distribution_tasks (
                  id, request_payload, fill_payload, response_payload, payload_purged_at
                ) VALUES (1, JSON_OBJECT('late', TRUE), NULL, NULL, CURRENT_TIMESTAMP(3))
                """);

        runMigration();
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE id = 1 AND request_payload IS NOT NULL AND payload_purged_at IS NULL
                """, Integer.class));
        jdbc.update("""
                UPDATE distribution_tasks
                   SET request_payload = NULL, payload_purged_at = CURRENT_TIMESTAMP(3)
                 WHERE id = 1
                """);
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                UPDATE distribution_tasks SET response_payload = JSON_OBJECT('late', TRUE) WHERE id = 1
                """));
        jdbc.update("""
                UPDATE distribution_tasks
                   SET response_payload = JSON_OBJECT('late', TRUE), payload_purged_at = NULL
                 WHERE id = 1
                """);

        runMigration();
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE id = 1 AND response_payload IS NOT NULL AND payload_purged_at IS NULL
                """, Integer.class));
        String clause = jdbc.queryForObject("""
                SELECT check_clause FROM information_schema.check_constraints
                 WHERE constraint_schema = DATABASE()
                   AND constraint_name = 'chk_distribution_tasks_payload_purge_consistency'
                """, String.class);
        assertTrue(clause != null && clause.toLowerCase().contains("payload_purged_at"));
    }

    private void runMigration() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION));
        }
    }
}
