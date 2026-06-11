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
}
