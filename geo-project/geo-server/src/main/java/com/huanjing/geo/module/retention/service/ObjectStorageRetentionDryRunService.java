package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.module.retention.dto.ObjectStorageOrphanCandidateVO;
import com.huanjing.geo.module.retention.dto.ObjectStorageReferenceColumnVO;
import com.huanjing.geo.module.retention.dto.ObjectStorageRetentionDryRunRequest;
import com.huanjing.geo.module.retention.dto.ObjectStorageRetentionDryRunResponse;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ObjectStorageRetentionDryRunService {

    private static final int DEFAULT_SAFETY_AGE_HOURS = 24;
    private static final int DEFAULT_LIMIT_PER_PREFIX = 100;
    private static final int MAX_LIMIT_PER_PREFIX = 1_000;

    private static final List<ObjectKeyColumn> OBJECT_KEY_COLUMNS = List.of(
            new ObjectKeyColumn("article_draft_version", "content_object_key", "archive/article/",
                    "archived article markdown body"),
            new ObjectKeyColumn("report_period_freeze", "snapshot_object_key", "retention/freeze/report-period/",
                    "frozen poll detail report snapshot")
    );

    private final ObjectStorageService objectStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;

    public ObjectStorageRetentionDryRunResponse dryRun(ObjectStorageRetentionDryRunRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        int safetyAgeHours = normalizePositive(request.getSafetyAgeHours(), DEFAULT_SAFETY_AGE_HOURS);
        int limitPerPrefix = normalizeLimit(request.getLimitPerPrefix());
        List<String> prefixes = resolvePrefixes(request.getPrefix());

        ObjectStorageRetentionDryRunResponse response = new ObjectStorageRetentionDryRunResponse();
        response.setSafetyAgeHours(safetyAgeHours);
        response.setLimitPerPrefix(limitPerPrefix);
        response.setScannedPrefixes(prefixes);
        response.setReferenceColumns(referenceColumnVOs());

        Long runId = auditService.startRun("object_storage_orphan", "dry_run", null, null, startMetrics(response));
        response.setRetentionRunId(runId);
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusHours(safetyAgeHours);
            for (String prefix : prefixes) {
                scanPrefix(response, prefix, cutoff, limitPerPrefix);
            }
            summarize(response);
            auditService.finishRun(runId, "succeeded", response.getCandidateCount(), response.getCandidateBytes(),
                    0, 0, metrics(response), null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(), response.getCandidateBytes(),
                    0, 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private void scanPrefix(ObjectStorageRetentionDryRunResponse response,
                            String prefix,
                            OffsetDateTime cutoff,
                            int limitPerPrefix) {
        List<ObjectStorageService.ObjectItem> items = objectStorageService.listObjects(prefix, limitPerPrefix);
        response.setScannedObjects(response.getScannedObjects() + items.size());
        for (ObjectStorageService.ObjectItem item : items) {
            boolean oldEnough = item.lastModified() != null && item.lastModified().isBefore(cutoff);
            if (!oldEnough) {
                continue;
            }
            int referenceCount = liveReferenceCount(item.objectKey());
            if (referenceCount > 0) {
                continue;
            }
            ObjectStorageOrphanCandidateVO candidate = new ObjectStorageOrphanCandidateVO();
            candidate.setObjectKey(item.objectKey());
            candidate.setPrefix(prefix);
            candidate.setSizeBytes(item.size());
            candidate.setLastModified(item.lastModified());
            candidate.setOlderThanSafetyWindow(true);
            candidate.setLiveReferenceCount(referenceCount);
            response.getCandidates().add(candidate);
        }
    }

    private int liveReferenceCount(String objectKey) {
        int count = 0;
        for (ObjectKeyColumn column : OBJECT_KEY_COLUMNS) {
            Integer references = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                      FROM %s
                     WHERE %s = ?
                    """.formatted(column.tableName(), column.columnName()), Integer.class, objectKey);
            count += references == null ? 0 : references;
        }
        return count;
    }

    private List<String> resolvePrefixes(String requestedPrefix) {
        List<String> managed = OBJECT_KEY_COLUMNS.stream()
                .map(ObjectKeyColumn::managedPrefix)
                .distinct()
                .toList();
        if (!StringUtils.hasText(requestedPrefix)) {
            return managed;
        }
        String prefix = requestedPrefix.trim();
        boolean allowed = managed.stream().anyMatch(prefix::startsWith);
        if (!allowed) {
            throw new BizException(400, "prefix is outside managed data-retention object prefixes");
        }
        return List.of(prefix);
    }

    private List<ObjectStorageReferenceColumnVO> referenceColumnVOs() {
        return OBJECT_KEY_COLUMNS.stream()
                .map(column -> new ObjectStorageReferenceColumnVO(
                        column.tableName(),
                        column.columnName(),
                        column.managedPrefix(),
                        column.note()))
                .toList();
    }

    private void summarize(ObjectStorageRetentionDryRunResponse response) {
        response.setCandidateCount(response.getCandidates().size());
        response.setCandidateBytes(response.getCandidates().stream()
                .mapToLong(candidate -> candidate.getSizeBytes() == null ? 0L : candidate.getSizeBytes())
                .sum());
    }

    private Map<String, Object> startMetrics(ObjectStorageRetentionDryRunResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("safetyAgeHours", response.getSafetyAgeHours());
        metrics.put("limitPerPrefix", response.getLimitPerPrefix());
        metrics.put("scannedPrefixes", response.getScannedPrefixes());
        metrics.put("referenceColumns", response.getReferenceColumns().stream()
                .map(column -> column.getTableName() + "." + column.getColumnName())
                .toList());
        return metrics;
    }

    private Map<String, Object> metrics(ObjectStorageRetentionDryRunResponse response) {
        Map<String, Object> metrics = startMetrics(response);
        metrics.put("scannedObjects", response.getScannedObjects());
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("candidateBytes", response.getCandidateBytes());
        return metrics;
    }

    private int normalizePositive(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT_PER_PREFIX : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT_PER_PREFIX;
        }
        return Math.min(value, MAX_LIMIT_PER_PREFIX);
    }

    private record ObjectKeyColumn(String tableName, String columnName, String managedPrefix, String note) {
    }
}
