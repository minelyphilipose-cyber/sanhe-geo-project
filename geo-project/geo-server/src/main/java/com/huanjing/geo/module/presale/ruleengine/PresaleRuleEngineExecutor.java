package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.ruleengine.persist.PresaleOptimizationRule;
import com.huanjing.geo.module.presale.ruleengine.persist.PresaleOptimizationRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 售前优化规则引擎主入口。
 *
 * <p>契约(docs §3 / §9):</p>
 * <ul>
 *   <li>纯函数,无 DB 写副作用(读规则表不算副作用)</li>
 *   <li>输入 L1 + L2,输出已排序的 findings + 诊断</li>
 *   <li>单规则失败不阻断其他规则,不阻断报告生成</li>
 *   <li>排序:priority (HIGH/MEDIUM/LOW) → sort_order ASC → rule id ASC</li>
 * </ul>
 */
@Service
public class PresaleRuleEngineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PresaleRuleEngineExecutor.class);

    private final PresaleOptimizationRuleService ruleService;
    private final RuleExpressionEvaluator evaluator;
    private final EvidenceDataBuilderRegistry builderRegistry;

    public PresaleRuleEngineExecutor(PresaleOptimizationRuleService ruleService,
                                     RuleExpressionEvaluator evaluator,
                                     EvidenceDataBuilderRegistry builderRegistry) {
        this.ruleService = ruleService;
        this.evaluator = evaluator;
        this.builderRegistry = builderRegistry;
    }

    /**
     * 执行规则引擎。
     *
     * @param l1 原始快照(不可为 null)
     * @param l2 计算快照(不可为 null)
     * @return 排序后的命中结果 + 诊断信息;规则表空/全 disabled 返回 {@link RuleEngineResult#empty()}
     */
    public RuleEngineResult execute(RawSnapshotDTO l1, ComputedSnapshotDTO l2) {
        if (l1 == null || l2 == null) {
            throw new IllegalArgumentException("L1 and L2 snapshots must not be null");
        }

        List<PresaleOptimizationRule> rules = ruleService.loadEnabledRulesOrdered();
        if (rules.isEmpty()) {
            log.info("Rule engine: no enabled rules loaded, returning empty result");
            return RuleEngineResult.empty();
        }

        FindingIdAllocator idAllocator = new FindingIdAllocator();
        List<OptimizationFinding> findings = new ArrayList<>();
        List<RuleEvaluationError> errors = new ArrayList<>();

        for (PresaleOptimizationRule rule : rules) {
            try {
                RuleExpressionEvaluator.EvaluationOutcome outcome =
                        evaluator.evaluate(rule.getTriggerExpression(), l1, l2);

                if (outcome.hasError()) {
                    errors.add(RuleEvaluationError.builder()
                            .ruleCode(rule.getRuleCode())
                            .expression(rule.getTriggerExpression())
                            .errorMessage(outcome.getErrorMessage())
                            .errorType(outcome.getErrorType())
                            .build());
                    continue;
                }

                if (!outcome.isHit()) {
                    continue;
                }

                // 命中。先检查 Builder 是否存在,不存在视为 BUILDER_MISSING 错误
                // (可观测性:避免规则配置/代码漂移被静默吞掉,Codex P1·E 审阅 P2-1 修复)
                EvidenceDataBuilder builder = builderRegistry.get(rule.getRuleCode());
                if (builder == null) {
                    log.warn("Rule engine: no EvidenceDataBuilder registered for rule_code={}, "
                                    + "recording as BUILDER_MISSING error",
                            rule.getRuleCode());
                    errors.add(RuleEvaluationError.builder()
                            .ruleCode(rule.getRuleCode())
                            .expression(rule.getTriggerExpression())
                            .errorMessage("No EvidenceDataBuilder registered for rule_code="
                                    + rule.getRuleCode())
                            .errorType(RuleEvaluationError.ErrorType.BUILDER_MISSING)
                            .build());
                    continue;
                }

                // 分配 finding id 并构建
                OptimizationFinding finding = buildFinding(rule, builder, l1, l2, idAllocator);
                findings.add(finding);
            } catch (RuntimeException ex) {
                // Builder 内部抛异常也不应阻断其他规则
                log.warn("Rule engine: builder failed for rule_code={}, treating as error",
                        rule.getRuleCode(), ex);
                errors.add(RuleEvaluationError.builder()
                        .ruleCode(rule.getRuleCode())
                        .expression(rule.getTriggerExpression())
                        .errorMessage("Builder exception: " + ex.getMessage())
                        .errorType(RuleEvaluationError.ErrorType.EVAL)
                        .build());
            }
        }

        findings = applyCustomerVisibleFilters(findings);
        findings.sort(buildFindingComparator(rules));

        return RuleEngineResult.builder()
                .findings(findings)
                .evaluatedRuleCount(rules.size())
                .hitCount(findings.size())
                .errors(errors)
                .build();
    }

    /**
     * 对客 findings 的二次过滤。
     *
     * <p>LOW priority 是互斥分区,不是上档问题的重复表达。这里先拿到完整 fired set,
     * 再移除只适合作为内部可靠性信号或已被 HIGH/MEDIUM 覆盖的 LOW 项。</p>
     */
    private List<OptimizationFinding> applyCustomerVisibleFilters(List<OptimizationFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return findings == null ? List.of() : findings;
        }
        Set<String> fired = new HashSet<>();
        for (OptimizationFinding finding : findings) {
            if (finding != null && finding.getRuleCode() != null) {
                fired.add(finding.getRuleCode());
            }
        }

        List<OptimizationFinding> filtered = new ArrayList<>();
        for (OptimizationFinding finding : findings) {
            if (finding == null) continue;
            String code = finding.getRuleCode();
            if (RuleCodes.RULE_PLATFORM_COUNT_LOW.equals(code)) {
                log.info("Rule engine: suppress customer-facing finding {}, kept as internal reliability signal", code);
                continue;
            }
            if (shouldSuppressLowFinding(code, fired)) {
                log.info("Rule engine: suppress duplicated LOW finding {}, higher-priority firedSet={}", code, fired);
                continue;
            }
            filtered.add(finding);
        }
        return filtered;
    }

    private boolean shouldSuppressLowFinding(String code, Set<String> fired) {
        if (RuleCodes.RULE_PLATFORM_DEPTH_SHALLOW.equals(code)) {
            return fired.contains(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW)
                    || fired.contains(RuleCodes.RULE_PLATFORM_IMBALANCE)
                    || fired.contains(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT);
        }
        if (RuleCodes.RULE_LONG_TAIL_SCENE_GAP.equals(code)) {
            return fired.contains(RuleCodes.RULE_COVERAGE_LOW_RECOMMEND)
                    || fired.contains(RuleCodes.RULE_SCENE_MISS_HIGH_VALUE)
                    || fired.contains(RuleCodes.RULE_HIGH_VALUE_RECO_GAP);
        }
        if (RuleCodes.RULE_CONTENT_CONSISTENCY_CHECK.equals(code)) {
            return fired.contains(RuleCodes.RULE_PLATFORM_IMBALANCE)
                    || fired.contains(RuleCodes.RULE_PLATFORM_COVERAGE_NARROW)
                    || fired.contains(RuleCodes.RULE_SINGLE_PLATFORM_DOMINANT);
        }
        return false;
    }

    private OptimizationFinding buildFinding(PresaleOptimizationRule rule,
                                             EvidenceDataBuilder builder,
                                             RawSnapshotDTO l1,
                                             ComputedSnapshotDTO l2,
                                             FindingIdAllocator idAllocator) {
        RuleBuildInput input = RuleBuildInput.builder()
                .l1(l1)
                .l2(l2)
                .benchmarks(l1.getBenchmarksFrozen())
                .rule(rule)
                .build();

        Map<String, Object> evidenceData = builder.build(input);
        if (evidenceData == null) {
            evidenceData = Collections.emptyMap();
        }

        return OptimizationFinding.builder()
                .findingId(idAllocator.next())
                .ruleCode(rule.getRuleCode())
                .priority(parsePriority(rule.getDefaultPriority()))
                .category(rule.getCategory())
                .evidenceData(evidenceData)
                .build();
    }

    private OptimizationFinding.Priority parsePriority(String value) {
        if (value == null) return OptimizationFinding.Priority.LOW;
        try {
            return OptimizationFinding.Priority.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Rule engine: unknown priority value '{}', defaulting to LOW", value);
            return OptimizationFinding.Priority.LOW;
        }
    }

    /**
     * 排序:priority HIGH>MEDIUM>LOW → sort_order ASC → rule id ASC。
     * 使用规则列表建立 ruleCode → 辅助索引。
     */
    private Comparator<OptimizationFinding> buildFindingComparator(List<PresaleOptimizationRule> rules) {
        // 建立 rule_code → (sort_order, id) 索引
        java.util.Map<String, int[]> index = new java.util.HashMap<>();
        for (PresaleOptimizationRule r : rules) {
            index.put(r.getRuleCode(), new int[] {
                    r.getSortOrder() == null ? Integer.MAX_VALUE : r.getSortOrder(),
                    r.getId() == null ? Integer.MAX_VALUE : r.getId().intValue()
            });
        }

        return (a, b) -> {
            int pa = priorityRank(a.getPriority());
            int pb = priorityRank(b.getPriority());
            if (pa != pb) return Integer.compare(pa, pb);

            int[] ra = index.getOrDefault(a.getRuleCode(), new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE });
            int[] rb = index.getOrDefault(b.getRuleCode(), new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE });
            if (ra[0] != rb[0]) return Integer.compare(ra[0], rb[0]);
            return Integer.compare(ra[1], rb[1]);
        };
    }

    /** HIGH=0, MEDIUM=1, LOW=2,未知/null=3。 */
    private int priorityRank(OptimizationFinding.Priority p) {
        if (p == null) return 3;
        switch (p) {
            case HIGH: return 0;
            case MEDIUM: return 1;
            case LOW: return 2;
            default: return 3;
        }
    }
}
