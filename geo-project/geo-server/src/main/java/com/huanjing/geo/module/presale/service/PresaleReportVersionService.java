package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.presale.dto.response.ReportDetailVO;
import com.huanjing.geo.module.presale.dto.response.ReportVersionMetaVO;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import org.springframework.stereotype.Service;

/**
 * 版本读取服务。职责单一:取详情 / 取最新版本元信息(进度页轮询)。
 */
@Service
public class PresaleReportVersionService {

    private final PresaleReportMapper reportMapper;
    private final PresaleReportVersionMapper versionMapper;

    public PresaleReportVersionService(PresaleReportMapper reportMapper,
                                       PresaleReportVersionMapper versionMapper) {
        this.reportMapper = reportMapper;
        this.versionMapper = versionMapper;
    }

    /**
     * 按 reportId + versionNo 取详情。versionNo 为 null 时取 latest。
     */
    public ReportDetailVO getDetail(Long reportId, Integer versionNo) {
        PresaleReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("Report not found: " + reportId);
        }

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
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .createdAt(report.getCreatedAt())
                .version(PresaleReportService.toVersionMeta(version))
                .rawSnapshotJson(version.getRawSnapshotJson())
                .computedSnapshotJson(version.getComputedSnapshotJson())
                .editableContentJson(version.getEditableContentJson())
                .build();
    }

    /**
     * 进度页轮询:只取最新版本元信息,不返回快照 JSON(减小响应体积)。
     */
    public ReportVersionMetaVO getLatestVersionMeta(Long reportId) {
        PresaleReport report = reportMapper.selectById(reportId);
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
