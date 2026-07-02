package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.ProjectSelfMediaScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.YearMonth;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media.auto-schedule.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProjectSelfMediaAutoScheduleJob {
    private final ProjectSelfMediaScheduleService projectSelfMediaScheduleService;
    @Value("${geo.self-media.auto-schedule.job.limit:50}")
    private int limit;
    @Value("${geo.self-media.auto-schedule.job.progress-limit:20}")
    private int progressLimit;
    @Value("${geo.self-media.auto-schedule.job.compensation-limit:20}")
    private int compensationLimit;
    @Value("${geo.self-media.auto-schedule.job.pre-schedule-window-hours:96}")
    private int preScheduleWindowHours;
    @Value("${geo.self-media.auto-schedule.job.month-start-window-hours:40}")
    private int monthStartWindowHours;
    @Value("${geo.self-media.auto-schedule.job.plan-jitter-minutes:10}")
    private int planJitterMinutes;
    @Value("${geo.self-media.auto-schedule.job.plan-retry-max-count:3}")
    private int planRetryMaxCount;
    @Value("${geo.self-media.auto-schedule.job.plan-running-timeout-minutes:120}")
    private int planRunningTimeoutMinutes;
    @Value("${geo.self-media.auto-schedule.job.timezone:Asia/Shanghai}")
    private String timezone;

    @Scheduled(
            cron = "${geo.self-media.auto-schedule.job.pre-schedule-cron:0 0 2 26 * *}",
            zone = "${geo.self-media.auto-schedule.job.timezone:Asia/Shanghai}"
    )
    public void createNextMonthPreSchedulePlans() {
        ZoneId zone = ZoneId.of(timezone);
        String targetMonth = YearMonth.now(zone).plusMonths(1).toString();
        try {
            int created = projectSelfMediaScheduleService.createDueEnabledProjectPlans(
                    targetMonth,
                    "pre_schedule",
                    LocalDateTime.now(zone).withNano(0),
                    preScheduleWindowHours,
                    planJitterMinutes,
                    limit
            );
            log.info("project self-media next-month pre-schedule plans created targetMonth={} created={}", targetMonth, created);
        } catch (Exception ex) {
            log.warn("project self-media next-month pre-schedule planning failed targetMonth={} error={}", targetMonth, ex.getMessage());
        }
    }

    @Scheduled(
            cron = "${geo.self-media.auto-schedule.job.cron:0 15 2 1 * *}",
            zone = "${geo.self-media.auto-schedule.job.timezone:Asia/Shanghai}"
    )
    public void createCurrentMonthCompensationPlans() {
        ZoneId zone = ZoneId.of(timezone);
        String targetMonth = YearMonth.now(zone).toString();
        try {
            int created = projectSelfMediaScheduleService.createDueEnabledProjectPlans(
                    targetMonth,
                    "compensation",
                    LocalDateTime.now(zone).withNano(0),
                    monthStartWindowHours,
                    planJitterMinutes,
                    limit
            );
            log.info("project self-media current-month compensation plans created targetMonth={} created={}", targetMonth, created);
        } catch (Exception ex) {
            log.warn("project self-media current-month compensation planning failed targetMonth={} error={}", targetMonth, ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${geo.self-media.auto-schedule.job.plan-poll-ms:60000}")
    public void processDueProjectSchedulePlans() {
        try {
            int processed = projectSelfMediaScheduleService.processDueProjectSchedulePlans(
                    progressLimit,
                    planRetryMaxCount,
                    planRunningTimeoutMinutes
            );
            if (processed > 0) {
                log.info("project self-media staggered schedule plans processed={}", processed);
            }
        } catch (Exception ex) {
            log.warn("project self-media staggered schedule plan processing failed error={}", ex.getMessage());
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

    @Scheduled(fixedDelayString = "${geo.self-media.auto-schedule.job.compensation-poll-ms:300000}")
    public void compensateRetryableAbnormalSchedules() {
        try {
            int processed = projectSelfMediaScheduleService.compensateRetryableAbnormalSchedules(compensationLimit);
            if (processed > 0) {
                log.info("project self-media auto schedule compensation completed processed={}", processed);
            }
        } catch (Exception ex) {
            log.warn("project self-media auto schedule compensation failed error={}", ex.getMessage());
        }
    }
}
