package com.huanjing.geo.module.project.service;

import java.util.Map;
import java.util.Set;

public final class ProjectFlowPolicy {

    private ProjectFlowPolicy() {
    }

    public static final Set<String> STATUS_SET = Set.of("pending_start", "active", "paused", "expired");
    public static final Set<String> STAGE_SET = Set.of(
            "pending_start",
            "collecting_materials",
            "baseline_diagnosis",
            "executing",
            "needs_renewal",
            "high_risk",
            "dispute_handling",
            "completed"
    );
    public static final Map<String, Set<String>> STATUS_TRANSITION = Map.of(
            "pending_start", Set.of("active"),
            "active", Set.of("paused", "expired"),
            "paused", Set.of("active", "expired"),
            "expired", Set.<String>of()
    );
}
