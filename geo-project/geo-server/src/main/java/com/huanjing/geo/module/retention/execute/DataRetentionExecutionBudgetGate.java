package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class DataRetentionExecutionBudgetGate {
    private final DataRetentionProperties properties;
    private final Clock clock;

    @Autowired
    public DataRetentionExecutionBudgetGate(DataRetentionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DataRetentionExecutionBudgetGate(DataRetentionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ExecutionBudget serverBudget() {
        DataRetentionProperties.Execute execute = properties.getExecute();
        return new ExecutionBudget(execute.getMaxMutationTransactions(), execute.getMaxCandidateItems(),
                Duration.ofSeconds(execute.getMaxRunSeconds()));
    }

    public void validateConfiguration() {
        DataRetentionProperties.Execute execute = properties.getExecute();
        long ttl = properties.getScheduler().getLockTtlSeconds();
        long required;
        try {
            required = Math.addExact(execute.getMaxRunSeconds(),
                    Math.multiplyExact(2L, execute.getMutationTimeoutSeconds()));
        } catch (ArithmeticException error) {
            throw invalidConfiguration(error);
        }
        if (execute.getMutationTimeoutSeconds() < 1
                || execute.getMaxRunSeconds() < execute.getMutationTimeoutSeconds()
                || ttl < required
                || execute.getMaxDeferredRetriesPerDomain() < 1
                || execute.getMaxDeferredRetriesPerDomain() > 100) {
            throw invalidConfiguration(null);
        }
    }

    public void preflight(ExecutionBudget budget) {
        validateConfiguration();
        if (budget == null) throw invalidConfiguration(null);
    }

    public void beforeMutation(ExecutionBudget budget, ExecutionProgress progress, MutationEstimate estimate) {
        if (!clock.instant().isBefore(progress.deadline())) exhausted("deadline");
        if (progress.usedMutationTransactions() >= budget.maxMutationTransactions()) exhausted("transaction");
        try {
            long projected = Math.addExact(progress.usedCandidateItems(), estimate.candidateCount());
            if (projected > budget.maxCandidateItems()) exhausted("candidate");
        } catch (ArithmeticException error) {
            exhausted("candidate overflow");
        }
    }

    public void validateUsage(ExecutionBudget budget, ExecutionProgress progress, ExecutionBudgetUsage usage) {
        try {
            long transactions = Math.addExact(progress.usedMutationTransactions(), usage.committedTransactionCount());
            long candidates = Math.addExact(progress.usedCandidateItems(), usage.chargedCandidateItems());
            if (transactions > budget.maxMutationTransactions() || candidates > budget.maxCandidateItems()) {
                exhausted("trusted usage");
            }
        } catch (ArithmeticException error) {
            exhausted("trusted usage overflow");
        }
    }

    public ExecutionProgress afterMutation(ExecutionBudget budget,
                                           ExecutionProgress progress,
                                           ExecutionBudgetUsage usage) {
        validateUsage(budget, progress, usage);
        ExecutionProgress charged = progress.charge(usage);
        return charged;
    }

    public ExecutionProgress initial(Instant startedAt, ExecutionBudget budget) {
        return new ExecutionProgress(0, 0, startedAt.plus(budget.maxRunDuration()));
    }

    private void exhausted(String dimension) {
        throw new DataRetentionExecutionException(409, "EXECUTE_BUDGET_EXHAUSTED",
                "Data retention execution budget is exhausted: " + dimension);
    }

    private DataRetentionExecutionException invalidConfiguration(Throwable cause) {
        String message = "Data retention execution configuration is invalid";
        return cause == null
                ? new DataRetentionExecutionException(503, "EXECUTE_CONFIGURATION_INVALID", message)
                : new DataRetentionExecutionException(503, "EXECUTE_CONFIGURATION_INVALID", message, cause);
    }
}
