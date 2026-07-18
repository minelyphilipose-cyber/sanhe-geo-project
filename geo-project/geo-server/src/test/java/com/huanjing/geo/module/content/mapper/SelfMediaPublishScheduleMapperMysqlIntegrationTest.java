package com.huanjing.geo.module.content.mapper;

import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class SelfMediaPublishScheduleMapperMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("geo_mapper_it")
            .withUsername("geo")
            .withPassword("geo");

    private SqlSessionFactory sqlSessionFactory;
    private LocalDateTime now;

    @BeforeEach
    void resetSchema() throws Exception {
        now = LocalDateTime.of(2026, 7, 18, 16, 0);
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS self_media_publish_schedule");
            statement.execute("DROP TABLE IF EXISTS browser_environment_agent_binding");
            statement.execute("DROP TABLE IF EXISTS local_agent_runtime_status");
            statement.execute("DROP TABLE IF EXISTS local_agent_session");
            statement.execute("DROP TABLE IF EXISTS browser_environment");
            statement.execute("""
                    CREATE TABLE browser_environment (
                      id BIGINT PRIMARY KEY,
                      brand_id BIGINT NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      deleted_at DATETIME(6) NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE local_agent_session (
                      id BIGINT PRIMARY KEY,
                      brand_id BIGINT NULL,
                      operator_id BIGINT NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      last_seen_at DATETIME(6) NULL,
                      expires_at DATETIME(6) NOT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE local_agent_runtime_status (
                      id BIGINT PRIMARY KEY,
                      machine_id VARCHAR(128) NOT NULL,
                      active_profile VARCHAR(32) NOT NULL,
                      session_id BIGINT NOT NULL,
                      operator_id BIGINT NOT NULL,
                      running_task_count INT NOT NULL,
                      capacity INT NOT NULL,
                      last_seen_at DATETIME(6) NULL,
                      updated_at DATETIME(6) NULL,
                      UNIQUE KEY uk_runtime_machine_profile (machine_id, active_profile)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE browser_environment_agent_binding (
                      browser_environment_id BIGINT PRIMARY KEY,
                      machine_id VARCHAR(128) NOT NULL,
                      active_profile VARCHAR(32) NOT NULL,
                      bound_session_id BIGINT NULL,
                      status VARCHAR(32) NOT NULL,
                      bound_by BIGINT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE self_media_publish_schedule (
                      id BIGINT PRIMARY KEY,
                      brand_id BIGINT NOT NULL,
                      browser_environment_id BIGINT NOT NULL,
                      status VARCHAR(64) NOT NULL,
                      queue_kind VARCHAR(64) NOT NULL,
                      queue_priority INT NOT NULL DEFAULT 100,
                      platform VARCHAR(32) NOT NULL,
                      next_attempt_at DATETIME(6) NULL,
                      planned_publish_at DATETIME(6) NULL,
                      platform_scheduled_at DATETIME(6) NULL,
                      locked_until DATETIME(6) NULL,
                      runtime_worker_id VARCHAR(64) NULL,
                      attempt_count INT NOT NULL DEFAULT 0,
                      updated_at DATETIME(6) NULL
                    ) ENGINE=InnoDB
                    """);
        }
        sqlSessionFactory = mapperSessionFactory();
    }

    @Test
    void accountWideSessionCanClaimAndRepairedSessionInheritsSameMachineProfile() throws Exception {
        insertSession(5L, null, 13L);
        insertRuntime(100L, "machine-a", "profile-a", 5L, 13L, 1);
        insertEnvironment(10L, 15L, "active", null);
        insertBinding(10L, "machine-a", "profile-a", 5L, 13L);
        insertSchedule(1066L, 15L, 10L, "pending", null, null);

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = sqlSession.getMapper(SelfMediaPublishScheduleMapper.class);
            assertEquals(List.of(1066L), dueIds(mapper, 5L, 13L, List.of(15L)));
            assertTrue(mapper.isBrowserEnvironmentOwnedByLocalAgent(10L, 5L, 15L, 13L, now));
        }

        insertSession(6L, null, 13L);
        updateRuntimeSession(100L, 6L);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = sqlSession.getMapper(SelfMediaPublishScheduleMapper.class);
            assertTrue(mapper.isBrowserEnvironmentOwnedByLocalAgent(10L, 6L, 15L, 13L, now));
        }

        insertSession(7L, null, 13L);
        insertRuntime(101L, "machine-b", "profile-b", 7L, 13L, 1);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = sqlSession.getMapper(SelfMediaPublishScheduleMapper.class);
            assertFalse(mapper.isBrowserEnvironmentOwnedByLocalAgent(10L, 7L, 15L, 13L, now));
        }
    }

    @Test
    void brandScopedSessionOperatorBindingAndEnvironmentStateAreEnforced() throws Exception {
        insertSession(5L, 99L, 13L);
        insertRuntime(100L, "machine-a", "profile-a", 5L, 13L, 1);
        insertEnvironment(10L, 15L, "active", null);
        insertBinding(10L, "machine-a", "profile-a", 5L, 13L);
        insertSchedule(1066L, 15L, 10L, "pending", null, null);

        assertEquals(List.of(), queryDueIds(5L, 13L, List.of(15L)));
        execute("UPDATE local_agent_session SET brand_id = 15 WHERE id = 5");
        assertEquals(List.of(1066L), queryDueIds(5L, 13L, List.of(15L)));
        execute("UPDATE browser_environment_agent_binding SET bound_by = 88 WHERE browser_environment_id = 10");
        assertEquals(List.of(), queryDueIds(5L, 13L, List.of(15L)));
        execute("UPDATE browser_environment_agent_binding SET bound_by = 13 WHERE browser_environment_id = 10");
        execute("UPDATE browser_environment SET status = 'disabled' WHERE id = 10");
        assertEquals(List.of(), queryDueIds(5L, 13L, List.of(15L)));
        execute("UPDATE browser_environment SET status = 'active', deleted_at = NOW(6) WHERE id = 10");
        assertEquals(List.of(), queryDueIds(5L, 13L, List.of(15L)));
    }

    @Test
    void capacityAndRunningLoadAreDeduplicatedByRuntimeAcrossBrandsAndEnvironments() throws Exception {
        insertSession(5L, null, 13L);
        insertRuntime(100L, "machine-a", "profile-a", 5L, 13L, 1);
        insertEnvironment(10L, 15L, "active", null);
        insertEnvironment(11L, 15L, "active", null);
        insertEnvironment(12L, 16L, "active", null);
        insertBinding(10L, "machine-a", "profile-a", 5L, 13L);
        insertBinding(11L, "machine-a", "profile-a", 5L, 13L);
        insertBinding(12L, "machine-a", "profile-a", 5L, 13L);
        insertSchedule(1066L, 15L, 10L, "filling", now.plusMinutes(3), "13");
        insertSchedule(1067L, 16L, 12L, "filling", now.plusMinutes(3), "13");

        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = sqlSession.getMapper(SelfMediaPublishScheduleMapper.class);
            assertEquals(1L, mapper.sumOnlineLocalAgentCapacityByBrand(
                    15L, now, now.minusMinutes(2)));
            assertEquals(1L, mapper.countOnlineLocalAgentsServingBrand(
                    15L, now, now.minusMinutes(2)));
            assertEquals(2L, mapper.countLockedByLocalAgentSessionAndStatuses(
                    5L, 13L, List.of("filling"), now));
        }
    }

    @Test
    void repairedSessionCanRenewButDifferentMachineCannot() throws Exception {
        insertSession(5L, null, 13L);
        insertSession(6L, null, 13L);
        insertSession(7L, null, 13L);
        insertRuntime(100L, "machine-a", "profile-a", 6L, 13L, 1);
        insertRuntime(101L, "machine-b", "profile-b", 7L, 13L, 1);
        insertEnvironment(10L, 15L, "active", null);
        insertBinding(10L, "machine-a", "profile-a", 5L, 13L);
        insertSchedule(1066L, 15L, 10L, "filling", now.plusMinutes(1), "13");

        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            SelfMediaPublishScheduleMapper mapper = sqlSession.getMapper(SelfMediaPublishScheduleMapper.class);
            assertEquals(1, mapper.renewLocalAgentLock(
                    1066L, "13", 6L, 13L, List.of("filling"), now.plusMinutes(3), now));
            assertEquals(0, mapper.renewLocalAgentLock(
                    1066L, "13", 7L, 13L, List.of("filling"), now.plusMinutes(4), now));
        }
    }

    private List<Long> queryDueIds(long sessionId, long operatorId, List<Long> brandIds) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return dueIds(sqlSession.getMapper(SelfMediaPublishScheduleMapper.class),
                    sessionId, operatorId, brandIds);
        }
    }

    private List<Long> dueIds(SelfMediaPublishScheduleMapper mapper,
                              long sessionId,
                              long operatorId,
                              List<Long> brandIds) {
        return mapper.selectDueQueueCandidatesForLocalAgent(
                        "schedule_execution",
                        List.of("pending"),
                        now,
                        10,
                        sessionId,
                        operatorId,
                        brandIds,
                        "toutiao",
                        Set.of("toutiao"))
                .stream()
                .map(SelfMediaPublishSchedule::getId)
                .toList();
    }

    private SqlSessionFactory mapperSessionFactory() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Environment environment = new Environment("mysql", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/content/SelfMediaPublishScheduleMapper.xml";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing mapper resource " + resource);
            }
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void insertSession(long sessionId, Long brandId, long operatorId) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO local_agent_session
                       (id, brand_id, operator_id, status, last_seen_at, expires_at)
                     VALUES (?, ?, ?, 'active', ?, ?)
                     """)) {
            insert.setLong(1, sessionId);
            if (brandId == null) insert.setNull(2, java.sql.Types.BIGINT); else insert.setLong(2, brandId);
            insert.setLong(3, operatorId);
            insert.setObject(4, now);
            insert.setObject(5, now.plusDays(1));
            insert.executeUpdate();
        }
    }

    private void insertRuntime(long runtimeId,
                               String machine,
                               String profile,
                               long sessionId,
                               long operatorId,
                               int capacity) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO local_agent_runtime_status
                       (id, machine_id, active_profile, session_id, operator_id,
                        running_task_count, capacity, last_seen_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)
                     """)) {
            insert.setLong(1, runtimeId);
            insert.setString(2, machine);
            insert.setString(3, profile);
            insert.setLong(4, sessionId);
            insert.setLong(5, operatorId);
            insert.setInt(6, capacity);
            insert.setObject(7, now);
            insert.setObject(8, now);
            insert.executeUpdate();
        }
    }

    private void insertEnvironment(long id,
                                   long brandId,
                                   String status,
                                   LocalDateTime deletedAt) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO browser_environment (id, brand_id, status, deleted_at) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, id);
            insert.setLong(2, brandId);
            insert.setString(3, status);
            insert.setObject(4, deletedAt);
            insert.executeUpdate();
        }
    }

    private void insertBinding(long environmentId,
                               String machine,
                               String profile,
                               Long boundSessionId,
                               Long boundBy) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO browser_environment_agent_binding
                       (browser_environment_id, machine_id, active_profile, bound_session_id, status, bound_by)
                     VALUES (?, ?, ?, ?, 'active', ?)
                     """)) {
            insert.setLong(1, environmentId);
            insert.setString(2, machine);
            insert.setString(3, profile);
            if (boundSessionId == null) insert.setNull(4, java.sql.Types.BIGINT); else insert.setLong(4, boundSessionId);
            if (boundBy == null) insert.setNull(5, java.sql.Types.BIGINT); else insert.setLong(5, boundBy);
            insert.executeUpdate();
        }
    }

    private void insertSchedule(long id,
                                long brandId,
                                long environmentId,
                                String status,
                                LocalDateTime lockedUntil,
                                String runtimeWorkerId) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO self_media_publish_schedule
                       (id, brand_id, browser_environment_id, status, queue_kind, platform,
                        next_attempt_at, locked_until, runtime_worker_id, updated_at)
                     VALUES (?, ?, ?, ?, 'schedule_execution', 'toutiao', ?, ?, ?, ?)
                     """)) {
            insert.setLong(1, id);
            insert.setLong(2, brandId);
            insert.setLong(3, environmentId);
            insert.setString(4, status);
            insert.setObject(5, now.minusMinutes(1));
            insert.setObject(6, lockedUntil);
            insert.setString(7, runtimeWorkerId);
            insert.setObject(8, now);
            insert.executeUpdate();
        }
    }

    private void updateRuntimeSession(long runtimeId, long sessionId) throws Exception {
        execute("UPDATE local_agent_runtime_status SET session_id = " + sessionId + " WHERE id = " + runtimeId);
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
