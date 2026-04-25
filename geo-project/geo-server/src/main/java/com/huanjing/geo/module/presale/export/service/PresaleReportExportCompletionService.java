package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PresaleReportExportCompletionService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

    @Transactional
    public boolean markSuccessAndIncrementVersion(Long exportId,
                                                   String fileKey,
                                                   long fileSize,
                                                   int filePages,
                                                   String metricsJson) {
        PresaleReportExport latest = exportMapper.selectByIdForUpdate(exportId);
        if (latest == null || PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            return false;
        }
        latest.setStatus(PresaleExportStatuses.SUCCESS);
        latest.setFileKey(fileKey);
        latest.setFileSize(fileSize);
        latest.setFilePages(filePages);
        latest.setErrorMsg(null);
        latest.setMetricsJson(metricsJsonHelper.mergeRenderMetrics(latest.getMetricsJson(), metricsJson));
        latest.setRenderTokenId(null);
        latest.setUpdatedAt(LocalDateTime.now());
        exportMapper.updateById(latest);
        exportMapper.clearRenderToken(exportId);
        exportMapper.clearErrorMsg(exportId);
        exportMapper.incrementVersionExportSuccess(latest.getVersionId());
        return true;
    }
}
