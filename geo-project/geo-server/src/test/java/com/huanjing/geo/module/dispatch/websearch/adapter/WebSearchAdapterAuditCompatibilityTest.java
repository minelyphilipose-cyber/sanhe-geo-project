package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.VolcengineResponsesWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchPlatformProfile;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.ProviderExchange;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSearchAdapterAuditCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productionAdapterStillUsesAuditedTransportAndCompletesPhysicalCall() throws Exception {
        WebSearchProviderCallExecutor executor = mock(WebSearchProviderCallExecutor.class);
        ProviderExchange exchange = new ProviderExchange(
                99L, 200, fixtureText("volcengine/success.json"), Map.of(), 25L);
        when(executor.postJson(any(), anyString())).thenReturn(exchange);
        VolcengineResponsesWebSearchAdapter adapter = new VolcengineResponsesWebSearchAdapter(
                objectMapper, executor, new VolcengineResponsesWebSearchCodec(objectMapper));
        WebSearchRequest request = request();

        WebSearchResponse response = adapter.execute(request);

        ArgumentCaptor<String> requestBody = ArgumentCaptor.forClass(String.class);
        verify(executor).postJson(org.mockito.ArgumentMatchers.same(request), requestBody.capture());
        JsonNode encoded = objectMapper.readTree(requestBody.getValue());
        assertEquals(adapter.buildRequest(request), encoded);
        verify(executor).completeSuccess(exchange, response.providerRequestId(),
                objectMapper.writeValueAsString(response.usage()));
    }

    @Test
    void productionAdapterStillCompletesParseFailureThroughAuditTransport() {
        WebSearchProviderCallExecutor executor = mock(WebSearchProviderCallExecutor.class);
        ProviderExchange exchange = new ProviderExchange(99L, 200, "{invalid", Map.of(), 25L);
        WebSearchProviderException expected = mock(WebSearchProviderException.class);
        when(executor.postJson(any(), anyString())).thenReturn(exchange);
        when(executor.completeParseFailure(org.mockito.ArgumentMatchers.same(exchange), any()))
                .thenReturn(expected);
        VolcengineResponsesWebSearchAdapter adapter = new VolcengineResponsesWebSearchAdapter(
                objectMapper, executor, new VolcengineResponsesWebSearchCodec(objectMapper));

        WebSearchProviderException actual = assertThrows(
                WebSearchProviderException.class, () -> adapter.execute(request()));

        assertSame(expected, actual);
        verify(executor).completeParseFailure(org.mockito.ArgumentMatchers.same(exchange), any());
    }

    private String fixtureText(String name) throws Exception {
        String path = "/fixtures/websearch/" + name;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture " + path);
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private WebSearchRequest request() {
        WebSearchPlatformProfile profile = new WebSearchPlatformProfile(
                1L, "fixture_web", "fixture", "provider", IntegrationType.VOLCENGINE_RESPONSES_WEB,
                "https://example.test/invoke", "fixture-model", "env://FIXTURE_API_KEY",
                null, 1L, "{}", "fixture-hash", 3_000, 60_000
        );
        return new WebSearchRequest(
                10L, 20L, "今天有什么热点新闻", "必须引用联网来源。",
                profile, LocalDateTime.now().plusMinutes(2)
        );
    }
}
