package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SinglePlatformDominantBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new SinglePlatformDominantBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT);
    }

    @Test
    void build_computesDominantAndRatio() {
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("kimi", "Kimi", 70, false),
                platform("doubao", "豆包", 20, false),
                platform("qwen", "通义千问", 10, false)
        );
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);

        Map<String, Object> ev = new SinglePlatformDominantBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("dominant_platform_name")).isEqualTo("Kimi");
        assertThat(ev.get("dominant_count")).isEqualTo(70);
        assertThat(ev.get("total_primary")).isEqualTo(100);
        assertThat(ev.get("dominant_ratio")).isEqualTo(70L);
    }

    @Test
    void build_whenTotalPrimaryZero_returnsZeroRatio() {
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(Collections.singletonList(
                platform("a", "A", 0, false)));
        Map<String, Object> ev = new SinglePlatformDominantBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("dominant_ratio")).isEqualTo(0);
        assertThat(ev.get("total_primary")).isEqualTo(0);
    }

    @Test
    void build_excludesDegradedFromCalculation() {
        List<PlatformBreakdown> platforms = Arrays.asList(
                platform("a", "A", 30, false),
                platform("b", "B", 20, false),
                platform("c", "C", 999, true)   // 降级平台,剔除后不应入选
        );
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setPlatformBreakdown(platforms);

        Map<String, Object> ev = new SinglePlatformDominantBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("dominant_platform_name")).isEqualTo("A");
        assertThat(ev.get("total_primary")).isEqualTo(50);   // 30 + 20,不含降级的 999
    }

    private PlatformBreakdown platform(String code, String name, int primary, boolean degraded) {
        PlatformBreakdown p = new PlatformBreakdown();
        p.setPlatformCode(code); p.setPlatformName(name);
        p.setPrimaryRecommendationCount(primary);
        p.setIsDegraded(degraded);
        return p;
    }
}
