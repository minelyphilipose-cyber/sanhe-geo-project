package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsitePublishedContentCleanupServiceTest {

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private DataRetentionRunAuditService auditService;
    private DataRetentionProperties properties;
    private WebsitePublishedContentCleanupService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionTemplate = mock(TransactionTemplate.class);
        auditService = mock(DataRetentionRunAuditService.class);
        properties = new DataRetentionProperties();
        when(auditService.startRun(anyString(), anyString(), any(), any(), any())).thenReturn(71L);
        service = new WebsitePublishedContentCleanupService(
                jdbcTemplate,
                transactionTemplate,
                auditService,
                properties,
                mock(CurrentUserService.class)
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dryRunRequiresOwnedWebsiteDistributedPublicEvidenceAndNoActiveWork() throws Exception {
        stubSingleCandidate();

        WebsitePublishedContentCleanupService.CleanupBatchResult result =
                service.runScheduled(24, 100, null, true, "scheduled dry run");

        assertTrue(result.dryRun());
        assertEquals(1, result.candidateCount());
        assertEquals("pending", result.items().get(0).result());
        assertEquals(2, result.items().get(0).bodyRowCount());
        assertEquals(4096L, result.items().get(0).bodyBytes());
        verify(transactionTemplate, never()).execute(any());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("'brand_geo_site','industry_site','forum_site'"));
        assertTrue(sql.contains("r.publish_status = 'distributed'"));
        assertTrue(sql.contains("r.url_quality = 'public_url'"));
        assertTrue(sql.contains("a.status IN ('distributed', 'published')"));
        assertTrue(sql.contains("a.status = 'deleted'"));
        assertTrue(sql.contains("purged_v.content_purged_at IS NOT NULL"));
        assertTrue(sql.contains("purged_dt.payload_purged_at IS NOT NULL"));
        assertTrue(sql.contains("active_dt.status NOT IN"));
        assertTrue(sql.contains("active_sm.status NOT IN"));
        assertFalse(sql.contains("published_url_pending','distributed"));
    }

    @Test
    void retentionHoursCannotGoBelowTwentyFourHours() throws Exception {
        stubSingleCandidate();

        WebsitePublishedContentCleanupService.CleanupBatchResult result =
                service.runScheduled(0, 100, null, true, "retention floor");

        assertEquals(24, result.retentionHours());
    }

    @Test
    void executeIsProtectedByIndependentFeatureGate() {
        BizException error = assertThrows(BizException.class,
                () -> service.runScheduled(24, 100, null, false, "execute"));

        assertEquals(403, error.getCode());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void executePurgesBodiesAndSuccessfulPayloadsThenSoftDeletesDraft() throws Exception {
        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        stubSingleCandidate();
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(101L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(2, 1, 1, 1);

        WebsitePublishedContentCleanupService.CleanupBatchResult result =
                service.runScheduled(24, 100, null, false, "website publication completed");

        assertEquals(1, result.cleanedCount());
        assertEquals(2, result.bodyRows());
        assertEquals(1, result.payloadRows());
        assertEquals(1, result.publishRecordRows());
        assertEquals("cleaned", result.items().get(0).result());

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(4))
                .update(updateSql.capture(), any(Object[].class));
        String allUpdates = String.join("\n", updateSql.getAllValues());
        assertTrue(allUpdates.contains("content_markdown = NULL"));
        assertTrue(allUpdates.contains("payload_purged_at = CURRENT_TIMESTAMP"));
        assertTrue(allUpdates.contains("raw_response = NULL"));
        assertTrue(allUpdates.contains("status = 'deleted'"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void executeSkipsWhenEligibilityChangesAfterCandidateScan() throws Exception {
        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        stubSingleCandidate();
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(101L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        WebsitePublishedContentCleanupService.CleanupBatchResult result =
                service.runScheduled(24, 100, null, false, "website publication completed");

        assertEquals(0, result.cleanedCount());
        assertEquals(1, result.skippedCount());
        assertEquals("skipped", result.items().get(0).result());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSingleCandidate() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    if (sql.contains("SELECT r.published_url,")) {
                        when(rs.getString("published_url"))
                                .thenReturn("https://agent.example/articles/101");
                        when(rs.getTimestamp("effective_published_at"))
                                .thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
                        return List.of(mapper.mapRow(rs, 0));
                    }
                    when(rs.getLong("article_id")).thenReturn(101L);
                    when(rs.getLong("project_id")).thenReturn(202L);
                    when(rs.getString("article_status")).thenReturn("distributed");
                    when(rs.getTimestamp("last_published_at"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
                    when(rs.getInt("body_row_count")).thenReturn(2);
                    when(rs.getLong("body_bytes")).thenReturn(4096L);
                    when(rs.getInt("payload_row_count")).thenReturn(1);
                    when(rs.getLong("payload_bytes")).thenReturn(1024L);
                    return List.of(mapper.mapRow(rs, 0));
                });
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("article_id")).thenReturn(101L);
            when(rs.getString("published_url"))
                    .thenReturn("https://agent.example/articles/101");
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }
}
