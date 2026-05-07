package com.huanjing.geo.module.customer.access;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.ActorType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrandAccessService {

    private static final Set<String> GLOBAL_BRAND_ROLES = Set.of(
            "super_admin", "manager", "delivery_manager"
    );
    private static final Set<String> PARTNER_ROLES = Set.of("partner", "partner_staff", "partner_viewer");

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final BrandOperatorAssignmentMapper assignmentMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public Brand requireCurrentUserBrandAccess(Long brandId, BrandAccessAction action) {
        SysUser current = currentUserService.requireCurrentUser();
        return requireBrandAccess(brandId, current.getId(), action);
    }

    /**
     * Brand-level data-scope gate for internal semi-auto self-media operations.
     *
     * <p>Role resolution is intentionally compatible with both the legacy {@code sys_user.role}
     * column and the newer {@code sys_user_role/sys_role} RBAC table. The legacy column keeps
     * older accounts working; the RBAC table is the forward-compatible source for multi-role
     * users.</p>
     *
     * <p>Partner users have read-only access to brands in their partner scope regardless of
     * {@code brand_operator_assignment}; partner accounts must not trigger semi-auto operations
     * even if mistakenly assigned PRIMARY/SECONDARY.</p>
     *
     * Checks brand-level access for a concrete operator id.
     *
     * <p>Security contract: this is the central data-scope gate for semi-auto self-media
     * operations. Credential decrypt/fill-token paths must call this method before touching
     * {@code CredentialVaultService}. Permission deny is audited explicitly here instead of via
     * {@code @AuditOperation}, because allowed checks are high-volume and should not create
     * misleading {@code PERMISSION_DENY/SUCCESS} rows.</p>
     */
    public Brand requireBrandAccess(Long brandId, Long operatorId, BrandAccessAction action) {
        return checkBrandAccess(brandId, operatorId, action, true);
    }

    public boolean hasBrandAccess(Long brandId, Long operatorId, BrandAccessAction action) {
        if (brandId == null) {
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_BAD_REQUEST, "brandId is required");
        }
        if (operatorId == null) {
            return false;
        }
        try {
            checkBrandAccess(brandId, operatorId, action, false);
            return true;
        } catch (BizException ex) {
            return false;
        }
    }

    public List<Long> listAccessibleBrandIds(Long operatorId, BrandAccessAction action) {
        if (operatorId == null) {
            return List.of();
        }
        SysUser operator;
        try {
            operator = requireActiveOperator(operatorId, null, action, false);
        } catch (BizException ex) {
            return List.of();
        }
        BrandAccessAction resolved = action == null ? BrandAccessAction.READ : action;
        if (!hasBasePermission(operator, resolved) || isPartnerUser(operator) && resolved != BrandAccessAction.READ) {
            return List.of();
        }
        if (hasGlobalBrandRole(operator)) {
            return brandMapper.selectActiveBrandIds();
        }
        return assignmentMapper.selectActiveBrandIdsByRoles(operatorId, assignmentRolesFor(resolved));
    }

    private Brand checkBrandAccess(Long brandId, Long operatorId, BrandAccessAction action, boolean auditDenied) {
        if (brandId == null) {
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_BAD_REQUEST, "brandId is required");
        }
        if (operatorId == null) {
            if (auditDenied) {
                auditPermissionDenied(null, brandId, action, "OPERATOR_REQUIRED");
            }
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED, "Not logged in");
        }
        Brand brand = requireBrand(brandId, operatorId, action, auditDenied);
        SysUser operator = requireActiveOperator(operatorId, brandId, action, auditDenied);
        AccessDecision decision = decideAccess(operator, brand, action == null ? BrandAccessAction.READ : action, auditDenied);
        if (decision.allowed()) {
            return brand;
        }
        if (auditDenied) {
            auditPermissionDenied(operatorId, brandId, action, decision.reason());
        }
        throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "No permission to access this brand");
    }

    private AccessDecision decideAccess(SysUser operator, Brand brand, BrandAccessAction action, boolean auditDenied) {
        if (!hasBasePermission(operator, action)) {
            return AccessDecision.deny("BASE_PERMISSION_MISSING");
        }
        if (hasGlobalBrandRole(operator)) {
            return AccessDecision.allow();
        }
        if (isPartnerUser(operator)) {
            return partnerReadAccess(operator, brand, action, auditDenied);
        }
        String assignmentRole = assignmentMapper.selectActiveRole(brand.getId(), operator.getId());
        if (!StringUtils.hasText(assignmentRole)) {
            return AccessDecision.deny("ASSIGNMENT_MISSING");
        }
        if (assignmentAllows(assignmentRole, action)) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny("ASSIGNMENT_ROLE_DENIED");
    }

    private boolean hasBasePermission(SysUser operator, BrandAccessAction action) {
        String permission = action == BrandAccessAction.MANAGE ? "brand.update" : "company.read";
        return permissionService.hasPerm(operator, permission);
    }

    private AccessDecision partnerReadAccess(SysUser operator, Brand brand, BrandAccessAction action, boolean auditDenied) {
        if (action != BrandAccessAction.READ) {
            return AccessDecision.deny("PARTNER_OPERATE_DENIED");
        }
        Company company = requireCompany(brand.getCompanyId(), operator.getId(), brand.getId(), action, auditDenied);
        if (operator.getPartnerId() == null || !operator.getPartnerId().equals(company.getPartnerId())) {
            return AccessDecision.deny("PARTNER_SCOPE_DENIED");
        }
        return AccessDecision.allow();
    }

    private boolean assignmentAllows(String assignmentRole, BrandAccessAction action) {
        String role = assignmentRole.trim().toUpperCase(Locale.ROOT);
        return switch (role) {
            case "PRIMARY" -> true;
            case "SECONDARY" -> action != BrandAccessAction.MANAGE;
            case "VIEWER" -> action == BrandAccessAction.READ;
            default -> false;
        };
    }

    private List<String> assignmentRolesFor(BrandAccessAction action) {
        return switch (action) {
            case MANAGE -> List.of("PRIMARY");
            case OPERATE -> List.of("PRIMARY", "SECONDARY");
            case READ -> List.of("PRIMARY", "SECONDARY", "VIEWER");
        };
    }

    private boolean hasGlobalBrandRole(SysUser operator) {
        if (operator == null) {
            return false;
        }
        if (GLOBAL_BRAND_ROLES.contains(normalizeRole(operator.getRole()))) {
            return true;
        }
        var roleKeys = sysUserRoleMapper.selectRoleKeysByUserId(operator.getId());
        if (roleKeys == null) {
            return false;
        }
        return roleKeys.stream()
                .map(this::normalizeRole)
                .anyMatch(GLOBAL_BRAND_ROLES::contains);
    }

    private boolean isPartnerUser(SysUser operator) {
        return PARTNER_ROLES.contains(normalizeRole(operator.getRole()));
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "";
    }

    private Brand requireBrand(Long brandId, Long operatorId, BrandAccessAction action, boolean auditDenied) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            if (auditDenied) {
                auditPermissionDenied(operatorId, brandId, action, "BRAND_NOT_FOUND");
            }
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND, "Brand not found");
        }
        return brand;
    }

    private Company requireCompany(Long companyId, Long operatorId, Long brandId, BrandAccessAction action, boolean auditDenied) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            if (auditDenied) {
                auditPermissionDenied(operatorId, brandId, action, "COMPANY_NOT_FOUND");
            }
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND, "Company not found");
        }
        return company;
    }

    private SysUser requireActiveOperator(Long operatorId, Long brandId, BrandAccessAction action, boolean auditDenied) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            if (auditDenied) {
                auditPermissionDenied(operatorId, brandId, action, "OPERATOR_NOT_FOUND");
            }
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED, "User not found or inactive");
        }
        if (Boolean.FALSE.equals(operator.getIsActive())) {
            if (auditDenied) {
                auditPermissionDenied(operatorId, brandId, action, "OPERATOR_INACTIVE");
            }
            throw new BizException(BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED, "User not found or inactive");
        }
        return operator;
    }

    private void auditPermissionDenied(Long operatorId, Long brandId, BrandAccessAction action, String reason) {
        AuditEvent event = new AuditEvent();
        event.setEventType("PERMISSION_DENY");
        event.setActorType(operatorId == null ? ActorType.UNAUTHENTICATED : ActorType.OPERATOR);
        event.setActorId(operatorId);
        event.setBrandId(brandId);
        event.setTargetType("BRAND");
        event.setTargetId(brandId == null ? null : String.valueOf(brandId));
        event.setResult(AuditResult.DENIED);
        event.setSensitive(true);
        event.setMode(AuditMode.SYNC);
        event.setErrorCode(String.valueOf(BrandAccessErrorCodes.BRAND_ACCESS_DENIED));
        event.setErrorMessage(reason);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action == null ? BrandAccessAction.READ.name() : action.name());
        detail.put("reason", reason);
        event.setDetail(detail);
        auditService.record(event);
    }

    private record AccessDecision(boolean allowed, String reason) {
        static AccessDecision allow() {
            return new AccessDecision(true, null);
        }

        static AccessDecision deny(String reason) {
            return new AccessDecision(false, reason);
        }
    }
}
