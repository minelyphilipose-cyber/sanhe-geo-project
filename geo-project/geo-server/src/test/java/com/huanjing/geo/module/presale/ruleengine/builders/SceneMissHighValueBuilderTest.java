package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SceneMissHighValueBuilderTest {

    @Test
    void supportRuleCode_isCorrect() {
        assertThat(new SceneMissHighValueBuilder().supportRuleCode())
                .isEqualTo(RuleCodes.RULE_SCENE_MISS_HIGH_VALUE);
    }

    @Test
    void build_computesMissedCountAndQuotedText() {
        SceneQueryMissing q1 = new SceneQueryMissing();
        q1.setPromptContent("北京最正宗火锅店");
        SceneQueryMissing q2 = new SceneQueryMissing();
        q2.setPromptContent("北京约会吃火锅推荐");

        SceneCoverageGroup hv = new SceneCoverageGroup();
        hv.setTotal(5);
        hv.setCovered(3);
        hv.setMissingQueries(Arrays.asList(q1, q2));

        ComputedSnapshotDTO.SceneCoverage sc = new ComputedSnapshotDTO.SceneCoverage();
        sc.setHighValue(hv);
        ComputedSnapshotDTO l2 = new ComputedSnapshotDTO();
        l2.setSceneCoverage(sc);

        Map<String, Object> ev = new SceneMissHighValueBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(l2).build());

        assertThat(ev.get("missed_count")).isEqualTo(2);
        // 中文引号包裹并顿号分隔
        assertThat(ev.get("missed_scenes_text").toString())
                .contains("\u201c北京最正宗火锅店\u201d", "\u201c北京约会吃火锅推荐\u201d", "、");
    }

    @Test
    void build_whenNoSceneCoverage_returnsZero() {
        Map<String, Object> ev = new SceneMissHighValueBuilder().build(
                RuleBuildInput.builder().l1(new RawSnapshotDTO()).l2(new ComputedSnapshotDTO()).build());

        assertThat(ev.get("missed_count")).isEqualTo(0);
        assertThat(ev.get("missed_scenes_text")).isEqualTo("");
    }
}
