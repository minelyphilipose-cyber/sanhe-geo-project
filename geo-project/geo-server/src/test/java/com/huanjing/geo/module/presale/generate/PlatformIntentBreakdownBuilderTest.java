package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiTestResultMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformIntentBreakdownBuilderTest {

    @Test
    void build_returnsFullMatrixInStableOrderAndUniqueKeys() {
        PresaleAiTestResultMapper mapper = Mockito.mock(PresaleAiTestResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of(
                row("P1", "推荐型", "SUCCESS", 0, 1),
                row("P2", "对比型", "SUCCESS", 0, 1)
        ));
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", "P2", 1, 1), computed(), false);

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
    void build_distinguishesNullVsZeroPlatformPromptCount() {
        PresaleAiTestResultMapper mapper = Mockito.mock(PresaleAiTestResultMapper.class);
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(List.of(
                // 有记录但全 excluded -> platform_prompt_count = 0
                row("P1", "推荐型", "SUCCESS", 1, 1)
                // 对比型无记录 -> platform_prompt_count = null
        ));
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", 0), computed(), false);

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
        PresaleAiTestResultMapper mapper = Mockito.mock(PresaleAiTestResultMapper.class);
        List<PlatformIntentSampleRow> rows = new ArrayList<>();
        // 1/8 = 12.5 -> 13
        rows.add(row("P1", "推荐型", "SUCCESS", 0, 1));
        for (int i = 0; i < 7; i++) {
            rows.add(row("P1", "推荐型", "SUCCESS", 0, 0));
        }
        Mockito.when(mapper.selectIntentSamplesByVersionId(1L)).thenReturn(rows);
        PlatformIntentBreakdownBuilder builder = new PlatformIntentBreakdownBuilder(mapper);

        List<PlatformIntentCell> cells = builder.build(1L, raw("P1", 1), computed(), false);
        PlatformIntentCell recommendation = findCell(cells, "P1", "RECOMMENDATION");

        assertThat(recommendation.getPlatformPromptCount()).isEqualTo(8);
        assertThat(recommendation.getMentionCount()).isEqualTo(1);
        assertThat(recommendation.getMentionRate()).isEqualTo(13);
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
}

