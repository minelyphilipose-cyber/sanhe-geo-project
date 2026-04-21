package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import com.huanjing.geo.module.presale.ruleengine.util.PlatformStatUtil;
import com.huanjing.geo.module.presale.ruleengine.util.TextFormatUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复杂 Builder(涉及聚合/排序/文本拼接)。
 *
 * evidence_data 字段:
 * - total_platforms
 * - strong_platform_name, strong_mention_rate
 * - weak_platform_name, weak_mention_rate
 * - gap_pp
 * - strong_platforms_text, weak_platforms_text
 *
 * 算法:
 * 1. 取有效平台(非降级),按 mentionRate 降序排列
 * 2. strong = 第一位,weak = 最后一位
 * 3. gap_pp = strong - weak(百分点)
 * 4. strong/weak_platforms_text 取前 3 / 后 3(或不足则全取)
 */
@Component
public class PlatformImbalanceBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PLATFORM_IMBALANCE;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        Map<String, Object> ev = new LinkedHashMap<>();

        List<PlatformBreakdown> all = input.getL1().getPlatformBreakdown();
        List<PlatformBreakdown> sorted = PlatformStatUtil.sortedByMentionRateDesc(all);

        Integer totalPlatforms = input.getL1().getTestSummary() == null
                ? null : input.getL1().getTestSummary().getTotalPlatforms();
        ev.put("total_platforms", totalPlatforms == null ? (all == null ? 0 : all.size()) : totalPlatforms);

        if (sorted.size() < 2) {
            // 规则不应命中此状态,防御性默认
            ev.put("strong_platform_name", "");
            ev.put("strong_mention_rate", 0);
            ev.put("weak_platform_name", "");
            ev.put("weak_mention_rate", 0);
            ev.put("gap_pp", 0);
            ev.put("strong_platforms_text", "");
            ev.put("weak_platforms_text", "");
            return ev;
        }

        PlatformBreakdown strong = sorted.get(0);
        PlatformBreakdown weak = sorted.get(sorted.size() - 1);

        double strongRate = strong.getMentionRate() == null ? 0.0 : strong.getMentionRate();
        double weakRate = weak.getMentionRate() == null ? 0.0 : weak.getMentionRate();

        ev.put("strong_platform_name", strong.getPlatformName());
        ev.put("strong_mention_rate", Math.round(strongRate));
        ev.put("weak_platform_name", weak.getPlatformName());
        ev.put("weak_mention_rate", Math.round(weakRate));
        ev.put("gap_pp", Math.round(strongRate - weakRate));

        int topN = 3;
        List<PlatformBreakdown> topStrong = sorted.subList(0, Math.min(topN, sorted.size()));
        List<PlatformBreakdown> topWeakList = sorted.subList(Math.max(0, sorted.size() - topN), sorted.size());
        // weak 从低到高,反转一下让最弱的靠前
        List<PlatformBreakdown> weakRev = new java.util.ArrayList<>(topWeakList);
        Collections.reverse(weakRev);

        ev.put("strong_platforms_text",
                TextFormatUtil.formatPlatformsWithRate(PlatformStatUtil.toRateEntries(topStrong)));
        ev.put("weak_platforms_text",
                TextFormatUtil.formatPlatformsWithRate(PlatformStatUtil.toRateEntries(weakRev)));
        return ev;
    }
}
