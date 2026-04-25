package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresaleReportExportCancelDbService {
    private final PresaleReportExportMapper exportMapper;

    @Transactional
    public CancelResult cancelInDb(Long reportId, Long exportId) {
        PresaleReportExport task = exportMapper.selectByIdForUpdate(exportId);
        if (task == null || !reportId.equals(task.getReportId())) {
            throw new BizException(404, "Presale export not found");
        }
        if (PresaleExportStatuses.PENDING.equals(task.getStatus())
                || PresaleExportStatuses.RUNNING.equals(task.getStatus())) {
            String renderTokenId = task.getRenderTokenId();
            task.setStatus(PresaleExportStatuses.CANCELED);
            task.setCancelRequested(true);
            task.setRenderTokenId(null);
            exportMapper.updateById(task);
            return new CancelResult(task, renderTokenId);
        }
        throw new BizException(409, "Presale export status is not cancelable");
    }

    public record CancelResult(PresaleReportExport task, String renderTokenIdToInvalidate) {
    }
}
