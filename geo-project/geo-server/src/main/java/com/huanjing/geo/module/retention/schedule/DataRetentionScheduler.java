package com.huanjing.geo.module.retention.schedule;

import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeRequest;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
import com.huanjing.geo.module.retention.service.WebsitePublishedContentCleanupService;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCommandFactory;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCoordinator;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.retention.scheduler", name = "enabled", havingValue = "true")
public class DataRetentionScheduler {

    private final DataRetentionProperties properties;
    private final WebsitePublishedContentCleanupService websitePublishedContentCleanupService;
    private final ArticleRetentionDryRunService articleRetentionDryRunService;
    private final ArticleBodyPurgeService articleBodyPurgeService;
    private final PollRetentionDryRunService pollRetentionDryRunService;
    private final DataRetentionSchedulerLock schedulerLock;
    private final DataRetentionExecuteCoordinator executeCoordinator;
    private final DataRetentionExecuteCommandFactory executeCommandFactory;
    private final DataRetentionP0AuditService p0AuditService;

    @Scheduled(cron = "${geo.retention.scheduler.cron:0 30 3 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void runDryRunSuite() {
        if (properties.getScheduler().isExecuteEnabled()) {
            runExecuteSuite();
            return;
        }
        DataRetentionSchedulerLock.Lease lease = schedulerLock.tryAcquire();
        if (lease == null) {
            log.info("Skip data retention schedule because another instance holds the lock");
            return;
        }
        try (lease) {
            runPriorityRetention(lease);
        }
    }

    private void runExecuteSuite() {
        boolean websiteBlocked = recoverDomainSafely("website_published_content");
        boolean pollBlocked = recoverDomainSafely("poll_results");
        if (websiteBlocked && pollBlocked) {
            return;
        }
        String triggerId = executeCommandFactory.newTriggerId();
        if (!websiteBlocked) {
            runFreshDomain("website_published_content", triggerId,
                    () -> executeCoordinator.execute(executeCommandFactory.schedulerWebsite(triggerId)));
        }
        if (!pollBlocked) {
            runFreshDomain("poll_results", triggerId,
                    () -> executeCoordinator.execute(executeCommandFactory.schedulerPoll(triggerId)));
        }
        log.info("Article archive/purge execute remains outside WP5 coordinator domains and was not invoked");
    }

    private boolean recoverDomainSafely(String domain) {
        try {
            return recoverDomain(domain);
        } catch (Exception ex) {
            log.warn("Data retention scheduled recovery failed, domain={}, error={}",
                    domain, ex.getMessage(), ex);
            return true;
        }
    }

    private void runFreshDomain(String domain, String triggerId, Runnable freshExecution) {
        try {
            freshExecution.run();
        } catch (Exception ex) {
            log.warn("Data retention scheduled fresh execution failed, domain={}, triggerId={}, error={}",
                    domain, triggerId, ex.getMessage(), ex);
        }
    }

    private boolean recoverDomain(String domain) {
        java.util.List<com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot> runs =
                p0AuditService.findRecoverableSchedulerRuns(domain);
        if (runs.size() > 1) {
            log.warn("Multiple recoverable data retention scheduler runs, domain={}, count={}", domain, runs.size());
            return true;
        }
        if (runs.size() == 1) {
            executeCoordinator.recoverScheduler(runs.get(0).context().runId(),
                    properties.getScheduler().getOperatorUserId());
            return true;
        }
        java.util.List<com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot>
                deferred = p0AuditService.findRecoverableDeferredRetryRuns(domain);
        if (!deferred.isEmpty()) {
            for (com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunSnapshot run
                    : deferred) {
                executeCoordinator.recoverDeferredRetry(run.context().runId(),
                        properties.getScheduler().getOperatorUserId());
            }
            return true;
        }
        java.util.List<com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.DeferredRetryOperation>
                retryable = p0AuditService.findDeferredRetryableOperations(domain,
                properties.getExecute().getMaxDeferredRetriesPerDomain());
        if (!retryable.isEmpty()) {
            for (com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.DeferredRetryOperation
                    operation : retryable) {
                executeCoordinator.execute(executeCommandFactory.schedulerDeferredRetry(
                        operation, properties.getScheduler().getOperatorUserId()));
            }
            return true;
        }
        return false;
    }

    private void runPriorityRetention(DataRetentionSchedulerLock.Lease lease) {
        DataRetentionProperties.Scheduler scheduler = properties.getScheduler();
        int limit = Math.max(1, scheduler.getLimitPerDomain());
        int maxBatches = Math.max(1, scheduler.getMaxBatchesPerRun());
        boolean execute = scheduler.isExecuteEnabled();
        run("website_published_content",
                () -> runWebsitePublishedCleanup(limit, maxBatches, lease));
        run("article_body_archive", () -> runArticleArchive(limit, maxBatches, execute, lease));
        run("article_body_purge", () -> runArticlePurge(limit, maxBatches, execute, lease));
        run("poll_results", () -> runPollPurge(Math.min(limit, 20), maxBatches, execute, lease));
    }

    private void runWebsitePublishedCleanup(int limit,
                                            int maxBatches,
                                            DataRetentionSchedulerLock.Lease lease) {
        lease.ensureHeld();
        websitePublishedContentCleanupService.runScheduledDryRun(
                properties.getScheduler().getWebsitePublishedRetentionHours(),
                limit,
                maxBatches,
                "scheduled website published content dry-run",
                properties.getScheduler().getOperatorUserId());
    }

    private void runArticleArchive(int limit,
                                   int maxBatches,
                                   boolean execute,
                                   DataRetentionSchedulerLock.Lease lease) {
        Long cursor = null;
        for (int batch = 0; batch < maxBatches; batch++) {
            lease.ensureHeld();
            ArticleArchiveDryRunRequest request = new ArticleArchiveDryRunRequest();
            request.setLimit(limit);
            request.setMinPublishedAgeDays(properties.getScheduler().getArticleRetentionDays());
            request.setCursorVersionId(cursor);
            request.setReason("scheduled article body archive");
            ArticleArchiveDryRunResponse response =
                    articleRetentionDryRunService.runScheduled(request, !execute);
            if (!Boolean.TRUE.equals(response.getHasMore()) || response.getNextCursorVersionId() == null) {
                break;
            }
            cursor = response.getNextCursorVersionId();
        }
    }

    private void runArticlePurge(int limit,
                                 int maxBatches,
                                 boolean execute,
                                 DataRetentionSchedulerLock.Lease lease) {
        Long cursor = null;
        for (int batch = 0; batch < maxBatches; batch++) {
            lease.ensureHeld();
            ArticleBodyPurgeRequest request = new ArticleBodyPurgeRequest();
            request.setLimit(limit);
            request.setRetentionDays(properties.getScheduler().getArticleRetentionDays());
            request.setArchiveGraceHours(properties.getScheduler().getArticleArchiveGraceHours());
            request.setCursorVersionId(cursor);
            request.setReason("scheduled article body retention");
            ArticleBodyPurgeResponse response = articleBodyPurgeService.runScheduled(request, !execute);
            if (!Boolean.TRUE.equals(response.getHasMore()) || response.getNextCursorVersionId() == null) {
                break;
            }
            cursor = response.getNextCursorVersionId();
        }
    }

    private void runPollPurge(int limit,
                              int maxBatches,
                              boolean execute,
                              DataRetentionSchedulerLock.Lease lease) {
        PollRetentionDryRunRequest request = new PollRetentionDryRunRequest();
        request.setLimit(limit);
        request.setHotRetentionDays(properties.getScheduler().getPollHotRetentionDays());
        request.setReason("scheduled poll detail retention");
        lease.ensureHeld();
        pollRetentionDryRunService.runScheduledDryRun(request, maxBatches,
                properties.getScheduler().getOperatorUserId());
    }

    private void run(String domain, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            log.warn("Data retention scheduled domain failed, domain={}, error={}", domain, ex.getMessage(), ex);
        }
    }
}
