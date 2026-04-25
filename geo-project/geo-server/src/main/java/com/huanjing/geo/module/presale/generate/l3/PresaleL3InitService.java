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
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PresaleL3InitService {

    private static final Map<String, String> RULE_TITLE_MAP = buildRuleTitleMap();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z_]+)}");
    /**
     * 优化发现对外文案模板映射。
     *
     * 文案初稿由 Claude 在 PR-3.D3 CP3 提供,基于 rule_code 的触发语义撰写。
     * 阈值(如覆盖率 80%、overall 50 分)与业务口径为 MVP 拍板,待 P1 产品侧 review:
     *   - D25 评分公式权重 review
     *   - D30 multiplier review
     *   - 规则触发阈值 review
     *   - 对客文案风格统一 review
     *
     * 占位符格式 {key},插值实现见 render(template, evidenceData)。
     * 缺失占位符会被替换为 "—",并输出 WARN 日志(便于排查规则与文案不一致)。
     */
    static final Map<String, RuleFindingTemplate> RULE_FINDING_MAP = buildRuleFindingMap();
    private static final String DEFAULT_ROI_DISCLAIMER = "基于行业平均模型的估算,实际效果受多种因素影响,建议结合业务实际情况评估。";

    private final ObjectMapper objectMapper;
    private final PresaleTextFormatter textFormatter;

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
        int roundedOverall = textFormatter.roundToInt(overall);
        int delta = textFormatter.roundToInt(overall - industryAvgOverall);
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
                    : textFormatter.formatInt(topIntent.getCoverageRate());
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
            RuleFindingTemplate template = RULE_FINDING_MAP.get(finding.getRuleCode());
            String title;
            String description;
            String evidenceText;
            if (template == null) {
                log.warn("Finding rule code {} not in RULE_FINDING_MAP, fallback to legacy render", finding.getRuleCode());
                title = RULE_TITLE_MAP.getOrDefault(finding.getRuleCode(),
                        finding.getRuleCode() == null ? "未命名规则" : finding.getRuleCode());
                description = renderEvidence(finding.getEvidenceData(), finding.getCategory());
                evidenceText = renderEvidence(finding.getEvidenceData(), finding.getCategory());
            } else {
                title = render(finding.getRuleCode(), template.title(), finding.getEvidenceData());
                description = render(finding.getRuleCode(), template.description(), finding.getEvidenceData());
                evidenceText = render(finding.getRuleCode(), template.evidenceText(), finding.getEvidenceData());
            }
            out.add(FindingContent.builder()
                    .findingId(finding.getFindingId())
                    .title(title)
                    .description(description)
                    .evidenceText(evidenceText)
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

    private String render(String ruleCode, String template, Map<String, Object> evidenceData) {
        String result = template;
        if (evidenceData != null && !evidenceData.isEmpty()) {
            for (Map.Entry<String, Object> entry : evidenceData.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                result = result.replace(placeholder, formatValue(entry.getValue()));
            }
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer buffer = new StringBuffer();
        Set<String> missingKeys = new TreeSet<>();
        while (matcher.find()) {
            missingKeys.add(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("—"));
        }
        matcher.appendTail(buffer);
        if (!missingKeys.isEmpty()) {
            log.warn("Presale finding template placeholder missing: ruleCode={}, missingKeys={}", ruleCode, missingKeys);
        }
        return buffer.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Number number) {
            return textFormatter.formatInt(number);
        }
        return value.toString();
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

    private static Map<String, RuleFindingTemplate> buildRuleFindingMap() {
        return Map.of(
                RuleCodes.RULE_COVERAGE_LOW_RECOMMEND, new RuleFindingTemplate(
                        "高价值推荐场景覆盖不足",
                        "在 {total_prompts} 个推荐型高价值查询中,仅覆盖 {covered_prompts} 个,覆盖率 {coverage_rate}%," +
                                "意味着 {uncovered_rate}% 的潜在推荐场景未被有效触达。对标 Top 竞品在同类场景的覆盖率为 {top_competitor_coverage_rate}%," +
                                "差距来自内容、渠道或品牌认知。建议优先补齐推荐型内容,提升高价值场景下的 AI 可见度。",
                        "推荐型高价值场景覆盖率 {coverage_rate}%(竞品 Top 覆盖率 {top_competitor_coverage_rate}%)"
                ),
                RuleCodes.RULE_BRAND_AWARENESS_LOW, new RuleFindingTemplate(
                        "品牌综合可见度偏低",
                        "综合得分 {overall_score} 分,低于行业均值 {industry_avg_overall} 分,与行业 Top1 的 {top1_overall} 分存在较大差距。" +
                                "综合得分覆盖提及率、排名、情感、场景覆盖四个维度,偏低表明品牌在 AI 平台的整体认知深度不足。" +
                                "建议从基础认知建设入手,通过内容铺设、平台优化、负面管理多管齐下提升整体可见度。",
                        "综合得分 {overall_score} 分(行业均值 {industry_avg_overall} / Top1 {top1_overall})"
                ),
                RuleCodes.RULE_COMPARE_GAP, new RuleFindingTemplate(
                        "对比型查询覆盖不足",
                        "在 {total_prompts} 个对比型查询中,品牌仅被提及 {covered_prompts} 次,覆盖率 {coverage_rate}%。" +
                                "对比型查询反映用户在决策阶段的信息需求,覆盖不足意味着在用户主动对比时品牌难以进入候选集。" +
                                "建议补齐\"与竞品 X 相比\"、\"X 类型哪个好\"等典型对比型场景的内容布局。",
                        "对比型查询覆盖率 {coverage_rate}%({covered_prompts}/{total_prompts})"
                ),
                RuleCodes.RULE_PLATFORM_IMBALANCE, new RuleFindingTemplate(
                        "平台间提及率差距过大",
                        "在 {total_platforms} 个测试平台中,{strong_platform_name} 的提及率为 {strong_mention_rate}%," +
                                "显著高于 {weak_platform_name} 的 {weak_mention_rate}%,差距达 {gap_pp} 个百分点。" +
                                "优势平台:{strong_platforms_text};弱势平台:{weak_platforms_text}。" +
                                "差距过大说明品牌在部分平台的内容投入与认知建设不均衡。建议在弱势平台加强内容铺设与 AI 可读性优化。",
                        "最高 {strong_mention_rate}% / 最低 {weak_mention_rate}%(差距 {gap_pp} pp)"
                ),
                RuleCodes.RULE_SCENE_MISS_HIGH_VALUE, new RuleFindingTemplate(
                        "高价值场景存在缺失",
                        "共有 {missed_count} 个高价值场景未被覆盖,包括:{missed_scenes_text}。" +
                                "高价值场景是用户决策路径上的关键触点,缺失意味着在最具商业价值的查询上,品牌对 AI 的可见度为零。" +
                                "建议针对每个缺失场景,规划专项内容建设并持续监测 AI 平台的收录与推荐情况。",
                        "高价值场景缺失 {missed_count} 个"
                ),
                RuleCodes.RULE_NEGATIVE_EVIDENCE, new RuleFindingTemplate(
                        "存在负面评价证据",
                        "在本次测试中,品牌出现了 {negative_count} 条负面提及,主要集中在\"{key_topic}\"相关话题,涉及 {affected_platform_count} 个平台:{affected_platforms_text}。" +
                                "负面评价会直接影响 AI 在推荐场景下的品牌倾向,削弱潜在客户的初印象。" +
                                "建议对负面话题溯源并制定专项回应内容,通过正面叙事在 AI 平台上形成对冲。",
                        "负面提及 {negative_count} 条,涉及 {affected_platform_count} 个平台"
                ),
                RuleCodes.RULE_LOW_SENTIMENT_SCORE, new RuleFindingTemplate(
                        "情感分偏低",
                        "情感维度得分 {sentiment_score} 分,低于健康水位。本次共采集到正面 {positive_count} 条、中性 {neutral_count} 条、负面 {negative_count} 条情感标记," +
                                "中性与负面占比偏高,反映品牌在 AI 平台上缺乏明显的情感倾向性或正面叙事不够突出。" +
                                "建议补齐用户故事、专业背书、产品优势等正向内容,提升情感得分基线。",
                        "情感得分 {sentiment_score}(正 {positive_count} / 中 {neutral_count} / 负 {negative_count})"
                ),
                RuleCodes.RULE_PLATFORM_COVERAGE_NARROW, new RuleFindingTemplate(
                        "平台覆盖面偏窄",
                        "在 {total_platforms} 个测试平台中,品牌仅在 {covered_platform_count} 个平台被提及," +
                                "{uncovered_platform_count} 个平台完全未覆盖:{uncovered_platforms_text}。" +
                                "平台覆盖窄意味着用户在不同 AI 入口搜索同一品类时,品牌的曝光机会分布极不均衡。" +
                                "建议梳理未覆盖平台的内容适配性,针对性补齐平台级内容资产。",
                        "已覆盖 {covered_platform_count}/{total_platforms} 个平台"
                ),
                RuleCodes.RULE_PLATFORM_COUNT_LOW, new RuleFindingTemplate(
                        "有效测试平台数偏少",
                        "本次有效测试平台为 {effective_platforms} 个,其中 {degraded_count} 个平台因成功率不足降级处理:{degraded_platforms_text}。" +
                                "有效平台数偏少会降低测试数据的代表性,本报告结论在其他平台上的外推能力需谨慎评估。" +
                                "建议在下次测试前排查平台接入稳定性,或扩展测试平台列表以提升数据基础。",
                        "有效平台 {effective_platforms} 个(降级 {degraded_count} 个)"
                ),
                RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT, new RuleFindingTemplate(
                        "首推过度集中于单一平台",
                        "本次测试共产生 {total_primary} 次首推,其中 {dominant_count} 次集中在 {dominant_platform_name}," +
                                "占比 {dominant_ratio}%。首推过度集中意味着品牌对单一平台的路径依赖过强," +
                                "一旦该平台算法或收录规则调整,首推规模可能出现显著波动。" +
                                "建议在其他主流平台加强内容建设,降低对单一平台的 AI 流量依赖。",
                        "{dominant_platform_name} 首推占比 {dominant_ratio}%({dominant_count}/{total_primary})"
                )
        );
    }

    private record RuleFindingTemplate(String title, String description, String evidenceText) {
    }
}
