package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresaleReportExportHeartbeatService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportWorkerIdentity workerIdentity;

    public void heartbeat(Long exportId) {
        exportMapper.heartbeat(exportId, workerIdentity.workerId());
    }
}
