package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataRetentionExecutionBudgetGateTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void trustedUsageIsTheOnlyProgressCharge() {
        DataRetentionExecutionBudgetGate gate = gate();
        ExecutionBudget budget = new ExecutionBudget(2, 20, Duration.ofMinutes(10));
        ExecutionProgress progress = new ExecutionProgress(0, 0, NOW.plusSeconds(60));

        ExecutionProgress charged = gate.afterMutation(budget, progress,
                new ExecutionBudgetUsage(1, 1, 7));

        assertEquals(1, charged.usedMutationTransactions());
        assertEquals(7, charged.usedCandidateItems());
    }

    @Test
    void budgetRejectsCandidateAndDeadlineBoundaries() {
        DataRetentionExecutionBudgetGate gate = gate();
        ExecutionBudget budget = new ExecutionBudget(1, 5, Duration.ofMinutes(10));
        assertThrows(DataRetentionExecutionException.class,
                () -> gate.beforeMutation(budget, new ExecutionProgress(0, 4, NOW.plusSeconds(1)),
                        new MutationEstimate(2)));
        assertThrows(DataRetentionExecutionException.class,
                () -> gate.beforeMutation(budget, new ExecutionProgress(0, 0, NOW),
                        new MutationEstimate(1)));
    }

    @Test
    void invalidLeaseTimeoutEnvelopeFailsClosed() {
        DataRetentionProperties properties = properties();
        properties.getScheduler().setLockTtlSeconds(100);
        assertThrows(DataRetentionExecutionException.class,
                () -> new DataRetentionExecutionBudgetGate(properties, Clock.fixed(NOW, ZoneOffset.UTC))
                        .validateConfiguration());
    }

    private DataRetentionExecutionBudgetGate gate() {
        return new DataRetentionExecutionBudgetGate(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DataRetentionProperties properties() {
        DataRetentionProperties value = new DataRetentionProperties();
        value.getScheduler().setLockTtlSeconds(7200);
        value.getExecute().setMaxRunSeconds(3600);
        value.getExecute().setMutationTimeoutSeconds(120);
        return value;
    }
}
