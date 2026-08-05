package com.huanjing.geo.module.dispatch.websearch.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.SearchEvidence;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MimoChatWebSearchCodec extends AbstractJsonWebSearchCodec {

    public MimoChatWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.MIMO_CHAT_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);
        root.put("max_completion_tokens", bounded(config.path("maxCompletionTokens").asInt(2048), 1, 8192));
        root.put("temperature", config.path("temperature").asDouble(1.0));
        root.put("top_p", config.path("topP").asDouble(0.95));
        root.putObject("thinking").put("type", config.path("thinkingType").asText("disabled"));
        ArrayNode messages = root.putArray("messages");
        request.messages().forEach(source -> messages.add(message(source)));
        ObjectNode tool = root.putArray("tools").addObject();
        tool.put("type", "web_search");
        tool.put("max_keyword", bounded(config.path("maxKeyword").asInt(3), 1, 10));
        tool.put("force_search", config.path("forceSearch").asBoolean(true));
        tool.put("limit", bounded(config.path("resultLimit").asInt(10), 1, 50));
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        JsonNode message = root.path("choices").path(0).path("message");
        String answer = message.path("content").asText("");
        JsonNode annotations = message.path("annotations");
        List<WebSearchSource> sources = new ArrayList<>();
        List<WebSearchCitation> citations = new ArrayList<>();
        if (annotations.isArray()) {
            int rank = 0;
            for (JsonNode item : annotations) {
                if (!"url_citation".equals(item.path("type").asText())) {
                    continue;
                }
                rank++;
                String url = item.path("url").asText(null);
                boolean validUrl = WebSearchCodecSupport.validHttpUrl(url);
                sources.add(new WebSearchSource(
                        1, rank, null, item.path("title").asText(null), url,
                        validUrl ? url : null,
                        validUrl ? WebSearchCodecSupport.domain(url) : null,
                        item.path("site_name").asText(null), item.path("summary").asText(null),
                        publishTime(item.path("publish_time").asText(null)),
                        BrandMatchStrength.NONE, List.of()));
                citations.add(new WebSearchCitation(
                        rank, rank - 1, null, null, item.path("title").asText(null),
                        validUrl ? CitationConfidence.CONFIRMED : CitationConfidence.NONE,
                        validUrl ? "VALID" : "INVALID_URL"));
            }
        }
        JsonNode webUsage = root.path("usage").path("web_search_usage");
        int toolUsage = webUsage.path("tool_usage").asInt(0);
        int pageUsage = webUsage.path("page_usage").asInt(0);
        boolean searchObserved = (annotations.isArray() && !annotations.isEmpty())
                || toolUsage > 0 || pageUsage > 0;
        SearchStatus searchStatus = !searchObserved
                ? SearchStatus.NOT_CONFIRMED
                : sources.stream().anyMatch(source -> source.normalizedUrl() != null)
                    ? SearchStatus.TRIGGERED : SearchStatus.NO_VALID_SOURCE;
        List<SearchEvidence> evidence = searchObserved
                ? List.of(new SearchEvidence(1, "web_search", null, Map.of(
                        "toolUsage", toolUsage,
                        "pageUsage", pageUsage,
                        "resultCount", sources.size())))
                : List.of();
        return new WebSearchResponse(
                WebSearchCodecSupport.text(root, "id"), request.requestedModelId(),
                root.path("model").asText(request.requestedModelId()), answer, searchStatus, false,
                evidence, sources, citations,
                WebSearchCodecSupport.asMap(objectMapper, root.path("usage")),
                root.path("choices").path(0).path("finish_reason").asText(null));
    }

    private ObjectNode message(WebSearchMessage source) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", source.role());
        node.put("content", source.content());
        return node;
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private java.time.LocalDateTime publishTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
