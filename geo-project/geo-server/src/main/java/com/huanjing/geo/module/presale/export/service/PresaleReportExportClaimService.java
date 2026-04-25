package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PresaleReportExportClaimService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportWorkerIdentity workerIdentity;

    @Transactional
    public Optional<PresaleReportExport> claimOne() {
        PresaleReportExport task = exportMapper.selectOnePendingForUpdateSkipLocked();
        if (task == null) {
            return Optional.empty();
        }
        int updated = exportMapper.markClaimed(task.getId(), workerIdentity.workerId());
        if (updated != 1) {
            return Optional.empty();
        }
        task.setStatus(PresaleExportStatuses.RUNNING);
        task.setWorkerId(workerIdentity.workerId());
        return Optional.of(task);
    }
}
