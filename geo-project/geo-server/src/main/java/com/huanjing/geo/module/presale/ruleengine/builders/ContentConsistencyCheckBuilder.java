package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.PlatformStatUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台间表达一致性的轻量检查。
 */
@Component
public class ContentConsistencyCheckBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_CONTENT_CONSISTENCY_CHECK;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        List<PlatformBreakdown> sorted = PlatformStatUtil.sortedByMentionRateDesc(
                input.getL1() == null ? null : input.getL1().getPlatformBreakdown());

        PlatformBreakdown strong = sorted.isEmpty() ? null : sorted.get(0);
        PlatformBreakdown weak = sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
        long gap = Math.round(rate(strong) - rate(weak));

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("strong_platform_name", strong == null ? "" : strong.getPlatformName());
        ev.put("strong_mention_rate", Math.round(rate(strong)));
        ev.put("weak_platform_name", weak == null ? "" : weak.getPlatformName());
        ev.put("weak_mention_rate", Math.round(rate(weak)));
        ev.put("gap_pp", Math.max(0, gap));
        ev.put("covered_platform_count", PlatformStatUtil.coveredCount(
                input.getL1() == null ? null : input.getL1().getPlatformBreakdown()));
        ev.put("total_platforms", sorted.size());
        return ev;
    }

    private double rate(PlatformBreakdown platform) {
        return platform == null || platform.getMentionRate() == null ? 0.0 : platform.getMentionRate();
    }
}
