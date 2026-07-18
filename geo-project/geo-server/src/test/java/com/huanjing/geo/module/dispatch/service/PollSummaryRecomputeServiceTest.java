package com.huanjing.geo.module.dispatch.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollSummaryRecomputeServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recomputePersistsEffectiveWebMetricsAndUsesMatchingSqlArguments() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        AtomicReference<String> sourceQuerySql = new AtomicReference<>();
        LocalDate batchDate = LocalDate.of(2026, 7, 16);
        LocalDateTime sourceTime = LocalDateTime.of(2026, 7, 16, 10, 0);

        when(jdbcTemplate.queryForObject(contains("data_retention_purged_slice"), eq(Integer.class),
                any(), any(), any())).thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("SELECT id"), eq(Long.class),
                any(), any(), any())).thenReturn(1L);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getLong("project_id")).thenReturn(200L);
        when(resultSet.getLong("keyword_result_id")).thenReturn(300L);
        when(resultSet.getLong("platform_id")).thenReturn(55L);
        when(resultSet.getLong("effective_attempt_id")).thenReturn(9001L);
        when(resultSet.getLong("response_time_ms")).thenReturn(1200L);
        when(resultSet.getInt("request_count")).thenReturn(1);
        when(resultSet.getInt("contact_mention_count")).thenReturn(0);
        when(resultSet.getString("keyword_text_snapshot")).thenReturn("联网测试问题");
        when(resultSet.getString("question_tier")).thenReturn("A");
        when(resultSet.getString("platform_code")).thenReturn("doubao_web");
        when(resultSet.getString("channel_code")).thenReturn("doubao");
        when(resultSet.getString("platform_name_snapshot")).thenReturn("豆包联网问答");
        when(resultSet.getString("status")).thenReturn("completed");
        when(resultSet.getString("record_type")).thenReturn("normal");
        when(resultSet.getObject("is_hit")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("effective_hit")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("site_mentioned")).thenReturn(Boolean.FALSE);
        when(resultSet.getObject("contact_mentioned")).thenReturn(Boolean.FALSE);
        when(resultSet.getObject("execution_finalized")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("search_requested")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("search_triggered")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("brand_in_search")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("brand_in_answer")).thenReturn(Boolean.TRUE);
        when(resultSet.getObject("confirmed_citation_exposure")).thenReturn(Boolean.TRUE);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(sourceTime));

        doAnswer(invocation -> {
            sourceQuerySql.set(invocation.getArgument(0));
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(), any(), any());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        PollSummaryRecomputeService service = new PollSummaryRecomputeService(jdbcTemplate);
        service.recomputeSlice(200L, batchDate, "A");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());

        SqlCall keywordCall = findCall(sqlCaptor.getAllValues(), argsCaptor.getAllValues(),
                "INSERT INTO poll_keyword_daily_summary");
        SqlCall platformCall = findCall(sqlCaptor.getAllValues(), argsCaptor.getAllValues(),
                "INSERT INTO poll_platform_daily_summary");

        assertEquals(countPlaceholders(keywordCall.sql()), keywordCall.args().length);
        assertEquals(1, keywordCall.args()[18]);
        assertEquals(1, keywordCall.args()[19]);
        assertEquals(1, keywordCall.args()[20]);
        assertEquals(1, keywordCall.args()[21]);
        assertEquals(new BigDecimal("1.0000"), keywordCall.args()[22]);

        assertEquals(countPlaceholders(platformCall.sql()), platformCall.args().length);
        assertEquals("doubao", platformCall.args()[6]);
        assertEquals(1, platformCall.args()[16]);
        assertEquals(1, platformCall.args()[17]);
        assertEquals(1, platformCall.args()[18]);
        assertEquals(1, platformCall.args()[19]);
        assertEquals(new BigDecimal("1.0000"), platformCall.args()[20]);
        org.junit.jupiter.api.Assertions.assertTrue(
                sourceQuerySql.get().contains("COALESCE(pb.trigger_type, 'SCHEDULED') != 'MANUAL'"),
                "formal summary recompute must exclude manual verification batches"
        );
    }

    private static SqlCall findCall(List<String> sqlValues, List<Object[]> argsValues, String marker) {
        for (int i = 0; i < sqlValues.size(); i++) {
            if (sqlValues.get(i).contains(marker)) {
                Object[] args = argsValues.get(i);
                assertNotNull(args);
                return new SqlCall(sqlValues.get(i), args);
            }
        }
        throw new AssertionError("SQL call not found: " + marker);
    }

    private static int countPlaceholders(String sql) {
        return (int) sql.chars().filter(ch -> ch == '?').count();
    }

    private record SqlCall(String sql, Object[] args) {
    }
}
