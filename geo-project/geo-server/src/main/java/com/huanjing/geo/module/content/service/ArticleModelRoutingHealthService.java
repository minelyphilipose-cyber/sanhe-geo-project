package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.llm.health.ArticleModelHealthPolicy;
import com.huanjing.geo.common.llm.measurement.LlmCallObservation;
import com.huanjing.geo.common.llm.measurement.LlmCallObservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleModelRoutingHealthService {
    private static final String ARTICLE_FEATURE = "article";
    private static final int QUERY_LIMIT = ArticleModelHealthPolicy.WINDOW_SIZE;
    private static final long CACHE_SECONDS = 10L;

    private final LlmCallObservationMapper observationMapper;
    private final ConcurrentMap<String, CachedEvaluation> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> localCooldownUntil = new ConcurrentHashMap<>();

    public Map<String, ArticleModelHealthPolicy.Evaluation> assess(Collection<String> platformCodes) {
        if (platformCodes == null || platformCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, ArticleModelHealthPolicy.Evaluation> result = new LinkedHashMap<>();
        for (String platformCode : platformCodes) {
            String normalized = normalize(platformCode);
            if (normalized != null) {
                result.put(normalized, assess(normalized));
            }
        }
        return result;
    }

    public void recordInfrastructureFailure(String platformCode) {
        String normalized = normalize(platformCode);
        if (normalized == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        localCooldownUntil.put(normalized, now.plus(ArticleModelHealthPolicy.FAILURE_COOLDOWN));
        cache.remove(normalized);
    }

    private ArticleModelHealthPolicy.Evaluation assess(String platformCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime forcedCooldownUntil = localCooldownUntil.get(platformCode);
        if (forcedCooldownUntil != null) {
            if (forcedCooldownUntil.isAfter(now)) {
                return new ArticleModelHealthPolicy.Evaluation(
                        ArticleModelHealthPolicy.HealthLevel.COOLDOWN,
                        0,
                        1,
                        0L,
                        now
                );
            }
            localCooldownUntil.remove(platformCode, forcedCooldownUntil);
        }

        CachedEvaluation cached = cache.get(platformCode);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.evaluation();
        }
        ArticleModelHealthPolicy.Evaluation evaluation = loadEvaluation(platformCode, now);
        cache.put(platformCode, new CachedEvaluation(evaluation, now.plusSeconds(CACHE_SECONDS)));
        return evaluation;
    }

    private ArticleModelHealthPolicy.Evaluation loadEvaluation(String platformCode, LocalDateTime now) {
        try {
            List<LlmCallObservation> observations = observationMapper.selectRecentForFeature(
                    platformCode,
                    ARTICLE_FEATURE,
                    now.minus(ArticleModelHealthPolicy.LOOKBACK),
                    QUERY_LIMIT
            );
            List<ArticleModelHealthPolicy.Sample> samples = observations == null
                    ? List.of()
                    : observations.stream()
                            .map(this::toSample)
                            .filter(java.util.Objects::nonNull)
                            .toList();
            return ArticleModelHealthPolicy.evaluate(samples, now);
        } catch (RuntimeException ex) {
            log.warn("Failed to evaluate recent article model health platform={} reason={}",
                    platformCode, ex.getMessage());
            return ArticleModelHealthPolicy.Evaluation.noData();
        }
    }

    private ArticleModelHealthPolicy.Sample toSample(LlmCallObservation observation) {
        if (observation == null || observation.getOccurredAt() == null) {
            return null;
        }
        boolean success = "success".equalsIgnoreCase(observation.getStatus());
        boolean infrastructureFailure = "failure".equalsIgnoreCase(observation.getStatus())
                && isInfrastructureCategory(observation.getErrorCategory());
        if (!success && !infrastructureFailure) {
            return null;
        }
        return new ArticleModelHealthPolicy.Sample(
                success,
                infrastructureFailure,
                observation.getHttpMs() == null ? observation.getTotalMs() : observation.getHttpMs(),
                observation.getOccurredAt()
        );
    }

    private boolean isInfrastructureCategory(String errorCategory) {
        if (!StringUtils.hasText(errorCategory)) {
            return false;
        }
        return switch (errorCategory.trim().toUpperCase(Locale.ROOT)) {
            case "TIMEOUT", "INVOKE_FAILED", "HTTP_5XX", "PLATFORM_429" -> true;
            default -> false;
        };
    }

    private String normalize(String platformCode) {
        return StringUtils.hasText(platformCode)
                ? platformCode.trim().toLowerCase(Locale.ROOT)
                : null;
    }

    private record CachedEvaluation(ArticleModelHealthPolicy.Evaluation evaluation,
                                    LocalDateTime expiresAt) {
    }
}
