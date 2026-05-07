package com.huanjing.geo.module.presale.ruleengine.builders;

import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder;
import com.huanjing.geo.module.presale.ruleengine.RuleBuildInput;
import com.huanjing.geo.module.presale.ruleengine.RuleCodes;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史 rule_code RULE_COVERAGE_LOW_RECOMMEND 保留以维持数据库兼容,
 * 业务口径已迁移为"高价值场景覆盖"(scene_coverage.high_value)。
 * 详见 Ticket 2.2 决策记录。
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

        SceneCoverageGroup highValue = highValue(input);
        if (highValue == null || safeInt(highValue.getTotal()) == 0) {
            // 回退:规则不应命中此状态,但为防御性返回安全默认
            ev.put("coverage_rate", 0);
            ev.put("uncovered_rate", 100);
            ev.put("total_prompts", 0);
            ev.put("covered_prompts", 0);
            ev.put("missed_count", 0);
            return ev;
        }

        int total = safeInt(highValue.getTotal());
        int covered = safeInt(highValue.getCovered());
        double coverage = safe(highValue.getCoverageRate());
        ev.put("coverage_rate", round(coverage));
        ev.put("uncovered_rate", round(100 - coverage));
        ev.put("total_prompts", total);
        ev.put("covered_prompts", covered);
        ev.put("missed_count", Math.max(0, total - covered));
        return ev;
    }

    private SceneCoverageGroup highValue(RuleBuildInput input) {
        if (input == null || input.getL2() == null) {
            return null;
        }
        ComputedSnapshotDTO.SceneCoverage sceneCoverage = input.getL2().getSceneCoverage();
        return sceneCoverage == null ? null : sceneCoverage.getHighValue();
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
