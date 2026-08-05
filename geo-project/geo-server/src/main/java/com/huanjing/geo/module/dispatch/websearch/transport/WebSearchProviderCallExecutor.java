package com.huanjing.geo.module.dispatch.websearch.transport;

import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.entity.PollProviderCall;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSearchProviderCallExecutor {

    private final LlmHttpClient httpClient;
    private final PlatformCredentialService credentialService;
    private final ProviderCallAuditWriter auditWriter;

    public ProviderExchange postJson(WebSearchRequest request, String requestBody) {
        String apiKey = credentialService.resolveCredential(request.profile().primaryCredentialRef(), null);
        if (!StringUtils.hasText(apiKey)) {
            throw new WebSearchProviderException(
                    ErrorCategory.AUTHENTICATION, null,
                    "Credential is unavailable for " + request.profile().primaryCredentialRef(), null);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        int requestTimeoutMs = effectiveRequestTimeout(request, startedAt);
        PollProviderCall call = auditWriter.start(request, requestBody, startedAt);
        try {
            LlmHttpClient.HttpResponse response = httpClient.postJson(
                    request.profile().endpointUrl(),
                    headers(request.profile().integrationType(), apiKey),
                    requestBody,
                    request.profile().connectTimeoutMs(),
                    requestTimeoutMs
            );
            long latencyMs = elapsedMillis(startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ErrorCategory category = classifyHttpStatus(response.statusCode());
                auditWriter.fail(call.getId(), response.statusCode(), response.body(), category,
                        "HTTP_" + response.statusCode(), "Provider returned HTTP " + response.statusCode(),
                        LocalDateTime.now(), latencyMs);
                throw new WebSearchProviderException(category, response.statusCode(),
                        "Provider returned HTTP " + response.statusCode(), null);
            }
            return new ProviderExchange(call.getId(), response.statusCode(), response.body(),
                    response.headers(), latencyMs);
        } catch (WebSearchProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            ErrorCategory category = classifyException(ex);
            long latencyMs = elapsedMillis(startedAt);
            auditWriter.fail(call.getId(), null, null, category, category.name(), ex.getMessage(),
                    LocalDateTime.now(), latencyMs);
            throw new WebSearchProviderException(category, null, ex.getMessage(), ex);
        }
    }

    public void completeSuccess(ProviderExchange exchange, String providerRequestId, String usageJson) {
        auditWriter.succeed(exchange.callId(), exchange.httpStatus(), exchange.responseBody(),
                providerRequestId, usageJson, LocalDateTime.now(), exchange.latencyMs());
    }

    public WebSearchProviderException completeParseFailure(ProviderExchange exchange, Exception cause) {
        auditWriter.fail(exchange.callId(), exchange.httpStatus(), exchange.responseBody(),
                ErrorCategory.PARSE_ERROR, "PARSE_ERROR", cause.getMessage(),
                LocalDateTime.now(), exchange.latencyMs());
        return new WebSearchProviderException(
                ErrorCategory.PARSE_ERROR, exchange.httpStatus(), "Failed to parse provider response", cause);
    }

    private Map<String, String> headers(IntegrationType type, String apiKey) {
        if (type == IntegrationType.MIMO_CHAT_WEB) {
            return Map.of("api-key", apiKey, "Content-Type", "application/json");
        }
        return Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json");
    }

    private int effectiveRequestTimeout(WebSearchRequest request, LocalDateTime startedAt) {
        long remaining = Duration.between(startedAt, request.attemptDeadlineAt()).toMillis();
        if (remaining < 1000) {
            throw new WebSearchProviderException(
                    ErrorCategory.TIMEOUT, null, "Attempt deadline elapsed before provider call", null);
        }
        return (int) Math.min(request.profile().requestTimeoutMs(), Math.min(remaining, Integer.MAX_VALUE));
    }

    private long elapsedMillis(LocalDateTime startedAt) {
        return Math.max(0L, Duration.between(startedAt, LocalDateTime.now()).toMillis());
    }

    private ErrorCategory classifyHttpStatus(int status) {
        if (status == 401) return ErrorCategory.AUTHENTICATION;
        if (status == 403) return ErrorCategory.PERMISSION;
        if (status == 429) return ErrorCategory.RATE_LIMIT;
        if (status >= 500) return ErrorCategory.SERVER_ERROR;
        return ErrorCategory.INVALID_REQUEST;
    }

    private ErrorCategory classifyException(Exception ex) {
        if (ex instanceof HttpTimeoutException) return ErrorCategory.TIMEOUT;
        if (ex instanceof ConnectException) return ErrorCategory.NETWORK;
        return ErrorCategory.NETWORK;
    }
}
