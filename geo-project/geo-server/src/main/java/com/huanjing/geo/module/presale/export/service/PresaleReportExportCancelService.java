package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.export.dto.PresaleExportResponse;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportExportCancelService {

    private final PresaleAccessService accessService;
    private final PresaleReportExportCancelDbService cancelDbService;
    private final PresaleExportCancellationRegistry cancellationRegistry;
    private final PresaleRenderTokenService renderTokenService;
    private final PresaleReportExportService exportService;

    public PresaleExportResponse cancel(Long reportId, Long exportId) {
        accessService.requireReportWithAccess(reportId);

        // 阶段 1: DB 事务先落 CANCELED。DB 是真理之源,用户可立即发起新导出。
        PresaleReportExportCancelDbService.CancelResult cancelResult = cancelDbService.cancelInDb(reportId, exportId);
        PresaleReportExport canceled = cancelResult.task();
        if (!PresaleExportStatuses.CANCELED.equals(canceled.getStatus())) {
            throw new BizException(409, "Presale export status is not cancelable");
        }

        // 阶段 2: 事务外副作用。内存标志不失败;token 删除失败有 TTL 兜底。
        cancellationRegistry.cancel(exportId);
        try {
            renderTokenService.invalidate(cancelResult.renderTokenIdToInvalidate());
        } catch (Exception ex) {
            log.warn("Invalidate render token after cancel failed, exportId={}", exportId, ex);
        }
        return exportService.toResponse(canceled);
    }
}
