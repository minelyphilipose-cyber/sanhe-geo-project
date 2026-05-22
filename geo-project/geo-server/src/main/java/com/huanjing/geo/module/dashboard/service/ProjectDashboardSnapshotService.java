package com.huanjing.geo.module.dashboard.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardShareMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardSnapshotMapper;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.service.KeywordGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDashboardSnapshotService {

    private static final int REFRESH_LOCK_SECONDS = 60;
    private static final String REFRESH_LOCK_PREFIX = "geo:dashboard:snapshot:refresh:";

    private final ProjectDashboardShareMapper shareMapper;
    private final ProjectDashboardSnapshotMapper snapshotMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final PollResultMapper pollResultMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final KeywordGroupService keywordGroupService;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    public void refreshAllActive() {
        List<Long> projectIds = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardShare>()
                        .eq(ProjectDashboardShare::getStatus, "active")
                        .select(ProjectDashboardShare::getProjectId)
        ).stream().map(ProjectDashboardShare::getProjectId).filter(Objects::nonNull).distinct().toList();
        for (Long projectId : projectIds) {
            try {
                refreshProjectWithLock(projectId);
            } catch (Exception ex) {
                log.error("Refresh project dashboard snapshot failed, projectId={}", projectId, ex);
            }
        }
    }

    public Map<String, Object> refreshProjectWithLock(Long projectId) {
        String key = REFRESH_LOCK_PREFIX + projectId;
        String startedAt = LocalDateTime.now().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, startedAt, REFRESH_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            String runningStartedAt = stringRedisTemplate.opsForValue().get(key);
            return Map.of(
                    "status", "RUNNING",
                    "message", "Dashboard snapshot refresh is already running",
                    "startedAt", runningStartedAt == null ? "" : runningStartedAt,
                    "refreshedAt", Optional.ofNullable(resolveRefreshedAt(projectId)).map(LocalDateTime::toString).orElse("")
            );
        }
        try {
            transactionTemplate.executeWithoutResult(ignored -> refreshProject(projectId));
            return Map.of(
                    "status", "SUCCESS",
                    "message", "Dashboard snapshot refreshed",
                    "refreshedAt", Optional.ofNullable(resolveRefreshedAt(projectId)).map(LocalDateTime::toString).orElse("")
            );
        } finally {
            stringRedisTemplate.delete(key);
        }
    }

    @Transactional
    public void refreshProject(Long projectId) {
        LocalDateTime refreshedAt = LocalDateTime.now();
        snapshotMapper.delete(new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                .ne(ProjectDashboardSnapshot::getSnapshotType, "period_summary"));

        List<ProjectDashboardSnapshot> snapshots = new ArrayList<>();
        snapshots.add(buildSummarySnapshot(projectId, refreshedAt));
        snapshots.addAll(buildPlatformSnapshots(projectId, refreshedAt));
        List<ProjectDashboardSnapshot> dailyTrendSnapshots = buildDailyTrendSnapshots(projectId, refreshedAt);
        snapshots.addAll(dailyTrendSnapshots);
        snapshots.addAll(buildPeriodSummarySnapshots(projectId, dailyTrendSnapshots, refreshedAt));
        snapshots.addAll(buildDailyPlatformSnapshots(projectId, refreshedAt));
        snapshots.addAll(buildWordFreqSnapshots(projectId, refreshedAt));
        snapshots.add(buildContentProgressSnapshot(projectId, refreshedAt));

        for (ProjectDashboardSnapshot snapshot : snapshots) {
            snapshotMapper.insert(snapshot);
        }
    }

    private ProjectDashboardSnapshot buildSummarySnapshot(Long projectId, LocalDateTime refreshedAt) {
        QueryWrapper<PollDailyStat> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COALESCE(SUM(hit_count), 0) AS hitTotal",
                "COALESCE(SUM(CASE WHEN batch_date = CURDATE() THEN hit_count ELSE 0 END), 0) AS hitToday",
                "COUNT(DISTINCT CASE WHEN hit_count > 0 THEN platform_code END) AS platformCount",
                "COALESCE(SUM(contact_mention_count), 0) AS contactTotal",
                "COALESCE(SUM(CASE WHEN batch_date = CURDATE() THEN contact_mention_count ELSE 0 END), 0) AS contactToday",
                "COALESCE(SUM(site_mention_count), 0) AS siteTotal",
                "COALESCE(SUM(CASE WHEN batch_date = CURDATE() THEN site_mention_count ELSE 0 END), 0) AS siteToday"
        ).eq("project_id", projectId);

        Map<String, Object> row = pollDailyStatMapper.selectMaps(wrapper).stream().findFirst().orElse(Map.of());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hitTotal", longValue(row.get("hitTotal")));
        payload.put("hitToday", longValue(row.get("hitToday")));
        payload.put("platformCount", longValue(row.get("platformCount")));
        payload.put("contactTotal", longValue(row.get("contactTotal")));
        payload.put("contactToday", longValue(row.get("contactToday")));
        payload.put("siteTotal", longValue(row.get("siteTotal")));
        payload.put("siteToday", longValue(row.get("siteToday")));
        return createSnapshot(projectId, "summary", null, JSONUtil.toJsonStr(payload), null, refreshedAt);
    }

    private List<ProjectDashboardSnapshot> buildPlatformSnapshots(Long projectId, LocalDateTime refreshedAt) {
        QueryWrapper<PollDailyStat> wrapper = new QueryWrapper<>();
        wrapper.select(
                "platform_code AS platformCode",
                "MAX(platform_name) AS platformName",
                "COALESCE(SUM(hit_count), 0) AS hitCount",
                "COALESCE(SUM(contact_mention_count), 0) AS contactCount",
                "COALESCE(SUM(site_mention_count), 0) AS siteCount"
        ).eq("project_id", projectId)
                .groupBy("platform_code")
                .orderByDesc("SUM(hit_count)");

        return pollDailyStatMapper.selectMaps(wrapper).stream().map(row -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("platformCode", stringValue(row.get("platformCode")));
            payload.put("platformName", stringValue(row.get("platformName")));
            payload.put("hitCount", longValue(row.get("hitCount")));
            payload.put("contactCount", longValue(row.get("contactCount")));
            payload.put("siteCount", longValue(row.get("siteCount")));
            return createSnapshot(projectId, "platform", stringValue(row.get("platformCode")), JSONUtil.toJsonStr(payload), null, refreshedAt);
        }).toList();
    }

    private List<ProjectDashboardSnapshot> buildDailyTrendSnapshots(Long projectId, LocalDateTime refreshedAt) {
        LocalDate startDate = LocalDate.now().minusDays(89);
        Map<LocalDate, Map<String, Object>> merged = new TreeMap<>();
        for (int i = 0; i < 90; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("articleCreated", 0L);
            row.put("articlePublished", 0L);
            row.put("hitCount", 0L);
            merged.put(date, row);
        }

        QueryWrapper<ArticleBatch> createdWrapper = new QueryWrapper<>();
        createdWrapper.select("batch_date AS batchDate", "COALESCE(SUM(total_count), 0) AS articleCreated")
                .eq("project_id", projectId)
                .ge("batch_date", startDate)
                .groupBy("batch_date");
        for (Map<String, Object> row : articleBatchMapper.selectMaps(createdWrapper)) {
            LocalDate date = localDateValue(row.get("batchDate"));
            if (date != null && merged.containsKey(date)) {
                merged.get(date).put("articleCreated", longValue(row.get("articleCreated")));
            }
        }

        QueryWrapper<DistributionTask> publishedWrapper = new QueryWrapper<>();
        publishedWrapper.select("DATE(finished_at) AS finishedDate", "COUNT(DISTINCT article_id) AS articlePublished")
                .eq("project_id", projectId)
                .eq("status", "completed")
                .isNotNull("finished_at")
                .ge("finished_at", startDate.atStartOfDay())
                .groupBy("DATE(finished_at)");
        for (Map<String, Object> row : distributionTaskMapper.selectMaps(publishedWrapper)) {
            LocalDate date = localDateValue(row.get("finishedDate"));
            if (date != null && merged.containsKey(date)) {
                merged.get(date).put("articlePublished", longValue(row.get("articlePublished")));
            }
        }

        QueryWrapper<PollDailyStat> hitWrapper = new QueryWrapper<>();
        hitWrapper.select(
                        "batch_date AS batchDate",
                        "COALESCE(SUM(hit_count), 0) AS hitCount",
                        "COALESCE(SUM(site_mention_count), 0) AS siteCount",
                        "COALESCE(SUM(contact_mention_count), 0) AS contactCount"
                )
                .eq("project_id", projectId)
                .ge("batch_date", startDate)
                .groupBy("batch_date");
        for (Map<String, Object> row : pollDailyStatMapper.selectMaps(hitWrapper)) {
            LocalDate date = localDateValue(row.get("batchDate"));
            if (date != null && merged.containsKey(date)) {
                merged.get(date).put("hitCount", longValue(row.get("hitCount")));
                merged.get(date).put("siteCount", longValue(row.get("siteCount")));
                merged.get(date).put("contactCount", longValue(row.get("contactCount")));
            }
        }

        QueryWrapper<PollDailyStat> platformWrapper = new QueryWrapper<>();
        platformWrapper.select("batch_date AS batchDate", "platform_code AS platformCode")
                .eq("project_id", projectId)
                .gt("hit_count", 0)
                .ge("batch_date", startDate)
                .groupBy("batch_date", "platform_code");
        for (Map<String, Object> row : pollDailyStatMapper.selectMaps(platformWrapper)) {
            LocalDate date = localDateValue(row.get("batchDate"));
            String platformCode = stringValue(row.get("platformCode"));
            if (date != null && merged.containsKey(date) && !platformCode.isBlank()) {
                Object existing = merged.get(date).get("hitPlatformCodes");
                @SuppressWarnings("unchecked")
                Set<String> codes = existing instanceof Set<?> set
                        ? (Set<String>) set
                        : new LinkedHashSet<>();
                codes.add(platformCode);
                merged.get(date).put("hitPlatformCodes", codes);
            }
        }

        for (Map<String, Object> row : merged.values()) {
            if (!row.containsKey("siteCount")) {
                row.put("siteCount", 0L);
            }
            if (!row.containsKey("contactCount")) {
                row.put("contactCount", 0L);
            }
            if (!row.containsKey("hitPlatformCodes")) {
                row.put("hitPlatformCodes", List.of());
            }
        }

        return merged.entrySet().stream()
                .map(entry -> createSnapshot(projectId, "daily_trend", null, JSONUtil.toJsonStr(entry.getValue()), entry.getKey(), refreshedAt))
                .toList();
    }

    private List<ProjectDashboardSnapshot> buildPeriodSummarySnapshots(Long projectId,
                                                                       List<ProjectDashboardSnapshot> dailyTrendSnapshots,
                                                                       LocalDateTime refreshedAt) {
        if (dailyTrendSnapshots == null || dailyTrendSnapshots.isEmpty()) {
            return List.of();
        }
        List<Integer> periods = List.of(7, 30, 90);
        return periods.stream()
                .map(days -> {
                    LocalDate startDate = LocalDate.now().minusDays(days - 1L);
                    Map<String, Object> payload = aggregatePeriodSummary(dailyTrendSnapshots, startDate);
                    payload.put("days", days);
                    payload.put("periodStart", startDate);
                    payload.put("periodEnd", LocalDate.now());
                    payload.put("monitorQuestionCount", keywordGroupService.countSelectedSavedKeywords(projectId));
                    return createSnapshot(projectId, "period_summary", "days:" + days, JSONUtil.toJsonStr(payload), LocalDate.now(), refreshedAt);
                })
                .toList();
    }

    private Map<String, Object> aggregatePeriodSummary(List<ProjectDashboardSnapshot> dailyTrendSnapshots, LocalDate startDate) {
        long hitTotal = 0L;
        long contactTotal = 0L;
        long siteTotal = 0L;
        long articleCreated = 0L;
        long articlePublished = 0L;
        Set<String> platformCodes = new LinkedHashSet<>();
        for (ProjectDashboardSnapshot snapshot : dailyTrendSnapshots) {
            if (snapshot.getSnapshotDate() == null || snapshot.getSnapshotDate().isBefore(startDate)) {
                continue;
            }
            Map<String, Object> value = parseObject(snapshot.getSnapshotValue());
            hitTotal += longValue(value.get("hitCount"));
            contactTotal += longValue(value.get("contactCount"));
            siteTotal += longValue(value.get("siteCount"));
            articleCreated += longValue(value.get("articleCreated"));
            articlePublished += longValue(value.get("articlePublished"));
            Object codes = value.get("hitPlatformCodes");
            if (codes instanceof Collection<?> collection) {
                for (Object code : collection) {
                    String platformCode = stringValue(code).trim();
                    if (!platformCode.isEmpty()) {
                        platformCodes.add(platformCode);
                    }
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hitTotal", hitTotal);
        payload.put("platformCount", platformCodes.size());
        payload.put("contactTotal", contactTotal);
        payload.put("siteTotal", siteTotal);
        payload.put("articleCreated", articleCreated);
        payload.put("articlePublished", articlePublished);
        return payload;
    }

    private List<ProjectDashboardSnapshot> buildDailyPlatformSnapshots(Long projectId, LocalDateTime refreshedAt) {
        LocalDate startDate = LocalDate.now().minusDays(89);
        QueryWrapper<PollDailyStat> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "batch_date AS batchDate",
                        "platform_code AS platformCode",
                        "MAX(platform_name) AS platformName",
                        "COALESCE(SUM(hit_count), 0) AS hitCount",
                        "COALESCE(SUM(contact_mention_count), 0) AS contactCount",
                        "COALESCE(SUM(site_mention_count), 0) AS siteCount"
                )
                .eq("project_id", projectId)
                .ge("batch_date", startDate)
                .groupBy("batch_date", "platform_code")
                .orderByAsc("batch_date");

        return pollDailyStatMapper.selectMaps(wrapper).stream().map(row -> {
            LocalDate date = localDateValue(row.get("batchDate"));
            String platformCode = stringValue(row.get("platformCode"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("platformCode", platformCode);
            payload.put("platformName", stringValue(row.get("platformName")));
            payload.put("hitCount", longValue(row.get("hitCount")));
            payload.put("contactCount", longValue(row.get("contactCount")));
            payload.put("siteCount", longValue(row.get("siteCount")));
            return createSnapshot(projectId, "daily_platform", platformCode, JSONUtil.toJsonStr(payload), date, refreshedAt);
        }).toList();
    }

    private List<ProjectDashboardSnapshot> buildWordFreqSnapshots(Long projectId, LocalDateTime refreshedAt) {
        Map<String, Long> frequencyByText = new LinkedHashMap<>();

        QueryWrapper<PollResult> keywordWrapper = new QueryWrapper<>();
        keywordWrapper.select("keyword_text_snapshot AS keywordText", "COUNT(*) AS hitCount")
                .eq("project_id", projectId)
                .eq("is_hit", 1)
                .isNotNull("keyword_text_snapshot")
                .ne("keyword_text_snapshot", "")
                .groupBy("keyword_text_snapshot");
        for (Map<String, Object> row : pollResultMapper.selectMaps(keywordWrapper)) {
            String keywordText = stringValue(row.get("keywordText")).trim();
            if (!keywordText.isEmpty()) {
                frequencyByText.merge(keywordText, longValue(row.get("hitCount")), Long::sum);
            }
        }

        return frequencyByText.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(30)
                .map(entry -> createSnapshot(
                        projectId,
                        "word_freq",
                        entry.getKey(),
                        JSONUtil.toJsonStr(Map.of("frequency", entry.getValue())),
                        null,
                        refreshedAt
                ))
                .toList();
    }

    private ProjectDashboardSnapshot buildContentProgressSnapshot(Long projectId, LocalDateTime refreshedAt) {
        long generatedCount = articleDraftMapper.selectCount(
                new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, projectId)
        );
        long approvedCount = articleDraftMapper.selectCount(
                new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, projectId)
                        .in(ArticleDraft::getStatus, List.of("approved", "distributing", "distributed", "published", "unpublished"))
        );
        Set<Long> distributedArticleIds = loadDistributionArticleIds(projectId, List.of("submitting", "submitted", "confirmed"));
        Set<Long> publishedArticleIds = loadDistributionArticleIds(projectId, List.of("submitted", "confirmed"));
        Set<Long> pendingArticleIds = loadDistributionArticleIds(projectId, List.of("pending"));
        Set<Long> failedDistributionArticleIds = loadDistributionArticleIds(projectId, List.of("failed"));
        long generationFailureCount = sumGenerationFailureCount(projectId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedCount", generatedCount);
        payload.put("approvedCount", approvedCount);
        payload.put("distributedCount", distributedArticleIds.size());
        payload.put("publishedCount", publishedArticleIds.size());
        payload.put("pendingCount", pendingArticleIds.size());
        payload.put("generationFailureCount", generationFailureCount);
        payload.put("distributionFailureCount", failedDistributionArticleIds.size());
        payload.put("items", List.of(
                progressItem("generated", "已生成", generatedCount, "已进入内容库的文章草稿数量"),
                progressItem("approved", "已就绪", approvedCount, "当前处于可发布状态的文章数量"),
                progressItem("distributed", "已分发", distributedArticleIds.size(), "已实际进入分发执行的去重文章数量"),
                progressItem("published", "发布成功", publishedArticleIds.size(), "分发任务成功提交或确认的去重文章数量"),
                progressItem("pending", "待处理", pendingArticleIds.size(), "待执行分发任务按文章去重"),
                progressItem("generation_failed", "生成失败", generationFailureCount, "内容生成批次中的失败条目数量"),
                progressItem("distribution_failed", "分发失败", failedDistributionArticleIds.size(), "分发任务失败的去重文章数量")
        ));
        return createSnapshot(projectId, "content_progress", null, JSONUtil.toJsonStr(payload), null, refreshedAt);
    }

    private Map<String, Object> progressItem(String key, String label, long value, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("value", value);
        item.put("description", description);
        return item;
    }

    private Set<Long> loadArticleIdsByStatus(Long projectId, Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return articleDraftMapper.selectList(
                new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, projectId)
                        .in(ArticleDraft::getStatus, statuses)
                        .select(ArticleDraft::getId)
        ).stream()
                .map(ArticleDraft::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> loadDistributionArticleIds(Long projectId, Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getProjectId, projectId)
                        .in(DistributionTask::getStatus, statuses)
                        .select(DistributionTask::getArticleId)
        ).stream()
                .map(DistributionTask::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private long sumGenerationFailureCount(Long projectId) {
        QueryWrapper<ArticleBatch> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(SUM(failed_count), 0) AS failed_count")
                .eq("project_id", projectId);
        return articleBatchMapper.selectMaps(wrapper).stream()
                .findFirst()
                .map(row -> {
                    Object value = row.get("failed_count");
                    return value != null ? value : row.values().stream().findFirst().orElse(null);
                })
                .filter(Objects::nonNull)
                .map(value -> value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value)))
                .orElse(0L);
    }

    private ProjectDashboardSnapshot createSnapshot(Long projectId,
                                                    String snapshotType,
                                                    String snapshotKey,
                                                    String snapshotValue,
                                                    LocalDate snapshotDate,
                                                    LocalDateTime refreshedAt) {
        ProjectDashboardSnapshot snapshot = new ProjectDashboardSnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setSnapshotKey(snapshotKey);
        snapshot.setSnapshotValue(snapshotValue);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setRefreshedAt(refreshedAt);
        return snapshot;
    }

    private LocalDateTime resolveRefreshedAt(Long projectId) {
        return snapshotMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                        .eq(ProjectDashboardSnapshot::getProjectId, projectId)
                        .orderByDesc(ProjectDashboardSnapshot::getRefreshedAt)
                        .last("LIMIT 1")
        ).stream().findFirst().map(ProjectDashboardSnapshot::getRefreshedAt).orElse(null);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDate localDateValue(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value == null) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSONUtil.parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }
}
