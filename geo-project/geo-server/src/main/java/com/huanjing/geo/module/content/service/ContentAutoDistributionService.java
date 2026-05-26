package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.BatchArticlePublishItem;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionBatch;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionItem;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionBatchMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionItemMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentAutoDistributionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int GENERATION_BATCH_LIMIT = 30;
    private static final Set<String> ACTIVE_BATCH_STATUSES = Set.of("created", "generating", "publish_scheduled", "partial_failed");
    private static final Map<String, String> QUOTA_TO_GENERATION_GROUP = Map.of(
            "official_site", ArticlePromptChannels.AGENT_SITE,
            "industry_site", ArticlePromptChannels.INDUSTRY_SITE,
            "forum", ArticlePromptChannels.FORUM
    );

    private final ProjectMapper projectMapper;
    private final ProjectChannelAllocationMapper allocationMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final BrandMapper brandMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final SysUserMapper sysUserMapper;
    private final ContentAutoDistributionBatchMapper batchMapper;
    private final ContentAutoDistributionItemMapper itemMapper;
    private final BatchArticleGenerationTaskMapper generationTaskMapper;
    private final BatchArticlePublishItemMapper publishItemMapper;
    private final BatchArticleGenerationService generationService;
    private final BatchArticlePublishService publishService;
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

    @Scheduled(cron = "${geo.content.auto-distribution.cron:0 0 1 * * ?}", zone = "Asia/Shanghai")
    public void runDailyPlan() {
        if (!enabled) {
            return;
        }
        withLock(dailyLockKey, () -> createDailyPlan(LocalDate.now(BUSINESS_ZONE)));
    }

    @Scheduled(fixedDelayString = "${geo.content.auto-distribution.progress-poll-ms:60000}")
    public void progressPlans() {
        if (!enabled) {
            return;
        }
        withLock(progressLockKey, this::progressActivePlans);
    }

    public void createDailyPlan(LocalDate planDate) {
        Long operatorId = resolveOperatorUserId();
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, "active")
                .isNull(Project::getDeletedAt)
                .and(wrapper -> wrapper.eq(Project::getContentGenerationEnabled, true)
                        .or()
                        .isNull(Project::getContentGenerationEnabled))
                .orderByAsc(Project::getId));
        for (Project project : projects) {
            try {
                createProjectPlan(project, planDate, operatorId);
            } catch (Exception ex) {
                log.warn("auto distribution plan failed projectId={} date={} error={}",
                        project.getId(), planDate, ex.getMessage(), ex);
            }
        }
    }

    @Transactional
    public void createProjectPlan(Project project, LocalDate planDate, Long operatorId) {
        if (batchMapper.selectCount(new LambdaQueryWrapper<ContentAutoDistributionBatch>()
                .eq(ContentAutoDistributionBatch::getProjectId, project.getId())
                .eq(ContentAutoDistributionBatch::getPlanDate, planDate)) > 0) {
            return;
        }
        List<ProjectChannelAllocation> allocations = allocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, project.getId())
                        .in(ProjectChannelAllocation::getChannelCode, QUOTA_TO_GENERATION_GROUP.keySet())
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
            insertSkippedBatch(project, planDate, "no available distribution target");
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
            platform.setContentStyle(item.getContentStyle());
            platform.setAllocationMode("auto");
            platform.setCount(1);
            topic.setPlatforms(List.of(platform));
            topics.add(topic);
        }
        request.setTopics(topics);
        return request;
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

        List<ContentAutoDistributionBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionBatch>()
                        .in(ContentAutoDistributionBatch::getStatus, ACTIVE_BATCH_STATUSES)
                        .orderByAsc(ContentAutoDistributionBatch::getId)
                        .last("LIMIT 50")
        );
        for (ContentAutoDistributionBatch batch : batches) {
            scheduleGeneratedItems(batch);
            refreshPublishedItems(batch.getId());
            refreshBatchCounters(batch.getId());
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
        String jobName = "自动分发_" + batch.getProjectId() + "_" + batch.getPlanDate();
        List<BatchArticlePublishService.SystemPublishPlan> plans = generated.stream()
                .map(item -> new BatchArticlePublishService.SystemPublishPlan(
                        item.getArticleId(),
                        platformKey(item),
                        item.getContentStyle(),
                        DistributionTargetKind.BRAND_GEO_SITE.equals(item.getTargetKind()) ? null : item.getTargetId(),
                        item.getTargetBrandId(),
                        item.getTargetForumFid(),
                        item.getPlannedPublishAt()
                ))
                .toList();
        BatchArticlePublishService.SystemPublishJobResult result =
                publishService.createSystemScheduledJob(jobName, resolveOperatorUserId(), plans);
        Map<Long, Long> publishItemIds = result.itemIdsByArticleId();
        for (ContentAutoDistributionItem item : generated) {
            itemMapper.update(null, new LambdaUpdateWrapper<ContentAutoDistributionItem>()
                    .eq(ContentAutoDistributionItem::getId, item.getId())
                    .set(ContentAutoDistributionItem::getPublishJobId, result.jobId())
                    .set(ContentAutoDistributionItem::getPublishItemId, publishItemIds.get(item.getArticleId()))
                    .set(ContentAutoDistributionItem::getStatus, "publish_scheduled"));
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

    private void refreshBatchCounters(Long batchId) {
        List<ContentAutoDistributionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ContentAutoDistributionItem>().eq(ContentAutoDistributionItem::getBatchId, batchId)
        );
        int generated = (int) items.stream().filter(item -> item.getArticleId() != null).count();
        int scheduled = (int) items.stream().filter(item -> item.getPublishItemId() != null).count();
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
            result.add(new PlannedTarget(
                    channelCode,
                    QUOTA_TO_GENERATION_GROUP.get(channelCode),
                    ArticlePromptChannels.contentStyle(QUOTA_TO_GENERATION_GROUP.get(channelCode), null),
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
            if (brand == null || !StringUtils.hasText(brand.getGeoSiteCode())
                    || (StringUtils.hasText(brand.getGeoSiteStatus()) && !"active".equalsIgnoreCase(brand.getGeoSiteStatus()))) {
                return List.of();
            }
            return List.of(new TargetRef(DistributionTargetKind.BRAND_GEO_SITE, brand.getId(), "Agent 官网", brand.getId(), null));
        }
        if ("industry_site".equals(channelCode)) {
            PublishSite site = resolveBrandIndustrySite(project);
            return site == null ? List.of() : List.of(new TargetRef(DistributionTargetKind.INDUSTRY_SITE, site.getId(), site.getSiteName(), null, null));
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
                result.add(new TargetRef(DistributionTargetKind.FORUM_SITE, site.getId(), site.getSiteName(), null, fid));
            }
            return result;
        }
        return List.of();
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

    private Long resolveOperatorUserId() {
        if (configuredOperatorUserId > 0) {
            return configuredOperatorUserId;
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "super_admin")
                .eq(SysUser::getIsActive, true)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        if (user != null) {
            return user.getId();
        }
        user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, true)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        return user == null ? null : user.getId();
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

    private record TargetRef(String targetKind, Long targetId, String targetName, Long targetBrandId, Integer targetForumFid) {
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
}
