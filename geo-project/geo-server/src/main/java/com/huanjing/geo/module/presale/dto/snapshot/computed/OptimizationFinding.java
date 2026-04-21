package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 规则引擎命中的优化发现(L2)。
 * <p>Schema v1.2 $.computed_snapshot.optimization_findings[]</p>
 * <p>
 * 与 {@code presale_optimization_finding} 子表同源写入(JSON 列视为冻结副本,子表用于跨版本查询)。
 * <br>
 * 对客文案(title/description 等)在 L3 {@code optimization_findings_content}
 * 通过 {@code finding_id} 一一对应。
 * </p>
 * <p>
 * <b>priority 和 category:</b>schema 枚举:
 * <ul>
 *   <li>priority: HIGH / MEDIUM / LOW(稳定英文,用 Java enum)</li>
 *   <li>category: 基础设施 / 内容建设 / 关系建设 / 平台扩展(中文字面值,用 String)</li>
 * </ul>
 * 按决策 3C 混合处理。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptimizationFinding {

    /** 本次报告内唯一,如 "F001"。L3 通过此 ID 关联文案。 */
    @JsonProperty("finding_id")
    private String findingId;

    /** 触发的规则编码,如 "RULE_COVERAGE_LOW_RECOMMEND",来自 presale_optimization_rule.rule_code。 */
    @JsonProperty("rule_code")
    private String ruleCode;

    /** 优先级(稳定枚举)。 */
    private Priority priority;

    /** 分类(中文字面值,保持 String)。 */
    private String category;

    /**
     * 规则触发时的结构化上下文数据。
     * <p>
     * Schema 定义为 {@code additionalProperties: true} 的自由对象,供 L3 文案模板填充。
     * 按决策 1A 使用 {@code Map<String, Object>},不同 rule_code 有不同字段集,
     * 前端按 rule_code 路由取值即可。
     * </p>
     * <p>
     * 示例(RULE_COVERAGE_LOW_RECOMMEND):
     * <pre>
     * {
     *   "coverage_rate": 70,
     *   "total_prompts": 10,
     *   "covered_prompts": 7,
     *   "top_competitor_coverage_rate": 100
     * }
     * </pre>
     * </p>
     */
    @JsonProperty("evidence_data")
    private Map<String, Object> evidenceData;

    public enum Priority { HIGH, MEDIUM, LOW }
}
