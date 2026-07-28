package com.huanjing.geo.module.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.retention.report-freeze", name = "enabled", havingValue = "true")
public class ReportPeriodFreezeJob {

    private final ReportPeriodFreezeService reportPeriodFreezeService;

    @Scheduled(cron = "${geo.retention.report-freeze.cron:0 40 2 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void freezePreviousQuarter() {
        int frozen = reportPeriodFreezeService.freezePreviousQuarterCandidates(100);
        if (frozen > 0) {
            log.info("Report period freeze job completed: frozen={}", frozen);
        }
    }
}
