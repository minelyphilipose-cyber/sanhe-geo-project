package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformIntentBreakdownBuilderTest {

    @Test
    void build_returnsFullMatrixInStableOrderAndUniqueKeys() {
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of(
                row("P1", "推荐型", "SUCCESS", 0, 1),
                row("P2", "对比型", "SUCCESS", 0, 1)
        ));
        Mockito.when(mapper.selectVersionPromptIntentStats(1L)).thenReturn(templateStats());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", "P2", 1, 1), computed(), false).cells();

        assertThat(cells).hasSize(10);
        assertThat(cells.subList(0, 5).stream().map(PlatformIntentCell::getPlatformCode).distinct())
                .containsExactly("P1");
        assertThat(cells.subList(5, 10).stream().map(PlatformIntentCell::getPlatformCode).distinct())
                .containsExactly("P2");
        assertThat(cells.subList(0, 5).stream().map(PlatformIntentCell::getIntentCode))
                .containsExactly(
                        "RECOMMENDATION", "COMPARISON", "INQUIRY", "COGNITIVE", "SCENARIO"
                );
        Set<String> uniqueKeys = cells.stream()
                .map(it -> it.getPlatformCode() + "::" + it.getIntentCode())
                .collect(Collectors.toSet());
        assertThat(uniqueKeys).hasSize(10);
    }

    @Test
    void build_overridesCognitiveAndComparisonMentionRateFromJudgeRows() {
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of(
                row("P1", "认知型", "SUCCESS", 0, 1),
                row("P1", "认知型", "SUCCESS", 0, 0),
                row("P1", "对比型", "SUCCESS", 0, 1),
                row("P1", "对比型", "SUCCESS", 0, 0)
        ));
        Mockito.when(mapper.selectJudgeAggregatesByVersionId(1L)).thenReturn(List.of(
                judge("P1", "COGNITIVE", new BigDecimal("71.43"), null, 7),
                judge("P1", "COMPARISON", new BigDecimal("47.06"), "target", 17)
        ));
        Mockito.when(mapper.selectVersionPromptIntentStats(1L)).thenReturn(templateStats());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", 2), computed(), false).cells();
        PlatformIntentCell cognitive = findCell(cells, "P1", "COGNITIVE");
        PlatformIntentCell comparison = findCell(cells, "P1", "COMPARISON");

        assertThat(cognitive.getMentionRate()).isEqualTo(71);
        assertThat(cognitive.getPlatformPromptCount()).isEqualTo(7);
        assertThat(cognitive.getStance()).isNull();
        assertThat(cognitive.getMentionCount()).isEqualTo(0);

        assertThat(comparison.getMentionRate()).isEqualTo(47);
        assertThat(comparison.getPlatformPromptCount()).isEqualTo(17);
        assertThat(comparison.getStance()).isEqualTo("target");
        assertThat(comparison.getMentionCount()).isEqualTo(0);
    }

    @Test
    void build_distinguishesNullVsZeroPlatformPromptCount() {
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of(
                // 有记录但全 excluded -> platform_prompt_count = 0
                row("P1", "推荐型", "SUCCESS", 1, 1)
                // 对比型无记录 -> platform_prompt_count = null
        ));
        Mockito.when(mapper.selectVersionPromptIntentStats(1L)).thenReturn(templateStats());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", 0), computed(), false).cells();

        PlatformIntentCell recommendation = findCell(cells, "P1", "RECOMMENDATION");
        PlatformIntentCell comparison = findCell(cells, "P1", "COMPARISON");

        assertThat(recommendation.getPlatformPromptCount()).isEqualTo(0);
        assertThat(recommendation.getMentionCount()).isEqualTo(0);
        assertThat(recommendation.getMentionRate()).isEqualTo(0);

        assertThat(comparison.getPlatformPromptCount()).isNull();
        assertThat(comparison.getMentionCount()).isEqualTo(0);
        assertThat(comparison.getMentionRate()).isEqualTo(0);
    }

    @Test
    void build_usesHalfUpRoundingForMentionRate() {
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        List<PlatformIntentSampleRow> rows = new ArrayList<>();
        // 1/8 = 12.5 -> 13
        rows.add(row("P1", "推荐型", "SUCCESS", 0, 1));
        for (int i = 0; i < 7; i++) {
            rows.add(row("P1", "推荐型", "SUCCESS", 0, 0));
        }
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(rows);
        Mockito.when(mapper.selectVersionPromptIntentStats(1L)).thenReturn(templateStats());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", 1), computed(), false).cells();
        PlatformIntentCell recommendation = findCell(cells, "P1", "RECOMMENDATION");

        assertThat(recommendation.getPlatformPromptCount()).isEqualTo(8);
        assertThat(recommendation.getMentionCount()).isEqualTo(1);
        assertThat(recommendation.getMentionRate()).isEqualTo(13);
    }

    @Test
    // D26 架构让步(PR-3.D3 CP5 Block 2)的防御测试:
    // SQL 层返回 has_competitor_var 分组全量后,本方法用于锁定 Java 层过滤不可删除。
    // 删除该测试等于解除唯一屏障,会导致竞品模板混入 intent_breakdown。
    void build_templateCountWithCompetitorVar_doesNotMultiplyByCompetitorCount() {
        // Defensive regression:
        // even when template stats include has_competitor_var=1 rows, Java-side total_prompts must not
        // multiply by competitor count. Keep this regression test to prevent reintroducing
        // "template_count × competitor_count" logic.
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of());
        Mockito.when(mapper.selectVersionPromptIntentStats(1L)).thenReturn(templateStatsWithCompetitorVarRecommendation());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        RawSnapshotDTO raw = raw("P1", 0);
        raw.setCompetitors(List.of(
                Competitor.builder().name("Claude").build(),
                Competitor.builder().name("Gemini").build(),
                Competitor.builder().name("Doubao").build()
        ));

        PlatformIntentBreakdownBuilder.BuildResult result = builder.build(1L, raw, computed(), true);
        assertThat(result.intentTotalPrompts().get("RECOMMENDATION")).isEqualTo(5);

        PlatformIntentCell recommendation = findCell(result.cells(), "P1", "RECOMMENDATION");
        assertThat(recommendation.getTotalPrompts()).isEqualTo(5);
    }

    @Test
    void build_allowsComparisonIntentFromCompetitorVarTemplateOnly() {
        PresaleAiPromptResultMapper mapper = Mockito.mock(PresaleAiPromptResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of());
        Mockito.when(mapper.selectVersionPromptIntentStats(1L))
                .thenReturn(templateStatsComparisonOnlyCompetitorVar());
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        PlatformIntentBreakdownBuilder.BuildResult result = builder.build(1L, raw("P1", 0), computed(), true);
        PlatformIntentCell comparison = findCell(result.cells(), "P1", "COMPARISON");

        assertThat(result.intentTotalPrompts().get("COMPARISON")).isEqualTo(7);
        assertThat(comparison.getTotalPrompts()).isEqualTo(7);
    }

    private RawSnapshotDTO raw(String p1, int mentionCount) {
        return raw(p1, null, mentionCount, 0);
    }

    private RawSnapshotDTO raw(String p1, String p2, int mention1, int mention2) {
        List<PlatformBreakdown> list = new ArrayList<>();
        PlatformBreakdown first = new PlatformBreakdown();
        first.setPlatformCode(p1);
        first.setMentionCount(mention1);
        first.setIsDegraded(false);
        list.add(first);
        if (p2 != null) {
            PlatformBreakdown second = new PlatformBreakdown();
            second.setPlatformCode(p2);
            second.setMentionCount(mention2);
            second.setIsDegraded(false);
            list.add(second);
        }
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setPlatformBreakdown(list);
        return raw;
    }

    private ComputedSnapshotDTO computed() {
        List<IntentBreakdown> breakdown = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            IntentBreakdown item = new IntentBreakdown();
            item.setCategory(code.getLabel());
            item.setTotalPrompts(10);
            breakdown.add(item);
        }
        ComputedSnapshotDTO dto = new ComputedSnapshotDTO();
        dto.setIntentBreakdown(breakdown);
        return dto;
    }

    private PlatformIntentSampleRow row(String platform, String intentLabel, String status, int excluded, int mentioned) {
        PlatformIntentSampleRow row = new PlatformIntentSampleRow();
        row.setPlatformCode(platform);
        row.setIntentLabel(intentLabel);
        row.setCallStatus(status);
        row.setIsExcluded(excluded);
        row.setIsMentioned(mentioned);
        return row;
    }

    private PlatformIntentCell findCell(List<PlatformIntentCell> cells, String platform, String intentCode) {
        return cells.stream()
                .filter(it -> platform.equals(it.getPlatformCode()) && intentCode.equals(it.getIntentCode()))
                .findFirst()
                .orElseThrow();
    }

    private PlatformIntentJudgeAggregateRow judge(String platformCode,
                                                  String category,
                                                  BigDecimal cellScore,
                                                  String stance,
                                                  Integer sampleCount) {
        PlatformIntentJudgeAggregateRow row = new PlatformIntentJudgeAggregateRow();
        row.setPlatformCode(platformCode);
        row.setCategory(category);
        row.setCellScore(cellScore);
        row.setStance(stance);
        row.setSampleCount(sampleCount);
        return row;
    }

    private List<PromptTemplateIntentStatRow> templateStats() {
        List<PromptTemplateIntentStatRow> rows = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            PromptTemplateIntentStatRow row = new PromptTemplateIntentStatRow();
            row.setIntentLabel(code.getLabel());
            row.setHasCompetitorVar(0);
            row.setTemplateCount(10);
            rows.add(row);
        }
        return rows;
    }

    private List<PromptTemplateIntentStatRow> templateStatsWithCompetitorVarRecommendation() {
        List<PromptTemplateIntentStatRow> rows = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            PromptTemplateIntentStatRow row = new PromptTemplateIntentStatRow();
            row.setIntentLabel(code.getLabel());
            row.setHasCompetitorVar(0);
            row.setTemplateCount(code == PresaleIntentCode.RECOMMENDATION ? 5 : 0);
            rows.add(row);
            if (code == PresaleIntentCode.RECOMMENDATION) {
                PromptTemplateIntentStatRow competitorVarRow = new PromptTemplateIntentStatRow();
                competitorVarRow.setIntentLabel(code.getLabel());
                competitorVarRow.setHasCompetitorVar(1);
                competitorVarRow.setTemplateCount(50);
                rows.add(competitorVarRow);
            }
        }
        return rows;
    }

    private List<PromptTemplateIntentStatRow> templateStatsComparisonOnlyCompetitorVar() {
        List<PromptTemplateIntentStatRow> rows = new ArrayList<>();
        for (PresaleIntentCode code : PresaleIntentCode.allInOrder()) {
            if (code == PresaleIntentCode.COMPARISON) {
                PromptTemplateIntentStatRow comparisonRow = new PromptTemplateIntentStatRow();
                comparisonRow.setIntentLabel(code.getLabel());
                comparisonRow.setHasCompetitorVar(1);
                comparisonRow.setTemplateCount(7);
                rows.add(comparisonRow);
                continue;
            }
            PromptTemplateIntentStatRow row = new PromptTemplateIntentStatRow();
            row.setIntentLabel(code.getLabel());
            row.setHasCompetitorVar(0);
            row.setTemplateCount(5);
            rows.add(row);
        }
        return rows;
    }
}
