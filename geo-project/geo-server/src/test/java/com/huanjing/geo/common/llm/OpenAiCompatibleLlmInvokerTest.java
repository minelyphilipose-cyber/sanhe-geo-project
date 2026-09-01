package com.huanjing.geo.common.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.measurement.LlmErrorCategory;
import com.huanjing.geo.common.llm.measurement.LlmStructuredException;
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
    void invoke_doesNotRetryWhenMaxRetryIsZero() {
        FakeHttpClient httpClient = new FakeHttpClient(
                new RuntimeException("temporary timeout"),
                new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        );
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        assertThrows(LlmInvokeException.class, () -> invoker.invoke("hello", config(0, 10_000, 30_000, 0)));

        assertEquals(1, httpClient.callCount);
    }

    @Test
    void modelConfig_rejectsRequestTimeoutOverMaxLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> config(0, 10_000, 60_001, 0));

        assertTrue(ex.getMessage().contains("requestTimeoutMs"));
    }

    @Test
    void invoke_doesNotRetryExhaustedAccountQuota() {
        FakeHttpClient httpClient = new FakeHttpClient(new LlmHttpClient.HttpResponse(
                429,
                "{\"error\":{\"type\":\"exceeded_current_quota_error\",\"message\":\"insufficient balance\"}}"
        ));
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        LlmInvokeException error = assertThrows(LlmInvokeException.class,
                () -> invoker.invoke("hello", config(0, 10_000, 30_000, 2)));

        assertEquals(1, httpClient.callCount);
        assertEquals(LlmErrorCategory.ACCOUNT_QUOTA_EXHAUSTED, findStructured(error).errorCategory());
    }

    @Test
    void invoke_retriesEmptyResponseOnlyOnce() {
        FakeHttpClient httpClient = new FakeHttpClient(
                new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"),
                new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}")
        );
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        assertThrows(LlmInvokeException.class,
                () -> invoker.invoke("hello", config(0, 10_000, 30_000, 2)));

        assertEquals(2, httpClient.callCount);
    }

    @Test
    void invoke_extractsTextFromContentBlocks() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient(new LlmHttpClient.HttpResponse(
                200,
                "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"O\"},{\"type\":\"text\",\"text\":\"K\"}]}}]}"
        ));
        OpenAiCompatibleLlmInvoker invoker = new OpenAiCompatibleLlmInvoker(httpClient, new ObjectMapper());

        LlmInvokeResult result = invoker.invoke("hello", config(0, 10_000, 30_000, 0));

        assertEquals("OK", result.responseText());
    }

    @Test
    void invoke_disablesThinkingOnlyForKimiK26() throws Exception {
        FakeHttpClient kimiClient = successfulClient();
        FakeHttpClient openAiClient = successfulClient();

        new OpenAiCompatibleLlmInvoker(kimiClient, new ObjectMapper())
                .invoke("hello", config("kimi", "kimi-k2.6", 0, 10_000, 30_000, 0));
        new OpenAiCompatibleLlmInvoker(openAiClient, new ObjectMapper())
                .invoke("hello", config("openai", "gpt-test", 0, 10_000, 30_000, 0));

        ObjectMapper objectMapper = new ObjectMapper();
        var kimiRequest = objectMapper.readTree(kimiClient.lastBody);
        var openAiRequest = objectMapper.readTree(openAiClient.lastBody);
        assertEquals("disabled", kimiRequest.path("thinking").path("type").asText());
        assertTrue(kimiRequest.path("temperature").isMissingNode());
        assertTrue(kimiRequest.path("reasoning").isMissingNode());
        assertTrue(openAiRequest.path("thinking").isMissingNode());
        assertEquals(0.2D, openAiRequest.path("temperature").asDouble());
    }

    private FakeHttpClient successfulClient() {
        return new FakeHttpClient(
                new LlmHttpClient.HttpResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        );
    }

    private LlmStructuredException findStructured(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LlmStructuredException structured) {
                return structured;
            }
            current = current.getCause();
        }
        throw new AssertionError("structured error not found");
    }

    private LlmModelConfig config(int rateLimitQps,
                                  Integer connectTimeoutMs,
                                  Integer requestTimeoutMs,
                                  Integer maxRetry) {
        return config("openai", "gpt-test", rateLimitQps, connectTimeoutMs, requestTimeoutMs, maxRetry);
    }

    private LlmModelConfig config(String platformCode,
                                  String modelId,
                                  int rateLimitQps,
                                  Integer connectTimeoutMs,
                                  Integer requestTimeoutMs,
                                  Integer maxRetry) {
        return new LlmModelConfig(
                platformCode,
                platformCode,
                modelId,
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
        private String lastBody;

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
            this.lastBody = body;
            Object response = responses[Math.min(callCount, responses.length - 1)];
            callCount++;
            if (response instanceof Exception ex) {
                throw ex;
            }
            return (HttpResponse) response;
        }
    }
}
