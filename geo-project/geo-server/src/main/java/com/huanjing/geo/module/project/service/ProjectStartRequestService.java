package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.dto.PartnerChannelQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectStartRequestVO;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.project.dto.AdminProjectStartRequestVO;
import com.huanjing.geo.module.project.dto.PartnerSubmissionReadinessItemVO;
import com.huanjing.geo.module.project.dto.PartnerSubmissionReadinessVO;
import com.huanjing.geo.module.project.dto.ProjectSetupReadyRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestApproveRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestRejectRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestSubmitRequest;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.entity.ProjectQuotaSnapshot;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.project.mapper.ProjectQuotaSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectStartRequestMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectStartRequestService {

    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String SNAPSHOT_STATUS_SUBMITTED = "submitted";
    private static final String SNAPSHOT_STATUS_LOCKED = "locked";
    private static final String SNAPSHOT_STATUS_RELEASED = "released";
    private static final String FIRST_ORDER_BIZ_TYPE = "partner_project_first_order";
    private static final DateTimeFormatter REQUEST_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String BRAND_IMAGE_CATEGORY = "brand_image";
    private static final String COVER_FOLDER_NAME = "封面";
    private static final String ILLUSTRATION_FOLDER_PREFIX = "插图";
    private static final String PARTNER_WORKFLOW_ENTRY_COMPLETED = "entry_completed";
    private static final String PARTNER_WORKFLOW_SUBMITTED_TO_HQ = "submitted_to_hq";
    private static final int BRAND_IMAGE_FOLDER_MAX_COUNT = 80;

    private final ProjectStartRequestMapper requestMapper;
    private final ProjectQuotaSnapshotMapper quotaSnapshotMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final BrandImageFolderMapper brandImageFolderMapper;
    private final BrandMaterialMapper brandMaterialMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final BrowserEnvironmentAccountMapper browserEnvironmentAccountMapper;
    private final CompanyMapper companyMapper;
    private final CompanyPackageBindingMapper bindingMapper;
    private final PackagePlanMapper packagePlanMapper;
    private final ProjectChannelAllocationMapper projectChannelAllocationMapper;
    private final KeywordGroupMapper keywordGroupMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final SysUserMapper sysUserMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;
    private final ProjectDisplayStatusResolver displayStatusResolver;
    private final KeywordGroupService keywordGroupService;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ActivityLogService activityLogService;
    private final SystemAlertService systemAlertService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public PartnerProjectStartRequestVO submit(Long projectId, ProjectStartRequestSubmitRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        ensurePartnerOwner(operator);
        Project project = requireProject(projectId);
        ensureProjectOwnedByPartner(project, operator);
        ensureProjectSubmittable(project);
        validatePartnerSubmissionMaterials(project);

        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        PackagePlan plan = requirePartnerPackagePlan(binding);
        BigDecimal discountRate = resolveDiscountRate(project.getPartnerId());
        BigDecimal pointsRequired = resolveFirstOrderPoints(project, binding, plan, discountRate);

        channelAllocationService.validateActivation(project);
        String partnerAllocatedQuotaJson = channelAllocationService.partnerVisibleAllocationSnapshot(project);
        String requestNo = normalizeRequestNo(req == null ? null : req.getRequestId());

        ProjectStartRequest request = new ProjectStartRequest();
        request.setProjectId(project.getId());
        request.setCompanyId(project.getCompanyId());
        request.setPartnerId(project.getPartnerId());
        request.setApplicantUserId(operator.getId());
        request.setStatus(STATUS_SUBMITTED);
        request.setRequestNo(requestNo);
        request.setSubmittedAt(LocalDateTime.now());
        request.setPointsRequiredSnapshot(pointsRequired);
        request.setDiscountRateSnapshot(discountRate);
        request.setPackageSnapshotJson(binding.getPackageSnapshotJson());
        request.setPartnerAllocatedQuotaJson(partnerAllocatedQuotaJson);
        request.setInternalDeliverySnapshotJson(null);

        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException ex) {
            ProjectStartRequest existing = requestMapper.selectByRequestNo(requestNo);
            if (existing != null
                    && Objects.equals(existing.getProjectId(), project.getId())
                    && Objects.equals(existing.getPartnerId(), project.getPartnerId())
                    && STATUS_SUBMITTED.equals(existing.getStatus())) {
                markCompanySubmittedToHq(project.getCompanyId());
                return toPartnerVO(project, existing);
            }
            throw new BizException(409, "项目已有待审批申请", 409,
                    Map.of("errorCode", "PROJECT_START_REQUEST_SUBMITTED"), ex);
        }

        insertQuotaSnapshot(project, request, partnerAllocatedQuotaJson);
        updateProjectStatusFromSubmittable(project.getId(), ProjectFlowPolicy.SUBMITTED);
        markCompanySubmittedToHq(project.getCompanyId());
        project.setStatus(ProjectFlowPolicy.SUBMITTED);

        activityLogService.logAction(
                operator.getId(),
                "project.start_request.submit",
                "project_start_request",
                request.getId(),
                null,
                Map.of("status", request.getStatus(), "projectId", project.getId()),
                Map.of(
                        "companyId", project.getCompanyId(),
                        "partnerId", project.getPartnerId(),
                        "remark", Objects.toString(trimToNull(req == null ? null : req.getRemark()), "")
                )
        );
        notifyInternalReviewers(request, project);
        return toPartnerVO(project, request);
    }

    @Transactional
    public PartnerProjectStartRequestVO cancel(Long projectId, Long requestId) {
        SysUser operator = currentUserService.requireCurrentUser();
        ensurePartnerOwner(operator);
        Project project = requireProject(projectId);
        ensureProjectOwnedByPartner(project, operator);
        ProjectStartRequest request = requestMapper.selectById(requestId);
        if (request == null || !Objects.equals(request.getProjectId(), project.getId())) {
            throw new BizException(404, "Start request not found");
        }
        if (!STATUS_SUBMITTED.equals(request.getStatus())) {
            throw new BizException(400, "Only submitted start request can be cancelled");
        }

        int updated = requestMapper.update(null, new UpdateWrapper<ProjectStartRequest>()
                .eq("id", request.getId())
                .eq("project_id", project.getId())
                .eq("status", STATUS_SUBMITTED)
                .set("status", STATUS_CANCELLED));
        if (updated == 0) {
            throw new BizException(409, "Start request status changed, please refresh and retry");
        }
        releaseQuotaSnapshot(request.getId());
        updateProjectStatusFrom(project.getId(), ProjectFlowPolicy.SUBMITTED, ProjectFlowPolicy.DRAFT);
        request.setStatus(STATUS_CANCELLED);
        project.setStatus(ProjectFlowPolicy.DRAFT);

        activityLogService.logAction(
                operator.getId(),
                "project.start_request.cancel",
                "project_start_request",
                request.getId(),
                Map.of("status", STATUS_SUBMITTED),
                Map.of("status", STATUS_CANCELLED),
                Map.of("projectId", project.getId(), "companyId", project.getCompanyId(), "partnerId", project.getPartnerId())
        );
        return toPartnerVO(project, request);
    }

    @Transactional
    public AdminProjectStartRequestVO approve(Long requestId, ProjectStartRequestApproveRequest req) {
        currentUserService.ensurePermission("delivery.assignment.manage");
        SysUser operator = currentUserService.requireCurrentUser();
        ProjectStartRequest request = requireStartRequest(requestId);
        if (!STATUS_SUBMITTED.equals(request.getStatus())) {
            throw new BizException(400, "Only submitted start request can be approved");
        }
        Project project = requireProject(request.getProjectId());
        channelAllocationService.lockCompany(project.getCompanyId());
        Company company = requireCompany(project.getCompanyId());
        CompanyPackageBinding binding = requireActiveBindingAfterLock(project.getCompanyId());
        SysUser owner = resolveInternalOwner(company, req == null ? null : req.getAssignedInternalOwnerId());
        String internalDeliverySnapshotJson = binding.getInternalDeliverySnapshotJson();
        LocalDateTime now = LocalDateTime.now();

        updateRequestForApproval(request, operator.getId(), owner.getId(), internalDeliverySnapshotJson, now);
        PartnerAccountTxn pointsTxn = null;
        boolean firstOrder = binding.getLockedAt() == null;
        if (firstOrder) {
            pointsTxn = deductFirstOrderPoints(request, operator.getId());
        }

        updateProjectApprovedFromSubmitted(project.getId(), request, pointsTxn);
        lockQuotaSnapshot(request.getId(), internalDeliverySnapshotJson, now);
        if (firstOrder) {
            lockCompanyPackageBinding(binding, project.getId(), request.getId(), now);
        }
        assignCompanyOwnerAfterApproval(company, owner);

        request.setStatus(STATUS_APPROVED);
        request.setReviewedBy(operator.getId());
        request.setReviewedAt(now);
        request.setAssignedInternalOwnerId(owner.getId());
        request.setInternalDeliverySnapshotJson(internalDeliverySnapshotJson);
        project.setStatus(ProjectFlowPolicy.APPROVED_PENDING_SETUP);
        project.setDiscountRateSnapshot(request.getDiscountRateSnapshot());
        project.setDeductionAmount(pointsTxn == null ? BigDecimal.ZERO : pointsTxn.getAmount().abs());
        project.setDeductionTxnNo(pointsTxn == null ? null : pointsTxn.getTxnNo());

        activityLogService.logAction(
                operator.getId(),
                "project.start_request.approve",
                "project_start_request",
                request.getId(),
                Map.of("status", STATUS_SUBMITTED),
                Map.of("status", STATUS_APPROVED, "projectStatus", ProjectFlowPolicy.APPROVED_PENDING_SETUP),
                Map.of(
                        "projectId", project.getId(),
                        "companyId", project.getCompanyId(),
                        "partnerId", project.getPartnerId(),
                        "assignedInternalOwnerId", owner.getId(),
                        "pointsTxnId", pointsTxn == null ? "" : pointsTxn.getId(),
                        "reviewRemark", Objects.toString(trimToNull(req == null ? null : req.getReviewRemark()), "")
                )
        );
        notifyAssignedOwner(request, project, owner);
        return toAdminVO(project, request, quotaSnapshotMapper.selectLatestByStartRequestId(request.getId()), pointsTxn);
    }

    @Transactional
    public AdminProjectStartRequestVO reject(Long requestId, ProjectStartRequestRejectRequest req) {
        currentUserService.ensurePermission("delivery.assignment.manage");
        SysUser operator = currentUserService.requireCurrentUser();
        ProjectStartRequest request = requireStartRequest(requestId);
        if (!STATUS_SUBMITTED.equals(request.getStatus())) {
            throw new BizException(400, "Only submitted start request can be rejected");
        }
        Project project = requireProject(request.getProjectId());
        LocalDateTime now = LocalDateTime.now();

        updateRequestForRejection(request, operator.getId(), req, now);
        releaseSubmittedQuotaSnapshot(request.getId(), now);
        updateProjectStatusFrom(project.getId(), ProjectFlowPolicy.SUBMITTED, ProjectFlowPolicy.REJECTED);
        returnCompanyToPartnerOwnerReview(request.getCompanyId(), now);

        request.setStatus(STATUS_REJECTED);
        request.setReviewedBy(operator.getId());
        request.setReviewedAt(now);
        request.setRejectReasonCode(trimToNull(req == null ? null : req.getRejectReasonCode()));
        request.setRejectReasonText(trimToNull(req == null ? null : req.getRejectReasonText()));
        project.setStatus(ProjectFlowPolicy.REJECTED);

        activityLogService.logAction(
                operator.getId(),
                "project.start_request.reject",
                "project_start_request",
                request.getId(),
                Map.of("status", STATUS_SUBMITTED),
                Map.of("status", STATUS_REJECTED, "projectStatus", ProjectFlowPolicy.REJECTED),
                Map.of(
                        "projectId", project.getId(),
                        "companyId", project.getCompanyId(),
                        "partnerId", project.getPartnerId(),
                        "rejectReasonCode", Objects.toString(request.getRejectReasonCode(), "")
                )
        );
        notifyPartnerRejected(request, project, operator);
        return toAdminVO(project, request, quotaSnapshotMapper.selectLatestByStartRequestId(request.getId()), null);
    }

    @Transactional
    public AdminProjectStartRequestVO markSetupReady(Long requestId, ProjectSetupReadyRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        ProjectStartRequest request = requireStartRequest(requestId);
        if (!STATUS_APPROVED.equals(request.getStatus())) {
            throw new BizException(400, "Only approved start request can be marked setup ready");
        }
        if (request.getAssignedInternalOwnerId() == null) {
            throw new BizException(400, "Assigned internal owner is required before setup ready");
        }
        Project project = requireProject(request.getProjectId());
        ensureSetupReadyOperator(operator, project, request);
        validateProjectSetupReadiness(project);

        int updated = projectMapper.update(null, new UpdateWrapper<Project>()
                .eq("id", project.getId())
                .eq("status", ProjectFlowPolicy.APPROVED_PENDING_SETUP)
                .set("status", ProjectFlowPolicy.SETUP_READY));
        if (updated == 0) {
            throw new BizException(409, "Project status changed, please refresh and retry");
        }
        project.setStatus(ProjectFlowPolicy.SETUP_READY);

        activityLogService.logAction(
                operator.getId(),
                "project.start_request.setup_ready",
                "project_start_request",
                request.getId(),
                Map.of("projectStatus", ProjectFlowPolicy.APPROVED_PENDING_SETUP),
                Map.of("projectStatus", ProjectFlowPolicy.SETUP_READY),
                Map.of(
                        "projectId", project.getId(),
                        "companyId", project.getCompanyId(),
                        "partnerId", project.getPartnerId(),
                        "assignedInternalOwnerId", request.getAssignedInternalOwnerId(),
                        "remark", Objects.toString(trimToNull(req == null ? null : req.getRemark()), "")
                )
        );
        return toAdminVO(project, request, quotaSnapshotMapper.selectLatestByStartRequestId(request.getId()),
                partnerAccountTxnMapper.selectByBizTypeAndProjectId(FIRST_ORDER_BIZ_TYPE, project.getId()));
    }

    public Page<AdminProjectStartRequestVO> adminPage(long current,
                                                      long size,
                                                      String status,
                                                      Long partnerId,
                                                      Long companyId,
                                                      Long projectId) {
        currentUserService.ensurePermission("delivery.assignment.manage");
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<ProjectStartRequest> wrapper = new LambdaQueryWrapper<ProjectStartRequest>()
                .eq(StringUtils.hasText(status), ProjectStartRequest::getStatus, trimToNull(status))
                .eq(partnerId != null, ProjectStartRequest::getPartnerId, partnerId)
                .eq(companyId != null, ProjectStartRequest::getCompanyId, companyId)
                .eq(projectId != null, ProjectStartRequest::getProjectId, projectId)
                .orderByDesc(ProjectStartRequest::getSubmittedAt)
                .orderByDesc(ProjectStartRequest::getId);
        Page<ProjectStartRequest> page = requestMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        Page<AdminProjectStartRequestVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toAdminVO).toList());
        return result;
    }

    public AdminProjectStartRequestVO adminDetail(Long requestId) {
        currentUserService.ensurePermission("delivery.assignment.manage");
        ProjectStartRequest request = requireStartRequest(requestId);
        return toAdminVO(request);
    }

    @Transactional
    public PartnerSubmissionReadinessVO partnerSubmissionReadiness(Long companyId) {
        SysUser operator = currentUserService.requireCurrentUser();
        Company company = requireCompany(companyId);
        if (!"partner".equals(company.getOwnerType()) || company.getPartnerId() == null) {
            throw new BizException(400, "仅合伙人客户需要提交前检查");
        }
        currentUserService.ensurePartnerResourceAccess(operator, company.getPartnerId(), "company");
        internalScopeService.ensureCompanyAccess(operator, company, "company");

        PartnerSubmissionReadinessVO vo = new PartnerSubmissionReadinessVO();
        vo.setCompanyId(company.getId());
        vo.setCompanyName(company.getCompanyName());
        vo.setCheckedAt(LocalDateTime.now());

        List<PartnerSubmissionReadinessItemVO> items = new ArrayList<>();
        items.add(checkItem("company.basic", "客户资料", "客户基础资料", "客户名称、行业、联系人、联系电话需完整", null,
                () -> {
                    requireText(company.getCompanyName(), "请先补齐客户名称");
                    requireText(company.getIndustry(), "请先补齐客户行业");
                    requireText(company.getContactName(), "请先补齐客户联系人");
                    requireText(company.getContactPhone(), "请先补齐客户联系电话");
                }));
        items.add(checkItem("company.package", "客户套餐", "合伙人套餐", "客户需绑定有效合伙人套餐", null,
                () -> {
                    CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(company.getId());
                    requirePartnerPackagePlan(binding);
                }));

        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getCompanyId, company.getId())
                .eq(Project::getOwnerType, "partner")
                .isNull(Project::getDeletedAt)
                .orderByDesc(Project::getUpdatedAt)
                .orderByDesc(Project::getId));
        if (projects.isEmpty()) {
            items.add(notReadyItem("project.exists", "项目资料", "项目信息", "请先至少创建 1 个项目", "新增项目", null));
        }
        for (Project project : projects) {
            items.addAll(projectReadinessItems(project, company));
        }

        long readyCount = items.stream().filter(item -> Boolean.TRUE.equals(item.getReady())).count();
        vo.setItems(items);
        vo.setTotalCount(items.size());
        vo.setReadyCount((int) readyCount);
        vo.setPendingCount(items.size() - (int) readyCount);
        vo.setReady(!items.isEmpty() && readyCount == items.size());
        return vo;
    }

    public void ensurePartnerSubmissionReady(Long companyId) {
        PartnerSubmissionReadinessVO readiness = partnerSubmissionReadiness(companyId);
        if (Boolean.TRUE.equals(readiness.getReady())) {
            return;
        }
        String summary = readiness.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getReady()))
                .map(PartnerSubmissionReadinessItemVO::getTitle)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(java.util.stream.Collectors.joining("、"));
        throw new BizException(400, "提交前检查未通过，请先补齐：" + summary);
    }

    private void ensureSetupReadyOperator(SysUser operator, Project project, ProjectStartRequest request) {
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner users cannot mark project setup ready");
        }
        boolean canManageAssignment = currentUserService.hasPermission("delivery.assignment.manage");
        if (canManageAssignment) {
            return;
        }
        if (!Objects.equals(request.getAssignedInternalOwnerId(), operator.getId())) {
            throw new BizException(403, "Only assigned internal owner can mark project setup ready");
        }
        currentUserService.ensurePermission("project.update");
        internalScopeService.ensureProjectAccess(operator, project, "project");
    }

    private void validateProjectSetupReadiness(Project project) {
        if (project == null || project.getId() == null || project.getBrandId() == null) {
            throw new BizException(400, "项目信息不完整，无法标记配置完成");
        }
        List<ProjectChannelAllocation> allocations = projectChannelAllocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, project.getId())
                        .gt(ProjectChannelAllocation::getAllocatedCount, 0)
        );
        LinkedHashSet<String> requiredPlatforms = new LinkedHashSet<>();
        for (ProjectChannelAllocation allocation : allocations) {
            String platform = selfMediaPlatformFromChannel(allocation.getChannelCode());
            if (StringUtils.hasText(platform)) {
                requiredPlatforms.add(platform);
            }
        }
        if (requiredPlatforms.isEmpty()) {
            return;
        }

        List<Map<String, Object>> missingItems = new ArrayList<>();
        List<String> missingMessages = new ArrayList<>();
        for (String platform : requiredPlatforms) {
            SelfMediaAccount account = activeSelfMediaAccount(project.getBrandId(), platform);
            String label = selfMediaPlatformLabel(platform);
            if (account == null) {
                String message = label + "未配置启用的自媒体账号";
                missingMessages.add(message);
                missingItems.add(setupMissingItem("self_media_account", platform, label, message));
                continue;
            }
            BrowserEnvironmentAccount environmentAccount = browserEnvironmentAccountMapper.selectActiveBySelfMediaAccountId(account.getId());
            if (environmentAccount == null) {
                String message = label + "未绑定启用的指纹浏览器环境";
                missingMessages.add(message);
                missingItems.add(setupMissingItem("browser_environment", platform, label, message));
            }
        }
        if (!missingItems.isEmpty()) {
            throw new BizException(400, "项目启动配置未完成：" + String.join("；", missingMessages), 400,
                    Map.of("errorCode", "PROJECT_SETUP_NOT_READY", "missingItems", missingItems));
        }
    }

    private List<PartnerSubmissionReadinessItemVO> projectReadinessItems(Project project, Company company) {
        List<PartnerSubmissionReadinessItemVO> items = new ArrayList<>();
        String prefix = "project." + project.getId() + ".";
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        items.add(checkItem(prefix + "basic", "项目资料", "项目基础资料", "项目名称、目标区域、核心关键词、目标受众需完整",
                project, () -> validateProjectBasic(project)));
        items.add(checkItem(prefix + "brand", "品牌资料", "品牌基础资料", "品牌名称、简称、行业、主营业务需完整",
                project, () -> validateBrandBasic(brand, company)));
        items.add(checkItem(prefix + "competitor", "竞品信息", "项目竞品", "至少添加 1 个有效竞品信息",
                project, () -> validateProjectCompetitors(project.getId())));
        items.add(checkItem(prefix + "keywords", "核心问题", "项目核心问题", "核心问题组数量需等于项目分配额度",
                project, () -> validateProjectKeywordGroups(project)));
        items.add(checkItem(prefix + "channels", "展示渠道", "展示渠道额度", "项目展示渠道需有有效分配额度",
                project, () -> channelAllocationService.validateActivation(project)));
        items.add(checkItem(prefix + "images", "品牌资产", "品牌图片资产", "需有封面和插图文件夹，且每个要求文件夹至少 1 张图片",
                project, () -> {
                    if (brand == null) {
                        throw new BizException(400, "请先选择有效品牌");
                    }
                    validateBrandArticleImages(brand.getId());
                }));
        return items;
    }

    private void validateProjectBasic(Project project) {
        requireText(project.getProjectName(), "请先补齐项目名称");
        requireText(firstText(project.getTargetRegions(), project.getCityName(), project.getProvinceName()), "请先补齐项目目标区域");
        requireText(project.getCoreKeywords(), "请先补齐项目核心关键词");
        requireText(project.getTargetAudience(), "请先补齐项目目标受众");
    }

    private void validateBrandBasic(Brand brand, Company company) {
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(400, "请先选择有效品牌");
        }
        requireText(brand.getBrandName(), "请先补齐品牌名称");
        requireText(brand.getBrandShortName(), "请先补齐品牌简称/别名");
        requireText(firstText(brand.getIndustry(), company == null ? null : company.getIndustry()), "请先补齐品牌行业");
        requireText(firstText(brand.getMainBusiness(), brand.getBusinessIntro(), company == null ? null : company.getBusinessDirection()), "请先补齐品牌主营业务");
    }

    private PartnerSubmissionReadinessItemVO checkItem(String key,
                                                       String category,
                                                       String title,
                                                       String successDescription,
                                                       Project project,
                                                       Runnable checker) {
        PartnerSubmissionReadinessItemVO item = new PartnerSubmissionReadinessItemVO();
        item.setKey(key);
        item.setCategory(category);
        item.setTitle(project == null ? title : projectDisplayTitle(project, title));
        item.setReady(true);
        item.setSeverity("success");
        item.setDescription(successDescription);
        item.setActionText("已完成");
        if (project != null) {
            item.setProjectId(project.getId());
            item.setProjectName(projectDisplayName(project));
        }
        try {
            checker.run();
        } catch (BizException ex) {
            item.setReady(false);
            item.setSeverity("danger");
            item.setDescription(ex.getMessage());
            item.setActionText(actionText(category));
        } catch (RuntimeException ex) {
            item.setReady(false);
            item.setSeverity("danger");
            item.setDescription("检查失败，请刷新后重试");
            item.setActionText("刷新检查");
        }
        return item;
    }

    private PartnerSubmissionReadinessItemVO notReadyItem(String key,
                                                          String category,
                                                          String title,
                                                          String description,
                                                          String actionText,
                                                          Project project) {
        PartnerSubmissionReadinessItemVO item = new PartnerSubmissionReadinessItemVO();
        item.setKey(key);
        item.setCategory(category);
        item.setTitle(project == null ? title : projectDisplayTitle(project, title));
        item.setDescription(description);
        item.setReady(false);
        item.setSeverity("danger");
        item.setActionText(actionText);
        if (project != null) {
            item.setProjectId(project.getId());
            item.setProjectName(projectDisplayName(project));
        }
        return item;
    }

    private String projectDisplayTitle(Project project, String title) {
        return "项目「" + projectDisplayName(project) + "」" + title;
    }

    private String projectDisplayName(Project project) {
        if (project == null) {
            return "-";
        }
        return StringUtils.hasText(project.getProjectName()) ? project.getProjectName() : String.valueOf(project.getId());
    }

    private String actionText(String category) {
        return switch (category) {
            case "客户资料" -> "补充客户资料";
            case "客户套餐" -> "联系负责人绑定套餐";
            case "项目资料" -> "编辑项目";
            case "品牌资料", "品牌资产" -> "维护品牌资料";
            case "竞品信息" -> "补充竞品";
            case "核心问题" -> "进入拓词管理";
            case "展示渠道" -> "调整项目额度";
            default -> "去补充";
        };
    }

    private Map<String, Object> setupMissingItem(String type, String platform, String label, String message) {
        return Map.of(
                "type", type,
                "platform", Objects.toString(platform, ""),
                "label", Objects.toString(label, ""),
                "message", Objects.toString(message, "")
        );
    }

    private SelfMediaAccount activeSelfMediaAccount(Long brandId, String platform) {
        String publishPlatform = ArticlePromptChannels.normalizeSelfMediaPublishPlatform(platform);
        SelfMediaAccount account = activeSelfMediaAccountByStoredPlatform(brandId, publishPlatform);
        if (account != null) {
            return account;
        }
        return activeSelfMediaAccountByStoredPlatform(brandId, ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(platform));
    }

    private SelfMediaAccount activeSelfMediaAccountByStoredPlatform(Long brandId, String platform) {
        if (brandId == null || !StringUtils.hasText(platform)) {
            return null;
        }
        return selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, brandId)
                .eq(SelfMediaAccount::getPlatform, platform)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .orderByDesc(SelfMediaAccount::getUpdatedAt)
                .orderByDesc(SelfMediaAccount::getId)
                .last("LIMIT 1"));
    }

    private String selfMediaPlatformFromChannel(String channelCode) {
        String prefix = ArticlePromptChannels.SELF_MEDIA + ":";
        if (!StringUtils.hasText(channelCode) || !channelCode.startsWith(prefix)) {
            return null;
        }
        return ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(channelCode.substring(prefix.length()));
    }

    private String selfMediaPlatformLabel(String platform) {
        return ArticlePromptChannels.channelName(ArticlePromptChannels.SELF_MEDIA, platform);
    }

    private void ensurePartnerOwner(SysUser operator) {
        String role = operator == null || operator.getRole() == null
                ? ""
                : operator.getRole().trim().toLowerCase(Locale.ROOT);
        if (!"partner".equals(role)) {
            throw new BizException(403, "Only partner owner can submit project start request");
        }
        if (operator.getPartnerId() == null) {
            throw new BizException(403, "Partner account missing partner_id binding");
        }
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private ProjectStartRequest requireStartRequest(Long requestId) {
        ProjectStartRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BizException(404, "Start request not found");
        }
        return request;
    }

    private Company requireCompany(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private CompanyPackageBinding requireActiveBindingAfterLock(Long companyId) {
        CompanyPackageBinding binding = bindingMapper.selectActiveByCompanyId(companyId);
        if (binding == null) {
            throw new BizException(400, "Customer has no active package binding");
        }
        return binding;
    }

    private void ensureProjectOwnedByPartner(Project project, SysUser operator) {
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(operator, project, "project");
        if (!"partner".equals(project.getOwnerType())) {
            throw new BizException(400, "Only partner project can submit start request");
        }
    }

    private void ensureProjectSubmittable(Project project) {
        ProjectStartRequest latest = requestMapper.selectLatestByProjectId(project.getId());
        ProjectDisplayStatusResult display = displayStatusResolver.resolve(project, latest);
        if (!display.submittable()) {
            throw new BizException(400, "Project is not submittable in current status");
        }
    }

    private void markCompanySubmittedToHq(Long companyId) {
        if (companyId == null) {
            return;
        }
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            return;
        }
        if (PARTNER_WORKFLOW_SUBMITTED_TO_HQ.equals(company.getPartnerWorkflowStatus())) {
            return;
        }
        company.setPartnerWorkflowStatus(PARTNER_WORKFLOW_SUBMITTED_TO_HQ);
        company.setPartnerWorkflowUpdatedAt(LocalDateTime.now());
        companyMapper.updateById(company);
    }

    private void returnCompanyToPartnerOwnerReview(Long companyId, LocalDateTime updatedAt) {
        if (companyId == null) {
            return;
        }
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            return;
        }
        if (!PARTNER_WORKFLOW_SUBMITTED_TO_HQ.equals(company.getPartnerWorkflowStatus())) {
            return;
        }
        company.setPartnerWorkflowStatus(PARTNER_WORKFLOW_ENTRY_COMPLETED);
        company.setPartnerWorkflowUpdatedAt(updatedAt == null ? LocalDateTime.now() : updatedAt);
        companyMapper.updateById(company);
    }

    private void notifyInternalReviewers(ProjectStartRequest request, Project project) {
        Map<String, Object> context = notificationContext(request, project);
        String message = "合伙人提交了项目启动工单：" + Objects.toString(project.getProjectName(), request.getRequestNo());
        for (String role : List.of("delivery_manager", "manager", "super_admin")) {
            systemAlertService.createOrRefreshRecipientAlert(
                    "partner_project_start_request_submitted",
                    "warn",
                    "project_start_request",
                    message,
                    context,
                    null,
                    role,
                    "partner-start-request:submitted:" + request.getId() + ":" + role
            );
        }
    }

    private void notifyAssignedOwner(ProjectStartRequest request, Project project, SysUser owner) {
        if (owner == null || owner.getId() == null) {
            return;
        }
        systemAlertService.createOrRefreshRecipientAlert(
                "partner_project_start_request_approved",
                "info",
                "project_start_request",
                "合伙人项目已通过审批，请补齐启动配置：" + Objects.toString(project.getProjectName(), request.getRequestNo()),
                notificationContext(request, project),
                owner.getId(),
                null,
                "partner-start-request:approved:" + request.getId() + ":" + owner.getId()
        );
    }

    private void notifyPartnerRejected(ProjectStartRequest request, Project project, SysUser sender) {
        Map<String, Object> context = new HashMap<>(notificationContext(request, project));
        context.put("route", "/partner/my-projects");
        context.put("rejectReasonCode", Objects.toString(request.getRejectReasonCode(), ""));
        context.put("rejectReasonText", Objects.toString(request.getRejectReasonText(), ""));
        context.put("actionRoles", List.of("partner"));
        context.put("actionRoleText", "合伙人负责人");
        addSenderContext(context, sender);
        String message = "项目启动工单已驳回，请负责人查看原因后退回交付员工修改：" + Objects.toString(project.getProjectName(), request.getRequestNo());
        Set<Long> recipients = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPartnerId, request.getPartnerId())
                        .eq(SysUser::getRole, "partner")
                        .eq(SysUser::getIsActive, true))
                .stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (Long userId : recipients) {
            systemAlertService.createOrRefreshRecipientAlert(
                    "partner_project_start_request_rejected",
                    "warn",
                    "project_start_request",
                    message,
                    context,
                    userId,
                    null,
                    "partner-start-request:rejected:" + request.getId() + ":" + userId
            );
        }
    }

    private Map<String, Object> notificationContext(ProjectStartRequest request, Project project) {
        return Map.of(
                "requestId", request.getId(),
                "requestNo", Objects.toString(request.getRequestNo(), ""),
                "projectId", project.getId(),
                "projectName", Objects.toString(project.getProjectName(), ""),
                "companyId", request.getCompanyId(),
                "partnerId", request.getPartnerId()
        );
    }

    private void addSenderContext(Map<String, Object> context, SysUser sender) {
        if (context == null || sender == null) {
            return;
        }
        context.put("senderUserId", sender.getId());
        context.put("senderName", userName(sender));
        context.put("senderRole", Objects.toString(sender.getRole(), ""));
    }

    private void validatePartnerSubmissionMaterials(Project project) {
        Company company = requireCompany(project.getCompanyId());
        Brand brand = requireBrand(project.getBrandId());

        if (!PARTNER_WORKFLOW_ENTRY_COMPLETED.equals(company.getPartnerWorkflowStatus())) {
            throw new BizException(400, "交付资料已退回或尚未提交负责人确认，请等待交付员工补齐后重新提交负责人确认");
        }

        requireText(company.getCompanyName(), "请先补齐客户名称");
        requireText(company.getIndustry(), "请先补齐客户行业");
        requireText(company.getContactName(), "请先补齐客户联系人");
        requireText(company.getContactPhone(), "请先补齐客户联系电话");
        requireText(brand.getBrandName(), "请先补齐品牌名称");
        requireText(brand.getBrandShortName(), "请先补齐品牌简称/别名");
        requireText(firstText(brand.getIndustry(), company.getIndustry()), "请先补齐品牌行业");
        requireText(firstText(brand.getMainBusiness(), brand.getBusinessIntro(), company.getBusinessDirection()), "请先补齐品牌主营业务");
        requireText(project.getProjectName(), "请先补齐项目名称");
        requireText(firstText(project.getTargetRegions(), project.getCityName(), project.getProvinceName()), "请先补齐项目目标区域");
        requireText(project.getCoreKeywords(), "请先补齐项目核心关键词");
        requireText(project.getTargetAudience(), "请先补齐项目目标受众");

        validateProjectCompetitors(project.getId());
        validateProjectKeywordGroups(project);
        validateBrandArticleImages(brand.getId());
    }

    private Brand requireBrand(Long brandId) {
        Brand brand = brandId == null ? null : brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(400, "请先选择有效品牌");
        }
        return brand;
    }

    private void validateProjectCompetitors(Long projectId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM project_competitor_config
                 WHERE project_id = ?
                   AND status = 'active'
                   AND competitor_name IS NOT NULL
                   AND TRIM(competitor_name) <> ''
                """, Long.class, projectId);
        if (count == null || count <= 0) {
            throw new BizException(400, "请先至少添加 1 个竞品信息");
        }
    }

    private void validateProjectKeywordGroups(Project project) {
        int target = defaultInt(project.getPlanKeywordGroupLimit(), 0);
        if (target <= 0) {
            target = defaultInt(project.getPlanKeywordGroupLimitA(), 0)
                    + defaultInt(project.getPlanKeywordGroupLimitB(), 0)
                    + defaultInt(project.getPlanKeywordGroupLimitC(), 0);
        }
        if (target <= 0) {
            throw new BizException(400, "请先为项目分配核心问题额度");
        }
        List<Long> groupIds = selectedKeywordGroupIds(project);
        if (groupIds.isEmpty()) {
            throw new BizException(400, "请先在拓词管理中确认核心问题组");
        }
        long savedCount = keywordGroupService.calcSavedCountsByGroupIds(groupIds)
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
        if (savedCount != target) {
            throw new BizException(400, "核心问题数量未达项目分配额度：" + savedCount + " / " + target);
        }
    }

    private List<Long> selectedKeywordGroupIds(Project project) {
        List<Long> relIds = projectKeywordGroupRelMapper.selectList(
                        new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                                .eq(ProjectKeywordGroupRel::getProjectId, project.getId()))
                .stream()
                .map(ProjectKeywordGroupRel::getKeywordGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!relIds.isEmpty()) {
            return keywordGroupMapper.selectList(new LambdaQueryWrapper<KeywordGroup>()
                            .in(KeywordGroup::getId, relIds)
                            .eq(KeywordGroup::getCompanyId, project.getCompanyId())
                            .eq(KeywordGroup::getDeleted, false))
                    .stream()
                    .map(KeywordGroup::getId)
                    .toList();
        }
        return keywordGroupMapper.selectList(new LambdaQueryWrapper<KeywordGroup>()
                        .eq(KeywordGroup::getProjectId, project.getId())
                        .eq(KeywordGroup::getCompanyId, project.getCompanyId())
                        .eq(KeywordGroup::getDeleted, false))
                .stream()
                .map(KeywordGroup::getId)
                .toList();
    }

    private void validateBrandArticleImages(Long brandId) {
        List<BrandImageFolder> folders = brandImageFolderMapper.selectList(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .eq(BrandImageFolder::getStatus, "active"));
        List<Long> coverFolderIds = folders.stream()
                .filter(folder -> COVER_FOLDER_NAME.equals(trimToNull(folder.getFolderName())))
                .map(BrandImageFolder::getId)
                .toList();
        List<Long> illustrationFolderIds = folders.stream()
                .filter(folder -> StringUtils.hasText(folder.getFolderName())
                        && folder.getFolderName().trim().startsWith(ILLUSTRATION_FOLDER_PREFIX))
                .map(BrandImageFolder::getId)
                .toList();
        if (coverFolderIds.isEmpty() || illustrationFolderIds.isEmpty()) {
            throw new BizException(400, "品牌图片资产需保留启用的“封面”文件夹和至少一个“插图”开头的文件夹");
        }
        Map<Long, Long> counts = imageCountsByFolder(brandId, folders.stream().map(BrandImageFolder::getId).toList());
        for (BrandImageFolder folder : folders) {
            long count = counts.getOrDefault(folder.getId(), 0L);
            if (count > BRAND_IMAGE_FOLDER_MAX_COUNT) {
                throw new BizException(400, "图片文件夹“" + folder.getFolderName() + "”最多上传 " + BRAND_IMAGE_FOLDER_MAX_COUNT + " 张图片");
            }
        }
        long coverCount = coverFolderIds.stream().mapToLong(id -> counts.getOrDefault(id, 0L)).sum();
        long illustrationCount = illustrationFolderIds.stream().mapToLong(id -> counts.getOrDefault(id, 0L)).sum();
        if (coverCount <= 0) {
            throw new BizException(400, "品牌图片资产的“封面”文件夹中至少需要 1 张图片");
        }
        if (illustrationCount <= 0) {
            throw new BizException(400, "品牌图片资产的“插图”文件夹中至少需要 1 张图片");
        }
    }

    private Map<Long, Long> imageCountsByFolder(Long brandId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Map.of();
        }
        return brandMaterialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, BRAND_IMAGE_CATEGORY)
                        .in(BrandMaterial::getFolderId, folderIds))
                .stream()
                .filter(material -> material.getFolderId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        BrandMaterial::getFolderId,
                        java.util.stream.Collectors.counting()
                ));
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private PackagePlan requirePartnerPackagePlan(CompanyPackageBinding binding) {
        PackagePlan plan = packagePlanMapper.selectById(binding.getPackagePlanId());
        if (plan == null || plan.getDeletedAt() != null) {
            throw new BizException(400, "Bound package plan not found");
        }
        if (!PackagePlanService.AUDIENCE_PARTNER.equals(plan.getAudienceType())) {
            throw new BizException(400, "Customer must bind a partner package before project submission");
        }
        if (plan.getPartnerPoints() == null || plan.getPartnerPoints().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Partner package points are not configured");
        }
        return plan;
    }

    private BigDecimal resolveDiscountRate(Long partnerId) {
        Partner partner = partnerMapper.selectById(partnerId);
        if (partner == null) {
            throw new BizException(404, "Partner not found");
        }
        BigDecimal rate = partner.getDiscountRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return rate;
    }

    private BigDecimal resolveFirstOrderPoints(Project project,
                                               CompanyPackageBinding binding,
                                               PackagePlan plan,
                                               BigDecimal discountRate) {
        if (binding.getLockedAt() != null) {
            return BigDecimal.ZERO;
        }
        BigDecimal required = plan.getPartnerPoints()
                .multiply(discountRate)
                .setScale(2, RoundingMode.HALF_UP);
        PartnerAccount account = partnerAccountMapper.selectOne(
                new LambdaQueryWrapper<PartnerAccount>().eq(PartnerAccount::getPartnerId, project.getPartnerId())
        );
        BigDecimal balance = account == null || account.getCurrentBalance() == null
                ? BigDecimal.ZERO
                : account.getCurrentBalance();
        if (account == null || !"active".equals(account.getStatus()) || balance.compareTo(required) < 0) {
            throw new BizException(400, "Partner points are insufficient for first project approval");
        }
        return required;
    }

    private void insertQuotaSnapshot(Project project, ProjectStartRequest request, String partnerAllocatedQuotaJson) {
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setProjectId(project.getId());
        snapshot.setCompanyId(project.getCompanyId());
        snapshot.setStartRequestId(request.getId());
        snapshot.setStatus(SNAPSHOT_STATUS_SUBMITTED);
        snapshot.setPartnerAllocatedQuotaJson(partnerAllocatedQuotaJson);
        snapshot.setInternalDeliverySnapshotJson(null);
        quotaSnapshotMapper.insert(snapshot);
    }

    private void releaseQuotaSnapshot(Long requestId) {
        ProjectQuotaSnapshot snapshot = quotaSnapshotMapper.selectLatestByStartRequestId(requestId);
        if (snapshot == null || SNAPSHOT_STATUS_RELEASED.equals(snapshot.getStatus())) {
            return;
        }
        snapshot.setStatus(SNAPSHOT_STATUS_RELEASED);
        snapshot.setReleasedAt(LocalDateTime.now());
        quotaSnapshotMapper.updateById(snapshot);
    }

    private void updateRequestForApproval(ProjectStartRequest request,
                                          Long reviewerId,
                                          Long ownerId,
                                          String internalDeliverySnapshotJson,
                                          LocalDateTime reviewedAt) {
        int updated = requestMapper.update(null, new UpdateWrapper<ProjectStartRequest>()
                .eq("id", request.getId())
                .eq("status", STATUS_SUBMITTED)
                .set("status", STATUS_APPROVED)
                .set("reviewed_by", reviewerId)
                .set("reviewed_at", reviewedAt)
                .set("assigned_internal_owner_id", ownerId)
                .set("internal_delivery_snapshot_json", internalDeliverySnapshotJson));
        if (updated == 0) {
            throw new BizException(409, "Start request status changed, please refresh and retry");
        }
    }

    private void updateRequestForRejection(ProjectStartRequest request,
                                           Long reviewerId,
                                           ProjectStartRequestRejectRequest req,
                                           LocalDateTime reviewedAt) {
        int updated = requestMapper.update(null, new UpdateWrapper<ProjectStartRequest>()
                .eq("id", request.getId())
                .eq("status", STATUS_SUBMITTED)
                .set("status", STATUS_REJECTED)
                .set("reviewed_by", reviewerId)
                .set("reviewed_at", reviewedAt)
                .set("reject_reason_code", trimToNull(req == null ? null : req.getRejectReasonCode()))
                .set("reject_reason_text", trimToNull(req == null ? null : req.getRejectReasonText())));
        if (updated == 0) {
            throw new BizException(409, "Start request status changed, please refresh and retry");
        }
    }

    private void lockQuotaSnapshot(Long requestId, String internalDeliverySnapshotJson, LocalDateTime lockedAt) {
        int updated = quotaSnapshotMapper.update(null, new UpdateWrapper<ProjectQuotaSnapshot>()
                .eq("start_request_id", requestId)
                .eq("status", SNAPSHOT_STATUS_SUBMITTED)
                .set("status", SNAPSHOT_STATUS_LOCKED)
                .set("internal_delivery_snapshot_json", internalDeliverySnapshotJson)
                .set("locked_at", lockedAt));
        if (updated == 0) {
            throw new BizException(409, "Quota snapshot status changed, please refresh and retry");
        }
    }

    private void releaseSubmittedQuotaSnapshot(Long requestId, LocalDateTime releasedAt) {
        int updated = quotaSnapshotMapper.update(null, new UpdateWrapper<ProjectQuotaSnapshot>()
                .eq("start_request_id", requestId)
                .eq("status", SNAPSHOT_STATUS_SUBMITTED)
                .set("status", SNAPSHOT_STATUS_RELEASED)
                .set("released_at", releasedAt));
        if (updated == 0) {
            throw new BizException(409, "Quota snapshot status changed, please refresh and retry");
        }
    }

    private void lockCompanyPackageBinding(CompanyPackageBinding binding, Long projectId, Long approvalId, LocalDateTime lockedAt) {
        int updated = bindingMapper.update(null, new UpdateWrapper<CompanyPackageBinding>()
                .eq("id", binding.getId())
                .isNull("locked_at")
                .set("locked_at", lockedAt)
                .set("locked_by_project_id", projectId)
                .set("locked_by_approval_id", approvalId));
        if (updated == 0) {
            throw new BizException(409, "Customer package binding status changed, please refresh and retry");
        }
    }

    private void updateProjectStatusFromSubmittable(Long projectId, String targetStatus) {
        int updated = projectMapper.update(null, new UpdateWrapper<Project>()
                .eq("id", projectId)
                .in("status", List.of(ProjectFlowPolicy.DRAFT, ProjectFlowPolicy.PENDING_START, ProjectFlowPolicy.REJECTED))
                .set("status", targetStatus));
        if (updated == 0) {
            throw new BizException(409, "Project status changed, please refresh and retry");
        }
    }

    private void updateProjectStatusFrom(Long projectId, String expectedStatus, String targetStatus) {
        int updated = projectMapper.update(null, new UpdateWrapper<Project>()
                .eq("id", projectId)
                .eq("status", expectedStatus)
                .set("status", targetStatus));
        if (updated == 0) {
            throw new BizException(409, "Project status changed, please refresh and retry");
        }
    }

    private void updateProjectApprovedFromSubmitted(Long projectId, ProjectStartRequest request, PartnerAccountTxn pointsTxn) {
        int updated = projectMapper.update(null, new UpdateWrapper<Project>()
                .eq("id", projectId)
                .eq("status", ProjectFlowPolicy.SUBMITTED)
                .set("status", ProjectFlowPolicy.APPROVED_PENDING_SETUP)
                .set("discount_rate_snapshot", request.getDiscountRateSnapshot())
                .set("deduction_amount", pointsTxn == null ? BigDecimal.ZERO : pointsTxn.getAmount().abs())
                .set("deduction_txn_no", pointsTxn == null ? null : pointsTxn.getTxnNo()));
        if (updated == 0) {
            throw new BizException(409, "Project status changed, please refresh and retry");
        }
    }

    private SysUser resolveInternalOwner(Company company, Long requestedOwnerId) {
        if (company.getOwnerId() != null) {
            SysUser existingOwner = sysUserMapper.selectById(company.getOwnerId());
            if (existingOwner != null && Boolean.TRUE.equals(existingOwner.getIsActive()) && "operator".equals(existingOwner.getRole())) {
                if (requestedOwnerId != null && !requestedOwnerId.equals(company.getOwnerId())) {
                    throw new BizException(400, "Customer owner change must use owner transfer before approval");
                }
                return existingOwner;
            }
            if (!"partner".equals(company.getOwnerType())) {
                throw new BizException(400, "Customer owner is inactive, transfer customer owner before approval");
            }
        }
        if (requestedOwnerId == null) {
            throw new BizException(400, "Assigned internal owner is required");
        }
        SysUser owner = sysUserMapper.selectById(requestedOwnerId);
        if (owner == null || !Boolean.TRUE.equals(owner.getIsActive()) || !"operator".equals(owner.getRole())) {
            throw new BizException(400, "Assigned internal owner must be an active operator");
        }
        return owner;
    }

    private void assignCompanyOwnerAfterApproval(Company company, SysUser owner) {
        if (company.getOwnerId() != null && company.getOwnerId().equals(owner.getId())) {
            return;
        }
        company.setOwnerId(owner.getId());
        companyMapper.updateById(company);
    }

    private PartnerAccountTxn deductFirstOrderPoints(ProjectStartRequest request, Long operatorId) {
        if (request.getPointsRequiredSnapshot() == null
                || request.getPointsRequiredSnapshot().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        PartnerAccountTxn existing = partnerAccountTxnMapper.selectByBizTypeAndProjectId(FIRST_ORDER_BIZ_TYPE, request.getProjectId());
        if (existing != null) {
            return existing;
        }
        PartnerAccount account = partnerAccountMapper.selectByPartnerIdForUpdate(request.getPartnerId());
        BigDecimal before = account == null || account.getCurrentBalance() == null
                ? BigDecimal.ZERO
                : account.getCurrentBalance();
        BigDecimal amount = request.getPointsRequiredSnapshot().setScale(2, RoundingMode.HALF_UP);
        if (account == null || !"active".equals(account.getStatus()) || before.compareTo(amount) < 0) {
            throw new BizException(400, "Partner points are insufficient for first project approval");
        }
        BigDecimal after = before.subtract(amount);
        account.setCurrentBalance(after);
        account.setTotalDeduction((account.getTotalDeduction() == null ? BigDecimal.ZERO : account.getTotalDeduction()).add(amount));
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(request.getPartnerId());
        txn.setAccountId(account.getId());
        txn.setTxnNo(buildTxnNo());
        txn.setTxnType("deduct");
        txn.setBizType(FIRST_ORDER_BIZ_TYPE);
        txn.setAmount(amount.negate());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setRelatedProjectId(request.getProjectId());
        txn.setRelatedCompanyId(request.getCompanyId());
        txn.setRelatedStartRequestId(request.getId());
        txn.setPackageSnapshotJson(request.getPackageSnapshotJson());
        txn.setOperatorUserId(operatorId);
        txn.setRemark("Partner first project approval points, requestNo=" + request.getRequestNo());
        try {
            partnerAccountTxnMapper.insert(txn);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "Project points transaction already exists, please refresh and retry", 409,
                    Map.of("errorCode", "PARTNER_POINTS_TXN_DUPLICATED"), ex);
        }
        return txn;
    }

    private String buildTxnNo() {
        return "PTD" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private AdminProjectStartRequestVO toAdminVO(Project project,
                                                 ProjectStartRequest request,
                                                 ProjectQuotaSnapshot snapshot,
                                                 PartnerAccountTxn pointsTxn) {
        AdminProjectStartRequestVO vo = new AdminProjectStartRequestVO();
        vo.setId(request.getId());
        vo.setRequestNo(request.getRequestNo());
        vo.setStatus(request.getStatus());
        vo.setProjectId(request.getProjectId());
        vo.setProjectStatus(project == null ? null : project.getStatus());
        vo.setProjectDisplayStatus(project == null ? null : displayStatusResolver.resolveStatus(project, request));
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setBrandId(project == null ? null : project.getBrandId());
        vo.setBrandName(project == null ? null : project.getBrandName());
        vo.setCompanyId(request.getCompanyId());
        Company company = companyMapper.selectById(request.getCompanyId());
        vo.setCompanyName(company == null ? null : company.getCompanyName());
        SysUser defaultInternalOwner = company == null ? null : activeInternalOperator(company.getOwnerId());
        if (defaultInternalOwner != null) {
            vo.setDefaultInternalOwnerId(company.getOwnerId());
            vo.setDefaultInternalOwnerName(userName(defaultInternalOwner));
        }
        vo.setPartnerId(request.getPartnerId());
        Partner partner = partnerMapper.selectById(request.getPartnerId());
        vo.setPartnerName(partner == null ? null : partner.getPartnerName());
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setApplicantUserName(userName(request.getApplicantUserId()));
        vo.setSubmittedAt(request.getSubmittedAt());
        vo.setReviewedBy(request.getReviewedBy());
        vo.setReviewerName(userName(request.getReviewedBy()));
        vo.setReviewedAt(request.getReviewedAt());
        vo.setAssignedInternalOwnerId(request.getAssignedInternalOwnerId());
        vo.setAssignedInternalOwnerName(userName(request.getAssignedInternalOwnerId()));
        vo.setPointsRequiredSnapshot(request.getPointsRequiredSnapshot());
        vo.setDiscountRateSnapshot(request.getDiscountRateSnapshot());
        vo.setPackageSnapshotJson(request.getPackageSnapshotJson());
        vo.setPartnerAllocatedQuotaJson(request.getPartnerAllocatedQuotaJson());
        vo.setInternalDeliverySnapshotJson(request.getInternalDeliverySnapshotJson());
        vo.setRejectReasonCode(request.getRejectReasonCode());
        vo.setRejectReasonText(request.getRejectReasonText());
        if (snapshot != null) {
            vo.setQuotaSnapshotStatus(snapshot.getStatus());
            vo.setQuotaLockedAt(snapshot.getLockedAt());
            vo.setQuotaReleasedAt(snapshot.getReleasedAt());
        }
        if (pointsTxn != null) {
            vo.setPointsTxnId(pointsTxn.getId());
            vo.setPointsTxnNo(pointsTxn.getTxnNo());
            vo.setPointsTxnAmount(pointsTxn.getAmount());
        }
        vo.setCreatedAt(request.getCreatedAt());
        vo.setUpdatedAt(request.getUpdatedAt());
        return vo;
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return userName(user);
    }

    private String userName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
    }

    private SysUser activeInternalOperator(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive()) || !"operator".equals(user.getRole())) {
            return null;
        }
        return user;
    }

    private AdminProjectStartRequestVO toAdminVO(ProjectStartRequest request) {
        Project project = projectMapper.selectById(request.getProjectId());
        ProjectQuotaSnapshot snapshot = quotaSnapshotMapper.selectLatestByStartRequestId(request.getId());
        PartnerAccountTxn pointsTxn = partnerAccountTxnMapper.selectByBizTypeAndProjectId(FIRST_ORDER_BIZ_TYPE, request.getProjectId());
        return toAdminVO(project, request, snapshot, pointsTxn);
    }

    private PartnerProjectStartRequestVO toPartnerVO(Project project, ProjectStartRequest request) {
        PartnerProjectStartRequestVO vo = new PartnerProjectStartRequestVO();
        vo.setRequestId(request.getId());
        vo.setRequestNo(request.getRequestNo());
        vo.setStatus(request.getStatus());
        vo.setProjectDisplayStatus(displayStatusResolver.resolveStatus(project, request));
        vo.setPointsRequiredSnapshot(request.getPointsRequiredSnapshot());
        vo.setDiscountRateSnapshot(request.getDiscountRateSnapshot());
        vo.setPartnerAllocatedQuota(parsePartnerAllocatedQuota(request.getPartnerAllocatedQuotaJson()));
        vo.setSubmittedAt(request.getSubmittedAt());
        return vo;
    }

    private List<PartnerChannelQuotaVO> parsePartnerAllocatedQuota(String json) {
        if (!StringUtils.hasText(json) || !JSONUtil.isTypeJSONArray(json)) {
            return List.of();
        }
        JSONArray array = JSONUtil.parseArray(json);
        return array.stream().map(item -> {
            PartnerChannelQuotaVO vo = new PartnerChannelQuotaVO();
            try {
                PartnerChannelQuotaSnapshot snapshot = objectMapper.readValue(JSONUtil.toJsonStr(item), PartnerChannelQuotaSnapshot.class);
                vo.setChannelCode(snapshot.channelCode());
                vo.setChannelName(snapshot.channelName());
                vo.setPeriodType(snapshot.periodType());
                vo.setQuotaLimit(snapshot.quotaLimit());
                vo.setCurrentProjectAllocatedCount(snapshot.allocatedCount());
            } catch (JsonProcessingException ex) {
                throw new BizException(400, "Invalid partner allocated quota snapshot", ex);
            }
            return vo;
        }).toList();
    }

    private String normalizeRequestNo(String requestId) {
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }
        return "PSR" + LocalDateTime.now().format(REQUEST_NO_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record PartnerChannelQuotaSnapshot(String channelCode,
                                               String channelName,
                                               String periodType,
                                               Integer quotaLimit,
                                               Integer allocatedCount) {
    }
}
