package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.AttemptStatus;
import com.huanjing.geo.module.dispatch.websearch.enums.ResultCode;
import com.huanjing.geo.module.dispatch.websearch.enums.RetryChainStatus;
import com.huanjing.geo.module.retention.service.PollRetentionSliceGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PollResultProjectionService {

    private final PollInvocationAttemptMapper attemptMapper;
    private final PollResultMapper pollResultMapper;
    private final PollRetentionSliceGuardService retentionSliceGuardService;

    @Transactional
    public void finalizeAttempt(Long attemptId, boolean automaticChainFinalized, LocalDateTime finalizedAt) {
        PollInvocationAttempt identityAttempt = attemptMapper.selectById(attemptId);
        if (identityAttempt == null) {
            throw new BizException(404, "Invocation attempt not found: " + attemptId);
        }
        PollResult identityResult = pollResultMapper.selectById(identityAttempt.getPollResultId());
        if (identityResult == null || identityResult.getDeletedAt() != null) {
            throw new BizException(404, "Poll result not found: " + identityAttempt.getPollResultId());
        }
        retentionSliceGuardService.lockAndRequireWritable(identityResult);
        PollInvocationAttempt attempt = attemptMapper.selectByIdForUpdate(attemptId);
        if (attempt == null) {
            throw new BizException(404, "Invocation attempt not found: " + attemptId);
        }
        AttemptStatus status = parseTerminalStatus(attempt);
        if (attempt.getFinalizedAt() != null) {
            return;
        }

        PollResult result = pollResultMapper.selectByIdForUpdate(attempt.getPollResultId());
        if (result == null || result.getDeletedAt() != null) {
            throw new BizException(404, "Poll result not found: " + attempt.getPollResultId());
        }
        if (attemptMapper.markFinalized(attemptId, finalizedAt) != 1) {
            throw new BizException(409, "Invocation attempt was finalized concurrently: " + attemptId);
        }

        if (Objects.equals(result.getLatestAttemptId(), attemptId)) {
            result.setLatestAttemptStatus(status.name());
        }
        if (!automaticChainFinalized) {
            result.setExecutionFinalized(false);
            result.setRetryChainStatus(RetryChainStatus.SEARCH_RETRY_PENDING.name());
        } else {
            result.setExecutionFinalized(true);
            result.setRetryChainStatus(status == AttemptStatus.SUCCEEDED
                    ? RetryChainStatus.FINALIZED.name()
                    : RetryChainStatus.FAILED.name());
            promoteEffectiveResultWhenEligible(result, attempt, status);
        }
        result.setVersion(nextVersion(result.getVersion()));
        if (pollResultMapper.updateById(result) != 1) {
            throw new BizException(409, "Poll result projection changed concurrently");
        }
    }

    private void promoteEffectiveResultWhenEligible(PollResult result,
                                                    PollInvocationAttempt attempt,
                                                    AttemptStatus status) {
        ResultCode resultCode = parseResultCode(attempt.getResultCode());
        if (status != AttemptStatus.SUCCEEDED || resultCode == ResultCode.R0) {
            return;
        }
        result.setEffectiveAttemptId(attempt.getId());
        result.setResultCode(resultCode.name());
        result.setSearchRequested(Boolean.TRUE.equals(attempt.getSearchRequested()));
        result.setSearchTriggered(Boolean.TRUE.equals(attempt.getSearchTriggered()));
        result.setSearchStatus(attempt.getSearchStatus());
        result.setBrandInSearch(Boolean.TRUE.equals(attempt.getBrandInSearch()));
        result.setBrandInAnswer(Boolean.TRUE.equals(attempt.getBrandInAnswer()));
        result.setCitationConfidence(attempt.getCitationConfidence());
        result.setConfirmedCitationExposure(resultCode == ResultCode.R5);
    }

    private AttemptStatus parseTerminalStatus(PollInvocationAttempt attempt) {
        AttemptStatus status;
        try {
            status = AttemptStatus.valueOf(attempt.getStatus());
        } catch (RuntimeException ex) {
            throw new BizException(409, "Invocation attempt has invalid status: " + attempt.getStatus());
        }
        if (!status.isTerminal()) {
            throw new BizException(409, "Invocation attempt is not terminal: " + attempt.getId());
        }
        return status;
    }

    private ResultCode parseResultCode(String value) {
        if (value == null || value.isBlank()) {
            return ResultCode.R0;
        }
        try {
            return ResultCode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(409, "Invocation attempt has invalid resultCode: " + value);
        }
    }

    private long nextVersion(Long version) {
        return version == null ? 1L : version + 1L;
    }
}
