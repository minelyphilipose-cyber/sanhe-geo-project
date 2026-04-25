# PR-D Presale Export Closeout Design

## Scope

PR-D is the closeout phase for presale report PDF export. It covers production cleanup, an export render kernel abstraction for future aftersale integration, and operations documentation.

Out of scope for PR-D:
- Aftersale PDF implementation.
- Prometheus/Micrometer metrics wiring.
- Frontend visual redesign.

## Decision: Hard Delete Expired Files

Expired export artifacts use hard delete:
- Delete MinIO objects.
- Keep `presale_report_export` DB rows for audit.
- Mark `file_purged_at` after cleanup.
- Download returns `410 PRESALE_EXPORT_FILE_PURGED` after purge.

Rationale: `expire_at` is the artifact lifecycle boundary. Keeping expired objects increases storage and compliance risk. Audit remains in DB.

## Task 1: Expired Artifact Cleanup

### DDL

Add `file_purged_at`:

```sql
ALTER TABLE presale_report_export
  ADD COLUMN file_purged_at DATETIME NULL COMMENT '导出文件及关联产物清理时间';

CREATE INDEX idx_presale_export_expire_purge
  ON presale_report_export (expire_at, file_purged_at, status);
```

### Candidate Query

Criteria:
- `expire_at < NOW()`
- `file_purged_at IS NULL`
- `status IN ('SUCCESS', 'FAILED', 'CANCELED')`
- batch limit: `1000`

Do not process `PENDING` or `RUNNING` rows even if expired.

### Artifacts To Delete

For each export row:
- PDF: `file_key`
- Snapshot: `snapshot_key` when `snapshot_storage_type = 'OBJECT'`
- Debug: prefix `presale/exports/{export_id}/debug/`
- Local fallback: `target/presale-exports/{export_id}`

### DB Marking

Cleanup is strict per row:
- collect `[fileKey, snapshotKey?, debugPrefix]`
- all deletions succeed -> mark `file_purged_at`
- object not found is success
- any deletion fails -> do not mark, increment `metrics_json.cleanup_retry_count`, retry next cycle
- when `cleanup_retry_count >= max-retry-count`, mark `file_purged_at` and write `metrics_json.cleanup_failure = { error_code, retry_count, pending_keys, at }` for operations follow-up

```sql
UPDATE presale_report_export
SET file_purged_at = NOW(),
    updated_at = NOW()
WHERE id = ?
  AND file_purged_at IS NULL;
```

No new terminal status is introduced. `SUCCESS/FAILED/CANCELED` remain business terminal states.

### Download Behavior

Download API order:
1. Export row not found: `404`
2. `status != SUCCESS`: `409`
3. `file_purged_at IS NOT NULL`: `410 PRESALE_EXPORT_FILE_PURGED`
4. `expire_at < now`: `410 PRESALE_EXPORT_FILE_EXPIRED`
5. missing `file_key`: `404`

Semantics:
- `PRESALE_EXPORT_FILE_PURGED`: permanent physical purge; regenerate.
- `PRESALE_EXPORT_FILE_EXPIRED`: business expiration before cleanup has run; regenerate.

### Multi-Instance Safety

Use Redis distributed lock:
- key: `lock:presale-export-cleanup`
- TTL: `30m`

If Redis lock cannot be acquired, skip this cleanup cycle. Do not fall back to unlocked cleanup.

Add cron jitter:
- random `0-60s` delay after every cron trigger, before lock acquisition; this is not startup-only delay.

### Config

```yaml
geo:
  presale-export:
    cleanup:
      enabled: true
      cron: "0 15 3 * * *"
      batch-size: 1000
      lock-key: "lock:presale-export-cleanup"
      lock-ttl-ms: 1800000
      cron-jitter-ms: 60000
```

### Proposed Classes

- `PresaleExportCleanupJob`
- `PresaleExportCleanupService`
- `PresaleExportCleanupLockService`
- `PresaleReportExportMapper.selectExpiredForCleanup(...)`
- `PresaleReportExportMapper.markFilePurged(...)`
- `PresaleExportStorageService.removePrefix(...)`

### Logging

Per run:
- started timestamp
- candidate count
- purged count
- failed count
- elapsed ms

Per row failure:
- exportId
- fileKey
- snapshotKey
- debugPrefix
- error message

MinIO delete failures do not stop other object deletions. If cleanup fails for a row, do not mark `file_purged_at`; retry next cycle. If `cleanup_retry_count >= max-retry-count`, mark `file_purged_at` and write the failure details to `metrics_json.cleanup_failure`; `error_msg` remains reserved for render failure reasons.

### Acceptance

- Expired `SUCCESS` row: PDF/debug/snapshot removed, `file_purged_at` set.
- Not expired `SUCCESS` row: untouched.
- Expired `RUNNING` row: untouched.
- Purged file download: `410 PRESALE_EXPORT_FILE_PURGED`.
- Lock not acquired: cleanup skipped.

## Task 4: Export Render Kernel Abstraction

### Goal

Move Playwright PDF rendering behind a generic interface so aftersale can reuse the renderer without depending on presale task tables, tokens, or file-key policy.

### Interface

Package:

```text
com.huanjing.geo.module.export.render
```

Interface:

```java
public interface ExportRenderKernel {
    ExportRenderResult render(ExportRenderRequest request) throws Exception;
}
```

Request:

```java
@Builder
@Value
public class ExportRenderRequest {
    Long exportId;
    String renderUrl;
    Path outputPath;
    Path debugDir;
    ExportRenderProfile profile;
}
```

Result:

```java
@Builder
@Value
public class ExportRenderResult {
    long elapsedMs;
    long fileSize;
    String metricsJson;

    public ExportRenderResult withMetricsJson(String newMetricsJson) { ... }
}
```

Profile:

```java
@Builder
@Value
public class ExportRenderProfile {
    String pageFormat;
    boolean printBackground;
    boolean preferCssPageSize;
    double deviceScaleFactor;
    int viewportWidth;
    int viewportHeight;
    long pageLoadTimeoutMs;
    long readyTimeoutMs;
    long pdfTimeoutMs;
}
```

### Implementation Plan

- Rename/adapt `PresalePdfRenderKernel` into `PlaywrightPdfRenderKernel implements ExportRenderKernel`.
- Move common request/result classes into `module.export.render`.
- Keep `PresaleBrowserManager` name in PR-D. Renaming is tracked for aftersale integration because it touches health/preheat/config naming.
- Presale worker builds `ExportRenderRequest` + `ExportRenderProfile` from `PresaleExportProperties`.
- Presale worker depends on `ExportRenderKernel`, not concrete kernel.

### Keep In Presale Adapter

Do not abstract these in PR-D:
- task table
- idempotency key
- permissions
- renderToken issuance
- snapshot format
- file-key policy
- quality rules
- retry/cancel state machine

Reason: these are adapter concerns and differ between presale and aftersale.

### Acceptance

- Presale export still succeeds.
- `PresaleReportExportWorker` depends on `ExportRenderKernel`.
- Generic render package does not contain presale business terms.
- `mvn test-compile` passes.
- One successful export verifies metrics are unchanged.

## Task 5: Adapter Matrix Documentation

Create `docs/report-export-adapter-matrix.md`.

Purpose:
- Document presale/aftersale differences before aftersale integration.
- Make clear what belongs in kernel vs adapter.

## Operations Metrics Documentation

PR-D should document but not implement Prometheus wiring.

Recommended metrics:
- queue backlog: `PENDING` count
- active task count: `RUNNING` count
- success/failure/cancel rate
- retry count distribution
- render elapsed ms p50/p95
- PDF elapsed ms p50/p95
- memory peak MB p50/p95
- cleanup purged count and failed count

Recommended alert thresholds:
- `PENDING` count > 20 for 10 minutes
- failure rate > 10% over 30 minutes
- p95 render elapsed > 60s over 30 minutes
- memory peak > 1.2GB for 3 consecutive exports
- cleanup failures > 0 for 2 consecutive runs

## PR-D Split

### PR-D1 Cleanup

Includes:
- migration `file_purged_at`
- cleanup job/service/mapper/storage prefix delete
- download `410 FILE_PURGED`
- cleanup acceptance tests/manual verification

### PR-D2 Kernel Abstraction

Includes:
- `ExportRenderKernel`
- common request/result/profile
- `PlaywrightPdfRenderKernel`
- presale worker dependency inversion
- successful export regression

### PR-D3 Documentation

Includes:
- adapter matrix
- operations metrics and alert thresholds

### PR-D4 Optional v1.1 Items

Includes if time allows:
- font bundle slimming
- ResizeObserver scoping
- browser quickProbe health check

