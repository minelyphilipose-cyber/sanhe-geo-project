package com.huanjing.geo.module.presale.generate.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatiblePresaleLlmInvoker implements PresaleLlmInvoker {

    private static final String DEFAULT_QUERY_SYSTEM_PROMPT = "You are a GEO monitoring assistant.";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final String PLATFORM_WENXIN = "wenxin";
    private static final double WENXIN_MIN_JUDGE_TEMPERATURE = 0.1D;

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final PresaleLlmHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, PlatformThrottleState> throttleStates = new ConcurrentHashMap<>();

    @Value("${presale.generate.llm.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

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

        int maxRetry = normalize(config.getMaxRetry(), 2);
        int timeoutMs = normalize(config.getTimeoutMs(), 60_000);
        int qps = Math.max(1, normalize(config.getRateLimitQps(), 1));

        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            long started = System.currentTimeMillis();
            try {
                throttle(config.getPlatformCode(), qps);
                InvocationResponse response = invokeOnce(config, modelId, apiKey, systemPrompt, userPrompt, temperature, timeoutMs);
                String responseText = response.text();
                if (normalizeJsonOutput) {
                    responseText = normalizeJsonText(responseText);
                }
                long durationMs = System.currentTimeMillis() - started;
                if (durationMs <= 0) {
                    log.warn("Non-positive LLM duration detected, platformCode={}, durationMs={}",
                            config.getPlatformCode(), durationMs);
                    durationMs = 1L;
                }
                return new LlmCallResult(
                        responseText,
                        response.promptTokens(),
                        response.completionTokens(),
                        durationMs,
                        attempt,
                        CallStatus.SUCCESS,
                        config.getPlatformCode(),
                        config.getPlatformName(),
                        modelId,
                        resolveModelDisplayName(config, modelId)
                );
            } catch (Exception ex) {
                lastError = ex;
                if (attempt == maxRetry) {
                    break;
                }
            }
        }
        String reason = lastError == null ? "unknown error" : lastError.getMessage();
        throw new LlmInvokeException("LLM invoke failed after retries: " + reason, lastError);
    }

    private InvocationResponse invokeOnce(AiPlatformConfig config,
                                          String modelId,
                                          String apiKey,
                                          String systemPrompt,
                                          String userPrompt,
                                          double temperature,
                                          int timeoutMs) throws Exception {
        String targetUrl = normalizeChatCompletionsUrl(config.getApiUrl());
        String requestBody = buildRequestBody(modelId, systemPrompt, userPrompt, temperature);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);

        PresaleLlmHttpClient.HttpResponse response = httpClient.postJson(
                targetUrl,
                headers,
                requestBody,
                Math.max(connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS),
                timeoutMs
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmInvokeException("HTTP " + response.statusCode() + ": " + safeSnippet(response.body()));
        }
        InvocationResponse invocation = extractResponse(response.body());
        String text = invocation.text();
        if (!StringUtils.hasText(text)) {
            throw new LlmInvokeException("Empty model response text");
        }
        return invocation;
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

    private String normalizeJsonText(String responseText) {
        String stripped = stripMarkdownCodeFence(responseText);
        try {
            JsonNode node = objectMapper.readTree(stripped);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return stripped;
        }
    }

    private InvocationResponse extractResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return new InvocationResponse(null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            Integer promptTokens = extractNullableInt(root.path("usage").path("prompt_tokens"));
            Integer completionTokens = extractNullableInt(root.path("usage").path("completion_tokens"));
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode message = first.get("message");
                if (message != null && message.get("content") != null && message.get("content").isTextual()) {
                    return new InvocationResponse(message.get("content").asText(), promptTokens, completionTokens);
                }
                JsonNode text = first.get("text");
                if (text != null && text.isTextual()) {
                    return new InvocationResponse(text.asText(), promptTokens, completionTokens);
                }
            }
            JsonNode outputText = root.get("output_text");
            if (outputText != null && outputText.isTextual()) {
                return new InvocationResponse(outputText.asText(), promptTokens, completionTokens);
            }
            return new InvocationResponse(body, promptTokens, completionTokens);
        } catch (Exception ex) {
            return new InvocationResponse(body, null, null);
        }
    }

    private Integer extractNullableInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    private String stripMarkdownCodeFence(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
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
        return modelId;
    }

    double normalizeJudgeTemperature(PlatformCallContext ctx, double temperature) {
        if (ctx != null
                && PLATFORM_WENXIN.equalsIgnoreCase(ctx.platformCode())
                && temperature <= 0D) {
            return WENXIN_MIN_JUDGE_TEMPERATURE;
        }
        return temperature;
    }

    private String buildRequestBody(String modelId, String systemPrompt, String userPrompt, double temperature)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelId);
        payload.put("temperature", temperature);
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        payload.put("messages", messages);
        return objectMapper.writeValueAsString(payload);
    }

    private String normalizeChatCompletionsUrl(String apiUrl) {
        String trimmed = apiUrl.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private int normalize(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String safeSnippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private void throttle(String platformCode, int qps) throws LlmInvokeException {
        long minIntervalMs = Math.max(1L, 1000L / qps);
        PlatformThrottleState state = throttleStates.computeIfAbsent(
                platformCode, k -> new PlatformThrottleState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            long last = state.lastCallAtMillis;
            if (last > 0L) {
                long waitMs = minIntervalMs - (now - last);
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LlmInvokeException("Interrupted during rate limit throttle", e);
                    }
                }
            }
            state.lastCallAtMillis = System.currentTimeMillis();
        }
    }

    private static final class PlatformThrottleState {
        private volatile long lastCallAtMillis = 0L;
    }

    private record InvocationResponse(String text, Integer promptTokens, Integer completionTokens) {
    }
}
