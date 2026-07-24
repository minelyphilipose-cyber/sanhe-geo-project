package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final Map<String, Set<String>> LEGACY_ROLE_PERMS = Map.of(
            "super_admin", Set.of("*")
    );
    private static final Map<String, Set<String>> ROLE_READ_FALLBACK_PERMS = Map.of(
            "sales", Set.of("workbench.sales.read", "company.read", "project.read")
    );

    private final SysPermissionMapper sysPermissionMapper;

    public Set<String> listPermKeys(SysUser user) {
        List<String> fromDb = sysPermissionMapper.selectPermKeysByUserId(user.getId());
        Set<String> perms = new HashSet<>();
        if (fromDb != null) {
            perms.addAll(fromDb);
        }

        Set<String> legacy = LEGACY_ROLE_PERMS.getOrDefault(user.getRole(), Collections.emptySet());
        perms.addAll(legacy);
        perms.addAll(ROLE_READ_FALLBACK_PERMS.getOrDefault(normalizeRole(user.getRole()), Collections.emptySet()));
        return perms;
    }

    public boolean hasPerm(SysUser user, String permKey) {
        Set<String> perms = listPermKeys(user);
        return perms.contains("*") || perms.contains(permKey);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }
}
