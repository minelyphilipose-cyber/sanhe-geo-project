package com.huanjing.geo.module.presale.poc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.generate.PresaleGenerateStatus;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * POC ONLY.
 *
 * <p>Dev-only endpoint for local Playwright PDF experiments. It deliberately
 * bypasses the regular admin auth flow and must not be used as a production
 * export API.</p>
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev/presale-print-poc")
@RequiredArgsConstructor
public class PresalePrintPocController {

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/{reportId}")
    public R<ReportDetailVO> getPrintSnapshot(@PathVariable Long reportId) {
        PresaleReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(404, "Presale report not found");
        }

        PresaleReportVersion version = findPrintableVersion(report);
        if (version == null) {
            throw new BizException(404, "DONE presale report version not found");
        }
        if (!StringUtils.hasText(version.getRawSnapshotJson())
                || !StringUtils.hasText(version.getComputedSnapshotJson())
                || !StringUtils.hasText(version.getEditableContentJson())) {
            throw new BizException(409, "Printable presale snapshot is incomplete");
        }

        return R.ok(ReportDetailVO.builder()
                .reportId(report.getId())
                .brandName(report.getBrandName())
                .brandFormerNames(parseJsonStringArray(report.getBrandFormerNames()))
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .createdAt(report.getCreatedAt())
                .version(toMeta(version))
                .rawSnapshotJson(version.getRawSnapshotJson())
                .computedSnapshotJson(version.getComputedSnapshotJson())
                .editableContentJson(version.getEditableContentJson())
                .build());
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

    private PresaleReportVersion findPrintableVersion(PresaleReport report) {
        if (report.getLatestVersionId() != null) {
            PresaleReportVersion latest = versionMapper.selectById(report.getLatestVersionId());
            if (latest != null
                    && report.getId().equals(latest.getReportId())
                    && PresaleGenerateStatus.DONE.name().equals(latest.getGenerationStatus())) {
                return latest;
            }
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<PresaleReportVersion>()
                .eq(PresaleReportVersion::getReportId, report.getId())
                .eq(PresaleReportVersion::getGenerationStatus, PresaleGenerateStatus.DONE.name())
                .orderByDesc(PresaleReportVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    private ReportVersionMetaVO toMeta(PresaleReportVersion version) {
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
}
