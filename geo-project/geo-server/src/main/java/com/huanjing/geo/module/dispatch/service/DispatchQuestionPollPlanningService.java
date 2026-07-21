package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.ProjectPollRotation;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchQuestionPollPlanningService {

    static final String BATCH_STATUS_PLANNING = "planning";
    static final String BATCH_STATUS_READY = "ready";
    static final String SHARD_STATUS_READY = "ready";

    private final PollBatchMapper pollBatchMapper;
    private final PollBatchShardMapper pollBatchShardMapper;
    private final PollBatchShardItemMapper pollBatchShardItemMapper;
    private final ProjectPollRotationMapper projectPollRotationMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final DispatchProperties dispatchProperties;
    private final ObjectProvider<DispatchTaskService> dispatchTaskServiceProvider;

    @Transactional
    public PollBatch planProjectTierPoll(Project project,
                                         LocalDate batchDate,
                                         LocalDate windowStart,
                                         String questionTier,
                                         int batchNo) {
        PollBatch existing = findBatch(project.getId(), batchDate, batchNo, questionTier);
        if (existing != null && existing.getTotalShardCount() != null && existing.getTotalShardCount() > 0) {
            log.info("Reuse existing question poll shard plan, projectId={}, tier={}, batchDate={}, batchNo={}, batchId={}",
                    project.getId(), questionTier, batchDate, batchNo, existing.getId());
            return existing;
        }

        List<PollKeywordCandidate> allKeywords = loadProjectPollKeywords(project.getId(), questionTier);
        if (allKeywords.isEmpty()) {
            log.info("Skip question poll planning because no enabled {} tier monitoring questions, projectId={}",
                    questionTier, project.getId());
            return existing;
        }
        List<AiPlatformConfig> platforms = resolveQuestionPollPlatformCandidates();
        if (platforms.isEmpty()) {
            log.info("Skip question poll planning because no question-poll platform configured, projectId={}", project.getId());
            return existing;
        }

        int planCap = resolveTierPollLimit(project, questionTier);
        int takeCount = resolveDailyTakeCount(allKeywords.size(), planCap, questionTier);
        List<PollKeywordCandidate> selected = selectRotatedKeywords(project.getId(), questionTier, allKeywords, takeCount);
        return createPlannedBatch(
                existing,
                project,
                batchDate,
                windowStart,
                questionTier,
                batchNo,
                "SCHEDULED",
                null,
                null,
                null,
                null,
                platforms,
                selected
        );
    }

    @Transactional
    public PollBatch planManualProjectTierPoll(Project project,
                                               LocalDate batchDate,
                                               String questionTier,
                                               int batchNo,
                                               List<AiPlatformConfig> platforms,
                                               int questionLimit,
                                               Long createdBy,
                                               String clientRequestId,
                                               String requestFingerprint) {
        List<PollKeywordCandidate> allKeywords = loadProjectPollKeywords(project.getId(), questionTier);
        if (allKeywords.isEmpty()) {
            throw new com.huanjing.geo.common.exception.BizException(
                    400, "当前项目没有启用的 " + questionTier + " 类监测问题", 400, null
            );
        }
        int effectiveLimit = Math.max(1, Math.min(questionLimit, allKeywords.size()));
        List<PollKeywordCandidate> selected = new ArrayList<>(allKeywords.subList(0, effectiveLimit));
        return createPlannedBatch(
                null,
                project,
                batchDate,
                batchDate,
                questionTier,
                batchNo,
                "MANUAL",
                createdBy,
                clientRequestId,
                requestFingerprint,
                effectiveLimit,
                platforms,
                selected
        );
    }

    private PollBatch createPlannedBatch(PollBatch existing,
                                         Project project,
                                         LocalDate batchDate,
                                         LocalDate windowStart,
                                         String questionTier,
                                         int batchNo,
                                         String triggerType,
                                         Long createdBy,
                                         String clientRequestId,
                                         String requestFingerprint,
                                         Integer manualQuestionLimit,
                                         List<AiPlatformConfig> platforms,
                                         List<PollKeywordCandidate> selected) {
        int shardSize = resolveEffectiveShardSize();
        int shardCountPerPlatform = (int) Math.ceil(selected.size() / (double) shardSize);
        int totalShardCount = shardCountPerPlatform * platforms.size();

        PollBatch batch = existing == null ? new PollBatch() : existing;
        batch.setDispatchTaskId(null);
        batch.setProjectId(project.getId());
        batch.setBatchDate(batchDate);
        batch.setBatchNo(batchNo);
        batch.setQuestionTier(questionTier);
        batch.setTriggerType(triggerType);
        batch.setCreatedBy(createdBy);
        batch.setClientRequestId(clientRequestId);
        batch.setRequestFingerprint(requestFingerprint);
        batch.setManualQuestionLimit(manualQuestionLimit);
        batch.setTriggeredAt(LocalDateTime.now());
        batch.setPlanningStartedAt(LocalDateTime.now());
        batch.setReadyAt(null);
        batch.setFinishedAt(null);
        batch.setStatus(BATCH_STATUS_PLANNING);
        batch.setTotalQuestionCount(selected.size());
        batch.setTotalPlatformCount(platforms.size());
        batch.setTotalShardCount(totalShardCount);
        batch.setCompletedShardCount(0);
        batch.setQuestionCount(0);
        batch.setCompletedCount(0);
        batch.setFailedCount(0);
        batch.setHitCount(0);
        batch.setOverallHitRate(BigDecimal.ZERO);
        if (batch.getId() == null) {
            pollBatchMapper.insert(batch);
        } else {
            pollBatchMapper.updateById(batch);
        }

        List<DispatchTask> shardTasks = createShardsAndTasks(batch, project, windowStart, batchDate, batchNo,
                questionTier, platforms, selected, shardSize);

        batch.setStatus(BATCH_STATUS_READY);
        batch.setReadyAt(LocalDateTime.now());
        pollBatchMapper.updateById(batch);
        enqueueAfterCommit(shardTasks);
        return batch;
    }

    @Transactional
    public PollBatch planFromLegacyTask(DispatchTask task, Project project, LocalDate batchDate, int batchNo, String questionTier) {
        return planProjectTierPoll(project, batchDate,
                task == null || task.getWindowStart() == null ? batchDate : task.getWindowStart(), questionTier, batchNo);
    }

    int resolveEffectiveShardSize() {
        int configured = Math.max(1, dispatchProperties.getQuestionPollShardSize());
        int maxAllowed = Math.max(1, dispatchProperties.getQuestionPollMaxShardSize());
        int effective = Math.min(configured, maxAllowed);
        if (configured != effective) {
            log.warn("Question poll shard size capped, configured={}, maxAllowed={}, effective={}",
                    configured, maxAllowed, effective);
        }
        return effective;
    }

    int resolveDailyTakeCount(int deduplicatedKeywordCount, int planCap, String questionTier) {
        int total = Math.max(0, deduplicatedKeywordCount);
        if (total == 0) {
            return 0;
        }
        int effectiveTotal = planCap > 0 ? Math.min(total, planCap) : total;
        if (!"A".equalsIgnoreCase(questionTier)) {
            return effectiveTotal;
        }
        int cycleDays = Math.max(1, dispatchProperties.getQuestionPollCycleDays());
        if (cycleDays <= 1) {
            return effectiveTotal;
        }
        return Math.max(1, (int) Math.ceil(effectiveTotal / (double) cycleDays));
    }

    private List<DispatchTask> createShardsAndTasks(PollBatch batch,
                                                    Project project,
                                                    LocalDate windowStart,
                                                    LocalDate batchDate,
                                                    int batchNo,
                                                    String questionTier,
                                                    List<AiPlatformConfig> platforms,
                                                    List<PollKeywordCandidate> selected,
                                                    int shardSize) {
        DispatchTaskService dispatchTaskService = dispatchTaskServiceProvider.getObject();
        List<DispatchTask> shardTasks = new ArrayList<>();
        int shardNoBase = 1;
        for (AiPlatformConfig platform : platforms) {
            int shardNo = shardNoBase;
            for (int from = 0; from < selected.size(); from += shardSize) {
                int to = Math.min(from + shardSize, selected.size());
                PollBatchShard shard = new PollBatchShard();
                shard.setBatchId(batch.getId());
                shard.setProjectId(project.getId());
                shard.setPlatformId(platform.getId());
                shard.setPlatformCode(platform.getPlatformCode());
                shard.setChannelCode(StringUtils.hasText(platform.getChannelCode())
                        ? platform.getChannelCode() : platform.getPlatformCode());
                shard.setPlatformName(platform.getPlatformName());
                shard.setBatchDate(batchDate);
                shard.setBatchNo(batchNo);
                shard.setQuestionTier(questionTier);
                shard.setShardNo(shardNo);
                shard.setStatus(SHARD_STATUS_READY);
                shard.setExpectedCount(to - from);
                shard.setCompletedCount(0);
                shard.setFailedCount(0);
                shard.setResourceWaitCount(0);
                pollBatchShardMapper.insert(shard);

                for (int i = from; i < to; i++) {
                    PollKeywordCandidate keyword = selected.get(i);
                    PollBatchShardItem item = new PollBatchShardItem();
                    item.setShardId(shard.getId());
                    item.setBatchId(batch.getId());
                    item.setKeywordResultId(keyword.keywordResultId());
                    item.setKeywordTextSnapshot(keyword.keywordText());
                    item.setSortOrder(i + 1);
                    item.setStatus("pending");
                    pollBatchShardItemMapper.insert(item);
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("mode", "question-poll-shard");
                payload.put("shardId", shard.getId());
                payload.put("batchId", batch.getId());
                payload.put("triggerType", batch.getTriggerType());
                DispatchTask task = dispatchTaskService.createTaskWithoutEnqueue(
                        project.getId(),
                        DispatchTaskType.QUESTION_POLL,
                        windowStart,
                        batchDate,
                        LocalDateTime.now(),
                        payload,
                        "question-poll:" + questionTier + ":" + batchDate + ":" + batchNo
                                + ":platform:" + platform.getId() + ":shard:" + shardNo,
                        null,
                        null
                );
                task.setPlatformCode(platform.getPlatformCode());
                dispatchTaskService.updateTaskPlatform(task);
                shard.setDispatchTaskId(task.getId());
                pollBatchShardMapper.updateById(shard);
                shardTasks.add(task);
                shardNo++;
            }
        }
        return shardTasks;
    }

    private void enqueueAfterCommit(List<DispatchTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        DispatchTaskService dispatchTaskService = dispatchTaskServiceProvider.getObject();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchTaskService.enqueueQuestionPollShardTasksWithStagger(tasks);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchTaskService.enqueueQuestionPollShardTasksWithStagger(tasks);
            }
        });
    }

    private PollBatch findBatch(Long projectId, LocalDate batchDate, int batchNo, String questionTier) {
        return pollBatchMapper.selectOne(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getProjectId, projectId)
                        .eq(PollBatch::getBatchDate, batchDate)
                        .eq(PollBatch::getBatchNo, batchNo)
                        .eq(PollBatch::getQuestionTier, questionTier)
                        .last("LIMIT 1")
        );
    }

    private List<AiPlatformConfig> resolveQuestionPollPlatformCandidates() {
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .eq(AiPlatformConfig::getUsageScene, "QUESTION_POLL_WEB")
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
        );
    }

    private int resolveTierPollLimit(Project project, String questionTier) {
        Integer limit = switch (questionTier) {
            case "A" -> project.getPlanKeywordGroupLimitA();
            case "B" -> project.getPlanKeywordGroupLimitB();
            case "C" -> project.getPlanKeywordGroupLimitC();
            default -> null;
        };
        if (limit != null && limit > 0) {
            return limit;
        }
        if ("A".equals(questionTier) && project.getPlanKeywordGroupLimit() != null && project.getPlanKeywordGroupLimit() > 0) {
            return project.getPlanKeywordGroupLimit();
        }
        return 0;
    }

    private List<PollKeywordCandidate> loadProjectPollKeywords(Long projectId, String questionTier) {
        List<Long> groupIds = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .select(ProjectKeywordGroupRel::getKeywordGroupId)
        ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).filter(Objects::nonNull).distinct().toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }

        List<KeywordGroupResult> results = keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .eq(KeywordGroupResult::getQuestionTier, questionTier)
                        .eq(KeywordGroupResult::getPollingEnabled, true)
                        .orderByAsc(KeywordGroupResult::getId)
        );
        Map<String, PollKeywordCandidate> deduplicated = new LinkedHashMap<>();
        for (KeywordGroupResult result : results) {
            String keywordText = normalizeKeywordText(result.getKeywordText());
            if (!StringUtils.hasText(keywordText)) {
                continue;
            }
            deduplicated.putIfAbsent(normalizeKeywordKey(keywordText), new PollKeywordCandidate(result.getId(), keywordText));
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<PollKeywordCandidate> selectRotatedKeywords(Long projectId,
                                                             String layer,
                                                             List<PollKeywordCandidate> source,
                                                             int takeCount) {
        ProjectPollRotation rotation = ensureRotation(projectId, layer);
        int size = source.size();
        int offset = rotation.getRotationOffset() == null ? 0 : rotation.getRotationOffset();
        int normalizedOffset = Math.floorMod(offset, size);
        int normalizedTakeCount = Math.max(1, Math.min(takeCount, size));

        List<PollKeywordCandidate> picked = new ArrayList<>();
        for (int i = 0; i < normalizedTakeCount; i++) {
            picked.add(source.get((normalizedOffset + i) % size));
        }
        rotation.setRotationOffset((normalizedOffset + normalizedTakeCount) % size);
        projectPollRotationMapper.updateById(rotation);
        return picked;
    }

    private ProjectPollRotation ensureRotation(Long projectId, String layer) {
        ProjectPollRotation rotation = projectPollRotationMapper.selectForUpdate(projectId, layer);
        if (rotation != null) {
            return rotation;
        }
        ProjectPollRotation created = new ProjectPollRotation();
        created.setProjectId(projectId);
        created.setPriorityLevel(layer);
        created.setRotationOffset(0);
        projectPollRotationMapper.insert(created);
        return projectPollRotationMapper.selectForUpdate(projectId, layer);
    }

    private String normalizeKeywordText(String keywordText) {
        return keywordText == null ? null : keywordText.trim();
    }

    private String normalizeKeywordKey(String keywordText) {
        return normalizeKeywordText(keywordText).toLowerCase(Locale.ROOT);
    }

    private record PollKeywordCandidate(Long keywordResultId, String keywordText) {
    }
}
