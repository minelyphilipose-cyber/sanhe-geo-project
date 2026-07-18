package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.AttemptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttemptLifecycleService {

    private final PollInvocationAttemptMapper attemptMapper;

    public void start(Long attemptId, LocalDateTime startedAt) {
        if (attemptMapper.markRunning(attemptId, startedAt) != 1) {
            throw conflict(attemptId, "PENDING -> RUNNING");
        }
    }

    public void heartbeat(Long attemptId, LocalDateTime heartbeatAt) {
        if (attemptMapper.touchHeartbeat(attemptId, heartbeatAt) != 1) {
            throw conflict(attemptId, "RUNNING heartbeat before fixed deadline");
        }
    }

    public void succeed(Long attemptId, LocalDateTime completedAt) {
        finish(attemptId, AttemptStatus.SUCCEEDED, completedAt);
    }

    public void fail(Long attemptId, LocalDateTime completedAt) {
        finish(attemptId, AttemptStatus.FAILED, completedAt);
    }

    public void abandon(Long attemptId, LocalDateTime completedAt) {
        finish(attemptId, AttemptStatus.ABANDONED, completedAt);
    }

    private void finish(Long attemptId, AttemptStatus targetStatus, LocalDateTime completedAt) {
        if (attemptMapper.markTerminal(attemptId, targetStatus.name(), completedAt) != 1) {
            throw conflict(attemptId, "RUNNING -> " + targetStatus.name());
        }
    }

    private BizException conflict(Long attemptId, String transition) {
        return new BizException(409,
                "Attempt " + attemptId + " rejected transition " + transition
                        + "; status is stale, terminal, or deadline has elapsed");
    }
}
