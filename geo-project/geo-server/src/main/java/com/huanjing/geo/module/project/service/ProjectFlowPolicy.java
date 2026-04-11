package com.huanjing.geo.module.project.service;

import java.util.Map;
import java.util.Set;

public final class ProjectFlowPolicy {

    private ProjectFlowPolicy() {
    }

    public static final Set<String> STATUS_SET = Set.of("draft", "active", "paused", "dispute", "completed", "archived");
    public static final Set<String> STAGE_SET = Set.of(
            "pending_start",
            "collecting_materials",
            "baseline_diagnosis",
            "building_questions",
            "executing",
            "biweekly_feedback",
            "monthly_report",
            "quarterly_report",
            "needs_renewal",
            "high_risk",
            "dispute_handling",
            "completed"
    );
    public static final Set<String> DRAFT_ALLOWED_STAGES = Set.of("pending_start", "collecting_materials");

    public static final Map<String, Set<String>> STATUS_TRANSITION = Map.of(
            "draft", Set.of("active", "archived"),
            "active", Set.of("paused", "dispute", "completed", "archived"),
            "paused", Set.of("active", "dispute", "archived"),
            "dispute", Set.of("active", "paused", "archived"),
            "completed", Set.of("archived"),
            "archived", Set.of()
    );
}
