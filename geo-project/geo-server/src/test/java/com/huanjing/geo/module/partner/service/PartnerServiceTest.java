package com.huanjing.geo.module.partner.service;

import com.huanjing.geo.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.partner.dto.PartnerCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffCreateRequest;
import com.huanjing.geo.module.partner.dto.PartnerStaffUpdateRequest;
import com.huanjing.geo.module.partner.dto.PartnerUpdateRequest;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerDiscountHistory;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerDiscountHistoryMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.partner.mapper.PartnerRechargeOrderMapper;
import com.huanjing.geo.module.system.entity.SysRole;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SysUserRole;
import com.huanjing.geo.module.system.mapper.SysRoleMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class PartnerServiceTest {

    private PartnerMapper partnerMapper;
    private PartnerAccountMapper partnerAccountMapper;
    private PartnerDiscountHistoryMapper partnerDiscountHistoryMapper;
    private CompanyMapper companyMapper;
    private SysUserMapper sysUserMapper;
    private SysRoleMapper sysRoleMapper;
    private SysUserRoleMapper sysUserRoleMapper;
    private CurrentUserService currentUserService;
    private InternalScopeService internalScopeService;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        partnerMapper = mock(PartnerMapper.class);
        partnerAccountMapper = mock(PartnerAccountMapper.class);
        PartnerAccountTxnMapper partnerAccountTxnMapper = mock(PartnerAccountTxnMapper.class);
        partnerDiscountHistoryMapper = mock(PartnerDiscountHistoryMapper.class);
        PartnerRechargeOrderMapper partnerRechargeOrderMapper = mock(PartnerRechargeOrderMapper.class);
        companyMapper = mock(CompanyMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        sysRoleMapper = mock(SysRoleMapper.class);
        sysUserRoleMapper = mock(SysUserRoleMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        currentUserService = mock(CurrentUserService.class);
        internalScopeService = mock(InternalScopeService.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        service = new PartnerService(
                partnerMapper,
                partnerAccountMapper,
                partnerAccountTxnMapper,
                partnerDiscountHistoryMapper,
                partnerRechargeOrderMapper,
                companyMapper,
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                currentUserService,
                internalScopeService,
                activityLogService,
                minioStorageService,
                objectMapper
        );

        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(internalScopeService.isSuperAdmin(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysRoleMapper.selectOne(any())).thenReturn(role());
        doAnswer(invocation -> {
            Partner partner = invocation.getArgument(0);
            partner.setId(100L);
            return 1;
        }).when(partnerMapper).insert(any(Partner.class));
        doAnswer(invocation -> {
            PartnerAccount account = invocation.getArgument(0);
            account.setId(200L);
            return 1;
        }).when(partnerAccountMapper).insert(any(PartnerAccount.class));
        doAnswer(invocation -> {
            PartnerDiscountHistory history = invocation.getArgument(0);
            history.setId(300L);
            return 1;
        }).when(partnerDiscountHistoryMapper).insert(any(PartnerDiscountHistory.class));
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(400L);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
    }

    @Test
    void createStoresPresaleReportQuotaConfig() {
        PartnerCreateRequest req = new PartnerCreateRequest();
        req.setPartnerCode("P001");
        req.setPartnerName("合伙人一号");
        req.setPartnerLevel("level_29800");
        req.setDiscountRate(new BigDecimal("0.8000"));
        req.setInitialAmount(BigDecimal.ZERO);
        req.setPresaleReportFreeQuotaLimit(5);
        req.setPresaleReportExtraPoints(new BigDecimal("12.50"));

        service.create(req);

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerMapper).insert(captor.capture());
        Partner saved = captor.getValue();
        assertEquals(5, saved.getPresaleReportFreeQuotaLimit());
        assertEquals(0, new BigDecimal("12.50").compareTo(saved.getPresaleReportExtraPoints()));
    }

    @Test
    void createGeneratesPartnerCodeWhenMissing() {
        PartnerCreateRequest req = new PartnerCreateRequest();
        req.setPartnerName("合伙人一号");
        req.setDiscountRate(new BigDecimal("0.8000"));
        req.setInitialAmount(BigDecimal.ZERO);
        when(partnerMapper.selectCount(any())).thenReturn(0L);

        service.create(req);

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerMapper).insert(captor.capture());
        Partner saved = captor.getValue();
        assertNotNull(saved.getPartnerCode());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getPartnerCode().matches("P\\d{14}"));
        assertEquals("custom", saved.getPartnerLevel());
    }

    @Test
    void updateStoresPresaleReportQuotaConfig() {
        Partner existed = new Partner();
        existed.setId(100L);
        existed.setPartnerCode("P001");
        existed.setPartnerName("合伙人一号");
        existed.setPartnerLevel("level_29800");
        existed.setDiscountRate(new BigDecimal("0.8000"));
        existed.setStatus("active");
        when(partnerMapper.selectById(100L)).thenReturn(existed);

        PartnerUpdateRequest req = new PartnerUpdateRequest();
        req.setPartnerName("合伙人一号");
        req.setPartnerLevel("level_29800");
        req.setDiscountRate(new BigDecimal("0.8000"));
        req.setStatus("active");
        req.setPresaleReportFreeQuotaLimit(8);
        req.setPresaleReportExtraPoints(new BigDecimal("15.00"));

        service.update(100L, req);

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerMapper).updateById(captor.capture());
        Partner saved = captor.getValue();
        assertEquals(8, saved.getPresaleReportFreeQuotaLimit());
        assertEquals(0, new BigDecimal("15.00").compareTo(saved.getPresaleReportExtraPoints()));
    }

    @Test
    void partnerOwnerCanCreateSingleStaffAccount() {
        SysUser owner = user();
        owner.setRole("partner");
        owner.setPartnerId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(partnerMapper.selectByIdForUpdate(100L)).thenReturn(activePartner());
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        PartnerStaffCreateRequest req = new PartnerStaffCreateRequest();
        req.setUsername("staff001");
        req.setDisplayName("交付员工");
        req.setPhone("13800000000");

        var result = service.createMyStaff(req);

        assertNotNull(result.getInitialPassword());
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(userCaptor.capture());
        SysUser saved = userCaptor.getValue();
        assertEquals("partner_staff", saved.getRole());
        assertEquals(100L, saved.getPartnerId());
        assertEquals(true, saved.getIsActive());
        verify(partnerMapper).selectByIdForUpdate(100L);
    }

    @Test
    void createStaffConvertsUniqueConstraintConflictToBusinessError() {
        SysUser owner = user();
        owner.setRole("partner");
        owner.setPartnerId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(partnerMapper.selectByIdForUpdate(100L)).thenReturn(activePartner());
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_sys_user_partner_staff_single"))
                .when(sysUserMapper).insert(any(SysUser.class));
        PartnerStaffCreateRequest req = new PartnerStaffCreateRequest();
        req.setUsername("staff001");
        req.setDisplayName("交付员工");

        BizException ex = assertThrows(BizException.class, () -> service.createMyStaff(req));

        assertEquals(409, ex.getCode());
        assertEquals("Only one partner staff account is allowed", ex.getMessage());
    }

    @Test
    void partnerStaffCannotCreateStaffAccount() {
        SysUser staff = user();
        staff.setRole("partner_staff");
        staff.setPartnerId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(staff);

        PartnerStaffCreateRequest req = new PartnerStaffCreateRequest();
        req.setUsername("staff001");
        req.setDisplayName("交付员工");

        BizException ex = assertThrows(BizException.class, () -> service.createMyStaff(req));

        assertEquals(403, ex.getCode());
    }

    @Test
    void partnerOwnerCanUpdateOwnedStaffProfile() {
        SysUser owner = user();
        owner.setRole("partner");
        owner.setPartnerId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        SysUser staff = staffUser();
        when(sysUserMapper.selectById(30L)).thenReturn(staff);
        PartnerStaffUpdateRequest req = new PartnerStaffUpdateRequest();
        req.setDisplayName(" 新员工 ");
        req.setPhone("13900000000");
        req.setEmail("staff@example.com");

        var result = service.updateMyStaff(30L, req);

        assertEquals("新员工", result.getDisplayName());
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(userCaptor.capture());
        assertEquals("新员工", userCaptor.getValue().getDisplayName());
        assertEquals("13900000000", userCaptor.getValue().getPhone());
        assertEquals("staff@example.com", userCaptor.getValue().getEmail());
    }

    @Test
    void partnerOwnerCanDeleteOwnedStaffAndClearAssignments() {
        SysUser owner = user();
        owner.setRole("partner");
        owner.setPartnerId(100L);
        when(currentUserService.requireCurrentUser()).thenReturn(owner);
        when(sysUserMapper.selectById(30L)).thenReturn(staffUser());

        service.deleteMyStaff(30L);

        verify(companyMapper).update(any(), any());
        verify(sysUserRoleMapper).delete(any());
        verify(sysUserMapper).deleteById(30L);
    }

    @Test
    void updateStatusDeactivatesPartnerStaffAccounts() {
        Partner partner = activePartner();
        when(partnerMapper.selectById(100L)).thenReturn(partner);
        SysUser staff = new SysUser();
        staff.setId(30L);
        staff.setRole("partner_staff");
        staff.setPartnerId(100L);
        staff.setIsActive(true);
        staff.setTokenVersion(1);
        when(sysUserMapper.selectList(any())).thenReturn(java.util.List.of(staff));

        service.updateStatus(100L, "paused");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(userCaptor.capture());
        assertEquals(false, userCaptor.getValue().getIsActive());
        assertEquals(2, userCaptor.getValue().getTokenVersion());
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setRole("admin");
        return user;
    }

    private SysUser staffUser() {
        SysUser staff = new SysUser();
        staff.setId(30L);
        staff.setUsername("staff001");
        staff.setDisplayName("交付员工");
        staff.setRole("partner_staff");
        staff.setPartnerId(100L);
        staff.setIsActive(true);
        staff.setTokenVersion(0);
        return staff;
    }

    private Partner activePartner() {
        Partner partner = new Partner();
        partner.setId(100L);
        partner.setPartnerName("合伙人一号");
        partner.setStatus("active");
        return partner;
    }

    private SysRole role() {
        SysRole role = new SysRole();
        role.setId(6L);
        role.setRoleKey("partner");
        role.setStatus("active");
        return role;
    }
}
