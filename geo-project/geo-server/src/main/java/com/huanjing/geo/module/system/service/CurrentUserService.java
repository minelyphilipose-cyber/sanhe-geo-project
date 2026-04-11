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

    public SysUser requireCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "Not logged in");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new BizException(401, "User not found or inactive");
        }
        return user;
    }

    public boolean isPartnerUser(SysUser user) {
        return PARTNER_ROLES.contains(user.getRole());
    }

    public void ensureInternalOperator() {
        SysUser user = requireCurrentUser();
        if (isPartnerUser(user)) {
            throw new BizException(403, "Partner accounts are read-only in phase 1");
        }
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
}
