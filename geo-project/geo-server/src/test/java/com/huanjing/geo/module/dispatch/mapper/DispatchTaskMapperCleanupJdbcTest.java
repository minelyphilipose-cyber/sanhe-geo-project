package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchTaskMapperCleanupJdbcTest {

    private JdbcTemplate jdbcTemplate;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dispatch_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        createSchema();

        MybatisConfiguration configuration = new MybatisConfiguration(
                new Environment("test", new JdbcTransactionFactory(), dataSource)
        );
        configuration.addMapper(DispatchTaskMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void deletesOnlyExpiredTerminalTasksWithoutBusinessEvidenceReferences() {
        insertTask(1L, "completed", 120);
        insertTask(2L, "failed", 120);
        insertTask(3L, "dead_letter", 120);
        insertTask(4L, "cancelled", 120);
        insertTask(5L, "running", 120);
        insertTask(6L, "completed", 1);
        jdbcTemplate.update("INSERT INTO article_batch (id, dispatch_task_id) VALUES (20, 2)");
        jdbcTemplate.update("INSERT INTO presale_diagnosis_batches (id, dispatch_task_id) VALUES (30, 3)");
        jdbcTemplate.update("INSERT INTO dispatch_alert (id, task_id) VALUES (40, 4)");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            int deleted = session.getMapper(DispatchTaskMapper.class)
                    .deleteUnreferencedTerminalBefore(LocalDateTime.now().minusDays(90));

            assertEquals(1, deleted);
        }

        assertEquals(0, countTask(1L));
        assertEquals(1, countTask(2L));
        assertEquals(1, countTask(3L));
        assertEquals(1, countTask(4L));
        assertEquals(1, countTask(5L));
        assertEquals(1, countTask(6L));
    }

    private void insertTask(long id, String status, int ageDays) {
        jdbcTemplate.update("""
                INSERT INTO dispatch_task (id, status, updated_at)
                VALUES (?, ?, DATEADD('DAY', ?, CURRENT_TIMESTAMP))
                """, id, status, -ageDays);
    }

    private int countTask(long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_task WHERE id = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE dispatch_task (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(32) NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_batch (
                    id BIGINT PRIMARY KEY,
                    dispatch_task_id BIGINT NOT NULL,
                    CONSTRAINT fk_article_batch_task
                        FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE presale_diagnosis_batches (
                    id BIGINT PRIMARY KEY,
                    dispatch_task_id BIGINT NOT NULL,
                    CONSTRAINT fk_presale_batch_dispatch
                        FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE dispatch_alert (
                    id BIGINT PRIMARY KEY,
                    task_id BIGINT,
                    CONSTRAINT fk_dispatch_alert_task
                        FOREIGN KEY (task_id) REFERENCES dispatch_task(id)
                )
                """);
    }
}
