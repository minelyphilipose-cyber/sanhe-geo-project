package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfMediaPublishScheduleWorker {
    private static final int DEFAULT_LOCK_MINUTES = 30;
    private static final NoopSelfMediaPublishScheduleAdapter NOOP_ADAPTER = new NoopSelfMediaPublishScheduleAdapter();

    private final SelfMediaPublishScheduleService scheduleService;
    private final List<SelfMediaPublishScheduleAdapter> adapters;

    public int runBatch(int limit) {
        int max = Math.max(limit, 1);
        int processed = 0;
        while (processed < max && runOnce()) {
            processed++;
        }
        return processed;
    }

    public boolean runOnce() {
        SelfMediaPublishScheduleVO publishCheck = scheduleService.claimNext(
                SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK,
                DEFAULT_LOCK_MINUTES
        );
        if (publishCheck != null) {
            processPublishResultCheck(publishCheck);
            return true;
        }

        SelfMediaPublishScheduleVO scheduleExecution = scheduleService.claimNext(
                SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION,
                DEFAULT_LOCK_MINUTES
        );
        if (scheduleExecution != null) {
            processScheduleExecution(scheduleExecution);
            return true;
        }
        return false;
    }

    private void processScheduleExecution(SelfMediaPublishScheduleVO schedule) {
        try {
            ScheduleExecutionResult result = adapterFor(schedule.getPlatform()).schedule(schedule);
            if (result.success()) {
                scheduleService.markClaimedFilledVerified(schedule.getId(), result.diagnosticsJson());
                scheduleService.markClaimedScheduling(schedule.getId(), result.diagnosticsJson());
                scheduleService.markClaimedScheduled(
                        schedule.getId(),
                        result.platformScheduleId(),
                        result.diagnosticsJson()
                );
                log.info("self media schedule execution completed scheduleId={} platform={}",
                        schedule.getId(), schedule.getPlatform());
                return;
            }
            scheduleService.markClaimFailed(
                    schedule.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING,
                    fallback(result.failureCode(), "SCHEDULE_ADAPTER_FAILED"),
                    result.failureMessage(),
                    result.diagnosticsJson(),
                    result.nextAttemptAt()
            );
        } catch (Exception ex) {
            log.warn("self media schedule execution failed scheduleId={} platform={} error={}",
                    schedule.getId(), schedule.getPlatform(), safeMessage(ex));
            scheduleService.markClaimFailed(
                    schedule.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING,
                    "SCHEDULE_ADAPTER_EXCEPTION",
                    safeMessage(ex),
                    null,
                    null
            );
        }
    }

    private void processPublishResultCheck(SelfMediaPublishScheduleVO schedule) {
        try {
            PublishCheckResult result = adapterFor(schedule.getPlatform()).checkPublishResult(schedule);
            switch (result.outcome()) {
                case PUBLISHED -> scheduleService.markClaimedPublishedConfirmed(
                        schedule.getId(),
                        result.platformPublishedUrl(),
                        result.diagnosticsJson()
                );
                case FAILED -> scheduleService.markClaimedPublishFailed(
                        schedule.getId(),
                        fallback(result.failureCode(), "PUBLISH_RESULT_CHECK_FAILED"),
                        result.failureMessage(),
                        result.diagnosticsJson()
                );
                case RETRY -> scheduleService.markClaimFailed(
                        schedule.getId(),
                        SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
                        fallback(result.failureCode(), "PUBLISH_RESULT_CHECK_RETRY"),
                        result.failureMessage(),
                        result.diagnosticsJson(),
                        result.nextAttemptAt()
                );
                case UNKNOWN -> scheduleService.markClaimedPublishCheckUnknown(schedule.getId(), result.diagnosticsJson());
            }
        } catch (Exception ex) {
            log.warn("self media publish result check failed scheduleId={} platform={} error={}",
                    schedule.getId(), schedule.getPlatform(), safeMessage(ex));
            scheduleService.markClaimFailed(
                    schedule.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
                    "PUBLISH_RESULT_CHECK_EXCEPTION",
                    safeMessage(ex),
                    null,
                    null
            );
        }
    }

    private SelfMediaPublishScheduleAdapter adapterFor(String platform) {
        for (SelfMediaPublishScheduleAdapter adapter : adapters) {
            if (adapter.supports(platform)) {
                return adapter;
            }
        }
        return NOOP_ADAPTER;
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }
}
