package com.huanjing.geo.module.dispatch.websearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.service.PollDatabaseWriteRetryService;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.enums.TriggerType;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class WebSearchPollExecutionServiceTest {

    private final PollAttemptCreationService creationService = mock(PollAttemptCreationService.class);
    private final AttemptLifecycleService lifecycleService = mock(AttemptLifecycleService.class);
    private final PollResultProjectionService projectionService = mock(PollResultProjectionService.class);
    private final PollInvocationAttemptMapper attemptMapper = mock(PollInvocationAttemptMapper.class);
    private final WebSearchLlmGateway gateway = mock(WebSearchLlmGateway.class);
    private final WebSearchAttemptResultWriter resultWriter = mock(WebSearchAttemptResultWriter.class);
    private WebSearchPollExecutionService service;

    @BeforeEach
    void setUp() {
        service = new WebSearchPollExecutionService(
                creationService, lifecycleService, projectionService, attemptMapper,
                new PollDatabaseWriteRetryService(), gateway, resultWriter, new ObjectMapper());
        AtomicLong ids = new AtomicLong(100);
        when(creationService.create(any(), any(), any(), any(Integer.class), any(), any()))
                .thenAnswer(invocation -> {
                    PollInvocationAttempt draft = invocation.getArgument(0);
                    draft.setId(ids.incrementAndGet());
                    draft.setAttemptDeadlineAt(LocalDateTime.now().plusMinutes(2));
                    if (draft.getRootAttemptId() == null) {
                        draft.setRootAttemptId(draft.getId());
                    }
                    return draft;
                });
    }

    @Test
    void retriesOnceWithSearchRetryAttemptWhenFirstAttemptIsR1() {
        WebSearchResponse first = response(SearchStatus.NOT_CONFIRMED, "模型知识回答");
        WebSearchResponse second = response(SearchStatus.TRIGGERED, "联网回答");
        when(gateway.execute(any())).thenReturn(first, second);
        when(resultWriter.writeSuccess(any(), any(), any(), any()))
                .thenReturn(ResultCode.R1, ResultCode.R2);

        WebSearchPollExecutionOutcome outcome = service.execute(command());

        assertTrue(outcome.success());
        assertEquals(2, outcome.attemptCount());
        assertEquals(ResultCode.R2, outcome.resultCode());
        ArgumentCaptor<PollInvocationAttempt> attempts = ArgumentCaptor.forClass(PollInvocationAttempt.class);
        verify(creationService, times(2)).create(attempts.capture(), any(), any(), any(Integer.class), any(), any());
        assertEquals(TriggerType.SCHEDULED.name(), attempts.getAllValues().get(0).getTriggerType());
        assertEquals(TriggerType.SEARCH_RETRY.name(), attempts.getAllValues().get(1).getTriggerType());
        assertEquals(attempts.getAllValues().get(0).getId(), attempts.getAllValues().get(1).getRetryOfAttemptId());
        verify(projectionService).finalizeAttempt(
                org.mockito.ArgumentMatchers.eq(attempts.getAllValues().get(0).getId()),
                org.mockito.ArgumentMatchers.eq(false), any());
        verify(projectionService).finalizeAttempt(
                org.mockito.ArgumentMatchers.eq(attempts.getAllValues().get(1).getId()),
                org.mockito.ArgumentMatchers.eq(true), any());
    }

    @Test
    void doesNotRetrySuccessfulSearchResult() {
        when(gateway.execute(any())).thenReturn(response(SearchStatus.TRIGGERED, "联网回答"));
        when(resultWriter.writeSuccess(any(), any(), any(), any())).thenReturn(ResultCode.R3);

        WebSearchPollExecutionOutcome outcome = service.execute(command());

        assertTrue(outcome.success());
        assertEquals(1, outcome.attemptCount());
        verify(creationService, times(1)).create(any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void storedEncryptedCredentialUsesInternalDatabaseReference() {
        WebSearchPollCommand command = command();
        command.platform().setPrimaryKeyRef(null);
        command.platform().setApiKey("ENC:ciphertext");
        when(gateway.execute(any())).thenReturn(response(SearchStatus.TRIGGERED, "联网回答"));
        when(resultWriter.writeSuccess(any(), any(), any(), any())).thenReturn(ResultCode.R2);

        service.execute(command);

        ArgumentCaptor<WebSearchRequest> request = ArgumentCaptor.forClass(WebSearchRequest.class);
        verify(gateway).execute(request.capture());
        assertEquals("db://ai-platform-config/1",
                request.getValue().profile().primaryCredentialRef());
    }

    @Test
    void resumesPersistedProviderOutcomeWithoutCallingProviderAgain() {
        PollInvocationAttempt persisted = new PollInvocationAttempt();
        persisted.setId(88L);
        persisted.setShardItemId(2L);
        persisted.setTriggerType(TriggerType.SCHEDULED.name());
        persisted.setStatus("SUCCEEDED");
        persisted.setResultCode(ResultCode.R3.name());
        persisted.setSearchStatus(SearchStatus.TRIGGERED.name());
        persisted.setAnswer("已落库联网回答");
        persisted.setRequestedModelId("model");
        persisted.setResponseModelId("model");
        persisted.setCompletedAt(LocalDateTime.now());
        when(attemptMapper.selectLatestTerminalByShardItemId(2L)).thenReturn(persisted);

        WebSearchPollExecutionOutcome outcome = service.execute(command());

        assertTrue(outcome.success());
        assertEquals("已落库联网回答", outcome.response().answer());
        verify(projectionService).finalizeAttempt(any(), any(Boolean.class), any());
        verify(gateway, never()).execute(any());
        verify(creationService, never()).create(any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void retriesProjectionInFreshServiceInvocationAfterDeadlock() {
        when(gateway.execute(any())).thenReturn(response(SearchStatus.TRIGGERED, "联网回答"));
        when(resultWriter.writeSuccess(any(), any(), any(), any())).thenReturn(ResultCode.R3);
        doThrow(new DeadlockLoserDataAccessException("Deadlock found", null))
                .doNothing()
                .when(projectionService).finalizeAttempt(any(), any(Boolean.class), any());

        WebSearchPollExecutionOutcome outcome = service.execute(command());

        assertTrue(outcome.success());
        verify(projectionService, times(2)).finalizeAttempt(any(), any(Boolean.class), any());
        verify(gateway, times(1)).execute(any());
    }

    @Test
    void retriesAttemptCreationBeforeCallingProvider() {
        doThrow(new DeadlockLoserDataAccessException("Deadlock found", null))
                .doAnswer(invocation -> {
                    PollInvocationAttempt draft = invocation.getArgument(0);
                    draft.setId(101L);
                    draft.setRootAttemptId(101L);
                    draft.setAttemptDeadlineAt(LocalDateTime.now().plusMinutes(2));
                    return draft;
                })
                .when(creationService).create(any(), any(), any(), any(Integer.class), any(), any());
        when(gateway.execute(any())).thenReturn(response(SearchStatus.TRIGGERED, "联网回答"));
        when(resultWriter.writeSuccess(any(), any(), any(), any())).thenReturn(ResultCode.R3);

        WebSearchPollExecutionOutcome outcome = service.execute(command());

        assertTrue(outcome.success());
        verify(creationService, times(2)).create(any(), any(), any(), any(Integer.class), any(), any());
        verify(gateway, times(1)).execute(any());
    }

    private WebSearchPollCommand command() {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setId(1L);
        platform.setPlatformCode("doubao_web");
        platform.setChannelCode("doubao");
        platform.setIntegrationType("VOLCENGINE_RESPONSES_WEB");
        platform.setProviderConfigJson("{\"provider\":\"volcengine\"}");
        platform.setConfigVersion(1L);
        platform.setApiUrl("https://example.test/responses");
        platform.setModelId("model");
        platform.setPrimaryKeyRef("env://TEST_KEY");
        return new WebSearchPollCommand(
                1L, 2L, 3L, 4L, 5L, "问题", "系统提示", platform,
                TriggerType.SCHEDULED, 3_000, 60_000, Set.of("测试品牌"));
    }

    private WebSearchResponse response(SearchStatus status, String answer) {
        return new WebSearchResponse(
                "request", "model", "model", answer, status, false,
                List.of(), List.of(), List.of(), Map.of(), "completed");
    }
}
