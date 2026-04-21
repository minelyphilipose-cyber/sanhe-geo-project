package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * evidence_data 字段:overall_score, industry_avg_overall, top1_overall
 *
 * 数据来源:
 * - overall_score         ← L2.scores.overall
 * - industry_avg_overall  ← benchmarks.industryAvg.overall
 * - top1_overall          ← benchmarks.top1.overall
 */
@Component
public class BrandAwarenessLowBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_BRAND_AWARENESS_LOW;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        Scores scores = input.getL2().getScores();
        BenchmarksFrozen bench = input.getBenchmarks();

        ev.put("overall_score", round(scores == null ? 0.0 : safe(scores.getOverall())));
        ev.put("industry_avg_overall", round(bench == null || bench.getIndustryAvg() == null
                ? 0.0 : safe(bench.getIndustryAvg().getOverall())));
        ev.put("top1_overall", round(bench == null || bench.getTop1() == null
                ? 0.0 : safe(bench.getTop1().getOverall())));
        return ev;
    }

    private double safe(Double v) {
        return v == null ? 0.0 : v;
    }

    private long round(double v) {
        return Math.round(v);
    }
}
