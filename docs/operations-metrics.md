# Report Export Operations Metrics

This document lists the minimum operational metrics and alert thresholds for the report export system. It is documentation-only for PR-D3; Prometheus or other metric sink wiring is intentionally out of scope.

## Core Signals

| Metric | Source | Suggested Threshold | Action |
|---|---|---|---|
| Queue backlog | count of `PENDING` rows | `> 20` for 10 minutes | Check worker health, Browser health, and Redis/DB latency |
| Running age | oldest `RUNNING.updated_at` | `> staleRunningTimeoutMs` | Confirm stale scanner is marking timed-out rows |
| Success rate | `SUCCESS / completed` | `< 95%` over 30 minutes | Inspect `retry_history.error_code` distribution |
| Render failure rate | count of `RENDER_FAILED` | `> 5` in 30 minutes | Check frontend availability, render route, and Playwright dependencies |
| Quality failure rate | `PRINT_*` failures | any spike | Check print CSS, chart readiness, and recent frontend releases |
| Browser memory peak | `metrics_json.memory_peak_mb_approx` | `> 900MB` warm steady state | Inspect DPR, chart canvas size, and Chromium process cleanup |
| Browser cold peak | first export after startup | `> 1.2GB` | Check preheat behavior and browser launch flags |
| Export latency | `ready_elapsed_ms + pdf_elapsed_ms` | `> 13s` p95 | Check frontend render readiness and chart load time |
| Cleanup failures | `metrics_json.cleanup_retry_count` | `>= 3` | Operations should inspect MinIO health before permanent cleanup marking |
| Permanent cleanup failure | `metrics_json.cleanup_failure` exists | any | Manually inspect `pending_keys` and clean orphan objects |

## Error Codes To Track

| Error Code | Meaning | Primary Owner |
|---|---|---|
| `RENDER_FAILED` | Playwright navigation/render failed | Backend + frontend |
| `PRINT_BOTTOM_BAND_BLOCKED` | PDF bottom safety band detected content | Frontend |
| `PRINT_PAGE_COUNT_MISMATCH` | page count differs from expected profile | Frontend |
| `PRINT_CHART_BLANK` | chart canvas blank when enforcement enabled | Frontend |
| `PRINT_METRICS_INVALID` | metrics JSON missing or malformed | Frontend + backend |
| `CONCURRENCY_REQUEUE` | kernel could not acquire render slot | Backend operations |
| `WORKER_HEARTBEAT_TIMEOUT` | stale running task failed by scanner | Backend operations |
| `CLEANUP_PARTIAL_FAILURE` | cleanup exceeded retry limit for some keys | Operations |

## Dashboards

Minimum dashboard panels:
- Export task status counts by status.
- PENDING backlog and oldest PENDING age.
- RUNNING count and oldest RUNNING heartbeat age.
- Export success/failure rate over time.
- Failure code distribution from `metrics_json.retry_history`.
- Warm render latency p50/p95.
- `memory_peak_mb_approx` p50/p95/max.
- Cleanup retry count distribution.

## Operational Notes

- `memory_peak_mb_approx` uses Java `ProcessHandle`/platform process data and is an approximation. On Windows it is based on private page count; Linux values may differ from RSS/private memory tools.
- The current worker is intentionally single-serial in v1.0. Increasing concurrency requires revalidating memory peak and Browser stability.
- Cleanup runs on a cron schedule with Redis lock. If Redis is unavailable, cleanup must skip rather than run unlocked.
- `cleanup_retry_count` increments once per cleanup cycle. With the default daily cron and `maxRetryCount=5`, permanent cleanup marking means roughly five consecutive days of cleanup failure.
