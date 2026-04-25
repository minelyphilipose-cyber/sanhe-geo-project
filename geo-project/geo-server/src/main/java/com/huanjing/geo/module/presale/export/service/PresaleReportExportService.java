package com.huanjing.geo.module.presale.export.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.dto.PresaleExportCreateRequest;
import com.huanjing.geo.module.presale.export.dto.PresaleExportResponse;
import com.huanjing.geo.module.presale.export.dto.PresalePrintRenderResponse;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresaleReportExportService {
    private static final String PERM_VIEW = "presale.report.view";
    private static final String DEFAULT_PROFILE = "PDF_A4_DPR2";

    private final PresaleReportExportMapper exportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final PresaleAccessService accessService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final PresaleExportProperties properties;
    private final PresaleExportStorageService storageService;
    private final PresaleRenderTokenService renderTokenService;

    @Transactional
    public PresaleExportResponse create(Long reportId, PresaleExportCreateRequest req) {
        currentUserService.ensurePermission(PERM_VIEW);
        Long userId = currentUserService.requireCurrentUser().getId();
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        PresaleReportVersion version = versionMapper.selectById(req.getVersionId());
        if (version == null || !reportId.equals(version.getReportId())) {
            throw new BizException(404, "Presale version not found");
        }
        if (!PresaleGenerateStatus.DONE.name().equals(version.getGenerationStatus())) {
            throw new BizException(409, "Presale version is not ready for export");
        }

        PresaleReportExport active = exportMapper.selectActiveByUserReportVersion(userId, reportId, version.getId());
        if (active != null) {
            return toResponse(active).toBuilder()
                    .runningExportId(active.getId())
                    .runningStatus(active.getStatus())
                    .build();
        }

        String exportProfile = StringUtils.hasText(req.getExportProfile()) ? req.getExportProfile() : DEFAULT_PROFILE;
        String editableHash = StringUtils.hasText(req.getEditableContentHash())
                ? req.getEditableContentHash()
                : sha256(version.getEditableContentJson());
        String baseKey = reportId + ":" + version.getId() + ":" + editableHash + ":" + exportProfile;
        String idempotencyKey = Boolean.TRUE.equals(req.getForceRefresh())
                ? baseKey + ":force:" + System.currentTimeMillis() + ":" + UUID.randomUUID()
                : baseKey;

        if (!Boolean.TRUE.equals(req.getForceRefresh())) {
            PresaleReportExport existing = exportMapper.selectByIdempotencyKeyForUpdate(baseKey);
            if (existing != null) {
                if (PresaleExportStatuses.SUCCESS.equals(existing.getStatus())
                        && existing.getExpireAt() != null
                        && existing.getExpireAt().isBefore(LocalDateTime.now())) {
                    String archivedKey = baseKey + ":expired:" + System.currentTimeMillis();
                    exportMapper.archiveExpiredIdempotencyKey(existing.getId(), archivedKey);
                } else {
                    return toResponse(existing);
                }
            }
        }

        ReportDetailVO snapshot = buildSnapshot(report, version);
        String snapshotJson = writeJson(snapshot);
        PresaleReportExport task = new PresaleReportExport();
        task.setReportId(report.getId());
        task.setVersionId(version.getId());
        task.setIdempotencyKey(idempotencyKey);
        task.setExportProfile(exportProfile);
        task.setFileFormat("PDF");
        task.setStatus(PresaleExportStatuses.PENDING);
        task.setRetryCount(0);
        task.setCancelRequested(false);
        task.setTriggerUserId(userId);
        task.setTriggerAt(LocalDateTime.now());
        task.setExpireAt(LocalDateTime.now().plusDays(properties.getStorage().getExpireDays()));
        task.setSnapshotStorageType("INLINE");
        task.setSnapshotJson(snapshotJson);
        try {
            exportMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            PresaleReportExport concurrent = exportMapper.selectByIdempotencyKeyForUpdate(idempotencyKey);
            if (concurrent != null) {
                return toResponse(concurrent);
            }
            throw ex;
        }

        byte[] snapshotBytes = snapshotJson.getBytes(StandardCharsets.UTF_8);
        if (snapshotBytes.length > properties.getStorage().getInlineSnapshotMaxBytes()) {
            String snapshotKey = snapshotKey(task.getId());
            storageService.uploadSnapshot(snapshotBytes, snapshotKey);
            task.setSnapshotStorageType("OBJECT");
            task.setSnapshotJson(null);
            task.setSnapshotKey(snapshotKey);
            exportMapper.updateById(task);
        }
        return toResponse(task);
    }

    public PresaleExportResponse get(Long reportId, Long exportId) {
        currentUserService.ensurePermission(PERM_VIEW);
        accessService.requireReportWithAccess(reportId);
        PresaleReportExport task = requireExport(reportId, exportId);
        return toResponse(task);
    }

    public String downloadUrl(Long reportId, Long exportId) {
        currentUserService.ensurePermission(PERM_VIEW);
        accessService.requireReportWithAccess(reportId);
        PresaleReportExport task = requireExport(reportId, exportId);
        if (!PresaleExportStatuses.SUCCESS.equals(task.getStatus())) {
            throw new BizException(409, "Presale export is not successful");
        }
        if (task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(410, "Presale export file expired");
        }
        if (!StringUtils.hasText(task.getFileKey())) {
            throw new BizException(404, "Presale export file not found");
        }
        return storageService.presignedDownloadUrl(task.getFileKey());
    }

    @Transactional
    public PresaleExportResponse retry(Long reportId, Long exportId) {
        currentUserService.ensurePermission(PERM_VIEW);
        accessService.requireReportWithAccess(reportId);
        PresaleReportExport task = exportMapper.selectByIdForUpdate(exportId);
        if (task == null || !reportId.equals(task.getReportId())) {
            throw new BizException(404, "Presale export not found");
        }
        if (!PresaleExportStatuses.FAILED.equals(task.getStatus())) {
            throw new BizException(409, "Presale export status is not retryable");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxCount = properties.getRetry().getMaxCount();
        if (retryCount >= maxCount) {
            throw new BizException(409, "Presale export retry limit exceeded: " + maxCount);
        }
        String previousError = task.getErrorMsg();
        task.setStatus(PresaleExportStatuses.PENDING);
        task.setRetryCount(retryCount + 1);
        task.setErrorMsg(null);
        task.setCancelRequested(false);
        task.setRenderTokenId(null);
        task.setWorkerId(null);
        task.setUpdatedAt(LocalDateTime.now());
        task.setMetricsJson(appendRetryHistory(task, "RETRY", previousError));
        exportMapper.updateById(task);
        return toResponse(task);
    }

    public PresalePrintRenderResponse getRenderPayload(String renderToken) {
        PresaleRenderTokenService.TokenPayload token = renderTokenService.resolve(renderToken);
        if (token == null) {
            throw new BizException(401, "Invalid or expired render token");
        }
        PresaleReportExport task = exportMapper.selectById(token.getExportId());
        if (task == null || !PresaleExportStatuses.RUNNING.equals(task.getStatus())) {
            throw new BizException(409, "Presale export is not renderable");
        }
        Object snapshot = readSnapshot(task);
        return PresalePrintRenderResponse.builder()
                .exportId(task.getId())
                .reportId(task.getReportId())
                .versionId(task.getVersionId())
                .snapshot(snapshot)
                .renderProfile(PresalePrintRenderResponse.RenderProfile.builder()
                        .deviceScaleFactor(properties.getBrowser().getDeviceScaleFactor())
                        .pageFormat("A4")
                        .expectedPages(18)
                        .build())
                .build();
    }

    public PresaleReportExport requireExport(Long reportId, Long exportId) {
        PresaleReportExport task = exportMapper.selectById(exportId);
        if (task == null || !reportId.equals(task.getReportId())) {
            throw new BizException(404, "Presale export not found");
        }
        return task;
    }

    public Object readSnapshot(PresaleReportExport task) {
        try {
            String raw = "OBJECT".equals(task.getSnapshotStorageType())
                    ? new String(storageService.readObject(task.getSnapshotKey()), StandardCharsets.UTF_8)
                    : task.getSnapshotJson();
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            throw new BizException(500, "Read presale export snapshot failed");
        }
    }

    public PresaleExportResponse toResponse(PresaleReportExport task) {
        return PresaleExportResponse.builder()
                .exportId(task.getId())
                .reportId(task.getReportId())
                .versionId(task.getVersionId())
                .status(task.getStatus())
                .idempotencyKey(task.getIdempotencyKey())
                .errorMsg(task.getErrorMsg())
                .retryCount(task.getRetryCount())
                .fileKey(task.getFileKey())
                .fileSize(task.getFileSize())
                .filePages(task.getFilePages())
                .expireAt(task.getExpireAt())
                .build();
    }

    private ReportDetailVO buildSnapshot(PresaleReport report, PresaleReportVersion version) {
        return ReportDetailVO.builder()
                .reportId(report.getId())
                .brandName(report.getBrandName())
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .createdAt(report.getCreatedAt())
                .version(toVersionMeta(version))
                .rawSnapshotJson(version.getRawSnapshotJson())
                .computedSnapshotJson(version.getComputedSnapshotJson())
                .editableContentJson(version.getEditableContentJson())
                .build();
    }

    private ReportVersionMetaVO toVersionMeta(PresaleReportVersion version) {
        return ReportVersionMetaVO.builder()
                .versionId(version.getId())
                .versionNo(version.getVersionNo())
                .generationStatus(version.getGenerationStatus())
                .generationStage(version.getGenerationStage())
                .totalLlmCalls(version.getTotalLlmCalls())
                .completedLlmCalls(version.getCompletedLlmCalls())
                .batch1TotalCalls(version.getBatch1TotalCalls())
                .batch1CompletedCalls(version.getBatch1CompletedCalls())
                .batch2TotalCalls(version.getBatch2TotalCalls())
                .batch2CompletedCalls(version.getBatch2CompletedCalls())
                .extractedCompetitorCount(version.getExtractedCompetitorCount())
                .isDegraded(Boolean.TRUE.equals(version.getIsDegraded()))
                .degradedPlatforms(null)
                .failureReason(version.getFailureReason())
                .frozen(version.getFrozenAt() != null)
                .frozenAt(version.getFrozenAt())
                .contentUpdatedAt(version.getContentUpdatedAt())
                .exportSuccessCount(version.getExportSuccessCount())
                .exportSuccessAt(version.getExportSuccessAt())
                .createdAt(version.getCreatedAt())
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(500, "Build presale export snapshot failed");
        }
    }

    private String snapshotKey(Long exportId) {
        return "presale/exports/" + exportId + "/snapshot.json";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String appendRetryHistory(PresaleReportExport task, String code, String message) {
        String safeMessage = message == null ? "" : message.replace("\"", "'");
        return "{\"retry_history\":[{\"error_code\":\"" + code + "\",\"error_msg\":\"" + safeMessage + "\"}]}";
    }
}
