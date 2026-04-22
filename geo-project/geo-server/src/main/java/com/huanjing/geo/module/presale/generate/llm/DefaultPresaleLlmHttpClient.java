package com.huanjing.geo.module.presale.generate.llm;

import com.huanjing.geo.common.util.HttpClientUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultPresaleLlmHttpClient implements PresaleLlmHttpClient {
    @Override
    public HttpResponse postJson(String url,
                                 Map<String, String> headers,
                                 String body,
                                 int connectTimeoutMs,
                                 int requestTimeoutMs) throws Exception {
        HttpClientUtil.HttpResult response = HttpClientUtil.postJson(
                url, headers, body, connectTimeoutMs, requestTimeoutMs
        );
        return new HttpResponse(response.statusCode(), response.body());
    }
}

