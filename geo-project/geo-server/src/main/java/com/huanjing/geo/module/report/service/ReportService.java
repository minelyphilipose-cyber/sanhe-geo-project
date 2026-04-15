package com.huanjing.geo.module.report.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.QuestionPoolItem;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.QuestionPoolItemMapper;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.report.dto.PresaleSnapshotUpdateRequest;
import com.huanjing.geo.module.report.dto.ReportPublishRequest;
import com.huanjing.geo.module.report.entity.*;
import com.huanjing.geo.module.report.mapper.*;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final ReportMapper reportMapper;
    private final PresaleReportSnapshotMapper presaleSnapshotMapper;
    private final PresaleDiagnosisBatchMapper diagnosisBatchMapper;
    private final PresaleDiagnosisResultMapper diagnosisResultMapper;
    private final PresaleQuestionItemMapper questionItemMapper;
    private final ReportAccessLogMapper reportAccessLogMapper;
    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final AiPlatformConfigMapper platformConfigMapper;
    private final SysDictItemMapper dictItemMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReportPdfService reportPdfService;
    private final MinioStorageService minioStorageService;
    private final PostsaleReportSnapshotMapper postsaleSnapshotMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final PollResultMapper pollResultMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final QuestionPoolItemMapper questionPoolItemMapper;

    public Page<Report> page(long current, long size, Long projectId, String reportType, String status) {
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreatedAt);
        if (projectId != null) {
            wrapper.eq(Report::getProjectId, projectId);
        }
        if (StringUtils.hasText(reportType)) {
            wrapper.eq(Report::getReportType, reportType.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Report::getStatus, status.trim());
        }
        return reportMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Map<String, Object> detail(Long reportId) {
        currentUserService.ensurePermission("project.read");
        Report report = requireReport(reportId);
        ensureProjectReadable(report.getProjectId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report", report);
        data.put("subject", buildReportSubject(report.getProjectId()));
        if ("presale".equals(report.getReportType())) {
            PresaleReportSnapshot snapshot = presaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PresaleReportSnapshot>().eq(PresaleReportSnapshot::getReportId, reportId)
            );
            data.put("presaleSnapshot", snapshot);
        } else if (isPostsaleType(report.getReportType())) {
            PostsaleReportSnapshot snapshot = postsaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PostsaleReportSnapshot>().eq(PostsaleReportSnapshot::getReportId, reportId)
            );
            data.put("postsaleSnapshot", snapshot);
        }
        return data;
    }

    @Transactional
    public Report generatePresaleDraftByLatestBatch(Long projectId, Long creatorId) {
        PresaleDiagnosisBatch batch = diagnosisBatchMapper.selectOne(
                new LambdaQueryWrapper<PresaleDiagnosisBatch>()
                        .eq(PresaleDiagnosisBatch::getProjectId, projectId)
                        .eq(PresaleDiagnosisBatch::getStatus, "completed")
                        .orderByDesc(PresaleDiagnosisBatch::getFinishedAt, PresaleDiagnosisBatch::getId)
                        .last("LIMIT 1")
        );
        if (batch == null) {
            throw new BizException(400, "No completed presale diagnosis batch");
        }
        return generatePresaleDraftFromBatch(batch.getId(), creatorId);
    }

    @Transactional
    public Report generatePresaleDraftFromBatch(Long batchId, Long creatorId) {
        PresaleDiagnosisBatch batch = diagnosisBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(404, "Diagnosis batch not found");
        }
        Project project = projectMapper.selectById(batch.getProjectId());
        if (project == null) {
            throw new BizException(404, "Project not found");
        }

        Integer maxVersion = Optional.ofNullable(reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getProjectId, project.getId())
                        .eq(Report::getReportType, "presale")
                        .orderByDesc(Report::getVersionNo)
                        .last("LIMIT 1")
        )).map(Report::getVersionNo).orElse(0);

        Report report = new Report();
        report.setProjectId(project.getId());
        report.setReportType("presale");
        report.setVersionNo(maxVersion + 1);
        report.setStatus("draft");
        report.setVisibility("client");
        report.setIsLatest(true);
        report.setCreatedBy(creatorId);
        reportMapper.insert(report);

        Map<String, Object> metrics = calculatePresaleSnapshot(batch, project);
        PresaleReportSnapshot snapshot = new PresaleReportSnapshot();
        snapshot.setReportId(report.getId());
        snapshot.setDiagnosisBatchId(batch.getId());
        snapshot.setSnapshotData(JSONUtil.toJsonStr(metrics));
        snapshot.setDiagnosisSummary(defaultDiagnosisSummary(metrics));
        snapshot.setActionRecommendations(defaultActionRecommendations(metrics));
        snapshot.setBrandCompletenessChecks(JSONUtil.toJsonStr(metrics.get("brandCompletenessChecks")));
        snapshot.setQuestionMatrix(JSONUtil.toJsonStr(metrics.get("questionMatrix")));
        presaleSnapshotMapper.insert(snapshot);

        return report;
    }

    @Transactional
    public Map<String, Report> generatePostsaleDraftPair(Long projectId,
                                                         String reportType,
                                                         LocalDate periodStart,
                                                         LocalDate periodEnd,
                                                         Long creatorId,
                                                         boolean forceNewVersion) {
        if (!List.of("biweekly", "monthly", "quarterly").contains(reportType)) {
            throw new BizException(400, "Unsupported postsale report type");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
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
    public Report updatePresaleSnapshot(Long reportId, PresaleSnapshotUpdateRequest req) {
        currentUserService.ensurePermission("report.review");
        Report report = requireReport(reportId);
        if (!"presale".equals(report.getReportType())) {
            throw new BizException(400, "Only presale report supports this operation");
        }
        if (!"draft".equals(report.getStatus())) {
            throw new BizException(400, "Only draft report can be edited");
        }
        PresaleReportSnapshot snapshot = presaleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<PresaleReportSnapshot>().eq(PresaleReportSnapshot::getReportId, reportId)
        );
        if (snapshot == null) {
            throw new BizException(404, "Snapshot not found");
        }
        if (req.getDiagnosisSummary() != null) {
            snapshot.setDiagnosisSummary(req.getDiagnosisSummary());
        }
        if (req.getActionRecommendations() != null) {
            snapshot.setActionRecommendations(req.getActionRecommendations());
        }
        presaleSnapshotMapper.updateById(snapshot);
        return report;
    }

    @Transactional
    public Report regeneratePdf(Long reportId) {
        currentUserService.ensurePermission("report.review");
        Report report = requireReport(reportId);
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "loaded");
        data.put("report", report);
        data.put("subject", buildReportSubject(report.getProjectId()));
        if ("presale".equals(report.getReportType())) {
            PresaleReportSnapshot snapshot = presaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PresaleReportSnapshot>().eq(PresaleReportSnapshot::getReportId, report.getId())
            );
            data.put("snapshot", snapshot);
        } else if (isPostsaleType(report.getReportType())) {
            PostsaleReportSnapshot snapshot = postsaleSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<PostsaleReportSnapshot>().eq(PostsaleReportSnapshot::getReportId, report.getId())
            );
            data.put("snapshot", snapshot);
        }
        return data;
    }

    private Map<String, Object> calculatePresaleSnapshot(PresaleDiagnosisBatch batch, Project project) {
        List<PresaleDiagnosisResult> results = diagnosisResultMapper.selectList(
                new LambdaQueryWrapper<PresaleDiagnosisResult>()
                        .eq(PresaleDiagnosisResult::getBatchId, batch.getId())
        );
        if (results.isEmpty()) {
            throw new BizException(400, "Diagnosis result is empty");
        }
        List<PresaleQuestionItem> items = questionItemMapper.selectList(
                new LambdaQueryWrapper<PresaleQuestionItem>()
                        .eq(PresaleQuestionItem::getSetId, batch.getQuestionSetId())
                        .orderByAsc(PresaleQuestionItem::getSortOrder, PresaleQuestionItem::getId)
        );
        Map<Long, PresaleQuestionItem> itemMap = items.stream().collect(Collectors.toMap(PresaleQuestionItem::getId, i -> i, (a, b) -> a, LinkedHashMap::new));
        Map<String, Integer> weightByPlatformCode = resolvePlatformWeights(results.stream().map(PresaleDiagnosisResult::getPlatformCode).collect(Collectors.toSet()));

        long completed = results.stream().filter(r -> "completed".equals(r.getStatus())).count();
        long brandHits = results.stream().filter(r -> "completed".equals(r.getStatus()) && Boolean.TRUE.equals(r.getBrandHit())).count();
        long siteHits = results.stream().filter(r -> "completed".equals(r.getStatus()) && Boolean.TRUE.equals(r.getSiteMentioned())).count();
        long contactHits = results.stream().filter(r -> "completed".equals(r.getStatus()) && Boolean.TRUE.equals(r.getContactMentioned())).count();

        BigDecimal brandMentionRate = ratio(brandHits, completed);
        BigDecimal siteMentionRate = ratio(siteHits, completed);
        BigDecimal contactMentionRate = ratio(contactHits, completed);

        Map<String, Map<String, Object>> platformStats = new LinkedHashMap<>();
        BigDecimal weightedHitNumerator = BigDecimal.ZERO;
        BigDecimal weightedHitDenominator = BigDecimal.ZERO;
        for (String platformCode : results.stream().map(PresaleDiagnosisResult::getPlatformCode).collect(Collectors.toCollection(LinkedHashSet::new))) {
            List<PresaleDiagnosisResult> rows = results.stream().filter(r -> platformCode.equals(r.getPlatformCode())).toList();
            long c = rows.stream().filter(r -> "completed".equals(r.getStatus())).count();
            long h = rows.stream().filter(r -> "completed".equals(r.getStatus()) && Boolean.TRUE.equals(r.getBrandHit())).count();
            BigDecimal hitRate = ratio(h, c);
            int w = weightByPlatformCode.getOrDefault(platformCode, 1);
            weightedHitNumerator = weightedHitNumerator.add(BigDecimal.valueOf(h * w));
            weightedHitDenominator = weightedHitDenominator.add(BigDecimal.valueOf(c * w));

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("platformCode", platformCode);
            stat.put("completed", c);
            stat.put("brandHits", h);
            stat.put("brandMentionRate", toPercent(hitRate));
            stat.put("weight", w);
            platformStats.put(platformCode, stat);
        }

        BigDecimal weightedMentionRate = weightedHitDenominator.signum() <= 0
                ? BigDecimal.ZERO
                : weightedHitNumerator.divide(weightedHitDenominator, 6, RoundingMode.HALF_UP);

        long platformCovered = platformStats.values().stream()
                .filter(m -> ((Number) m.get("brandHits")).longValue() > 0)
                .count();
        BigDecimal platformCoverage = ratio(platformCovered, platformStats.size());

        BigDecimal visibilityScore = brandMentionRate.multiply(BigDecimal.valueOf(40))
                .add(siteMentionRate.multiply(BigDecimal.valueOf(25)))
                .add(contactMentionRate.multiply(BigDecimal.valueOf(15)))
                .add(platformCoverage.multiply(BigDecimal.valueOf(20)))
                .setScale(2, RoundingMode.HALF_UP);

        Map<Long, List<PresaleDiagnosisResult>> byQuestion = results.stream().collect(Collectors.groupingBy(PresaleDiagnosisResult::getQuestionItemId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> weakestQuestions = new ArrayList<>();
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (Map.Entry<Long, List<PresaleDiagnosisResult>> e : byQuestion.entrySet()) {
            PresaleQuestionItem item = itemMap.get(e.getKey());
            if (item == null) {
                continue;
            }
            List<PresaleDiagnosisResult> rows = e.getValue();
            long c = rows.stream().filter(r -> "completed".equals(r.getStatus())).count();
            long h = rows.stream().filter(r -> "completed".equals(r.getStatus()) && Boolean.TRUE.equals(r.getBrandHit())).count();
            BigDecimal hitRate = ratio(h, c);
            List<String> missPlatforms = rows.stream()
                    .filter(r -> "completed".equals(r.getStatus()) && !Boolean.TRUE.equals(r.getBrandHit()))
                    .map(PresaleDiagnosisResult::getPlatformCode)
                    .distinct()
                    .toList();

            Map<String, Object> q = new LinkedHashMap<>();
            q.put("questionItemId", item.getId());
            q.put("content", item.getContent());
            q.put("hitRate", toPercent(hitRate));
            q.put("missPlatforms", missPlatforms);
            weakestQuestions.add(q);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionItemId", item.getId());
            row.put("content", item.getContent());
            Map<String, Object> hits = new LinkedHashMap<>();
            for (PresaleDiagnosisResult r : rows) {
                hits.put(r.getPlatformCode(), Boolean.TRUE.equals(r.getBrandHit()));
            }
            row.put("hits", hits);
            matrix.add(row);
        }
        weakestQuestions.sort(Comparator.comparing(m -> BigDecimal.valueOf(Double.parseDouble(String.valueOf(m.get("hitRate"))))));
        if (weakestQuestions.size() > 8) {
            weakestQuestions = weakestQuestions.subList(0, 8);
        }

        Map<String, Object> completeness = buildBrandCompletenessChecks(project);

        double coef = industryCoefficient(project);
        long missed = Math.max(0, completed - brandHits);
        BigDecimal missedOpportunity = BigDecimal.valueOf(missed).multiply(BigDecimal.valueOf(coef)).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("diagnosisBatchId", batch.getId());
        snapshot.put("brandMentionRate", toPercent(brandMentionRate));
        snapshot.put("siteMentionRate", toPercent(siteMentionRate));
        snapshot.put("contactMentionRate", toPercent(contactMentionRate));
        snapshot.put("weightedBrandMentionRate", toPercent(weightedMentionRate));
        snapshot.put("platformCoverage", toPercent(platformCoverage));
        snapshot.put("visibilityScore", visibilityScore);
        snapshot.put("missedInquiryOpportunity", missedOpportunity);
        snapshot.put("platformStats", platformStats.values());
        snapshot.put("weakestQuestions", weakestQuestions);
        snapshot.put("brandCompletenessChecks", completeness);
        snapshot.put("questionMatrix", matrix);
        snapshot.put("completedCount", completed);
        snapshot.put("failedCount", results.size() - completed);
        return snapshot;
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
        summary.put("core_question_coverage_rate", current.coreCoverageRate);
        summary.put("vs_previous", Map.of(
                "hit_rate_change", percent(current.hitCount, current.completedCount).subtract(percent(previous.hitCount, previous.completedCount)),
                "site_mention_change", current.siteMentionCount - previous.siteMentionCount,
                "contact_mention_change", current.contactMentionCount - previous.contactMentionCount
        ));

        Map<String, Object> trend = buildTrendData(report.getProjectId(), start, end);
        Map<String, Object> detail = buildDetailData(report.getProjectId(), start, end, internal);
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

        List<PollResult> results = pollResultMapper.selectList(
                new LambdaQueryWrapper<PollResult>()
                        .eq(PollResult::getProjectId, projectId)
                        .between(PollResult::getBatchDate, start, end)
                        .eq(PollResult::getStatus, "completed")
        );
        Set<Long> questionIds = results.stream().map(PollResult::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!questionIds.isEmpty()) {
            List<QuestionPoolItem> items = questionPoolItemMapper.selectList(
                    new LambdaQueryWrapper<QuestionPoolItem>().in(QuestionPoolItem::getId, questionIds)
            );
            Set<Long> coreIds = items.stream()
                    .filter(q -> "A".equalsIgnoreCase(q.getPriority()))
                    .map(QuestionPoolItem::getId)
                    .collect(Collectors.toSet());
            if (!coreIds.isEmpty()) {
                long coreHit = results.stream()
                        .filter(r -> coreIds.contains(r.getQuestionId()) && Boolean.TRUE.equals(r.getIsHit()))
                        .map(PollResult::getQuestionId)
                        .distinct()
                        .count();
                pack.coreCoverageRate = percent(coreHit, coreIds.size());
            }
        }
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

    private Map<String, Object> buildDetailData(Long projectId, LocalDate start, LocalDate end, boolean internal) {
        List<PollResult> rows = pollResultMapper.selectList(
                new LambdaQueryWrapper<PollResult>()
                        .eq(PollResult::getProjectId, projectId)
                        .between(PollResult::getBatchDate, start, end)
        );
        Map<Long, QuestionPoolItem> questionMap = questionPoolItemMapper.selectList(
                new LambdaQueryWrapper<QuestionPoolItem>().in(!rows.isEmpty(), QuestionPoolItem::getId, rows.stream().map(PollResult::getQuestionId).collect(Collectors.toSet()))
        ).stream().collect(Collectors.toMap(QuestionPoolItem::getId, q -> q, (a, b) -> a));
        Map<Long, List<PollResult>> grouped = rows.stream().collect(Collectors.groupingBy(PollResult::getQuestionId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<Long, List<PollResult>> e : grouped.entrySet()) {
            QuestionPoolItem q = questionMap.get(e.getKey());
            List<PollResult> list = e.getValue();
            long hitPlatforms = list.stream().filter(r -> Boolean.TRUE.equals(r.getIsHit())).map(PollResult::getPlatformId).distinct().count();
            long allPlatforms = list.stream().map(PollResult::getPlatformId).distinct().count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question_id", e.getKey());
            row.put("question_content", q == null ? "-" : q.getQuestionText());
            row.put("question_type", q == null ? null : q.getQuestionType());
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
        private BigDecimal coreCoverageRate = BigDecimal.ZERO;
    }
    private String defaultDiagnosisSummary(Map<String, Object> metrics) {
        return "Brand mention rate " + metrics.get("brandMentionRate") + "%, visibility score "
                + metrics.get("visibilityScore")
                + ". Overall exposure should be improved through focused platform and topic optimization.";
    }

    private String defaultActionRecommendations(Map<String, Object> metrics) {
        return "1) Fill high-value topic coverage and unify brand expression; "
                + "2) Increase publishing frequency and stability on core platforms; "
                + "3) Create a focused backlog for low-visibility topics and track improvements; "
                + "4) Review key indicators weekly and iterate continuously.";
    }

    private Map<String, Integer> resolvePlatformWeights(Set<String> platformCodes) {
        if (platformCodes.isEmpty()) {
            return Map.of();
        }
        List<AiPlatformConfig> configs = platformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>().in(AiPlatformConfig::getPlatformCode, platformCodes)
        );
        Map<String, Integer> weights = new HashMap<>();
        for (AiPlatformConfig cfg : configs) {
            int w = switch (String.valueOf(cfg.getPriorityLevel())) {
                case "P0" -> 3;
                case "P1" -> 2;
                default -> 1;
            };
            weights.put(cfg.getPlatformCode(), w);
        }
        return weights;
    }
    private Map<String, Object> buildBrandCompletenessChecks(Project project) {
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        Company company = project.getCompanyId() == null ? null : companyMapper.selectById(project.getCompanyId());
        List<String> reminders = new ArrayList<>();
        if (brand == null || !StringUtils.hasText(brand.getWebsite())) {
            reminders.add("Brand website is missing");
        }
        if (brand == null || (!StringUtils.hasText(brand.getPhone()) && !StringUtils.hasText(brand.getWechat()))) {
            reminders.add("Primary contact information is missing");
        }
        if (brand == null || !StringUtils.hasText(brand.getDescription())) {
            reminders.add("Brand description is missing");
        }
        if (company == null || !StringUtils.hasText(company.getCompetitors())) {
            reminders.add("Competitor information is missing");
        }
        if (brand != null && brand.getUpdatedAt() != null && brand.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(180))) {
            reminders.add("Brand profile has not been updated for more than 180 days");
        }
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("complete", reminders.isEmpty());
        checks.put("reminders", reminders);
        return checks;
    }

    private double industryCoefficient(Project project) {
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        String industry = brand == null ? null : brand.getIndustry();
        if (!StringUtils.hasText(industry)) {
            return 0.25;
        }
        SysDictItem item = dictItemMapper.selectOne(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "industry_conversion_rate")
                        .eq(SysDictItem::getDictKey, industry)
                        .eq(SysDictItem::getEnabled, true)
                        .last("LIMIT 1")
        );
        String value = item == null ? "0.25" : item.getDictValue();
        try {
            double v = Double.parseDouble(value);
            if (v < 0.05 || v > 1.0) {
                return 0.25;
            }
            return v;
        } catch (Exception ex) {
            return 0.25;
        }
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
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
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(currentUserService.requireCurrentUser(), project.getPartnerId(), "project");
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
        return "biweekly".equals(reportType) || "monthly".equals(reportType) || "quarterly".equals(reportType);
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



