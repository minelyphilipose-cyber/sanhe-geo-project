package com.huanjing.geo.module.project.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.huanjing.geo.module.project.dto.ProjectFlowUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStageUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.dto.ProjectUpdateRequest;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.ProjectPlatformBinding;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectPlatformBindingMapper;
import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import com.huanjing.geo.module.report.entity.PostsaleReportSnapshot;
import com.huanjing.geo.module.report.entity.Report;
import com.huanjing.geo.module.report.entity.ReportAccessLog;
import com.huanjing.geo.module.report.mapper.PostsaleReportSnapshotMapper;
import com.huanjing.geo.module.report.mapper.ReportAccessLogMapper;
import com.huanjing.geo.module.report.mapper.ReportMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private static final Set<String> OWNER_TYPES = Set.of("direct", "partner", "joint");

    private final ProjectMapper projectMapper;
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
    private final ProjectPlatformBindingMapper projectPlatformBindingMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final PostsaleReportSnapshotMapper postsaleReportSnapshotMapper;
    private final ReportAccessLogMapper reportAccessLogMapper;
    private final ReportMapper reportMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final CurrentUserService currentUserService;
    private final ProjectStateGuard projectStateGuard;
    private final ActivityLogService activityLogService;
    private final BrandStatementDispatchService brandStatementDispatchService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;

    public Page<Project> page(long current, long size, String keyword, String status, String stage, Long partnerId, Long brandId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getProjectName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Project::getStatus, status);
        }
        if (StringUtils.hasText(stage)) {
            wrapper.eq(Project::getStage, stage);
        }
        if (brandId != null) {
            wrapper.eq(Project::getBrandId, brandId);
        }

        Long scopePartnerId = currentUserService.resolvePartnerQueryScope(user, partnerId);
        if (scopePartnerId != null) {
            wrapper.eq(Project::getPartnerId, scopePartnerId);
        }
        if ("sales".equals(user.getRole())) {
            List<Long> signedCompanyIds = companyMapper.selectList(
                    new LambdaQueryWrapper<Company>()
                            .isNull(Company::getDeletedAt)
                            .select(Company::getId)
                            .eq(Company::getSalesOwnerId, user.getId())
                            .eq(Company::getStatus, "signed")
            ).stream().map(Company::getId).collect(Collectors.toList());
            if (signedCompanyIds.isEmpty()) {
                return new Page<>(current, size);
            }
            wrapper.in(Project::getCompanyId, signedCompanyIds);
        }

        Page<Project> page = projectMapper.selectPage(new Page<>(current, size), wrapper);
        attachPlatformSelections(page.getRecords());
        attachKeywordGroupSelections(page.getRecords());
        channelAllocationService.attachAllocations(page.getRecords());
        return page;
    }

    public Project detail(Long id) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(id);
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        ensureSalesProjectAccess(user, project);
        attachPlatformSelections(Collections.singletonList(project));
        attachKeywordGroupSelections(Collections.singletonList(project));
        channelAllocationService.attachAllocations(Collections.singletonList(project));
        return project;
    }

    @Transactional
    public Project create(ProjectCreateRequest req) {
        currentUserService.ensurePermission("project.create");
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = validateCompanyBrand(req.getCompanyId(), req.getBrandId());
        String ownerType = resolveOwnerTypeByCompany(company);
        Long partnerId = resolvePartnerIdByCompany(company);
        validateOwnerBinding(ownerType, partnerId);
        companyPackageBindingService.requireActiveBinding(company.getId());
        validateProjectCompanyPartnerConsistency(ownerType, partnerId, company.getPartnerId());
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "project");

        Project project = new Project();
        project.setCompanyId(req.getCompanyId());
        project.setCompanyName(company.getCompanyName());
        project.setProjectCode(buildProjectCode());
        project.setBrandId(req.getBrandId());
        project.setBrandName(resolveBrandName(req.getBrandId()));
        project.setProjectName(req.getProjectName());
        project.setProjectAliases(normalizeAliases(req.getProjectAliases()));
        project.setStatus("paused");
        project.setStage("pending_start");
        project.setOwnerType(ownerType);
        project.setSourceType(resolveProjectSourceType(operator));
        project.setPartnerId(partnerId);
        applyRegionFields(project, req.getProvinceCode(), req.getProvinceName(), req.getCityCode(), req.getCityName(), req.getDistrictCode(), req.getDistrictName());
        project.setDiscountRateSnapshot(null);
        project.setDeductionAmount(BigDecimal.ZERO);
        project.setDeductionTxnNo(null);
        project.setDeliveryMode(StringUtils.hasText(req.getDeliveryMode()) ? req.getDeliveryMode() : "managed");
        project.setSignedAt(req.getSignedAt() != null ? req.getSignedAt() : LocalDateTime.now());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        applyContentStrategyFields(
                project,
                req.getTargetRegions(),
                req.getTargetAudience(),
                req.getCustomStatement(),
                req.getContentTone(),
                req.getPreferredAngles(),
                req.getExtraForbiddenPhrases(),
                req.getContentNote()
        );
        project.setCreatedBy(operator.getId());
        project.setRemark(req.getRemark());
        projectMapper.insert(project);
        replaceKeywordGroupSelections(project.getId(), project.getCompanyId(), req.getKeywordGroupIds());
        channelAllocationService.replaceAllocations(project, req.getChannelAllocations(), req.getAllocationVersion(),
                operator.getId(), "project.create");
        attachPlatformSelections(Collections.singletonList(project));
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
        String ownerType = resolveOwnerTypeByCompany(company);
        Long partnerId = resolvePartnerIdByCompany(company);
        validateOwnerBinding(ownerType, partnerId);
        currentUserService.ensurePartnerResourceAccess(operator, partnerId, "project");
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
        project.setDeliveryMode(StringUtils.hasText(req.getDeliveryMode()) ? req.getDeliveryMode() : project.getDeliveryMode());
        project.setSignedAt(req.getSignedAt());
        project.setStartDate(req.getStartDate());
        project.setEndDate(req.getEndDate());
        project.setPrimaryGoal(req.getPrimaryGoal());
        applyContentStrategyFields(
                project,
                req.getTargetRegions(),
                req.getTargetAudience(),
                req.getCustomStatement(),
                req.getContentTone(),
                req.getPreferredAngles(),
                req.getExtraForbiddenPhrases(),
                req.getContentNote()
        );
        project.setRemark(req.getRemark());
        projectMapper.updateById(project);
        replaceKeywordGroupSelections(project.getId(), project.getCompanyId(), req.getKeywordGroupIds());
        channelAllocationService.replaceAllocations(project, req.getChannelAllocations(), req.getAllocationVersion(),
                operator.getId(), "project.update");
        if ("active".equals(project.getStatus())) {
            validateKeywordGroupQuota(project);
            channelAllocationService.validateActivation(project);
        }
        attachPlatformSelections(Collections.singletonList(project));
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

    public void updateStage(Long id, ProjectStageUpdateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        validateStage(req.getStage());
        Project project = requireProject(id);
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
        Project project = requireProject(id);
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
            markActivatedIfNeeded(project);
        } else if (isReleasingActiveAllocation(fromStatus, req.getStatus())) {
            channelAllocationService.lockCompany(project.getCompanyId());
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.pause", true);
        }
        project.setStatus(req.getStatus());
        projectMapper.updateById(project);
        if (isActivating(fromStatus, req.getStatus())) {
            try {
                brandStatementDispatchService.maybeEnqueueOnProjectActivated(project);
            } catch (Exception ex) {
                // Brand statement dispatch is best-effort; project activation should not fail because Redis is unavailable.
                log.warn("Brand statement enqueue skipped after activation, projectId={}, brandId={}, reason={}",
                        project.getId(), project.getBrandId(), ex.getMessage());
            }
        }
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
        validateStage(req.getStage());

        Project project = requireProject(id);
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
            markActivatedIfNeeded(project);
        } else if (isReleasingActiveAllocation(project.getStatus(), req.getStatus())) {
            channelAllocationService.lockCompany(project.getCompanyId());
            channelAllocationService.auditCurrentAllocations(project, operator.getId(), "project.pause", true);
        }
        project.setStatus(req.getStatus());
        project.setStage(req.getStage());
        projectMapper.updateById(project);
        if (isActivating(String.valueOf(before.get("status")), req.getStatus())) {
            try {
                brandStatementDispatchService.maybeEnqueueOnProjectActivated(project);
            } catch (Exception ex) {
                // Brand statement dispatch is best-effort; project activation should not fail because Redis is unavailable.
                log.warn("Brand statement enqueue skipped after flow activation, projectId={}, brandId={}, reason={}",
                        project.getId(), project.getBrandId(), ex.getMessage());
            }
        }
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
        projectStateGuard.ensureCanDelete(project, operator);
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
        channelAllocationService.deleteProjectAllocations(projectId);
        projectPlatformBindingMapper.delete(new LambdaQueryWrapper<ProjectPlatformBinding>()
                .eq(ProjectPlatformBinding::getProjectId, projectId));
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
        return project;
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
        if (("partner".equals(ownerType) || "joint".equals(ownerType)) && partnerId == null) {
            throw new BizException(400, "partner/joint project must bind partner_id");
        }
    }

    private void validateStatus(String status) {
        if (!ProjectFlowPolicy.STATUS_SET.contains(status)) {
            throw new BizException(400, "Invalid project status");
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
            throw new BizException(400, "Selected brand belongs to direct company, cannot create partner/joint project");
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
        if (!"signed".equals(company.getStatus())) {
            throw new BizException(403, "Sales can only access projects of signed companies");
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
        if ("sales".equals(user.getRole())) {
            if (company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
                throw new BizException(403, "No permission to access this company");
            }
            if (!"signed".equals(company.getStatus())) {
                throw new BizException(403, "Sales can only access signed companies");
            }
        }
        return channelAllocationService.quota(companyId, excludeProjectId);
    }

    private void markActivatedIfNeeded(Project project) {
        if (project.getActivatedAt() == null) {
            project.setActivatedAt(LocalDateTime.now());
        }
    }

    private void validateKeywordGroupQuota(Project project) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        int quotaLimit = binding.getKeywordGroupLimit() == null ? 0 : binding.getKeywordGroupLimit();
        long activeUsed = keywordGroupService.countActiveProjectSavedKeywords(project.getCompanyId(), project.getId());
        long projectUsed = keywordGroupService.countSelectedSavedKeywords(project.getId());
        long totalUsed = activeUsed + projectUsed;
        if (totalUsed > quotaLimit) {
            throw new BizException(400, "KEYWORD_GROUP_QUOTA_EXCEEDED: 关键词组额度不足，套餐限制 "
                    + quotaLimit + " 条，当前已激活项目占用 " + activeUsed + " 条，本项目占用 "
                    + projectUsed + " 条");
        }
    }

    private String buildProjectCode() {
        return "PRJ" + System.currentTimeMillis() + RandomUtil.randomNumbers(4);
    }

    private void attachPlatformSelections(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
        List<ProjectPlatformBinding> bindings = projectPlatformBindingMapper.selectList(
                new LambdaQueryWrapper<ProjectPlatformBinding>()
                        .in(ProjectPlatformBinding::getProjectId, projectIds)
                        .orderByAsc(ProjectPlatformBinding::getPriorityLevel, ProjectPlatformBinding::getId)
        );
        Map<Long, List<String>> p0 = new LinkedHashMap<>();
        Map<Long, List<String>> p1 = new LinkedHashMap<>();
        Map<Long, List<String>> p2 = new LinkedHashMap<>();
        for (ProjectPlatformBinding binding : bindings) {
            Map<Long, List<String>> bucket;
            if ("P0".equals(binding.getPriorityLevel())) {
                bucket = p0;
            } else if ("P1".equals(binding.getPriorityLevel())) {
                bucket = p1;
            } else {
                bucket = p2;
            }
            bucket.computeIfAbsent(binding.getProjectId(), k -> new ArrayList<>()).add(binding.getPlatformCode());
        }
        for (Project project : projects) {
            project.setSelectedPlatformCodesP0(p0.getOrDefault(project.getId(), List.of()));
            project.setSelectedPlatformCodesP1(p1.getOrDefault(project.getId(), List.of()));
            project.setSelectedPlatformCodesP2(p2.getOrDefault(project.getId(), List.of()));
        }
    }

    private void replacePlatformSelections(Long projectId,
                                           Integer requiredP0,
                                           Integer requiredP1,
                                           Integer requiredP2,
                                           List<String> selectedP0,
                                           List<String> selectedP1,
                                           List<String> selectedP2) {
        List<String> normalizedP0 = normalizePlatformCodes(selectedP0);
        List<String> normalizedP1 = normalizePlatformCodes(selectedP1);
        List<String> normalizedP2 = normalizePlatformCodes(selectedP2);

        int expectP0 = requiredP0 == null ? 0 : requiredP0;
        int expectP1 = requiredP1 == null ? 0 : requiredP1;
        int expectP2 = requiredP2 == null ? 0 : requiredP2;

        if (normalizedP0.size() != expectP0) {
            throw new BizException(400, "P0 platform count must be exactly " + expectP0);
        }
        if (normalizedP1.size() != expectP1) {
            throw new BizException(400, "P1 platform count must be exactly " + expectP1);
        }
        if (normalizedP2.size() != expectP2) {
            throw new BizException(400, "P2 platform count must be exactly " + expectP2);
        }

        Set<String> allCodes = new HashSet<>();
        allCodes.addAll(normalizedP0);
        allCodes.addAll(normalizedP1);
        allCodes.addAll(normalizedP2);
        int totalSelected = normalizedP0.size() + normalizedP1.size() + normalizedP2.size();
        if (allCodes.size() != totalSelected) {
            throw new BizException(400, "Selected platforms cannot duplicate across P0/P1/P2");
        }

        List<AiPlatformConfig> configs = allCodes.isEmpty() ? List.of() : aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, allCodes)
                        .eq(AiPlatformConfig::getEnabled, true)
        );
        Map<String, AiPlatformConfig> configMap = configs.stream().collect(
                Collectors.toMap(AiPlatformConfig::getPlatformCode, c -> c, (a, b) -> a, LinkedHashMap::new)
        );
        for (String code : normalizedP0) {
            validatePlatformPriority(configMap.get(code), "P0");
        }
        for (String code : normalizedP1) {
            validatePlatformPriority(configMap.get(code), "P1");
        }
        for (String code : normalizedP2) {
            validatePlatformPriority(configMap.get(code), "P2");
        }

        projectPlatformBindingMapper.delete(
                new LambdaQueryWrapper<ProjectPlatformBinding>().eq(ProjectPlatformBinding::getProjectId, projectId)
        );
        savePlatformBindings(projectId, normalizedP0, "P0", configMap);
        savePlatformBindings(projectId, normalizedP1, "P1", configMap);
        savePlatformBindings(projectId, normalizedP2, "P2", configMap);
    }

    private void savePlatformBindings(Long projectId, List<String> codes, String priorityLevel, Map<String, AiPlatformConfig> configMap) {
        for (String code : codes) {
            AiPlatformConfig cfg = configMap.get(code);
            ProjectPlatformBinding binding = new ProjectPlatformBinding();
            binding.setProjectId(projectId);
            binding.setPlatformCode(code);
            binding.setPlatformName(cfg.getPlatformName());
            binding.setPriorityLevel(priorityLevel);
            projectPlatformBindingMapper.insert(binding);
        }
    }

    private void replaceKeywordGroupSelections(Long projectId, Long companyId, List<Long> selectedKeywordGroupIds) {
        List<Long> normalizedIds = normalizeKeywordGroupIds(selectedKeywordGroupIds);
        if (normalizedIds.isEmpty()) {
            throw new BizException(400, "At least one keyword group is required");
        }
        if (normalizedIds.size() > 10) {
            throw new BizException(400, "Keyword group count must be <= 10");
        }

        List<KeywordGroup> groups = keywordGroupMapper.selectList(
                new LambdaQueryWrapper<KeywordGroup>()
                        .in(KeywordGroup::getId, normalizedIds)
                        .eq(KeywordGroup::getCompanyId, companyId)
        );
        if (groups.size() != normalizedIds.size()) {
            throw new BizException(400, "Selected keyword groups must belong to project company");
        }

        projectKeywordGroupRelMapper.delete(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
        );
        for (Long groupId : normalizedIds) {
            ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
            rel.setProjectId(projectId);
            rel.setKeywordGroupId(groupId);
            projectKeywordGroupRelMapper.insert(rel);
        }
    }

    private void validatePlatformPriority(AiPlatformConfig cfg, String priorityLevel) {
        if (cfg == null) {
            throw new BizException(400, "Selected platform is invalid or disabled");
        }
        if (!priorityLevel.equals(cfg.getPriorityLevel())) {
            throw new BizException(400, "Platform " + cfg.getPlatformCode() + " does not belong to " + priorityLevel);
        }
    }

    private List<String> normalizePlatformCodes(List<String> selectedCodes) {
        if (selectedCodes == null) {
            return List.of();
        }
        return selectedCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
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
        Map<Long, Long> savedCountMap = keywordGroupService.calcSavedCountsByGroupIds(new ArrayList<>(allGroupIds));
        for (Project project : projects) {
            List<Long> groupIds = projectGroupIdMap.getOrDefault(project.getId(), List.of());
            long totalSaved = 0L;
            for (Long groupId : groupIds) {
                totalSaved += savedCountMap.getOrDefault(groupId, 0L);
            }
            project.setSelectedKeywordGroupIds(groupIds);
            project.setSelectedKeywordGroupCount(groupIds.size());
            project.setSelectedKeywordSavedKeywords(totalSaved);
        }
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
        snapshot.put("targetAudience", project.getTargetAudience());
        snapshot.put("customStatement", project.getCustomStatement());
        snapshot.put("contentTone", project.getContentTone());
        snapshot.put("preferredAngles", project.getPreferredAngles());
        snapshot.put("extraForbiddenPhrases", project.getExtraForbiddenPhrases());
        snapshot.put("contentNote", project.getContentNote());
        snapshot.put("status", project.getStatus());
        snapshot.put("stage", project.getStage());
        snapshot.put("activatedAt", project.getActivatedAt());
        snapshot.put("biweeklyAnchorDate", project.getBiweeklyAnchorDate());
        snapshot.put("expiredAt", project.getExpiredAt());
        snapshot.put("planKeywordGroupLimit", project.getPlanKeywordGroupLimit());
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
                                            String targetAudience,
                                            String customStatement,
                                            String contentTone,
                                            List<String> preferredAngles,
                                            List<String> extraForbiddenPhrases,
                                            String contentNote) {
        project.setTargetRegions(normalizeJsonStringList(targetRegions));
        project.setTargetAudience(trimToNull(targetAudience));
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
}
