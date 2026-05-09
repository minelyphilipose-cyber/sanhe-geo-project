package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MeititejiaClient {

    private static final int DEFAULT_LIMIT = 200;

    private final MeititejiaProperties properties;
    private final PlatformCredentialService platformCredentialService;
    private final ObjectMapper objectMapper;
    private final SimpleRateLimiter rateLimiter = new SimpleRateLimiter();

    public JsonNode userInfo() {
        return postSigned("userInfo", Map.of());
    }

    public JsonNode listResources(MeititejiaResourceType type, int page, int limit, Long id, Long uptime) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", Math.max(page, 1));
        params.put("limit", limit <= 0 ? DEFAULT_LIMIT : limit);
        if (id != null) {
            params.put("id", id);
        }
        if (uptime != null) {
            params.put("uptime", uptime);
        }
        return postSigned(type.listPath(), params);
    }

    public JsonNode getIds(MeititejiaResourceType type) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", 1);
        params.put("type", type.idsType());
        return postSigned("get_ids", params);
    }

    public JsonNode createNewsMediaOrder(NewsMediaOrderRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", request.title());
        params.put("content", request.content());
        params.put("mid", request.mediaId());
        params.put("no", request.externalNo());
        params.put("remark", request.remark());
        params.put("published_at", request.publishedAt());
        params.put("saling_price", request.salingPrice());
        return postSigned(MeititejiaResourceType.NEWS_MEDIA.createOrderPath(), params);
    }

    public JsonNode queryOrders(MeititejiaResourceType type, List<String> externalNos) {
        String nostr = externalNos == null ? "" : externalNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(nostr)) {
            throw new IllegalArgumentException("externalNos is required");
        }
        return postSigned(type.queryOrderPath(), Map.of("nostr", nostr));
    }

    protected JsonNode postSigned(String path, Map<String, ?> params) {
        requireEnabledOrMock();
        Map<String, String> signed = signedParametersForRequest(params);
        String body = MeititejiaSigner.formBody(signed);
        return executeWithRetry(path, body);
    }

    protected Map<String, String> signedParametersForRequest(Map<String, ?> params) {
        return MeititejiaSigner.signedParameters(
                encodeStringValues(params),
                requireSecretId(),
                requireSecretKey(),
                Instant.now().getEpochSecond()
        );
    }

    /**
     * Builds the encoded business-field view used for request_payload audit
     * storage. This deliberately does not add secret_id, timestamp, or
     * signature, so it can be persisted without signing credentials.
     */
    public Map<String, String> buildAuditPayload(Map<String, ?> params) {
        Map<String, String> payload = new LinkedHashMap<>();
        encodeStringValues(params).forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            String normalizedValue = MeititejiaSigner.normalizeValue(value);
            if (StringUtils.hasText(key)
                    && normalizedValue != null
                    && !MeititejiaSigner.SIGNATURE.equals(normalizedKey)
                    && !"sign".equals(normalizedKey)) {
                payload.put(key, normalizedValue);
            }
        });
        return payload;
    }

    private JsonNode executeWithRetry(String path, String body) {
        // Only vendor/API failures are retried. Configuration and validation
        // errors from parameter preparation are allowed to surface directly.
        int maxAttempts = Math.max(properties.getRetryMaxAttempts(), 1);
        MeititejiaApiException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return doPostSigned(path, body);
            } catch (MeititejiaApiException ex) {
                last = ex;
                if (!ex.isRetryable() || attempt >= maxAttempts) {
                    throw ex;
                }
                backoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("Meititejia request failed") : last;
    }

    private JsonNode doPostSigned(String path, String body) {
        try {
            throttle();
            HttpClientUtil.HttpResult result = postForm(
                    endpoint(path),
                    Map.of("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"),
                    body,
                    properties.getConnectTimeoutMs(),
                    properties.getRequestTimeoutMs()
            );
            int status = result.statusCode();
            if (status < 200 || status >= 300) {
                throw apiException(status, null, null, path, result.body(), retryableHttp(status), null);
            }
            JsonNode root = objectMapper.readTree(StringUtils.hasText(result.body()) ? result.body() : "{}");
            JsonNode codeNode = root.get("code");
            if (codeNode != null && codeNode.canConvertToInt() && codeNode.asInt() != 200) {
                throw apiException(status, codeNode.asInt(), text(root, "msg"), path, result.body(), false, null);
            }
            return root;
        } catch (MeititejiaApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw apiException(0, null, ex.getMessage(), path, null, true, ex);
        }
    }

    /**
     * Produces a copy suitable for persisting to request_payload. It removes
     * replayable signing credentials while keeping business fields for audit.
     */
    public static Map<String, String> sanitizeForAudit(Map<String, String> signed) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (signed == null) {
            return sanitized;
        }
        signed.forEach((key, value) -> {
            String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if (!MeititejiaSigner.SECRET_ID.equals(normalized)
                    && !MeititejiaSigner.TIMESTAMP.equals(normalized)
                    && !MeititejiaSigner.SIGNATURE.equals(normalized)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    protected HttpClientUtil.HttpResult postForm(String url,
                                                 Map<String, String> headers,
                                                 String body,
                                                 int connectTimeoutMs,
                                                 int requestTimeoutMs) throws Exception {
        return HttpClientUtil.request("POST", url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    protected void throttle() {
        rateLimiter.acquire(Math.max(properties.getRateLimitQps(), 1));
    }

    private Map<String, ?> encodeStringValues(Map<String, ?> params) {
        Map<String, Object> encoded = new LinkedHashMap<>();
        if (params == null) {
            return encoded;
        }
        params.forEach((key, value) -> {
            if (value instanceof String text) {
                encoded.put(key, MeititejiaSigner.phpUrlencode(text));
            } else {
                encoded.put(key, value);
            }
        });
        return encoded;
    }

    private void backoff(int attempt) {
        long delay = Math.max(properties.getRetryBackoffMs(), 0L);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meititejia retry backoff interrupted", ex);
        }
    }

    private boolean retryableHttp(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private MeititejiaApiException apiException(int httpStatus,
                                                Integer bizCode,
                                                String bizMsg,
                                                String path,
                                                String responseBody,
                                                boolean retryable,
                                                Throwable cause) {
        return new MeititejiaApiException(httpStatus, bizCode, bizMsg, path, responseBody, retryable, cause);
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private void requireEnabledOrMock() {
        if (!properties.isEnabled() && !properties.isMockMode()) {
            throw new IllegalStateException("Meititejia client is disabled");
        }
    }

    private String requireSecretId() {
        if (!StringUtils.hasText(properties.getSecretId())) {
            throw new IllegalStateException("geo.meititejia.secret-id is required");
        }
        return properties.getSecretId().trim();
    }

    private String requireSecretKey() {
        String value = null;
        if (platformCredentialService != null) {
            value = platformCredentialService.resolveApiKey(
                    "meititejia",
                    properties.getSecretKeyRef(),
                    properties.getSecretKey()
            );
        }
        if (!StringUtils.hasText(value)) {
            value = properties.getSecretKey();
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("geo.meititejia.secret-key-ref or secret-key is required");
        }
        return value.trim();
    }

    private String endpoint(String path) {
        String baseUrl = Objects.requireNonNullElse(properties.getBaseUrl(), "").trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return baseUrl + "/" + normalizedPath;
    }

    public record NewsMediaOrderRequest(
            String title,
            String content,
            Long mediaId,
            String externalNo,
            String remark,
            String publishedAt,
            BigDecimal salingPrice
    ) {
    }

    private static final class SimpleRateLimiter {
        private long nextAllowedAtMillis;

        synchronized void acquire(int qps) {
            long now = System.currentTimeMillis();
            long waitMillis = nextAllowedAtMillis - now;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Meititejia rate limiter interrupted", ex);
                }
            }
            long intervalMillis = Math.max(1000L / Math.max(qps, 1), 1L);
            nextAllowedAtMillis = Math.max(now, nextAllowedAtMillis) + intervalMillis;
        }
    }
}
