package com.huanjing.geo.module.extension.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionSessionMapperTest {
    private SqlSessionFactory factory;
    private JdbcTemplate jdbc;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 4, 0);
    private final LocalDateTime boundary = LocalDateTime.of(2026, 11, 7, 0, 0);

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE extension_session (id BIGINT PRIMARY KEY, status VARCHAR(20), expires_at TIMESTAMP)");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ExtensionSessionMapper.class);
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void dailyBoundaryExpiredAndRevokedSessionsCannotRenew() {
        jdbc.update("INSERT INTO extension_session VALUES (1, 'active', ?), (2, 'active', ?), (3, 'revoked', ?)",
                boundary, now, now.plusDays(1));
        assertEquals(0, renew(1L));
        assertEquals(0, renew(2L));
        assertEquals(0, renew(3L));
    }

    @Test
    void concurrentRequestsUpdateExpiryOnlyOnce() throws Exception {
        jdbc.update("INSERT INTO extension_session VALUES (1, 'active', ?)", now.plusDays(6));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { start.await(); return renew(1L); });
            var second = executor.submit(() -> { start.await(); return renew(1L); });
            start.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(now.plusDays(30), jdbc.queryForObject(
                "SELECT expires_at FROM extension_session WHERE id = 1", LocalDateTime.class));
    }

    private int renew(Long id) {
        try (var session = factory.openSession(true)) {
            return session.getMapper(ExtensionSessionMapper.class).renewActiveExpiry(id, now, boundary, now.plusDays(30));
        }
    }
}
