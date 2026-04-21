package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformImbalanceBuilderTest {

    @Test
    void supportRuleCode_returnsCorrectCode() {
        assertThat(new PlatformImbalanceBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_PLATFORM_IMBALANCE);
    }

    @Test
    void build_identifiesStrongAndWeak_andComputesGap() {
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("kimi", "Kimi", 64.0, false),
                platform("doubao", "豆包", 40.0, false),
                platform("qwen", "通义千问", 18.0, false)
        );
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(3);

        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformImbalanceBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("total_platforms")).isEqualTo(3);
        assertThat(ev.get("strong_platform_name")).isEqualTo("Kimi");
        assertThat(ev.get("strong_mention_rate")).isEqualTo(64L);
        assertThat(ev.get("weak_platform_name")).isEqualTo("通义千问");
        assertThat(ev.get("weak_mention_rate")).isEqualTo(18L);
        assertThat(ev.get("gap_pp")).isEqualTo(46L);
        // 文本格式验证:包含排名最前的名字和百分比,用中文顿号分隔
        assertThat(ev.get("strong_platforms_text").toString()).contains("Kimi(64%)");
        assertThat(ev.get("weak_platforms_text").toString()).contains("通义千问(18%)");
    }

    @Test
    void build_excludesDegradedPlatforms() {
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("kimi", "Kimi", 64.0, false),
                platform("doubao", "豆包", 40.0, false),
                platform("failing", "FailingPlatform", 5.0, true)   // 降级,应被剔除
        );
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(3);
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformImbalanceBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        // 降级平台不参与 strong/weak 计算,weak 应为 doubao 40 而不是 failing 5
        assertThat(ev.get("weak_platform_name")).isEqualTo("豆包");
        assertThat(ev.get("weak_mention_rate")).isEqualTo(40L);
    }

    @Test
    void build_singlePlatform_returnsSafeDefaults() {
        List<PlatformBreakdown> platforms = new ArrayList<>();
        platforms.add(platform("only", "仅一个", 50.0, false));
        TestSummary ts = new TestSummary();
        ts.setTotalPlatforms(1);
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);
        l1.setTestSummary(ts);

        Map<String, Object> ev = new PlatformImbalanceBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("gap_pp")).isEqualTo(0);
        assertThat(ev.get("strong_platform_name")).isEqualTo("");
        assertThat(ev.get("weak_platform_name")).isEqualTo("");
    }

    private PlatformBreakdown platform(String code, String name, double rate, boolean degraded) {
        PlatformBreakdown p = new PlatformBreakdown();
        p.setPlatformCode(code);
        p.setPlatformName(name);
        p.setMentionRate(rate);
        p.setIsDegraded(degraded);
        return p;
    }
}
