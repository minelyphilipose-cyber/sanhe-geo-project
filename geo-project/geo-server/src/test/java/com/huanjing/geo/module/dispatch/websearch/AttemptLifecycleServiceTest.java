package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.retention.service.PollRetentionSliceWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AttemptLifecycleServiceTest {

    @Mock
    private PollInvocationAttemptMapper attemptMapper;
    @Mock
    private PollRetentionSliceWriteService retentionSliceWriteService;

    @InjectMocks
    private AttemptLifecycleService service;

    @BeforeEach
    void runWritesInsideFence() {
        when(retentionSliceWriteService.executeByAttemptId(eq(7L), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    }

    @Test
    void heartbeatOnlyTouchesHeartbeatAndDoesNotReceiveANewDeadline() {
        LocalDateTime heartbeatAt = LocalDateTime.of(2026, 7, 14, 10, 1);
        when(attemptMapper.touchHeartbeat(7L, heartbeatAt)).thenReturn(1);

        service.heartbeat(7L, heartbeatAt);

        verify(attemptMapper).touchHeartbeat(7L, heartbeatAt);
    }

    @Test
    void rejectsAnyTerminalRewriteReportedByAtomicUpdate() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 14, 10, 2);
        when(attemptMapper.markTerminal(7L, "FAILED", completedAt)).thenReturn(0);

        assertThatThrownBy(() -> service.fail(7L, completedAt))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("rejected transition");
    }
}
