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

class CompareGapBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new CompareGapBuilder().supportRuleCode()).isEqualTo(RuleCodes.RULE_COMPARE_GAP);
    }

    @Test
    void build_picksCompareBreakdown() {
        IntentBreakdown cmp = new IntentBreakdown();
        cmp.setCategory("对比型");
        cmp.setCoverageRate(40.0);
        cmp.setTotalPrompts(5);
        cmp.setCoveredPrompts(2);

        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setIntentBreakdown(List.of(cmp));

        Map<String, Object> ev = new CompareGapBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev.get("coverage_rate")).isEqualTo(40L);
        assertThat(ev.get("total_prompts")).isEqualTo(5);
        assertThat(ev.get("covered_prompts")).isEqualTo(2);
    }

    @Test
    void build_whenNoCompareBreakdown_returnsSafeDefaults() {
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setIntentBreakdown(Collections.emptyList());
        Map<String, Object> ev = new CompareGapBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev.get("coverage_rate")).isEqualTo(0);
        assertThat(ev.get("total_prompts")).isEqualTo(0);
        assertThat(ev.get("covered_prompts")).isEqualTo(0);
    }
}
