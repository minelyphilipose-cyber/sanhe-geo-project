package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.ProjectSelfMediaScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media.auto-schedule.job", name = "enabled", havingValue = "true")
public class ProjectSelfMediaAutoScheduleJob {
    private final ProjectSelfMediaScheduleService projectSelfMediaScheduleService;
    @Value("${geo.self-media.auto-schedule.job.limit:50}")
    private int limit;
    @Value("${geo.self-media.auto-schedule.job.progress-limit:20}")
    private int progressLimit;

    @Scheduled(cron = "${geo.self-media.auto-schedule.job.cron:0 15 2 1 * *}")
    public void createMonthlySchedules() {
        String targetMonth = YearMonth.now().toString();
        try {
            int processed = projectSelfMediaScheduleService.createDueEnabledProjects(targetMonth, limit);
            log.info("project self-media auto schedule job completed targetMonth={} processed={}", targetMonth, processed);
        } catch (Exception ex) {
            log.warn("project self-media auto schedule job failed targetMonth={} error={}", targetMonth, ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${geo.self-media.auto-schedule.job.progress-poll-ms:60000}")
    public void progressProcessingSchedules() {
        try {
            int processed = projectSelfMediaScheduleService.progressProcessingBatches(progressLimit);
            if (processed > 0) {
                log.info("project self-media auto schedule progress completed processed={}", processed);
            }
        } catch (Exception ex) {
            log.warn("project self-media auto schedule progress failed error={}", ex.getMessage());
        }
    }
}
