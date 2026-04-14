package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.DispatchAlertVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDashboardVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDateRange;
import com.huanjing.geo.module.dispatch.dto.DispatchPlatformHealthVO;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskMonitorVO;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final DispatchAlertService dispatchAlertService;

    public DispatchDashboardVO dashboard(String rangeType, LocalDate startDate, LocalDate endDate) {
        ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);

        DispatchDashboardVO vo = new DispatchDashboardVO();
        vo.setRangeLabel(range.getStartDate() + " ~ " + range.getEndDate());
        vo.setActiveProjectCount(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getStatus, "active")
        ));
        vo.setDueTaskCount(dispatchTaskMapper.selectCount(
                new LambdaQueryWrapper<DispatchTask>()
                        .ge(DispatchTask::getDueTime, range.getStartAt())
                        .lt(DispatchTask::getDueTime, range.getEndAtExclusive())
        ));
        vo.setCompletedTaskCount(dispatchTaskMapper.selectCount(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getStatus, "completed")
                        .ge(DispatchTask::getFinishedAt, range.getStartAt())
                        .lt(DispatchTask::getFinishedAt, range.getEndAtExclusive())
        ));
        vo.setFailedTaskCount(dispatchTaskMapper.selectCount(
                new LambdaQueryWrapper<DispatchTask>()
                        .isNotNull(DispatchTask::getLastError)
                        .ge(DispatchTask::getUpdatedAt, range.getStartAt())
                        .lt(DispatchTask::getUpdatedAt, range.getEndAtExclusive())
        ));
        vo.setDeadLetterPendingCount(dispatchTaskMapper.selectCount(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getStatus, "dead_letter")
        ));
        vo.setPlatformExceptionCount(dispatchTaskMapper.selectCount(
                new LambdaQueryWrapper<DispatchTask>()
                        .isNotNull(DispatchTask::getPlatformCode)
                        .isNotNull(DispatchTask::getLastError)
                        .ge(DispatchTask::getUpdatedAt, range.getStartAt())
                        .lt(DispatchTask::getUpdatedAt, range.getEndAtExclusive())
        ));

        List<DispatchTask> completed = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getStatus, "completed")
                        .isNotNull(DispatchTask::getFirstStartedAt)
                        .isNotNull(DispatchTask::getFinishedAt)
                        .ge(DispatchTask::getFinishedAt, range.getStartAt())
                        .lt(DispatchTask::getFinishedAt, range.getEndAtExclusive())
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
                                                String taskType,
                                                String status,
                                                String keyword) {
        ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);

        LambdaQueryWrapper<DispatchTask> wrapper = new LambdaQueryWrapper<DispatchTask>()
                .ge(DispatchTask::getDueTime, range.getStartAt())
                .lt(DispatchTask::getDueTime, range.getEndAtExclusive())
                .eq(StringUtils.hasText(taskType), DispatchTask::getTaskType, taskType)
                .eq(StringUtils.hasText(status), DispatchTask::getStatus, status)
                .orderByDesc(DispatchTask::getCreatedAt);

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
        result.setRecords(page.getRecords().stream().map(task -> {
            DispatchTaskMonitorVO vo = new DispatchTaskMonitorVO();
            vo.setId(task.getId());
            vo.setTaskNo(task.getTaskNo());
            vo.setProjectId(task.getProjectId());
            vo.setProjectName(projectNameMap.getOrDefault(task.getProjectId(), "-"));
            vo.setPlatformCode(task.getPlatformCode());
            vo.setCurrentChannel(task.getCurrentChannel());
            vo.setTaskType(task.getTaskType());
            vo.setPriorityLevel(task.getPriorityLevel());
            vo.setStatus(task.getStatus());
            vo.setWindowStart(task.getWindowStart());
            vo.setWindowEnd(task.getWindowEnd());
            vo.setDueTime(task.getDueTime());
            vo.setRetryCount(task.getRetryCount());
            vo.setFinishedAt(task.getFinishedAt());
            vo.setLastError(task.getLastError());
            vo.setErrorContext(task.getErrorContext());
            vo.setCreatedAt(task.getCreatedAt());
            return vo;
        }).toList());
        return result;
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

        return platforms.stream().map(p -> {
            DispatchPlatformHealthVO vo = new DispatchPlatformHealthVO();
            vo.setId(p.getId());
            vo.setPlatformCode(p.getPlatformCode());
            vo.setPlatformName(p.getPlatformName());
            vo.setPriorityLevel(p.getPriorityLevel());
            vo.setEnabled(p.getEnabled());
            vo.setRpmLimit(p.getRpmLimit());
            vo.setTpmLimit(p.getTpmLimit());
            vo.setDegraded(p.getDegraded());
            vo.setDegradedReason(p.getDegradedReason());
            vo.setCurrentHealthStatus(p.getCurrentHealthStatus());
            vo.setLastFailureAt(p.getLastFailureAt());
            vo.setExceptionCount(exceptionCountMap.getOrDefault(p.getPlatformCode(), 0L));
            return vo;
        }).toList();
    }

    public Page<DispatchAlertVO> alertPage(long current,
                                           long size,
                                           String rangeType,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           String severity,
                                           String status) {
        ensureMonitorAccess();
        DispatchDateRange range = resolveDateRange(rangeType, startDate, endDate);

        Page<DispatchAlert> page = dispatchAlertMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<DispatchAlert>()
                        .eq(StringUtils.hasText(severity), DispatchAlert::getSeverity, severity)
                        .eq(StringUtils.hasText(status), DispatchAlert::getStatus, status)
                        .ge(DispatchAlert::getCreatedAt, range.getStartAt())
                        .lt(DispatchAlert::getCreatedAt, range.getEndAtExclusive())
                        .orderByDesc(DispatchAlert::getCreatedAt)
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

    private void ensureMonitorAccess() {
        var user = currentUserService.requireCurrentUser();
        if (!INTERNAL_MONITOR_ROLES.contains(user.getRole())) {
            throw new BizException(403, "No permission for monitoring center");
        }
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
}
