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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ZhipuChatWebSearchCodec extends AbstractJsonWebSearchCodec {

    private static final Pattern REFER_INDEX_PATTERN = Pattern.compile("(?i)^ref_(\\d+)$");
    private static final Pattern CITATION_PATTERN = Pattern.compile("(?i)ref_(\\d+)");

    public ZhipuChatWebSearchCodec(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.ZHIPU_CHAT_WEB;
    }

    @Override
    public ObjectNode encode(WebSearchCodecRequest request) {
        JsonNode config = providerConfig(request);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.requestedModelId());
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        request.messages().forEach(source -> messages.add(message(source)));

        ObjectNode tool = root.putArray("tools").addObject();
        tool.put("type", "web_search");
        ObjectNode search = tool.putObject("web_search");
        search.put("enable", true);
        search.put("search_engine", text(config, "searchEngine", "search_pro"));
        search.put("search_result", true);
        search.put("count", boundedCount(config.path("count").asInt(10)));
        search.put("search_recency_filter", text(config, "searchRecencyFilter", "noLimit"));
        search.put("content_size", text(config, "contentSize", "medium"));
        root.put("tool_choice", "auto");
        return root;
    }

    @Override
    public WebSearchResponse decode(JsonNode root, WebSearchCodecRequest request) {
        String answer = answer(root);
        boolean searchStructurePresent = root.has("web_search");
        JsonNode searchResults = searchStructurePresent ? root.path("web_search") : null;
        List<SourceWithRefer> parsedSources = parseSources(searchResults);
        List<WebSearchSource> sources = parsedSources.stream().map(SourceWithRefer::source).toList();
        List<WebSearchCitation> citations = parseCitations(answer, parsedSources);

        List<SearchEvidence> evidence = searchStructurePresent
                ? List.of(new SearchEvidence(1, "web_search", null,
                Map.of("resultCount", searchResults != null && searchResults.isArray()
                        ? searchResults.size() : 0)))
                : List.of();
        return new WebSearchResponse(
                firstText(root, "request_id", "id"),
                request.requestedModelId(),
                root.path("model").asText(request.requestedModelId()),
                answer,
                classifySearchStatus(searchStructurePresent, sources),
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

    private List<SourceWithRefer> parseSources(JsonNode searchResults) {
        if (searchResults == null || !searchResults.isArray()) {
            return List.of();
        }
        List<SourceWithRefer> sources = new ArrayList<>();
        int occurrence = 0;
        for (JsonNode item : searchResults) {
            occurrence++;
            String refer = item.path("refer").asText(null);
            int rank = referIndex(refer, occurrence);
            String url = item.path("link").asText(null);
            boolean validUrl = WebSearchCodecSupport.validHttpUrl(url);
            WebSearchSource source = new WebSearchSource(
                    1,
                    rank,
                    null,
                    item.path("title").asText(null),
                    url,
                    validUrl ? url : null,
                    validUrl ? WebSearchCodecSupport.domain(url) : null,
                    item.path("media").asText(null),
                    item.path("content").asText(null),
                    parsePublishTime(item.path("publish_date").asText(null)),
                    BrandMatchStrength.NONE,
                    List.of()
            );
            sources.add(new SourceWithRefer(normalizeRefer(refer, rank), source));
        }
        return List.copyOf(sources);
    }

    private List<WebSearchCitation> parseCitations(String answer, List<SourceWithRefer> sources) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Map<String, Integer> sourceIndexes = new LinkedHashMap<>();
        for (int index = 0; index < sources.size(); index++) {
            sourceIndexes.putIfAbsent(sources.get(index).refer(), index);
        }
        List<WebSearchCitation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            String refer = normalizeRefer("ref_" + matcher.group(1), parseIndex(matcher.group(1), -1));
            Integer sourceIndex = sourceIndexes.get(refer);
            boolean validSource = sourceIndex != null
                    && sources.get(sourceIndex).source().normalizedUrl() != null;
            citations.add(new WebSearchCitation(
                    parseIndex(matcher.group(1), null),
                    sourceIndex,
                    matcher.start(),
                    matcher.end(),
                    matcher.group(),
                    validSource ? CitationConfidence.CONFIRMED : CitationConfidence.NONE,
                    sourceIndex == null ? "SOURCE_INDEX_NOT_FOUND" : validSource ? "VALID" : "INVALID_SOURCE_URL"
            ));
        }
        return List.copyOf(citations);
    }

    private SearchStatus classifySearchStatus(boolean searchStructurePresent, List<WebSearchSource> sources) {
        if (!searchStructurePresent) {
            return SearchStatus.NOT_CONFIRMED;
        }
        if (sources.isEmpty()) {
            return SearchStatus.EMPTY;
        }
        return sources.stream().anyMatch(source -> source.normalizedUrl() != null)
                ? SearchStatus.TRIGGERED : SearchStatus.NO_VALID_SOURCE;
    }

    private int boundedCount(int value) {
        return Math.max(1, Math.min(value, 50));
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private int referIndex(String refer, int fallback) {
        if (!StringUtils.hasText(refer)) {
            return fallback;
        }
        Matcher matcher = REFER_INDEX_PATTERN.matcher(refer.trim());
        return matcher.matches() ? parseIndex(matcher.group(1), fallback) : fallback;
    }

    private String normalizeRefer(String refer, int fallbackIndex) {
        if (StringUtils.hasText(refer)) {
            return refer.trim().toLowerCase(Locale.ROOT);
        }
        return "ref_" + fallbackIndex;
    }

    private Integer parseIndex(String value, Integer fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private record SourceWithRefer(String refer, WebSearchSource source) { }
}
