package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.PlatformStatUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 复杂 Builder。
 *
 * evidence_data 字段:
 * dominant_platform_name, dominant_count, total_primary, dominant_ratio
 *
 * 算法:
 * 1. 有效平台中 primaryRecommendationCount 最大者 = dominant
 * 2. total_primary = sum(primaryRecommendationCount)
 * 3. dominant_ratio = dominant_count / total_primary * 100(百分比,四舍五入整数)
 */
@Component
public class SinglePlatformDominantBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        PlatformBreakdown top = PlatformStatUtil.topByPrimaryRecommendation(input.getL1().getPlatformBreakdown());
        int totalPrimary = PlatformStatUtil.sumPrimaryRecommendation(input.getL1().getPlatformBreakdown());

        if (top == null || totalPrimary == 0) {
            ev.put("dominant_platform_name", "");
            ev.put("dominant_count", 0);
            ev.put("total_primary", totalPrimary);
            ev.put("dominant_ratio", 0);
            return ev;
        }

        int dominantCount = top.getPrimaryRecommendationCount() == null
                ? 0 : top.getPrimaryRecommendationCount();
        long ratio = Math.round(100.0 * dominantCount / totalPrimary);

        ev.put("dominant_platform_name", top.getPlatformName());
        ev.put("dominant_count", dominantCount);
        ev.put("total_primary", totalPrimary);
        ev.put("dominant_ratio", ratio);
        return ev;
    }
}
