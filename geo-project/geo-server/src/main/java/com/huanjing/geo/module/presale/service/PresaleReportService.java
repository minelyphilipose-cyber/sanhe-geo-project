package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.module.presale.dto.request.CreateReportRequest;
import com.huanjing.geo.module.presale.dto.request.ReportListQueryRequest;
import com.huanjing.geo.module.presale.dto.response.ReportListItemVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import com.huanjing.geo.module.presale.access.AccessScope;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 售前报告领域服务。
 *
 * <p>职责:</p>
 * <ul>
 *   <li>新建报告 + 触发首版生成(调用 Orchestrator)</li>
 *   <li>列表查询(分页/筛选/排序)</li>
 * </ul>
 *
 * <p>不负责:版本读取(见 {@link PresaleReportVersionService})、权限判定
 * (见 {@link PresaleRoleResolver})、生成流程(见 Orchestrator)。</p>
 */
@Service
public class PresaleReportService {
    private static final String PERM_LIST = "presale.report.list";
    private static final String PERM_CREATE = "presale.report.create";

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final PresaleGenerateOrchestrator orchestrator;
    private final CurrentUserService currentUserService;
    private final PresaleAccessService accessService;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;

    public PresaleReportService(PresaleReportMapper reportMapper,
                                PresaleReportVersionMapper versionMapper,
                                PresaleGenerateOrchestrator orchestrator,
                                CurrentUserService currentUserService,
                                PresaleAccessService accessService,
                                AiPlatformConfigMapper aiPlatformConfigMapper,
                                PresaleAiPromptResultMapper aiPromptResultMapper) {
        this.reportMapper = reportMapper;
        this.versionMapper = versionMapper;
        this.orchestrator = orchestrator;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
    }

    /**
     * 新建报告:insert report + insert 首版 version + 触发异步生成。
     *
     * @return 新建的 reportId(前端跳进度页用)
     */
    @Transactional
    public Long createReport(CreateReportRequest req) {
        currentUserService.ensurePermission(PERM_CREATE);
        Long userId = currentUserService.requireCurrentUser().getId();
        LocalDateTime now = LocalDateTime.now();

        PresaleReport report = new PresaleReport();
        report.setBrandName(req.getBrandName());
        report.setIndustry(req.getIndustry());
        report.setIndustryRole(req.getIndustryRole());
        report.setRegion(req.getRegion());
        report.setUserDemand(req.getUserDemand());
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        report.setCreatedBy(userId);
        reportMapper.insert(report);

        PresaleReportVersion version = new PresaleReportVersion();
        int platformCount = countEnabledPlatforms();
        int genericPromptCount = countPromptTemplates(0);
        int competitorPromptCount = countPromptTemplates(1);
        int batch1Total = platformCount * genericPromptCount * 2;
        int totalUpperBound = batch1Total + (platformCount * competitorPromptCount * 3 * 2);
        version.setReportId(report.getId());
        version.setVersionNo(1);
        version.setGenerationStatus(PresaleGenerateStatus.QUEUED.name());
        version.setTotalLlmCalls(totalUpperBound);
        version.setCompletedLlmCalls(0);
        version.setBatch1TotalCalls(batch1Total);
        version.setBatch1CompletedCalls(0);
        version.setBatch2TotalCalls(null);
        version.setBatch2CompletedCalls(0);
        version.setIsDegraded(false);
        version.setExportSuccessCount(0);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setCreatedBy(userId);
        versionMapper.insert(version);

        // 回填 latest_version_id
        report.setLatestVersionId(version.getId());
        reportMapper.updateById(report);

        // 异步生成在事务提交后触发,避免异步线程早于 version insert 提交导致 "version not found"
        triggerGenerateAfterCommit(version.getId(), userId, accessService.canManageCurrentUser());

        return report.getId();
    }

    private void triggerGenerateAfterCommit(Long versionId, Long userId, boolean canManageCurrentUser) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    orchestrator.triggerGenerate(versionId, userId, canManageCurrentUser);
                }
            });
            return;
        }
        orchestrator.triggerGenerate(versionId, userId, canManageCurrentUser);
    }

    /**
     * 列表查询。返回 MyBatis-Plus Page,Controller 直接包进 R。
     */
    public Page<ReportListItemVO> listReports(ReportListQueryRequest req) {
        currentUserService.ensurePermission(PERM_LIST);
        Page<PresaleReport> page = Page.of(
                req.getPage() == null ? 1 : req.getPage(),
                req.getPageSize() == null ? 20 : req.getPageSize()
        );

        LambdaQueryWrapper<PresaleReport> q = buildQueryWrapper(req);
        applySorting(q, req);

        Page<PresaleReport> entityPage = reportMapper.selectPage(page, q);

        // 批量取 latest version + versionCount(避免 N+1)
        List<Long> reportIds = entityPage.getRecords().stream()
                .map(PresaleReport::getId).collect(Collectors.toList());
        List<Long> latestVersionIds = entityPage.getRecords().stream()
                .map(PresaleReport::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, PresaleReportVersion> versionById = new HashMap<>();
        if (!latestVersionIds.isEmpty()) {
            List<PresaleReportVersion> versions = versionMapper.selectBatchIds(latestVersionIds);
            versions.forEach(v -> versionById.put(v.getId(), v));
        }

        Map<Long, Integer> countByReport = new HashMap<>();
        if (!reportIds.isEmpty()) {
            for (Map<String, Object> row : versionMapper.countByReportIds(reportIds)) {
                Long rid = ((Number) row.get("report_id")).longValue();
                Integer cnt = ((Number) row.get("cnt")).intValue();
                countByReport.put(rid, cnt);
            }
        }

        // 转换为 VO Page
        Page<ReportListItemVO> voPage = new Page<>(entityPage.getCurrent(),
                entityPage.getSize(), entityPage.getTotal());
        List<ReportListItemVO> items = new ArrayList<>();
        for (PresaleReport r : entityPage.getRecords()) {
            PresaleReportVersion v = r.getLatestVersionId() == null ? null
                    : versionById.get(r.getLatestVersionId());
            items.add(ReportListItemVO.builder()
                    .reportId(r.getId())
                    .brandName(r.getBrandName())
                    .industry(r.getIndustry())
                    .industryRole(r.getIndustryRole())
                    .region(r.getRegion())
                    .versionCount(countByReport.getOrDefault(r.getId(), 0))
                    .latestVersion(v == null ? null : toVersionMeta(v))
                    .createdAt(r.getCreatedAt())
                    .build());
        }
        voPage.setRecords(items);
        return voPage;
    }

    private LambdaQueryWrapper<PresaleReport> buildQueryWrapper(ReportListQueryRequest req) {
        LambdaQueryWrapper<PresaleReport> q = new LambdaQueryWrapper<>();
        Long currentUserId = accessService.currentUserId();
        if (accessService.getAccessScope() == AccessScope.OWN_ONLY) {
            q.eq(PresaleReport::getCreatedBy, currentUserId);
        }

        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            q.like(PresaleReport::getBrandName, req.getKeyword().trim());
        }
        if (req.getIndustry() != null && !req.getIndustry().isBlank()) {
            q.eq(PresaleReport::getIndustry, req.getIndustry());
        }
        if (req.getIndustryRole() != null && !req.getIndustryRole().isBlank()) {
            q.eq(PresaleReport::getIndustryRole, req.getIndustryRole());
        }
        if (req.getStartAt() != null) {
            // OffsetDateTime → LocalDateTime 使用系统默认时区(应用配置为 +08:00)
            q.ge(PresaleReport::getCreatedAt, req.getStartAt().toLocalDateTime());
        }
        if (req.getEndAt() != null) {
            q.le(PresaleReport::getCreatedAt, req.getEndAt().toLocalDateTime());
        }
        // generationStatus / frozen 过滤基于 version 字段,v1 先不做(需要 join 或二次筛选),
        // 作为 P1·F·1·b 的优化点;当前列表页这两列只用于展示,筛选提示"暂未实现"
        return q;
    }

    private void applySorting(LambdaQueryWrapper<PresaleReport> q, ReportListQueryRequest req) {
        String sortBy = req.getSortBy() == null ? "createdAt" : req.getSortBy();
        boolean desc = !"asc".equalsIgnoreCase(req.getSortDir());
        switch (sortBy) {
            case "brandName":
                if (desc) q.orderByDesc(PresaleReport::getBrandName);
                else q.orderByAsc(PresaleReport::getBrandName);
                break;
            case "createdAt":
            default:
                if (desc) q.orderByDesc(PresaleReport::getCreatedAt);
                else q.orderByAsc(PresaleReport::getCreatedAt);
                break;
        }
    }

    static ReportVersionMetaVO toVersionMeta(PresaleReportVersion v) {
        if (v == null) return null;
        List<String> degradedPlatforms = v.getDegradedPlatforms() == null
                ? Collections.emptyList()
                : parseStringList(v.getDegradedPlatforms());
        return ReportVersionMetaVO.builder()
                .versionId(v.getId())
                .versionNo(v.getVersionNo())
                .generationStatus(v.getGenerationStatus())
                .generationStage(v.getGenerationStage())
                .totalLlmCalls(v.getTotalLlmCalls())
                .completedLlmCalls(v.getCompletedLlmCalls())
                .batch1TotalCalls(v.getBatch1TotalCalls())
                .batch1CompletedCalls(v.getBatch1CompletedCalls())
                .batch2TotalCalls(v.getBatch2TotalCalls())
                .batch2CompletedCalls(v.getBatch2CompletedCalls())
                .extractedCompetitorCount(v.getExtractedCompetitorCount())
                .isDegraded(v.getIsDegraded())
                .degradedPlatforms(degradedPlatforms)
                .failureReason(v.getFailureReason())
                .frozen(v.getFrozenAt() != null)
                .frozenAt(v.getFrozenAt())
                .contentUpdatedAt(v.getContentUpdatedAt())
                .exportSuccessCount(v.getExportSuccessCount())
                .exportSuccessAt(v.getExportSuccessAt())
                .createdAt(v.getCreatedAt())
                .build();
    }

    /**
     * 简易 JSON 数组字符串解析。生产可换 Jackson,v1 简化处理。
     */
    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        String stripped = json.trim().replaceAll("^\\[|\\]$", "");
        if (stripped.isBlank()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String part : stripped.split(",")) {
            String trimmed = part.trim().replaceAll("^\"|\"$", "");
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private int countEnabledPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(PresalePlatformConfigQueries.presaleEnabledWrapper());
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(int hasCompetitorVar) {
        List<PromptTemplateIntentStatRow> stats = aiPromptResultMapper.selectTemplateIntentStats();
        if (stats == null || stats.isEmpty()) {
            return 0;
        }
        return stats.stream()
                .filter(row -> row != null && row.getHasCompetitorVar() != null
                        && row.getHasCompetitorVar() == hasCompetitorVar)
                .mapToInt(row -> row.getTemplateCount() == null ? 0 : row.getTemplateCount())
                .sum();
    }
}
