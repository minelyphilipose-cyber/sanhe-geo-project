package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则引擎执行结果。
 *
 * <p>{@link PresaleRuleEngineExecutor#execute} 的返回值,由 GenerateService 消费。</p>
 *
 * <p>语义:</p>
 * <ul>
 *   <li>{@link #findings} 已按 priority → sort_order → id 三级稳定排序,上层无需再排</li>
 *   <li>{@link #hitCount} 等于 {@code findings.size()},字段冗余为调用方可读</li>
 *   <li>{@link #evaluatedRuleCount} 为本次 enabled 规则总数,不含 disabled 或空表情况</li>
 *   <li>{@link #errors} 非空时,GenerateService 应记 WARN + metric,v1 不在 UI 展示</li>
 * </ul>
 *
 * <p>参考 docs/presale/p1e-rule-engine-design-v1.md §5.1。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEngineResult {

    /** 已排序的命中结果。空规则库 / 全 disabled / 零命中 均返回空 List。 */
    private List<OptimizationFinding> findings;

    /** 本次评估的 enabled 规则数量。 */
    private int evaluatedRuleCount;

    /** 命中数量,等于 findings.size()。 */
    private int hitCount;

    /** 评估失败的规则列表。正常情况为空。 */
    private List<RuleEvaluationError> errors;

    /** 便捷工厂:空结果(规则库为空/全 disabled 时使用)。 */
    public static RuleEngineResult empty() {
        return RuleEngineResult.builder()
                .findings(Collections.emptyList())
                .evaluatedRuleCount(0)
                .hitCount(0)
                .errors(new ArrayList<>())
                .build();
    }
}
