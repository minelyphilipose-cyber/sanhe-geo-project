package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * batch1 竞品提取聚合器,供 Orchestrator 与 Assembler 共用。
 */
@Component
public class PresaleCompetitorAggregator {

    private static final Logger log = LoggerFactory.getLogger(PresaleCompetitorAggregator.class);

    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final ObjectMapper objectMapper;

    public PresaleCompetitorAggregator(PresaleAiPromptResultMapper aiPromptResultMapper,
                                       ObjectMapper objectMapper) {
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.objectMapper = objectMapper;
    }

    public Batch1MentionStats aggregateBatch1MentionStats(Long versionId, String brandName) {
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getBatchNo, 1)
                        .isNotNull(PresaleAiPromptResult::getIsMentioned)
        );
        if (rows == null || rows.isEmpty()) {
            return new Batch1MentionStats(Collections.emptyMap(), Collections.emptyMap(), 0);
        }

        String normalizedBrand = normalizeName(brandName);
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
                    if (normalized.isEmpty() || normalized.equals(normalizedBrand)) {
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
        Batch1MentionStats stats = aggregateBatch1MentionStats(versionId, brandName);
        return stats.countByNormalized().entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(entry -> stats.displayByNormalized().getOrDefault(entry.getKey(), entry.getKey()))
                .toList();
    }

    /**
     * 语义归并:trim + 去所有空白 + lowercase。
     */
    public String normalizeName(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public record Batch1MentionStats(
            Map<String, Integer> countByNormalized,
            Map<String, String> displayByNormalized,
            int denominatorRows
    ) {
    }
}
