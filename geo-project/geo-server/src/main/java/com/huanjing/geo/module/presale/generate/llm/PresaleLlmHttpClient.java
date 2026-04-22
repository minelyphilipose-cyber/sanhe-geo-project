package com.huanjing.geo.module.presale.generate.llm;

import java.util.Map;

/**
 * 可替换的 HTTP 调用抽象,便于单元测试 mock。
 */
public interface PresaleLlmHttpClient {

    HttpResponse postJson(String url,
                          Map<String, String> headers,
                          String body,
                          int connectTimeoutMs,
                          int requestTimeoutMs) throws Exception;

    record HttpResponse(int statusCode, String body) {
    }
}

