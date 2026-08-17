package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ExecutionBudgetUsage;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.service.TrustedMutationReceipt;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionMutationActual;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DefaultRetentionExecutionSessionTest {
    @Test
    void candidateMismatchRollsBackBeforeProgressCharge() throws Exception {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getScheduler().setExecuteEnabled(true);
        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        properties.getScheduler().setLockTtlSeconds(7200);
        DataRetentionExecutionBudgetGate budget = new DataRetentionExecutionBudgetGate(
                properties, Clock.fixed(now, ZoneOffset.UTC));
        ExecutionBudget limit = new ExecutionBudget(2, 20, Duration.ofMinutes(10));
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "website_published_content", 1, new byte[32], "website-published-v1");
        RetentionRunContext run = new RetentionRunContext(10, "00000000-0000-4000-8000-000000000001",
                "manual", "execute", contract, "v1", "v1", RunReplayStatus.CREATED, "running", now);
        RetentionExecuteCommand command = new RetentionExecuteCommand(run.triggerId(),
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL, 1, "test", "a", null,
                contract, Map.of(), null, null, limit, null);
        DataRetentionExecuteLease.Lease lease = mock(DataRetentionExecuteLease.Lease.class);
        org.mockito.Mockito.when(lease.isKnownHeld()).thenReturn(true);
        DataRetentionDynamicSwitchGate dynamic = mock(DataRetentionDynamicSwitchGate.class);
        DataRetentionP0AuditService audit = mock(DataRetentionP0AuditService.class);
        DefaultRetentionExecutionSession session = new DefaultRetentionExecutionSession(run, command, lease,
                new DataRetentionStaticExecuteGate(properties), dynamic, budget, audit,
                transactionManager(), 30,
                new ExecutionProgress(0, 0, now.plusSeconds(60)), Clock.fixed(now, ZoneOffset.UTC));

        TrustedMutationReceipt receipt = receipt(10, "op", 7);
        assertThrows(DataRetentionExecutionException.class, () -> session.inMutationTransaction(
                new MutationEstimate(7), () -> new PendingMutation<>("value",
                        new RetentionMutationActual("op", 1, 1, 0, 0), receipt)));
        assertEquals(0, session.progress().usedCandidateItems());
    }

    private PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            public void commit(TransactionStatus status) { }
            public void rollback(TransactionStatus status) { }
        };
    }

    private TrustedMutationReceipt receipt(long runId, String operationId, long candidates) throws Exception {
        Constructor<TrustedMutationReceipt> constructor = TrustedMutationReceipt.class.getDeclaredConstructor(
                long.class, String.class, long.class, String.class, ExecutionBudgetUsage.class);
        constructor.setAccessible(true);
        return constructor.newInstance(runId, operationId, 20L, "marker",
                new ExecutionBudgetUsage(1, 1, candidates));
    }
}
