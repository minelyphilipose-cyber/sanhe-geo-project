package com.huanjing.geo.module.presale.generate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptScopeCalculatorTest {

    @Test
    void calculate_matchesPromptScopeFormula() {
        PromptScopeCalculator.ScopeResult result = PromptScopeCalculator.calculate(10, 38, 7);

        assertEquals(10, result.platformCount());
        assertEquals(38, result.genericPromptCount());
        assertEquals(7, result.competitorPromptCount());
        assertEquals(760, result.batch1Calls());
        assertEquals(140, result.batch2Calls());
        assertEquals(900, result.totalUpperBound());
    }
}
