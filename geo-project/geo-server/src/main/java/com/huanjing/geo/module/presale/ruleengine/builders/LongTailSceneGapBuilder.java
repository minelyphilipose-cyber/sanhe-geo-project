package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中低价值长尾场景的持续补强项。
 */
@Component
public class LongTailSceneGapBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_LONG_TAIL_SCENE_GAP;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        ComputedSnapshotDTO.SceneCoverage coverage = input.getL2() == null ? null : input.getL2().getSceneCoverage();
        SceneCoverageGroup mid = coverage == null ? null : coverage.getMidValue();
        SceneCoverageGroup low = coverage == null ? null : coverage.getLowValue();

        int midTotal = total(mid);
        int midCovered = covered(mid);
        int lowTotal = total(low);
        int lowCovered = covered(low);
        int midGap = Math.max(0, midTotal - midCovered);
        int lowGap = Math.max(0, lowTotal - lowCovered);

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("mid_gap", midGap);
        ev.put("mid_total", midTotal);
        ev.put("mid_covered", midCovered);
        ev.put("low_gap", lowGap);
        ev.put("low_total", lowTotal);
        ev.put("low_covered", lowCovered);
        ev.put("long_tail_gap", midGap + lowGap);
        return ev;
    }

    private int total(SceneCoverageGroup group) {
        return group == null || group.getTotal() == null ? 0 : group.getTotal();
    }

    private int covered(SceneCoverageGroup group) {
        return group == null || group.getCovered() == null ? 0 : group.getCovered();
    }
}
