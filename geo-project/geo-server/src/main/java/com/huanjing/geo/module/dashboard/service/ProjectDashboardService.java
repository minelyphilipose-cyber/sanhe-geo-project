package com.huanjing.geo.module.dashboard.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dashboard.dto.ProjectDashboardAdviceRequest;
import com.huanjing.geo.module.dashboard.dto.ProjectDashboardAdviceVO;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardAdvice;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardAdviceMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardShareMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardSnapshotMapper;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDashboardService {

    private static final int MAX_VIEWABLE = 5000;
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_ADVICE_ITEMS = 8;
    private static final int MAX_ADVICE_SUMMARY_LENGTH = 2000;

    private final ProjectDashboardAdviceMapper adviceMapper;
    private final ProjectDashboardShareMapper shareMapper;
    private final ProjectDashboardSnapshotMapper snapshotMapper;
    private final ProjectMapper projectMapper;
    private final KeywordGroupService keywordGroupService;
    private final PollResultMapper pollResultMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final CurrentUserService currentUserService;
    private final ProjectDashboardSnapshotService snapshotService;

    public ProjectDashboardAdviceVO getAdvice(Long projectId) {
        Project project = requireReadableProject(projectId);
        ProjectDashboardAdvice draft = selectAdvice(project.getId(), "draft");
        return toAdviceVO(draft != null ? draft : selectAdvice(project.getId(), "published"));
    }

    @Transactional
    public ProjectDashboardAdviceVO saveAdvice(Long projectId, ProjectDashboardAdviceRequest req) {
        Project project = requireWritableProject(projectId);
        validateAdviceRequest(req, false);
        SysUser user = currentUserService.requireCurrentUser();
        return toAdviceVO(saveAdviceEntity(project.getId(), req, user));
    }

    @Transactional
    public ProjectDashboardAdviceVO publishAdvice(Long projectId, ProjectDashboardAdviceRequest req) {
        Project project = requireWritableProject(projectId);
        validateAdviceRequest(req, true);
        SysUser user = currentUserService.requireCurrentUser();
        ProjectDashboardAdvice draft = saveAdviceEntity(project.getId(), req, user);
        LocalDateTime now = LocalDateTime.now();
        ProjectDashboardAdvice published = selectAdvice(project.getId(), "published");
        if (published == null) {
            published = new ProjectDashboardAdvice();
            published.setProjectId(project.getId());
            published.setStatus("published");
            published.setCreatedBy(user.getId());
            published.setCreatedAt(now);
        }
        copyAdvicePayload(draft, published);
        published.setPublishedAt(now);
        published.setUpdatedBy(user.getId());
        published.setUpdatedAt(now);
        if (published.getId() == null) {
            adviceMapper.insert(published);
        } else {
            adviceMapper.updateById(published);
        }
        adviceMapper.deleteById(draft.getId());
        log.info("Project dashboard advice published, projectId={}, operatorId={}, publishedAt={}, summaryLength={}, highlights={}, improvementDirections={}, nextActions={}",
                project.getId(),
                user.getId(),
                now,
                published.getSummary() == null ? 0 : published.getSummary().length(),
                parseTextList(published.getHighlights()).size(),
                parseTextList(published.getImprovementDirections()).size(),
                parseTextList(published.getNextActions()).size());
        return toAdviceVO(published);
    }

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

    public Map<String, Object> getSummary(String shareCode, Integer days) {
        ProjectDashboardShare share = requireActiveShare(shareCode);
        Project project = requireProject(share.getProjectId());
        ensureSnapshotsReady(project.getId());
        int safeDays = normalizeDays(days);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", project.getProjectName());
        payload.put("brandName", project.getBrandName());
        payload.put("projectStage", project.getStage());
        payload.put("startDate", project.getStartDate());
        payload.put("endDate", project.getEndDate());
        payload.put("monitorPlatformCount", countMonitorPlatforms(project.getId()));
        payload.put("monitorQuestionCount", countMonitorQuestions(project.getId()));
        payload.put("days", safeDays);
        payload.put("summary", readSummary(project.getId(), safeDays));
        payload.put("comparison", readPeriodComparison(project.getId(), safeDays));
        payload.put("platforms", readPlatformSnapshots(project.getId(), safeDays));
        payload.put("wordCloud", readWordCloud(project.getId()));
        payload.put("contentProgress", readContentProgress(project.getId()));
        payload.put("advice", readPublishedAdvice(project.getId()));
        payload.put("refreshedAt", resolveRefreshedAt(project.getId()));
        return payload;
    }

    public Map<String, Object> getTrend(String shareCode, Integer days) {
        ProjectDashboardShare share = requireActiveShare(shareCode);
        ensureSnapshotsReady(share.getProjectId());
        int safeDays = normalizeDays(days);
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

    public Map<String, Object> refreshSnapshot(Long projectId) {
        Project project = requireWritableProject(projectId);
        return snapshotService.refreshProjectWithLock(project.getId());
    }

    public Map<String, Object> getSnapshotStatus(Long projectId) {
        Project project = requireReadableProject(projectId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId());
        payload.put("refreshedAt", Optional.ofNullable(resolveRefreshedAt(project.getId())).map(LocalDateTime::toString).orElse(""));
        return payload;
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
            wrapper.like("keyword_text_snapshot", trimmedKeyword);
        }

        long total = pollResultMapper.selectCount(wrapper);
        long visibleTotal = Math.min(total, MAX_VIEWABLE);
        if (offset >= MAX_VIEWABLE) {
            return emptyDetailResult(safeCurrent, safeSize, visibleTotal);
        }

        long pageSize = Math.min(safeSize, MAX_VIEWABLE - offset);
        Page<PollResult> page = pollResultMapper.selectPage(new Page<>(safeCurrent, pageSize, false), wrapper);
        List<PollResult> records = page.getRecords();
        Map<String, String> platformNameMap = loadPlatformNameMap(records);
        Map<String, String> platformUrlMap = loadPlatformUrlMap(records);
        Map<String, String> platformLogoMap = loadPlatformLogoMap(records);

        List<Map<String, Object>> items = records.stream().map(record -> {
            Map<String, Object> detail = parseObject(record.getDetailJson());
            Map<String, Object> matchDetails = parseObject(JSONUtil.toJsonStr(detail.get("match_details")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.getId());
            row.put("questionText", resolveDisplayText(record));
            row.put("platformCode", record.getPlatformCode());
            row.put("platformName", platformNameMap.getOrDefault(record.getPlatformCode(), record.getPlatformCode()));
            row.put("batchDate", record.getBatchDate());
            row.put("hasSnapshot", false);
            row.put("platformUrl", platformUrlMap.get(record.getPlatformCode()));
            row.put("platformLogoUrl", platformLogoMap.get(record.getPlatformCode()));
            row.put("answerText", stringValue(detail.get("platform_response")));
            row.put("matchType", record.getMatchType());
            row.put("siteMentioned", Boolean.TRUE.equals(record.getSiteMentioned()));
            row.put("contactMentioned", Boolean.TRUE.equals(record.getContactMentioned()));
            row.put("contactMentionCount", record.getContactMentionCount() != null
                    ? Math.max(record.getContactMentionCount(), 0)
                    : longValue(matchDetails.get("contact_mention_count")));
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
        if (count == 0 || isSnapshotStale(projectId)) {
            snapshotService.refreshProjectWithLock(projectId);
        }
    }

    private Map<String, Object> readSummary(Long projectId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        List<ProjectDashboardSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "daily_trend")
                        .ge(ProjectDashboardSnapshot::getSnapshotDate, startDate)
        );
        long hitTotal = 0;
        long contactTotal = 0;
        long siteTotal = 0;
        Set<String> platformCodes = new LinkedHashSet<>();
        for (ProjectDashboardSnapshot snapshot : snapshots) {
            Map<String, Object> value = parseObject(snapshot.getSnapshotValue());
            hitTotal += longValue(value.get("hitCount"));
            contactTotal += longValue(value.get("contactCount"));
            siteTotal += longValue(value.get("siteCount"));
            Object codes = value.get("hitPlatformCodes");
            if (codes instanceof Collection<?> collection) {
                for (Object code : collection) {
                    if (code != null && StringUtils.hasText(String.valueOf(code))) {
                        platformCodes.add(String.valueOf(code));
                    }
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hitTotal", hitTotal);
        payload.put("platformCount", platformCodes.size());
        payload.put("contactTotal", contactTotal);
        payload.put("siteTotal", siteTotal);
        return payload;
    }

    private Map<String, Object> readPeriodComparison(Long projectId, int days) {
        List<ProjectDashboardSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "period_summary")
                        .eq(ProjectDashboardSnapshot::getSnapshotKey, "days:" + days)
                        .orderByDesc(ProjectDashboardSnapshot::getRefreshedAt)
                        .orderByDesc(ProjectDashboardSnapshot::getId)
                        .last("LIMIT 2")
        );
        if (snapshots.size() < 2) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("available", false);
            payload.put("message", "暂无上一周期快照");
            return payload;
        }
        Map<String, Object> current = parseObject(snapshots.get(0).getSnapshotValue());
        Map<String, Object> previous = parseObject(snapshots.get(1).getSnapshotValue());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", true);
        payload.put("currentRefreshedAt", snapshots.get(0).getRefreshedAt());
        payload.put("previousRefreshedAt", snapshots.get(1).getRefreshedAt());
        payload.put("hitTotal", compareMetric(current, previous, "hitTotal"));
        payload.put("contactTotal", compareMetric(current, previous, "contactTotal"));
        payload.put("siteTotal", compareMetric(current, previous, "siteTotal"));
        payload.put("platformCount", compareMetric(current, previous, "platformCount"));
        payload.put("monitorQuestionCount", compareMetric(current, previous, "monitorQuestionCount"));
        payload.put("articleCreated", compareMetric(current, previous, "articleCreated"));
        payload.put("articlePublished", compareMetric(current, previous, "articlePublished"));
        return payload;
    }

    private Map<String, Object> compareMetric(Map<String, Object> current, Map<String, Object> previous, String key) {
        long currentValue = longValue(current.get(key));
        long previousValue = longValue(previous.get(key));
        long delta = currentValue - previousValue;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", currentValue);
        payload.put("previous", previousValue);
        payload.put("delta", delta);
        payload.put("rate", previousValue == 0L ? null : delta * 100.0 / previousValue);
        return payload;
    }

    private List<Map<String, Object>> readPlatformSnapshots(Long projectId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        List<ProjectDashboardSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "daily_platform")
                        .ge(ProjectDashboardSnapshot::getSnapshotDate, startDate)
                        .orderByAsc(ProjectDashboardSnapshot::getSnapshotDate, ProjectDashboardSnapshot::getId)
        );
        for (ProjectDashboardSnapshot snapshot : snapshots) {
            Map<String, Object> value = parseObject(snapshot.getSnapshotValue());
            String code = String.valueOf(value.getOrDefault("platformCode", snapshot.getSnapshotKey()));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, Object> row = merged.computeIfAbsent(code, ignored -> {
                Map<String, Object> initial = new LinkedHashMap<>();
                initial.put("platformCode", code);
                initial.put("platformName", stringValue(value.get("platformName")));
                initial.put("hitCount", 0L);
                initial.put("contactCount", 0L);
                initial.put("siteCount", 0L);
                return initial;
            });
            row.put("hitCount", longValue(row.get("hitCount")) + longValue(value.get("hitCount")));
            row.put("contactCount", longValue(row.get("contactCount")) + longValue(value.get("contactCount")));
            row.put("siteCount", longValue(row.get("siteCount")) + longValue(value.get("siteCount")));
            if (!StringUtils.hasText(String.valueOf(row.get("platformName"))) && StringUtils.hasText(stringValue(value.get("platformName")))) {
                row.put("platformName", stringValue(value.get("platformName")));
            }
        }
        for (AiPlatformConfig platform : loadQuestionPollPlatforms()) {
            String code = platform.getPlatformCode();
            Map<String, Object> row = merged.computeIfAbsent(code, ignored -> {
                Map<String, Object> initial = new LinkedHashMap<>();
                initial.put("platformCode", code);
                initial.put("hitCount", 0L);
                initial.put("contactCount", 0L);
                initial.put("siteCount", 0L);
                return initial;
            });
            row.put("platformName", StringUtils.hasText(stringValue(row.get("platformName")))
                    ? stringValue(row.get("platformName"))
                    : platform.getPlatformName());
            row.put("platformLogoUrl", platform.getPlatformLogoUrl());
        }
        return merged.values().stream()
                .sorted((a, b) -> Long.compare(longValue(b.get("hitCount")), longValue(a.get("hitCount"))))
                .toList();
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

    private Map<String, Object> readContentProgress(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "content_progress")
                        .last("LIMIT 1")
        ).stream().findFirst().map(snapshot -> parseObject(snapshot.getSnapshotValue())).orElseGet(() -> {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("generatedCount", 0L);
            empty.put("approvedCount", 0L);
            empty.put("distributedCount", 0L);
            empty.put("publishedCount", 0L);
            empty.put("pendingCount", 0L);
            empty.put("generationFailureCount", 0L);
            empty.put("distributionFailureCount", 0L);
            empty.put("items", List.of());
            return empty;
        });
    }

    private ProjectDashboardAdviceVO readPublishedAdvice(Long projectId) {
        return toAdviceVO(selectAdvice(projectId, "published"));
    }

    private LocalDateTime resolveRefreshedAt(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .orderByDesc(ProjectDashboardSnapshot::getRefreshedAt)
                        .last("LIMIT 1")
        ).stream().findFirst().map(ProjectDashboardSnapshot::getRefreshedAt).orElse(null);
    }

    private boolean isSnapshotStale(Long projectId) {
        LocalDate latestTrendDate = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .eq(ProjectDashboardSnapshot::getSnapshotType, "daily_trend")
                        .orderByDesc(ProjectDashboardSnapshot::getSnapshotDate)
                        .last("LIMIT 1")
        ).stream().findFirst().map(ProjectDashboardSnapshot::getSnapshotDate).orElse(null);
        return latestTrendDate == null || latestTrendDate.isBefore(LocalDate.now());
    }

    private int normalizeDays(Integer days) {
        if (days != null && (days == 7 || days == 30 || days == 90)) {
            return days;
        }
        return DEFAULT_DAYS;
    }

    private long countMonitorPlatforms(Long projectId) {
        Long count = aiPlatformConfigMapper.selectCount(new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForQuestionPoll, true));
        return count == null ? 0L : count;
    }

    private long countMonitorQuestions(Long projectId) {
        return keywordGroupService.countSelectedSavedKeywords(projectId);
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

    private ProjectDashboardAdvice selectAdvice(Long projectId, String status) {
        return adviceMapper.selectOne(
                new LambdaQueryWrapper<ProjectDashboardAdvice>()
                        .eq(ProjectDashboardAdvice::getProjectId, projectId)
                        .eq(ProjectDashboardAdvice::getStatus, status)
                        .last("LIMIT 1")
        );
    }

    private ProjectDashboardAdvice saveAdviceEntity(Long projectId, ProjectDashboardAdviceRequest req, SysUser user) {
        ProjectDashboardAdvice advice = selectAdvice(projectId, "draft");
        LocalDateTime now = LocalDateTime.now();
        if (advice == null) {
            advice = new ProjectDashboardAdvice();
            advice.setProjectId(projectId);
            advice.setStatus("draft");
            advice.setCreatedBy(user.getId());
            advice.setCreatedAt(now);
        }
        applyAdvicePayload(advice, req);
        advice.setUpdatedBy(user.getId());
        advice.setUpdatedAt(now);
        if (advice.getId() == null) {
            adviceMapper.insert(advice);
        } else {
            adviceMapper.updateById(advice);
        }
        return advice;
    }

    private void applyAdvicePayload(ProjectDashboardAdvice advice, ProjectDashboardAdviceRequest req) {
        advice.setSummary(normalizeSummary(req.getSummary()));
        advice.setHighlights(JSONUtil.toJsonStr(normalizeTextList(req.getHighlights())));
        advice.setImprovementDirections(JSONUtil.toJsonStr(normalizeTextList(req.getImprovementDirections())));
        advice.setNextActions(JSONUtil.toJsonStr(normalizeTextList(req.getNextActions())));
    }

    private void copyAdvicePayload(ProjectDashboardAdvice source, ProjectDashboardAdvice target) {
        target.setSummary(source.getSummary());
        target.setHighlights(source.getHighlights());
        target.setImprovementDirections(source.getImprovementDirections());
        target.setNextActions(source.getNextActions());
    }

    private void validateAdviceRequest(ProjectDashboardAdviceRequest req, boolean requireContent) {
        if (req == null) {
            throw new BizException(400, "Dashboard advice payload is required");
        }
        String summary = normalizeText(req.getSummary());
        if (summary.length() > MAX_ADVICE_SUMMARY_LENGTH) {
            throw new BizException(400, "Service summary is too long");
        }
        if (requireContent
                && !StringUtils.hasText(summary)
                && normalizeTextList(req.getHighlights()).isEmpty()
                && normalizeTextList(req.getImprovementDirections()).isEmpty()
                && normalizeTextList(req.getNextActions()).isEmpty()) {
            throw new BizException(400, "Dashboard advice content is required before publishing");
        }
    }

    private ProjectDashboardAdviceVO toAdviceVO(ProjectDashboardAdvice advice) {
        if (advice == null) {
            return null;
        }
        ProjectDashboardAdviceVO vo = new ProjectDashboardAdviceVO();
        vo.setId(advice.getId());
        vo.setProjectId(advice.getProjectId());
        vo.setSummary(advice.getSummary());
        vo.setHighlights(parseTextList(advice.getHighlights()));
        vo.setImprovementDirections(parseTextList(advice.getImprovementDirections()));
        vo.setNextActions(parseTextList(advice.getNextActions()));
        vo.setStatus(advice.getStatus());
        vo.setPublishedAt(advice.getPublishedAt());
        vo.setUpdatedAt(advice.getUpdatedAt());
        return vo;
    }

    private List<String> normalizeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .limit(MAX_ADVICE_ITEMS)
                .toList();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeSummary(String value) {
        String summary = normalizeText(value);
        if (summary.length() > MAX_ADVICE_SUMMARY_LENGTH) {
            throw new BizException(400, "Service summary is too long");
        }
        return summary;
    }

    private List<String> parseTextList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(json).stream()
                    .map(item -> item == null ? "" : String.valueOf(item).trim())
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
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
        currentUserService.ensurePermission("project.report.export");
        Project project = requireProject(projectId);
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        return project;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String resolveDisplayText(PollResult record) {
        if (StringUtils.hasText(record.getKeywordTextSnapshot())) {
            return record.getKeywordTextSnapshot().trim();
        }
        return "-";
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
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, platformCodes)
                        .isNotNull(AiPlatformConfig::getPlatformHomeUrl)
                        .ne(AiPlatformConfig::getPlatformHomeUrl, "")
                        .select(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformHomeUrl)
        ).stream().collect(Collectors.toMap(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformHomeUrl, (a, b) -> a));
    }

    private Map<String, String> loadPlatformLogoMap(List<PollResult> records) {
        List<String> platformCodes = records.stream().map(PollResult::getPlatformCode).filter(StringUtils::hasText).distinct().toList();
        if (platformCodes.isEmpty()) {
            return Map.of();
        }
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, platformCodes)
                        .isNotNull(AiPlatformConfig::getPlatformLogoUrl)
                        .ne(AiPlatformConfig::getPlatformLogoUrl, "")
                        .select(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformLogoUrl)
        ).stream().collect(Collectors.toMap(AiPlatformConfig::getPlatformCode, AiPlatformConfig::getPlatformLogoUrl, (a, b) -> a));
    }

    private List<AiPlatformConfig> loadQuestionPollPlatforms() {
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .orderByAsc(AiPlatformConfig::getPriorityLevel)
                        .orderByAsc(AiPlatformConfig::getPlatformName)
                        .orderByAsc(AiPlatformConfig::getId)
                        .select(
                                AiPlatformConfig::getPlatformCode,
                                AiPlatformConfig::getPlatformName,
                                AiPlatformConfig::getPlatformLogoUrl
                        )
        );
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
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
