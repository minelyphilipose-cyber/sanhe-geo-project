package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 持续运营价值项,不是诊断缺陷。
 */
@Component
public class PeriodicRetestMonitoringBuilder implements EvidenceDataBuilder {

    @Override
    public String supportRuleCode() {
        return RuleCodes.RULE_PERIODIC_RETEST_MONITORING;
    }

    @Override
    public Map<String, Object> build(RuleBuildInput input) {
        ComputedSnapshotDTO l2 = input == null ? null : input.getL2();
        int totalFindings = l2 == null || l2.getOptimizationFindings() == null
                ? 0 : l2.getOptimizationFindings().size();
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("monitoring_focus", "核心推荐场景、竞品进入与 AI 回答口径变化");
        ev.put("existing_finding_count", totalFindings);
        ev.put("service_action", "周期复测与变化预警");
        return ev;
    }
}
