package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.service.TrustedTerminalUsageReceipt;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchGate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.CommittedMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class DefaultRetentionExecutionSession implements RetentionExecutionSession {
    private final RetentionRunContext run;
    private final RetentionExecuteCommand command;
    private final DataRetentionExecuteLease.Lease lease;
    private final DataRetentionStaticExecuteGate staticGate;
    private final DataRetentionDynamicSwitchGate dynamicGate;
    private final DataRetentionExecutionBudgetGate budgetGate;
    private final DataRetentionP0AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final AtomicReference<ExecutionProgress> progress;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Set<String> consumedTerminalReceipts = new HashSet<>();

    DefaultRetentionExecutionSession(RetentionRunContext run,
                                     RetentionExecuteCommand command,
                                     DataRetentionExecuteLease.Lease lease,
                                     DataRetentionStaticExecuteGate staticGate,
                                     DataRetentionDynamicSwitchGate dynamicGate,
                                     DataRetentionExecutionBudgetGate budgetGate,
                                     DataRetentionP0AuditService audit,
                                     PlatformTransactionManager transactionManager,
                                     int timeoutSeconds,
                                     ExecutionProgress progress,
                                     Clock clock) {
        this.run = run;
        this.command = command;
        this.lease = lease;
        this.staticGate = staticGate;
        this.dynamicGate = dynamicGate;
        this.budgetGate = budgetGate;
        this.audit = audit;
        this.clock = clock;
        this.progress = new AtomicReference<>(progress);
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setReadOnly(false);
        this.transaction.setTimeout(timeoutSeconds);
    }

    @Override public RetentionRunContext run() { return run; }
    @Override public RetentionExecuteCommand command() { return command; }
    @Override public ExecutionProgress progress() { return progress.get(); }
    @Override public DataRetentionP0AuditService audit() { return audit; }
    @Override public boolean isStopped() { return stopped.get() || !lease.isKnownHeld(); }

    @Override
    public synchronized <T> CommittedMutation<T> inMutationTransaction(MutationEstimate estimate,
                                                                       RetentionMutationWork<T> work) {
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(work, "work");
        if (isStopped()) throw stopped("session is stopped");
        checkpoint(estimate);
        PendingMutation<T> pending;
        AtomicBoolean callbackValidated = new AtomicBoolean(false);
        try {
            pending = transaction.execute(status -> {
                PendingMutation<T> validated = validatePending(work.execute(), estimate);
                callbackValidated.set(true);
                return validated;
            });
        } catch (RuntimeException error) {
            if (callbackValidated.get()) {
                stopped.set(true);
                throw new DataRetentionExecutionException(503, "MUTATION_OUTCOME_UNKNOWN",
                        "Mutation transaction commit outcome is unknown", error);
            }
            throw error;
        }
        if (pending == null) throw new DataRetentionExecutionException(503, "MUTATION_OUTCOME_UNKNOWN",
                "Mutation transaction returned no committed value");
        ExecutionProgress charged = budgetGate.afterMutation(command.budget(), progress.get(),
                pending.trustedReceipt().usage());
        progress.set(charged);
        return new CommittedMutation<>(pending.value(), pending.reportedActual(), pending.trustedReceipt(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public synchronized void consumeTerminalUsage(TrustedTerminalUsageReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (isStopped()) throw stopped("session is stopped");
        String identity = receipt.receiptType() + ":" + receipt.attemptId() + ":"
                + receipt.persistedFactDigest();
        try {
            lease.ensureHeld();
            staticGate.requireEnabled(command.domain(), command.source());
            dynamicGate.requireEnabled(command.domain());
            audit.validateTrustedTerminalUsageReceipt(receipt);
            if (consumedTerminalReceipts.contains(identity)) {
                return;
            }
            budgetGate.validateUsage(command.budget(), progress.get(), receipt.usage());
            progress.set(budgetGate.afterMutation(command.budget(), progress.get(), receipt.usage()));
            consumedTerminalReceipts.add(identity);
            lease.ensureHeld();
        } catch (RuntimeException error) {
            stopped.set(true);
            throw new DataRetentionExecutionException(503, "WEBSITE_TERMINAL_USAGE_RECEIPT_CONFLICT",
                    "Trusted terminal usage receipt validation failed", error);
        }
    }

    private void checkpoint(MutationEstimate estimate) {
        try {
            lease.ensureHeld();
            staticGate.requireEnabled(command.domain(), command.source());
            dynamicGate.requireEnabled(command.domain());
            budgetGate.beforeMutation(command.budget(), progress.get(), estimate);
            lease.ensureHeld();
        } catch (RuntimeException error) {
            stopped.set(true);
            throw error;
        }
    }

    private <T> PendingMutation<T> validatePending(PendingMutation<T> pending, MutationEstimate estimate) {
        if (pending == null || pending.reportedActual() == null || pending.trustedReceipt() == null
                || pending.trustedReceipt().usage() == null) mismatch("mutation result is incomplete");
        if (pending.trustedReceipt().runId() != run.runId()
                || !Objects.equals(pending.reportedActual().operationId(), pending.trustedReceipt().operationId())
                || pending.reportedActual().candidateCount()
                != pending.trustedReceipt().usage().chargedCandidateItems()
                || pending.trustedReceipt().usage().committedTransactionCount() != 1
                || pending.trustedReceipt().usage().chargedCandidateItems() > estimate.candidateCount()) {
            mismatch("reported mutation counts do not match trusted marker usage");
        }
        budgetGate.validateUsage(command.budget(), progress.get(), pending.trustedReceipt().usage());
        audit.validateAndConsumeTrustedReceipt(pending.trustedReceipt());
        return pending;
    }

    private static void mismatch(String message) {
        throw new DataRetentionExecutionException(503, "MUTATION_USAGE_MISMATCH", message);
    }

    private DataRetentionExecutionException stopped(String message) {
        stopped.set(true);
        return new DataRetentionExecutionException(503, "EXECUTE_LEASE_LOST", message);
    }
}
