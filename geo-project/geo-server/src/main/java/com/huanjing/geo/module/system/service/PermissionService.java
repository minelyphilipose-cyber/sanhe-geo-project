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
            "super_admin", Set.of("*"),
            "manager", Set.of("user.manage", "partner.read", "partner.write", "company.read", "company.write", "project.read", "project.write"),
            "delivery_manager", Set.of("company.read", "company.write", "project.read", "project.write", "partner.read"),
            "operator", Set.of("company.read", "company.write", "project.read", "project.write", "partner.read"),
            "sales", Set.of("company.read", "company.write", "project.read"),
            "partner", Set.of("partner.read", "company.read", "project.read"),
            "partner_staff", Set.of("partner.read", "company.read", "project.read"),
            "partner_viewer", Set.of("partner.read", "company.read", "project.read")
    );

    private final SysPermissionMapper sysPermissionMapper;

    public Set<String> listPermKeys(SysUser user) {
        List<String> fromDb = sysPermissionMapper.selectPermKeysByUserId(user.getId());
        Set<String> perms = new HashSet<>();
        if (fromDb != null) {
            perms.addAll(fromDb);
        }

        // Fallback compatibility for historical data not fully backfilled.
        Set<String> legacy = LEGACY_ROLE_PERMS.getOrDefault(user.getRole(), Collections.emptySet());
        perms.addAll(legacy);
        return perms;
    }

    public boolean hasPerm(SysUser user, String permKey) {
        Set<String> perms = listPermKeys(user);
        return perms.contains("*") || perms.contains(permKey);
    }
}
