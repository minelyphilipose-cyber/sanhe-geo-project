package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.llm.LlmHttpClient;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodecRequest;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.PollPayloadProtector;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ModelDiagnosticGateway {

    private final LlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PollPayloadProtector payloadProtector;
    private final PlatformCredentialService credentialService;
    private final Map<IntegrationType, WebSearchCodec> webSearchCodecs;

    public ModelDiagnosticGateway(LlmHttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  PollPayloadProtector payloadProtector,
                                  PlatformCredentialService credentialService,
                                  List<WebSearchCodec> codecs) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.payloadProtector = payloadProtector;
        this.credentialService = credentialService;
        EnumMap<IntegrationType, WebSearchCodec> registered = new EnumMap<>(IntegrationType.class);
        for (WebSearchCodec codec : codecs) {
            WebSearchCodec previous = registered.put(codec.integrationType(), codec);
            if (previous != null) {
                throw new IllegalStateException("Duplicate diagnostic codec for " + codec.integrationType());
            }
        }
        this.webSearchCodecs = Map.copyOf(registered);
    }

    public ModelDiagnosticProviderResult execute(ModelDiagnosticProviderRequest request) {
        long startedNanos = System.nanoTime();
        String requestBody = null;
        String sanitizedRequest = null;
        try {
            RequestEncoding encoding = encode(request);
            requestBody = objectMapper.writeValueAsString(encoding.body());
            sanitizedRequest = payloadProtector.sanitize(requestBody);
            int requestTimeoutMs = remainingTimeout(request);
            String credential = credentialService.resolvePrimaryCredentialStrict(
                    request.platform().primaryCredentialRef(), request.platform().encryptedApiKey());
            if (!StringUtils.hasText(credential)) {
                throw failure(ErrorCategory.AUTHENTICATION, null,
                        "Configured primary credential is unavailable",
                        sanitizedRequest, null, null);
            }

            LlmHttpClient.HttpResponse response = httpClient.postJson(
                    encoding.endpointUrl(),
                    headers(credential),
                    requestBody,
                    Math.min(request.platform().connectTimeoutMs(), requestTimeoutMs),
                    requestTimeoutMs
            );
            String sanitizedResponse = payloadProtector.sanitize(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure(classifyHttpStatus(response.statusCode()), response.statusCode(),
                        providerHttpFailure(response.statusCode(), response.body()),
                        sanitizedRequest, sanitizedResponse, null);
            }

            return decode(request, encoding.codecRequest(), response,
                    sanitizedRequest, sanitizedResponse, elapsedMillis(startedNanos));
        } catch (ModelDiagnosticExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            ErrorCategory category = classifyException(ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw failure(category, null, safeMessage(ex),
                    sanitizedRequest, null, ex);
        }
    }

    private RequestEncoding encode(ModelDiagnosticProviderRequest request) {
        IntegrationType integrationType = request.platform().integrationType();
        if (integrationType == IntegrationType.OPENAI_CHAT) {
            return new RequestEncoding(chatCompletionsUrl(request.platform().endpointUrl()),
                    encodeOpenAiChat(request), null);
        }
        WebSearchCodec codec = webSearchCodecs.get(integrationType);
        if (codec == null) {
            throw failure(ErrorCategory.INVALID_REQUEST, null,
                    "No diagnostic codec registered for " + integrationType,
                    null, null, null);
        }
        List<WebSearchMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new WebSearchMessage("system", request.systemPrompt()));
        }
        messages.addAll(request.messages());
        WebSearchCodecRequest codecRequest = new WebSearchCodecRequest(
                request.platform().requestedModelId(), messages,
                request.platform().providerConfigSnapshotJson());
        return new RequestEncoding(request.platform().endpointUrl(),
                codec.encode(codecRequest), codecRequest);
    }

    private ObjectNode encodeOpenAiChat(ModelDiagnosticProviderRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.platform().requestedModelId());
        ArrayNode messages = root.putArray("messages");
        if (StringUtils.hasText(request.systemPrompt())) {
            addMessage(messages, "system", request.systemPrompt());
        }
        request.messages().forEach(message -> addMessage(messages, message.role(), message.content()));
        applyOpenAiOptions(root, request.platform().providerConfigSnapshotJson());
        return root;
    }

    private void applyOpenAiOptions(ObjectNode request, String providerConfigJson) {
        try {
            JsonNode config = objectMapper.readTree(providerConfigJson);
            JsonNode temperature = config.get("temperature");
            if (temperature != null && temperature.isNumber()) {
                request.set("temperature", temperature);
            }
            JsonNode maxTokens = config.has("max_tokens")
                    ? config.get("max_tokens") : config.get("maxTokens");
            if (maxTokens != null && maxTokens.canConvertToInt() && maxTokens.asInt() > 0) {
                request.put("max_tokens", maxTokens.asInt());
            }
        } catch (Exception ex) {
            throw failure(ErrorCategory.INVALID_REQUEST, null,
                    "Platform providerConfigJson is invalid", null, null, ex);
        }
    }

    private ModelDiagnosticProviderResult decode(ModelDiagnosticProviderRequest request,
                                                  WebSearchCodecRequest codecRequest,
                                                  LlmHttpClient.HttpResponse exchange,
                                                  String sanitizedRequest,
                                                  String sanitizedResponse,
                                                  long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(exchange.body());
            if (request.platform().integrationType() == IntegrationType.OPENAI_CHAT) {
                return decodeOpenAiChat(root, exchange, sanitizedRequest, sanitizedResponse, durationMs);
            }
            WebSearchCodec codec = webSearchCodecs.get(request.platform().integrationType());
            WebSearchResponse response = codec.decode(root, codecRequest);
            if (!StringUtils.hasText(response.answer()) && !response.generationSkipped()) {
                throw new IllegalArgumentException("Provider response does not contain an answer");
            }
            return new ModelDiagnosticProviderResult(
                    firstText(response.providerRequestId(), requestId(exchange.headers())),
                    response.responseModelId(), response.answer(), exchange.statusCode(),
                    response.searchStatus(), response.searchEvidence(), response.sources(),
                    response.citations(), response.usage(), response.finishReason(),
                    sanitizedRequest, sanitizedResponse, durationMs);
        } catch (ModelDiagnosticExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(ErrorCategory.PARSE_ERROR, exchange.statusCode(),
                    "Failed to parse provider response", sanitizedRequest, sanitizedResponse, ex);
        }
    }

    private ModelDiagnosticProviderResult decodeOpenAiChat(JsonNode root,
                                                           LlmHttpClient.HttpResponse exchange,
                                                           String sanitizedRequest,
                                                           String sanitizedResponse,
                                                           long durationMs) {
        JsonNode first = root.path("choices").path(0);
        String answer = contentText(first.path("message").get("content"));
        if (!StringUtils.hasText(answer)) {
            answer = first.path("text").asText(null);
        }
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("Provider response does not contain an answer");
        }
        Map<String, Object> usage = root.path("usage").isObject()
                ? objectMapper.convertValue(root.path("usage"), new TypeReference<LinkedHashMap<String, Object>>() { })
                : Map.of();
        return new ModelDiagnosticProviderResult(
                firstText(root.path("id").asText(null), requestId(exchange.headers())),
                root.path("model").asText(null), answer, exchange.statusCode(),
                SearchStatus.NOT_CONFIRMED, List.of(), List.of(), List.of(), usage,
                first.path("finish_reason").asText(null), sanitizedRequest, sanitizedResponse, durationMs);
    }

    private String contentText(JsonNode content) {
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder value = new StringBuilder();
            for (JsonNode item : content) {
                String text = item.path("text").asText(null);
                if (StringUtils.hasText(text)) {
                    value.append(text);
                }
            }
            return value.isEmpty() ? null : value.toString();
        }
        return null;
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", content);
    }

    private int remainingTimeout(ModelDiagnosticProviderRequest request) {
        long remaining = Duration.between(LocalDateTime.now(), request.deadlineAt()).toMillis();
        if (remaining < 1_000L) {
            throw failure(ErrorCategory.TIMEOUT, null,
                    "Diagnostic deadline elapsed before provider call", null, null, null);
        }
        return (int) Math.min(request.platform().requestTimeoutMs(),
                Math.min(remaining, Integer.MAX_VALUE));
    }

    private Map<String, String> headers(String credential) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + credential);
        headers.put("Content-Type", "application/json");
        headers.put("api-key", credential);
        headers.put("x-api-key", credential);
        return headers;
    }

    private String requestId(Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (("x-request-id".equals(key) || "request-id".equals(key))
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String chatCompletionsUrl(String endpoint) {
        String value = endpoint.trim();
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        return value.endsWith("/") ? value + "chat/completions" : value + "/chat/completions";
    }

    private ErrorCategory classifyHttpStatus(int status) {
        if (status == 401) return ErrorCategory.AUTHENTICATION;
        if (status == 403) return ErrorCategory.PERMISSION;
        if (status == 429) return ErrorCategory.RATE_LIMIT;
        if (status >= 500) return ErrorCategory.SERVER_ERROR;
        return ErrorCategory.INVALID_REQUEST;
    }

    private String providerHttpFailure(int status, String responseBody) {
        String prefix = "Provider returned HTTP " + status;
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String code = error.path("code").asText(null);
            if (!StringUtils.hasText(code) || !code.matches("^[A-Za-z0-9._-]{1,80}$")) {
                return prefix;
            }
            if ("ModelNotOpen".equalsIgnoreCase(code)) {
                return prefix + " (ModelNotOpen): activate the requested model in the provider console";
            }
            return prefix + " (" + code + ")";
        } catch (Exception ignored) {
            return prefix;
        }
    }

    private ErrorCategory classifyException(Exception ex) {
        if (ex instanceof InterruptedException) return ErrorCategory.WORKER_INTERRUPTED;
        if (ex instanceof HttpTimeoutException) return ErrorCategory.TIMEOUT;
        if (ex instanceof ConnectException) return ErrorCategory.NETWORK;
        return ErrorCategory.NETWORK;
    }

    private ModelDiagnosticExecutionException failure(ErrorCategory category,
                                                      Integer status,
                                                      String message,
                                                      String sanitizedRequest,
                                                      String sanitizedResponse,
                                                      Throwable cause) {
        return new ModelDiagnosticExecutionException(
                category, status, message, sanitizedRequest, sanitizedResponse, cause);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private record RequestEncoding(String endpointUrl,
                                   ObjectNode body,
                                   WebSearchCodecRequest codecRequest) {
    }
}
