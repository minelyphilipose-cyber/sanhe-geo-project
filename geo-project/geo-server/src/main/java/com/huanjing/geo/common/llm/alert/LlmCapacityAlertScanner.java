package com.huanjing.geo.common.llm.alert;

import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetric;
import com.huanjing.geo.common.llm.monitoring.LlmCapacityQueryService;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmCapacityAlertScanner {

    private static final String SOURCE = "llm_capacity_alert";
    private static final String RECIPIENT_ROLE = "super_admin";
    private static final String HUNYUAN_ACTIVE_DEDUPE = "llm_capacity:hunyuan:active_peak";
    private static final String HUNYUAN_PROGRESS_DEDUPE = "llm_capacity:hunyuan:slice_progress";
    private static final String HUNYUAN_EXHAUSTED_DEDUPE = "llm_capacity:hunyuan:capacity_retry_exhausted";

    private final LlmCapacityAlertProperties alertProperties;
    private final SystemAlertService systemAlertService;
    private final LlmPoolProperties poolProperties;
    private final LlmCapacityQueryService capacityQueryService;
    private final Map<String, LocalDateTime> pendingSinceByDedupeKey = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${geo.llm.capacity-alert.scan-fixed-delay-ms:120000}")
    public void scan() {
        if (!alertProperties.isEnabled()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(LlmCapacityQueryService.ZONE);
            LocalDateTime since = now.minusMinutes(capacityQueryService.scanWindowMinutes());
            List<LlmCapacityMinuteMetric> metrics = capacityQueryService.loadRecentMetrics(since);
            evaluateGlobalAndFeatureCapacity(metrics, now);
            evaluateHunyuanActivePeak(metrics, now);
            evaluateHunyuanRateLimitRatio(since, now);
            evaluateHunyuanSliceProgress(now);
            evaluateCapacityRetryExhausted(since, now);
        } catch (Exception ex) {
            log.warn("LLM capacity alert scan failed, reason={}", ex.getMessage(), ex);
        }
    }

    private void evaluateGlobalAndFeatureCapacity(List<LlmCapacityMinuteMetric> metrics, LocalDateTime now) {
        int globalThreshold = (int) Math.ceil(poolProperties.getGlobalConcurrency() * 0.9D);
        boolean globalBusy = capacityQueryService.hasSustainedPeak(
                metrics,
                metric -> "all",
                metric -> capacityQueryService.safe(metric.getGlobalActivePeak()),
                "all",
                globalThreshold,
                alertProperties.getHunyuan().getActivePeakSustainedMinutes()
        );
        String globalKey = "llm_capacity:global:active_peak";
        if (globalBusy) {
            upsertAlert(
                    globalKey,
                    "LLM_CAPACITY_GLOBAL_ACTIVE_PEAK",
                    "warn",
                    "LLM 全局 permit 使用持续接近上限",
                    Map.of(
                            "category", "operations",
                            "globalConcurrency", poolProperties.getGlobalConcurrency(),
                            "threshold", globalThreshold,
                            "windowMinutes", capacityQueryService.scanWindowMinutes(),
                            "observedAt", now.toString()
                    )
            );
        } else {
            resolve(globalKey);
        }

        if (poolProperties.getFeatureConcurrency() == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : poolProperties.getFeatureConcurrency().entrySet()) {
            String feature = capacityQueryService.normalize(entry.getKey());
            int limit = entry.getValue() == null ? 0 : entry.getValue();
            if (!StringUtils.hasText(feature) || limit <= 0) {
                continue;
            }
            int threshold = (int) Math.ceil(limit * 0.9D);
            boolean featureBusy = capacityQueryService.hasSustainedPeak(
                    metrics,
                    metric -> capacityQueryService.normalize(metric.getFeature()),
                    metric -> capacityQueryService.safe(metric.getFeatureActivePeak()),
                    feature,
                    threshold,
                    alertProperties.getHunyuan().getActivePeakSustainedMinutes()
            );
            String dedupeKey = "llm_capacity:feature:" + feature + ":active_peak";
            if (featureBusy) {
                upsertAlert(
                        dedupeKey,
                        "LLM_CAPACITY_FEATURE_ACTIVE_PEAK",
                        "warn",
                        "LLM feature「" + feature + "」permit 使用持续接近上限",
                        Map.of(
                                "category", "operations",
                                "feature", feature,
                                "featureConcurrency", limit,
                                "threshold", threshold,
                                "windowMinutes", capacityQueryService.scanWindowMinutes(),
                                "observedAt", now.toString()
                        )
                );
            } else {
                resolve(dedupeKey);
            }
        }
    }

    private void evaluateHunyuanActivePeak(List<LlmCapacityMinuteMetric> metrics, LocalDateTime now) {
        List<String> platformCodes = capacityQueryService.hunyuanPlatformCodes();
        boolean busy = platformCodes.stream().anyMatch(platform -> capacityQueryService.hasSustainedPeak(
                metrics,
                metric -> capacityQueryService.normalize(metric.getPlatformCode()),
                metric -> capacityQueryService.safe(metric.getPlatformActivePeak()),
                platform,
                alertProperties.getHunyuan().getActivePeakThreshold(),
                alertProperties.getHunyuan().getActivePeakSustainedMinutes()
        ));
        if (busy) {
            upsertAlert(
                    HUNYUAN_ACTIVE_DEDUPE,
                    "LLM_CAPACITY_HUNYUAN_ACTIVE_PEAK",
                    "critical",
                    "混元/元宝平台 active peak 持续顶格",
                    Map.of(
                            "category", "decision",
                            "platformCodes", platformCodes,
                            "threshold", alertProperties.getHunyuan().getActivePeakThreshold(),
                            "sustainedMinutes", alertProperties.getHunyuan().getActivePeakSustainedMinutes(),
                            "observedAt", now.toString()
                    )
            );
        } else {
            resolve(HUNYUAN_ACTIVE_DEDUPE);
        }
    }

    private void evaluateHunyuanRateLimitRatio(LocalDateTime since, LocalDateTime now) {
        List<String> platformCodes = capacityQueryService.hunyuanPlatformCodes();
        if (platformCodes.isEmpty()) {
            return;
        }
        LlmCapacityQueryService.PlatformLimitRatioSnapshot snapshot = capacityQueryService.platformLimitRatio(since, platformCodes);
        long total = snapshot.totalCount();
        long limited = snapshot.limitedCount();
        double ratio = snapshot.ratio();
        String dedupeKey = "llm_capacity:hunyuan:rate_limit_ratio";
        if (total >= alertProperties.getMinCallsForRatio()
                && ratio >= alertProperties.getRateLimitRatioThreshold()) {
            upsertAlert(
                    dedupeKey,
                    "LLM_CAPACITY_HUNYUAN_RATE_LIMIT_RATIO",
                    "critical",
                    "混元/元宝平台持续限流比例超过阈值",
                    Map.of(
                            "category", "decision",
                            "platformCodes", platformCodes,
                            "limitedCount", limited,
                            "totalCount", total,
                            "ratio", ratio,
                            "threshold", alertProperties.getRateLimitRatioThreshold(),
                            "windowStart", since.toString(),
                            "windowEnd", now.toString(),
                            "limitCategories", LlmCapacityQueryService.LIMIT_CATEGORIES
                    )
            );
        } else {
            resolve(dedupeKey);
        }
    }

    private void evaluateHunyuanSliceProgress(LocalDateTime now) {
        List<String> platformCodes = capacityQueryService.hunyuanPlatformCodes();
        if (platformCodes.isEmpty()) {
            return;
        }
        LocalDate batchDate = now.toLocalDate();
        LlmCapacityQueryService.PollSliceProgressSnapshot snapshot = capacityQueryService.platformSliceProgress(batchDate, "A", platformCodes, now);
        long expected = snapshot.expectedCount();
        if (expected <= 0) {
            resolve(HUNYUAN_PROGRESS_DEDUPE);
            pendingSinceByDedupeKey.remove(HUNYUAN_PROGRESS_DEDUPE);
            return;
        }
        long completed = snapshot.completedCount();
        long failed = snapshot.failedCount();
        long resourceWait = snapshot.resourceWaitCount();
        double actual = snapshot.actualProgress();
        int windowMinutes = snapshot.windowMinutes();
        double expectedProgress = snapshot.expectedProgress();
        double lag = snapshot.lag();
        LocalDateTime finalDeadline = snapshot.sliceStart().plusMinutes(windowMinutes + alertProperties.getHunyuan().getCompletionGraceMinutes());
        boolean finalCritical = !now.isBefore(finalDeadline)
                && actual < alertProperties.getHunyuan().getFinalCompletionCriticalRatio();
        boolean lagging = lag >= alertProperties.getHunyuan().getProgressLagWarnRatio();
        if (finalCritical) {
            pendingSinceByDedupeKey.remove(HUNYUAN_PROGRESS_DEDUPE);
            upsertHunyuanProgressAlert("critical", "混元/元宝当日切片窗口结束后完成度低于红线",
                    expected, completed, failed, resourceWait, actual, expectedProgress, lag, windowMinutes, now);
            return;
        }
        if (lagging && conditionSustained(HUNYUAN_PROGRESS_DEDUPE, now,
                alertProperties.getHunyuan().getProgressLagSustainedMinutes())) {
            upsertHunyuanProgressAlert("warn", "混元/元宝当日切片进度持续落后",
                    expected, completed, failed, resourceWait, actual, expectedProgress, lag, windowMinutes, now);
            return;
        }
        if (!lagging) {
            pendingSinceByDedupeKey.remove(HUNYUAN_PROGRESS_DEDUPE);
            resolve(HUNYUAN_PROGRESS_DEDUPE);
        }
    }

    private void upsertHunyuanProgressAlert(String severity,
                                           String message,
                                           long expected,
                                           long completed,
                                           long failed,
                                           long resourceWait,
                                           double actual,
                                           double expectedProgress,
                                           double lag,
                                           int windowMinutes,
                                           LocalDateTime now) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("category", "decision");
        context.put("platformCodes", capacityQueryService.hunyuanPlatformCodes());
        context.put("expectedCount", expected);
        context.put("completedCount", completed);
        context.put("failedCount", failed);
        context.put("resourceWaitCount", resourceWait);
        context.put("actualProgress", actual);
        context.put("expectedProgress", expectedProgress);
        context.put("lag", lag);
        context.put("windowMinutes", windowMinutes);
        context.put("observedAt", now.toString());
        upsertAlert(
                HUNYUAN_PROGRESS_DEDUPE,
                "LLM_CAPACITY_HUNYUAN_SLICE_PROGRESS",
                severity,
                message,
                context
        );
    }

    private void evaluateCapacityRetryExhausted(LocalDateTime since, LocalDateTime now) {
        long exhaustedCount = capacityQueryService.capacityRetryExhaustedCount(since);
        if (exhaustedCount <= 0) {
            return;
        }
        upsertAlert(
                HUNYUAN_EXHAUSTED_DEDUPE,
                "LLM_CAPACITY_RETRY_EXHAUSTED",
                "critical",
                "出现 LLM 容量重排预算耗尽任务",
                Map.of(
                        "category", "decision",
                        "exhaustedCount", exhaustedCount,
                        "windowStart", since.toString(),
                        "windowEnd", now.toString()
                )
        );
    }

    private boolean conditionSustained(String dedupeKey, LocalDateTime now, int sustainedMinutes) {
        LocalDateTime firstSeen = pendingSinceByDedupeKey.computeIfAbsent(dedupeKey, ignored -> now);
        return !firstSeen.plusMinutes(sustainedMinutes).isAfter(now);
    }

    private void upsertAlert(String dedupeKey,
                             String alertType,
                             String severity,
                             String message,
                             Map<String, Object> context) {
        systemAlertService.createOrRefreshRecipientAlert(
                alertType,
                severity,
                SOURCE,
                message,
                context,
                null,
                StringUtils.hasText(alertProperties.getRecipientRole())
                        ? alertProperties.getRecipientRole()
                        : RECIPIENT_ROLE,
                dedupeKey
        );
    }

    private void resolve(String dedupeKey) {
        systemAlertService.resolveOpenByDedupeKey(dedupeKey, null);
    }
}
