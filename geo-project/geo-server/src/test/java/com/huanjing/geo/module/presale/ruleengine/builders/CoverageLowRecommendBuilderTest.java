package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageLowRecommendBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new CoverageLowRecommendBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND);
    }

    @Test
    void build_picksRecommendHighBreakdown() {
        IntentBreakdown rec = new IntentBreakdown();
        rec.setCategory("推荐型");
        rec.setBusinessValue("高");
        rec.setCoverageRate(70.0);
        rec.setTotalPrompts(10);
        rec.setCoveredPrompts(7);

        IntentBreakdown other = new IntentBreakdown();
        other.setCategory("推荐型");
        other.setBusinessValue("中");
        other.setCoverageRate(30.0);

        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setIntentBreakdown(List.of(other, rec));

        Map<String, Object> ev = new CoverageLowRecommendBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev.get("coverage_rate")).isEqualTo(70L);
        assertThat(ev.get("uncovered_rate")).isEqualTo(30L);
        assertThat(ev.get("total_prompts")).isEqualTo(10);
        assertThat(ev.get("covered_prompts")).isEqualTo(7);
        assertThat(ev).containsKey("top_competitor_coverage_rate");
    }

    @Test
    void build_whenNoMatchingBreakdown_returnsSafeDefaults() {
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setIntentBreakdown(Collections.emptyList());

        Map<String, Object> ev = new CoverageLowRecommendBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev.get("coverage_rate")).isEqualTo(0);
        assertThat(ev.get("uncovered_rate")).isEqualTo(100);
        assertThat(ev.get("total_prompts")).isEqualTo(0);
        assertThat(ev.get("covered_prompts")).isEqualTo(0);
    }
}
