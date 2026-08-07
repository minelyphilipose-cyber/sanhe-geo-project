package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.DeriveVersionRequest;
import com.huanjing.geo.module.presale.dto.DeriveVersionResponse;
import com.huanjing.geo.module.presale.dto.EditVersionContentRequest;
import com.huanjing.geo.module.presale.dto.FreezeVersionRequest;
import com.huanjing.geo.module.presale.dto.RetryVersionResponse;
import com.huanjing.geo.module.presale.dto.VersionActionResponse;
import com.huanjing.geo.module.presale.dto.snapshot.editable.CompetitorSceneDescription;
import com.huanjing.geo.module.presale.dto.snapshot.editable.EditableContentDTO;
import com.huanjing.geo.module.presale.dto.snapshot.editable.ExecutiveSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.FindingContent;
import com.huanjing.geo.module.presale.dto.snapshot.editable.KeyTakeaway;
import com.huanjing.geo.module.presale.dto.snapshot.editable.MarketBattleground;
import com.huanjing.geo.module.presale.dto.snapshot.editable.PhaseDescription;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.generate.PresaleGenerateCancellationRegistry;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleWebReadinessChecker;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.generate.l3.MarketBattlegroundValidator;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3Defaults;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 版本写动作 Service:edit / derive / freeze / unfreeze / delete / retry。
 *
 * <p>所有方法遵循统一的参数校验与权限顺序:
 * <ol>
 *   <li>校验 report 存在(404)</li>
 *   <li>校验 version 存在且属于该 report(404)</li>
 *   <li>权限校验(403,由 CurrentUserService 抛出)</li>
 *   <li>业务状态校验(409,如冻结/未失败)</li>
 *   <li>执行 + 返回响应</li>
 * </ol>
 * </p>
 *
 * <p>权限 key 约定:
 * <ul>
 *   <li>编辑类操作 —— manager 或报告创建者本人可执行</li>
 *   <li>{@code presale.report.manage} —— 全局管理;报告创建者本人可管理自己的版本</li>
 * </ul>
 * 所有异常消息使用英文,对齐仓库现状(CurrentUserService 同风格),避免跨平台编码风险。
 * </p>
 *
 * <p>物理删除对齐仓库主流风格(Company/Project/Brand/KeywordGroup/AiPlatformConfig)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportVersionActionService {

    /**
     * V65 新增 seed:presale.report.manage。
     * 该权限代表全局管理;报告创建者本人无需全局权限即可管理自己的报告版本。
     */
    private static final String PERM_MANAGE = "presale.report.manage";
    private static final String ERR_CONTENT_CONFLICT = "content_conflict";
    private static final String ERR_VERSION_FROZEN = "version_frozen";
    private static final String ERR_VERSION_NOT_DONE = "version_not_done";

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleAiPromptJudgeResultMapper aiPromptJudgeResultMapper;
    private final CurrentUserService currentUserService;
    private final PresaleAccessService accessService;
    private final ObjectMapper objectMapper;
    private final PresaleL3Defaults l3Defaults;
    private final MarketBattlegroundValidator marketBattlegroundValidator;
    private final PresaleGenerateCancellationRegistry cancellationRegistry;
    private final PresaleWebReadinessChecker webReadinessChecker;

    /**
     * P1·F·1·a 已存在的 Mock Orchestrator,retry 时重新触发生成。
     * 若后续替换为真实 orchestrator,该依赖接口不变(通过配置
     * {@code presale.generate.mock} 切换实现)。
     */
    private final PresaleGenerateOrchestrator generateOrchestrator;

    // ---------------------------------------------------------------
    // 1. PATCH content -- edit L3
    // ---------------------------------------------------------------

    @Transactional
    public VersionActionResponse editContent(Long reportId, Integer versionNo,
                                             EditVersionContentRequest req) {
        PresaleReport report = requireEditableReport(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (version.getFrozenAt() != null) {
            throw editConflict(ERR_VERSION_FROZEN, "Version is frozen, cannot edit");
        }
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())) {
            // 只有生成完成的版本允许编辑 L3;INIT/QUEUED/RUNNING/FAILED 均不可
            throw editConflict(ERR_VERSION_NOT_DONE, "Version not generated yet, cannot edit");
        }
        validateEditableContentJson(req.getEditableContentJson(), false);
        String normalizedEditableContentJson = l3Defaults.normalizeJson(
                req.getEditableContentJson(),
                version.getRawSnapshotJson(),
                version.getComputedSnapshotJson());
        validateEditableContentJson(normalizedEditableContentJson, true);
        if (!Boolean.TRUE.equals(req.getForceOverwrite())
                && !Objects.equals(version.getContentUpdatedAt(), req.getExpectedContentUpdatedAt())) {
            throw editConflict(ERR_CONTENT_CONFLICT, "Content has been updated by another user");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getEditableContentJson, normalizedEditableContentJson)
                .set(PresaleReportVersion::getContentUpdatedAt, now);
        versionMapper.update(null, update);

        return VersionActionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(version.getGenerationStatus())
                .frozen(false)
                .updatedAt(now)
                .build();
    }

    private BizException editConflict(String errorCode, String message) {
        return new BizException(409, message, 200, Map.of("errorCode", errorCode));
    }

    private PresaleReport requireEditableReport(Long reportId) {
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        if (!accessService.canEditCurrentUser(report)) {
            throw new BizException(403, "No edit access to this report");
        }
        return report;
    }

    private void validateEditableContentJson(String json, boolean requireAllTopLevelFields) {
        JsonNode root;
        EditableContentDTO dto;
        try {
            root = objectMapper.readTree(json);
            dto = objectMapper.treeToValue(root, EditableContentDTO.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BizException(400, "Invalid editable content JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BizException(400, "Invalid editable content JSON");
        }
        marketBattlegroundValidator.validateRawJson(root.get("market_battleground"));
        if (requireAllTopLevelFields) {
            requireTopLevelFields(root);
        }
        validateText("report_title", dto.getReportTitle(), 40);
        validateText("report_subtitle", dto.getReportSubtitle(), 80);
        validateExecutiveSummary(dto.getExecutiveSummary());
        validateMarketBattleground(dto.getMarketBattleground());
        validateKeyTakeaways(dto.getKeyTakeaways());
        validateFindings(dto.getOptimizationFindingsContent());
        validatePhases(dto.getPhaseDescriptions());
        validateCompetitors(dto.getCompetitorSceneDescriptions());
        validateText("roi_disclaimer", dto.getRoiDisclaimer(), 200);
    }

    private void requireTopLevelFields(JsonNode root) {
        List<String> fields = List.of(
                "report_title",
                "report_subtitle",
                "market_battleground",
                "executive_summary",
                "key_takeaways",
                "optimization_findings_content",
                "phase_descriptions",
                "competitor_scene_descriptions",
                "roi_disclaimer"
        );
        for (String field : fields) {
            if (!root.has(field)) {
                throw new BizException(400, "Missing editable content field: " + field);
            }
        }
    }

    private void validateExecutiveSummary(ExecutiveSummary summary) {
        if (summary == null) {
            return;
        }
        requireText("executive_summary.headline", summary.getHeadline(), 60);
        requireText("executive_summary.paragraph", summary.getParagraph(), 500);
    }

    private void validateKeyTakeaways(List<KeyTakeaway> list) {
        requireList("key_takeaways", list);
        if (list.size() > 8) {
            throw new BizException(400, "key_takeaways must not exceed 8 items");
        }
        for (int i = 0; i < list.size(); i++) {
            KeyTakeaway item = list.get(i);
            if (item == null) {
                throw new BizException(400, "key_takeaways item must not be null");
            }
            if (item.getOrderNo() == null || item.getOrderNo() <= 0) {
                throw new BizException(400, "key_takeaways.order_no must be positive");
            }
            requireText("key_takeaways.title", item.getTitle(), 30);
            requireText("key_takeaways.description", item.getDescription(), 500);
        }
    }

    private void validateMarketBattleground(MarketBattleground value) {
        marketBattlegroundValidator.validate(value);
    }

    private void validateMarketCard(MarketBattleground.MarketCard value) {
        if (value == null) {
            return;
        }
        validateText("market_battleground.market_card.label", value.getLabel(), 32);
        validateText("market_battleground.market_card.source", value.getSource(), 32);
        if (value.getStats() != null) {
            if (value.getStats().size() != 4) {
                throw new BizException(400, "market_battleground.market_card.stats must contain exactly 4 items");
            }
            for (MarketBattleground.Stat item : value.getStats()) {
                if (item == null) {
                    throw new BizException(400, "market_battleground.market_card.stats item must not be null");
                }
                validateText("market_battleground.market_card.stats.value", item.getValue(), 12);
                validateText("market_battleground.market_card.stats.unit", item.getUnit(), 8);
                validateText("market_battleground.market_card.stats.label", item.getLabel(), 24);
            }
        }
        validateText("market_battleground.market_card.platform_label", value.getPlatformLabel(), 16);
        if (value.getPlatforms() != null) {
            if (value.getPlatforms().size() != 3) {
                throw new BizException(400, "market_battleground.market_card.platforms must contain exactly 3 items");
            }
            for (MarketBattleground.Platform item : value.getPlatforms()) {
                if (item == null) {
                    throw new BizException(400, "market_battleground.market_card.platforms item must not be null");
                }
                validateText("market_battleground.market_card.platforms.name", item.getName(), 12);
                validateText("market_battleground.market_card.platforms.value", item.getValue(), 12);
            }
        }
        validateText("market_battleground.market_card.platform_suffix", value.getPlatformSuffix(), 18);
    }

    private void validateCalculationCard(String field, MarketBattleground.CalculationCard value) {
        if (value == null) {
            return;
        }
        validateText(field + ".label", value.getLabel(), 24);
        validateText(field + ".value_prefix", value.getValuePrefix(), 6);
        validateText(field + ".value", value.getValue(), 12);
        validateText(field + ".unit", value.getUnit(), 8);
        validateText(field + ".subtitle", value.getSubtitle(), 28);
        validateText(field + ".calculation_label", value.getCalculationLabel(), 24);
        if (value.getRows() == null) {
            return;
        }
        if (value.getRows().size() != 4) {
            throw new BizException(400, field + ".rows must contain exactly 4 items");
        }
        for (MarketBattleground.CalculationRow row : value.getRows()) {
            if (row == null) {
                throw new BizException(400, field + ".rows item must not be null");
            }
            validateText(field + ".rows.label", row.getLabel(), 18);
            validateText(field + ".rows.value", row.getValue(), 30);
        }
    }

    private void validateNarrative(MarketBattleground.Narrative value) {
        if (value == null) {
            return;
        }
        validateText("market_battleground.narrative.intro", value.getIntro(), 56);
        if (value.getQuestions() != null) {
            if (value.getQuestions().size() != 3) {
                throw new BizException(400, "market_battleground.narrative.questions must contain exactly 3 items");
            }
            for (String question : value.getQuestions()) {
                validateText("market_battleground.narrative.questions[]", question, 34);
            }
        }
        validateText("market_battleground.narrative.conclusion", value.getConclusion(), 44);
        validateText("market_battleground.narrative.brand_line_prefix", value.getBrandLinePrefix(), 8);
        validateText("market_battleground.narrative.brand_name", value.getBrandName(), 18);
        validateText("market_battleground.narrative.brand_line_suffix", value.getBrandLineSuffix(), 48);
    }

    private void validateFindings(List<FindingContent> list) {
        requireList("optimization_findings_content", list);
        for (FindingContent item : list) {
            if (item == null) {
                throw new BizException(400, "optimization_findings_content item must not be null");
            }
            requireText("optimization_findings_content.finding_id", item.getFindingId(), 64);
            validateText("optimization_findings_content.title", item.getTitle(), 50);
            validateText("optimization_findings_content.description", item.getDescription(), 500);
            validateText("optimization_findings_content.evidence_text", item.getEvidenceText(), 300);
        }
    }

    private void validatePhases(List<PhaseDescription> list) {
        requireList("phase_descriptions", list);
        if (list.size() != 3) {
            throw new BizException(400, "phase_descriptions must contain exactly 3 items");
        }
        Set<Integer> seen = new HashSet<>();
        for (PhaseDescription item : list) {
            if (item == null) {
                throw new BizException(400, "phase_descriptions item must not be null");
            }
            Integer phaseNo = item.getPhaseNo();
            if (phaseNo == null || phaseNo < 1 || phaseNo > 3) {
                throw new BizException(400, "phase_no must be 1, 2 or 3");
            }
            if (!seen.add(phaseNo)) {
                throw new BizException(400, "phase_no must be unique");
            }
            validateText("phase_descriptions.title", item.getTitle(), 30);
            validateText("phase_descriptions.description", item.getDescription(), 300);
        }
    }

    private void validateCompetitors(List<CompetitorSceneDescription> list) {
        requireList("competitor_scene_descriptions", list);
        Set<Integer> seen = new HashSet<>();
        for (CompetitorSceneDescription item : list) {
            if (item == null) {
                throw new BizException(400, "competitor_scene_descriptions item must not be null");
            }
            Integer rank = item.getCompetitorRank();
            if (rank == null || rank < 1 || rank > 3) {
                throw new BizException(400, "competitor_rank must be 1, 2 or 3");
            }
            if (!seen.add(rank)) {
                throw new BizException(400, "competitor_rank must be unique");
            }
            List<String> values = item.getSceneAdvantagesPolished();
            if (values == null) {
                continue;
            }
            if (values.size() > 6) {
                throw new BizException(400, "scene_advantages_polished must not exceed 6 items");
            }
            for (String value : values) {
                requireText("scene_advantages_polished[]", value, 100);
            }
        }
    }

    private void requireList(String field, List<?> list) {
        if (list == null) {
            throw new BizException(400, field + " must not be null");
        }
    }

    private void requireText(String field, String value, int maxLength) {
        if (value == null) {
            throw new BizException(400, field + " must not be null");
        }
        validateText(field, value, maxLength);
    }

    private void validateText(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(400, field + " length must not exceed " + maxLength);
        }
    }

    // ---------------------------------------------------------------
    // 2. POST derive -- derive a new version
    // ---------------------------------------------------------------

    @Transactional
    public DeriveVersionResponse derive(Long reportId, Integer versionNo,
                                        DeriveVersionRequest req) {
        PresaleReport report = requireEditableReport(reportId);
        SysUser user = currentUserService.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        PresaleReportVersion source = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (!PresaleGenerateStatus.DONE.name().equals(source.getGenerationStatus())) {
            throw new BizException(409, "Only DONE version can be derived");
        }

        // 新 versionNo:同 report 下 max(version_no) + 1
        Integer maxVersionNo = versionMapper.selectMaxVersionNo(report.getId());
        int nextNo = (maxVersionNo == null ? 0 : maxVersionNo) + 1;

        // ============================================================
        // P1·F·1·b·1 r2 修复(Codex P1):
        // L1 raw 层属于"事实冻结层",派生版本必须原样继承源版本的事实元数据,
        // 包括 isDegraded / degradedPlatforms / totalLlmCalls / completedLlmCalls。
        // r1 把这些字段置为 false/null/0 是把它们当成运行态字段处理,错。
        // 修复后,派生版本对 L1 的事实记录与源版本一致,只有"版本号 / 派生来源 /
        // 导出计数 / 创建人"是新版本自有。
        // ============================================================
        PresaleReportVersion next = new PresaleReportVersion();
        next.setReportId(report.getId());
        next.setVersionNo(nextNo);
        next.setDerivedFromVersionId(source.getId());

        // 状态:派生完成后直接就绪(无需重跑 LLM)
        next.setGenerationStatus(PresaleGenerateStatus.DONE.name());
        next.setQueryWebMode(source.getQueryWebMode());
        next.setAttributionMode(source.getAttributionMode());
        next.setMatchedRoleName(source.getMatchedRoleName());
        next.setRepresentedBrandsSnapshot(source.getRepresentedBrandsSnapshot());
        next.setBenchmarkIndustryKey(source.getBenchmarkIndustryKey());
        next.setIndustryClassificationSource(source.getIndustryClassificationSource());
        next.setIndustryClassificationConfidence(source.getIndustryClassificationConfidence());
        next.setIndustryClassifierModel(source.getIndustryClassifierModel());

        // 事实冻结层字段:继承源版本
        next.setTotalLlmCalls(source.getTotalLlmCalls());
        next.setCompletedLlmCalls(source.getCompletedLlmCalls());
        next.setIsDegraded(source.getIsDegraded());
        next.setDegradedPlatforms(source.getDegradedPlatforms());
        next.setPlannedQueryCount(source.getPlannedQueryCount());
        next.setPlannedWebQueryCount(source.getPlannedWebQueryCount());
        next.setWebValidQueryCount(source.getWebValidQueryCount());
        next.setEffectiveSampleCount(source.getEffectiveSampleCount());
        next.setQueryFailedCount(source.getQueryFailedCount());
        next.setAnalyzeFailedCount(source.getAnalyzeFailedCount());
        next.setSkippedQueryCount(source.getSkippedQueryCount());
        next.setDegradedExcludedSampleCount(source.getDegradedExcludedSampleCount());
        next.setMainWebFailureCode(source.getMainWebFailureCode());
        next.setFailureReason(null); // source 是 DONE,本就为 null,这里显式一次

        // 三层 JSON:继承源版本(用户此后可在新版本上继续编辑 L3)
        next.setRawSnapshotJson(source.getRawSnapshotJson());
        next.setComputedSnapshotJson(source.getComputedSnapshotJson());
        next.setEditableContentJson(l3Defaults.normalizeJson(
                source.getEditableContentJson(),
                source.getRawSnapshotJson(),
                source.getComputedSnapshotJson()));

        // 新版本自有字段
        next.setExportSuccessCount(0);
        next.setCreatedBy(user.getId());
        next.setCreatedAt(now);
        next.setUpdatedAt(now);

        versionMapper.insert(next);

        // 切换 latestVersionId(定稿条款:派生后自动切新版为当前)
        LambdaUpdateWrapper<PresaleReport> reportUpdate = new LambdaUpdateWrapper<PresaleReport>()
                .eq(PresaleReport::getId, report.getId())
                .set(PresaleReport::getLatestVersionId, next.getId());
        reportMapper.update(null, reportUpdate);

        log.info("presale.derive report={} from v{}({}) to v{}({}) by user={}",
                report.getId(), source.getVersionNo(), source.getId(),
                next.getVersionNo(), next.getId(), user.getId());

        return DeriveVersionResponse.builder()
                .newVersionId(next.getId())
                .newVersionNo(next.getVersionNo())
                .sourceVersionId(source.getId())
                .sourceVersionNo(source.getVersionNo())
                .latestVersionId(next.getId())
                .build();
    }

    // ---------------------------------------------------------------
    // 3. POST freeze -- manager 或报告创建者本人可执行
    // ---------------------------------------------------------------

    @Transactional
    public VersionActionResponse freeze(Long reportId, Integer versionNo,
                                        FreezeVersionRequest req) {
        PresaleReport report = requireEditableReport(reportId);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (version.getFrozenAt() != null) {
            throw new BizException(409, "Version already frozen");
        }
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Only DONE version can be frozen");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getFrozenAt, now)
                .set(PresaleReportVersion::getFrozenBy, user.getId())
                .set(PresaleReportVersion::getFrozenReason,
                        req == null ? null : req.getReason());
        versionMapper.update(null, update);

        log.info("presale.freeze report={} version={} by user={}",
                report.getId(), versionNo, user.getId());

        return VersionActionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(version.getGenerationStatus())
                .frozen(true)
                .frozenAt(now)
                .updatedAt(now)
                .build();
    }

    // ---------------------------------------------------------------
    // 4. POST unfreeze -- global manager or report owner
    // ---------------------------------------------------------------

    @Transactional
    public VersionActionResponse unfreeze(Long reportId, Integer versionNo) {
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = requireManageableReport(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (version.getFrozenAt() == null) {
            throw new BizException(409, "Version not frozen");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getFrozenAt, null)
                .set(PresaleReportVersion::getFrozenBy, null)
                .set(PresaleReportVersion::getFrozenReason, null);
        versionMapper.update(null, update);

        log.info("presale.unfreeze report={} version={} by user={}",
                report.getId(), versionNo, user.getId());

        return VersionActionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(version.getGenerationStatus())
                .frozen(false)
                .updatedAt(now)
                .build();
    }

    // ---------------------------------------------------------------
    // 5. DELETE -- global manager or report owner, physical delete, exported forbidden
    // ---------------------------------------------------------------

    @Transactional
    public void delete(Long reportId, Integer versionNo) {
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = requireManageableReport(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        // 已导出过的版本禁删(定稿条款)
        Integer exportedCount = version.getExportSuccessCount();
        if (exportedCount != null && exportedCount > 0) {
            throw new BizException(409, "Version has exports, cannot delete");
        }

        // 物理删除(对齐仓库主流)
        versionMapper.deleteById(version.getId());

        // 若删的正是 latestVersion,回退到同 report 下 versionNo 最大的存活版;
        // 若无存活版本,置 null(兜底)
        boolean isLatest = version.getId().equals(report.getLatestVersionId());
        if (isLatest) {
            PresaleReportVersion fallback = versionMapper.selectOne(
                    new LambdaQueryWrapper<PresaleReportVersion>()
                            .eq(PresaleReportVersion::getReportId, report.getId())
                            .orderByDesc(PresaleReportVersion::getVersionNo)
                            .last("LIMIT 1")
            );
            Long newLatestId = fallback == null ? null : fallback.getId();

            // 注意:set null 时 LambdaUpdateWrapper 默认不会忽略,这里需要显式 set
            LambdaUpdateWrapper<PresaleReport> reportUpdate = new LambdaUpdateWrapper<PresaleReport>()
                    .eq(PresaleReport::getId, report.getId())
                    .set(PresaleReport::getLatestVersionId, newLatestId);
            reportMapper.update(null, reportUpdate);

            log.info("presale.delete report={} version={} was latest, rollback latestVersionId to {} by user={}",
                    report.getId(), versionNo, newLatestId, user.getId());
        } else {
            log.info("presale.delete report={} version={} by user={}",
                    report.getId(), versionNo, user.getId());
        }
    }

    private PresaleReport requireManageableReport(Long reportId) {
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        if (!currentUserService.hasPermission(PERM_MANAGE) && !accessService.canEditCurrentUser(report)) {
            throw new BizException(403, "No manage access to this report");
        }
        return report;
    }

    // ---------------------------------------------------------------
    // 6. POST retry -- only FAILED version, reuse versionNo
    // ---------------------------------------------------------------

    @Transactional
    public RetryVersionResponse retry(Long reportId, Integer versionNo) {
        PresaleReport report = requireEditableReport(reportId);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (!PresaleGenerateStatus.FAILED.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Only FAILED version can be retried");
        }
        // Retry keeps the saved version contract and checks it before cancellation state is changed.
        webReadinessChecker.checkSavedMode(version.getQueryWebMode());
        cancellationRegistry.clear(version.getId());

        // 重置失败态相关字段(保留 L1/L2/L3 JSON,避免重试期间前端看到空页)
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getGenerationStatus, PresaleGenerateStatus.QUEUED.name())
                .set(PresaleReportVersion::getGenerationStage, null)
                .set(PresaleReportVersion::getTotalLlmCalls, version.getTotalLlmCalls())
                .set(PresaleReportVersion::getCompletedLlmCalls, 0)
                .set(PresaleReportVersion::getBatch1CompletedCalls, 0)
                .set(PresaleReportVersion::getBatch2CompletedCalls, 0)
                .set(PresaleReportVersion::getBatch2TotalCalls, version.getBatch2TotalCalls())
                .set(PresaleReportVersion::getExtractedCompetitorCount, null)
                .set(PresaleReportVersion::getIsDegraded, false)
                .set(PresaleReportVersion::getDegradedPlatforms, null)
                .set(PresaleReportVersion::getFailureReason, null)
                .set(PresaleReportVersion::getFailureCategory, null);
        versionMapper.update(null, update);

        // 事务提交后触发,避免异步线程早于 QUEUED 状态提交而跳过执行。
        triggerGenerateAfterCommit(version.getId(), user.getId(), accessService.canManageCurrentUser());

        log.info("presale.retry report={} version={} by user={}",
                report.getId(), versionNo, user.getId());

        return RetryVersionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(PresaleGenerateStatus.QUEUED.name())
                .build();
    }

    @Transactional
    public RetryVersionResponse regenerate(Long reportId, Integer versionNo) {
        PresaleReport report = requireEditableReport(reportId);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())
                && !PresaleGenerateStatus.FAILED.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Only DONE or FAILED version can be regenerated");
        }
        if (version.getFrozenAt() != null) {
            throw new BizException(409, "Frozen version cannot be regenerated");
        }
        // Regenerate is a new run and adopts the current application mode. Check before any cleanup.
        PresaleQueryWebMode queryWebMode = webReadinessChecker.checkConfiguredMode().mode();
        cancellationRegistry.clear(version.getId());

        clearGeneratedRunData(version.getId());

        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getGenerationStatus, PresaleGenerateStatus.QUEUED.name())
                .set(PresaleReportVersion::getQueryWebMode, queryWebMode.name())
                .set(PresaleReportVersion::getGenerationStage, null)
                .set(PresaleReportVersion::getTotalLlmCalls, version.getTotalLlmCalls())
                .set(PresaleReportVersion::getCompletedLlmCalls, 0)
                .set(PresaleReportVersion::getBatch1CompletedCalls, 0)
                .set(PresaleReportVersion::getBatch2CompletedCalls, 0)
                .set(PresaleReportVersion::getBatch2TotalCalls, version.getBatch2TotalCalls())
                .set(PresaleReportVersion::getExtractedCompetitorCount, null)
                .set(PresaleReportVersion::getFailureReason, null)
                .set(PresaleReportVersion::getFailureCategory, null)
                .set(PresaleReportVersion::getIsDegraded, false)
                .set(PresaleReportVersion::getDegradedPlatforms, null);
        update.set(PresaleReportVersion::getPlannedQueryCount, 0)
                .set(PresaleReportVersion::getPlannedWebQueryCount, 0)
                .set(PresaleReportVersion::getWebValidQueryCount, 0)
                .set(PresaleReportVersion::getEffectiveSampleCount, 0)
                .set(PresaleReportVersion::getQueryFailedCount, 0)
                .set(PresaleReportVersion::getAnalyzeFailedCount, 0)
                .set(PresaleReportVersion::getSkippedQueryCount, 0)
                .set(PresaleReportVersion::getDegradedExcludedSampleCount, 0)
                .set(PresaleReportVersion::getMainWebFailureCode, null);
        versionMapper.update(null, update);

        triggerGenerateAfterCommit(version.getId(), user.getId(), accessService.canManageCurrentUser());

        return RetryVersionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(PresaleGenerateStatus.QUEUED.name())
                .build();
    }

    @Transactional
    public RetryVersionResponse cancelGeneration(Long reportId, Integer versionNo) {
        PresaleReport report = requireEditableReport(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);
        String status = version.getGenerationStatus();
        if (!PresaleGenerateStatus.QUEUED.name().equals(status)
                && !PresaleGenerateStatus.RUNNING.name().equals(status)) {
            throw new BizException(409, "Only QUEUED or RUNNING version can be canceled");
        }

        cancellationRegistry.cancel(version.getId());
        String reason = "Generation canceled by user";
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .in(PresaleReportVersion::getGenerationStatus,
                        PresaleGenerateStatus.QUEUED.name(), PresaleGenerateStatus.RUNNING.name())
                .set(PresaleReportVersion::getGenerationStatus, PresaleGenerateStatus.FAILED.name())
                .set(PresaleReportVersion::getFailureCategory, "MANUAL_CANCELED")
                .set(PresaleReportVersion::getFailureReason, reason)
                .set(PresaleReportVersion::getUpdatedAt, LocalDateTime.now());
        int updated = versionMapper.update(null, update);
        if (updated == 0) {
            throw new BizException(409, "Version generation status changed, cancel rejected");
        }

        PresaleReport reportUpdate = new PresaleReport();
        reportUpdate.setId(report.getId());
        reportUpdate.setStatus(PresaleGenerateStatus.FAILED.name());
        reportUpdate.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(reportUpdate);

        return RetryVersionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(PresaleGenerateStatus.FAILED.name())
                .build();
    }

    private void clearGeneratedRunData(Long versionId) {
        aiPromptJudgeResultMapper.delete(new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                .eq(PresaleAiPromptJudgeResult::getVersionId, versionId));
        aiPromptResultMapper.delete(new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId));
        aiCallMapper.delete(new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId));
    }

    private void triggerGenerateAfterCommit(Long versionId, Long userId, boolean canManageCurrentUser) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generateOrchestrator.triggerGenerate(versionId, userId, canManageCurrentUser);
                }
            });
            return;
        }
        generateOrchestrator.triggerGenerate(versionId, userId, canManageCurrentUser);
    }

}
