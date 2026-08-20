package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PollResultMapperJdbcTest {

    private JdbcTemplate jdbcTemplate;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:poll_result_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        createSchema();

        MybatisConfiguration configuration = new MybatisConfiguration(
                new Environment("test", new JdbcTransactionFactory(), dataSource)
        );
        configuration.addMapper(PollResultMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void businessUpdatePreservesExecutionAndRetentionProjection() {
        jdbcTemplate.update("""
                INSERT INTO poll_results (
                    id, status, request_count, response_time_ms, is_hit,
                    execution_finalized, retry_chain_status, latest_attempt_id,
                    effective_attempt_id, latest_attempt_status, result_code,
                    search_requested, search_triggered, search_status,
                    brand_in_search, brand_in_answer, citation_confidence,
                    confirmed_citation_exposure, version, deleted_at, deleted_by, delete_reason
                ) VALUES (
                    1, 'running', 0, 0, FALSE,
                    TRUE, 'FINALIZED', 101, 100, 'SUCCEEDED', 'R2',
                    TRUE, TRUE, 'TRIGGERED', TRUE, TRUE, 'HIGH', TRUE,
                    7, TIMESTAMP '2026-08-01 10:00:00', 9, 'retention marker'
                )
                """);

        PollResult businessResult = new PollResult();
        businessResult.setId(1L);
        businessResult.setStatus("completed");
        businessResult.setRequestCount(2);
        businessResult.setResponseTimeMs(250L);
        businessResult.setIsHit(true);
        businessResult.setEffectiveHit(true);
        businessResult.setMatchType("exact");
        businessResult.setSiteMentioned(true);
        businessResult.setContactMentioned(true);
        businessResult.setContactMentionCount(1);
        businessResult.setJudgeStatus("success");
        businessResult.setHitLevel("strong");
        businessResult.setHitSentiment("positive");
        businessResult.setMentionType("brand");
        businessResult.setJudgeEvidence("matched brand");
        businessResult.setJudgeRiskReason(null);
        businessResult.setJudgeModel("judge-v1");
        businessResult.setJudgeAt(LocalDateTime.of(2026, 8, 20, 8, 30));
        businessResult.setJudgeError(null);
        businessResult.setRecordType("hit");
        businessResult.setDetailJson("{\"answer\":\"ok\"}");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            assertEquals(1, session.getMapper(PollResultMapper.class)
                    .updateBusinessProjection(businessResult));
        }

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM poll_results WHERE id = 1");
        assertEquals("completed", row.get("STATUS"));
        assertEquals(2, ((Number) row.get("REQUEST_COUNT")).intValue());
        assertEquals(true, row.get("EXECUTION_FINALIZED"));
        assertEquals("FINALIZED", row.get("RETRY_CHAIN_STATUS"));
        assertEquals(101L, ((Number) row.get("LATEST_ATTEMPT_ID")).longValue());
        assertEquals(100L, ((Number) row.get("EFFECTIVE_ATTEMPT_ID")).longValue());
        assertEquals("SUCCEEDED", row.get("LATEST_ATTEMPT_STATUS"));
        assertEquals("R2", row.get("RESULT_CODE"));
        assertEquals(true, row.get("SEARCH_REQUESTED"));
        assertEquals(true, row.get("SEARCH_TRIGGERED"));
        assertEquals("TRIGGERED", row.get("SEARCH_STATUS"));
        assertEquals(true, row.get("BRAND_IN_SEARCH"));
        assertEquals(true, row.get("BRAND_IN_ANSWER"));
        assertEquals("HIGH", row.get("CITATION_CONFIDENCE"));
        assertEquals(true, row.get("CONFIRMED_CITATION_EXPOSURE"));
        assertEquals(7L, ((Number) row.get("VERSION")).longValue());
        assertEquals(9L, ((Number) row.get("DELETED_BY")).longValue());
        assertEquals("retention marker", row.get("DELETE_REASON"));
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE poll_results (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(32),
                    request_count INT,
                    response_time_ms BIGINT,
                    is_hit BOOLEAN,
                    effective_hit BOOLEAN,
                    match_type VARCHAR(32),
                    site_mentioned BOOLEAN,
                    contact_mentioned BOOLEAN,
                    contact_mention_count INT,
                    judge_status VARCHAR(20),
                    hit_level VARCHAR(20),
                    hit_sentiment VARCHAR(20),
                    mention_type VARCHAR(32),
                    judge_evidence VARCHAR(500),
                    judge_risk_reason VARCHAR(500),
                    judge_model VARCHAR(128),
                    judge_at TIMESTAMP,
                    judge_error VARCHAR(500),
                    record_type VARCHAR(16),
                    detail_json JSON,
                    latest_attempt_id BIGINT,
                    effective_attempt_id BIGINT,
                    latest_attempt_status VARCHAR(24),
                    execution_finalized BOOLEAN NOT NULL DEFAULT FALSE,
                    retry_chain_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
                    result_code VARCHAR(4),
                    search_requested BOOLEAN NOT NULL DEFAULT FALSE,
                    search_triggered BOOLEAN NOT NULL DEFAULT FALSE,
                    search_status VARCHAR(32),
                    brand_in_search BOOLEAN NOT NULL DEFAULT FALSE,
                    brand_in_answer BOOLEAN NOT NULL DEFAULT FALSE,
                    citation_confidence VARCHAR(16) NOT NULL DEFAULT 'NONE',
                    confirmed_citation_exposure BOOLEAN NOT NULL DEFAULT FALSE,
                    version BIGINT NOT NULL DEFAULT 0,
                    deleted_at TIMESTAMP,
                    deleted_by BIGINT,
                    delete_reason VARCHAR(500),
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
