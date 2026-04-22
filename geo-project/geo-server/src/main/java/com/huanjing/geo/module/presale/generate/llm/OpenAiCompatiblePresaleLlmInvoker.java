package com.huanjing.geo.module.presale.generate.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleLlmConfigMapper;
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

    private final PresaleLlmConfigMapper configMapper;
    private final PlatformCredentialService platformCredentialService;
    private final PresaleLlmHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Map<String, Long> lastInvokeAtByPlatform = new ConcurrentHashMap<>();

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
        String userPrompt = AnalyzePromptTemplates.USER_TEMPLATE
                .replace("{{originalPrompt}}", safe(originalPrompt))
                .replace("{{queryAnswer}}", safe(queryAnswer))
                .replace("{{brandName}}", safe(ctx.brandName()));
        LlmCallResult result = invokeWithRetry(
                ctx,
                AnalyzePromptTemplates.SYSTEM_INSTRUCTION,
                userPrompt,
                0D,
                true
        );
        validateAnalyzeJson(result.rawResponse());
        return result;
    }

    private LlmCallResult invokeWithRetry(PlatformCallContext ctx,
                                          String systemPrompt,
                                          String userPrompt,
                                          double temperature,
                                          boolean normalizeJsonOutput) throws LlmInvokeException {
        PresaleLlmPlatformConfigRow config = requireConfig(ctx.platformCode());
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
                InvocationResponse response = invokeOnce(config, apiKey, systemPrompt, userPrompt, temperature, timeoutMs);
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
                        CallStatus.SUCCESS
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

    private InvocationResponse invokeOnce(PresaleLlmPlatformConfigRow config,
                                          String apiKey,
                                          String systemPrompt,
                                          String userPrompt,
                                          double temperature,
                                          int timeoutMs) throws Exception {
        String targetUrl = normalizeChatCompletionsUrl(config.getApiUrl());
        String requestBody = buildRequestBody(config.getModelId(), systemPrompt, userPrompt, temperature);
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
        } catch (AnalyzeParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AnalyzeParseException("analyze output is not valid JSON", ex);
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

    private PresaleLlmPlatformConfigRow requireConfig(String platformCode) throws LlmInvokeException {
        PresaleLlmPlatformConfigRow row = configMapper.selectRuntimeConfig(platformCode);
        if (row == null) {
            throw new LlmInvokeException("Platform config not found: " + platformCode);
        }
        if (row.getInWhitelist() == null || row.getInWhitelist() != 1) {
            throw new LlmInvokeException("Platform not in presale whitelist: " + platformCode);
        }
        if (!StringUtils.hasText(row.getApiUrl()) || !StringUtils.hasText(row.getModelId())) {
            throw new LlmInvokeException("Invalid api_url/model_id for platform: " + platformCode);
        }
        return row;
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

    private void throttle(String platformCode, int qps) {
        long minIntervalMs = Math.max(1L, 1000L / qps);
        long now = System.currentTimeMillis();
        synchronized (lastInvokeAtByPlatform) {
            Long last = lastInvokeAtByPlatform.get(platformCode);
            if (last != null) {
                long waitMs = minIntervalMs - (now - last);
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            lastInvokeAtByPlatform.put(platformCode, System.currentTimeMillis());
        }
    }

    private record InvocationResponse(String text, Integer promptTokens, Integer completionTokens) {
    }
}
