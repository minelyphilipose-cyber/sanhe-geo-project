package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * batch1 竞品提取聚合器,供 Orchestrator 与 Assembler 共用。
 */
@Component
public class PresaleCompetitorAggregator {

    private static final Logger log = LoggerFactory.getLogger(PresaleCompetitorAggregator.class);

    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final ObjectMapper objectMapper;
    private final CompetitorNameNormalizer nameNormalizer;

    @Autowired
    public PresaleCompetitorAggregator(PresaleAiPromptResultMapper aiPromptResultMapper,
                                       ObjectMapper objectMapper,
                                       CompetitorNameNormalizer nameNormalizer) {
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.objectMapper = objectMapper;
        this.nameNormalizer = nameNormalizer;
    }

    PresaleCompetitorAggregator(PresaleAiPromptResultMapper aiPromptResultMapper,
                                ObjectMapper objectMapper) {
        this(aiPromptResultMapper, objectMapper, new CompetitorNameNormalizer());
    }

    public Batch1MentionStats aggregateBatch1MentionStats(Long versionId, String brandName) {
        return aggregateBatch1MentionStats(versionId, Collections.singletonList(brandName));
    }

    public Batch1MentionStats aggregateBatch1MentionStats(Long versionId, Collection<String> selfBrandNames) {
        return aggregateBatch1MentionStats(versionId, selfBrandNames, Set.of());
    }

    public Batch1MentionStats aggregateBatch1MentionStats(Long versionId,
                                                          Collection<String> selfBrandNames,
                                                          Set<String> excludedPlatformCodes) {
        Set<String> excluded = excludedPlatformCodes == null ? Set.of() : excludedPlatformCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getEffectiveSample, true)
                        .eq(PresaleAiPromptResult::getBatchNo, 1)
                        .notIn(!excluded.isEmpty(), PresaleAiPromptResult::getPlatformCode, excluded)
                        .isNotNull(PresaleAiPromptResult::getIsMentioned)
        );
        if (rows == null || rows.isEmpty()) {
            return new Batch1MentionStats(Collections.emptyMap(), Collections.emptyMap(), 0);
        }

        Set<String> normalizedSelfNames = normalizeSelfNames(selfBrandNames);
        Map<String, Integer> countByNormalized = new HashMap<>();
        Map<String, String> displayByNormalized = new LinkedHashMap<>();

        for (PresaleAiPromptResult row : rows) {
            String mentionedCompetitors = row.getMentionedCompetitors();
            if (mentionedCompetitors == null || mentionedCompetitors.isBlank()) {
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(mentionedCompetitors);
                if (!node.isArray()) {
                    continue;
                }

                Set<String> rowDedup = new HashSet<>();
                for (JsonNode item : node) {
                    if (!item.isTextual()) {
                        continue;
                    }
                    String display = item.asText() == null ? "" : item.asText().trim();
                    if (display.isEmpty()) {
                        continue;
                    }
                    String normalized = normalizeName(display);
                    if (normalized.isEmpty() || normalizedSelfNames.contains(normalized)) {
                        continue;
                    }
                    if (!rowDedup.add(normalized)) {
                        continue;
                    }
                    countByNormalized.merge(normalized, 1, Integer::sum);
                    displayByNormalized.putIfAbsent(normalized, display);
                }
            } catch (Exception ex) {
                log.warn("Skip invalid mentioned_competitors json, versionId={}, promptResultId={}",
                        versionId, row.getId(), ex);
            }
        }

        return new Batch1MentionStats(
                Collections.unmodifiableMap(countByNormalized),
                Collections.unmodifiableMap(displayByNormalized),
                rows.size()
        );
    }

    public List<String> extractTopCompetitorsFromBatch1(Long versionId, String brandName) {
        return extractTopCompetitorStatsFromBatch1(versionId, Collections.singletonList(brandName)).stream()
                .map(ExtractedCompetitor::name)
                .toList();
    }

    public List<String> extractTopCompetitorsFromBatch1(Long versionId, Collection<String> selfBrandNames) {
        return extractTopCompetitorStatsFromBatch1(versionId, selfBrandNames).stream()
                .map(ExtractedCompetitor::name)
                .toList();
    }

    public List<String> extractTopCompetitorsFromBatch1(Long versionId,
                                                        Collection<String> selfBrandNames,
                                                        Set<String> excludedPlatformCodes) {
        return extractTopRawCompetitorMentions(versionId, selfBrandNames, 3, excludedPlatformCodes).stream()
                .map(RawCompetitorMention::name)
                .toList();
    }

    public List<RawCompetitorMention> extractTopRawCompetitorMentions(Long versionId, String brandName, int limit) {
        return extractTopRawCompetitorMentions(versionId, Collections.singletonList(brandName), limit);
    }

    public List<RawCompetitorMention> extractTopRawCompetitorMentions(Long versionId,
                                                                      Collection<String> selfBrandNames,
                                                                      int limit) {
        return extractTopRawCompetitorMentions(versionId, selfBrandNames, limit, Set.of());
    }

    public List<RawCompetitorMention> extractTopRawCompetitorMentions(Long versionId,
                                                                      Collection<String> selfBrandNames,
                                                                      int limit,
                                                                      Set<String> excludedPlatformCodes) {
        if (limit <= 0) {
            return List.of();
        }
        Batch1MentionStats stats = aggregateBatch1MentionStats(
                versionId, selfBrandNames, excludedPlatformCodes);
        return stats.countByNormalized().entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> new RawCompetitorMention(
                        stats.displayByNormalized().getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue(),
                        entry.getKey()
                ))
                .toList();
    }

    public List<ExtractedCompetitor> extractTopCompetitorStatsFromBatch1(Long versionId, String brandName) {
        return extractTopCompetitorStatsFromBatch1(versionId, Collections.singletonList(brandName));
    }

    public List<ExtractedCompetitor> extractTopCompetitorStatsFromBatch1(Long versionId,
                                                                         Collection<String> selfBrandNames) {
        return extractTopRawCompetitorMentions(versionId, selfBrandNames, 3).stream()
                .map(item -> new ExtractedCompetitor(item.name(), item.mentionCount(), List.of(item.name())))
                .toList();
    }

    private Set<String> normalizeSelfNames(Collection<String> selfBrandNames) {
        if (selfBrandNames == null || selfBrandNames.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String name : selfBrandNames) {
            String normalized = normalizeName(name);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return out;
    }

    /**
     * 语义归并:trim + 去所有空白 + lowercase。
     */
    public String normalizeName(String input) {
        return nameNormalizer.normalizeKey(input);
    }

    public Optional<String> matchCompetitorDisplayName(String rawName, List<String> candidateDisplayNames) {
        return nameNormalizer.matchDisplayName(rawName, candidateDisplayNames);
    }

    public record Batch1MentionStats(
            Map<String, Integer> countByNormalized,
            Map<String, String> displayByNormalized,
            int denominatorRows
    ) {
    }

    public record RawCompetitorMention(String name, int mentionCount, String normalizedName) {
    }

    public record ExtractedCompetitor(String name, int mentionCount, List<String> aliases) {
        public ExtractedCompetitor {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
