package com.huanjing.geo.module.project.service;

import java.util.LinkedHashMap;
import java.util.Map;

final class BaselineCanonicalVersionPolicy {
    static final String SCHEMA_VERSION = "baseline_canonical_v1";
    static final String SCORE_ALGORITHM_VERSION = "baseline_score_semantic_judge_v2";
    static final String HIGHLIGHT_ALGORITHM_VERSION = "baseline_highlight_semantic_judge_v2";
    static final String COMPETITOR_NORMALIZATION_VERSION = "baseline_competitor_semantic_norm_v2";
    static final String CANONICAL_AGGREGATE_VERSION = "baseline_canonical_aggregate_v1";

    private BaselineCanonicalVersionPolicy() {
    }

    static Map<String, String> expandAlgorithmVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("mention", SCORE_ALGORITHM_VERSION);
        versions.put("recommendation", SCORE_ALGORITHM_VERSION);
        versions.put("ranking", SCORE_ALGORITHM_VERSION);
        versions.put("sentiment", SCORE_ALGORITHM_VERSION);
        versions.put("impression", SCORE_ALGORITHM_VERSION);
        versions.put("highlight", HIGHLIGHT_ALGORITHM_VERSION);
        versions.put("competitor_normalization", COMPETITOR_NORMALIZATION_VERSION);
        versions.put("coverage", CANONICAL_AGGREGATE_VERSION);
        versions.put("band", CANONICAL_AGGREGATE_VERSION);
        return versions;
    }

    static Map<String, String> selectedVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("score_algorithm_version", SCORE_ALGORITHM_VERSION);
        versions.put("highlight_algorithm_version", HIGHLIGHT_ALGORITHM_VERSION);
        versions.put("competitor_normalization_version", COMPETITOR_NORMALIZATION_VERSION);
        versions.put("canonical_aggregate_version", CANONICAL_AGGREGATE_VERSION);
        return versions;
    }
}
