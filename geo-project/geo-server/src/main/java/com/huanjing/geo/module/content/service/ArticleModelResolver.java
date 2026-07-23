package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmPlatformCodeFilters;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleModelResolver {

    private static final String ARTICLE_FEATURE = "article";
    private static final int MAX_CANDIDATE_WEIGHT = 20;
    private static final Set<String> ADMIN_UNAVAILABLE_HEALTH_STATUSES = Set.of("maintenance", "manual_takeover");

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;

    @Value("${geo.llm.routing.article-excluded-platform-codes:hunyuan,yuanbao}")
    private String articleExcludedPlatformCodes = "hunyuan,yuanbao";

    @Value("${geo.llm.routing.article-request-timeout-ms:300000}")
    private int articleRequestTimeoutMs = LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS;

    public ModelSelection resolve(String platformCode, String modelId, String systemPrompt, boolean longForm) {
        return resolve(platformCode, modelId, systemPrompt, longForm, ArticleGenerationTemperatures.DEFAULT);
    }

    public ModelSelection resolve(String platformCode,
                                  String modelId,
                                  String systemPrompt,
                                  boolean longForm,
                                  double temperature) {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForArticle, true)
                .orderByAsc(AiPlatformConfig::getId);
        Set<String> excluded = LlmPlatformCodeFilters.parseCodes(articleExcludedPlatformCodes);
        if (!excluded.isEmpty()) {
            wrapper.notIn(AiPlatformConfig::getPlatformCode, excluded);
        }
        if (StringUtils.hasText(platformCode)) {
            wrapper.eq(AiPlatformConfig::getPlatformCode, platformCode.trim());
        }
        if (StringUtils.hasText(modelId)) {
            String trimmedModelId = modelId.trim();
            wrapper.and(w -> w.eq(AiPlatformConfig::getModelId, trimmedModelId)
                    .or()
                    .eq(AiPlatformConfig::getLowModelId, trimmedModelId));
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(wrapper.last("LIMIT 1"));
        if (config == null
                || excluded.contains(LlmPlatformCodeFilters.normalize(config.getPlatformCode()))
                || !StringUtils.hasText(config.getApiUrl())) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        return buildSelection(config, modelId, systemPrompt, longForm, temperature);
    }

    public ModelSelection resolveForBatch(long selectionKey,
                                          String systemPrompt,
                                          boolean longForm,
                                          double temperature,
                                          Set<String> excludedPlatformCodes) {
        Set<String> excluded = mergeExcludedPlatformCodes(excludedPlatformCodes);
        List<AiPlatformConfig> configs = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForArticle, true)
                        .orderByAsc(AiPlatformConfig::getId)
        );
        List<BatchCandidate> candidates = new ArrayList<>();
        if (configs != null) {
            for (AiPlatformConfig config : configs) {
                if (!isAvailableBatchCandidate(config, excluded)) {
                    continue;
                }
                try {
                    ModelSelection selection = buildSelection(config, null, systemPrompt, longForm, temperature);
                    candidates.add(new BatchCandidate(selection, candidateWeight(config)));
                } catch (RuntimeException ex) {
                    log.warn("Skipping unusable article model candidate platform={} model={} reason={}",
                            config == null ? null : config.getPlatformCode(),
                            config == null ? null : config.getModelId(),
                            ex.getMessage());
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        return chooseWeightedCandidate(candidates, selectionKey).selection();
    }

    private ModelSelection buildSelection(AiPlatformConfig config,
                                          String requestedModelId,
                                          String systemPrompt,
                                          boolean longForm,
                                          double temperature) {
        String resolvedModelId = StringUtils.hasText(requestedModelId)
                ? requestedModelId.trim()
                : (StringUtils.hasText(config.getModelId()) ? config.getModelId().trim() : config.getLowModelId());
        if (!StringUtils.hasText(resolvedModelId)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }

        int timeout = longForm ? resolveArticleRequestTimeout(config.getTimeoutMs()) : resolveStandardRequestTimeout(config.getTimeoutMs());
        LlmModelConfig modelConfig = new LlmModelConfig(
                config.getPlatformCode(),
                config.getPlatformName(),
                resolvedModelId,
                resolveModelDisplayName(config, resolvedModelId),
                config.getApiUrl(),
                apiKey,
                systemPrompt,
                temperature,
                LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS,
                timeout,
                longForm ? 0 : normalize(config.getMaxRetry(), 2),
                Math.max(1, normalize(config.getRateLimitQps(), 1)),
                null,
                false,
                longForm ? LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS : LlmModelConfig.MAX_REQUEST_TIMEOUT_MS,
                ARTICLE_FEATURE,
                config.getConcurrencyLimit()
        );
        return new ModelSelection(config.getPlatformCode(), resolvedModelId, modelConfig);
    }

    private boolean isAvailableBatchCandidate(AiPlatformConfig config, Set<String> excluded) {
        if (config == null
                || !Boolean.TRUE.equals(config.getEnabled())
                || !Boolean.TRUE.equals(config.getEnabledForArticle())
                || Boolean.TRUE.equals(config.getDegraded())
                || !StringUtils.hasText(config.getPlatformCode())
                || !StringUtils.hasText(config.getApiUrl())
                || excluded.contains(LlmPlatformCodeFilters.normalize(config.getPlatformCode()))) {
            return false;
        }
        String healthStatus = StringUtils.hasText(config.getCurrentHealthStatus())
                ? config.getCurrentHealthStatus().trim().toLowerCase(Locale.ROOT)
                : null;
        return healthStatus == null || !ADMIN_UNAVAILABLE_HEALTH_STATUSES.contains(healthStatus);
    }

    private Set<String> mergeExcludedPlatformCodes(Set<String> excludedPlatformCodes) {
        Set<String> merged = new java.util.LinkedHashSet<>(
                LlmPlatformCodeFilters.parseCodes(articleExcludedPlatformCodes));
        if (excludedPlatformCodes != null) {
            merged.addAll(excludedPlatformCodes.stream()
                    .filter(StringUtils::hasText)
                    .map(LlmPlatformCodeFilters::normalize)
                    .collect(Collectors.toSet()));
        }
        return merged;
    }

    private int candidateWeight(AiPlatformConfig config) {
        Integer concurrencyLimit = config.getConcurrencyLimit();
        if (concurrencyLimit == null || concurrencyLimit <= 0) {
            return 1;
        }
        return Math.min(concurrencyLimit, MAX_CANDIDATE_WEIGHT);
    }

    private BatchCandidate chooseWeightedCandidate(List<BatchCandidate> candidates, long selectionKey) {
        int totalWeight = candidates.stream().mapToInt(BatchCandidate::weight).sum();
        int slot = (int) Math.floorMod(mixSelectionKey(selectionKey), totalWeight);
        for (BatchCandidate candidate : candidates) {
            if (slot < candidate.weight()) {
                return candidate;
            }
            slot -= candidate.weight();
        }
        return candidates.get(candidates.size() - 1);
    }

    private long mixSelectionKey(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private int resolveStandardRequestTimeout(Integer configuredTimeoutMs) {
        int timeout = normalize(configuredTimeoutMs, LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS);
        return Math.min(timeout, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS);
    }

    private int resolveArticleRequestTimeout(Integer configuredTimeoutMs) {
        int articleTimeout = Math.min(
                Math.max(articleRequestTimeoutMs, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS),
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        int timeout = normalize(configuredTimeoutMs, articleTimeout);
        timeout = Math.max(timeout, articleTimeout);
        return Math.min(timeout, LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
    }

    private String resolveModelDisplayName(AiPlatformConfig config, String modelId) {
        if (StringUtils.hasText(config.getModelName()) && modelId.equals(config.getModelId())) {
            return config.getModelName().trim();
        }
        return modelId;
    }

    private int normalize(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    public record ModelSelection(String platformCode, String modelId, LlmModelConfig config) {
    }

    private record BatchCandidate(ModelSelection selection, int weight) {
    }
}
