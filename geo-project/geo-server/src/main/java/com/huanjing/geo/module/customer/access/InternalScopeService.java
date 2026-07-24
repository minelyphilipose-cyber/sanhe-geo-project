package com.huanjing.geo.module.customer.access;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InternalScopeService {

    private static final Set<String> GLOBAL_INTERNAL_ROLES = Set.of("super_admin", "manager", "delivery_manager");
    private static final String PARTNER_ROLE = "partner";
    private static final String PARTNER_STAFF_ROLE = "partner_staff";
    private static final Set<String> PARTNER_ROLES = Set.of(PARTNER_ROLE, PARTNER_STAFF_ROLE);

    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public boolean isGlobalInternal(SysUser user) {
        return hasAnyRole(user, GLOBAL_INTERNAL_ROLES);
    }

    public boolean isSuperAdmin(SysUser user) {
        return hasAnyRole(user, Set.of("super_admin"));
    }

    public boolean requiresOwnerScope(SysUser user) {
        if (user == null || user.getId() == null || isGlobalInternal(user)) {
            return false;
        }
        return !isPartnerUser(user) && !isSalesUser(user);
    }

    public boolean isPartnerUser(SysUser user) {
        return hasAnyRole(user, PARTNER_ROLES);
    }

    public boolean isPartnerOwner(SysUser user) {
        return hasAnyRole(user, Set.of(PARTNER_ROLE));
    }

    public boolean isPartnerStaff(SysUser user) {
        return hasAnyRole(user, Set.of(PARTNER_STAFF_ROLE));
    }

    public boolean isSalesUser(SysUser user) {
        return hasAnyRole(user, Set.of("sales"));
    }

    public void applyCompanyScope(LambdaQueryWrapper<Company> wrapper, SysUser user) {
        if (isGlobalInternal(user)) {
            return;
        }
        if (isPartnerUser(user)) {
            applyPartnerCompanyScope(wrapper, user);
            return;
        }
        if (isSalesUser(user) && user != null && user.getId() != null) {
            wrapper.eq(Company::getSalesOwnerId, user.getId());
            return;
        }
        if (requiresOwnerScope(user)) {
            wrapper.eq(Company::getOwnerId, user.getId());
        } else {
            applyNoRows(wrapper);
        }
    }

    public void applyBrandScope(LambdaQueryWrapper<Brand> wrapper, SysUser user) {
        if (isGlobalInternal(user)) {
            return;
        }
        if (isPartnerUser(user)) {
            wrapper.inSql(Brand::getCompanyId, partnerCompanyIdSql(user));
            return;
        }
        if (isSalesUser(user) && user != null && user.getId() != null) {
            wrapper.inSql(Brand::getCompanyId, salesCompanyIdSql(user.getId()));
            return;
        }
        if (requiresOwnerScope(user)) {
            wrapper.inSql(Brand::getCompanyId, ownerCompanyIdSql(user.getId()));
        } else {
            applyNoRows(wrapper);
        }
    }

    public void applyProjectScope(LambdaQueryWrapper<Project> wrapper, SysUser user) {
        if (isGlobalInternal(user)) {
            return;
        }
        if (isPartnerUser(user)) {
            wrapper.inSql(Project::getCompanyId, partnerCompanyIdSql(user));
            return;
        }
        if (isSalesUser(user) && user != null && user.getId() != null) {
            wrapper.inSql(Project::getCompanyId, salesCompanyIdSql(user.getId()));
            return;
        }
        if (requiresOwnerScope(user)) {
            wrapper.inSql(Project::getCompanyId, ownerCompanyIdSql(user.getId()));
        } else {
            applyNoRows(wrapper);
        }
    }

    public void ensureCompanyAccess(SysUser user, Company company, String resourceName) {
        if (isPartnerUser(user)) {
            ensurePartnerCompanyAccess(user, company, resourceName);
            return;
        }
        if (isSalesUser(user)) {
            if (company == null || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
                throw new BizException(403, "No permission to access this " + resourceName);
            }
            return;
        }
        if (!requiresOwnerScope(user)) {
            return;
        }
        if (company == null || company.getOwnerId() == null || !company.getOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this " + resourceName);
        }
    }

    public void ensureBrandAccess(SysUser user, Brand brand, String resourceName) {
        Company company = requireCompany(brand == null ? null : brand.getCompanyId());
        ensureCompanyAccess(user, company, resourceName);
    }

    public void ensureProjectAccess(SysUser user, Project project, String resourceName) {
        Company company = requireCompany(project == null ? null : project.getCompanyId());
        ensureCompanyAccess(user, company, resourceName);
    }

    public Long resolveProjectOwnerId(Project project) {
        Company company = requireCompany(project == null ? null : project.getCompanyId());
        return company.getOwnerId();
    }

    public Long resolveProjectOwnerId(Long projectId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return resolveProjectOwnerId(project);
    }

    public String ownerProjectIdSql(SysUser user) {
        if (user == null || user.getId() == null) {
            return "select id from project where 1 = 0";
        }
        return ownerProjectIdSql(user.getId());
    }

    public String partnerCompanyIdSql(SysUser user) {
        // Only pass the authenticated SysUser here; user id and partner id are embedded into SQL.
        if (!isPartnerUser(user) || user.getId() == null || user.getPartnerId() == null) {
            return "select id from company where 1 = 0";
        }
        if (isPartnerStaff(user)) {
            return "select id from company where deleted_at is null and partner_id = " + user.getPartnerId()
                    + " and partner_staff_owner_id = " + user.getId();
        }
        return "select id from company where deleted_at is null and partner_id = " + user.getPartnerId();
    }

    public String partnerProjectIdSql(SysUser user) {
        // Only pass the authenticated SysUser here; user id and partner id are embedded into SQL.
        if (!isPartnerUser(user) || user.getId() == null || user.getPartnerId() == null) {
            return "select id from project where 1 = 0";
        }
        return "select p.id from project p join company c on c.id = p.company_id "
                + "where p.deleted_at is null and c.deleted_at is null and c.partner_id = " + user.getPartnerId()
                + (isPartnerStaff(user) ? " and c.partner_staff_owner_id = " + user.getId() : "");
    }

    public String visibleCompanyIdSql(SysUser user) {
        // Only pass the authenticated SysUser here; user id and partner id are embedded into SQL.
        if (isGlobalInternal(user)) {
            return null;
        }
        if (isPartnerUser(user)) {
            return partnerCompanyIdSql(user);
        }
        if (isSalesUser(user) && user != null && user.getId() != null) {
            return "select id from company where deleted_at is null and sales_owner_id = " + user.getId();
        }
        if (requiresOwnerScope(user)) {
            return ownerCompanyIdSql(user.getId());
        }
        return "select id from company where 1 = 0";
    }

    public String visibleProjectIdSql(SysUser user) {
        // Only pass the authenticated SysUser here; user id and partner id are embedded into SQL.
        if (isGlobalInternal(user)) {
            return null;
        }
        if (isPartnerUser(user)) {
            return partnerProjectIdSql(user);
        }
        if (isSalesUser(user) && user != null && user.getId() != null) {
            return "select p.id from project p join company c on c.id = p.company_id "
                    + "where p.deleted_at is null and c.deleted_at is null and c.sales_owner_id = " + user.getId();
        }
        if (requiresOwnerScope(user)) {
            return ownerProjectIdSql(user.getId());
        }
        return "select id from project where 1 = 0";
    }

    private Company requireCompany(Long companyId) {
        Company company = companyId == null ? null : companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private void applyPartnerCompanyScope(LambdaQueryWrapper<Company> wrapper, SysUser user) {
        if (user == null || user.getPartnerId() == null) {
            applyNoRows(wrapper);
            return;
        }
        wrapper.eq(Company::getPartnerId, user.getPartnerId());
        if (isPartnerStaff(user)) {
            wrapper.eq(Company::getPartnerStaffOwnerId, user.getId());
        }
    }

    private void ensurePartnerCompanyAccess(SysUser user, Company company, String resourceName) {
        if (user == null || user.getPartnerId() == null) {
            throw new BizException(403, "Partner account missing partner_id binding");
        }
        if (company == null || company.getPartnerId() == null || !company.getPartnerId().equals(user.getPartnerId())) {
            throw new BizException(403, "No permission to access this " + resourceName);
        }
        if (isPartnerStaff(user)
                && (company.getPartnerStaffOwnerId() == null || !company.getPartnerStaffOwnerId().equals(user.getId()))) {
            throw new BizException(403, "No permission to access this " + resourceName);
        }
    }

    private String ownerCompanyIdSql(Long ownerId) {
        // ownerId must come from the authenticated SysUser id; do not pass external request input here.
        return "select id from company where deleted_at is null and owner_id = " + ownerId;
    }

    private String salesCompanyIdSql(Long salesOwnerId) {
        // salesOwnerId must come from the authenticated SysUser id; do not pass external request input here.
        return "select id from company where deleted_at is null and sales_owner_id = " + salesOwnerId;
    }

    private String ownerProjectIdSql(Long ownerId) {
        // ownerId must come from the authenticated SysUser id; do not pass external request input here.
        return "select p.id from project p join company c on c.id = p.company_id "
                + "where p.deleted_at is null and c.deleted_at is null and c.owner_id = " + ownerId;
    }

    public <T> void applyNoRows(LambdaQueryWrapper<T> wrapper) {
        wrapper.apply("1 = 0");
    }

    private boolean hasAnyRole(SysUser user, Set<String> expectedRoles) {
        if (user == null) {
            return false;
        }
        if (expectedRoles.contains(normalizeRole(user.getRole()))) {
            return true;
        }
        if (user.getId() == null) {
            return false;
        }
        var roleKeys = sysUserRoleMapper.selectRoleKeysByUserId(user.getId());
        if (roleKeys == null) {
            return false;
        }
        return roleKeys.stream()
                .map(this::normalizeRole)
                .anyMatch(expectedRoles::contains);
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "";
    }
}
