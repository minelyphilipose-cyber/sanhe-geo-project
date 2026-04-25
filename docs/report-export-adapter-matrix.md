# Report Export Adapter Matrix

This document defines the boundary between the reusable PDF render kernel and product-specific export adapters.

Core principle:
- Kernel renders a URL into a PDF and collects raw render/debug artifacts.
- Adapter owns business lifecycle, permissions, data snapshot, file keys, quality policy, retry/cancel, and audit.

| Dimension | Presale | Aftersale | Kernel Or Adapter |
|---|---|---|---|
| Render URL | `/presale-print/:renderToken` | TBD | Adapter builds URL; kernel consumes URL |
| Authentication | Redis-backed renderToken with TTL | TBD | Adapter |
| Data Source | `presale_report_export` snapshot JSON | TBD | Adapter |
| Snapshot Timing | Snapshot captured at export creation | TBD | Adapter |
| Header/Footer | Presale 18-page template controls page chrome | May need watermark/version/confidential labels | Adapter |
| Page Format | A4 portrait, CSS `@page` | TBD, may include A3/landscape | Kernel profile + adapter policy |
| Page Count | Expected 18 pages | TBD | Adapter quality rule |
| Chart Readiness | `window.__PRESALE_PRINT_READY__` | TBD | Kernel can wait for configured signal; adapter defines signal |
| Metrics Schema | snake_case: `page_count`, `chart_count`, `bottom_band_ok`, etc. | TBD | Common base + adapter extension |
| Chart Non-Blank Policy | Warn by default, optional enforce | TBD | Adapter quality rule |
| Bottom Band Policy | Enforced by default | TBD | Adapter quality rule |
| File Key | `presale/exports/{export_id}/report.pdf` | TBD, likely `aftersale/exports/...` | Adapter |
| Debug Key | `presale/exports/{export_id}/debug/{timestamp}/` | TBD | Adapter path policy, kernel writes files |
| Snapshot Object Key | `presale/exports/{export_id}/snapshot.json` | TBD | Adapter |
| Expiration | `expire_at` on export row | TBD | Adapter; cleanup pattern reusable |
| Permissions | `presale.report.export`, `presale.report.view`, `presale.report.download` | TBD | Adapter |
| Audit | Export row + download log | TBD | Adapter |
| Idempotency | `reportId:versionId:editableContentHash:exportProfile` | TBD | Adapter |
| Retry | Same row, `retry_count`, `retry_history` | TBD | Adapter |
| Cancel | DB status + in-memory cancellation registry | TBD | Adapter |
| Queue Model | Single serial worker v1.0 | TBD | Adapter; kernel remains synchronous |
| Browser Lifecycle | Shared Playwright browser, preheated. Current implementation is still named `PresaleBrowserManager`; aftersale integration should upgrade it to a generic BrowserManager. | Same kernel can reuse | Kernel now, rename later |
| Concurrency Gate | Kernel semaphore + worker queue | Same kernel behavior reusable | Kernel |
| PDF Generation | Playwright `page.pdf` | Reusable | Kernel |
| Raw Debug Capture | screenshot/html/console/network/metrics/error | Reusable | Kernel writes, adapter stores |
| Storage Backend | MinIO + local fallback for debug | TBD | Adapter storage policy |
| Cleanup | Delete PDF/snapshot/debug after `expire_at`, keep DB row | Likely reusable | Adapter service pattern |

## Kernel Boundary

The reusable kernel should contain:
- browser acquisition and lifecycle
- page navigation
- ready-signal waiting
- PDF generation
- raw debug file creation
- raw metrics extraction
- concurrency semaphore

The reusable kernel should not contain:
- presale/aftersale route construction
- renderToken creation
- DB state transitions
- idempotency decisions
- permission checks
- storage object key construction
- business quality policy
- retry/cancel semantics

## Adapter Boundary

Each product adapter should own:
- export task table and state machine
- request/response API
- permission and audit requirements
- snapshot creation and consistency policy
- render URL and token strategy
- file/debug/snapshot key strategy
- quality enforcement rules
- cleanup eligibility
- retry and cancel behavior

## Aftersale Integration Guidance

When aftersale starts integration:
1. Reuse `ExportRenderKernel` only.
2. Do not reuse `presale_report_export` table or presale endpoints.
3. Define aftersale snapshot format and render route first.
4. Define aftersale permissions and audit events explicitly.
5. Map aftersale quality rules to its page design instead of copying presale page-count/bottom-band values.
6. Reuse cleanup service pattern, but use aftersale object key prefixes.