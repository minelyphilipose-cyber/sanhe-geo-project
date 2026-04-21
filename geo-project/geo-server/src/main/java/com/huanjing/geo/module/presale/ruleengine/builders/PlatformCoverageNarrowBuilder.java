package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.PlatformStatUtil;
import com.huanjing.geo.module.presale.ruleengine.util.TextFormatUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * evidence_data 字段:
 * covered_platform_count, total_platforms, uncovered_platform_count, uncovered_platforms_text
 */
@Component
public class PlatformCoverageNarrowBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PLATFORM_COVERAGE_NARROW;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        List<PlatformBreakdown> all = input.getL1().getPlatformBreakdown();
        int coveredCount = PlatformStatUtil.coveredCount(all);
        Integer total = input.getL1().getTestSummary() == null
                ? null : input.getL1().getTestSummary().getTotalPlatforms();
        int totalPlatforms = total == null ? (all == null ? 0 : all.size()) : total;

        List<String> uncoveredNames = PlatformStatUtil.uncoveredNames(all);

        ev.put("covered_platform_count", coveredCount);
        ev.put("total_platforms", totalPlatforms);
        ev.put("uncovered_platform_count", uncoveredNames.size());
        ev.put("uncovered_platforms_text", TextFormatUtil.formatPlatformNames(uncoveredNames));
        return ev;
    }
}
