package com.huanjing.geo.common.llm.monitoring;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.llm.alert.LlmCapacityAlertProperties;
import com.huanjing.geo.common.llm.measurement.LlmCallObservationMapper;
import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetric;
import com.huanjing.geo.common.llm.measurement.LlmCapacityMinuteMetricMapper;
import com.huanjing.geo.common.llm.measurement.LlmPlatformLimitRatioRow;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.dto.PollPlatformSliceProgressRow;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmCapacityQueryService {
    public static final Set<String> LIMIT_CATEGORIES = Set.of("PERMIT_BUSY", "INTERNAL_RATE_LIMITED", "PLATFORM_429");
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final LlmCapacityAlertProperties alertProperties;
    private final LlmCapacityMinuteMetricMapper minuteMetricMapper;
    private final LlmCallObservationMapper observationMapper;
    private final PollBatchShardMapper pollBatchShardMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchProperties dispatchProperties;

    public int scanWindowMinutes() {
        int activeWindow = alertProperties.getHunyuan().getActivePeakSustainedMinutes() + 1;
        int progressWindow = alertProperties.getHunyuan().getProgressLagSustainedMinutes() + 1;
        return Math.max(alertProperties.getWindowMinutes(), Math.max(activeWindow, progressWindow));
    }

    public List<LlmCapacityMinuteMetric> loadRecentMetrics(LocalDateTime since) {
        return minuteMetricMapper.selectList(new LambdaQueryWrapper<LlmCapacityMinuteMetric>()
                .ge(LlmCapacityMinuteMetric::getBucketMinute, since)
                .orderByAsc(LlmCapacityMinuteMetric::getBucketMinute));
    }

    public boolean hasSustainedPeak(List<LlmCapacityMinuteMetric> metrics,
                                    Function<LlmCapacityMinuteMetric, String> dimension,
                                    Function<LlmCapacityMinuteMetric, Long> value,
                                    String expectedDimension,
                                    long threshold,
                                    int sustainedMinutes) {
        if (threshold <= 0 || sustainedMinutes <= 0) {
            return false;
        }
        Map<LocalDateTime, Long> peakByMinute = metrics.stream()
                .filter(metric -> expectedDimension.equals(dimension.apply(metric)))
                .collect(Collectors.groupingBy(
                        metric -> truncateMinute(metric.getBucketMinute()),
                        LinkedHashMap::new,
                        Collectors.mapping(value, Collectors.collectingAndThen(Collectors.toList(),
                                values -> values.stream().mapToLong(this::safe).max().orElse(0L)))
                ));
        List<LocalDateTime> buckets = new ArrayList<>(peakByMinute.keySet());
        buckets.sort(Comparator.reverseOrder());
        if (buckets.size() < sustainedMinutes) {
            return false;
        }
        for (int i = 0; i < sustainedMinutes; i++) {
            if (peakByMinute.getOrDefault(buckets.get(i), 0L) < threshold) {
                return false;
            }
        }
        return true;
    }

    public PlatformLimitRatioSnapshot platformLimitRatio(LocalDateTime since, List<String> platformCodes) {
        List<String> normalizedCodes = normalizeCodes(platformCodes);
        if (normalizedCodes.isEmpty()) {
            return new PlatformLimitRatioSnapshot(List.of(), 0L, 0L, 0.0D);
        }
        List<LlmPlatformLimitRatioRow> rows = observationMapper.aggregateLimitRatio(since, normalizedCodes);
        long total = rows.stream().mapToLong(row -> safe(row.getTotalCount())).sum();
        long limited = rows.stream().mapToLong(row -> safe(row.getLimitedCount())).sum();
        double ratio = total <= 0 ? 0.0D : (double) limited / (double) total;
        return new PlatformLimitRatioSnapshot(rows, total, limited, ratio);
    }

    public PollSliceProgressSnapshot platformSliceProgress(LocalDate batchDate,
                                                           String questionTier,
                                                           List<String> platformCodes,
                                                           LocalDateTime now) {
        List<String> normalizedCodes = normalizeCodes(platformCodes);
        if (batchDate == null || !StringUtils.hasText(questionTier) || normalizedCodes.isEmpty()) {
            return PollSliceProgressSnapshot.empty(batchDate, questionTier, normalizedCodes, now, hunyuanWindowMinutes());
        }
        List<PollPlatformSliceProgressRow> rows = pollBatchShardMapper.aggregatePlatformSliceProgress(
                batchDate,
                questionTier,
                normalizedCodes
        );
        long expected = rows.stream().mapToLong(row -> safe(row.getExpectedCount())).sum();
        long completed = rows.stream().mapToLong(row -> safe(row.getCompletedCount())).sum();
        long failed = rows.stream().mapToLong(row -> safe(row.getFailedCount())).sum();
        long resourceWait = rows.stream().mapToLong(row -> safe(row.getResourceWaitCount())).sum();
        int windowMinutes = hunyuanWindowMinutes();
        LocalDateTime sliceStart = batchDate.atTime(LocalTime.MIN).plusMinutes(alertProperties.getHunyuan().getSliceStartMinuteOfDay());
        double actual = expected <= 0 ? 0.0D : (double) completed / (double) expected;
        double expectedProgress = expectedProgress(sliceStart, windowMinutes, now);
        double lag = expectedProgress - actual;
        return new PollSliceProgressSnapshot(
                batchDate,
                questionTier,
                normalizedCodes,
                rows,
                expected,
                completed,
                failed,
                resourceWait,
                actual,
                expectedProgress,
                lag,
                windowMinutes,
                sliceStart,
                now
        );
    }

    public double expectedProgress(LocalDateTime sliceStart, int windowMinutes, LocalDateTime now) {
        if (sliceStart == null || now == null || windowMinutes <= 0) {
            return 0.0D;
        }
        long elapsedMinutes = Math.max(0L, Duration.between(sliceStart, now).toMinutes());
        return Math.min(1.0D, (double) elapsedMinutes / (double) windowMinutes);
    }

    public long capacityRetryExhaustedCount(LocalDateTime since) {
        Long exhaustedCount = dispatchTaskMapper.selectCount(new LambdaQueryWrapper<com.huanjing.geo.module.dispatch.entity.DispatchTask>()
                .eq(com.huanjing.geo.module.dispatch.entity.DispatchTask::getStatus, DispatchTaskStatus.DEAD_LETTER.value())
                .like(com.huanjing.geo.module.dispatch.entity.DispatchTask::getLastError, "CAPACITY_RETRY_EXHAUSTED")
                .ge(com.huanjing.geo.module.dispatch.entity.DispatchTask::getUpdatedAt, since));
        return safe(exhaustedCount);
    }

    public List<String> hunyuanPlatformCodes() {
        String configured = alertProperties.getHunyuan().getPlatformCodes();
        if (!StringUtils.hasText(configured)) {
            return List.of("hunyuan", "yuanbao");
        }
        return Arrays.stream(configured.split("[,，;；\\s]+"))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    public int hunyuanWindowMinutes() {
        int fallback = Math.max(1, dispatchProperties.getStagger().getMaxDelayMinutes());
        return hunyuanPlatformCodes().stream()
                .map(code -> dispatchProperties.getStagger().getPlatforms().get(code))
                .filter(override -> override != null && override.getMaxDelayMinutes() != null)
                .mapToInt(DispatchProperties.PlatformOverride::getMaxDelayMinutes)
                .max()
                .orElse(fallback);
    }

    public LocalDateTime truncateMinute(LocalDateTime time) {
        if (time == null) {
            return LocalDateTime.MIN;
        }
        return time.truncatedTo(ChronoUnit.MINUTES);
    }

    public String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    public long safe(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private List<String> normalizeCodes(List<String> platformCodes) {
        if (platformCodes == null || platformCodes.isEmpty()) {
            return List.of();
        }
        return platformCodes.stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public record PlatformLimitRatioSnapshot(
            List<LlmPlatformLimitRatioRow> rows,
            long totalCount,
            long limitedCount,
            double ratio
    ) {
    }

    public record PollSliceProgressSnapshot(
            LocalDate batchDate,
            String questionTier,
            List<String> platformCodes,
            List<PollPlatformSliceProgressRow> rows,
            long expectedCount,
            long completedCount,
            long failedCount,
            long resourceWaitCount,
            double actualProgress,
            double expectedProgress,
            double lag,
            int windowMinutes,
            LocalDateTime sliceStart,
            LocalDateTime observedAt
    ) {
        static PollSliceProgressSnapshot empty(LocalDate batchDate,
                                               String questionTier,
                                               List<String> platformCodes,
                                               LocalDateTime now,
                                               int windowMinutes) {
            return new PollSliceProgressSnapshot(
                    batchDate,
                    questionTier,
                    platformCodes == null ? List.of() : platformCodes,
                    List.of(),
                    0L,
                    0L,
                    0L,
                    0L,
                    0.0D,
                    0.0D,
                    0.0D,
                    windowMinutes,
                    null,
                    now
            );
        }
    }
}
