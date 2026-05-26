package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.pool.LlmExecutionGateway;
import com.huanjing.geo.common.llm.pool.LlmExecutionPermit;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.dispatch.service.AiPlatformHealthMonitorService;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenAiCompatibleLlmInvoker implements LlmInvoker {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmExecutionGateway executionGateway;
    private final AiPlatformHealthMonitorService platformHealthMonitorService;
    private final ConcurrentHashMap<String, PlatformThrottleState> throttleStates = new ConcurrentHashMap<>();

    @Autowired
    public OpenAiCompatibleLlmInvoker(LlmHttpClient httpClient,
                                      ObjectMapper objectMapper,
                                      ObjectProvider<LlmExecutionGateway> executionGatewayProvider,
                                      ObjectProvider<AiPlatformHealthMonitorService> platformHealthMonitorServiceProvider) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executionGateway = executionGatewayProvider.getIfAvailable();
        this.platformHealthMonitorService = platformHealthMonitorServiceProvider.getIfAvailable();
    }

    public OpenAiCompatibleLlmInvoker(LlmHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executionGateway = null;
        this.platformHealthMonitorService = null;
    }

    @Override
    public LlmInvokeResult invoke(String prompt, LlmModelConfig modelConfig) throws LlmInvokeException {
        Exception lastError = null;
        for (int attempt = 0; attempt <= modelConfig.maxRetry(); attempt++) {
            long started = System.currentTimeMillis();
            try {
                InvocationResponse response;
                if (executionGateway == null || !modelConfig.useExecutionGateway()) {
                    throttle(modelConfig.platformCode(), modelConfig.rateLimitQps());
                    response = invokeOnce(prompt == null ? "" : prompt, modelConfig);
                } else {
                    try (LlmExecutionPermit ignored = executionGateway.acquireBlocking(modelConfig.feature(), toPlatformConfig(modelConfig))) {
                        throttle(modelConfig.platformCode(), modelConfig.rateLimitQps());
                        response = invokeOnce(prompt == null ? "" : prompt, modelConfig);
                    }
                }
                String responseText = modelConfig.normalizeJsonOutput()
                        ? normalizeJsonText(response.text())
                        : response.text();
                long durationMs = Math.max(1L, System.currentTimeMillis() - started);
                recordSuccess(modelConfig, durationMs);
                return new LlmInvokeResult(
                        responseText,
                        response.promptTokens(),
                        response.completionTokens(),
                        durationMs,
                        attempt,
                        LlmCallStatus.SUCCESS,
                        modelConfig.platformCode(),
                        modelConfig.platformName(),
                        modelConfig.modelId(),
                        modelConfig.modelName()
                );
            } catch (LlmPermitUnavailableException ex) {
                throw ex;
            } catch (Exception ex) {
                lastError = ex;
                if (attempt == modelConfig.maxRetry()) {
                    break;
                }
            }
        }
        String reason = lastError == null ? "unknown error" : lastError.getMessage();
        recordFailure(modelConfig, reason);
        throw new LlmInvokeException("LLM invoke failed after retries: " + reason, lastError);
    }

    private void recordSuccess(LlmModelConfig modelConfig, long durationMs) {
        if (platformHealthMonitorService == null || modelConfig == null) {
            return;
        }
        platformHealthMonitorService.recordSuccess(modelConfig.platformCode(), modelConfig.feature(), durationMs);
    }

    private void recordFailure(LlmModelConfig modelConfig, String reason) {
        if (platformHealthMonitorService == null || modelConfig == null) {
            return;
        }
        platformHealthMonitorService.recordFailure(modelConfig.platformCode(), modelConfig.feature(), reason);
    }

    private InvocationResponse invokeOnce(String prompt, LlmModelConfig modelConfig) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + modelConfig.apiKey());
        headers.put("api-key", modelConfig.apiKey());
        headers.put("x-api-key", modelConfig.apiKey());

        LlmHttpClient.HttpResponse response = httpClient.postJson(
                normalizeChatCompletionsUrl(modelConfig.apiUrl()),
                headers,
                buildRequestBody(prompt, modelConfig),
                modelConfig.connectTimeoutMs(),
                modelConfig.requestTimeoutMs()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmInvokeException("HTTP " + response.statusCode() + ": " + safeSnippet(response.body()));
        }
        InvocationResponse invocation = extractResponse(response.body());
        if (!StringUtils.hasText(invocation.text())) {
            throw new LlmInvokeException("Empty model response text");
        }
        return invocation;
    }

    private AiPlatformConfig toPlatformConfig(LlmModelConfig modelConfig) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode(modelConfig.platformCode());
        config.setPlatformName(modelConfig.platformName());
        config.setConcurrencyLimit(modelConfig.concurrencyLimit());
        return config;
    }

    private String buildRequestBody(String prompt, LlmModelConfig modelConfig) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelConfig.modelId());
        payload.put("temperature", modelConfig.temperature());
        if (modelConfig.maxTokens() != null) {
            payload.put("max_tokens", modelConfig.maxTokens());
        }
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(modelConfig.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", modelConfig.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", prompt));
        payload.put("messages", messages);
        return objectMapper.writeValueAsString(payload);
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

    private String normalizeJsonText(String responseText) {
        String stripped = stripMarkdownCodeFence(responseText);
        try {
            JsonNode node = objectMapper.readTree(stripped);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return stripped;
        }
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

    private String safeSnippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    private void throttle(String platformCode, int qps) throws LlmInvokeException {
        long minIntervalMs = Math.max(1L, 1000L / qps);
        PlatformThrottleState state = throttleStates.computeIfAbsent(platformCode, k -> new PlatformThrottleState());
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
