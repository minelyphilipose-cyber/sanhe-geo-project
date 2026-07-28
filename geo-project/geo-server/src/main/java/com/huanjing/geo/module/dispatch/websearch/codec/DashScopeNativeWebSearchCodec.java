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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DashScopeNativeWebSearchCodec extends AbstractJsonWebSearchCodec {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[ref_(\\d+)]");

    public DashScopeNativeWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.DASHSCOPE_NATIVE_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());

        ArrayNode messages = root.putObject("input").putArray("messages");
        request.messages().forEach(source -> messages.add(message(source)));

        ObjectNode parameters = root.putObject("parameters");
        parameters.put("result_format", "message");
        parameters.put("enable_search", true);
        ObjectNode searchOptions = parameters.putObject("search_options");
        searchOptions.put("forced_search", config.path("forcedSearch").asBoolean(true));
        searchOptions.put("enable_source", config.path("enableSource").asBoolean(true));
        searchOptions.put("enable_citation", config.path("enableCitation").asBoolean(true));
        searchOptions.put("citation_format", config.path("citationFormat").asText("[ref_<number>]"));
        searchOptions.put("search_strategy", config.path("searchStrategy").asText("turbo"));
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        JsonNode output = root.path("output");
        JsonNode searchInfo = output.path("search_info");
        JsonNode searchResults = searchInfo.path("search_results");
        List<WebSearchSource> sources = parseSources(searchResults);
        String answer = firstAnswer(output);
        List<WebSearchCitation> citations = parseCitations(answer, sources);

        boolean searchStructurePresent = searchInfo.isObject()
                && (searchInfo.has("search_results") || searchInfo.has("search_queries") || searchInfo.has("query"));
        SearchStatus searchStatus;
        if (!searchStructurePresent && sources.isEmpty()) {
            searchStatus = SearchStatus.NOT_CONFIRMED;
        } else if (sources.isEmpty()) {
            searchStatus = SearchStatus.EMPTY;
        } else if (sources.stream().anyMatch(source -> source.normalizedUrl() != null)) {
            searchStatus = SearchStatus.TRIGGERED;
        } else {
            searchStatus = SearchStatus.NO_VALID_SOURCE;
        }

        List<SearchEvidence> evidence = searchStructurePresent
                ? List.of(new SearchEvidence(1, "search_info", firstQuery(searchInfo),
                evidenceAttributes(searchInfo)))
                : List.of();
        return new WebSearchResponse(
                WebSearchCodecSupport.text(root, "request_id"),
                request.requestedModelId(),
                root.path("model").asText(request.requestedModelId()),
                answer,
                searchStatus,
                false,
                evidence,
                sources,
                citations,
                WebSearchCodecSupport.asMap(objectMapper, root.path("usage")),
                output.path("finish_reason").asText(null)
        );
    }

    private ObjectNode message(WebSearchMessage source) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", source.role());
        message.put("content", source.content());
        return message;
    }

    private List<WebSearchSource> parseSources(JsonNode searchResults) {
        if (!searchResults.isArray()) {
            return List.of();
        }
        List<WebSearchSource> sources = new ArrayList<>();
        int fallbackRank = 1;
        for (JsonNode item : searchResults) {
            String url = item.path("url").asText(null);
            boolean validUrl = WebSearchCodecSupport.validHttpUrl(url);
            int rank = item.path("index").canConvertToInt() ? item.path("index").asInt() : fallbackRank;
            sources.add(new WebSearchSource(
                    1, rank, item.path("query").asText(null), item.path("title").asText(null), url,
                    validUrl ? url : null, validUrl ? WebSearchCodecSupport.domain(url) : null,
                    null,
                    item.path("snippet").asText(item.path("text").asText(null)), null,
                    BrandMatchStrength.NONE, List.of()
            ));
            fallbackRank++;
        }
        return List.copyOf(sources);
    }

    private String firstAnswer(JsonNode output) {
        JsonNode choices = output.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder answer = new StringBuilder();
            for (JsonNode part : content) {
                answer.append(part.path("text").asText(""));
            }
            return answer.toString();
        }
        return "";
    }

    private List<WebSearchCitation> parseCitations(String answer, List<WebSearchSource> sources) {
        List<WebSearchCitation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            int citationIndex = Integer.parseInt(matcher.group(1));
            int sourceOccurrenceIndex = findSourceIndex(sources, citationIndex);
            boolean validSource = sourceOccurrenceIndex >= 0
                    && sources.get(sourceOccurrenceIndex).normalizedUrl() != null;
            citations.add(new WebSearchCitation(
                    citationIndex,
                    sourceOccurrenceIndex >= 0 ? sourceOccurrenceIndex : null,
                    matcher.start(), matcher.end(), matcher.group(),
                    validSource ? CitationConfidence.CONFIRMED : CitationConfidence.NONE,
                    validSource ? "VALID" : "SOURCE_INDEX_NOT_FOUND"
            ));
        }
        return List.copyOf(citations);
    }

    private int findSourceIndex(List<WebSearchSource> sources, int citationIndex) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).rank() == citationIndex) {
                return i;
            }
        }
        return -1;
    }

    private String firstQuery(JsonNode searchInfo) {
        String query = searchInfo.path("query").asText(null);
        if (query != null) {
            return query;
        }
        JsonNode queries = searchInfo.path("search_queries");
        return queries.isArray() && !queries.isEmpty() ? queries.get(0).asText(null) : null;
    }

    private Map<String, Object> evidenceAttributes(JsonNode searchInfo) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        JsonNode results = searchInfo.path("search_results");
        attributes.put("resultCount", results.isArray() ? results.size() : 0);
        JsonNode queries = searchInfo.path("search_queries");
        if (queries.isArray()) {
            attributes.put("queryCount", queries.size());
        }
        return attributes;
    }
}
