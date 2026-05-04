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

    private final SysPermissionMapper sysPermissionMapper;

    public Set<String> listPermKeys(SysUser user) {
        List<String> fromDb = sysPermissionMapper.selectPermKeysByUserId(user.getId());
        Set<String> perms = new HashSet<>();
        if (fromDb != null) {
            perms.addAll(fromDb);
        }

        // Only super_admin keeps a hardcoded wildcard fallback; normal role grants are DB-authoritative.
        Set<String> legacy = LEGACY_ROLE_PERMS.getOrDefault(user.getRole(), Collections.emptySet());
        perms.addAll(legacy);
        return perms;
    }

    public boolean hasPerm(SysUser user, String permKey) {
        Set<String> perms = listPermKeys(user);
        return perms.contains("*") || perms.contains(permKey);
    }
}
