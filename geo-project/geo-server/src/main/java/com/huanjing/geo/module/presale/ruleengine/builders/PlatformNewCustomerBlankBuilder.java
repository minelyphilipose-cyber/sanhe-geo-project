package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PlatformNewCustomerBlankBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PLATFORM_NEW_CUSTOMER_BLANK;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        IntentBreakdown recommendation = RuleEvidenceSupport.intent(input, "推荐型", "高");
        IntentBreakdown inquiry = RuleEvidenceSupport.intent(input, "问题型");
        IntentBreakdown scenario = RuleEvidenceSupport.intent(input, "场景型");
        long average = Math.round((RuleEvidenceSupport.rate(recommendation)
                + RuleEvidenceSupport.rate(inquiry)
                + RuleEvidenceSupport.rate(scenario)) / 3.0);

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("new_customer_avg_rate", average);
        ev.put("recommendation_rate", RuleEvidenceSupport.rate(recommendation));
        ev.put("inquiry_rate", RuleEvidenceSupport.rate(inquiry));
        ev.put("scenario_rate", RuleEvidenceSupport.rate(scenario));
        return ev;
    }
}
