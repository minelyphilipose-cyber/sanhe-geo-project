package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CompetitorPresentClientAbsentBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_COMPETITOR_PRESENT_CLIENT_ABSENT;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        SceneCompetitorPressure pressure = RuleEvidenceSupport.pressure(input);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("hv_reco_total", RuleEvidenceSupport.hvRecoTotal(pressure));
        ev.put("display_gap_count", RuleEvidenceSupport.competitorPresentAbsentCount(pressure));
        ev.put("top_competitor_name", RuleEvidenceSupport.topCompetitorInDisplaySet(pressure));
        ev.put("top_competitor_platform_mentions", RuleEvidenceSupport.topCompetitorMentionedPlatforms(pressure));
        ev.put("example_query", RuleEvidenceSupport.firstAbsentQuery(pressure));
        return ev;
    }
}
