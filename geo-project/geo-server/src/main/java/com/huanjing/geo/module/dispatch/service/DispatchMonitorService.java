package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.DispatchAlertVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDashboardVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDateRange;
import com.huanjing.geo.module.dispatch.dto.DispatchPlatformHealthVO;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskMonitorVO;
import com.huanjing.geo.module.dispatch.dto.PlatformHealthAggregateRow;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.AiPlatformHealthEventMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.common.llm.pool.LlmExecutionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispatchMonitorService {

    private static final Set<String> INTERNAL_MONITOR_ROLES = Set.of("super_admin", "manager", "delivery_manager", "operator");

    private final CurrentUserService currentUserService;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchAlertMapper dispatchAlertMapper;
    private final PollBatchShardMapper pollBatchShardMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final AiPlatformHealthEventMapper aiPlatformHealthEventMapper;
    private final DispatchAlertService dispatchAlertService;
    private final LlmExecutionGateway llmExecutionGateway;
    private final InternalScopeService internalScopeService;

    public DispatchDashboardVO dashboard(String rangeType, LocalDate startDate, LocalDate endDate, Long projectId) {
        SysUser user = ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);
        ensureProjectVisible(user, projectId);

        DispatchDashboardVO vo = new DispatchDashboardVO();
        vo.setRangeLabel(range.getStartDate() + " ~ " + range.getEndDate());
        LambdaQueryWrapper<Project> activeProjectWrapper = new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, "active")
                .eq(projectId != null, Project::getId, projectId);
        internalScopeService.applyProjectScope(activeProjectWrapper, user);
        vo.setActiveProjectCount(projectMapper.selectCount(activeProjectWrapper));

        vo.setDueTaskCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .ge(DispatchTask::getDueTime, range.getStartAt())
                        .lt(DispatchTask::getDueTime, range.getEndAtExclusive()),
                user
        )));
        vo.setRunningTaskCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getStatus, "running")
                        .ge(DispatchTask::getDueTime, range.getStartAt())
                        .lt(DispatchTask::getDueTime, range.getEndAtExclusive()),
                user
        )));
        vo.setCompletedTaskCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getStatus, "completed")
                        .ge(DispatchTask::getFinishedAt, range.getStartAt())
                        .lt(DispatchTask::getFinishedAt, range.getEndAtExclusive()),
                user
        )));
        vo.setFailedTaskCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .isNotNull(DispatchTask::getLastError)
                        .ge(DispatchTask::getUpdatedAt, range.getStartAt())
                        .lt(DispatchTask::getUpdatedAt, range.getEndAtExclusive()),
                user
        )));
        vo.setDeadLetterPendingCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getStatus, "dead_letter"),
                user
        )));
        vo.setPlatformExceptionCount(dispatchTaskMapper.selectCount(applyDispatchTaskScope(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .isNotNull(DispatchTask::getPlatformCode)
                        .isNotNull(DispatchTask::getLastError)
                        .ge(DispatchTask::getUpdatedAt, range.getStartAt())
                        .lt(DispatchTask::getUpdatedAt, range.getEndAtExclusive()),
                user
        )));

        List<DispatchTask> completed = dispatchTaskMapper.selectList(
                applyDispatchTaskScope(new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getStatus, "completed")
                        .isNotNull(DispatchTask::getFirstStartedAt)
                        .isNotNull(DispatchTask::getFinishedAt)
                        .ge(DispatchTask::getFinishedAt, range.getStartAt())
                        .lt(DispatchTask::getFinishedAt, range.getEndAtExclusive()),
                        user)
        );
        long avgMs = completed.isEmpty() ? 0 : Math.round(
                completed.stream()
                        .mapToLong(item -> java.time.Duration.between(item.getFirstStartedAt(), item.getFinishedAt()).toMillis())
                        .average()
                        .orElse(0)
        );
        vo.setAvgTaskDurationMs(avgMs);
        return vo;
    }

    public Page<DispatchTaskMonitorVO> taskPage(long current,
                                                long size,
                                                String rangeType,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                Long projectId,
                                                String taskType,
                                                String status,
                                                String keyword) {
        SysUser user = ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);
        ensureProjectVisible(user, projectId);

        LambdaQueryWrapper<DispatchTask> wrapper = applyDispatchTaskScope(new LambdaQueryWrapper<DispatchTask>()
                .ge(DispatchTask::getDueTime, range.getStartAt())
                .lt(DispatchTask::getDueTime, range.getEndAtExclusive())
                .eq(projectId != null, DispatchTask::getProjectId, projectId)
                .eq(StringUtils.hasText(taskType), DispatchTask::getTaskType, taskType)
                .eq(StringUtils.hasText(status), DispatchTask::getStatus, status)
                .orderByDesc(DispatchTask::getCreatedAt), user);

        if (StringUtils.hasText(keyword)) {
            List<Long> matchedProjectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .select(Project::getId)
                            .like(Project::getProjectName, keyword.trim())
            ).stream().map(Project::getId).toList();
            if (matchedProjectIds.isEmpty()) {
                return new Page<>(current, size);
            }
            wrapper.in(DispatchTask::getProjectId, matchedProjectIds);
        }

        Page<DispatchTask> page = dispatchTaskMapper.selectPage(new Page<>(current, size), wrapper);
        Map<Long, String> projectNameMap = projectNameMap(page.getRecords().stream().map(DispatchTask::getProjectId).toList());
        Page<DispatchTaskMonitorVO> result = new Page<>(current, size, page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(task -> toTaskMonitorVO(task, projectNameMap.getOrDefault(task.getProjectId(), "-")))
                .toList());
        return result;
    }

    public DispatchTaskMonitorVO taskDetail(Long taskId) {
        SysUser user = ensureMonitorAccess();
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "Dispatch task not found");
        }
        ensureProjectVisible(user, task.getProjectId());
        Map<Long, String> projectNameMap = projectNameMap(List.of(task.getProjectId()));
        return toTaskMonitorVO(task, projectNameMap.getOrDefault(task.getProjectId(), "-"));
    }

    public List<DispatchPlatformHealthVO> platformHealth(String rangeType, LocalDate startDate, LocalDate endDate) {
        ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);

        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getPlatformName)
        );
        if (platforms.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> codes = platforms.stream().map(AiPlatformConfig::getPlatformCode).toList();
        List<DispatchTask> exceptionTasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .in(DispatchTask::getPlatformCode, codes)
                        .isNotNull(DispatchTask::getLastError)
                        .ge(DispatchTask::getUpdatedAt, range.getStartAt())
                        .lt(DispatchTask::getUpdatedAt, range.getEndAtExclusive())
        );
        Map<String, Long> exceptionCountMap = exceptionTasks.stream()
                .collect(Collectors.groupingBy(DispatchTask::getPlatformCode, Collectors.counting()));
        Map<String, PlatformHealthAggregateRow> healthStats = aiPlatformHealthEventMapper
                .aggregateByPlatform(codes, range.getStartAt(), range.getEndAtExclusive())
                .stream()
                .collect(Collectors.toMap(PlatformHealthAggregateRow::getPlatformCode, item -> item, (a, b) -> a));

        return platforms.stream().map(p -> {
            PlatformHealthAggregateRow stats = healthStats.get(p.getPlatformCode());
            long taskExceptionCount = exceptionCountMap.getOrDefault(p.getPlatformCode(), 0L);
            long failureCount = value(stats == null ? null : stats.getFailureCount());
            long rateLimitedCount = value(stats == null ? null : stats.getRateLimitedCount());
            long permitBusyCount = value(stats == null ? null : stats.getPermitBusyCount());
            long circuitOpenCount = value(stats == null ? null : stats.getCircuitOpenCount());
            long healthExceptionCount = failureCount + rateLimitedCount + permitBusyCount + circuitOpenCount;
            long successCount = value(stats == null ? null : stats.getSuccessCount());
            long invocationCount = successCount + healthExceptionCount;
            DispatchPlatformHealthVO vo = new DispatchPlatformHealthVO();
            vo.setId(p.getId());
            vo.setPlatformCode(p.getPlatformCode());
            vo.setPlatformName(p.getPlatformName());
            vo.setPriorityLevel(p.getPriorityLevel());
            vo.setEnabled(p.getEnabled());
            vo.setRpmLimit(p.getRpmLimit());
            vo.setTpmLimit(p.getTpmLimit());
            vo.setConcurrencyLimit(p.getConcurrencyLimit());
            vo.setActivePermitCount(llmExecutionGateway.activePlatformCount(p.getPlatformCode()));
            vo.setDegraded(p.getDegraded());
            vo.setDegradedReason(p.getDegradedReason());
            vo.setCurrentHealthStatus(p.getCurrentHealthStatus());
            vo.setLastFailureAt(resolveLatest(p.getLastFailureAt(), stats == null ? null : stats.getLastFailureAt()));
            vo.setExceptionCount(healthExceptionCount > 0 ? healthExceptionCount : taskExceptionCount);
            vo.setInvocationCount(invocationCount);
            vo.setSuccessCount(successCount);
            vo.setFailureCount(failureCount);
            vo.setRateLimitedCount(rateLimitedCount);
            vo.setPermitBusyCount(permitBusyCount);
            vo.setCircuitOpenCount(circuitOpenCount);
            vo.setSlowResponseCount(value(stats == null ? null : stats.getSlowResponseCount()));
            vo.setFailureRate(invocationCount <= 0 ? 0D : Math.round((healthExceptionCount * 10000D) / invocationCount) / 100D);
            vo.setAvgDurationMs(value(stats == null ? null : stats.getAvgDurationMs()));
            vo.setLastSuccessAt(stats == null ? null : stats.getLastSuccessAt());
            return vo;
        }).toList();
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime resolveLatest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    public Page<DispatchAlertVO> alertPage(long current,
                                           long size,
                                           String rangeType,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           String severity,
                                           String status) {
        SysUser user = ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);

        Page<DispatchAlert> page = dispatchAlertMapper.selectPage(
                new Page<>(current, size),
                applyDispatchAlertScope(new LambdaQueryWrapper<DispatchAlert>()
                        .eq(StringUtils.hasText(severity), DispatchAlert::getSeverity, severity)
                        .eq(StringUtils.hasText(status), DispatchAlert::getStatus, status)
                        .ge(DispatchAlert::getCreatedAt, range.getStartAt())
                        .lt(DispatchAlert::getCreatedAt, range.getEndAtExclusive())
                        .orderByDesc(DispatchAlert::getCreatedAt), user)
        );

        Map<Long, String> projectNameMap = projectNameMap(page.getRecords().stream()
                .map(DispatchAlert::getProjectId)
                .filter(id -> id != null && id > 0)
                .toList());

        Page<DispatchAlertVO> result = new Page<>(current, size, page.getTotal());
        result.setRecords(page.getRecords().stream().map(alert -> {
            DispatchAlertVO vo = new DispatchAlertVO();
            vo.setId(alert.getId());
            vo.setAlertCode(alert.getAlertCode());
            vo.setTaskId(alert.getTaskId());
            vo.setProjectId(alert.getProjectId());
            vo.setProjectName(projectNameMap.getOrDefault(alert.getProjectId(), "-"));
            vo.setSeverity(alert.getSeverity());
            vo.setStatus(alert.getStatus());
            vo.setTitle(alert.getTitle());
            vo.setContent(alert.getContent());
            vo.setRetryCount(alert.getRetryCount());
            vo.setContextJson(alert.getContextJson());
            vo.setResolvedAt(alert.getResolvedAt());
            vo.setResolvedBy(alert.getResolvedBy());
            vo.setCreatedAt(alert.getCreatedAt());
            return vo;
        }).toList());
        return result;
    }

    public void resolveAlert(Long id, String note) {
        currentUserService.ensurePermission("dispatch.alert.resolve");
        var user = currentUserService.requireCurrentUser();
        Long userId = user.getId();
        dispatchAlertService.resolveAlert(id, userId, note);
    }

    private SysUser ensureMonitorAccess() {
        var user = currentUserService.requireCurrentUser();
        if (!INTERNAL_MONITOR_ROLES.contains(user.getRole())) {
            throw new BizException(403, "No permission for monitoring center");
        }
        return user;
    }

    private LambdaQueryWrapper<DispatchTask> applyDispatchTaskScope(LambdaQueryWrapper<DispatchTask> wrapper, SysUser user) {
        if (internalScopeService.isGlobalInternal(user)) {
            return wrapper;
        }
        if (internalScopeService.requiresOwnerScope(user)) {
            wrapper.inSql(DispatchTask::getProjectId, internalScopeService.ownerProjectIdSql(user));
        } else {
            internalScopeService.applyNoRows(wrapper);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<DispatchAlert> applyDispatchAlertScope(LambdaQueryWrapper<DispatchAlert> wrapper, SysUser user) {
        if (internalScopeService.isGlobalInternal(user)) {
            return wrapper;
        }
        if (internalScopeService.requiresOwnerScope(user)) {
            wrapper.inSql(DispatchAlert::getProjectId, internalScopeService.ownerProjectIdSql(user));
        } else {
            internalScopeService.applyNoRows(wrapper);
        }
        return wrapper;
    }

    private DispatchDateRange resolveDateRange(String rangeType, LocalDate startDate, LocalDate endDate) {
        String type = StringUtils.hasText(rangeType) ? rangeType.trim().toLowerCase() : "today";
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        switch (type) {
            case "last7" -> {
                start = today.minusDays(6);
                end = today;
            }
            case "last30" -> {
                start = today.minusDays(29);
                end = today;
            }
            case "custom" -> {
                if (startDate == null || endDate == null) {
                    throw new BizException(400, "custom range requires startDate and endDate");
                }
                if (endDate.isBefore(startDate)) {
                    throw new BizException(400, "endDate must be >= startDate");
                }
                start = startDate;
                end = endDate;
            }
            case "today" -> {
                start = today;
                end = today;
            }
            default -> throw new BizException(400, "Unsupported rangeType: " + rangeType);
        }
        return new DispatchDateRange(
                start,
                end,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );
    }

    private Map<Long, String> projectNameMap(List<Long> projectIds) {
        List<Long> ids = projectIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectMapper.selectList(
                        new LambdaQueryWrapper<Project>()
                                .select(Project::getId, Project::getProjectName)
                                .in(Project::getId, ids)
                ).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a, LinkedHashMap::new));
    }

    private void ensureProjectVisible(SysUser user, Long projectId) {
        if (projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        internalScopeService.ensureProjectAccess(user, project, "project");
    }

    private DispatchTaskMonitorVO toTaskMonitorVO(DispatchTask task, String projectName) {
        DispatchTaskMonitorVO vo = new DispatchTaskMonitorVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setProjectId(task.getProjectId());
        vo.setProjectName(projectName);
        vo.setPlatformCode(task.getPlatformCode());
        vo.setCurrentChannel(task.getCurrentChannel());
        vo.setTaskType(task.getTaskType());
        vo.setTaskDisplayName(resolveTaskDisplayName(task));
        vo.setPriorityLevel(task.getPriorityLevel());
        vo.setStatus(task.getStatus());
        vo.setWindowStart(task.getWindowStart());
        vo.setWindowEnd(task.getWindowEnd());
        vo.setDueTime(task.getDueTime());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setFirstStartedAt(task.getFirstStartedAt());
        vo.setLastStartedAt(task.getLastStartedAt());
        vo.setNextRetryAt(task.getNextRetryAt());
        vo.setTimeoutAt(task.getTimeoutAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setLastError(task.getLastError());
        vo.setErrorContext(task.getErrorContext());
        vo.setPayloadJson(task.getPayloadJson());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private String resolveTaskDisplayName(DispatchTask task) {
        if (task == null || !DispatchTaskType.BI_DAILY_POLL.name().equalsIgnoreCase(task.getTaskType())) {
            return null;
        }
        String tier = resolveQuestionTier(task);
        return StringUtils.hasText(tier) ? "问题池跑批（" + tier + "）" : "问题池跑批";
    }

    private String resolveQuestionTier(DispatchTask task) {
        Map<String, Object> payload = parsePayload(task.getPayloadJson());
        Object questionTier = payload.get("questionTier");
        if (questionTier != null && StringUtils.hasText(String.valueOf(questionTier))) {
            return normalizeQuestionTier(String.valueOf(questionTier));
        }
        Object shardId = payload.get("shardId");
        if (shardId == null || !StringUtils.hasText(String.valueOf(shardId))) {
            return null;
        }
        try {
            PollBatchShard shard = pollBatchShardMapper.selectById(Long.parseLong(String.valueOf(shardId)));
            return shard == null ? null : normalizeQuestionTier(shard.getQuestionTier());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            JSONUtil.parseObj(payloadJson).forEach((key, value) -> payload.put(String.valueOf(key), value));
            return payload;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String normalizeQuestionTier(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String tier = value.trim().toUpperCase();
        return List.of("A", "B", "C").contains(tier) ? tier : value.trim();
    }
}
