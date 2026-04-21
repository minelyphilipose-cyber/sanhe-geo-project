package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneQueryMissing;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.TextFormatUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复杂 Builder。
 *
 * evidence_data 字段:missed_count, missed_scenes_text
 *
 * 数据来源:L2.sceneCoverage.highValue.missingQueries[].promptContent
 */
@Component
public class SceneMissHighValueBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_SCENE_MISS_HIGH_VALUE;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        SceneCoverageGroup highValue = input.getL2().getSceneCoverage() == null ? null
                : input.getL2().getSceneCoverage().getHighValue();

        int total = highValue == null || highValue.getTotal() == null ? 0 : highValue.getTotal();
        int covered = highValue == null || highValue.getCovered() == null ? 0 : highValue.getCovered();
        int missedCount = Math.max(0, total - covered);

        ev.put("missed_count", missedCount);

        List<String> missedScenes = new ArrayList<>();
        if (highValue != null && highValue.getMissingQueries() != null) {
            for (SceneQueryMissing q : highValue.getMissingQueries()) {
                if (q != null && q.getPromptContent() != null) {
                    missedScenes.add(q.getPromptContent());
                }
            }
        }
        ev.put("missed_scenes_text", TextFormatUtil.formatQuotedScenes(missedScenes));
        return ev;
    }
}
