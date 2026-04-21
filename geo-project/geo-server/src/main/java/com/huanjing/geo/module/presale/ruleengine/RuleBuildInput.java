package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.persist.PresaleOptimizationRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * {@link EvidenceDataBuilder} 的输入封装。
 *
 * <p>字段:</p>
 * <ul>
 *   <li>{@link #l1} L1 原始快照</li>
 *   <li>{@link #l2} L2 计算快照</li>
 *   <li>{@link #benchmarks} L1.benchmarksFrozen 的快捷引用,与 SpEL 上下文 #benchmarks 对称</li>
 *   <li>{@link #rule} 当前规则行,含 rule_code / title_template / description_template / evidence_template / default_priority</li>
 * </ul>
 *
 * <p>参考 docs/presale/p1e-rule-engine-design-v1.md §5。</p>
 */
@Data
@Builder
@AllArgsConstructor
public class RuleBuildInput {
    private final RawSnapshotDTO l1;
    private final ComputedSnapshotDTO l2;
    private final BenchmarksFrozen benchmarks;
    private final PresaleOptimizationRule rule;
}
