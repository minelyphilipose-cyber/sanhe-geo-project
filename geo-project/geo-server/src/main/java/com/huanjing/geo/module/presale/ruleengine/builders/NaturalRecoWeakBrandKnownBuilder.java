package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NaturalRecoWeakBrandKnownBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_NATURAL_RECO_WEAK_BRAND_KNOWN;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        IntentBreakdown recommendation = RuleEvidenceSupport.intent(input, "推荐型", "高");
        IntentBreakdown cognitive = RuleEvidenceSupport.intent(input, "认知型");
        IntentBreakdown comparison = RuleEvidenceSupport.intent(input, "对比型");

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("recommendation_rate", RuleEvidenceSupport.rate(recommendation));
        ev.put("recommendation_covered", RuleEvidenceSupport.covered(recommendation));
        ev.put("recommendation_total", RuleEvidenceSupport.total(recommendation));
        ev.put("cognitive_rate", RuleEvidenceSupport.rate(cognitive));
        ev.put("comparison_rate", RuleEvidenceSupport.rate(comparison));
        ev.put("known_rate", Math.max(RuleEvidenceSupport.rate(cognitive), RuleEvidenceSupport.rate(comparison)));
        ev.put("threshold_rate", 20);
        return ev;
    }
}
