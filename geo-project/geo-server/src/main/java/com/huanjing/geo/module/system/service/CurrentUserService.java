package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.SecurityUtils;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final Set<String> PARTNER_ROLES = Set.of("partner", "partner_staff", "partner_viewer");
    private final SysUserMapper sysUserMapper;
    private final PermissionService permissionService;

    public SysUser requireCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "Not logged in");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "User not found or inactive");
        }
        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        Integer tokenVersionInJwt = SecurityUtils.getCurrentTokenVersion();
        if (tokenVersionInJwt == null) {
            tokenVersionInJwt = 0;
        }
        if (!tokenVersion.equals(tokenVersionInJwt)) {
            throw new BizException(401, "Session expired, please login again");
        }
        return user;
    }

    public boolean isPartnerUser(SysUser user) {
        return PARTNER_ROLES.contains(user.getRole());
    }

    public void ensureInternalOperator() {
        SysUser user = requireCurrentUser();
        if (isPartnerUser(user) || !permissionService.hasPerm(user, "company.write")) {
            throw new BizException(403, "No permission for internal operation");
        }
    }

    public void ensureUserManageOperator() {
        ensurePermission("user.manage");
    }

    public Long requirePartnerScope(SysUser user) {
        if (!isPartnerUser(user)) {
            return null;
        }
        if (user.getPartnerId() == null) {
            throw new BizException(403, "Partner account missing partner_id binding");
        }
        return user.getPartnerId();
    }

    public SysUser requireById(Long userId) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId)
        );
        if (user == null) {
            throw new BizException(404, "User not found");
        }
        return user;
    }

    public void ensurePermission(String permKey) {
        SysUser user = requireCurrentUser();
        if (!permissionService.hasPerm(user, permKey)) {
            throw new BizException(403, "No permission: " + permKey);
        }
    }

    public Long resolvePartnerQueryScope(SysUser user, Long requestedPartnerId) {
        Long selfPartnerId = requirePartnerScope(user);
        if (selfPartnerId == null) {
            return requestedPartnerId;
        }
        if (requestedPartnerId != null && !selfPartnerId.equals(requestedPartnerId)) {
            throw new BizException(403, "Cannot query data of other partners");
        }
        return selfPartnerId;
    }

    public void ensurePartnerResourceAccess(SysUser user, Long resourcePartnerId, String resourceName) {
        Long selfPartnerId = requirePartnerScope(user);
        if (selfPartnerId == null) {
            return;
        }
        if (resourcePartnerId == null || !selfPartnerId.equals(resourcePartnerId)) {
            throw new BizException(403, "No permission to access this " + resourceName);
        }
    }
}
