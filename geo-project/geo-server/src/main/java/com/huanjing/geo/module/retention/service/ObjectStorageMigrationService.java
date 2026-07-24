package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.ObjectStorageService;
import com.huanjing.geo.common.storage.StorageProperties;
import com.huanjing.geo.module.retention.dto.ObjectStorageMigrationItemVO;
import com.huanjing.geo.module.retention.dto.ObjectStorageMigrationRequest;
import com.huanjing.geo.module.retention.dto.ObjectStorageMigrationResponse;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
public class ObjectStorageMigrationService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final String UNION_STRING_CAST_TEMPLATE =
            "CAST(%s AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin";

    private final ObjectStorageService minioBackend;
    private final ObjectStorageService cosBackend;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final DataRetentionRunAuditService auditService;
    private final ObjectStorageKeyRegistry objectStorageKeyRegistry;
    private final StorageProperties storageProperties;

    public ObjectStorageMigrationService(
            @Qualifier("minioObjectStorageBackend") ObjectStorageService minioBackend,
            @Qualifier("cosObjectStorageBackend") ObjectStorageService cosBackend,
            JdbcTemplate jdbcTemplate,
            CurrentUserService currentUserService,
            DataRetentionRunAuditService auditService,
            ObjectStorageKeyRegistry objectStorageKeyRegistry,
            StorageProperties storageProperties) {
        this.minioBackend = minioBackend;
        this.cosBackend = cosBackend;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.objectStorageKeyRegistry = objectStorageKeyRegistry;
        this.storageProperties = storageProperties;
    }

    public ObjectStorageMigrationResponse migrate(ObjectStorageMigrationRequest request) {
        currentUserService.ensurePermission("dispatch.task.release");
        boolean dryRun = request.getDryRun() == null || Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun && !storageProperties.getMigration().isExecuteEnabled()) {
            throw new BizException(403, "Object storage migration execute is disabled by geo.storage.migration.execute-enabled");
        }
        int limit = normalizeLimit(request.getLimit());
        String cursor = StringUtils.hasText(request.getCursorObjectKey()) ? request.getCursorObjectKey().trim() : null;
        String prefix = StringUtils.hasText(request.getPrefix()) ? request.getPrefix().trim() : null;

        ObjectStorageMigrationResponse response = new ObjectStorageMigrationResponse();
        response.setDryRun(dryRun);
        response.setLimit(limit);
        response.setCursorObjectKey(cursor);
        response.setPrefix(prefix);

        Long runId = auditService.startRun("object_storage_migration", dryRun ? "dry_run" : "execute",
                null, null, startMetrics(response));
        response.setRetentionRunId(runId);
        try {
            List<MigrationCandidate> loaded = loadCandidates(cursor, prefix, limit + 1);
            response.setHasMore(loaded.size() > limit);
            List<MigrationCandidate> candidates = loaded.size() > limit ? loaded.subList(0, limit) : loaded;
            if (!candidates.isEmpty()) {
                response.setNextCursorObjectKey(candidates.get(candidates.size() - 1).objectKey());
            }
            for (MigrationCandidate candidate : candidates) {
                ObjectStorageMigrationItemVO item = dryRun ? dryRunOne(candidate) : migrateOne(candidate);
                response.getItems().add(item);
            }
            summarize(response);
            auditService.finishRun(runId, response.getFailedCount() > 0 ? "failed" : "succeeded",
                    response.getCandidateCount(), response.getMigratedCount(), response.getSkippedCount(),
                    response.getFailedCount() + response.getWarningCount(), metrics(response),
                    response.getFailedCount() > 0 ? "one or more objects failed" : null);
            return response;
        } catch (Exception ex) {
            auditService.finishRun(runId, "failed", response.getCandidateCount(), response.getMigratedCount(),
                    response.getSkippedCount(), response.getFailedCount() + 1, metrics(response), ex.getMessage());
            throw ex;
        }
    }

    private ObjectStorageMigrationItemVO dryRunOne(MigrationCandidate candidate) {
        ObjectStorageMigrationItemVO item = baseItem(candidate);
        item.setAction("copy_minio_to_cos");
        if ("failed".equals(item.getResult())) {
            return item;
        }
        try {
            ObjectStorageService.ObjectStat stat = minioBackend.stat(candidate.objectKey());
            item.setSourceSizeBytes(stat.size());
            item.setResult("would_copy");
            item.setMetrics(Map.of(
                    "source", "minio",
                    "target", "cos",
                    "sourceEtag", nullToEmpty(stat.etag())
            ));
        } catch (Exception ex) {
            item.setResult("failed");
            item.setErrorMessage(trimError(ex.getMessage()));
        }
        return item;
    }

    private ObjectStorageMigrationItemVO migrateOne(MigrationCandidate candidate) {
        ObjectStorageMigrationItemVO item = baseItem(candidate);
        item.setAction("copy_minio_to_cos");
        if ("failed".equals(item.getResult())) {
            return item;
        }
        try {
            byte[] sourceBytes = minioBackend.readBytes(candidate.objectKey());
            String sourceChecksum = sha256Hex(sourceBytes);
            item.setSourceSizeBytes((long) sourceBytes.length);
            if (StringUtils.hasText(candidate.expectedChecksum())
                    && !candidate.expectedChecksum().equalsIgnoreCase(sourceChecksum)) {
                item.setResult("failed");
                item.setErrorMessage("source_checksum_mismatch_with_db");
                return item;
            }

            ExistingCosObject existing = readExistingCos(candidate.objectKey());
            if (existing.exists() && sourceChecksum.equalsIgnoreCase(existing.checksum())) {
                item.setResult("skipped");
                item.setMetrics(Map.of(
                        "reason", "cos_object_already_matches",
                        "checksum", sourceChecksum,
                        "bytes", sourceBytes.length
                ));
                return item;
            }

            cosBackend.putBytes(candidate.objectKey(), sourceBytes, contentTypeForObjectKey(candidate.objectKey()));
            byte[] readback = cosBackend.readBytes(candidate.objectKey());
            String readbackChecksum = sha256Hex(readback);
            if (!sourceChecksum.equalsIgnoreCase(readbackChecksum)) {
                item.setResult("failed");
                item.setErrorMessage("cos_readback_checksum_mismatch");
                return item;
            }
            item.setResult("migrated");
            item.setMetrics(Map.of(
                    "checksum", sourceChecksum,
                    "bytes", sourceBytes.length
            ));
            return item;
        } catch (Exception ex) {
            log.warn("Object storage migration failed, objectKey={}", candidate.objectKey(), ex);
            item.setResult("failed");
            item.setErrorMessage(trimError(ex.getMessage()));
            return item;
        }
    }

    private ExistingCosObject readExistingCos(String objectKey) {
        try {
            byte[] bytes = cosBackend.readBytes(objectKey);
            return new ExistingCosObject(true, sha256Hex(bytes));
        } catch (BizException ex) {
            if (ex.getCode() == 404) {
                return new ExistingCosObject(false, null);
            }
            throw ex;
        }
    }

    private List<MigrationCandidate> loadCandidates(String cursor, String prefix, int limit) {
        List<Object> args = new ArrayList<>();
        String unionSql = unionKeySql();
        StringBuilder sql = new StringBuilder("""
                SELECT object_key,
                       MAX(expected_checksum) AS expected_checksum,
                       COUNT(DISTINCT expected_checksum) AS checksum_variant_count,
                       COUNT(1) AS reference_count
                  FROM (
                """);
        sql.append(unionSql);
        sql.append("""
                       ) keys_union
                 WHERE object_key IS NOT NULL
                   AND object_key <> ''
                """);
        if (StringUtils.hasText(cursor)) {
            sql.append("   AND object_key > ?\n");
            args.add(cursor);
        }
        if (StringUtils.hasText(prefix)) {
            sql.append("   AND object_key LIKE ?\n");
            args.add(prefix + "%");
        }
        sql.append("""
                 GROUP BY object_key
                 ORDER BY object_key ASC
                 LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new MigrationCandidate(
                rs.getString("object_key"),
                rs.getString("expected_checksum"),
                rs.getInt("checksum_variant_count"),
                rs.getInt("reference_count")
        ), args.toArray());
    }

    private String unionKeySql() {
        StringJoiner joiner = new StringJoiner("\nUNION ALL\n");
        for (ObjectStorageKeyRegistry.ObjectKeyColumn column : objectStorageKeyRegistry.columns()) {
            StringBuilder sql = new StringBuilder("""
                    SELECT %s AS object_key,
                           %s AS expected_checksum
                      FROM %s
                    """.formatted(unionString(column.columnName()), unionString(column.checksumExpression()), column.tableName()));
            if (StringUtils.hasText(column.whereClause())) {
                sql.append(" WHERE ").append(column.whereClause()).append("\n");
            }
            joiner.add(sql.toString());
        }
        return joiner.toString();
    }

    private String unionString(String expression) {
        return UNION_STRING_CAST_TEMPLATE.formatted(expression);
    }

    private ObjectStorageMigrationItemVO baseItem(MigrationCandidate candidate) {
        ObjectStorageMigrationItemVO item = new ObjectStorageMigrationItemVO();
        item.setObjectKey(candidate.objectKey());
        item.setExpectedChecksum(candidate.expectedChecksum());
        item.setChecksumVariantCount(candidate.checksumVariantCount());
        item.setReferenceCount(candidate.referenceCount());
        if (candidate.checksumVariantCount() > 1) {
            item.setResult("failed");
            item.setWarningMessage("checksum_variant_count_gt_1");
            item.setErrorMessage("conflicting_db_checksums_for_same_object_key");
            item.setMetrics(Map.of(
                    "checksumVariantCount", candidate.checksumVariantCount(),
                    "warning", "same object_key has multiple expected checksums in DB"
            ));
        }
        return item;
    }

    private void summarize(ObjectStorageMigrationResponse response) {
        response.setCandidateCount(response.getItems().size());
        response.setMigratedCount((int) response.getItems().stream()
                .filter(item -> "migrated".equals(item.getResult()))
                .count());
        response.setSkippedCount((int) response.getItems().stream()
                .filter(item -> "skipped".equals(item.getResult()))
                .count());
        response.setFailedCount((int) response.getItems().stream()
                .filter(item -> "failed".equals(item.getResult()))
                .count());
        response.setWarningCount((int) response.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getWarningMessage()))
                .count());
        long bytes = response.getItems().stream()
                .mapToLong(item -> item.getSourceSizeBytes() == null ? 0L : item.getSourceSizeBytes())
                .sum();
        response.setEstimatedBytes(bytes);
        response.setMigratedBytes(response.getItems().stream()
                .filter(item -> "migrated".equals(item.getResult()))
                .mapToLong(item -> item.getSourceSizeBytes() == null ? 0L : item.getSourceSizeBytes())
                .sum());
    }

    private Map<String, Object> startMetrics(ObjectStorageMigrationResponse response) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sourceProvider", "minio");
        metrics.put("targetProvider", "cos");
        metrics.put("limit", response.getLimit());
        metrics.put("cursorObjectKey", response.getCursorObjectKey());
        metrics.put("prefix", response.getPrefix());
        metrics.put("registeredColumns", objectStorageKeyRegistry.columns().stream()
                .map(column -> column.tableName() + "." + column.columnName())
                .toList());
        return metrics;
    }

    private Map<String, Object> metrics(ObjectStorageMigrationResponse response) {
        Map<String, Object> metrics = startMetrics(response);
        metrics.put("candidateCount", response.getCandidateCount());
        metrics.put("migratedCount", response.getMigratedCount());
        metrics.put("skippedCount", response.getSkippedCount());
        metrics.put("failedCount", response.getFailedCount());
        metrics.put("warningCount", response.getWarningCount());
        metrics.put("estimatedBytes", response.getEstimatedBytes());
        metrics.put("migratedBytes", response.getMigratedBytes());
        metrics.put("hasMore", response.getHasMore());
        metrics.put("nextCursorObjectKey", response.getNextCursorObjectKey());
        return metrics;
    }

    static String contentTypeForObjectKey(String objectKey) {
        String normalized = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".gif")) return "image/gif";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".svg")) return "image/svg+xml";
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (normalized.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record MigrationCandidate(String objectKey,
                                      String expectedChecksum,
                                      int checksumVariantCount,
                                      int referenceCount) {
    }

    private record ExistingCosObject(boolean exists, String checksum) {
    }
}
