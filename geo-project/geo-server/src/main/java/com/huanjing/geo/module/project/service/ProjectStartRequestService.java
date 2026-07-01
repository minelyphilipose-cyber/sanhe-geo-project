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
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.dto.PartnerChannelQuotaVO;
import com.huanjing.geo.module.partner.dto.PartnerProjectStartRequestVO;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.project.dto.AdminProjectStartRequestVO;
import com.huanjing.geo.module.project.dto.ProjectSetupReadyRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestApproveRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestRejectRequest;
import com.huanjing.geo.module.project.dto.ProjectStartRequestSubmitRequest;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectQuotaSnapshot;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectQuotaSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectStartRequestMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final ProjectStartRequestMapper requestMapper;
    private final ProjectQuotaSnapshotMapper quotaSnapshotMapper;
    private final ProjectMapper projectMapper;
    private final CompanyMapper companyMapper;
    private final CompanyPackageBindingMapper bindingMapper;
    private final PackagePlanMapper packagePlanMapper;
    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final SysUserMapper sysUserMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final ProjectDistributionChannelAllocationService channelAllocationService;
    private final ProjectDisplayStatusResolver displayStatusResolver;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PartnerProjectStartRequestVO submit(Long projectId, ProjectStartRequestSubmitRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        ensurePartnerOwner(operator);
        Project project = requireProject(projectId);
        ensureProjectOwnedByPartner(project, operator);
        ensureProjectSubmittable(project);

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
                return toPartnerVO(project, existing);
            }
            throw new BizException(409, "项目已有待审批申请", 409,
                    Map.of("errorCode", "PROJECT_START_REQUEST_SUBMITTED"), ex);
        }

        insertQuotaSnapshot(project, request, partnerAllocatedQuotaJson);
        updateProjectStatusFromSubmittable(project.getId(), ProjectFlowPolicy.SUBMITTED);
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
        assignInitialCompanyOwnerIfMissing(company, owner);

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
        if (!"partner".equals(project.getOwnerType()) && !"joint".equals(project.getOwnerType())) {
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
            if (requestedOwnerId != null && !requestedOwnerId.equals(company.getOwnerId())) {
                throw new BizException(400, "Customer owner change must use owner transfer before approval");
            }
            SysUser existingOwner = sysUserMapper.selectById(company.getOwnerId());
            if (existingOwner == null || !Boolean.TRUE.equals(existingOwner.getIsActive()) || !"operator".equals(existingOwner.getRole())) {
                throw new BizException(400, "Customer owner is inactive, transfer customer owner before approval");
            }
            return existingOwner;
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

    private void assignInitialCompanyOwnerIfMissing(Company company, SysUser owner) {
        if (company.getOwnerId() != null) {
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
        vo.setCompanyId(request.getCompanyId());
        vo.setPartnerId(request.getPartnerId());
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setSubmittedAt(request.getSubmittedAt());
        vo.setReviewedBy(request.getReviewedBy());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setAssignedInternalOwnerId(request.getAssignedInternalOwnerId());
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
