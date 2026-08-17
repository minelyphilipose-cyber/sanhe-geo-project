package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DataRetentionExecutionAuthorizationPolicy {
    public static final String MANUAL = "data.retention.execute.manual";
    public static final String SCHEDULER = "data.retention.execute.scheduler";
    public static final String CANARY = "data.retention.execute.canary";
    public static final String RETRY = "data.retention.execute.retry";
    public static final String RECONCILE = "data.retention.execute.reconcile";
    private static final Set<String> EXTERNAL = Set.of("partner", "partner_staff", "partner_viewer");

    private final JdbcTemplate jdbc;

    public DataRetentionExecutionAuthorizationPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireOperator(long userId, ExecutionSource source) {
        requirePermissions(userId, permission(source));
    }

    public void requireDeferredRetryOperator(long userId) {
        requirePermissions(userId, SCHEDULER, RETRY);
    }

    public void requireReconcileOperator(long userId) {
        requirePermissions(userId, RECONCILE);
    }

    private void requirePermissions(long userId, String... permissions) {
        if (userId <= 0) denied("operator is not configured");
        List<OperatorRow> rows = jdbc.query("SELECT id, role, is_active FROM sys_user WHERE id = ?",
                (rs, n) -> new OperatorRow(rs.getLong("id"), rs.getString("role"), rs.getBoolean("is_active")), userId);
        if (rows.size() != 1 || !rows.get(0).active()
                || EXTERNAL.contains(normalize(rows.get(0).role()))) denied("operator is not active/internal");
        for (String permission : permissions) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                      FROM sys_user_role ur
                      JOIN sys_role r ON r.id = ur.role_id AND r.status = 'active' AND r.role_type = 'internal'
                      JOIN sys_role_permission rp ON rp.role_id = r.id
                      JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'active'
                     WHERE ur.user_id = ? AND p.perm_key = ?
                    """, Integer.class, userId, permission);
            if (count == null || count < 1) denied("operator lacks " + permission);
        }
    }

    private static String permission(ExecutionSource source) {
        if (source == null) denied("execution source is missing");
        return switch (source) {
            case MANUAL -> MANUAL;
            case SCHEDULER -> SCHEDULER;
            case CANARY -> CANARY;
            case RETRY -> RETRY;
        };
    }

    private static void denied(String message) {
        throw new DataRetentionExecutionException(403, "EXECUTE_PERMISSION_DENIED", message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record OperatorRow(long id, String role, boolean active) { }
}
