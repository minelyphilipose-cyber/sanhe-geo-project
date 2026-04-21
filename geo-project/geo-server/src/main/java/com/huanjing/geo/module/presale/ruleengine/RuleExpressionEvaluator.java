package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * SpEL 表达式安全执行器。
 *
 * <p>契约(docs §4):</p>
 * <ul>
 *   <li>返回 Boolean,true = 命中;null = 不命中(兜底);false = 不命中</li>
 *   <li>解析异常 / 执行异常 / 类型不匹配 → 包装为 {@link EvaluationOutcome} 返回</li>
 *   <li>永不抛出异常,调用方按 outcome 决定处理策略</li>
 * </ul>
 */
@Component
public class RuleExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 评估表达式。返回值中包含 hit 布尔值和可选的错误信息。
     */
    public EvaluationOutcome evaluate(String expression, RawSnapshotDTO l1, ComputedSnapshotDTO l2) {
        if (expression == null || expression.isBlank()) {
            return EvaluationOutcome.error(RuleEvaluationError.ErrorType.PARSE, "Empty expression");
        }

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("l1", l1);
        ctx.setVariable("l2", l2);
        ctx.setVariable("benchmarks", l1 == null ? null : l1.getBenchmarksFrozen());

        Expression exp;
        try {
            exp = parser.parseExpression(expression);
        } catch (ParseException e) {
            return EvaluationOutcome.error(RuleEvaluationError.ErrorType.PARSE, e.getMessage());
        }

        Object result;
        try {
            result = exp.getValue(ctx);
        } catch (EvaluationException e) {
            return EvaluationOutcome.error(RuleEvaluationError.ErrorType.EVAL, e.getMessage());
        } catch (RuntimeException e) {
            // NPE / ClassCastException 等运行期异常
            return EvaluationOutcome.error(RuleEvaluationError.ErrorType.EVAL,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (result == null) {
            return EvaluationOutcome.hit(false);
        }
        if (result instanceof Boolean) {
            return EvaluationOutcome.hit((Boolean) result);
        }
        return EvaluationOutcome.error(RuleEvaluationError.ErrorType.TYPE_MISMATCH,
                "Expected Boolean but got " + result.getClass().getName());
    }

    /** 评估结果封装。错误和命中是互斥的。 */
    public static final class EvaluationOutcome {
        private final boolean hit;
        private final RuleEvaluationError.ErrorType errorType;
        private final String errorMessage;

        private EvaluationOutcome(boolean hit, RuleEvaluationError.ErrorType errorType, String errorMessage) {
            this.hit = hit;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        static EvaluationOutcome hit(boolean hit) {
            return new EvaluationOutcome(hit, null, null);
        }

        static EvaluationOutcome error(RuleEvaluationError.ErrorType type, String message) {
            return new EvaluationOutcome(false, type, message);
        }

        public boolean isHit() {
            return hit;
        }

        public boolean hasError() {
            return errorType != null;
        }

        public RuleEvaluationError.ErrorType getErrorType() {
            return errorType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
