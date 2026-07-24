package com.huanjing.geo.module.project.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.entity.ArticlePublishLog;
import com.huanjing.geo.module.content.entity.ArticleReviewLog;
import com.huanjing.geo.module.content.entity.ContentQuestionRotation;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.ArticlePublishLogMapper;
import com.huanjing.geo.module.content.mapper.ArticleReviewLogMapper;
import com.huanjing.geo.module.content.mapper.ContentQuestionRotationMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.SpecialIndustryReadinessService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardShareMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardSnapshotMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.entity.ProjectPollRotation;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.module.project.dto.ProjectCreateRequest;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectFlowUpdateRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupColumnsVO;
import com.huanjing.geo.module.project.dto.ProjectKeywordGroupQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.dto.KeywordGroupListItemVO;
import com.huanjing.geo.module.project.dto.KeywordWordItemVO;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupWord;
import com.huanjing.geo.module.project.entity.ProjectCustomerRequirement;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupWordMapper;
import com.huanjing.geo.module.project.mapper.ProjectCustomerRequirementMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectStartRequestMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.report.entity.PostsaleReportSnapshot;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.entity.ReportAccessLog;
import com.huanjing.geo.module.report.mapper.PostsaleReportSnapshotMapper;
import com.huanjing.geo.module.report.mapper.ReportAccessLogMapper;
import com.huanjing.geo.module.report.mapper.ReportMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner");
    private static final Set<String> HQ_VISIBLE_PARTNER_PROJECT_STATUSES = Set.of(
            ProjectFlowPolicy.SUBMITTED,
            ProjectFlowPolicy.REJECTED,
            ProjectFlowPolicy.APPROVED_PENDING_SETUP,
            ProjectFlowPolicy.SETUP_READY,
            ProjectFlowPolicy.ACTIVE,
            ProjectFlowPolicy.PAUSED,
            ProjectFlowPolicy.COMPLETED,
            ProjectFlowPolicy.ARCHIVED,
            ProjectFlowPolicy.CANCELLED,
            ProjectFlowPolicy.EXPIRED
    );
    private static final int CUSTOMER_REQUIREMENT_MAX_COUNT = 20;
    private static final int CUSTOMER_REQUIREMENT_MIN_LENGTH = 10;
    private static final int CUSTOMER_REQUIREMENT_MAX_LENGTH = 100;

    private final ProjectMapper projectMapper;
    private final ProjectStartRequestMapper projectStartRequestMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final KeywordGroupService keywordGroupService;
    private final ArticleBatchMapper articleBatchMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ArticlePublishLogMapper articlePublishLogMapper;
    private final ArticleReviewLogMapper articleReviewLogMapper;
    private final ContentQuestionRotationMapper contentQuestionRotationMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final ProjectDashboardShareMapper projectDashboardShareMapper;
    private final ProjectDashboardSnapshotMapper projectDashboardSnapshotMapper;
    private final DispatchAlertMapper dispatchAlertMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final PollBatchMapper pollBatchMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final PollResultMapper pollResultMapper;
    private final ProjectPollRotationMapper projectPollRotationMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final ProjectCustomerRequirementMapper projectCustomerRequirementMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final KeywordGroupWordMapper keywordGroupWordMapper;
    private final PostsaleReportSnapshotMapper postsaleReportSnapshotMapper;
    private final ReportAccessLogMapper reportAccessLogMapper;
    private final ReportMapper reportMapper;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ProjectStateGuard projectStateGuard;
    private final ActivityLogService activityLogService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;
    private final KeywordTypeConfigService keywordTypeConfigService;
    private final SpecialIndustryReadinessService specialIndustryReadinessService;

    public Page<Project> page(long current, long size, String keyword, String status, String stage, Long partnerId, Long brandId) {
        return page(current, size, keyword, status, stage, null, null, partnerId, brandId, false);
    }

    public Page<Project> page(long current, long size, String keyword, String status, String stage, Long partnerId, Long brandId, boolean excludeThirdPartySource) {
        return page(current, size, keyword, status, stage, null, null, partnerId, brandId, excludeThirdPartySource);
    }

    public Page<Project> page(long current, long size, String keyword, String status, String stage, String ownerType, Long partnerId, Long brandId, boolean excludeThirdPartySource) {
        return page(current, size, keyword, status, stage, ownerType, null, partnerId, brandId, excludeThirdPartySource);
    }

    public Page<Project> page(long current, long size, String keyword, String status, String stage, String ownerType, Long companyId, Long partnerId, Long brandId, boolean excludeThirdPartySource) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        expireOverdueProjects();
        List<Long> thirdPartySourceBrandIds = excludeThirdPartySource ? thirdPartySourceBrandIds() : List.of();
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Project::getProjectName, keyword)
                    .or()
                    .like(Project::getCompanyName, keyword)
                    .or()
                    .like(Project::getBrandName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Project::getStatus, status);
        }
        if (StringUtils.hasText(stage)) {
            wrapper.eq(Project::getStage, stage);
        }
        if (StringUtils.hasText(ownerType)) {
            wrapper.eq(Project::getOwnerType, ownerType);
        }
        if (companyId != null) {
            wrapper.eq(Project::getCompanyId, companyId);
        }
        if (brandId != null) {
            wrapper.eq(Project::getBrandId, brandId);
        }
        if (!thirdPartySourceBrandIds.isEmpty()) {
            wrapper.notIn(Project::getBrandId, thirdPartySourceBrandIds);
        }

        Long scopePartnerId = currentUserService.resolvePartnerQueryScope(user, partnerId);
        if (scopePartnerId != null) {
            wrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        applyInternalPartnerVisibility(wrapper, user, ownerType, scopePartnerId);
        if (internalScopeService.isSalesUser(user)) {
            List<Long> salesCompanyIds = companyMapper.selectList(
                    new LambdaQueryWrapper<Company>()
                            .isNull(Company::getDeletedAt)
                            .select(Company::getId)
                            .eq(Company::getSalesOwnerId, user.getId())
            ).stream().map(Company::getId).collect(Collectors.toList());
            if (salesCompanyIds.isEmpty()) {
                return new Page<>(current, size);
            }
            wrapper.in(Project::getCompanyId, salesCompanyIds);
        } else {
            internalScopeService.applyProjectScope(wrapper, user);
        }

        Page<Project> page = projectMapper.selectPage(new Page<>(current, size), wrapper);
        page.getRecords().forEach(this::refreshProjectExpiration);
        attachThirdPartySourceFlag(page.getRecords(), thirdPartySourceBrandIds);
        attachPlatformSelections(page.getRecords());
        attachCustomerRequirements(page.getRecords());
        attachKeywordGroupSelections(page.getRecords());
        channelAllocationService.attachAllocations(page.getRecords());
        return page;
    }

    private void applyInternalPartnerVisibility(LambdaQueryWrapper<Project> wrapper, SysUser user, String ownerType, Long scopePartnerId) {
        if (currentUserService.isPartnerUser(user) || internalScopeService.isSuperAdmin(user)) {
            return;
        }
        boolean partnerQuery = "partner".equals(ownerType) || scopePartnerId != null;
        if (partnerQuery) {
            wrapper.in(Project::getStatus, HQ_VISIBLE_PARTNER_PROJECT_STATUSES);
        } else if (!StringUtils.hasText(ownerType)) {
            wrapper.and(w -> w.ne(Project::getOwnerType, "partner").or().in(Project::getStatus, HQ_VISIBLE_PARTNER_PROJECT_STATUSES));
        }
    }

    private void ensureInternalPartnerProjectVisible(SysUser user, Project project) {
        if (currentUserService.isPartnerUser(user) || project == null || !"partner".equals(project.getOwnerType())) {
            return;
        }
        if (internalScopeService.isSuperAdmin(user)) {
            return;
        }
        if (!HQ_VISIBLE_PARTNER_PROJECT_STATUSES.contains(project.getStatus())) {
            throw new BizException(403, "合伙人项目尚未提交总部，当前账号无权查看");
        }
    }

    public Project detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(id);
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        ensureInternalPartnerProjectVisible(user, project);
        internalScopeService.ensureProjectAccess(user, project, "project");
        ensureSalesProjectAccess(user, project);
        attachThirdPartySourceFlag(Collections.singletonList(project), List.of());
        attachPlatformSelections(Collections.singletonList(project));
        attachCustomerRequirements(Collections.singletonList(project));
        attachKeywordGroupSelections(Collections.singletonList(project));
        channelAllocationService.attachAllocations(Collections.singletonList(project));
        return project;
    }

    private void attachThirdPartySourceFlag(List<Project> projects, List<Long> knownThirdPartySourceBrandIds) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> sourceBrandIds = knownThirdPartySourceBrandIds == null || knownThirdPartySourceBrandIds.isEmpty()
                ? thirdPartySourceBrandIds()
                : knownThirdPartySourceBrandIds;
        if (sourceBrandIds.isEmpty()) {
            projects.forEach(project -> project.setThirdPartySource(false));
            return;
        }
        Set<Long> sourceBrandIdSet = new HashSet<>(sourceBrandIds);
        projects.forEach(project -> project.setThirdPartySource(project.getBrandId() != null
                && sourceBrandIdSet.contains(project.getBrandId())));
    }

    private List<Long> thirdPartySourceBrandIds() {
        return brandMapper.selectThirdPartySourceBrands().stream()
                .map(Brand::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Transactional
    public Project create(ProjectCreateRequest req) {
        currentUserService.ensurePermission("project.create");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = validateCompanyBrand(req.getCompanyId(), req.getBrandId());
        internalScopeService.ensureCompanyAccess(operator, company, "project");
        String ownerType = resolveOwnerTypeByCompany(company);
        Long partnerId = resolvePartnerIdByCompany(company);
        validateOwnerBinding(ownerType, partnerId);
        companyPackageBindingService.requireActiveBinding(company.getId());
        validateProjectCompanyPartnerConsistency(ownerType, partnerId, company.getPartnerId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "project");
        if (req.getKeywordGroupIds() != null && !req.getKeywordGroupIds().isEmpty()) {
            throw new BizException(400, "项目新建时不允许绑定拓词组，请先创建项目后再创建或导入拓词组");
        }

        Project project = new Project();
        project.setCompanyId(req.getCompanyId());
        project.setCompanyName(company.getCompanyName());
        project.setProjectCode(buildProjectCode());
        project.setBrandId(req.getBrandId());
        project.setBrandName(resolveBrandName(req.getBrandId()));
        project.setProjectName(req.getProjectName());
        project.setProjectAliases(normalizeAliases(req.getProjectAliases()));
        project.setStatus("pending_start");
        project.setStage("pending_start");
        project.setOwnerType(ownerType);
        project.setSourceType(resolveProjectSourceType(operator));
        project.setPartnerId(partnerId);
        applyRegionFields(project, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        project.setDiscountRateSnapshot(null);
        project.setDeductionAmount(BigDecimal.ZERO);
        project.setDeductionTxnNo(null);
        project.setDeliveryMode("managed");
        project.setSignedAt(req.getSignedAt() != null ? req.getSignedAt() : LocalDateTime.now());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        applyContentStrategyFields(
                project,
                req.getTargetRegions(),
                req.getCoreKeywords(),
                req.getTargetAudience(),
                req.getCustomStatement(),
                req.getContentTone(),
                req.getPreferredAngles(),
                req.getExtraForbiddenPhrases(),
                req.getContentNote()
        );
        project.setCreatedBy(operator.getId());
        project.setRemark(req.getRemark());
        applyKeywordGroupAllocation(project, resolveKeywordGroupAllocation(company.getId(), null,
                req.getKeywordGroupLimitA(), req.getKeywordGroupLimitB(), req.getKeywordGroupLimitC()));
        projectMapper.insert(project);
        replaceCustomerRequirements(project.getId(), req.getCustomerRequirements());
        channelAllocationService.replaceAllocations(project, req.getChannelAllocations(), req.getAllocationVersion(),
                operator.getId(), "project.create");
        attachPlatformSelections(Collections.singletonList(project));
        attachCustomerRequirements(Collections.singletonList(project));
        attachKeywordGroupSelections(Collections.singletonList(project));
        channelAllocationService.attachAllocations(Collections.singletonList(project));

        activityLogService.logAction(
                operator.getId(),
                "project.create",
                "project",
                project.getId(),
                null,
                snapshotProject(project),
                Map.of("companyId", project.getCompanyId(), "brandId", project.getBrandId())
        );

        return project;
    }

    @Transactional
    public Project update(Long id, ProjectUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireProject(id);
        projectStateGuard.ensureCanEditBasicInfo(project, operator);
        Company company = validateCompanyBrand(req.getCompanyId(), req.getBrandId());
        internalScopeService.ensureProjectAccess(operator, project, "project");
        internalScopeService.ensureCompanyAccess(operator, company, "project");
        String ownerType = resolveOwnerTypeByCompany(company);
        Long partnerId = resolvePartnerIdByCompany(company);
        validateOwnerBinding(ownerType, partnerId);
        currentUserService.ensurePartnerResourceAccess(operator, partnerId, "project");
        attachCustomerRequirements(Collections.singletonList(project));
        Map<String, Object> before = snapshotProject(project);
        validateProjectCompanyPartnerConsistency(ownerType, partnerId, company.getPartnerId());
        companyPackageBindingService.requireActiveBinding(company.getId());

        project.setCompanyId(company.getId());
        project.setCompanyName(company.getCompanyName());
        project.setBrandId(req.getBrandId());
        project.setBrandName(resolveBrandName(req.getBrandId()));
        project.setProjectName(req.getProjectName());
        project.setProjectAliases(normalizeAliases(req.getProjectAliases()));
        project.setOwnerType(ownerType);
        project.setPartnerId(partnerId);
        applyRegionFields(project, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        project.setSignedAt(req.getSignedAt());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        applyContentStrategyFields(
                project,
                req.getTargetRegions(),
                req.getCoreKeywords(),
                req.getTargetAudience(),
                req.getCustomStatement(),
                req.getContentTone(),
                req.getPreferredAngles(),
                req.getExtraForbiddenPhrases(),
                req.getContentNote()
        );
        project.setRemark(req.getRemark());
        applyKeywordGroupAllocation(project, resolveKeywordGroupAllocation(company.getId(), project.getId(),
                req.getKeywordGroupLimitA(), req.getKeywordGroupLimitB(), req.getKeywordGroupLimitC()));
        projectMapper.updateById(project);
        if (req.getCustomerRequirements() != null) {
            replaceCustomerRequirements(project.getId(), req.getCustomerRequirements());
        }
        replaceKeywordGroupSelections(project.getId(), project.getCompanyId(), req.getKeywordGroupIds());
        channelAllocationService.replaceAllocations(project, req.getChannelAllocations(), req.getAllocationVersion(),
                operator.getId(), "project.update");
        if ("active".equals(project.getStatus())) {
            validateKeywordGroupQuota(project);
            channelAllocationService.validateActivation(project);
        }
        attachPlatformSelections(Collections.singletonList(project));
        attachCustomerRequirements(Collections.singletonList(project));
        attachKeywordGroupSelections(Collections.singletonList(project));
        channelAllocationService.attachAllocations(Collections.singletonList(project));
        activityLogService.logAction(
                operator.getId(),
                "project.update",
                "project",
                project.getId(),
                before,
                snapshotProject(project),
                Map.of("companyId", project.getCompanyId(), "brandId", project.getBrandId())
        );

        return project;
    }

    @Transactional
    public Project updateChannelAllocations(Long id, ProjectChannelAllocationUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireProject(id);
        projectStateGuard.ensureCanEditBasicInfo(project, operator);
        internalScopeService.ensureProjectAccess(operator, project, "project");
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        channelAllocationService.attachAllocations(Collections.singletonList(project));
        Map<String, Object> before = snapshotProject(project);

        channelAllocationService.replaceAllocations(project, req.getChannelAllocations(), req.getAllocationVersion(),
                operator.getId(), "project.channel_allocations.update");
        if ("active".equals(project.getStatus())) {
            channelAllocationService.validateActivation(project);
        }
        channelAllocationService.attachAllocations(Collections.singletonList(project));
        activityLogService.logAction(
                operator.getId(),
                "project.channel_allocations.update",
                "project",
                project.getId(),
                before,
                snapshotProject(project),
                Map.of("companyId", project.getCompanyId(), "brandId", project.getBrandId())
        );
        return project;
    }

    public void updateStage(Long id, ProjectStageUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        validateStage(req.getStage());
        Project project = requireProject(id);
        internalScopeService.ensureProjectAccess(operator, project, "project");
        projectStateGuard.ensureCanChangeStage(project, operator, req.getStage());
        ensureStageBoundary(project.getStatus(), project.getStage(), req.getStage());
        if (req.getStage().equals(project.getStage())) {
            return;
        }
        String fromStage = project.getStage();
        project.setStage(req.getStage());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.stage.update",
                "project",
                project.getId(),
                Map.of("stage", fromStage),
                Map.of("stage", project.getStage()),
                Map.of("status", project.getStatus(), "from", fromStage, "to", project.getStage())
        );
    }

    @Transactional
    public void updateStatus(Long id, ProjectStatusUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        validateStatus(req.getStatus());
        validateExternalStatus(req.getStatus());
        Project project = requireProject(id);
        internalScopeService.ensureProjectAccess(operator, project, "project");
        ensureStatusOperatePermission(project, req.getStatus(), operator);
        ensureStatusTransition(project.getStatus(), req.getStatus());
        if (req.getStatus().equals(project.getStatus())) {
            return;
        }
        String fromStatus = project.getStatus();
        if (isActivating(fromStatus, req.getStatus())) {
            validateKeywordGroupQuota(project);
            channelAllocationService.validateActivation(project);
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.activate", false);
            markActivatedAndApplyPackageValidity(project);
        } else if (isReleasingActiveAllocation(fromStatus, req.getStatus())) {
            channelAllocationService.lockCompany(project.getCompanyId());
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.pause", true);
        }
        project.setStatus(req.getStatus());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.status.update",
                "project",
                project.getId(),
                Map.of("status", fromStatus),
                Map.of("status", project.getStatus()),
                Map.of("from", fromStatus, "to", project.getStatus())
        );
    }

    @Transactional
    public void updateFlow(Long id, ProjectFlowUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        validateStatus(req.getStatus());
        validateExternalStatus(req.getStatus());
        validateStage(req.getStage());

        Project project = requireProject(id);
        internalScopeService.ensureProjectAccess(operator, project, "project");
        if (!req.getStatus().equals(project.getStatus())) {
            ensureStatusOperatePermission(project, req.getStatus(), operator);
        }
        if (!req.getStage().equals(project.getStage())) {
            projectStateGuard.ensureCanChangeStage(project, operator, req.getStage());
        }

        ensureStatusTransition(project.getStatus(), req.getStatus());
        ensureStageBoundary(req.getStatus(), project.getStage(), req.getStage());

        if (req.getStatus().equals(project.getStatus()) && req.getStage().equals(project.getStage())) {
            return;
        }

        Map<String, Object> before = Map.of("status", project.getStatus(), "stage", project.getStage());
        if (isActivating(project.getStatus(), req.getStatus())) {
            validateKeywordGroupQuota(project);
            channelAllocationService.validateActivation(project);
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.activate", false);
            markActivatedAndApplyPackageValidity(project);
        } else if (isReleasingActiveAllocation(project.getStatus(), req.getStatus())) {
            channelAllocationService.lockCompany(project.getCompanyId());
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.pause", true);
        }
        project.setStatus(req.getStatus());
        project.setStage(req.getStage());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.flow.update",
                "project",
                project.getId(),
                before,
                Map.of("status", project.getStatus(), "stage", project.getStage()),
                Map.of("fromStatus", before.get("status"), "toStatus", req.getStatus(), "fromStage", before.get("stage"), "toStage", req.getStage())
        );
    }

    @Transactional
    public void delete(Long id) {
        SysUser operator = currentUserService.requireCurrentUser();
        Project project = requireProject(id);
        internalScopeService.ensureProjectAccess(operator, project, "project");
        projectStateGuard.ensureCanDelete(project, operator);
        ensurePartnerProjectCanDelete(project);
        if ("active".equals(project.getStatus())) {
            channelAllocationService.lockCompany(project.getCompanyId());
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.delete", true);
        }
        project.setDeletedAt(LocalDateTime.now());
        project.setDeletedBy(operator.getId());
        projectMapper.updateById(project);
        activityLogService.logAction(
                operator.getId(),
                "project.delete",
                "project",
                id,
                snapshotProject(project),
                null,
                null
        );
    }

    private void ensurePartnerProjectCanDelete(Project project) {
        if (!"partner".equals(project.getOwnerType())) {
            return;
        }
        ProjectStartRequest latest = projectStartRequestMapper.selectLatestByProjectId(project.getId());
        if (latest != null) {
            throw new BizException(400, "合伙人项目已提交过总部，不能删除");
        }
    }

    private void purgeProjectRelations(Long projectId) {
        List<Long> articleIds = articleDraftMapper.selectList(
                new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, projectId)
                        .select(ArticleDraft::getId)
        ).stream().map(ArticleDraft::getId).toList();

        List<Long> taskIds = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getProjectId, projectId)
                        .select(DispatchTask::getId)
        ).stream().map(DispatchTask::getId).toList();

        List<Long> reportIds = reportMapper.selectList(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .select(Report::getId)
        ).stream().map(Report::getId).toList();

        if (!articleIds.isEmpty()) {
            articleDraftVersionMapper.delete(new LambdaQueryWrapper<ArticleDraftVersion>()
                    .in(ArticleDraftVersion::getArticleId, articleIds));
            articlePublishLogMapper.delete(new LambdaQueryWrapper<ArticlePublishLog>()
                    .in(ArticlePublishLog::getArticleId, articleIds));
            articleReviewLogMapper.delete(new LambdaQueryWrapper<ArticleReviewLog>()
                    .in(ArticleReviewLog::getArticleId, articleIds));
            distributionTaskMapper.delete(new LambdaQueryWrapper<DistributionTask>()
                    .in(DistributionTask::getArticleId, articleIds));
        }

        articleGenerationLogMapper.delete(new LambdaQueryWrapper<ArticleGenerationLog>()
                .eq(ArticleGenerationLog::getProjectId, projectId));
        contentQuestionRotationMapper.delete(new LambdaQueryWrapper<ContentQuestionRotation>()
                .eq(ContentQuestionRotation::getProjectId, projectId));
        projectDashboardSnapshotMapper.delete(new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                .eq(ProjectDashboardSnapshot::getProjectId, projectId));
        projectDashboardShareMapper.delete(new LambdaQueryWrapper<ProjectDashboardShare>()
                .eq(ProjectDashboardShare::getProjectId, projectId));
        projectKeywordGroupRelMapper.delete(new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                .eq(ProjectKeywordGroupRel::getProjectId, projectId));
        projectCustomerRequirementMapper.delete(new LambdaQueryWrapper<ProjectCustomerRequirement>()
                .eq(ProjectCustomerRequirement::getProjectId, projectId));
        channelAllocationService.deleteProjectAllocations(projectId);
        projectPollRotationMapper.delete(new LambdaQueryWrapper<ProjectPollRotation>()
                .eq(ProjectPollRotation::getProjectId, projectId));

        if (!taskIds.isEmpty()) {
            dispatchAlertMapper.delete(new LambdaQueryWrapper<DispatchAlert>()
                    .in(DispatchAlert::getTaskId, taskIds));
        }
        dispatchAlertMapper.delete(new LambdaQueryWrapper<DispatchAlert>()
                .eq(DispatchAlert::getProjectId, projectId));

        pollResultMapper.delete(new LambdaQueryWrapper<PollResult>()
                .eq(PollResult::getProjectId, projectId));
        pollDailyStatMapper.delete(new LambdaQueryWrapper<PollDailyStat>()
                .eq(PollDailyStat::getProjectId, projectId));
        pollBatchMapper.delete(new LambdaQueryWrapper<PollBatch>()
                .eq(PollBatch::getProjectId, projectId));

        if (!reportIds.isEmpty()) {
            postsaleReportSnapshotMapper.delete(new LambdaQueryWrapper<PostsaleReportSnapshot>()
                    .in(PostsaleReportSnapshot::getReportId, reportIds));
            reportAccessLogMapper.delete(new LambdaQueryWrapper<ReportAccessLog>()
                    .in(ReportAccessLog::getReportId, reportIds));
        }
        reportMapper.delete(new LambdaQueryWrapper<Report>()
                .eq(Report::getProjectId, projectId));

        distributionTaskMapper.delete(new LambdaQueryWrapper<DistributionTask>()
                .eq(DistributionTask::getProjectId, projectId));
        articleDraftMapper.delete(new LambdaQueryWrapper<ArticleDraft>()
                .eq(ArticleDraft::getProjectId, projectId));
        articleBatchMapper.delete(new LambdaQueryWrapper<ArticleBatch>()
                .eq(ArticleBatch::getProjectId, projectId));
        dispatchTaskMapper.delete(new LambdaQueryWrapper<DispatchTask>()
                .eq(DispatchTask::getProjectId, projectId));
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        refreshProjectExpiration(project);
        return project;
    }

    private void refreshProjectExpiration(Project project) {
        if (project.getExpiredAt() == null || "expired".equals(project.getStatus())) {
            return;
        }
        if (!"active".equals(project.getStatus()) && !"paused".equals(project.getStatus())) {
            return;
        }
        if (project.getExpiredAt().isAfter(LocalDateTime.now())) {
            return;
        }
        String fromStatus = project.getStatus();
        project.setStatus("expired");
        projectMapper.update(
                null,
                new LambdaUpdateWrapper<Project>()
                        .eq(Project::getId, project.getId())
                        .eq(Project::getStatus, fromStatus)
                        .set(Project::getStatus, "expired")
        );
    }

    private void expireOverdueProjects() {
        projectMapper.update(
                null,
                new LambdaUpdateWrapper<Project>()
                        .in(Project::getStatus, List.of("active", "paused"))
                        .isNotNull(Project::getExpiredAt)
                        .le(Project::getExpiredAt, LocalDateTime.now())
                        .set(Project::getStatus, "expired")
        );
    }

    private Company validateCompany(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private Company validateCompanyBrand(Long companyId, Long brandId) {
        Company company = validateCompany(companyId);
        if (brandId != null) {
            Brand brand = brandMapper.selectById(brandId);
            if (brand == null || brand.getDeletedAt() != null) {
                throw new BizException(404, "Brand not found");
            }
            if (!companyId.equals(brand.getCompanyId())) {
                throw new BizException(400, "Brand does not belong to selected company");
            }
        }
        return company;
    }

    private void validateOwnerBinding(String ownerType, Long partnerId) {
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(400, "Invalid owner_type");
        }
        if ("direct".equals(ownerType) && partnerId != null) {
            throw new BizException(400, "direct project must not bind partner_id");
        }
        if ("partner".equals(ownerType) && partnerId == null) {
            throw new BizException(400, "partner project must bind partner_id");
        }
    }

    private void validateStatus(String status) {
        if (!ProjectFlowPolicy.STATUS_SET.contains(status)) {
            throw new BizException(400, "Invalid project status");
        }
    }

    private void validateExternalStatus(String status) {
        if (!ProjectFlowPolicy.isExternalStatus(status)) {
            throw new BizException(400, "Project workflow status must be changed by the approval workflow");
        }
    }

    private void validateStage(String stage) {
        if (!ProjectFlowPolicy.STAGE_SET.contains(stage)) {
            throw new BizException(400, "Invalid project stage");
        }
    }

    private void ensureStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus)) {
            return;
        }
        Set<String> allowedTargets = ProjectFlowPolicy.STATUS_TRANSITION.getOrDefault(currentStatus, Set.of());
        if (allowedTargets.isEmpty() && ProjectFlowPolicy.STATUS_SET.contains(targetStatus)) {
            // allow legacy status to migrate into simplified active/paused states
            return;
        }
        if (!allowedTargets.contains(targetStatus)) {
            throw new BizException(400, "Illegal status transition: " + currentStatus + " -> " + targetStatus);
        }
    }

    private void ensureStageBoundary(String status, String currentStage, String targetStage) {
        if (currentStage.equals(targetStage)) {
            return;
        }
        if (!"active".equals(status) && !"paused".equals(status)) {
            throw new BizException(400, "Only active/paused project can change stage");
        }
    }

    private void validateProjectCompanyPartnerConsistency(String ownerType, Long projectPartnerId, Long companyPartnerId) {
        if ("direct".equals(ownerType)) {
            return;
        }
        if (companyPartnerId == null) {
            throw new BizException(400, "Selected brand belongs to direct company, cannot create partner project");
        }
        if (!companyPartnerId.equals(projectPartnerId)) {
            throw new BizException(400, "Project partner_id must match company partner_id");
        }
    }

    private String resolveOwnerTypeByCompany(Company company) {
        if (company == null) {
            return "direct";
        }
        if (StringUtils.hasText(company.getOwnerType())) {
            return company.getOwnerType().trim();
        }
        return company.getPartnerId() == null ? "direct" : "partner";
    }

    private Long resolvePartnerIdByCompany(Company company) {
        if (company == null) {
            return null;
        }
        return company.getPartnerId();
    }

    private String resolveProjectSourceType(SysUser operator) {
        return currentUserService.isPartnerUser(operator) ? "partner" : "internal";
    }

    private void ensureStatusOperatePermission(Project project, String targetStatus, SysUser operator) {
        if ("active".equals(targetStatus)) {
            projectStateGuard.ensureCanStart(project, operator);
            return;
        }
        if ("paused".equals(targetStatus)) {
            projectStateGuard.ensureCanPause(project, operator);
            return;
        }
        if ("expired".equals(targetStatus)) {
            currentUserService.ensurePermission("project.update");
            currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        }
    }

    private void ensureSalesProjectAccess(SysUser user, Project project) {
        if (!"sales".equals(user.getRole())) {
            return;
        }
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null
                || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this project");
        }
    }

    private boolean isActivating(String fromStatus, String targetStatus) {
        return !"active".equals(fromStatus) && "active".equals(targetStatus);
    }

    private boolean isReleasingActiveAllocation(String fromStatus, String targetStatus) {
        return "active".equals(fromStatus) && !"active".equals(targetStatus);
    }

    public ProjectChannelAllocationQuotaVO channelAllocationQuota(Long companyId, Long excludeProjectId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Company company = validateCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        if ("sales".equals(user.getRole())) {
            if (company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
                throw new BizException(403, "No permission to access this company");
            }
        }
        return channelAllocationService.quota(companyId, excludeProjectId);
    }

    public ProjectKeywordGroupQuotaVO keywordGroupQuota(Long companyId, Long excludeProjectId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Company company = validateCompany(companyId);
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(user, company, "company");
        if ("sales".equals(user.getRole())) {
            if (company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
                throw new BizException(403, "No permission to access this company");
            }
        }
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        KeywordAllocation used = activeKeywordAllocation(companyId, excludeProjectId);
        KeywordAllocation current = currentProjectKeywordAllocation(excludeProjectId);
        KeywordAllocation limit = bindingKeywordLimit(binding);

        ProjectKeywordGroupQuotaVO vo = new ProjectKeywordGroupQuotaVO();
        vo.setCompanyId(companyId);
        vo.setExcludeProjectId(excludeProjectId);
        vo.setQuotaLimit(defaultInt(binding.getKeywordGroupLimit(), 0));
        vo.setQuotaLimitA(limit.a());
        vo.setQuotaLimitB(limit.b());
        vo.setQuotaLimitC(limit.c());
        vo.setActiveAllocatedCount(used.total());
        vo.setActiveAllocatedCountA(used.a());
        vo.setActiveAllocatedCountB(used.b());
        vo.setActiveAllocatedCountC(used.c());
        vo.setCurrentProjectAllocatedCount(current.total());
        vo.setCurrentProjectAllocatedCountA(current.a());
        vo.setCurrentProjectAllocatedCountB(current.b());
        vo.setCurrentProjectAllocatedCountC(current.c());
        vo.setRemainingCount(Math.max(limit.total() - used.total(), 0));
        vo.setRemainingCountA(Math.max(limit.a() - used.a(), 0));
        vo.setRemainingCountB(Math.max(limit.b() - used.b(), 0));
        vo.setRemainingCountC(Math.max(limit.c() - used.c(), 0));
        vo.setInputMaxA(vo.getRemainingCountA());
        vo.setInputMaxB(vo.getRemainingCountB());
        vo.setInputMaxC(vo.getRemainingCountC());
        return vo;
    }

    private void markActivatedAndApplyPackageValidity(Project project) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        LocalDateTime validFrom = binding.getBoundAt() != null ? binding.getBoundAt() : LocalDateTime.now();
        project.setStartDate(validFrom.toLocalDate());
        if (binding.getServiceMonths() != null && binding.getServiceMonths() > 0) {
            LocalDateTime validUntil = validFrom.plusMonths(binding.getServiceMonths());
            if (!validUntil.isAfter(LocalDateTime.now())) {
                throw new BizException(400, "Customer package validity has expired, cannot start project");
            }
            project.setEndDate(validUntil.toLocalDate());
            project.setExpiredAt(validUntil);
        } else {
            project.setEndDate(null);
            project.setExpiredAt(null);
        }
        if (project.getActivatedAt() == null) {
            project.setActivatedAt(LocalDateTime.now());
        }
    }

    private void validateKeywordGroupQuota(Project project) {
        keywordGroupService.validateProjectKeywordGroupComplete(project);
        specialIndustryReadinessService.validateProjectActivation(project);
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        KeywordAllocation limit = bindingKeywordLimit(binding);
        KeywordAllocation activeUsed = activeKeywordAllocation(project.getCompanyId(), project.getId());
        KeywordAllocation projectUsed = projectKeywordAllocation(project);
        KeywordAllocation totalUsed = activeUsed.plus(projectUsed);
        if (totalUsed.a() > limit.a() || totalUsed.b() > limit.b() || totalUsed.c() > limit.c()) {
            throw new BizException(400, "KEYWORD_GROUP_QUOTA_EXCEEDED: 关键词组额度不足，套餐限制 A/B/C="
                    + limit.a() + "/" + limit.b() + "/" + limit.c()
                    + "，当前已激活项目占用 A/B/C=" + activeUsed.a() + "/" + activeUsed.b() + "/" + activeUsed.c()
                    + "，本项目占用 A/B/C=" + projectUsed.a() + "/" + projectUsed.b() + "/" + projectUsed.c());
        }
    }

    private void validateProjectHasKeywordGroup(Project project) {
        List<ProjectKeywordGroupRel> rels = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, project.getId())
        );
        Set<Long> relGroupIds = rels.stream()
                .map(ProjectKeywordGroupRel::getKeywordGroupId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Long relCount = relGroupIds.isEmpty() ? 0L : keywordGroupMapper.selectCount(
                new LambdaQueryWrapper<KeywordGroup>()
                        .in(KeywordGroup::getId, relGroupIds)
                        .eq(KeywordGroup::getCompanyId, project.getCompanyId())
                        .eq(KeywordGroup::getDeleted, false)
        );
        Long directCount = keywordGroupMapper.selectCount(
                new LambdaQueryWrapper<KeywordGroup>()
                        .eq(KeywordGroup::getProjectId, project.getId())
                        .eq(KeywordGroup::getCompanyId, project.getCompanyId())
                        .eq(KeywordGroup::getDeleted, false)
        );
        if ((relCount == null || relCount == 0) && (directCount == null || directCount == 0)) {
            throw new BizException(400, "KEYWORD_GROUP_REQUIRED: 项目启动前必须至少绑定一个关键词组");
        }
    }

    private KeywordAllocation resolveKeywordGroupAllocation(Long companyId, Long excludeProjectId, Integer requestedA, Integer requestedB, Integer requestedC) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        KeywordAllocation limit = bindingKeywordLimit(binding);
        KeywordAllocation used = activeKeywordAllocation(companyId, excludeProjectId);
        KeywordAllocation max = new KeywordAllocation(
                Math.max(limit.a() - used.a(), 0),
                Math.max(limit.b() - used.b(), 0),
                Math.max(limit.c() - used.c(), 0)
        );
        int a = requestedA == null ? max.a() : requestedA;
        int b = requestedB == null ? max.b() : requestedB;
        int c = requestedC == null ? max.c() : requestedC;
        if (a < 0 || b < 0 || c < 0) {
            throw new BizException(400, "keyword group tier allocation must be >= 0");
        }
        if (a > max.a() || b > max.b() || c > max.c()) {
            throw new BizException(400, "KEYWORD_GROUP_QUOTA_EXCEEDED: 项目 A/B/C 分配不能超过当前可分配数量 "
                    + max.a() + "/" + max.b() + "/" + max.c());
        }
        return new KeywordAllocation(a, b, c);
    }

    private void applyKeywordGroupAllocation(Project project, KeywordAllocation allocation) {
        project.setPlanKeywordGroupLimitA(allocation.a());
        project.setPlanKeywordGroupLimitB(allocation.b());
        project.setPlanKeywordGroupLimitC(allocation.c());
        project.setPlanKeywordGroupLimit(allocation.total());
    }

    private KeywordAllocation activeKeywordAllocation(Long companyId, Long excludeProjectId) {
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getCompanyId, companyId)
                        .eq(Project::getStatus, "active")
                        .isNull(Project::getDeletedAt)
                        .ne(excludeProjectId != null, Project::getId, excludeProjectId)
        );
        int a = projects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitA(), defaultInt(p.getPlanKeywordGroupLimit(), 0))).sum();
        int b = projects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitB(), 0)).sum();
        int c = projects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitC(), 0)).sum();
        return new KeywordAllocation(a, b, c);
    }

    private KeywordAllocation currentProjectKeywordAllocation(Long projectId) {
        if (projectId == null) {
            return new KeywordAllocation(0, 0, 0);
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            return new KeywordAllocation(0, 0, 0);
        }
        return projectKeywordAllocation(project);
    }

    private KeywordAllocation projectKeywordAllocation(Project project) {
        return new KeywordAllocation(
                defaultInt(project.getPlanKeywordGroupLimitA(), defaultInt(project.getPlanKeywordGroupLimit(), 0)),
                defaultInt(project.getPlanKeywordGroupLimitB(), 0),
                defaultInt(project.getPlanKeywordGroupLimitC(), 0)
        );
    }

    private KeywordAllocation bindingKeywordLimit(CompanyPackageBinding binding) {
        return new KeywordAllocation(
                defaultInt(binding.getKeywordGroupLimitA(), defaultInt(binding.getKeywordGroupLimit(), 0)),
                defaultInt(binding.getKeywordGroupLimitB(), 0),
                defaultInt(binding.getKeywordGroupLimitC(), 0)
        );
    }

    private int defaultInt(Integer value, Integer fallback) {
        return value == null ? (fallback == null ? 0 : fallback) : value;
    }

    private String buildProjectCode() {
        return "PRJ" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);
    }

    private void attachPlatformSelections(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        for (Project project : projects) {
            project.setSelectedPlatformCodesP0(List.of());
            project.setSelectedPlatformCodesP1(List.of());
            project.setSelectedPlatformCodesP2(List.of());
        }
    }

    private void replaceKeywordGroupSelections(Long projectId, Long companyId, List<Long> selectedKeywordGroupIds) {
        List<Long> normalizedIds = normalizeKeywordGroupIds(selectedKeywordGroupIds);
        if (normalizedIds.size() > 10) {
            throw new BizException(400, "Keyword group count must be <= 10");
        }

        projectKeywordGroupRelMapper.delete(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
        );
        if (normalizedIds.isEmpty()) {
            return;
        }

        List<KeywordGroup> groups = keywordGroupMapper.selectList(
                new LambdaQueryWrapper<KeywordGroup>()
                        .in(KeywordGroup::getId, normalizedIds)
                        .eq(KeywordGroup::getCompanyId, companyId)
                        .eq(KeywordGroup::getDeleted, false)
        );
        if (groups.size() != normalizedIds.size()) {
            throw new BizException(400, "Selected keyword groups must belong to project company");
        }
        for (Long groupId : normalizedIds) {
            ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
            rel.setProjectId(projectId);
            rel.setKeywordGroupId(groupId);
            projectKeywordGroupRelMapper.insert(rel);
        }
    }

    private List<Long> normalizeKeywordGroupIds(List<Long> selectedIds) {
        if (selectedIds == null) {
            return List.of();
        }
        return selectedIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private void attachCustomerRequirements(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
        List<ProjectCustomerRequirement> requirements = projectCustomerRequirementMapper.selectList(
                new LambdaQueryWrapper<ProjectCustomerRequirement>()
                        .in(ProjectCustomerRequirement::getProjectId, projectIds)
                        .orderByDesc(ProjectCustomerRequirement::getCreatedAt)
                        .orderByDesc(ProjectCustomerRequirement::getId)
        );
        Map<Long, List<String>> requirementMap = new LinkedHashMap<>();
        for (ProjectCustomerRequirement requirement : requirements) {
            requirementMap
                    .computeIfAbsent(requirement.getProjectId(), k -> new ArrayList<>())
                    .add(requirement.getRequirementText());
        }
        for (Project project : projects) {
            project.setCustomerRequirements(requirementMap.getOrDefault(project.getId(), List.of()));
        }
    }

    private void replaceCustomerRequirements(Long projectId, List<String> rawRequirements) {
        List<String> requirements = normalizeCustomerRequirements(rawRequirements);
        projectCustomerRequirementMapper.delete(new LambdaQueryWrapper<ProjectCustomerRequirement>()
                .eq(ProjectCustomerRequirement::getProjectId, projectId));
        if (requirements.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (String text : requirements) {
            ProjectCustomerRequirement requirement = new ProjectCustomerRequirement();
            requirement.setProjectId(projectId);
            requirement.setRequirementText(text);
            requirement.setCreatedAt(now);
            requirement.setUpdatedAt(now);
            projectCustomerRequirementMapper.insert(requirement);
        }
    }

    private List<String> normalizeCustomerRequirements(List<String> rawRequirements) {
        if (rawRequirements == null || rawRequirements.isEmpty()) {
            return List.of();
        }
        if (rawRequirements.size() > CUSTOMER_REQUIREMENT_MAX_COUNT) {
            throw new BizException(400, "客户需求最多录入 " + CUSTOMER_REQUIREMENT_MAX_COUNT + " 条");
        }
        List<String> requirements = new ArrayList<>();
        for (String raw : rawRequirements) {
            if (!StringUtils.hasText(raw)) {
                throw new BizException(400, "客户需求不能为空");
            }
            String text = raw.trim();
            int length = text.codePointCount(0, text.length());
            if (length < CUSTOMER_REQUIREMENT_MIN_LENGTH || length > CUSTOMER_REQUIREMENT_MAX_LENGTH) {
                throw new BizException(400, "每条客户需求字数需在 10-100 之间");
            }
            requirements.add(text);
        }
        return requirements;
    }

    private void attachKeywordGroupSelections(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
        List<ProjectKeywordGroupRel> rels = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .in(ProjectKeywordGroupRel::getProjectId, projectIds)
                        .orderByAsc(ProjectKeywordGroupRel::getId)
        );
        Map<Long, List<Long>> projectGroupIdMap = new LinkedHashMap<>();
        Set<Long> allGroupIds = new LinkedHashSet<>();
        for (ProjectKeywordGroupRel rel : rels) {
            projectGroupIdMap.computeIfAbsent(rel.getProjectId(), k -> new ArrayList<>()).add(rel.getKeywordGroupId());
            allGroupIds.add(rel.getKeywordGroupId());
        }
        Map<Long, KeywordGroup> groupMap = allGroupIds.isEmpty() ? Map.of() : keywordGroupMapper.selectList(
                new LambdaQueryWrapper<KeywordGroup>()
                        .in(KeywordGroup::getId, allGroupIds)
                        .eq(KeywordGroup::getDeleted, false)
        ).stream().collect(Collectors.toMap(KeywordGroup::getId, g -> g, (a, b) -> a, LinkedHashMap::new));
        List<Long> activeGroupIds = new ArrayList<>(groupMap.keySet());
        Map<Long, KeywordGroupColumnsVO> groupColumnsMap = loadKeywordGroupColumns(activeGroupIds);
        Map<Long, Long> savedCountMap = keywordGroupService.calcSavedCountsByGroupIds(activeGroupIds);
        Map<Long, KeywordGroupService.KeywordTierCounts> tierCountMap = keywordGroupService.calcSavedTierCountsByGroupIds(activeGroupIds);
        for (Project project : projects) {
            List<Long> groupIds = projectGroupIdMap.getOrDefault(project.getId(), List.of()).stream()
                    .filter(groupMap::containsKey)
                    .toList();
            long totalSaved = 0L;
            long totalA = 0L;
            long totalB = 0L;
            long totalC = 0L;
            List<KeywordGroupListItemVO> groupItems = new ArrayList<>();
            for (Long groupId : groupIds) {
                totalSaved += savedCountMap.getOrDefault(groupId, 0L);
                KeywordGroupService.KeywordTierCounts tierCounts = tierCountMap.get(groupId);
                totalA += tierCounts == null ? 0L : tierCounts.a();
                totalB += tierCounts == null ? 0L : tierCounts.b();
                totalC += tierCounts == null ? 0L : tierCounts.c();
                KeywordGroup group = groupMap.get(groupId);
                if (group != null) {
                    KeywordGroupListItemVO item = new KeywordGroupListItemVO();
                    item.setId(group.getId());
                    item.setCompanyId(group.getCompanyId());
                    item.setProjectId(group.getProjectId());
                    item.setName(group.getName());
                    item.setType(group.getType());
                    item.setTypeLabel(keywordTypeConfigService.labelOf(group.getType()));
                    item.setLegacyType(keywordTypeConfigService.isLegacyType(group.getType()));
                    item.setSavedKeywordCount(savedCountMap.getOrDefault(groupId, 0L));
                    item.setSavedKeywordCountA(tierCounts == null ? 0L : tierCounts.a());
                    item.setSavedKeywordCountB(tierCounts == null ? 0L : tierCounts.b());
                    item.setSavedKeywordCountC(tierCounts == null ? 0L : tierCounts.c());
                    item.setColumns(groupColumnsMap.get(groupId));
                    item.setUpdatedAt(group.getUpdatedAt());
                    groupItems.add(item);
                }
            }
            project.setSelectedKeywordGroupIds(groupIds);
            project.setSelectedKeywordGroupCount(groupIds.size());
            project.setSelectedKeywordSavedKeywords(totalSaved);
            project.setSelectedKeywordSavedKeywordsA(totalA);
            project.setSelectedKeywordSavedKeywordsB(totalB);
            project.setSelectedKeywordSavedKeywordsC(totalC);
            project.setSelectedKeywordGroups(groupItems);
        }
    }

    private Map<Long, KeywordGroupColumnsVO> loadKeywordGroupColumns(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<KeywordGroupWord> words = keywordGroupWordMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupWord>()
                        .in(KeywordGroupWord::getGroupId, groupIds)
                        .orderByAsc(KeywordGroupWord::getGroupId)
                        .orderByAsc(KeywordGroupWord::getColumnType)
                        .orderByAsc(KeywordGroupWord::getSortOrder)
                        .orderByAsc(KeywordGroupWord::getId)
        );
        Map<Long, Map<String, List<KeywordWordItemVO>>> grouped = new LinkedHashMap<>();
        for (KeywordGroupWord word : words) {
            KeywordWordItemVO item = new KeywordWordItemVO();
            item.setId(word.getId());
            item.setWordText(word.getWordText());
            item.setSource(word.getSource());
            item.setSortOrder(word.getSortOrder());
            item.setIsManual(false);
            item.setIsTemporary(false);
            grouped.computeIfAbsent(word.getGroupId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(word.getColumnType(), key -> new ArrayList<>())
                    .add(item);
        }
        Map<Long, KeywordGroupColumnsVO> result = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            Map<String, List<KeywordWordItemVO>> columns = grouped.getOrDefault(groupId, Map.of());
            KeywordGroupColumnsVO vo = new KeywordGroupColumnsVO();
            List<KeywordWordItemVO> areaWords = new ArrayList<>();
            areaWords.addAll(columns.getOrDefault("area", List.of()));
            areaWords.addAll(columns.getOrDefault("region", List.of()));
            vo.setAreaWords(areaWords);
            vo.setRegionWords(areaWords);
            vo.setPrefixWords(columns.getOrDefault("prefix", List.of()));
            vo.setCoreWords(columns.getOrDefault("core", List.of()));
            vo.setIndustryWords(columns.getOrDefault("industry", List.of()));
            vo.setSuffixWords(columns.getOrDefault("suffix", List.of()));
            vo.setCoreWordsA(columns.getOrDefault("core_a", List.of()));
            vo.setCompareWords(columns.getOrDefault("compare", List.of()));
            vo.setCoreWordsB(columns.getOrDefault("core_b", List.of()));
            result.put(groupId, vo);
        }
        return result;
    }

    private Map<String, Object> snapshotProject(Project project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", project.getId());
        snapshot.put("projectCode", project.getProjectCode());
        snapshot.put("companyId", project.getCompanyId());
        snapshot.put("companyName", project.getCompanyName());
        snapshot.put("brandId", project.getBrandId());
        snapshot.put("brandName", project.getBrandName());
        snapshot.put("projectName", project.getProjectName());
        snapshot.put("projectAliases", project.getProjectAliases());
        snapshot.put("ownerType", project.getOwnerType());
        snapshot.put("sourceType", project.getSourceType());
        snapshot.put("contentGenerationEnabled", project.getContentGenerationEnabled());
        snapshot.put("partnerId", project.getPartnerId());
        snapshot.put("provinceCode", project.getProvinceCode());
        snapshot.put("provinceName", project.getProvinceName());
        snapshot.put("cityCode", project.getCityCode());
        snapshot.put("cityName", project.getCityName());
        snapshot.put("districtCode", project.getDistrictCode());
        snapshot.put("districtName", project.getDistrictName());
        snapshot.put("targetRegions", project.getTargetRegions());
        snapshot.put("coreKeywords", project.getCoreKeywords());
        snapshot.put("targetAudience", project.getTargetAudience());
        snapshot.put("customStatement", project.getCustomStatement());
        snapshot.put("contentTone", project.getContentTone());
        snapshot.put("preferredAngles", project.getPreferredAngles());
        snapshot.put("extraForbiddenPhrases", project.getExtraForbiddenPhrases());
        snapshot.put("contentNote", project.getContentNote());
        snapshot.put("customerRequirements", project.getCustomerRequirements());
        snapshot.put("status", project.getStatus());
        snapshot.put("stage", project.getStage());
        snapshot.put("activatedAt", project.getActivatedAt());
        snapshot.put("expiredAt", project.getExpiredAt());
        snapshot.put("planKeywordGroupLimit", project.getPlanKeywordGroupLimit());
        snapshot.put("planKeywordGroupLimitA", project.getPlanKeywordGroupLimitA());
        snapshot.put("planKeywordGroupLimitB", project.getPlanKeywordGroupLimitB());
        snapshot.put("planKeywordGroupLimitC", project.getPlanKeywordGroupLimitC());
        snapshot.put("planMonthlyReportDepth", project.getPlanMonthlyReportDepth());
        snapshot.put("planQuarterlyReportDepth", project.getPlanQuarterlyReportDepth());
        snapshot.put("planConsultantIntensity", project.getPlanConsultantIntensity());
        snapshot.put("planCompetitorInsightDepth", project.getPlanCompetitorInsightDepth());
        snapshot.put("planMediaDistributionIntensity", project.getPlanMediaDistributionIntensity());
        snapshot.put("planCommitmentTargetIntensity", project.getPlanCommitmentTargetIntensity());
        snapshot.put("planTargetMetricType", project.getPlanTargetMetricType());
        snapshot.put("planTargetMetricValue", project.getPlanTargetMetricValue());
        snapshot.put("planTargetWindowDays", project.getPlanTargetWindowDays());
        snapshot.put("selectedPlatformCodesP0", project.getSelectedPlatformCodesP0());
        snapshot.put("selectedPlatformCodesP1", project.getSelectedPlatformCodesP1());
        snapshot.put("selectedPlatformCodesP2", project.getSelectedPlatformCodesP2());
        snapshot.put("selectedKeywordGroupIds", project.getSelectedKeywordGroupIds());
        snapshot.put("selectedKeywordGroupCount", project.getSelectedKeywordGroupCount());
        snapshot.put("selectedKeywordSavedKeywords", project.getSelectedKeywordSavedKeywords());
        snapshot.put("discountRateSnapshot", project.getDiscountRateSnapshot());
        snapshot.put("deductionAmount", project.getDeductionAmount());
        snapshot.put("deductionTxnNo", project.getDeductionTxnNo());
        return snapshot;
    }

    private void applyRegionFields(Project project,
                                   String provinceCode,
                                   String provinceName,
                                   String cityCode,
                                   String cityName,
                                   String districtCode,
                                   String districtName) {
        project.setProvinceCode(trimToNull(provinceCode));
        project.setProvinceName(trimToNull(provinceName));
        project.setCityCode(trimToNull(cityCode));
        project.setCityName(trimToNull(cityName));
        project.setDistrictCode(trimToNull(districtCode));
        project.setDistrictName(trimToNull(districtName));
    }

    private void applyContentStrategyFields(Project project,
                                            List<String> targetRegions,
                                            String coreKeywords,
                                            String targetAudience,
                                            String customStatement,
                                            String contentTone,
                                            List<String> preferredAngles,
                                            List<String> extraForbiddenPhrases,
                                            String contentNote) {
        String normalizedTargetRegions = normalizeJsonStringList(targetRegions);
        if (!StringUtils.hasText(normalizedTargetRegions)) {
            throw new BizException(400, "目标区域词不能为空");
        }
        String normalizedCoreKeywords = normalizeCommaText(coreKeywords, 200, "核心关键词");
        String normalizedTargetAudience = trimToNull(targetAudience);
        if (!StringUtils.hasText(normalizedTargetAudience)) {
            throw new BizException(400, "目标受众不能为空");
        }
        project.setTargetRegions(normalizedTargetRegions);
        project.setCoreKeywords(normalizedCoreKeywords);
        project.setTargetAudience(normalizedTargetAudience);
        project.setCustomStatement(trimToNull(customStatement));
        project.setContentTone(trimToNull(contentTone));
        project.setPreferredAngles(normalizeJsonStringList(preferredAngles));
        project.setExtraForbiddenPhrases(normalizeJsonStringList(extraForbiddenPhrases));
        project.setContentNote(trimToNull(contentNote));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeJsonStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(normalized);
    }

    private String normalizeCommaText(String value, int maxLength, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, fieldLabel + "不能为空");
        }
        String joined = Arrays.stream(value.replace('，', ',').split("[,、;；\\n\\r]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(joined)) {
            throw new BizException(400, fieldLabel + "不能为空");
        }
        if (joined.length() > maxLength) {
            throw new BizException(400, fieldLabel + "不能超过 " + maxLength + " 字");
        }
        return joined;
    }

    private String normalizeAliases(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replace('，', ',');
        String joined = Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(","));
        return StringUtils.hasText(joined) ? joined : null;
    }

    private String resolveBrandName(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        return brand.getBrandName();
    }

    private record KeywordAllocation(int a, int b, int c) {
        private int total() {
            return a + b + c;
        }

        private KeywordAllocation plus(KeywordAllocation other) {
            return new KeywordAllocation(a + other.a, b + other.b, c + other.c);
        }
    }
}
