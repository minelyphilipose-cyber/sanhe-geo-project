package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3Defaults;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本读取服务。职责单一:取详情 / 取最新版本元信息(进度页轮询)。
 */
@Service
public class PresaleReportVersionService {
    private static final String PERM_VIEW = "presale.report.view";

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleAccessService accessService;
    private final CurrentUserService currentUserService;
    private final PresaleL3Defaults l3Defaults;
    private final ObjectMapper objectMapper;

    public PresaleReportVersionService(PresaleReportVersionMapper versionMapper,
                                       PresaleAccessService accessService,
                                       CurrentUserService currentUserService,
                                       PresaleL3Defaults l3Defaults,
                                       ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
        this.l3Defaults = l3Defaults;
        this.objectMapper = objectMapper;
    }

    /**
     * 按 reportId + versionNo 取详情。versionNo 为 null 时取 latest。
     */
    public ReportDetailVO getDetail(Long reportId, Integer versionNo) {
        currentUserService.ensurePermission(PERM_VIEW);
        PresaleReport report = accessService.requireReportWithAccess(reportId);

        PresaleReportVersion version;
        if (versionNo == null) {
            version = report.getLatestVersionId() == null ? null
                    : versionMapper.selectById(report.getLatestVersionId());
        } else {
            LambdaQueryWrapper<PresaleReportVersion> q = new LambdaQueryWrapper<>();
            q.eq(PresaleReportVersion::getReportId, reportId)
                    .eq(PresaleReportVersion::getVersionNo, versionNo);
            version = versionMapper.selectOne(q);
        }
        if (version == null) {
            throw new IllegalArgumentException("Version not found for report=" + reportId
                    + ", versionNo=" + versionNo);
        }

        return ReportDetailVO.builder()
                .reportId(report.getId())
                .brandName(report.getBrandName())
                .brandFormerNames(parseJsonStringArray(report.getBrandFormerNames()))
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .representedBrands(parseJsonStringArray(report.getRepresentedBrands()))
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .createdAt(report.getCreatedAt())
                .version(PresaleReportService.toVersionMeta(version))
                .rawSnapshotJson(version.getRawSnapshotJson())
                .computedSnapshotJson(version.getComputedSnapshotJson())
                .editableContentJson(l3Defaults.normalizeJson(
                        version.getEditableContentJson(),
                        version.getRawSnapshotJson(),
                        version.getComputedSnapshotJson()))
                .editableFieldMeta(l3Defaults.fieldMeta())
                .build();
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

    /**
     * 进度页轮询:只取最新版本元信息,不返回快照 JSON(减小响应体积)。
     */
    public ReportVersionMetaVO getLatestVersionMeta(Long reportId) {
        currentUserService.ensurePermission(PERM_VIEW);
        PresaleReport report = accessService.requireReportWithAccess(reportId);
        if (report == null || report.getLatestVersionId() == null) {
            throw new IllegalArgumentException("Report or latest version not found: " + reportId);
        }
        PresaleReportVersion v = versionMapper.selectById(report.getLatestVersionId());
        if (v == null) {
            throw new IllegalArgumentException("Latest version not found: " + report.getLatestVersionId());
        }
        return PresaleReportService.toVersionMeta(v);
    }
}
