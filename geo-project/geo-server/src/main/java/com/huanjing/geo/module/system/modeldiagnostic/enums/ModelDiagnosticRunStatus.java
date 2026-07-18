package com.huanjing.geo.module.system.modeldiagnostic.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ModelDiagnosticRunStatus {
    RUNNING(false),
    SUCCEEDED(true),
    FAILED(true),
    REJECTED(true),
    ABANDONED(true);

    private static final Set<ModelDiagnosticRunStatus> RUNNING_TARGETS =
            EnumSet.of(SUCCEEDED, FAILED, REJECTED, ABANDONED);

    private final boolean terminal;

    ModelDiagnosticRunStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean terminal() {
        return terminal;
    }

    public boolean canTransitionTo(ModelDiagnosticRunStatus target) {
        return this == RUNNING && target != null && RUNNING_TARGETS.contains(target);
    }
}
