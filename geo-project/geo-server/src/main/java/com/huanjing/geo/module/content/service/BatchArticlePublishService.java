package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchArticlePublishService {

    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BatchArticlePublishJobMapper jobMapper;
    private final BatchArticlePublishItemMapper itemMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final BatchArticleGenerationTaskMapper generationTaskMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final CurrentUserService currentUserService;
    private final ContentDistributionService contentDistributionService;

    @Transactional
    public BatchArticlePublishResponse submit(BatchArticlePublishRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        String publishMode = normalizePublishMode(request.getPublishMode());
        int intervalMinutes = request.getIntervalMinutes() == null ? 30 : request.getIntervalMinutes();
        LocalDateTime baseTime = resolveBaseTime(publishMode, request.getScheduledAt());

        List<Long> articleIds = request.getArticleIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            throw new BizException(400, "articleIds cannot be empty");
        }
        if (articleIds.size() > 100) {
            throw new BizException(400, "single batch publish cannot exceed 100 articles");
        }

        PublishSite manualIndustrySite = request.getIndustrySiteId() == null ? null : requireIndustrySite(request.getIndustrySiteId());

        BatchArticlePublishJob job = new BatchArticlePublishJob();
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
            BatchArticlePublishItem item = buildItem(job.getId(), articleId, baseTime, intervalMinutes, platformIndex, manualIndustrySite);
            itemMapper.insert(item);
        }

        if ("now".equals(publishMode)) {
            executeDueItems(100);
        }
        return response(job.getId());
    }

    @Scheduled(fixedDelayString = "${geo.content.batch-publish.poll-ms:30000}")
    public void executeScheduledItems() {
        try {
            executeDueItems(20);
        } catch (Exception ex) {
            log.warn("batch article publish scheduler failed: {}", ex.getMessage(), ex);
        }
    }

    public void executeDueItems(int limit) {
        List<BatchArticlePublishItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getStatus, "pending")
                        .le(BatchArticlePublishItem::getPlannedAt, LocalDateTime.now())
                        .orderByAsc(BatchArticlePublishItem::getPlannedAt, BatchArticlePublishItem::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100)))
        );
        for (BatchArticlePublishItem item : items) {
            executeOne(item);
        }
    }

    public Page<BatchArticlePublishJobSummary> page(long current, long size, String status) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<BatchArticlePublishJob> wrapper = new LambdaQueryWrapper<BatchArticlePublishJob>()
                .orderByDesc(BatchArticlePublishJob::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BatchArticlePublishJob::getStatus, status.trim());
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
                                              PublishSite industrySite) {
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
        PublishSite resolvedIndustrySite = industrySite;
        if ("industry_site".equals(platform.platformKey()) && resolvedIndustrySite == null) {
            resolvedIndustrySite = resolveBrandIndustrySite(project);
        }

        int index = platformIndex.merge(platform.platformKey(), 1, Integer::sum) - 1;
        BatchArticlePublishItem item = new BatchArticlePublishItem();
        item.setJobId(jobId);
        item.setArticleId(articleId);
        item.setProjectId(article.getProjectId());
        item.setPlatformKey(platform.platformKey());
        item.setContentStyle(contentStyle);
        item.setTargetSiteId("industry_site".equals(platform.platformKey()) ? resolvedIndustrySite.getId() : null);
        item.setTargetBrandId("agent_site".equals(platform.platformKey()) ? project.getBrandId() : null);
        item.setPlannedAt(baseTime.plusMinutes((long) index * intervalMinutes));
        item.setStatus("pending");
        return item;
    }

    private void executeOne(BatchArticlePublishItem item) {
        if (hasRunningPlatformItem(item)) {
            return;
        }
        int locked = itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                .eq(BatchArticlePublishItem::getId, item.getId())
                .eq(BatchArticlePublishItem::getStatus, "pending")
                .set(BatchArticlePublishItem::getStatus, "running")
                .set(BatchArticlePublishItem::getErrorMessage, null));
        if (locked == 0) {
            return;
        }
        if (hasOtherRunningPlatformItem(item)) {
            itemMapper.update(null, new LambdaUpdateWrapper<BatchArticlePublishItem>()
                    .eq(BatchArticlePublishItem::getId, item.getId())
                    .eq(BatchArticlePublishItem::getStatus, "running")
                    .set(BatchArticlePublishItem::getStatus, "pending"));
            return;
        }
        markJobRunning(item.getJobId());
        try {
            BatchArticlePublishJob job = jobMapper.selectById(item.getJobId());
            DistributionTask task = executeDistribution(item, job.getCreatedBy());
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
            refreshJobStatus(item.getJobId());
        }
    }

    private DistributionTask executeDistribution(BatchArticlePublishItem item, Long operatorId) {
        if ("agent_site".equals(item.getPlatformKey())) {
            return contentDistributionService.distributeToAsOperator(
                    item.getArticleId(),
                    new TargetContext.BrandGeoSiteTarget(item.getTargetBrandId(), null),
                    operatorId
            );
        }
        if ("industry_site".equals(item.getPlatformKey())) {
            PublishSite site = requireIndustrySite(item.getTargetSiteId());
            return contentDistributionService.distributeToAsOperator(
                    item.getArticleId(),
                    new TargetContext.IndustrySiteTarget(site),
                    operatorId
            );
        }
        throw new BizException(400, "unsupported publish platform: " + item.getPlatformKey());
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
            throw new BizException(404, "batch publish job not found");
        }
        List<BatchArticlePublishItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getJobId, jobId)
                        .orderByAsc(BatchArticlePublishItem::getPlannedAt, BatchArticlePublishItem::getId)
        );
        BatchArticlePublishResponse response = new BatchArticlePublishResponse();
        response.setJobId(job.getId());
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
        vo.setTargetBrandId(item.getTargetBrandId());
        vo.setPlannedAt(item.getPlannedAt());
        vo.setStatus(item.getStatus());
        vo.setDistributionTaskId(item.getDistributionTaskId());
        vo.setErrorMessage(item.getErrorMessage());
        return vo;
    }

    private String normalizePublishMode(String publishMode) {
        if (!StringUtils.hasText(publishMode)) {
            throw new BizException(400, "publishMode is required");
        }
        String value = publishMode.trim();
        if (!Set.of("now", "scheduled").contains(value)) {
            throw new BizException(400, "publishMode must be now or scheduled");
        }
        return value;
    }

    private LocalDateTime resolveBaseTime(String publishMode, String scheduledAt) {
        if ("now".equals(publishMode)) {
            return LocalDateTime.now();
        }
        if (!StringUtils.hasText(scheduledAt)) {
            throw new BizException(400, "scheduledAt is required");
        }
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(scheduledAt.trim(), DATE_TIME);
        } catch (Exception ex) {
            throw new BizException(400, "scheduledAt format must be yyyy-MM-dd HH:mm:ss");
        }
        if (time.isBefore(LocalDateTime.now())) {
            throw new BizException(400, "scheduledAt cannot be earlier than now");
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
        Map<String, String> blocked = Map.of(
                "toutiao", "今日头条不允许自动发布",
                "wechat", "公众号不允许自动发布",
                "zhihu", "知乎不允许自动发布",
                "douyin_image_text", "抖音图文不允许自动发布",
                "authority_media", "权威媒体不允许自动发布",
                "forum", "论坛发布执行器暂未接入"
        );
        throw new BizException(400, blocked.getOrDefault(contentStyle, "article content style does not support auto publish"));
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(404, "article not found: " + articleId);
        }
        return article;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "project not found: " + projectId);
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
            throw new BizException(404, "publish site not found");
        }
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "publish site is not active");
        }
        if ("brand_geo_site".equalsIgnoreCase(site.getIntegrationMethod())
                || "agent_official_site".equalsIgnoreCase(site.getSiteCode())) {
            throw new BizException(400, "publish site is not an industry site");
        }
        return site;
    }

    private PublishSite resolveBrandIndustrySite(Project project) {
        if (project.getBrandId() == null) {
            throw new BizException(400, "industry site articles require a project brand");
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "brand not found");
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
            throw new BizException(404, "brand configured industry publish site not found");
        }
        return requireIndustrySite(site.getId());
    }

    private boolean hasRunningPlatformItem(BatchArticlePublishItem item) {
        return hasOtherRunningPlatformItem(item);
    }

    private boolean hasOtherRunningPlatformItem(BatchArticlePublishItem item) {
        Long running = itemMapper.selectCount(
                new LambdaQueryWrapper<BatchArticlePublishItem>()
                        .eq(BatchArticlePublishItem::getPlatformKey, item.getPlatformKey())
                        .eq(BatchArticlePublishItem::getStatus, "running")
                        .ne(BatchArticlePublishItem::getId, item.getId())
        );
        return running != null && running > 0;
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record PlatformTarget(String platformKey) {
    }
}
