package com.huanjing.geo.module.retention.audit.service;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;

import java.util.Objects;

public final class TrustedTerminalUsageReceipt {
    public static final String WEBSITE_LEAF_QUARANTINE_V1 = "WEBSITE_LEAF_QUARANTINE_V1";

    private final String receiptType;
    private final long runId;
    private final String operationId;
    private final long attemptId;
    private final String terminalStatus;
    private final ExecutionBudgetUsage usage;
    private final String persistedFactDigest;

    TrustedTerminalUsageReceipt(String receiptType,
                                long runId,
                                String operationId,
                                long attemptId,
                                String terminalStatus,
                                ExecutionBudgetUsage usage,
                                String persistedFactDigest) {
        this.receiptType = Objects.requireNonNull(receiptType, "receiptType");
        this.runId = runId;
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.attemptId = attemptId;
        this.terminalStatus = Objects.requireNonNull(terminalStatus, "terminalStatus");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.persistedFactDigest = Objects.requireNonNull(persistedFactDigest, "persistedFactDigest");
    }

    public String receiptType() { return receiptType; }
    public long runId() { return runId; }
    public String operationId() { return operationId; }
    public long attemptId() { return attemptId; }
    public String terminalStatus() { return terminalStatus; }
    public ExecutionBudgetUsage usage() { return usage; }
    public String persistedFactDigest() { return persistedFactDigest; }
}
