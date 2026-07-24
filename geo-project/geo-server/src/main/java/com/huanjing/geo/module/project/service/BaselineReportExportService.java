package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.service.PresaleExportStorageService;
import com.huanjing.geo.module.project.dto.BaselineCanonicalReportVO;
import com.huanjing.geo.module.project.dto.BaselinePrintRenderResponse;
import com.huanjing.geo.module.project.dto.BaselineReportExportResponse;
import com.huanjing.geo.module.project.entity.BaselineReportExport;
import com.huanjing.geo.module.project.mapper.BaselineReportExportMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BaselineReportExportService {
    private static final int CODE_EXPORT_IN_PROGRESS = 40901;
    private static final String DEFAULT_PROFILE = "PDF_A4_DPR2";

    private final BaselineReportExportMapper exportMapper;
    private final BaselineCanonicalAggregateService canonicalAggregateService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final PresaleExportProperties properties;
    private final PresaleExportStorageService storageService;
    private final BaselineRenderTokenService renderTokenService;

    @Transactional
    public BaselineReportExportResponse create(Long projectId, Long baselineId, boolean forceRefresh) {
        SysUser operator = currentUserService.requireCurrentUser();
        BaselineCanonicalReportVO canonical = canonicalAggregateService.latest(projectId, baselineId);
        String canonicalJson = canonical.getCanonicalJson();
        if (canonicalJson == null || canonicalJson.isBlank()) {
            throw new BizException(409, "Baseline canonical is not ready for export");
        }
        BaselineReportExport active = exportMapper.selectActiveByUserBaseline(operator.getId(), projectId, baselineId);
        if (active != null) {
            throw new BizException(CODE_EXPORT_IN_PROGRESS, "BASELINE_EXPORT_IN_PROGRESS", HttpStatus.CONFLICT.value(),
                    Map.of("runningExportId", active.getId(), "runningStatus", active.getStatus()));
        }

        String baseKey = projectId + ":" + baselineId + ":" + sha256(canonicalJson) + ":" + DEFAULT_PROFILE;
        String idempotencyKey = forceRefresh ? baseKey + ":force:" + System.currentTimeMillis() : baseKey;
        if (!forceRefresh) {
            BaselineReportExport existing = exportMapper.selectByIdempotencyKeyForUpdate(baseKey);
            if (existing != null) {
                return toResponse(existing);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        BaselineReportExport task = new BaselineReportExport();
        task.setProjectId(projectId);
        task.setBaselineId(baselineId);
        task.setIdempotencyKey(idempotencyKey);
        task.setExportProfile(DEFAULT_PROFILE);
        task.setFileFormat("PDF");
        task.setStatus(BaselineReportExportStatuses.PENDING);
        task.setSnapshotJson(canonicalJson);
        task.setTriggerUserId(operator.getId());
        task.setTriggerAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            exportMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            BaselineReportExport concurrent = exportMapper.selectByIdempotencyKeyForUpdate(idempotencyKey);
            if (concurrent != null) {
                return toResponse(concurrent);
            }
            throw ex;
        }
        return toResponse(task);
    }

    public BaselineReportExportResponse get(Long projectId, Long baselineId, Long exportId) {
        canonicalAggregateService.latest(projectId, baselineId);
        return toResponse(requireExport(projectId, baselineId, exportId));
    }

    public byte[] downloadBytes(Long projectId, Long baselineId, Long exportId) {
        canonicalAggregateService.latest(projectId, baselineId);
        BaselineReportExport task = requireExport(projectId, baselineId, exportId);
        if (!BaselineReportExportStatuses.SUCCESS.equals(task.getStatus()) || task.getFileKey() == null) {
            throw new BizException(409, "Baseline export is not successful");
        }
        return storageService.readObject(task.getFileKey());
    }

    public String downloadFileName(Long projectId, Long baselineId, Long exportId) {
        requireExport(projectId, baselineId, exportId);
        return "基线监测报告_" + baselineId + "-" + exportId + ".pdf";
    }

    public BaselinePrintRenderResponse getRenderPayload(String renderToken) {
        BaselineRenderTokenService.TokenPayload token = renderTokenService.resolve(renderToken);
        if (token == null) {
            throw new BizException(401, "Invalid or expired baseline render token");
        }
        BaselineReportExport task = exportMapper.selectById(token.getExportId());
        if (task == null || !BaselineReportExportStatuses.RUNNING.equals(task.getStatus())) {
            throw new BizException(409, "Baseline export is not renderable");
        }
        try {
            Object canonical = objectMapper.readValue(task.getSnapshotJson(), Object.class);
            return BaselinePrintRenderResponse.builder()
                    .exportId(task.getId())
                    .projectId(task.getProjectId())
                    .baselineId(task.getBaselineId())
                    .canonical(canonical)
                    .renderProfile(BaselinePrintRenderResponse.RenderProfile.builder()
                            .deviceScaleFactor(properties.getBrowser().getDeviceScaleFactor())
                            .pageFormat("A4")
                            .build())
                    .build();
        } catch (Exception ex) {
            throw new BizException(500, "Read baseline export canonical failed");
        }
    }

    BaselineReportExport requireExport(Long projectId, Long baselineId, Long exportId) {
        BaselineReportExport task = exportMapper.selectById(exportId);
        if (task == null || !projectId.equals(task.getProjectId()) || !baselineId.equals(task.getBaselineId())) {
            throw new BizException(404, "Baseline export not found");
        }
        return task;
    }

    BaselineReportExport latest(Long projectId, Long baselineId) {
        return exportMapper.selectOne(new LambdaQueryWrapper<BaselineReportExport>()
                .eq(BaselineReportExport::getProjectId, projectId)
                .eq(BaselineReportExport::getBaselineId, baselineId)
                .orderByDesc(BaselineReportExport::getId)
                .last("LIMIT 1"));
    }

    BaselineReportExportResponse toResponse(BaselineReportExport task) {
        if (task == null) {
            return null;
        }
        return BaselineReportExportResponse.builder()
                .exportId(task.getId())
                .baselineId(task.getBaselineId())
                .projectId(task.getProjectId())
                .status(task.getStatus())
                .idempotencyKey(task.getIdempotencyKey())
                .errorMsg(task.getErrorMsg())
                .fileKey(task.getFileKey())
                .fileSize(task.getFileSize())
                .filePages(task.getFilePages())
                .triggerAt(task.getTriggerAt())
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Compute baseline export hash failed", ex);
        }
    }
}
