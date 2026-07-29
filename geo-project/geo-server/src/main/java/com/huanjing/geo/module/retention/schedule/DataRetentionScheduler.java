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

    @Scheduled(cron = "${geo.retention.scheduler.cron:0 30 3 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void runDryRunSuite() {
        DataRetentionSchedulerLock.Lease lease = schedulerLock.tryAcquire();
        if (lease == null) {
            log.info("Skip data retention schedule because another instance holds the lock");
            return;
        }
        try (lease) {
            runPriorityRetention(lease);
        }
    }

    private void runPriorityRetention(DataRetentionSchedulerLock.Lease lease) {
        DataRetentionProperties.Scheduler scheduler = properties.getScheduler();
        int limit = Math.max(1, scheduler.getLimitPerDomain());
        int maxBatches = Math.max(1, scheduler.getMaxBatchesPerRun());
        boolean execute = scheduler.isExecuteEnabled();
        run("website_published_content",
                () -> runWebsitePublishedCleanup(limit, maxBatches, execute, lease));
        run("article_body_archive", () -> runArticleArchive(limit, maxBatches, execute, lease));
        run("article_body_purge", () -> runArticlePurge(limit, maxBatches, execute, lease));
        run("poll_results", () -> runPollPurge(Math.min(limit, 20), maxBatches, execute, lease));
    }

    private void runWebsitePublishedCleanup(int limit,
                                            int maxBatches,
                                            boolean execute,
                                            DataRetentionSchedulerLock.Lease lease) {
        Long cursor = null;
        for (int batch = 0; batch < maxBatches; batch++) {
            lease.ensureHeld();
            WebsitePublishedContentCleanupService.CleanupBatchResult response =
                    websitePublishedContentCleanupService.runScheduled(
                            properties.getScheduler().getWebsitePublishedRetentionHours(),
                            limit,
                            cursor,
                            !execute,
                            "scheduled website published hot-data cleanup"
                    );
            if (!response.hasMore() || response.nextCursorArticleId() == null) {
                break;
            }
            cursor = response.nextCursorArticleId();
        }
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
        for (int batch = 0; batch < maxBatches; batch++) {
            lease.ensureHeld();
            PollRetentionDryRunResponse response = pollRetentionDryRunService.runScheduled(
                    request, !execute, properties.getScheduler().getOperatorUserId());
            if (!Boolean.TRUE.equals(response.getHasMore())
                    || response.getNextCursorBatchDate() == null
                    || response.getNextCursorProjectId() == null
                    || response.getNextCursorQuestionTier() == null) {
                break;
            }
            request.setCursorBatchDate(response.getNextCursorBatchDate());
            request.setCursorProjectId(response.getNextCursorProjectId());
            request.setCursorQuestionTier(response.getNextCursorQuestionTier());
        }
    }

    private void run(String domain, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            log.warn("Data retention scheduled domain failed, domain={}, error={}", domain, ex.getMessage(), ex);
        }
    }
}
