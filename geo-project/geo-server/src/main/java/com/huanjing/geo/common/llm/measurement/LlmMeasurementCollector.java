package com.huanjing.geo.common.llm.measurement;

import com.huanjing.geo.common.llm.LlmGovernanceStack;
import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.common.llm.LlmWaitSemantics;
import com.huanjing.geo.common.llm.LlmCapacityView;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class LlmMeasurementCollector {
    private final LlmCallObservationMapper observationMapper;
    private final LlmCapacityMinuteMetricMapper minuteMetricMapper;
    private final ObjectProvider<LlmCapacityView> capacityViewProvider;
    private final ObjectProvider<LlmPoolProperties> poolPropertiesProvider;
    private final ObjectProvider<AiPlatformConfigMapper> platformConfigMapperProvider;
    private final ArrayBlockingQueue<LlmMeasurementEvent> observationQueue;
    private final ConcurrentHashMap<MinuteKey, MinuteAccumulator> minuteBuckets = new ConcurrentHashMap<>();
    private final AtomicLong droppedObservations = new AtomicLong();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final int flushBatchSize;
    private final long platformCacheRefreshMs;
    private volatile List<String> cachedPlatformCodes = List.of();
    private volatile long platformCacheLoadedAtMs;

    public LlmMeasurementCollector(LlmCallObservationMapper observationMapper,
                                   LlmCapacityMinuteMetricMapper minuteMetricMapper,
                                   ObjectProvider<LlmCapacityView> capacityViewProvider,
                                   ObjectProvider<LlmPoolProperties> poolPropertiesProvider,
                                   ObjectProvider<AiPlatformConfigMapper> platformConfigMapperProvider,
                                   @Value("${geo.llm.measurement.observation-queue-capacity:10000}") int queueCapacity,
                                   @Value("${geo.llm.measurement.flush-batch-size:200}") int flushBatchSize,
                                   @Value("${geo.llm.measurement.platform-cache-refresh-ms:300000}") long platformCacheRefreshMs) {
        this.observationMapper = observationMapper;
        this.minuteMetricMapper = minuteMetricMapper;
        this.capacityViewProvider = capacityViewProvider;
        this.poolPropertiesProvider = poolPropertiesProvider;
        this.platformConfigMapperProvider = platformConfigMapperProvider;
        this.observationQueue = new ArrayBlockingQueue<>(Math.max(100, queueCapacity));
        this.flushBatchSize = Math.max(1, flushBatchSize);
        this.platformCacheRefreshMs = Math.max(10_000L, platformCacheRefreshMs);
    }

    public void recordObservation(LlmMeasurementEvent event) {
        if (event == null) {
            return;
        }
        try {
            if (!observationQueue.offer(event)) {
                long dropped = droppedObservations.incrementAndGet();
                if (dropped == 1 || dropped % 1000 == 0) {
                    log.warn("LLM measurement observation queue full, dropped={}", dropped);
                }
            }
            recordCapacitySignal(toCapacitySignal(event));
        } catch (Exception ex) {
            log.debug("failed to enqueue LLM measurement observation, reason={}", ex.getMessage());
        }
    }

    public void recordCapacitySignal(LlmCapacitySignal signal) {
        if (signal == null) {
            return;
        }
        try {
            LocalDateTime minute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            MinuteKey key = new MinuteKey(
                    safeRunId(signal.context().runId()),
                    minute,
                    safeDimension(signal.platformCode(), "unknown"),
                    safeDimension(signal.feature(), "generic"),
                    signal.governanceStack() == null ? LlmGovernanceStack.GATEWAY.name() : signal.governanceStack().name()
            );
            minuteBuckets.computeIfAbsent(key, ignored -> new MinuteAccumulator()).apply(signal);
        } catch (Exception ex) {
            log.debug("failed to record LLM capacity signal, reason={}", ex.getMessage());
        }
    }

    @Async("llmMeasurementExecutor")
    @Scheduled(fixedDelayString = "${geo.llm.measurement.flush-delay-ms:5000}")
    public void flush() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            flushObservations();
            flushMinuteMetrics();
        } finally {
            flushing.set(false);
        }
    }

    @Scheduled(fixedDelayString = "${geo.llm.measurement.capacity-sample-delay-ms:5000}")
    public void sampleCapacity() {
        try {
            LlmCapacityView capacityView = capacityViewProvider.getIfAvailable();
            if (capacityView == null) {
                return;
            }
            long globalActive = safe(capacityView.activeGlobalCount());
            long waiters = safe(capacityView.activeWaiterCount());
            sampleFeatures(capacityView, globalActive, waiters);
            samplePlatforms(capacityView, globalActive, waiters);
        } catch (Exception ex) {
            log.debug("failed to sample LLM capacity, reason={}", ex.getMessage());
        }
    }

    private void sampleFeatures(LlmCapacityView capacityView, long globalActive, long waiters) {
        LlmPoolProperties properties = poolPropertiesProvider.getIfAvailable();
        if (properties == null || properties.getFeatureConcurrency() == null) {
            return;
        }
        properties.getFeatureConcurrency().keySet().forEach(feature -> recordCapacitySignal(new LlmCapacitySignal(
                LlmCallMeasurementContext.empty(),
                feature,
                "all",
                LlmGovernanceStack.GATEWAY,
                null,
                globalActive,
                safe(capacityView.activeFeatureCount(feature)),
                0L,
                waiters,
                0L
        )));
    }

    private void samplePlatforms(LlmCapacityView capacityView, long globalActive, long waiters) {
        for (String platformCode : platformCodes()) {
            recordCapacitySignal(new LlmCapacitySignal(
                    LlmCallMeasurementContext.empty(),
                    "all",
                    platformCode,
                    LlmGovernanceStack.GATEWAY,
                    null,
                    globalActive,
                    0L,
                    safe(capacityView.activePlatformCount(platformCode)),
                    waiters,
                    0L
            ));
        }
    }

    private List<String> platformCodes() {
        long now = System.currentTimeMillis();
        List<String> local = cachedPlatformCodes;
        if (!local.isEmpty() && now - platformCacheLoadedAtMs < platformCacheRefreshMs) {
            return local;
        }
        AiPlatformConfigMapper mapper = platformConfigMapperProvider.getIfAvailable();
        if (mapper == null) {
            return local;
        }
        try {
            List<String> loaded = mapper.selectList(null).stream()
                    .map(AiPlatformConfig::getPlatformCode)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            cachedPlatformCodes = loaded;
            platformCacheLoadedAtMs = now;
            return loaded;
        } catch (Exception ex) {
            log.debug("failed to refresh LLM measurement platform cache, reason={}", ex.getMessage());
            return local;
        }
    }

    private void flushObservations() {
        List<LlmMeasurementEvent> drained = new ArrayList<>(flushBatchSize);
        observationQueue.drainTo(drained, flushBatchSize);
        for (LlmMeasurementEvent event : drained) {
            try {
                observationMapper.insert(toObservation(event));
            } catch (Exception ex) {
                log.warn("failed to persist LLM call observation, reason={}", ex.getMessage());
            }
        }
    }

    private void flushMinuteMetrics() {
        if (minuteBuckets.isEmpty()) {
            return;
        }
        List<MinuteKey> keys = List.copyOf(minuteBuckets.keySet());
        for (MinuteKey key : keys) {
            MinuteAccumulator accumulator = minuteBuckets.remove(key);
            if (accumulator == null) {
                continue;
            }
            try {
                minuteMetricMapper.upsert(accumulator.toMetric(key));
            } catch (Exception ex) {
                log.warn("failed to persist LLM capacity minute metric, reason={}", ex.getMessage());
            }
        }
    }

    private LlmCapacitySignal toCapacitySignal(LlmMeasurementEvent event) {
        return new LlmCapacitySignal(
                event.context(),
                event.feature(),
                event.platformCode(),
                event.governanceStack(),
                event.errorCategory(),
                0L,
                0L,
                0L,
                0L,
                0L
        );
    }

    private LlmCallObservation toObservation(LlmMeasurementEvent event) {
        LlmCallMeasurementContext context = event.context();
        LlmCallObservation observation = new LlmCallObservation();
        observation.setRunId(context.runId());
        observation.setCustomerId(context.customerId());
        observation.setProjectId(context.projectId());
        observation.setScope(context.scope() == null ? null : context.scope().name());
        observation.setNormalizedPromptHash(context.normalizedPromptHash());
        observation.setFeature(trim(event.feature(), 64));
        observation.setPlatformCode(trim(event.platformCode(), 64));
        observation.setPlatformName(trim(event.platformName(), 128));
        observation.setModelId(trim(event.modelId(), 128));
        observation.setModelName(trim(event.modelName(), 128));
        observation.setGovernanceStack(name(event.governanceStack()));
        observation.setRoutingStrategy(name(event.routingStrategy()));
        observation.setWaitSemantics(name(event.waitSemantics()));
        observation.setStatus(trim(event.status(), 32));
        observation.setErrorCategory(event.errorCategory() == null ? null : event.errorCategory().name());
        observation.setHttpStatusCode(event.httpStatusCode());
        observation.setProviderErrorCode(trim(event.providerErrorCode(), 128));
        observation.setRetryAfterMs(event.retryAfterMs());
        observation.setFailureKind(trim(event.failureKind(), 64));
        observation.setRequestCount(event.requestCount());
        observation.setWaitMs(event.waitMs());
        observation.setHttpMs(event.httpMs());
        observation.setTotalMs(event.totalMs());
        observation.setPromptTokens(event.promptTokens());
        observation.setCompletionTokens(event.completionTokens());
        Integer promptTokens = event.promptTokens();
        Integer completionTokens = event.completionTokens();
        observation.setTotalTokens(promptTokens == null && completionTokens == null
                ? null
                : (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens));
        observation.setOccurredAt(event.occurredAt());
        return observation;
    }

    private String safeRunId(String runId) {
        return StringUtils.hasText(runId) ? runId.trim() : "ad_hoc";
    }

    private String safeDimension(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private record MinuteKey(String runId,
                             LocalDateTime bucketMinute,
                             String platformCode,
                             String feature,
                             String governanceStack) {
    }

    private static final class MinuteAccumulator {
        private long globalActivePeak;
        private long featureActivePeak;
        private long platformActivePeak;
        private long permitBusyCount;
        private long permitWaiterPeak;
        private long internalRateLimitedCount;
        private long platform429Count;
        private long http5xxCount;
        private long timeoutCount;
        private long legacyRateLimitedCount;
        private long legacyConcurrencyWaiterPeak;

        synchronized void apply(LlmCapacitySignal signal) {
            globalActivePeak = Math.max(globalActivePeak, Math.max(0L, signal.globalActive()));
            featureActivePeak = Math.max(featureActivePeak, Math.max(0L, signal.featureActive()));
            platformActivePeak = Math.max(platformActivePeak, Math.max(0L, signal.platformActive()));
            permitWaiterPeak = Math.max(permitWaiterPeak, Math.max(0L, signal.permitWaiters()));
            legacyConcurrencyWaiterPeak = Math.max(legacyConcurrencyWaiterPeak, Math.max(0L, signal.legacyConcurrencyWaiters()));
            if (signal.errorCategory() == LlmErrorCategory.PERMIT_BUSY) {
                permitBusyCount++;
            } else if (signal.errorCategory() == LlmErrorCategory.INTERNAL_RATE_LIMITED) {
                internalRateLimitedCount++;
                if (signal.governanceStack() == LlmGovernanceStack.LEGACY_LIMITER) {
                    legacyRateLimitedCount++;
                }
            } else if (signal.errorCategory() == LlmErrorCategory.PLATFORM_429) {
                platform429Count++;
            } else if (signal.errorCategory() == LlmErrorCategory.HTTP_5XX) {
                http5xxCount++;
            } else if (signal.errorCategory() == LlmErrorCategory.TIMEOUT) {
                timeoutCount++;
            }
        }

        synchronized LlmCapacityMinuteMetric toMetric(MinuteKey key) {
            LlmCapacityMinuteMetric metric = new LlmCapacityMinuteMetric();
            metric.setRunId(key.runId());
            metric.setBucketMinute(key.bucketMinute());
            metric.setPlatformCode(key.platformCode());
            metric.setFeature(key.feature());
            metric.setGovernanceStack(key.governanceStack());
            metric.setGlobalActivePeak(globalActivePeak);
            metric.setFeatureActivePeak(featureActivePeak);
            metric.setPlatformActivePeak(platformActivePeak);
            metric.setPermitBusyCount(permitBusyCount);
            metric.setPermitWaiterPeak(permitWaiterPeak);
            metric.setInternalRateLimitedCount(internalRateLimitedCount);
            metric.setPlatform429Count(platform429Count);
            metric.setHttp5xxCount(http5xxCount);
            metric.setTimeoutCount(timeoutCount);
            metric.setLegacyRateLimitedCount(legacyRateLimitedCount);
            metric.setLegacyConcurrencyWaiterPeak(legacyConcurrencyWaiterPeak);
            return metric;
        }
    }
}
