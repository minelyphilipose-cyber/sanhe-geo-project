package com.huanjing.geo.module.customer.access;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandAccessServiceTest {

    private static final Long BRAND_ID = 10L;
    private static final Long COMPANY_ID = 20L;
    private static final Long OPERATOR_ID = 30L;
    private static final Long PARTNER_ID = 40L;

    @Mock
    private BrandMapper brandMapper;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private BrandOperatorAssignmentMapper assignmentMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditService auditService;

    private BrandAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new BrandAccessService(
                brandMapper,
                companyMapper,
                assignmentMapper,
                sysUserMapper,
                sysUserRoleMapper,
                currentUserService,
                permissionService,
                auditService
        );
        when(brandMapper.selectById(BRAND_ID)).thenReturn(brand());
    }

    @Test
    void requireBrandAccess_globalRoleBypassesAssignment() {
        SysUser manager = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(manager);
        when(permissionService.hasPerm(manager, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("manager"));

        Brand brand = accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE);

        assertEquals(BRAND_ID, brand.getId());
        verify(assignmentMapper, never()).selectActiveRole(BRAND_ID, OPERATOR_ID);
    }

    @Test
    void requireBrandAccess_primaryCanManage() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "brand.update")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("operator"));
        when(assignmentMapper.selectActiveRole(BRAND_ID, OPERATOR_ID)).thenReturn("PRIMARY");

        Brand brand = accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.MANAGE);

        assertEquals(BRAND_ID, brand.getId());
    }

    @Test
    void requireBrandAccess_secondaryCanOperateButCannotManage() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "brand.update")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("operator"));
        when(assignmentMapper.selectActiveRole(BRAND_ID, OPERATOR_ID)).thenReturn("SECONDARY");

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.MANAGE));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        assertPermissionDeniedAudit("ASSIGNMENT_ROLE_DENIED", BrandAccessAction.MANAGE);
    }

    @Test
    void requireBrandAccess_viewerCanReadButCannotOperate() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("operator"));
        when(assignmentMapper.selectActiveRole(BRAND_ID, OPERATOR_ID)).thenReturn("VIEWER");

        Brand brand = accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ);

        assertEquals(BRAND_ID, brand.getId());

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
    }

    @Test
    void requireBrandAccess_partnerCanReadOwnPartnerBrandOnly() {
        SysUser partner = user(OPERATOR_ID, "partner");
        partner.setPartnerId(PARTNER_ID);
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(partner);
        when(permissionService.hasPerm(partner, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("partner"));
        when(companyMapper.selectById(COMPANY_ID)).thenReturn(company(PARTNER_ID));

        Brand brand = accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ);

        assertEquals(BRAND_ID, brand.getId());
        verify(assignmentMapper, never()).selectActiveRole(BRAND_ID, OPERATOR_ID);
    }

    @Test
    void requireBrandAccess_missingAssignmentAuditsDenied() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("operator"));
        when(assignmentMapper.selectActiveRole(BRAND_ID, OPERATOR_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        assertPermissionDeniedAudit("ASSIGNMENT_MISSING", BrandAccessAction.OPERATE);
    }

    @Test
    void requireBrandAccess_inactiveOperatorAuditsDistinctReason() {
        SysUser operator = user(OPERATOR_ID, "operator");
        operator.setIsActive(false);
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED, ex.getCode());
        assertPermissionDeniedAudit("OPERATOR_INACTIVE", BrandAccessAction.READ);
    }

    @Test
    void requireBrandAccess_missingOperatorAuditsDistinctReason() {
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED, ex.getCode());
        assertPermissionDeniedAudit("OPERATOR_NOT_FOUND", BrandAccessAction.READ);
    }

    @Test
    void requireBrandAccess_missingBrandAuditsDenied() {
        when(brandMapper.selectById(BRAND_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND, ex.getCode());
        assertPermissionDeniedAudit("BRAND_NOT_FOUND", BrandAccessAction.READ);
    }

    @Test
    void requireBrandAccess_missingCompanyAuditsDenied() {
        SysUser partner = user(OPERATOR_ID, "partner");
        partner.setPartnerId(PARTNER_ID);
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(partner);
        when(permissionService.hasPerm(partner, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("partner"));
        when(companyMapper.selectById(COMPANY_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> accessService.requireBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND, ex.getCode());
        assertPermissionDeniedAudit("COMPANY_NOT_FOUND", BrandAccessAction.READ);
    }

    @Test
    void hasBrandAccessReturnsFalseWithoutLeakingException() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(false);

        boolean result = accessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.READ);

        assertFalse(result);
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hasBrandAccessReturnsTrueForAllowedAssignment() {
        SysUser operator = user(OPERATOR_ID, "operator");
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(operator);
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("operator"));
        when(assignmentMapper.selectActiveRole(BRAND_ID, OPERATOR_ID)).thenReturn("SECONDARY");

        boolean result = accessService.hasBrandAccess(BRAND_ID, OPERATOR_ID, BrandAccessAction.OPERATE);

        assertTrue(result);
    }

    @Test
    void requireCurrentUserBrandAccessUsesCurrentOperatorId() {
        SysUser current = user(OPERATOR_ID, "operator");
        when(currentUserService.requireCurrentUser()).thenReturn(current);
        when(sysUserMapper.selectById(OPERATOR_ID)).thenReturn(current);
        when(permissionService.hasPerm(current, "company.read")).thenReturn(true);
        when(sysUserRoleMapper.selectRoleKeysByUserId(OPERATOR_ID)).thenReturn(List.of("manager"));

        Brand brand = accessService.requireCurrentUserBrandAccess(BRAND_ID, BrandAccessAction.READ);

        assertEquals(BRAND_ID, brand.getId());
    }

    private void assertPermissionDeniedAudit(String reason, BrandAccessAction action) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("PERMISSION_DENY", event.getEventType());
        assertEquals(OPERATOR_ID, event.getActorId());
        assertEquals(BRAND_ID, event.getBrandId());
        assertEquals("BRAND", event.getTargetType());
        assertEquals(String.valueOf(BRAND_ID), event.getTargetId());
        assertEquals(AuditResult.DENIED, event.getResult());
        assertEquals(AuditMode.SYNC, event.getMode());
        assertEquals(reason, event.getErrorMessage());
        assertEquals(action.name(), event.getDetail().get("action"));
        assertEquals(reason, event.getDetail().get("reason"));
    }

    private static Brand brand() {
        Brand brand = new Brand();
        brand.setId(BRAND_ID);
        brand.setCompanyId(COMPANY_ID);
        return brand;
    }

    private static Company company(Long partnerId) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setPartnerId(partnerId);
        return company;
    }

    private static SysUser user(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }
}
