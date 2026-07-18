package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.system.modeldiagnostic.audit.ModelDiagnosticOperationAuditService;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermit;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermitAccessException;
import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticExecutionCoordinator {

    public static final Duration END_TO_END_DEADLINE = Duration.ofSeconds(180);

    private final ModelDiagnosticPlatformResolver platformResolver;
    private final ModelDiagnosticRunPersistenceService persistenceService;
    private final ModelDiagnosticGateway gateway;
    private final ModelDiagnosticEvaluator evaluator;
    private final ModelDiagnosticPermitService permitService;
    private final ModelDiagnosticOperationAuditService operationAuditService;

    public AiModelDiagnosticRun execute(ModelDiagnosticExecutionCommand command) {
        AiModelDiagnosticRun replay = persistenceService.findIdempotentReplay(command);
        if (replay != null) {
            return replay;
        }
        ModelDiagnosticPlatformProfile platform = platformResolver.resolve(
                command.platformConfigId(), command.diagnosticMode(), command.modelTier());
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime deadlineAt = startedAt.plus(END_TO_END_DEADLINE);
        ModelDiagnosticBeginResult begin = persistenceService.begin(
                command, platform, startedAt, deadlineAt);
        if (begin.reused()) {
            return begin.run();
        }

        ModelDiagnosticPermit permit;
        try {
            permit = permitService.tryAcquire(command.operatorId(), deadlineAt);
        } catch (ModelDiagnosticPermitAccessException ex) {
            return audit(persistenceService.rejectRunningBeforeExecution(
                    begin.run().getId(),
                    permitFailureCategory(ex),
                    ex.rejectionCode(), ex.getMessage()));
        }
        if (permit == null) {
            return audit(persistenceService.rejectRunningBeforeExecution(
                    begin.run().getId(),
                    ErrorCategory.RATE_LIMIT,
                    ModelDiagnosticPermitService.BUSY_CODE,
                    "Diagnostic concurrency is busy; retry explicitly"));
        }

        try (permit) {
            ModelDiagnosticPreparedExecution prepared =
                    persistenceService.prepareMessagesBeforeExecution(begin.run().getId());
            if (!prepared.executable()) {
                return audit(new ModelDiagnosticTransitionResult(
                        prepared.run(), prepared.transitionedByCaller()));
            }
            return audit(executeWithPermit(command, platform, prepared, deadlineAt));
        }
    }

    private ModelDiagnosticTransitionResult executeWithPermit(
            ModelDiagnosticExecutionCommand command,
            ModelDiagnosticPlatformProfile platform,
            ModelDiagnosticPreparedExecution prepared,
            LocalDateTime deadlineAt) {
        ModelDiagnosticProviderResult result;
        ModelDiagnosticEvaluation evaluation;
        try {
            result = gateway.execute(
                    new ModelDiagnosticProviderRequest(
                            platform, command.systemPrompt(), prepared.messages(), deadlineAt));
            evaluation = evaluator.evaluate(
                    command.diagnosticMode(), result);
        } catch (ModelDiagnosticExecutionException ex) {
            return persistenceService.finishFailure(
                    prepared.run().getId(), command.diagnosticMode(), ex);
        } catch (RuntimeException ex) {
            ModelDiagnosticExecutionException failure = new ModelDiagnosticExecutionException(
                    ErrorCategory.INTERNAL_ERROR, null,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    null, null, ex);
            return persistenceService.finishFailure(
                    prepared.run().getId(), command.diagnosticMode(), failure);
        }
        // A terminal database write failure must leave the run RUNNING for recovery; do not
        // translate it into a second, misleading FAILED write.
        return persistenceService.finishSuccess(
                prepared.run().getId(), result, evaluation);
    }

    private ErrorCategory permitFailureCategory(ModelDiagnosticPermitAccessException failure) {
        return ModelDiagnosticPermitService.INTERRUPTED_CODE.equals(failure.rejectionCode())
                ? ErrorCategory.WORKER_INTERRUPTED : ErrorCategory.INTERNAL_ERROR;
    }

    private AiModelDiagnosticRun audit(ModelDiagnosticTransitionResult result) {
        if (result.transitionedByCaller()) {
            operationAuditService.recordTerminal(result.run());
        }
        return result.run();
    }
}
