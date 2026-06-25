package com.huanjing.geo.module.presale.generate.llm;

import com.huanjing.geo.common.llm.LlmHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultPresaleLlmHttpClient implements PresaleLlmHttpClient {
    private final LlmHttpClient llmHttpClient;

    @Override
    public HttpResponse postJson(String url,
                                 Map<String, String> headers,
                                 String body,
                                 int connectTimeoutMs,
                                 int requestTimeoutMs) throws Exception {
        LlmHttpClient.HttpResponse response = llmHttpClient.postJson(
                url, headers, body, connectTimeoutMs, requestTimeoutMs
        );
        return new HttpResponse(response.statusCode(), response.body());
    }
}
