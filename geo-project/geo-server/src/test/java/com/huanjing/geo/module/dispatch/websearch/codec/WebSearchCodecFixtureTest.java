package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchCodecFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void volcengineCodecMatchesCompleteGoldenRequestAndResponses() throws Exception {
        WebSearchCodec codec = new VolcengineResponsesWebSearchCodec(objectMapper);
        WebSearchCodecRequest request = request("{}");

        assertGoldenRequest(codec, request, "volcengine");
        assertGoldenFixtures(codec, request, "volcengine",
                List.of("success", "not_confirmed", "empty", "invalid_source"));
    }

    @Test
    void dashScopeCodecMatchesCompleteGoldenRequestAndResponses() throws Exception {
        WebSearchCodec codec = new DashScopeNativeWebSearchCodec(objectMapper);
        WebSearchCodecRequest request = request("{}");

        assertGoldenRequest(codec, request, "dashscope");
        assertGoldenFixtures(codec, request, "dashscope",
                List.of("success", "not_confirmed", "empty", "invalid_source",
                        "valid_source_without_citation"));
    }

    @Test
    void tokenHubCodecMatchesCompleteGoldenRequestAndResponses() throws Exception {
        WebSearchCodec codec = new TencentTokenHubResponsesWebSearchCodec(objectMapper);
        WebSearchCodecRequest request = request(
                "{\"searchContextSize\":\"large\",\"searchSource\":\"standard\"}");

        assertGoldenRequest(codec, request, "tokenhub");
        assertGoldenFixtures(codec, request, "tokenhub",
                List.of("success", "not_confirmed", "empty", "invalid_source"));
    }

    @Test
    void unifiedInputRejectsInvalidProtocolMessagesBeforeTransport() {
        assertThrows(IllegalArgumentException.class, () -> new WebSearchMessage("tool", "content"));
        assertThrows(IllegalArgumentException.class, () -> new WebSearchCodecRequest(
                "model", List.of(new WebSearchMessage("system", "prompt")), "{}"));
    }

    private void assertGoldenRequest(WebSearchCodec codec,
                                     WebSearchCodecRequest request,
                                     String provider) throws Exception {
        assertEquals(
                readGolden("requests/" + provider + ".json"),
                codec.encode(request),
                provider + " complete request JSON"
        );
    }

    private void assertGoldenFixtures(WebSearchCodec codec,
                                      WebSearchCodecRequest request,
                                      String provider,
                                      List<String> fixtures) throws Exception {
        for (String fixture : fixtures) {
            JsonNode actual = objectMapper.valueToTree(
                    codec.decode(readFixture(provider + "/" + fixture + ".json"), request));
            assertEquals(
                    readGolden("responses/" + provider + "/" + fixture + ".json"),
                    actual,
                    provider + "/" + fixture + " complete WebSearchResponse"
            );
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

    private JsonNode readGolden(String name) throws Exception {
        return readFixture("golden/" + name);
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
