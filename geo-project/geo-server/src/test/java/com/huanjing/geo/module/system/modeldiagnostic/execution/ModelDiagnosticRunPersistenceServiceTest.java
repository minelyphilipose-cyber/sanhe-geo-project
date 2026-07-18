package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticCapabilityStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticConclusion;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticRunStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticRunPersistenceServiceTest {

    private final AiModelDiagnosticSessionMapper sessionMapper = mock(AiModelDiagnosticSessionMapper.class);
    private final AiModelDiagnosticRunMapper runMapper = mock(AiModelDiagnosticRunMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModelDiagnosticRunPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ModelDiagnosticRunPersistenceService(sessionMapper, runMapper, objectMapper);
    }

    @Test
    void beginCreatesRunningPlaceholderWithoutFreezingConversationContext() {
        ModelDiagnosticExecutionCommand command = command();
        AiModelDiagnosticSession session = session(command, 3);
        when(sessionMapper.selectOwnedForUpdate(command.operatorId(), command.sessionId()))
                .thenReturn(session);
        when(runMapper.insert(any())).thenAnswer(invocation -> {
            AiModelDiagnosticRun inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        });
        when(sessionMapper.consumeTurn(anyLong(), anyInt(), any())).thenReturn(1);

        ModelDiagnosticBeginResult result = service.begin(
                command, profile(), LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));

        assertEquals(List.of(), result.messages());
        assertEquals(3, result.run().getTurnNo());
        assertEquals(99L, result.run().getId());
        assertEquals("[]", result.run().getRequestMessagesJson());
        verify(runMapper, never()).selectRecentSuccessfulFreeChatContext(anyLong(), anyInt());
    }

    @Test
    void permitStageReloadsPriorSuccessfulTurnsAndFreezesProviderMessages() {
        AiModelDiagnosticRun current = new AiModelDiagnosticRun();
        current.setId(99L);
        current.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        current.setTestMode(ModelDiagnosticTestMode.FREE_CHAT.name());
        current.setSessionRecordId(10L);
        current.setTurnNo(3);
        current.setUserMessage("current question");
        AiModelDiagnosticRun newest = new AiModelDiagnosticRun();
        newest.setUserMessage("newer question");
        newest.setAnswer("newer answer");
        AiModelDiagnosticRun oldest = new AiModelDiagnosticRun();
        oldest.setUserMessage("older question");
        oldest.setAnswer("older answer");
        AiModelDiagnosticRun frozen = new AiModelDiagnosticRun();
        frozen.setId(99L);
        frozen.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        when(runMapper.selectByIdForUpdate(99L)).thenReturn(current, frozen);
        when(runMapper.selectRecentSuccessfulFreeChatContext(10L, 3))
                .thenReturn(List.of(newest, oldest));
        when(runMapper.freezeRequestMessagesIfRunning(anyLong(), any())).thenReturn(1);

        ModelDiagnosticPreparedExecution result =
                service.prepareMessagesBeforeExecution(99L);

        assertTrue(result.executable());
        assertEquals(List.of(
                        new WebSearchMessage("user", "older question"),
                        new WebSearchMessage("assistant", "older answer"),
                        new WebSearchMessage("user", "newer question"),
                        new WebSearchMessage("assistant", "newer answer"),
                        new WebSearchMessage("user", "current question")),
                result.messages());
        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(runMapper).freezeRequestMessagesIfRunning(eq(99L), snapshot.capture());
        assertTrue(snapshot.getValue().contains("newer answer"));
    }

    @Test
    void permitStageKeepsNewestCompleteTurnsWithinProviderCharacterBudget() {
        AiModelDiagnosticRun current = runningFreeChat(99L, "c".repeat(2_000));
        current.setSystemPrompt("s".repeat(1_000));
        AiModelDiagnosticRun newest = completedTurn(
                "n".repeat(5_000), "a".repeat(5_000));
        AiModelDiagnosticRun older = completedTurn(
                "o".repeat(9_000), "b".repeat(9_000));
        AiModelDiagnosticRun oldest = completedTurn("old", "answer");
        AiModelDiagnosticRun frozen = new AiModelDiagnosticRun();
        frozen.setId(99L);
        frozen.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        when(runMapper.selectByIdForUpdate(99L)).thenReturn(current, frozen);
        when(runMapper.selectRecentSuccessfulFreeChatContext(10L, 3))
                .thenReturn(List.of(newest, older, oldest));
        when(runMapper.freezeRequestMessagesIfRunning(anyLong(), any())).thenReturn(1);

        ModelDiagnosticPreparedExecution result =
                service.prepareMessagesBeforeExecution(99L);

        assertTrue(result.executable());
        assertEquals(3, result.messages().size());
        assertEquals(newest.getUserMessage(), result.messages().get(0).content());
        assertEquals(newest.getAnswer(), result.messages().get(1).content());
        assertEquals(current.getUserMessage(), result.messages().get(2).content());
        int providerTextCharacters = current.getSystemPrompt().length()
                + result.messages().stream().mapToInt(message -> message.content().length()).sum();
        assertTrue(providerTextCharacters <=
                ModelDiagnosticRunPersistenceService.MAX_PROVIDER_TEXT_CHARACTERS);
    }

    @Test
    void sameIdempotencyKeyWithDifferentFingerprintIsRejected() {
        ModelDiagnosticExecutionCommand command = command();
        AiModelDiagnosticRun existing = new AiModelDiagnosticRun();
        existing.setRequestFingerprint("different");
        when(runMapper.selectByIdempotencyKey(command.operatorId(), command.clientRequestId()))
                .thenReturn(existing);

        assertThrows(ModelDiagnosticIdempotencyConflictException.class,
                () -> service.begin(command, profile(), LocalDateTime.now(),
                        LocalDateTime.now().plusMinutes(3)));
    }

    @Test
    void sameIdempotencyKeyAndFingerprintReturnsExistingWithoutAllocatingTurn() {
        ModelDiagnosticExecutionCommand command = command();
        AiModelDiagnosticRun existing = new AiModelDiagnosticRun();
        existing.setId(77L);
        existing.setRequestFingerprint(service.fingerprint(command));
        when(runMapper.selectByIdempotencyKey(command.operatorId(), command.clientRequestId()))
                .thenReturn(existing);

        ModelDiagnosticBeginResult result = service.begin(
                command, profile(), LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));

        assertTrue(result.reused());
        assertEquals(77L, result.run().getId());
        verify(sessionMapper, never()).consumeTurn(anyLong(), anyInt(), any());
    }

    @Test
    void standardProbeIsIsolatedFromConversationHistory() {
        ModelDiagnosticExecutionCommand command = new ModelDiagnosticExecutionCommand(
                1L, UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.STANDARD_PROBE,
                "basic_generation", "v1", null, null, null,
                "system", "probe question");
        AiModelDiagnosticSession session = session(command, 1);
        when(sessionMapper.selectOwnedForUpdate(command.operatorId(), command.sessionId()))
                .thenReturn(session);
        when(runMapper.insert(any())).thenAnswer(invocation -> {
            AiModelDiagnosticRun inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });
        when(sessionMapper.consumeTurn(anyLong(), anyInt(), any())).thenReturn(1);

        ModelDiagnosticBeginResult result = service.begin(
                command, profile(), LocalDateTime.now(), LocalDateTime.now().plusMinutes(3));

        assertEquals(List.of(), result.messages());
        verify(runMapper, never()).selectRecentSuccessfulFreeChatContext(anyLong(), anyInt());
    }

    @Test
    void busyRequestConditionallyRejectsItsExistingRunningAudit() {
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(101L);
        running.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        AiModelDiagnosticRun rejected = new AiModelDiagnosticRun();
        rejected.setId(101L);
        rejected.setStatus(ModelDiagnosticRunStatus.REJECTED.name());
        rejected.setErrorCategory(ErrorCategory.RATE_LIMIT.name());
        rejected.setErrorCode("DIAGNOSTIC_BUSY");
        when(runMapper.selectByIdForUpdate(101L)).thenReturn(running, rejected);
        when(runMapper.rejectRunningBeforeExecution(
                101L, ErrorCategory.RATE_LIMIT.name(), "DIAGNOSTIC_BUSY", "busy"))
                .thenReturn(1);

        ModelDiagnosticTransitionResult result = service.rejectRunningBeforeExecution(
                101L, ErrorCategory.RATE_LIMIT, "DIAGNOSTIC_BUSY", "busy");

        assertTrue(result.transitionedByCaller());
        assertEquals(ModelDiagnosticRunStatus.REJECTED.name(), result.run().getStatus());
        assertEquals("DIAGNOSTIC_BUSY", result.run().getErrorCode());
        assertEquals(ErrorCategory.RATE_LIMIT.name(), result.run().getErrorCategory());
        assertNull(result.run().getConclusion());
        verify(runMapper).rejectRunningBeforeExecution(
                101L, ErrorCategory.RATE_LIMIT.name(), "DIAGNOSTIC_BUSY", "busy");
    }

    @Test
    void lateSuccessCannotOverwriteRunningAndBecomesAbandoned() {
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(10L);
        running.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(10L);
        abandoned.setStatus(ModelDiagnosticRunStatus.ABANDONED.name());
        when(runMapper.finishRunning(any())).thenReturn(0);
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(running, abandoned);
        when(runMapper.markAbandonedIfExpired(10L)).thenReturn(1);

        ModelDiagnosticTransitionResult result = service.finishSuccess(
                10L, providerResult(), evaluation());

        assertTrue(result.transitionedByCaller());
        assertEquals(ModelDiagnosticRunStatus.ABANDONED.name(), result.run().getStatus());
        verify(runMapper).markAbandonedIfExpired(10L);
        ArgumentCaptor<AiModelDiagnosticRun> attempted = ArgumentCaptor.forClass(AiModelDiagnosticRun.class);
        verify(runMapper).finishRunning(attempted.capture());
        assertEquals(ModelDiagnosticRunStatus.SUCCEEDED.name(), attempted.getValue().getStatus());
        InOrder deadlineOrder = inOrder(runMapper);
        deadlineOrder.verify(runMapper).selectByIdForUpdate(10L);
        deadlineOrder.verify(runMapper).finishRunning(any());
        deadlineOrder.verify(runMapper).markAbandonedIfExpired(10L);
    }

    @Test
    void completionReportsNoTransitionWhenRecoveryAlreadyWon() {
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(10L);
        abandoned.setStatus(ModelDiagnosticRunStatus.ABANDONED.name());
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(abandoned);

        ModelDiagnosticTransitionResult result = service.finishSuccess(
                10L, providerResult(), evaluation());

        assertSame(abandoned, result.run());
        assertFalse(result.transitionedByCaller());
        verify(runMapper, never()).finishRunning(any());
        verify(runMapper, never()).markAbandonedIfExpired(anyLong());
    }

    @Test
    void rejectionReportsNoTransitionWhenRecoveryAlreadyWon() {
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(11L);
        abandoned.setStatus(ModelDiagnosticRunStatus.ABANDONED.name());
        when(runMapper.selectByIdForUpdate(11L)).thenReturn(abandoned);

        ModelDiagnosticTransitionResult result = service.rejectRunningBeforeExecution(
                11L, ErrorCategory.RATE_LIMIT, "DIAGNOSTIC_BUSY", "busy");

        assertSame(abandoned, result.run());
        assertFalse(result.transitionedByCaller());
        verify(runMapper, never()).rejectRunningBeforeExecution(
                anyLong(), any(), any(), any());
    }

    @Test
    void preparationReportsNoTransitionWhenRecoveryAlreadyWon() {
        AiModelDiagnosticRun abandoned = new AiModelDiagnosticRun();
        abandoned.setId(12L);
        abandoned.setStatus(ModelDiagnosticRunStatus.ABANDONED.name());
        when(runMapper.selectByIdForUpdate(12L)).thenReturn(abandoned);

        ModelDiagnosticPreparedExecution result =
                service.prepareMessagesBeforeExecution(12L);

        assertSame(abandoned, result.run());
        assertFalse(result.executable());
        assertFalse(result.transitionedByCaller());
        verify(runMapper, never()).freezeRequestMessagesIfRunning(anyLong(), any());
        verify(runMapper, never()).markAbandonedIfExpired(anyLong());
    }

    @Test
    void fingerprintUsesOnlyNormalizedClientSemanticsAcrossServerUpgrades() {
        String sessionId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        ModelDiagnosticExecutionCommand first = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.STANDARD_PROBE,
                " basic_generation ", "v1", null, null, null,
                "server prompt v1", "server question v1");
        ModelDiagnosticExecutionCommand upgraded = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.STANDARD_PROBE,
                "basic_generation", "v2", null, null, null,
                "server prompt v2", "server question v2");

        assertEquals(service.fingerprint(first), service.fingerprint(upgraded));

        ModelDiagnosticExecutionCommand fixedV1 = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v1", "template-v1",
                ModelDiagnosticInputMode.FIXED, null,
                "template system v1", "server generated question A");
        ModelDiagnosticExecutionCommand fixedV2 = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v2", "template-v2",
                ModelDiagnosticInputMode.FIXED, null,
                "template system v2", "server generated question B");
        assertEquals(service.fingerprint(fixedV1), service.fingerprint(fixedV2));
        AiModelDiagnosticRun originalFixedRun = new AiModelDiagnosticRun();
        originalFixedRun.setId(88L);
        originalFixedRun.setRequestFingerprint(service.fingerprint(fixedV1));
        when(runMapper.selectByIdempotencyKey(1L, requestId)).thenReturn(originalFixedRun);
        assertSame(originalFixedRun, service.findIdempotentReplay(fixedV2));

        ModelDiagnosticExecutionCommand userRequiredV1 = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v1", "template-v1",
                ModelDiagnosticInputMode.USER_REQUIRED, "  client\r\nquestion  ",
                "template system v1", "wrapped client question A");
        ModelDiagnosticExecutionCommand userRequiredV2 = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v2", "template-v2",
                ModelDiagnosticInputMode.USER_REQUIRED, "client\nquestion",
                "template system v2", "wrapped client question B");
        assertEquals(service.fingerprint(userRequiredV1), service.fingerprint(userRequiredV2));

        ModelDiagnosticExecutionCommand differentClientInput = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v2", "template-v2",
                ModelDiagnosticInputMode.USER_REQUIRED, "different question",
                "template system v2", "wrapped different question");
        assertNotEquals(service.fingerprint(userRequiredV2),
                service.fingerprint(differentClientInput));

        ModelDiagnosticExecutionCommand crlf = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "  line1\r\nline2  ", "  question\r\nnext  ");
        ModelDiagnosticExecutionCommand lf = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "line1\nline2", "question\nnext");
        assertEquals(service.fingerprint(crlf), service.fingerprint(lf));

        ModelDiagnosticExecutionCommand lowTier = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "line1\nline2", "question\nnext", ModelDiagnosticModelTier.LOW);
        assertNotEquals(service.fingerprint(lf), service.fingerprint(lowTier));

        ModelDiagnosticExecutionCommand changed = new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "line1\nline2", "different question");
        assertNotEquals(service.fingerprint(lf), service.fingerprint(changed));
    }

    @Test
    void productionTemplateInputModeEnforcesClientInputContract() {
        String sessionId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () -> new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v1", "template-v1",
                ModelDiagnosticInputMode.FIXED, "client input is forbidden",
                "system", "fixed question"));
        assertThrows(IllegalArgumentException.class, () -> new ModelDiagnosticExecutionCommand(
                1L, sessionId, requestId, 2L,
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll", "probe-v1", "template-v1",
                ModelDiagnosticInputMode.USER_REQUIRED, null,
                "system", "resolved question"));
    }

    @Test
    void authenticationFailureLeavesUnexecutedGenerationUnknown() {
        AiModelDiagnosticRun captured = captureFailure(ModelDiagnosticMode.BASIC_CHAT,
                new ModelDiagnosticExecutionException(
                        ErrorCategory.AUTHENTICATION, null, "credential missing", null, null, null));

        assertEquals(ModelDiagnosticCapabilityStatus.FAIL.name(), captured.getAuthenticationStatus());
        assertNull(captured.getGenerationStatus());
        assertEquals(ModelDiagnosticCapabilityStatus.NOT_APPLICABLE.name(), captured.getWebSearchStatus());
        assertEquals(ModelDiagnosticCapabilityStatus.NOT_APPLICABLE.name(), captured.getSourceParsingStatus());
        assertEquals(ModelDiagnosticCapabilityStatus.NOT_APPLICABLE.name(), captured.getCitationParsingStatus());
    }

    @Test
    void webSearchNetworkFailureLeavesAllUnconfirmedCapabilitiesUnknown() {
        AiModelDiagnosticRun captured = captureFailure(ModelDiagnosticMode.WEB_SEARCH,
                new ModelDiagnosticExecutionException(
                        ErrorCategory.NETWORK, null, "connection reset", null, null, null));

        assertNull(captured.getAuthenticationStatus());
        assertNull(captured.getGenerationStatus());
        assertNull(captured.getWebSearchStatus());
        assertNull(captured.getSourceParsingStatus());
        assertNull(captured.getCitationParsingStatus());
    }

    @Test
    void twoHundredParseFailureOnlyConfirmsAuthentication() {
        AiModelDiagnosticRun captured = captureFailure(ModelDiagnosticMode.WEB_SEARCH,
                new ModelDiagnosticExecutionException(
                        ErrorCategory.PARSE_ERROR, 200, "invalid response", "{}", "{}", null));

        assertEquals(ModelDiagnosticCapabilityStatus.PASS.name(), captured.getAuthenticationStatus());
        assertNull(captured.getGenerationStatus());
        assertNull(captured.getWebSearchStatus());
        assertNull(captured.getSourceParsingStatus());
        assertNull(captured.getCitationParsingStatus());
    }

    private AiModelDiagnosticRun captureFailure(ModelDiagnosticMode mode,
                                                ModelDiagnosticExecutionException failure) {
        AiModelDiagnosticRun running = new AiModelDiagnosticRun();
        running.setId(20L);
        running.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        AiModelDiagnosticRun stored = new AiModelDiagnosticRun();
        stored.setId(20L);
        stored.setStatus(ModelDiagnosticRunStatus.FAILED.name());
        when(runMapper.finishRunning(any())).thenReturn(1);
        when(runMapper.selectByIdForUpdate(20L)).thenReturn(running, stored);

        service.finishFailure(20L, mode, failure);

        ArgumentCaptor<AiModelDiagnosticRun> captor = ArgumentCaptor.forClass(AiModelDiagnosticRun.class);
        verify(runMapper).finishRunning(captor.capture());
        return captor.getValue();
    }

    private AiModelDiagnosticSession session(ModelDiagnosticExecutionCommand command, int nextTurn) {
        AiModelDiagnosticSession session = new AiModelDiagnosticSession();
        session.setId(8L);
        session.setOperatorId(command.operatorId());
        session.setSessionId(command.sessionId());
        session.setStatus("ACTIVE");
        session.setNextTurnNo(nextTurn);
        return session;
    }

    private ModelDiagnosticExecutionCommand command() {
        return new ModelDiagnosticExecutionCommand(
                1L, UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, null, null, null,
                "system", "current question");
    }

    private AiModelDiagnosticRun runningFreeChat(Long id, String userMessage) {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setId(id);
        run.setStatus(ModelDiagnosticRunStatus.RUNNING.name());
        run.setTestMode(ModelDiagnosticTestMode.FREE_CHAT.name());
        run.setSessionRecordId(10L);
        run.setTurnNo(3);
        run.setUserMessage(userMessage);
        return run;
    }

    private AiModelDiagnosticRun completedTurn(String userMessage, String answer) {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setUserMessage(userMessage);
        run.setAnswer(answer);
        return run;
    }

    private ModelDiagnosticPlatformProfile profile() {
        return new ModelDiagnosticPlatformProfile(
                2L, "platform", "channel", "Platform", "BASIC_CHAT",
                IntegrationType.OPENAI_CHAT, "https://example.test/v1", "model", 1L,
                "{}", "{}", "hash", "env://TEST_KEY", null,
                3_000, 60_000);
    }

    private ModelDiagnosticProviderResult providerResult() {
        return new ModelDiagnosticProviderResult(
                "request", "model", "answer", 200, SearchStatus.NOT_CONFIRMED,
                List.of(), List.of(), List.of(), Map.of(), "stop", "{}", "{}", 1L);
    }

    private ModelDiagnosticEvaluation evaluation() {
        return new ModelDiagnosticEvaluation(
                ModelDiagnosticConclusion.PASS, "passed",
                ModelDiagnosticCapabilityStatus.PASS,
                ModelDiagnosticCapabilityStatus.PASS,
                ModelDiagnosticCapabilityStatus.NOT_APPLICABLE,
                ModelDiagnosticCapabilityStatus.NOT_APPLICABLE,
                ModelDiagnosticCapabilityStatus.NOT_APPLICABLE,
                null, null, null, null, 0, 0, 0, 0);
    }
}
