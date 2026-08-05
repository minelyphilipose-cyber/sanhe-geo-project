package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MimoAndQihoo360CodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final WebSearchCodecRequest request = new WebSearchCodecRequest(
            "model", List.of(new WebSearchMessage("user", "今天有什么新闻")), "{}");

    @Test
    void mimoUsesOfficialToolContractAndDecodesAnnotations() throws Exception {
        MimoChatWebSearchCodec codec = new MimoChatWebSearchCodec(objectMapper);
        JsonNode body = codec.encode(request);
        assertEquals("web_search", body.path("tools").path(0).path("type").asText());
        assertTrue(body.path("tools").path(0).path("force_search").asBoolean());
        WebSearchResponse response = codec.decode(objectMapper.readTree("""
                {"id":"mimo-request","model":"mimo-v2.5-pro","choices":[{"finish_reason":"stop",
                 "message":{"content":"联网回答","annotations":[{"type":"url_citation",
                 "url":"https://example.com/news","title":"新闻","summary":"摘要",
                 "site_name":"示例","publish_time":"2026-08-04T08:00:00+08:00"}]}}],
                 "usage":{"web_search_usage":{"tool_usage":1,"page_usage":1}}}
                """), request);

        assertEquals(SearchStatus.TRIGGERED, response.searchStatus());
        assertEquals("example.com", response.sources().get(0).domain());
        assertEquals(1, response.citations().size());
    }

    @Test
    void qihooUsesOfficialRequestFieldsAndFixturePinnedReferenceShape() throws Exception {
        Qihoo360AiSearchCodec codec = new Qihoo360AiSearchCodec(objectMapper);
        JsonNode body = codec.encode(request);
        assertEquals(20, body.path("max_refer_search_items").asInt());
        assertTrue(body.path("enable_corner_markers").asBoolean());
        WebSearchResponse response = codec.decode(objectMapper.readTree("""
                {"request_id":"360-request","model":"360gpt-pro",
                 "choices":[{"message":{"content":"搜索回答[1]"}}],
                 "references":[{"title":"来源","url":"https://example.org/source",
                 "summary":"来源摘要","site_name":"示例站"}],"usage":{"total_tokens":50}}
                """), request);

        assertEquals(SearchStatus.TRIGGERED, response.searchStatus());
        assertEquals("搜索回答[1]", response.answer());
        assertEquals("example.org", response.sources().get(0).domain());
    }
}
