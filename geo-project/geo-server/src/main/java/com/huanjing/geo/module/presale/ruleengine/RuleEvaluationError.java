package com.huanjing.geo.module.presale.ruleengine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条规则评估失败的诊断信息。
 *
 * <p>由 {@link RuleExpressionEvaluator} 捕获异常后产出,最终汇入
 * {@link RuleEngineResult#getErrors()},供 GenerateService 做 WARN 日志和 metric 上报。</p>
 *
 * <p>参考 docs/presale/p1e-rule-engine-design-v1.md §4 Error reporting。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEvaluationError {

    /** 触发错误的规则编码,如 "RULE_COVERAGE_LOW_RECOMMEND"。 */
    private String ruleCode;

    /** 失败的原始 SpEL 表达式,排查用。 */
    private String expression;

    /** 异常 message(堆栈不保留,由日志层按需打印)。 */
    private String errorMessage;

    /** 错误分类:PARSE / EVAL / TYPE_MISMATCH / BUILDER_MISSING。 */
    private ErrorType errorType;

    public enum ErrorType {
        /** 解析期失败:SpEL 语法非法。 */
        PARSE,
        /** 运行期失败:NPE、Stream 空集合等。 */
        EVAL,
        /** 返回值类型不匹配:表达式结果非 Boolean 且不是 null。 */
        TYPE_MISMATCH,
        /**
         * 规则命中但 {@link com.huanjing.geo.module.presale.ruleengine.EvidenceDataBuilder}
         * 未注册(代码/数据配置错配:规则表加了新 rule_code 但 Builder 没跟上)。
         * 属于非阻塞错误,finding 不产出,但需要上层告警以便运维发现。
         */
        BUILDER_MISSING
    }
}
