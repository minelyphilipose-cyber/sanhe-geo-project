package com.huanjing.geo.module.report.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.report.dto.ReportPublishRequest;
import com.huanjing.geo.module.report.entity.*;
import com.huanjing.geo.module.report.mapper.*;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    private static final Set<String> DISABLED_POSTSALE_TYPES = Set.of("biweekly", "monthly", "quarterly");
    private static final Set<String> LEGACY_PRESALE_TYPES = Set.of("presale", "presale_diagnosis");
    private static final String REPORT_DISABLED_MESSAGE = "report generation disabled by product policy";
    private static final int POLL_DETAIL_HOT_DAYS = 120;

    private final ReportMapper reportMapper;
    private final ReportAccessLogMapper reportAccessLogMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;
    private final PermissionService permissionService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReportPdfService reportPdfService;
    private final MinioStorageService minioStorageService;
    private final ObjectStorageService objectStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final PostsaleReportSnapshotMapper postsaleSnapshotMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final PollResultMapper pollResultMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final DistributionTaskMapper distributionTaskMapper;

    public Page<Report> page(long current, long size, Long projectId, String keyword, String status) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreatedAt)
                .notIn(Report::getReportType, DISABLED_POSTSALE_TYPES)
                .notIn(Report::getReportType, LEGACY_PRESALE_TYPES);
        if (projectId != null) {
            ensureProjectReadable(projectId);
            wrapper.eq(Report::getProjectId, projectId);
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .isNull(Project::getDeletedAt)
                            .like(Project::getProjectName, keyword.trim())
                            .select(Project::getId)
            ).stream().map(Project::getId).toList();
            if (projectIds.isEmpty()) {
                return new Page<>(current, size);
            }
            if (projectId != null) {
                if (!projectIds.contains(projectId)) {
                    return new Page<>(current, size);
                }
            } else {
                wrapper.in(Report::getProjectId, projectIds);
            }
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Report::getStatus, status.trim());
        }
        Long scopePartnerId = currentUserService.requirePartnerScope(user);
        if (scopePartnerId != null) {
            wrapper.inSql(Report::getProjectId,
                    "select id from project where deleted_at is null and partner_id = " + scopePartnerId);
        } else if (internalScopeService.isSalesUser(user)) {
            internalScopeService.applyNoRows(wrapper);
        } else if (internalScopeService.requiresOwnerScope(user)) {
            wrapper.inSql(Report::getProjectId,
                    "select p.id from project p join company c on c.id = p.company_id " +
                            "where p.deleted_at is null and c.deleted_at is null and c.owner_id = " + user.getId());
        }
        Page<Report> pageData = reportMapper.selectPage(new Page<>(current, size), wrapper);
        fillProjectNames(pageData.getRecords());
        return pageData;
    }

    public Map<String, Object> detail(Long reportId) {
        currentUserService.ensurePermission("project.read");
        Report report = requireReport(reportId);
        ensureReportTypeActive(report.getReportType());
        ensureProjectReadable(report.getProjectId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report", report);
        data.put("subject", buildReportSubject(report.getProjectId()));
        if (isPostsaleType(report.getReportType())) {
            PostsaleReportSnapshot snapshot = postsaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PostsaleReportSnapshot>().eq(PostsaleReportSnapshot::getReportId, reportId)
            );
            data.put("postsaleSnapshot", snapshot);
        }
        return data;
    }

    @Transactional
    public Map<String, Report> generatePostsaleDraftPair(Long projectId,
                                                         String reportType,
                                                         LocalDate periodStart,
                                                         LocalDate periodEnd,
                                                         Long creatorId,
                                                         boolean forceNewVersion) {
        if (!isPostsaleType(reportType)) {
            throw new BizException(400, "Unsupported postsale report type");
        }
        ensurePostsaleEnabled(reportType);
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }

        Report existingClientLatest = findLatestByPeriod(projectId, reportType, periodStart, periodEnd, "client");
        Report existingInternalLatest = findLatestByPeriod(projectId, reportType, periodStart, periodEnd, "internal");
        if (!forceNewVersion && existingClientLatest != null && existingInternalLatest != null) {
            return Map.of("client", existingClientLatest, "internal", existingInternalLatest);
        }
        if (forceNewVersion) {
            markLatestAsHistory(projectId, reportType, "client");
            markLatestAsHistory(projectId, reportType, "internal");
        }

        Integer maxVersion = Optional.ofNullable(reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .eq(Report::getReportType, reportType)
                        .orderByDesc(Report::getVersionNo)
                        .last("LIMIT 1")
        )).map(Report::getVersionNo).orElse(0);
        int nextVersion = maxVersion + 1;

        Report internal = new Report();
        internal.setProjectId(projectId);
        internal.setReportType(reportType);
        internal.setVersionNo(nextVersion);
        internal.setPeriodStart(periodStart);
        internal.setPeriodEnd(periodEnd);
        internal.setStatus("draft");
        internal.setVisibility("internal");
        internal.setIsLatest(true);
        internal.setShareToken("tmp_" + RandomUtil.randomString(24));
        internal.setCreatedBy(creatorId);
        reportMapper.insert(internal);

        Report client = new Report();
        client.setProjectId(projectId);
        client.setReportType(reportType);
        client.setVersionNo(nextVersion);
        client.setPeriodStart(periodStart);
        client.setPeriodEnd(periodEnd);
        client.setStatus("draft");
        client.setVisibility("client");
        client.setIsLatest(true);
        client.setShareToken("tmp_" + RandomUtil.randomString(24));
        client.setPairReportId(internal.getId());
        client.setCreatedBy(creatorId);
        reportMapper.insert(client);

        internal.setPairReportId(client.getId());
        reportMapper.updateById(internal);

        Map<String, Object> method = buildMethodologyNotes();
        PostsaleReportSnapshot internalSnap = buildPostsaleSnapshot(internal, project, true, method);
        PostsaleReportSnapshot clientSnap = buildPostsaleSnapshot(client, project, false, method);
        postsaleSnapshotMapper.insert(internalSnap);
        postsaleSnapshotMapper.insert(clientSnap);

        internal.setPdfUrl(reportPdfService.generateAndUpload(internal));
        internal.setPdfGeneratedAt(LocalDateTime.now());
        reportMapper.updateById(internal);

        client.setPdfUrl(reportPdfService.generateAndUpload(client));
        client.setPdfGeneratedAt(LocalDateTime.now());
        reportMapper.updateById(client);

        return Map.of("client", client, "internal", internal);
    }

    @Transactional
    public Report regeneratePdf(Long reportId) {
        currentUserService.ensurePermission("report.review");
        Report report = requireReport(reportId);
        ensureReportTypeActive(report.getReportType());
        if (!StringUtils.hasText(report.getShareToken())) {
            report.setShareToken("tmp_" + RandomUtil.randomString(24));
            reportMapper.updateById(report);
        }
        String pdfUrl = reportPdfService.generateAndUpload(report);
        report.setPdfUrl(pdfUrl);
        report.setPdfGeneratedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        return report;
    }

    @Transactional
    public Map<String, Report> regeneratePostsalePair(Long reportId) {
        ensurePostsaleRegenerateAllowed(currentUserService.requireCurrentUser());
        Report report = requireReport(reportId);
        if (!isPostsaleType(report.getReportType())) {
            throw new BizException(400, "Only postsale reports support regenerate");
        }
        ensurePostsaleEnabled(report.getReportType());
        return generatePostsaleDraftPair(
                report.getProjectId(),
                report.getReportType(),
                report.getPeriodStart(),
                report.getPeriodEnd(),
                currentUserService.requireCurrentUser().getId(),
                true
        );
    }

    @Transactional
    public Report publish(Long reportId, ReportPublishRequest req) {
        SysUser user = currentUserService.requireCurrentUser();
        if (!permissionService.hasPerm(user, "report.review")) {
            throw new BizException(403, "No permission to publish report");
        }
        Report report = requireReport(reportId);
        ensureReportTypeActive(report.getReportType());
        if (!"draft".equals(report.getStatus())) {
            throw new BizException(400, "Only draft report can be published");
        }

        LocalDateTime now = LocalDateTime.now();
        if (isPostsaleType(report.getReportType())) {
            checkPostsalePublishPreconditions(report);
        } else {
            reportMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Report>()
                            .eq(Report::getProjectId, report.getProjectId())
                            .eq(Report::getReportType, report.getReportType())
                            .eq(Report::getVisibility, report.getVisibility())
                            .eq(Report::getStatus, "published")
                            .set(Report::getStatus, "superseded")
                            .set(Report::getSupersededBy, report.getId())
            );
        }

        report.setStatus("published");
        report.setPublishedAt(now);
        report.setPublishedBy(user.getId());
        if ("client".equalsIgnoreCase(report.getVisibility())) {
            if (!StringUtils.hasText(report.getShareToken()) || report.getShareToken().startsWith("tmp_")) {
                report.setShareToken("shr_" + RandomUtil.randomString(24));
            }
            if (StringUtils.hasText(req.getSharePassword())) {
                report.setSharePasswordHash(BCRYPT.encode(req.getSharePassword()));
            }
        } else {
            report.setShareToken(null);
            report.setSharePasswordHash(null);
        }
        report.setShareExpiresAt(req.getShareExpiresAt());
        reportMapper.updateById(report);
        return report;
    }

    @Transactional
    public Report intercept(Long reportId, String reason) {
        currentUserService.ensurePermission("report.review");
        Report report = requireReport(reportId);
        report.setStatus("intercepted");
        report.setStageAdvice(reason);
        reportMapper.updateById(report);
        return report;
    }

    public Map<String, Object> getShareReport(String token, HttpServletRequest request) {
        Report tokenReport = findReportByToken(token);
        if (tokenReport == null || !"client".equalsIgnoreCase(tokenReport.getVisibility())) {
            return Map.of("bizCode", "NOT_FOUND", "message", "Share report not found");
        }
        if (isDisabledPostsaleType(tokenReport.getReportType())) {
            return Map.of("bizCode", "DISABLED", "message", "该报表类型已停用");
        }
        if ("draft".equals(tokenReport.getStatus()) || "generating".equals(tokenReport.getStatus())) {
            return Map.of("bizCode", "NOT_PUBLISHED", "message", "Report is not published yet");
        }
        if ("superseded".equals(tokenReport.getStatus())) {
            Report latest = findLatestPublishedClient(tokenReport.getProjectId(), tokenReport.getReportType());
            if (latest == null || !StringUtils.hasText(latest.getShareToken())) {
                return Map.of("bizCode", "VERSION_SUPERSEDED", "message", "This version is superseded");
            }
            return Map.of("bizCode", "VERSION_SUPERSEDED", "message", "This version is superseded", "latestToken", latest.getShareToken());
        }
        if (!"published".equals(tokenReport.getStatus()) || isExpired(tokenReport)) {
            return Map.of("bizCode", "LINK_EXPIRED", "message", "Share link has expired");
        }
        if (requiresPassword(tokenReport)) {
            logAccess(tokenReport, token, request, false);
            return Map.of("bizCode", "PASSWORD_REQUIRED", "message", "Password required");
        }
        Map<String, Object> payload = buildSharePayload(tokenReport);
        payload.put("bizCode", "SUCCESS");
        payload.put("message", "ok");
        logAccess(tokenReport, token, request, true);
        return payload;
    }
    public Map<String, Object> verifySharePassword(String token, String password, HttpServletRequest request) {
        Report report = findReportByToken(token);
        if (report == null || !"published".equals(report.getStatus()) || isExpired(report)) {
            throw new BizException(400, "Share link has expired");
        }
        ensureReportTypeActive(report.getReportType());
        String lockKey = "share:lock:" + token;
        String raw = stringRedisTemplate.opsForValue().get(lockKey);
        int failures = raw == null ? 0 : Integer.parseInt(raw);
        if (failures >= 5) {
            throw new BizException(429, "Too many failed attempts, please try again later");
        }

        if (!requiresPassword(report) || BCRYPT.matches(password, report.getSharePasswordHash())) {
            stringRedisTemplate.delete(lockKey);
            Map<String, Object> payload = buildSharePayload(report);
            payload.put("bizCode", "SUCCESS");
            payload.put("message", "ok");
            logAccess(report, token, request, true);
            return payload;
        }

        Long next = stringRedisTemplate.opsForValue().increment(lockKey);
        if (next != null && next == 1L) {
            stringRedisTemplate.expire(lockKey, Duration.ofHours(1));
        }
        logAccess(report, token, request, false);
        throw new BizException(400, "Incorrect password");
    }
    @Transactional
    public String resolveSharePdfUrl(String token) {
        Report report = findReportByToken(token);
        if (report == null || !"published".equals(report.getStatus()) || !"client".equalsIgnoreCase(report.getVisibility())) {
            throw new BizException(404, "Report not found");
        }
        ensureReportTypeActive(report.getReportType());
        if (isExpired(report)) {
            throw new BizException(400, "Link expired");
        }
        String pdfUrl = report.getPdfUrl();
        String objectKey = buildPdfObjectKey(report);
        boolean needsRegenerate = !StringUtils.hasText(pdfUrl) || isLegacySharePdfPath(token, pdfUrl);

        if (needsRegenerate) {
            if (requiresPassword(report)) {
                throw new BizException(400, "PDF unavailable, please republish report");
            }
            String generated = reportPdfService.generateAndUpload(report);
            report.setPdfUrl(generated);
            report.setPdfGeneratedAt(LocalDateTime.now());
            reportMapper.updateById(report);
        }

        return minioStorageService.buildPresignedDownloadUrl(objectKey, 600);
    }

    private Map<String, Object> buildSharePayload(Report report) {
        ensureReportTypeActive(report.getReportType());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "loaded");
        data.put("report", report);
        data.put("subject", buildReportSubject(report.getProjectId()));
        if (isPostsaleType(report.getReportType())) {
            PostsaleReportSnapshot snapshot = postsaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PostsaleReportSnapshot>().eq(PostsaleReportSnapshot::getReportId, report.getId())
            );
            data.put("snapshot", snapshot);
        }
        return data;
    }

    private Report findLatestByPeriod(Long projectId, String reportType, LocalDate start, LocalDate end, String visibility) {
        return reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .eq(Report::getReportType, reportType)
                        .eq(Report::getPeriodStart, start)
                        .eq(Report::getPeriodEnd, end)
                        .eq(Report::getVisibility, visibility)
                        .eq(Report::getIsLatest, true)
                        .orderByDesc(Report::getId)
                        .last("LIMIT 1")
        );
    }

    private void markLatestAsHistory(Long projectId, String reportType, String visibility) {
        reportMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .eq(Report::getReportType, reportType)
                        .eq(Report::getVisibility, visibility)
                        .eq(Report::getIsLatest, true)
                        .set(Report::getIsLatest, false)
                        .set(Report::getStatus, "superseded")
        );
    }

    private PostsaleReportSnapshot buildPostsaleSnapshot(Report report,
                                                         Project project,
                                                         boolean internal,
                                                         Map<String, Object> methodology) {
        LocalDate start = report.getPeriodStart();
        LocalDate end = report.getPeriodEnd();
        SummaryPack current = aggregateSummary(report.getProjectId(), start, end);
        LocalDate prevStart = previousStart(report.getReportType(), start, end);
        LocalDate prevEnd = previousEnd(report.getReportType(), start);
        SummaryPack previous = aggregateSummary(report.getProjectId(), prevStart, prevEnd);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("data_updated_at", LocalDateTime.now());
        summary.put("total_hit_count", current.hitCount);
        summary.put("total_completed_count", current.completedCount);
        summary.put("total_request_count", current.requestCount);
        summary.put("hit_rate", percent(current.hitCount, current.completedCount));
        summary.put("site_mention_count", current.siteMentionCount);
        summary.put("contact_mention_count", current.contactMentionCount);
        summary.put("platform_coverage_count", current.platformCoverageCount);
        summary.put("platform_total_count", current.platformTotalCount);
        summary.put("keyword_coverage_rate", current.keywordCoverageRate);
        summary.put("vs_previous", Map.of(
                "hit_rate_change", percent(current.hitCount, current.completedCount).subtract(percent(previous.hitCount, previous.completedCount)),
                "site_mention_change", current.siteMentionCount - previous.siteMentionCount,
                "contact_mention_change", current.contactMentionCount - previous.contactMentionCount
        ));

        Map<String, Object> trend = buildTrendData(report.getProjectId(), start, end);
        Map<String, Object> detail = buildDetailData(report.getProjectId(), report.getReportType(), start, end, internal);
        Map<String, Object> platformBreakdown = buildPlatformBreakdown(report.getProjectId(), start, end);
        Map<String, Object> comparison = buildComparisonData(start, end, current, previous);
        Map<String, Object> targetEval = buildTargetEvaluation(report, project, current, internal);
        List<Map<String, Object>> riskFlags = internal
                ? buildRiskFlags(report, project, current, previous, start, end)
                : null;
        Map<String, Object> contentExecution = buildContentExecutionSummary(report.getProjectId(), start, end);

        PostsaleReportSnapshot snapshot = new PostsaleReportSnapshot();
        snapshot.setReportId(report.getId());
        snapshot.setReportSubtype(report.getReportType());
        snapshot.setSummaryData(JSONUtil.toJsonStr(summary));
        snapshot.setTrendData(JSONUtil.toJsonStr(trend));
        snapshot.setDetailData(JSONUtil.toJsonStr(detail));
        snapshot.setPlatformBreakdown(JSONUtil.toJsonStr(platformBreakdown));
        snapshot.setComparisonData(JSONUtil.toJsonStr(comparison));
        snapshot.setTargetEvaluation(targetEval == null ? null : JSONUtil.toJsonStr(targetEval));
        snapshot.setStageAdvice(null);
        snapshot.setStageAdviceInput(null);
        snapshot.setInternalNotes(internal ? null : null);
        snapshot.setRiskFlags(riskFlags == null ? null : JSONUtil.toJsonStr(riskFlags));
        snapshot.setContentExecutionSummary(JSONUtil.toJsonStr(contentExecution));
        snapshot.setMethodologyNote(String.valueOf(methodology.get("default_note")));
        return snapshot;
    }
    private Map<String, Object> buildMethodologyNotes() {
        return Map.of(
                "default_note",
                "This report is generated from project execution data, platform monitoring data and content delivery records. "
                        + "Metrics may have a short delay due to data synchronization and should be interpreted together with trend analysis."
        );
    }

    private SummaryPack aggregateSummary(Long projectId, LocalDate start, LocalDate end) {
        SummaryPack pack = new SummaryPack();
        if (start == null || end == null || start.isAfter(end)) {
            return pack;
        }
        List<PollDailyStat> stats = pollDailyStatMapper.selectList(
                new LambdaQueryWrapper<PollDailyStat>()
                        .eq(PollDailyStat::getProjectId, projectId)
                        .between(PollDailyStat::getBatchDate, start, end)
        );
        pack.completedCount = stats.stream().mapToInt(s -> nvl(s.getCompletedCount())).sum();
        pack.requestCount = stats.stream().mapToInt(s -> nvl(s.getRequestCount())).sum();
        pack.hitCount = stats.stream().mapToInt(s -> nvl(s.getHitCount())).sum();
        pack.siteMentionCount = stats.stream().mapToInt(s -> nvl(s.getSiteMentionCount())).sum();
        pack.contactMentionCount = stats.stream().mapToInt(s -> nvl(s.getContactMentionCount())).sum();
        pack.platformTotalCount = (int) stats.stream().map(PollDailyStat::getPlatformId).filter(Objects::nonNull).distinct().count();
        pack.platformCoverageCount = (int) stats.stream()
                .filter(s -> nvl(s.getHitCount()) > 0)
                .map(PollDailyStat::getPlatformId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return pack;
    }

    private Map<String, Object> buildTrendData(Long projectId, LocalDate start, LocalDate end) {
        List<PollDailyStat> stats = pollDailyStatMapper.selectList(
                new LambdaQueryWrapper<PollDailyStat>()
                        .eq(PollDailyStat::getProjectId, projectId)
                        .between(PollDailyStat::getBatchDate, start, end)
                        .orderByAsc(PollDailyStat::getBatchDate)
        );
        Map<LocalDate, List<PollDailyStat>> byDate = stats.stream().collect(Collectors.groupingBy(PollDailyStat::getBatchDate, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<LocalDate, List<PollDailyStat>> e : byDate.entrySet()) {
            int completed = e.getValue().stream().mapToInt(s -> nvl(s.getCompletedCount())).sum();
            int hit = e.getValue().stream().mapToInt(s -> nvl(s.getHitCount())).sum();
            int site = e.getValue().stream().mapToInt(s -> nvl(s.getSiteMentionCount())).sum();
            int contact = e.getValue().stream().mapToInt(s -> nvl(s.getContactMentionCount())).sum();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", e.getKey());
            p.put("hit_rate", percent(hit, completed));
            p.put("site_rate", percent(site, completed));
            p.put("contact_rate", percent(contact, completed));
            points.add(p);
        }
        return Map.of("daily_points", points);
    }

    private Map<String, Object> buildDetailData(Long projectId, String reportType, LocalDate start, LocalDate end, boolean internal) {
        if ("quarterly".equals(reportType)) {
            Optional<String> freezeObjectKey = findFrozenReportObjectKey(projectId, reportType, start, end);
            if (freezeObjectKey.isPresent()) {
                return buildDetailDataFromFreeze(freezeObjectKey.get(), internal);
            }
            if (isBeyondPollDetailHotWindow(end)) {
                return Map.of(
                        "items", List.of(),
                        "status", "freeze_missing",
                        "message", "冻结缺失",
                        "report_type", reportType,
                        "period_key", quarterKey(start, end)
                );
            }
        }

        List<PollResult> rows = pollResultMapper.selectList(
                new LambdaQueryWrapper<PollResult>()
                        .eq(PollResult::getProjectId, projectId)
                        .between(PollResult::getBatchDate, start, end)
        );
        Map<String, List<PollResult>> grouped = rows.stream()
                .collect(Collectors.groupingBy(r -> nvlStr(r.getKeywordTextSnapshot()), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<PollResult>> e : grouped.entrySet()) {
            List<PollResult> list = e.getValue();
            long hitPlatforms = list.stream().filter(r -> Boolean.TRUE.equals(r.getIsHit())).map(PollResult::getPlatformId).distinct().count();
            long allPlatforms = list.stream().map(PollResult::getPlatformId).distinct().count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", StringUtils.hasText(e.getKey()) ? e.getKey() : "-");
            row.put("question_content", StringUtils.hasText(e.getKey()) ? e.getKey() : "-");
            row.put("question_type", null);
            row.put("platforms_hit", hitPlatforms);
            row.put("platforms_total", allPlatforms);
            row.put("site_mentioned", list.stream().anyMatch(r -> Boolean.TRUE.equals(r.getSiteMentioned())));
            row.put("contact_mentioned", list.stream().anyMatch(r -> Boolean.TRUE.equals(r.getContactMentioned())));
            if (internal) {
                List<Map<String, Object>> details = list.stream().map(r -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("platform_id", r.getPlatformId());
                    detail.put("platform_code", nvlStr(r.getPlatformCode()));
                    detail.put("status", nvlStr(r.getStatus()));
                    detail.put("is_hit", Boolean.TRUE.equals(r.getIsHit()));
                    detail.put("site_mentioned", Boolean.TRUE.equals(r.getSiteMentioned()));
                    detail.put("contact_mentioned", Boolean.TRUE.equals(r.getContactMentioned()));
                    return detail;
                }).collect(Collectors.toList());
                row.put("platform_details", details);
            }
            items.add(row);
        }
        return Map.of("items", items);
    }

    private Optional<String> findFrozenReportObjectKey(Long projectId, String reportType, LocalDate start, LocalDate end) {
        String periodKey = quarterKey(start, end);
        if (!StringUtils.hasText(periodKey)) {
            return Optional.empty();
        }
        List<String> keys = jdbcTemplate.query("""
                SELECT snapshot_object_key
                  FROM report_period_freeze
                 WHERE project_id = ?
                   AND report_type = ?
                   AND period_key = ?
                   AND status = 'FROZEN'
                   AND snapshot_object_key IS NOT NULL
                 ORDER BY version_no DESC
                 LIMIT 1
                """, (rs, rowNum) -> rs.getString("snapshot_object_key"), projectId, reportType, periodKey);
        return keys.stream().filter(StringUtils::hasText).findFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDetailDataFromFreeze(String objectKey, boolean internal) {
        byte[] bytes = objectStorageService.readBytes(objectKey);
        Map<String, Object> root = JSONUtil.parseObj(new String(bytes, StandardCharsets.UTF_8));
        Object rawRows = root.get("rows");
        List<Map<String, Object>> rows = rawRows instanceof List<?> list
                ? list.stream()
                .filter(Map.class::isInstance)
                .map(row -> (Map<String, Object>) row)
                .toList()
                : List.of();
        Map<String, List<Map<String, Object>>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> nvlStr(stringValue(row.get("keyword_identity_value"))),
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<Map<String, Object>> list = e.getValue();
            long hitPlatforms = list.stream().filter(row -> boolValue(row.get("is_hit")))
                    .map(row -> row.get("platform_id")).filter(Objects::nonNull).distinct().count();
            long allPlatforms = list.stream().map(row -> row.get("platform_id")).filter(Objects::nonNull).distinct().count();
            String keyword = list.stream()
                    .map(row -> stringValue(row.get("keyword_text_snapshot")))
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("-");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", keyword);
            row.put("question_content", keyword);
            row.put("question_type", null);
            row.put("platforms_hit", hitPlatforms);
            row.put("platforms_total", allPlatforms);
            row.put("site_mentioned", list.stream().anyMatch(item -> boolValue(item.get("site_mentioned"))));
            row.put("contact_mentioned", list.stream().anyMatch(item -> boolValue(item.get("contact_mentioned"))));
            if (internal) {
                List<Map<String, Object>> details = list.stream().map(item -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("platform_id", item.get("platform_id"));
                    detail.put("platform_code", nvlStr(stringValue(item.get("platform_code"))));
                    detail.put("status", nvlStr(stringValue(item.get("status"))));
                    detail.put("is_hit", boolValue(item.get("is_hit")));
                    detail.put("site_mentioned", boolValue(item.get("site_mentioned")));
                    detail.put("contact_mentioned", boolValue(item.get("contact_mentioned")));
                    return detail;
                }).collect(Collectors.toList());
                row.put("platform_details", details);
            }
            items.add(row);
        }
        return Map.of(
                "items", items,
                "source", "report_period_freeze",
                "snapshot_object_key", objectKey
        );
    }

    private Map<String, Object> buildPlatformBreakdown(Long projectId, LocalDate start, LocalDate end) {
        List<PollDailyStat> stats = pollDailyStatMapper.selectList(
                new LambdaQueryWrapper<PollDailyStat>()
                        .eq(PollDailyStat::getProjectId, projectId)
                        .between(PollDailyStat::getBatchDate, start, end)
        );
        Map<Long, List<PollDailyStat>> grouped = stats.stream().collect(Collectors.groupingBy(PollDailyStat::getPlatformId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> platforms = new ArrayList<>();
        for (Map.Entry<Long, List<PollDailyStat>> e : grouped.entrySet()) {
            List<PollDailyStat> list = e.getValue();
            int completed = list.stream().mapToInt(s -> nvl(s.getCompletedCount())).sum();
            int hit = list.stream().mapToInt(s -> nvl(s.getHitCount())).sum();
            int site = list.stream().mapToInt(s -> nvl(s.getSiteMentionCount())).sum();
            int contact = list.stream().mapToInt(s -> nvl(s.getContactMentionCount())).sum();
            PollDailyStat first = list.get(0);
            platforms.add(Map.of(
                    "platform_id", e.getKey(),
                    "platform_code", nvlStr(first.getPlatformCode()),
                    "platform_name", nvlStr(first.getPlatformName()),
                    "hit_rate", percent(hit, completed),
                    "completed_count", completed,
                    "hit_count", hit,
                    "site_mention_count", site,
                    "contact_mention_count", contact
            ));
        }
        return Map.of("platforms", platforms);
    }

    private Map<String, Object> buildComparisonData(LocalDate start, LocalDate end, SummaryPack current, SummaryPack previous) {
        BigDecimal currentRate = percent(current.hitCount, current.completedCount);
        BigDecimal previousRate = percent(previous.hitCount, previous.completedCount);
        BigDecimal changePct = previousRate.signum() == 0
                ? BigDecimal.ZERO
                : currentRate.subtract(previousRate).multiply(BigDecimal.valueOf(100))
                .divide(previousRate, 2, RoundingMode.HALF_UP);
        return Map.of(
                "previous_period", Map.of("start", previous.start, "end", previous.end),
                "current_period", Map.of("start", start, "end", end),
                "hit_rate_previous", previousRate,
                "hit_rate_current", currentRate,
                "hit_rate_change_pct", changePct
        );
    }

    private Map<String, Object> buildTargetEvaluation(Report report, Project project, SummaryPack current, boolean internal) {
        if (!internal || !"quarterly".equals(report.getReportType())) {
            return null;
        }
        BigDecimal target = project.getPlanTargetMetricValue() == null ? BigDecimal.ZERO : project.getPlanTargetMetricValue();
        BigDecimal actual = percent(current.hitCount, current.completedCount);
        boolean met = target.signum() > 0 && actual.compareTo(target) >= 0;
        return Map.of(
                "targets", List.of(Map.of(
                        "metric", nvlStr(project.getPlanTargetMetricType()),
                        "target_value", target,
                        "actual_value", actual,
                        "met", met
                )),
                "overall_met", met,
                "unmet_count", met ? 0 : 1
        );
    }

    private List<Map<String, Object>> buildRiskFlags(Report report, Project project, SummaryPack current, SummaryPack previous, LocalDate start, LocalDate end) {
        List<Map<String, Object>> risks = new ArrayList<>();
        int expectedDays = expectedTriggerDays(project, start, end).size();
        int availableDays = (int) pollDailyStatMapper.selectList(
                new LambdaQueryWrapper<PollDailyStat>()
                        .eq(PollDailyStat::getProjectId, report.getProjectId())
                        .between(PollDailyStat::getBatchDate, start, end)
        ).stream().map(PollDailyStat::getBatchDate).distinct().count();
        if (availableDays < expectedDays) {
            risks.add(Map.of("type", "data_incomplete", "message", "Data coverage is incomplete in the selected period", "severity", "warn"));
        }
        BigDecimal currRate = percent(current.hitCount, current.completedCount);
        BigDecimal prevRate = percent(previous.hitCount, previous.completedCount);
        if (prevRate.signum() > 0) {
            BigDecimal dropPct = prevRate.subtract(currRate).multiply(BigDecimal.valueOf(100)).divide(prevRate, 2, RoundingMode.HALF_UP);
            if (dropPct.compareTo(BigDecimal.valueOf(20)) > 0) {
                risks.add(Map.of("type", "hit_rate_drop", "message", "Hit rate dropped by more than 20% compared with previous period", "severity", "warn"));
            }
        }
        return risks;
    }

    private Map<String, Object> buildContentExecutionSummary(Long projectId, LocalDate start, LocalDate end) {
        List<ArticleBatch> batches = articleBatchMapper.selectList(
                new LambdaQueryWrapper<ArticleBatch>()
                        .eq(ArticleBatch::getProjectId, projectId)
                        .between(ArticleBatch::getBatchDate, start, end)
        );
        int generated = batches.stream().mapToInt(b -> nvl(b.getTotalCount())).sum();
        int approved = batches.stream().mapToInt(b -> nvl(b.getCompletedCount())).sum();
        List<DistributionTask> tasks = distributionTaskMapper.selectList(
                new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getProjectId, projectId)
                        .between(DistributionTask::getCreatedAt, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
        );
        int distributed = (int) tasks.stream().filter(t -> "completed".equalsIgnoreCase(t.getStatus())).count();
        return Map.of(
                "articles_generated", generated,
                "articles_approved", approved,
                "articles_distributed", distributed
        );
    }

    private List<LocalDate> expectedTriggerDays(Project project, LocalDate start, LocalDate end) {
        if (project.getActivatedAt() == null || start == null || end == null || start.isAfter(end)) {
            return List.of();
        }
        List<LocalDate> days = new ArrayList<>();
        LocalDate activated = project.getActivatedAt().toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long gap = java.time.temporal.ChronoUnit.DAYS.between(activated, d);
            if (gap >= 0 && gap % 2 == 0) {
                days.add(d);
            }
        }
        return days;
    }

    private LocalDate previousStart(String reportType, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        long len = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        return start.minusDays(len);
    }

    private LocalDate previousEnd(String reportType, LocalDate start) {
        if (start == null) {
            return null;
        }
        return start.minusDays(1);
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private String nvlStr(String v) {
        return v == null ? "" : v;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean boolValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isBeyondPollDetailHotWindow(LocalDate end) {
        return end != null && end.isBefore(LocalDate.now().minusDays(POLL_DETAIL_HOT_DAYS - 1L));
    }

    private String quarterKey(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return "";
        }
        int quarter = ((start.getMonthValue() - 1) / 3) + 1;
        LocalDate expectedStart = LocalDate.of(start.getYear(), (quarter - 1) * 3 + 1, 1);
        LocalDate expectedEnd = YearMonth.from(expectedStart.plusMonths(2)).atEndOfMonth();
        if (!expectedStart.equals(start) || !expectedEnd.equals(end)) {
            return "";
        }
        return start.getYear() + "Q" + quarter;
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static class SummaryPack {
        private LocalDate start;
        private LocalDate end;
        private int requestCount;
        private int completedCount;
        private int hitCount;
        private int siteMentionCount;
        private int contactMentionCount;
        private int platformCoverageCount;
        private int platformTotalCount;
        private BigDecimal keywordCoverageRate = BigDecimal.ZERO;
    }
    private Report requireReport(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(404, "Report not found");
        }
        return report;
    }

    private Report findReportByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getShareToken, token.trim())
                        .orderByDesc(Report::getId)
                        .last("LIMIT 1")
        );
    }

    private Report findLatestPublishedClient(Long projectId, String reportType) {
        return reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, projectId)
                        .eq(Report::getReportType, reportType)
                        .eq(Report::getVisibility, "client")
                        .eq(Report::getIsLatest, true)
                        .eq(Report::getStatus, "published")
                        .orderByDesc(Report::getPublishedAt, Report::getId)
                        .last("LIMIT 1")
        );
    }

    private void ensureProjectReadable(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(user, project, "project");
    }

    private Map<String, Object> buildReportSubject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return Map.of(
                    "customerName", "-",
                    "brandName", "-",
                    "projectName", "-"
            );
        }
        String projectName = StringUtils.hasText(project.getProjectName()) ? project.getProjectName() : "-";
        String brandName = StringUtils.hasText(project.getBrandName()) ? project.getBrandName() : "-";
        String customerName = StringUtils.hasText(project.getCompanyName()) ? project.getCompanyName() : "-";

        if ("-".equals(brandName) && project.getBrandId() != null) {
            Brand brand = brandMapper.selectById(project.getBrandId());
            if (brand != null && StringUtils.hasText(brand.getBrandName())) {
                brandName = brand.getBrandName();
            }
        }
        if ("-".equals(customerName) && project.getCompanyId() != null) {
            Company company = companyMapper.selectById(project.getCompanyId());
            if (company != null && StringUtils.hasText(company.getCompanyName())) {
                customerName = company.getCompanyName();
            }
        }
        return Map.of(
                "customerName", customerName,
                "brandName", brandName,
                "projectName", projectName
        );
    }

    private boolean isExpired(Report report) {
        return report.getShareExpiresAt() != null && LocalDateTime.now().isAfter(report.getShareExpiresAt());
    }

    private boolean requiresPassword(Report report) {
        return StringUtils.hasText(report.getSharePasswordHash());
    }

    private boolean isPostsaleType(String reportType) {
        return isDisabledPostsaleType(reportType);
    }

    private void fillProjectNames(List<Report> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        List<Long> projectIds = reports.stream()
                .map(Report::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (projectIds.isEmpty()) {
            return;
        }
        Map<Long, String> projectNameMap = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .isNull(Project::getDeletedAt)
                        .in(Project::getId, projectIds)
                        .select(Project::getId, Project::getProjectName)
        ).stream().collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));
        for (Report report : reports) {
            report.setProjectName(projectNameMap.getOrDefault(report.getProjectId(), "-"));
        }
    }

    private boolean isDisabledPostsaleType(String reportType) {
        return DISABLED_POSTSALE_TYPES.contains(reportType);
    }

    private void ensurePostsaleEnabled(String reportType) {
        if (isDisabledPostsaleType(reportType)) {
            throw new BizException(410, REPORT_DISABLED_MESSAGE);
        }
    }

    private void ensureReportTypeActive(String reportType) {
        if (isDisabledPostsaleType(reportType) || LEGACY_PRESALE_TYPES.contains(reportType)) {
            throw new BizException(404, "Report not found");
        }
    }

    private void ensurePostsaleRegenerateAllowed(SysUser user) {
        String role = user == null ? null : user.getRole();
        if (!Set.of("super_admin", "manager", "delivery_manager").contains(role)) {
            throw new BizException(403, "No permission to regenerate report");
        }
    }

    private void checkPostsalePublishPreconditions(Report report) {
        PostsaleReportSnapshot snapshot = postsaleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<PostsaleReportSnapshot>().eq(PostsaleReportSnapshot::getReportId, report.getId())
        );
        if (snapshot == null) {
            throw new BizException(400, "Postsale snapshot not found");
        }
        if (report.getPdfGeneratedAt() == null || (snapshot.getUpdatedAt() != null && report.getPdfGeneratedAt().isBefore(snapshot.getUpdatedAt()))) {
            throw new BizException(400, "Please regenerate the latest PDF before publishing");
        }
        if ("client".equalsIgnoreCase(report.getVisibility())) {
            if (report.getPairReportId() == null) {
                throw new BizException(400, "Internal report pair is missing");
            }
            Report internal = reportMapper.selectById(report.getPairReportId());
            if (internal == null || !"published".equals(internal.getStatus())) {
                throw new BizException(400, "Please publish internal report before publishing client report");
            }
        }
    }

    private void logAccess(Report report, String token, HttpServletRequest request, boolean verified) {
        ReportAccessLog log = new ReportAccessLog();
        log.setReportId(report.getId());
        log.setShareToken(token);
        log.setPasswordVerified(verified);
        log.setIpAddress(maskIp(resolveClientIp(request)));
        log.setUserAgent(limit(request.getHeader("User-Agent"), 500));
        log.setReferer(limit(request.getHeader("Referer"), 500));
        reportAccessLogMapper.insert(log);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String value = ip.trim();
        if (value.contains(".")) {
            String[] seg = value.split("\\.");
            if (seg.length == 4) {
                return seg[0] + "." + seg[1] + "." + seg[2] + ".*";
            }
        }
        if (value.contains(":")) {
            int idx = value.lastIndexOf(":");
            return idx > 0 ? value.substring(0, idx) + ":*" : "*";
        }
        return "*";
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean isLegacySharePdfPath(String token, String pdfUrl) {
        String legacy = "/api/share/" + token + "/pdf";
        if (legacy.equals(pdfUrl)) {
            return true;
        }
        return pdfUrl.endsWith(legacy);
    }

    private String buildPdfObjectKey(Report report) {
        return String.format("reports/%d/%s/latest.pdf", report.getProjectId(), report.getReportType());
    }
}



