package com.huanjing.geo.module.dashboard.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardShare;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardShareMapper;
import com.huanjing.geo.module.dashboard.mapper.ProjectDashboardSnapshotMapper;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import com.huanjing.geo.module.project.mapper.QuestionPoolItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDashboardSnapshotService {

    private final ProjectDashboardShareMapper shareMapper;
    private final ProjectDashboardSnapshotMapper snapshotMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final PollResultMapper pollResultMapper;
    private final QuestionPoolItemMapper questionPoolItemMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final DistributionTaskMapper distributionTaskMapper;

    public void refreshAllActive() {
        List<Long> projectIds = shareMapper.selectList(
                new LambdaQueryWrapper<ProjectDashboardShare>()
                        .eq(ProjectDashboardShare::getStatus, "active")
                        .select(ProjectDashboardShare::getProjectId)
        ).stream().map(ProjectDashboardShare::getProjectId).filter(Objects::nonNull).distinct().toList();
        for (Long projectId : projectIds) {
            try {
                refreshProject(projectId);
            } catch (Exception ex) {
                log.error("Refresh project dashboard snapshot failed, projectId={}", projectId, ex);
            }
        }
    }

    @Transactional
    public void refreshProject(Long projectId) {
        LocalDateTime refreshedAt = LocalDateTime.now();
        snapshotMapper.delete(new LambdaQueryWrapper<ProjectDashboardSnapshot>()
                .eq(ProjectDashboardSnapshot::getProjectId, projectId));

        List<ProjectDashboardSnapshot> snapshots = new ArrayList<>();
        snapshots.add(buildSummarySnapshot(projectId, refreshedAt));
        snapshots.addAll(buildPlatformSnapshots(projectId, refreshedAt));
        snapshots.addAll(buildDailyTrendSnapshots(projectId, refreshedAt));
        snapshots.addAll(buildWordFreqSnapshots(projectId, refreshedAt));

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
        hitWrapper.select("batch_date AS batchDate", "COALESCE(SUM(hit_count), 0) AS hitCount")
                .eq("project_id", projectId)
                .ge("batch_date", startDate)
                .groupBy("batch_date");
        for (Map<String, Object> row : pollDailyStatMapper.selectMaps(hitWrapper)) {
            LocalDate date = localDateValue(row.get("batchDate"));
            if (date != null && merged.containsKey(date)) {
                merged.get(date).put("hitCount", longValue(row.get("hitCount")));
            }
        }

        return merged.entrySet().stream()
                .map(entry -> createSnapshot(projectId, "daily_trend", null, JSONUtil.toJsonStr(entry.getValue()), entry.getKey(), refreshedAt))
                .toList();
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

        QueryWrapper<PollResult> legacyWrapper = new QueryWrapper<>();
        legacyWrapper.select("question_id AS questionId", "COUNT(*) AS hitCount")
                .eq("project_id", projectId)
                .eq("is_hit", 1)
                .isNull("keyword_text_snapshot")
                .isNotNull("question_id")
                .groupBy("question_id");
        List<Map<String, Object>> legacyRows = pollResultMapper.selectMaps(legacyWrapper);
        if (!legacyRows.isEmpty()) {
            List<Long> questionIds = legacyRows.stream()
                    .map(row -> longValue(row.get("questionId")))
                    .filter(v -> v > 0)
                    .distinct()
                    .toList();
            Map<Long, String> questionTextMap = questionPoolItemMapper.selectList(
                    new LambdaQueryWrapper<QuestionPoolItem>()
                            .in(QuestionPoolItem::getId, questionIds)
                            .select(QuestionPoolItem::getId, QuestionPoolItem::getQuestionText)
            ).stream().collect(Collectors.toMap(QuestionPoolItem::getId, QuestionPoolItem::getQuestionText, (a, b) -> a));
            for (Map<String, Object> row : legacyRows) {
                long questionId = longValue(row.get("questionId"));
                String questionText = Optional.ofNullable(questionTextMap.get(questionId)).map(String::trim).orElse("");
                if (!questionText.isEmpty()) {
                    frequencyByText.merge(questionText, longValue(row.get("hitCount")), Long::sum);
                }
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
}
