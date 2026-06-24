package com.huanjing.geo.common.llm;

import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Single transport abstraction for OpenAI-compatible chat completions requests.
 */
public interface LlmHttpClient {

    HttpResponse postJson(String url,
                          Map<String, String> headers,
                          String body,
                          int connectTimeoutMs,
                          int requestTimeoutMs) throws Exception;

    record HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
        public HttpResponse(int statusCode, String body) {
            this(statusCode, body, Collections.emptyMap());
        }

        public HttpResponse {
            if (headers == null) {
                headers = Collections.emptyMap();
            } else {
                headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            }
        }
    }
}
