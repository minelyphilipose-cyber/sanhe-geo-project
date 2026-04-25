package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleExportCleanupService {
    /*
     * cleanup-max-retry-count 设计说明：
     * 此计数 += 1 每“清理周期”（默认每天一次），不是每次抖动。
     * maxRetryCount=5 ≈ 5 天连续清理失败后强制永久标记。
     * 设计目的：避免短期 MinIO 抖动导致 file_purged_at 被错误置位，
     * 让用户的 PDF 永远找不回。
     * 运维如发现某行连续 3 次清理失败（cleanup_retry_count=3），
     * 应介入排查 MinIO 健康。
     */
    private static final String CLEANUP_RETRY_COUNT = "cleanup_retry_count";

    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportStorageService storageService;
    private final PresaleExportProperties properties;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

    public CleanupRunResult cleanupExpiredOnce() {
        long started = System.nanoTime();
        int limit = Math.max(1, properties.getCleanup().getBatchSize());
        List<PresaleReportExport> candidates = exportMapper.selectExpiredForCleanup(LocalDateTime.now(), limit);
        int purged = 0;
        int failed = 0;
        for (PresaleReportExport task : candidates) {
            CleanupRowResult result = cleanupOne(task);
            if (result.purged()) {
                purged++;
            } else {
                failed++;
            }
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Presale export cleanup finished: candidates={}, purged={}, failed={}, elapsedMs={}",
                candidates.size(), purged, failed, elapsedMs);
        return new CleanupRunResult(candidates.size(), purged, failed, elapsedMs);
    }

    private CleanupRowResult cleanupOne(PresaleReportExport task) {
        List<String> pendingKeys = new ArrayList<>();
        deleteObject(task.getFileKey(), pendingKeys);
        if ("OBJECT".equals(task.getSnapshotStorageType())) {
            deleteObject(task.getSnapshotKey(), pendingKeys);
        }
        deletePrefix(debugPrefix(task.getId()), pendingKeys);

        if (pendingKeys.isEmpty()) {
            exportMapper.markFilePurged(task.getId());
            log.info("Presale export artifacts purged: exportId={}", task.getId());
            return new CleanupRowResult(true, List.of());
        }

        int retryCount = metricsJsonHelper.intValue(task.getMetricsJson(), CLEANUP_RETRY_COUNT) + 1;
        task.setMetricsJson(metricsJsonHelper.putInt(task.getMetricsJson(), CLEANUP_RETRY_COUNT, retryCount));
        if (retryCount >= properties.getCleanup().getMaxRetryCount()) {
            task.setMetricsJson(metricsJsonHelper.putCleanupFailure(task.getMetricsJson(), retryCount, pendingKeys));
            task.setFilePurgedAt(LocalDateTime.now());
            exportMapper.updateById(task);
            log.warn("Presale export cleanup permanently marked after retries: exportId={}, retryCount={}, pendingKeys={}",
                    task.getId(), retryCount, pendingKeys);
            return new CleanupRowResult(true, pendingKeys);
        }

        exportMapper.updateById(task);
        log.warn("Presale export cleanup failed; will retry: exportId={}, retryCount={}, pendingKeys={}",
                task.getId(), retryCount, pendingKeys);
        return new CleanupRowResult(false, pendingKeys);
    }

    private void deleteObject(String objectKey, List<String> pendingKeys) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            storageService.removeStrict(objectKey);
        } catch (Exception ex) {
            pendingKeys.add(objectKey);
            log.warn("Delete presale export object failed: objectKey={}", objectKey, ex);
        }
    }

    private void deletePrefix(String prefix, List<String> pendingKeys) {
        try {
            storageService.removePrefixStrict(prefix);
        } catch (Exception ex) {
            pendingKeys.add(prefix);
            log.warn("Delete presale export object prefix failed: prefix={}", prefix, ex);
        }
    }

    private String debugPrefix(Long exportId) {
        return "presale/exports/" + exportId + "/debug/";
    }

    @Value
    public static class CleanupRunResult {
        int candidates;
        int purged;
        int failed;
        long elapsedMs;
    }

    private record CleanupRowResult(boolean purged, List<String> pendingKeys) {
    }
}
