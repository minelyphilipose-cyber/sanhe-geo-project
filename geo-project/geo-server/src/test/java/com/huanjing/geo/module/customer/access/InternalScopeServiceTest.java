package com.huanjing.geo.module.customer.access;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalScopeServiceTest {

    private CompanyMapper companyMapper;
    private ProjectMapper projectMapper;
    private SysUserRoleMapper sysUserRoleMapper;
    private InternalScopeService service;

    @BeforeEach
    void setUp() {
        initTableInfo(Company.class);
        initTableInfo(Brand.class);
        initTableInfo(Project.class);
        companyMapper = mock(CompanyMapper.class);
        projectMapper = mock(ProjectMapper.class);
        sysUserRoleMapper = mock(SysUserRoleMapper.class);
        service = new InternalScopeService(companyMapper, projectMapper, sysUserRoleMapper);
    }

    @Test
    void globalRoleSkipsOwnerScopeFromLegacyRole() {
        SysUser manager = user(1L, "manager");

        assertTrue(service.isGlobalInternal(manager));
        assertFalse(service.requiresOwnerScope(manager));
    }

    @Test
    void globalRoleSkipsOwnerScopeFromRbacRole() {
        SysUser user = user(1L, "operator");
        when(sysUserRoleMapper.selectRoleKeysByUserId(1L)).thenReturn(List.of("delivery_manager"));

        assertTrue(service.isGlobalInternal(user));
        assertFalse(service.requiresOwnerScope(user));
    }

    @Test
    void operatorRequiresOwnerScope() {
        SysUser operator = user(2L, "operator");
        when(sysUserRoleMapper.selectRoleKeysByUserId(2L)).thenReturn(List.of("operator"));

        assertTrue(service.requiresOwnerScope(operator));
    }

    @Test
    void partnerAndSalesDoNotUseOwnerScope() {
        assertFalse(service.requiresOwnerScope(user(3L, "partner_staff")));
        assertFalse(service.requiresOwnerScope(user(4L, "sales")));
    }

    @Test
    void salesListScopeDefaultsToNoRowsWhenNoDedicatedSalesScopeExists() {
        SysUser sales = user(4L, "sales");
        LambdaQueryWrapper<Brand> brandWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<>();

        service.applyBrandScope(brandWrapper, sales);
        service.applyProjectScope(projectWrapper, sales);

        assertTrue(brandWrapper.getTargetSql().contains("1 = 0"));
        assertTrue(projectWrapper.getTargetSql().contains("1 = 0"));
    }

    @Test
    void partnerListScopeIsLeftForPartnerSpecificFilters() {
        SysUser partner = user(5L, "partner_staff");
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();

        service.applyBrandScope(wrapper, partner);

        assertFalse(wrapper.getTargetSql().contains("1 = 0"));
        assertFalse(wrapper.getTargetSql().contains("owner_id"));
    }

    @Test
    void ensureCompanyAccessAllowsOnlyOwnerForScopedOperator() {
        SysUser operator = user(9L, "operator");
        when(sysUserRoleMapper.selectRoleKeysByUserId(9L)).thenReturn(List.of("operator"));
        Company owned = company(9L);
        Company other = company(10L);
        Company nullOwner = company(null);

        assertDoesNotThrow(() -> service.ensureCompanyAccess(operator, owned, "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(operator, other, "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(operator, nullOwner, "company"));
    }

    @Test
    void resolveProjectOwnerReturnsOwningCompanyOwner() {
        Project project = new Project();
        project.setId(30L);
        project.setCompanyId(40L);
        when(projectMapper.selectById(30L)).thenReturn(project);
        when(companyMapper.selectById(40L)).thenReturn(company(8L));

        assertEquals(8L, service.resolveProjectOwnerId(30L));
    }

    private SysUser user(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Company company(Long ownerId) {
        Company company = new Company();
        company.setId(40L);
        company.setOwnerId(ownerId);
        return company;
    }

    private void initTableInfo(Class<?> entityType) {
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        } catch (Exception ignored) {
            // MyBatis-Plus keeps table metadata in static caches across tests.
        }
    }
}
