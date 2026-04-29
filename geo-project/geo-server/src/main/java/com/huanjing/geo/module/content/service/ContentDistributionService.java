package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.DistributionAttemptVO;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSiteVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.content.service.adapter.SiteAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentDistributionService {

    private static final ZoneId SH_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<String> SUCCESS_TASK_STATUS = Set.of("submitted", "confirmed");
    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final Set<String> DISTRIBUTE_ALLOWED_ROLES = Set.of("super_admin", "manager", "delivery_manager", "operator");
    private static final String GENERAL_INDUSTRY = "general";

    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final PackagePublishConfigMapper packagePublishConfigMapper;
    private final ProjectPublishQuotaMapper projectPublishQuotaMapper;
    private final ProjectMapper projectMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final CurrentUserService currentUserService;
    private final SystemAlertService systemAlertService;
    private final List<SiteAdapter> siteAdapters;
    private final BrandMapper brandMapper;

    @Transactional
    public DistributionTask distribute(Long articleId, Long siteId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        ensureDistributeRole(operator);
        ArticleDraft article = requireArticle(articleId);
        if (!ACTIVE_ARTICLE_STATUS.contains(article.getStatus())) {
            throw new BizException(400, "Only approved/unpublished article can distribute");
        }
        Project project = requireProject(article.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");

        PublishSite site = requireSite(siteId);
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "Site status is not active");
        }

        PackagePublishConfig packageConfig = requirePackagePublishConfig(project.getPackageType());
        List<String> allowedTiers = parseJsonArray(packageConfig.getAllowedSiteTiers());
        if (!allowedTiers.contains(site.getTier())) {
            throw new BizException(400, "Current package does not include " + site.getTier() + " tier sites");
        }
        String brandIndustry = requireProjectBrandIndustry(project);
        if (!matchIndustry(site, brandIndustry)) {
            throw new BizException(400, "该站点不适用于当前品牌行业");
        }

        String content = requireLatestContent(article.getId());
        ValidationResult validation = validateByMethod(article, content, site);
        if (!validation.isPassed()) {
            throw new BizException(400, String.join("; ", validation.getErrors()));
        }

        QuotaContext quota = validateQuota(project, packageConfig);
        DistributionTask task = createAttempt(article, site, operator.getId(), 1);
        article.setStatus("distributing");
        articleDraftMapper.updateById(article);

        if ("manual".equalsIgnoreCase(site.getIntegrationMethod())) {
            return task;
        }
        SubmitResult submitResult = executeByAdapter(article, site, content);
        finalizeExecution(task, article, project, submitResult, quota);
        return distributionTaskMapper.selectById(task.getId());
    }

    @Transactional
    public DistributionTask retry(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        ensureDistributeRole(operator);
        DistributionTask oldTask = requireTask(taskId);
        if (!"failed".equalsIgnoreCase(oldTask.getStatus())) {
            throw new BizException(400, "Only failed task can retry");
        }
        ArticleDraft article = requireArticle(oldTask.getArticleId());
        Project project = requireProject(oldTask.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        PublishSite site = requireSite(oldTask.getSiteId());
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "Site status is not active");
        }
        PackagePublishConfig packageConfig = requirePackagePublishConfig(project.getPackageType());
        String brandIndustry = requireProjectBrandIndustry(project);
        if (!matchIndustry(site, brandIndustry)) {
            throw new BizException(400, "该站点不适用于当前品牌行业");
        }
        QuotaContext quota = validateQuota(project, packageConfig);
        String content = requireLatestContent(article.getId());
        ValidationResult validation = validateByMethod(article, content, site);
        if (!validation.isPassed()) {
            throw new BizException(400, String.join("; ", validation.getErrors()));
        }

        DistributionTask task = createAttempt(article, site, operator.getId(), Optional.ofNullable(oldTask.getRetryCount()).orElse(0) + 1);
        article.setStatus("distributing");
        articleDraftMapper.updateById(article);
        if ("manual".equalsIgnoreCase(site.getIntegrationMethod())) {
            return task;
        }

        SubmitResult submitResult = executeByAdapter(article, site, content);
        finalizeExecution(task, article, project, submitResult, quota);
        return distributionTaskMapper.selectById(task.getId());
    }

    @Transactional
    public DistributionTask confirmManual(Long taskId, String publishedUrl, String responsePayload) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        ensureDistributeRole(operator);
        DistributionTask task = requireTask(taskId);
        if (!"manual".equalsIgnoreCase(task.getIntegrationMethod())) {
            throw new BizException(400, "Task is not manual integration");
        }
        if (!Set.of("pending", "submitting", "failed").contains(task.getStatus())) {
            throw new BizException(400, "Task status does not allow manual confirm");
        }
        ArticleDraft article = requireArticle(task.getArticleId());
        Project project = requireProject(task.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        PackagePublishConfig packageConfig = requirePackagePublishConfig(project.getPackageType());
        QuotaContext quota = validateQuota(project, packageConfig);

        task.setStatus("submitted");
        task.setPublishedUrl(publishedUrl.trim());
        task.setResponsePayload(StringUtils.hasText(responsePayload) ? responsePayload.trim() : null);
        task.setErrorMessage(null);
        task.setFinishedAt(LocalDateTime.now());
        distributionTaskMapper.updateById(task);

        article.setStatus("distributed");
        article.setPublishedAt(LocalDateTime.now());
        articleDraftMapper.updateById(article);
        increaseMonthlyQuota(quota.monthQuota);
        return distributionTaskMapper.selectById(taskId);
    }

    public Map<String, Object> distributionHistory(Long articleId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");

        List<DistributionTask> tasks = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, articleId)
                        .orderByDesc(DistributionTask::getCreatedAt, DistributionTask::getAttemptNo)
        );
        Map<Long, PublishSite> siteMap = mapSites(tasks.stream().map(DistributionTask::getSiteId).collect(Collectors.toSet()));
        List<DistributionAttemptVO> attempts = tasks.stream().map(task -> toAttemptVO(task, siteMap.get(task.getSiteId()))).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("articleStatus", article.getStatus());
        result.put("attempts", attempts);
        return result;
    }

    public PublishQuotaVO quota(Long projectId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(projectId);
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        PackagePublishConfig config = requirePackagePublishConfig(project.getPackageType());
        QuotaContext quota = resolveQuota(project, config);
        PublishQuotaVO vo = new PublishQuotaVO();
        vo.setMonth(quota.monthKey);
        vo.setMonthUsed(quota.monthQuota.getUsedCount());
        vo.setMonthLimit(quota.monthQuota.getMonthlyLimit());
        vo.setWeekUsed(quota.weekUsed);
        vo.setWeekLimit(config.getWeeklyPublishLimit());
        vo.setAllowedSiteTiers(parseJsonArray(config.getAllowedSiteTiers()));
        return vo;
    }

    public RecommendedSitesResponseVO recommendedSites(Long projectId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(projectId);
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        PackagePublishConfig config = requirePackagePublishConfig(project.getPackageType());
        String brandIndustry = requireProjectBrandIndustry(project);
        List<String> allowedTiers = parseJsonArray(config.getAllowedSiteTiers());

        List<PublishSite> sites = publishSiteMapper.selectList(
                new LambdaQueryWrapper<PublishSite>()
                        .eq(PublishSite::getIsFramework, 0)
                        .ne(PublishSite::getStatus, "suspended")
                        .ne(PublishSite::getStatus, "maintenance")
        );
        sites = sites.stream()
                .filter(site -> allowedTiers.contains(site.getTier()))
                .collect(Collectors.toList());
        List<PublishSite> industryMatchedSites = sites.stream()
                .filter(site -> matchIndustry(site, brandIndustry))
                .collect(Collectors.toList());
        boolean fallbackToGeneral = industryMatchedSites.isEmpty();
        if (fallbackToGeneral) {
            industryMatchedSites = sites.stream()
                    .filter(this::isGeneralOnly)
                    .collect(Collectors.toList());
        }
        Map<Long, BigDecimal> successRateMap = querySiteSuccessRate30d(sites.stream().map(PublishSite::getId).collect(Collectors.toSet()));
        List<RecommendedSiteVO> result = industryMatchedSites.stream().map(site -> {
            Set<String> siteIndustries = parseSiteIndustryTagSet(site);
            String matchType = determineMatchType(siteIndustries, brandIndustry);
            RecommendedSiteVO vo = new RecommendedSiteVO();
            vo.setSiteId(site.getId());
            vo.setSiteName(site.getSiteName());
            vo.setDomain(site.getDomain());
            vo.setTier(site.getTier());
            vo.setStatus(site.getStatus());
            vo.setIntegrationMethod(site.getIntegrationMethod());
            vo.setCurrentHealthStatus(site.getCurrentHealthStatus());
            vo.setFailureRate(site.getFailureRate());
            vo.setIndustryTags(new ArrayList<>(siteIndustries));
            vo.setContentConstraints(site.getContentConstraints());
            vo.setSuccessRate30d(successRateMap.getOrDefault(site.getId(), BigDecimal.ZERO));
            vo.setMatchType(matchType);
            return vo;
        }).sorted((a, b) -> {
            int c1 = Integer.compare(matchTypeRank(b.getMatchType()), matchTypeRank(a.getMatchType()));
            if (c1 != 0) return c1;
            int c2 = Integer.compare(tierRank(b.getTier()), tierRank(a.getTier()));
            if (c2 != 0) return c2;
            int c3 = Integer.compare(healthRank(a.getCurrentHealthStatus()), healthRank(b.getCurrentHealthStatus()));
            if (c3 != 0) return c3;
            int c4 = b.getSuccessRate30d().compareTo(a.getSuccessRate30d());
            if (c4 != 0) return c4;
            return Long.compare(Optional.ofNullable(a.getSiteId()).orElse(0L), Optional.ofNullable(b.getSiteId()).orElse(0L));
        }).collect(Collectors.toList());
        RecommendedSitesResponseVO response = new RecommendedSitesResponseVO();
        response.setFallbackToGeneral(fallbackToGeneral);
        response.setSites(result);
        return response;
    }

    private SubmitResult executeByAdapter(ArticleDraft article, PublishSite site, String content) {
        if ("manual".equalsIgnoreCase(site.getIntegrationMethod())) {
            return SubmitResult.success(200, null, null, null);
        }
        SiteAdapter adapter = resolveAdapter(site.getIntegrationMethod());
        try {
            ValidationResult validation = adapter.validate(article, content, site);
            if (!validation.isPassed()) {
                return SubmitResult.fail(400, null, null, String.join("; ", validation.getErrors()));
            }
            return adapter.submit(article, content, site);
        } catch (UnsupportedOperationException ex) {
            return SubmitResult.fail(501, null, null, ex.getMessage());
        }
    }

    private void finalizeExecution(DistributionTask task,
                                   ArticleDraft article,
                                   Project project,
                                   SubmitResult submitResult,
                                   QuotaContext quota) {
        task.setStatus(submitResult.isSuccess() ? "submitted" : "failed");
        task.setRequestPayload(submitResult.getRequestPayload());
        task.setResponsePayload(submitResult.getResponseBody());
        task.setPublishedUrl(submitResult.getPublishedUrl());
        task.setErrorMessage(submitResult.isSuccess() ? null : trimError(submitResult.getErrorMessage()));
        task.setFinishedAt(LocalDateTime.now());
        distributionTaskMapper.updateById(task);

        if (submitResult.isSuccess()) {
            article.setStatus("distributed");
            article.setPublishedAt(LocalDateTime.now());
            articleDraftMapper.updateById(article);
            increaseMonthlyQuota(quota.monthQuota);
            return;
        }
        article.setStatus("approved");
        articleDraftMapper.updateById(article);
        systemAlertService.createAlert(
                "distribution_failed",
                "error",
                "content_distribution",
                trimError(submitResult.getErrorMessage()),
                Map.of(
                        "projectId", project.getId(),
                        "articleId", article.getId(),
                        "taskId", task.getId(),
                        "siteId", task.getSiteId(),
                        "statusCode", submitResult.getStatusCode() == null ? 500 : submitResult.getStatusCode()
                )
        );
    }

    private DistributionTask createAttempt(ArticleDraft article, PublishSite site, Long operatorId, int retryCount) {
        Integer maxAttempt = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, article.getId())
                        .eq(DistributionTask::getSiteId, site.getId())
                        .select(DistributionTask::getAttemptNo)
        ).stream().map(DistributionTask::getAttemptNo).max(Integer::compareTo).orElse(0);

        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setSiteId(site.getId());
        task.setAttemptNo(maxAttempt + 1);
        task.setStatus("pending");
        task.setIntegrationMethod(site.getIntegrationMethod());
        task.setRetryCount(retryCount);
        task.setOperatorId(operatorId);
        distributionTaskMapper.insert(task);
        return task;
    }

    private QuotaContext validateQuota(Project project, PackagePublishConfig config) {
        QuotaContext quota = resolveQuota(project, config);
        if (quota.monthQuota.getUsedCount() >= quota.monthQuota.getMonthlyLimit()) {
            throw new BizException(400, "Monthly publishing quota exhausted (" + quota.monthQuota.getUsedCount() + "/" + quota.monthQuota.getMonthlyLimit() + ")");
        }
        if (quota.weekUsed >= config.getWeeklyPublishLimit()) {
            throw new BizException(400, "Weekly publishing quota exhausted (" + quota.weekUsed + "/" + config.getWeeklyPublishLimit() + ")");
        }
        return quota;
    }

    private QuotaContext resolveQuota(Project project, PackagePublishConfig config) {
        LocalDate today = LocalDate.now(SH_ZONE);
        String monthKey = today.format(MONTH_FMT);
        ProjectPublishQuota monthQuota = projectPublishQuotaMapper.selectOne(
                new LambdaQueryWrapper<ProjectPublishQuota>()
                        .eq(ProjectPublishQuota::getProjectId, project.getId())
                        .eq(ProjectPublishQuota::getQuotaMonth, monthKey)
                        .last("LIMIT 1")
        );
        if (monthQuota == null) {
            monthQuota = new ProjectPublishQuota();
            monthQuota.setProjectId(project.getId());
            monthQuota.setQuotaMonth(monthKey);
            monthQuota.setUsedCount(0);
            monthQuota.setMonthlyLimit(Optional.ofNullable(config.getMonthlyPublishLimit()).orElse(0));
            projectPublishQuotaMapper.insert(monthQuota);
        }

        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = monday.plusDays(6).atTime(LocalTime.of(23, 59, 59));
        long weekUsed = distributionTaskMapper.selectCount(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getProjectId, project.getId())
                        .in(DistributionTask::getStatus, SUCCESS_TASK_STATUS)
                        .between(DistributionTask::getCreatedAt, weekStart, weekEnd)
        );

        QuotaContext context = new QuotaContext();
        context.monthKey = monthKey;
        context.monthQuota = monthQuota;
        context.weekUsed = (int) weekUsed;
        return context;
    }

    private void increaseMonthlyQuota(ProjectPublishQuota monthQuota) {
        monthQuota.setUsedCount(Optional.ofNullable(monthQuota.getUsedCount()).orElse(0) + 1);
        projectPublishQuotaMapper.updateById(monthQuota);
    }

    private ValidationResult validateByMethod(ArticleDraft article, String content, PublishSite site) {
        if ("manual".equalsIgnoreCase(site.getIntegrationMethod())) {
            return ValidationResult.pass();
        }
        SiteAdapter adapter = resolveAdapter(site.getIntegrationMethod());
        try {
            return adapter.validate(article, content, site);
        } catch (UnsupportedOperationException ex) {
            throw new BizException(501, ex.getMessage());
        }
    }

    private SiteAdapter resolveAdapter(String integrationMethod) {
        if (!StringUtils.hasText(integrationMethod)) {
            throw new BizException(400, "Missing integration method");
        }
        return siteAdapters.stream()
                .filter(adapter -> adapter.supports(integrationMethod))
                .findFirst()
                .orElseThrow(() -> new BizException(501, "Integration method not implemented: " + integrationMethod));
    }

    private Map<Long, PublishSite> mapSites(Set<Long> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return Map.of();
        }
        return publishSiteMapper.selectList(
                new LambdaQueryWrapper<PublishSite>().in(PublishSite::getId, siteIds)
        ).stream().collect(Collectors.toMap(PublishSite::getId, site -> site));
    }

    private DistributionAttemptVO toAttemptVO(DistributionTask task, PublishSite site) {
        DistributionAttemptVO vo = new DistributionAttemptVO();
        vo.setId(task.getId());
        vo.setSiteId(task.getSiteId());
        vo.setSiteName(site == null ? null : site.getSiteName());
        vo.setDomain(site == null ? null : site.getDomain());
        vo.setTier(site == null ? null : site.getTier());
        vo.setAttemptNo(task.getAttemptNo());
        vo.setStatus(task.getStatus());
        vo.setIntegrationMethod(task.getIntegrationMethod());
        vo.setPublishedUrl(task.getPublishedUrl());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRequestPayload(task.getRequestPayload());
        vo.setResponsePayload(task.getResponsePayload());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setFinishedAt(task.getFinishedAt());
        return vo;
    }

    private Map<Long, BigDecimal> querySiteSuccessRate30d(Set<Long> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime from = LocalDateTime.now(SH_ZONE).minusDays(30);
        List<DistributionTask> tasks = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .in(DistributionTask::getSiteId, siteIds)
                        .ge(DistributionTask::getCreatedAt, from)
                        .select(DistributionTask::getSiteId, DistributionTask::getStatus)
        );
        Map<Long, Long> total = tasks.stream().collect(Collectors.groupingBy(DistributionTask::getSiteId, Collectors.counting()));
        Map<Long, Long> success = tasks.stream()
                .filter(t -> SUCCESS_TASK_STATUS.contains(t.getStatus()))
                .collect(Collectors.groupingBy(DistributionTask::getSiteId, Collectors.counting()));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : total.entrySet()) {
            long siteTotal = entry.getValue();
            long siteSuccess = success.getOrDefault(entry.getKey(), 0L);
            BigDecimal rate = siteTotal <= 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(siteSuccess).divide(BigDecimal.valueOf(siteTotal), 4, RoundingMode.HALF_UP);
            result.put(entry.getKey(), rate);
        }
        return result;
    }

    private int tierRank(String tier) {
        if ("S0".equalsIgnoreCase(tier)) return 3;
        if ("S1".equalsIgnoreCase(tier)) return 2;
        if ("S2".equalsIgnoreCase(tier)) return 1;
        return 0;
    }

    private int healthRank(String health) {
        if ("normal".equalsIgnoreCase(health)) return 1;
        if ("slow".equalsIgnoreCase(health)) return 2;
        if ("high_failure".equalsIgnoreCase(health)) return 3;
        if ("degraded".equalsIgnoreCase(health)) return 4;
        return 5;
    }

    private List<String> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(raw).stream().map(String::valueOf).collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private int matchTypeRank(String matchType) {
        if ("exact".equalsIgnoreCase(matchType)) {
            return 2;
        }
        if ("general".equalsIgnoreCase(matchType)) {
            return 1;
        }
        return 0;
    }

    private String requireProjectBrandIndustry(Project project) {
        if (project == null || project.getBrandId() == null) {
            throw new BizException(400, "请先完善品牌行业信息后再进行分发");
        }
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || !StringUtils.hasText(brand.getIndustry())) {
            throw new BizException(400, "请先完善品牌行业信息后再进行分发");
        }
        return brand.getIndustry().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchIndustry(PublishSite site, String brandIndustry) {
        Set<String> tags = parseSiteIndustryTagSet(site);
        return tags.contains(brandIndustry) || tags.contains(GENERAL_INDUSTRY);
    }

    private boolean isGeneralOnly(PublishSite site) {
        Set<String> tags = parseSiteIndustryTagSet(site);
        return tags.size() == 1 && tags.contains(GENERAL_INDUSTRY);
    }

    private String determineMatchType(Set<String> siteIndustries, String brandIndustry) {
        if (siteIndustries.contains(brandIndustry) && !GENERAL_INDUSTRY.equals(brandIndustry)) {
            return "exact";
        }
        return "general";
    }

    private Set<String> parseSiteIndustryTagSet(PublishSite site) {
        List<String> industries = parseJsonArray(site == null ? null : site.getIndustryTags());
        return industries.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String requireLatestContent(Long articleId) {
        ArticleDraftVersion latest = articleDraftVersionMapper.selectOne(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getArticleId, articleId)
                        .orderByDesc(ArticleDraftVersion::getVersionNo)
                        .last("LIMIT 1")
        );
        if (latest == null || !StringUtils.hasText(latest.getContentMarkdown())) {
            throw new BizException(400, "Article content is empty");
        }
        return latest.getContentMarkdown();
    }

    private PackagePublishConfig requirePackagePublishConfig(String packageType) {
        PackagePublishConfig config = packagePublishConfigMapper.selectOne(
                new LambdaQueryWrapper<PackagePublishConfig>()
                        .eq(PackagePublishConfig::getPackageType, packageType)
                        .eq(PackagePublishConfig::getIsActive, true)
                        .last("LIMIT 1")
        );
        if (config == null) {
            throw new BizException(400, "Package publish config not found for " + packageType);
        }
        return config;
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(404, "Article not found");
        }
        return article;
    }

    private DistributionTask requireTask(Long taskId) {
        DistributionTask task = distributionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "Distribution task not found");
        }
        return task;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private PublishSite requireSite(Long siteId) {
        PublishSite site = publishSiteMapper.selectById(siteId);
        if (site == null) {
            throw new BizException(404, "Publish site not found");
        }
        // IC-3 (d): framework rows are not valid publish targets
        if (site.getIsFramework() != null && site.getIsFramework() == 1) {
            throw new BizException(400, "framework site is not a valid publish target");
        }
        return site;
    }

    private String trimError(String msg) {
        if (!StringUtils.hasText(msg)) {
            return "unknown error";
        }
        String text = msg.trim();
        return text.length() <= 900 ? text : text.substring(0, 900);
    }

    private void ensureDistributeRole(SysUser user) {
        if (user == null || !DISTRIBUTE_ALLOWED_ROLES.contains(user.getRole())) {
            throw new BizException(403, "No permission to distribute article");
        }
    }

    private static class QuotaContext {
        private String monthKey;
        private ProjectPublishQuota monthQuota;
        private int weekUsed;
    }
}
