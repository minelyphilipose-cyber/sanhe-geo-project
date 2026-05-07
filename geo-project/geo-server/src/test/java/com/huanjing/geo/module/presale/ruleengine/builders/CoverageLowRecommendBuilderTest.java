package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageLowRecommendBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new CoverageLowRecommendBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND);
    }

    @Test
    void build_usesHighValueSceneCoverage() {
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setSceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(SceneCoverageGroup.builder()
                        .total(22)
                        .covered(8)
                        .coverageRate(36.3636)
                        .build())
                .build());

        Map<String, Object> ev = new CoverageLowRecommendBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev).containsEntry("coverage_rate", 36L);
        assertThat(ev).containsEntry("uncovered_rate", 64L);
        assertThat(ev).containsEntry("total_prompts", 22);
        assertThat(ev).containsEntry("covered_prompts", 8);
        assertThat(ev).containsEntry("missed_count", 14);
        assertThat(ev).doesNotContainKey("top_competitor_coverage_rate");
    }

    @Test
    void build_whenHighValueMissing_returnsSafeDefaults() {
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setSceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder().build());

        Map<String, Object> ev = new CoverageLowRecommendBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertSafeDefaults(ev);
    }

    @Test
    void build_whenHighValueTotalZero_returnsSafeDefaults() {
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setSceneCoverage(ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(SceneCoverageGroup.builder()
                        .total(0)
                        .covered(0)
                        .coverageRate(0.0)
                        .build())
                .build());

        Map<String, Object> ev = new CoverageLowRecommendBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertSafeDefaults(ev);
    }

    private void assertSafeDefaults(Map<String, Object> ev) {
        assertThat(ev).containsEntry("coverage_rate", 0);
        assertThat(ev).containsEntry("uncovered_rate", 100);
        assertThat(ev).containsEntry("total_prompts", 0);
        assertThat(ev).containsEntry("covered_prompts", 0);
        assertThat(ev).containsEntry("missed_count", 0);
        assertThat(ev).doesNotContainKey("top_competitor_coverage_rate");
    }
}
