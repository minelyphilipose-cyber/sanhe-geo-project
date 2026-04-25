package com.huanjing.geo.module.presale.export.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresaleExportCancellationRegistry {
    /*
     * In-memory only. If the app restarts after a cancel request, DB status is already CANCELED.
     * Startup recovery scans RUNNING only and workers claim PENDING only, so canceled tasks are not resumed.
     */
    private final Set<Long> canceledExportIds = ConcurrentHashMap.newKeySet();

    public void cancel(Long exportId) {
        if (exportId != null) {
            canceledExportIds.add(exportId);
        }
    }

    public boolean isCanceled(Long exportId) {
        return exportId != null && canceledExportIds.contains(exportId);
    }

    public void clear(Long exportId) {
        if (exportId != null) {
            canceledExportIds.remove(exportId);
        }
    }
}
