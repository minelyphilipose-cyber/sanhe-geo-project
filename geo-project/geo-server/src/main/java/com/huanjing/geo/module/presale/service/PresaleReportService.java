package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.partner.service.PartnerPresaleReportQuotaService;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import com.huanjing.geo.module.presale.export.service.PresaleExportStatuses;
import com.huanjing.geo.module.presale.dto.PromptSourceMode;
import com.huanjing.geo.module.presale.dto.request.CreateReportRequest;
import com.huanjing.geo.module.presale.dto.request.ReportListQueryRequest;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.response.PromptTemplateVO;
import com.huanjing.geo.module.presale.dto.response.RegenerateDraftVO;
import com.huanjing.geo.module.presale.dto.response.ReportScopePreviewVO;
import com.huanjing.geo.module.presale.dto.response.ReportListItemVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.generate.PromptScopeCalculator;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PresaleIntentCode;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import com.huanjing.geo.module.presale.access.AccessScope;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.EnumMap;
import java.util.Set;
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
    private static final String PERM_DELETE = "presale.report.delete";
    private static final List<String> ACTIVE_GENERATION_STATUSES = List.of(
            PresaleGenerateStatus.INIT.name(),
            PresaleGenerateStatus.QUEUED.name(),
            PresaleGenerateStatus.RUNNING.name()
    );
    private static final List<String> ACTIVE_EXPORT_STATUSES = List.of(
            PresaleExportStatuses.PENDING,
            PresaleExportStatuses.RUNNING
    );

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportExportMapper exportMapper;
    private final PresaleGenerateOrchestrator orchestrator;
    private final CurrentUserService currentUserService;
    private final PresaleAccessService accessService;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    private final PromptTemplateDraftValidator promptTemplateDraftValidator;
    private final LlmPromptQuestionDraftValidator llmPromptQuestionDraftValidator;
    private final PartnerPresaleReportQuotaService partnerPresaleReportQuotaService;
    private final ObjectMapper objectMapper;
    @Value("${presale.prompt.active-version:v2}")
    private String activePromptTemplateVersion;

    public PresaleReportService(PresaleReportMapper reportMapper,
                                PresaleReportVersionMapper versionMapper,
                                PresaleReportExportMapper exportMapper,
                                PresaleGenerateOrchestrator orchestrator,
                                CurrentUserService currentUserService,
                                PresaleAccessService accessService,
                                AiPlatformConfigMapper aiPlatformConfigMapper,
                                PresaleAiPromptResultMapper aiPromptResultMapper,
                                PresalePromptTemplateMapper promptTemplateMapper,
                                PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper,
                                PromptTemplateDraftValidator promptTemplateDraftValidator,
                                LlmPromptQuestionDraftValidator llmPromptQuestionDraftValidator,
                                PartnerPresaleReportQuotaService partnerPresaleReportQuotaService,
                                ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.versionMapper = versionMapper;
        this.exportMapper = exportMapper;
        this.orchestrator = orchestrator;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.versionPromptTemplateMapper = versionPromptTemplateMapper;
        this.promptTemplateDraftValidator = promptTemplateDraftValidator;
        this.llmPromptQuestionDraftValidator = llmPromptQuestionDraftValidator;
        this.partnerPresaleReportQuotaService = partnerPresaleReportQuotaService;
        this.objectMapper = objectMapper;
    }

    /**
     * 新建报告:insert report + insert 首版 version + 触发异步生成。
     *
     * @return 新建的 reportId(前端跳进度页用)
     */
    @Transactional
    public Long createReport(CreateReportRequest req) {
        currentUserService.ensurePermission(PERM_CREATE);
        var currentUser = currentUserService.requireCurrentUser();
        Long userId = currentUser.getId();
        PartnerPresaleReportQuotaService.Reservation reservation =
                partnerPresaleReportQuotaService.reserveIfPartner(currentUser, req);
        if (reservation.existingReportId() != null) {
            return reservation.existingReportId();
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> brandFormerNames = normalizeBrandFormerNames(req.getBrandFormerNames(), req.getBrandName());
        List<String> specifiedCompetitors = normalizeSpecifiedCompetitors(
                req.getSpecifiedCompetitors(), req.getBrandName(), brandFormerNames);

        PresaleReport report = new PresaleReport();
        report.setBrandName(req.getBrandName());
        report.setBrandFormerNames(toJsonArray(brandFormerNames, "品牌曾用名序列化失败"));
        report.setIndustry(req.getIndustry());
        report.setIndustryRole(req.getIndustryRole());
        report.setRegion(req.getRegion());
        report.setUserDemand(req.getUserDemand());
        report.setUserType(req.getUserType());
        report.setSpecifiedCompetitors(toJsonArray(specifiedCompetitors));
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        report.setCreatedBy(userId);
        applyPartnerReservation(report, reservation);
        reportMapper.insert(report);
        partnerPresaleReportQuotaService.confirm(reservation, report.getId());

        PresaleReportVersion version = new PresaleReportVersion();
        version.setReportId(report.getId());
        version.setVersionNo(1);
        version.setGenerationStatus(PresaleGenerateStatus.QUEUED.name());
        version.setTotalLlmCalls(0);
        version.setCompletedLlmCalls(0);
        version.setBatch1TotalCalls(0);
        version.setBatch1CompletedCalls(0);
        version.setBatch2TotalCalls(null);
        version.setBatch2CompletedCalls(0);
        version.setIsDegraded(false);
        version.setExportSuccessCount(0);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setCreatedBy(userId);
        versionMapper.insert(version);

        List<PresaleReportVersionPromptTemplate> promptSnapshots = buildPromptSnapshots(req, report.getId(), version.getId(), now);
        for (PresaleReportVersionPromptTemplate row : promptSnapshots) {
            versionPromptTemplateMapper.insert(row);
        }
        applyPromptScopeToVersion(version.getId(), promptSnapshots);

        // 回填 latest_version_id
        report.setLatestVersionId(version.getId());
        reportMapper.updateById(report);

        // 异步生成在事务提交后触发,避免异步线程早于 version insert 提交导致 "version not found"
        triggerGenerateAfterCommit(version.getId(), userId, accessService.canManageCurrentUser());

        return report.getId();
    }

    private void applyPartnerReservation(PresaleReport report, PartnerPresaleReportQuotaService.Reservation reservation) {
        if (reservation == null || !reservation.partnerReservation()) {
            return;
        }
        report.setPartnerId(reservation.partnerId());
        report.setPartnerPresaleChargeType(reservation.quotaTxn().getBizType());
        report.setPartnerPresalePoints(reservation.quotaTxn().getPointsAmount());
        report.setPartnerPresaleQuotaTxnId(reservation.quotaTxn().getId());
        report.setPartnerPresalePointsTxnId(reservation.pointsTxn() == null ? null : reservation.pointsTxn().getId());
        report.setRequestId(reservation.requestId());
        report.setRequestHash(reservation.requestHash());
        report.setRequestPayloadSnapshotJson(reservation.requestPayloadJson());
    }

    private List<PresaleReportVersionPromptTemplate> buildPromptSnapshots(CreateReportRequest req,
                                                                          Long reportId,
                                                                          Long versionId,
                                                                          LocalDateTime now) {
        PromptSourceMode mode = PromptSourceMode.fromJson(req.getPromptSourceMode());
        if (mode == PromptSourceMode.LLM) {
            return llmPromptQuestionDraftValidator.validateAndBuildSnapshots(
                    req.getLlmQuestionPlan(),
                    req.getLlmPromptQuestions(),
                    reportId,
                    versionId,
                    now
            );
        }
        return promptTemplateDraftValidator.validateAndBuildSnapshots(
                req.getPromptTemplateVersion(),
                req.getPromptTemplates(),
                activePromptTemplateVersion,
                reportId,
                versionId,
                now
        );
    }

    private void applyPromptScopeToVersion(Long versionId, List<PresaleReportVersionPromptTemplate> promptSnapshots) {
        int platformCount = countEnabledPlatforms();
        int genericPromptCount = 0;
        int competitorPromptCount = 0;
        for (PresaleReportVersionPromptTemplate row : promptSnapshots) {
            if (Integer.valueOf(1).equals(row.getHasCompetitorVar())) {
                competitorPromptCount++;
            } else {
                genericPromptCount++;
            }
        }
        PromptScopeCalculator.ScopeResult scope = PromptScopeCalculator.calculate(
                platformCount,
                genericPromptCount,
                competitorPromptCount
        );
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setTotalLlmCalls(scope.totalUpperBound());
        update.setBatch1TotalCalls(scope.batch1Calls());
        update.setBatch2TotalCalls(null);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    /**
     * 列表页删除报告。当前采用软删除:隐藏报告主入口,保留版本、AI 调用和导出审计数据。
     */
    @Transactional
    public void deleteReport(Long reportId) {
        currentUserService.ensurePermission(PERM_DELETE);
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        if (!accessService.canEditCurrentUser(report)) {
            throw new BizException(403, "No delete access to this report");
        }
        if (report.getDeletedAt() != null) {
            return;
        }

        ensureNoActiveGeneration(reportId);
        ensureNoActiveExport(reportId);
        ensureNoFrozenVersion(reportId);

        LocalDateTime now = LocalDateTime.now();
        report.setDeletedAt(now);
        report.setDeletedBy(currentUserService.requireCurrentUser().getId());
        report.setUpdatedAt(now);
        reportMapper.updateById(report);
    }

    /**
     * 新建报告页诊断范围预览。与 createReport 写入版本执行量的口径共用同一套计算。
     */
    public ReportScopePreviewVO getScopePreview() {
        currentUserService.ensurePermission(PERM_CREATE);
        return buildScopePreview();
    }

    public List<PromptTemplateVO> listPromptTemplates() {
        currentUserService.ensurePermission(PERM_CREATE);
        return activePromptTemplates().stream()
                .map(t -> PromptTemplateVO.builder()
                        .id(t.getId())
                        .promptCode(t.getPromptCode())
                        .category(t.getCategory())
                        .businessValue(t.getBusinessValue())
                        .promptContent(t.getPromptContent())
                        .hasCompetitorVar(Integer.valueOf(1).equals(t.getHasCompetitorVar()))
                        .sortOrder(t.getSortOrder())
                        .remark(t.getRemark())
                        .templateVersion(t.getTemplateVersion())
                        .build())
                .toList();
    }

    public RegenerateDraftVO buildRegenerateDraft(Long reportId) {
        currentUserService.ensurePermission(PERM_CREATE);
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = report.getLatestVersionId() == null ? null
                : versionMapper.selectById(report.getLatestVersionId());
        if (version == null) {
            throw new BizException(404, "Latest version not found");
        }

        List<PresaleReportVersionPromptTemplate> prompts = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, version.getId())
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );
        String sourceMode = prompts.stream().anyMatch(p -> "llm".equalsIgnoreCase(p.getSourceType()))
                ? PromptSourceMode.LLM.toJson()
                : PromptSourceMode.TEMPLATE.toJson();

        RegenerateDraftVO.RegenerateDraftVOBuilder builder = RegenerateDraftVO.builder()
                .reportId(report.getId())
                .brandName(report.getBrandName())
                .brandFormerNames(parseJsonStringArray(report.getBrandFormerNames()))
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .userType(report.getUserType())
                .specifiedCompetitors(parseJsonStringArray(report.getSpecifiedCompetitors()))
                .promptSourceMode(sourceMode);

        if (PromptSourceMode.LLM.toJson().equals(sourceMode)) {
            builder.llmPromptQuestions(prompts.stream()
                    .map(p -> RegenerateDraftVO.LlmQuestion.builder()
                            .categoryCode(resolveCategoryCode(p.getCategory()))
                            .promptContent(p.getPromptContent())
                            .build())
                    .toList());
            builder.llmQuestionPlan(RegenerateDraftVO.LlmQuestionPlan.builder()
                    .totalCount(prompts.size())
                    .categoryCounts(countCategories(prompts))
                    .build());
        } else {
            builder.promptTemplates(prompts.stream()
                    .filter(p -> p.getSourceTemplateId() != null)
                    .map(p -> RegenerateDraftVO.TemplateQuestion.builder()
                            .sourceTemplateId(p.getSourceTemplateId())
                            .sourcePromptCode(p.getSourcePromptCode())
                            .promptContent(p.getPromptContent())
                            .build())
                    .toList());
        }
        return builder.build();
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

    private Map<PresalePromptCategoryCode, Integer> countCategories(List<PresaleReportVersionPromptTemplate> prompts) {
        Map<PresalePromptCategoryCode, Integer> counts = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            counts.put(code, 0);
        }
        for (PresaleReportVersionPromptTemplate prompt : prompts) {
            PresalePromptCategoryCode code = resolveCategoryCode(prompt.getCategory());
            counts.put(code, counts.getOrDefault(code, 0) + 1);
        }
        return counts;
    }

    private PresalePromptCategoryCode resolveCategoryCode(String category) {
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            if (code.getDisplayName().equals(category)) {
                return code;
            }
        }
        throw new BizException(500, "Unsupported prompt category: " + category);
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
                    .canEdit(canEditLatestVersion(r, v))
                    .canEditReason(resolveEditDisabledReason(r, v))
                    .createdAt(r.getCreatedAt())
                    .build());
        }
        voPage.setRecords(items);
        return voPage;
    }

    private boolean canEditLatestVersion(PresaleReport report, PresaleReportVersion version) {
        if (!accessService.canEditCurrentUser(report)) {
            return false;
        }
        if (version == null) {
            return false;
        }
        return PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus());
    }

    private String resolveEditDisabledReason(PresaleReport report, PresaleReportVersion version) {
        if (!accessService.canEditCurrentUser(report)) {
            return "无编辑权限或非本人创建的报告";
        }
        if (version == null) {
            return "报告版本不存在";
        }
        String status = version.getGenerationStatus();
        if (PresaleGenerateStatus.INIT.name().equals(status)
                || PresaleGenerateStatus.QUEUED.name().equals(status)
                || PresaleGenerateStatus.RUNNING.name().equals(status)) {
            return "报告生成中";
        }
        if (PresaleGenerateStatus.FAILED.name().equals(status)) {
            return "报告生成失败,请重新生成";
        }
        return null;
    }

    private LambdaQueryWrapper<PresaleReport> buildQueryWrapper(ReportListQueryRequest req) {
        LambdaQueryWrapper<PresaleReport> q = new LambdaQueryWrapper<>();
        q.isNull(PresaleReport::getDeletedAt);
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

    private void ensureNoActiveGeneration(Long reportId) {
        Long activeCount = versionMapper.selectCount(new LambdaQueryWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getReportId, reportId)
                .in(PresaleReportVersion::getGenerationStatus, ACTIVE_GENERATION_STATUSES));
        if (activeCount != null && activeCount > 0) {
            throw new BizException(409, "Report is generating, cannot delete now");
        }
    }

    private void ensureNoActiveExport(Long reportId) {
        Long activeCount = exportMapper.selectCount(new LambdaQueryWrapper<PresaleReportExport>()
                .eq(PresaleReportExport::getReportId, reportId)
                .in(PresaleReportExport::getStatus, ACTIVE_EXPORT_STATUSES));
        if (activeCount != null && activeCount > 0) {
            throw new BizException(409, "Report export is running, cannot delete now");
        }
    }

    private void ensureNoFrozenVersion(Long reportId) {
        Long frozenCount = versionMapper.selectCount(new LambdaQueryWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getReportId, reportId)
                .isNotNull(PresaleReportVersion::getFrozenAt));
        if (frozenCount != null && frozenCount > 0) {
            throw new BizException(409, "Report has frozen versions, cannot delete");
        }
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

    private List<String> normalizeBrandFormerNames(List<String> input, String brandName) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<String> values = input.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
        if (values.isEmpty()) {
            return List.of();
        }
        if (values.size() > 3) {
            throw new BizException(400, "品牌曾用名最多 3 个");
        }

        Set<String> dedup = new LinkedHashSet<>();
        String normalizedBrand = normalizeCompetitorName(brandName);
        for (String value : values) {
            String normalized = normalizeCompetitorName(value);
            if (normalized.isEmpty()) {
                throw new BizException(400, "品牌曾用名不能为空");
            }
            if (normalized.equals(normalizedBrand)) {
                throw new BizException(400, "品牌曾用名不能与品牌名称相同");
            }
            if (!dedup.add(normalized)) {
                throw new BizException(400, "品牌曾用名不能重复");
            }
        }
        return values;
    }

    private List<String> normalizeSpecifiedCompetitors(List<String> input,
                                                       String brandName,
                                                       List<String> brandFormerNames) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<String> values = input.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
        if (values.isEmpty()) {
            return List.of();
        }
        if (values.size() != 3) {
            throw new BizException(400, "指定竞品必须为空或正好 3 个");
        }
        Set<String> dedup = new LinkedHashSet<>();
        Set<String> selfNames = new LinkedHashSet<>();
        selfNames.add(normalizeCompetitorName(brandName));
        if (brandFormerNames != null) {
            brandFormerNames.stream()
                    .map(this::normalizeCompetitorName)
                    .filter(value -> !value.isEmpty())
                    .forEach(selfNames::add);
        }
        for (String value : values) {
            String normalized = normalizeCompetitorName(value);
            if (normalized.isEmpty()) {
                throw new BizException(400, "指定竞品不能为空");
            }
            if (selfNames.contains(normalized)) {
                throw new BizException(400, "指定竞品不能与品牌名称或曾用名相同");
            }
            if (!dedup.add(normalized)) {
                throw new BizException(400, "指定竞品不能重复");
            }
        }
        return values;
    }

    private String normalizeCompetitorName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String toJsonArray(List<String> values) {
        return toJsonArray(values, "指定竞品序列化失败");
    }

    private String toJsonArray(List<String> values, String errorMessage) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new BizException(500, errorMessage);
        }
    }

    private List<String> parseJsonStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    out.add(item.asText().trim());
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private int countEnabledPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(PresalePlatformConfigQueries.presaleEnabledWrapper());
        return count == null ? 0 : count.intValue();
    }

    private ReportScopePreviewVO buildScopePreview() {
        int platformCount = countEnabledPlatforms();
        Map<Integer, Integer> promptCountByCompetitorVar = countPromptTemplatesByCompetitorVar();
        int genericPromptCount = promptCountByCompetitorVar.getOrDefault(0, 0);
        int competitorPromptCount = promptCountByCompetitorVar.getOrDefault(1, 0);
        PromptScopeCalculator.ScopeResult scope = PromptScopeCalculator.calculate(
                platformCount,
                genericPromptCount,
                competitorPromptCount
        );
        return ReportScopePreviewVO.builder()
                .platformCount(platformCount)
                .genericPromptCount(genericPromptCount)
                .competitorPromptCount(competitorPromptCount)
                .promptQueryCount(genericPromptCount + competitorPromptCount)
                .llmCallUpperBound(scope.totalUpperBound())
                .dimensionCount(PresaleIntentCode.allInOrder().size())
                .build();
    }

    private List<PresalePromptTemplate> activePromptTemplates() {
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getTemplateVersion, activePromptTemplateVersion)
                        .orderByAsc(PresalePromptTemplate::getSortOrder)
                        .orderByAsc(PresalePromptTemplate::getId)
        );
    }

    private Map<Integer, Integer> countPromptTemplatesByCompetitorVar() {
        List<PromptTemplateIntentStatRow> stats = aiPromptResultMapper.selectTemplateIntentStats(activePromptTemplateVersion);
        if (stats == null || stats.isEmpty()) {
            return Collections.emptyMap();
        }
        return stats.stream()
                .filter(row -> row != null && row.getHasCompetitorVar() != null
                        && row.getTemplateCount() != null)
                .collect(Collectors.groupingBy(
                        PromptTemplateIntentStatRow::getHasCompetitorVar,
                        Collectors.summingInt(PromptTemplateIntentStatRow::getTemplateCount)
                ));
    }
}
