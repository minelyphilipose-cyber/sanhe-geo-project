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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QianfanErnieChatWebSearchCodec extends AbstractJsonWebSearchCodec {

    private static final Pattern CITATION_MARKER_PATTERN =
            Pattern.compile("\\^((?:\\[\\d+])+?)\\^");
    private static final Pattern CITATION_NUMBER_PATTERN = Pattern.compile("\\[(\\d+)]");

    public QianfanErnieChatWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.QIANFAN_ERNIE_CHAT_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        int searchNumber = boundedReferenceCount(config.path("searchNumber").asInt(10));
        int referenceNumber = Math.min(
                searchNumber,
                boundedReferenceCount(config.path("referenceNumber").asInt(5))
        );

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        request.messages().forEach(source -> messages.add(message(source)));

        ObjectNode webSearch = root.putObject("web_search");
        webSearch.put("enable", true);
        webSearch.put("enable_trace", true);
        webSearch.put("enable_citation", true);
        webSearch.put("search_mode", "auto");
        webSearch.put("search_number", searchNumber);
        webSearch.put("reference_number", referenceNumber);
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        String answer = answer(root);
        JsonNode searchResults = searchResults(root);
        int searchTokens = root.path("usage").path("prompt_tokens_details").path("search_tokens").asInt(0);
        boolean searchStructurePresent = searchResults != null;
        List<WebSearchSource> sources = parseSources(searchResults);
        List<WebSearchCitation> citations = parseCitations(answer, sources);
        SearchStatus searchStatus = classifySearchStatus(searchStructurePresent, searchTokens, sources);

        List<SearchEvidence> evidence = searchStructurePresent || searchTokens > 0
                ? List.of(new SearchEvidence(
                1,
                "search_results",
                null,
                Map.of(
                        "resultCount", searchResults != null && searchResults.isArray()
                                ? searchResults.size() : 0,
                        "searchTokens", searchTokens
                )
        ))
                : List.of();
        return new WebSearchResponse(
                WebSearchCodecSupport.text(root, "id"),
                request.requestedModelId(),
                root.path("model").asText(request.requestedModelId()),
                answer,
                searchStatus,
                false,
                evidence,
                sources,
                citations,
                WebSearchCodecSupport.asMap(objectMapper, root.path("usage")),
                root.path("choices").path(0).path("finish_reason").asText(null)
        );
    }

    private ObjectNode message(WebSearchMessage source) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", source.role());
        message.put("content", source.content());
        return message;
    }

    private int boundedReferenceCount(int count) {
        return Math.max(1, Math.min(count, 28));
    }

    private String answer(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        StringBuilder answer = new StringBuilder();
        for (JsonNode part : content) {
            if (part.isTextual()) {
                answer.append(part.asText());
            } else {
                answer.append(part.path("text").asText(""));
            }
        }
        return answer.toString();
    }

    private JsonNode searchResults(JsonNode root) {
        if (root.has("search_results")) {
            return root.path("search_results");
        }
        JsonNode message = root.path("choices").path(0).path("message");
        return message.has("search_results") ? message.path("search_results") : null;
    }

    private List<WebSearchSource> parseSources(JsonNode searchResults) {
        if (searchResults == null || !searchResults.isArray()) {
            return List.of();
        }
        List<WebSearchSource> sources = new ArrayList<>();
        int occurrence = 0;
        for (JsonNode item : searchResults) {
            occurrence++;
            String url = item.path("url").asText(null);
            boolean validUrl = WebSearchCodecSupport.validHttpUrl(url);
            sources.add(new WebSearchSource(
                    1,
                    positiveIndex(item.path("index").asInt(occurrence), occurrence),
                    null,
                    item.path("title").asText(null),
                    url,
                    validUrl ? url : null,
                    validUrl ? WebSearchCodecSupport.domain(url) : null,
                    item.path("media").asText(null),
                    firstText(item, "content", "snippet"),
                    parsePublishTime(firstText(item, "publish_date", "publish_time")),
                    BrandMatchStrength.NONE,
                    List.of()
            ));
        }
        return List.copyOf(sources);
    }

    private int positiveIndex(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String firstText(JsonNode source, String... fields) {
        for (String field : fields) {
            String value = source.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime parsePublishTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return OffsetDateTime.parse(trimmed).toLocalDateTime();
                } catch (DateTimeParseException ignoredOffset) {
                    return null;
                }
            }
        }
    }

    private List<WebSearchCitation> parseCitations(String answer, List<WebSearchSource> sources) {
        List<WebSearchCitation> citations = new ArrayList<>();
        Matcher markerMatcher = CITATION_MARKER_PATTERN.matcher(answer == null ? "" : answer);
        while (markerMatcher.find()) {
            Matcher numberMatcher = CITATION_NUMBER_PATTERN.matcher(markerMatcher.group(1));
            while (numberMatcher.find()) {
                Integer citationIndex = parseCitationIndex(numberMatcher.group(1));
                if (citationIndex == null) {
                    continue;
                }
                int sourceOccurrenceIndex = findSourceIndex(sources, citationIndex);
                boolean validSource = sourceOccurrenceIndex >= 0
                        && sources.get(sourceOccurrenceIndex).normalizedUrl() != null;
                citations.add(new WebSearchCitation(
                        citationIndex,
                        sourceOccurrenceIndex >= 0 ? sourceOccurrenceIndex : null,
                        markerMatcher.start(),
                        markerMatcher.end(),
                        markerMatcher.group(),
                        validSource ? CitationConfidence.CONFIRMED : CitationConfidence.NONE,
                        validSource ? "VALID" : "SOURCE_INDEX_NOT_FOUND"
                ));
            }
        }
        return List.copyOf(citations);
    }

    private Integer parseCitationIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int findSourceIndex(List<WebSearchSource> sources, int citationIndex) {
        for (int index = 0; index < sources.size(); index++) {
            if (sources.get(index).rank() == citationIndex) {
                return index;
            }
        }
        return -1;
    }

    private SearchStatus classifySearchStatus(boolean searchStructurePresent,
                                              int searchTokens,
                                              List<WebSearchSource> sources) {
        if (!searchStructurePresent) {
            return searchTokens > 0 ? SearchStatus.NO_VALID_SOURCE : SearchStatus.NOT_CONFIRMED;
        }
        if (sources.isEmpty()) {
            return SearchStatus.EMPTY;
        }
        return sources.stream().anyMatch(source -> source.normalizedUrl() != null)
                ? SearchStatus.TRIGGERED : SearchStatus.NO_VALID_SOURCE;
    }
}
