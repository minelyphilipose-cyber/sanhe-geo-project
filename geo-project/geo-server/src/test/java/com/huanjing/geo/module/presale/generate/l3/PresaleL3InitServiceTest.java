package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.FindingContent;
import com.huanjing.geo.module.presale.dto.snapshot.editable.KeyTakeaway;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SamplePrompt;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.generate.narrative.NarrativeConfigService;
import com.huanjing.geo.module.presale.persist.entity.PresalePage03MarketConfig;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.service.PresalePage03MarketConfigService;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresaleL3InitServiceTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{[a-z_]+}");
    private static final List<String> ALL_RULE_CODES = List.of(
            RuleCodes.RULE_COVERAGE_LOW_RECOMMEND,
            RuleCodes.RULE_BRAND_AWARENESS_LOW,
            RuleCodes.RULE_COMPARE_GAP,
            RuleCodes.RULE_PLATFORM_IMBALANCE,
            RuleCodes.RULE_SCENE_MISS_HIGH_VALUE,
            RuleCodes.RULE_NEGATIVE_EVIDENCE,
            RuleCodes.RULE_LOW_SENTIMENT_SCORE,
            RuleCodes.RULE_PLATFORM_COVERAGE_NARROW,
            RuleCodes.RULE_PLATFORM_COUNT_LOW,
            RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PresalePage03MarketConfigService configService = mockConfigService();
    private final PresaleL3Defaults l3Defaults = new PresaleL3Defaults(objectMapper, configService);
    private final NarrativeConfigService narrativeConfigService = mockNarrativeConfigService();
    private final PresaleL3InitService service = new PresaleL3InitService(objectMapper, new PresaleTextFormatter(), l3Defaults, narrativeConfigService);

    @Test
    void ruleFindingMap_containsAllTenRuleCodes() throws Exception {
        Field field = PresaleL3InitService.class.getDeclaredField("RULE_FINDING_MAP");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) field.get(null);

        assertEquals(10, map.size());
        for (String ruleCode : ALL_RULE_CODES) {
            assertTrue(map.containsKey(ruleCode), "missing rule template: " + ruleCode);
        }
    }

    @Test
    void derive_withEmptyEvidence_rendersWithoutPlaceholder() throws Exception {
        String rawJson = buildRawJson();
        String computedJson = buildComputedJson(buildFindings(Map.of()));

        String editableJson = service.derive(rawJson, computedJson);
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);
        assertNotNull(editable.getOptimizationFindingsContent());
        assertEquals(10, editable.getOptimizationFindingsContent().size());
        for (FindingContent finding : editable.getOptimizationFindingsContent()) {
            assertNotNull(finding.getTitle());
            assertNotNull(finding.getDescription());
            assertNotNull(finding.getEvidenceText());
            assertNoPlaceholder(finding.getTitle());
            assertNoPlaceholder(finding.getDescription());
            assertNoPlaceholder(finding.getEvidenceText());
        }
    }

    @Test
    void derive_withFullEvidence_rendersAllTemplatePlaceholders() throws Exception {
        String rawJson = buildRawJson();
        String computedJson = buildComputedJson(buildFindings(buildFullEvidenceData()));

        String editableJson = service.derive(rawJson, computedJson);
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);
        assertNotNull(editable.getOptimizationFindingsContent());
        assertEquals(10, editable.getOptimizationFindingsContent().size());
        for (FindingContent finding : editable.getOptimizationFindingsContent()) {
            assertNotNull(finding.getTitle());
            assertNotNull(finding.getDescription());
            assertNotNull(finding.getEvidenceText());
            assertNoPlaceholder(finding.getTitle());
            assertNoPlaceholder(finding.getDescription());
            assertNoPlaceholder(finding.getEvidenceText());
            assertFalse(finding.getEvidenceText().contains("negative_evidence_count"));
        }
    }

    @Test
    void derive_l2ToL3Flow_populatesFindingContentForMergeConsumer() throws Exception {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("total_prompts", 20);
        evidence.put("covered_prompts", 9);
        evidence.put("coverage_rate", 45.0D);
        evidence.put("uncovered_rate", 55.0D);
        evidence.put("missed_count", 11);

        String rawJson = buildRawJson();
        String computedJson = buildComputedJson(List.of(
                OptimizationFinding.builder()
                        .findingId("F001")
                        .ruleCode(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND)
                        .priority(OptimizationFinding.Priority.HIGH)
                        .category("基础设施")
                        .evidenceData(evidence)
                        .build()
        ));

        String editableJson = service.derive(rawJson, computedJson);
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertNotNull(editable.getOptimizationFindingsContent());
        assertEquals(1, editable.getOptimizationFindingsContent().size());
        FindingContent mergedFinding = editable.getOptimizationFindingsContent().get(0);
        assertEquals("F001", mergedFinding.getFindingId());
        assertNotNull(mergedFinding.getTitle());
        assertNotNull(mergedFinding.getDescription());
        assertNotNull(mergedFinding.getEvidenceText());
        assertFalse(mergedFinding.getTitle().isBlank());
        assertFalse(mergedFinding.getDescription().isBlank());
        assertFalse(mergedFinding.getEvidenceText().isBlank());
        assertNoPlaceholder(mergedFinding.getTitle());
        assertNoPlaceholder(mergedFinding.getDescription());
        assertNoPlaceholder(mergedFinding.getEvidenceText());
    }

    @Test
    void derive_withNarrativeProfile_keepsFindingTiersAndTakeawaysSameOrderAndCount() throws Exception {
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 1, Map.of("recommendation_rate", 12)),
                tier(NarrativeProfile.FindingSource.DERIVED, "HV_COVERAGE_LOW", "HV_COVERAGE_LOW",
                        NarrativeProfile.FindingTierLevel.T2, 2, Map.of()),
                tier(NarrativeProfile.FindingSource.DERIVED, "SENTIMENT_THIN", "SENTIMENT_THIN",
                        NarrativeProfile.FindingTierLevel.T2, 3, Map.of("neutral_share", 86))
        ), false, NarrativeProfile.Band.MIDDLE);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertEquals(3, editable.getKeyTakeaways().size());
        assertEquals(3, editable.getOptimizationFindingsContent().size());
        assertEquals("NF001", editable.getOptimizationFindingsContent().get(0).getFindingId());
        assertEquals("NF002", editable.getOptimizationFindingsContent().get(1).getFindingId());
        assertEquals("NF003", editable.getOptimizationFindingsContent().get(2).getFindingId());
        assertEquals(editable.getKeyTakeaways().get(0).getTitle(), editable.getOptimizationFindingsContent().get(0).getTitle());
        assertNotNull(editable.getHeatmapSummary());
        assertEquals("RECO_EMERGING", editable.getHeatmapSummary().getHeatmapPattern());
        assertEquals("推荐入口尚未被稳定打开", editable.getKeyTakeaways().get(0).getTitle());
        String text = narrativeText(editable);
        assertTrue(text.contains("患者"), text);
        assertTrue(text.contains("到诊"), text);
        assertTrue(text.contains("高价值问题覆盖:8/22"), text);
        assertNoNarrativePlaceholder(editable);
    }

    @Test
    void derive_withoutTrueNegative_suppressesNegativePressureAndFillsToThree() throws Exception {
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.RULE, "RULE_NEGATIVE_EVIDENCE", "NEGATIVE_PRESSURE",
                        NarrativeProfile.FindingTierLevel.T1, 1, Map.of()),
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 2, Map.of("recommendation_rate", 10))
        ), false, NarrativeProfile.Band.MIDDLE);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertEquals(3, editable.getKeyTakeaways().size());
        String text = narrativeText(editable);
        assertFalse(text.contains("负面"), text);
        assertFalse(text.contains("真实负面信号需要优先处理"), text);
        assertEquals("推荐入口尚未被稳定打开", editable.getKeyTakeaways().get(0).getTitle());
    }

    @Test
    void derive_whenRenderedCopyFailsGuard_usesConservativeFallback() throws Exception {
        PresaleNarrativeFindingCopy badCopy = new PresaleNarrativeFindingCopy();
        badCopy.setCode("HV_COVERAGE_LOW");
        badCopy.setTier("T1");
        badCopy.setTitleTemplate("领先优势明显");
        badCopy.setBodyTemplate("{{brand_name}} 领先");
        badCopy.setEvidenceTemplate("领先");
        when(narrativeConfigService.loadFindingCopyMap()).thenReturn(Map.of(
                NarrativeConfigService.copyKey("HV_COVERAGE_LOW", "T1", "", ""), badCopy
        ));
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "HV_COVERAGE_LOW", "HV_COVERAGE_LOW",
                        NarrativeProfile.FindingTierLevel.T1, 1, Map.of()),
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 2, Map.of("recommendation_rate", 10)),
                tier(NarrativeProfile.FindingSource.DERIVED, "BRANDED_ONLY", "BRANDED_ONLY",
                        NarrativeProfile.FindingTierLevel.T2, 3, Map.of())
        ), false, NarrativeProfile.Band.BEHIND);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertEquals(3, editable.getKeyTakeaways().size());
        assertEquals("整体可见度需要持续观察", editable.getKeyTakeaways().get(0).getTitle());
        assertFalse(narrativeText(editable).contains("领先"));
    }

    @Test
    void derive_lowBandSparseFindings_usesNeutralFillersInsteadOfStrengthFallback() throws Exception {
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "SENTIMENT_THIN", "SENTIMENT_THIN",
                        NarrativeProfile.FindingTierLevel.T2, 1, Map.of("neutral_share", 86))
        ), false, NarrativeProfile.Band.BEHIND);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertEquals(3, editable.getKeyTakeaways().size());
        String text = narrativeText(editable);
        assertFalse(text.contains("领先"), text);
        assertFalse(text.contains("整体可见度需要持续观察"), text);
        assertTrue(text.contains("推荐入口尚未被稳定打开"), text);
    }

    @Test
    void derive_lowBandFillerUsesConfiguredStrengthCopy() throws Exception {
        PresaleNarrativeFindingCopy fillerCopy = findingCopy(
                "HV_COVERAGE_LOW",
                "STRENGTH",
                "运营配置的高价值补位标题",
                "{{brand_name}} 需要先补 {{scene_example}}。",
                "{{high_value_covered}}/{{high_value_total}} 个高价值问题");
        when(narrativeConfigService.loadFindingCopyMap()).thenReturn(Map.of(
                NarrativeConfigService.copyKey("HV_COVERAGE_LOW", "STRENGTH", "", ""), fillerCopy
        ));
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 1, Map.of("recommendation_rate", 10))
        ), false, NarrativeProfile.Band.BEHIND);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        String text = narrativeText(editable);
        assertTrue(text.contains("运营配置的高价值补位标题"), text);
        assertTrue(text.contains("8/22 个高价值问题"), text);
    }

    @Test
    void derive_platformBlindSlot_usesZeroMentionPlatformNames() throws Exception {
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "PLATFORM_BLIND", "PLATFORM_BLIND",
                        NarrativeProfile.FindingTierLevel.T2, 1, Map.of()),
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 2, Map.of("recommendation_rate", 10)),
                tier(NarrativeProfile.FindingSource.DERIVED, "SENTIMENT_THIN", "SENTIMENT_THIN",
                        NarrativeProfile.FindingTierLevel.T2, 3, Map.of("neutral_share", 86))
        ), false, NarrativeProfile.Band.MIDDLE);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertTrue(narrativeText(editable).contains("待强化平台:豆包、元宝"));
        assertFalse(narrativeText(editable).contains("待强化平台:待补齐平台"));
    }

    @Test
    void derive_strongCompetitorClaimWithoutFlag_fallsBack() throws Exception {
        NarrativeProfile profile = profile(List.of(
                tier(NarrativeProfile.FindingSource.DERIVED, "COMPETITOR_OVERTAKE_STRONG", "COMPETITOR_OVERTAKE",
                        NarrativeProfile.FindingTierLevel.T1, 1, Map.of("competitor_names", "竞品A")),
                tier(NarrativeProfile.FindingSource.DERIVED, "RECO_ABSENT", "RECO_ABSENT",
                        NarrativeProfile.FindingTierLevel.T1, 2, Map.of("recommendation_rate", 10)),
                tier(NarrativeProfile.FindingSource.DERIVED, "SENTIMENT_THIN", "SENTIMENT_THIN",
                        NarrativeProfile.FindingTierLevel.T2, 3, Map.of("neutral_share", 86))
        ), false, NarrativeProfile.Band.MIDDLE);

        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of(), profile));
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        assertEquals("整体可见度需要持续观察", editable.getKeyTakeaways().get(0).getTitle());
    }

    @Test
    void derive_coverageLowRecommend_rendersHighValueSceneCoverageCopy() throws Exception {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("total_prompts", 22);
        evidence.put("covered_prompts", 8);
        evidence.put("coverage_rate", 36);
        evidence.put("uncovered_rate", 64);
        evidence.put("missed_count", 14);

        String computedJson = buildComputedJson(List.of(
                OptimizationFinding.builder()
                        .findingId("F001")
                        .ruleCode(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND)
                        .priority(OptimizationFinding.Priority.HIGH)
                        .category("基础设施")
                        .evidenceData(evidence)
                        .build()
        ));

        String editableJson = service.derive(buildRawJson(), computedJson);
        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);

        FindingContent finding = editable.getOptimizationFindingsContent().get(0);
        assertEquals("高价值场景覆盖待激活", finding.getTitle());
        assertTrue(finding.getDescription().contains("22 个高价值问题"));
        assertTrue(finding.getDescription().contains("覆盖 8 个"));
        assertTrue(finding.getDescription().contains("覆盖率 36%"));
        assertTrue(finding.getDescription().contains("14 个核心决策场景"));
        assertNoPlaceholder(finding.getDescription());
    }

    @Test
    void derive_populatesMarketBattlegroundDefaults() throws Exception {
        String editableJson = service.derive(buildRawJson(), buildComputedJson(List.of()));

        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);
        assertNotNull(editable.getMarketBattleground());
        assertEquals("每天，有数千万次消费决策正在 AI 上发生", editable.getMarketBattleground().getPageTitle());
        assertEquals("THE NEW BATTLEGROUND FOR YOUR BRAND", editable.getMarketBattleground().getPageKicker());
        assertEquals("→", editable.getMarketBattleground().getNarrative().getBrandLinePrefix());
        assertEquals(4, editable.getMarketBattleground().getMarketCard().getStats().size());
        assertEquals(3, editable.getMarketBattleground().getMarketCard().getPlatforms().size());
        assertEquals(4, editable.getMarketBattleground().getNationalCard().getRows().size());
        assertEquals(4, editable.getMarketBattleground().getRegionalCard().getRows().size());
    }

    @Test
    void derive_populatesPage03QuestionsFromRawSamplePrompts() throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("无二火锅")
                        .industry("restaurant")
                        .industryRole("连锁品牌")
                        .region("阜阳")
                        .build())
                .testSummary(TestSummary.builder()
                        .totalPlatforms(5)
                        .totalPrompts(25)
                        .build())
                .benchmarksFrozen(BenchmarksFrozen.builder()
                        .industry("restaurant")
                        .industryRole("_ALL_")
                        .industryAvg(ScoreSet.builder().overall(50.0D).build())
                        .build())
                .competitors(List.of())
                .samplePrompts(List.of(
                        SamplePrompt.builder().category("推荐型").promptContent("阜阳火锅店哪家更好吃？").build(),
                        SamplePrompt.builder().category("问题型").promptContent("阜阳吃火锅哪家性价比高？").build(),
                        SamplePrompt.builder().category("场景型").promptContent("阜阳家庭聚餐吃火锅推荐哪家？").build()
                ))
                .build();

        String editableJson = service.derive(objectMapper.writeValueAsString(raw), buildComputedJson(List.of()));

        EditableContentDTO editable = objectMapper.readValue(editableJson, EditableContentDTO.class);
        MarketBattleground market = editable.getMarketBattleground();
        assertTrue(market.getNarrative().getQuestions().stream().allMatch(q -> q.contains("阜阳")));
        assertTrue(market.getNarrative().getQuestions().stream().allMatch(q -> q.contains("火锅")));
        assertTrue(market.getNarrative().getQuestions().stream().noneMatch(q -> q.contains("无二火锅")));
    }

    @Test
    void normalizeJson_preservesExplicitEmptyStringsWhenDerivingOldOrEditedJson() throws Exception {
      EditableContentDTO editable = objectMapper.readValue(
              service.derive(buildRawJson(), buildComputedJson(List.of())),
              EditableContentDTO.class);
      editable.getMarketBattleground().setPageTitle("");
      editable.getMarketBattleground().getMarketCard().getStats().get(2).setLabel("");

        String normalized = l3Defaults.normalizeJson(
                objectMapper.writeValueAsString(editable),
                buildRawJson(),
                buildComputedJson(List.of()));

      MarketBattleground market = objectMapper.readValue(normalized, EditableContentDTO.class).getMarketBattleground();
      assertEquals("", market.getPageTitle());
      assertEquals("", market.getMarketCard().getStats().get(2).getLabel());
    }

    @Test
    void normalizeJson_restoresFixedMarketBattlegroundFields() throws Exception {
      EditableContentDTO editable = objectMapper.readValue(
              service.derive(buildRawJson(), buildComputedJson(List.of())),
              EditableContentDTO.class);
      editable.getMarketBattleground().setPageKicker("The new battleground for your brand");
      editable.getMarketBattleground().setBridgeText("custom bridge");
      editable.getMarketBattleground().getNarrative().setBrandLinePrefix("-");

        String normalized = l3Defaults.normalizeJson(
                objectMapper.writeValueAsString(editable),
                buildRawJson(),
                buildComputedJson(List.of()));

      MarketBattleground market = objectMapper.readValue(normalized, EditableContentDTO.class).getMarketBattleground();
      assertEquals("THE NEW BATTLEGROUND FOR YOUR BRAND", market.getPageKicker());
      assertEquals("↓ 聚焦到您的核心市场", market.getBridgeText());
      assertEquals("→", market.getNarrative().getBrandLinePrefix());
    }

    @Test
    void normalizeJson_recalculatesMarketTrafficFromDoubaoFactors() throws Exception {
      EditableContentDTO editable = objectMapper.readValue(
              service.derive(buildRawJson(), buildComputedJson(List.of())),
              EditableContentDTO.class);
      MarketBattleground market = editable.getMarketBattleground();
      market.getNationalCard().getRows().get(0).setValue("15.0亿次");
      market.getNationalCard().getRows().get(1).setValue("12.5%");
      market.getNationalCard().getRows().get(2).setValue("1.5%");
      market.getNationalCard().getRows().get(3).setValue("9999万次");
      market.getRegionalCard().getRows().get(1).setValue("0.08%");
      market.getRegionalCard().getRows().get(3).setValue("9999次");

      String normalized = l3Defaults.normalizeJson(
              objectMapper.writeValueAsString(editable),
              buildRawJson(),
              buildComputedJson(List.of()));

      MarketBattleground normalizedMarket = objectMapper.readValue(normalized, EditableContentDTO.class)
              .getMarketBattleground();
      assertEquals("281.3", normalizedMarket.getNationalCard().getValue());
      assertEquals("万次", normalizedMarket.getNationalCard().getUnit());
      assertEquals("281.3万次", normalizedMarket.getNationalCard().getRows().get(3).getValue());
      assertEquals("2250", normalizedMarket.getRegionalCard().getValue());
      assertEquals("次", normalizedMarket.getRegionalCard().getUnit());
      assertEquals("281.3万次", normalizedMarket.getRegionalCard().getRows().get(0).getValue());
      assertEquals("2250次", normalizedMarket.getRegionalCard().getRows().get(3).getValue());
    }

    @Test
    void normalizeJson_addsMarketBattlegroundForLegacyEditableJson() throws Exception {
        String legacy = """
                {
                  "report_title": "Acme Report",
                  "report_subtitle": "",
                  "executive_summary": null,
                  "key_takeaways": [],
                  "optimization_findings_content": [],
                  "phase_descriptions": [
                    {"phase_no": 1},
                    {"phase_no": 2},
                    {"phase_no": 3}
                  ],
                  "competitor_scene_descriptions": [],
                  "roi_disclaimer": null
                }
                """;

        EditableContentDTO normalized = objectMapper.readValue(
                l3Defaults.normalizeJson(legacy, buildRawJson(), buildComputedJson(List.of())),
                EditableContentDTO.class);

      assertEquals("", normalized.getReportSubtitle());
      assertNotNull(normalized.getMarketBattleground());
      assertEquals("每天，有数千万次消费决策正在 AI 上发生", normalized.getMarketBattleground().getPageTitle());
    }

    private String buildRawJson() throws Exception {
        RawSnapshotDTO raw = RawSnapshotDTO.builder()
                .clientInfo(ClientInfo.builder()
                        .brandName("Acme")
                        .industry("retail")
                        .industryRole("brand")
                        .region("CN")
                        .build())
                .testSummary(TestSummary.builder()
                        .totalPlatforms(5)
                        .totalPrompts(25)
                        .build())
                .benchmarksFrozen(BenchmarksFrozen.builder()
                        .industry("retail")
                        .industryRole("_ALL_")
                        .industryAvg(ScoreSet.builder().overall(50.0D).build())
                        .build())
                .competitors(List.of())
                .platformBreakdown(List.of(
                        PlatformBreakdown.builder()
                                .platformCode("kimi")
                                .platformName("Kimi")
                                .totalTests(25)
                                .mentionCount(8)
                                .mentionRate(32D)
                                .isDegraded(false)
                                .build(),
                        PlatformBreakdown.builder()
                                .platformCode("doubao")
                                .platformName("豆包")
                                .totalTests(25)
                                .mentionCount(0)
                                .mentionRate(0D)
                                .isDegraded(false)
                                .build(),
                        PlatformBreakdown.builder()
                                .platformCode("yuanbao")
                                .platformName("元宝")
                                .totalTests(25)
                                .mentionCount(0)
                                .mentionRate(0D)
                                .isDegraded(false)
                                .build()
                ))
                .build();
        return objectMapper.writeValueAsString(raw);
    }

    private String buildComputedJson(List<OptimizationFinding> findings) throws Exception {
        return buildComputedJson(findings, null);
    }

    private String buildComputedJson(List<OptimizationFinding> findings, NarrativeProfile profile) throws Exception {
        ComputedSnapshotDTO computed = ComputedSnapshotDTO.builder()
                .scores(Scores.builder().overall(60.0D).build())
                .sceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder()
                        .highValue(SceneCoverageGroup.builder()
                                .total(22)
                                .covered(8)
                                .coverageRate(36.3636)
                                .missingQueries(List.of(SceneQueryMissing.builder()
                                        .category("推荐型")
                                        .promptContent("附近种牙哪家医院靠谱")
                                        .build()))
                                .build())
                        .build())
                .optimizationFindings(findings)
                .narrativeProfile(profile)
                .build();
        return objectMapper.writeValueAsString(computed);
    }

    private NarrativeProfile profile(List<NarrativeProfile.FindingTier> tiers,
                                     boolean showNegativeBox,
                                     NarrativeProfile.Band band) {
        return NarrativeProfile.builder()
                .profileVersion("test")
                .configVersion("v1")
                .band(band)
                .bandTone("neutral")
                .archetypePrimary(NarrativeProfile.Archetype.DECISION_GAP)
                .findingTiers(tiers)
                .displayFlags(NarrativeProfile.DisplayFlags.builder()
                        .showNegativeBox(showNegativeBox)
                        .showAdvantageBox(true)
                        .comparisonMetric(NarrativeProfile.ComparisonMetric.MENTION_RATE)
                        .showRadarBaselineGap(true)
                        .hideEmptyBlocks(true)
                        .allowCompetitorOvertakeClaim(false)
                        .build())
                .diagnostics(Map.of(
                        "recommendation_rate", 12,
                        "neutral_share", 0.86D,
                        "positive_share", 0.14D
                ))
                .fallback(false)
                .build();
    }

    private NarrativeProfile.FindingTier tier(NarrativeProfile.FindingSource source,
                                              String code,
                                              String dedupeKey,
                                              NarrativeProfile.FindingTierLevel level,
                                              int priority,
                                              Map<String, Object> evidence) {
        return NarrativeProfile.FindingTier.builder()
                .source(source)
                .code(code)
                .dedupeKey(dedupeKey)
                .tier(level)
                .priority(priority)
                .archetype(NarrativeProfile.Archetype.DECISION_GAP)
                .primaryArchetypeMatch(priority == 1)
                .evidence(evidence)
                .build();
    }

    private PresaleNarrativeFindingCopy findingCopy(String code,
                                                    String tier,
                                                    String titleTemplate,
                                                    String bodyTemplate,
                                                    String evidenceTemplate) {
        PresaleNarrativeFindingCopy row = new PresaleNarrativeFindingCopy();
        row.setCode(code);
        row.setTier(tier);
        row.setTitleTemplate(titleTemplate);
        row.setBodyTemplate(bodyTemplate);
        row.setEvidenceTemplate(evidenceTemplate);
        return row;
    }

    private List<OptimizationFinding> buildFindings(Map<String, Object> evidenceData) {
        List<OptimizationFinding> findings = new ArrayList<>();
        int index = 1;
        for (String ruleCode : ALL_RULE_CODES) {
            findings.add(OptimizationFinding.builder()
                    .findingId("F%03d".formatted(index++))
                    .ruleCode(ruleCode)
                    .priority(OptimizationFinding.Priority.HIGH)
                    .category("内容建设")
                    .evidenceData(evidenceData)
                    .build());
        }
        return findings;
    }

    private Map<String, Object> buildFullEvidenceData() {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("total_prompts", 20);
        evidence.put("covered_prompts", 9);
        evidence.put("coverage_rate", 45.0D);
        evidence.put("uncovered_rate", 55.0D);
        evidence.put("missed_count", 11);
        evidence.put("overall_score", 42.0D);
        evidence.put("industry_avg_overall", 59.0D);
        evidence.put("top1_overall", 83.0D);
        evidence.put("total_platforms", 5);
        evidence.put("strong_platform_name", "Kimi");
        evidence.put("strong_mention_rate", 80.0D);
        evidence.put("weak_platform_name", "豆包");
        evidence.put("weak_mention_rate", 12.0D);
        evidence.put("gap_pp", 68.0D);
        evidence.put("strong_platforms_text", "Kimi、文心");
        evidence.put("weak_platforms_text", "豆包");
        evidence.put("missed_count", 3);
        evidence.put("missed_scenes_text", "价格对比、渠道推荐、口碑评估");
        evidence.put("negative_count", 4);
        evidence.put("negative_evidence_count", 4);
        evidence.put("key_topic", "价格偏高");
        evidence.put("affected_platform_count", 2);
        evidence.put("affected_platforms_text", "Kimi、豆包");
        evidence.put("sentiment_score", 38.0D);
        evidence.put("positive_count", 3);
        evidence.put("neutral_count", 6);
        evidence.put("covered_platform_count", 3);
        evidence.put("uncovered_platform_count", 2);
        evidence.put("uncovered_platforms_text", "腾讯元宝、豆包");
        evidence.put("effective_platforms", 3);
        evidence.put("degraded_count", 2);
        evidence.put("degraded_platforms_text", "通义、讯飞星火");
        evidence.put("total_primary", 18);
        evidence.put("dominant_count", 13);
        evidence.put("dominant_platform_name", "Kimi");
        evidence.put("dominant_ratio", 72.0D);
        return evidence;
    }

    private void assertNoPlaceholder(String text) {
        assertFalse(PLACEHOLDER_PATTERN.matcher(text).find(), "placeholder remained in text: " + text);
    }

    private void assertNoNarrativePlaceholder(EditableContentDTO editable) {
        for (KeyTakeaway takeaway : editable.getKeyTakeaways()) {
            assertFalse(takeaway.getTitle().contains("{{"), takeaway.getTitle());
            assertFalse(takeaway.getDescription().contains("{{"), takeaway.getDescription());
            assertNoPlaceholder(takeaway.getTitle());
            assertNoPlaceholder(takeaway.getDescription());
        }
        for (FindingContent finding : editable.getOptimizationFindingsContent()) {
            assertFalse(finding.getTitle().contains("{{"), finding.getTitle());
            assertFalse(finding.getDescription().contains("{{"), finding.getDescription());
            assertFalse(finding.getEvidenceText().contains("{{"), finding.getEvidenceText());
            assertNoPlaceholder(finding.getTitle());
            assertNoPlaceholder(finding.getDescription());
            assertNoPlaceholder(finding.getEvidenceText());
        }
    }

    private String narrativeText(EditableContentDTO editable) {
        String takeawayText = editable.getKeyTakeaways().stream()
                .map(item -> item.getTitle() + "\n" + item.getDescription())
                .collect(java.util.stream.Collectors.joining("\n"));
        String findingText = editable.getOptimizationFindingsContent().stream()
                .map(item -> item.getTitle() + "\n" + item.getDescription() + "\n" + item.getEvidenceText())
                .collect(java.util.stream.Collectors.joining("\n"));
        return takeawayText + "\n" + findingText;
    }

    private PresalePage03MarketConfigService mockConfigService() {
        PresalePage03MarketConfigService service = mock(PresalePage03MarketConfigService.class);
        when(service.getConfig()).thenReturn(defaultConfig());
        return service;
    }

    private NarrativeConfigService mockNarrativeConfigService() {
        NarrativeConfigService service = mock(NarrativeConfigService.class);
        when(service.loadFindingCopyMap()).thenReturn(Map.of());
        when(service.load("retail")).thenReturn(NarrativeConfigService.NarrativeConfigSnapshot.builder()
                .configVersion("v1")
                .bandRules(List.of())
                .lexicon(NarrativeConfigService.IndustryLexicon.builder()
                        .customerTerm("患者")
                        .conversionTerm("到诊")
                        .industryShort("口腔")
                        .fallback(false)
                        .build())
                .build());
        when(service.load("restaurant")).thenReturn(NarrativeConfigService.NarrativeConfigSnapshot.builder()
                .configVersion("v1")
                .bandRules(List.of())
                .lexicon(NarrativeConfigService.IndustryLexicon.builder()
                        .customerTerm("顾客")
                        .conversionTerm("到店")
                        .industryShort("餐饮")
                        .fallback(false)
                        .build())
                .build());
        when(service.loadHeatmapSummary("RECO_EMERGING", "MIDDLE")).thenReturn(heatmapSummary("RECO_EMERGING"));
        when(service.loadHeatmapSummary("RECO_EMERGING", "BEHIND")).thenReturn(heatmapSummary("RECO_EMERGING"));
        when(service.loadHeatmapSummary("RECO_EMERGING", null)).thenReturn(heatmapSummary("RECO_EMERGING"));
        return service;
    }

    private PresaleHeatmapSummary heatmapSummary(String pattern) {
        PresaleHeatmapSummary row = new PresaleHeatmapSummary();
        row.setHeatmapPattern(pattern);
        row.setSummaryTemplate("推荐场景开始出现品牌信号,但覆盖广度和强度仍需要继续放大。");
        row.setColorLegendTemplate("颜色越深表示该场景信号越强;浅色表示仍处在建设初期。");
        return row;
    }

    private PresalePage03MarketConfig defaultConfig() {
        PresalePage03MarketConfig out = new PresalePage03MarketConfig();
        out.setMarketLabel("AI 搜索流量总览");
        out.setMarketSource("来源：行业公开数据综合估算");
        out.setAppMonthlyActiveValue("8.3");
        out.setAppMonthlyActiveUnit("亿");
        out.setDailyActiveUsersValue("7.2");
        out.setDailyActiveUsersUnit("亿");
        out.setDailyQuestionTotalValue("12");
        out.setDailyQuestionTotalUnit("亿次");
        out.setDoubaoMonthlyUsageValue("28");
        out.setDoubaoMonthlyUsageUnit("次");
        out.setPlatform1Name("豆包");
        out.setPlatform1Value("5.8亿/月活");
        out.setPlatform2Name("千问");
        out.setPlatform2Value("4.2亿/月活");
        out.setPlatform3Name("DeepSeek");
        out.setPlatform3Value("3.1亿/月活");
        out.setPlatformSuffix("元宝 / Kimi 等");
        out.setPage03DataSource("公开口径综合测算");
        out.setFootnote("注：以上数据基于行业公开数据与主流AI平台问答量综合估算，存在±20%合理浮动区间，仅作量级参考，不构成精确市场断言。");
        out.setQuestionCount(3);
        return out;
    }
}
