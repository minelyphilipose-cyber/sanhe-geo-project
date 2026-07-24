package com.huanjing.geo.module.project.service;

final class BaselineCanonicalAggregateRules {
    static final String METRIC_MENTION_RATE = "mention_rate";
    static final String METRIC_AWARENESS = "awareness";
    static final String METRIC_FAVORABILITY = "favorability";

    private BaselineCanonicalAggregateRules() {
    }

    static boolean includeInRateDenominator(int expectedSamples, int successSamples) {
        return successSamples >= Math.min(2, expectedSamples);
    }

    static boolean isCovered(int expectedSamples, int successSamples, int positiveSamples) {
        if (!includeInRateDenominator(expectedSamples, successSamples)) {
            return false;
        }
        return positiveSamples * 2 >= successSamples;
    }

    static String metricKindForIntent(String intentType) {
        if (BaselineReportSnapshotRules.INTENT_AWARENESS.equals(intentType)) {
            return METRIC_AWARENESS;
        }
        if (BaselineReportSnapshotRules.INTENT_COMPARISON.equals(intentType)) {
            return METRIC_FAVORABILITY;
        }
        return METRIC_MENTION_RATE;
    }

    static boolean isMetricPositive(int expectedSamples,
                                    int successSamples,
                                    int mentionedSamples,
                                    int awarenessSamples,
                                    int favorableSamples,
                                    String metricKind) {
        if (!includeInRateDenominator(expectedSamples, successSamples)) {
            return false;
        }
        if (METRIC_AWARENESS.equals(metricKind)) {
            return awarenessSamples * 2 >= successSamples;
        }
        if (METRIC_FAVORABILITY.equals(metricKind)) {
            return favorableSamples * 2 >= successSamples;
        }
        return mentionedSamples * 2 >= successSamples;
    }

    static Band wilsonBand(int numerator, int denominator) {
        if (denominator <= 0) {
            return new Band(0D, 0D, "NO_SAMPLE");
        }
        double z = 1.96D;
        double n = denominator;
        double p = numerator / n;
        double z2 = z * z;
        double center = (p + z2 / (2D * n)) / (1D + z2 / n);
        double margin = z * Math.sqrt((p * (1D - p) + z2 / (4D * n)) / n) / (1D + z2 / n);
        return new Band(clamp(center - margin), clamp(center + margin), "WILSON_95");
    }

    static double safeRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return numerator / (double) denominator;
    }

    private static double clamp(double value) {
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    record Band(double low, double high, String method) {
    }
}
