package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.retention.service.PollRetentionSliceGuardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollResultProjectionServiceTest {

    @Mock
    private PollInvocationAttemptMapper attemptMapper;
    @Mock
    private PollResultMapper pollResultMapper;
    @Mock
    private PollRetentionSliceGuardService retentionSliceGuardService;
    @InjectMocks
    private PollResultProjectionService service;

    @Test
    void r0DoesNotOverwriteExistingEffectiveAttempt() {
        PollInvocationAttempt attempt = terminalAttempt(9L, "R0");
        PollResult result = resultWithEffectiveAttempt(9L, 4L);
        stubLocks(attempt, result);

        service.finalizeAttempt(9L, true, LocalDateTime.of(2026, 7, 14, 11, 0));

        ArgumentCaptor<PollResult> captor = ArgumentCaptor.forClass(PollResult.class);
        verify(pollResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEffectiveAttemptId()).isEqualTo(4L);
        assertThat(captor.getValue().getExecutionFinalized()).isTrue();
    }

    @Test
    void onlyFinalR5PromotesConfirmedCitationExposure() {
        PollInvocationAttempt attempt = terminalAttempt(9L, "R5");
        attempt.setSearchRequested(true);
        attempt.setSearchTriggered(true);
        attempt.setSearchStatus("TRIGGERED");
        attempt.setBrandInSearch(true);
        attempt.setBrandInAnswer(true);
        attempt.setCitationConfidence("CONFIRMED");
        PollResult result = resultWithEffectiveAttempt(9L, 4L);
        stubLocks(attempt, result);

        service.finalizeAttempt(9L, true, LocalDateTime.of(2026, 7, 14, 11, 0));

        ArgumentCaptor<PollResult> captor = ArgumentCaptor.forClass(PollResult.class);
        verify(pollResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEffectiveAttemptId()).isEqualTo(9L);
        assertThat(captor.getValue().getConfirmedCitationExposure()).isTrue();
    }

    @Test
    void unfinishedSearchRetryChainDoesNotPromoteAttempt() {
        PollInvocationAttempt attempt = terminalAttempt(9L, "R1");
        PollResult result = resultWithEffectiveAttempt(9L, 4L);
        stubLocks(attempt, result);

        service.finalizeAttempt(9L, false, LocalDateTime.of(2026, 7, 14, 11, 0));

        ArgumentCaptor<PollResult> captor = ArgumentCaptor.forClass(PollResult.class);
        verify(pollResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEffectiveAttemptId()).isEqualTo(4L);
        assertThat(captor.getValue().getExecutionFinalized()).isFalse();
        assertThat(captor.getValue().getRetryChainStatus()).isEqualTo("SEARCH_RETRY_PENDING");
    }

    private void stubLocks(PollInvocationAttempt attempt, PollResult result) {
        when(attemptMapper.selectById(attempt.getId())).thenReturn(attempt);
        when(pollResultMapper.selectById(result.getId())).thenReturn(result);
        when(attemptMapper.selectByIdForUpdate(attempt.getId())).thenReturn(attempt);
        when(pollResultMapper.selectByIdForUpdate(result.getId())).thenReturn(result);
        when(attemptMapper.markFinalized(any(), any())).thenReturn(1);
        when(pollResultMapper.updateById(any())).thenReturn(1);
    }

    private PollInvocationAttempt terminalAttempt(Long id, String resultCode) {
        PollInvocationAttempt attempt = new PollInvocationAttempt();
        attempt.setId(id);
        attempt.setPollResultId(2L);
        attempt.setStatus("SUCCEEDED");
        attempt.setResultCode(resultCode);
        return attempt;
    }

    private PollResult resultWithEffectiveAttempt(Long latestAttemptId, Long effectiveAttemptId) {
        PollResult result = new PollResult();
        result.setId(2L);
        result.setLatestAttemptId(latestAttemptId);
        result.setEffectiveAttemptId(effectiveAttemptId);
        result.setExecutionFinalized(true);
        result.setVersion(3L);
        return result;
    }
}
