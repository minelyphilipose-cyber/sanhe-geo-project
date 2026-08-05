package com.huanjing.geo.module.presale.generate.web.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodecRequest;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;
import com.huanjing.geo.module.presale.generate.web.PresaleWebEndpointPolicy;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared transport only; business retries are owned exclusively by PresaleWebQueryInvoker. */
@Component
public class CodecBackedProviderSupport {
    private final LlmHttpClient httpClient;
    private final PlatformCredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final Map<com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType, WebSearchCodec> codecs;

    public CodecBackedProviderSupport(LlmHttpClient httpClient,
                                      PlatformCredentialService credentialService,
                                      ObjectMapper objectMapper,
                                      List<WebSearchCodec> codecList) {
        this.httpClient = httpClient;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
        Map<com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType, WebSearchCodec> map = new LinkedHashMap<>();
        for (WebSearchCodec codec : codecList) {
            if (map.put(codec.integrationType(), codec) != null) {
                throw new IllegalStateException("Duplicate web-search codec for " + codec.integrationType());
            }
        }
        this.codecs = Map.copyOf(map);
    }

    public PresaleWebProviderAttempt execute(ResolvedCompanionExecutionConfig config,
                                             String userPrompt) throws PresaleWebProviderException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("presale web QUERY interrupted before provider call");
        }
        WebSearchCodec codec = codecs.get(config.integrationType());
        if (codec == null) {
            throw failure("WEB_CODEC_MISSING", "No codec for " + config.integrationType(), false, null, false, null);
        }
        try {
            PresaleWebEndpointPolicy.validate(config.integrationType(), config.endpointUrl());
        } catch (IllegalArgumentException ex) {
            throw failure("ENDPOINT_REJECTED", ex.getMessage(), false, null, false, ex);
        }
        String credential = credentialService.resolveCredential(config.credentialRef(), null);
        if (!StringUtils.hasText(credential)) {
            throw failure("AUTHENTICATION", "Companion credential is unavailable", false, null, false, null);
        }
        WebSearchCodecRequest codecRequest = new WebSearchCodecRequest(
                config.modelId(), List.of(new WebSearchMessage("user", userPrompt)), config.providerConfigJson());
        String body;
        try {
            body = objectMapper.writeValueAsString(codec.encode(codecRequest));
        } catch (Exception ex) {
            throw failure("INVALID_REQUEST", "Failed to encode web QUERY request", false, null, false, ex);
        }
        long start = System.nanoTime();
        try {
            LlmHttpClient.HttpResponse exchange = httpClient.postJson(
                    config.endpointUrl(), headers(config.integrationType(), credential), body,
                    config.connectTimeoutMs(), config.requestTimeoutMs());
            String requestId = requestId(exchange.headers());
            if (exchange.statusCode() < 200 || exchange.statusCode() >= 300) {
                boolean retryable = exchange.statusCode() == 429 || exchange.statusCode() >= 500;
                throw failure("HTTP_" + exchange.statusCode(),
                        "Web companion returned HTTP " + exchange.statusCode(), retryable,
                        requestId, true, null);
            }
            try {
                JsonNode root = objectMapper.readTree(exchange.body());
                WebSearchResponse response = codec.decode(root, codecRequest);
                if (!StringUtils.hasText(response.providerRequestId()) && StringUtils.hasText(requestId)) {
                    response = new WebSearchResponse(requestId, response.requestedModelId(), response.responseModelId(),
                            response.answer(), response.searchStatus(), response.generationSkipped(),
                            response.searchEvidence(), response.sources(), response.citations(), response.usage(),
                            response.finishReason());
                }
                return new PresaleWebProviderAttempt(response, elapsedMs(start));
            } catch (Exception ex) {
                throw failure("PARSE_ERROR", "Failed to decode web QUERY response", true,
                        requestId, true, ex);
            }
        } catch (PresaleWebProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            boolean timeout = ex instanceof HttpTimeoutException;
            boolean retryable = timeout || ex instanceof ConnectException || ex.getCause() instanceof ConnectException;
            throw failure(timeout ? "TIMEOUT" : "NETWORK", safeMessage(ex), retryable,
                    null, true, ex);
        }
    }

    private Map<String, String> headers(com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType type,
                                        String credential) {
        String header = type == com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType.MIMO_CHAT_WEB
                ? "api-key" : "Authorization";
        String value = type == com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType.MIMO_CHAT_WEB
                ? credential : "Bearer " + credential;
        return Map.of(header, value, "Content-Type", "application/json");
    }

    private String requestId(Map<String, List<String>> headers) {
        if (headers == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (("x-request-id".equals(key) || "request-id".equals(key))
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private long elapsedMs(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private PresaleWebProviderException failure(String code, String message, boolean retryable,
                                                String requestId, boolean physicalCallOccurred, Throwable cause) {
        return new PresaleWebProviderException(code, message, retryable, requestId, physicalCallOccurred, cause);
    }
}
