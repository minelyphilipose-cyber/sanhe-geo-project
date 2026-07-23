package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.BatchArticlePublishRequest;
import com.huanjing.geo.module.content.dto.BatchArticlePublishJobSummary;
import com.huanjing.geo.module.content.dto.BatchArticlePublishResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.BatchArticlePublishItem;
import com.huanjing.geo.module.content.entity.BatchArticlePublishJob;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishJobMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchArticlePublishService {

    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final Set<String> LEGACY_PROJECT_WRITE_ROLES =
            Set.of("operator", "delivery_manager", "partner", "partner_staff");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter JOB_NAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern LEGACY_AUTO_DISTRIBUTION_JOB_NAME =
            Pattern.compile("^自动分发_(\\d+)_(\\d{4}-\\d{2}-\\d{2})$");

    private final BatchArticlePublishJobMapper jobMapper;
    private final BatchArticlePublishItemMapper itemMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final BatchArticleGenerationTaskMapper generationTaskMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final CurrentUserService currentUserService;
    private final ContentDistributionService contentDistributionService;
    private final ForumBoardRoutingService forumBoardRoutingService;
    private final StringRedisTemplate redisTemplate;
    @Resource(name = "taskExecutor")
    private Executor batchPublishExecutor;
    @Value("${geo.content.batch-publish.lock-key:geo:content:batch-publish:scheduler:lock}")
    private String schedulerLockKey;
    @Value("${geo.content.batch-publish.lock-ttl-seconds:120}")
    private long schedulerLockTtlSeconds;
    @Value("${geo.content.batch-publish.running-timeout-minutes:120}")
    private long runningTimeoutMinutes;
    @Value("${geo.content.batch-publish.forum-site-min-execution-interval-ms:1000}")
    private long forumSiteMinExecutionIntervalMs;
    @Value("${geo.content.batch-publish.due-candidate-scan-size:100}")
    private int dueCandidateScanSize;

    @Transactional
    public BatchArticlePublishResponse submit(BatchArticlePublishRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.publish.operate", "project.write", LEGACY_PROJECT_WRITE_ROLES);
        String publishMode = normalizePublishMode(request.getPublishMode());
        int intervalMinutes = request.getIntervalMinutes() == null ? 30 : request.getIntervalMinutes();
        LocalDateTime baseTime = resolveBaseTime(publishMode, request.getScheduledAt());

        List<Long> articleIds = request.getArticleIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            throw new BizException(400, "请选择需要发布的文章");
        }
        if (articleIds.size() > 100) {
            throw new BizException(400, "单次批量发布不能超过 100 篇文章");
        }

        PublishSite manualIndustrySite = request.getIndustrySiteId() == null ? null : requireIndustrySite(request.getIndustrySiteId());
        PublishSite manualForumSite = request.getForumSiteId() == null ? null : requireForumSite(request.getForumSiteId());

        BatchArticlePublishJob job = new BatchArticlePublishJob();
        job.setJobName(buildJobName(publishMode, articleIds, baseTime));
        job.setPublishMode(publishMode);
        job.setStatus("pending");
        job.setScheduledAt("scheduled".equals(publishMode) ? baseTime : null);
        job.setIntervalMinutes(intervalMinutes);
        job.setPlatformConcurrency(1);
        job.setTotalCount(articleIds.size());
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setCreatedBy(operator.getId());
        jobMapper.insert(job);

        Map<String, Integer> platformIndex = new HashMap<>();
        for (Long articleId : articleIds) {
            BatchArticlePublishItem item = buildItem(
                    job.getId(),
                    articleId,
                    baseTime,
                    intervalMinutes,
                    platformIndex,
                    manualIndustrySite,
                    manualForumSite,
                    request.getForumFid()
            );
            itemMapper.insert(item);
        }

        if ("now".equals(publishMode)) {
            triggerAsyncExecutionAfterCommit();
        }
        return response(job.getId());
    }

    @Transactional
    public SystemPublishJobResult createSystemScheduledJob(String jobName,
                                                           Long operatorId,
        List<SystemPublishPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            throw new BizException(400, "请配置发布计划");
        }
        BatchArticlePublishJob job = new BatchArticlePublishJob();
        job.setJobName(StringUtils.hasText(jobName) ? compactJobNamePart(jobName) : "自动分发_" + LocalDateTime.now().format(JOB_NAME_DATE));
        job.setPublishMode("scheduled");
        job.setStatus("pending");
        job.setScheduledAt(plans.stream().map(SystemPublishPlan::plannedAt).min(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        job.setIntervalMinutes(0);
        job.setPlatformConcurrency(1);
        job.setTotalCount(plans.size());
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setCreatedBy(operatorId);
        jobMapper.insert(job);

        Map<Long, Long> itemIdsByArticleId = new LinkedHashMap<>();
        for (SystemPublishPlan plan : plans) {
            ArticleDraft article = requireArticle(plan.articleId());
            if (!ACTIVE_ARTICLE_STATUS.contains(article.getStatus())) {
                throw new BizException(400, "article " + plan.articleId() + " is not approved or unpublished");
            }
            ensureMedicalOfficialPublishAllowed(article, plan.platformKey());
            BatchArticlePublishItem item = new BatchArticlePublishItem();
            item.setJobId(job.getId());
            item.setArticleId(plan.articleId());
            item.setProjectId(article.getProjectId());
            item.setPlatformKey(plan.platformKey());
            item.setContentStyle(plan.contentStyle());
            item.setTargetSiteId(plan.targetSiteId());
            item.setTargetForumFid(plan.targetForumFid());
            item.setTargetBrandId(plan.targetBrandId());
            item.setPlannedAt(plan.plannedAt());
            item.setStatus("pending");
            itemMapper.insert(item);
            itemIdsByArticleId.put(plan.articleId(), item.getId());
        }
        triggerAsyncExecutionAfterCommit();
        return new SystemPublishJobResult(job.getId(), itemIdsByArticleId);
    }

    @Scheduled(fixedDelayString = "${geo.content.batch-publish.poll-ms:30000}")
    public void executeScheduledItems() {
        String lockValue = UUID.randomUUID().toString();
        try {
            if (!tryAcquireSchedulerLock(lockValue)) {
                return;
            }
            executeDueItems(20);
        } catch (Exception ex) {
            log.warn("batch article publish scheduler failed: {}", ex.getMessage(), ex);
        } finally {
            releaseSchedulerLock(lockValue);
        }
    }

    private boolean tryAcquireSchedulerLock(String lockValue) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                schedulerLockKey,
                lockValue,
                Math.max(schedulerLockTtlSeconds, 1),
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(locked);
    }

    private void releaseSchedulerLock(String lockValue) {
        try {
            String current = redisTemplate.opsForValue().get(schedulerLockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(schedulerLockKey);
            }
        } catch (Exception ex) {
            log.warn("batch article publish scheduler lock release failed: {}", ex.getMessage());
        }
    }

    public void executeDueItems(int limit) {
        recoverStaleRunningItems();
        int submitLimit = Math.max(1, limit);
        int candidateLimit = Math.max(submitLimit, Math.min(Math.max(1, dueCandidateScanSize), 500));
        List<BatchArticlePublishItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getStatus, "pending")
                        .le(BatchArticlePublishItem::getPlannedAt, LocalDateTime.now())
                        .orderByAsc(BatchArticlePublishItem::getPlannedAt, BatchArticlePublishItem::getId)
                        .last("LIMIT " + candidateLimit)
        );
        Set<String> submittedLanes = new HashSet<>();
        for (BatchArticlePublishItem item : items) {
            if (submittedLanes.size() >= submitLimit) {
                break;
            }
            if (!submittedLanes.add(publishLaneKey(item))) {
                continue;
            }
            batchPublishExecutor.execute(() -> executeOne(item));
        }
    }

    public Page<BatchArticlePublishJobSummary> page(long current, long size, String status) {
        return page(current, size, status, "all");
    }

    public Page<BatchArticlePublishJobSummary> page(long current, long size, String status, String jobSource) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<BatchArticlePublishJob> wrapper = new LambdaQueryWrapper<BatchArticlePublishJob>()
                .orderByDesc(BatchArticlePublishJob::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BatchArticlePublishJob::getStatus, status.trim());
        }
        String normalizedSource = normalizeJobSource(jobSource);
        if ("manual".equals(normalizedSource)) {
            wrapper.notLikeRight(BatchArticlePublishJob::getJobName, "自动分发_");
        } else if ("auto".equals(normalizedSource)) {
            wrapper.likeRight(BatchArticlePublishJob::getJobName, "自动分发_");
        }
        Page<BatchArticlePublishJob> page = jobMapper.selectPage(new Page<>(current, size), wrapper);
        Page<BatchArticlePublishJobSummary> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toSummary).collect(Collectors.toList()));
        return result;
    }

    private BatchArticlePublishItem buildItem(Long jobId,
                                              Long articleId,
                                              LocalDateTime baseTime,
                                              int intervalMinutes,
                                              Map<String, Integer> platformIndex,
                                              PublishSite industrySite,
                                              PublishSite forumSite,
                                              Integer forumFid) {
        ArticleDraft article = requireArticle(articleId);
        if (!ACTIVE_ARTICLE_STATUS.contains(article.getStatus())) {
            throw new BizException(400, "article " + articleId + " is not approved or unpublished");
        }
        Project project = requireProject(article.getProjectId());
        BatchArticleGenerationTask generationTask = latestGenerationTask(articleId);
        String contentStyle = generationTask == null || !StringUtils.hasText(generationTask.getContentStyle())
                ? article.getContentStyle()
                : generationTask.getContentStyle();
        PlatformTarget platform = resolvePlatform(contentStyle);

        if ("agent_site".equals(platform.platformKey()) && project.getBrandId() == null) {
            throw new BizException(400, "article " + articleId + " project has no brand for Agent official site publish");
        }
        ensureMedicalOfficialPublishAllowed(article, platform.platformKey());
        PublishSite resolvedIndustrySite = industrySite;
        if ("industry_site".equals(platform.platformKey()) && resolvedIndustrySite == null) {
            resolvedIndustrySite = resolveBrandIndustrySite(project);
        }
        PublishSite resolvedForumSite = null;
        Integer resolvedForumFid = null;
        if ("forum_site".equals(platform.platformKey())) {
            resolvedForumSite = forumSite == null ? resolveDefaultForumSite() : forumSite;
            Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
            resolvedForumFid = forumBoardRoutingService.resolveForumFid(resolvedForumSite, project, brand, forumFid);
        }

        int index = platformIndex.merge(platform.platformKey(), 1, Integer::sum) - 1;
        BatchArticlePublishItem item = new BatchArticlePublishItem();
        item.setJobId(jobId);
        item.setArticleId(articleId);
        item.setProjectId(article.getProjectId());
        item.setPlatformKey(platform.platformKey());
        item.setContentStyle(contentStyle);
        item.setTargetSiteId(switch (platform.platformKey()) {
            case "industry_site" -> resolvedIndustrySite.getId();
            case "forum_site" -> resolvedForumSite.getId();
            default -> null;
        });
        item.setTargetForumFid("forum_site".equals(platform.platformKey()) ? resolvedForumFid : null);
        item.setTargetBrandId("agent_site".equals(platform.platformKey()) ? project.getBrandId() : null);
        item.setPlannedAt(baseTime.plusMinutes((long) index * intervalMinutes));
        item.setStatus("pending");
        return item;
    }

    private void executeOne(BatchArticlePublishItem item) {
        LockLease laneLock = tryAcquirePublishLane(item);
        if (laneLock == null) {
            return;
        }
        boolean refreshJob = false;
        try {
            int locked = itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                    .eq(BatchArticlePublishItem::getId, item.getId())
                    .eq(BatchArticlePublishItem::getStatus, "pending")
                    .set(BatchArticlePublishItem::getStatus, "running")
                    .set(BatchArticlePublishItem::getErrorMessage, null));
            if (locked == 0) {
                return;
            }
            if (!tryAcquirePublishThrottle(item)) {
                itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getId, item.getId())
                        .eq(BatchArticlePublishItem::getStatus, "running")
                        .set(BatchArticlePublishItem::getStatus, "pending"));
                return;
            }
            markJobRunning(item.getJobId());
            refreshJob = true;
            if (hasSuccessfulPublishForSameTarget(item)) {
                log.warn("batch article publish duplicate target skipped itemId={} articleId={} platform={} targetSiteId={} targetBrandId={} targetForumFid={}",
                        item.getId(), item.getArticleId(), item.getPlatformKey(), item.getTargetSiteId(),
                        item.getTargetBrandId(), item.getTargetForumFid());
                itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getId, item.getId())
                        .eq(BatchArticlePublishItem::getStatus, "running")
                        .set(BatchArticlePublishItem::getStatus, "success")
                        .set(BatchArticlePublishItem::getErrorMessage, "重复发布保护：同一文章已成功发布到相同目标"));
                return;
            }
            BatchArticlePublishJob job = jobMapper.selectById(item.getJobId());
            DistributionTask task = executeDistribution(item, job);
            if ("failed".equals(task.getStatus())) {
                itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getId, item.getId())
                        .set(BatchArticlePublishItem::getDistributionTaskId, task.getId()));
                throw new BizException(500, StringUtils.hasText(task.getErrorMessage()) ? task.getErrorMessage() : "文章分发失败");
            }
            itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                    .eq(BatchArticlePublishItem::getId, item.getId())
                    .set(BatchArticlePublishItem::getStatus, "success")
                    .set(BatchArticlePublishItem::getDistributionTaskId, task.getId())
                    .set(BatchArticlePublishItem::getErrorMessage, null));
        } catch (Exception ex) {
            itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                    .eq(BatchArticlePublishItem::getId, item.getId())
                    .set(BatchArticlePublishItem::getStatus, "failed")
                    .set(BatchArticlePublishItem::getErrorMessage, trimError(ex.getMessage())));
        } finally {
            if (refreshJob) {
                refreshJobStatus(item.getJobId());
            }
            releasePublishLane(laneLock);
            triggerAsyncExecutionSoon();
        }
    }

    private boolean hasSuccessfulPublishForSameTarget(BatchArticlePublishItem item) {
        LambdaQueryWrapper<BatchArticlePublishItem> wrapper = new LambdaQueryWrapper<BatchArticlePublishItem>()
                .eq(BatchArticlePublishItem::getArticleId, item.getArticleId())
                .eq(BatchArticlePublishItem::getPlatformKey, item.getPlatformKey())
                .eq(BatchArticlePublishItem::getStatus, "success")
                .ne(BatchArticlePublishItem::getId, item.getId());
        applyTargetMatch(wrapper, item);
        Long count = itemMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private void applyTargetMatch(LambdaQueryWrapper<BatchArticlePublishItem> wrapper, BatchArticlePublishItem item) {
        if (item.getTargetSiteId() == null) {
            wrapper.isNull(BatchArticlePublishItem::getTargetSiteId);
        } else {
            wrapper.eq(BatchArticlePublishItem::getTargetSiteId, item.getTargetSiteId());
        }
        if (item.getTargetBrandId() == null) {
            wrapper.isNull(BatchArticlePublishItem::getTargetBrandId);
        } else {
            wrapper.eq(BatchArticlePublishItem::getTargetBrandId, item.getTargetBrandId());
        }
        if (item.getTargetForumFid() == null) {
            wrapper.isNull(BatchArticlePublishItem::getTargetForumFid);
        } else {
            wrapper.eq(BatchArticlePublishItem::getTargetForumFid, item.getTargetForumFid());
        }
    }

    private LockLease tryAcquirePublishLane(BatchArticlePublishItem item) {
        String lockValue = UUID.randomUUID().toString();
        String lockKey = schedulerLockKey + ":lane:" + publishLaneKey(item);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Math.max(5, runningTimeoutMinutes + 5),
                TimeUnit.MINUTES
        );
        return Boolean.TRUE.equals(acquired) ? new LockLease(lockKey, lockValue) : null;
    }

    private void releasePublishLane(LockLease lease) {
        if (lease == null) {
            return;
        }
        try {
            if (lease.value().equals(redisTemplate.opsForValue().get(lease.key()))) {
                redisTemplate.delete(lease.key());
            }
        } catch (Exception ex) {
            log.warn("batch article publish lane lock release failed key={} error={}", lease.key(), ex.getMessage());
        }
    }

    private boolean tryAcquirePublishThrottle(BatchArticlePublishItem item) {
        if (!"forum_site".equals(item.getPlatformKey()) || item.getTargetSiteId() == null
                || forumSiteMinExecutionIntervalMs <= 0) {
            return true;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                schedulerLockKey + ":forum-site:" + item.getTargetSiteId() + ":throttle",
                UUID.randomUUID().toString(),
                Math.max(1, forumSiteMinExecutionIntervalMs),
                TimeUnit.MILLISECONDS
        );
        return Boolean.TRUE.equals(acquired);
    }

    private String publishLaneKey(BatchArticlePublishItem item) {
        String platform = StringUtils.hasText(item.getPlatformKey()) ? item.getPlatformKey() : "unknown";
        if ("forum_site".equals(platform) || "industry_site".equals(platform)) {
            return platform + ":site:" + item.getTargetSiteId();
        }
        if ("agent_site".equals(platform)) {
            return platform + ":brand:" + item.getTargetBrandId();
        }
        return platform + ":item:" + item.getId();
    }

    private DistributionTask executeDistribution(BatchArticlePublishItem item, BatchArticlePublishJob job) {
        Long operatorId = job == null ? null : job.getCreatedBy();
        if ("agent_site".equals(item.getPlatformKey())) {
            return contentDistributionService.distributeToAsOperator(
                    item.getArticleId(),
                    new TargetContext.BrandGeoSiteTarget(item.getTargetBrandId(), null),
                    operatorId
            );
        }
        if ("industry_site".equals(item.getPlatformKey())) {
            PublishSite site = requireIndustrySite(item.getTargetSiteId());
            requireCurrentBrandIndustrySiteForAutoJob(item, site, job);
            return contentDistributionService.distributeToAsOperator(
                    item.getArticleId(),
                    new TargetContext.IndustrySiteTarget(site),
                    operatorId
            );
        }
        if ("forum_site".equals(item.getPlatformKey())) {
            PublishSite site = requireForumSite(item.getTargetSiteId());
            return contentDistributionService.distributeToAsOperator(
                    item.getArticleId(),
                    new TargetContext.ForumSiteTarget(site, null, item.getTargetForumFid()),
                    operatorId
            );
        }
        throw new BizException(400, "不支持的发布平台：" + item.getPlatformKey());
    }

    private void requireCurrentBrandIndustrySiteForAutoJob(BatchArticlePublishItem item,
                                                           PublishSite targetSite,
                                                           BatchArticlePublishJob job) {
        if (!isAutoDistributionJob(job)) {
            return;
        }
        Project project = requireProject(item.getProjectId());
        if (project.getBrandId() == null) {
            throw new BizException(400, "品牌行业资讯站配置已取消或变更，跳过旧自动分发计划");
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || brand.getDeletedAt() != null || !StringUtils.hasText(brand.getIndustrySiteCode())) {
            throw new BizException(400, "品牌行业资讯站配置已取消或变更，跳过旧自动分发计划");
        }
        PublishSite currentSite = resolveBrandIndustrySite(project);
        if (!Objects.equals(currentSite.getId(), targetSite.getId())) {
            throw new BizException(400, "品牌行业资讯站配置已取消或变更，跳过旧自动分发计划");
        }
    }

    private boolean isAutoDistributionJob(BatchArticlePublishJob job) {
        return job != null
                && StringUtils.hasText(job.getJobName())
                && job.getJobName().trim().startsWith("自动分发_");
    }

    private void markJobRunning(Long jobId) {
        jobMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishJob>()
                .eq(BatchArticlePublishJob::getId, jobId)
                .eq(BatchArticlePublishJob::getStatus, "pending")
                .set(BatchArticlePublishJob::getStatus, "running")
                .set(BatchArticlePublishJob::getStartedAt, LocalDateTime.now()));
    }

    private void refreshJobStatus(Long jobId) {
        List<BatchArticlePublishItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getJobId, jobId)
                        .select(BatchArticlePublishItem::getStatus)
        );
        long success = items.stream().filter(item -> "success".equals(item.getStatus())).count();
        long failed = items.stream().filter(item -> "failed".equals(item.getStatus())).count();
        boolean done = success + failed == items.size();
        String status = done
                ? (failed == 0 ? "completed" : (success == 0 ? "failed" : "partial_failed"))
                : "running";
        LambdaUpdateWrapper<BatchArticlePublishJob> wrapper = new LambdaUpdateWrapper<BatchArticlePublishJob>()
                .eq(BatchArticlePublishJob::getId, jobId)
                .set(BatchArticlePublishJob::getStatus, status)
                .set(BatchArticlePublishJob::getSuccessCount, (int) success)
                .set(BatchArticlePublishJob::getFailedCount, (int) failed);
        if (done) {
            wrapper.set(BatchArticlePublishJob::getFinishedAt, LocalDateTime.now());
        }
        jobMapper.update(null, wrapper);
    }

    public BatchArticlePublishResponse response(Long jobId) {
        BatchArticlePublishJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(404, "批量发布任务不存在");
        }
        List<BatchArticlePublishItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getJobId, jobId)
                        .orderByAsc(BatchArticlePublishItem::getPlannedAt, BatchArticlePublishItem::getId)
        );
        BatchArticlePublishResponse response = new BatchArticlePublishResponse();
        response.setJobId(job.getId());
        response.setJobName(displayJobName(job.getJobName()));
        response.setJobSource(jobSource(job));
        response.setPublishMode(job.getPublishMode());
        response.setStatus(job.getStatus());
        response.setScheduledAt(job.getScheduledAt());
        response.setIntervalMinutes(job.getIntervalMinutes());
        response.setTotalCount(job.getTotalCount());
        response.setSuccessCount(job.getSuccessCount());
        response.setFailedCount(job.getFailedCount());
        response.setItems(items.stream().map(this::toResponseItem).collect(Collectors.toList()));
        return response;
    }

    private BatchArticlePublishJobSummary toSummary(BatchArticlePublishJob job) {
        BatchArticlePublishJobSummary summary = new BatchArticlePublishJobSummary();
        summary.setJobId(job.getId());
        summary.setJobName(displayJobName(job.getJobName()));
        summary.setJobSource(jobSource(job));
        summary.setPublishMode(job.getPublishMode());
        summary.setStatus(job.getStatus());
        summary.setScheduledAt(job.getScheduledAt());
        summary.setIntervalMinutes(job.getIntervalMinutes());
        summary.setTotalCount(job.getTotalCount());
        summary.setSuccessCount(job.getSuccessCount());
        summary.setFailedCount(job.getFailedCount());
        summary.setCreatedBy(job.getCreatedBy());
        summary.setCreatedAt(job.getCreatedAt());
        summary.setStartedAt(job.getStartedAt());
        summary.setFinishedAt(job.getFinishedAt());
        return summary;
    }

    private String normalizeJobSource(String value) {
        if (!StringUtils.hasText(value)) {
            return "manual";
        }
        String source = value.trim();
        if (Set.of("manual", "auto", "all").contains(source)) {
            return source;
        }
        throw new BizException(400, "任务来源只能为 manual、auto 或 all");
    }

    private String jobSource(BatchArticlePublishJob job) {
        return isAutoDistributionJob(job) ? "auto" : "manual";
    }

    private String buildJobName(String publishMode, List<Long> articleIds, LocalDateTime baseTime) {
        String subjectName = "任务";
        ArticleDraft firstArticle = articleIds.isEmpty() ? null : articleDraftMapper.selectById(articleIds.get(0));
        if (firstArticle != null && firstArticle.getProjectId() != null) {
            Project project = projectMapper.selectById(firstArticle.getProjectId());
            subjectName = compactJobNamePart(firstText(
                    project == null ? null : project.getBrandName(),
                    project == null ? null : project.getProjectName(),
                    firstArticle.getTitle()
            ));
        }
        return "批量_" + subjectName + "_" + baseTime.format(JOB_NAME_DATE);
    }

    private String displayJobName(String jobName) {
        if (!StringUtils.hasText(jobName)) {
            return jobName;
        }
        Matcher matcher = LEGACY_AUTO_DISTRIBUTION_JOB_NAME.matcher(jobName.trim());
        if (!matcher.matches()) {
            return jobName;
        }
        Long projectId;
        try {
            projectId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ex) {
            return jobName;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || !StringUtils.hasText(project.getProjectName())) {
            return jobName;
        }
        return "自动分发_" + compactJobNamePart(project.getProjectName()) + "_" + matcher.group(2);
    }

    private String compactJobNamePart(String name) {
        String text = StringUtils.hasText(name) ? name.trim().replaceAll("\\s+", "") : "任务";
        int maxLength = 24;
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private BatchArticlePublishResponse.Item toResponseItem(BatchArticlePublishItem item) {
        BatchArticlePublishResponse.Item vo = new BatchArticlePublishResponse.Item();
        vo.setId(item.getId());
        vo.setArticleId(item.getArticleId());
        ArticleDraft article = articleDraftMapper.selectById(item.getArticleId());
        if (article != null) {
            vo.setArticleTitle(article.getTitle());
            Project project = projectMapper.selectById(article.getProjectId());
            vo.setProjectName(project == null ? null : project.getProjectName());
        }
        vo.setPlatformKey(item.getPlatformKey());
        vo.setContentStyle(item.getContentStyle());
        vo.setTargetSiteId(item.getTargetSiteId());
        vo.setTargetForumFid(item.getTargetForumFid());
        if (item.getTargetSiteId() != null) {
            PublishSite site = publishSiteMapper.selectById(item.getTargetSiteId());
            vo.setTargetSiteName(site == null ? null : site.getSiteName());
        }
        vo.setTargetBrandId(item.getTargetBrandId());
        vo.setPlannedAt(item.getPlannedAt());
        vo.setStatus(item.getStatus());
        vo.setDistributionTaskId(item.getDistributionTaskId());
        if (item.getDistributionTaskId() != null) {
            DistributionTask task = distributionTaskMapper.selectById(item.getDistributionTaskId());
            vo.setPublishedAt(task == null ? null : (task.getPublishedAt() != null ? task.getPublishedAt() : task.getFinishedAt()));
        }
        vo.setErrorMessage(item.getErrorMessage());
        return vo;
    }

    private String normalizePublishMode(String publishMode) {
        if (!StringUtils.hasText(publishMode)) {
            throw new BizException(400, "请选择发布模式");
        }
        String value = publishMode.trim();
        if (!Set.of("now", "scheduled").contains(value)) {
            throw new BizException(400, "发布模式只能为立即发布或定时发布");
        }
        return value;
    }

    private LocalDateTime resolveBaseTime(String publishMode, String scheduledAt) {
        if ("now".equals(publishMode)) {
            return LocalDateTime.now();
        }
        if (!StringUtils.hasText(scheduledAt)) {
            throw new BizException(400, "请选择计划发布时间");
        }
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(scheduledAt.trim(), DATE_TIME);
        } catch (Exception ex) {
            throw new BizException(400, "计划发布时间格式必须为 yyyy-MM-dd HH:mm:ss");
        }
        if (time.isBefore(LocalDateTime.now())) {
            throw new BizException(400, "计划发布时间不能早于当前时间");
        }
        return time;
    }

    private PlatformTarget resolvePlatform(String contentStyle) {
        if ("agent_site_article".equals(contentStyle) || "linkedin".equals(contentStyle)) {
            return new PlatformTarget("agent_site");
        }
        if ("industry_site".equals(contentStyle)) {
            return new PlatformTarget("industry_site");
        }
        if ("forum".equals(contentStyle)) {
            return new PlatformTarget("forum_site");
        }
        Map<String, String> blocked = Map.of(
                "toutiao", "今日头条不允许自动发布",
                "wechat", "公众号不允许自动发布",
                "zhihu", "知乎不允许自动发布",
                "douyin", "抖音图文暂不纳入批量发布",
                "authority_media", "权威媒体不允许自动发布"
        );
        throw new BizException(400, blocked.getOrDefault(contentStyle, "article content style does not support auto publish"));
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(404, "文章不存在：" + articleId);
        }
        return article;
    }

    private void ensureMedicalOfficialPublishAllowed(ArticleDraft article, String platformKey) {
        if (!"agent_site".equals(platformKey)) {
            return;
        }
        if (!MedicalArticleConstants.TIER_OFFICIAL_SITE.equals(article.getMedicalChannelTier())) {
            return;
        }
        if (!MedicalArticleConstants.COMPLIANCE_PASSED.equals(article.getComplianceStatus())) {
            throw new BizException(400, "article " + article.getId() + " medical compliance is not passed");
        }
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "项目不存在：" + projectId);
        }
        return project;
    }

    private BatchArticleGenerationTask latestGenerationTask(Long articleId) {
        return generationTaskMapper.selectOne(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getArticleId, articleId)
                        .orderByDesc(BatchArticleGenerationTask::getId)
                        .last("LIMIT 1")
        );
    }

    private PublishSite requireIndustrySite(Long siteId) {
        PublishSite site = publishSiteMapper.selectById(siteId);
        if (site == null) {
            throw new BizException(404, "发布站点不存在");
        }
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "publish site is not active");
        }
        if ("brand_geo_site".equalsIgnoreCase(site.getIntegrationMethod())
                || "agent_official_site".equalsIgnoreCase(site.getSiteCode())) {
            throw new BizException(400, "publish site is not an industry site");
        }
        String integrationMethod = site.getIntegrationMethod();
        if (StringUtils.hasText(integrationMethod)
                && Set.of("forum_playwright", "discuz_http").contains(integrationMethod.toLowerCase())) {
            throw new BizException(400, "publish site is a forum target; use forum publish target");
        }
        return site;
    }

    private PublishSite requireForumSite(Long siteId) {
        PublishSite site = publishSiteMapper.selectById(siteId);
        if (site == null) {
            throw new BizException(404, "论坛发布站点不存在");
        }
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "forum publish site is not active");
        }
        String integrationMethod = site.getIntegrationMethod();
        if (!StringUtils.hasText(integrationMethod)
                || !Set.of("forum_playwright", "discuz_http").contains(integrationMethod.toLowerCase())) {
            throw new BizException(400, "publish site is not a supported forum target");
        }
        return site;
    }

    private PublishSite resolveDefaultForumSite() {
        List<PublishSite> sites = resolveActiveForumSites();
        return sites.get(0);
    }

    private List<PublishSite> resolveActiveForumSites() {
        List<PublishSite> sites = publishSiteMapper.selectList(
                new LambdaQueryWrapper<PublishSite>()
                        .in(PublishSite::getIntegrationMethod, "forum_playwright", "discuz_http")
                        .eq(PublishSite::getStatus, "active")
                        .orderByAsc(PublishSite::getId)
        );
        if (sites.isEmpty()) {
            throw new BizException(400, "forum publish site is not configured");
        }
        return sites;
    }

    private PublishSite resolveBrandIndustrySite(Project project) {
        if (project.getBrandId() == null) {
            throw new BizException(400, "industry site articles require a project brand");
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "品牌不存在");
        }
        if (!StringUtils.hasText(brand.getIndustrySiteCode())) {
            throw new BizException(400, "brand industry site code is not configured");
        }
        PublishSite site = publishSiteMapper.selectOne(
                new LambdaQueryWrapper<PublishSite>()
                        .eq(PublishSite::getSiteCode, brand.getIndustrySiteCode().trim())
                        .last("LIMIT 1")
        );
        if (site == null && StringUtils.hasText(brand.getIndustrySiteName())) {
            site = publishSiteMapper.selectOne(
                    new LambdaQueryWrapper<PublishSite>()
                            .eq(PublishSite::getSiteName, brand.getIndustrySiteName().trim())
                            .last("LIMIT 1")
            );
        }
        if (site == null) {
            throw new BizException(404, "品牌配置的行业资讯站不存在");
        }
        return requireIndustrySite(site.getId());
    }

    private void recoverStaleRunningItems() {
        if (runningTimeoutMinutes <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, runningTimeoutMinutes));
        int recovered = itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                .eq(BatchArticlePublishItem::getStatus, "running")
                .lt(BatchArticlePublishItem::getUpdatedAt, cutoff)
                .set(BatchArticlePublishItem::getStatus, "failed")
                .set(BatchArticlePublishItem::getErrorMessage,
                        "批量发布任务运行超过 " + Math.max(1, runningTimeoutMinutes) + " 分钟，已自动释放以恢复队列"));
        if (recovered > 0) {
            log.warn("batch article publish stale running items recovered count={} timeoutMinutes={}",
                    recovered, Math.max(1, runningTimeoutMinutes));
        }
    }

    private void triggerAsyncExecutionAfterCommit() {
        Runnable task = this::triggerAsyncExecutionSoon;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void triggerAsyncExecutionSoon() {
        batchPublishExecutor.execute(() -> {
            try {
                executeDueItems(1);
            } catch (Exception ex) {
                log.warn("batch article publish async trigger failed: {}", ex.getMessage(), ex);
            }
        });
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record PlatformTarget(String platformKey) {
    }

    public record SystemPublishPlan(Long articleId,
                                    String platformKey,
                                    String contentStyle,
                                    Long targetSiteId,
                                    Long targetBrandId,
                                    Integer targetForumFid,
                                    LocalDateTime plannedAt) {
    }

    public record SystemPublishJobResult(Long jobId, Map<Long, Long> itemIdsByArticleId) {
    }

    private record LockLease(String key, String value) {
    }
}
