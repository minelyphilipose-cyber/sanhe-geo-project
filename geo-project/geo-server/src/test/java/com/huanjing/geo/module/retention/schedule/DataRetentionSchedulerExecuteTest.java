package com.huanjing.geo.module.retention.schedule;

import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCommandFactory;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCoordinator;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
import com.huanjing.geo.module.retention.service.WebsitePublishedContentCleanupService;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionSchedulerExecuteTest {

    @Test
    void websiteDryRunDelegatesAllPaginationToOneTypedRunInvocation() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getScheduler().setExecuteEnabled(false);
        properties.getScheduler().setLimitPerDomain(37);
        properties.getScheduler().setMaxBatchesPerRun(9);
        properties.getScheduler().setWebsitePublishedRetentionHours(48);
        properties.getScheduler().setOperatorUserId(9001);
        WebsitePublishedContentCleanupService website = mock(WebsitePublishedContentCleanupService.class);
        DataRetentionSchedulerLock lock = mock(DataRetentionSchedulerLock.class);
        DataRetentionSchedulerLock.Lease lease = mock(DataRetentionSchedulerLock.Lease.class);
        when(lock.tryAcquire()).thenReturn(lease);
        ArticleRetentionDryRunService articleArchive = mock(ArticleRetentionDryRunService.class);
        ArticleArchiveDryRunResponse articleArchiveResponse = mock(ArticleArchiveDryRunResponse.class);
        when(articleArchive.runScheduled(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(articleArchiveResponse);
        ArticleBodyPurgeService articlePurge = mock(ArticleBodyPurgeService.class);
        ArticleBodyPurgeResponse articlePurgeResponse = mock(ArticleBodyPurgeResponse.class);
        when(articlePurge.runScheduled(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(articlePurgeResponse);
        PollRetentionDryRunService poll = mock(PollRetentionDryRunService.class);
        PollRetentionDryRunResponse pollResponse = mock(PollRetentionDryRunResponse.class);
        when(poll.runScheduled(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(pollResponse);
        DataRetentionScheduler scheduler = new DataRetentionScheduler(properties, website,
                articleArchive, articlePurge, poll, lock, mock(DataRetentionExecuteCoordinator.class),
                mock(DataRetentionExecuteCommandFactory.class), mock(DataRetentionP0AuditService.class));

        scheduler.runDryRunSuite();

        verify(website).runScheduledDryRun(
                48, 37, 9, "scheduled website published content dry-run", 9001L);
    }

    @Test
    void websiteRecoveryBacklogDoesNotStarvePollFreshExecution() {
        Fixture fixture = fixture();
        when(fixture.audit.findRecoverableSchedulerRuns("website_published_content"))
                .thenReturn(List.of(running(11, "website_published_content")));
        when(fixture.audit.findRecoverableSchedulerRuns("poll_results")).thenReturn(List.of());
        when(fixture.audit.findRecoverableDeferredRetryRuns("poll_results")).thenReturn(List.of());
        when(fixture.audit.findDeferredRetryableOperations("poll_results", 20)).thenReturn(List.of());

        fixture.scheduler.runDryRunSuite();

        verify(fixture.coordinator).recoverScheduler(11, 9001);
        verify(fixture.coordinator).execute(fixture.pollCommand);
    }

    @Test
    void websiteRecoveryFailureDoesNotStarvePollRecovery() {
        Fixture fixture = fixture();
        when(fixture.audit.findRecoverableSchedulerRuns("website_published_content"))
                .thenReturn(List.of(running(11, "website_published_content")));
        when(fixture.audit.findRecoverableSchedulerRuns("poll_results"))
                .thenReturn(List.of(running(22, "poll_results")));
        doThrow(new IllegalStateException("website recovery failed"))
                .when(fixture.coordinator).recoverScheduler(11, 9001);

        fixture.scheduler.runDryRunSuite();

        verify(fixture.coordinator).recoverScheduler(11, 9001);
        verify(fixture.coordinator).recoverScheduler(22, 9001);
    }

    private Fixture fixture() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getScheduler().setExecuteEnabled(true);
        properties.getScheduler().setOperatorUserId(9001);
        DataRetentionExecuteCoordinator coordinator = mock(DataRetentionExecuteCoordinator.class);
        DataRetentionExecuteCommandFactory factory = mock(DataRetentionExecuteCommandFactory.class);
        DataRetentionP0AuditService audit = mock(DataRetentionP0AuditService.class);
        String trigger = "10000000-0000-0000-0000-000000000001";
        RetentionExecuteCommand website = command(trigger, RetentionDomain.WEBSITE_PUBLISHED_CONTENT);
        RetentionExecuteCommand poll = command(trigger, RetentionDomain.POLL_RESULTS);
        when(factory.newTriggerId()).thenReturn(trigger);
        when(factory.schedulerWebsite(trigger)).thenReturn(website);
        when(factory.schedulerPoll(trigger)).thenReturn(poll);
        DataRetentionScheduler scheduler = new DataRetentionScheduler(properties,
                mock(WebsitePublishedContentCleanupService.class), mock(ArticleRetentionDryRunService.class),
                mock(ArticleBodyPurgeService.class), mock(PollRetentionDryRunService.class),
                mock(DataRetentionSchedulerLock.class), coordinator, factory, audit);
        return new Fixture(scheduler, coordinator, audit, poll);
    }

    private RetentionExecuteCommand command(String trigger, RetentionDomain domain) {
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                domain.databaseValue(), 1, new byte[32], "policy-v1");
        return new RetentionExecuteCommand(trigger, domain, ExecutionSource.SCHEDULER, 9001,
                "scheduler test", null, null, contract, Map.of(), null, null,
                new ExecutionBudget(1, 1, Duration.ofMinutes(1)), null);
    }

    private RetentionRunSnapshot running(long id, String domain) {
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(domain, 1,
                new byte[32], "policy-v1");
        RetentionRunContext context = new RetentionRunContext(id,
                id == 11 ? "20000000-0000-0000-0000-000000000001"
                        : "20000000-0000-0000-0000-000000000002",
                "scheduler", "execute", contract, "v1", "v1",
                RunReplayStatus.RUNNING_REPLAY, "running", Instant.parse("2026-08-13T00:00:00Z"));
        return new RetentionRunSnapshot(context, null, 0, 0, 0, 0, Map.of());
    }

    private record Fixture(DataRetentionScheduler scheduler,
                           DataRetentionExecuteCoordinator coordinator,
                           DataRetentionP0AuditService audit,
                           RetentionExecuteCommand pollCommand) {
    }
}
