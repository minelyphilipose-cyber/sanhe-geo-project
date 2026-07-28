package com.huanjing.geo.module.dispatch.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void verifySliceRejectsCorruptSummaryMetricsWhenSourceChecksumStillMatches() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet source = mock(ResultSet.class);
        ResultSet storedKeyword = mock(ResultSet.class);
        ResultSet storedPlatform = mock(ResultSet.class);
        LocalDate batchDate = LocalDate.of(2026, 7, 16);
        LocalDateTime sourceTime = LocalDateTime.of(2026, 7, 16, 10, 0);

        when(source.wasNull()).thenReturn(false);
        when(source.getLong("id")).thenReturn(1L);
        when(source.getLong("project_id")).thenReturn(200L);
        when(source.getLong("keyword_result_id")).thenReturn(300L);
        when(source.getLong("platform_id")).thenReturn(55L);
        when(source.getString("keyword_text_snapshot")).thenReturn("checksum question");
        when(source.getString("question_tier")).thenReturn("A");
        when(source.getString("platform_code")).thenReturn("doubao_web");
        when(source.getString("channel_code")).thenReturn("doubao");
        when(source.getString("status")).thenReturn("completed");
        when(source.getString("record_type")).thenReturn("normal");
        when(source.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(source.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(sourceTime));

        String sourceChecksum = sha256(canonical(
                1L, 300L, "checksum question", 55L, "doubao_web", "completed",
                0, 0L, null, null, null, null, 0, "doubao", 0L,
                null, null, null, null, null, null, null,
                "normal", sourceTime, sourceTime));
        configureStoredKeyword(storedKeyword, batchDate, sourceTime, sourceChecksum);
        configureStoredPlatform(storedPlatform, batchDate, sourceTime, sourceChecksum);
        when(storedKeyword.getInt("hit_count")).thenReturn(99);
        when(storedPlatform.getInt("hit_count")).thenReturn(99);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);
            if (sql.contains("FROM poll_results pr")) {
                return List.of(mapper.mapRow(source, 0));
            }
            if (sql.contains("FROM poll_keyword_daily_summary")) {
                return List.of(mapper.mapRow(storedKeyword, 0));
            }
            if (sql.contains("FROM poll_platform_daily_summary")) {
                return List.of(mapper.mapRow(storedPlatform, 0));
            }
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        PollSummaryRecomputeService.SummaryVerification verification =
                new PollSummaryRecomputeService(jdbcTemplate).verifySlice(200L, batchDate, "A");

        assertEquals(1, verification.sourceRowCount());
        assertEquals(1, verification.keywordSummarySourceRowCount());
        assertEquals(1, verification.platformSummarySourceRowCount());
        assertFalse(verification.keywordMatched());
        assertFalse(verification.platformMatched());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recomputePersistsEffectiveWebMetricsAndUsesMatchingSqlArguments() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet searchNotTriggeredResultSet = mock(ResultSet.class);
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

        when(searchNotTriggeredResultSet.wasNull()).thenReturn(false);
        when(searchNotTriggeredResultSet.getLong("id")).thenReturn(2L);
        when(searchNotTriggeredResultSet.getLong("project_id")).thenReturn(200L);
        when(searchNotTriggeredResultSet.getLong("keyword_result_id")).thenReturn(300L);
        when(searchNotTriggeredResultSet.getLong("platform_id")).thenReturn(55L);
        when(searchNotTriggeredResultSet.getLong("effective_attempt_id")).thenReturn(9002L);
        when(searchNotTriggeredResultSet.getLong("response_time_ms")).thenReturn(1000L);
        when(searchNotTriggeredResultSet.getInt("request_count")).thenReturn(1);
        when(searchNotTriggeredResultSet.getInt("contact_mention_count")).thenReturn(0);
        when(searchNotTriggeredResultSet.getString("keyword_text_snapshot")).thenReturn("联网测试问题");
        when(searchNotTriggeredResultSet.getString("question_tier")).thenReturn("A");
        when(searchNotTriggeredResultSet.getString("platform_code")).thenReturn("doubao_web");
        when(searchNotTriggeredResultSet.getString("channel_code")).thenReturn("doubao");
        when(searchNotTriggeredResultSet.getString("platform_name_snapshot")).thenReturn("豆包联网问答");
        when(searchNotTriggeredResultSet.getString("status")).thenReturn("completed");
        when(searchNotTriggeredResultSet.getString("record_type")).thenReturn("normal");
        when(searchNotTriggeredResultSet.getObject("execution_finalized")).thenReturn(Boolean.TRUE);
        when(searchNotTriggeredResultSet.getObject("search_requested")).thenReturn(Boolean.TRUE);
        when(searchNotTriggeredResultSet.getObject("search_triggered")).thenReturn(Boolean.FALSE);
        when(searchNotTriggeredResultSet.getObject("brand_in_search")).thenReturn(Boolean.TRUE);
        when(searchNotTriggeredResultSet.getObject("brand_in_answer")).thenReturn(Boolean.TRUE);
        when(searchNotTriggeredResultSet.getObject("confirmed_citation_exposure")).thenReturn(Boolean.TRUE);
        when(searchNotTriggeredResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(searchNotTriggeredResultSet.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(sourceTime));

        doAnswer(invocation -> {
            sourceQuerySql.set(invocation.getArgument(0));
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0), mapper.mapRow(searchNotTriggeredResultSet, 1));
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
        assertEquals(new BigDecimal("0.5000"), keywordCall.args()[22]);

        assertEquals(countPlaceholders(platformCall.sql()), platformCall.args().length);
        assertEquals("doubao", platformCall.args()[6]);
        assertEquals(1, platformCall.args()[16]);
        assertEquals(1, platformCall.args()[17]);
        assertEquals(1, platformCall.args()[18]);
        assertEquals(1, platformCall.args()[19]);
        assertEquals(new BigDecimal("0.5000"), platformCall.args()[20]);
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

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte item : hash) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private static String canonical(Object... fields) {
        return java.util.Arrays.stream(fields)
                .map(field -> field == null ? "<NULL>" : String.valueOf(field).trim())
                .collect(java.util.stream.Collectors.joining("\u001F"));
    }

    private static void configureStoredKeyword(ResultSet resultSet,
                                               LocalDate batchDate,
                                               LocalDateTime sourceTime,
                                               String sourceChecksum) throws Exception {
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("project_id")).thenReturn(200L);
        when(resultSet.getDate("batch_date")).thenReturn(java.sql.Date.valueOf(batchDate));
        when(resultSet.getString("question_tier")).thenReturn("A");
        when(resultSet.getString("keyword_identity_type")).thenReturn("ID");
        when(resultSet.getString("keyword_identity_value")).thenReturn("ID:300");
        when(resultSet.getString("dim_hash"))
                .thenReturn(sha256("200\u001F2026-07-16\u001FA\u001FID:300"));
        when(resultSet.getLong("keyword_result_id")).thenReturn(300L);
        when(resultSet.getString("keyword_text_snapshot")).thenReturn("checksum question");
        when(resultSet.getString("keyword_text_normalized")).thenReturn("checksum question");
        when(resultSet.getInt("source_row_count")).thenReturn(1);
        when(resultSet.getInt("platform_count")).thenReturn(1);
        when(resultSet.getInt("completed_count")).thenReturn(1);
        when(resultSet.getBigDecimal("confirmed_citation_exposure_rate"))
                .thenReturn(new BigDecimal("0.0000"));
        when(resultSet.getTimestamp("last_source_created_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(resultSet.getTimestamp("last_source_updated_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(resultSet.getString("source_checksum")).thenReturn(sourceChecksum);
    }

    private static void configureStoredPlatform(ResultSet resultSet,
                                                LocalDate batchDate,
                                                LocalDateTime sourceTime,
                                                String sourceChecksum) throws Exception {
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("project_id")).thenReturn(200L);
        when(resultSet.getDate("batch_date")).thenReturn(java.sql.Date.valueOf(batchDate));
        when(resultSet.getString("question_tier")).thenReturn("A");
        when(resultSet.getLong("platform_id")).thenReturn(55L);
        when(resultSet.getString("dim_hash"))
                .thenReturn(sha256("200\u001F2026-07-16\u001FA\u001F55"));
        when(resultSet.getString("platform_code")).thenReturn("doubao_web");
        when(resultSet.getString("channel_code")).thenReturn("doubao");
        when(resultSet.getInt("source_row_count")).thenReturn(1);
        when(resultSet.getInt("completed_count")).thenReturn(1);
        when(resultSet.getBigDecimal("confirmed_citation_exposure_rate"))
                .thenReturn(new BigDecimal("0.0000"));
        when(resultSet.getTimestamp("last_source_created_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(resultSet.getTimestamp("last_source_updated_at")).thenReturn(Timestamp.valueOf(sourceTime));
        when(resultSet.getString("source_checksum")).thenReturn(sourceChecksum);
    }

    private record SqlCall(String sql, Object[] args) {
    }
}
