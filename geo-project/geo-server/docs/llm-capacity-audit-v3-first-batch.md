# LLM Capacity Audit - v3 First Batch

This note records the first-batch capacity audit required before enabling permit strategy changes.
It intentionally avoids production secrets and only lists capacity-relevant effective values.

## Effective Configuration Baseline

The local production `.env` does not override the core LLM pool, dispatch worker, baseline collection,
or presale generation capacity variables. With `SPRING_PROFILES_ACTIVE=prod`, these values fall back to
`application.yml` defaults unless deployment overrides them elsewhere.

| Area | Effective value | Source |
|---|---:|---|
| Dispatch worker poll concurrency | 4 | `WORKER_POLL_CONCURRENCY` default |
| Dispatch resource busy retry min | 30s | `DISPATCH_RESOURCE_BUSY_RETRY_MIN_SECONDS` default |
| Dispatch resource busy retry jitter | 30s | `DISPATCH_RESOURCE_BUSY_RETRY_JITTER_SECONDS` default |
| Dispatch resource busy max attempts | 60 | `DISPATCH_RESOURCE_BUSY_MAX_ATTEMPTS` default |
| LLM global permit | 36 | `GEO_LLM_POOL_GLOBAL_CONCURRENCY` default |
| LLM permit blocking timeout | 120000ms | `GEO_LLM_POOL_PERMIT_WAIT_TIMEOUT_MS` default |
| LLM feature permit: monitoring | 8 | `GEO_LLM_POOL_FEATURE_MONITORING` default |
| LLM feature permit: article | 4 | `GEO_LLM_POOL_FEATURE_ARTICLE` default |
| LLM feature permit: presale | 12 | `GEO_LLM_POOL_FEATURE_PRESALE` default |
| LLM feature permit: baseline | 16 | `GEO_LLM_POOL_FEATURE_BASELINE` default |
| Baseline sample executor max pool | 16 | `BASELINE_COLLECTION_SAMPLE_MAX_POOL_SIZE` default |
| Baseline max concurrent baselines | 1 | `BASELINE_COLLECTION_MAX_CONCURRENT_BASELINES` default |
| Presale generate core/max pool | 11 / 13 | `PRESALE_GENERATE_CORE_POOL_SIZE` / `PRESALE_GENERATE_MAX_POOL_SIZE` defaults |
| Presale max concurrent reports | 2 | `PRESALE_MAX_CONCURRENT_REPORTS` default |
| Presale export max concurrency | 1 | `.env` override |
| HTTP client max connections | unbounded / not enforced | `java.net.http.HttpClient` stack |

## First-Batch Code Flags

The first-batch dispatch capacity adaptation is present but off by default:

| Flag | Default | Effect when enabled |
|---|---:|---|
| `DISPATCH_CAPACITY_FAILURE_CLASSIFICATION_ENABLED` | `false` | Dispatch route failures use `LlmCapacityFailureClassifier`, so structured `429/5xx/timeout` causes can become capacity retry signals. |
| `DISPATCH_RESOURCE_BUSY_RETRY_AFTER_ENABLED` | `false` | Dispatch resource waiting uses `Retry-After` first, otherwise jittered exponential backoff capped by `DISPATCH_RESOURCE_BUSY_RETRY_MAX_SECONDS`. |
| `DISPATCH_RESOURCE_BUSY_RETRY_MAX_SECONDS` | `900` | Caps fallback exponential resource-busy retry delay. Platform-provided `Retry-After` is treated as authoritative and is not shortened by this cap. |
| `BASELINE_COLLECTION_CAPACITY_FAILURE_DEFER_ENABLED` | `false` | Baseline capacity failures are retried within the existing transient retry loop and, when exhausted, leave the sample missing instead of inserting a `FAILED` observation. |
| `PRESALE_GENERATE_CAPACITY_FAILURE_DEFER_ENABLED` | `false` | Presale generation capacity failures are requeued as `QUEUED` with `CAPACITY_DEFERRED` metadata instead of marking the version/report `FAILED`. |
| `GEO_LLM_POOL_BLOCKING_ACQUIRE_FAIL_FAST_ENABLED` | `false` | Master switch only. `acquireBlocking` keeps its compatibility method name; no chain changes behavior unless its feature is explicitly listed below. |
| `GEO_LLM_POOL_BLOCKING_ACQUIRE_FAIL_FAST_FEATURES` | empty | Comma-separated feature allowlist for gateway fail-fast, for example `presale`. Empty means no feature is enabled even when the master switch is on. |

With both flags disabled, dispatch keeps the existing behavior: route-level capacity failures already wrapped as
`DispatchResourceBusyException` are retried with fixed min delay plus jitter.

## Audit Notes

- `WORKER_POLL_CONCURRENCY=4` is not the total LLM pressure. Baseline collection and presale generation have their own executors and can independently drive LLM demand.
- The current HTTP stack does not enforce a connection-pool maximum, so permit/RPM/TPM are the only explicit outbound capacity constraints in this batch.
- Permit should eventually be the binding constraint below platform RPM/TPM, leaving headroom to avoid true platform 429s. Platform-specific RPM/TPM values still need to be filled from provider quotas.
