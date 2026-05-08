package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.authoritymedia.AuthorityMediaDistributionAdapter;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.DistributionAttemptVO;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSiteVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.content.service.adapter.BrandGeoSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.OfficialCmsSiteAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SiteAdapter;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SemiAutoFillTask;
import com.huanjing.geo.module.content.service.adapter.SemiAutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.extension.service.FillTokenService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentDistributionService {

    private static final ZoneId SH_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SUCCESS_TASK_STATUS = Set.of("submitted", "confirmed", "published");
    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final Set<String> DISTRIBUTE_ALLOWED_ROLES = Set.of("super_admin", "manager", "delivery_manager", "operator");
    private static final String GENERAL_INDUSTRY = "general";
    private static final String AUTH_MODE_COOKIE = "COOKIE";
    private static final int MAX_FILL_PAYLOAD_BYTES = 16 * 1024;
    private static final ConcurrentHashMap<String, Object> AUTHORITY_MEDIA_LOCKS = new ConcurrentHashMap<>();

    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final PackagePublishConfigMapper packagePublishConfigMapper;
    private final ProjectMapper projectMapper;
    private final PublishSiteMapper publishSiteMapper;
    private final CurrentUserService currentUserService;
    private final SystemAlertService systemAlertService;
    private final List<SiteAdapter> siteAdapters;
    private final List<AutoSelfMediaAdapter> selfMediaAdapters;
    private final List<SemiAutoSelfMediaAdapter> semiAutoSelfMediaAdapters;
    private final BrandService brandService;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final BrandAccessService brandAccessService;
    private final FillTokenService fillTokenService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AuthorityMediaDistributionAdapter authorityMediaDistributionAdapter;

    @Transactional
    public DistributionTask distribute(Long articleId, Long siteId) {
        throw new BizException(400, "Legacy site distribution is deprecated; use explicit channel targets");
    }

    @Transactional
    public DistributionTask distributeTo(Long articleId, TargetContext target) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        ensureDistributeRole(operator);

        ArticleDraft article = requireArticle(articleId);
        if (!ACTIVE_ARTICLE_STATUS.contains(article.getStatus())) {
            throw new BizException(400, "Only approved/unpublished article can distribute");
        }
        Project project = requireProject(article.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        requireDistributionAccess(operator, project.getBrandId());

        if (target instanceof TargetContext.BrandOfficialSiteTarget brandTarget) {
            return distributeToBrandOfficialSite(article, project, operator, brandTarget);
        }
        if (target instanceof TargetContext.BrandGeoSiteTarget brandGeoTarget) {
            return distributeToBrandGeoSite(article, project, operator, brandGeoTarget);
        }
        if (target instanceof TargetContext.SiteTarget) {
            throw new BizException(400, "Legacy site target is deprecated; use explicit channel targets");
        }
        if (target instanceof TargetContext.SelfMediaTarget selfMediaTarget) {
            return distributeToSelfMedia(article, project, operator, selfMediaTarget);
        }
        if (target instanceof TargetContext.AuthorityMediaTarget authorityMediaTarget) {
            return distributeToAuthorityMedia(article, project, operator, authorityMediaTarget);
        }
        throw new IllegalArgumentException("Unsupported TargetContext type: " + target.getClass().getSimpleName());
    }

    @Transactional
    public DistributionTask retry(Long taskId) {
        throw new BizException(400, "Legacy site retry is deprecated; use explicit channel retry flow");
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
        if (!StringUtils.hasText(task.getTargetKind())) {
            throw new BizException(400, "Legacy manual site task cannot be confirmed under company package channel quota");
        }
        String targetKind = task.getTargetKind();
        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), targetKind, task.getId());

        task.setStatus("submitted");
        task.setPublishedUrl(publishedUrl.trim());
        task.setResponsePayload(StringUtils.hasText(responsePayload) ? responsePayload.trim() : null);
        task.setErrorMessage(null);
        task.setFinishedAt(LocalDateTime.now());
        distributionTaskMapper.updateById(task);

        article.setStatus("distributed");
        article.setPublishedAt(LocalDateTime.now());
        articleDraftMapper.updateById(article);
        companyChannelQuotaService.confirmDistribution(task.getId());
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

    @Transactional
    public DistributionTask refreshDistributionTaskReviewStatus(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        ensureDistributeRole(operator);
        DistributionTask task = requireTask(taskId);
        if (!DistributionTargetKind.MP_ACCOUNT.equals(task.getTargetKind()) || task.getSelfMediaAccountId() == null) {
            throw new BizException(400, "distribution task is not self-media");
        }
        ArticleDraft article = requireArticle(task.getArticleId());
        Project project = requireProject(article.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        requireDistributionAccess(operator, project.getBrandId());
        SelfMediaAccount account = selfMediaAccountMapper.selectById(task.getSelfMediaAccountId());
        if (account == null) {
            throw new BizException(404, "self media account not found");
        }
        if (project.getBrandId() == null || !project.getBrandId().equals(account.getBrandId())) {
            throw new BizException(403, "自媒体账号与文章品牌不匹配");
        }
        AutoSelfMediaAdapter adapter = resolveSelfMediaAdapter(account.getPlatform());
        ReviewStatusResult result = adapter.refreshReviewStatus(task, account);
        if (result != null
                && result.status() != null
                && result.status() != ReviewStatusResult.ReviewStatus.UNKNOWN) {
            LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                    .eq(DistributionTask::getId, taskId)
                    .set(DistributionTask::getReviewStatus, SubmitResult.toStorageValue(result.status()))
                    .set(DistributionTask::getReviewFeedback, result.reviewFeedback())
                    .set(DistributionTask::getExternalStatus, result.externalStatus());
            distributionTaskMapper.update(null, wrapper);
        }
        return distributionTaskMapper.selectById(taskId);
    }

    public PublishQuotaVO quota(Long projectId) {
        throw new BizException(410, "Project publish quota endpoint is deprecated; use customer channel quota APIs");
    }

    public RecommendedSitesResponseVO recommendedSites(Long projectId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(projectId);
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        PackagePublishConfig config = requirePackagePublishConfig(activePackageType(project));
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
                                   SubmitResult submitResult) {
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
            companyChannelQuotaService.confirmDistribution(task.getId());
            return;
        }
        companyChannelQuotaService.refundDistribution(task.getId());
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

    private DistributionTask distributeToBrandOfficialSite(ArticleDraft article,
                                                           Project project,
                                                           SysUser operator,
                                                           TargetContext.BrandOfficialSiteTarget brandTarget) {
        BrandOfficialSite site = brandTarget.site();
        if (!"active".equalsIgnoreCase(site.getStatus())) {
            throw new BizException(400, "Brand official site is not active");
        }
        currentUserService.ensureBrandAccess(operator, site.getBrandId(), "official_site");
        requireDistributionAccess(operator, site.getBrandId());

        String content = requireLatestContent(article.getId());
        OfficialCmsSiteAdapter adapter = resolveOfficialCmsAdapter();
        DistributionTask task = createAttemptForBrandOfficialSite(article, site, operator.getId());
        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), DistributionTargetKind.BRAND_OFFICIAL_SITE, task.getId());

        try {
            transitionArticleStatus(article, article.getStatus(), "distributing", false);
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            throw ex;
        }

        SubmitResult submitResult;
        try {
            submitResult = adapter.submitToTarget(article, content, brandTarget);
        } catch (Exception unexpected) {
            submitResult = SubmitResult.failure(
                    500,
                    null,
                    null,
                    "adapter unexpected exception: " + unexpected.getClass().getSimpleName(),
                    FailureKind.UNKNOWN,
                    false
            );
        }

        finalizeAttemptForBrandOfficialSite(task.getId(), submitResult);
        finalizeArticleStatus(article, submitResult);
        if (submitResult.isSuccess()) {
            companyChannelQuotaService.confirmDistribution(task.getId());
        } else {
            companyChannelQuotaService.refundDistribution(task.getId());
        }
        return distributionTaskMapper.selectById(task.getId());
    }

    private DistributionTask distributeToBrandGeoSite(ArticleDraft article,
                                                      Project project,
                                                      SysUser operator,
                                                      TargetContext.BrandGeoSiteTarget brandGeoTarget) {
        Brand brand = brandAccessService.requireBrandAccess(brandGeoTarget.brandId(), operator.getId(), BrandAccessAction.OPERATE);
        String siteCode = validateBrandGeoSite(brand);

        String content = requireLatestContent(article.getId());
        BrandGeoSiteAdapter adapter = resolveBrandGeoSiteAdapter();
        DistributionTask task = createAttemptForBrandGeoSite(article, brand, operator.getId());
        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), DistributionTargetKind.BRAND_GEO_SITE, task.getId());
        try {
            transitionArticleStatus(article, article.getStatus(), "distributing", false);
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            throw ex;
        }

        SubmitResult submitResult;
        try {
            submitResult = adapter.submitToTarget(article, content, new TargetContext.BrandGeoSiteTarget(brand.getId(), siteCode));
        } catch (Exception ex) {
            submitResult = SubmitResult.failure(
                    500,
                    null,
                    null,
                    "adapter unexpected exception: " + ex.getClass().getSimpleName(),
                    FailureKind.UNKNOWN,
                    false
            );
        }

        finalizeAttemptForBrandGeoSite(task.getId(), submitResult);
        finalizeArticleStatus(article, submitResult);
        if (submitResult.isSuccess()) {
            companyChannelQuotaService.confirmDistribution(task.getId());
        } else {
            companyChannelQuotaService.refundDistribution(task.getId());
        }
        return distributionTaskMapper.selectById(task.getId());
    }

    private DistributionTask distributeToSelfMedia(ArticleDraft article,
                                                   Project project,
                                                   SysUser operator,
                                                   TargetContext.SelfMediaTarget mpTarget) {
        SelfMediaAccount account = mpTarget.account();
        if (account == null || account.getId() == null) {
            throw new BizException(400, "self media account missing");
        }
        if (!StringUtils.hasText(mpTarget.requestId())) {
            throw new BizException(400, "requestId is required");
        }
        if (!"active".equalsIgnoreCase(account.getStatus())) {
            throw new BizException(400, "自媒体账号不可用，请重新授权");
        }
        if (project.getBrandId() == null || !project.getBrandId().equals(account.getBrandId())) {
            throw new BizException(403, "自媒体账号与文章品牌不匹配");
        }
        requireDistributionAccess(operator, account.getBrandId());
        DistributionTask existed = distributionTaskMapper.selectOne(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getRequestId, mpTarget.requestId().trim())
                        .last("LIMIT 1")
        );
        if (existed != null) {
            return existed;
        }
        if (AUTH_MODE_COOKIE.equalsIgnoreCase(account.getAuthMode())) {
            return createSemiAutoSelfMediaTask(article, project, operator, account, mpTarget);
        }

        String content = requireLatestContent(article.getId());
        AutoSelfMediaAdapter adapter = resolveSelfMediaAdapter(account.getPlatform());
        DistributionTask task = createAttemptForSelfMedia(article, account, operator.getId(), mpTarget.requestId().trim());
        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), DistributionTargetKind.MP_ACCOUNT, task.getId());
        try {
            transitionArticleStatus(article, article.getStatus(), "distributing", false);
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            throw ex;
        }

        SubmitResult submitResult;
        try {
            submitResult = adapter.submitToTarget(article, content, mpTarget);
        } catch (Exception ex) {
            submitResult = SubmitResult.failure(500, null, null, trimError(ex.getMessage()), FailureKind.UNKNOWN, false);
        }
        finalizeAttemptForSelfMedia(task.getId(), submitResult);
        finalizeArticleStatusForDraft(article, submitResult);
        if (submitResult.isSuccess()) {
            companyChannelQuotaService.confirmDistribution(task.getId());
        } else {
            companyChannelQuotaService.refundDistribution(task.getId());
        }
        return distributionTaskMapper.selectById(task.getId());
    }

    private DistributionTask distributeToAuthorityMedia(ArticleDraft article,
                                                        Project project,
                                                        SysUser operator,
                                                        TargetContext.AuthorityMediaTarget target) {
        if (target.resourceId() == null) {
            throw new BizException(400, "authority media resourceId is required");
        }
        String lockKey = article.getId() + ":" + target.resourceId();
        Object lock = AUTHORITY_MEDIA_LOCKS.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                return doDistributeToAuthorityMedia(article, project, operator, target);
            } finally {
                AUTHORITY_MEDIA_LOCKS.remove(lockKey, lock);
            }
        }
    }

    private DistributionTask doDistributeToAuthorityMedia(ArticleDraft article,
                                                          Project project,
                                                          SysUser operator,
                                                          TargetContext.AuthorityMediaTarget target) {
        authorityMediaDistributionAdapter.validateBeforeCreatingTask(article, target);
        String content = requireLatestContent(article.getId());
        DistributionTask task = createAttemptForAuthorityMedia(article, target.resourceId(), operator.getId());
        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), DistributionTargetKind.AUTHORITY_MEDIA, task.getId());
        try {
            transitionArticleStatus(article, article.getStatus(), "distributing", false);
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            throw ex;
        }

        SubmitResult submitResult;
        try {
            submitResult = authorityMediaDistributionAdapter.submitNewsMedia(article, project, task, operator.getId(), target, content);
        } catch (Exception ex) {
            submitResult = SubmitResult.failure(500, null, null, trimError(ex.getMessage()), FailureKind.UNKNOWN, false);
        }
        finalizeAttemptForAuthorityMedia(task.getId(), submitResult);
        finalizeArticleStatusForDraft(article, submitResult);
        if (submitResult.isSuccess()) {
            companyChannelQuotaService.confirmDistribution(task.getId());
        } else {
            companyChannelQuotaService.refundDistribution(task.getId());
        }
        return distributionTaskMapper.selectById(task.getId());
    }

    private DistributionTask createSemiAutoSelfMediaTask(ArticleDraft article,
                                                        Project project,
                                                        SysUser operator,
                                                        SelfMediaAccount account,
                                                        TargetContext.SelfMediaTarget mpTarget) {
        brandAccessService.requireBrandAccess(account.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        String content = requireLatestContent(article.getId());
        SemiAutoSelfMediaAdapter adapter = resolveSemiAutoSelfMediaAdapter(account.getPlatform());
        SemiAutoFillTask fillTask = adapter.prepareFillTask(article, content, adapter.fillProfile());
        String fillPayload = toFillPayload(fillTask);
        DistributionTask task = createAttemptForSelfMedia(article, account, operator.getId(), mpTarget.requestId().trim());
        updateSemiAutoTaskPrepared(task.getId(), fillPayload);

        companyChannelQuotaService.reserveDistribution(project.getCompanyId(), project.getId(), DistributionTargetKind.MP_ACCOUNT, task.getId());
        FillTokenIssueResponse token;
        try {
            token = fillTokenService.issueInternalWithoutVersionCheck(
                    account.getId(),
                    account.getBrandId(),
                    operator.getId(),
                    task.getId()
            );
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            auditSemiAutoTaskCreationFailed(article, account, operator, task, ex);
            throw ex;
        }
        LocalDateTime issuedAt = LocalDateTime.now(SH_ZONE);
        updateSemiAutoTaskTokenIssued(task.getId(), issuedAt);
        task.setDispatchMode("SEMI_AUTO");
        task.setStatus("token_issued");
        task.setFillPayload(fillPayload);
        task.setFillTokenIssuedAt(issuedAt);
        task.setLockedUntil(null);

        try {
            transitionArticleStatus(article, article.getStatus(), "distributing", false);
        } catch (BizException ex) {
            companyChannelQuotaService.refundDistribution(task.getId());
            auditSemiAutoTaskCreationFailed(article, account, operator, task, ex);
            throw ex;
        }

        DistributionTask returned = distributionTaskMapper.selectById(task.getId());
        if (returned == null) {
            returned = task;
        }
        returned.setFillToken(token.fillToken());
        returned.setFillTokenExpiresAt(token.expiresAt());
        returned.setFillTokenNonce(token.nonce());
        auditSemiAutoTaskCreated(article, account, operator, task, token);
        return returned;
    }

    private void updateSemiAutoTaskPrepared(Long taskId, String fillPayload) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .set(DistributionTask::getDispatchMode, "SEMI_AUTO")
                .set(DistributionTask::getStatus, "pending")
                .set(DistributionTask::getFillPayload, fillPayload)
                .set(DistributionTask::getLockedUntil, null);
        distributionTaskMapper.update(null, wrapper);
    }

    private void updateSemiAutoTaskTokenIssued(Long taskId, LocalDateTime issuedAt) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .eq(DistributionTask::getDispatchMode, "SEMI_AUTO")
                .set(DistributionTask::getStatus, "token_issued")
                .set(DistributionTask::getFillTokenIssuedAt, issuedAt);
        distributionTaskMapper.update(null, wrapper);
    }

    private String validateBrandGeoSite(Brand brand) {
        if (!StringUtils.hasText(brand.getGeoSiteCode())) {
            throw new BizException(400, "Brand has no GEO site configured");
        }
        if (!"active".equalsIgnoreCase(brand.getGeoSiteStatus())) {
            throw new BizException(400, "Brand GEO site is not active");
        }
        return brand.getGeoSiteCode().trim();
    }

    private DistributionTask createAttemptForBrandOfficialSite(ArticleDraft article, BrandOfficialSite site, Long operatorId) {
        Integer maxAttempt = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, article.getId())
                        .eq(DistributionTask::getBrandOfficialSiteId, site.getId())
        ).stream().map(DistributionTask::getAttemptNo).max(Integer::compareTo).orElse(0);

        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setSiteId(null);
        task.setTargetKind(DistributionTargetKind.BRAND_OFFICIAL_SITE);
        task.setBrandOfficialSiteId(site.getId());
        task.setAttemptNo(maxAttempt + 1);
        task.setStatus("submitting");
        task.setIntegrationMethod("official_cms");
        task.setRetryCount(0);
        task.setOperatorId(operatorId);
        task.setLockedUntil(LocalDateTime.now(SH_ZONE).plusMinutes(5));
        distributionTaskMapper.insert(task);
        return task;
    }

    private DistributionTask createAttemptForBrandGeoSite(ArticleDraft article, Brand brand, Long operatorId) {
        Integer maxAttempt = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, article.getId())
                        .eq(DistributionTask::getTargetBrandId, brand.getId())
        ).stream().map(DistributionTask::getAttemptNo).max(Integer::compareTo).orElse(0);

        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setSiteId(null);
        task.setTargetKind(DistributionTargetKind.BRAND_GEO_SITE);
        task.setTargetBrandId(brand.getId());
        task.setAttemptNo(maxAttempt + 1);
        task.setStatus("submitting");
        task.setIntegrationMethod(BrandGeoSiteAdapter.PLATFORM);
        task.setRetryCount(0);
        task.setOperatorId(operatorId);
        task.setLockedUntil(LocalDateTime.now(SH_ZONE).plusMinutes(5));
        distributionTaskMapper.insert(task);
        return task;
    }

    private DistributionTask createAttemptForSelfMedia(ArticleDraft article, SelfMediaAccount account, Long operatorId, String requestId) {
        Integer maxAttempt = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, article.getId())
                        .eq(DistributionTask::getSelfMediaAccountId, account.getId())
        ).stream().map(DistributionTask::getAttemptNo).max(Integer::compareTo).orElse(0);

        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setSiteId(null);
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(account.getId());
        task.setAttemptNo(maxAttempt + 1);
        task.setStatus("submitting");
        task.setIntegrationMethod(account.getPlatform());
        task.setRetryCount(0);
        task.setOperatorId(operatorId);
        task.setRequestId(requestId);
        task.setLockedUntil(LocalDateTime.now(SH_ZONE).plusMinutes(5));
        distributionTaskMapper.insert(task);
        return task;
    }

    private DistributionTask createAttemptForAuthorityMedia(ArticleDraft article, Long authorityMediaResourceId, Long operatorId) {
        Integer maxAttempt = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, article.getId())
                        .eq(DistributionTask::getAuthorityMediaId, authorityMediaResourceId)
        ).stream().map(DistributionTask::getAttemptNo).max(Integer::compareTo).orElse(0);

        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setSiteId(null);
        task.setTargetKind(DistributionTargetKind.AUTHORITY_MEDIA);
        task.setAuthorityMediaId(authorityMediaResourceId);
        task.setAttemptNo(maxAttempt + 1);
        task.setStatus("submitting");
        task.setIntegrationMethod("meititejia_news_media");
        task.setRetryCount(0);
        task.setOperatorId(operatorId);
        task.setLockedUntil(LocalDateTime.now(SH_ZONE).plusMinutes(5));
        distributionTaskMapper.insert(task);
        return task;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizeAttemptForBrandOfficialSite(Long taskId, SubmitResult result) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .eq(DistributionTask::getStatus, "submitting")
                .set(DistributionTask::getLockedUntil, null)
                .set(DistributionTask::getFinishedAt, LocalDateTime.now(SH_ZONE));

        if (result.isSuccess()) {
            wrapper.set(DistributionTask::getStatus, "submitted")
                    .set(DistributionTask::getPublishedUrl, result.getPublishedUrl())
                    .set(DistributionTask::getPlatformArticleId, result.getPlatformArticleId())
                    .set(DistributionTask::getResponsePayload, result.getResponseBody());
        } else {
            wrapper.set(DistributionTask::getStatus, "failed")
                    .set(DistributionTask::getFailureKind, result.getFailureKind())
                    .set(DistributionTask::getErrorMessage, result.getErrorMessage());
        }

        int affected = distributionTaskMapper.update(null, wrapper);
        if (affected == 0) {
            log.warn("finalizeAttemptForBrandOfficialSite: task {} state changed concurrently, skipped finalize", taskId);
        }
    }

    private void finalizeAttemptForBrandGeoSite(Long taskId, SubmitResult result) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .eq(DistributionTask::getStatus, "submitting")
                .set(DistributionTask::getLockedUntil, null)
                .set(DistributionTask::getFinishedAt, LocalDateTime.now(SH_ZONE))
                .set(DistributionTask::getRequestPayload, result.getRequestPayload())
                .set(DistributionTask::getResponsePayload, result.getResponseBody());

        if (result.isSuccess()) {
            wrapper.set(DistributionTask::getStatus, "submitted")
                    .set(DistributionTask::getPublishedUrl, result.getPublishedUrl())
                    .set(DistributionTask::getPlatformArticleId, result.getPlatformArticleId())
                    .set(DistributionTask::getFailureKind, null)
                    .set(DistributionTask::getErrorMessage, null)
                    .set(DistributionTask::getNextRetryAt, null);
        } else {
            LocalDateTime nextRetryAt = result.isRetryable()
                    ? LocalDateTime.now(SH_ZONE).plusMinutes(5)
                    : null;
            wrapper.set(DistributionTask::getStatus, "failed")
                    .set(DistributionTask::getFailureKind, result.getFailureKind())
                    .set(DistributionTask::getErrorMessage, result.getErrorMessage())
                    .set(DistributionTask::getNextRetryAt, nextRetryAt);
        }

        int affected = distributionTaskMapper.update(null, wrapper);
        if (affected == 0) {
            log.warn("finalizeAttemptForBrandGeoSite: task {} state changed concurrently, skipped finalize", taskId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizeAttemptForSelfMedia(Long taskId, SubmitResult result) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .eq(DistributionTask::getStatus, "submitting")
                .set(DistributionTask::getLockedUntil, null)
                .set(DistributionTask::getFinishedAt, LocalDateTime.now(SH_ZONE))
                .set(DistributionTask::getRequestPayload, result.getRequestPayload())
                .set(DistributionTask::getResponsePayload, result.getResponseBody())
                .set(DistributionTask::getExternalStatus, result.getExternalStatus())
                .set(DistributionTask::getReviewStatus, result.getReviewStatus())
                .set(DistributionTask::getReviewFeedback, result.getReviewFeedback());

        if (result.isSuccess()) {
            wrapper.set(DistributionTask::getStatus, "submitted")
                    .set(DistributionTask::getPlatformArticleId, result.getPlatformArticleId())
                    .set(DistributionTask::getFailureKind, null)
                    .set(DistributionTask::getErrorMessage, null)
                    .set(DistributionTask::getNextRetryAt, null);
        } else {
            LocalDateTime nextRetryAt = result.isRetryable()
                    ? LocalDateTime.now(SH_ZONE).plusMinutes(5)
                    : null;
            wrapper.set(DistributionTask::getStatus, "failed")
                    .set(DistributionTask::getFailureKind, result.getFailureKind())
                    .set(DistributionTask::getErrorMessage, trimError(result.getErrorMessage()))
                    .set(DistributionTask::getNextRetryAt, nextRetryAt);
        }

        int affected = distributionTaskMapper.update(null, wrapper);
        if (affected == 0) {
            log.warn("finalizeAttemptForSelfMedia: task {} state changed concurrently, skipped finalize", taskId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizeAttemptForAuthorityMedia(Long taskId, SubmitResult result) {
        LambdaUpdateWrapper<DistributionTask> wrapper = new LambdaUpdateWrapper<DistributionTask>()
                .eq(DistributionTask::getId, taskId)
                .eq(DistributionTask::getStatus, "submitting")
                .set(DistributionTask::getLockedUntil, null)
                .set(DistributionTask::getFinishedAt, LocalDateTime.now(SH_ZONE))
                .set(DistributionTask::getRequestPayload, result.getRequestPayload())
                .set(DistributionTask::getResponsePayload, result.getResponseBody())
                .set(DistributionTask::getExternalStatus, result.getExternalStatus());

        if (result.isSuccess()) {
            wrapper.set(DistributionTask::getStatus, "submitted")
                    .set(DistributionTask::getPlatformArticleId, result.getPlatformArticleId())
                    .set(DistributionTask::getFailureKind, null)
                    .set(DistributionTask::getErrorMessage, null)
                    .set(DistributionTask::getNextRetryAt, null);
        } else {
            LocalDateTime nextRetryAt = result.isRetryable()
                    ? LocalDateTime.now(SH_ZONE).plusMinutes(5)
                    : null;
            wrapper.set(DistributionTask::getStatus, "failed")
                    .set(DistributionTask::getFailureKind, result.getFailureKind())
                    .set(DistributionTask::getErrorMessage, trimError(result.getErrorMessage()))
                    .set(DistributionTask::getNextRetryAt, nextRetryAt);
        }

        int affected = distributionTaskMapper.update(null, wrapper);
        if (affected == 0) {
            log.warn("finalizeAttemptForAuthorityMedia: task {} state changed concurrently, skipped finalize", taskId);
        }
    }

    private void finalizeArticleStatus(ArticleDraft article, SubmitResult result) {
        transitionArticleStatus(article, "distributing", result.isSuccess() ? "published" : "approved", result.isSuccess());
    }

    private void finalizeArticleStatusForDraft(ArticleDraft article, SubmitResult result) {
        transitionArticleStatus(article, "distributing", result.isSuccess() ? "distributed" : "approved", result.isSuccess());
    }

    private void transitionArticleStatus(ArticleDraft article, String expectedStatus, String newStatus, boolean setPublishedAt) {
        LambdaUpdateWrapper<ArticleDraft> wrapper = new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, article.getId())
                .eq(ArticleDraft::getStatus, expectedStatus)
                .set(ArticleDraft::getStatus, newStatus);
        LocalDateTime publishedAt = null;
        if (setPublishedAt) {
            publishedAt = LocalDateTime.now(SH_ZONE);
            wrapper.set(ArticleDraft::getPublishedAt, publishedAt);
        }
        int updated = articleDraftMapper.update(null, wrapper);
        if (updated != 1) {
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        article.setStatus(newStatus);
        if (publishedAt != null) {
            article.setPublishedAt(publishedAt);
        }
    }

    private void requireDistributionAccess(SysUser operator, Long brandId) {
        brandAccessService.requireBrandAccess(brandId, operator == null ? null : operator.getId(), BrandAccessAction.OPERATE);
    }

    private OfficialCmsSiteAdapter resolveOfficialCmsAdapter() {
        return siteAdapters.stream()
                .filter(adapter -> adapter.supportsPlatform("official_cms"))
                .filter(OfficialCmsSiteAdapter.class::isInstance)
                .map(OfficialCmsSiteAdapter.class::cast)
                .findFirst()
                .orElseThrow(() -> new BizException(500, "OfficialCmsSiteAdapter not registered"));
    }

    private BrandGeoSiteAdapter resolveBrandGeoSiteAdapter() {
        return siteAdapters.stream()
                .filter(adapter -> adapter.supportsPlatform(BrandGeoSiteAdapter.PLATFORM))
                .filter(BrandGeoSiteAdapter.class::isInstance)
                .map(BrandGeoSiteAdapter.class::cast)
                .findFirst()
                .orElseThrow(() -> new BizException(500, "BrandGeoSiteAdapter not registered"));
    }

    private AutoSelfMediaAdapter resolveSelfMediaAdapter(String platform) {
        if (!StringUtils.hasText(platform)) {
            throw new BizException(400, "Missing self-media platform");
        }
        return selfMediaAdapters.stream()
                .filter(adapter -> adapter.supportsPlatform(platform))
                .findFirst()
                .orElseThrow(() -> new BizException(501, "Self-media platform not implemented: " + platform));
    }

    private SemiAutoSelfMediaAdapter resolveSemiAutoSelfMediaAdapter(String platform) {
        if (!StringUtils.hasText(platform)) {
            throw new BizException(400, "Missing semi-auto self-media platform");
        }
        return semiAutoSelfMediaAdapters.stream()
                .filter(adapter -> adapter.supportsPlatform(platform))
                .findFirst()
                .orElseThrow(() -> new BizException(501, "Semi-auto self-media platform not implemented: " + platform));
    }

    private String toFillPayload(SemiAutoFillTask fillTask) {
        try {
            String payload = objectMapper.writeValueAsString(fillTask);
            if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FILL_PAYLOAD_BYTES) {
                throw new BizException(400, "semi-auto fill payload too large");
            }
            return payload;
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "semi-auto fill payload serialization failed", ex);
        }
    }

    private void auditSemiAutoTaskCreated(ArticleDraft article,
                                          SelfMediaAccount account,
                                          SysUser operator,
                                          DistributionTask task,
                                          FillTokenIssueResponse token) {
        AuditEvent event = new AuditEvent();
        event.setEventType("SEMI_AUTO_TASK_CREATED");
        event.setActorType(ActorType.OPERATOR);
        event.setActorId(operator.getId());
        event.setBrandId(account.getBrandId());
        event.setAccountId(account.getId());
        event.setTaskId(task.getId());
        event.setTargetType("DISTRIBUTION_TASK");
        event.setTargetId(String.valueOf(task.getId()));
        event.setResult(AuditResult.SUCCESS);
        event.setMode(AuditMode.ASYNC);
        event.setSensitive(false);
        event.setDetail(Map.of(
                "articleId", article.getId(),
                "platform", account.getPlatform(),
                "fillTokenExpiresAt", token.expiresAt(),
                "fillTokenNonce", token.nonce()
        ));
        auditService.record(event);
    }

    private void auditSemiAutoTaskCreationFailed(ArticleDraft article,
                                                 SelfMediaAccount account,
                                                 SysUser operator,
                                                 DistributionTask task,
                                                 BizException ex) {
        AuditEvent event = new AuditEvent();
        event.setEventType("SEMI_AUTO_TASK_CREATION_FAILED");
        event.setActorType(ActorType.OPERATOR);
        event.setActorId(operator.getId());
        event.setBrandId(account.getBrandId());
        event.setAccountId(account.getId());
        event.setTaskId(task.getId());
        event.setTargetType("DISTRIBUTION_TASK");
        event.setTargetId(String.valueOf(task.getId()));
        event.setResult(auditResultForSemiAutoFailure(ex));
        event.setMode(AuditMode.ASYNC);
        event.setSensitive(false);
        event.setErrorCode(String.valueOf(ex.getCode()));
        event.setErrorMessage(ex.getMessage());
        event.setDetail(Map.of(
                "articleId", article.getId(),
                "platform", account.getPlatform()
        ));
        auditService.record(event);
    }

    private AuditResult auditResultForSemiAutoFailure(BizException ex) {
        int code = ex.getCode();
        if (code == BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND || code == ExtensionErrorCodes.EXTENSION_NOT_FOUND) {
            return AuditResult.NOT_FOUND;
        }
        if (code == BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED
                || code == BrandAccessErrorCodes.BRAND_ACCESS_DENIED
                || code == ExtensionErrorCodes.EXTENSION_UNAUTHORIZED
                || code == ExtensionErrorCodes.EXTENSION_DENIED
                || code == ExtensionErrorCodes.FILL_TOKEN_INVALID
                || code == ExtensionErrorCodes.FILL_TOKEN_USED_OR_EXPIRED) {
            return AuditResult.DENIED;
        }
        return AuditResult.FAILURE;
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
        vo.setPlatformArticleId(task.getPlatformArticleId());
        vo.setExternalStatus(task.getExternalStatus());
        vo.setReviewStatus(task.getReviewStatus());
        vo.setReviewFeedback(task.getReviewFeedback());
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
        Brand brand = brandService.requireExistingBrand(project.getBrandId());
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
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String activePackageType(Project project) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        return binding.getPackageType();
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

}
