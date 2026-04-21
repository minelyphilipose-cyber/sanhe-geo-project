package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCoverageNarrowBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new PlatformCoverageNarrowBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW);
    }

    @Test
    void build_countsCoveredAndListsUncovered() {
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("a", "A", 10, false),
                platform("b", "B", 0, false),
                platform("c", "C", 15, false),
                platform("d", "D", 0, false),
                platform("e", "E", 0, true)   // 降级,应被剔除
        );
        TestSummary ts = new TestSummary(); ts.setTotalPlatforms(5);

        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformCoverageNarrowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("total_platforms")).isEqualTo(5);
        assertThat(ev.get("covered_platform_count")).isEqualTo(2);   // A, C
        assertThat(ev.get("uncovered_platform_count")).isEqualTo(2); // B, D(降级 E 不算)
        assertThat(ev.get("uncovered_platforms_text").toString()).contains("B", "D").doesNotContain("E");
    }

    private PlatformBreakdown platform(String code, String name, int mentions, boolean degraded) {
        PlatformBreakdown p = new PlatformBreakdown();
        p.setPlatformCode(code); p.setPlatformName(name);
        p.setMentionCount(mentions); p.setIsDegraded(degraded);
        return p;
    }
}
