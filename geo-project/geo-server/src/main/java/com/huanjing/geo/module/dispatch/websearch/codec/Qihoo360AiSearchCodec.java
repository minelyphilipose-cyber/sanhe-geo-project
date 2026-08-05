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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 360's public AI Search response schema is not yet documented; accepted shapes are fixture-pinned. */
@Component
public class Qihoo360AiSearchCodec extends AbstractJsonWebSearchCodec {

    public Qihoo360AiSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.QIHOO_360_AI_SEARCH_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);
        root.put("max_refer_search_items", bounded(config.path("maxReferSearchItems").asInt(20), 1, 50));
        root.put("enable_corner_markers", config.path("enableCornerMarkers").asBoolean(true));
        root.put("enable_web_page_safety", config.path("enableWebPageSafety").asBoolean(true));
        ArrayNode messages = root.putArray("messages");
        request.messages().forEach(source -> {
            ObjectNode message = messages.addObject();
            message.put("role", source.role());
            message.put("content", source.content());
        });
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        String answer = firstText(
                root.path("choices").path(0).path("message").path("content"),
                root.path("data").path("answer"), root.path("answer"), root.path("result").path("answer"));
        JsonNode references = firstArray(
                root.path("references"), root.path("data").path("references"),
                root.path("result").path("references"),
                root.path("choices").path(0).path("message").path("references"),
                root.path("search_results"));
        List<WebSearchSource> sources = new ArrayList<>();
        List<WebSearchCitation> citations = new ArrayList<>();
        if (references != null) {
            int rank = 0;
            for (JsonNode item : references) {
                rank++;
                String url = field(item, "url", "link", "source_url");
                boolean validUrl = WebSearchCodecSupport.validHttpUrl(url);
                int sourceIndex = sources.size();
                sources.add(new WebSearchSource(
                        1, rank, null, field(item, "title", "name"), url,
                        validUrl ? url : null,
                        validUrl ? WebSearchCodecSupport.domain(url) : null,
                        field(item, "site_name", "source", "media"),
                        field(item, "summary", "snippet", "content"), null,
                        BrandMatchStrength.NONE, List.of()));
                int markerStart = WebSearchCodecSupport.findCitationMarker(answer, rank);
                boolean confirmed = validUrl && markerStart >= 0;
                citations.add(new WebSearchCitation(
                        rank, sourceIndex,
                        markerStart >= 0 ? markerStart : null,
                        markerStart >= 0 ? markerStart + ("[" + rank + "]").length() : null,
                        "[" + rank + "]",
                        confirmed ? CitationConfidence.CONFIRMED : CitationConfidence.NONE,
                        !validUrl ? "INVALID_URL" : confirmed ? "VALID" : "MARKER_NOT_FOUND"));
            }
        }
        boolean referenceStructurePresent = references != null;
        SearchStatus status = !referenceStructurePresent
                ? SearchStatus.NOT_CONFIRMED
                : sources.stream().anyMatch(source -> source.normalizedUrl() != null)
                    ? SearchStatus.TRIGGERED : sources.isEmpty()
                        ? SearchStatus.EMPTY : SearchStatus.NO_VALID_SOURCE;
        List<SearchEvidence> evidence = referenceStructurePresent
                ? List.of(new SearchEvidence(1, "references", null,
                        Map.of("resultCount", sources.size())))
                : List.of();
        return new WebSearchResponse(
                firstText(root.path("id"), root.path("request_id"), root.path("data").path("id")),
                request.requestedModelId(), root.path("model").asText(request.requestedModelId()),
                answer, status, false, evidence, sources, citations,
                WebSearchCodecSupport.asMap(objectMapper, root.path("usage")),
                root.path("choices").path(0).path("finish_reason").asText(null));
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private JsonNode firstArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private String firstText(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isTextual() && StringUtils.hasText(candidate.asText())) {
                return candidate.asText();
            }
        }
        return "";
    }

    private String field(JsonNode node, String... fields) {
        for (String name : fields) {
            String value = node.path(name).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
