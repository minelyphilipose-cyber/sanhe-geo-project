package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PollRetentionTypedDryRunTest {

    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void scheduledDryRunUsesOneRunAndChainsMultiplePageAttempts() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicInteger pageQueries = new AtomicInteger();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    if (pageQueries.getAndIncrement() == 0) {
                        return List.of(
                                mapper.mapRow(candidateRow(7L, LocalDate.of(2026, 1, 1), "A"), 0),
                                mapper.mapRow(candidateRow(7L, LocalDate.of(2026, 1, 2), "A"), 1));
                    }
                    return List.of(mapper.mapRow(
                            candidateRow(7L, LocalDate.of(2026, 1, 2), "A"), 0));
                });
        stubReadOnlyEvaluation(jdbcTemplate);

        AuditFixture audit = auditFixture();
        PollRetentionDryRunRequest request = new PollRetentionDryRunRequest();
        request.setLimit(1);
        request.setHotRetentionDays(14);

        PollRetentionDryRunResponse response = service(jdbcTemplate, audit.service())
                .runScheduledDryRun(request, 2, 99L);

        assertEquals(1L, response.getRetentionRunId());
        assertEquals(2, response.getCandidateSlices());
        assertFalse(response.getHasMore());
        verify(audit.service(), times(1)).startRun(any());
        verify(audit.service(), times(2)).markAttemptRunning(anyLong());
        verify(audit.service(), times(2)).completeAttempt(anyLong(), any());
        verify(audit.service(), times(1)).finishRun(eq(1L), any());

        ArgumentCaptor<RetentionAttemptResult> resultCaptor =
                ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(audit.service(), times(2)).completeAttempt(anyLong(), resultCaptor.capture());
        ArgumentCaptor<RunBusinessSummary> summaryCaptor = ArgumentCaptor.forClass(RunBusinessSummary.class);
        verify(audit.service()).finishRun(eq(1L), summaryCaptor.capture());
        assertEquals(resultCaptor.getAllValues().stream().mapToLong(RetentionAttemptResult::candidateCount).sum(),
                summaryCaptor.getValue().candidateCount());
        assertEquals(resultCaptor.getAllValues().stream().mapToLong(RetentionAttemptResult::skippedCount).sum(),
                summaryCaptor.getValue().skippedCount());
        assertEquals(resultCaptor.getAllValues().stream().mapToLong(RetentionAttemptResult::warningCount).sum(),
                summaryCaptor.getValue().warningCount());

        ArgumentCaptor<RetentionAttemptCommand> commandCaptor =
                ArgumentCaptor.forClass(RetentionAttemptCommand.class);
        verify(audit.service(), times(2)).createOperationlessAttempt(any(), commandCaptor.capture());
        List<RetentionAttemptCommand> commands = commandCaptor.getAllValues();
        assertEquals(AttemptType.PAGE, commands.get(0).attemptType());
        assertEquals(null, commands.get(0).expectedLatestAttemptId());
        assertEquals(10L, commands.get(1).expectedLatestAttemptId());
        assertEquals(10L, commands.get(1).lineageParentAttemptId());

        ArgumentCaptor<RetentionRunStartCommand> runCaptor =
                ArgumentCaptor.forClass(RetentionRunStartCommand.class);
        verify(audit.service()).startRun(runCaptor.capture());
        assertEquals(1, runCaptor.getValue().scope().get("configFingerprintSchemaVersion"));
        assertEquals(64, ((String) runCaptor.getValue().scope().get("configFingerprint")).length());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageReadFailureTerminatesCurrentAttemptAndRun() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("read unavailable"));
        AuditFixture audit = auditFixture();

        assertThrows(IllegalStateException.class, () -> service(jdbcTemplate, audit.service())
                .runScheduledDryRun(new PollRetentionDryRunRequest(), 2, 99L));

        ArgumentCaptor<RetentionAttemptResult> resultCaptor =
                ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(audit.service()).completeAttempt(eq(10L), resultCaptor.capture());
        assertEquals(AttemptStatus.FAILED, resultCaptor.getValue().status());
        assertEquals("POLL_DRY_RUN_FAILED", resultCaptor.getValue().stableErrorCode());
        verify(audit.service(), times(1)).finishRun(eq(1L), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubReadOnlyEvaluation(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        doReturn(null).when(jdbcTemplate)
                .queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private ResultSet candidateRow(long projectId, LocalDate batchDate, String questionTier) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("project_id")).thenReturn(projectId);
        when(resultSet.getDate("batch_date")).thenReturn(Date.valueOf(batchDate));
        when(resultSet.getString("question_tier")).thenReturn(questionTier);
        when(resultSet.getLong("source_rows")).thenReturn(1L);
        return resultSet;
    }

    private PollRetentionDryRunService service(JdbcTemplate jdbcTemplate,
                                                DataRetentionP0AuditService auditService) {
        PollSummaryRecomputeService summaries = mock(PollSummaryRecomputeService.class);
        when(summaries.verifySlice(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(new PollSummaryRecomputeService.SummaryVerification(0, 0, 0, true, 0, 0, true));
        MobileDashboardEntityJudgeService entityJudges = mock(MobileDashboardEntityJudgeService.class);
        when(entityJudges.verifySummarySlice(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(new MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification(0, 0, true));
        return new PollRetentionDryRunService(jdbcTemplate, mock(CurrentUserService.class), summaries,
                entityJudges, new DataRetentionProperties(), auditService,
                new DataRetentionCanonicalEncoder(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuditFixture auditFixture() {
        DataRetentionP0AuditService auditService = mock(DataRetentionP0AuditService.class);
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", 1, new byte[32], "poll-results-v2");
        when(auditService.startRun(any())).thenReturn(new RetentionRunContext(1L,
                "00000000-0000-0000-0000-000000000001", "scheduler", "dry_run", contract,
                "test", "test", RunReplayStatus.CREATED, "running", NOW));
        when(auditService.createOperationlessAttempt(any(), any())).thenReturn(
                new RetentionAttemptContext(10L, 1L, null, 1, 1, AttemptType.PAGE,
                        AttemptStatus.PREPARED, "page-1"),
                new RetentionAttemptContext(11L, 1L, null, 2, 1, AttemptType.PAGE,
                        AttemptStatus.PREPARED, "page-2"));
        return new AuditFixture(auditService);
    }

    private record AuditFixture(DataRetentionP0AuditService service) {
    }
}
