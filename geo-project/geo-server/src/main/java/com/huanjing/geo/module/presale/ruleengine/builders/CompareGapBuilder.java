package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * evidence_data 字段:coverage_rate, total_prompts, covered_prompts
 *
 * 数据来源:L2.intentBreakdown 中 category='对比型' 的条目(不区分 businessValue)。
 */
@Component
public class CompareGapBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_COMPARE_GAP;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        IntentBreakdown cmp = findFirstByCategory(input.getL2().getIntentBreakdown(), "对比型");
        if (cmp == null) {
            ev.put("coverage_rate", 0);
            ev.put("total_prompts", 0);
            ev.put("covered_prompts", 0);
            return ev;
        }

        ev.put("coverage_rate", Math.round(cmp.getCoverageRate() == null ? 0.0 : cmp.getCoverageRate()));
        ev.put("total_prompts", cmp.getTotalPrompts() == null ? 0 : cmp.getTotalPrompts());
        ev.put("covered_prompts", cmp.getCoveredPrompts() == null ? 0 : cmp.getCoveredPrompts());
        return ev;
    }

    private IntentBreakdown findFirstByCategory(java.util.List<IntentBreakdown> list, String category) {
        if (list == null) return null;
        for (IntentBreakdown ib : list) {
            if (category.equals(ib.getCategory())) return ib;
        }
        return null;
    }
}
