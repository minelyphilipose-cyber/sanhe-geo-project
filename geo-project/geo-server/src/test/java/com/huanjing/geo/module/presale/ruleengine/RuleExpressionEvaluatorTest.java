package com.huanjing.geo.module.presale.ruleengine;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleExpressionEvaluatorTest {

    private RuleExpressionEvaluator evaluator;
    private RawSnapshotDTO l1;
    private ComputedSnapshotDTO l2;

    @BeforeEach
    void setUp() {
        evaluator = new RuleExpressionEvaluator();

        l1 = new RawSnapshotDTO();
        l2 = new ComputedSnapshotDTO();

        Scores scores = new Scores();
        scores.setOverall(45.0);
        scores.setSentiment(70.0);
        l2.setScores(scores);
    }

    @Test
    void hit_booleanTrue() {
        var outcome = evaluator.evaluate("#l2.scores.overall < 50", l1, l2);
        assertThat(outcome.hasError()).isFalse();
        assertThat(outcome.isHit()).isTrue();
    }

    @Test
    void notHit_booleanFalse() {
        var outcome = evaluator.evaluate("#l2.scores.overall > 100", l1, l2);
        assertThat(outcome.hasError()).isFalse();
        assertThat(outcome.isHit()).isFalse();
    }

    @Test
    void nullResult_treatedAsNotHit() {
        // 表达式结果返回 null(访问不存在但合法的字段路径)
        var outcome = evaluator.evaluate("#l1.sentimentDetail == null ? null : false", l1, l2);
        assertThat(outcome.hasError()).isFalse();
        assertThat(outcome.isHit()).isFalse();
    }

    @Test
    void parseError_reportedAsParseType() {
        var outcome = evaluator.evaluate("#l2.scores.overall < < 50", l1, l2);
        assertThat(outcome.hasError()).isTrue();
        assertThat(outcome.getErrorType()).isEqualTo(RuleEvaluationError.ErrorType.PARSE);
    }

    @Test
    void evalError_reportedAsEvalType() {
        // 故意访问 null 字段抛 NPE
        l2.setScores(null);
        var outcome = evaluator.evaluate("#l2.scores.overall < 50", l1, l2);
        assertThat(outcome.hasError()).isTrue();
        assertThat(outcome.getErrorType()).isEqualTo(RuleEvaluationError.ErrorType.EVAL);
    }

    @Test
    void typeMismatch_reportedAsTypeMismatch() {
        // 表达式返回数字不是布尔
        var outcome = evaluator.evaluate("#l2.scores.overall", l1, l2);
        assertThat(outcome.hasError()).isTrue();
        assertThat(outcome.getErrorType()).isEqualTo(RuleEvaluationError.ErrorType.TYPE_MISMATCH);
    }

    @Test
    void emptyExpression_reportedAsParse() {
        var outcome = evaluator.evaluate("", l1, l2);
        assertThat(outcome.hasError()).isTrue();
        assertThat(outcome.getErrorType()).isEqualTo(RuleEvaluationError.ErrorType.PARSE);
    }
}
