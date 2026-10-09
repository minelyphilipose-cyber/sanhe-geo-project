package com.huanjing.geo.module.content.mapper;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelfMediaPublishScheduleReplacementTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 9, 9, 24);
    private SqlSessionFactory factory;
    private UnpooledDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:replacement_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE self_media_publish_schedule (
                      id BIGINT PRIMARY KEY, brand_id BIGINT, platform VARCHAR(32),
                      request_idempotency_key VARCHAR(255), status VARCHAR(32),
                      planned_publish_at TIMESTAMP, next_attempt_at TIMESTAMP, locked_until TIMESTAMP,
                      platform_schedule_id VARCHAR(64), platform_publish_id VARCHAR(64),
                      platform_published_url VARCHAR(255), cancelled_at TIMESTAMP,
                      failure_code VARCHAR(64), failure_message VARCHAR(255), updated_at TIMESTAMP
                    )
                    """);
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SelfMediaPublishScheduleMapper.class);
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform-quick-dispatch-", "platform-quick-schedule-", "manual-schedule-"})
    void replacementSkipsManualTaskEvenWhenItIsMoreThanTenMinutesAway(String prefix) throws Exception {
        insert(495L, prefix + "first", now.plusMinutes(12));
        try (SqlSession session = factory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = session.getMapper(SelfMediaPublishScheduleMapper.class);
            assertNull(mapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(8L, "sohu", start(), end(), now));
            assertNull(mapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                    8L, "sohu", start(), end(), now, now.plusMinutes(10)));
            assertEquals(0, mapper.cancelReplaceablePendingSchedule(495L, 8L, "sohu", start(), end(), now));
            assertEquals(0, mapper.cancelSafeReplaceablePendingSchedule(
                    495L, 8L, "sohu", start(), end(), now, now.plusMinutes(10)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"project-auto-", "project-self-media-schedule-"})
    void confirmedReplacementCanSelectAutomaticTaskBehindManualTask(String prefix) throws Exception {
        insert(495L, "platform-quick-dispatch-first", now.plusMinutes(12));
        insert(496L, prefix + "second", now.plusMinutes(20));
        try (SqlSession session = factory.openSession()) {
            SelfMediaPublishScheduleMapper mapper = session.getMapper(SelfMediaPublishScheduleMapper.class);
            assertEquals(496L, mapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(
                    8L, "sohu", start(), end(), now).getId());
            assertEquals(496L, mapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                    8L, "sohu", start(), end(), now, now.plusMinutes(10)).getId());
            assertEquals(1, mapper.cancelSafeReplaceablePendingSchedule(
                    496L, 8L, "sohu", start(), end(), now, now.plusMinutes(10)));
            assertEquals(0, mapper.cancelReplaceablePendingSchedule(495L, 8L, "sohu", start(), end(), now));
        }
    }

    private void insert(long id, String requestKey, LocalDateTime plannedAt) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO self_media_publish_schedule
                  (id, brand_id, platform, request_idempotency_key, status, planned_publish_at, next_attempt_at)
                VALUES (?, 8, 'sohu', ?, 'pending', ?, ?)
                """)) {
            statement.setLong(1, id);
            statement.setString(2, requestKey);
            statement.setObject(3, plannedAt);
            statement.setObject(4, plannedAt);
            statement.executeUpdate();
        }
    }

    private LocalDateTime start() {
        return now.toLocalDate().withDayOfMonth(1).atStartOfDay();
    }

    private LocalDateTime end() {
        return start().plusMonths(1);
    }
}
