package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class SelfMediaClaimConcurrencyMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("geo_claim_it")
            .withUsername("geo")
            .withPassword("geo");

    @BeforeEach
    void resetSchema() throws Exception {
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
                      brand_id BIGINT NOT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE local_agent_session (
                      id BIGINT PRIMARY KEY,
                      brand_id BIGINT NOT NULL,
                      operator_id BIGINT NOT NULL,
                      status VARCHAR(32) NOT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE local_agent_runtime_status (
                      id BIGINT PRIMARY KEY,
                      machine_id VARCHAR(128) NOT NULL,
                      active_profile VARCHAR(128) NOT NULL,
                      session_id BIGINT NOT NULL,
                      operator_id BIGINT NOT NULL,
                      running_task_count INT NOT NULL,
                      capacity INT NOT NULL,
                      last_seen_at DATETIME(6),
                      updated_at DATETIME(6),
                      INDEX idx_runtime_session (session_id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE browser_environment_agent_binding (
                      browser_environment_id BIGINT NOT NULL,
                      machine_id VARCHAR(128) NOT NULL,
                      active_profile VARCHAR(128) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      PRIMARY KEY (browser_environment_id, machine_id, active_profile)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE self_media_publish_schedule (
                      id BIGINT PRIMARY KEY,
                      brand_id BIGINT NOT NULL,
                      browser_environment_id BIGINT NOT NULL,
                      status VARCHAR(64) NOT NULL,
                      locked_until DATETIME(6),
                      runtime_worker_id VARCHAR(64),
                      attempt_count INT NOT NULL DEFAULT 0,
                      INDEX idx_schedule_lock (status, locked_until)
                    ) ENGINE=InnoDB
                    """);
        }
    }

    @Test
    void capacityOneAllowsOnlyOneConcurrentClaim() throws Exception {
        insertEnvironment(10L, 8L, "machine-a", "profile-a", 50L, 99L, 1);
        insertSchedule(101L, 8L, 10L);
        insertSchedule(102L, 8L, 10L);

        List<Boolean> results = runConcurrently(
                () -> claim(50L, 99L, 101L),
                () -> claim(50L, 99L, 102L));

        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, countSchedules("filling"));
        assertEquals(1, countSchedules("pending"));
    }

    @Test
    void differentAdspowerInstancesCanClaimInParallelWithinOwnCapacity() throws Exception {
        insertEnvironment(10L, 8L, "machine-a", "profile-a", 50L, 99L, 1);
        insertEnvironment(11L, 9L, "machine-a", "profile-b", 51L, 99L, 1);
        insertSchedule(201L, 8L, 10L);
        insertSchedule(202L, 9L, 11L);

        List<Boolean> results = runConcurrently(
                () -> claim(50L, 99L, 201L),
                () -> claim(51L, 99L, 202L));

        assertTrue(results.stream().allMatch(Boolean::booleanValue));
        assertEquals(2, countSchedules("filling"));
    }

    @Test
    void staleClaimAttemptAndCrossBrandEnvironmentCannotMutateCurrentTask() throws Exception {
        insertEnvironment(10L, 8L, "machine-a", "profile-a", 50L, 99L, 1);
        insertSchedule(301L, 8L, 10L);
        assertTrue(claim(50L, 99L, 301L));

        assertFalse(validateClaimForUpdate(301L, 99L, 50L, 0));
        assertTrue(validateClaimForUpdate(301L, 99L, 50L, 1));

        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE local_agent_session SET brand_id = 9 WHERE id = 50")) {
            update.executeUpdate();
        }
        assertFalse(validateClaimForUpdate(301L, 99L, 50L, 1));
    }

    private boolean claim(long sessionId, long operatorId, long scheduleId) throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                int capacity;
                int helperRunning;
                try (PreparedStatement lockRuntime = connection.prepareStatement("""
                        SELECT capacity, running_task_count
                        FROM local_agent_runtime_status
                        WHERE session_id = ? AND operator_id = ?
                        ORDER BY last_seen_at DESC, updated_at DESC
                        LIMIT 1 FOR UPDATE
                        """)) {
                    lockRuntime.setLong(1, sessionId);
                    lockRuntime.setLong(2, operatorId);
                    try (ResultSet result = lockRuntime.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        capacity = Math.max(1, result.getInt("capacity"));
                        helperRunning = Math.max(0, result.getInt("running_task_count"));
                    }
                }
                long databaseRunning = countLockedForSession(connection, sessionId);
                if (Math.max(databaseRunning, helperRunning) >= capacity) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement claim = connection.prepareStatement("""
                        UPDATE self_media_publish_schedule
                        SET status = 'filling', locked_until = ?, runtime_worker_id = ?,
                            attempt_count = attempt_count + 1
                        WHERE id = ? AND status = 'pending'
                        """)) {
                    claim.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(3)));
                    claim.setString(2, String.valueOf(operatorId));
                    claim.setLong(3, scheduleId);
                    boolean claimed = claim.executeUpdate() == 1;
                    connection.commit();
                    return claimed;
                }
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private long countLockedForSession(Connection connection, long sessionId) throws Exception {
        try (PreparedStatement count = connection.prepareStatement("""
                SELECT COUNT(DISTINCT schedule.id)
                FROM self_media_publish_schedule schedule
                JOIN browser_environment environment
                  ON environment.id = schedule.browser_environment_id
                 AND environment.brand_id = schedule.brand_id
                JOIN browser_environment_agent_binding binding
                  ON binding.browser_environment_id = environment.id
                 AND binding.status = 'active'
                JOIN local_agent_runtime_status runtime
                  ON runtime.machine_id = binding.machine_id
                 AND runtime.active_profile = binding.active_profile
                 AND runtime.session_id = ?
                JOIN local_agent_session session
                  ON session.id = runtime.session_id
                 AND session.brand_id = schedule.brand_id
                 AND session.operator_id = runtime.operator_id
                 AND session.status = 'active'
                WHERE schedule.status IN ('filling', 'filled_verified', 'scheduling', 'checking_publish_result')
                  AND schedule.locked_until IS NOT NULL
                  AND schedule.locked_until > CURRENT_TIMESTAMP(6)
                """)) {
            count.setLong(1, sessionId);
            try (ResultSet result = count.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private boolean validateClaimForUpdate(long scheduleId,
                                           long operatorId,
                                           long sessionId,
                                           int claimAttempt) throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            connection.setAutoCommit(false);
            try (PreparedStatement rowQuery = connection.prepareStatement("""
                    SELECT brand_id, browser_environment_id, runtime_worker_id, attempt_count
                    FROM self_media_publish_schedule
                    WHERE id = ? FOR UPDATE
                    """)) {
                rowQuery.setLong(1, scheduleId);
                try (ResultSet row = rowQuery.executeQuery()) {
                    if (!row.next()
                            || !String.valueOf(operatorId).equals(row.getString("runtime_worker_id"))
                            || claimAttempt != row.getInt("attempt_count")) {
                        connection.rollback();
                        return false;
                    }
                    boolean owned = ownsEnvironment(
                            connection,
                            row.getLong("browser_environment_id"),
                            row.getLong("brand_id"),
                            operatorId,
                            sessionId);
                    connection.rollback();
                    return owned;
                }
            }
        }
    }

    private boolean ownsEnvironment(Connection connection,
                                    long environmentId,
                                    long brandId,
                                    long operatorId,
                                    long sessionId) throws Exception {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT COUNT(1)
                FROM browser_environment_agent_binding binding
                JOIN browser_environment environment ON environment.id = binding.browser_environment_id
                JOIN local_agent_runtime_status runtime
                  ON runtime.machine_id = binding.machine_id
                 AND runtime.active_profile = binding.active_profile
                 AND runtime.session_id = ?
                JOIN local_agent_session session
                  ON session.id = runtime.session_id
                 AND session.brand_id = environment.brand_id
                 AND session.operator_id = ?
                 AND session.status = 'active'
                WHERE binding.browser_environment_id = ?
                  AND environment.brand_id = ?
                  AND binding.status = 'active'
                """)) {
            query.setLong(1, sessionId);
            query.setLong(2, operatorId);
            query.setLong(3, environmentId);
            query.setLong(4, brandId);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                return result.getLong(1) > 0;
            }
        }
    }

    private void insertEnvironment(long environmentId,
                                   long brandId,
                                   String machineId,
                                   String profile,
                                   long sessionId,
                                   long operatorId,
                                   int capacity) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO browser_environment VALUES (" + environmentId + ", " + brandId + ")");
            statement.executeUpdate("INSERT INTO local_agent_session VALUES (" + sessionId + ", " + brandId
                    + ", " + operatorId + ", 'active')");
            statement.executeUpdate("INSERT INTO local_agent_runtime_status VALUES (" + sessionId + ", '"
                    + machineId + "', '" + profile + "', " + sessionId + ", " + operatorId
                    + ", 0, " + capacity + ", NOW(6), NOW(6))");
            statement.executeUpdate("INSERT INTO browser_environment_agent_binding VALUES (" + environmentId
                    + ", '" + machineId + "', '" + profile + "', 'active')");
        }
    }

    private void insertSchedule(long scheduleId, long brandId, long environmentId) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO self_media_publish_schedule "
                             + "(id, brand_id, browser_environment_id, status, attempt_count) "
                             + "VALUES (?, ?, ?, 'pending', 0)")) {
            insert.setLong(1, scheduleId);
            insert.setLong(2, brandId);
            insert.setLong(3, environmentId);
            insert.executeUpdate();
        }
    }

    private int countSchedules(String status) throws Exception {
        try (Connection connection = MYSQL.createConnection("");
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(1) FROM self_media_publish_schedule WHERE status = ?")) {
            query.setString(1, status);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    @SafeVarargs
    private final List<Boolean> runConcurrently(CheckedBooleanSupplier... suppliers) throws Exception {
        CountDownLatch ready = new CountDownLatch(suppliers.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(suppliers.length);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (CheckedBooleanSupplier supplier : suppliers) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return supplier.get();
                }));
            }
            ready.await();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean get() throws Exception;
    }
}
