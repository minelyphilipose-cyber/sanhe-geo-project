package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailure;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.common.llm.measurement.LlmObservationScope;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.common.llm.limiter.PlatformConcurrencyLimiterService;
import com.huanjing.geo.module.project.entity.BaselineCollectionTask;
import com.huanjing.geo.module.project.entity.BaselineCompetitorMention;
import com.huanjing.geo.module.project.entity.BaselineCompetitorSource;
import com.huanjing.geo.module.project.entity.BaselineHighlightSpan;
import com.huanjing.geo.module.project.entity.BaselineObservation;
import com.huanjing.geo.module.project.entity.BaselineObservationScore;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.BaselineSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineCollectionTaskMapper;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorMentionMapper;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorSourceMapper;
import com.huanjing.geo.module.project.mapper.BaselineHighlightSpanMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationScoreMapper;
import com.huanjing.geo.module.project.mapper.BaselineQuestionSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineObservationCollectionWorker {
    private static final String SCORE_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION;
    private static final String HIGHLIGHT_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.HIGHLIGHT_ALGORITHM_VERSION;
    private static final String COMPETITOR_NORMALIZATION_VERSION = BaselineCanonicalVersionPolicy.COMPETITOR_NORMALIZATION_VERSION;
    private static final long RUNNING_STALE_MINUTES = 30;
    private static final Object QUEUE_LOCK = new Object();
    private static final long RATE_LIMIT_BACKOFF_MS = 10_000L;
    private static final long UNKNOWN_BACKOFF_MS = 2_000L;
    private static final String CALL_STATUS_SUCCESS = "SUCCESS";
    private static final String CALL_STATUS_FAILED = "FAILED";
    private static final String CALL_STATUS_CAPACITY_DEFERRED = "CAPACITY_DEFERRED";

    private final BaselineCollectionTaskMapper baselineCollectionTaskMapper;
    private final BaselineSnapshotMapper baselineSnapshotMapper;
    private final BaselineQuestionSnapshotMapper baselineQuestionSnapshotMapper;
    private final BaselineObservationMapper baselineObservationMapper;
    private final BaselineObservationScoreMapper baselineObservationScoreMapper;
    private final BaselineHighlightSpanMapper baselineHighlightSpanMapper;
    private final BaselineCompetitorSourceMapper baselineCompetitorSourceMapper;
    private final BaselineCompetitorMentionMapper baselineCompetitorMentionMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final LlmCallFacade llmCallFacade;
    private final LlmPoolProperties llmPoolProperties;
    private final TransactionTemplate transactionTemplate;
    private final PlatformConcurrencyLimiterService platformConcurrencyLimiterService;
    private final BaselineSemanticJudgeService baselineSemanticJudgeService;
    private final LlmCapacityFailureClassifier capacityFailureClassifier;
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;
    @Autowired
    @Qualifier("baselineCollectionSampleExecutor")
    private Executor baselineCollectionSampleExecutor;

    @Value("${baseline.collection.db-write-max-concurrency:6}")
    private int dbWriteMaxConcurrency;

    @Value("${baseline.collection.transient-retry-max-attempts:2}")
    private int transientRetryMaxAttempts;

    @Value("${baseline.collection.transient-retry-initial-backoff-ms:800}")
    private long transientRetryInitialBackoffMs;

    @Value("${baseline.collection.transient-retry-max-backoff-ms:5000}")
    private long transientRetryMaxBackoffMs;

    @Value("${baseline.collection.model-connect-timeout-ms:10000}")
    private int modelConnectTimeoutMs;

    @Value("${baseline.collection.model-request-timeout-ms:45000}")
    private int modelRequestTimeoutMs;

    @Value("${baseline.collection.max-concurrent-baselines:1}")
    private int maxConcurrentBaselines;

    @Value("${baseline.collection.capacity-failure-defer-enabled:false}")
    private boolean capacityFailureDeferEnabled;

    private volatile Semaphore dbWriteSemaphore;
    private final ConcurrentHashMap<String, PlatformBackoffState> platformBackoffStates = new ConcurrentHashMap<>();

    public void runTask(Long taskId) {
        AtomicBoolean claimed = new AtomicBoolean(false);
        synchronized (QUEUE_LOCK) {
            transactionTemplate.executeWithoutResult(status -> {
                BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
                if (task == null
                        || BaselineObservationCollectionService.TASK_STATUS_COMPLETED.equals(task.getStatus())
                        || BaselineObservationCollectionService.TASK_STATUS_CANCELED.equals(task.getStatus())) {
                    return;
                }
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime staleBefore = now.minusMinutes(RUNNING_STALE_MINUTES);
                if (isFreshRunning(task, staleBefore)) {
                    return;
                }
                if (BaselineObservationCollectionService.TASK_STATUS_PENDING.equals(task.getStatus())
                        && baselineCollectionTaskMapper.countRunning() >= normalizedMaxConcurrentBaselines()) {
                    return;
                }
                BaselineCollectionTask update = new BaselineCollectionTask();
                update.setStatus(BaselineObservationCollectionService.TASK_STATUS_RUNNING);
                update.setStartedAt(task.getStartedAt() == null ? now : task.getStartedAt());
                update.setUpdatedAt(now);
                update.setErrorMessage(null);
                int updated = baselineCollectionTaskMapper.update(update, new LambdaUpdateWrapper<BaselineCollectionTask>()
                        .eq(BaselineCollectionTask::getId, taskId)
                        .ne(BaselineCollectionTask::getStatus, BaselineObservationCollectionService.TASK_STATUS_COMPLETED)
                        .ne(BaselineCollectionTask::getStatus, BaselineObservationCollectionService.TASK_STATUS_CANCELED)
                        .and(wrapper -> wrapper
                                .ne(BaselineCollectionTask::getStatus, BaselineObservationCollectionService.TASK_STATUS_RUNNING)
                                .or()
                                .isNull(BaselineCollectionTask::getUpdatedAt)
                                .or()
                                .le(BaselineCollectionTask::getUpdatedAt, staleBefore)));
                claimed.set(updated > 0);
            });
        }
        if (!claimed.get()) {
            return;
        }

        try {
            processTask(taskId);
            if (!isCanceled(taskId)) {
                finishTask(taskId);
            }
        } catch (Exception ex) {
            failTask(taskId, ex);
            log.warn("Baseline collection task failed, taskId={}", taskId, ex);
        } finally {
            dispatchPending();
        }
    }

    public void dispatchPending() {
        int available;
        synchronized (QUEUE_LOCK) {
            available = Math.max(0, normalizedMaxConcurrentBaselines() - baselineCollectionTaskMapper.countRunning());
        }
        if (available <= 0) {
            return;
        }
        List<BaselineCollectionTask> pendingTasks = baselineCollectionTaskMapper.selectPending(available);
        for (BaselineCollectionTask pendingTask : pendingTasks) {
            taskExecutor.execute(() -> runTask(pendingTask.getId()));
        }
    }

    private void processTask(Long taskId) {
        BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
        BaselineSnapshot snapshot = baselineSnapshotMapper.selectById(task.getBaselineId());
        Project project = projectMapper.selectById(task.getProjectId());
        List<BaselineQuestionSnapshot> questions = baselineQuestionSnapshotMapper.selectList(
                new LambdaQueryWrapper<BaselineQuestionSnapshot>()
                        .eq(BaselineQuestionSnapshot::getBaselineId, task.getBaselineId())
                        .orderByAsc(BaselineQuestionSnapshot::getSortOrder, BaselineQuestionSnapshot::getId));
        List<AiPlatformConfig> platforms = loadPlatforms(task);
        List<String> aliases = parseAliases(project.getProjectAliases());
        List<BaselineObservationScoringRules.CompetitorName> competitors = loadCompetitors(task.getBaselineId());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (BaselineQuestionSnapshot question : questions) {
            for (AiPlatformConfig platform : platforms) {
                for (int sampleSeq = 1; sampleSeq <= task.getSamplePerCell(); sampleSeq++) {
                    if (isCanceled(taskId)) {
                        return;
                    }
                    if (observationExists(task.getBaselineId(), question.getId(), platform.getPlatformCode(), sampleSeq)) {
                        continue;
                    }
                    int currentSampleSeq = sampleSeq;
                    futures.add(CompletableFuture.runAsync(() -> collectAndPersistSample(
                            taskId,
                            snapshot,
                            question,
                            platform,
                            project,
                            aliases,
                            competitors,
                            currentSampleSeq
                    ), baselineCollectionSampleExecutor));
                }
            }
        }
        waitForSamples(taskId, futures);
    }

    private void collectAndPersistSample(Long taskId,
                                         BaselineSnapshot snapshot,
                                         BaselineQuestionSnapshot question,
                                         AiPlatformConfig platform,
                                         Project project,
                                         List<String> aliases,
                                         List<BaselineObservationScoringRules.CompetitorName> competitors,
                                         int sampleSeq) {
        if (isCanceled(taskId)
                || observationExists(snapshot.getId(), question.getId(), platform.getPlatformCode(), sampleSeq)) {
            return;
        }
        awaitPlatformBackoff(platform.getPlatformCode());
        try (PlatformConcurrencyLimiterService.Permit ignored = platformConcurrencyLimiterService.acquire(platform)) {
            if (isCanceled(taskId)) {
                return;
            }
            BaselineObservation observation = collectOneWithRetry(snapshot, question, platform, sampleSeq);
            if (isCanceled(taskId)) {
                return;
            }
            BaselineSemanticJudgeResult judgeResult = null;
            if (CALL_STATUS_CAPACITY_DEFERRED.equals(observation.getCallStatus())) {
                log.info("Baseline observation sample deferred by LLM capacity, task={}, baseline={}, platform={}, question={}, sampleSeq={}, errorCode={}",
                        taskId, snapshot.getId(), platform.getPlatformCode(), question.getId(), sampleSeq, observation.getErrorCode());
                return;
            }
            if (CALL_STATUS_SUCCESS.equals(observation.getCallStatus())) {
                judgeResult = baselineSemanticJudgeService.judge(
                        question,
                        project,
                        aliases,
                        competitors,
                        platform,
                        observation.getRawResponseText()
                );
            }
            persistObservation(taskId, snapshot, question, project, aliases, competitors, observation, judgeResult);
        }
    }

    private void waitForSamples(Long taskId, List<CompletableFuture<Void>> futures) {
        if (futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            while (!all.isDone()) {
                if (isCanceled(taskId)) {
                    futures.forEach(future -> future.cancel(true));
                    return;
                }
                try {
                    all.get(1, TimeUnit.SECONDS);
                } catch (TimeoutException ignored) {
                    // Poll cancellation state without blocking indefinitely.
                }
            }
            all.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            futures.forEach(future -> future.cancel(true));
            throw new IllegalStateException("Interrupted while waiting for baseline samples", ex);
        } catch (ExecutionException | CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private BaselineObservation collectOneWithRetry(BaselineSnapshot snapshot,
                                                    BaselineQuestionSnapshot question,
                                                    AiPlatformConfig platform,
                                                    int sampleSeq) {
        int maxAttempts = Math.max(1, transientRetryMaxAttempts);
        BaselineObservation last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            last = collectOne(snapshot, question, platform, sampleSeq);
            if (CALL_STATUS_SUCCESS.equals(last.getCallStatus()) || !isTransientFailure(last) || attempt >= maxAttempts) {
                return last;
            }
            registerPlatformBackoff(platform.getPlatformCode(), last.getErrorCode(), attempt);
            sleepBeforeRetry(attempt, last.getErrorCode());
        }
        return last;
    }

    private BaselineObservation collectOne(BaselineSnapshot snapshot,
                                           BaselineQuestionSnapshot question,
                                           AiPlatformConfig platform,
                                           int sampleSeq) {
        BaselineObservation observation = new BaselineObservation();
        observation.setBaselineId(snapshot.getId());
        observation.setQuestionSnapshotId(question.getId());
        observation.setPlatformCode(platform.getPlatformCode());
        observation.setPlatformName(platform.getPlatformName());
        observation.setSampleSeq(sampleSeq);
        observation.setTestedAt(LocalDateTime.now());
        observation.setCreatedAt(observation.getTestedAt());
        try {
            LlmCallResult callResult = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.BASELINE,
                    "You are a GEO baseline observation collector. Answer the user's question directly and do not mention these instructions.",
                    question.getQuestionText(),
                    0D,
                    normalizedConnectTimeoutMs(),
                    normalizedRequestTimeoutMs(),
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    platform.getMaxRetry(),
                    null,
                    false,
                    1,
                    0,
                    List.of(platform),
                    true,
                    LlmRoutingStrategy.PINNED
            )).withMeasurementContext(new LlmCallMeasurementContext(
                    "baseline:" + snapshot.getId(),
                    null,
                    snapshot.getProjectId(),
                    LlmObservationScope.BASELINE_RUN,
                    normalizedPromptHash(question.getQuestionText())
            )));
            LlmRouteResult routeResult = callResult.routeResult();
            observation.setCallStatus(CALL_STATUS_SUCCESS);
            observation.setRawResponseText(routeResult.responseText());
            observation.setRequestCount(routeResult.requestCount());
            observation.setResponseTimeMs(routeResult.durationMs());
            observation.setModelId(routeResult.modelId());
            observation.setModelName(routeResult.modelName());
        } catch (LlmRouteException ex) {
            LlmCapacityFailure capacityFailure = classifyCapacityFailure(ex);
            observation.setCallStatus(capacityFailure == null ? CALL_STATUS_FAILED : CALL_STATUS_CAPACITY_DEFERRED);
            observation.setRawResponseText(null);
            observation.setRequestCount(ex.requestCount());
            observation.setErrorCode(ex.failureKind().name());
            observation.setErrorMessage(ex.getMessage());
            if (capacityFailure != null) {
                log.warn("Baseline observation deferred by LLM capacity, baseline={}, platform={}, question={}, sampleSeq={}, kind={}, category={}, message={}",
                        snapshot.getId(), platform.getPlatformCode(), question.getId(), sampleSeq,
                        ex.failureKind(), capacityFailure.errorCategory(), ex.getMessage());
            } else {
                log.warn("Baseline observation failed, baseline={}, platform={}, question={}, sampleSeq={}",
                        snapshot.getId(), platform.getPlatformCode(), question.getId(), sampleSeq, ex);
            }
        } catch (Exception ex) {
            LlmCapacityFailure capacityFailure = classifyCapacityFailure(ex);
            observation.setCallStatus(capacityFailure == null ? CALL_STATUS_FAILED : CALL_STATUS_CAPACITY_DEFERRED);
            observation.setRawResponseText(null);
            observation.setRequestCount(0);
            observation.setErrorCode(capacityFailure == null || capacityFailure.errorCategory() == null
                    ? "UNKNOWN"
                    : capacityFailure.errorCategory().name());
            observation.setErrorMessage(ex.getMessage());
            if (capacityFailure != null) {
                log.warn("Baseline observation deferred by LLM capacity, baseline={}, platform={}, question={}, sampleSeq={}, category={}, message={}",
                        snapshot.getId(), platform.getPlatformCode(), question.getId(), sampleSeq,
                        capacityFailure.errorCategory(), ex.getMessage());
            } else {
                log.warn("Baseline observation failed, baseline={}, platform={}, question={}, sampleSeq={}",
                        snapshot.getId(), platform.getPlatformCode(), question.getId(), sampleSeq, ex);
            }
        }
        return observation;
    }

    private void persistObservation(Long taskId,
                                    BaselineSnapshot snapshot,
                                    BaselineQuestionSnapshot question,
                                    Project project,
                                    List<String> aliases,
                                    List<BaselineObservationScoringRules.CompetitorName> competitors,
                                    BaselineObservation observation,
                                    BaselineSemanticJudgeResult judgeResult) {
        Semaphore semaphore = dbWriteSemaphore();
        acquireDbPermit(semaphore);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
                if (task == null || BaselineObservationCollectionService.TASK_STATUS_CANCELED.equals(task.getStatus())) {
                    return;
                }
                try {
                    baselineObservationMapper.insert(observation);
                } catch (DuplicateKeyException duplicate) {
                    log.info("Baseline observation already exists, baseline={}, platform={}, question={}, sampleSeq={}",
                            snapshot.getId(), observation.getPlatformCode(), question.getId(), observation.getSampleSeq());
                    return;
                }
                if (CALL_STATUS_SUCCESS.equals(observation.getCallStatus())) {
                    if (judgeResult == null) {
                        throw new IllegalStateException("Baseline semantic judge result is required before persistence");
                    }
                    BaselineSemanticJudgeResult finalJudgeResult = judgeResult;
                    BaselineObservationScore score = buildScore(snapshot, observation, finalJudgeResult);
                    baselineObservationScoreMapper.insert(score);
                    int competitorMentionCount = persistCompetitorMentions(snapshot, observation, finalJudgeResult);
                    persistHighlightSpans(observation, finalJudgeResult);
                    baselineCollectionTaskMapper.incrementProgress(taskId, 1, 0, 1, competitorMentionCount);
                } else {
                    baselineCollectionTaskMapper.incrementProgress(taskId, 0, 1, 0, 0);
                }
            });
        } finally {
            semaphore.release();
        }
    }

    private BaselineObservationScore buildScore(BaselineSnapshot snapshot,
                                                BaselineObservation observation,
                                                BaselineSemanticJudgeResult result) {
        BaselineObservationScore score = new BaselineObservationScore();
        score.setBaselineId(snapshot.getId());
        score.setObservationId(observation.getId());
        score.setAlgorithmVersion(SCORE_ALGORITHM_VERSION);
        score.setMentioned(result.isMentioned());
        score.setRecommended(result.isRecommended());
        score.setRankingPosition(result.getRankingPosition());
        score.setSentiment(result.getSentiment());
        score.setImpressionState(result.getImpressionState());
        score.setMentionType(result.getMentionType());
        score.setJudgeEvidence(buildJudgeEvidence(result));
        score.setCreatedAt(LocalDateTime.now());
        return score;
    }

    private int persistCompetitorMentions(BaselineSnapshot snapshot,
                                          BaselineObservation observation,
                                          BaselineSemanticJudgeResult result) {
        List<BaselineSemanticJudgeResult.EntityHit> hits = result == null ? List.of() : result.getCompetitorHits();
        int count = 0;
        for (BaselineSemanticJudgeResult.EntityHit hit : hits) {
            if (!StringUtils.hasText(hit.getCanonicalName())) {
                continue;
            }
            BaselineCompetitorMention mention = new BaselineCompetitorMention();
            mention.setBaselineId(snapshot.getId());
            mention.setObservationId(observation.getId());
            mention.setAlgorithmVersion(COMPETITOR_NORMALIZATION_VERSION);
            mention.setCompetitorId(hit.getEntityId());
            mention.setNormalizedName(hit.getCanonicalName());
            mention.setRawText(StringUtils.hasText(hit.getRawText()) ? hit.getRawText() : hit.getCanonicalName());
            mention.setMentionCount(Math.max(1, hit.getMentionCount()));
            mention.setTracked(hit.isTracked());
            mention.setStartOffset(hit.getStartOffset());
            mention.setEndOffset(hit.getEndOffset());
            mention.setCreatedAt(LocalDateTime.now());
            baselineCompetitorMentionMapper.insert(mention);
            count++;
        }
        return count;
    }

    private void persistHighlightSpans(BaselineObservation observation,
                                       BaselineSemanticJudgeResult result) {
        if (result == null) {
            return;
        }
        insertHighlightSpan(observation.getId(), result.getBrandHit(), "BRAND");
        for (BaselineSemanticJudgeResult.EntityHit hit : result.getCompetitorHits()) {
            insertHighlightSpan(observation.getId(), hit, "COMPETITOR");
        }
        for (BaselineSemanticJudgeResult.EntityHit hit : result.getNegativeHits()) {
            insertHighlightSpan(observation.getId(), hit, "NEGATIVE");
        }
    }

    private void insertHighlightSpan(Long observationId,
                                     BaselineSemanticJudgeResult.EntityHit hit,
                                     String type) {
        if (hit == null || !hit.hasOffset() || !StringUtils.hasText(hit.getRawText())) {
            return;
        }
        BaselineHighlightSpan span = new BaselineHighlightSpan();
        span.setObservationId(observationId);
        span.setAlgorithmVersion(HIGHLIGHT_ALGORITHM_VERSION);
        span.setType(type);
        span.setText(hit.getRawText());
        span.setStartOffset(hit.getStartOffset());
        span.setEndOffset(hit.getEndOffset());
        span.setNormalizedEntityId(hit.getEntityId());
        span.setCreatedAt(LocalDateTime.now());
        baselineHighlightSpanMapper.insert(span);
    }

    private String buildJudgeEvidence(BaselineSemanticJudgeResult result) {
        if (result == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(result.getJudgeEvidence())) {
            parts.add(result.getJudgeEvidence());
        }
        parts.add(result.isJudgeUsed() ? "语义裁判" : "规则兜底");
        if (StringUtils.hasText(result.getJudgeError())) {
            parts.add("裁判降级: " + result.getJudgeError());
        }
        return String.join("；", parts);
    }

    private void finishTask(Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
            if (task == null || BaselineObservationCollectionService.TASK_STATUS_CANCELED.equals(task.getStatus())) {
                return;
            }
            int completed = nullToZero(task.getSuccessObservationCount()) + nullToZero(task.getFailedObservationCount());
            if (completed < nullToZero(task.getTotalObservationCount())) {
                task.setStatus(BaselineObservationCollectionService.TASK_STATUS_PARTIAL_FAILED);
                task.setErrorMessage("采集任务提前结束，仍有未完成样本");
            } else if (nullToZero(task.getFailedObservationCount()) > 0) {
                task.setStatus(BaselineObservationCollectionService.TASK_STATUS_PARTIAL_FAILED);
            } else {
                task.setStatus(BaselineObservationCollectionService.TASK_STATUS_COMPLETED);
            }
            task.setFinishedAt(LocalDateTime.now());
            task.setUpdatedAt(task.getFinishedAt());
            baselineCollectionTaskMapper.updateById(task);
        });
    }

    private void failTask(Long taskId, Exception ex) {
        transactionTemplate.executeWithoutResult(status -> {
            BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            if (BaselineObservationCollectionService.TASK_STATUS_CANCELED.equals(task.getStatus())) {
                return;
            }
            task.setStatus(BaselineObservationCollectionService.TASK_STATUS_FAILED);
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            task.setUpdatedAt(task.getFinishedAt());
            baselineCollectionTaskMapper.updateById(task);
        });
    }

    private boolean isCanceled(Long taskId) {
        BaselineCollectionTask task = baselineCollectionTaskMapper.selectById(taskId);
        return task == null || BaselineObservationCollectionService.TASK_STATUS_CANCELED.equals(task.getStatus());
    }

    private boolean observationExists(Long baselineId, Long questionSnapshotId, String platformCode, int sampleSeq) {
        return baselineObservationMapper.selectCount(new LambdaQueryWrapper<BaselineObservation>()
                .eq(BaselineObservation::getBaselineId, baselineId)
                .eq(BaselineObservation::getQuestionSnapshotId, questionSnapshotId)
                .eq(BaselineObservation::getPlatformCode, platformCode)
                .eq(BaselineObservation::getSampleSeq, sampleSeq)) > 0;
    }

    private List<AiPlatformConfig> loadPlatforms(BaselineCollectionTask task) {
        List<String> platformCodes = JSONUtil.parseArray(task.getSelectedPlatformCodesJson()).stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .toList();
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                .isNotNull(AiPlatformConfig::getLowModelId)
                .apply("TRIM(low_model_id) <> ''")
                .in(AiPlatformConfig::getPlatformCode, platformCodes));
        Map<String, AiPlatformConfig> map = platforms.stream().collect(Collectors.toMap(
                AiPlatformConfig::getPlatformCode,
                this::useLowPerformanceModel,
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        return platformCodes.stream().map(map::get).filter(java.util.Objects::nonNull).toList();
    }

    private AiPlatformConfig useLowPerformanceModel(AiPlatformConfig platform) {
        platform.setModelId(platform.getLowModelId().trim());
        return platform;
    }

    private List<String> parseAliases(String aliasesJson) {
        if (!StringUtils.hasText(aliasesJson)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(aliasesJson).stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return Arrays.stream(aliasesJson.split("[,，]"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
    }

    private List<BaselineObservationScoringRules.CompetitorName> loadCompetitors(Long baselineId) {
        List<BaselineCompetitorSource> sources = baselineCompetitorSourceMapper.selectList(new LambdaQueryWrapper<BaselineCompetitorSource>()
                .eq(BaselineCompetitorSource::getBaselineId, baselineId));
        return sources.stream()
                .filter(source -> StringUtils.hasText(source.getCompetitorName()))
                .map(source -> new BaselineObservationScoringRules.CompetitorName(
                        source.getCompetitorId(),
                        source.getCompetitorName().trim(),
                        parseAliases(source.getAliasesJson()),
                        !"REJECTED".equals(source.getReviewStatus())
                ))
                .toList();
    }

    private Semaphore dbWriteSemaphore() {
        Semaphore local = dbWriteSemaphore;
        if (local == null) {
            synchronized (this) {
                local = dbWriteSemaphore;
                if (local == null) {
                    local = new Semaphore(Math.max(1, dbWriteMaxConcurrency));
                    dbWriteSemaphore = local;
                }
            }
        }
        return local;
    }

    private void acquireDbPermit(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for baseline DB write permit", ex);
        }
    }

    private boolean isTransientFailure(BaselineObservation observation) {
        String errorCode = observation.getErrorCode();
        return CALL_STATUS_CAPACITY_DEFERRED.equals(observation.getCallStatus())
                || "ALL_RATE_LIMITED".equals(errorCode)
                || "ALL_PERMIT_BUSY".equals(errorCode)
                || "ALL_CIRCUIT_OPEN".equals(errorCode)
                || isRetryableAllFailed(observation)
                || "UNKNOWN".equals(errorCode);
    }

    private int normalizedConnectTimeoutMs() {
        return modelConnectTimeoutMs <= 0
                ? LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS
                : modelConnectTimeoutMs;
    }

    private int normalizedRequestTimeoutMs() {
        if (modelRequestTimeoutMs <= 0) {
            return LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS;
        }
        return Math.min(modelRequestTimeoutMs, LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
    }

    private void sleepBeforeRetry(int attempt) {
        sleepBeforeRetry(attempt, null);
    }

    private void sleepBeforeRetry(int attempt, String errorCode) {
        long base = "ALL_CIRCUIT_OPEN".equals(errorCode)
                ? Math.max(1_000L, llmPoolProperties.getCircuitBreakerOpenDurationMs())
                : "ALL_RATE_LIMITED".equals(errorCode)
                ? RATE_LIMIT_BACKOFF_MS
                : Math.max(100L, transientRetryInitialBackoffMs);
        long cap = Math.max(base, transientRetryMaxBackoffMs);
        long exponential = base * (1L << Math.min(8, Math.max(0, attempt - 1)));
        long backoff = Math.min(cap, exponential);
        long jitter = ThreadLocalRandom.current().nextLong(0L, Math.max(1L, backoff / 3L));
        try {
            TimeUnit.MILLISECONDS.sleep(backoff + jitter);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for baseline retry backoff", ex);
        }
    }

    private boolean isResourceBackpressure(LlmRouteFailureKind failureKind) {
        return failureKind == LlmRouteFailureKind.ALL_PERMIT_BUSY
                || failureKind == LlmRouteFailureKind.ALL_RATE_LIMITED
                || failureKind == LlmRouteFailureKind.ALL_CIRCUIT_OPEN;
    }

    private LlmCapacityFailure classifyCapacityFailure(Throwable error) {
        if (!capacityFailureDeferEnabled) {
            return null;
        }
        LlmCapacityFailure classified = capacityFailureClassifier.classify(error).orElse(null);
        if (classified != null) {
            return classified;
        }
        if (error instanceof LlmRouteException routeException && isResourceBackpressure(routeException.failureKind())) {
            return new LlmCapacityFailure(null, null, routeException.failureKind().name(), routeException.getClass().getSimpleName());
        }
        return null;
    }

    private boolean isRetryableAllFailed(BaselineObservation observation) {
        if (!"ALL_FAILED".equals(observation.getErrorCode())) {
            return false;
        }
        String message = observation.getErrorMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("connection reset")
                || lower.contains("connection refused")
                || lower.contains("http 429")
                || lower.contains("http 5");
    }

    private void awaitPlatformBackoff(String platformCode) {
        PlatformBackoffState state = platformBackoffStates.get(platformCode);
        if (state == null) {
            return;
        }
        long delayMs = state.delayMillis();
        if (delayMs <= 0L) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs + ThreadLocalRandom.current().nextLong(100L, 500L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for baseline platform backoff", ex);
        }
    }

    private void registerPlatformBackoff(String platformCode, String errorCode, int attempt) {
        long backoffMs = switch (errorCode == null ? "" : errorCode) {
            case "ALL_CIRCUIT_OPEN" -> Math.max(1_000L, llmPoolProperties.getCircuitBreakerOpenDurationMs());
            case "ALL_RATE_LIMITED" -> RATE_LIMIT_BACKOFF_MS;
            case "ALL_PERMIT_BUSY" -> Math.max(500L, transientRetryInitialBackoffMs);
            case "ALL_FAILED", "UNKNOWN" -> UNKNOWN_BACKOFF_MS;
            default -> 0L;
        };
        if (backoffMs <= 0L) {
            return;
        }
        long exponential = backoffMs * (1L << Math.min(4, Math.max(0, attempt - 1)));
        long cap = Math.max(backoffMs, Math.max(transientRetryMaxBackoffMs, llmPoolProperties.getCircuitBreakerOpenDurationMs()));
        long jitter = ThreadLocalRandom.current().nextLong(0L, Math.max(1L, backoffMs / 5L));
        long until = System.currentTimeMillis() + Math.min(cap, exponential) + jitter;
        platformBackoffStates.computeIfAbsent(platformCode, ignored -> new PlatformBackoffState()).backoffUntil(until);
    }

    private static final class PlatformBackoffState {
        private volatile long backoffUntilMillis;

        long delayMillis() {
            return Math.max(0L, backoffUntilMillis - System.currentTimeMillis());
        }

        void backoffUntil(long untilMillis) {
            if (untilMillis > backoffUntilMillis) {
                backoffUntilMillis = untilMillis;
            }
        }
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizedPromptHash(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return null;
        }
        String normalized = prompt.trim().replaceAll("\\s+", " ").toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private int normalizedMaxConcurrentBaselines() {
        return Math.max(1, maxConcurrentBaselines);
    }

    static boolean isFreshRunning(BaselineCollectionTask task, LocalDateTime staleBefore) {
        return BaselineObservationCollectionService.TASK_STATUS_RUNNING.equals(task.getStatus())
                && task.getUpdatedAt() != null
                && task.getUpdatedAt().isAfter(staleBefore);
    }
}
