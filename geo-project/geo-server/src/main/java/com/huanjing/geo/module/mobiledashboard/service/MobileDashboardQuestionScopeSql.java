package com.huanjing.geo.module.mobiledashboard.service;

final class MobileDashboardQuestionScopeSql {
    static final String TOKEN = "ENABLED_MONITORING_QUESTION_SCOPE";

    private MobileDashboardQuestionScopeSql() {
    }

    static String apply(String sql, String resultAlias) {
        String alias = requireSafeAlias(resultAlias);
        String condition = """
                EXISTS (
                    SELECT 1
                      FROM keyword_group_result scope_r
                      JOIN keyword_group scope_kg
                        ON scope_kg.id = scope_r.group_id
                       AND COALESCE(scope_kg.deleted, 0) = 0
                      JOIN project_keyword_group_rel scope_rel
                        ON scope_rel.keyword_group_id = scope_r.group_id
                     WHERE scope_r.id = %1$s.keyword_result_id
                       AND scope_rel.project_id = %1$s.project_id
                       AND scope_r.question_tier = 'A'
                       AND scope_r.polling_enabled = 1
                )
                """.formatted(alias);
        return sql.replace(TOKEN, condition.strip());
    }

    private static String requireSafeAlias(String alias) {
        if (alias == null || !alias.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe SQL alias");
        }
        return alias;
    }
}
