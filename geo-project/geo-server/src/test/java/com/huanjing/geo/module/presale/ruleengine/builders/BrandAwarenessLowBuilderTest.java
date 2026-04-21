package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrandAwarenessLowBuilderTest {

    @Test
    void supportRuleCode_returnsCorrectCode() {
        assertThat(new BrandAwarenessLowBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_BRAND_AWARENESS_LOW);
    }

    @Test
    void build_fillsAllRequiredKeys() {
        Scores scores = new Scores();
        scores.setOverall(42.0);

        ScoreSet industryAvg = new ScoreSet();
        industryAvg.setOverall(60.0);
        ScoreSet top1 = new ScoreSet();
        top1.setOverall(85.0);

        BenchmarksFrozen bench = new BenchmarksFrozen();
        bench.setIndustryAvg(industryAvg);
        bench.setTop1(top1);

        RawSnapshotDTO l1 = new RawSnapshotDTO();
        l1.setBenchmarksFrozen(bench);
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setScores(scores);

        Map<String, Object> ev = new BrandAwarenessLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(l2).benchmarks(bench).rule(null).build());

        assertThat(ev).containsKeys("overall_score", "industry_avg_overall", "top1_overall");
        assertThat(ev.get("overall_score")).isEqualTo(42L);
        assertThat(ev.get("industry_avg_overall")).isEqualTo(60L);
        assertThat(ev.get("top1_overall")).isEqualTo(85L);
    }

    @Test
    void build_whenScoresNull_returnsZeroFallback() {
        RawSnapshotDTO l1 = new RawSnapshotDTO();
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();  // scores=null

        Map<String, Object> ev = new BrandAwarenessLowBuilder().build(
                RuleBuildInput.builder().l1(l1).l2(l2).benchmarks(null).rule(null).build());

        assertThat(ev.get("overall_score")).isEqualTo(0L);
        assertThat(ev.get("industry_avg_overall")).isEqualTo(0L);
        assertThat(ev.get("top1_overall")).isEqualTo(0L);
    }
}
