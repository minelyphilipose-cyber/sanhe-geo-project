package com.huanjing.geo.module.system.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private CompanyMapper companyMapper;
    @Mock
    private InternalScopeService internalScopeService;

    @InjectMocks
    private CurrentUserService currentUserService;

    @Test
    void ensureBrandAccess_noPermission_throws403() {
        SysUser operator = internalOperator();
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(false);

        BizException ex = assertThrows(
                BizException.class,
                () -> currentUserService.ensureBrandAccess(operator, 10L, "brand")
        );

        assertEquals(403, ex.getCode());
        assertEquals("No permission: company.read", ex.getMessage());
        verify(brandMapper, never()).selectById(10L);
    }

    @Test
    void ensureBrandAccess_brandNotFound_throws404() {
        SysUser operator = internalOperator();
        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(brandMapper.selectById(10L)).thenReturn(null);

        BizException ex = assertThrows(
                BizException.class,
                () -> currentUserService.ensureBrandAccess(operator, 10L, "brand")
        );

        assertEquals(404, ex.getCode());
        assertEquals("Brand not found", ex.getMessage());
        verify(companyMapper, never()).selectById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void ensureBrandAccess_companyNotFound_throws404() {
        SysUser operator = internalOperator();
        Brand brand = new Brand();
        brand.setId(10L);
        brand.setCompanyId(100L);

        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(brandMapper.selectById(10L)).thenReturn(brand);
        when(companyMapper.selectById(100L)).thenReturn(null);

        BizException ex = assertThrows(
                BizException.class,
                () -> currentUserService.ensureBrandAccess(operator, 10L, "brand")
        );

        assertEquals(404, ex.getCode());
        assertEquals("Company not found", ex.getMessage());
    }

    @Test
    void ensureBrandAccess_hasPermission_passes() {
        SysUser operator = internalOperator();
        Brand brand = new Brand();
        brand.setId(10L);
        brand.setCompanyId(100L);
        Company company = new Company();
        company.setId(100L);
        company.setPartnerId(900L);

        when(permissionService.hasPerm(operator, "company.read")).thenReturn(true);
        when(brandMapper.selectById(10L)).thenReturn(brand);
        when(companyMapper.selectById(100L)).thenReturn(company);

        assertDoesNotThrow(() -> currentUserService.ensureBrandAccess(operator, 10L, "brand"));
    }

    @Test
    void partnerViewer_isNotPartnerUser() {
        SysUser user = new SysUser();
        user.setRole("partner_viewer");
        user.setPartnerId(900L);

        assertFalse(currentUserService.isPartnerUser(user));
    }

    private SysUser internalOperator() {
        SysUser operator = new SysUser();
        operator.setId(1L);
        operator.setRole("super_admin");
        operator.setIsActive(true);
        return operator;
    }
}
