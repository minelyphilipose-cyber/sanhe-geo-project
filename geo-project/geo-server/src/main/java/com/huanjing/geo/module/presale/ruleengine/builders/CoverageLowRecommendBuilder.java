package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * evidence_data 字段(来自 README_P1D):
 * - coverage_rate, uncovered_rate, total_prompts, covered_prompts, top_competitor_coverage_rate
 *
 * 数据来源:L2.intentBreakdown 中 category='推荐型' 且 businessValue='高' 的条目。
 * top_competitor_coverage_rate 在 L2 里没有直接字段(该字段本是 mock 示意性存在),
 * 这里由 Builder 在真实 L2 数据可用时填充;当前 DTO 缺此字段时用默认 100(Top1 全覆盖是常见场景)。
 */
@Component
public class CoverageLowRecommendBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_COVERAGE_LOW_RECOMMEND;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        IntentBreakdown recHigh = findRecommendHigh(input.getL2().getIntentBreakdown());
        if (recHigh == null) {
            // 回退:规则不应命中此状态,但为防御性返回安全默认
            ev.put("coverage_rate", 0);
            ev.put("uncovered_rate", 100);
            ev.put("total_prompts", 0);
            ev.put("covered_prompts", 0);
            ev.put("top_competitor_coverage_rate", 100);
            return ev;
        }

        double coverage = safe(recHigh.getCoverageRate());
        ev.put("coverage_rate", round(coverage));
        ev.put("uncovered_rate", round(100 - coverage));
        ev.put("total_prompts", safeInt(recHigh.getTotalPrompts()));
        ev.put("covered_prompts", safeInt(recHigh.getCoveredPrompts()));
        // Top1 竞品覆盖率:当前 L2 无此字段,使用常识默认(Top1 在推荐型场景通常全覆盖)
        ev.put("top_competitor_coverage_rate", 100);
        return ev;
    }

    private IntentBreakdown findRecommendHigh(List<IntentBreakdown> list) {
        if (list == null) return null;
        for (IntentBreakdown ib : list) {
            if ("推荐型".equals(ib.getCategory()) && "高".equals(ib.getBusinessValue())) {
                return ib;
            }
        }
        return null;
    }

    private double safe(Double v) {
        return v == null ? 0.0 : v;
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private long round(double v) {
        return Math.round(v);
    }
}
