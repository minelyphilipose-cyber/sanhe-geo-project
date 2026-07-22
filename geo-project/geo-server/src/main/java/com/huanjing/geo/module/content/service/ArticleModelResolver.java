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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleModelResolver {

    private static final int ARTICLE_REQUEST_TIMEOUT_MS = LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS;
    private static final String ARTICLE_FEATURE = "article";

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;

    @Value("${geo.llm.routing.article-excluded-platform-codes:hunyuan,yuanbao}")
    private String articleExcludedPlatformCodes = "hunyuan,yuanbao";

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
        String resolvedModelId = StringUtils.hasText(modelId)
                ? modelId.trim()
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

    private int resolveStandardRequestTimeout(Integer configuredTimeoutMs) {
        int timeout = normalize(configuredTimeoutMs, LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS);
        return Math.min(timeout, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS);
    }

    private int resolveArticleRequestTimeout(Integer configuredTimeoutMs) {
        int timeout = normalize(configuredTimeoutMs, ARTICLE_REQUEST_TIMEOUT_MS);
        timeout = Math.max(timeout, ARTICLE_REQUEST_TIMEOUT_MS);
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
}
