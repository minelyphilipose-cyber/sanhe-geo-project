package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.*;
import com.huanjing.geo.module.system.entity.SysRole;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.entity.SysUserRole;
import com.huanjing.geo.module.system.mapper.SysRoleMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final String REMOVED_PARTNER_VIEWER_ROLE = "partner_viewer";
    private static final Set<String> PARTNER_ROLE_KEYS = Set.of("partner", "partner_staff");
    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final CurrentUserService currentUserService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public Page<UserAdminVO> page(long current, long size, String keyword, String roleKey, Long partnerId, Boolean isActive) {
        currentUserService.ensurePermission("user.manage");

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getDisplayName, keyword)
                    .or().like(SysUser::getPhone, keyword)
                    .or().like(SysUser::getEmail, keyword));
        }
        if (partnerId != null) {
            wrapper.eq(SysUser::getPartnerId, partnerId);
        }
        if (isActive != null) {
            wrapper.eq(SysUser::getIsActive, isActive);
        }
        if (StringUtils.hasText(roleKey)) {
            List<Long> userIds = sysUserRoleMapper.selectUserIdsByRoleKey(roleKey);
            if (userIds == null || userIds.isEmpty()) {
                return new Page<>(current, size, 0);
            }
            wrapper.in(SysUser::getId, userIds);
        }

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(current, size), wrapper);
        List<UserAdminVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        Page<UserAdminVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    public UserAdminVO detail(Long id) {
        currentUserService.ensurePermission("user.manage");
        return toVO(requireUser(id));
    }

    @Transactional
    public UserAdminVO create(UserCreateRequest req) {
        currentUserService.ensurePermission("user.manage");

        SysUser existed = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (existed != null) {
            throw new BizException(400, "username already exists");
        }

        SysRole role = requireRole(req.getRoleKey());
        validateRolePartnerBinding(role.getRoleKey(), req.getPartnerId());

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        user.setRole(role.getRoleKey());
        user.setPartnerId(req.getPartnerId());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setIsActive(req.getIsActive() == null || req.getIsActive());
        user.setTokenVersion(0);
        sysUserMapper.insert(user);

        bindSingleRole(user.getId(), role.getId());
        return toVO(requireUser(user.getId()));
    }

    @Transactional
    public UserAdminVO update(Long userId, UserUpdateRequest req) {
        currentUserService.ensurePermission("user.manage");

        SysUser user = requireUser(userId);
        Long targetPartnerId = req.getPartnerId() != null ? req.getPartnerId() : user.getPartnerId();
        validateRolePartnerBinding(user.getRole(), targetPartnerId);

        user.setDisplayName(req.getDisplayName());
        user.setPartnerId(targetPartnerId);
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setTokenVersion(nextTokenVersion(user));
        sysUserMapper.updateById(user);
        revokeRefreshToken(userId);
        return toVO(requireUser(userId));
    }

    @Transactional
    public void updateStatus(Long userId, Boolean isActive) {
        currentUserService.ensurePermission("user.manage");
        SysUser user = requireUser(userId);
        user.setIsActive(isActive);
        user.setTokenVersion(nextTokenVersion(user));
        sysUserMapper.updateById(user);
        if (Boolean.FALSE.equals(isActive) && "partner".equals(user.getRole()) && user.getPartnerId() != null) {
            deactivatePartnerStaffAccounts(user.getPartnerId());
        }
        revokeRefreshToken(userId);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        currentUserService.ensurePermission("user.manage");
        SysUser user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(nextTokenVersion(user));
        sysUserMapper.updateById(user);
        revokeRefreshToken(userId);
    }

    @Transactional
    public UserAdminVO bindRole(Long userId, String roleKey) {
        currentUserService.ensurePermission("user.manage");

        SysUser user = requireUser(userId);
        SysRole role = requireRole(roleKey);
        validateRolePartnerBinding(role.getRoleKey(), user.getPartnerId());

        user.setRole(role.getRoleKey());
        user.setTokenVersion(nextTokenVersion(user));
        sysUserMapper.updateById(user);
        bindSingleRole(userId, role.getId());
        revokeRefreshToken(userId);
        return toVO(requireUser(userId));
    }

    public List<RoleSimpleVO> roleOptions() {
        currentUserService.ensurePermission("user.manage");
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, "active")
                        .orderByAsc(SysRole::getSortOrder)
                        .orderByAsc(SysRole::getId)
        );
        return roles.stream().map(r -> {
            RoleSimpleVO vo = new RoleSimpleVO();
            vo.setId(r.getId());
            vo.setRoleKey(r.getRoleKey());
            vo.setRoleName(r.getRoleName());
            vo.setRoleType(r.getRoleType());
            vo.setStatus(r.getStatus());
            vo.setSortOrder(r.getSortOrder());
            return vo;
        }).collect(Collectors.toList());
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "User not found");
        }
        return user;
    }

    private SysRole requireRole(String roleKey) {
        if (REMOVED_PARTNER_VIEWER_ROLE.equals(roleKey)) {
            throw new BizException(400, "role not found or inactive");
        }
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleKey, roleKey)
                        .eq(SysRole::getStatus, "active")
        );
        if (role == null) {
            throw new BizException(400, "role not found or inactive");
        }
        return role;
    }

    private void bindSingleRole(Long userId, Long roleId) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        sysUserRoleMapper.insert(relation);
    }

    private void validateRolePartnerBinding(String roleKey, Long partnerId) {
        if (REMOVED_PARTNER_VIEWER_ROLE.equals(roleKey)) {
            throw new BizException(400, "partner_viewer role has been removed");
        }
        if (PARTNER_ROLE_KEYS.contains(roleKey) && partnerId == null) {
            throw new BizException(400, "partner role must bind partnerId");
        }
        if (!PARTNER_ROLE_KEYS.contains(roleKey) && partnerId != null) {
            throw new BizException(400, "internal role should not bind partnerId");
        }
    }

    private UserAdminVO toVO(SysUser user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPrimaryRole(user.getRole());
        vo.setPartnerId(user.getPartnerId());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setIsActive(user.getIsActive());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        List<String> roleKeys = sysUserRoleMapper.selectRoleKeysByUserId(user.getId());
        if (roleKeys == null || roleKeys.isEmpty()) {
            roleKeys = StringUtils.hasText(user.getRole())
                    ? Collections.singletonList(user.getRole())
                    : Collections.emptyList();
        }
        vo.setRoleKeys(roleKeys);
        return vo;
    }

    private int nextTokenVersion(SysUser user) {
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return current + 1;
    }

    private void deactivatePartnerStaffAccounts(Long partnerId) {
        List<SysUser> staffUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPartnerId, partnerId)
                .eq(SysUser::getRole, "partner_staff")
                .eq(SysUser::getIsActive, true));
        for (SysUser staff : staffUsers) {
            staff.setIsActive(false);
            staff.setTokenVersion(nextTokenVersion(staff));
            sysUserMapper.updateById(staff);
            revokeRefreshToken(staff.getId());
        }
    }

    private void revokeRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }
}
