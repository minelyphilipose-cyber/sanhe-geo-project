package com.huanjing.geo.module.presale.generate.l3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.FindingContent;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
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
    private final PresaleL3InitService service = new PresaleL3InitService(objectMapper);

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
            assertFalse(finding.getDescription().contains("—"), "unexpected fallback value in " + finding.getFindingId());
            assertFalse(finding.getEvidenceText().contains("—"), "unexpected fallback value in " + finding.getFindingId());
        }
    }

    @Test
    void derive_l2ToL3Flow_populatesFindingContentForMergeConsumer() throws Exception {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("total_prompts", 20);
        evidence.put("covered_prompts", 9);
        evidence.put("coverage_rate", 45.0D);
        evidence.put("uncovered_rate", 55.0D);
        evidence.put("top_competitor_coverage_rate", 78.0D);

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
                .build();
        return objectMapper.writeValueAsString(raw);
    }

    private String buildComputedJson(List<OptimizationFinding> findings) throws Exception {
        ComputedSnapshotDTO computed = ComputedSnapshotDTO.builder()
                .scores(Scores.builder().overall(60.0D).build())
                .optimizationFindings(findings)
                .build();
        return objectMapper.writeValueAsString(computed);
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
        evidence.put("top_competitor_coverage_rate", 78.0D);
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
}
