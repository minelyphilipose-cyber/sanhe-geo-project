package com.huanjing.geo.module.presale.generate.calc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryItem;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int COGNITIVE_COVERAGE_THRESHOLD = 10;
    private static final int COMPARISON_COVERAGE_THRESHOLD = 10;
    private static final int JUDGE_INTENT_COVERAGE_PLATFORM_DIVISOR = 3;
    private static final String COMPARISON_STANCE_COMPETITOR = "competitor";

    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;

    public SceneAndIntentResult compute(Long versionId,
                                        RawSnapshotDTO raw,
                                        Map<String, Integer> intentTotalPrompts) {
        return compute(versionId, raw, intentTotalPrompts, List.of());
    }

    public SceneAndIntentResult compute(Long versionId,
                                        RawSnapshotDTO raw,
                                        Map<String, Integer> intentTotalPrompts,
                                        List<PlatformIntentCell> platformIntentCells) {
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

        List<PresaleReportVersionPromptTemplate> templates = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );

        List<PresaleAiPromptResult> promptRows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .in(PresaleAiPromptResult::getBatchNo, List.of(1, 2))
        );

        Map<Long, Set<String>> hitPlatformsByTemplate = new HashMap<>();
        Map<Long, List<Integer>> rankingsByTemplate = new HashMap<>();
        Map<Long, List<PresaleAiPromptResult>> rowsByTemplate = new HashMap<>();
        Map<Long, String> renderedPromptByTemplate = new HashMap<>();
        for (PresaleAiPromptResult row : promptRows) {
            rowsByTemplate.computeIfAbsent(row.getPromptTemplateId(), ignored -> new ArrayList<>()).add(row);
            if (row.getPromptTemplateId() != null && row.getRequestPromptContent() != null
                    && !row.getRequestPromptContent().isBlank()) {
                renderedPromptByTemplate.putIfAbsent(row.getPromptTemplateId(), row.getRequestPromptContent());
            }
            if (!Integer.valueOf(1).equals(row.getBatchNo())) {
                continue;
            }
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
        Map<PresaleIntentCode, IntentCoverage> judgeCoverageByIntent = buildJudgeCoverageByIntent(
                platformIntentCells, effectivePlatforms);
        for (PresaleReportVersionPromptTemplate template : templates) {
            if (template.getId() != null && template.getPromptContent() != null && !template.getPromptContent().isBlank()) {
                renderedPromptByTemplate.putIfAbsent(template.getId(), template.getPromptContent());
            }
        }
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            byIntent.put(intent, new ArrayList<>());
        }
        for (PresaleReportVersionPromptTemplate template : templates) {
            PresaleIntentCode intent = PresaleIntentCode.fromLabel(template.getCategory());
            if (!isTemplateIncludedForIntent(template, intent)) {
                continue;
            }
            int hitCount = hitPlatformsByTemplate.getOrDefault(template.getId(), Set.of()).size();
            boolean covered = isJudgeIntent(intent)
                    ? judgeCoverageByIntent.getOrDefault(intent, IntentCoverage.empty()).isCovered()
                    : hitCount >= threshold;
            byIntent.get(intent).add(new TemplateWithCovered(template, intent, covered));
        }

        List<IntentBreakdown> intentBreakdown = new ArrayList<>();
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            List<TemplateWithCovered> list = byIntent.getOrDefault(intent, List.of());
            int total = intentTotalPrompts.getOrDefault(intent.getCode(), 0);
            IntentCoverage judgeCoverage = judgeCoverageByIntent.get(intent);
            int covered = judgeCoverage == null
                    ? (int) list.stream().filter(TemplateWithCovered::covered).count()
                    : toPromptEquivalentCovered(total, judgeCoverage);
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
                rowsByTemplate,
                renderedPromptByTemplate);
        SceneCoverageGroup midGroup = buildGroup(byIntent,
                Set.of(PresaleIntentCode.INQUIRY, PresaleIntentCode.COGNITIVE),
                intentTotalPrompts,
                topCompetitorDisplayNames,
                rowsByTemplate,
                renderedPromptByTemplate);
        SceneCoverageGroup lowGroup = buildGroup(byIntent,
                Set.of(PresaleIntentCode.SCENARIO),
                intentTotalPrompts,
                topCompetitorDisplayNames,
                rowsByTemplate,
                renderedPromptByTemplate);

        ComputedSnapshotDTO.SceneCoverage sceneCoverage = ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(highGroup)
                .midValue(midGroup)
                .lowValue(lowGroup)
                .build();
        return new SceneAndIntentResult(sceneCoverage, intentBreakdown);
    }

    private Map<PresaleIntentCode, IntentCoverage> buildJudgeCoverageByIntent(List<PlatformIntentCell> cells,
                                                                              Set<String> effectivePlatforms) {
        Map<PresaleIntentCode, IntentCoverage> result = new EnumMap<>(PresaleIntentCode.class);
        if (cells == null || cells.isEmpty()) {
            return result;
        }
        for (PlatformIntentCell cell : cells) {
            if (cell == null || cell.getIntentCode() == null || cell.getPlatformCode() == null) {
                continue;
            }
            PresaleIntentCode intent;
            try {
                intent = PresaleIntentCode.fromCode(cell.getIntentCode());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (!isJudgeIntent(intent) || !effectivePlatforms.contains(cell.getPlatformCode())) {
                continue;
            }
            IntentCoverage coverage = result.computeIfAbsent(intent,
                    ignored -> new IntentCoverage(effectivePlatforms.size()));
            coverage.totalCells++;
            if (isJudgeCellCovered(intent, cell)) {
                coverage.coveredCells++;
            }
        }
        return result;
    }

    private boolean isJudgeCellCovered(PresaleIntentCode intent, PlatformIntentCell cell) {
        if (cell == null || cell.getMentionRate() == null) {
            return false;
        }
        int score = cell.getMentionRate();
        if (intent == PresaleIntentCode.COGNITIVE) {
            return score >= COGNITIVE_COVERAGE_THRESHOLD;
        }
        if (intent == PresaleIntentCode.COMPARISON) {
            return score >= COMPARISON_COVERAGE_THRESHOLD
                    && !COMPARISON_STANCE_COMPETITOR.equals(cell.getStance());
        }
        return false;
    }

    private int toPromptEquivalentCovered(int totalPrompts, IntentCoverage coverage) {
        if (totalPrompts <= 0 || coverage == null || coverage.totalCells <= 0) {
            return 0;
        }
        return (int) Math.round(totalPrompts * coverage.coveredCells * 1.0d / coverage.totalCells);
    }

    private boolean isTemplateIncludedForIntent(PresaleReportVersionPromptTemplate template, PresaleIntentCode intent) {
        Integer hasCompetitorVar = template.getHasCompetitorVar();
        if (intent == PresaleIntentCode.COMPARISON) {
            return hasCompetitorVar != null && hasCompetitorVar == 1;
        }
        return hasCompetitorVar != null && hasCompetitorVar == 0;
    }

    private boolean isJudgeIntent(PresaleIntentCode intent) {
        return intent == PresaleIntentCode.COGNITIVE || intent == PresaleIntentCode.COMPARISON;
    }

    private SceneCoverageGroup buildGroup(Map<PresaleIntentCode, List<TemplateWithCovered>> byIntent,
                                          Set<PresaleIntentCode> intents,
                                          Map<String, Integer> intentTotalPrompts,
                                          List<String> topCompetitorDisplayNames,
                                          Map<Long, List<PresaleAiPromptResult>> rowsByTemplate,
                                          Map<Long, String> renderedPromptByTemplate) {
        List<TemplateWithCovered> combined = intents.stream()
                .flatMap(intent -> byIntent.getOrDefault(intent, List.of()).stream())
                .toList();

        int total = intents.stream().mapToInt(i -> intentTotalPrompts.getOrDefault(i.getCode(), 0)).sum();
        int covered = (int) combined.stream().filter(TemplateWithCovered::covered).count();
        double coverageRate = total == 0 ? 0.0 : (covered * 100.0 / total);

        List<SceneQueryItem> coveredQueries = combined.stream()
                .filter(TemplateWithCovered::covered)
                .map(item -> SceneQueryItem.builder()
                        .promptCode(item.template().getSourcePromptCode())
                        .promptContent(resolveRenderedPrompt(renderedPromptByTemplate, item.template().getId()))
                        .category(item.intent().getLabel())
                        .build())
                .toList();

        List<SceneQueryMissing> missingQueries = combined.stream()
                .filter(item -> !item.covered())
                .map(item -> SceneQueryMissing.builder()
                        .promptCode(item.template().getSourcePromptCode())
                        .promptContent(resolveRenderedPrompt(renderedPromptByTemplate, item.template().getId()))
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

    private String resolveRenderedPrompt(Map<Long, String> renderedPromptByTemplate, Long templateId) {
        if (templateId == null) {
            return "—";
        }
        String rendered = renderedPromptByTemplate.get(templateId);
        if (rendered == null || rendered.isBlank()) {
            return "—";
        }
        return rendered;
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

    private record TemplateWithCovered(PresaleReportVersionPromptTemplate template,
                                       PresaleIntentCode intent,
                                       boolean covered) {
    }

    private static class IntentCoverage {
        private final int effectivePlatformCount;
        int totalCells;
        int coveredCells;

        IntentCoverage(int effectivePlatformCount) {
            this.effectivePlatformCount = effectivePlatformCount;
        }

        static IntentCoverage empty() {
            return new IntentCoverage(0);
        }

        boolean isCovered() {
            return effectivePlatformCount > 0
                    && coveredCells >= (int) Math.ceil(effectivePlatformCount * 1.0d
                    / JUDGE_INTENT_COVERAGE_PLATFORM_DIVISOR);
        }
    }
}
