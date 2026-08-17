package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DataRetentionDynamicSwitchAuthorizationPolicy {

    public static final String READ = "data.retention.switch.read";
    public static final String DISABLE = "data.retention.switch.disable";
    public static final String EMERGENCY_DISABLE = "data.retention.switch.emergency_disable";
    public static final String ENABLE = "data.retention.switch.enable";
    public static final String APPROVE = "data.retention.switch.approve";
    public static final String RECONCILE = "data.retention.switch.reconcile";
    private static final Set<String> EXTERNAL_ROLES = Set.of("partner", "partner_staff", "partner_viewer");

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbc;

    public DataRetentionDynamicSwitchAuthorizationPolicy(CurrentUserService currentUserService, JdbcTemplate jdbc) {
        this.currentUserService = currentUserService;
        this.jdbc = jdbc;
    }

    public SysUser requireCurrent(String permission) {
        SysUser user = currentUserService.requireCurrentUser();
        requireInternal(user);
        currentUserService.ensurePermission(permission);
        return user;
    }

    public void validateReconcileOperator(long userId) {
        if (userId <= 0) {
            throw new DataRetentionDynamicSwitchModels.DynamicSwitchException(503,
                    "RECONCILE_OPERATOR_MISSING", "Dynamic switch reconcile operator is not configured");
        }
        List<OperatorRow> rows = jdbc.query("""
                SELECT u.id, u.role, u.is_active,
                       EXISTS(
                         SELECT 1
                           FROM sys_user_role ur
                           JOIN sys_role r ON r.id = ur.role_id
                           JOIN sys_role_permission rp ON rp.role_id = ur.role_id
                           JOIN sys_permission p ON p.id = rp.permission_id
                          WHERE ur.user_id = u.id
                            AND r.status = 'active'
                            AND r.role_type = 'internal'
                            AND p.perm_key = ?
                            AND p.status = 'active'
                       ) AS allowed
                  FROM sys_user u WHERE u.id = ?
                """, (rs, rowNum) -> new OperatorRow(rs.getLong("id"), rs.getString("role"),
                rs.getBoolean("is_active"), rs.getBoolean("allowed")), RECONCILE, userId);
        if (rows.size() != 1) {
            throw new DataRetentionDynamicSwitchModels.DynamicSwitchException(503,
                    "RECONCILE_OPERATOR_INVALID", "Dynamic switch reconcile operator does not exist");
        }
        OperatorRow operator = rows.get(0);
        if (!operator.active() || EXTERNAL_ROLES.contains(normalizeRole(operator.role())) || !operator.allowed()) {
            throw new DataRetentionDynamicSwitchModels.DynamicSwitchException(503,
                    "RECONCILE_OPERATOR_INVALID", "Dynamic switch reconcile operator is not active/internal/authorized");
        }
    }

    private static void requireInternal(SysUser user) {
        if (user == null || EXTERNAL_ROLES.contains(normalizeRole(user.getRole()))) {
            throw new BizException(403, "Only an internal operator may manage data retention switches");
        }
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private record OperatorRow(long id, String role, boolean active, boolean allowed) {
    }
}
