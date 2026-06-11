package com.huanjing.geo.module.content.constant;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MedicalArticleConstants {
    public static final String INDUSTRY_MEDICAL_BEAUTY = "medical_beauty";
    public static final String INDUSTRY_ORAL = "oral";

    public static final String TIER_EDUCATION = "education";
    public static final String TIER_SOURCE_SITE = "source_site";
    public static final String TIER_OFFICIAL_SITE = "official_site";

    public static final String COMPLIANCE_PENDING = "pending";
    public static final String COMPLIANCE_PASSED = "passed";
    public static final String COMPLIANCE_FAILED = "failed";
    public static final String COMPLIANCE_DISCARDED = "discarded_compliance_failed";

    public static final String REVIEW_NOT_REQUIRED = "not_required";
    public static final String REVIEW_PENDING = "pending";
    public static final String REVIEW_PASSED = "passed";
    public static final String REVIEW_REJECTED = "rejected";

    public static final int MAX_COMPLIANCE_GENERATION_ATTEMPTS = 3;
    public static final int RECENT_HISTORY_LIMIT = 8;

    public static final List<String> STRUCTURE_SKELETONS = List.of(
            "concept_distinction",
            "misconception_correction",
            "medical_decision",
            "faq",
            "audience_focus"
    );
    public static final List<String> FOCUSES = List.of(
            "principle",
            "misconception",
            "risk",
            "rational_decision"
    );
    public static final Map<String, Set<String>> ALLOWED_FOCUSES_BY_SKELETON = Map.of(
            "concept_distinction", Set.of("principle", "misconception"),
            "misconception_correction", Set.of("misconception", "risk"),
            "medical_decision", Set.of("rational_decision", "risk"),
            "faq", Set.of("misconception", "rational_decision"),
            "audience_focus", Set.of("risk", "rational_decision")
    );

    private MedicalArticleConstants() {
    }
}
