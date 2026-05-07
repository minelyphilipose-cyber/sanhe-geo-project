package com.huanjing.geo.module.presale.generate.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OpenAiCompatiblePresaleLlmInvoker implements PresaleLlmInvoker {

    private static final String DEFAULT_QUERY_SYSTEM_PROMPT = "You are a GEO monitoring assistant.";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final String PLATFORM_WENXIN = "wenxin";
    private static final double WENXIN_MIN_JUDGE_TEMPERATURE = 0.1D;

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final LlmInvoker llmInvoker;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    @Value("${presale.generate.llm.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Autowired
    public OpenAiCompatiblePresaleLlmInvoker(AiPlatformConfigMapper aiPlatformConfigMapper,
                                             PlatformCredentialService platformCredentialService,
                                             LlmInvoker llmInvoker,
                                             LlmProperties llmProperties,
                                             ObjectMapper objectMapper) {
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.platformCredentialService = platformCredentialService;
        this.llmInvoker = llmInvoker;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    OpenAiCompatiblePresaleLlmInvoker(AiPlatformConfigMapper aiPlatformConfigMapper,
                                      PlatformCredentialService platformCredentialService,
                                      PresaleLlmHttpClient httpClient,
                                      ObjectMapper objectMapper) {
        this(
                aiPlatformConfigMapper,
                platformCredentialService,
                new com.huanjing.geo.common.llm.OpenAiCompatibleLlmInvoker(
                        (url, headers, body, connectTimeoutMs, requestTimeoutMs) -> {
                            PresaleLlmHttpClient.HttpResponse response = httpClient.postJson(
                                    url, headers, body, connectTimeoutMs, requestTimeoutMs);
                            return new com.huanjing.geo.common.llm.LlmHttpClient.HttpResponse(
                                    response.statusCode(), response.body());
                        },
                        objectMapper
                ),
                new LlmProperties(),
                objectMapper
        );
    }

    @Override
    public LlmCallResult query(PlatformCallContext ctx, String renderedPrompt) throws LlmInvokeException {
        return invokeWithRetry(
                ctx,
                DEFAULT_QUERY_SYSTEM_PROMPT,
                renderedPrompt == null ? "" : renderedPrompt,
                0.2D,
                false
        );
    }

    @Override
    public LlmCallResult analyze(PlatformCallContext ctx, String originalPrompt, String queryAnswer)
            throws LlmInvokeException, AnalyzeParseException {
        String userPrompt = AnalyzePromptTemplates.renderUserPrompt(
                originalPrompt,
                queryAnswer,
                ctx.brandName()
        );
        LlmCallResult result = invokeWithRetry(
                ctx,
                AnalyzePromptTemplates.SYSTEM_INSTRUCTION,
                userPrompt,
                0.1D,
                true
        );
        validateAnalyzeJson(result.rawResponse());
        return result;
    }

    @Override
    public LlmCallResult judge(PlatformCallContext ctx, String judgePrompt, double temperature)
            throws LlmInvokeException {
        return invokeWithRetry(
                ctx,
                JudgePromptTemplates.SYSTEM_INSTRUCTION,
                safe(judgePrompt),
                normalizeJudgeTemperature(ctx, temperature),
                true
        );
    }

    @Override
    public LlmCallResult normalizeCompetitors(PlatformCallContext ctx, String normalizationPrompt)
            throws LlmInvokeException {
        return invokeWithRetry(
                ctx,
                CompetitorNormalizationPromptTemplates.SYSTEM_INSTRUCTION,
                safe(normalizationPrompt),
                normalizeJudgeTemperature(ctx, 0D),
                true
        );
    }

    private LlmCallResult invokeWithRetry(PlatformCallContext ctx,
                                          String systemPrompt,
                                          String userPrompt,
                                          double temperature,
                                          boolean normalizeJsonOutput) throws LlmInvokeException {
        AiPlatformConfig config = requireConfig(ctx.platformCode());
        String modelId = resolvePresaleModelId(config);
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new LlmInvokeException("Missing API key for platform: " + ctx.platformCode());
        }

        try {
            LlmInvokeResult result = llmInvoker.invoke(userPrompt, new LlmModelConfig(
                    config.getPlatformCode(),
                    config.getPlatformName(),
                    modelId,
                    resolveModelDisplayName(config, modelId),
                    config.getApiUrl(),
                    apiKey,
                    systemPrompt,
                    temperature,
                    Math.max(connectTimeoutMs, Math.max(DEFAULT_CONNECT_TIMEOUT_MS, llmProperties.getConnectTimeoutMs())),
                    normalize(config.getTimeoutMs(), llmProperties.getRequestTimeoutMs()),
                    normalize(config.getMaxRetry(), llmProperties.getMaxRetry()),
                    Math.max(1, normalize(config.getRateLimitQps(), llmProperties.getRateLimitQps())),
                    null,
                    normalizeJsonOutput
            ));
            return new LlmCallResult(
                    result.rawResponse(),
                    result.promptTokens(),
                    result.completionTokens(),
                    result.durationMs(),
                    result.retryCount(),
                    CallStatus.SUCCESS,
                    result.platformCode(),
                    result.platformName(),
                    result.modelId(),
                    result.modelName()
            );
        } catch (com.huanjing.geo.common.llm.LlmInvokeException | IllegalArgumentException ex) {
            throw new LlmInvokeException(ex.getMessage(), ex);
        }
    }

    private void validateAnalyzeJson(String responseText) throws AnalyzeParseException {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            if (!root.has("is_mentioned") || !root.get("is_mentioned").isBoolean()) {
                throw new AnalyzeParseException("analyze output missing boolean is_mentioned");
            }
            if (root.has("ranking") && !root.get("ranking").isNull() && !root.get("ranking").isNumber()) {
                throw new AnalyzeParseException("analyze output ranking must be number or null");
            }
            if (!root.has("sentiment") || root.get("sentiment").isNull() || !root.get("sentiment").isTextual()) {
                throw new AnalyzeParseException("analyze output sentiment must be non-null string");
            }
            String sentiment = root.get("sentiment").asText();
            if (!"POSITIVE".equals(sentiment)
                    && !"NEUTRAL".equals(sentiment)
                    && !"NEGATIVE".equals(sentiment)) {
                throw new AnalyzeParseException("analyze output sentiment invalid: " + sentiment);
            }
            if (!root.has("mentioned_competitors") || !root.get("mentioned_competitors").isArray()) {
                throw new AnalyzeParseException("analyze output mentioned_competitors must be array");
            }
            if (!root.has("scene_advantages") || !root.get("scene_advantages").isArray()) {
                throw new AnalyzeParseException("analyze output scene_advantages must be array");
            }
            validateTopKeywords(root.get("top_keywords"));
            validateNegativeEvidence(root.get("negative_evidence"));
        } catch (AnalyzeParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AnalyzeParseException("analyze output is not valid JSON", ex);
        }
    }

    private void validateTopKeywords(JsonNode topKeywordsNode) throws AnalyzeParseException {
        if (topKeywordsNode == null || !topKeywordsNode.isArray()) {
            throw new AnalyzeParseException("analyze output top_keywords must be array");
        }
        for (JsonNode item : topKeywordsNode) {
            if (item == null || !item.isObject()) {
                throw new AnalyzeParseException("analyze output top_keywords element must be object");
            }
            JsonNode keywordNode = item.get("keyword");
            if (keywordNode == null || !keywordNode.isTextual() || keywordNode.asText().trim().isEmpty()) {
                throw new AnalyzeParseException("analyze output top_keywords.keyword must be non-blank string");
            }
            JsonNode sentimentNode = item.get("sentiment");
            if (sentimentNode == null || !sentimentNode.isTextual()) {
                throw new AnalyzeParseException("analyze output top_keywords.sentiment must be string");
            }
            String sentiment = sentimentNode.asText();
            if (!"POSITIVE".equals(sentiment) && !"NEUTRAL".equals(sentiment) && !"NEGATIVE".equals(sentiment)) {
                throw new AnalyzeParseException("analyze output top_keywords.sentiment invalid: " + sentiment);
            }
        }
    }

    private void validateNegativeEvidence(JsonNode negativeEvidenceNode) throws AnalyzeParseException {
        if (negativeEvidenceNode == null || !negativeEvidenceNode.isObject()) {
            throw new AnalyzeParseException("analyze output negative_evidence must be object");
        }
        JsonNode hasNegativeNode = negativeEvidenceNode.get("has_negative");
        if (hasNegativeNode == null || !hasNegativeNode.isBoolean()) {
            throw new AnalyzeParseException("analyze output negative_evidence.has_negative must be boolean");
        }
        JsonNode snippetNode = negativeEvidenceNode.get("snippet");
        if (hasNegativeNode.asBoolean()) {
            if (snippetNode == null || !snippetNode.isTextual() || snippetNode.asText().trim().isEmpty()) {
                throw new AnalyzeParseException("analyze output negative_evidence.snippet must be non-blank string when has_negative=true");
            }
            return;
        }
        if (snippetNode != null && !snippetNode.isNull()) {
            throw new AnalyzeParseException("analyze output negative_evidence.snippet must be null when has_negative=false");
        }
    }

    private AiPlatformConfig requireConfig(String platformCode) throws LlmInvokeException {
        AiPlatformConfig platform = aiPlatformConfigMapper.selectOne(
                PresalePlatformConfigQueries.presaleEnabledWrapper()
                        .eq(AiPlatformConfig::getPlatformCode, platformCode)
                        .last("LIMIT 1")
        );
        if (platform == null) {
            throw new LlmInvokeException("Platform config not found: " + platformCode);
        }
        String modelId = resolvePresaleModelId(platform);
        if (!StringUtils.hasText(platform.getApiUrl()) || !StringUtils.hasText(modelId)) {
            throw new LlmInvokeException("Invalid api_url/model_id for platform: " + platformCode);
        }
        return platform;
    }

    /**
     * 防御性 fallback: 正常流程由 SQL 过滤保证 low_model_id 非空,
     * 本方法仅防止调用方绕过 SQL 过滤导致崩溃。
     */
    String resolvePresaleModelId(AiPlatformConfig platform) {
        String low = platform == null ? null : platform.getLowModelId();
        if (StringUtils.hasText(low)) {
            return low.trim();
        }
        if (platform != null) {
            log.warn("low_model_id is blank, fallback to model_id, platformCode={}", platform.getPlatformCode());
            return platform.getModelId();
        }
        return null;
    }

    private String resolveModelDisplayName(AiPlatformConfig config, String modelId) {
        if (config == null) {
            return modelId;
        }
        String platformName = StringUtils.hasText(config.getPlatformName())
                ? config.getPlatformName().trim()
                : config.getPlatformCode();
        String displayModel = StringUtils.hasText(config.getModelName())
                ? config.getModelName().trim()
                : modelId;
        if (!StringUtils.hasText(platformName)) {
            return displayModel;
        }
        if (!StringUtils.hasText(displayModel)) {
            return platformName;
        }
        return platformName + " / " + displayModel;
    }

    double normalizeJudgeTemperature(PlatformCallContext ctx, double temperature) {
        if (ctx != null
                && PLATFORM_WENXIN.equalsIgnoreCase(ctx.platformCode())
                && temperature <= 0D) {
            return WENXIN_MIN_JUDGE_TEMPERATURE;
        }
        return temperature;
    }

    private int normalize(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
