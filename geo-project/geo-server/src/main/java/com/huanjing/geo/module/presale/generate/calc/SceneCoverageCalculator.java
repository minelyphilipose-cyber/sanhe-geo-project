package com.huanjing.geo.module.presale.generate.calc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryItem;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SceneCoverageCalculator {

    private static final Logger log = LoggerFactory.getLogger(SceneCoverageCalculator.class);

    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;
    @Value("${presale.prompt.active-version:v2}")
    private String activePromptTemplateVersion;

    public SceneAndIntentResult compute(Long versionId,
                                        RawSnapshotDTO raw,
                                        Map<String, Integer> intentTotalPrompts) {
        List<String> whitelistedPlatformCodes = aiPlatformConfigMapper.selectList(
                        PresalePlatformConfigQueries.presaleEnabledWrapper()
                ).stream()
                .map(AiPlatformConfig::getPlatformCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
        Set<String> allPlatforms = new HashSet<>(
                whitelistedPlatformCodes == null ? List.of() : whitelistedPlatformCodes
        );

        Set<String> degraded = raw == null || raw.getTestSummary() == null || raw.getTestSummary().getDegradedPlatforms() == null
                ? Set.of() : new HashSet<>(raw.getTestSummary().getDegradedPlatforms());
        Set<String> effectivePlatforms = new HashSet<>(allPlatforms);
        effectivePlatforms.removeAll(degraded);
        int threshold = (int) Math.ceil(effectivePlatforms.size() / 2.0);

        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getTemplateVersion, activePromptTemplateVersion)
                        .eq(PresalePromptTemplate::getHasCompetitorVar, 0)
                        .orderByAsc(PresalePromptTemplate::getSortOrder)
                        .orderByAsc(PresalePromptTemplate::getId)
        );

        List<PresaleAiPromptResult> batch1Rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getBatchNo, 1)
        );

        Map<Long, Set<String>> hitPlatformsByTemplate = new HashMap<>();
        Map<Long, List<Integer>> rankingsByTemplate = new HashMap<>();
        Map<Long, List<PresaleAiPromptResult>> rowsByTemplate = new HashMap<>();
        for (PresaleAiPromptResult row : batch1Rows) {
            rowsByTemplate.computeIfAbsent(row.getPromptTemplateId(), ignored -> new ArrayList<>()).add(row);
            if (!effectivePlatforms.contains(row.getPlatformCode())) {
                continue;
            }
            if (!Integer.valueOf(1).equals(row.getIsMentioned())) {
                continue;
            }
            hitPlatformsByTemplate
                    .computeIfAbsent(row.getPromptTemplateId(), ignored -> new HashSet<>())
                    .add(row.getPlatformCode());
            if (row.getRanking() != null) {
                rankingsByTemplate.computeIfAbsent(row.getPromptTemplateId(), ignored -> new ArrayList<>())
                        .add(row.getRanking());
            }
        }

        Map<PresaleIntentCode, List<TemplateWithCovered>> byIntent = new EnumMap<>(PresaleIntentCode.class);
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            byIntent.put(intent, new ArrayList<>());
        }
        for (PresalePromptTemplate template : templates) {
            int hitCount = hitPlatformsByTemplate.getOrDefault(template.getId(), Set.of()).size();
            boolean covered = hitCount >= threshold;
            PresaleIntentCode intent = PresaleIntentCode.fromLabel(template.getCategory());
            byIntent.get(intent).add(new TemplateWithCovered(template, intent, covered));
        }

        List<IntentBreakdown> intentBreakdown = new ArrayList<>();
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            List<TemplateWithCovered> list = byIntent.getOrDefault(intent, List.of());
            int total = intentTotalPrompts.getOrDefault(intent.getCode(), 0);
            int covered = (int) list.stream().filter(TemplateWithCovered::covered).count();
            double coverageRate = total == 0 ? 0.0 : (covered * 100.0 / total);

            List<Integer> rankings = list.stream()
                    .flatMap(t -> rankingsByTemplate.getOrDefault(t.template().getId(), List.of()).stream())
                    .toList();
            Double avgRanking = rankings.isEmpty() ? null : rankings.stream().mapToInt(Integer::intValue).average().getAsDouble();

            intentBreakdown.add(IntentBreakdown.builder()
                    .category(intent.getLabel())
                    .businessValue(intent.businessValue())
                    .totalPrompts(total)
                    .coveredPrompts(covered)
                    .coverageRate(coverageRate)
                    .avgRanking(avgRanking)
                    .build());
        }

        List<String> topCompetitorDisplayNames = raw == null || raw.getCompetitors() == null
                ? List.of()
                : raw.getCompetitors().stream()
                .map(Competitor::getName)
                .filter(name -> name != null && !name.isBlank())
                .limit(3)
                .toList();

        SceneCoverageGroup highGroup = buildGroup(byIntent,
                Set.of(PresaleIntentCode.RECOMMENDATION, PresaleIntentCode.COMPARISON),
                intentTotalPrompts,
                topCompetitorDisplayNames,
                rowsByTemplate);
        SceneCoverageGroup midGroup = buildGroup(byIntent,
                Set.of(PresaleIntentCode.INQUIRY, PresaleIntentCode.COGNITIVE),
                intentTotalPrompts,
                topCompetitorDisplayNames,
                rowsByTemplate);
        SceneCoverageGroup lowGroup = buildGroup(byIntent,
                Set.of(PresaleIntentCode.SCENARIO),
                intentTotalPrompts,
                topCompetitorDisplayNames,
                rowsByTemplate);

        ComputedSnapshotDTO.SceneCoverage sceneCoverage = ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(highGroup)
                .midValue(midGroup)
                .lowValue(lowGroup)
                .build();
        return new SceneAndIntentResult(sceneCoverage, intentBreakdown);
    }

    private SceneCoverageGroup buildGroup(Map<PresaleIntentCode, List<TemplateWithCovered>> byIntent,
                                          Set<PresaleIntentCode> intents,
                                          Map<String, Integer> intentTotalPrompts,
                                          List<String> topCompetitorDisplayNames,
                                          Map<Long, List<PresaleAiPromptResult>> rowsByTemplate) {
        List<TemplateWithCovered> combined = intents.stream()
                .flatMap(intent -> byIntent.getOrDefault(intent, List.of()).stream())
                .toList();

        int total = intents.stream().mapToInt(i -> intentTotalPrompts.getOrDefault(i.getCode(), 0)).sum();
        int covered = (int) combined.stream().filter(TemplateWithCovered::covered).count();
        double coverageRate = total == 0 ? 0.0 : (covered * 100.0 / total);

        List<SceneQueryItem> coveredQueries = combined.stream()
                .filter(TemplateWithCovered::covered)
                .map(item -> SceneQueryItem.builder()
                        .promptCode(item.template().getPromptCode())
                        .promptContent(item.template().getPromptContent())
                        .category(item.intent().getLabel())
                        .build())
                .toList();

        List<SceneQueryMissing> missingQueries = combined.stream()
                .filter(item -> !item.covered())
                .map(item -> SceneQueryMissing.builder()
                        .promptCode(item.template().getPromptCode())
                        .promptContent(item.template().getPromptContent())
                        .category(item.intent().getLabel())
                        .topCompetitorCoverage(resolveTopCompetitorCoverage(
                                rowsByTemplate.getOrDefault(item.template().getId(), List.of()),
                                topCompetitorDisplayNames))
                        .build())
                .toList();

        return SceneCoverageGroup.builder()
                .total(total)
                .covered(covered)
                .coverageRate(coverageRate)
                .coveredQueries(coveredQueries)
                .missingQueries(missingQueries)
                .build();
    }

    private List<String> resolveTopCompetitorCoverage(List<PresaleAiPromptResult> rows,
                                                      List<String> topDisplayNames) {
        if (rows == null || rows.isEmpty() || topDisplayNames == null || topDisplayNames.isEmpty()) {
            return List.of();
        }
        Set<String> mentionedNormalized = new HashSet<>();
        for (PresaleAiPromptResult row : rows) {
            if (row.getMentionedCompetitors() == null || row.getMentionedCompetitors().isBlank()) {
                continue;
            }
            try {
                JsonNode array = objectMapper.readTree(row.getMentionedCompetitors());
                if (!array.isArray()) {
                    continue;
                }
                for (JsonNode item : array) {
                    if (item.isTextual()) {
                        mentionedNormalized.add(competitorAggregator.normalizeName(item.asText()));
                    }
                }
            } catch (Exception ex) {
                log.warn("Skip invalid mentioned_competitors, promptResultId={}", row.getId(), ex);
            }
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String displayName : topDisplayNames) {
            if (mentionedNormalized.contains(competitorAggregator.normalizeName(displayName))) {
                ordered.add(displayName);
            }
        }
        return ordered.stream().limit(3).toList();
    }

    private record TemplateWithCovered(PresalePromptTemplate template,
                                       PresaleIntentCode intent,
                                       boolean covered) {
    }
}
