package com.huanjing.geo.module.dispatch.websearch.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.codec.DashScopeNativeWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.TencentTokenHubResponsesWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.VolcengineResponsesWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.codec.QianfanErnieChatWebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchPlatformProfile;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.transport.WebSearchProviderCallExecutor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebSearchAdapterFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSearchProviderCallExecutor callExecutor = mock(WebSearchProviderCallExecutor.class);

    @Test
    void volcengineBuildsNonStreamingResponsesRequest() {
        VolcengineResponsesWebSearchAdapter adapter = volcengineAdapter();

        JsonNode body = adapter.buildRequest(request(IntegrationType.VOLCENGINE_RESPONSES_WEB, "{}"));

        assertFalse(body.path("stream").asBoolean(true));
        assertEquals("web_search", body.path("tools").get(0).path("type").asText());
        assertEquals("input_text", body.path("input").get(1).path("content").get(0).path("type").asText());
    }

    @Test
    void volcengineClassifiesFrozenSearchBoundaries() throws Exception {
        VolcengineResponsesWebSearchAdapter adapter = volcengineAdapter();

        assertFixture(adapter, "volcengine/success.json", SearchStatus.TRIGGERED, CitationConfidence.CONFIRMED);
        assertFixture(adapter, "volcengine/not_confirmed.json", SearchStatus.NOT_CONFIRMED, null);
        assertFixture(adapter, "volcengine/empty.json", SearchStatus.EMPTY, null);
        assertFixture(adapter, "volcengine/invalid_source.json", SearchStatus.NO_VALID_SOURCE, CitationConfidence.NONE);
    }

    @Test
    void dashScopeBuildsForcedNativeSearchRequest() {
        DashScopeNativeWebSearchAdapter adapter = dashScopeAdapter();

        JsonNode body = adapter.buildRequest(request(IntegrationType.DASHSCOPE_NATIVE_WEB, "{}"));

        JsonNode parameters = body.path("parameters");
        assertTrue(parameters.path("enable_search").asBoolean());
        assertTrue(parameters.path("search_options").path("forced_search").asBoolean());
        assertEquals("[ref_<number>]", parameters.path("search_options").path("citation_format").asText());
    }

    @Test
    void dashScopeClassifiesFrozenSearchBoundaries() throws Exception {
        DashScopeNativeWebSearchAdapter adapter = dashScopeAdapter();

        assertFixture(adapter, "dashscope/success.json", SearchStatus.TRIGGERED, CitationConfidence.CONFIRMED);
        assertFixture(adapter, "dashscope/not_confirmed.json", SearchStatus.NOT_CONFIRMED, null);
        assertFixture(adapter, "dashscope/empty.json", SearchStatus.EMPTY, null);
        assertFixture(adapter, "dashscope/invalid_source.json", SearchStatus.NO_VALID_SOURCE, CitationConfidence.NONE);
        assertFixture(adapter, "dashscope/valid_source_without_citation.json", SearchStatus.TRIGGERED, null);
    }

    @Test
    void tokenHubBuildsNonStreamingResponsesRequest() {
        TencentTokenHubResponsesWebSearchAdapter adapter =
                tokenHubAdapter();

        JsonNode body = adapter.buildRequest(request(
                IntegrationType.TENCENT_TOKENHUB_RESPONSES_WEB,
                "{\"searchContextSize\":\"large\",\"searchSource\":\"standard\"}"
        ));

        assertFalse(body.path("stream").asBoolean(true));
        assertEquals("web_search", body.path("tools").get(0).path("type").asText());
        assertEquals("large", body.path("tools").get(0).path("search_context_size").asText());
    }

    @Test
    void tokenHubClassifiesFrozenSearchBoundaries() throws Exception {
        TencentTokenHubResponsesWebSearchAdapter adapter =
                tokenHubAdapter();

        assertFixture(adapter, "tokenhub/success.json", SearchStatus.TRIGGERED, CitationConfidence.CONFIRMED);
        assertFixture(adapter, "tokenhub/not_confirmed.json", SearchStatus.NOT_CONFIRMED, null);
        assertFixture(adapter, "tokenhub/empty.json", SearchStatus.EMPTY, null);
        assertFixture(adapter, "tokenhub/invalid_source.json", SearchStatus.NO_VALID_SOURCE, CitationConfidence.NONE);
    }

    @Test
    void qianfanErnieBuildsNonStreamingBuiltInWebSearchRequest() {
        QianfanErnieChatWebSearchAdapter adapter = qianfanAdapter();

        JsonNode body = adapter.buildRequest(request(IntegrationType.QIANFAN_ERNIE_CHAT_WEB, "{}"));

        assertFalse(body.path("stream").asBoolean(true));
        JsonNode webSearch = body.path("web_search");
        assertTrue(webSearch.path("enable").asBoolean());
        assertTrue(webSearch.path("enable_trace").asBoolean());
        assertFalse(webSearch.has("enable_status"));
        assertTrue(webSearch.path("enable_citation").asBoolean());
        assertEquals("auto", webSearch.path("search_mode").asText());
        assertEquals(10, webSearch.path("search_number").asInt());
        assertEquals(5, webSearch.path("reference_number").asInt());
    }

    @Test
    void qianfanErnieClassifiesFrozenSearchBoundaries() throws Exception {
        QianfanErnieChatWebSearchAdapter adapter = qianfanAdapter();

        assertFixture(adapter, "qianfan/success.json", SearchStatus.TRIGGERED, CitationConfidence.CONFIRMED);
        assertFixture(adapter, "qianfan/not_confirmed.json", SearchStatus.NOT_CONFIRMED, null);
        assertFixture(adapter, "qianfan/empty.json", SearchStatus.EMPTY, null);
        assertFixture(adapter, "qianfan/invalid_source.json", SearchStatus.NO_VALID_SOURCE, CitationConfidence.NONE);
        assertFixture(adapter, "qianfan/valid_source_without_citation.json", SearchStatus.TRIGGERED, null);
        assertFixture(adapter, "qianfan/triggered_without_trace.json", SearchStatus.NO_VALID_SOURCE, null);
    }

    private void assertFixture(AbstractJsonWebSearchAdapter adapter,
                               String fixture,
                               SearchStatus expectedStatus,
                               CitationConfidence expectedConfidence) throws Exception {
        WebSearchResponse response = adapter.parseResponse(readFixture(fixture), request(adapter.integrationType(), "{}"));
        assertEquals(expectedStatus, response.searchStatus(), fixture);
        if (expectedConfidence != null) {
            assertFalse(response.citations().isEmpty(), fixture);
            assertEquals(expectedConfidence, response.citations().get(0).confidence(), fixture);
        }
    }

    private JsonNode readFixture(String name) throws Exception {
        String path = "/fixtures/websearch/" + name;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture " + path);
            }
            return objectMapper.readTree(input);
        }
    }

    private WebSearchRequest request(IntegrationType integrationType, String providerConfig) {
        WebSearchPlatformProfile profile = new WebSearchPlatformProfile(
                1L, "fixture_web", "fixture", "provider", integrationType,
                "https://example.test/invoke", "fixture-model", "env://FIXTURE_API_KEY",
                null, 1L, providerConfig, "fixture-hash", 3_000, 60_000
        );
        return new WebSearchRequest(
                10L, 20L, "今天有什么热点新闻", "必须引用联网来源。",
                profile, LocalDateTime.now().plusMinutes(2)
        );
    }

    private VolcengineResponsesWebSearchAdapter volcengineAdapter() {
        return new VolcengineResponsesWebSearchAdapter(
                objectMapper, callExecutor, new VolcengineResponsesWebSearchCodec(objectMapper));
    }

    private DashScopeNativeWebSearchAdapter dashScopeAdapter() {
        return new DashScopeNativeWebSearchAdapter(
                objectMapper, callExecutor, new DashScopeNativeWebSearchCodec(objectMapper));
    }

    private TencentTokenHubResponsesWebSearchAdapter tokenHubAdapter() {
        return new TencentTokenHubResponsesWebSearchAdapter(
                objectMapper, callExecutor, new TencentTokenHubResponsesWebSearchCodec(objectMapper));
    }

    private QianfanErnieChatWebSearchAdapter qianfanAdapter() {
        return new QianfanErnieChatWebSearchAdapter(
                objectMapper, callExecutor, new QianfanErnieChatWebSearchCodec(objectMapper));
    }
}
