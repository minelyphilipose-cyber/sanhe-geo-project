package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.editable.CompetitorSceneDescription;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.ExecutiveSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.FindingContent;
import com.huanjing.geo.module.presale.dto.snapshot.editable.HeatmapSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.KeyTakeaway;
import com.huanjing.geo.module.presale.dto.snapshot.editable.PhaseDescription;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.generate.narrative.NarrativeConfigService;
import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import com.huanjing.geo.module.presale.ruleengine.util.PlatformStatUtil;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final String DEFAULT_ROI_DISCLAIMER = "以上为基于你当前得分与计划优化项设定的改进目标与情景测算,非保证结果;实际效果取决于执行、AI 平台变化与竞争情况。";

    private final ObjectMapper objectMapper;
    private final PresaleTextFormatter textFormatter;
    private final PresaleL3Defaults l3Defaults;
    private final NarrativeConfigService narrativeConfigService;

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
            List<RenderedNarrativeFinding> narrativeFindings = buildNarrativeFindings(raw, computed);
            List<KeyTakeaway> keyTakeaways = narrativeFindings == null
                    ? buildKeyTakeaways(computed)
                    : buildKeyTakeaways(narrativeFindings);
            List<FindingContent> findingContents = narrativeFindings == null
                    ? buildFindingContents(computed)
                    : buildFindingContents(narrativeFindings);
            editable.setKeyTakeaways(deduplicateKeyTakeaways(keyTakeaways));
            editable.setOptimizationFindingsContent(hideDuplicateFindingContents(findingContents));
            editable.setPhaseDescriptions(buildPhaseDescriptions(computed));
            editable.setCompetitorSceneDescriptions(buildCompetitorScenes(raw));
            editable.setHeatmapSummary(buildHeatmapSummary(computed));
            editable.setRoiDisclaimer(DEFAULT_ROI_DISCLAIMER);
            return objectMapper.writeValueAsString(l3Defaults.normalize(editable, raw, computed));
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

    private List<KeyTakeaway> buildKeyTakeaways(List<RenderedNarrativeFinding> findings) {
        List<KeyTakeaway> out = new ArrayList<>();
        int order = 1;
        for (RenderedNarrativeFinding finding : findings) {
            out.add(KeyTakeaway.builder()
                    .orderNo(order++)
                    .title(finding.title())
                    .description(finding.description())
                    .build());
        }
        return out;
    }

    private List<KeyTakeaway> deduplicateKeyTakeaways(List<KeyTakeaway> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<KeyTakeaway> out = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (KeyTakeaway item : source) {
            if (item == null) {
                continue;
            }
            String signature = displaySignature(item.getTitle(), item.getDescription());
            if (!StringUtils.hasText(signature) || seen.add(signature)) {
                out.add(KeyTakeaway.builder()
                        .orderNo(out.size() + 1)
                        .title(item.getTitle())
                        .description(item.getDescription())
                        .build());
            }
        }
        return out;
    }

    private List<FindingContent> hideDuplicateFindingContents(List<FindingContent> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<FindingContent> out = new ArrayList<>();
        Set<String> seenVisible = new TreeSet<>();
        for (FindingContent item : source) {
            if (item == null) {
                continue;
            }
            boolean hidden = Boolean.TRUE.equals(item.getIsHidden());
            String signature = displaySignature(item.getTitle(), item.getDescription());
            boolean duplicateVisible = !hidden && StringUtils.hasText(signature) && !seenVisible.add(signature);
            out.add(FindingContent.builder()
                    .findingId(item.getFindingId())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .evidenceText(item.getEvidenceText())
                    .sortOrder(item.getSortOrder())
                    .isHidden(hidden || duplicateVisible)
                    .build());
        }
        return out;
    }

    private String displaySignature(String title, String description) {
        return normalizeDisplayText(title) + "\n" + normalizeDisplayText(description);
    }

    private String normalizeDisplayText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private List<RenderedNarrativeFinding> buildNarrativeFindings(RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        NarrativeProfile profile = computed == null ? null : computed.getNarrativeProfile();
        if (profile == null || profile.getFindingTiers() == null || profile.getFindingTiers().isEmpty()) {
            return null;
        }
        try {
            List<NarrativeProfile.FindingTier> tiers = profile.getFindingTiers().stream()
                    .filter(Objects::nonNull)
                    .filter(tier -> !"NEGATIVE_PRESSURE".equals(tier.getDedupeKey())
                            || Boolean.TRUE.equals(profile.getDisplayFlags() == null ? null : profile.getDisplayFlags().getShowNegativeBox()))
                    .limit(5)
                    .toList();
            Map<String, PresaleNarrativeFindingCopy> copyMap = narrativeConfigService.loadFindingCopyMap();
            Map<String, Object> base = buildNarrativeSlots(raw, computed, profile);
            List<RenderedNarrativeFinding> rendered = new ArrayList<>();
            int index = 1;
            for (NarrativeProfile.FindingTier tier : tiers) {
                rendered.add(renderNarrativeFinding(tier, profile, copyMap, base, index++));
            }
            while (rendered.size() < 3) {
                String code = nextFillerCode(profile, rendered);
                NarrativeProfile.FindingTier filler = NarrativeProfile.FindingTier.builder()
                        .source(NarrativeProfile.FindingSource.STRENGTH)
                        .code(code)
                        .dedupeKey(code)
                        .tier(NarrativeProfile.FindingTierLevel.STRENGTH)
                        .priority(90 + rendered.size())
                        .archetype(isHighBand(profile) ? NarrativeProfile.Archetype.LEADER_WITH_HOLES : NarrativeProfile.Archetype.DECISION_GAP)
                        .primaryArchetypeMatch(false)
                        .evidence(Map.of())
                        .build();
                rendered.add(renderNarrativeFinding(filler, profile, copyMap, base, index++));
            }
            rendered = rendered.stream().limit(5).toList();
            validateNarrativeRender(profile, rendered);
            return rendered;
        } catch (RuntimeException e) {
            log.warn("Narrative L3 render guard fallback: {}", e.getMessage());
            return fallbackNarrativeFindings(raw, computed);
        }
    }

    private String nextFillerCode(NarrativeProfile profile, List<RenderedNarrativeFinding> rendered) {
        List<String> candidates = isHighBand(profile)
                ? List.of("COVERAGE_STRENGTH", "RECO_STRENGTH", "DEFEND_GAP")
                : List.of("HV_COVERAGE_LOW", "RECO_ABSENT", "PLATFORM_BLIND");
        Set<String> used = rendered.stream()
                .map(RenderedNarrativeFinding::dedupeKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return candidates.stream()
                .filter(code -> !used.contains(code))
                .findFirst()
                .orElse(candidates.get(Math.min(rendered.size(), candidates.size() - 1)));
    }

    private boolean isHighBand(NarrativeProfile profile) {
        return profile != null && (profile.getBand() == NarrativeProfile.Band.STRONG
                || profile.getBand() == NarrativeProfile.Band.LEADER);
    }

    private RenderedNarrativeFinding renderNarrativeFinding(NarrativeProfile.FindingTier tier,
                                                            NarrativeProfile profile,
                                                            Map<String, PresaleNarrativeFindingCopy> copyMap,
                                                            Map<String, Object> base,
                                                            int order) {
        String code = templateCode(tier);
        String tierName = tier.getTier() == null ? "" : tier.getTier().name();
        PresaleNarrativeFindingCopy copy = resolveCopy(copyMap, code, tierName, profile);
        Map<String, Object> slots = new HashMap<>(base);
        if (tier.getEvidence() != null) {
            slots.putAll(tier.getEvidence());
        }
        NarrativeCopy fallback = fallbackCopy(code, tierName);
        String titleTemplate = copy == null || copy.getTitleTemplate() == null ? fallback.title() : copy.getTitleTemplate();
        String bodyTemplate = copy == null || copy.getBodyTemplate() == null ? fallback.body() : copy.getBodyTemplate();
        String evidenceTemplate = copy == null || copy.getEvidenceTemplate() == null ? fallback.evidence() : copy.getEvidenceTemplate();
        String title = renderNarrativeTemplate(code, titleTemplate, slots);
        String description = renderNarrativeTemplate(code, bodyTemplate, slots);
        String evidence = renderNarrativeTemplate(code, evidenceTemplate, slots);
        return new RenderedNarrativeFinding("NF%03d".formatted(order), code, tier.getDedupeKey(), title, description, evidence, order);
    }

    private PresaleNarrativeFindingCopy resolveCopy(Map<String, PresaleNarrativeFindingCopy> copyMap,
                                                    String code,
                                                    String tier,
                                                    NarrativeProfile profile) {
        if (copyMap == null || copyMap.isEmpty()) {
            return null;
        }
        String band = profile.getBand() == null ? "" : profile.getBand().name();
        String archetype = profile.getArchetypePrimary() == null ? "" : profile.getArchetypePrimary().name();
        PresaleNarrativeFindingCopy exact = copyMap.get(NarrativeConfigService.copyKey(code, tier, band, archetype));
        if (exact != null) return exact;
        PresaleNarrativeFindingCopy bandOnly = copyMap.get(NarrativeConfigService.copyKey(code, tier, band, ""));
        if (bandOnly != null) return bandOnly;
        PresaleNarrativeFindingCopy archetypeOnly = copyMap.get(NarrativeConfigService.copyKey(code, tier, "", archetype));
        if (archetypeOnly != null) return archetypeOnly;
        return copyMap.get(NarrativeConfigService.copyKey(code, tier, "", ""));
    }

    private String templateCode(NarrativeProfile.FindingTier tier) {
        if (tier == null) {
            return "DEFEND_GAP";
        }
        String code = tier.getCode();
        if (code != null && (code.endsWith("_STRONG") || code.endsWith("_SOFT")
                || code.startsWith("RECO_") || code.endsWith("_STRENGTH"))) {
            return code;
        }
        if (tier.getSource() == NarrativeProfile.FindingSource.RULE && tier.getDedupeKey() != null) {
            return tier.getDedupeKey();
        }
        return code == null || code.isBlank() ? tier.getDedupeKey() : code;
    }

    private Map<String, Object> buildNarrativeSlots(RawSnapshotDTO raw,
                                                    ComputedSnapshotDTO computed,
                                                    NarrativeProfile profile) {
        Map<String, Object> slots = new HashMap<>();
        String brandName = raw == null || raw.getClientInfo() == null ? "本品牌" : raw.getClientInfo().getBrandName();
        slots.put("brand_name", brandName == null || brandName.isBlank() ? "本品牌" : brandName);
        NarrativeConfigService.IndustryLexicon lexicon = resolveIndustryLexicon(raw);
        String customerTerm = StringUtils.hasText(lexicon.getCustomerTerm()) ? lexicon.getCustomerTerm() : "客户";
        String conversionTerm = StringUtils.hasText(lexicon.getConversionTerm()) ? lexicon.getConversionTerm() : "转化";
        slots.put("customer_term", customerTerm);
        slots.put("conversion_term", conversionTerm);
        slots.put("industry_short", StringUtils.hasText(lexicon.getIndustryShort()) ? lexicon.getIndustryShort() : "行业");
        slots.put("loss_phrase", buildLossPhrase(customerTerm, conversionTerm));
        slots.put("scene_example", firstMissingScene(computed));
        slots.put("overall_score", computed == null || computed.getScores() == null ? "—" : textFormatter.formatInt(computed.getScores().getOverall()));
        slots.put("coverage_score", computed == null || computed.getScores() == null ? "—" : textFormatter.formatInt(computed.getScores().getCoverage()));
        slots.put("recommendation_rate", extractDiagnostic(profile, "recommendation_rate"));
        slots.put("neutral_share", percentDiagnostic(profile, "neutral_share"));
        slots.put("positive_share", percentDiagnostic(profile, "positive_share"));
        slots.put("competitor_names", firstCompetitorName(raw));
        slots.put("weak_platforms", weakPlatformNames(raw));
        slots.put("high_value_covered", highValueCovered(computed));
        slots.put("high_value_total", highValueTotal(computed));
        return slots;
    }

    private NarrativeConfigService.IndustryLexicon resolveIndustryLexicon(RawSnapshotDTO raw) {
        String industry = raw == null || raw.getClientInfo() == null ? null : raw.getClientInfo().getIndustry();
        try {
            NarrativeConfigService.NarrativeConfigSnapshot snapshot = narrativeConfigService.load(industry);
            if (snapshot != null && snapshot.getLexicon() != null) {
                return snapshot.getLexicon();
            }
        } catch (RuntimeException e) {
            log.warn("Load narrative lexicon for L3 slots failed, using generic terms: {}", e.getMessage());
        }
        return NarrativeConfigService.IndustryLexicon.builder()
                .customerTerm("客户")
                .conversionTerm("转化")
                .industryShort("行业")
                .fallback(true)
                .build();
    }

    private String buildLossPhrase(String customerTerm, String conversionTerm) {
        if ("患者".equals(customerTerm) && "到诊".equals(conversionTerm)) {
            return "一位本可到诊的患者被指给了别人";
        }
        return "一位本可完成" + conversionTerm + "的" + customerTerm + "被指给了别人";
    }

    private Object highValueCovered(ComputedSnapshotDTO computed) {
        SceneCoverageGroupAccess access = highValueCoverage(computed);
        return access.covered() == null ? "—" : access.covered();
    }

    private Object highValueTotal(ComputedSnapshotDTO computed) {
        SceneCoverageGroupAccess access = highValueCoverage(computed);
        return access.total() == null ? "—" : access.total();
    }

    private SceneCoverageGroupAccess highValueCoverage(ComputedSnapshotDTO computed) {
        if (computed == null || computed.getSceneCoverage() == null || computed.getSceneCoverage().getHighValue() == null) {
            return new SceneCoverageGroupAccess(null, null);
        }
        return new SceneCoverageGroupAccess(
                computed.getSceneCoverage().getHighValue().getCovered(),
                computed.getSceneCoverage().getHighValue().getTotal()
        );
    }

    private String weakPlatformNames(RawSnapshotDTO raw) {
        if (raw == null || raw.getPlatformBreakdown() == null || raw.getPlatformBreakdown().isEmpty()) {
            return "待补齐平台";
        }
        List<String> names = PlatformStatUtil.uncoveredNames(raw.getPlatformBreakdown()).stream()
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
        if (!names.isEmpty()) {
            return String.join("、", names);
        }
        return raw.getPlatformBreakdown().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(this::safeMentionRate))
                .map(PlatformBreakdown::getPlatformName)
                .filter(StringUtils::hasText)
                .limit(3)
                .collect(Collectors.joining("、"));
    }

    private double safeMentionRate(PlatformBreakdown platform) {
        return platform == null || platform.getMentionRate() == null ? 0D : platform.getMentionRate();
    }

    private String firstMissingScene(ComputedSnapshotDTO computed) {
        if (computed != null && computed.getSceneCoverage() != null
                && computed.getSceneCoverage().getHighValue() != null
                && computed.getSceneCoverage().getHighValue().getMissingQueries() != null) {
            return computed.getSceneCoverage().getHighValue().getMissingQueries().stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.getPromptContent() == null ? item.getCategory() : item.getPromptContent())
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("核心决策问题");
        }
        return "核心决策问题";
    }

    private String firstCompetitorName(RawSnapshotDTO raw) {
        if (raw == null || raw.getCompetitors() == null || raw.getCompetitors().isEmpty()) {
            return "竞品";
        }
        String name = raw.getCompetitors().get(0).getName();
        return name == null || name.isBlank() ? "竞品" : name;
    }

    private Object extractDiagnostic(NarrativeProfile profile, String key) {
        if (profile == null || profile.getDiagnostics() == null) {
            return "—";
        }
        Object value = profile.getDiagnostics().get(key);
        if (value instanceof Number number) {
            return textFormatter.formatInt(number);
        }
        return value == null ? "—" : value;
    }

    private Object percentDiagnostic(NarrativeProfile profile, String key) {
        if (profile == null || profile.getDiagnostics() == null) {
            return "—";
        }
        Object value = profile.getDiagnostics().get(key);
        if (value instanceof Number number) {
            return textFormatter.formatInt(number.doubleValue() * 100D);
        }
        return value == null ? "—" : value;
    }

    private void validateNarrativeRender(NarrativeProfile profile, List<RenderedNarrativeFinding> findings) {
        if (findings.size() < 3 || findings.size() > 5) {
            throw new IllegalStateException("key finding count out of range: " + findings.size());
        }
        for (int i = 0; i < findings.size(); i++) {
            if (findings.get(i).sortOrder() != i + 1) {
                throw new IllegalStateException("key finding order mismatch");
            }
        }
        if (profile.getFindingTiers() != null && profile.getFindingTiers().size() >= 3) {
            int expected = Math.min(5, profile.getFindingTiers().stream()
                    .filter(tier -> tier != null && (!"NEGATIVE_PRESSURE".equals(tier.getDedupeKey())
                            || Boolean.TRUE.equals(profile.getDisplayFlags() == null ? null : profile.getDisplayFlags().getShowNegativeBox())))
                    .toList().size());
            if (expected >= 3 && expected != findings.size()) {
                throw new IllegalStateException("finding_tiers/key_takeaways count mismatch");
            }
        }
        String finalText = findings.stream()
                .map(item -> item.title() + "\n" + item.description() + "\n" + item.evidenceText())
                .collect(Collectors.joining("\n"));
        if (PLACEHOLDER_PATTERN.matcher(finalText).find() || Pattern.compile("\\{\\{[a-z_]+}}").matcher(finalText).find()) {
            throw new IllegalStateException("placeholder remained in final text");
        }
        if (profile.getBand() == NarrativeProfile.Band.INVISIBLE || profile.getBand() == NarrativeProfile.Band.BEHIND) {
            if (finalText.contains("领先") || finalText.contains("标杆") || finalText.contains("优势明显")) {
                throw new IllegalStateException("forbidden positive words for low band");
            }
        }
        if (!Boolean.TRUE.equals(profile.getDisplayFlags() == null ? null : profile.getDisplayFlags().getShowNegativeBox())
                && finalText.contains("负面")) {
            throw new IllegalStateException("negative copy without true negative flag");
        }
        if (findings.stream().anyMatch(item -> "COMPETITOR_OVERTAKE_STRONG".equals(item.code()))
                && !Boolean.TRUE.equals(profile.getDisplayFlags() == null ? null : profile.getDisplayFlags().getAllowCompetitorOvertakeClaim())) {
            throw new IllegalStateException("strong competitor claim without recommendation evidence flag");
        }
    }

    private List<RenderedNarrativeFinding> fallbackNarrativeFindings(RawSnapshotDTO raw, ComputedSnapshotDTO computed) {
        String brandName = raw == null || raw.getClientInfo() == null || raw.getClientInfo().getBrandName() == null
                ? "本品牌" : raw.getClientInfo().getBrandName();
        String overall = computed == null || computed.getScores() == null || computed.getScores().getOverall() == null
                ? "—" : textFormatter.formatInt(computed.getScores().getOverall());
        return List.of(
                new RenderedNarrativeFinding("NF001", "FALLBACK_OVERVIEW", "FALLBACK_OVERVIEW",
                        "整体可见度需要持续观察",
                        brandName + " 当前综合得分为 " + overall + " 分,建议结合平台和场景表现做持续优化。",
                        "综合得分 " + overall, 1),
                new RenderedNarrativeFinding("NF002", "FALLBACK_COVERAGE", "FALLBACK_COVERAGE",
                        "核心场景覆盖需要稳定建设",
                        "建议优先围绕推荐、咨询、对比等核心问题补齐内容资产,提升 AI 回答中的稳定出现。",
                        "核心场景覆盖待持续观察", 2),
                new RenderedNarrativeFinding("NF003", "FALLBACK_PLATFORM", "FALLBACK_PLATFORM",
                        "多平台表现需要均衡维护",
                        "建议持续跟踪不同 AI 平台上的品牌出现和表达差异,避免单个平台波动影响整体判断。",
                        "平台表现待持续观察", 3)
        );
    }

    private List<FindingContent> buildFindingContents(List<RenderedNarrativeFinding> findings) {
        List<FindingContent> out = new ArrayList<>();
        for (RenderedNarrativeFinding finding : findings) {
            out.add(FindingContent.builder()
                    .findingId(finding.findingId())
                    .title(finding.title())
                    .description(finding.description())
                    .evidenceText(finding.evidenceText())
                    .sortOrder(finding.sortOrder())
                    .isHidden(false)
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
            String ruleCode = finding.getRuleCode();
            RuleFindingTemplate template = ruleCode == null ? null : RULE_FINDING_MAP.get(ruleCode);
            String title;
            String description;
            String evidenceText;
            if (template == null) {
                log.warn("Finding rule code {} not in RULE_FINDING_MAP, fallback to legacy render", ruleCode);
                title = ruleCode == null ? "未命名规则" : RULE_TITLE_MAP.getOrDefault(ruleCode, ruleCode);
                description = renderEvidence(finding.getEvidenceData(), finding.getCategory());
                evidenceText = renderEvidence(finding.getEvidenceData(), finding.getCategory());
            } else {
                title = render(ruleCode, template.title(), finding.getEvidenceData());
                description = render(ruleCode, template.description(), finding.getEvidenceData());
                evidenceText = render(ruleCode, template.evidenceText(), finding.getEvidenceData());
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
        Integer phase1Total = extractPhase1TotalCount(computed);
        if (phase1Total == null) {
            return fallbackPhaseDescriptions();
        }
        int phase2Total = extractPhaseTotalCount(computed, 2);
        int phase3Total = extractPhaseTotalCount(computed, 3);
        List<PhaseDescription> out = new ArrayList<>();
        out.add(PhaseDescription.builder()
                .phaseNo(1)
                .title("基础优化阶段,聚焦" + phase1Total + "项关键改动")
                .description(null)
                .build());
        out.add(PhaseDescription.builder()
                .phaseNo(2)
                .title(phase2Total > 0 ? "内容深化阶段,推进" + phase2Total + "项优化" : "内容深化阶段")
                .description(null)
                .build());
        out.add(PhaseDescription.builder()
                .phaseNo(3)
                .title(phase3Total > 0 ? "持续优化阶段,跟进" + phase3Total + "项优化" : "巩固·监测阶段")
                .description(null)
                .build());
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

    private HeatmapSummary buildHeatmapSummary(ComputedSnapshotDTO computed) {
        NarrativeProfile profile = computed == null ? null : computed.getNarrativeProfile();
        String pattern = profile == null || profile.getHeatmapPattern() == null
                ? "RECO_EMERGING"
                : profile.getHeatmapPattern().name();
        String band = profile == null || profile.getBand() == null ? null : profile.getBand().name();
        PresaleHeatmapSummary row = narrativeConfigService.loadHeatmapSummary(pattern, band);
        return HeatmapSummary.builder()
                .heatmapPattern(row.getHeatmapPattern())
                .summary(row.getSummaryTemplate())
                .colorLegend(row.getColorLegendTemplate())
                .build();
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
                .map(e -> SignalKeyLabelMap.format(e.getKey(), e.getValue()))
                .collect(Collectors.joining("，"));
        return "关键信号: " + text;
    }

    private Integer extractPhase1TotalCount(ComputedSnapshotDTO computed) {
        if (computed == null || computed.getRoiSimulation() == null || computed.getRoiSimulation().getPhases() == null) {
            return null;
        }
        return extractPhaseTotalCount(computed, 1);
    }

    private int extractPhaseTotalCount(ComputedSnapshotDTO computed, int phaseNo) {
        if (computed == null || computed.getRoiSimulation() == null || computed.getRoiSimulation().getPhases() == null) {
            return 0;
        }
        return computed.getRoiSimulation().getPhases().stream()
                .filter(Objects::nonNull)
                .filter(phase -> Integer.valueOf(phaseNo).equals(phase.getPhaseNo()))
                .map(phase -> phase.getTotalOptimizationCount() == null ? 0 : phase.getTotalOptimizationCount())
                .findFirst()
                .orElse(0);
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

    private String renderNarrativeTemplate(String code, String template, Map<String, Object> slots) {
        if (template == null || template.isBlank()) {
            return "—";
        }
        String result = template;
        if (slots != null && !slots.isEmpty()) {
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                String value = formatValue(entry.getValue());
                result = result.replace("{{" + entry.getKey() + "}}", value);
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        result = replaceMissingNarrativePlaceholders(code, result, Pattern.compile("\\{\\{([a-z_]+)}}"));
        return replaceMissingNarrativePlaceholders(code, result, PLACEHOLDER_PATTERN);
    }

    private String replaceMissingNarrativePlaceholders(String code, String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        Set<String> missingKeys = new TreeSet<>();
        while (matcher.find()) {
            missingKeys.add(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("—"));
        }
        matcher.appendTail(buffer);
        if (!missingKeys.isEmpty()) {
            log.warn("Presale narrative template placeholder missing: code={}, missingKeys={}", code, missingKeys);
        }
        return buffer.toString();
    }

    private NarrativeCopy fallbackCopy(String code, String tierName) {
        return switch (code) {
            case "HV_COVERAGE_LOW" -> new NarrativeCopy(
                    "高价值场景覆盖仍有缺口",
                    "{{brand_name}} 在「{{scene_example}}」等关键问题上尚未形成稳定覆盖,这会影响{{customer_term}}进入决策阶段时看到品牌答案的概率。",
                    "高价值问题覆盖:{{high_value_covered}}/{{high_value_total}};缺失样例:{{scene_example}}"
            );
            case "RECO_ABSENT" -> new NarrativeCopy(
                    "推荐入口尚未被稳定打开",
                    "{{brand_name}} 在推荐型问题中的出现率偏低,用户主动询问选择建议时,品牌还没有成为 AI 回答里的稳定选项,{{loss_phrase}}。",
                    "推荐型出现率:{{recommendation_rate}}%"
            );
            case "BRANDED_ONLY" -> new NarrativeCopy(
                    "品牌认知集中在已知人群",
                    "{{brand_name}} 在点名查询中更容易出现,但在泛需求和推荐场景中承接不足,说明当前可见度更依赖已有认知。",
                    "典型缺口:{{scene_example}}"
            );
            case "SENTIMENT_THIN" -> new NarrativeCopy(
                    "AI 讲得出品牌,但讲不出足够优势",
                    "当前中性表达占比偏高,AI 对 {{brand_name}} 的描述更像基础信息罗列,还缺少能推动{{conversion_term}}的正向理由。",
                    "中性表达占比:{{neutral_share}}%"
            );
            case "NEGATIVE_PRESSURE" -> new NarrativeCopy(
                    "真实负面信号需要优先处理",
                    "{{brand_name}} 已出现明确负面表达,需要先处理影响信任的内容源,再用正向证据修复 AI 回答里的判断基础。",
                    "负面信号已通过二次校验"
            );
            case "PLATFORM_BLIND" -> new NarrativeCopy(
                    "部分平台仍是可见度盲区",
                    "{{brand_name}} 在不同 AI 平台上的出现不均衡,弱势平台会让一部分{{customer_term}}看不到品牌答案。",
                    "待强化平台:{{weak_platforms}}"
            );
            case "COMPETITOR_OVERTAKE_STRONG" -> new NarrativeCopy(
                    "推荐场景中竞品正在替代你出现",
                    "在「{{scene_example}}」这类主动推荐场景中,AI 已推荐 {{competitor_names}} 而没有稳定带出 {{brand_name}},这是需要优先抢回的决策入口。",
                    "竞品推荐证据:{{competitor_names}}"
            );
            case "COMPETITOR_OVERTAKE_SOFT" -> new NarrativeCopy(
                    "被点名比较时 AI 更倾向竞品",
                    "当用户把 {{brand_name}} 与 {{competitor_names}} 放在一起比较时,AI 的判断更容易偏向竞品,需要补足可比较的优势证据。",
                    "比较偏好信号:{{competitor_names}}"
            );
            case "COVERAGE_STRENGTH" -> new NarrativeCopy(
                    "已有覆盖基础可以继续放大",
                    "{{brand_name}} 已具备一定 AI 可见度基础,接下来应把已有覆盖从点状答案推进到更多高价值问题。",
                    "当前综合得分:{{overall_score}}"
            );
            case "RECO_STRENGTH" -> new NarrativeCopy(
                    "推荐型入口具备继续建设价值",
                    "推荐型问题已经出现可运营信号,适合继续围绕{{customer_term}}真实决策问题补充内容资产。",
                    "推荐型出现率:{{recommendation_rate}}%"
            );
            case "DEFEND_GAP" -> new NarrativeCopy(
                    "领先表现仍需要防守缺口",
                    "{{brand_name}} 的可见度建设需要持续维护,避免高价值问题、平台覆盖或竞品表达形成新的薄弱点。",
                    "建议持续监测关键场景"
            );
            default -> new NarrativeCopy(
                    "关键发现需要持续跟进",
                    "{{brand_name}} 当前可见度表现存在可优化空间,建议结合场景、平台和竞品信号持续推进。",
                    "建议结合场景、平台和竞品信号持续核查。"
            );
        };
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
        map.put(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND, "高价值场景覆盖待激活");
        map.put(RuleCodes.RULE_BRAND_AWARENESS_LOW, "综合可见度有显著提升空间");
        map.put(RuleCodes.RULE_RECOMMENDATION_ABSENT, "用户求推荐时品牌仍未稳定出现");
        map.put(RuleCodes.RULE_COMPARE_GAP, "对比型查询待加强");
        map.put(RuleCodes.RULE_PLATFORM_IMBALANCE, "平台表现可进一步均衡");
        map.put(RuleCodes.RULE_SCENE_MISS_HIGH_VALUE, "高价值场景待激活");
        map.put(RuleCodes.RULE_COMPETITOR_PRESENT_CLIENT_ABSENT, "竞品在场但品牌缺席");
        map.put(RuleCodes.RULE_NATURAL_RECO_WEAK_BRAND_KNOWN, "被点名认知较强但自然推荐偏弱");
        map.put(RuleCodes.RULE_HIGH_VALUE_RECO_GAP, "推荐型高价值问题仍有缺口");
        map.put(RuleCodes.RULE_NEGATIVE_EVIDENCE, "检出负面表述,需重点应对");
        map.put(RuleCodes.RULE_LOW_SENTIMENT_SCORE, "情感倾向待优化");
        map.put(RuleCodes.RULE_BRAND_SENTIMENT_SAMPLE_THIN, "品牌情感样本不足");
        map.put(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW, "平台覆盖面可拓展");
        map.put(RuleCodes.RULE_PLATFORM_COUNT_LOW, "覆盖平台数量可拓展");
        map.put(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT, "平台来源较为集中");
        map.put(RuleCodes.RULE_PLATFORM_NEW_CUSTOMER_BLANK, "新顾客入口场景存在空白");
        map.put(RuleCodes.RULE_PLATFORM_DEPTH_SHALLOW, "平台出现深度待补齐");
        map.put(RuleCodes.RULE_LONG_TAIL_SCENE_GAP, "长尾场景可持续补齐");
        map.put(RuleCodes.RULE_CONTENT_CONSISTENCY_CHECK, "品牌信息一致性建议检查");
        map.put(RuleCodes.RULE_PERIODIC_RETEST_MONITORING, "周期复测与变化预警");
        return map;
    }

    private static Map<String, RuleFindingTemplate> buildRuleFindingMap() {
        return Map.ofEntries(
                Map.entry(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND, new RuleFindingTemplate(
                        "高价值场景覆盖待激活",
                        "在 {total_prompts} 个高价值问题中,品牌已覆盖 {covered_prompts} 个,覆盖率 {coverage_rate}%。" +
                                "仍有 {missed_count} 个核心决策场景待激活,每个场景都对应明确的销售机会窗口。" +
                                "优先动作:针对高价值缺失场景规划专项内容布局,优先补齐对成交影响最大的查询入口。",
                        "{total_prompts} 个高价值问题中覆盖 {covered_prompts} 个"
                )),
                Map.entry(RuleCodes.RULE_BRAND_AWARENESS_LOW, new RuleFindingTemplate(
                        "综合可见度有显著提升空间",
                        "综合得分 {overall_score} 分,与行业均值 {industry_avg_overall} 分有差距,距离行业 Top1 的 {top1_overall} 分还有可观的提升空间。" +
                                "综合得分覆盖提及率、排名、情感、场景覆盖四个维度,意味着每个维度都有可优化的具体抓手。" +
                                "优先动作:从基础认知建设入手,通过内容铺设、平台优化、负面管理三线并行启动可见度提升。",
                        "综合得分 {overall_score} 分(行业均值 {industry_avg_overall} / Top1 {top1_overall})"
                )),
                Map.entry(RuleCodes.RULE_RECOMMENDATION_ABSENT, new RuleFindingTemplate(
                        "用户求推荐时品牌仍未稳定出现",
                        "在推荐型高价值场景中,品牌缺席 {client_absent_count}/{hv_reco_total} 个,缺席率 {absence_rate}%。" +
                                "这意味着用户主动寻找服务机构时,AI 还没有稳定把品牌列入候选答案。" +
                                "优先动作:围绕高价值推荐问题补齐品牌介绍、服务项目、案例和本地信源。",
                        "推荐型高价值场景缺席 {client_absent_count}/{hv_reco_total}"
                )),
                Map.entry(RuleCodes.RULE_COMPARE_GAP, new RuleFindingTemplate(
                        "对比型查询待加强",
                        "在 {total_prompts} 个对比型查询中,品牌已形成有效对比判断 {covered_prompts} 个,覆盖率 {coverage_rate}%。" +
                                "对比型查询是用户在决策阶段的主要信息入口,加强这部分内容可以直接影响用户在最终选择前的判断。" +
                                "优先动作:补齐\"与竞品 X 相比\"、\"X 类型哪个好\"等典型对比型场景的内容布局,在 AI 主动对比时形成清晰立场。",
                        "对比型查询覆盖率 {coverage_rate}%({covered_prompts}/{total_prompts})"
                )),
                Map.entry(RuleCodes.RULE_PLATFORM_IMBALANCE, new RuleFindingTemplate(
                        "平台表现可进一步均衡",
                        "在 {total_platforms} 个测试平台中,{strong_platform_name} 的提及率为 {strong_mention_rate}%," +
                                "与 {weak_platform_name} 的 {weak_mention_rate}% 相差 {gap_pp} 个百分点。" +
                                "优势平台:{strong_platforms_text};待强化平台:{weak_platforms_text}。" +
                                "当前优势平台已验证内容打法有效——把同样的内容策略复用到弱势平台,可快速放大整体可见度。优先动作:在弱势平台加强内容铺设与 AI 可读性优化。",
                        "最高 {strong_mention_rate}% / 最低 {weak_mention_rate}%(差距 {gap_pp} pp)"
                )),
                Map.entry(RuleCodes.RULE_SCENE_MISS_HIGH_VALUE, new RuleFindingTemplate(
                        "高价值场景待激活",
                        "共有 {missed_count} 个高价值场景未被覆盖,包括:{missed_scenes_text}。" +
                                "高价值场景是用户决策路径上的关键触点,每个场景都对应明确的销售机会窗口。" +
                                "优先动作:针对每个缺失场景规划专项内容建设,持续监测 AI 平台的收录与推荐情况,逐一激活商业价值最高的查询。",
                        "高价值场景缺失 {missed_count} 个"
                )),
                Map.entry(RuleCodes.RULE_COMPETITOR_PRESENT_CLIENT_ABSENT, new RuleFindingTemplate(
                        "竞品在场但品牌缺席",
                        "在 {display_gap_count}/{hv_reco_total} 个推荐型高价值场景中,已有竞品出现但品牌缺席。" +
                                "代表竞品为 {top_competitor_name},累计被点名平台次数 {top_competitor_platform_mentions}。" +
                                "优先动作:以这些场景为清单补齐内容入口,先让品牌进入 AI 的候选答案。",
                        "竞品在场且品牌缺席 {display_gap_count}/{hv_reco_total}"
                )),
                Map.entry(RuleCodes.RULE_NATURAL_RECO_WEAK_BRAND_KNOWN, new RuleFindingTemplate(
                        "被点名时 AI 知道你,但用户没点名时 AI 几乎不主动推荐你",
                        "被点名了解或比较时,AI 对品牌已有一定识别度,相关覆盖最高达到 {known_rate}%。" +
                                "但推荐型高价值场景覆盖率只有 {recommendation_rate}%,低于 {threshold_rate}% 阈值。" +
                                "优先动作:把已有品牌信息转化为推荐型内容,让 AI 在用户未点名时也能主动列入品牌。",
                        "推荐型高价值覆盖率 {recommendation_rate}%,认知/对比最高 {known_rate}%"
                )),
                Map.entry(RuleCodes.RULE_HIGH_VALUE_RECO_GAP, new RuleFindingTemplate(
                        "推荐型高价值问题仍有缺口",
                        "推荐型高价值问题覆盖 {hv_reco_covered}/{hv_reco_total},仍有 {hv_reco_gap} 个缺口。" +
                                "这类问题最接近用户筛选服务机构的时刻,建议按问题逐条建设可被 AI 引用的内容资产。",
                        "推荐型高价值覆盖 {hv_reco_covered}/{hv_reco_total}"
                )),
                Map.entry(RuleCodes.RULE_NEGATIVE_EVIDENCE, new RuleFindingTemplate(
                        "检出负面表述,需重点应对",
                        "本次测试中,品牌出现了 {negative_evidence_count} 条负面评价证据,集中在\"{key_topic}\"相关话题,涉及 {affected_platform_count} 个平台:{affected_platforms_text}。" +
                                "负面话题集中在单一维度,意味着可以通过定向内容布局精准对冲。" +
                                "优先动作:对负面话题溯源并制定专项回应内容,通过正面叙事在 AI 平台上形成对冲。",
                        "负面证据 {negative_evidence_count} 条,涉及 {affected_platform_count} 个平台"
                )),
                Map.entry(RuleCodes.RULE_LOW_SENTIMENT_SCORE, new RuleFindingTemplate(
                        "情感倾向待优化",
                        "情感维度得分 {sentiment_score} 分,本次共采集到正面 {positive_count} 条、中性 {neutral_count} 条、负面 {negative_count} 条情感标记。" +
                                "中性占比较高,反映品牌在 AI 平台上的正面叙事仍有强化空间。" +
                                "优先动作:补齐用户故事、专业背书、产品优势等正向内容,逐步提升情感基线。",
                        "情感得分 {sentiment_score}(正 {positive_count} / 中 {neutral_count} / 负 {negative_count})"
                )),
                Map.entry(RuleCodes.RULE_BRAND_SENTIMENT_SAMPLE_THIN, new RuleFindingTemplate(
                        "AI 还没有形成稳定的品牌情感印象",
                        "本次品牌自身情感样本仅 {brand_sentiment_sample_count} 条,不足以支撑稳定的正负面判断。" +
                                "建议先提升品牌在回答中的出现次数,再通过案例、评价和专业背书建立更明确的正向印象。",
                        "品牌情感样本 {brand_sentiment_sample_count} 条"
                )),
                Map.entry(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW, new RuleFindingTemplate(
                        "平台覆盖面可拓展",
                        "在 {total_platforms} 个测试平台中,品牌已在 {covered_platform_count} 个平台被提及," +
                                "另有 {uncovered_platform_count} 个平台尚未覆盖:{uncovered_platforms_text}。" +
                                "每个未覆盖平台都对应一批增量用户入口,补齐后可显著拓宽品牌在 AI 入口的总曝光面。" +
                                "优先动作:梳理未覆盖平台的内容适配性,针对性补齐平台级内容资产。",
                        "已覆盖 {covered_platform_count}/{total_platforms} 个平台"
                )),
                Map.entry(RuleCodes.RULE_PLATFORM_COUNT_LOW, new RuleFindingTemplate(
                        "覆盖平台数量可拓展",
                        "本次有效测试平台为 {effective_platforms} 个,其中 {degraded_count} 个平台因成功率不足降级处理:{degraded_platforms_text}。" +
                                "有效平台数有限会影响测试数据的代表性,提升后可显著增强报告结论的外推可信度。" +
                                "优先动作:在下次测试前排查平台接入稳定性,或扩展测试平台列表以建立更扎实的数据基础。",
                        "有效平台 {effective_platforms} 个(降级 {degraded_count} 个)"
                )),
                Map.entry(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT, new RuleFindingTemplate(
                        "平台来源较为集中",
                        "本次测试共产生 {total_primary} 次首推,其中 {dominant_count} 次集中在 {dominant_platform_name}," +
                                "占比 {dominant_ratio}%。首推集中说明该平台已建立稳定的品牌认知,但单平台路径依赖也意味着策略弹性受限——" +
                                "一旦该平台算法或收录规则调整,首推规模可能出现波动。" +
                                "优先动作:在其他主流平台同步加强内容建设,把单平台优势复制为多平台基本盘。",
                        "{dominant_platform_name} 首推占比 {dominant_ratio}%({dominant_count}/{total_primary})"
                )),
                Map.entry(RuleCodes.RULE_PLATFORM_NEW_CUSTOMER_BLANK, new RuleFindingTemplate(
                        "新顾客入口场景存在空白",
                        "推荐、问题和具体场景问题代表新顾客首次寻找服务机构的主要入口。" +
                                "当前三类场景平均出现率为 {new_customer_avg_rate}%,品牌还没有稳定进入新客搜索路径。" +
                                "优先动作:围绕新客常问问题建立内容矩阵,优先提升自然进入答案的概率。",
                        "新顾客入口平均出现率 {new_customer_avg_rate}%"
                )),
                Map.entry(RuleCodes.RULE_PLATFORM_DEPTH_SHALLOW, new RuleFindingTemplate(
                        "平台出现深度待补齐",
                        "品牌已在部分推荐型高价值场景出现,但仍主要停留在少数平台。" +
                                "代表场景「{scene_example}」中,品牌仅在 {target_platforms}/{evaluated_platforms} 个平台出现。" +
                                "建议把已验证有效的内容资产同步到更多 AI 平台,让出现从点状覆盖变成更稳定的多平台基本盘。",
                        "浅覆盖场景 {shallow_scene_count}/{hv_reco_total}"
                )),
                Map.entry(RuleCodes.RULE_LONG_TAIL_SCENE_GAP, new RuleFindingTemplate(
                        "长尾场景可持续补齐",
                        "核心高价值入口之外,中低价值问题仍有 {long_tail_gap} 个未覆盖场景。" +
                                "这类问题通常不需要抢在第一阶段处理,但适合在后续运营中持续补齐,拓宽 AI 能回答品牌的场景范围。" +
                                "建议优先补真实服务介绍、常见问题解答和可验证案例,避免使用虚构评价或未经证实的承诺。",
                        "中价值缺口 {mid_gap}/{mid_total},低价值缺口 {low_gap}/{low_total}"
                )),
                Map.entry(RuleCodes.RULE_CONTENT_CONSISTENCY_CHECK, new RuleFindingTemplate(
                        "品牌信息一致性建议检查",
                        "品牌已在 {covered_platform_count}/{total_platforms} 个平台出现,但平台间提及率仍有 {gap_pp} 个百分点差异。" +
                                "这类轻量差异适合通过一致性检查处理:核对不同平台对服务项目、优势证据和本地信息的描述是否一致。" +
                                "建议以真实资质、真实案例和真实服务流程为基础,统一可被 AI 引用的内容材料。",
                        "最高 {strong_mention_rate}% / 最低 {weak_mention_rate}%(差距 {gap_pp} pp)"
                )),
                Map.entry(RuleCodes.RULE_PERIODIC_RETEST_MONITORING, new RuleFindingTemplate(
                        "周期复测与变化预警",
                        "AI 回答、竞品在场和平台收录会持续变化。订阅期可持续执行{service_action},跟踪{monitoring_focus}。" +
                                "这不是当前诊断出的缺陷,而是后续持续运营的交付价值:定期发现变化,及时调整内容与平台动作。",
                        "{service_action}: {monitoring_focus}"
                ))
        );
    }

    private record RenderedNarrativeFinding(String findingId,
                                            String code,
                                            String dedupeKey,
                                            String title,
                                            String description,
                                            String evidenceText,
                                            Integer sortOrder) {
    }

    private record NarrativeCopy(String title, String body, String evidence) {
    }

    private record SceneCoverageGroupAccess(Integer covered, Integer total) {
    }

    private record RuleFindingTemplate(String title, String description, String evidenceText) {
    }
}
