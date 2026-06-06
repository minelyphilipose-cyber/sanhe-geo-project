package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RecommendationAbsentBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_RECOMMENDATION_ABSENT;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        SceneCompetitorPressure pressure = RuleEvidenceSupport.pressure(input);
        int total = RuleEvidenceSupport.hvRecoTotal(pressure);
        int absent = RuleEvidenceSupport.absentCount(pressure);
        long absenceRate = total == 0 ? 0 : Math.round(absent * 100.0 / total);

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("hv_reco_total", total);
        ev.put("client_absent_count", absent);
        ev.put("absence_rate", absenceRate);
        ev.put("example_query", RuleEvidenceSupport.firstAbsentQuery(pressure));
        return ev;
    }
}
