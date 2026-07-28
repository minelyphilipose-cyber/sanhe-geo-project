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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TencentTokenHubResponsesWebSearchCodec extends AbstractJsonWebSearchCodec {

    public TencentTokenHubResponsesWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.TENCENT_TOKENHUB_RESPONSES_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);
        ArrayNode input = root.putArray("input");
        request.messages().forEach(source -> input.add(message(source)));

        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "web_search");
        tool.put("search_context_size", config.path("searchContextSize").asText("medium"));
        tool.put("search_source", config.path("searchSource").asText("standard"));
        root.putArray("tools").add(tool);
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        List<SearchEvidence> evidence = new ArrayList<>();
        List<WebSearchSource> sources = new ArrayList<>();
        List<WebSearchCitation> citations = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        int eventIndex = 0;

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                String type = item.path("type").asText();
                if ("web_search_call".equals(type)) {
                    eventIndex++;
                    evidence.add(searchEvidence(item, eventIndex));
                } else if ("message".equals(type)) {
                    parseMessage(item, Math.max(eventIndex, 1), answer, sources, citations);
                }
            }
        }

        int toolUsage = root.path("usage").path("tool_usage").path("web_search_call").asInt(0);
        if (toolUsage == 0) {
            toolUsage = root.path("usage").path("tool_usage").path("web_search").asInt(0);
        }
        boolean searchConfirmed = !evidence.isEmpty() || toolUsage > 0 || !sources.isEmpty();
        SearchStatus searchStatus = classifySearchStatus(searchConfirmed, sources);
        return new WebSearchResponse(
                WebSearchCodecSupport.text(root, "id"),
                request.requestedModelId(),
                root.path("model").asText(request.requestedModelId()),
                answer.toString(),
                searchStatus,
                false,
                evidence,
                sources,
                citations,
                WebSearchCodecSupport.asMap(objectMapper, root.path("usage")),
                root.path("status").asText(null)
        );
    }

    private ObjectNode message(WebSearchMessage source) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", source.role());
        message.put("content", source.content());
        return message;
    }

    private SearchEvidence searchEvidence(JsonNode item, int eventIndex) {
        JsonNode action = item.path("action");
        String actionType = action.isTextual() ? action.asText() : action.path("type").asText(null);
        String query = action.isObject() ? action.path("query").asText(null) : null;
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "id", item.path("id").asText(null));
        putIfPresent(attributes, "action", actionType);
        putIfPresent(attributes, "status", item.path("status").asText(null));
        return new SearchEvidence(eventIndex, "web_search_call", query, attributes);
    }

    private void parseMessage(JsonNode item,
                              int eventIndex,
                              StringBuilder answer,
                              List<WebSearchSource> sources,
                              List<WebSearchCitation> citations) {
        JsonNode content = item.path("content");
        if (!content.isArray()) {
            return;
        }
        for (JsonNode part : content) {
            if (!"output_text".equals(part.path("type").asText())) {
                continue;
            }
            String text = part.path("text").asText("");
            int offset = answer.length();
            answer.append(text);
            JsonNode annotations = part.path("annotations");
            if (!annotations.isArray()) {
                continue;
            }
            for (JsonNode annotation : annotations) {
                parseAnnotation(annotation, eventIndex, text, offset, sources, citations);
            }
        }
    }

    private void parseAnnotation(JsonNode annotation,
                                 int eventIndex,
                                 String answerPart,
                                 int answerOffset,
                                 List<WebSearchSource> sources,
                                 List<WebSearchCitation> citations) {
        String url = annotation.path("url").asText(null);
        boolean validSource = WebSearchCodecSupport.validHttpUrl(url);
        Integer index = WebSearchCodecSupport.integer(annotation, "index");
        Integer start = WebSearchCodecSupport.integer(annotation, "start_index");
        Integer end = WebSearchCodecSupport.integer(annotation, "end_index");
        int sourceIndex = sources.size();
        sources.add(new WebSearchSource(
                eventIndex, index == null ? sourceIndex + 1 : index, null,
                annotation.path("title").asText(null), url,
                validSource ? url : null, validSource ? WebSearchCodecSupport.domain(url) : null,
                null,
                annotation.path("snippet").asText(null), null,
                BrandMatchStrength.NONE, List.of()
        ));

        int marker = WebSearchCodecSupport.findCitationMarker(answerPart, index);
        if (!validPosition(start, end, answerPart) && marker >= 0) {
            start = marker;
            String markerText = answerPart.startsWith("[ref_", marker)
                    ? "[ref_" + index + "]" : "[" + index + "]";
            end = marker + markerText.length();
        }
        boolean positionValid = validPosition(start, end, answerPart);
        CitationConfidence confidence = validSource && index != null && positionValid
                ? CitationConfidence.CONFIRMED
                : validSource ? CitationConfidence.PROBABLE : CitationConfidence.NONE;
        citations.add(new WebSearchCitation(
                index, sourceIndex,
                start == null ? null : answerOffset + start,
                end == null ? null : answerOffset + end,
                positionValid ? answerPart.substring(start, end) : null,
                confidence,
                confidence == CitationConfidence.CONFIRMED ? "VALID" : "INCOMPLETE_PROVIDER_METADATA"
        ));
    }

    private boolean validPosition(Integer start, Integer end, String answer) {
        return start != null && end != null && start >= 0 && end > start && end <= answer.length();
    }

    private SearchStatus classifySearchStatus(boolean searchConfirmed, List<WebSearchSource> sources) {
        if (!searchConfirmed) {
            return SearchStatus.NOT_CONFIRMED;
        }
        if (sources.isEmpty()) {
            return SearchStatus.EMPTY;
        }
        return sources.stream().anyMatch(source -> source.normalizedUrl() != null)
                ? SearchStatus.TRIGGERED : SearchStatus.NO_VALID_SOURCE;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
