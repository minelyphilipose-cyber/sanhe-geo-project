package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationProjectRow;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationQuotaVO;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationRequest;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocationAudit;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationAuditMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectDistributionChannelAllocationService {

    public static final String OFFICIAL_SITE = "official_site";
    public static final String INDUSTRY_SITE = "industry_site";
    public static final String SELF_MEDIA = "self_media";
    public static final String AUTHORITY_MEDIA = "authority_media";

    private static final List<ChannelDefinition> CHANNELS = List.of(
            new ChannelDefinition(OFFICIAL_SITE, "官网"),
            new ChannelDefinition(INDUSTRY_SITE, "行业资讯站"),
            new ChannelDefinition(SELF_MEDIA, "自媒体号"),
            new ChannelDefinition(AUTHORITY_MEDIA, "权威媒体")
    );
    private static final Set<String> CHANNEL_CODES = CHANNELS.stream()
            .map(ChannelDefinition::code)
            .collect(Collectors.toUnmodifiableSet());

    private final CompanyMapper companyMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final ProjectChannelAllocationMapper allocationMapper;
    private final ProjectChannelAllocationAuditMapper auditMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCompany(Long companyId) {
        Long locked = companyMapper.lockCompanyForUpdate(companyId);
        if (locked == null) {
            throw new BizException(404, "Company not found");
        }
    }

    public ProjectChannelAllocationQuotaVO quota(Long companyId, Long excludeProjectId) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        Map<String, SnapshotQuota> snapshot = parseSnapshot(binding);
        Map<String, ProjectChannelAllocation> current = excludeProjectId == null
                ? Map.of()
                : currentAllocations(excludeProjectId);
        List<ProjectChannelAllocationVO> items = CHANNELS.stream()
                .map(channel -> toQuotaItem(companyId, channel, snapshot.get(channel.code()), current.get(channel.code()), excludeProjectId))
                .toList();

        ProjectChannelAllocationQuotaVO vo = new ProjectChannelAllocationQuotaVO();
        vo.setCompanyId(companyId);
        vo.setExcludeProjectId(excludeProjectId);
        vo.setAllocationVersion(allocationMapper.maxRevisionByCompany(companyId));
        vo.setNote("剩余额度不含草稿/暂停项目，项目启动时会再次校验");
        vo.setItems(items);
        return vo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceAllocations(Project project,
                                   List<ProjectChannelAllocationRequest> requests,
                                   Long expectedVersion,
                                   Long operatorId,
                                   String sourceAction) {
        lockCompany(project.getCompanyId());
        validateVersion(project.getCompanyId(), expectedVersion);
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        Map<String, SnapshotQuota> snapshot = parseSnapshot(binding);
        Map<String, Integer> requested = normalizeRequests(requests);
        validateRequestedCounts(project, requested, snapshot);

        Map<String, ProjectChannelAllocation> before = currentAllocations(project.getId());
        long nextRevision = allocationMapper.maxRevisionByCompany(project.getCompanyId()) + 1;
        for (ChannelDefinition channel : CHANNELS) {
            String channelCode = channel.code();
            int beforeValue = before.containsKey(channelCode) && before.get(channelCode).getAllocatedCount() != null
                    ? before.get(channelCode).getAllocatedCount()
                    : 0;
            int afterValue = requested.getOrDefault(channelCode, 0);
            SnapshotQuota quota = snapshot.get(channelCode);
            ProjectChannelAllocation row = before.get(channelCode);
            if (row == null) {
                row = new ProjectChannelAllocation();
                row.setProjectId(project.getId());
                row.setCompanyId(project.getCompanyId());
                row.setChannelCode(channelCode);
            }
            row.setPeriodTypeSnapshot(quota == null ? "none" : quota.periodType());
            row.setPackageQuotaLimitSnapshot(quota == null ? 0 : quota.quotaLimit());
            row.setAllocatedCount(afterValue);
            row.setRevision(nextRevision);
            if (row.getId() == null) {
                allocationMapper.insert(row);
            } else {
                allocationMapper.updateById(row);
            }
            if (beforeValue != afterValue) {
                insertAudit(operatorId, project, channelCode, beforeValue, afterValue, sourceAction);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateActivation(Project project) {
        lockCompany(project.getCompanyId());
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        Map<String, SnapshotQuota> snapshot = parseSnapshot(binding);
        Map<String, Integer> requested = currentAllocations(project.getId()).values().stream()
                .collect(Collectors.toMap(ProjectChannelAllocation::getChannelCode,
                        row -> row.getAllocatedCount() == null ? 0 : row.getAllocatedCount(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        for (ChannelDefinition channel : CHANNELS) {
            requested.putIfAbsent(channel.code(), 0);
        }
        validateRequestedCounts(project, requested, snapshot);
    }

    public void attachAllocations(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> projectIds = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        if (projectIds.isEmpty()) {
            return;
        }
        List<ProjectChannelAllocation> rows = allocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .in(ProjectChannelAllocation::getProjectId, projectIds)
                        .orderByAsc(ProjectChannelAllocation::getProjectId, ProjectChannelAllocation::getChannelCode)
        );
        Map<Long, List<ProjectChannelAllocation>> byProject = rows.stream()
                .collect(Collectors.groupingBy(ProjectChannelAllocation::getProjectId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Long> versionByCompany = projects.stream()
                .map(Project::getCompanyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(id -> id, allocationMapper::maxRevisionByCompany));
        for (Project project : projects) {
            Map<String, ProjectChannelAllocation> rowMap = byProject.getOrDefault(project.getId(), List.of()).stream()
                    .collect(Collectors.toMap(ProjectChannelAllocation::getChannelCode, row -> row, (a, b) -> a));
            List<ProjectChannelAllocationVO> vos = CHANNELS.stream().map(channel -> {
                ProjectChannelAllocation row = rowMap.get(channel.code());
                ProjectChannelAllocationVO vo = new ProjectChannelAllocationVO();
                vo.setChannelCode(channel.code());
                vo.setChannelName(channel.name());
                vo.setPeriodType(row == null ? null : row.getPeriodTypeSnapshot());
                vo.setEnabled(row != null && row.getPackageQuotaLimitSnapshot() != null && row.getPackageQuotaLimitSnapshot() > 0);
                vo.setQuotaLimit(row == null || row.getPackageQuotaLimitSnapshot() == null ? 0 : row.getPackageQuotaLimitSnapshot());
                long activeAllocated = allocationMapper.sumActiveAllocatedByCompanyAndChannel(
                        project.getCompanyId(), channel.code(), project.getId());
                vo.setCurrentProjectAllocatedCount(row == null || row.getAllocatedCount() == null ? 0 : row.getAllocatedCount());
                vo.setActiveAllocatedCount(activeAllocated);
                vo.setRemainingCount(Math.max(vo.getQuotaLimit() - activeAllocated, 0));
                vo.setInputMax(vo.getRemainingCount());
                return vo;
            }).toList();
            project.setChannelAllocations(vos);
            project.setAllocationVersion(versionByCompany.getOrDefault(project.getCompanyId(), 0L));
        }
    }

    public List<ProjectChannelAllocation> contentGenerationAllocations(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return allocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, projectId)
                        .in(ProjectChannelAllocation::getChannelCode, List.of(OFFICIAL_SITE, INDUSTRY_SITE))
                        .gt(ProjectChannelAllocation::getAllocatedCount, 0)
                        .orderByAsc(ProjectChannelAllocation::getChannelCode)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteProjectAllocations(Long projectId) {
        allocationMapper.delete(new LambdaQueryWrapper<ProjectChannelAllocation>()
                .eq(ProjectChannelAllocation::getProjectId, projectId));
    }

    private void validateVersion(Long companyId, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        long current = allocationMapper.maxRevisionByCompany(companyId);
        if (!expectedVersion.equals(current)) {
            throw new BizException(409, "PROJECT_CHANNEL_ALLOCATION_VERSION_CONFLICT", 409,
                    Map.of(
                            "errorCode", "PROJECT_CHANNEL_ALLOCATION_VERSION_CONFLICT",
                            "currentVersion", current,
                            "expectedVersion", expectedVersion
                    ));
        }
    }

    private void validateRequestedCounts(Project project, Map<String, Integer> requested, Map<String, SnapshotQuota> snapshot) {
        List<Map<String, Object>> exceeded = new ArrayList<>();
        for (ChannelDefinition channel : CHANNELS) {
            int requestedCount = requested.getOrDefault(channel.code(), 0);
            SnapshotQuota quota = snapshot.get(channel.code());
            int quotaLimit = quota == null ? 0 : quota.quotaLimit();
            List<ProjectChannelAllocationProjectRow> activeProjects = allocationMapper.activeProjectRowsForUpdate(
                    project.getCompanyId(), channel.code(), project.getId());
            long activeAllocated = activeProjects.stream()
                    .map(ProjectChannelAllocationProjectRow::getAllocatedCount)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            long remaining = quotaLimit - activeAllocated;
            if (requestedCount > 0 && quota == null) {
                exceeded.add(exceededItem(channel, quotaLimit, activeAllocated, requestedCount, activeProjects));
                continue;
            }
            if (requestedCount > remaining) {
                exceeded.add(exceededItem(channel, quotaLimit, activeAllocated, requestedCount, activeProjects));
            }
        }
        if (!exceeded.isEmpty()) {
            throw new BizException(400, "PROJECT_CHANNEL_ALLOCATION_EXCEEDED", 200,
                    Map.of(
                            "errorCode", "PROJECT_CHANNEL_ALLOCATION_EXCEEDED",
                            "channels", exceeded
                    ));
        }
    }

    private Map<String, Object> exceededItem(ChannelDefinition channel,
                                             int quotaLimit,
                                             long activeAllocated,
                                             int requestedCount,
                                             List<ProjectChannelAllocationProjectRow> activeProjects) {
        List<Map<String, Object>> projects = activeProjects.stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("projectId", row.getProjectId());
                    map.put("projectName", row.getProjectName());
                    map.put("allocatedCount", row.getAllocatedCount());
                    return map;
                })
                .toList();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channelCode", channel.code());
        item.put("channelName", channel.name());
        item.put("quotaLimit", quotaLimit);
        item.put("activeAllocatedCount", activeAllocated);
        item.put("requestedCount", requestedCount);
        item.put("remainingCount", Math.max(quotaLimit - activeAllocated, 0));
        item.put("exceededBy", Math.max(requestedCount - Math.max(quotaLimit - activeAllocated, 0), 0));
        item.put("projects", projects);
        return item;
    }

    private ProjectChannelAllocationVO toQuotaItem(Long companyId,
                                                   ChannelDefinition channel,
                                                   SnapshotQuota quota,
                                                   ProjectChannelAllocation current,
                                                   Long excludeProjectId) {
        int currentCount = current == null || current.getAllocatedCount() == null ? 0 : current.getAllocatedCount();
        int quotaLimit = quota == null ? 0 : quota.quotaLimit();
        long activeAllocated = allocationMapper.sumActiveAllocatedByCompanyAndChannel(companyId, channel.code(), excludeProjectId);
        ProjectChannelAllocationVO vo = new ProjectChannelAllocationVO();
        vo.setChannelCode(channel.code());
        vo.setChannelName(channel.name());
        vo.setPeriodType(quota == null ? null : quota.periodType());
        vo.setEnabled(quota != null);
        vo.setQuotaLimit(quotaLimit);
        vo.setActiveAllocatedCount(activeAllocated);
        vo.setCurrentProjectAllocatedCount(currentCount);
        vo.setRemainingCount(Math.max(quotaLimit - activeAllocated, 0));
        vo.setInputMax(vo.getRemainingCount());
        return vo;
    }

    private Map<String, ProjectChannelAllocation> currentAllocations(Long projectId) {
        return allocationMapper.selectList(
                new LambdaQueryWrapper<ProjectChannelAllocation>()
                        .eq(ProjectChannelAllocation::getProjectId, projectId)
        ).stream().collect(Collectors.toMap(ProjectChannelAllocation::getChannelCode, row -> row, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Integer> normalizeRequests(List<ProjectChannelAllocationRequest> requests) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ChannelDefinition channel : CHANNELS) {
            result.put(channel.code(), 0);
        }
        if (requests == null) {
            return result;
        }
        for (ProjectChannelAllocationRequest req : requests) {
            if (req == null || !StringUtils.hasText(req.getChannelCode())) {
                continue;
            }
            String channelCode = req.getChannelCode().trim().toLowerCase(Locale.ROOT);
            if (!CHANNEL_CODES.contains(channelCode)) {
                throw new BizException(400, "Unsupported project distribution channel: " + req.getChannelCode());
            }
            int count = req.getAllocatedCount() == null ? 0 : req.getAllocatedCount();
            if (count < 0) {
                throw new BizException(400, "Channel allocation count must be >= 0");
            }
            result.put(channelCode, count);
        }
        return result;
    }

    private Map<String, SnapshotQuota> parseSnapshot(CompanyPackageBinding binding) {
        if (binding == null || !StringUtils.hasText(binding.getChannelQuotaSnapshot())) {
            return Map.of();
        }
        JSONArray arr = JSONUtil.parseArray(binding.getChannelQuotaSnapshot());
        Map<String, SnapshotQuota> result = new LinkedHashMap<>();
        for (Object obj : arr) {
            ChannelQuotaSnapshotItem item = JSONUtil.toBean(JSONUtil.parseObj(obj), ChannelQuotaSnapshotItem.class);
            if (item == null || !item.isEnabled() || !StringUtils.hasText(item.getChannelCode())) {
                continue;
            }
            String channelCode = item.getChannelCode().trim().toLowerCase(Locale.ROOT);
            if (!CHANNEL_CODES.contains(channelCode)) {
                continue;
            }
            String periodType = StringUtils.hasText(item.getPeriodType())
                    ? item.getPeriodType().trim().toLowerCase(Locale.ROOT)
                    : "none";
            result.put(channelCode, new SnapshotQuota(periodType, Math.max(item.getQuotaLimit(), 0)));
        }
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void auditCurrentAllocations(Project project, Long operatorId, String sourceAction, boolean releaseSnapshot) {
        Map<String, ProjectChannelAllocation> current = currentAllocations(project.getId());
        for (ChannelDefinition channel : CHANNELS) {
            ProjectChannelAllocation row = current.get(channel.code());
            int allocated = row == null || row.getAllocatedCount() == null ? 0 : row.getAllocatedCount();
            if (allocated == 0) {
                continue;
            }
            insertAudit(operatorId, project, channel.code(), allocated, releaseSnapshot ? 0 : allocated, sourceAction);
        }
    }

    private void insertAudit(Long operatorId,
                             Project project,
                             String channelCode,
                             Integer beforeValue,
                             Integer afterValue,
                             String sourceAction) {
        ProjectChannelAllocationAudit audit = new ProjectChannelAllocationAudit();
        audit.setOperatorId(operatorId);
        audit.setOperateAt(LocalDateTime.now());
        audit.setProjectId(project.getId());
        audit.setCompanyId(project.getCompanyId());
        audit.setChannelCode(channelCode);
        audit.setBeforeValue(beforeValue);
        audit.setAfterValue(afterValue);
        audit.setSourceAction(sourceAction);
        auditMapper.insert(audit);
    }

    private record ChannelDefinition(String code, String name) {
    }

    private record SnapshotQuota(String periodType, int quotaLimit) {
    }
}
