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
    void partnerOwnerUsesPartnerCompanyScope() {
        SysUser user = user(3L, "partner");
        user.setPartnerId(900L);
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();

        service.applyCompanyScope(wrapper, user);

        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("partner_id ="));
        assertFalse(sql.contains("partner_staff_owner_id"));
    }

    @Test
    void partnerStaffUsesAssignedCompanyScope() {
        SysUser user = user(4L, "partner_staff");
        user.setPartnerId(900L);
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<>();

        service.applyCompanyScope(wrapper, user);
        service.applyProjectScope(projectWrapper, user);

        assertTrue(wrapper.getTargetSql().contains("partner_id ="));
        assertTrue(wrapper.getTargetSql().contains("partner_staff_owner_id ="));
        assertTrue(projectWrapper.getTargetSql().contains("partner_staff_owner_id = 4"));
    }

    @Test
    void partnerStaffAccessesOnlyAssignedCompanyOfOwnPartner() {
        SysUser staff = user(4L, "partner_staff");
        staff.setPartnerId(900L);

        assertDoesNotThrow(() -> service.ensureCompanyAccess(staff, partnerCompany(900L, 4L), "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(staff, partnerCompany(900L, null), "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(staff, partnerCompany(900L, 5L), "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(staff, partnerCompany(901L, 4L), "company"));
    }

    @Test
    void partnerOwnerAccessesAllOwnPartnerCompaniesOnly() {
        SysUser owner = user(3L, "partner");
        owner.setPartnerId(900L);

        assertDoesNotThrow(() -> service.ensureCompanyAccess(owner, partnerCompany(900L, null), "company"));
        assertDoesNotThrow(() -> service.ensureCompanyAccess(owner, partnerCompany(900L, 4L), "company"));
        assertThrows(BizException.class, () -> service.ensureCompanyAccess(owner, partnerCompany(901L, 4L), "company"));
    }

    @Test
    void partnerStaffCannotAccessUnassignedProject() {
        SysUser staff = user(4L, "partner_staff");
        staff.setPartnerId(900L);
        Project project = project(30L, 40L);
        when(companyMapper.selectById(40L)).thenReturn(partnerCompany(900L, 5L));

        assertThrows(BizException.class, () -> service.ensureProjectAccess(staff, project, "project"));
    }

    @Test
    void partnerStaffCannotAccessUnassignedBrand() {
        SysUser staff = user(4L, "partner_staff");
        staff.setPartnerId(900L);
        Brand brand = new Brand();
        brand.setId(30L);
        brand.setCompanyId(40L);
        when(companyMapper.selectById(40L)).thenReturn(partnerCompany(900L, null));

        assertThrows(BizException.class, () -> service.ensureBrandAccess(staff, brand, "brand"));
    }

    @Test
    void partnerOwnerCanAccessOwnPartnerProject() {
        SysUser owner = user(3L, "partner");
        owner.setPartnerId(900L);
        Project project = project(30L, 40L);
        when(companyMapper.selectById(40L)).thenReturn(partnerCompany(900L, 4L));

        assertDoesNotThrow(() -> service.ensureProjectAccess(owner, project, "project"));
    }

    @Test
    void globalInternalStillBypassesProjectScope() {
        SysUser manager = user(3L, "manager");
        Project project = project(30L, 40L);
        when(companyMapper.selectById(40L)).thenReturn(partnerCompany(900L, 4L));

        assertDoesNotThrow(() -> service.ensureProjectAccess(manager, project, "project"));
    }

    @Test
    void partnerViewerUsesOwnerScopeInsteadOfPartnerScope() {
        SysUser user = user(5L, "partner_viewer");
        when(sysUserRoleMapper.selectRoleKeysByUserId(5L)).thenReturn(List.of("partner_viewer"));

        assertFalse(service.isPartnerUser(user));
        assertTrue(service.requiresOwnerScope(user));
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
    void partnerBrandScopeUsesPartnerCompanyFilter() {
        SysUser partner = user(5L, "partner_staff");
        partner.setPartnerId(900L);
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();

        service.applyBrandScope(wrapper, partner);

        assertFalse(wrapper.getTargetSql().contains("1 = 0"));
        assertTrue(wrapper.getTargetSql().contains("partner_id = 900"));
        assertTrue(wrapper.getTargetSql().contains("partner_staff_owner_id = 5"));
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

    private Company partnerCompany(Long partnerId, Long partnerStaffOwnerId) {
        Company company = company(null);
        company.setPartnerId(partnerId);
        company.setPartnerStaffOwnerId(partnerStaffOwnerId);
        return company;
    }

    private Project project(Long id, Long companyId) {
        Project project = new Project();
        project.setId(id);
        project.setCompanyId(companyId);
        return project;
    }

    private void initTableInfo(Class<?> entityType) {
        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        } catch (Exception ignored) {
            // MyBatis-Plus keeps table metadata in static caches across tests.
        }
    }
}
