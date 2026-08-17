package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.DeferredRetryOperation;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataRetentionPollCommandFactoryTest {
    private DataRetentionExecuteCommandFactory factory;

    @BeforeEach
    void setUp() {
        DataRetentionExecutionBudgetGate budget = mock(DataRetentionExecutionBudgetGate.class);
        when(budget.serverBudget()).thenReturn(new ExecutionBudget(20, 100, Duration.ofMinutes(10)));
        factory = new DataRetentionExecuteCommandFactory(new DataRetentionCanonicalEncoder(), budget,
                new DataRetentionProperties(),
                Clock.fixed(Instant.parse("2026-08-14T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void persistsRequestedAndEffectiveNullDateSemantics() {
        PollRetentionDryRunRequest request = request();
        request.setQuestionTier(" a_1 ");

        var command = factory.manualPoll(request, 7);

        assertNull(command.retentionWindowStart());
        assertEquals(LocalDate.of(2026, 7, 31), command.retentionWindowEnd());
        assertTrue(command.scope().containsKey("requestedStartDate"));
        assertTrue(command.scope().containsKey("requestedEndDate"));
        assertNull(command.scope().get("requestedStartDate"));
        assertNull(command.scope().get("requestedEndDate"));
        assertNull(command.scope().get("effectiveStartDate"));
        assertEquals("2026-07-31", command.scope().get("effectiveEndDate"));
        assertEquals("A_1", command.scope().get("questionTier"));
    }

    @Test
    void clampsEndAndRejectsEmptyRangeAfterClamp() {
        PollRetentionDryRunRequest request = request();
        request.setEndDate(LocalDate.of(2026, 8, 10));
        assertEquals(LocalDate.of(2026, 7, 31), factory.manualPoll(request, 7).retentionWindowEnd());

        request.setStartDate(LocalDate.of(2026, 8, 1));
        assertThrows(DataRetentionExecutionException.class, () -> factory.manualPoll(request, 7));
    }

    @Test
    void rejectsExecuteCursorHotWindowLimitAndInvalidTier() {
        PollRetentionDryRunRequest request = request();
        request.setHotRetentionDays(13);
        assertThrows(DataRetentionExecutionException.class, () -> factory.manualPoll(request, 7));
        request.setHotRetentionDays(14);
        request.setLimit(21);
        assertThrows(DataRetentionExecutionException.class, () -> factory.manualPoll(request, 7));
        request.setLimit(20);
        request.setCursorProjectId(1L);
        assertThrows(DataRetentionExecutionException.class, () -> factory.manualPoll(request, 7));
        request.setCursorProjectId(null);
        request.setQuestionTier("A-B");
        assertThrows(DataRetentionExecutionException.class, () -> factory.manualPoll(request, 7));
    }

    @Test
    void canaryRequiresExactlyOnePrimarySlice() {
        PollRetentionDryRunRequest request = request();
        request.setLimit(1);
        assertEquals(ExecutionSource.CANARY, factory.canaryPoll(request, 7).source());
        request.setLimit(2);
        assertThrows(DataRetentionExecutionException.class, () -> factory.canaryPoll(request, 7));
    }

    @Test
    void deferredPollRetryRestoresTrustedEffectiveDateWindow() {
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", 1, new byte[32], DataRetentionCanonicalEncoder.POLL_POLICY_VERSION);
        Map<String, Object> scope = Map.of(
                "effectiveConfig", new DataRetentionCanonicalEncoder().pollEffectiveConfig(14, 7),
                "requestLimit", 1,
                "effectiveStartDate", "2026-01-01",
                "effectiveEndDate", "2026-07-31");
        DeferredRetryOperation operation = new DeferredRetryOperation(
                "00000000-0000-0000-0000-000000000099", 11L, "poll_results", contract, scope);

        var command = factory.schedulerDeferredRetry(operation, 7L);

        assertEquals(LocalDate.of(2026, 1, 1), command.retentionWindowStart());
        assertEquals(LocalDate.of(2026, 7, 31), command.retentionWindowEnd());
        assertEquals(scope.get("effectiveStartDate"), command.scope().get("effectiveStartDate"));
        assertEquals(scope.get("effectiveEndDate"), command.scope().get("effectiveEndDate"));
    }

    @Test
    void deferredPollRetryRejectsMissingOrMalformedEffectiveDateEvidence() {
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", 1, new byte[32], DataRetentionCanonicalEncoder.POLL_POLICY_VERSION);
        DeferredRetryOperation missingEnd = new DeferredRetryOperation(
                "00000000-0000-0000-0000-000000000099", 11L, "poll_results", contract,
                Map.of("effectiveConfig", new DataRetentionCanonicalEncoder().pollEffectiveConfig(14, 7),
                        "requestLimit", 1));
        DeferredRetryOperation inverted = new DeferredRetryOperation(
                "00000000-0000-0000-0000-000000000100", 12L, "poll_results", contract,
                Map.of("effectiveConfig", new DataRetentionCanonicalEncoder().pollEffectiveConfig(14, 7),
                        "requestLimit", 1, "effectiveStartDate", "2026-08-01",
                        "effectiveEndDate", "2026-07-31"));

        DataRetentionExecutionException missing = assertThrows(DataRetentionExecutionException.class,
                () -> factory.schedulerDeferredRetry(missingEnd, 7L));
        DataRetentionExecutionException empty = assertThrows(DataRetentionExecutionException.class,
                () -> factory.schedulerDeferredRetry(inverted, 7L));

        assertEquals("DEFERRED_RETRY_EVIDENCE_INVALID", missing.resultCode());
        assertEquals("DEFERRED_RETRY_EVIDENCE_INVALID", empty.resultCode());
    }

    private PollRetentionDryRunRequest request() {
        PollRetentionDryRunRequest value = new PollRetentionDryRunRequest();
        value.setTriggerId("00000000-0000-0000-0000-000000000001");
        value.setHotRetentionDays(14);
        value.setStuckBatchSealDays(7);
        value.setLimit(20);
        value.setReason("test");
        value.setApprovalRef("approved-WP7");
        return value;
    }
}
