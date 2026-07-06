package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.dto.CompanyOwnerTransferRequest;
import com.huanjing.geo.module.customer.dto.CompanyPartnerEntryReturnRequest;
import com.huanjing.geo.module.customer.dto.CompanyPartnerStaffAssignRequest;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountTxnMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionItem;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionWorkorder;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionItemMapper;
import com.huanjing.geo.module.geoquestion.mapper.GeoQuestionWorkorderMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.project.service.ProjectStartRequestService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceOwnerTransferTest {

    private CompanyMapper companyMapper;
    private SysUserMapper sysUserMapper;
    private CurrentUserService currentUserService;
    private InternalScopeService internalScopeService;
    private CompanyPackageBindingService companyPackageBindingService;
    private ProjectMapper projectMapper;
    private GeoQuestionWorkorderMapper geoQuestionWorkorderMapper;
    private GeoQuestionItemMapper geoQuestionItemMapper;
    private ActivityLogService activityLogService;
    private ProjectStartRequestService projectStartRequestService;
    private SystemAlertService systemAlertService;
    private CompanyService companyService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(Project.class);
        initTableInfo(GeoQuestionWorkorder.class);
        initTableInfo(GeoQuestionItem.class);
    }

    @BeforeEach
    void setUp() {
        companyMapper = mock(CompanyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        currentUserService = mock(CurrentUserService.class);
        internalScopeService = mock(InternalScopeService.class);
        companyPackageBindingService = mock(CompanyPackageBindingService.class);
        projectMapper = mock(ProjectMapper.class);
        geoQuestionWorkorderMapper = mock(GeoQuestionWorkorderMapper.class);
        geoQuestionItemMapper = mock(GeoQuestionItemMapper.class);
        activityLogService = mock(ActivityLogService.class);
        projectStartRequestService = mock(ProjectStartRequestService.class);
        systemAlertService = mock(SystemAlertService.class);
        companyService = new CompanyService(
                companyMapper,
                mock(CompanyAccountMapper.class),
                mock(CompanyAccountTxnMapper.class),
                mock(BrandMapper.class),
                mock(PartnerMapper.class),
                mock(SysDictItemMapper.class),
                sysUserMapper,
                currentUserService,
                internalScopeService,
                companyPackageBindingService,
                mock(CompanyChannelQuotaUsageMapper.class),
                mock(KeywordGroupService.class),
                mock(KeywordGroupMapper.class),
                projectMapper,
                geoQuestionWorkorderMapper,
                geoQuestionItemMapper,
                activityLogService,
                projectStartRequestService,
                systemAlertService
        );
        SysUser deliveryManager = user(99L, "delivery_manager", true);
        when(currentUserService.requireCurrentUser()).thenReturn(deliveryManager);
    }

    @Test
    void transferOwner_updatesOwnerAndWritesStrictActivityLog() {
        Company company = company(1L, 10L);
        SysUser newOwner = user(20L, "operator", true);
        newOwner.setDisplayName("New Owner");
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(sysUserMapper.selectById(20L)).thenReturn(newOwner);
        CompanyOwnerTransferRequest req = request(20L, "handover");

        Company result = companyService.transferOwner(1L, req);

        assertEquals(20L, result.getOwnerId());
        verify(currentUserService).ensurePermission("delivery.assignment.manage");
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyMapper).updateById(companyCaptor.capture());
        assertEquals(20L, companyCaptor.getValue().getOwnerId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> extraCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).logActionRequired(
                eq(99L),
                eq("company.owner.transfer"),
                eq("company"),
                eq(1L),
                any(),
                any(),
                extraCaptor.capture()
        );
        assertEquals(10L, extraCaptor.getValue().get("oldOwnerId"));
        assertEquals(20L, extraCaptor.getValue().get("newOwnerId"));
        assertEquals("handover", extraCaptor.getValue().get("reason"));
    }

    @Test
    void transferOwner_rejectsNonOperatorTarget() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, 10L));
        when(sysUserMapper.selectById(20L)).thenReturn(user(20L, "manager", true));

        BizException ex = assertThrows(BizException.class, () -> companyService.transferOwner(1L, request(20L, null)));

        assertEquals(400, ex.getCode());
        assertEquals("New owner must be an active operator", ex.getMessage());
        verify(companyMapper, never()).updateById(any());
        verify(activityLogService, never()).logActionRequired(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transferOwner_rejectsSameOwner() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, 20L));
        when(sysUserMapper.selectById(20L)).thenReturn(user(20L, "operator", true));

        BizException ex = assertThrows(BizException.class, () -> companyService.transferOwner(1L, request(20L, null)));

        assertEquals(400, ex.getCode());
        assertEquals("New owner is already assigned to this company", ex.getMessage());
        verify(companyMapper, never()).updateById(any());
        verify(activityLogService, never()).logActionRequired(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transferOwner_propagatesStrictLogFailure() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, 10L));
        when(sysUserMapper.selectById(20L)).thenReturn(user(20L, "operator", true));
        RuntimeException failure = new RuntimeException("log failed");
        org.mockito.Mockito.doThrow(failure).when(activityLogService)
                .logActionRequired(any(), any(), any(), any(), any(), any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> companyService.transferOwner(1L, request(20L, null)));

        assertEquals(failure, ex);
        verify(companyMapper).updateById(any());
    }

    @Test
    void assignPartnerStaffOwner_allowsPartnerOwnerForOwnCompany() {
        SysUser partnerOwner = user(50L, "partner", true);
        partnerOwner.setPartnerId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(partnerOwner);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        when(currentUserService.hasPermission("partner.staff.manage")).thenReturn(true);
        Company company = company(1L, 10L);
        company.setPartnerId(7L);
        company.setPartnerStaffOwnerId(null);
        SysUser staff = user(60L, "partner_staff", true);
        staff.setPartnerId(7L);
        staff.setDisplayName("Staff");
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(sysUserMapper.selectById(60L)).thenReturn(staff);
        CompanyPartnerStaffAssignRequest req = staffAssignRequest(60L, "assign");

        Company result = companyService.assignPartnerStaffOwner(1L, req);

        assertEquals(60L, result.getPartnerStaffOwnerId());
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyMapper).updateById(companyCaptor.capture());
        assertEquals(60L, companyCaptor.getValue().getPartnerStaffOwnerId());
        verify(activityLogService).logActionRequired(
                eq(50L),
                eq("company.partner_staff.assign"),
                eq("company"),
                eq(1L),
                any(),
                any(),
                any()
        );
    }

    @Test
    void assignPartnerStaffOwner_rejectsOtherPartnerCompany() {
        SysUser partnerOwner = user(50L, "partner", true);
        partnerOwner.setPartnerId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(partnerOwner);
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(false);
        when(currentUserService.hasPermission("partner.staff.manage")).thenReturn(true);
        Company company = company(1L, 10L);
        company.setPartnerId(8L);
        when(companyMapper.selectById(1L)).thenReturn(company);

        BizException ex = assertThrows(BizException.class,
                () -> companyService.assignPartnerStaffOwner(1L, staffAssignRequest(60L, null)));

        assertEquals(403, ex.getCode());
        verify(companyMapper, never()).updateById(any());
    }

    @Test
    void assignPartnerStaffOwner_rejectsInactiveOrOtherPartnerStaff() {
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(true);
        Company company = company(1L, 10L);
        company.setPartnerId(7L);
        when(companyMapper.selectById(1L)).thenReturn(company);
        SysUser staff = user(60L, "partner_staff", true);
        staff.setPartnerId(8L);
        when(sysUserMapper.selectById(60L)).thenReturn(staff);

        BizException ex = assertThrows(BizException.class,
                () -> companyService.assignPartnerStaffOwner(1L, staffAssignRequest(60L, null)));

        assertEquals(400, ex.getCode());
        assertEquals("Partner staff must be active and belong to this partner", ex.getMessage());
        verify(companyMapper, never()).updateById(any());
    }

    @Test
    void assignPartnerStaffOwner_allowsUnassign() {
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(true);
        Company company = company(1L, 10L);
        company.setPartnerId(7L);
        company.setPartnerStaffOwnerId(60L);
        when(companyMapper.selectById(1L)).thenReturn(company);

        Company result = companyService.assignPartnerStaffOwner(1L, staffAssignRequest(null, "unassign"));

        assertEquals(null, result.getPartnerStaffOwnerId());
        verify(companyMapper).updateById(any());
    }

    @Test
    void deliveryOwnerOptionsReturnsActiveOperators() {
        SysUser operator = user(20L, "operator", true);
        operator.setDisplayName("Operator");
        when(sysUserMapper.selectList(any())).thenReturn(List.of(operator));

        var options = companyService.deliveryOwnerOptions();

        verify(currentUserService).ensurePermission("delivery.assignment.manage");
        assertEquals(1, options.size());
        assertEquals(20L, options.get(0).getId());
        assertEquals("Operator", options.get(0).getDisplayName());
    }

    @Test
    void partnerStaffOptionsReturnsActiveStaffForCompanyPartner() {
        when(currentUserService.hasPermission("delivery.assignment.manage")).thenReturn(true);
        Company company = company(1L, 10L);
        company.setPartnerId(7L);
        when(companyMapper.selectById(1L)).thenReturn(company);
        SysUser staff = user(60L, "partner_staff", true);
        staff.setPartnerId(7L);
        staff.setDisplayName("Staff");
        when(sysUserMapper.selectList(any())).thenReturn(List.of(staff));

        var options = companyService.partnerStaffOptions(1L);

        assertEquals(1, options.size());
        assertEquals(60L, options.get(0).getId());
        assertEquals("Staff", options.get(0).getDisplayName());
    }

    @Test
    void completePartnerEntry_rejectsWhenProjectCoreQuestionsDoNotMatchAllocatedQuota() {
        Company company = partnerCompanyAssignedToStaff();
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(companyPackageBindingService.activeBinding(1L)).thenReturn(new CompanyPackageBinding());
        doThrow(new BizException(400, "项目「Project」核心问题数量需等于分配额度 3 个，当前已录入 2 个"))
                .when(projectStartRequestService).ensurePartnerSubmissionReady(1L);

        BizException ex = assertThrows(BizException.class, () -> companyService.completePartnerEntry(1L));

        assertEquals(400, ex.getCode());
        assertEquals("项目「Project」核心问题数量需等于分配额度 3 个，当前已录入 2 个", ex.getMessage());
        verify(companyMapper, never()).updateById(any());
    }

    @Test
    void completePartnerEntry_allowsWhenEveryProjectCoreQuestionsMatchAllocatedQuota() {
        Company company = partnerCompanyAssignedToStaff();
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(companyPackageBindingService.activeBinding(1L)).thenReturn(new CompanyPackageBinding());

        Company result = companyService.completePartnerEntry(1L);

        assertEquals(CompanyService.PARTNER_WORKFLOW_ENTRY_COMPLETED, result.getPartnerWorkflowStatus());
        verify(projectStartRequestService).ensurePartnerSubmissionReady(1L);
        verify(companyMapper).updateById(any());
        verify(projectMapper).update(eq(null), any());
        verify(activityLogService).logAction(
                eq(60L),
                eq("company.partner_workflow.complete_entry"),
                eq("company"),
                eq(1L),
                any(),
                any(),
                any()
        );
    }

    @Test
    void returnPartnerEntry_allowsPartnerOwnerToSendCompletedEntryBackToStaff() {
        SysUser partnerOwner = user(50L, "partner", true);
        partnerOwner.setPartnerId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(partnerOwner);
        Company company = company(1L, 10L);
        company.setSourceType("partner");
        company.setPartnerId(7L);
        company.setPartnerStaffOwnerId(60L);
        company.setPartnerWorkflowStatus(CompanyService.PARTNER_WORKFLOW_ENTRY_COMPLETED);
        SysUser partnerStaff = user(60L, "partner_staff", true);
        when(sysUserMapper.selectById(60L)).thenReturn(partnerStaff);
        when(companyMapper.selectById(1L)).thenReturn(company);
        CompanyPartnerEntryReturnRequest req = new CompanyPartnerEntryReturnRequest();
        req.setReason("品牌图片需要补充");

        Company result = companyService.returnPartnerEntry(1L, req);

        assertEquals(CompanyService.PARTNER_WORKFLOW_PROJECT_ENTRY, result.getPartnerWorkflowStatus());
        verify(currentUserService).ensurePermission("company.read");
        verify(companyMapper).updateById(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> extraCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).logAction(
                eq(50L),
                eq("company.partner_workflow.return_entry"),
                eq("company"),
                eq(1L),
                any(),
                any(),
                extraCaptor.capture()
        );
        assertEquals("品牌图片需要补充", extraCaptor.getValue().get("reason"));
        verify(systemAlertService).createOrRefreshRecipientAlert(
                eq("partner_company_entry_returned"),
                eq("warn"),
                eq("partner_workflow"),
                eq("负责人已退回资料修改，请交付员工补充后重新提交负责人复核：Company"),
                any(),
                eq(60L),
                eq(null),
                eq("partner-company-entry-returned:1:60")
        );
    }

    @Test
    void returnPartnerEntry_rejectsWhenCompanyNotWaitingOwnerConfirm() {
        SysUser partnerOwner = user(50L, "partner", true);
        partnerOwner.setPartnerId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(partnerOwner);
        Company company = company(1L, 10L);
        company.setSourceType("partner");
        company.setPartnerId(7L);
        company.setPartnerWorkflowStatus(CompanyService.PARTNER_WORKFLOW_PROJECT_ENTRY);
        when(companyMapper.selectById(1L)).thenReturn(company);

        BizException ex = assertThrows(BizException.class, () -> companyService.returnPartnerEntry(1L, new CompanyPartnerEntryReturnRequest()));

        assertEquals(400, ex.getCode());
        assertEquals("仅待负责人确认的客户可以退回修改", ex.getMessage());
        verify(companyMapper, never()).updateById(any());
    }

    private CompanyOwnerTransferRequest request(Long newOwnerId, String reason) {
        CompanyOwnerTransferRequest req = new CompanyOwnerTransferRequest();
        req.setNewOwnerId(newOwnerId);
        req.setReason(reason);
        return req;
    }

    private CompanyPartnerStaffAssignRequest staffAssignRequest(Long staffUserId, String reason) {
        CompanyPartnerStaffAssignRequest req = new CompanyPartnerStaffAssignRequest();
        req.setStaffUserId(staffUserId);
        req.setReason(reason);
        return req;
    }

    private Company company(Long id, Long ownerId) {
        Company company = new Company();
        company.setId(id);
        company.setCompanyName("Company");
        company.setOwnerId(ownerId);
        return company;
    }

    private Company partnerCompanyAssignedToStaff() {
        SysUser partnerStaff = user(60L, "partner_staff", true);
        partnerStaff.setPartnerId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(partnerStaff);
        Company company = company(1L, 10L);
        company.setSourceType("partner");
        company.setPartnerId(7L);
        company.setPartnerStaffOwnerId(60L);
        company.setPartnerWorkflowStatus(CompanyService.PARTNER_WORKFLOW_PROJECT_ENTRY);
        return company;
    }

    private Project project(Long id, Long companyId, Integer coreQuestionLimit) {
        Project project = new Project();
        project.setId(id);
        project.setCompanyId(companyId);
        project.setProjectName("Project");
        project.setPlanKeywordGroupLimitA(coreQuestionLimit);
        return project;
    }

    private GeoQuestionWorkorder workorder(Long id, Long projectId) {
        GeoQuestionWorkorder workorder = new GeoQuestionWorkorder();
        workorder.setId(id);
        workorder.setProjectId(projectId);
        return workorder;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    private SysUser user(Long id, String role, boolean active) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }
}
