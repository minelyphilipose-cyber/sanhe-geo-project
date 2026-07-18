package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermit;
import com.huanjing.geo.module.system.modeldiagnostic.audit.ModelDiagnosticOperationAuditService;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermitAccessException;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermitService;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.recovery.ModelDiagnosticStateRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticExecutionCoordinatorTest {

    private final ModelDiagnosticPlatformResolver platformResolver = mock(ModelDiagnosticPlatformResolver.class);
    private final ModelDiagnosticRunPersistenceService persistenceService = mock(ModelDiagnosticRunPersistenceService.class);
    private final ModelDiagnosticGateway gateway = mock(ModelDiagnosticGateway.class);
    private final ModelDiagnosticEvaluator evaluator = mock(ModelDiagnosticEvaluator.class);
    private final ModelDiagnosticPermitService permitService = mock(ModelDiagnosticPermitService.class);
    private final ModelDiagnosticOperationAuditService operationAuditService =
            mock(ModelDiagnosticOperationAuditService.class);
    private final ModelDiagnosticPermit permit = mock(ModelDiagnosticPermit.class);
    private ModelDiagnosticExecutionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ModelDiagnosticExecutionCoordinator(
                platformResolver, persistenceService, gateway, evaluator, permitService,
                operationAuditService);
        when(permitService.tryAcquire(anyLong(), any())).thenReturn(permit);
        when(persistenceService.prepareMessagesBeforeExecution(anyLong()))
                .thenAnswer(invocation -> {
                    AiModelDiagnosticRun run = new AiModelDiagnosticRun();
                    run.setId(invocation.getArgument(0));
                    run.setStatus("RUNNING");
                    return new ModelDiagnosticPreparedExecution(
                            run, List.of(new WebSearchMessage("user", "question")), true);
                });
    }

    @Test
    void idempotentExistingRunDoesNotCallProviderAgain() {
        ModelDiagnosticExecutionCommand command = command();
        AiModelDiagnosticRun existing = new AiModelDiagnosticRun();
        existing.setId(8L);
        when(persistenceService.findIdempotentReplay(command)).thenReturn(existing);

        AiModelDiagnosticRun result = coordinator.execute(command);

        assertSame(existing, result);
        verify(platformResolver, never()).resolve(any(), any());
        verify(permitService, never()).tryAcquire(anyLong(), any());
        verify(persistenceService, never()).begin(any(), any(), any(), any());
        verify(gateway, never()).execute(any());
    }

    @Test
    void strictCredentialFailureAfterRunningCreationIsPersistedAsFailed() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(9L);
        AiModelDiagnosticRun failed = new AiModelDiagnosticRun();
        failed.setId(9L);
        failed.setStatus("FAILED");
        ModelDiagnosticExecutionException authentication = new ModelDiagnosticExecutionException(
                ErrorCategory.AUTHENTICATION, null, "credential missing", "{}", null, null);
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(
                        running, List.of(new WebSearchMessage("user", "question")), false));
        when(gateway.execute(any())).thenThrow(authentication);
        when(persistenceService.finishFailure(
                9L, ModelDiagnosticMode.BASIC_CHAT, authentication))
                .thenReturn(new ModelDiagnosticTransitionResult(failed, true));

        AiModelDiagnosticRun result = coordinator.execute(command);

        assertSame(failed, result);
        verify(persistenceService).finishFailure(
                9L, ModelDiagnosticMode.BASIC_CHAT, authentication);
        verify(operationAuditService).recordTerminal(failed);
        verify(permit).close();
    }

    @Test
    void successfulTerminalPersistenceFailureIsNotReclassifiedAsBusinessFailure() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(11L);
        ModelDiagnosticProviderResult providerResult = mock(ModelDiagnosticProviderResult.class);
        ModelDiagnosticEvaluation evaluationResult = mock(ModelDiagnosticEvaluation.class);
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(
                        running, List.of(new WebSearchMessage("user", "question")), false));
        when(gateway.execute(any())).thenReturn(providerResult);
        when(evaluator.evaluate(command.diagnosticMode(), providerResult)).thenReturn(evaluationResult);
        when(persistenceService.finishSuccess(11L, providerResult, evaluationResult))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> coordinator.execute(command));

        verify(persistenceService, never()).finishFailure(any(), any(), any());
        verify(permit).close();
    }

    @Test
    void busyPermitCreatesRejectedRunWithoutProviderCall() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(12L);
        AiModelDiagnosticRun rejected = new AiModelDiagnosticRun();
        rejected.setStatus("REJECTED");
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(
                        running, List.of(new WebSearchMessage("user", "question")), false));
        when(permitService.tryAcquire(eq(command.operatorId()), any())).thenReturn(null);
        when(persistenceService.rejectRunningBeforeExecution(
                eq(12L),
                eq(ErrorCategory.RATE_LIMIT),
                eq(ModelDiagnosticPermitService.BUSY_CODE), any()))
                .thenReturn(new ModelDiagnosticTransitionResult(rejected, true));

        AiModelDiagnosticRun result = coordinator.execute(command);

        assertSame(rejected, result);
        verify(persistenceService).begin(eq(command), eq(platform), any(), any());
        verify(gateway, never()).execute(any());
    }

    @Test
    void unavailablePermitStoreCreatesRejectedRunWithoutProviderCall() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(13L);
        AiModelDiagnosticRun rejected = new AiModelDiagnosticRun();
        rejected.setStatus("REJECTED");
        ModelDiagnosticPermitAccessException unavailable =
                new ModelDiagnosticPermitAccessException(
                        ModelDiagnosticPermitService.UNAVAILABLE_CODE,
                        "redis unavailable", new IllegalStateException("down"));
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(
                        running, List.of(new WebSearchMessage("user", "question")), false));
        when(permitService.tryAcquire(eq(command.operatorId()), any())).thenThrow(unavailable);
        when(persistenceService.rejectRunningBeforeExecution(
                eq(13L),
                eq(ErrorCategory.INTERNAL_ERROR),
                eq(ModelDiagnosticPermitService.UNAVAILABLE_CODE), any()))
                .thenReturn(new ModelDiagnosticTransitionResult(rejected, true));

        AiModelDiagnosticRun result = coordinator.execute(command);

        assertSame(rejected, result);
        verify(persistenceService).begin(eq(command), eq(platform), any(), any());
        verify(gateway, never()).execute(any());
    }

    @Test
    void completionDoesNotAuditWhenRecoveryAlreadyWonTerminalTransition() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(20L);
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(20L);
        abandoned.setStatus("ABANDONED");
        ModelDiagnosticProviderResult providerResult = mock(ModelDiagnosticProviderResult.class);
        ModelDiagnosticEvaluation evaluationResult = mock(ModelDiagnosticEvaluation.class);
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(running, List.of(), false));
        when(gateway.execute(any())).thenReturn(providerResult);
        when(evaluator.evaluate(command.diagnosticMode(), providerResult))
                .thenReturn(evaluationResult);
        when(persistenceService.finishSuccess(20L, providerResult, evaluationResult))
                .thenReturn(new ModelDiagnosticTransitionResult(abandoned, false));
        recoverFirst(abandoned);

        assertSame(abandoned, coordinator.execute(command));

        verify(operationAuditService, times(1)).recordTerminal(abandoned);
    }

    @Test
    void rejectionDoesNotAuditWhenRecoveryAlreadyWonTerminalTransition() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(21L);
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(21L);
        abandoned.setStatus("ABANDONED");
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(running, List.of(), false));
        when(permitService.tryAcquire(eq(command.operatorId()), any())).thenReturn(null);
        when(persistenceService.rejectRunningBeforeExecution(
                eq(21L), eq(ErrorCategory.RATE_LIMIT),
                eq(ModelDiagnosticPermitService.BUSY_CODE), any()))
                .thenReturn(new ModelDiagnosticTransitionResult(abandoned, false));
        recoverFirst(abandoned);

        assertSame(abandoned, coordinator.execute(command));

        verify(operationAuditService, times(1)).recordTerminal(abandoned);
        verify(gateway, never()).execute(any());
    }

    @Test
    void preparationDoesNotAuditWhenRecoveryAlreadyWonTerminalTransition() {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(22L);
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(22L);
        abandoned.setStatus("ABANDONED");
        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(running, List.of(), false));
        when(persistenceService.prepareMessagesBeforeExecution(22L))
                .thenReturn(new ModelDiagnosticPreparedExecution(
                        abandoned, List.of(), false, false));
        recoverFirst(abandoned);

        assertSame(abandoned, coordinator.execute(command));

        verify(operationAuditService, times(1)).recordTerminal(abandoned);
        verify(gateway, never()).execute(any());
    }

    @Test
    void concurrentReplayReturnsRunningPlaceholderBeforeBusinessPermit() throws Exception {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(14L);
        running.setStatus("RUNNING");
        AiModelDiagnosticRun succeeded = new AiModelDiagnosticRun();
        succeeded.setId(14L);
        succeeded.setStatus("SUCCEEDED");
        ModelDiagnosticProviderResult providerResult = mock(ModelDiagnosticProviderResult.class);
        ModelDiagnosticEvaluation evaluationResult = mock(ModelDiagnosticEvaluation.class);
        CountDownLatch firstPermitEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstPermit = new CountDownLatch(1);
        AtomicInteger beginCalls = new AtomicInteger();

        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenAnswer(invocation -> beginCalls.incrementAndGet() == 1
                        ? new ModelDiagnosticBeginResult(
                                running, List.of(new WebSearchMessage("user", "question")), false)
                        : new ModelDiagnosticBeginResult(running, List.of(), true));
        when(permitService.tryAcquire(eq(command.operatorId()), any()))
                .thenAnswer(invocation -> {
                    firstPermitEntered.countDown();
                    if (!releaseFirstPermit.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test permit was not released");
                    }
                    return permit;
                });
        when(gateway.execute(any())).thenReturn(providerResult);
        when(evaluator.evaluate(command.diagnosticMode(), providerResult))
                .thenReturn(evaluationResult);
        when(persistenceService.finishSuccess(14L, providerResult, evaluationResult))
                .thenReturn(new ModelDiagnosticTransitionResult(succeeded, true));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AiModelDiagnosticRun> first = executor.submit(() -> coordinator.execute(command));
            assertTrue(firstPermitEntered.await(2, TimeUnit.SECONDS));
            Future<AiModelDiagnosticRun> replay = executor.submit(() -> coordinator.execute(command));

            assertSame(running, replay.get(2, TimeUnit.SECONDS));
            releaseFirstPermit.countDown();
            assertSame(succeeded, first.get(2, TimeUnit.SECONDS));
        } finally {
            releaseFirstPermit.countDown();
            executor.shutdownNow();
        }

        verify(permitService, times(1)).tryAcquire(eq(command.operatorId()), any());
        verify(persistenceService, never()).rejectRunningBeforeExecution(
                anyLong(), any(), any(), any());
    }

    @Test
    void freeChatContextIsReloadedAfterPreviousTurnFinishesDuringPermitWait() throws Exception {
        ModelDiagnosticExecutionCommand command = command();
        ModelDiagnosticPlatformProfile platform = profile();
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(15L);
        running.setStatus("RUNNING");
        AiModelDiagnosticRun succeeded = new AiModelDiagnosticRun();
        succeeded.setId(15L);
        succeeded.setStatus("SUCCEEDED");
        List<WebSearchMessage> refreshedMessages = List.of(
                new WebSearchMessage("user", "previous question"),
                new WebSearchMessage("assistant", "previous answer"),
                new WebSearchMessage("user", "question"));
        ModelDiagnosticProviderResult providerResult = mock(ModelDiagnosticProviderResult.class);
        ModelDiagnosticEvaluation evaluationResult = mock(ModelDiagnosticEvaluation.class);
        CountDownLatch permitWaitStarted = new CountDownLatch(1);
        CountDownLatch previousTurnFinished = new CountDownLatch(1);

        when(platformResolver.resolve(command.platformConfigId(), command.diagnosticMode(), command.modelTier()))
                .thenReturn(platform);
        when(persistenceService.begin(eq(command), eq(platform), any(), any()))
                .thenReturn(new ModelDiagnosticBeginResult(running, List.of(), false));
        when(permitService.tryAcquire(eq(command.operatorId()), any()))
                .thenAnswer(invocation -> {
                    permitWaitStarted.countDown();
                    if (!previousTurnFinished.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("previous turn did not finish");
                    }
                    return permit;
                });
        when(persistenceService.prepareMessagesBeforeExecution(15L))
                .thenReturn(new ModelDiagnosticPreparedExecution(
                        running, refreshedMessages, true));
        when(gateway.execute(any())).thenReturn(providerResult);
        when(evaluator.evaluate(command.diagnosticMode(), providerResult))
                .thenReturn(evaluationResult);
        when(persistenceService.finishSuccess(15L, providerResult, evaluationResult))
                .thenReturn(new ModelDiagnosticTransitionResult(succeeded, true));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AiModelDiagnosticRun> current = executor.submit(() -> coordinator.execute(command));
            assertTrue(permitWaitStarted.await(2, TimeUnit.SECONDS));
            previousTurnFinished.countDown();
            assertSame(succeeded, current.get(2, TimeUnit.SECONDS));
        } finally {
            previousTurnFinished.countDown();
            executor.shutdownNow();
        }

        ArgumentCaptor<ModelDiagnosticProviderRequest> request =
                ArgumentCaptor.forClass(ModelDiagnosticProviderRequest.class);
        verify(gateway).execute(request.capture());
        assertEquals(refreshedMessages, request.getValue().messages());
    }

    private ModelDiagnosticExecutionCommand command() {
        return new ModelDiagnosticExecutionCommand(
                1L, UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "system", "question");
    }

    private void recoverFirst(AiModelDiagnosticRun abandoned) {
        AiModelDiagnosticRunMapper runMapper = mock(AiModelDiagnosticRunMapper.class);
        when(runMapper.selectExpiredRunningIds(1)).thenReturn(List.of(abandoned.getId()));
        when(runMapper.markAbandonedIfExpired(abandoned.getId())).thenReturn(1);
        when(runMapper.selectById(abandoned.getId())).thenReturn(abandoned);
        new ModelDiagnosticStateRecoveryService(runMapper, operationAuditService)
                .recoverExpiredBatch(1);
    }

    private ModelDiagnosticPlatformProfile profile() {
        return new ModelDiagnosticPlatformProfile(
                2L, "platform", "channel", "Platform", "BASIC_CHAT",
                IntegrationType.OPENAI_CHAT, "https://example.test/v1", "model", 1L,
                "{}", "{}", "hash", "env://TEST_KEY", null,
                3_000, 60_000);
    }
}
