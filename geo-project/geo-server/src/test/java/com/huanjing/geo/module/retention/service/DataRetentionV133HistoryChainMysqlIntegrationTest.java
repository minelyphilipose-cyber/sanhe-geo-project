package com.huanjing.geo.module.retention.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class DataRetentionV133HistoryChainMysqlIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String REQUIRED_GATE_PROPERTY = "retention.v133-it.required";
    private static final String UNAVAILABLE_REASON =
            "Docker is unavailable and RETENTION_MYSQL_IT_SERVER_URL is not configured";
    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration")
            .toAbsolutePath();
    private static final Path CANDIDATE_DIRECTORY = Path.of(
            "src/test/resources/retention/v133-candidate").toAbsolutePath();

    private MySQLContainer<?> container;
    private DataSource dataSource;
    private boolean databaseReady;
    private String externalServerUrl;
    private String externalDatabaseName;
    private String externalUsername;
    private String externalPassword;

    @BeforeAll
    void configureMysql8() throws Exception {
        databaseReady = configureDatabase();
    }

    @AfterAll
    void releaseMysql8() throws Exception {
        if (container != null) {
            container.stop();
        }
        if (externalDatabaseName != null) {
            try (Connection connection = DriverManager.getConnection(
                    externalServerUrl, externalUsername, externalPassword);
                 Statement statement = connection.createStatement()) {
                if (!externalDatabaseName.startsWith("geo_retention_v133_it_")) {
                    throw new IllegalStateException("Refusing to drop unexpected database");
                }
                statement.execute("DROP DATABASE IF EXISTS `" + externalDatabaseName + "`");
            }
        }
    }

    @Test
    void candidateRepairsFreshChainThroughV133AndSecondMigrateIsNoOp() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        clean();

        Flyway flyway = candidateFlyway();
        assertNoUnexpectedStates(flyway.info().all());
        ValidateResult emptySchemaValidation = flyway.validateWithResult();
        assertFalse(emptySchemaValidation.validationSuccessful);
        assertEquals(135, emptySchemaValidation.invalidMigrations.size());
        assertTrue(emptySchemaValidation.invalidMigrations.stream().allMatch(
                invalid -> invalid.errorDetails.errorCode
                        == ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED));
        MigrateResult first = flyway.migrate();

        assertEquals(135, first.migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertMigrationState(flyway, "132.1", MigrationState.SUCCESS);
        assertMigrationState(flyway, "133", MigrationState.SUCCESS);
        assertExactTargetIndex();
        assertEquals(0, candidateFlyway().migrate().migrationsExecuted);
    }

    @Test
    void alreadyPastV133RejectsCandidateAsIgnoredWithoutValidationRelaxation() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        clean();

        assertEquals(133, legacyFlyway("132").migrate().migrationsExecuted);
        executeCandidateOutsideFlyway();
        assertEquals(1, legacyFlyway("133").migrate().migrationsExecuted);
        List<HistoryRow> successfulHistoryBefore = successfulHistory();

        Flyway candidate = candidateFlyway();
        assertMigrationState(candidate, "132.1", MigrationState.IGNORED);
        ValidateResult validation = candidate.validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertTrue(validation.invalidMigrations.stream().anyMatch(
                invalid -> invalid.version != null && invalid.version.equals("132.1")));
        assertThrows(FlywayException.class, candidate::validate);
        assertEquals(successfulHistoryBefore, successfulHistory());
        assertExactTargetIndex();
    }

    @Test
    void failedV133CanBeRepairedWithoutRewritingSuccessfulHistory() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        clean();

        Flyway legacy = legacyFlyway("133");
        assertThrows(FlywayException.class, legacy::migrate);
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM flyway_schema_history
                 WHERE version = '133' AND success = 0
                """));
        List<HistoryRow> successfulHistoryBefore = successfulHistory();

        Flyway candidate = candidateFlyway();
        candidate.repair();
        assertEquals(successfulHistoryBefore, successfulHistory());
        assertEquals(0, scalarInt("""
                SELECT COUNT(*) FROM flyway_schema_history
                 WHERE version = '133' AND success = 0
                """));

        assertEquals(2, candidate.migrate().migrationsExecuted);
        assertTrue(candidate.validateWithResult().validationSuccessful);
        assertEquals(successfulHistoryBefore, successfulHistory().subList(
                0, successfulHistoryBefore.size()));
        assertMigrationState(candidate, "132.1", MigrationState.SUCCESS);
        assertMigrationState(candidate, "133", MigrationState.SUCCESS);
        assertExactTargetIndex();
        assertEquals(0, candidateFlyway().migrate().migrationsExecuted);
    }

    private void assertNoUnexpectedStates(MigrationInfo[] migrations) {
        for (MigrationInfo migration : migrations) {
            assertFalse(migration.getState() == MigrationState.IGNORED
                            || migration.getState() == MigrationState.OUT_OF_ORDER,
                    () -> migration.getVersion() + " was " + migration.getState());
        }
    }

    private void assertMigrationState(Flyway flyway, String version, MigrationState state) {
        MigrationInfo migration = java.util.Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null)
                .filter(info -> version.equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();
        assertEquals(state, migration.getState());
    }

    private void assertExactTargetIndex() throws Exception {
        assertEquals("0:project_id,batch_date,target_channel,generation_slot_no", scalar("""
                SELECT CONCAT(
                           MIN(non_unique), ':',
                           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
                       )
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                   AND table_name = 'article_batch'
                   AND index_name = 'uk_article_batch_project_date_channel_slot'
                """));
        assertEquals(0, scalarInt("""
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                   AND table_name = 'article_batch'
                   AND index_name = 'uk_article_batch_project_date_no'
                """));
    }

    private void executeCandidateOutsideFlyway() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    CANDIDATE_DIRECTORY.resolve(
                            "V132_1__prepare_article_batch_channel_slot_unique.sql")));
        }
    }

    private void clean() {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
    }

    private Flyway legacyFlyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(filesystemLocation(MIGRATION_DIRECTORY))
                .target(MigrationVersion.fromVersion(target))
                .load();
    }

    private Flyway candidateFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(filesystemLocation(MIGRATION_DIRECTORY),
                        filesystemLocation(CANDIDATE_DIRECTORY))
                .target(MigrationVersion.fromVersion("133"))
                .load();
    }

    private String filesystemLocation(Path path) {
        return "filesystem:" + path.toString().replace('\\', '/');
    }

    private List<HistoryRow> successfulHistory() throws Exception {
        List<HistoryRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT installed_rank, version, description, type, script, checksum,
                            installed_by, installed_on, execution_time, success
                       FROM flyway_schema_history
                      WHERE success = 1
                      ORDER BY installed_rank
                     """)) {
            while (resultSet.next()) {
                rows.add(new HistoryRow(
                        resultSet.getInt("installed_rank"),
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("type"),
                        resultSet.getString("script"),
                        (Integer) resultSet.getObject("checksum"),
                        resultSet.getString("installed_by"),
                        resultSet.getTimestamp("installed_on").toInstant().toString(),
                        resultSet.getInt("execution_time"),
                        resultSet.getBoolean("success")));
            }
        }
        return rows;
    }

    private Object scalar(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getObject(1);
        }
    }

    private int scalarInt(String sql) throws Exception {
        return ((Number) Objects.requireNonNull(scalar(sql))).intValue();
    }

    private boolean configureDatabase() throws Exception {
        String serverUrl = System.getenv("RETENTION_MYSQL_IT_SERVER_URL");
        if (serverUrl != null && !serverUrl.isBlank()) {
            configureExternalDatabase(serverUrl);
            return true;
        }

        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            dockerAvailable = false;
        }
        if (!dockerAvailable) {
            if (Boolean.getBoolean(REQUIRED_GATE_PROPERTY)) {
                throw new IllegalStateException(
                        "Required retention-v133-it gate cannot run: " + UNAVAILABLE_REASON);
            }
            return false;
        }

        container = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("geo_retention_v133_it")
                .withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        container.start();
        dataSource = dataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return true;
    }

    private void configureExternalDatabase(String serverUrl) throws Exception {
        externalServerUrl = serverUrl;
        externalUsername = environmentOrDefault("RETENTION_MYSQL_IT_USER", "root");
        externalPassword = environmentOrDefault("RETENTION_MYSQL_IT_PASSWORD", "");
        externalDatabaseName = "geo_retention_v133_it_"
                + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                externalServerUrl, externalUsername, externalPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + externalDatabaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String databaseUrl = serverUrl.replaceFirst(
                "/\\?", "/" + externalDatabaseName + "?");
        if (databaseUrl.equals(serverUrl)) {
            databaseUrl = serverUrl.endsWith("/")
                    ? serverUrl + externalDatabaseName
                    : serverUrl + "/" + externalDatabaseName;
            databaseUrl += databaseUrl.contains("?") ? "&" : "?";
            databaseUrl += "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
        dataSource = dataSource(databaseUrl, externalUsername, externalPassword);
    }

    private DriverManagerDataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        return source;
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record HistoryRow(
            int installedRank,
            String version,
            String description,
            String type,
            String script,
            Integer checksum,
            String installedBy,
            String installedOn,
            int executionTime,
            boolean success) {
    }
}
