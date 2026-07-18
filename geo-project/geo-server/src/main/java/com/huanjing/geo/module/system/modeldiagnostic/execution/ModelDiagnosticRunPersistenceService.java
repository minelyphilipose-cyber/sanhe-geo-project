package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticCapabilityStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticConclusion;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticResponseMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticRunStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticSessionStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticRunPersistenceService {

    static final int MAX_PROVIDER_TEXT_CHARACTERS = 30_000;

    private final AiModelDiagnosticSessionMapper sessionMapper;
    private final AiModelDiagnosticRunMapper runMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public AiModelDiagnosticRun findIdempotentReplay(ModelDiagnosticExecutionCommand command) {
        AiModelDiagnosticRun existing = runMapper.selectByIdempotencyKey(
                command.operatorId(), command.clientRequestId());
        return existing == null ? null : existing(existing, fingerprint(command)).run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticBeginResult begin(ModelDiagnosticExecutionCommand command,
                                            ModelDiagnosticPlatformProfile platform,
                                            LocalDateTime startedAt,
                                            LocalDateTime deadlineAt) {
        String fingerprint = fingerprint(command);
        AiModelDiagnosticRun existing = runMapper.selectByIdempotencyKey(
                command.operatorId(), command.clientRequestId());
        if (existing != null) {
            return existing(existing, fingerprint);
        }

        sessionMapper.insertActiveIfAbsent(command.operatorId(), command.sessionId(), startedAt);
        AiModelDiagnosticSession session = sessionMapper.selectOwnedForUpdate(
                command.operatorId(), command.sessionId());
        if (session == null) {
            throw new IllegalStateException("Unable to create or load owned diagnostic session");
        }
        if (!ModelDiagnosticSessionStatus.ACTIVE.name().equals(session.getStatus())) {
            throw new IllegalStateException("Diagnostic session is closed");
        }

        existing = runMapper.selectByIdempotencyKeyForUpdate(
                command.operatorId(), command.clientRequestId());
        if (existing != null) {
            return existing(existing, fingerprint);
        }

        int turnNo = session.getNextTurnNo();

        AiModelDiagnosticRun run = newRun(
                command, platform, session, turnNo, fingerprint, deadlineAt, List.of());
        run.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        run.setStartedAt(startedAt);
        insertAndConsume(run, session, turnNo, startedAt);
        return new ModelDiagnosticBeginResult(run, List.of(), false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticPreparedExecution prepareMessagesBeforeExecution(Long runId) {
        AiModelDiagnosticRun current = runMapper.selectByIdForUpdate(runId);
        if (current == null) {
            throw new IllegalStateException("Diagnostic run does not exist: " + runId);
        }
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticPreparedExecution(current, List.of(), false);
        }

        List<WebSearchMessage> messages = ModelDiagnosticTestMode.FREE_CHAT.name()
                .equals(current.getTestMode())
                ? successfulContext(
                        current.getSessionRecordId(), current.getTurnNo(),
                        current.getSystemPrompt(), current.getUserMessage())
                : new ArrayList<>();
        messages.add(new WebSearchMessage("user", current.getUserMessage()));
        String requestMessagesJson = writeJson(messages);
        if (runMapper.freezeRequestMessagesIfRunning(runId, requestMessagesJson) == 1) {
            return new ModelDiagnosticPreparedExecution(requireRun(runId), messages, true);
        }
        if (runMapper.markAbandonedIfExpired(runId) == 1) {
            return new ModelDiagnosticPreparedExecution(
                    requireRun(runId), List.of(), false, true);
        }
        current = requireRun(runId);
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticPreparedExecution(current, List.of(), false);
        }
        throw new IllegalStateException("Diagnostic message snapshot lost while run is still active");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticTransitionResult rejectRunningBeforeExecution(
            Long runId,
            ErrorCategory errorCategory,
            String errorCode,
            String errorMessage) {
        AiModelDiagnosticRun current = runMapper.selectByIdForUpdate(runId);
        if (current == null) {
            throw new IllegalStateException("Diagnostic run does not exist: " + runId);
        }
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticTransitionResult(current, false);
        }
        if (runMapper.rejectRunningBeforeExecution(
                runId, errorCategory.name(), errorCode, truncate(errorMessage, 2_000)) == 1) {
            return new ModelDiagnosticTransitionResult(requireRun(runId), true);
        }
        if (runMapper.markAbandonedIfExpired(runId) == 1) {
            return new ModelDiagnosticTransitionResult(requireRun(runId), true);
        }
        current = requireRun(runId);
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticTransitionResult(current, false);
        }
        throw new IllegalStateException("Diagnostic rejection lost while run is still active");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticTransitionResult finishSuccess(
            Long runId,
            ModelDiagnosticProviderResult result,
            ModelDiagnosticEvaluation evaluation) {
        AiModelDiagnosticRun terminal = new AiModelDiagnosticRun();
        terminal.setId(runId);
        terminal.setStatus(ModelDiagnosticRunStatus.SUCCEEDED.name());
        terminal.setConclusion(evaluation.conclusion().name());
        terminal.setConclusionReason(evaluation.reason());
        terminal.setAuthenticationStatus(evaluation.authentication().name());
        terminal.setGenerationStatus(evaluation.generation().name());
        terminal.setWebSearchStatus(evaluation.webSearch().name());
        terminal.setSourceParsingStatus(evaluation.sourceParsing().name());
        terminal.setCitationParsingStatus(evaluation.citationParsing().name());
        terminal.setEvaluatorVersion(ModelDiagnosticEvaluator.EVALUATOR_VERSION);
        terminal.setAnswer(result.answer());
        terminal.setProviderRequestId(result.providerRequestId());
        terminal.setResponseModelId(result.responseModelId());
        terminal.setHttpStatus(result.httpStatus());
        terminal.setSearchStatus(result.searchStatus().name());
        terminal.setSearchEvidenceJson(writeJson(result.searchEvidence()));
        terminal.setSourcesJson(writeJson(result.sources()));
        terminal.setCitationsJson(writeJson(result.citations()));
        terminal.setUsageJson(writeJson(result.usage()));
        terminal.setPromptTokens(evaluation.promptTokens());
        terminal.setCompletionTokens(evaluation.completionTokens());
        terminal.setTotalTokens(evaluation.totalTokens());
        terminal.setWebSearchCallCount(evaluation.webSearchCallCount());
        terminal.setSourceCount(evaluation.sourceCount());
        terminal.setValidSourceCount(evaluation.validSourceCount());
        terminal.setCitationCount(evaluation.citationCount());
        terminal.setValidCitationCount(evaluation.validCitationCount());
        terminal.setSanitizedRequest(result.sanitizedRequest());
        terminal.setSanitizedResponse(result.sanitizedResponse());
        return finishAndResolve(terminal);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelDiagnosticTransitionResult finishFailure(
            Long runId,
            ModelDiagnosticMode mode,
            ModelDiagnosticExecutionException failure) {
        AiModelDiagnosticRun terminal = new AiModelDiagnosticRun();
        terminal.setId(runId);
        terminal.setStatus(ModelDiagnosticRunStatus.FAILED.name());
        terminal.setConclusion(ModelDiagnosticConclusion.FAIL.name());
        terminal.setConclusionReason("Diagnostic execution failed: " + safeMessage(failure));
        terminal.setAuthenticationStatus(authenticationFailureStatus(failure));
        terminal.setGenerationStatus(null);
        terminal.setWebSearchStatus(notApplicableOutsideWebSearch(mode));
        terminal.setSourceParsingStatus(notApplicableOutsideWebSearch(mode));
        terminal.setCitationParsingStatus(notApplicableOutsideWebSearch(mode));
        terminal.setEvaluatorVersion(ModelDiagnosticEvaluator.EVALUATOR_VERSION);
        terminal.setHttpStatus(failure.httpStatus());
        terminal.setSanitizedRequest(failure.sanitizedRequest());
        terminal.setSanitizedResponse(failure.sanitizedResponse());
        terminal.setErrorCategory(failure.category().name());
        terminal.setErrorCode(failure.httpStatus() == null
                ? failure.category().name() : "HTTP_" + failure.httpStatus());
        terminal.setErrorMessage(truncate(safeMessage(failure), 2_000));
        return finishAndResolve(terminal);
    }

    public String fingerprint(ModelDiagnosticExecutionCommand command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("sessionId", command.sessionId());
        canonical.put("platformConfigId", command.platformConfigId());
        canonical.put("modelTier", command.modelTier().name());
        canonical.put("diagnosticMode", command.diagnosticMode().name());
        canonical.put("testMode", command.testMode().name());
        canonical.put("probeCode", normalizeText(command.probeCode()));
        if (command.testMode() == ModelDiagnosticTestMode.FREE_CHAT) {
            canonical.put("systemPrompt", normalizeText(command.systemPrompt()));
            canonical.put("userMessage", normalizeText(command.resolvedUserMessage()));
        } else if (command.testMode() == ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE) {
            canonical.put("inputMode", command.inputMode().name());
            if (command.inputMode() == ModelDiagnosticInputMode.USER_REQUIRED) {
                canonical.put("clientUserMessage", normalizeText(command.clientUserMessage()));
            }
        }
        return sha256(writeJson(canonical));
    }

    private ModelDiagnosticBeginResult existing(AiModelDiagnosticRun run, String fingerprint) {
        if (!fingerprint.equals(run.getRequestFingerprint())) {
            throw new ModelDiagnosticIdempotencyConflictException();
        }
        return new ModelDiagnosticBeginResult(run, List.of(), true);
    }

    private List<WebSearchMessage> successfulContext(Long sessionRecordId,
                                                     Integer beforeTurnNo,
                                                     String systemPrompt,
                                                     String currentUserMessage) {
        List<AiModelDiagnosticRun> recent = runMapper
                .selectRecentSuccessfulFreeChatContext(sessionRecordId, beforeTurnNo);
        int remainingCharacters = MAX_PROVIDER_TEXT_CHARACTERS
                - textLength(systemPrompt) - textLength(currentUserMessage);
        List<AiModelDiagnosticRun> selectedNewestFirst = new ArrayList<>();
        for (AiModelDiagnosticRun previous : recent) {
            int pairCharacters = textLength(previous.getUserMessage())
                    + textLength(previous.getAnswer());
            if (pairCharacters > remainingCharacters) {
                break;
            }
            selectedNewestFirst.add(previous);
            remainingCharacters -= pairCharacters;
        }
        List<WebSearchMessage> messages = new ArrayList<>();
        for (int index = selectedNewestFirst.size() - 1; index >= 0; index--) {
            AiModelDiagnosticRun previous = selectedNewestFirst.get(index);
            messages.add(new WebSearchMessage("user", previous.getUserMessage()));
            messages.add(new WebSearchMessage("assistant", previous.getAnswer()));
        }
        return messages;
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    private void applyPlatform(AiModelDiagnosticRun run, ModelDiagnosticPlatformProfile platform) {
        run.setPlatformConfigId(platform.platformConfigId());
        run.setPlatformCode(platform.platformCode());
        run.setChannelCode(platform.channelCode());
        run.setPlatformName(platform.platformName());
        run.setUsageScene(platform.usageScene());
        run.setIntegrationType(platform.integrationType().name());
        run.setConfigVersion(platform.configVersion());
        run.setConfigSnapshotJson(platform.configSnapshotJson());
        run.setConfigSnapshotHash(platform.configSnapshotHash());
        run.setEndpointUrl(platform.endpointUrl());
    }

    private AiModelDiagnosticRun newRun(ModelDiagnosticExecutionCommand command,
                                        ModelDiagnosticPlatformProfile platform,
                                        AiModelDiagnosticSession session,
                                        int turnNo,
                                        String fingerprint,
                                        LocalDateTime deadlineAt,
                                        List<WebSearchMessage> messages) {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setSessionRecordId(session.getId());
        run.setSessionId(command.sessionId());
        run.setTurnNo(turnNo);
        run.setOperatorId(command.operatorId());
        run.setClientRequestId(command.clientRequestId());
        run.setRequestFingerprint(fingerprint);
        applyPlatform(run, platform);
        run.setDiagnosticMode(command.diagnosticMode().name());
        run.setTestMode(command.testMode().name());
        run.setResponseMode(ModelDiagnosticResponseMode.SYNC.name());
        run.setProbeCode(command.probeCode());
        run.setProbeVersion(command.probeVersion());
        run.setTemplateVersion(command.templateVersion());
        run.setUserMessage(command.resolvedUserMessage());
        run.setSystemPrompt(command.systemPrompt());
        run.setRequestMessagesJson(writeJson(messages));
        run.setRequestedModelId(platform.requestedModelId());
        run.setDeadlineAt(deadlineAt);
        run.setVersion(0L);
        return run;
    }

    private void insertAndConsume(AiModelDiagnosticRun run,
                                  AiModelDiagnosticSession session,
                                  int turnNo,
                                  LocalDateTime runAt) {
        if (runMapper.insert(run) != 1) {
            throw new IllegalStateException("Failed to create diagnostic run");
        }
        if (sessionMapper.consumeTurn(session.getId(), turnNo, runAt) != 1) {
            throw new IllegalStateException("Failed to allocate diagnostic turn");
        }
    }

    private ModelDiagnosticTransitionResult finishAndResolve(
            AiModelDiagnosticRun terminal) {
        AiModelDiagnosticRun current = runMapper.selectByIdForUpdate(terminal.getId());
        if (current == null) {
            throw new IllegalStateException("Diagnostic run does not exist: " + terminal.getId());
        }
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticTransitionResult(current, false);
        }
        if (runMapper.finishRunning(terminal) == 1) {
            return new ModelDiagnosticTransitionResult(
                    requireRun(terminal.getId()), true);
        }
        if (runMapper.markAbandonedIfExpired(current.getId()) == 1) {
            return new ModelDiagnosticTransitionResult(
                    requireRun(current.getId()), true);
        }
        current = requireRun(current.getId());
        if (!ModelDiagnosticRunStatus.RUNNING.name().equals(current.getStatus())) {
            return new ModelDiagnosticTransitionResult(current, false);
        }
        throw new IllegalStateException("Diagnostic terminal update lost while run is still active");
    }

    private AiModelDiagnosticRun requireRun(Long runId) {
        AiModelDiagnosticRun run = runMapper.selectByIdForUpdate(runId);
        if (run == null) {
            throw new IllegalStateException("Diagnostic run does not exist: " + runId);
        }
        return run;
    }

    private String authenticationFailureStatus(ModelDiagnosticExecutionException failure) {
        if (failure.category() == ErrorCategory.AUTHENTICATION
                || failure.category() == ErrorCategory.PERMISSION) {
            return ModelDiagnosticCapabilityStatus.FAIL.name();
        }
        Integer status = failure.httpStatus();
        return status != null && status >= 200 && status < 300
                ? ModelDiagnosticCapabilityStatus.PASS.name() : null;
    }

    private String notApplicableOutsideWebSearch(ModelDiagnosticMode mode) {
        return mode == ModelDiagnosticMode.BASIC_CHAT
                ? ModelDiagnosticCapabilityStatus.NOT_APPLICABLE.name() : null;
    }

    private String normalizeText(String value) {
        return value == null ? null
                : value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize diagnostic snapshot", ex);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash diagnostic request", ex);
        }
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
