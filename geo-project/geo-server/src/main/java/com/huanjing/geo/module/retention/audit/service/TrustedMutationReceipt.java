package com.huanjing.geo.module.retention.audit.service;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;

import java.util.Objects;

public final class TrustedMutationReceipt {
    private final long runId;
    private final String operationId;
    private final long attemptId;
    private final String markerIdentity;
    private final ExecutionBudgetUsage usage;

    TrustedMutationReceipt(long runId, String operationId, long attemptId,
                           String markerIdentity, ExecutionBudgetUsage usage) {
        this.runId = runId;
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.attemptId = attemptId;
        this.markerIdentity = Objects.requireNonNull(markerIdentity, "markerIdentity");
        this.usage = Objects.requireNonNull(usage, "usage");
    }

    public long runId() { return runId; }
    public String operationId() { return operationId; }
    public long attemptId() { return attemptId; }
    public String markerIdentity() { return markerIdentity; }
    public ExecutionBudgetUsage usage() { return usage; }
}
