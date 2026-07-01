package com.huanjing.geo.module.project.service;

import java.util.Map;
import java.util.Set;

public final class ProjectFlowPolicy {

    private ProjectFlowPolicy() {
    }

    public static final String DRAFT = "draft";
    public static final String PENDING_START = "pending_start";
    public static final String SUBMITTED = "submitted";
    public static final String REJECTED = "rejected";
    public static final String APPROVED_PENDING_SETUP = "approved_pending_setup";
    public static final String SETUP_READY = "setup_ready";
    public static final String ACTIVE = "active";
    public static final String PAUSED = "paused";
    public static final String COMPLETED = "completed";
    public static final String ARCHIVED = "archived";
    public static final String CANCELLED = "cancelled";
    public static final String EXPIRED = "expired";

    public static final Set<String> STATUS_SET = Set.of(
            DRAFT,
            PENDING_START,
            SUBMITTED,
            REJECTED,
            APPROVED_PENDING_SETUP,
            SETUP_READY,
            ACTIVE,
            PAUSED,
            COMPLETED,
            ARCHIVED,
            CANCELLED,
            EXPIRED
    );
    public static final Set<String> EXTERNAL_STATUS_SET = Set.of(PENDING_START, ACTIVE, PAUSED, COMPLETED, ARCHIVED, EXPIRED);
    public static final Set<String> DELIVERY_PROGRESS_STATUS_SET = Set.of(
            PENDING_START,
            SUBMITTED,
            APPROVED_PENDING_SETUP,
            SETUP_READY,
            ACTIVE,
            PAUSED
    );
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
    public static final Map<String, Set<String>> STATUS_TRANSITION = Map.ofEntries(
            Map.entry(DRAFT, Set.of(SUBMITTED)),
            Map.entry(PENDING_START, Set.of(ACTIVE, SUBMITTED)),
            Map.entry(SUBMITTED, Set.of(DRAFT, REJECTED, APPROVED_PENDING_SETUP)),
            Map.entry(REJECTED, Set.of(DRAFT, SUBMITTED)),
            Map.entry(APPROVED_PENDING_SETUP, Set.of(SETUP_READY)),
            Map.entry(SETUP_READY, Set.of(ACTIVE)),
            Map.entry(ACTIVE, Set.of(PAUSED, COMPLETED, ARCHIVED, EXPIRED)),
            Map.entry(PAUSED, Set.of(ACTIVE, COMPLETED, ARCHIVED, EXPIRED)),
            Map.entry(COMPLETED, Set.of(ARCHIVED)),
            Map.entry(ARCHIVED, Set.<String>of()),
            Map.entry(CANCELLED, Set.<String>of()),
            Map.entry(EXPIRED, Set.<String>of())
    );

    public static boolean isExternalStatus(String status) {
        return EXTERNAL_STATUS_SET.contains(status);
    }
}
