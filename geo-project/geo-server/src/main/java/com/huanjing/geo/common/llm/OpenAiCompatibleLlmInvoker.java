package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmHttpErrorException;
import com.huanjing.geo.common.llm.measurement.LlmResponseException;
import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.measurement.RetryAfterParser;
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
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class OpenAiCompatibleLlmInvoker implements LlmInvoker {

    private static final long RETRY_BASE_DELAY_MS = 250L;
    private static final long RETRY_MAX_DELAY_MS = 5_000L;

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
            try {
                InvocationResponse response;
                long invocationStarted;
                if (executionGateway == null || !modelConfig.useExecutionGateway()) {
                    throttle(modelConfig.platformCode(), modelConfig.rateLimitQps());
                    invocationStarted = System.currentTimeMillis();
                    response = invokeOnce(prompt == null ? "" : prompt, modelConfig);
                } else {
                    try (LlmExecutionPermit ignored = executionGateway.acquireBlocking(modelConfig.feature(), toPlatformConfig(modelConfig))) {
                        throttle(modelConfig.platformCode(), modelConfig.rateLimitQps());
                        invocationStarted = System.currentTimeMillis();
                        response = invokeOnce(prompt == null ? "" : prompt, modelConfig);
                    }
                }
                String responseText = modelConfig.normalizeJsonOutput()
                        ? normalizeJsonText(response.text())
                        : response.text();
                long durationMs = Math.max(1L, System.currentTimeMillis() - invocationStarted);
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
                if (attempt == modelConfig.maxRetry() || !isRetryable(ex, attempt)) {
                    break;
                }
                waitBeforeRetry(ex, attempt);
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
            String providerErrorCode = extractProviderErrorCode(response.body());
            throw new LlmHttpErrorException(
                    response.statusCode(),
                    "HTTP " + response.statusCode() + ": " + safeSnippet(response.body()),
                    providerErrorCode,
                    RetryAfterParser.parse(response.headers()),
                    classifyHttpError(response.statusCode(), providerErrorCode)
            );
        }
        InvocationResponse invocation = extractResponse(response.body());
        if (!StringUtils.hasText(invocation.text())) {
            log.warn("Empty LLM response, platform={}, model={}, bodyLength={}, choices={}, finishReason={}, "
                            + "reasoningContentPresent={}",
                    modelConfig.platformCode(), modelConfig.modelId(), safeLength(response.body()),
                    invocation.choicesCount(), invocation.finishReason(), invocation.reasoningContentPresent());
            throw new LlmResponseException(
                    "Empty model response text",
                    "empty_model_response",
                    LlmErrorCategory.EMPTY_RESPONSE
            );
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
        boolean disableKimiThinking = isKimiK26(modelConfig);
        payload.put("model", modelConfig.modelId());
        if (!disableKimiThinking) {
            payload.put("temperature", modelConfig.temperature());
        }
        if (modelConfig.maxTokens() != null) {
            payload.put("max_tokens", modelConfig.maxTokens());
        }
        if (disableKimiThinking) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(modelConfig.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", modelConfig.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", prompt));
        payload.put("messages", messages);
        return objectMapper.writeValueAsString(payload);
    }

    private boolean isKimiK26(LlmModelConfig modelConfig) {
        return "kimi".equalsIgnoreCase(modelConfig.platformCode())
                && "kimi-k2.6".equalsIgnoreCase(modelConfig.modelId());
    }

    private InvocationResponse extractResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return new InvocationResponse(null, null, null, 0, null, false);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            Integer promptTokens = extractNullableInt(root.path("usage").path("prompt_tokens"));
            Integer completionTokens = extractNullableInt(root.path("usage").path("completion_tokens"));
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                String finishReason = first.path("finish_reason").isTextual()
                        ? first.path("finish_reason").asText()
                        : null;
                JsonNode message = first.get("message");
                if (message != null) {
                    String content = extractMessageContent(message.get("content"));
                    boolean reasoningPresent = StringUtils.hasText(message.path("reasoning_content").asText(null));
                    if (content != null) {
                        return new InvocationResponse(content, promptTokens, completionTokens,
                                choices.size(), finishReason, reasoningPresent);
                    }
                }
                JsonNode text = first.get("text");
                if (text != null && text.isTextual()) {
                    return new InvocationResponse(text.asText(), promptTokens, completionTokens,
                            choices.size(), finishReason, false);
                }
                boolean reasoningPresent = message != null
                        && StringUtils.hasText(message.path("reasoning_content").asText(null));
                return new InvocationResponse(null, promptTokens, completionTokens,
                        choices.size(), finishReason, reasoningPresent);
            }
            JsonNode outputText = root.get("output_text");
            if (outputText != null && outputText.isTextual()) {
                return new InvocationResponse(outputText.asText(), promptTokens, completionTokens,
                        choices == null || !choices.isArray() ? 0 : choices.size(), null, false);
            }
            return new InvocationResponse(body, promptTokens, completionTokens,
                    choices == null || !choices.isArray() ? 0 : choices.size(), null, false);
        } catch (Exception ex) {
            return new InvocationResponse(body, null, null, 0, null, false);
        }
    }

    private String extractMessageContent(JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : content) {
            if (item.isTextual()) {
                parts.add(item.asText());
                continue;
            }
            JsonNode text = item.get("text");
            if (text != null && text.isTextual()) {
                parts.add(text.asText());
            }
        }
        return parts.isEmpty() ? null : String.join("", parts);
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

    private LlmErrorCategory classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return LlmErrorCategory.AUTHENTICATION_FAILED;
        }
        if (statusCode == 402) {
            return LlmErrorCategory.ACCOUNT_QUOTA_EXHAUSTED;
        }
        if (statusCode == 429) {
            return LlmErrorCategory.PLATFORM_429;
        }
        if (statusCode >= 500) {
            return LlmErrorCategory.HTTP_5XX;
        }
        return LlmErrorCategory.INVOKE_FAILED;
    }

    private LlmErrorCategory classifyHttpError(int statusCode, String providerErrorCode) {
        if (isQuotaError(providerErrorCode)) {
            return LlmErrorCategory.ACCOUNT_QUOTA_EXHAUSTED;
        }
        return classifyHttpStatus(statusCode);
    }

    private boolean isQuotaError(String providerErrorCode) {
        if (!StringUtils.hasText(providerErrorCode)) {
            return false;
        }
        String normalized = providerErrorCode.trim().toLowerCase();
        return normalized.contains("exceeded_current_quota")
                || normalized.contains("insufficient_quota")
                || normalized.contains("billing_hard_limit")
                || normalized.contains("account_suspended");
    }

    private boolean isRetryable(Throwable error, int attempt) {
        LlmStructuredException structured = findStructured(error);
        if (structured == null) {
            return true;
        }
        if (structured.errorCategory() == LlmErrorCategory.EMPTY_RESPONSE) {
            return attempt == 0;
        }
        if (structured.retryAfterMs() != null && structured.retryAfterMs() > RETRY_MAX_DELAY_MS) {
            return false;
        }
        return structured.errorCategory() == LlmErrorCategory.PLATFORM_429
                || structured.errorCategory() == LlmErrorCategory.HTTP_5XX
                || structured.errorCategory() == LlmErrorCategory.TIMEOUT;
    }

    private LlmStructuredException findStructured(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LlmStructuredException structured) {
                return structured;
            }
            current = current.getCause();
        }
        return null;
    }

    private void waitBeforeRetry(Throwable error, int attempt) throws LlmInvokeException {
        LlmStructuredException structured = findStructured(error);
        long delayMs = structured != null && structured.retryAfterMs() != null && structured.retryAfterMs() > 0L
                ? Math.min(RETRY_MAX_DELAY_MS, structured.retryAfterMs())
                : Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS * (1L << Math.min(attempt, 4)));
        if (structured == null || structured.retryAfterMs() == null) {
            delayMs += ThreadLocalRandom.current().nextLong(RETRY_BASE_DELAY_MS + 1L);
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmInvokeException("Interrupted during LLM retry backoff", interrupted);
        }
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String extractProviderErrorCode(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            JsonNode code = error.path("code");
            if (code.isTextual()) {
                return code.asText();
            }
            JsonNode type = error.path("type");
            if (type.isTextual()) {
                return type.asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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

    private record InvocationResponse(String text,
                                      Integer promptTokens,
                                      Integer completionTokens,
                                      int choicesCount,
                                      String finishReason,
                                      boolean reasoningContentPresent) {
    }
}
