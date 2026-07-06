package com.huanjing.geo.module.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.partner.dto.PartnerProjectStartRequestVO;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
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
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.entity.ProjectQuotaSnapshot;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectQuotaSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectStartRequestMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStartRequestServiceTest {

    private ProjectStartRequestMapper requestMapper;
    private ProjectQuotaSnapshotMapper quotaSnapshotMapper;
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private BrandImageFolderMapper brandImageFolderMapper;
    private BrandMaterialMapper brandMaterialMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private BrowserEnvironmentAccountMapper browserEnvironmentAccountMapper;
    private CompanyMapper companyMapper;
    private CompanyPackageBindingMapper bindingMapper;
    private KeywordGroupMapper keywordGroupMapper;
    private ProjectChannelAllocationMapper projectChannelAllocationMapper;
    private ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private PartnerAccountMapper partnerAccountMapper;
    private PartnerAccountTxnMapper partnerAccountTxnMapper;
    private SysUserMapper sysUserMapper;
    private KeywordGroupService keywordGroupService;
    private JdbcTemplate jdbcTemplate;
    private ProjectDistributionChannelAllocationService channelAllocationService;
    private CurrentUserService currentUserService;
    private SystemAlertService systemAlertService;
    private ProjectStartRequestService service;

    @BeforeEach
    void setUp() {
        requestMapper = mock(ProjectStartRequestMapper.class);
        quotaSnapshotMapper = mock(ProjectQuotaSnapshotMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        brandImageFolderMapper = mock(BrandImageFolderMapper.class);
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        browserEnvironmentAccountMapper = mock(BrowserEnvironmentAccountMapper.class);
        companyMapper = mock(CompanyMapper.class);
        bindingMapper = mock(CompanyPackageBindingMapper.class);
        PackagePlanMapper packagePlanMapper = mock(PackagePlanMapper.class);
        projectChannelAllocationMapper = mock(ProjectChannelAllocationMapper.class);
        keywordGroupMapper = mock(KeywordGroupMapper.class);
        projectKeywordGroupRelMapper = mock(ProjectKeywordGroupRelMapper.class);
        PartnerMapper partnerMapper = mock(PartnerMapper.class);
        partnerAccountMapper = mock(PartnerAccountMapper.class);
        partnerAccountTxnMapper = mock(PartnerAccountTxnMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        CompanyPackageBindingService bindingService = mock(CompanyPackageBindingService.class);
        channelAllocationService = mock(ProjectDistributionChannelAllocationService.class);
        keywordGroupService = mock(KeywordGroupService.class);
        currentUserService = mock(CurrentUserService.class);
        InternalScopeService internalScopeService = mock(InternalScopeService.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        systemAlertService = mock(SystemAlertService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new ProjectStartRequestService(
                requestMapper,
                quotaSnapshotMapper,
                projectMapper,
                brandMapper,
                brandImageFolderMapper,
                brandMaterialMapper,
                selfMediaAccountMapper,
                browserEnvironmentAccountMapper,
                companyMapper,
                bindingMapper,
                packagePlanMapper,
                projectChannelAllocationMapper,
                keywordGroupMapper,
                projectKeywordGroupRelMapper,
                partnerMapper,
                partnerAccountMapper,
                partnerAccountTxnMapper,
                sysUserMapper,
                bindingService,
                channelAllocationService,
                new ProjectDisplayStatusResolver(),
                keywordGroupService,
                currentUserService,
                internalScopeService,
                activityLogService,
                systemAlertService,
                new ObjectMapper(),
                jdbcTemplate
        );

        when(projectMapper.selectById(10L)).thenReturn(project("pending_start"));
        when(brandMapper.selectById(60L)).thenReturn(brand());
        when(companyMapper.selectById(20L)).thenReturn(company());
        when(bindingMapper.selectActiveByCompanyId(20L)).thenReturn(binding());
        when(bindingService.requireActiveBinding(20L)).thenReturn(binding());
        when(packagePlanMapper.selectById(30L)).thenReturn(plan());
        when(partnerMapper.selectById(100L)).thenReturn(partner());
        when(partnerAccountMapper.selectOne(any())).thenReturn(account());
        when(partnerAccountMapper.selectByPartnerIdForUpdate(100L)).thenReturn(account());
        when(sysUserMapper.selectById(70L)).thenReturn(internalOwner());
        when(jdbcTemplate.queryForObject(any(String.class), any(Class.class), any())).thenReturn(1L);
        ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
        rel.setProjectId(10L);
        rel.setKeywordGroupId(500L);
        when(projectKeywordGroupRelMapper.selectList(any())).thenReturn(List.of(rel));
        KeywordGroup group = new KeywordGroup();
        group.setId(500L);
        group.setCompanyId(20L);
        group.setDeleted(false);
        when(keywordGroupMapper.selectList(any())).thenReturn(List.of(group));
        when(keywordGroupService.calcSavedCountsByGroupIds(any())).thenReturn(java.util.Map.of(500L, 3L));
        BrandImageFolder cover = imageFolder(901L, "封面");
        BrandImageFolder illustration = imageFolder(902L, "插图");
        when(brandImageFolderMapper.selectList(any())).thenReturn(List.of(cover, illustration));
        BrandMaterial coverImage = imageMaterial(901L);
        BrandMaterial illustrationImage = imageMaterial(902L);
        when(brandMaterialMapper.selectList(any())).thenReturn(List.of(coverImage, illustrationImage));
        when(projectChannelAllocationMapper.selectList(any())).thenReturn(List.of());
        when(channelAllocationService.partnerVisibleAllocationSnapshot(any()))
                .thenReturn("[{\"channelCode\":\"official_site\",\"channelName\":\"Agent官网\",\"periodType\":\"month\",\"quotaLimit\":10,\"allocatedCount\":3}]");
        doAnswer(invocation -> {
            ProjectStartRequest row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(requestMapper).insert(any(ProjectStartRequest.class));
        doAnswer(invocation -> {
            ProjectQuotaSnapshot row = invocation.getArgument(0);
            row.setId(199L);
            return 1;
        }).when(quotaSnapshotMapper).insert(any(ProjectQuotaSnapshot.class));
        when(projectMapper.update(any(), any())).thenReturn(1);
        when(bindingMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void partnerStaffCannotSubmitStartRequest() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner_staff"));

        BizException ex = assertThrows(BizException.class,
                () -> service.submit(10L, submitRequest()));

        assertEquals(403, ex.getCode());
        verify(requestMapper, never()).insert(any());
    }

    @Test
    void partnerOwnerCanSubmitOwnProject() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));

        PartnerProjectStartRequestVO vo = service.submit(10L, submitRequest());

        assertEquals(99L, vo.getRequestId());
        assertEquals("REQ-1", vo.getRequestNo());
        assertEquals("submitted", vo.getStatus());
        assertEquals("submitted", vo.getProjectDisplayStatus());
        assertEquals(new BigDecimal("80.00"), vo.getPointsRequiredSnapshot());
        assertEquals(1, vo.getPartnerAllocatedQuota().size());

        ArgumentCaptor<ProjectStartRequest> requestCaptor = ArgumentCaptor.forClass(ProjectStartRequest.class);
        verify(requestMapper).insert(requestCaptor.capture());
        assertEquals(10L, requestCaptor.getValue().getProjectId());
        assertEquals(100L, requestCaptor.getValue().getPartnerId());
        assertEquals("REQ-1", requestCaptor.getValue().getRequestNo());

        ArgumentCaptor<ProjectQuotaSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ProjectQuotaSnapshot.class);
        verify(quotaSnapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("submitted", snapshotCaptor.getValue().getStatus());
        assertNotNull(snapshotCaptor.getValue().getPartnerAllocatedQuotaJson());

        verify(projectMapper).update(any(), any());

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyMapper).updateById(companyCaptor.capture());
        assertEquals("submitted_to_hq", companyCaptor.getValue().getPartnerWorkflowStatus());
        assertNotNull(companyCaptor.getValue().getPartnerWorkflowUpdatedAt());
    }

    @Test
    void submitFailsWhenCompanyEntryWasReturned() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        Company company = company();
        company.setPartnerWorkflowStatus("project_entry");
        when(companyMapper.selectById(20L)).thenReturn(company);

        BizException ex = assertThrows(BizException.class, () -> service.submit(10L, submitRequest()));

        assertEquals(400, ex.getCode());
        assertEquals("交付资料已退回或尚未提交负责人确认，请等待交付员工补齐后重新提交负责人确认", ex.getMessage());
        verify(requestMapper, never()).insert(any(ProjectStartRequest.class));
        verify(quotaSnapshotMapper, never()).insert(any(ProjectQuotaSnapshot.class));
    }

    @Test
    void duplicateRequestNoReturnsExistingSubmittedRequestForSameProject() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        doThrow(new DuplicateKeyException("duplicate")).when(requestMapper).insert(any(ProjectStartRequest.class));
        ProjectStartRequest existing = new ProjectStartRequest();
        existing.setId(88L);
        existing.setProjectId(10L);
        existing.setPartnerId(100L);
        existing.setStatus("submitted");
        existing.setRequestNo("REQ-1");
        existing.setSubmittedAt(java.time.LocalDateTime.now());
        existing.setPartnerAllocatedQuotaJson("[]");
        when(requestMapper.selectByRequestNo("REQ-1")).thenReturn(existing);

        PartnerProjectStartRequestVO vo = service.submit(10L, submitRequest());

        assertEquals(88L, vo.getRequestId());
        assertEquals("submitted", vo.getStatus());
        verify(quotaSnapshotMapper, never()).insert(any());
        verify(projectMapper, never()).update(any(), any());
    }

    @Test
    void duplicateSubmittedProjectWithoutMatchingRequestNoBecomesBusinessError() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        doThrow(new DuplicateKeyException("duplicate")).when(requestMapper).insert(any(ProjectStartRequest.class));

        BizException ex = assertThrows(BizException.class, () -> service.submit(10L, submitRequest()));

        assertEquals(409, ex.getCode());
        assertEquals("项目已有待审批申请", ex.getMessage());
    }

    @Test
    void submitFailsWhenProjectStatusWasChangedConcurrently() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        when(projectMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.submit(10L, submitRequest()));

        assertEquals(409, ex.getCode());
        assertEquals("Project status changed, please refresh and retry", ex.getMessage());
        verify(requestMapper).insert(any(ProjectStartRequest.class));
        verify(quotaSnapshotMapper).insert(any(ProjectQuotaSnapshot.class));
    }

    @Test
    void cancelSubmittedRequestReleasesSnapshotAndReturnsProjectToDraft() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        ProjectStartRequest request = new ProjectStartRequest();
        request.setId(99L);
        request.setProjectId(10L);
        request.setPartnerId(100L);
        request.setStatus("submitted");
        request.setRequestNo("REQ-1");
        request.setSubmittedAt(java.time.LocalDateTime.now());
        request.setPartnerAllocatedQuotaJson("[]");
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(1);
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setId(199L);
        snapshot.setStatus("submitted");
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(snapshot);
        when(projectMapper.update(any(), any())).thenReturn(1);

        PartnerProjectStartRequestVO vo = service.cancel(10L, 99L);

        assertEquals("cancelled", vo.getStatus());
        assertEquals("draft", vo.getProjectDisplayStatus());
        assertEquals("released", snapshot.getStatus());
        assertNotNull(snapshot.getReleasedAt());
        verify(quotaSnapshotMapper).updateById(snapshot);
        verify(projectMapper).update(any(), any());
    }

    @Test
    void cancelFailsWhenSubmittedRequestWasChangedConcurrently() {
        when(currentUserService.requireCurrentUser()).thenReturn(user("partner"));
        ProjectStartRequest request = new ProjectStartRequest();
        request.setId(99L);
        request.setProjectId(10L);
        request.setPartnerId(100L);
        request.setStatus("submitted");
        request.setRequestNo("REQ-1");
        request.setSubmittedAt(java.time.LocalDateTime.now());
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.cancel(10L, 99L));

        assertEquals(409, ex.getCode());
        assertEquals("Start request status changed, please refresh and retry", ex.getMessage());
        verify(quotaSnapshotMapper, never()).selectLatestByStartRequestId(99L);
        verify(projectMapper, never()).update(any(), any());
    }

    @Test
    void approveSubmittedRequestLocksQuotaAndDeductsFirstOrderPoints() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        Project submittedProject = project("submitted");
        when(projectMapper.selectById(10L)).thenReturn(submittedProject);
        ProjectStartRequest request = submittedRequest();
        request.setPointsRequiredSnapshot(new BigDecimal("80.00"));
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(quotaSnapshotMapper.update(any(), any())).thenReturn(1);
        ProjectQuotaSnapshot locked = new ProjectQuotaSnapshot();
        locked.setStatus("locked");
        locked.setLockedAt(java.time.LocalDateTime.now());
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(locked);
        doAnswer(invocation -> {
            PartnerAccountTxn txn = invocation.getArgument(0);
            txn.setId(501L);
            return 1;
        }).when(partnerAccountTxnMapper).insert(any(PartnerAccountTxn.class));

        ProjectStartRequestApproveRequest req = new ProjectStartRequestApproveRequest();
        req.setAssignedInternalOwnerId(70L);
        AdminProjectStartRequestVO vo = service.approve(99L, req);

        assertEquals("approved", vo.getStatus());
        assertEquals("approved_pending_setup", vo.getProjectStatus());
        assertEquals("locked", vo.getQuotaSnapshotStatus());
        assertEquals(501L, vo.getPointsTxnId());
        verify(partnerAccountMapper).updateById(any(PartnerAccount.class));
        verify(partnerAccountTxnMapper).insert(any(PartnerAccountTxn.class));
        verify(bindingMapper).update(any(), any());
        verify(projectMapper, org.mockito.Mockito.atLeastOnce()).update(any(), any());

        ArgumentCaptor<PartnerAccountTxn> txnCaptor = ArgumentCaptor.forClass(PartnerAccountTxn.class);
        verify(partnerAccountTxnMapper).insert(txnCaptor.capture());
        assertEquals(20L, txnCaptor.getValue().getRelatedCompanyId());
        assertEquals(99L, txnCaptor.getValue().getRelatedStartRequestId());
        assertEquals("{\"packageName\":\"Partner Plan\"}", txnCaptor.getValue().getPackageSnapshotJson());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<Project>> updateCaptor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(projectMapper, org.mockito.Mockito.atLeastOnce()).update(any(), updateCaptor.capture());
        String projectUpdateSql = updateCaptor.getAllValues().stream()
                .filter(UpdateWrapper.class::isInstance)
                .map(UpdateWrapper.class::cast)
                .map(UpdateWrapper::getSqlSet)
                .filter(value -> value.contains("deduction_txn_no"))
                .findFirst()
                .orElse("");
        org.junit.jupiter.api.Assertions.assertTrue(projectUpdateSql.contains("deduction_txn_no"));
    }

    @Test
    void approveAssignsInternalOwnerWhenPartnerCompanyOwnerIsPartnerStaff() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        Company partnerCompany = company();
        partnerCompany.setOwnerType("partner");
        partnerCompany.setOwnerId(7L);
        when(companyMapper.selectById(20L)).thenReturn(partnerCompany);
        SysUser partnerStaff = user("partner_staff");
        partnerStaff.setId(7L);
        when(sysUserMapper.selectById(7L)).thenReturn(partnerStaff);
        ProjectStartRequest request = submittedRequest();
        request.setPointsRequiredSnapshot(BigDecimal.ZERO);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(quotaSnapshotMapper.update(any(), any())).thenReturn(1);
        ProjectQuotaSnapshot locked = new ProjectQuotaSnapshot();
        locked.setStatus("locked");
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(locked);

        ProjectStartRequestApproveRequest req = new ProjectStartRequestApproveRequest();
        req.setAssignedInternalOwnerId(70L);
        AdminProjectStartRequestVO vo = service.approve(99L, req);

        assertEquals("approved", vo.getStatus());
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyMapper).updateById(companyCaptor.capture());
        assertEquals(70L, companyCaptor.getValue().getOwnerId());
    }

    @Test
    void approveFailsWhenRequestWasChangedConcurrentlyBeforeDeductingPoints() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        ProjectStartRequest request = submittedRequest();
        request.setPointsRequiredSnapshot(new BigDecimal("80.00"));
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.approve(99L, new ProjectStartRequestApproveRequest()));

        assertEquals(409, ex.getCode());
        assertEquals("Start request status changed, please refresh and retry", ex.getMessage());
        verify(projectMapper, never()).update(any(), any());
        verify(partnerAccountTxnMapper, never()).insert(any());
    }

    @Test
    void approveRejectsChangingExistingCompanyOwner() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        when(requestMapper.selectById(99L)).thenReturn(submittedRequest());

        ProjectStartRequestApproveRequest req = new ProjectStartRequestApproveRequest();
        req.setAssignedInternalOwnerId(71L);
        BizException ex = assertThrows(BizException.class, () -> service.approve(99L, req));

        assertEquals(400, ex.getCode());
        assertEquals("Customer owner change must use owner transfer before approval", ex.getMessage());
        verify(requestMapper, never()).update(any(), any());
        verify(partnerAccountTxnMapper, never()).insert(any());
    }

    @Test
    void approveRejectsInactiveExistingCompanyOwnerEvenWhenNewOwnerProvided() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        when(requestMapper.selectById(99L)).thenReturn(submittedRequest());
        SysUser inactiveOwner = internalOwner();
        inactiveOwner.setIsActive(false);
        when(sysUserMapper.selectById(70L)).thenReturn(inactiveOwner);

        ProjectStartRequestApproveRequest req = new ProjectStartRequestApproveRequest();
        req.setAssignedInternalOwnerId(70L);
        BizException ex = assertThrows(BizException.class, () -> service.approve(99L, req));

        assertEquals(400, ex.getCode());
        assertEquals("Customer owner is inactive, transfer customer owner before approval", ex.getMessage());
        verify(requestMapper, never()).update(any(), any());
        verify(partnerAccountTxnMapper, never()).insert(any());
    }

    @Test
    void approveRollsBackWhenPointsTxnInsertConflictsAfterBalanceUpdate() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        ProjectStartRequest request = submittedRequest();
        request.setPointsRequiredSnapshot(new BigDecimal("80.00"));
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(requestMapper.update(any(), any())).thenReturn(1);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(partnerAccountTxnMapper)
                .insert(any(PartnerAccountTxn.class));

        BizException ex = assertThrows(BizException.class, () -> service.approve(99L, new ProjectStartRequestApproveRequest()));

        assertEquals(409, ex.getCode());
        assertEquals("Project points transaction already exists, please refresh and retry", ex.getMessage());
        verify(partnerAccountMapper).updateById(any(PartnerAccount.class));
        verify(projectMapper, never()).update(any(), any());
        verify(quotaSnapshotMapper, never()).update(any(), any());
    }

    @Test
    void rejectSubmittedRequestReleasesSnapshotAndMarksProjectRejected() {
        SysUser reviewer = user("delivery_manager");
        reviewer.setId(9L);
        reviewer.setPartnerId(null);
        when(currentUserService.requireCurrentUser()).thenReturn(reviewer);
        when(projectMapper.selectById(10L)).thenReturn(project("submitted"));
        Company submittedCompany = company();
        submittedCompany.setPartnerWorkflowStatus("submitted_to_hq");
        when(companyMapper.selectById(20L)).thenReturn(submittedCompany);
        when(requestMapper.selectById(99L)).thenReturn(submittedRequest());
        when(requestMapper.update(any(), any())).thenReturn(1);
        when(quotaSnapshotMapper.update(any(), any())).thenReturn(1);
        SysUser partnerOwner = user("partner");
        partnerOwner.setId(101L);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(partnerOwner));
        ProjectQuotaSnapshot released = new ProjectQuotaSnapshot();
        released.setStatus("released");
        released.setReleasedAt(java.time.LocalDateTime.now());
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(released);

        ProjectStartRequestRejectRequest req = new ProjectStartRequestRejectRequest();
        req.setRejectReasonCode("missing_account");
        req.setRejectReasonText("资料不足");
        AdminProjectStartRequestVO vo = service.reject(99L, req);

        assertEquals("rejected", vo.getStatus());
        assertEquals("rejected", vo.getProjectStatus());
        assertEquals("released", vo.getQuotaSnapshotStatus());
        assertEquals("missing_account", vo.getRejectReasonCode());
        assertEquals("entry_completed", submittedCompany.getPartnerWorkflowStatus());
        assertNotNull(submittedCompany.getPartnerWorkflowUpdatedAt());
        verify(companyMapper).updateById(submittedCompany);
        verify(partnerAccountTxnMapper, never()).insert(any());

        ArgumentCaptor<Map> contextCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        verify(systemAlertService).createOrRefreshRecipientAlert(
                any(),
                any(),
                any(),
                any(),
                contextCaptor.capture(),
                recipientCaptor.capture(),
                any(),
                any()
        );
        assertEquals(java.util.Set.of(101L), new java.util.HashSet<>(recipientCaptor.getAllValues()));
        for (Map context : contextCaptor.getAllValues()) {
            assertEquals("/partner/my-projects", context.get("route"));
            assertEquals("missing_account", context.get("rejectReasonCode"));
            assertEquals("资料不足", context.get("rejectReasonText"));
            assertEquals(List.of("partner"), context.get("actionRoles"));
            assertEquals("合伙人负责人", context.get("actionRoleText"));
        }
    }

    @Test
    void adminPageReturnsDisplayStatusSnapshotAndPointsTxn() {
        ProjectStartRequest request = submittedRequest();
        request.setId(99L);
        request.setCreatedAt(LocalDateTime.now());
        Page<ProjectStartRequest> raw = new Page<>(1, 20, 1);
        raw.setRecords(List.of(request));
        when(requestMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(raw);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setStatus("locked");
        snapshot.setLockedAt(LocalDateTime.now());
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(snapshot);
        PartnerAccountTxn txn = pointsTxn();
        when(partnerAccountTxnMapper.selectByBizTypeAndProjectId("partner_project_first_order", 10L)).thenReturn(txn);

        Page<AdminProjectStartRequestVO> page = service.adminPage(1, 20, "approved", 100L, 20L, 10L);

        assertEquals(1, page.getTotal());
        AdminProjectStartRequestVO vo = page.getRecords().get(0);
        assertEquals("approved_pending_setup", vo.getProjectStatus());
        assertEquals("approved_pending_setup", vo.getProjectDisplayStatus());
        assertEquals("locked", vo.getQuotaSnapshotStatus());
        assertEquals(501L, vo.getPointsTxnId());
        assertEquals(new BigDecimal("-80.00"), vo.getPointsTxnAmount());
    }

    @Test
    void adminDetailReturnsRequestSnapshotAndTxnAuditInfo() {
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setReviewedAt(LocalDateTime.now());
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setStatus("locked");
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(snapshot);
        PartnerAccountTxn txn = pointsTxn();
        when(partnerAccountTxnMapper.selectByBizTypeAndProjectId("partner_project_first_order", 10L)).thenReturn(txn);

        AdminProjectStartRequestVO vo = service.adminDetail(99L);

        assertEquals("approved", vo.getStatus());
        assertEquals("approved_pending_setup", vo.getProjectDisplayStatus());
        assertEquals("locked", vo.getQuotaSnapshotStatus());
        assertEquals("PTD-1", vo.getPointsTxnNo());
        assertEquals(new BigDecimal("-80.00"), vo.getPointsTxnAmount());
    }

    @Test
    void assignedInternalOwnerCanMarkSetupReady() {
        SysUser operator = internalOwner();
        operator.setId(70L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        when(projectMapper.update(any(), any())).thenReturn(1);
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setStatus("locked");
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(snapshot);
        when(partnerAccountTxnMapper.selectByBizTypeAndProjectId("partner_project_first_order", 10L)).thenReturn(pointsTxn());
        ProjectSetupReadyRequest req = new ProjectSetupReadyRequest();
        req.setRemark("账号和浏览器已配置");

        AdminProjectStartRequestVO vo = service.markSetupReady(99L, req);

        assertEquals("approved", vo.getStatus());
        assertEquals("setup_ready", vo.getProjectStatus());
        assertEquals("setup_ready", vo.getProjectDisplayStatus());
        assertEquals("locked", vo.getQuotaSnapshotStatus());
        verify(currentUserService).ensurePermission("project.update");
    }

    @Test
    void setupReadyFailsWhenRequiredSelfMediaConfigurationMissing() {
        SysUser operator = internalOwner();
        operator.setId(70L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        when(projectChannelAllocationMapper.selectList(any())).thenReturn(List.of(channelAllocation("self_media:wechat", 13)));

        BizException ex = assertThrows(BizException.class, () -> service.markSetupReady(99L, null));

        assertEquals(400, ex.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("公众号未配置启用的自媒体账号"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missingItems = (List<Map<String, Object>>) data.get("missingItems");
        assertEquals("PROJECT_SETUP_NOT_READY", data.get("errorCode"));
        assertEquals("self_media_account", missingItems.get(0).get("type"));
        assertEquals("wechat", missingItems.get(0).get("platform"));
        verify(projectMapper, never()).update(any(), any());
    }

    @Test
    void setupReadyFailsWhenBrowserEnvironmentBindingMissing() {
        SysUser operator = internalOwner();
        operator.setId(70L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        when(projectChannelAllocationMapper.selectList(any())).thenReturn(List.of(channelAllocation("self_media:wechat", 13)));
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(selfMediaAccount("wechat_mp"));

        BizException ex = assertThrows(BizException.class, () -> service.markSetupReady(99L, null));

        assertEquals(400, ex.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("公众号未绑定启用的指纹浏览器环境"));
        verify(projectMapper, never()).update(any(), any());
    }

    @Test
    void setupReadyPassesWhenRequiredSelfMediaConfigurationExists() {
        SysUser operator = internalOwner();
        operator.setId(70L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        when(projectChannelAllocationMapper.selectList(any())).thenReturn(List.of(channelAllocation("self_media:wechat", 13)));
        SelfMediaAccount account = selfMediaAccount("wechat_mp");
        when(selfMediaAccountMapper.selectOne(any())).thenReturn(account);
        BrowserEnvironmentAccount environmentAccount = new BrowserEnvironmentAccount();
        environmentAccount.setId(901L);
        environmentAccount.setSelfMediaAccountId(account.getId());
        when(browserEnvironmentAccountMapper.selectActiveBySelfMediaAccountId(account.getId())).thenReturn(environmentAccount);
        when(projectMapper.update(any(), any())).thenReturn(1);
        ProjectQuotaSnapshot snapshot = new ProjectQuotaSnapshot();
        snapshot.setStatus("locked");
        when(quotaSnapshotMapper.selectLatestByStartRequestId(99L)).thenReturn(snapshot);
        when(partnerAccountTxnMapper.selectByBizTypeAndProjectId("partner_project_first_order", 10L)).thenReturn(pointsTxn());

        AdminProjectStartRequestVO vo = service.markSetupReady(99L, null);

        assertEquals("setup_ready", vo.getProjectStatus());
        verify(projectMapper).update(any(), any());
    }

    @Test
    void setupReadyRejectsUnassignedInternalOperatorWithoutManagerPermission() {
        SysUser operator = internalOwner();
        operator.setId(71L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));

        BizException ex = assertThrows(BizException.class, () -> service.markSetupReady(99L, null));

        assertEquals(403, ex.getCode());
        assertEquals("Only assigned internal owner can mark project setup ready", ex.getMessage());
        verify(projectMapper, never()).update(any(), any());
    }

    @Test
    void setupReadyFailsWhenProjectStatusChangedConcurrently() {
        SysUser operator = internalOwner();
        operator.setId(70L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        ProjectStartRequest request = submittedRequest();
        request.setStatus("approved");
        request.setAssignedInternalOwnerId(70L);
        when(requestMapper.selectById(99L)).thenReturn(request);
        when(projectMapper.selectById(10L)).thenReturn(project("approved_pending_setup"));
        when(projectMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.markSetupReady(99L, null));

        assertEquals(409, ex.getCode());
        assertEquals("Project status changed, please refresh and retry", ex.getMessage());
        verify(quotaSnapshotMapper, never()).selectLatestByStartRequestId(99L);
    }

    private ProjectStartRequestSubmitRequest submitRequest() {
        ProjectStartRequestSubmitRequest req = new ProjectStartRequestSubmitRequest();
        req.setRequestId("REQ-1");
        req.setRemark("ready");
        return req;
    }

    private ProjectStartRequest submittedRequest() {
        ProjectStartRequest request = new ProjectStartRequest();
        request.setId(99L);
        request.setProjectId(10L);
        request.setCompanyId(20L);
        request.setPartnerId(100L);
        request.setApplicantUserId(7L);
        request.setStatus("submitted");
        request.setRequestNo("REQ-1");
        request.setSubmittedAt(java.time.LocalDateTime.now());
        request.setPointsRequiredSnapshot(new BigDecimal("80.00"));
        request.setDiscountRateSnapshot(new BigDecimal("0.8"));
        request.setPackageSnapshotJson("{\"packageName\":\"Partner Plan\"}");
        request.setPartnerAllocatedQuotaJson("[]");
        return request;
    }

    private SysUser user(String role) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setRole(role);
        user.setPartnerId(100L);
        user.setIsActive(true);
        return user;
    }

    private Project project(String status) {
        Project project = new Project();
        project.setId(10L);
        project.setCompanyId(20L);
        project.setBrandId(60L);
        project.setProjectName("启动项目");
        project.setPartnerId(100L);
        project.setOwnerType("partner");
        project.setStatus(status);
        project.setPlanKeywordGroupLimit(3);
        project.setTargetRegions("合肥");
        project.setCoreKeywords("核心关键词");
        project.setTargetAudience("目标受众");
        return project;
    }

    private CompanyPackageBinding binding() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setId(40L);
        binding.setCompanyId(20L);
        binding.setPackagePlanId(30L);
        binding.setPackageSnapshotJson("{\"packageName\":\"Partner Plan\"}");
        binding.setInternalDeliverySnapshotJson("{\"forum\":5}");
        binding.setStatus(CompanyPackageBinding.STATUS_ACTIVE);
        return binding;
    }

    private Company company() {
        Company company = new Company();
        company.setId(20L);
        company.setCompanyName("测试客户");
        company.setIndustry("本地生活");
        company.setContactName("张三");
        company.setContactPhone("13800000000");
        company.setOwnerId(70L);
        company.setPartnerWorkflowStatus("entry_completed");
        return company;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(60L);
        brand.setCompanyId(20L);
        brand.setBrandName("测试品牌");
        brand.setBrandShortName("测试");
        brand.setIndustry("本地生活");
        brand.setMainBusiness("服务");
        return brand;
    }

    private BrandImageFolder imageFolder(Long id, String name) {
        BrandImageFolder folder = new BrandImageFolder();
        folder.setId(id);
        folder.setBrandId(60L);
        folder.setFolderName(name);
        folder.setStatus("active");
        return folder;
    }

    private BrandMaterial imageMaterial(Long folderId) {
        BrandMaterial material = new BrandMaterial();
        material.setBrandId(60L);
        material.setFolderId(folderId);
        material.setCategory("brand_image");
        return material;
    }

    private ProjectChannelAllocation channelAllocation(String channelCode, int allocatedCount) {
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setProjectId(10L);
        allocation.setCompanyId(20L);
        allocation.setChannelCode(channelCode);
        allocation.setAllocatedCount(allocatedCount);
        return allocation;
    }

    private SelfMediaAccount selfMediaAccount(String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(801L);
        account.setBrandId(60L);
        account.setPlatform(platform);
        account.setStatus("active");
        return account;
    }

    private PackagePlan plan() {
        PackagePlan plan = new PackagePlan();
        plan.setId(30L);
        plan.setAudienceType(PackagePlanService.AUDIENCE_PARTNER);
        plan.setPartnerPoints(new BigDecimal("100.00"));
        return plan;
    }

    private Partner partner() {
        Partner partner = new Partner();
        partner.setId(100L);
        partner.setDiscountRate(new BigDecimal("0.8"));
        return partner;
    }

    private PartnerAccount account() {
        PartnerAccount account = new PartnerAccount();
        account.setId(300L);
        account.setPartnerId(100L);
        account.setStatus("active");
        account.setCurrentBalance(new BigDecimal("500.00"));
        account.setTotalDeduction(BigDecimal.ZERO);
        return account;
    }

    private SysUser internalOwner() {
        SysUser user = new SysUser();
        user.setId(70L);
        user.setRole("operator");
        user.setIsActive(true);
        return user;
    }

    private PartnerAccountTxn pointsTxn() {
        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setId(501L);
        txn.setTxnNo("PTD-1");
        txn.setAmount(new BigDecimal("-80.00"));
        txn.setRelatedProjectId(10L);
        txn.setRelatedCompanyId(20L);
        txn.setRelatedStartRequestId(99L);
        return txn;
    }
}
