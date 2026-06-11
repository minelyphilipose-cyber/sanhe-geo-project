package com.huanjing.geo.module.retention.schedule;

import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunRequest;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dto.ObjectStorageRetentionDryRunRequest;
import com.huanjing.geo.module.retention.service.DataRetentionSlimDryRunService;
import com.huanjing.geo.module.retention.service.ObjectStorageRetentionDryRunService;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
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
    private final DataRetentionSlimDryRunService slimDryRunService;
    private final ArticleRetentionDryRunService articleRetentionDryRunService;
    private final PollRetentionDryRunService pollRetentionDryRunService;
    private final ObjectStorageRetentionDryRunService objectStorageRetentionDryRunService;

    @Scheduled(cron = "${geo.retention.scheduler.cron:0 30 3 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void runDryRunSuite() {
        DataRetentionProperties.Scheduler scheduler = properties.getScheduler();
        int limit = Math.max(1, scheduler.getLimitPerDomain());
        run("slim_payload", () -> {
            DataRetentionSlimDryRunRequest request = new DataRetentionSlimDryRunRequest();
            request.setDomain("all");
            request.setLimitPerDomain(limit);
            slimDryRunService.dryRun(request);
        });
        run("article_body_archive", () -> {
            ArticleArchiveDryRunRequest request = new ArticleArchiveDryRunRequest();
            request.setLimit(limit);
            articleRetentionDryRunService.dryRunArchive(request);
        });
        run("poll_results", () -> {
            PollRetentionDryRunRequest request = new PollRetentionDryRunRequest();
            request.setLimit(limit);
            request.setHotRetentionDays(scheduler.getPollHotRetentionDays());
            pollRetentionDryRunService.dryRun(request);
        });
        run("object_storage_orphan", () -> {
            ObjectStorageRetentionDryRunRequest request = new ObjectStorageRetentionDryRunRequest();
            request.setLimitPerPrefix(limit);
            request.setSafetyAgeHours(scheduler.getObjectSafetyAgeHours());
            objectStorageRetentionDryRunService.dryRun(request);
        });
    }

    private void run(String domain, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            log.warn("Data retention dry-run domain failed, domain={}, error={}", domain, ex.getMessage(), ex);
        }
    }
}
