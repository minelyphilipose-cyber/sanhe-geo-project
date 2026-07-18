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
public class VolcengineResponsesWebSearchCodec extends AbstractJsonWebSearchCodec {

    public VolcengineResponsesWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.VOLCENGINE_RESPONSES_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);

        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "web_search");
        JsonNode config = providerConfig(request);
        copyPositiveInt(config, tool, "maxKeyword", "max_keyword");
        copyPositiveInt(config, tool, "limit", "limit");
        root.putArray("tools").add(tool);

        ArrayNode input = root.putArray("input");
        request.messages().forEach(message -> input.add(message(message)));
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
                    evidence.add(parseSearchEvidence(item, eventIndex));
                } else if ("message".equals(type)) {
                    parseMessage(item, eventIndex, answer, sources, citations);
                }
            }
        }

        Map<String, Object> usage = WebSearchCodecSupport.asMap(objectMapper, root.path("usage"));
        int toolUsage = root.path("usage").path("tool_usage").path("web_search").asInt(0);
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
                usage,
                root.path("status").asText(null)
        );
    }

    private ObjectNode message(WebSearchMessage source) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", source.role());
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "input_text");
        content.put("text", source.content());
        message.putArray("content").add(content);
        return message;
    }

    private void copyPositiveInt(JsonNode config, ObjectNode target, String sourceField, String targetField) {
        int value = config.path(sourceField).asInt(0);
        if (value > 0) {
            target.put(targetField, value);
        }
    }

    private SearchEvidence parseSearchEvidence(JsonNode item, int eventIndex) {
        JsonNode action = item.get("action");
        String actionType = action != null && action.isTextual()
                ? action.asText()
                : action == null ? null : action.path("type").asText(null);
        String query = action != null && action.isObject() ? action.path("query").asText(null) : null;
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "id", item.path("id").asText(null));
        putIfPresent(attributes, "action", actionType);
        putIfPresent(attributes, "status", item.path("status").asText(null));
        return new SearchEvidence(eventIndex, "web_search_call", query, attributes);
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
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
            int answerOffset = answer.length();
            answer.append(text);
            JsonNode annotations = part.path("annotations");
            if (!annotations.isArray()) {
                continue;
            }
            for (JsonNode annotation : annotations) {
                parseAnnotation(annotation, Math.max(eventIndex, 1), text, answerOffset, sources, citations);
            }
        }
    }

    private void parseAnnotation(JsonNode annotation,
                                 int eventIndex,
                                 String text,
                                 int answerOffset,
                                 List<WebSearchSource> sources,
                                 List<WebSearchCitation> citations) {
        String url = annotation.path("url").asText(null);
        String title = annotation.path("title").asText(null);
        Integer index = WebSearchCodecSupport.integer(annotation, "index");
        Integer start = WebSearchCodecSupport.integer(annotation, "start_index");
        Integer end = WebSearchCodecSupport.integer(annotation, "end_index");
        boolean validSource = WebSearchCodecSupport.validHttpUrl(url);
        int sourceIndex = sources.size();
        sources.add(new WebSearchSource(
                eventIndex, sourceIndex + 1, null, title, url,
                validSource ? url : null,
                validSource ? WebSearchCodecSupport.domain(url) : null,
                annotation.path("snippet").asText(null), null,
                BrandMatchStrength.NONE, List.of()
        ));

        int marker = WebSearchCodecSupport.findCitationMarker(text, index);
        if ((start == null || end == null || start < 0 || end <= start || end > text.length()) && marker >= 0) {
            start = marker;
            end = marker + (text.startsWith("[ref_", marker)
                    ? ("[ref_" + index + "]").length() : ("[" + index + "]").length());
        }
        boolean positionValid = start != null && end != null && start >= 0 && end > start && end <= text.length();
        CitationConfidence confidence = validSource && index != null && positionValid
                ? CitationConfidence.CONFIRMED
                : validSource ? CitationConfidence.PROBABLE : CitationConfidence.NONE;
        citations.add(new WebSearchCitation(
                index, sourceIndex,
                start == null ? null : answerOffset + start,
                end == null ? null : answerOffset + end,
                positionValid ? text.substring(start, end) : null,
                confidence,
                confidence == CitationConfidence.CONFIRMED ? "VALID" : "INCOMPLETE_PROVIDER_METADATA"
        ));
    }

    private SearchStatus classifySearchStatus(boolean searchConfirmed, List<WebSearchSource> sources) {
        if (!searchConfirmed) {
            return SearchStatus.NOT_CONFIRMED;
        }
        if (sources.isEmpty()) {
            return SearchStatus.EMPTY;
        }
        boolean hasValidSource = sources.stream().anyMatch(source -> source.normalizedUrl() != null);
        return hasValidSource ? SearchStatus.TRIGGERED : SearchStatus.NO_VALID_SOURCE;
    }
}
