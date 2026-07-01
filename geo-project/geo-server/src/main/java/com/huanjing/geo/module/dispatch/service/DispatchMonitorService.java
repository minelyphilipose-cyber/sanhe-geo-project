package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.dto.DispatchAlertVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDashboardVO;
import com.huanjing.geo.module.dispatch.dto.DispatchDateRange;
import com.huanjing.geo.module.dispatch.dto.DispatchDueTimeBucketRow;
import com.huanjing.geo.module.dispatch.dto.DispatchDueTimeDistributionVO;
import com.huanjing.geo.module.dispatch.dto.DispatchPlatformHealthVO;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskMonitorVO;
import com.huanjing.geo.module.dispatch.dto.PlatformHealthAggregateRow;
import com.huanjing.geo.module.dispatch.dto.PollSliceProgressVO;
import com.huanjing.geo.module.dispatch.entity.DispatchAlert;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.mapper.AiPlatformHealthEventMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchAlertMapper;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.common.llm.monitoring.LlmCapacityQueryService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.common.llm.LlmCapacityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
    private final LlmCapacityView llmCapacityView;
    private final InternalScopeService internalScopeService;
    private final LlmCapacityQueryService capacityQueryService;

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
                .eq(StringUtils.hasText(status), DispatchTask::getStatus, status)
                .orderByDesc(DispatchTask::getCreatedAt), user);
        applyTaskTypeFilter(wrapper, taskType);

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

    private void applyTaskTypeFilter(LambdaQueryWrapper<DispatchTask> wrapper, String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return;
        }
        if (DispatchTaskType.isQuestionPoll(taskType)) {
            wrapper.in(DispatchTask::getTaskType, DispatchTaskType.QUESTION_POLL.name(), DispatchTaskType.BI_DAILY_POLL.name());
            return;
        }
        wrapper.eq(DispatchTask::getTaskType, taskType);
    }

    public DispatchDueTimeDistributionVO dueTimeDistribution(int bucketMinutes, String platformCode) {
        ensureMonitorAccess();
        int safeBucketMinutes = Math.max(5, Math.min(bucketMinutes, 240));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeEnd = now.plusDays(1);
        List<String> statuses = List.of(
                DispatchTaskStatus.PENDING.value(),
                DispatchTaskStatus.RETRY_PENDING.value(),
                DispatchTaskStatus.RUNNING.value()
        );
        List<DispatchDueTimeBucketRow> rows = dispatchTaskMapper.aggregateDueTimeDistribution(
                List.of(DispatchTaskType.QUESTION_POLL.name(), DispatchTaskType.BI_DAILY_POLL.name()),
                now,
                rangeEnd,
                safeBucketMinutes,
                statuses,
                normalizeFilter(platformCode)
        );
        DispatchDueTimeDistributionVO vo = new DispatchDueTimeDistributionVO();
        vo.setTaskType(DispatchTaskType.QUESTION_POLL.name());
        vo.setRangeStart(now);
        vo.setRangeEnd(rangeEnd);
        vo.setBucketMinutes(safeBucketMinutes);
        Map<String, List<DispatchDueTimeBucketRow>> byPlatform = rows.stream()
                .collect(Collectors.groupingBy(row -> defaultText(row.getPlatformCode(), "unknown"), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<DispatchDueTimeBucketRow>> platformEntry : byPlatform.entrySet()) {
            DispatchDueTimeDistributionVO.PlatformSeries platformSeries = new DispatchDueTimeDistributionVO.PlatformSeries();
            platformSeries.setPlatformCode(platformEntry.getKey());
            Map<String, List<DispatchDueTimeBucketRow>> byStatus = platformEntry.getValue().stream()
                    .collect(Collectors.groupingBy(row -> defaultText(row.getStatus(), "unknown"), LinkedHashMap::new, Collectors.toList()));
            for (String status : statuses) {
                List<DispatchDueTimeBucketRow> statusRows = byStatus.getOrDefault(status, List.of());
                DispatchDueTimeDistributionVO.StatusSeries statusSeries = new DispatchDueTimeDistributionVO.StatusSeries();
                statusSeries.setStatus(status);
                statusSeries.setBuckets(statusRows.stream().map(row -> {
                    DispatchDueTimeDistributionVO.Bucket bucket = new DispatchDueTimeDistributionVO.Bucket();
                    bucket.setBucketStart(row.getBucketStart());
                    bucket.setTaskCount(value(row.getTaskCount()));
                    return bucket;
                }).toList());
                platformSeries.getStatuses().add(statusSeries);
            }
            vo.getPlatforms().add(platformSeries);
        }
        return vo;
    }

    public PollSliceProgressVO pollSliceProgress(LocalDate batchDate, String questionTier, String platformCode) {
        ensureMonitorAccess();
        LocalDate targetDate = batchDate == null ? LocalDate.now() : batchDate;
        String tier = StringUtils.hasText(questionTier) ? questionTier.trim().toUpperCase() : "A";
        List<String> platformCodes = StringUtils.hasText(platformCode)
                ? List.of(platformCode.trim().toLowerCase())
                : enabledQuestionPollPlatformCodes();
        LlmCapacityQueryService.PollSliceProgressSnapshot snapshot = capacityQueryService.platformSliceProgress(
                targetDate,
                tier,
                platformCodes,
                LocalDateTime.now()
        );
        PollSliceProgressVO vo = new PollSliceProgressVO();
        vo.setBatchDate(snapshot.batchDate());
        vo.setQuestionTier(snapshot.questionTier());
        vo.setPlatformCodes(snapshot.platformCodes());
        vo.setExpectedCount(snapshot.expectedCount());
        vo.setCompletedCount(snapshot.completedCount());
        vo.setFailedCount(snapshot.failedCount());
        vo.setResourceWaitCount(snapshot.resourceWaitCount());
        vo.setActualProgress(snapshot.actualProgress());
        vo.setExpectedProgress(snapshot.expectedProgress());
        vo.setLag(snapshot.lag());
        vo.setWindowMinutes(snapshot.windowMinutes());
        vo.setSliceStart(snapshot.sliceStart());
        vo.setObservedAt(snapshot.observedAt());
        vo.setRows(snapshot.rows());
        return vo;
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
            vo.setActivePermitCount(llmCapacityView.activePlatformCount(p.getPlatformCode()));
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

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> enabledQuestionPollPlatformCodes() {
        return aiPlatformConfigMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .orderByAsc(AiPlatformConfig::getId))
                .stream()
                .map(AiPlatformConfig::getPlatformCode)
                .filter(StringUtils::hasText)
                .map(code -> code.trim().toLowerCase())
                .toList();
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

        List<DispatchAlert> alerts = dispatchAlertMapper.selectList(
                applyDispatchAlertScope(new LambdaQueryWrapper<DispatchAlert>()
                        .eq(StringUtils.hasText(severity), DispatchAlert::getSeverity, severity)
                        .eq(StringUtils.hasText(status), DispatchAlert::getStatus, status)
                        .ge(DispatchAlert::getCreatedAt, range.getStartAt())
                        .lt(DispatchAlert::getCreatedAt, range.getEndAtExclusive())
                        .orderByDesc(DispatchAlert::getCreatedAt), user)
        );
        Map<Long, DispatchTask> taskMap = dispatchTaskMap(alerts.stream().map(DispatchAlert::getTaskId).toList());
        Map<String, List<DispatchAlert>> grouped = new LinkedHashMap<>();
        for (DispatchAlert alert : alerts) {
            grouped.computeIfAbsent(alertGroupKey(alert, taskMap.get(alert.getTaskId())), key -> new ArrayList<>()).add(alert);
        }
        List<List<DispatchAlert>> groups = new ArrayList<>(grouped.values());
        long total = groups.size();
        int from = (int) Math.min(Math.max(current - 1, 0) * Math.max(size, 1), total);
        int to = (int) Math.min(from + Math.max(size, 1), total);
        List<List<DispatchAlert>> pageGroups = from >= to ? List.of() : groups.subList(from, to);

        Map<Long, String> projectNameMap = projectNameMap(pageGroups.stream()
                .flatMap(List::stream)
                .map(DispatchAlert::getProjectId)
                .filter(id -> id != null && id > 0)
                .toList());

        Page<DispatchAlertVO> result = new Page<>(current, size, total);
        result.setRecords(pageGroups.stream()
                .map(group -> toAlertGroupVO(group, projectNameMap))
                .toList());
        return result;
    }

    public DispatchAlertVO alertDetail(Long id) {
        SysUser user = ensureMonitorAccess();
        DispatchAlert alert = dispatchAlertMapper.selectById(id);
        if (alert == null) {
            throw new BizException(404, "Alert not found");
        }
        ensureProjectVisible(user, alert.getProjectId());
        DispatchTask task = alert.getTaskId() == null ? null : dispatchTaskMapper.selectById(alert.getTaskId());
        String groupKey = alertGroupKey(alert, task);
        DispatchDateRange range = new DispatchDateRange(
                alert.getCreatedAt().toLocalDate(),
                alert.getCreatedAt().toLocalDate(),
                alert.getCreatedAt().toLocalDate().atStartOfDay(),
                alert.getCreatedAt().toLocalDate().plusDays(1).atStartOfDay()
        );
        List<DispatchAlert> sameDayAlerts = dispatchAlertMapper.selectList(
                applyDispatchAlertScope(new LambdaQueryWrapper<DispatchAlert>()
                        .eq(alert.getProjectId() != null, DispatchAlert::getProjectId, alert.getProjectId())
                        .ge(DispatchAlert::getCreatedAt, range.getStartAt())
                        .lt(DispatchAlert::getCreatedAt, range.getEndAtExclusive())
                        .orderByDesc(DispatchAlert::getCreatedAt), user)
        );
        Map<Long, DispatchTask> taskMap = dispatchTaskMap(sameDayAlerts.stream().map(DispatchAlert::getTaskId).toList());
        List<DispatchAlert> group = sameDayAlerts.stream()
                .filter(item -> groupKey.equals(alertGroupKey(item, taskMap.get(item.getTaskId()))))
                .toList();
        Map<Long, String> projectNameMap = projectNameMap(group.stream().map(DispatchAlert::getProjectId).toList());
        DispatchAlertVO vo = toAlertGroupVO(group.isEmpty() ? List.of(alert) : group, projectNameMap);
        vo.setDetailAlerts((group.isEmpty() ? List.of(alert) : group).stream()
                .map(item -> toAlertVO(item, projectNameMap, false))
                .toList());
        return vo;
    }

    public void resolveAlert(Long id, String note) {
        currentUserService.ensurePermission("dispatch.alert.resolve");
        var user = currentUserService.requireCurrentUser();
        Long userId = user.getId();
        DispatchAlert alert = dispatchAlertMapper.selectById(id);
        if (alert == null) {
            throw new BizException(404, "Alert not found");
        }
        ensureProjectVisible(user, alert.getProjectId());
        DispatchTask task = alert.getTaskId() == null ? null : dispatchTaskMapper.selectById(alert.getTaskId());
        String groupKey = alertGroupKey(alert, task);
        LocalDate alertDate = alert.getCreatedAt().toLocalDate();
        List<DispatchAlert> sameDayAlerts = dispatchAlertMapper.selectList(
                applyDispatchAlertScope(new LambdaQueryWrapper<DispatchAlert>()
                        .eq(alert.getProjectId() != null, DispatchAlert::getProjectId, alert.getProjectId())
                        .eq(DispatchAlert::getStatus, "open")
                        .ge(DispatchAlert::getCreatedAt, alertDate.atStartOfDay())
                        .lt(DispatchAlert::getCreatedAt, alertDate.plusDays(1).atStartOfDay()), user)
        );
        Map<Long, DispatchTask> taskMap = dispatchTaskMap(sameDayAlerts.stream().map(DispatchAlert::getTaskId).toList());
        for (DispatchAlert item : sameDayAlerts) {
            if (groupKey.equals(alertGroupKey(item, taskMap.get(item.getTaskId())))) {
                dispatchAlertService.resolveAlert(item.getId(), userId, note);
            }
        }
    }

    private DispatchAlertVO toAlertGroupVO(List<DispatchAlert> group, Map<Long, String> projectNameMap) {
        DispatchAlert representative = group.stream()
                .max(Comparator.comparing(DispatchAlert::getCreatedAt))
                .orElseThrow();
        DispatchAlertVO vo = toAlertVO(representative, projectNameMap, true);
        int openCount = (int) group.stream().filter(item -> "open".equals(item.getStatus())).count();
        vo.setGroupCount(group.size());
        vo.setOpenGroupCount(openCount);
        vo.setStatus(openCount > 0 ? "open" : "resolved");
        vo.setRetryCount(group.stream().mapToInt(item -> item.getRetryCount() == null ? 0 : item.getRetryCount()).sum());
        vo.setSeverity(maxSeverity(group));
        if (group.size() > 1) {
            vo.setTitle(representative.getTitle() + "（" + group.size() + "条）");
        }
        vo.setPlatformFailures(aggregatePlatformFailures(group));
        applyBatchFailureStats(vo, group);
        return vo;
    }

    private DispatchAlertVO toAlertVO(DispatchAlert alert, Map<Long, String> projectNameMap, boolean includePlatformFailures) {
        DispatchAlertVO vo = new DispatchAlertVO();
        vo.setId(alert.getId());
        vo.setAlertCode(alert.getAlertCode());
        vo.setTaskId(alert.getTaskId());
        vo.setProjectId(alert.getProjectId());
        vo.setProjectName(projectNameMap.getOrDefault(alert.getProjectId(), "-"));
        vo.setDedupeKey(alert.getDedupeKey());
        vo.setSeverity(alert.getSeverity());
        vo.setStatus(alert.getStatus());
        vo.setTitle(alert.getTitle());
        vo.setContent(alert.getContent());
        vo.setRetryCount(alert.getRetryCount());
        vo.setContextJson(alert.getContextJson());
        vo.setGroupCount(1);
        vo.setOpenGroupCount("open".equals(alert.getStatus()) ? 1 : 0);
        if (includePlatformFailures) {
            vo.setPlatformFailures(aggregatePlatformFailures(List.of(alert)));
        }
        applyBatchFailureStats(vo, List.of(alert));
        vo.setResolvedAt(alert.getResolvedAt());
        vo.setResolvedBy(alert.getResolvedBy());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }

    private void applyBatchFailureStats(DispatchAlertVO vo, List<DispatchAlert> alerts) {
        int expected = 0;
        int failed = 0;
        boolean hasBatchSummary = false;
        for (DispatchAlert alert : alerts) {
            Map<String, Object> context = parsePayload(alert.getContextJson());
            if (!"question_poll_daily_summary".equals(stringValue(context.get("alertType")))) {
                continue;
            }
            int itemExpected = intValue(context.get("expectedResultCount"));
            int itemFailed = intValue(context.get("failedCount"));
            if (itemExpected <= 0 && itemFailed <= 0) {
                continue;
            }
            hasBatchSummary = true;
            expected += itemExpected;
            failed += itemFailed;
        }
        if (!hasBatchSummary) {
            return;
        }
        vo.setExpectedResultCount(expected);
        vo.setFailedCount(failed);
        vo.setFailureRate(expected <= 0 ? (failed > 0 ? 100D : 0D) : Math.round(failed * 10000D / expected) / 100D);
    }

    private List<DispatchAlertVO.PlatformFailureSummary> aggregatePlatformFailures(List<DispatchAlert> alerts) {
        Map<String, DispatchAlertVO.PlatformFailureSummary> platformMap = new LinkedHashMap<>();
        for (DispatchAlert alert : alerts) {
            Map<String, Object> context = parsePayload(alert.getContextJson());
            Object raw = context.get("platformFailures");
            if (!(raw instanceof Iterable<?> iterable)) {
                continue;
            }
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String platformCode = stringValue(map.get("platformCode"));
                String key = StringUtils.hasText(platformCode) ? platformCode : stringValue(map.get("platformId"));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                DispatchAlertVO.PlatformFailureSummary summary = platformMap.computeIfAbsent(key, ignored -> {
                    DispatchAlertVO.PlatformFailureSummary created = new DispatchAlertVO.PlatformFailureSummary();
                    created.setPlatformId(longValue(map.get("platformId")));
                    created.setPlatformCode(platformCode);
                    created.setPlatformName(stringValue(map.get("platformName")));
                    created.setExpectedCount(0);
                    created.setCompletedCount(0);
                    created.setFailedCount(0);
                    created.setRequestCount(0);
                    created.setReasons(new ArrayList<>());
                    return created;
                });
                summary.setExpectedCount(value(summary.getExpectedCount()) + intValue(map.get("expectedCount")));
                summary.setCompletedCount(value(summary.getCompletedCount()) + intValue(map.get("completedCount")));
                summary.setFailedCount(value(summary.getFailedCount()) + intValue(map.get("failedCount")));
                summary.setRequestCount(value(summary.getRequestCount()) + intValue(map.get("requestCount")));
                mergeReasons(summary, map.get("reasons"));
                summary.setFailureRate(summary.getExpectedCount() == null || summary.getExpectedCount() <= 0
                        ? 0D
                        : Math.round(summary.getFailedCount() * 10000D / summary.getExpectedCount()) / 100D);
            }
        }
        return platformMap.values().stream()
                .sorted(Comparator.comparing(DispatchAlertVO.PlatformFailureSummary::getFailedCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void mergeReasons(DispatchAlertVO.PlatformFailureSummary summary, Object rawReasons) {
        if (!(rawReasons instanceof Iterable<?> iterable)) {
            return;
        }
        Map<String, DispatchAlertVO.FailureReasonSummary> reasonMap = summary.getReasons().stream()
                .collect(Collectors.toMap(
                        item -> item.getErrorCode() + "\n" + item.getErrorMessage(),
                        item -> item,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        for (Object rawReason : iterable) {
            if (!(rawReason instanceof Map<?, ?> map)) {
                continue;
            }
            String errorCode = stringValue(map.get("errorCode"));
            String errorMessage = stringValue(map.get("errorMessage"));
            String key = errorCode + "\n" + errorMessage;
            DispatchAlertVO.FailureReasonSummary reason = reasonMap.get(key);
            if (reason == null) {
                reason = new DispatchAlertVO.FailureReasonSummary();
                reason.setErrorCode(errorCode);
                reason.setErrorMessage(errorMessage);
                reason.setCount(0);
                reasonMap.put(key, reason);
                summary.getReasons().add(reason);
            }
            reason.setCount(value(reason.getCount()) + intValue(map.get("count")));
        }
    }

    private String maxSeverity(List<DispatchAlert> group) {
        return group.stream()
                .map(DispatchAlert::getSeverity)
                .max(Comparator.comparingInt(this::severityRank))
                .orElse("info");
    }

    private int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "critical" -> 4;
            case "error" -> 3;
            case "warn" -> 2;
            default -> 1;
        };
    }

    private String alertGroupKey(DispatchAlert alert, DispatchTask task) {
        Map<String, Object> context = parsePayload(alert.getContextJson());
        if ("question_poll_daily_summary".equals(stringValue(context.get("alertType")))
                && alert.getProjectId() != null
                && StringUtils.hasText(stringValue(context.get("batchDate")))) {
            return "question_poll_daily:" + alert.getProjectId() + ":" + stringValue(context.get("batchDate"));
        }
        if (task != null && DispatchTaskType.isQuestionPoll(task.getTaskType())
                && alert.getProjectId() != null) {
            Map<String, Object> payload = parsePayload(task.getPayloadJson());
            String batchDate = stringValue(payload.get("batchDate"));
            if (!StringUtils.hasText(batchDate) && task.getWindowEnd() != null) {
                batchDate = task.getWindowEnd().toString();
            }
            if (!StringUtils.hasText(batchDate) && alert.getCreatedAt() != null) {
                batchDate = alert.getCreatedAt().toLocalDate().toString();
            }
            return "question_poll_daily:" + alert.getProjectId() + ":" + batchDate;
        }
        if (StringUtils.hasText(alert.getDedupeKey())) {
            return alert.getDedupeKey();
        }
        return "alert:" + alert.getId();
    }

    private Map<Long, DispatchTask> dispatchTaskMap(List<Long> taskIds) {
        List<Long> ids = taskIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTask>().in(DispatchTask::getId, ids))
                .stream()
                .collect(Collectors.toMap(DispatchTask::getId, item -> item, (a, b) -> a, HashMap::new));
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
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
        if (task == null || !DispatchTaskType.isQuestionPoll(task.getTaskType())) {
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
