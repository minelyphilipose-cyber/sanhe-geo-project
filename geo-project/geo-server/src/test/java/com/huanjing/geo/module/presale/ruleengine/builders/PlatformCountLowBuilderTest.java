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

class PlatformCountLowBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new PlatformCountLowBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_PLATFORM_COUNT_LOW);
    }

    @Test
    void build_usesTestSummaryAsAuthoritativeSource() {
        // testSummary 是权威源(Codex P1·E r1 修复 P2-2)
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(4);
        ts.setDegradedPlatforms(Arrays.asList("c", "d"));

        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("a", "A", false),
                platform("b", "B", false),
                platform("c", "C", true),
                platform("d", "D", true)
        );
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformCountLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("effective_platforms")).isEqualTo(2);
        assertThat(ev.get("degraded_count")).isEqualTo(2);
        // 通过 breakdown 翻译 code → name
        assertThat(ev.get("degraded_platforms_text").toString()).contains("C", "D");
    }

    @Test
    void build_whenTestSummaryAndBreakdownDisagree_testSummaryWins() {
        // 场景:breakdown 被上游过滤掉了 "c" 条目,但 testSummary 仍包含 "c" 作为 degraded
        // 规则触发用的是 testSummary,因此 evidence 也应以 testSummary 为准,
        // 避免规则命中而 evidence 显示 degraded_count 偏少的撒谎情况
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(4);
        ts.setDegradedPlatforms(Arrays.asList("c", "d"));

        // breakdown 只包含 "d"(被过滤),没有 "c"
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("a", "A", false),
                platform("b", "B", false),
                platform("d", "D", true)
                // "c" 缺失
        );
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformCountLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        // 仍然以 testSummary 为准:degraded_count = 2,effective = 2
        assertThat(ev.get("effective_platforms")).isEqualTo(2);
        assertThat(ev.get("degraded_count")).isEqualTo(2);
        // "c" 在 breakdown 中无 name 映射,用 code 本身兜底展示
        assertThat(ev.get("degraded_platforms_text").toString()).contains("c", "D");
    }

    @Test
    void build_whenTestSummaryDegradedPlatformsNull_fallsBackToBreakdown() {
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(4);
        // testSummary.degradedPlatforms 为 null,应回退到 breakdown.isDegraded

        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("a", "A", false),
                platform("b", "B", false),
                platform("c", "C", true),
                platform("d", "D", true)
        );
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformCountLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("effective_platforms")).isEqualTo(2);
        assertThat(ev.get("degraded_count")).isEqualTo(2);
        assertThat(ev.get("degraded_platforms_text").toString()).contains("C", "D");
    }

    @Test
    void build_whenNoDegraded_emptyText() {
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(3);
        ts.setDegradedPlatforms(java.util.Collections.emptyList());

        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(Arrays.asList(
                platform("a", "A", false), platform("b", "B", false), platform("c", "C", false)));
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformCountLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("effective_platforms")).isEqualTo(3);
        assertThat(ev.get("degraded_count")).isEqualTo(0);
        assertThat(ev.get("degraded_platforms_text")).isEqualTo("");
    }

    private PlatformBreakdown platform(String code, String name, boolean degraded) {
        PlatformBreakdown p = new PlatformBreakdown();
        p.setPlatformCode(code); p.setPlatformName(name);
        p.setIsDegraded(degraded);
        return p;
    }
}
