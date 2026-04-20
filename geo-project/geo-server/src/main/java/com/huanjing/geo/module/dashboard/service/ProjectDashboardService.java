package com.huanjing.geo.module.dashboard.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardShareMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardSnapshotMapper;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.QuestionPoolItemMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectDashboardService {

    private static final int MAX_VIEWABLE = 5000;

    private final ProjectDashboardShareMapper shareMapper;
    private final ProjectDashboardSnapshotMapper snapshotMapper;
    private final ProjectMapper projectMapper;
    private final QuestionPoolItemMapper questionPoolItemMapper;
    private final PollResultMapper pollResultMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final CurrentUserService currentUserService;
    private final ProjectDashboardSnapshotService snapshotService;

    public List<ProjectDashboardShare> listShares(Long projectId) {
        Project project = requireReadableProject(projectId);
        return shareMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardShare>()
                        .eq(ProjectDashboardShare::getProjectId, project.getId())
                        .orderByDesc(ProjectDashboardShare::getCreatedAt, ProjectDashboardShare::getId)
        );
    }

    @Transactional
    public ProjectDashboardShare createShare(Long projectId) {
        Project project = requireWritableProject(projectId);
        disableActiveShares(project.getId());

        ProjectDashboardShare share = new ProjectDashboardShare();
        share.setProjectId(project.getId());
        share.setShareCode("dash_" + UUID.randomUUID().toString().replace("-", ""));
        share.setStatus("active");
        share.setCreatedBy(currentUserService.requireCurrentUser().getId());
        shareMapper.insert(share);
        snapshotService.refreshProject(project.getId());
        return share;
    }

    @Transactional
    public void disableShare(Long id) {
        ProjectDashboardShare share = requireShare(id);
        requireWritableProject(share.getProjectId());
        if (!"active".equalsIgnoreCase(share.getStatus())) {
            return;
        }
        share.setStatus("disabled");
        share.setDisabledAt(LocalDateTime.now());
        shareMapper.updateById(share);
    }

    public Map<String, Object> getSummary(String shareCode) {
        ProjectDashboardShare share = requireActiveShare(shareCode);
        Project project = requireProject(share.getProjectId());
        ensureSnapshotsReady(project.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", project.getProjectName());
        payload.put("brandName", project.getBrandName());
        payload.put("summary", readSummary(project.getId()));
        payload.put("platforms", readPlatformSnapshots(project.getId()));
        payload.put("wordCloud", readWordCloud(project.getId()));
        payload.put("refreshedAt", resolveRefreshedAt(project.getId()));
        return payload;
    }

    public Map<String, Object> getTrend(String shareCode, Integer days) {
        ProjectDashboardShare share = requireActiveShare(shareCode);
        ensureSnapshotsReady(share.getProjectId());
        int safeDays = days == null || days <= 0 ? 30 : Math.min(days, 90);
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);

        List<Map<String, Object>> items = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, share.getProjectId())
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "daily_trend")
                        .ge(ProjectDashboardSnapshot::getSnapshotDate, startDate)
                        .orderByAsc(ProjectDashboardSnapshot::getSnapshotDate)
        ).stream().map(snapshot -> {
            Map<String, Object> row = parseObject(snapshot.getSnapshotValue());
            row.put("date", snapshot.getSnapshotDate());
            return row;
        }).toList();
        return Map.of("items", items);
    }

    public Map<String, Object> getDetails(String shareCode,
                                          long current,
                                          long size,
                                          String platformCode,
                                          LocalDate startDate,
                                          LocalDate endDate,
                                          String keyword) {
        ProjectDashboardShare share = requireActiveShare(shareCode);
        long safeSize = size <= 0 ? 20 : Math.min(size, 100);
        long safeCurrent = current <= 0 ? 1 : current;
        long offset = (safeCurrent - 1) * safeSize;

        QueryWrapper<PollResult> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", share.getProjectId())
                .eq("is_hit", 1)
                .orderByDesc("batch_date")
                .orderByDesc("id");
        if (StringUtils.hasText(platformCode)) {
            wrapper.eq("platform_code", platformCode.trim());
        }
        if (startDate != null) {
            wrapper.ge("batch_date", startDate);
        }
        if (endDate != null) {
            wrapper.le("batch_date", endDate);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            List<Long> questionIds = questionPoolItemMapper.selectList(
                    new LambdaQueryWrapper<QuestionPoolItem>()
                            .like(QuestionPoolItem::getQuestionText, trimmedKeyword)
                            .select(QuestionPoolItem::getId)
            ).stream().map(QuestionPoolItem::getId).toList();
            if (questionIds.isEmpty()) {
                wrapper.like("keyword_text_snapshot", trimmedKeyword);
            } else {
                wrapper.and(w -> w.like("keyword_text_snapshot", trimmedKeyword).or().in("question_id", questionIds));
            }
        }

        long total = pollResultMapper.selectCount(wrapper);
        long visibleTotal = Math.min(total, MAX_VIEWABLE);
        if (offset >= MAX_VIEWABLE) {
            return emptyDetailResult(safeCurrent, safeSize, visibleTotal);
        }

        long pageSize = Math.min(safeSize, MAX_VIEWABLE - offset);
        Page<PollResult> page = pollResultMapper.selectPage(new Page<>(safeCurrent, pageSize, false), wrapper);
        List<PollResult> records = page.getRecords();
        Map<Long, String> questionTextMap = loadQuestionTextMap(records);
        Map<String, String> platformNameMap = loadPlatformNameMap(records);
        Map<String, String> platformUrlMap = loadPlatformUrlMap(records);

        List<Map<String, Object>> items = records.stream().map(record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.getId());
            row.put("questionText", resolveDisplayText(record, questionTextMap));
            row.put("platformCode", record.getPlatformCode());
            row.put("platformName", platformNameMap.getOrDefault(record.getPlatformCode(), record.getPlatformCode()));
            row.put("batchDate", record.getBatchDate());
            row.put("hasSnapshot", false);
            row.put("platformUrl", platformUrlMap.get(record.getPlatformCode()));
            return row;
        }).toList();

        return Map.of(
                "total", visibleTotal,
                "page", safeCurrent,
                "size", safeSize,
                "maxViewable", MAX_VIEWABLE,
                "items", items
        );
    }

    private Map<String, Object> emptyDetailResult(long current, long size) {
        return emptyDetailResult(current, size, 0);
    }

    private Map<String, Object> emptyDetailResult(long current, long size, long total) {
        return Map.of(
                "total", total,
                "page", current,
                "size", size,
                "maxViewable", MAX_VIEWABLE,
                "items", List.of()
        );
    }

    private void ensureSnapshotsReady(Long projectId) {
        long count = snapshotMapper.selectCount(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
        );
        if (count == 0) {
            snapshotService.refreshProject(projectId);
        }
    }

    private Map<String, Object> readSummary(Long projectId) {
        ProjectDashboardSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "summary")
                        .last("LIMIT 1")
        );
        return snapshot == null ? Map.of() : parseObject(snapshot.getSnapshotValue());
    }

    private List<Map<String, Object>> readPlatformSnapshots(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "platform")
                        .orderByAsc(ProjectDashboardSnapshot::getId)
        ).stream().map(snapshot -> parseObject(snapshot.getSnapshotValue())).toList();
    }

    private List<Map<String, Object>> readWordCloud(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "word_freq")
                        .orderByAsc(ProjectDashboardSnapshot::getId)
        ).stream().map(snapshot -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("word", snapshot.getSnapshotKey());
            row.putAll(parseObject(snapshot.getSnapshotValue()));
            return row;
        }).toList();
    }

    private LocalDateTime resolveRefreshedAt(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .orderByDesc(ProjectDashboardSnapshot::getRefreshedAt)
                        .last("LIMIT 1")
        ).stream().findFirst().map(ProjectDashboardSnapshot::getRefreshedAt).orElse(null);
    }

    private void disableActiveShares(Long projectId) {
        List<ProjectDashboardShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardShare>()
                        .eq(ProjectDashboardShare::getProjectId, projectId)
                        .eq(ProjectDashboardShare::getStatus, "active")
        );
        LocalDateTime now = LocalDateTime.now();
        for (ProjectDashboardShare share : shares) {
            share.setStatus("disabled");
            share.setDisabledAt(now);
            shareMapper.updateById(share);
        }
    }

    private ProjectDashboardShare requireShare(Long id) {
        ProjectDashboardShare share = shareMapper.selectById(id);
        if (share == null) {
            throw new BizException(404, "Dashboard share not found");
        }
        return share;
    }

    private ProjectDashboardShare requireActiveShare(String shareCode) {
        ProjectDashboardShare share = shareMapper.selectOne(
                new LambdaQueryWrapper<ProjectDashboardShare>()
                        .eq(ProjectDashboardShare::getShareCode, shareCode)
                        .eq(ProjectDashboardShare::getStatus, "active")
                        .last("LIMIT 1")
        );
        if (share == null) {
            throw new BizException(404, "Dashboard share not found");
        }
        return share;
    }

    private Project requireReadableProject(Long projectId) {
        currentUserService.ensurePermission("project.read");
        Project project = requireProject(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return project;
    }

    private Project requireWritableProject(Long projectId) {
        currentUserService.ensurePermission("project.write");
        Project project = requireProject(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return project;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private Map<Long, String> loadQuestionTextMap(List<PollResult> records) {
        List<Long> questionIds = records.stream().map(PollResult::getQuestionId).filter(Objects::nonNull).distinct().toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionPoolItemMapper.selectList(
                new LambdaQueryWrapper<QuestionPoolItem>()
                        .in(QuestionPoolItem::getId, questionIds)
                        .select(QuestionPoolItem::getId, QuestionPoolItem::getQuestionText)
        ).stream().collect(Collectors.toMap(QuestionPoolItem::getId, QuestionPoolItem::getQuestionText, (a, b) -> a));
    }

    private String resolveDisplayText(PollResult record, Map<Long, String> questionTextMap) {
        if (StringUtils.hasText(record.getKeywordTextSnapshot())) {
            return record.getKeywordTextSnapshot().trim();
        }
        return questionTextMap.getOrDefault(record.getQuestionId(), "-");
    }

    private Map<String, String> loadPlatformNameMap(List<PollResult> records) {
        List<String> platformCodes = records.stream().map(PollResult::getPlatformCode).filter(StringUtils::hasText).distinct().toList();
        if (platformCodes.isEmpty()) {
            return Map.of();
        }
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, platformCodes)
                        .select(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformName)
        ).stream().collect(Collectors.toMap(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformName, (a, b) -> a));
    }

    private Map<String, String> loadPlatformUrlMap(List<PollResult> records) {
        List<String> platformCodes = records.stream().map(PollResult::getPlatformCode).filter(StringUtils::hasText).distinct().toList();
        if (platformCodes.isEmpty()) {
            return Map.of();
        }
        return sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "dashboard_platform_jump_url")
                        .eq(SysDictItem::getEnabled, true)
                        .in(SysDictItem::getDictKey, platformCodes)
                        .select(SysDictItem::getDictKey, SysDictItem::getDictValue)
        ).stream().collect(Collectors.toMap(SysDictItem::getDictKey, SysDictItem::getDictValue, (a, b) -> a));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSONUtil.parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }
}
