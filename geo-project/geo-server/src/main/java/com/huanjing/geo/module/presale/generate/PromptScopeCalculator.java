package com.huanjing.geo.module.presale.generate;

public final class PromptScopeCalculator {
    private PromptScopeCalculator() {
    }

    public static ScopeResult calculate(int platformCount, int genericPromptCount, int competitorPromptCount) {
        int batch1Calls = platformCount * genericPromptCount * 2;
        int batch2Calls = platformCount * competitorPromptCount * 2;
        return new ScopeResult(
                platformCount,
                genericPromptCount,
                competitorPromptCount,
                batch1Calls,
                batch2Calls,
                batch1Calls + batch2Calls
        );
    }

    public record ScopeResult(int platformCount,
                              int genericPromptCount,
                              int competitorPromptCount,
                              int batch1Calls,
                              int batch2Calls,
                              int totalUpperBound) {
    }
}
