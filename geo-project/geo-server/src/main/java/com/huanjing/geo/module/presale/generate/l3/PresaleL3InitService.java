package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.editable.CompetitorSceneDescription;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.ExecutiveSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.FindingContent;
import com.huanjing.geo.module.presale.dto.snapshot.editable.KeyTakeaway;
import com.huanjing.geo.module.presale.dto.snapshot.editable.PhaseDescription;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresaleL3InitService {

    private static final Map<String, String> RULE_TITLE_MAP = buildRuleTitleMap();
    private static final String DEFAULT_ROI_DISCLAIMER = "基于行业平均模型的估算,实际效果受多种因素影响,建议结合业务实际情况评估。";

    private final ObjectMapper objectMapper;

    public String derive(String rawSnapshotJson, String computedSnapshotJson) {
        try {
            RawSnapshotDTO raw = objectMapper.readValue(rawSnapshotJson, RawSnapshotDTO.class);
            ComputedSnapshotDTO computed = objectMapper.readValue(computedSnapshotJson, ComputedSnapshotDTO.class);
            String brandName = requireBrandName(raw);
            Integer platformCount = requirePlatformCount(raw);
            Integer totalPrompts = requireTotalPrompts(raw);

            EditableContentDTO editable = new EditableContentDTO();
            editable.setReportTitle(brandName + " GEO 可见度诊断报告");
            editable.setReportSubtitle("基于 " + platformCount + " 个 AI 平台 × " + totalPrompts + " 条查询的深度分析");
            editable.setExecutiveSummary(buildExecutiveSummary(raw, computed, brandName, platformCount));
            editable.setKeyTakeaways(buildKeyTakeaways(computed));
            editable.setOptimizationFindingsContent(buildFindingContents(computed));
            editable.setPhaseDescriptions(buildPhaseDescriptions(computed));
            editable.setCompetitorSceneDescriptions(buildCompetitorScenes(raw));
            editable.setRoiDisclaimer(DEFAULT_ROI_DISCLAIMER);
            return objectMapper.writeValueAsString(editable);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "L3 init failed: " + e.getMessage());
        }
    }

    private ExecutiveSummary buildExecutiveSummary(RawSnapshotDTO raw,
                                                   ComputedSnapshotDTO computed,
                                                   String brandName,
                                                   Integer platformCount) {
        Double overall = requireOverallScore(computed);
        Double industryAvgOverall = requireIndustryAvgOverall(raw);
        int roundedOverall = (int) Math.round(overall);
        int delta = (int) Math.round(overall - industryAvgOverall);
        String deltaLabel = classifyDelta(delta);
        String industry = requireIndustry(raw);

        String headline = brandName + " 在 " + industry + " 行业综合得分 "
                + roundedOverall + " 分," + deltaLabel;

        String paragraph;
        int findingCount = computed == null || computed.getOptimizationFindings() == null
                ? 0 : computed.getOptimizationFindings().size();
        if (raw == null || raw.getCompetitors() == null || raw.getCompetitors().isEmpty()) {
            paragraph = brandName + " 在 " + platformCount + " 个 AI 平台的综合表现得分 "
                    + roundedOverall + " 分,本报告给出 " + findingCount + " 项优化建议。";
        } else {
            IntentBreakdown topIntent = resolveTopCoverageIntent(computed);
            String intentLabel = topIntent == null || topIntent.getCategory() == null
                    ? "核心意图"
                    : topIntent.getCategory();
            String coverage = topIntent == null || topIntent.getCoverageRate() == null
                    ? "0"
                    : String.valueOf((int) Math.round(topIntent.getCoverageRate()));
            String competitorName = raw.getCompetitors().get(0).getName() == null
                    ? "行业Top1竞品"
                    : raw.getCompetitors().get(0).getName();
            paragraph = "在 " + platformCount + " 个 AI 平台中," + brandName + " 的"
                    + intentLabel + "提及率最高,达 " + coverage + "%,"
                    + "相较行业 Top1 " + competitorName + "," + brandName + "的综合表现" + deltaLabel
                    + ",本报告给出 " + findingCount + " 项优化建议。";
        }

        return ExecutiveSummary.builder()
                .headline(headline)
                .paragraph(paragraph)
                .build();
    }

    private List<KeyTakeaway> buildKeyTakeaways(ComputedSnapshotDTO computed) {
        List<OptimizationFinding> findings = computed == null || computed.getOptimizationFindings() == null
                ? List.of()
                : computed.getOptimizationFindings();
        List<OptimizationFinding> sorted = findings.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(this::priorityOrder))
                .limit(5)
                .collect(Collectors.toList());

        List<KeyTakeaway> out = new ArrayList<>();
        int order = 1;
        for (OptimizationFinding finding : sorted) {
            String ruleCode = finding.getRuleCode();
            String title = RULE_TITLE_MAP.getOrDefault(ruleCode, ruleCode == null ? "未命名规则" : ruleCode);
            String desc = renderEvidence(finding.getEvidenceData(), finding.getCategory());
            out.add(KeyTakeaway.builder()
                    .orderNo(order++)
                    .title(title)
                    .description(desc)
                    .build());
        }
        return out;
    }

    private List<FindingContent> buildFindingContents(ComputedSnapshotDTO computed) {
        List<FindingContent> out = new ArrayList<>();
        List<OptimizationFinding> findings = computed == null || computed.getOptimizationFindings() == null
                ? List.of()
                : computed.getOptimizationFindings();
        for (OptimizationFinding finding : findings) {
            if (finding == null || finding.getFindingId() == null) {
                continue;
            }
            out.add(FindingContent.builder()
                    .findingId(finding.getFindingId())
                    .title(null)
                    .description(null)
                    .evidenceText(null)
                    .sortOrder(null)
                    .isHidden(false)
                    .build());
        }
        return out;
    }

    private List<PhaseDescription> buildPhaseDescriptions(ComputedSnapshotDTO computed) {
        Integer phase1Completed = extractPhase1CompletedCount(computed);
        if (phase1Completed == null) {
            return fallbackPhaseDescriptions();
        }
        List<PhaseDescription> out = new ArrayList<>();
        out.add(PhaseDescription.builder()
                .phaseNo(1)
                .title("基础优化阶段,聚焦" + phase1Completed + "项关键改动")
                .description(null)
                .build());
        out.add(PhaseDescription.builder().phaseNo(2).title("内容深化阶段").description(null).build());
        out.add(PhaseDescription.builder().phaseNo(3).title("持续运营阶段").description(null).build());
        return out;
    }

    private List<CompetitorSceneDescription> buildCompetitorScenes(RawSnapshotDTO raw) {
        if (raw == null || raw.getCompetitors() == null) {
            return List.of();
        }
        List<CompetitorSceneDescription> out = new ArrayList<>();
        raw.getCompetitors().forEach(c -> {
            if (c == null || c.getRank() == null) {
                return;
            }
            out.add(CompetitorSceneDescription.builder()
                    .competitorRank(c.getRank())
                    .sceneAdvantagesPolished(null)
                    .build());
        });
        return out;
    }

    private String requireBrandName(RawSnapshotDTO raw) {
        String brandName = raw == null || raw.getClientInfo() == null
                ? null : raw.getClientInfo().getBrandName();
        if (brandName == null || brandName.isBlank()) {
            throw new BizException(500, "L3 init failed: missing raw.client_info.brand_name");
        }
        return brandName;
    }

    private String requireIndustry(RawSnapshotDTO raw) {
        String industry = raw == null || raw.getClientInfo() == null
                ? null : raw.getClientInfo().getIndustry();
        if (industry == null || industry.isBlank()) {
            throw new BizException(500, "L3 init failed: missing raw.client_info.industry");
        }
        return industry;
    }

    private Integer requirePlatformCount(RawSnapshotDTO raw) {
        Integer value = raw == null || raw.getTestSummary() == null
                ? null : raw.getTestSummary().getTotalPlatforms();
        if (value == null) {
            throw new BizException(500, "L3 init failed: missing raw.test_summary.total_platforms");
        }
        return value;
    }

    private Integer requireTotalPrompts(RawSnapshotDTO raw) {
        Integer value = raw == null || raw.getTestSummary() == null
                ? null : raw.getTestSummary().getTotalPrompts();
        if (value == null) {
            throw new BizException(500, "L3 init failed: missing raw.test_summary.total_prompts");
        }
        return value;
    }

    private Double requireOverallScore(ComputedSnapshotDTO computed) {
        Double value = computed == null || computed.getScores() == null
                ? null : computed.getScores().getOverall();
        if (value == null) {
            throw new BizException(500, "L3 init failed: missing computed.scores.overall");
        }
        return value;
    }

    private Double requireIndustryAvgOverall(RawSnapshotDTO raw) {
        Double value = raw == null || raw.getBenchmarksFrozen() == null || raw.getBenchmarksFrozen().getIndustryAvg() == null
                ? null : raw.getBenchmarksFrozen().getIndustryAvg().getOverall();
        if (value == null) {
            throw new BizException(500, "L3 init failed: missing raw.benchmarks_frozen.industry_avg.overall");
        }
        return value;
    }

    private IntentBreakdown resolveTopCoverageIntent(ComputedSnapshotDTO computed) {
        if (computed == null || computed.getIntentBreakdown() == null || computed.getIntentBreakdown().isEmpty()) {
            return null;
        }
        return computed.getIntentBreakdown().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(it -> it.getCoverageRate() == null ? 0D : it.getCoverageRate()))
                .orElse(null);
    }

    private String classifyDelta(int delta) {
        if (delta >= 10) {
            return "显著领先";
        }
        if (delta >= 5) {
            return "略高于行业平均";
        }
        if (delta > -5) {
            return "与行业平均持平";
        }
        if (delta > -10) {
            return "略低于行业平均";
        }
        return "明显落后于行业平均";
    }

    private int priorityOrder(OptimizationFinding finding) {
        if (finding.getPriority() == null) {
            return 3;
        }
        return switch (finding.getPriority()) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private String renderEvidence(Map<String, Object> evidenceData, String category) {
        if (evidenceData == null || evidenceData.isEmpty()) {
            return "建议优先处理「" + (category == null ? "待归类" : category) + "」问题。";
        }
        String text = evidenceData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + String.valueOf(e.getValue()))
                .collect(Collectors.joining("，"));
        return "关键信号: " + text;
    }

    private Integer extractPhase1CompletedCount(ComputedSnapshotDTO computed) {
        if (computed == null || computed.getRoiSimulation() == null || computed.getRoiSimulation().getPhases() == null) {
            return null;
        }
        return computed.getRoiSimulation().getPhases().stream()
                .filter(Objects::nonNull)
                .filter(phase -> Integer.valueOf(1).equals(phase.getPhaseNo()))
                .map(phase -> phase.getCompletedOptimizationCount() == null ? 0 : phase.getCompletedOptimizationCount())
                .findFirst()
                .orElse(null);
    }

    private List<PhaseDescription> fallbackPhaseDescriptions() {
        List<PhaseDescription> out = new ArrayList<>();
        out.add(PhaseDescription.builder().phaseNo(1).title("阶段 1").description(null).build());
        out.add(PhaseDescription.builder().phaseNo(2).title("阶段 2").description(null).build());
        out.add(PhaseDescription.builder().phaseNo(3).title("阶段 3").description(null).build());
        return out;
    }

    private static Map<String, String> buildRuleTitleMap() {
        Map<String, String> map = new HashMap<>();
        map.put(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND, "推荐型查询覆盖偏低");
        map.put(RuleCodes.RULE_BRAND_AWARENESS_LOW, "品牌认知度偏低");
        map.put(RuleCodes.RULE_COMPARE_GAP, "对比型查询存在差距");
        map.put(RuleCodes.RULE_PLATFORM_IMBALANCE, "平台表现不均衡");
        map.put(RuleCodes.RULE_SCENE_MISS_HIGH_VALUE, "高价值场景缺失");
        map.put(RuleCodes.RULE_NEGATIVE_EVIDENCE, "存在负面证据");
        map.put(RuleCodes.RULE_LOW_SENTIMENT_SCORE, "情感得分偏低");
        map.put(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW, "平台覆盖面狭窄");
        map.put(RuleCodes.RULE_PLATFORM_COUNT_LOW, "覆盖平台数量偏少");
        map.put(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT, "单平台依赖风险");
        return map;
    }
}
