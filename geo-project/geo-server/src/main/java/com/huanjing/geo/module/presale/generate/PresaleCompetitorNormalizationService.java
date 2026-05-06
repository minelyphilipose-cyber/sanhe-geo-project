package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator.ExtractedCompetitor;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator.RawCompetitorMention;
import com.huanjing.geo.module.presale.generate.llm.CompetitorNormalizationPromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class PresaleCompetitorNormalizationService {

    private static final int MAX_EXTRACTED_COMPETITORS = 3;

    private final PresaleLlmInvoker llmInvoker;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final ObjectMapper objectMapper;

    public PresaleCompetitorNormalizationService(PresaleLlmInvoker llmInvoker,
                                                 AiPlatformConfigMapper aiPlatformConfigMapper,
                                                 ObjectMapper objectMapper) {
        this.llmInvoker = llmInvoker;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.objectMapper = objectMapper;
    }

    public NormalizationOutcome normalize(Long versionId,
                                          String brandName,
                                          List<RawCompetitorMention> rawMentions,
                                          Long operatorUserId,
                                          boolean isManager) {
        if (rawMentions == null || rawMentions.isEmpty()) {
            return new NormalizationOutcome(List.of(), false);
        }

        List<ExtractedCompetitor> fallback = fallbackTop(rawMentions);
        AiPlatformConfig platform = resolveNormalizationPlatform();
        if (platform == null || !StringUtils.hasText(platform.getPlatformCode())) {
            log.warn("skip competitor normalization by LLM, no presale platform available, versionId={}", versionId);
            return new NormalizationOutcome(fallback, false);
        }

        try {
            String candidatesJson = objectMapper.writeValueAsString(toCandidatePayload(rawMentions));
            String prompt = CompetitorNormalizationPromptTemplates.renderUserPrompt(brandName, candidatesJson);
            PlatformCallContext ctx = new PlatformCallContext(
                    versionId, 1, platform.getPlatformCode(), null, "", brandName, operatorUserId, isManager);
            LlmCallResult result = llmInvoker.normalizeCompetitors(ctx, prompt);
            List<ExtractedCompetitor> normalized = parseAndValidate(result.rawResponse(), rawMentions);
            return new NormalizationOutcome(normalized.isEmpty() ? fallback : normalized, true);
        } catch (LlmInvokeException ex) {
            log.warn("competitor normalization LLM call failed, fallback to raw top, versionId={}",
                    versionId, ex);
            return new NormalizationOutcome(fallback, true);
        } catch (Exception ex) {
            log.warn("competitor normalization response invalid, fallback to raw top, versionId={}",
                    versionId, ex);
            return new NormalizationOutcome(fallback, true);
        }
    }

    private AiPlatformConfig resolveNormalizationPlatform() {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                PresalePlatformConfigQueries.presaleEnabledWrapper());
        if (platforms == null || platforms.isEmpty()) {
            return null;
        }
        return platforms.get(0);
    }

    private List<Map<String, Object>> toCandidatePayload(List<RawCompetitorMention> rawMentions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RawCompetitorMention item : rawMentions) {
            if (item == null || !StringUtils.hasText(item.name())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.name());
            row.put("count", item.mentionCount());
            out.add(row);
        }
        return out;
    }

    private List<ExtractedCompetitor> parseAndValidate(String rawResponse,
                                                       List<RawCompetitorMention> rawMentions) throws Exception {
        Map<String, Integer> countByName = new LinkedHashMap<>();
        for (RawCompetitorMention item : rawMentions) {
            if (item != null && StringUtils.hasText(item.name())) {
                countByName.putIfAbsent(item.name().trim(), item.mentionCount());
            }
        }
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode groups = root == null ? null : root.get("normalized_competitors");
        if (groups == null || !groups.isArray()) {
            throw new IllegalArgumentException("normalized_competitors must be array");
        }

        Set<String> usedAliases = new HashSet<>();
        List<ExtractedCompetitor> normalized = new ArrayList<>();
        for (JsonNode group : groups) {
            List<String> aliases = readValidAliases(group.get("aliases"), countByName.keySet(), usedAliases);
            if (aliases.isEmpty()) {
                continue;
            }
            String canonical = group.path("canonical_name").asText(null);
            if (!StringUtils.hasText(canonical) || !aliases.contains(canonical.trim())) {
                canonical = pickDisplayAlias(aliases, countByName);
            } else {
                canonical = canonical.trim();
            }
            int count = aliases.stream()
                    .mapToInt(alias -> countByName.getOrDefault(alias, 0))
                    .sum();
            normalized.add(new ExtractedCompetitor(canonical, count, aliases));
        }

        for (String rawName : countByName.keySet()) {
            if (!usedAliases.contains(rawName)) {
                normalized.add(new ExtractedCompetitor(rawName, countByName.get(rawName), List.of(rawName)));
            }
        }

        return mergeDuplicateCanonicalNames(normalized).stream()
                .sorted(Comparator
                        .comparing(ExtractedCompetitor::mentionCount, Comparator.reverseOrder())
                        .thenComparing(ExtractedCompetitor::name))
                .limit(MAX_EXTRACTED_COMPETITORS)
                .toList();
    }

    private List<String> readValidAliases(JsonNode aliasesNode, Set<String> allowedNames, Set<String> usedAliases) {
        if (aliasesNode == null || !aliasesNode.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (JsonNode aliasNode : aliasesNode) {
            if (aliasNode == null || !aliasNode.isTextual()) {
                continue;
            }
            String alias = aliasNode.asText().trim();
            if (!allowedNames.contains(alias) || usedAliases.contains(alias)) {
                continue;
            }
            aliases.add(alias);
            usedAliases.add(alias);
        }
        return new ArrayList<>(aliases);
    }

    private String pickDisplayAlias(List<String> aliases, Map<String, Integer> countByName) {
        return aliases.stream()
                .sorted(Comparator
                        .comparing((String alias) -> countByName.getOrDefault(alias, 0), Comparator.reverseOrder())
                        .thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElse(aliases.get(0));
    }

    private List<ExtractedCompetitor> mergeDuplicateCanonicalNames(List<ExtractedCompetitor> source) {
        Map<String, MutableGroup> byName = new LinkedHashMap<>();
        for (ExtractedCompetitor item : source) {
            if (item == null || !StringUtils.hasText(item.name())) {
                continue;
            }
            MutableGroup group = byName.computeIfAbsent(item.name().trim(), MutableGroup::new);
            group.count += item.mentionCount();
            group.aliases.addAll(item.aliases());
        }
        return byName.values().stream()
                .map(group -> new ExtractedCompetitor(group.name, group.count, new ArrayList<>(group.aliases)))
                .toList();
    }

    private List<ExtractedCompetitor> fallbackTop(List<RawCompetitorMention> rawMentions) {
        return rawMentions.stream()
                .filter(item -> item != null && StringUtils.hasText(item.name()))
                .sorted(Comparator
                        .comparing(RawCompetitorMention::mentionCount, Comparator.reverseOrder())
                        .thenComparing(RawCompetitorMention::name))
                .limit(MAX_EXTRACTED_COMPETITORS)
                .map(item -> new ExtractedCompetitor(item.name(), item.mentionCount(), List.of(item.name())))
                .toList();
    }

    public record NormalizationOutcome(List<ExtractedCompetitor> competitors, boolean llmCalled) {
        public NormalizationOutcome {
            competitors = competitors == null ? List.of() : List.copyOf(competitors);
        }
    }

    private static final class MutableGroup {
        private final String name;
        private final Set<String> aliases = new LinkedHashSet<>();
        private int count;

        private MutableGroup(String name) {
            this.name = name;
        }
    }
}
