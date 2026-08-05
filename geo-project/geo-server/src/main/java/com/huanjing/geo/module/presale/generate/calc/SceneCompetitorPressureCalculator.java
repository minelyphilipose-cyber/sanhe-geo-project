package com.huanjing.geo.module.presale.generate.calc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SceneCompetitorPressureCalculator {

    private static final Logger log = LoggerFactory.getLogger(SceneCompetitorPressureCalculator.class);
    private static final String RECOMMENDATION_CATEGORY = "推荐型";
    private static final int TARGET_LOW_THRESHOLD = 0;

    private final PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;

    public SceneCompetitorPressure compute(Long versionId, RawSnapshotDTO raw) {
        List<PresaleReportVersionPromptTemplate> templates = loadRecommendationTemplates(versionId);
        List<String> competitorNames = resolveCompetitorNames(raw);
        if (templates.isEmpty() || competitorNames.isEmpty()) {
            return SceneCompetitorPressure.builder()
                    .hvRecoTotal(templates.size())
                    .suppressedSceneCount(0)
                    .items(List.of())
                    .build();
        }

        Set<String> degraded = raw == null
                || raw.getTestSummary() == null
                || raw.getTestSummary().getDegradedPlatforms() == null
                ? Set.of()
                : new HashSet<>(raw.getTestSummary().getDegradedPlatforms());
        Map<Long, List<PresaleAiPromptResult>> rowsByTemplate = loadRowsByTemplate(versionId, templates, degraded);

        List<SceneCompetitorPressure.Item> items = new ArrayList<>();
        Map<String, Integer> suppressedCompetitorTotals = new HashMap<>();
        for (PresaleReportVersionPromptTemplate template : templates) {
            SceneCompetitorPressure.Item item = buildItem(template, rowsByTemplate.getOrDefault(template.getId(), List.of()),
                    competitorNames);
            items.add(item);
            if (Boolean.TRUE.equals(item.getSuppressed())) {
                for (SceneCompetitorPressure.CompetitorPressure competitor : item.getCompetitors()) {
                    int count = competitor.getMentionedPlatformCount() == null ? 0 : competitor.getMentionedPlatformCount();
                    if (count > 0) {
                        suppressedCompetitorTotals.merge(competitor.getName(), count, Integer::sum);
                    }
                }
            }
        }

        int suppressedCount = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.getSuppressed())).count();
        String topSuppressing = suppressedCompetitorTotals.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
        return SceneCompetitorPressure.builder()
                .hvRecoTotal(items.size())
                .suppressedSceneCount(suppressedCount)
                .topSuppressingCompetitor(topSuppressing)
                .items(items)
                .build();
    }

    private SceneCompetitorPressure.Item buildItem(PresaleReportVersionPromptTemplate template,
                                                   List<PresaleAiPromptResult> rows,
                                                   List<String> competitorNames) {
        Set<String> evaluatedPlatforms = rows.stream()
                .map(PresaleAiPromptResult::getPlatformCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> targetMentionedPlatforms = rows.stream()
                .filter(row -> Integer.valueOf(1).equals(row.getIsMentioned()))
                .map(PresaleAiPromptResult::getPlatformCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Set<String>> competitorPlatforms = new LinkedHashMap<>();
        for (String displayName : competitorNames) {
            competitorPlatforms.put(displayName, new LinkedHashSet<>());
        }
        for (PresaleAiPromptResult row : rows) {
            Set<String> mentioned = parseMentionedCompetitorNames(row);
            for (String rawName : mentioned) {
                competitorAggregator.matchCompetitorDisplayName(rawName, competitorNames)
                        .filter(displayName -> StringUtils.hasText(row.getPlatformCode()))
                        .ifPresent(displayName -> competitorPlatforms.get(displayName).add(row.getPlatformCode()));
            }
        }

        List<SceneCompetitorPressure.CompetitorPressure> competitors = competitorPlatforms.entrySet().stream()
                .map(entry -> SceneCompetitorPressure.CompetitorPressure.builder()
                        .name(entry.getKey())
                        .mentionedPlatformCount(entry.getValue().size())
                        .build())
                .sorted(Comparator
                        .comparing((SceneCompetitorPressure.CompetitorPressure item) ->
                                item.getMentionedPlatformCount() == null ? 0 : item.getMentionedPlatformCount())
                        .reversed()
                        .thenComparing(SceneCompetitorPressure.CompetitorPressure::getName))
                .toList();
        int platformsEvaluated = evaluatedPlatforms.size();
        int competitorThreshold = platformsEvaluated <= 0 ? Integer.MAX_VALUE : (int) Math.ceil(platformsEvaluated / 2.0);
        boolean competitorPresent = competitors.stream()
                .anyMatch(item -> (item.getMentionedPlatformCount() == null ? 0 : item.getMentionedPlatformCount()) >= competitorThreshold);
        boolean suppressed = platformsEvaluated > 0
                && targetMentionedPlatforms.size() <= TARGET_LOW_THRESHOLD
                && competitorPresent;

        return SceneCompetitorPressure.Item.builder()
                .promptCode(template.getSourcePromptCode())
                .query(resolveQuery(template, rows))
                .intent(RECOMMENDATION_CATEGORY)
                .targetMentionedPlatformCount(targetMentionedPlatforms.size())
                .platformsEvaluated(platformsEvaluated)
                .competitors(competitors)
                .suppressed(suppressed)
                .build();
    }

    private List<PresaleReportVersionPromptTemplate> loadRecommendationTemplates(Long versionId) {
        List<PresaleReportVersionPromptTemplate> rows = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getCategory, RECOMMENDATION_CATEGORY)
                        .and(q -> q.isNull(PresaleReportVersionPromptTemplate::getHasCompetitorVar)
                                .or()
                                .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, 0))
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(this::isRecommendationTemplate)
                .sorted(Comparator
                        .comparing((PresaleReportVersionPromptTemplate item) ->
                                item.getSortOrderInVersion() == null ? Integer.MAX_VALUE : item.getSortOrderInVersion())
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList();
    }

    private boolean isRecommendationTemplate(PresaleReportVersionPromptTemplate template) {
        if (template == null || !RECOMMENDATION_CATEGORY.equals(template.getCategory())) {
            return false;
        }
        boolean naturalRecommendation = template.getHasCompetitorVar() == null || Integer.valueOf(0).equals(template.getHasCompetitorVar());
        return naturalRecommendation && isHighValue(template.getBusinessValue());
    }

    private boolean isHighValue(String businessValue) {
        if (!StringUtils.hasText(businessValue)) {
            return true;
        }
        String normalized = businessValue.trim();
        return "高".equals(normalized) || "高价值".equals(normalized) || "HIGH".equalsIgnoreCase(normalized);
    }

    private Map<Long, List<PresaleAiPromptResult>> loadRowsByTemplate(Long versionId,
                                                                       List<PresaleReportVersionPromptTemplate> templates,
                                                                       Set<String> degraded) {
        List<Long> templateIds = templates.stream()
                .map(PresaleReportVersionPromptTemplate::getId)
                .filter(id -> id != null)
                .toList();
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<PresaleAiPromptResult> q = new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getEffectiveSample, true)
                .eq(PresaleAiPromptResult::getBatchNo, 1)
                .in(PresaleAiPromptResult::getPromptTemplateId, templateIds);
        if (degraded != null && !degraded.isEmpty()) {
            q.notIn(PresaleAiPromptResult::getPlatformCode, degraded);
        }
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(q);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row.getPromptTemplateId() != null)
                .collect(Collectors.groupingBy(PresaleAiPromptResult::getPromptTemplateId));
    }

    private List<String> resolveCompetitorNames(RawSnapshotDTO raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw.getSpecifiedCompetitors() != null && !raw.getSpecifiedCompetitors().isEmpty()) {
            return raw.getSpecifiedCompetitors().stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        if (raw.getCompetitors() == null) {
            return List.of();
        }
        return raw.getCompetitors().stream()
                .map(Competitor::getName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Set<String> parseMentionedCompetitorNames(PresaleAiPromptResult row) {
        if (row == null || !StringUtils.hasText(row.getMentionedCompetitors())) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(row.getMentionedCompetitors());
            if (node == null || !node.isArray()) {
                return Set.of();
            }
            Set<String> out = new HashSet<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                    out.add(item.asText().trim());
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("Skip invalid mentioned_competitors, promptResultId={}", row.getId(), ex);
            return Set.of();
        }
    }

    private String resolveQuery(PresaleReportVersionPromptTemplate template, List<PresaleAiPromptResult> rows) {
        for (PresaleAiPromptResult row : rows) {
            if (StringUtils.hasText(row.getRequestPromptContent())) {
                return row.getRequestPromptContent();
            }
        }
        return StringUtils.hasText(template.getPromptContent()) ? template.getPromptContent() : "—";
    }
}
