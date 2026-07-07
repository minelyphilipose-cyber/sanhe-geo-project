package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.BatchArticlePublishItem;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionBatch;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionItem;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionPlan;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionBatchMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionItemMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionPlanMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentAutoDistributionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int GENERATION_BATCH_LIMIT = 30;
    private static final Set<String> ACTIVE_BATCH_STATUSES = Set.of("created", "generating", "publish_scheduled");
    private static final Set<String> ACTIVE_ARTICLE_STATUSES = Set.of("approved", "unpublished");
    private static final Map<String, String> QUOTA_TO_GENERATION_GROUP = Map.of(
            "official_site", ArticlePromptChannels.AGENT_SITE,
            "industry_site", ArticlePromptChannels.INDUSTRY_SITE,
            "forum", ArticlePromptChannels.FORUM
    );
    private static final Set<String> PLANNABLE_CHANNEL_CODES = plannableChannelCodes();

    private final ProjectMapper projectMapper;
    private final ProjectChannelAllocationMapper allocationMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final SysUserMapper sysUserMapper;
    private final ContentAutoDistributionBatchMapper batchMapper;
    private final ContentAutoDistributionItemMapper itemMapper;
    private final ContentAutoDistributionPlanMapper planMapper;
    private final BatchArticleGenerationTaskMapper generationTaskMapper;
    private final BatchArticlePublishItemMapper publishItemMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final SelfMediaPublishScheduleMapper selfMediaPublishScheduleMapper;
    private final BatchArticleGenerationService generationService;
    private final BatchArticlePublishService publishService;
    private final SelfMediaPublishScheduleService selfMediaPublishScheduleService;
    private final SelfMediaScheduleCapabilityService selfMediaScheduleCapabilityService;
    private final BrowserEnvironmentService browserEnvironmentService;
    private final SystemAlertService systemAlertService;
    private final ForumBoardRoutingService forumBoardRoutingService;
    private final StringRedisTemplate redisTemplate;

    @Value("${geo.content.auto-distribution.enabled:true}")
    private boolean enabled;
    @Value("${geo.content.auto-distribution.lock-key:geo:content:auto-distribution:daily-lock}")
    private String dailyLockKey;
    @Value("${geo.content.auto-distribution.progress-lock-key:geo:content:auto-distribution:progress-lock}")
    private String progressLockKey;
    @Value("${geo.content.auto-distribution.lock-ttl-seconds:1800}")
    private long lockTtlSeconds;
    @Value("${geo.content.auto-distribution.publish-window-start:01:00}")
    private String publishWindowStart;
    @Value("${geo.content.auto-distribution.publish-window-end:23:00}")
    private String publishWindowEnd;
    @Value("${geo.content.auto-distribution.publish-time-jitter-minutes:15}")
    private int jitterMinutes;
    @Value("${geo.content.auto-distribution.operator-user-id:0}")
    private long configuredOperatorUserId;
    @Value("${geo.content.auto-distribution.stale-generation-timeout-hours:24}")
    private long staleGenerationTimeoutHours;
    @Value("${geo.content.auto-distribution.plan-window-start:00:30}")
    private String planWindowStart;
    @Value("${geo.content.auto-distribution.plan-window-end:18:00}")
    private String planWindowEnd;
    @Value("${geo.content.auto-distribution.plan-jitter-minutes:10}")
    private int planJitterMinutes;
    @Value("${geo.content.auto-distribution.plan-worker-batch-size:10}")
    private int planWorkerBatchSize;
    @Value("${geo.content.auto-distribution.plan-retry-max-count:3}")
    private int planRetryMaxCount;

    @Scheduled(cron = "${geo.content.auto-distribution.cron:0 0 1 * * ?}", zone = "Asia/Shanghai")
    public void runDailyPlan() {
        if (!enabled) {
            return;
        }
        withLock(dailyLockKey, () -> createDailyProjectPlans(LocalDate.now(BUSINESS_ZONE)));
    }

    @Scheduled(fixedDelayString = "${geo.content.auto-distribution.plan-worker-fixed-delay-ms:60000}")
    public void runDueDailyPlans() {
        if (!enabled) {
            return;
        }
        int processed = processDueDailyProjectPlans(planWorkerBatchSize);
        if (processed > 0) {
            log.info("auto distribution staggered daily plans processed count={}", processed);
        }
    }

    @Scheduled(fixedDelayString = "${geo.content.auto-distribution.progress-poll-ms:60000}")
    public void progressPlans() {
        if (!enabled) {
            return;
        }
        withLock(progressLockKey, this::progressActivePlans);
    }

    public void createDailyPlan(LocalDate planDate) {
        List<Project> projects = selectDailyPlanProjects();
        for (Project project : projects) {
            try {
                Long operatorId = resolveOperatorUserId(project);
                createProjectPlan(project, planDate, operatorId);
            } catch (Exception ex) {
                log.warn("auto distribution plan failed projectId={} date={} error={}",
                        project.getId(), planDate, ex.getMessage(), ex);
            }
        }
    }

    public int createDailyProjectPlans(LocalDate planDate) {
        List<Project> projects = selectDailyPlanProjects();
        if (projects.isEmpty()) {
            return 0;
        }
        List<LocalDateTime> executeTimes = spreadExecuteTimes(planDate, projects.size(), planWindowStart, planWindowEnd, planJitterMinutes);
        int created = 0;
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            ContentAutoDistributionPlan plan = new ContentAutoDistributionPlan();
            plan.setProjectId(project.getId());
            plan.setCompanyId(project.getCompanyId());
            plan.setBrandId(project.getBrandId());
            plan.setPlanDate(planDate);
            plan.setPlannedExecuteAt(executeTimes.get(i));
            plan.setNextAttemptAt(executeTimes.get(i));
            plan.setStatus("pending");
            plan.setRetryCount(0);
            try {
                planMapper.insert(plan);
                created++;
            } catch (DuplicateKeyException ignored) {
                // Idempotent daily planning: one project can only have one auto plan per date.
            } catch (Exception ex) {
                log.warn("auto distribution staggered plan create failed projectId={} date={} error={}",
                        project.getId(), planDate, ex.getMessage());
            }
        }
        return created;
    }

    public int processDueDailyProjectPlans(int limit) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        List<ContentAutoDistributionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionPlan>()
                        .in(ContentAutoDistributionPlan::getStatus, List.of("pending", "failed"))
                        .le(ContentAutoDistributionPlan::getNextAttemptAt, now)
                        .le(ContentAutoDistributionPlan::getPlannedExecuteAt, now)
                        .lt(ContentAutoDistributionPlan::getRetryCount, Math.max(1, planRetryMaxCount))
                        .orderByAsc(ContentAutoDistributionPlan::getPlannedExecuteAt, ContentAutoDistributionPlan::getId)
                        .last("LIMIT " + Math.max(1, limit))
        );
        int processed = 0;
        for (ContentAutoDistributionPlan plan : plans) {
            String status = plan.getStatus();
            int claimed = planMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionPlan>()
                    .eq(ContentAutoDistributionPlan::getId, plan.getId())
                    .eq(ContentAutoDistributionPlan::getStatus, status)
                    .set(ContentAutoDistributionPlan::getStatus, "running")
                    .set(ContentAutoDistributionPlan::getFailureMessage, null));
            if (claimed <= 0) {
                continue;
            }
            runDailyProjectPlan(plan);
            processed++;
        }
        return processed;
    }

    private List<Project> selectDailyPlanProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, "active")
                .isNull(Project::getDeletedAt)
                .and(wrapper -> wrapper.eq(Project::getContentGenerationEnabled, true)
                        .or()
                        .isNull(Project::getContentGenerationEnabled))
                .orderByAsc(Project::getId));
    }

    private void runDailyProjectPlan(ContentAutoDistributionPlan plan) {
        Project project = projectMapper.selectById(plan.getProjectId());
        if (project == null || project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            markDailyProjectPlanTerminal(plan, "skipped", "项目不存在、已删除或非启用状态");
            return;
        }
        try {
            Long operatorId = resolveOperatorUserId(project);
            createProjectPlan(project, plan.getPlanDate(), operatorId);
            markDailyProjectPlanTerminal(plan, "completed", null);
        } catch (Exception ex) {
            int retryCount = (plan.getRetryCount() == null ? 0 : plan.getRetryCount()) + 1;
            boolean exhausted = retryCount >= Math.max(1, planRetryMaxCount);
            planMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionPlan>()
                    .eq(ContentAutoDistributionPlan::getId, plan.getId())
                    .set(ContentAutoDistributionPlan::getStatus, exhausted ? "failed_terminal" : "failed")
                    .set(ContentAutoDistributionPlan::getRetryCount, retryCount)
                    .set(ContentAutoDistributionPlan::getNextAttemptAt, LocalDateTime.now(BUSINESS_ZONE).plusMinutes(15))
                    .set(ContentAutoDistributionPlan::getFailureMessage, trimError(ex.getMessage())));
        }
    }

    private void markDailyProjectPlanTerminal(ContentAutoDistributionPlan plan, String status, String message) {
        planMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionPlan>()
                .eq(ContentAutoDistributionPlan::getId, plan.getId())
                .set(ContentAutoDistributionPlan::getStatus, status)
                .set(ContentAutoDistributionPlan::getFailureMessage, StringUtils.hasText(message) ? trimError(message) : null));
    }

    @Transactional
    public void createProjectPlan(Project project, LocalDate planDate, Long operatorId) {
        ContentAutoDistributionBatch existingBatch = batchMapper.selectOne(new LambdaQueryWrapper<ContentAutoDistributionBatch>()
                .eq(ContentAutoDistributionBatch::getProjectId, project.getId())
                .eq(ContentAutoDistributionBatch::getPlanDate, planDate)
                .last("LIMIT 1"));
        if (existingBatch != null) {
            createGenerationBatches(existingBatch.getId(), operatorId);
            refreshBatchCounters(existingBatch.getId());
            return;
        }
        List<ProjectChannelAllocation> allocations = allocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, project.getId())
                        .in(ProjectChannelAllocation::getChannelCode, PLANNABLE_CHANNEL_CODES)
                        .gt(ProjectChannelAllocation::getAllocatedCount, 0)
        );
        if (allocations.isEmpty()) {
            return;
        }

        List<PlannedTarget> targetPlans = new ArrayList<>();
        for (ProjectChannelAllocation allocation : allocations) {
            targetPlans.addAll(planChannelTargets(project, allocation));
        }
        int totalCount = targetPlans.stream().mapToInt(PlannedTarget::count).sum();
        if (totalCount <= 0) {
            insertSkippedBatch(project, planDate, unavailableDistributionTargetReason(project, allocations));
            return;
        }

        List<KeywordGroupResult> questions = keywordGroupResultMapper.selectProjectQuestionsByTiers(project.getId(), "'A'");
        if (questions.isEmpty()) {
            insertSkippedBatch(project, planDate, "no A tier question");
            return;
        }
        List<KeywordGroupResult> pickedQuestions = pickQuestions(questions, totalCount);

        ContentAutoDistributionBatch batch = new ContentAutoDistributionBatch();
        batch.setProjectId(project.getId());
        batch.setCompanyId(project.getCompanyId());
        batch.setBrandId(project.getBrandId());
        batch.setPlanDate(planDate);
        batch.setStatus("created");
        batch.setTotalCount(totalCount);
        batch.setGeneratedCount(0);
        batch.setScheduledCount(0);
        batch.setFailedCount(0);
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException ignored) {
            return;
        }

        int questionIndex = 0;
        for (PlannedTarget target : targetPlans) {
            List<LocalDateTime> publishTimes = plannedPublishTimes(planDate, target.count());
            for (int i = 0; i < target.count(); i++) {
                KeywordGroupResult question = pickedQuestions.get(questionIndex++);
                ContentAutoDistributionItem item = new ContentAutoDistributionItem();
                item.setBatchId(batch.getId());
                item.setProjectId(project.getId());
                item.setCompanyId(project.getCompanyId());
                item.setBrandId(project.getBrandId());
                item.setPlanDate(planDate);
                item.setChannelCode(target.channelCode());
                item.setChannelGroupCode(target.channelGroupCode());
                item.setContentStyle(target.contentStyle());
                item.setTargetKind(target.targetKind());
                item.setTargetId(target.targetId());
                item.setTargetName(target.targetName());
                item.setTargetBrandId(target.targetBrandId());
                item.setTargetForumFid(target.targetForumFid());
                item.setSequenceNo(i + 1);
                item.setQuestionId(question.getId());
                item.setQuestionText(question.getKeywordText());
                item.setPlannedPublishAt(publishTimes.get(i));
                item.setStatus("pending_generation");
                itemMapper.insert(item);
            }
        }
        createGenerationBatches(batch.getId(), operatorId);
        refreshBatchCounters(batch.getId());
    }

    private void createGenerationBatches(Long batchId, Long operatorId) {
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getBatchId, batchId)
                        .eq(ContentAutoDistributionItem::getStatus, "pending_generation")
                        .orderByAsc(ContentAutoDistributionItem::getId)
        );
        if (items.isEmpty()) {
            return;
        }
        for (int start = 0; start < items.size(); start += GENERATION_BATCH_LIMIT) {
            List<ContentAutoDistributionItem> chunk = items.subList(start, Math.min(items.size(), start + GENERATION_BATCH_LIMIT));
            BatchArticleGenerateRequest request = buildGenerationRequest(chunk);
            BatchArticleGenerateResponse response = generationService.createSystemBatch(request, operatorId);
            List<BatchArticleGenerationTask> tasks = generationTaskMapper.selectList(
                    new LambdaQueryWrapper<BatchArticleGenerationTask>()
                    .eq(BatchArticleGenerationTask::getBatchId, response.batchId())
                            .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
            );
            for (int i = 0; i < chunk.size() && i < tasks.size(); i++) {
                ContentAutoDistributionItem item = chunk.get(i);
                BatchArticleGenerationTask task = tasks.get(i);
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getGenerationBatchId, response.batchId())
                        .set(ContentAutoDistributionItem::getGenerationTaskId, task.getId())
                        .set(ContentAutoDistributionItem::getStatus, "generating"));
            }
        }
        batchMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionBatch>()
                .eq(ContentAutoDistributionBatch::getId, batchId)
                .set(ContentAutoDistributionBatch::getStatus, "generating"));
    }

    private BatchArticleGenerateRequest buildGenerationRequest(List<ContentAutoDistributionItem> items) {
        BatchArticleGenerateRequest request = new BatchArticleGenerateRequest();
        request.setProjectId(items.get(0).getProjectId());
        request.setTopicSource("manual");
        List<BatchArticleGenerateRequest.TopicConfig> topics = new ArrayList<>();
        for (ContentAutoDistributionItem item : items) {
            BatchArticleGenerateRequest.TopicConfig topic = new BatchArticleGenerateRequest.TopicConfig();
            topic.setTopic(item.getQuestionText());
            topic.setTopicAsQuestion(item.getQuestionText());
            BatchArticleGenerateRequest.PlatformCount platform = new BatchArticleGenerateRequest.PlatformCount();
            platform.setChannelGroupCode(item.getChannelGroupCode());
            platform.setChannelSubCode(resolveGenerationChannelSubCode(item));
            platform.setContentStyle(item.getContentStyle());
            platform.setAllocationMode("auto");
            platform.setCount(1);
            topic.setPlatforms(List.of(platform));
            topics.add(topic);
        }
        request.setTopics(topics);
        return request;
    }

    private String resolveGenerationChannelSubCode(ContentAutoDistributionItem item) {
        String selfMediaPlatform = selfMediaPlatform(item.getChannelCode());
        if (selfMediaPlatform != null) {
            return selfMediaPlatform;
        }
        if (ArticlePromptChannels.SELF_MEDIA.equals(item.getChannelGroupCode())) {
            return ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(item.getContentStyle());
        }
        return null;
    }

    public void progressActivePlans() {
        List<ContentAutoDistributionItem> generatingItems = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getStatus, "generating")
                        .isNotNull(ContentAutoDistributionItem::getGenerationTaskId)
                        .last("LIMIT 200")
        );
        for (ContentAutoDistributionItem item : generatingItems) {
            BatchArticleGenerationTask task = generationTaskMapper.selectById(item.getGenerationTaskId());
            if (task == null) {
                markItemFailed(item.getId(), "文章生成任务不存在");
            } else if ("success".equals(task.getStatus()) && task.getArticleId() != null) {
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getArticleId, task.getArticleId())
                        .set(ContentAutoDistributionItem::getStatus, "generated")
                        .set(ContentAutoDistributionItem::getFailureReason, null));
            } else if ("failed".equals(task.getStatus())) {
                markItemFailed(item.getId(), task.getErrorMessage());
            }
        }

        Map<Long, ContentAutoDistributionBatch> batches = new LinkedHashMap<>();
        List<ContentAutoDistributionBatch> oldestActiveBatches = batchMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionBatch>()
                        .in(ContentAutoDistributionBatch::getStatus, ACTIVE_BATCH_STATUSES)
                        .orderByAsc(ContentAutoDistributionBatch::getId)
                        .last("LIMIT 50")
        );
        oldestActiveBatches.forEach(batch -> batches.put(batch.getId(), batch));

        List<ContentAutoDistributionItem> readyGeneratedItems = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getStatus, "generated")
                        .isNotNull(ContentAutoDistributionItem::getArticleId)
                        .orderByDesc(ContentAutoDistributionItem::getPlanDate)
                        .orderByAsc(ContentAutoDistributionItem::getBatchId, ContentAutoDistributionItem::getId)
                        .last("LIMIT 500")
        );
        for (ContentAutoDistributionItem item : readyGeneratedItems) {
            if (item.getBatchId() == null || batches.containsKey(item.getBatchId())) {
                continue;
            }
            ContentAutoDistributionBatch batch = batchMapper.selectById(item.getBatchId());
            if (batch != null && ACTIVE_BATCH_STATUSES.contains(batch.getStatus())) {
                batches.put(batch.getId(), batch);
            }
        }

        for (ContentAutoDistributionBatch batch : batches.values()) {
            try {
                scheduleGeneratedItems(batch);
                refreshPublishedItems(batch.getId());
                refreshSelfMediaScheduledItems(batch.getId());
                expireStaleGenerationItems(batch);
                refreshBatchCounters(batch.getId());
            } catch (Exception ex) {
                log.warn("auto distribution batch progress failed batchId={} projectId={} planDate={} status={} error={}",
                        batch.getId(), batch.getProjectId(), batch.getPlanDate(), batch.getStatus(), ex.getMessage(), ex);
            }
        }
    }

    private void expireStaleGenerationItems(ContentAutoDistributionBatch batch) {
        if (batch == null || batch.getUpdatedAt() == null) {
            return;
        }
        LocalDateTime expireBefore = LocalDateTime.now().minusHours(Math.max(1, staleGenerationTimeoutHours));
        if (!batch.getUpdatedAt().isBefore(expireBefore)) {
            return;
        }
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getBatchId, batch.getId())
                        .in(ContentAutoDistributionItem::getStatus, "pending_generation", "generating")
                        .isNull(ContentAutoDistributionItem::getArticleId)
        );
        if (items.isEmpty()) {
            return;
        }
        log.warn("auto distribution stale generation items expired batchId={} projectId={} planDate={} count={} timeoutHours={}",
                batch.getId(), batch.getProjectId(), batch.getPlanDate(), items.size(), Math.max(1, staleGenerationTimeoutHours));
        for (ContentAutoDistributionItem item : items) {
            String reason = "自动分发生成任务超过 " + Math.max(1, staleGenerationTimeoutHours) + " 小时未完成，已终止旧计划项";
            markItemFailed(item.getId(), reason);
        }
    }

    private void scheduleGeneratedItems(ContentAutoDistributionBatch batch) {
        List<ContentAutoDistributionItem> generated = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getBatchId, batch.getId())
                        .eq(ContentAutoDistributionItem::getStatus, "generated")
                        .isNotNull(ContentAutoDistributionItem::getArticleId)
                        .orderByAsc(ContentAutoDistributionItem::getPlannedPublishAt, ContentAutoDistributionItem::getId)
        );
        if (generated.isEmpty()) {
            return;
        }
        List<ContentAutoDistributionItem> publishable = generated.stream()
                .filter(this::markFailedIfTargetStale)
                .filter(this::markFailedIfArticleUnavailable)
                .toList();
        if (publishable.isEmpty()) {
            return;
        }
        scheduleSelfMediaGeneratedItems(batch, publishable.stream()
                .filter(item -> DistributionTargetKind.MP_ACCOUNT.equals(item.getTargetKind()))
                .toList());
        scheduleSiteGeneratedItems(batch, publishable.stream()
                .filter(item -> !DistributionTargetKind.MP_ACCOUNT.equals(item.getTargetKind()))
                .toList());
    }

    private void scheduleSiteGeneratedItems(ContentAutoDistributionBatch batch,
                                            List<ContentAutoDistributionItem> publishable) {
        if (publishable.isEmpty()) {
            return;
        }
        String jobName = buildAutoDistributionJobName(batch);
        Map<Long, LocalDateTime> plannedAtByArticleId = smoothSitePublishTimes(publishable);
        List<BatchArticlePublishService.SystemPublishPlan> plans = publishable.stream()
                .map(item -> new BatchArticlePublishService.SystemPublishPlan(
                        item.getArticleId(),
                        platformKey(item),
                        item.getContentStyle(),
                        DistributionTargetKind.BRAND_GEO_SITE.equals(item.getTargetKind()) ? null : item.getTargetId(),
                        item.getTargetBrandId(),
                        item.getTargetForumFid(),
                        plannedAtByArticleId.getOrDefault(item.getArticleId(), item.getPlannedPublishAt())
                ))
                .toList();
        BatchArticlePublishService.SystemPublishJobResult result =
                publishService.createSystemScheduledJob(jobName, resolveOperatorUserId(batch.getProjectId()), plans);
        Map<Long, Long> publishItemIds = result.itemIdsByArticleId();
        for (ContentAutoDistributionItem item : publishable) {
            LocalDateTime plannedAt = plannedAtByArticleId.getOrDefault(item.getArticleId(), item.getPlannedPublishAt());
            itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                    .eq(ContentAutoDistributionItem::getId, item.getId())
                    .set(ContentAutoDistributionItem::getPlannedPublishAt, plannedAt)
                    .set(ContentAutoDistributionItem::getPublishJobId, result.jobId())
                    .set(ContentAutoDistributionItem::getPublishItemId, publishItemIds.get(item.getArticleId()))
                    .set(ContentAutoDistributionItem::getStatus, "publish_scheduled"));
        }
    }

    private Map<Long, LocalDateTime> smoothSitePublishTimes(List<ContentAutoDistributionItem> publishable) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        Map<SitePublishScheduleKey, List<ContentAutoDistributionItem>> groups = new LinkedHashMap<>();
        for (ContentAutoDistributionItem item : publishable) {
            if (!"forum_site".equals(platformKey(item))) {
                continue;
            }
            groups.computeIfAbsent(sitePublishScheduleKey(item), ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<SitePublishScheduleKey, List<ContentAutoDistributionItem>> entry : groups.entrySet()) {
            List<ContentAutoDistributionItem> items = new ArrayList<>(entry.getValue());
            items.sort(Comparator
                    .comparing(ContentAutoDistributionItem::getPlannedPublishAt, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(ContentAutoDistributionItem::getId));
            LocalDateTime now = LocalDateTime.now();
            List<LocalDateTime> occupied = existingSitePublishTimes(entry.getKey(), now);
            List<LocalDateTime> slots = evenlyDistributedSlots(entry.getKey().planDate(), occupied.size() + items.size(), now);
            Set<Integer> occupiedIndexes = nearestSlotIndexes(slots, occupied);
            int slotIndex = 0;
            for (ContentAutoDistributionItem item : items) {
                while (slotIndex < slots.size() && occupiedIndexes.contains(slotIndex)) {
                    slotIndex++;
                }
                LocalDateTime plannedAt = slotIndex < slots.size()
                        ? slots.get(slotIndex++)
                        : item.getPlannedPublishAt();
                result.put(item.getArticleId(), plannedAt);
            }
        }
        return result;
    }

    private SitePublishScheduleKey sitePublishScheduleKey(ContentAutoDistributionItem item) {
        String platform = platformKey(item);
        Long targetId = "agent_site".equals(platform) ? item.getTargetBrandId() : item.getTargetId();
        return new SitePublishScheduleKey(item.getPlanDate(), platform, targetId);
    }

    private List<LocalDateTime> existingSitePublishTimes(SitePublishScheduleKey key, LocalDateTime now) {
        List<ContentAutoDistributionItem> existing = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getPlanDate, key.planDate())
                        .eq(ContentAutoDistributionItem::getStatus, "publish_scheduled")
                        .eq(ContentAutoDistributionItem::getTargetKind, DistributionTargetKind.FORUM_SITE)
                        .eq(ContentAutoDistributionItem::getTargetId, key.targetId())
                        .isNotNull(ContentAutoDistributionItem::getPublishItemId)
                        .orderByAsc(ContentAutoDistributionItem::getPlannedPublishAt, ContentAutoDistributionItem::getId)
        );
        return existing.stream()
                .map(ContentAutoDistributionItem::getPlannedPublishAt)
                .filter(Objects::nonNull)
                .filter(time -> !time.isBefore(now))
                .toList();
    }

    private List<LocalDateTime> evenlyDistributedSlots(LocalDate planDate, int count, LocalDateTime now) {
        if (count <= 0) {
            return List.of();
        }
        LocalTime configuredStart = parseTime(publishWindowStart, LocalTime.of(1, 0));
        LocalTime configuredEnd = parseTime(publishWindowEnd, LocalTime.of(23, 0));
        if (!configuredEnd.isAfter(configuredStart)) {
            configuredEnd = LocalTime.of(23, 0);
        }
        LocalDateTime start = LocalDateTime.of(planDate, configuredStart);
        LocalDateTime end = LocalDateTime.of(planDate, configuredEnd);
        if (planDate.equals(now.toLocalDate()) && now.isAfter(start)) {
            start = now.plusMinutes(1).withSecond(0).withNano(0);
        }
        if (start.isAfter(end)) {
            start = end;
        }
        List<LocalDateTime> result = new ArrayList<>();
        long windowMinutes = java.time.Duration.between(start, end).toMinutes();
        for (int i = 0; i < count; i++) {
            long offset = count <= 1 ? 0 : Math.round((double) windowMinutes * i / (count - 1));
            result.add(start.plusMinutes(offset));
        }
        return result;
    }

    private Set<Integer> nearestSlotIndexes(List<LocalDateTime> slots, List<LocalDateTime> occupied) {
        Set<Integer> indexes = new HashSet<>();
        for (LocalDateTime time : occupied) {
            int nearest = -1;
            long nearestDistance = Long.MAX_VALUE;
            for (int i = 0; i < slots.size(); i++) {
                if (indexes.contains(i)) {
                    continue;
                }
                long distance = Math.abs(java.time.Duration.between(slots.get(i), time).toSeconds());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = i;
                }
            }
            if (nearest >= 0) {
                indexes.add(nearest);
            }
        }
        return indexes;
    }

    private void scheduleSelfMediaGeneratedItems(ContentAutoDistributionBatch batch,
                                                 List<ContentAutoDistributionItem> publishable) {
        if (publishable.isEmpty()) {
            return;
        }
        Long operatorId = resolveOperatorUserId(batch.getProjectId());
        for (ContentAutoDistributionItem item : publishable) {
            SelfMediaPublishScheduleCreateRequest request = new SelfMediaPublishScheduleCreateRequest();
            request.setBrandId(item.getBrandId());
            request.setArticleIds(List.of(item.getArticleId()));
            request.setSelfMediaAccountIds(List.of(item.getTargetId()));
            request.setWindowStart(item.getPlannedPublishAt());
            request.setWindowEnd(item.getPlannedPublishAt().plusMinutes(1));
            request.setScheduleStrategy("platform_schedule");
            request.setMinIntervalMinutes(1);
            String requestKey = "auto-distribution-self-media-" + item.getId();
            SelfMediaPublishScheduleCreateResponse response =
                    selfMediaPublishScheduleService.createSystemSchedules(request, requestKey, operatorId);
            if (!response.getCreatedSchedules().isEmpty()) {
                SelfMediaPublishScheduleVO schedule = response.getCreatedSchedules().get(0);
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getSelfMediaScheduleId, schedule.getId())
                        .set(ContentAutoDistributionItem::getStatus, "publish_scheduled")
                        .set(ContentAutoDistributionItem::getFailureReason, null));
            } else if (!response.getExistingSchedules().isEmpty()) {
                SelfMediaPublishScheduleVO schedule = response.getExistingSchedules().get(0);
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getSelfMediaScheduleId, schedule.getId())
                        .set(ContentAutoDistributionItem::getStatus, "publish_scheduled")
                        .set(ContentAutoDistributionItem::getFailureReason, null));
            } else {
                String reason = response.getRejectedItems().isEmpty()
                        ? "自媒体自动排期创建失败"
                        : response.getRejectedItems().get(0).getCode() + "：" + response.getRejectedItems().get(0).getMessage();
                markItemFailed(item.getId(), reason);
            }
        }
    }

    private boolean markFailedIfTargetStale(ContentAutoDistributionItem item) {
        if (!DistributionTargetKind.INDUSTRY_SITE.equals(item.getTargetKind())) {
            if (DistributionTargetKind.MP_ACCOUNT.equals(item.getTargetKind())) {
                return markFailedIfSelfMediaTargetStale(item);
            }
            return true;
        }
        Project project = projectMapper.selectById(item.getProjectId());
        PublishSite currentSite = resolveBrandIndustrySite(project);
        if (currentSite != null && Objects.equals(currentSite.getId(), item.getTargetId())) {
            return true;
        }
        markItemFailed(item.getId(), "品牌行业资讯站配置已取消或变更，跳过旧自动分发计划");
        return false;
    }

    private boolean markFailedIfArticleUnavailable(ContentAutoDistributionItem item) {
        ArticleDraft article = item.getArticleId() == null ? null : articleDraftMapper.selectById(item.getArticleId());
        if (article == null) {
            markItemFailed(item.getId(), "文章不存在，跳过旧自动分发计划");
            return false;
        }
        String status = article.getStatus();
        if (ACTIVE_ARTICLE_STATUSES.contains(status)) {
            return true;
        }
        markItemFailed(item.getId(), "文章当前状态不可发布：" + (StringUtils.hasText(status) ? status : "unknown"));
        return false;
    }

    private boolean markFailedIfSelfMediaTargetStale(ContentAutoDistributionItem item) {
        SelfMediaAccount account = item.getTargetId() == null ? null : selfMediaAccountMapper.selectById(item.getTargetId());
        if (account == null || account.getDeletedAt() != null) {
            markItemFailed(item.getId(), "自媒体账号不存在或已删除，跳过旧自动分发计划");
            return false;
        }
        if (!Objects.equals(account.getBrandId(), item.getBrandId())) {
            markItemFailed(item.getId(), "自媒体账号品牌已变更，跳过旧自动分发计划");
            return false;
        }
        if (!"active".equalsIgnoreCase(String.valueOf(account.getStatus()))) {
            markItemFailed(item.getId(), "自媒体账号未启用，跳过旧自动分发计划");
            return false;
        }
        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                selfMediaScheduleCapabilityService.readiness(account.getPlatform());
        if (!readiness.ready()) {
            markItemFailed(item.getId(), readiness.message());
            return false;
        }
        try {
            BrowserEnvironmentAccount binding = browserEnvironmentService.validateForTaskCreation(account);
            if (binding == null) {
                markItemFailed(item.getId(), "自媒体账号未绑定可用指纹浏览器环境");
                return false;
            }
            return true;
        } catch (BizException ex) {
            markItemFailed(item.getId(), ex.getMessage());
            return false;
        }
    }

    private void refreshPublishedItems(Long batchId) {
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getBatchId, batchId)
                        .eq(ContentAutoDistributionItem::getStatus, "publish_scheduled")
                        .isNotNull(ContentAutoDistributionItem::getPublishItemId)
        );
        for (ContentAutoDistributionItem item : items) {
            BatchArticlePublishItem publishItem = publishItemMapper.selectById(item.getPublishItemId());
            if (publishItem == null) {
                continue;
            }
            if ("success".equals(publishItem.getStatus())) {
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getStatus, "published"));
            } else if ("failed".equals(publishItem.getStatus())) {
                maybeCreateCredentialAlert(item);
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getStatus, "failed")
                        .set(ContentAutoDistributionItem::getFailureReason, publishItem.getErrorMessage()));
            }
        }
    }

    private void refreshSelfMediaScheduledItems(Long batchId) {
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getBatchId, batchId)
                        .eq(ContentAutoDistributionItem::getStatus, "publish_scheduled")
                        .isNotNull(ContentAutoDistributionItem::getSelfMediaScheduleId)
        );
        for (ContentAutoDistributionItem item : items) {
            SelfMediaPublishSchedule schedule = selfMediaPublishScheduleMapper.selectById(item.getSelfMediaScheduleId());
            if (schedule == null) {
                markItemFailed(item.getId(), "自媒体排期不存在");
                continue;
            }
            String status = schedule.getStatus();
            if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)
                    || SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING.equals(status)) {
                itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                        .eq(ContentAutoDistributionItem::getId, item.getId())
                        .set(ContentAutoDistributionItem::getStatus, "published")
                        .set(ContentAutoDistributionItem::getFailureReason, null));
            } else if (Set.of(
                    SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
                    SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED,
                    SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED,
                    SelfMediaPublishScheduleConstants.STATUS_ROUTED_TO_SEMI_AUTO,
                    SelfMediaPublishScheduleConstants.STATUS_CANCELLED
            ).contains(status)) {
                String reason = StringUtils.hasText(schedule.getFailureMessage())
                        ? schedule.getFailureMessage()
                        : "自媒体排期状态异常：" + status;
                markItemFailed(item.getId(), reason);
            }
        }
    }

    private String buildAutoDistributionJobName(ContentAutoDistributionBatch batch) {
        String subjectName = "项目";
        if (batch.getProjectId() != null) {
            Project project = projectMapper.selectById(batch.getProjectId());
            if (project != null && StringUtils.hasText(project.getProjectName())) {
                subjectName = compactNamePart(project.getProjectName());
            }
        }
        return "自动分发_" + subjectName + "_" + batch.getPlanDate();
    }

    private String compactNamePart(String name) {
        String text = StringUtils.hasText(name) ? name.trim().replaceAll("\\s+", "") : "项目";
        int maxLength = 24;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void refreshBatchCounters(Long batchId) {
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>().eq(ContentAutoDistributionItem::getBatchId, batchId)
        );
        int generated = (int) items.stream().filter(item -> item.getArticleId() != null).count();
        int scheduled = (int) items.stream()
                .filter(item -> item.getPublishItemId() != null || item.getSelfMediaScheduleId() != null)
                .count();
        int failed = (int) items.stream().filter(item -> "failed".equals(item.getStatus())).count();
        boolean done = items.stream().allMatch(item -> Set.of("published", "failed").contains(item.getStatus()));
        String status;
        if (done) {
            long published = items.stream().filter(item -> "published".equals(item.getStatus())).count();
            status = published == items.size() ? "completed" : (published == 0 ? "failed" : "partial_failed");
        } else if (scheduled > 0) {
            status = "publish_scheduled";
        } else {
            status = "generating";
        }
        batchMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionBatch>()
                .eq(ContentAutoDistributionBatch::getId, batchId)
                .set(ContentAutoDistributionBatch::getGeneratedCount, generated)
                .set(ContentAutoDistributionBatch::getScheduledCount, scheduled)
                .set(ContentAutoDistributionBatch::getFailedCount, failed)
                .set(ContentAutoDistributionBatch::getStatus, status));
    }

    private List<PlannedTarget> planChannelTargets(Project project, ProjectChannelAllocation allocation) {
        String channelCode = allocation.getChannelCode();
        int count = allocation.getAllocatedCount() == null ? 0 : allocation.getAllocatedCount();
        if (count <= 0) {
            return List.of();
        }
        List<TargetRef> targets = resolveTargets(project, channelCode);
        if (targets.isEmpty()) {
            return List.of();
        }
        List<TargetRef> shuffled = new ArrayList<>(targets);
        Collections.shuffle(shuffled);
        int base = count / shuffled.size();
        int remainder = count % shuffled.size();
        List<PlannedTarget> result = new ArrayList<>();
        for (int i = 0; i < shuffled.size(); i++) {
            int targetCount = base + (i < remainder ? 1 : 0);
            if (targetCount <= 0) {
                continue;
            }
            TargetRef target = shuffled.get(i);
            String groupCode = generationGroup(channelCode);
            result.add(new PlannedTarget(
                    channelCode,
                    groupCode,
                    StringUtils.hasText(target.contentStyle())
                            ? target.contentStyle()
                            : ArticlePromptChannels.contentStyle(groupCode, null),
                    target.targetKind(),
                    target.targetId(),
                    target.targetName(),
                    target.targetBrandId(),
                    target.targetForumFid(),
                    targetCount
            ));
        }
        return result;
    }

    private List<TargetRef> resolveTargets(Project project, String channelCode) {
        if ("official_site".equals(channelCode)) {
            if (project.getBrandId() == null) {
                return List.of();
            }
            Brand brand = brandMapper.selectById(project.getBrandId());
            if (brand == null || !StringUtils.hasText(brand.getGeoSiteDomain())
                    || (StringUtils.hasText(brand.getGeoSiteStatus()) && !"active".equalsIgnoreCase(brand.getGeoSiteStatus()))) {
                return List.of();
            }
            String targetName = StringUtils.hasText(brand.getGeoSiteName()) ? brand.getGeoSiteName() : "Agent 官网";
            return List.of(new TargetRef(DistributionTargetKind.BRAND_GEO_SITE, brand.getId(), targetName, brand.getId(), null, null));
        }
        if ("industry_site".equals(channelCode)) {
            PublishSite site = resolveBrandIndustrySite(project);
            return site == null ? List.of() : List.of(new TargetRef(DistributionTargetKind.INDUSTRY_SITE, site.getId(), site.getSiteName(), null, null, null));
        }
        if ("forum".equals(channelCode)) {
            List<PublishSite> sites = publishSiteMapper.selectList(new LambdaQueryWrapper<PublishSite>()
                    .in(PublishSite::getIntegrationMethod, "forum_playwright", "discuz_http")
                    .eq(PublishSite::getStatus, "active")
                    .orderByAsc(PublishSite::getId));
            Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
            List<TargetRef> result = new ArrayList<>();
            for (PublishSite site : sites) {
                if (!hasCredential(site)) {
                    createCredentialAlert(site, "论坛");
                    continue;
                }
                Integer fid = forumBoardRoutingService.resolveForumFid(site, project, brand, null);
                result.add(new TargetRef(DistributionTargetKind.FORUM_SITE, site.getId(), site.getSiteName(), null, fid, null));
            }
            return result;
        }
        String selfMediaPlatform = selfMediaPlatform(channelCode);
        if (selfMediaPlatform != null) {
            return resolveSelfMediaTargets(project, selfMediaPlatform);
        }
        return List.of();
    }

    private String generationGroup(String channelCode) {
        if (selfMediaPlatform(channelCode) != null) {
            return ArticlePromptChannels.SELF_MEDIA;
        }
        return QUOTA_TO_GENERATION_GROUP.get(channelCode);
    }

    private static Set<String> plannableChannelCodes() {
        Set<String> codes = new HashSet<>(QUOTA_TO_GENERATION_GROUP.keySet());
        for (String platform : ArticlePromptChannels.SELF_MEDIA_SUB_CODES) {
            codes.add(ArticlePromptChannels.SELF_MEDIA + ":" + platform);
        }
        return Set.copyOf(codes);
    }

    private String selfMediaPlatform(String channelCode) {
        if (!StringUtils.hasText(channelCode) || !channelCode.startsWith(ArticlePromptChannels.SELF_MEDIA + ":")) {
            return null;
        }
        return ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(
                channelCode.substring((ArticlePromptChannels.SELF_MEDIA + ":").length())
        );
    }

    private List<TargetRef> resolveSelfMediaTargets(Project project, String quotaPlatform) {
        if (project.getBrandId() == null) {
            return List.of();
        }
        List<SelfMediaAccount> accounts = selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, project.getBrandId())
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .orderByAsc(SelfMediaAccount::getPlatform)
                .orderByAsc(SelfMediaAccount::getId));
        List<TargetRef> result = new ArrayList<>();
        for (SelfMediaAccount account : accounts) {
            String platform = ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(account.getPlatform());
            if (platform == null) {
                continue;
            }
            if (!quotaPlatform.equals(platform)) {
                continue;
            }
            SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                    selfMediaScheduleCapabilityService.readiness(platform);
            if (!readiness.ready()) {
                continue;
            }
            try {
                BrowserEnvironmentAccount binding = browserEnvironmentService.validateForTaskCreation(account);
                if (binding == null) {
                    continue;
                }
            } catch (BizException ex) {
                continue;
            }
            String targetName = ArticlePromptChannels.channelName(ArticlePromptChannels.SELF_MEDIA, platform)
                    + " / " + (StringUtils.hasText(account.getAccountName()) ? account.getAccountName() : account.getId());
            result.add(new TargetRef(
                    DistributionTargetKind.MP_ACCOUNT,
                    account.getId(),
                    targetName,
                    account.getBrandId(),
                    null,
                    ArticlePromptChannels.contentStyle(ArticlePromptChannels.SELF_MEDIA, platform)
            ));
        }
        return result;
    }

    private String unavailableDistributionTargetReason(Project project, List<ProjectChannelAllocation> allocations) {
        for (ProjectChannelAllocation allocation : allocations) {
            String platform = selfMediaPlatform(allocation.getChannelCode());
            if (platform == null || (allocation.getAllocatedCount() == null || allocation.getAllocatedCount() <= 0)) {
                continue;
            }
            String platformName = ArticlePromptChannels.channelName(ArticlePromptChannels.SELF_MEDIA, platform);
            if (project.getBrandId() == null) {
                return "自媒体平台 / " + platformName + " 无法分发：项目未绑定品牌";
            }
            long accountCount = selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                    .eq(SelfMediaAccount::getBrandId, project.getBrandId())
                    .eq(SelfMediaAccount::getStatus, "active")
                    .isNull(SelfMediaAccount::getDeletedAt))
                    .stream()
                    .filter(account -> platform.equals(ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(account.getPlatform())))
                    .count();
            if (accountCount <= 0) {
                return "自媒体平台 / " + platformName + " 无可用账号，请先在品牌下配置并启用账号";
            }
            return "自媒体平台 / " + platformName + " 账号未完成发布能力或浏览器环境配置";
        }
        return "no available distribution target";
    }

    private PublishSite resolveBrandIndustrySite(Project project) {
        if (project.getBrandId() == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || !StringUtils.hasText(brand.getIndustrySiteCode())) {
            return null;
        }
        PublishSite site = publishSiteMapper.selectOne(new LambdaQueryWrapper<PublishSite>()
                .eq(PublishSite::getSiteCode, brand.getIndustrySiteCode().trim())
                .eq(PublishSite::getStatus, "active")
                .last("LIMIT 1"));
        if (site == null && StringUtils.hasText(brand.getIndustrySiteName())) {
            site = publishSiteMapper.selectOne(new LambdaQueryWrapper<PublishSite>()
                    .eq(PublishSite::getSiteName, brand.getIndustrySiteName().trim())
                    .eq(PublishSite::getStatus, "active")
                    .last("LIMIT 1"));
        }
        return site;
    }

    private List<KeywordGroupResult> pickQuestions(List<KeywordGroupResult> questions, int count) {
        List<KeywordGroupResult> result = new ArrayList<>();
        while (result.size() < count) {
            List<KeywordGroupResult> shuffled = new ArrayList<>(questions);
            Collections.shuffle(shuffled);
            for (KeywordGroupResult question : shuffled) {
                result.add(question);
                if (result.size() >= count) {
                    break;
                }
            }
        }
        return result;
    }

    private List<LocalDateTime> plannedPublishTimes(LocalDate planDate, int count) {
        LocalTime start = parseTime(publishWindowStart, LocalTime.of(1, 0));
        LocalTime end = parseTime(publishWindowEnd, LocalTime.of(23, 0));
        if (!end.isAfter(start)) {
            end = LocalTime.of(23, 0);
        }
        List<LocalDateTime> result = new ArrayList<>();
        long windowMinutes = java.time.Duration.between(start, end).toMinutes();
        for (int i = 0; i < count; i++) {
            long offset = count <= 1 ? 0 : Math.round((double) windowMinutes * i / (count - 1));
            LocalDateTime planned = LocalDateTime.of(planDate, start).plusMinutes(offset);
            planned = applyJitter(planned, LocalDateTime.of(planDate, start), LocalDateTime.of(planDate, end));
            if (!result.isEmpty() && !planned.isAfter(result.get(result.size() - 1))) {
                planned = result.get(result.size() - 1).plusMinutes(1);
            }
            if (planned.isAfter(LocalDateTime.of(planDate, end))) {
                planned = LocalDateTime.of(planDate, end);
            }
            result.add(planned);
        }
        return result;
    }

    private LocalDateTime applyJitter(LocalDateTime planned, LocalDateTime min, LocalDateTime max) {
        int minutes = Math.max(0, jitterMinutes);
        if (minutes == 0) {
            return planned;
        }
        int delta = java.util.concurrent.ThreadLocalRandom.current().nextInt(-minutes, minutes + 1);
        LocalDateTime result = planned.plusMinutes(delta);
        if (result.isBefore(min)) {
            return min;
        }
        if (result.isAfter(max)) {
            return max;
        }
        return result;
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void insertSkippedBatch(Project project, LocalDate planDate, String reason) {
        ContentAutoDistributionBatch batch = new ContentAutoDistributionBatch();
        batch.setProjectId(project.getId());
        batch.setCompanyId(project.getCompanyId());
        batch.setBrandId(project.getBrandId());
        batch.setPlanDate(planDate);
        batch.setStatus("skipped");
        batch.setTotalCount(0);
        batch.setGeneratedCount(0);
        batch.setScheduledCount(0);
        batch.setFailedCount(0);
        batch.setErrorMessage(reason);
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void markItemFailed(Long itemId, String reason) {
        itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                .eq(ContentAutoDistributionItem::getId, itemId)
                .set(ContentAutoDistributionItem::getStatus, "failed")
                .set(ContentAutoDistributionItem::getFailureReason, trimError(reason)));
    }

    private void maybeCreateCredentialAlert(ContentAutoDistributionItem item) {
        String error = item.getFailureReason();
        if (!StringUtils.hasText(error)) {
            BatchArticlePublishItem publishItem = item.getPublishItemId() == null ? null : publishItemMapper.selectById(item.getPublishItemId());
            error = publishItem == null ? null : publishItem.getErrorMessage();
        }
        String lowered = error == null ? "" : error.toLowerCase();
        if (!(lowered.contains("auth") || lowered.contains("login") || lowered.contains("cookie")
                || lowered.contains("认证") || lowered.contains("登录"))) {
            return;
        }
        if (DistributionTargetKind.INDUSTRY_SITE.equals(item.getTargetKind()) || DistributionTargetKind.FORUM_SITE.equals(item.getTargetKind())) {
            PublishSite site = item.getTargetId() == null ? null : publishSiteMapper.selectById(item.getTargetId());
            if (site != null) {
                createCredentialAlert(site, DistributionTargetKind.FORUM_SITE.equals(item.getTargetKind()) ? "论坛" : "行业资讯站");
            }
        } else if (DistributionTargetKind.BRAND_GEO_SITE.equals(item.getTargetKind()) && item.getTargetId() != null) {
            createBrandGeoSiteCredentialAlert(item.getTargetId());
        }
    }

    private void createCredentialAlert(PublishSite site, String targetLabel) {
        String name = StringUtils.hasText(site.getSiteName()) ? site.getSiteName() : targetLabel;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("action", "publish_site_edit");
        context.put("publishSiteId", site.getId());
        context.put("route", "/admin/content/publish-platforms?siteId=" + site.getId());
        context.put("targetLabel", targetLabel);
        String message = name + "登录信息已过期，请更新";
        for (String role : List.of("super_admin", "manager", "admin")) {
            systemAlertService.createRecipientAlert(
                    "publish_credential_expired",
                    "warn",
                    "content_auto_distribution",
                    message,
                    context,
                    null,
                    role,
                    "publish-credential-expired:" + site.getId() + ":" + role
            );
        }
    }

    private void createBrandGeoSiteCredentialAlert(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        String name = brand == null || !StringUtils.hasText(brand.getBrandName()) ? "Agent 官网" : brand.getBrandName() + " Agent 官网";
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("action", "brand_geo_site_edit");
        context.put("brandId", brandId);
        context.put("route", "/admin/brands/" + brandId);
        context.put("targetLabel", "Agent 官网");
        String message = name + "登录信息已过期，请更新";
        for (String role : List.of("super_admin", "manager", "admin")) {
            systemAlertService.createRecipientAlert(
                    "publish_credential_expired",
                    "warn",
                    "content_auto_distribution",
                    message,
                    context,
                    null,
                    role,
                    "brand-geo-site-credential-expired:" + brandId + ":" + role
            );
        }
    }

    private boolean hasCredential(PublishSite site) {
        return StringUtils.hasText(site.getCredentialRef())
                || StringUtils.hasText(site.getApiCredentialEncrypted())
                || StringUtils.hasText(site.getApiCredential());
    }

    private String platformKey(ContentAutoDistributionItem item) {
        if (DistributionTargetKind.BRAND_GEO_SITE.equals(item.getTargetKind())) {
            return "agent_site";
        }
        if (DistributionTargetKind.INDUSTRY_SITE.equals(item.getTargetKind())) {
            return "industry_site";
        }
        return "forum_site";
    }

    private Long resolveOperatorUserId(Long projectId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        return resolveOperatorUserId(project);
    }

    private Long resolveOperatorUserId(Project project) {
        Long ownerId = resolveProjectOwnerId(project);
        if (ownerId != null) {
            return ownerId;
        }
        Long fallbackId = resolveConfiguredSuperAdminId();
        if (fallbackId != null) {
            log.warn("auto distribution uses configured super_admin fallback projectId={}", project == null ? null : project.getId());
            return fallbackId;
        }
        SysUser fallback = firstActiveSuperAdmin();
        if (fallback != null) {
            log.warn("auto distribution uses super_admin fallback projectId={} userId={}", project == null ? null : project.getId(), fallback.getId());
            return fallback.getId();
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, true)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        log.warn("auto distribution fallback super_admin missing projectId={}, using first active user userId={}",
                project == null ? null : project.getId(), user == null ? null : user.getId());
        return user == null ? null : user.getId();
    }

    private Long resolveProjectOwnerId(Project project) {
        if (project == null || project.getCompanyId() == null) {
            return null;
        }
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null || company.getOwnerId() == null) {
            log.warn("auto distribution project owner missing projectId={} companyId={}",
                    project.getId(), project.getCompanyId());
            return null;
        }
        SysUser owner = sysUserMapper.selectById(company.getOwnerId());
        if (owner == null || !Boolean.TRUE.equals(owner.getIsActive()) || !"operator".equals(owner.getRole())) {
            log.warn("auto distribution project owner invalid projectId={} companyId={} ownerId={}",
                    project.getId(), project.getCompanyId(), company.getOwnerId());
            return null;
        }
        return owner.getId();
    }

    private Long resolveConfiguredSuperAdminId() {
        if (configuredOperatorUserId <= 0) {
            return null;
        }
        SysUser configured = sysUserMapper.selectById(configuredOperatorUserId);
        if (configured != null && Boolean.TRUE.equals(configured.getIsActive()) && "super_admin".equals(configured.getRole())) {
            return configured.getId();
        }
        log.warn("auto distribution configured fallback user is not active super_admin userId={}", configuredOperatorUserId);
        return null;
    }

    private SysUser firstActiveSuperAdmin() {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "super_admin")
                .eq(SysUser::getIsActive, true)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
    }

    private List<LocalDateTime> spreadExecuteTimes(LocalDate date,
                                                   int count,
                                                   String windowStart,
                                                   String windowEnd,
                                                   int jitterMinutes) {
        if (count <= 0) {
            return List.of();
        }
        LocalTime start = parseLocalTime(windowStart, LocalTime.of(0, 30));
        LocalTime end = parseLocalTime(windowEnd, LocalTime.of(18, 0));
        LocalDateTime base = date.atTime(start);
        long windowSeconds = java.time.Duration.between(start, end).getSeconds();
        if (windowSeconds <= 0) {
            windowSeconds = TimeUnit.HOURS.toSeconds(18);
        }
        long jitterSeconds = Math.max(0, jitterMinutes) * 60L;
        List<LocalDateTime> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offsetSeconds = count <= 1 ? 0L : (windowSeconds * i) / count;
            if (jitterSeconds > 0) {
                offsetSeconds += ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
            }
            offsetSeconds = Math.min(offsetSeconds, windowSeconds);
            times.add(base.plusSeconds(offsetSeconds).withNano(0));
        }
        return times;
    }

    private LocalTime parseLocalTime(String value, LocalTime fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void withLock(String key, Runnable runnable) {
        String value = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, value, Math.max(lockTtlSeconds, 1), TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            runnable.run();
        } catch (Exception ex) {
            log.warn("content auto distribution task failed: {}", ex.getMessage(), ex);
        } finally {
            try {
                if (Objects.equals(value, redisTemplate.opsForValue().get(key))) {
                    redisTemplate.delete(key);
                }
            } catch (Exception ex) {
                log.warn("content auto distribution lock release failed: {}", ex.getMessage());
            }
        }
    }

    private String trimError(String value) {
        if (!StringUtils.hasText(value)) {
            return "未知错误";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private record TargetRef(String targetKind,
                             Long targetId,
                             String targetName,
                             Long targetBrandId,
                             Integer targetForumFid,
                             String contentStyle) {
    }

    private record PlannedTarget(String channelCode,
                                 String channelGroupCode,
                                 String contentStyle,
                                 String targetKind,
                                 Long targetId,
                                 String targetName,
                                 Long targetBrandId,
                                 Integer targetForumFid,
                                 int count) {
    }

    private record SitePublishScheduleKey(LocalDate planDate,
                                          String platformKey,
                                          Long targetId) {
    }
}
