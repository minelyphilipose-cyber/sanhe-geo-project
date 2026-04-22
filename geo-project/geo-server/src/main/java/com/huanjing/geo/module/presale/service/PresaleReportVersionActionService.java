package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.DeriveVersionRequest;
import com.huanjing.geo.module.presale.dto.DeriveVersionResponse;
import com.huanjing.geo.module.presale.dto.EditVersionContentRequest;
import com.huanjing.geo.module.presale.dto.FreezeVersionRequest;
import com.huanjing.geo.module.presale.dto.RetryVersionResponse;
import com.huanjing.geo.module.presale.dto.VersionActionResponse;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
 *   <li>{@code presale.report.edit_content} —— edit/derive/freeze/retry(沿用 V62 已有 seed)</li>
 *   <li>{@code presale.report.manage} —— unfreeze/delete,V65 新增 seed(见 V65__seed_presale_manage_permission.sql)</li>
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
     * 对齐 V62 已有 seed:presale.report.edit_content。
     * edit/derive/freeze/retry 都归属"内容编辑类"操作,复用同一权限 key。
     */
    private static final String PERM_EDIT = "presale.report.edit_content";

    /**
     * V65 新增 seed:presale.report.manage。
     * unfreeze/delete 是管理员级别操作,独立权限 key,只绑 manager 角色。
     */
    private static final String PERM_MANAGE = "presale.report.manage";

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final CurrentUserService currentUserService;
    private final PresaleAccessService accessService;

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
        currentUserService.ensurePermission(PERM_EDIT);

        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (version.getFrozenAt() != null) {
            throw new BizException(409, "Version is frozen, cannot edit");
        }
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())) {
            // 只有生成完成的版本允许编辑 L3;INIT/QUEUED/RUNNING/FAILED 均不可
            throw new BizException(409, "Version not generated yet, cannot edit");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PresaleReportVersion> update = new LambdaUpdateWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getId, version.getId())
                .set(PresaleReportVersion::getEditableContentJson, req.getEditableContentJson())
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

    // ---------------------------------------------------------------
    // 2. POST derive -- derive a new version
    // ---------------------------------------------------------------

    @Transactional
    public DeriveVersionResponse derive(Long reportId, Integer versionNo,
                                        DeriveVersionRequest req) {
        currentUserService.ensurePermission(PERM_EDIT);
        SysUser user = currentUserService.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
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

        // 事实冻结层字段:继承源版本
        next.setTotalLlmCalls(source.getTotalLlmCalls());
        next.setCompletedLlmCalls(source.getCompletedLlmCalls());
        next.setIsDegraded(source.getIsDegraded());
        next.setDegradedPlatforms(source.getDegradedPlatforms());
        next.setFailureReason(null); // source 是 DONE,本就为 null,这里显式一次

        // 三层 JSON:继承源版本(用户此后可在新版本上继续编辑 L3)
        next.setRawSnapshotJson(source.getRawSnapshotJson());
        next.setComputedSnapshotJson(source.getComputedSnapshotJson());
        next.setEditableContentJson(source.getEditableContentJson());

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
    // 3. POST freeze -- sales/manager 皆可(edit_content 权限)
    // ---------------------------------------------------------------

    @Transactional
    public VersionActionResponse freeze(Long reportId, Integer versionNo,
                                        FreezeVersionRequest req) {
        currentUserService.ensurePermission(PERM_EDIT);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
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
    // 4. POST unfreeze -- manager only
    // ---------------------------------------------------------------

    @Transactional
    public VersionActionResponse unfreeze(Long reportId, Integer versionNo) {
        currentUserService.ensurePermission(PERM_MANAGE);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
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

        log.info("presale.unfreeze report={} version={} by manager={}",
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
    // 5. DELETE -- manager only, physical delete, exported forbidden
    // ---------------------------------------------------------------

    @Transactional
    public void delete(Long reportId, Integer versionNo) {
        currentUserService.ensurePermission(PERM_MANAGE);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
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

            log.info("presale.delete report={} version={} was latest, rollback latestVersionId to {} by manager={}",
                    report.getId(), versionNo, newLatestId, user.getId());
        } else {
            log.info("presale.delete report={} version={} by manager={}",
                    report.getId(), versionNo, user.getId());
        }
    }

    // ---------------------------------------------------------------
    // 6. POST retry -- only FAILED version, reuse versionNo
    // ---------------------------------------------------------------

    @Transactional
    public RetryVersionResponse retry(Long reportId, Integer versionNo) {
        currentUserService.ensurePermission(PERM_EDIT);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);

        if (!PresaleGenerateStatus.FAILED.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Only FAILED version can be retried");
        }

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

        // 触发 orchestrator 重跑
        generateOrchestrator.triggerGenerate(version.getId(), user.getId(), accessService.canManageCurrentUser());

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
        currentUserService.ensurePermission(PERM_EDIT);
        SysUser user = currentUserService.requireCurrentUser();

        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = accessService.requireVersionWithAccess(report.getId(), versionNo);
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())
                && !PresaleGenerateStatus.FAILED.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Only DONE or FAILED version can be regenerated");
        }
        if (version.getFrozenAt() != null) {
            throw new BizException(409, "Frozen version cannot be regenerated");
        }

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
                .set(PresaleReportVersion::getFailureReason, null)
                .set(PresaleReportVersion::getFailureCategory, null)
                .set(PresaleReportVersion::getIsDegraded, false)
                .set(PresaleReportVersion::getDegradedPlatforms, null);
        versionMapper.update(null, update);

        generateOrchestrator.triggerGenerate(version.getId(), user.getId(), accessService.canManageCurrentUser());

        return RetryVersionResponse.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(PresaleGenerateStatus.QUEUED.name())
                .build();
    }

}
