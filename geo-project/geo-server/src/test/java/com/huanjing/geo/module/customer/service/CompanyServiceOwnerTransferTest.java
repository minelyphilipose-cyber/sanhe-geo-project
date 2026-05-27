package com.huanjing.geo.module.customer.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.dto.CompanyOwnerTransferRequest;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountMapper;
import com.huanjing.geo.module.customer.mapper.CompanyAccountTxnMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceOwnerTransferTest {

    private CompanyMapper companyMapper;
    private SysUserMapper sysUserMapper;
    private CurrentUserService currentUserService;
    private ActivityLogService activityLogService;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyMapper = mock(CompanyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        currentUserService = mock(CurrentUserService.class);
        activityLogService = mock(ActivityLogService.class);
        companyService = new CompanyService(
                companyMapper,
                mock(CompanyAccountMapper.class),
                mock(CompanyAccountTxnMapper.class),
                mock(BrandMapper.class),
                mock(PartnerMapper.class),
                mock(SysDictItemMapper.class),
                sysUserMapper,
                currentUserService,
                mock(InternalScopeService.class),
                mock(CompanyPackageBindingService.class),
                mock(CompanyChannelQuotaUsageMapper.class),
                mock(KeywordGroupService.class),
                mock(ProjectMapper.class),
                activityLogService
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

    private CompanyOwnerTransferRequest request(Long newOwnerId, String reason) {
        CompanyOwnerTransferRequest req = new CompanyOwnerTransferRequest();
        req.setNewOwnerId(newOwnerId);
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

    private SysUser user(Long id, String role, boolean active) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }
}
