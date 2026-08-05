package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZhipuChatWebSearchCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ZhipuChatWebSearchCodec codec = new ZhipuChatWebSearchCodec(objectMapper);

    @Test
    void buildsNonStreamingWebSearchInChatRequest() {
        JsonNode body = codec.encode(request("""
                {
                  "searchEngine": "search_pro_sogou",
                  "count": 99,
                  "searchRecencyFilter": "oneWeek",
                  "contentSize": "high"
                }
                """));

        assertEquals(IntegrationType.ZHIPU_CHAT_WEB, codec.integrationType());
        assertTrue(IntegrationType.ZHIPU_CHAT_WEB.isWebSearch());
        assertFalse(body.path("stream").asBoolean(true));
        assertEquals("fixture-model", body.path("model").asText());
        assertEquals("web_search", body.path("tools").path(0).path("type").asText());
        JsonNode search = body.path("tools").path(0).path("web_search");
        assertTrue(search.path("enable").asBoolean());
        assertTrue(search.path("search_result").asBoolean());
        assertEquals("search_pro_sogou", search.path("search_engine").asText());
        assertEquals(50, search.path("count").asInt());
        assertEquals("oneWeek", search.path("search_recency_filter").asText());
        assertEquals("high", search.path("content_size").asText());
        assertEquals("auto", body.path("tool_choice").asText());
    }

    @Test
    void parsesAnswerSourcesUsageAndMultipleCitationForms() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "id": "fallback-id",
                  "request_id": "zhipu-request-id",
                  "model": "glm-4.5-air",
                  "choices": [{
                    "message": {"content": "结论一[ref_1]，结论二[来源：ref_2]。"},
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30},
                  "web_search": [
                    {
                      "refer": "ref_1",
                      "title": "来源一",
                      "link": "https://example.com/one",
                      "media": "示例媒体",
                      "publish_date": "2026-08-05",
                      "content": "摘要一"
                    },
                    {
                      "refer": "ref_2",
                      "title": "来源二",
                      "link": "https://example.org/two",
                      "content": "摘要二"
                    }
                  ]
                }
                """);

        WebSearchResponse parsed = codec.decode(response, request("{}"));

        assertEquals("zhipu-request-id", parsed.providerRequestId());
        assertEquals("glm-4.5-air", parsed.responseModelId());
        assertEquals(SearchStatus.TRIGGERED, parsed.searchStatus());
        assertEquals(2, parsed.sources().size());
        assertEquals("示例媒体", parsed.sources().get(0).media());
        assertEquals("example.com", parsed.sources().get(0).domain());
        assertEquals(2, parsed.citations().size());
        assertTrue(parsed.citations().stream()
                .allMatch(citation -> citation.confidence() == CitationConfidence.CONFIRMED));
        assertEquals(30, parsed.usage().get("total_tokens"));
        assertEquals("stop", parsed.finishReason());
    }

    @Test
    void distinguishesMissingEmptyAndInvalidSearchResults() throws Exception {
        assertEquals(SearchStatus.NOT_CONFIRMED, decode("""
                {"choices":[{"message":{"content":"未联网"}}]}
                """).searchStatus());
        assertEquals(SearchStatus.EMPTY, decode("""
                {"choices":[{"message":{"content":"空结果"}}],"web_search":[]}
                """).searchStatus());
        WebSearchResponse invalid = decode("""
                {
                  "choices":[{"message":{"content":"引用 ref_9"}}],
                  "web_search":[{"refer":"ref_9","title":"无效来源","link":"javascript:alert(1)"}]
                }
                """);
        assertEquals(SearchStatus.NO_VALID_SOURCE, invalid.searchStatus());
        assertEquals(CitationConfidence.NONE, invalid.citations().get(0).confidence());
        assertEquals("INVALID_SOURCE_URL", invalid.citations().get(0).validationStatus());
    }

    @Test
    void recordsCitationWhoseSourceNumberDoesNotExist() throws Exception {
        WebSearchResponse parsed = decode("""
                {
                  "choices":[{"message":{"content":"缺失引用 [ref_3]"}}],
                  "web_search":[{"refer":"ref_1","title":"来源","link":"https://example.com"}]
                }
                """);

        assertEquals(1, parsed.citations().size());
        assertEquals("SOURCE_INDEX_NOT_FOUND", parsed.citations().get(0).validationStatus());
    }

    private WebSearchResponse decode(String json) throws Exception {
        return codec.decode(objectMapper.readTree(json), request("{}"));
    }

    private WebSearchCodecRequest request(String providerConfig) {
        return new WebSearchCodecRequest(
                "fixture-model",
                List.of(
                        new WebSearchMessage("system", "必须引用联网来源。"),
                        new WebSearchMessage("user", "今天有什么热点新闻")
                ),
                providerConfig
        );
    }
}
