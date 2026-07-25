package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.llm.health.ArticleModelHealthPolicy;
import com.huanjing.geo.module.dispatch.entity.AiPlatformHealthEvent;
import com.huanjing.geo.module.dispatch.mapper.AiPlatformHealthEventMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlatformHealthMonitorService {
    public static final String EVENT_SUCCESS = "success";
    public static final String EVENT_SLOW_RESPONSE = "slow_response";
    public static final String EVENT_FAILURE = "failure";
    public static final String EVENT_RATE_LIMITED = "rate_limited";
    public static final String EVENT_PERMIT_BUSY = "permit_busy";
    public static final String EVENT_CIRCUIT_OPEN = "circuit_open";

    private static final String ARTICLE_FEATURE = "article";
    private static final long DEFAULT_SLOW_RESPONSE_THRESHOLD_MS = 30_000L;
    private static final int MAX_ERROR_LENGTH = 500;

    private final AiPlatformHealthEventMapper healthEventMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;

    public void recordSuccess(String platformCode, String feature, long durationMs) {
        if (durationMs >= slowResponseThresholdMs(feature)) {
            record(platformCode, feature, EVENT_SLOW_RESPONSE, durationMs, null, "slow_response", false);
            return;
        }
        record(platformCode, feature, EVENT_SUCCESS, durationMs, null, "normal", false);
    }

    public void recordFailure(String platformCode, String feature, String errorMessage) {
        record(platformCode, feature, EVENT_FAILURE, null, errorMessage, "high_failure", true);
    }

    public void recordRateLimited(String platformCode, String feature) {
        record(platformCode, feature, EVENT_RATE_LIMITED, null, "platform rate limit reached", "slow_response", true);
    }

    public void recordPermitBusy(String platformCode, String feature) {
        record(platformCode, feature, EVENT_PERMIT_BUSY, null, "platform concurrency permit busy", "slow_response", true);
    }

    public void recordCircuitOpen(String platformCode, String feature) {
        record(platformCode, feature, EVENT_CIRCUIT_OPEN, null, "platform circuit breaker open", "degraded", true);
    }

    private void record(String platformCode,
                        String feature,
                        String eventType,
                        Long durationMs,
                        String errorMessage,
                        String healthStatus,
                        boolean failureSignal) {
        String normalizedCode = normalizePlatformCode(platformCode);
        if (!StringUtils.hasText(normalizedCode)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            AiPlatformHealthEvent event = new AiPlatformHealthEvent();
            event.setPlatformCode(normalizedCode);
            event.setFeature(normalizeFeature(feature));
            event.setEventType(eventType);
            event.setDurationMs(durationMs);
            event.setErrorMessage(trim(errorMessage));
            event.setOccurredAt(now);
            healthEventMapper.insert(event);
            if (ARTICLE_FEATURE.equals(event.getFeature())) {
                updateArticleWindowHealth(normalizedCode, eventType, now);
            } else {
                updateCurrentHealth(normalizedCode, healthStatus, failureSignal, now);
            }
        } catch (Exception ex) {
            log.warn("failed to record AI platform health event, platformCode={}, eventType={}, reason={}",
                    normalizedCode, eventType, ex.getMessage());
        }
    }

    private void updateArticleWindowHealth(String platformCode,
                                           String currentEventType,
                                           LocalDateTime now) {
        List<AiPlatformHealthEvent> events = healthEventMapper.selectRecentForFeature(
                platformCode,
                ARTICLE_FEATURE,
                now.minus(ArticleModelHealthPolicy.LOOKBACK),
                ArticleModelHealthPolicy.WINDOW_SIZE
        );
        List<ArticleModelHealthPolicy.Sample> samples = events == null
                ? List.of()
                : events.stream()
                        .map(this::toArticleSample)
                        .filter(Objects::nonNull)
                        .toList();
        ArticleModelHealthPolicy.Evaluation evaluation = ArticleModelHealthPolicy.evaluate(samples, now);
        boolean providerFailureSignal = EVENT_FAILURE.equals(currentEventType)
                || EVENT_CIRCUIT_OPEN.equals(currentEventType);
        updateCurrentHealth(platformCode, evaluation.healthStatus(), providerFailureSignal, now);
    }

    private ArticleModelHealthPolicy.Sample toArticleSample(AiPlatformHealthEvent event) {
        boolean success = EVENT_SUCCESS.equals(event.getEventType())
                || EVENT_SLOW_RESPONSE.equals(event.getEventType());
        boolean infrastructureFailure = EVENT_FAILURE.equals(event.getEventType())
                || EVENT_CIRCUIT_OPEN.equals(event.getEventType());
        if (!success && !infrastructureFailure) {
            return null;
        }
        return new ArticleModelHealthPolicy.Sample(
                success,
                infrastructureFailure,
                event.getDurationMs(),
                event.getOccurredAt()
        );
    }

    private long slowResponseThresholdMs(String feature) {
        return ARTICLE_FEATURE.equals(normalizeFeature(feature))
                ? ArticleModelHealthPolicy.SLOW_RESPONSE_THRESHOLD_MS
                : DEFAULT_SLOW_RESPONSE_THRESHOLD_MS;
    }

    private void updateCurrentHealth(String platformCode, String healthStatus, boolean failureSignal, LocalDateTime now) {
        LambdaUpdateWrapper<AiPlatformConfig> wrapper = new LambdaUpdateWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getPlatformCode, platformCode)
                .and(w -> w.isNull(AiPlatformConfig::getDegraded).or().eq(AiPlatformConfig::getDegraded, false))
                .and(w -> w.isNull(AiPlatformConfig::getCurrentHealthStatus)
                        .or()
                        .notIn(AiPlatformConfig::getCurrentHealthStatus, "manual_takeover", "maintenance"))
                .set(AiPlatformConfig::getCurrentHealthStatus, healthStatus);
        if (failureSignal) {
            wrapper.set(AiPlatformConfig::getLastFailureAt, now);
        }
        aiPlatformConfigMapper.update(null, wrapper);
    }

    private String normalizePlatformCode(String platformCode) {
        return StringUtils.hasText(platformCode)
                ? platformCode.trim().toLowerCase(Locale.ROOT)
                : null;
    }

    private String normalizeFeature(String feature) {
        if (!StringUtils.hasText(feature)) {
            return "generic";
        }
        String normalized = feature.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_ERROR_LENGTH ? trimmed : trimmed.substring(0, MAX_ERROR_LENGTH);
    }
}
