package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionReviewStatusPollService {

    private static final int DEFAULT_BATCH_LIMIT = 50;
    private static final Duration TASK_LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration MANUAL_REFRESH_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration MAX_POLL_AGE = Duration.ofDays(7);
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(3),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(2)
    };

    private final DistributionTaskMapper distributionTaskMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final List<AutoSelfMediaAdapter> selfMediaAdapters;
    private final ObjectMapper objectMapper;

    public int pollDueTasks() {
        return pollDueTasks(DEFAULT_BATCH_LIMIT);
    }

    public int pollDueTasks(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<DistributionTask> tasks = distributionTaskMapper.selectDueReviewTasks(now, limit);
        int processed = 0;
        for (DistributionTask task : tasks) {
            if (distributionTaskMapper.claimReviewTask(task.getId(), now, now.plus(TASK_LOCK_TTL)) != 1) {
                continue;
            }
            try {
                pollOne(task, now);
                processed++;
            } catch (Exception ex) {
                log.warn("review status poll failed taskId={}", task.getId(), ex);
                applyResult(task, ReviewStatusResult.unknown(null, safeMessage(ex), true, null), now);
            }
        }
        return processed;
    }

    public DistributionTask refreshTask(DistributionTask task) {
        LocalDateTime now = LocalDateTime.now();
        DistributionTask latest = distributionTaskMapper.selectById(task.getId());
        if (latest == null || !DistributionTaskStatePolicy.isAutomaticReviewCandidate(
                latest.getDispatchMode(),
                latest.getTargetKind(),
                latest.getStatus(),
                latest.getReviewStatus())) {
            return latest;
        }
        if (latest.getReviewCheckedAt() != null
                && latest.getReviewCheckedAt().plus(MANUAL_REFRESH_COOLDOWN).isAfter(now)) {
            return latest;
        }
        if (distributionTaskMapper.claimReviewTask(latest.getId(), now, now.plus(TASK_LOCK_TTL)) != 1) {
            return distributionTaskMapper.selectById(latest.getId());
        }
        try {
            pollOne(latest, now);
        } catch (Exception ex) {
            log.warn("manual review status refresh failed taskId={}", latest.getId(), ex);
            applyResult(latest, ReviewStatusResult.unknown(null, safeMessage(ex), true, null), now);
        }
        return distributionTaskMapper.selectById(task.getId());
    }

    public void pollOne(DistributionTask task, LocalDateTime now) {
        if (!DistributionTaskStatePolicy.isAutomaticReviewCandidate(
                task.getDispatchMode(),
                task.getTargetKind(),
                task.getStatus(),
                task.getReviewStatus())) {
            releaseLock(task.getId());
            return;
        }
        SelfMediaAccount account = selfMediaAccountMapper.selectById(task.getSelfMediaAccountId());
        if (account == null) {
            applyResult(task, ReviewStatusResult.unknown(null, "self media account not found", false, null), now);
            return;
        }
        AutoSelfMediaAdapter adapter = resolveAdapter(account.getPlatform());
        ReviewStatusResult result = adapter.refreshReviewStatus(task, account);
        applyResult(task, result, now);
    }

    private AutoSelfMediaAdapter resolveAdapter(String platform) {
        return selfMediaAdapters.stream()
                .filter(adapter -> adapter.supportsPlatform(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Self-media platform not implemented: " + platform));
    }

    private void applyResult(DistributionTask task, ReviewStatusResult result, LocalDateTime now) {
        if (isTerminalReview(task.getReviewStatus())) {
            return;
        }
        ReviewStatusResult safeResult = result == null
                ? ReviewStatusResult.unknown(null, null, true, null)
                : result;
        int nextCount = Math.max(0, task.getReviewCheckCount() == null ? 0 : task.getReviewCheckCount()) + 1;
        String reviewStatus = SubmitResult.toStorageValue(safeResult.status());
        if (!StringUtils.hasText(reviewStatus)
                || DistributionTaskStatePolicy.REVIEW_NOT_APPLICABLE.equals(reviewStatus)) {
            reviewStatus = DistributionTaskStatePolicy.REVIEW_UNKNOWN;
        }

        DistributionTaskStatePolicy.ReviewTransition transition =
                DistributionTaskStatePolicy.transitionForReview(reviewStatus);
        LocalDateTime submittedAt = task.getSubmittedAt() != null ? task.getSubmittedAt() : task.getCreatedAt();
        boolean expired = submittedAt != null && submittedAt.plus(MAX_POLL_AGE).isBefore(now);
        LocalDateTime nextReviewCheckAt = isTerminalReview(reviewStatus) || expired
                ? null
                : now.plus(backoff(nextCount));
        if (expired && !isTerminalReview(reviewStatus)) {
            reviewStatus = DistributionTaskStatePolicy.REVIEW_UNKNOWN;
            transition = DistributionTaskStatePolicy.transitionForReview(reviewStatus);
        }

        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, task.getId())
                .eq(DistributionTask::getStatus, DistributionTaskStatePolicy.STATUS_SUBMITTED)
                .in(DistributionTask::getReviewStatus,
                        DistributionTaskStatePolicy.REVIEW_UNDER_REVIEW,
                        DistributionTaskStatePolicy.REVIEW_UNKNOWN)
                .set(DistributionTask::getStatus, transition.taskStatus())
                .set(DistributionTask::getReviewStatus, reviewStatus)
                .set(DistributionTask::getReviewFeedback, safeResult.reviewFeedback())
                .set(DistributionTask::getExternalStatus, safeResult.externalStatus())
                .set(DistributionTask::getReviewCheckedAt, now)
                .set(DistributionTask::getNextReviewCheckAt, nextReviewCheckAt)
                .set(DistributionTask::getReviewCheckCount, nextCount)
                .set(DistributionTask::getReviewLockedUntil, null);
        if (StringUtils.hasText(safeResult.rawResponse())) {
            wrapper.set(DistributionTask::getResponsePayload, JsonColumnPayloads.normalize(objectMapper, safeResult.rawResponse()));
        }
        if (StringUtils.hasText(safeResult.platformArticleId())) {
            wrapper.set(DistributionTask::getPlatformArticleId, safeResult.platformArticleId());
        }
        if (isTerminalReview(reviewStatus)) {
            wrapper.set(DistributionTask::getFinishedAt, now);
        }
        distributionTaskMapper.update(null, wrapper);
    }

    private void releaseLock(Long taskId) {
        distributionTaskMapper.update(null, new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .set(DistributionTask::getReviewLockedUntil, null));
    }

    private boolean isTerminalReview(String reviewStatus) {
        return DistributionTaskStatePolicy.REVIEW_PUBLISHED.equals(reviewStatus)
                || DistributionTaskStatePolicy.REVIEW_REJECTED.equals(reviewStatus)
                || DistributionTaskStatePolicy.REVIEW_OFFLINE.equals(reviewStatus);
    }

    private Duration backoff(int checkCount) {
        int index = Math.max(0, Math.min(checkCount - 1, BACKOFF.length - 1));
        return BACKOFF[index];
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
