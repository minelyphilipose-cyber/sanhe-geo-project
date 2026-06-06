package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HighValueRecoGapBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_HIGH_VALUE_RECO_GAP;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        IntentBreakdown recommendation = RuleEvidenceSupport.intent(input, "推荐型", "高");
        SceneCompetitorPressure pressure = RuleEvidenceSupport.pressure(input);
        int total = RuleEvidenceSupport.hvRecoTotal(pressure);
        int covered = RuleEvidenceSupport.covered(recommendation);
        if (total == 0) {
            total = RuleEvidenceSupport.total(recommendation);
        }
        int gap = Math.max(0, total - covered);

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("hv_reco_total", total);
        ev.put("hv_reco_covered", covered);
        ev.put("hv_reco_gap", gap);
        ev.put("hv_reco_rate", RuleEvidenceSupport.rate(recommendation));
        return ev;
    }
}
