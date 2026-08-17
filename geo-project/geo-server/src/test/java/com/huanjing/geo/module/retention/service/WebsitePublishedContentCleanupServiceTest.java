package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.dto.WebsitePublishedCleanupRequest;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader.DryRunCandidate;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader.DryRunPage;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsitePublishedContentCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private WebsiteRetentionDryRunReader reader;
    private DataRetentionP0AuditService audit;
    private CurrentUserService currentUserService;
    private WebsitePublishedContentCleanupService service;

    @BeforeEach
    void setUp() {
        reader = mock(WebsiteRetentionDryRunReader.class);
        audit = mock(DataRetentionP0AuditService.class);
        currentUserService = mock(CurrentUserService.class);
        service = new WebsitePublishedContentCleanupService(reader, audit,
                new DataRetentionCanonicalEncoder(), currentUserService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        SysUser operator = new SysUser();
        operator.setId(9L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(audit.startRun(any())).thenAnswer(invocation -> run(invocation.getArgument(0)));
        AtomicLong attemptIds = new AtomicLong(70L);
        when(audit.createOperationlessAttempt(any(), any())).thenAnswer(invocation -> {
            RetentionRunContext run = invocation.getArgument(0);
            RetentionAttemptCommand command = invocation.getArgument(1);
            long attemptId = attemptIds.incrementAndGet();
            return new RetentionAttemptContext(attemptId, run.runId(), null,
                    (int) (attemptId - 70L), (int) (attemptId - 70L), command.attemptType(),
                    AttemptStatus.PREPARED, command.attemptRequestId());
        });
    }

    @Test
    void manualDryRunUsesTypedRunAndOperationlessPageWithoutPersistingResponseEvidence() {
        when(reader.readPage(any(), eq(null), eq(100))).thenReturn(new DryRunPage(
                List.of(candidate(101L, 4096L)), false, 101L));

        WebsitePublishedContentCleanupService.CleanupBatchResult result =
                service.dryRun(new WebsitePublishedCleanupRequest());

        assertTrue(result.dryRun());
        assertEquals(71L, result.retentionRunId());
        assertEquals(1, result.candidateCount());
        assertEquals(101L, result.items().get(0).articleId());
        assertEquals(4096L, result.items().get(0).bodyBytes());
        ArgumentCaptor<RetentionRunStartCommand> start = ArgumentCaptor.forClass(RetentionRunStartCommand.class);
        verify(audit).startRun(start.capture());
        assertEquals("dry_run", start.getValue().mode());
        assertEquals("manual", start.getValue().triggerSource());
        assertEquals(9L, start.getValue().requestedBy());
        assertFalse(start.getValue().scope().toString().contains("101"));
        assertFalse(start.getValue().scope().toString().contains("4096"));

        ArgumentCaptor<RetentionAttemptCommand> attempt = ArgumentCaptor.forClass(RetentionAttemptCommand.class);
        verify(audit).createOperationlessAttempt(any(), attempt.capture());
        assertEquals("page", attempt.getValue().attemptType().databaseValue());
        assertFalse(attempt.getValue().domainScope().toString().contains("101"));
        ArgumentCaptor<RetentionAttemptResult> completed = ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(audit).completeAttempt(eq(71L), completed.capture());
        assertEquals(AttemptStatus.SUCCEEDED, completed.getValue().status());
        assertEquals(1, completed.getValue().candidateCount());
        assertEquals(0, completed.getValue().affectedCount());
        assertFalse(completed.getValue().metrics().toString().contains("4096"));
        ArgumentCaptor<RunBusinessSummary> summary = ArgumentCaptor.forClass(RunBusinessSummary.class);
        verify(audit).finishRun(eq(71L), summary.capture());
        assertEquals(1, summary.getValue().candidateCount());
        assertEquals(0, summary.getValue().affectedCount());
    }

    @Test
    void schedulerPaginatesInsideOneTypedRunAndChainsExpectedLatestAttempt() {
        when(reader.readPage(any(), eq(null), eq(1))).thenReturn(
                new DryRunPage(List.of(candidate(101L, 10L)), true, 101L));
        when(reader.readPage(any(), eq(101L), eq(1))).thenReturn(
                new DryRunPage(List.of(candidate(102L, 20L)), false, 102L));

        WebsitePublishedContentCleanupService.CleanupBatchResult result = service.runScheduledDryRun(
                24, 1, 2, "scheduled dry-run", 19L);

        assertEquals(2, result.candidateCount());
        assertFalse(result.hasMore());
        verify(audit).startRun(any());
        ArgumentCaptor<RetentionAttemptCommand> attempts = ArgumentCaptor.forClass(RetentionAttemptCommand.class);
        verify(audit, times(2)).createOperationlessAttempt(any(), attempts.capture());
        assertNull(attempts.getAllValues().get(0).expectedLatestAttemptId());
        assertEquals(71L, attempts.getAllValues().get(1).expectedLatestAttemptId());
        verify(audit).finishRun(eq(71L), any());
    }

    @Test
    void pageReadFailurePersistsFailedTypedAttemptAndFinishesFailedRun() {
        when(reader.readPage(any(), eq(null), eq(100))).thenThrow(new IllegalStateException("db unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.runScheduledDryRun(24, 100, 1, "scheduled dry-run", 19L));

        ArgumentCaptor<RetentionAttemptResult> completed = ArgumentCaptor.forClass(RetentionAttemptResult.class);
        verify(audit).completeAttempt(eq(71L), completed.capture());
        assertEquals(AttemptStatus.FAILED, completed.getValue().status());
        assertEquals("WEBSITE_DRY_RUN_FAILED", completed.getValue().stableErrorCode());
        assertFalse(completed.getValue().errorMessage().contains("db unavailable"));
        ArgumentCaptor<RunBusinessSummary> summary = ArgumentCaptor.forClass(RunBusinessSummary.class);
        verify(audit).finishRun(eq(71L), summary.capture());
        assertEquals(1, summary.getValue().warningCount());
    }

    private static RetentionRunContext run(RetentionRunStartCommand command) {
        return new RetentionRunContext(71L, command.triggerId(), command.triggerSource(), command.mode(),
                command.compatibilityContract(), "test", "test", RunReplayStatus.CREATED, "running", NOW);
    }

    private static DryRunCandidate candidate(long articleId, long bodyBytes) {
        return new DryRunCandidate(articleId, 202L, "distributed",
                LocalDateTime.of(2026, 8, 12, 10, 0), 2, bodyBytes, 1, 1024L);
    }
}
