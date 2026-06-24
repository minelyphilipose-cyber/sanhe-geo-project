package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.limiter.PlatformConcurrencyLimiterService;
import com.huanjing.geo.common.llm.limiter.PlatformRateLimiterService;
import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmHttpErrorException;
import com.huanjing.geo.common.llm.measurement.LlmMeasurementCollector;
import com.huanjing.geo.common.llm.measurement.LlmMeasurementEvent;
import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
import com.huanjing.geo.common.llm.measurement.RetryAfterParser;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LlmCallFacade {
    private final LlmPlatformRouter platformRouter;
    private final LlmInvoker llmInvoker;
    private final PlatformRateLimiterService platformRateLimiterService;
    private final PlatformConcurrencyLimiterService platformConcurrencyLimiterService;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;
    private final LlmMeasurementCollector measurementCollector;
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public LlmCallResult execute(LlmCallRequest request) throws LlmInvokeException {
        long started = System.currentTimeMillis();
        incrementCounter(request);
        try {
            LlmCallResult result = switch (request.governanceStack()) {
                case GATEWAY -> executeGateway(request);
                case LEGACY_LIMITER -> executeLegacyLimiter(request);
                case NO_PERMIT_LEGACY -> executeLegacyHttp(request);
            };
            safeRecordSuccess(request, result, elapsed(started));
            return result;
        } catch (RuntimeException | LlmInvokeException ex) {
            safeRecordFailure(request, ex, elapsed(started));
            throw ex;
        }
    }

    public Map<String, Long> debugCounters() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }

    private LlmCallResult executeGateway(LlmCallRequest request) throws LlmInvokeException {
        if (request.routeRequest() != null) {
            LlmRouteResult routed = platformRouter.invoke(request.routeRequest().withMeasurementContext(request.measurementContext()));
            return LlmCallResult.routed(routed);
        }
        if (request.modelConfig() == null) {
            throw new IllegalArgumentException("modelConfig is required for direct LLM call");
        }
        return LlmCallResult.direct(llmInvoker.invoke(request.prompt(), request.modelConfig()));
    }

    private LlmCallResult executeLegacyLimiter(LlmCallRequest request) {
        if (request.legacyPlatformConfig() == null) {
            throw new IllegalArgumentException("legacyPlatformConfig is required for legacy limiter calls");
        }
        if (!platformRateLimiterService.tryAcquire(request.legacyPlatformConfig(), request.legacyTokenCost())) {
            throw new BizException(429, "Platform limited: " + request.legacyPlatformCode());
        }
        try (PlatformConcurrencyLimiterService.Permit ignored =
                     platformConcurrencyLimiterService.acquire(request.legacyPlatformConfig())) {
            return executeLegacyHttp(request);
        }
    }

    private LlmCallResult executeLegacyHttp(LlmCallRequest request) {
        long started = System.currentTimeMillis();
        try {
            LlmHttpClient.HttpResponse response = llmHttpClient.postJson(
                    normalizeChatCompletionsUrl(request.legacyApiUrl()),
                    legacyHeaders(request.legacyApiKey()),
                    legacyPayload(request),
                    request.legacyConnectTimeoutMs(),
                    request.legacyRequestTimeoutMs()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmHttpErrorException(
                        response.statusCode(),
                        "Model API HTTP " + response.statusCode() + ": " + safeSnippet(response.body()),
                        extractProviderErrorCode(response.body()),
                        RetryAfterParser.parse(response.headers()),
                        classifyHttpStatus(response.statusCode())
                );
            }
            return LlmCallResult.raw(
                    response.body(),
                    request.legacyPlatformCode(),
                    request.legacyPlatformName(),
                    request.legacyChannel(),
                    request.legacyModelId(),
                    Math.max(1L, System.currentTimeMillis() - started),
                    request.requestCount()
            );
        } catch (BizException ex) {
            throw ex;
        } catch (LlmHttpErrorException ex) {
            throw new BizException(ex.httpStatusCode() == null ? 500 : ex.httpStatusCode(), ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new BizException(500, "Model API invoke failed: " + ex.getMessage());
        }
    }

    private Map<String, String> legacyHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);
        return headers;
    }

    private String legacyPayload(LlmCallRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.legacyModelId());
        payload.put("temperature", request.legacyTemperature());
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.legacySystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.legacySystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.legacyUserPrompt()));
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

    private String safeSnippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    private void incrementCounter(LlmCallRequest request) {
        String key = request.feature() + "|" + request.routingStrategy() + "|" + request.governanceStack();
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private void safeRecordSuccess(LlmCallRequest request, LlmCallResult result, long totalMs) {
        try {
            recordSuccess(request, result, totalMs);
        } catch (Exception ignored) {
            // Measurement must never change LLM call control flow.
        }
    }

    private void safeRecordFailure(LlmCallRequest request, Throwable ex, long totalMs) {
        try {
            recordFailure(request, ex, totalMs);
        } catch (Exception ignored) {
            // Measurement must never change LLM call control flow.
        }
    }

    private void recordSuccess(LlmCallRequest request, LlmCallResult result, long totalMs) {
        measurementCollector.recordObservation(new LlmMeasurementEvent(
                request.measurementContext(),
                request.feature(),
                result.platformCode(),
                result.platformName(),
                result.modelId(),
                result.modelName(),
                request.governanceStack(),
                request.routingStrategy(),
                request.waitSemantics(),
                "success",
                null,
                null,
                null,
                null,
                null,
                result.requestCount(),
                null,
                request.governanceStack() == LlmGovernanceStack.LEGACY_LIMITER ? result.durationMs() : null,
                totalMs,
                result.invokeResult() == null ? null : result.invokeResult().promptTokens(),
                result.invokeResult() == null ? null : result.invokeResult().completionTokens(),
                LocalDateTime.now()
        ));
        recordLegacyWaiters(request, null);
    }

    private void recordFailure(LlmCallRequest request, Throwable ex, long totalMs) {
        StructuredFailure failure = structuredFailure(ex);
        measurementCollector.recordObservation(new LlmMeasurementEvent(
                request.measurementContext(),
                request.feature(),
                platformCode(request),
                platformName(request),
                modelId(request),
                modelName(request),
                request.governanceStack(),
                request.routingStrategy(),
                request.waitSemantics(),
                "failure",
                failure.errorCategory(),
                failure.httpStatusCode(),
                failure.providerErrorCode(),
                failure.retryAfterMs(),
                failure.failureKind(),
                failure.requestCount() == null ? request.requestCount() : failure.requestCount(),
                null,
                null,
                totalMs,
                null,
                null,
                LocalDateTime.now()
        ));
        recordLegacyWaiters(request, failure.errorCategory());
    }

    private void recordLegacyWaiters(LlmCallRequest request, LlmErrorCategory category) {
        if (request.governanceStack() != LlmGovernanceStack.LEGACY_LIMITER) {
            return;
        }
        measurementCollector.recordCapacitySignal(new com.huanjing.geo.common.llm.measurement.LlmCapacitySignal(
                request.measurementContext(),
                request.feature(),
                request.legacyPlatformCode(),
                request.governanceStack(),
                category,
                0L,
                0L,
                0L,
                0L,
                platformConcurrencyLimiterService.waiterCount(request.legacyPlatformCode())
        ));
    }

    private StructuredFailure structuredFailure(Throwable ex) {
        LlmStructuredException structured = findStructured(ex);
        LlmRouteException routeException = findRouteException(ex);
        if (structured != null) {
            return new StructuredFailure(
                    structured.httpStatusCode(),
                    structured.providerErrorCode(),
                    structured.retryAfterMs(),
                    structured.errorCategory(),
                    routeException == null ? null : routeException.failureKind().name(),
                    routeException == null ? null : routeException.requestCount()
            );
        }
        if (routeException != null) {
            LlmErrorCategory category = switch (routeException.failureKind()) {
                case ALL_PERMIT_BUSY -> LlmErrorCategory.PERMIT_BUSY;
                case ALL_RATE_LIMITED -> LlmErrorCategory.INTERNAL_RATE_LIMITED;
                case NO_CANDIDATE -> LlmErrorCategory.CONFIG_ERROR;
                default -> LlmErrorCategory.INVOKE_FAILED;
            };
            return new StructuredFailure(null, null, null, category,
                    routeException.failureKind().name(), routeException.requestCount());
        }
        if (ex instanceof BizException bizException && bizException.getCode() == 429) {
            return new StructuredFailure(429, null, null, LlmErrorCategory.INTERNAL_RATE_LIMITED, null, null);
        }
        if (containsTimeout(ex)) {
            return new StructuredFailure(null, null, null, LlmErrorCategory.TIMEOUT, null, null);
        }
        if (ex instanceof IllegalArgumentException) {
            return new StructuredFailure(null, null, null, LlmErrorCategory.CONFIG_ERROR, null, null);
        }
        return new StructuredFailure(null, null, null, LlmErrorCategory.INVOKE_FAILED, null, null);
    }

    private LlmStructuredException findStructured(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof LlmStructuredException structured) {
                return structured;
            }
            current = current.getCause();
        }
        return null;
    }

    private LlmRouteException findRouteException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof LlmRouteException routeException) {
                return routeException;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean containsTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsed(long started) {
        return Math.max(1L, System.currentTimeMillis() - started);
    }

    private LlmErrorCategory classifyHttpStatus(int statusCode) {
        if (statusCode == 429) {
            return LlmErrorCategory.PLATFORM_429;
        }
        if (statusCode >= 500) {
            return LlmErrorCategory.HTTP_5XX;
        }
        return LlmErrorCategory.INVOKE_FAILED;
    }

    private String extractProviderErrorCode(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            var root = objectMapper.readTree(body);
            var error = root.path("error");
            var code = error.path("code");
            if (code.isTextual()) {
                return code.asText();
            }
            var type = error.path("type");
            if (type.isTextual()) {
                return type.asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String platformCode(LlmCallRequest request) {
        if (request.modelConfig() != null) {
            return request.modelConfig().platformCode();
        }
        return request.legacyPlatformCode();
    }

    private String platformName(LlmCallRequest request) {
        if (request.modelConfig() != null) {
            return request.modelConfig().platformName();
        }
        return request.legacyPlatformName();
    }

    private String modelId(LlmCallRequest request) {
        if (request.modelConfig() != null) {
            return request.modelConfig().modelId();
        }
        return request.legacyModelId();
    }

    private String modelName(LlmCallRequest request) {
        return request.modelConfig() == null ? null : request.modelConfig().modelName();
    }

    private record StructuredFailure(Integer httpStatusCode,
                                     String providerErrorCode,
                                     Long retryAfterMs,
                                     LlmErrorCategory errorCategory,
                                     String failureKind,
                                     Integer requestCount) {
    }
}
