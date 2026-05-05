package com.huanjing.geo.common.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HttpClientUtil {

    private static final ConcurrentHashMap<Integer, HttpClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    private HttpClientUtil() {}

    public static HttpResult postJson(String url,
                                      Map<String, String> headers,
                                      String body,
                                      int connectTimeoutMs,
                                      int requestTimeoutMs) throws Exception {
        return request("POST", url, headers, body, connectTimeoutMs, requestTimeoutMs);
    }

    public static HttpResult get(String url,
                                 Map<String, String> headers,
                                 int connectTimeoutMs,
                                 int requestTimeoutMs) throws Exception {
        return request("GET", url, headers, null, connectTimeoutMs, requestTimeoutMs);
    }

    public static HttpResult request(String method,
                                     String url,
                                     Map<String, String> headers,
                                     String body,
                                     int connectTimeoutMs,
                                     int requestTimeoutMs) throws Exception {
        HttpClient client = clientByTimeout(connectTimeoutMs);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        if (requestTimeoutMs > 0) {
            builder.timeout(Duration.ofMillis(Math.max(requestTimeoutMs, 1000)));
        }

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        String upperMethod = method == null ? "GET" : method.trim().toUpperCase();
        if ("POST".equals(upperMethod)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("PUT".equals(upperMethod)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("PATCH".equals(upperMethod)) {
            builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("DELETE".equals(upperMethod)) {
            if (body == null) {
                builder.DELETE();
            } else {
                builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body));
            }
        } else {
            builder.GET();
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResult(
                response.statusCode(),
                response.body(),
                response.headers().map()
        );
    }

    private static HttpClient clientByTimeout(int connectTimeoutMs) {
        int timeout = Math.max(connectTimeoutMs, 1000);
        return CLIENT_CACHE.computeIfAbsent(timeout, ms -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ms))
                .build());
    }

    public record HttpResult(int statusCode, String body, Map<String, List<String>> headers) {
        public HttpResult {
            if (headers == null) {
                headers = Collections.emptyMap();
            } else {
                headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            }
        }
    }
}
