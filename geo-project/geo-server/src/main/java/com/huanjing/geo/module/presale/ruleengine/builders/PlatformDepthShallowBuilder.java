package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 品牌已出现但平台深度不足。
 *
 * <p>只读 #8 推荐型高价值逐场景明细,用于 LOW 诊断型补强项。</p>
 */
@Component
public class PlatformDepthShallowBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PLATFORM_DEPTH_SHALLOW;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        SceneCompetitorPressure pressure = RuleEvidenceSupport.pressure(input);
        int shallowCount = 0;
        String example = "";
        int targetPlatforms = 0;
        int evaluatedPlatforms = 0;

        for (SceneCompetitorPressure.Item item : RuleEvidenceSupport.pressureItems(pressure)) {
            int target = safe(item == null ? null : item.getTargetMentionedPlatformCount());
            int total = safe(item == null ? null : item.getPlatformsEvaluated());
            if (target > 0 && total > 0 && target < Math.ceil(total / 2.0)) {
                shallowCount++;
                if (example.isBlank()) {
                    example = item.getQuery() == null ? "" : item.getQuery();
                    targetPlatforms = target;
                    evaluatedPlatforms = total;
                }
            }
        }

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("shallow_scene_count", shallowCount);
        ev.put("hv_reco_total", RuleEvidenceSupport.hvRecoTotal(pressure));
        ev.put("scene_example", example);
        ev.put("target_platforms", targetPlatforms);
        ev.put("evaluated_platforms", evaluatedPlatforms);
        return ev;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
