package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeBudgetConfigRequest;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeBudgetConfigVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileEntityJudgeBudgetService {
    static final String RUN_ID_PREFIX = "mobile_entity_judge:";
    private static final String GLOBAL_SCOPE = "global";
    private static final String PROJECT_SCOPE = "project";

    private final JdbcTemplate jdbcTemplate;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;

    public BudgetDecision allowNextCall(Long projectId) {
        Usage projectDaily = usage(projectId, LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        YearMonth month = YearMonth.now();
        Usage projectMonthly = usage(projectId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        Usage globalDaily = usage(null, LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        Usage globalMonthly = usage(null, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        List<BudgetConfig> configs = effectiveConfigs(projectId);
        for (BudgetConfig config : configs) {
            if (!config.enabled()) {
                continue;
            }
            String prefix = PROJECT_SCOPE.equals(config.scopeType()) ? "项目预算" : "全局预算";
            Usage daily = PROJECT_SCOPE.equals(config.scopeType()) ? projectDaily : globalDaily;
            Usage monthly = PROJECT_SCOPE.equals(config.scopeType()) ? projectMonthly : globalMonthly;
            if (config.dailyCallLimit() != null && daily.calls() >= config.dailyCallLimit()) {
                return BudgetDecision.blocked(prefix + "今日调用量已达上限 " + config.dailyCallLimit());
            }
            if (config.monthlyCallLimit() != null && monthly.calls() >= config.monthlyCallLimit()) {
                return BudgetDecision.blocked(prefix + "本月调用量已达上限 " + config.monthlyCallLimit());
            }
            if (config.dailyEstimatedCostLimit() != null && daily.estimatedCost().compareTo(config.dailyEstimatedCostLimit()) >= 0) {
                return BudgetDecision.blocked(prefix + "今日估算成本已达上限 " + config.dailyEstimatedCostLimit());
            }
            if (config.monthlyEstimatedCostLimit() != null && monthly.estimatedCost().compareTo(config.monthlyEstimatedCostLimit()) >= 0) {
                return BudgetDecision.blocked(prefix + "本月估算成本已达上限 " + config.monthlyEstimatedCostLimit());
            }
        }
        return BudgetDecision.allow();
    }

    public EntityJudgeBudgetConfigVO getProjectConfig(Long projectId) {
        requireProjectExport(projectId);
        BudgetConfig config = loadConfig(PROJECT_SCOPE, projectId);
        return config == null ? emptyProjectConfig(projectId) : toVO(config);
    }

    public EntityJudgeBudgetConfigVO updateProjectConfig(Long projectId, EntityJudgeBudgetConfigRequest request) {
        requireProjectExport(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        EntityJudgeBudgetConfigRequest safe = request == null ? new EntityJudgeBudgetConfigRequest() : request;
        jdbcTemplate.update("""
                INSERT INTO mobile_entity_judge_budget_config (
                  scope_type, project_id, enabled, daily_call_limit, monthly_call_limit,
                  daily_estimated_cost_limit, monthly_estimated_cost_limit, updated_by
                ) VALUES ('project', ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  enabled = VALUES(enabled),
                  daily_call_limit = VALUES(daily_call_limit),
                  monthly_call_limit = VALUES(monthly_call_limit),
                  daily_estimated_cost_limit = VALUES(daily_estimated_cost_limit),
                  monthly_estimated_cost_limit = VALUES(monthly_estimated_cost_limit),
                  updated_by = VALUES(updated_by),
                  updated_at = CURRENT_TIMESTAMP
                """, projectId, Boolean.FALSE.equals(safe.getEnabled()) ? 0 : 1,
                positiveOrNull(safe.getDailyCallLimit()), positiveOrNull(safe.getMonthlyCallLimit()),
                positiveOrNull(safe.getDailyEstimatedCostLimit()), positiveOrNull(safe.getMonthlyEstimatedCostLimit()),
                user.getId());
        return getProjectConfig(projectId);
    }

    public Usage usage(Long projectId, LocalDateTime from, LocalDateTime to) {
        if (projectId == null) {
            Usage usage = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) AS calls,
                           COALESCE(SUM(estimated_cost), 0) AS estimated_cost
                      FROM llm_call_observation
                     WHERE run_id LIKE ?
                       AND occurred_at >= ?
                       AND occurred_at < ?
                    """, (rs, rowNum) -> new Usage(
                    rs.getLong("calls"),
                    nvl(rs.getBigDecimal("estimated_cost"))
            ), RUN_ID_PREFIX + "%", Timestamp.valueOf(from), Timestamp.valueOf(to));
            return usage == null ? new Usage(0, BigDecimal.ZERO) : usage;
        }
        Usage usage = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS calls,
                       COALESCE(SUM(estimated_cost), 0) AS estimated_cost
                  FROM llm_call_observation
                 WHERE project_id = ?
                   AND run_id LIKE ?
                   AND occurred_at >= ?
                   AND occurred_at < ?
                """, (rs, rowNum) -> new Usage(
                rs.getLong("calls"),
                nvl(rs.getBigDecimal("estimated_cost"))
        ), projectId, RUN_ID_PREFIX + "%", Timestamp.valueOf(from), Timestamp.valueOf(to));
        return usage == null ? new Usage(0, BigDecimal.ZERO) : usage;
    }

    private List<BudgetConfig> effectiveConfigs(Long projectId) {
        List<BudgetConfig> configs = new ArrayList<>();
        BudgetConfig global = loadConfig(GLOBAL_SCOPE, null);
        BudgetConfig project = loadConfig(PROJECT_SCOPE, projectId);
        if (global != null) {
            configs.add(global);
        }
        if (project != null) {
            configs.add(project);
        }
        configs.sort(Comparator.comparing(BudgetConfig::scopeType));
        return configs;
    }

    private BudgetConfig loadConfig(String scopeType, Long projectId) {
        List<BudgetConfig> rows = jdbcTemplate.query("""
                SELECT id, scope_type, project_id, enabled, daily_call_limit, monthly_call_limit,
                       daily_estimated_cost_limit, monthly_estimated_cost_limit, updated_by, updated_at
                  FROM mobile_entity_judge_budget_config
                 WHERE scope_type = ?
                   AND ((? IS NULL AND project_id IS NULL) OR project_id = ?)
                 LIMIT 1
                """, (rs, rowNum) -> new BudgetConfig(
                rs.getLong("id"),
                rs.getString("scope_type"),
                nullableLong(rs.getLong("project_id"), rs.wasNull()),
                rs.getBoolean("enabled"),
                nullableInt(rs.getInt("daily_call_limit"), rs.wasNull()),
                nullableInt(rs.getInt("monthly_call_limit"), rs.wasNull()),
                rs.getBigDecimal("daily_estimated_cost_limit"),
                rs.getBigDecimal("monthly_estimated_cost_limit"),
                nullableLong(rs.getLong("updated_by"), rs.wasNull()),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        ), scopeType, projectId, projectId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private EntityJudgeBudgetConfigVO emptyProjectConfig(Long projectId) {
        EntityJudgeBudgetConfigVO vo = new EntityJudgeBudgetConfigVO();
        vo.setScopeType(PROJECT_SCOPE);
        vo.setProjectId(projectId);
        vo.setEnabled(true);
        return vo;
    }

    private EntityJudgeBudgetConfigVO toVO(BudgetConfig config) {
        EntityJudgeBudgetConfigVO vo = new EntityJudgeBudgetConfigVO();
        vo.setId(config.id());
        vo.setScopeType(config.scopeType());
        vo.setProjectId(config.projectId());
        vo.setEnabled(config.enabled());
        vo.setDailyCallLimit(config.dailyCallLimit());
        vo.setMonthlyCallLimit(config.monthlyCallLimit());
        vo.setDailyEstimatedCostLimit(config.dailyEstimatedCostLimit());
        vo.setMonthlyEstimatedCostLimit(config.monthlyEstimatedCostLimit());
        vo.setUpdatedBy(config.updatedBy());
        vo.setUpdatedAt(config.updatedAt());
        return vo;
    }

    private Project requireProjectExport(Long projectId) {
        currentUserService.ensurePermission("project.report.export");
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(user, project, "project");
        return project;
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? null : value;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer nullableInt(int value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record BudgetConfig(Long id,
                                String scopeType,
                                Long projectId,
                                boolean enabled,
                                Integer dailyCallLimit,
                                Integer monthlyCallLimit,
                                BigDecimal dailyEstimatedCostLimit,
                                BigDecimal monthlyEstimatedCostLimit,
                                Long updatedBy,
                                LocalDateTime updatedAt) {
    }

    public record BudgetDecision(boolean allowed, String reason) {
        static BudgetDecision allow() {
            return new BudgetDecision(true, null);
        }

        static BudgetDecision blocked(String reason) {
            return new BudgetDecision(false, reason);
        }
    }

    public record Usage(long calls, BigDecimal estimatedCost) {
    }
}
