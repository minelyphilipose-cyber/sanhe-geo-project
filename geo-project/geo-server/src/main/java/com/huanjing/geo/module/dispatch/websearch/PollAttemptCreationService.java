package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollInvocationAttemptMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.websearch.classification.PollResultClassifier;
import com.huanjing.geo.module.dispatch.websearch.enums.AttemptStatus;
import com.huanjing.geo.module.dispatch.websearch.enums.CitationConfidence;
import com.huanjing.geo.module.dispatch.websearch.enums.RetryChainStatus;
import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.enums.TriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PollAttemptCreationService {

    private final PollResultMapper pollResultMapper;
    private final PollInvocationAttemptMapper attemptMapper;
    private final AttemptDeadlinePolicy deadlinePolicy;

    @Transactional
    public PollInvocationAttempt create(PollInvocationAttempt draft,
                                        LocalDateTime createdAt,
                                        Duration perCallTimeout,
                                        int maximumPhysicalCalls,
                                        Duration retryBackoffBudget,
                                        Duration safetyMargin) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(draft.getPollResultId(), "draft.pollResultId");
        TriggerType triggerType = parseTriggerType(draft.getTriggerType());

        PollResult result = pollResultMapper.selectByIdForUpdate(draft.getPollResultId());
        if (result == null || result.getDeletedAt() != null) {
            throw new BizException(404, "Poll result not found: " + draft.getPollResultId());
        }

        draft.setId(null);
        draft.setAttemptNo(attemptMapper.selectMaxAttemptNo(result.getId()) + 1);
        draft.setChainNo(draft.getChainNo() == null ? 1 : draft.getChainNo());
        draft.setStatus(AttemptStatus.PENDING.name());
        draft.setSearchStatus(SearchStatus.NOT_CONFIRMED.name());
        draft.setSearchRequested(true);
        draft.setSearchTriggered(false);
        draft.setGenerationSkipped(false);
        draft.setCitationConfidence(CitationConfidence.NONE.name());
        draft.setAttemptDeadlineAt(deadlinePolicy.calculate(
                createdAt, perCallTimeout, maximumPhysicalCalls, retryBackoffBudget, safetyMargin));
        draft.setLastHeartbeatAt(null);
        draft.setStartedAt(null);
        draft.setCompletedAt(null);
        draft.setFinalizedAt(null);
        draft.setCreatedAt(createdAt);
        draft.setUpdatedAt(createdAt);
        if (draft.getClassifierVersion() == null || draft.getClassifierVersion().isBlank()) {
            draft.setClassifierVersion(PollResultClassifier.VERSION);
        }
        requireAuditSnapshot(draft);

        if (attemptMapper.insert(draft) != 1) {
            throw new BizException(500, "Failed to create invocation attempt");
        }
        Long rootAttemptId = draft.getRootAttemptId() == null ? draft.getId() : draft.getRootAttemptId();
        if (attemptMapper.setRootAttemptIdIfAbsent(draft.getId(), rootAttemptId) == 1) {
            draft.setRootAttemptId(rootAttemptId);
        }

        result.setLatestAttemptId(draft.getId());
        result.setLatestAttemptStatus(AttemptStatus.PENDING.name());
        if (triggerType != TriggerType.MANUAL_RETRY) {
            result.setExecutionFinalized(false);
        }
        result.setRetryChainStatus(RetryChainStatus.RUNNING.name());
        result.setVersion(nextVersion(result.getVersion()));
        if (pollResultMapper.updateById(result) != 1) {
            throw new BizException(409, "Poll result changed while creating an invocation attempt");
        }
        return draft;
    }

    private TriggerType parseTriggerType(String value) {
        try {
            return TriggerType.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BizException(400, "Unsupported attempt triggerType: " + value);
        }
    }

    private void requireAuditSnapshot(PollInvocationAttempt draft) {
        requireText(draft.getQuestionSnapshot(), "questionSnapshot");
        requireText(draft.getSystemPromptSnapshot(), "systemPromptSnapshot");
        requireText(draft.getPlatformCode(), "platformCode");
        requireText(draft.getChannelCode(), "channelCode");
        requireText(draft.getProvider(), "provider");
        requireText(draft.getIntegrationType(), "integrationType");
        requireText(draft.getRequestedModelId(), "requestedModelId");
        requireText(draft.getEndpointUrl(), "endpointUrl");
        requireText(draft.getAdapterVersion(), "adapterVersion");
        Objects.requireNonNull(draft.getProjectId(), "draft.projectId");
        Objects.requireNonNull(draft.getPlatformConfigId(), "draft.platformConfigId");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, "Attempt audit snapshot requires " + field);
        }
    }

    private long nextVersion(Long version) {
        return version == null ? 1L : version + 1L;
    }
}
