package com.huanjing.geo.common.llm;

import java.util.Map;

/**
 * Replaceable HTTP abstraction for LLM unit tests.
 */
public interface LlmHttpClient {

    HttpResponse postJson(String url,
                          Map<String, String> headers,
                          String body,
                          int connectTimeoutMs,
                          int requestTimeoutMs) throws Exception;

    record HttpResponse(int statusCode, String body) {
    }
}
