package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardOperationsVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardShareAccessSummaryVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileDashboardOpsService {
    private static final int COVERAGE_THRESHOLD_PERCENT = 80;
    private static final String MOBILE_QUESTION_TIER = "A";
    private static final long SHARE_RISK_DISTINCT_IP_THRESHOLD = 20L;
    private static final String ENTITY_JUDGE_RUN_PREFIX = "mobile_entity_judge:%";

    private final JdbcTemplate jdbcTemplate;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;

    public List<MobileDashboardShareAccessSummaryVO> shareAccessSummary(Long projectId) {
        requireProjectExport(projectId);
        return jdbcTemplate.query("""
                SELECT s.id AS share_id,
                       COALESCE(COUNT(l.id), 0) AS total_access,
                       COALESCE(SUM(CASE WHEN l.success = 1 THEN 1 ELSE 0 END), 0) AS success_access,
                       COALESCE(SUM(CASE WHEN l.success = 0 THEN 1 ELSE 0 END), 0) AS failed_access,
                       COALESCE(COUNT(DISTINCT l.client_ip_hash), 0) AS distinct_ip_count,
                       MAX(l.created_at) AS last_access_at,
                       (SELECT l2.fail_reason
                          FROM mobile_dashboard_access_log l2
                         WHERE l2.share_id = s.id
                           AND l2.success = 0
                         ORDER BY l2.created_at DESC, l2.id DESC
                         LIMIT 1) AS latest_fail_reason,
                       (SELECT l3.user_agent
                          FROM mobile_dashboard_access_log l3
                         WHERE l3.share_id = s.id
                         ORDER BY l3.created_at DESC, l3.id DESC
                         LIMIT 1) AS latest_user_agent
                  FROM mobile_dashboard_share s
                  LEFT JOIN mobile_dashboard_access_log l ON l.share_id = s.id
                 WHERE s.project_id = ?
                 GROUP BY s.id
                 ORDER BY s.created_at DESC, s.id DESC
                """, (rs, rowNum) -> new MobileDashboardShareAccessSummaryVO(
                rs.getLong("share_id"),
                rs.getLong("total_access"),
                rs.getLong("success_access"),
                rs.getLong("failed_access"),
                rs.getLong("distinct_ip_count"),
                toLocalDateTime(rs.getTimestamp("last_access_at")),
                rs.getString("latest_fail_reason"),
                rs.getString("latest_user_agent")
        ), projectId);
    }

    public MobileDashboardOperationsVO operations(Long projectId, LocalDate startDate, LocalDate endDate) {
        requireProjectExport(projectId);
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(13) : startDate;
        MobileDashboardOperationsVO vo = new MobileDashboardOperationsVO();
        vo.setProjectId(projectId);
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setJudgeHealth(loadJudgeHealth(projectId, start, end));
        vo.setApiErrorStats(loadApiErrorStats(projectId, start, end));
        vo.setLlmUsage(loadLlmUsage(projectId, start, end));
        vo.setShareRisks(loadShareRisks(projectId, start, end));
        return vo;
    }

    private MobileDashboardOperationsVO.JudgeHealth loadJudgeHealth(Long projectId, LocalDate start, LocalDate end) {
        MobileDashboardOperationsVO.JudgeHealth health = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(expected_count), 0) AS expected_count,
                       COALESCE(SUM(success_count), 0) AS success_count,
                       MAX(recomputed_at) AS last_recomputed_at
                 FROM poll_entity_judge_daily_summary
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                   AND entity_type = 'focus_brand'
                   AND entity_ref_id = 0
                """, (rs, rowNum) -> {
            MobileDashboardOperationsVO.JudgeHealth item = new MobileDashboardOperationsVO.JudgeHealth();
            long expected = rs.getLong("expected_count");
            long success = rs.getLong("success_count");
            item.setExpectedCount(expected);
            item.setSuccessCount(success);
            item.setCoveragePercent(percent(success, expected));
            item.setCoverageReady(expected > 0 && success * 100 >= expected * COVERAGE_THRESHOLD_PERCENT);
            item.setLastRecomputedAt(toLocalDateTime(rs.getTimestamp("last_recomputed_at")));
            return item;
        }, projectId, Date.valueOf(start), Date.valueOf(end), MOBILE_QUESTION_TIER);
        if (health == null) {
            health = new MobileDashboardOperationsVO.JudgeHealth();
        }
        Long failed = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                 FROM poll_result_entity_judge
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                   AND entity_type = 'focus_brand'
                   AND entity_ref_id = 0
                   AND judge_status = 'failed'
                """, Long.class, projectId, Date.valueOf(start), Date.valueOf(end), MOBILE_QUESTION_TIER);
        health.setFailedCount(failed == null ? 0L : failed);
        return health;
    }

    private MobileDashboardOperationsVO.ApiErrorStats loadApiErrorStats(Long projectId, LocalDate start, LocalDate end) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        MobileDashboardOperationsVO.ApiErrorStats stats = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS total,
                       COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failed
                  FROM mobile_dashboard_access_log
                 WHERE project_id = ?
                   AND created_at >= ?
                   AND created_at < ?
                """, (rs, rowNum) -> {
            MobileDashboardOperationsVO.ApiErrorStats item = new MobileDashboardOperationsVO.ApiErrorStats();
            long total = rs.getLong("total");
            long failed = rs.getLong("failed");
            item.setTotal(total);
            item.setFailed(failed);
            item.setErrorRatePercent(percent(failed, total));
            return item;
        }, projectId, Timestamp.valueOf(from), Timestamp.valueOf(to));
        if (stats == null) {
            stats = new MobileDashboardOperationsVO.ApiErrorStats();
        }
        stats.setEndpoints(jdbcTemplate.query("""
                SELECT event_type,
                       COUNT(1) AS total,
                       COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failed,
                       (SELECT l2.fail_reason
                          FROM mobile_dashboard_access_log l2
                         WHERE l2.project_id = l.project_id
                           AND l2.event_type = l.event_type
                           AND l2.success = 0
                           AND l2.created_at >= ?
                           AND l2.created_at < ?
                         ORDER BY l2.created_at DESC, l2.id DESC
                         LIMIT 1) AS latest_fail_reason
                  FROM mobile_dashboard_access_log l
                 WHERE l.project_id = ?
                   AND l.created_at >= ?
                   AND l.created_at < ?
                 GROUP BY event_type
                 ORDER BY failed DESC, total DESC
                """, (rs, rowNum) -> {
            MobileDashboardOperationsVO.ApiEndpointStats item = new MobileDashboardOperationsVO.ApiEndpointStats();
            long total = rs.getLong("total");
            long failed = rs.getLong("failed");
            item.setEventType(rs.getString("event_type"));
            item.setTotal(total);
            item.setFailed(failed);
            item.setErrorRatePercent(percent(failed, total));
            item.setLatestFailReason(rs.getString("latest_fail_reason"));
            return item;
        }, Timestamp.valueOf(from), Timestamp.valueOf(to), projectId, Timestamp.valueOf(from), Timestamp.valueOf(to)));
        return stats;
    }

    private MobileDashboardOperationsVO.LlmUsageStats loadLlmUsage(Long projectId, LocalDate start, LocalDate end) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        MobileDashboardOperationsVO.LlmUsageStats usage = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS total_calls,
                       COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END), 0) AS success_calls,
                       COALESCE(SUM(CASE WHEN status <> 'success' THEN 1 ELSE 0 END), 0) AS failed_calls,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(estimated_cost), 0) AS estimated_cost,
                       MAX(currency) AS currency
                  FROM llm_call_observation
                 WHERE project_id = ?
                   AND run_id LIKE ?
                   AND occurred_at >= ?
                   AND occurred_at < ?
                """, (rs, rowNum) -> {
            MobileDashboardOperationsVO.LlmUsageStats item = new MobileDashboardOperationsVO.LlmUsageStats();
            item.setTotalCalls(rs.getLong("total_calls"));
            item.setSuccessCalls(rs.getLong("success_calls"));
            item.setFailedCalls(rs.getLong("failed_calls"));
            item.setTotalTokens(rs.getLong("total_tokens"));
            item.setEstimatedCost(nvl(rs.getBigDecimal("estimated_cost")));
            String currency = rs.getString("currency");
            item.setCurrency(currency == null || currency.isBlank() ? "CNY" : currency);
            item.setEstimated(true);
            return item;
        }, projectId, ENTITY_JUDGE_RUN_PREFIX, Timestamp.valueOf(from), Timestamp.valueOf(to));
        return usage == null ? new MobileDashboardOperationsVO.LlmUsageStats() : usage;
    }

    private List<MobileDashboardOperationsVO.ShareRisk> loadShareRisks(Long projectId, LocalDate start, LocalDate end) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        return jdbcTemplate.query("""
                SELECT s.id AS share_id,
                       s.token_prefix,
                       COUNT(l.id) AS total_access,
                       COUNT(DISTINCT l.client_ip_hash) AS distinct_ip_count,
                       COALESCE(SUM(CASE WHEN l.success = 0 THEN 1 ELSE 0 END), 0) AS failed_access,
                       MAX(l.created_at) AS last_access_at
                  FROM mobile_dashboard_share s
                  LEFT JOIN mobile_dashboard_access_log l
                    ON l.share_id = s.id
                   AND l.created_at >= ?
                   AND l.created_at < ?
                 WHERE s.project_id = ?
                 GROUP BY s.id, s.token_prefix
                 ORDER BY distinct_ip_count DESC, total_access DESC
                """, (rs, rowNum) -> {
            MobileDashboardOperationsVO.ShareRisk item = new MobileDashboardOperationsVO.ShareRisk();
            long distinctIp = rs.getLong("distinct_ip_count");
            long failedAccess = rs.getLong("failed_access");
            item.setShareId(rs.getLong("share_id"));
            item.setTokenPrefix(rs.getString("token_prefix"));
            item.setTotalAccess(rs.getLong("total_access"));
            item.setDistinctIpCount(distinctIp);
            item.setFailedAccess(failedAccess);
            item.setLastAccessAt(toLocalDateTime(rs.getTimestamp("last_access_at")));
            item.setSuspicious(distinctIp >= SHARE_RISK_DISTINCT_IP_THRESHOLD || failedAccess >= 20);
            return item;
        }, Timestamp.valueOf(from), Timestamp.valueOf(to), projectId);
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

    private int percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0D / denominator);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
