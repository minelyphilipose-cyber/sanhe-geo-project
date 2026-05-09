package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmInvokerTest {

    @Test
    void invoke_retriesOnceThenReturnsTokenUsage() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient(
                new RuntimeException("temporary timeout"),
                new LlmHttpClient.HttpResponse(200,
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":11}}")
        );
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        LlmInvokeResult result = invoker.invoke("hello", config(0, 10_000, 30_000, 1));

        assertEquals("ok", result.responseText());
        assertEquals(7, result.promptTokens());
        assertEquals(11, result.completionTokens());
        assertEquals(1, result.retryCount());
        assertTrue(result.isRetriedSuccess());
        assertEquals(2, httpClient.callCount);
    }

    @Test
    void invoke_usesDefaultTimeoutsWhenConfigOmitsThem() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient(
                new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        );
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        invoker.invoke("hello", config(0, null, null, 0));

        assertEquals(LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS, httpClient.lastConnectTimeoutMs);
        assertEquals(LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS, httpClient.lastRequestTimeoutMs);
    }

    @Test
    void modelConfig_rejectsRequestTimeoutOverMaxLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> config(0, 10_000, 60_001, 0));

        assertTrue(ex.getMessage().contains("requestTimeoutMs"));
    }

    private LlmModelConfig config(int rateLimitQps,
                                  Integer connectTimeoutMs,
                                  Integer requestTimeoutMs,
                                  Integer maxRetry) {
        return new LlmModelConfig(
                "openai",
                "OpenAI",
                "gpt-test",
                "GPT Test",
                "https://api.example.com/v1",
                "secret",
                "system",
                0.2D,
                connectTimeoutMs,
                requestTimeoutMs,
                maxRetry,
                rateLimitQps <= 0 ? 1000 : rateLimitQps,
                128,
                false
        );
    }

    private static final class FakeHttpClient implements LlmHttpClient {
        private final Object[] responses;
        private int callCount;
        private int lastConnectTimeoutMs;
        private int lastRequestTimeoutMs;

        private FakeHttpClient(Object... responses) {
            this.responses = responses;
        }

        @Override
        public HttpResponse postJson(String url,
                                     Map<String, String> headers,
                                     String body,
                                     int connectTimeoutMs,
                                     int requestTimeoutMs) throws Exception {
            this.lastConnectTimeoutMs = connectTimeoutMs;
            this.lastRequestTimeoutMs = requestTimeoutMs;
            Object response = responses[Math.min(callCount, responses.length - 1)];
            callCount++;
            if (response instanceof Exception ex) {
                throw ex;
            }
            return (HttpResponse) response;
        }
    }
}
