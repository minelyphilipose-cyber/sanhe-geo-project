package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices.ExternalMysql;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRetentionExecutePermissionMigrationMysqlIntegrationTest {
    private static final String REQUIRED = "retention.coordinator-it.required";
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration").toAbsolutePath();

    private MySQLContainer<?> mysql;
    private ExternalMysql externalMysql;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private boolean ready;

    @BeforeAll
    void startMysql() throws Exception {
        if (RetentionExternalTestServices.mysqlConfigured()) {
            externalMysql = RetentionExternalTestServices.createMysql("geo_retention_v364_it_");
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
                throw new IllegalStateException("Required V364 migration integration infrastructure is unavailable");
            }
            return;
        }
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("geo_retention_v364_it").withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        mysql.start();
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        ready = true;
    }

    @BeforeEach
    void resetSchema() {
        Assumptions.assumeTrue(ready, "Docker is unavailable");
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        try (var connection = dataSource.getConnection()) {
            executeHistoricalScript(connection, "V1__init_user.sql");
            executeHistoricalScript(connection, "V5__phase2_rbac_user.sql");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @AfterAll
    void stopMysql() throws Exception {
        if (mysql != null) mysql.stop();
        if (externalMysql != null) externalMysql.close();
    }

    @Test
    void v364CreatesExactPermissionMatrixAndNoOpMigrateIsStable() {
        migrateV364();
        assertMatrix();

        migrateV364();
        assertMatrix();
    }

    private void migrateV364() {
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + MIGRATIONS.toString().replace('\\', '/'))
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("363"))
                .target(MigrationVersion.fromVersion("364")).load().migrate();
    }

    private void executeHistoricalScript(java.sql.Connection connection, String migration) throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve(migration), StandardCharsets.UTF_8);
        if (!sql.isEmpty() && sql.charAt(0) == '\uFEFF') {
            sql = sql.substring(1);
        }
        ScriptUtils.executeSqlScript(connection,
                new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertMatrix() {
        assertEquals(5, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_permission
                 WHERE perm_key LIKE 'data.retention.execute.%' AND module='data_retention' AND status='active'
                """, Integer.class));
        assertEquals(10, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_role r ON r.id=rp.role_id
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE r.role_key IN ('super_admin','manager','delivery_manager')
                  AND p.perm_key LIKE 'data.retention.execute.%'
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_role r ON r.id=rp.role_id
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE r.role_key='delivery_manager'
                  AND p.perm_key LIKE 'data.retention.execute.%'
                  AND p.perm_key<>'data.retention.execute.canary'
                """, Integer.class));
    }
}
