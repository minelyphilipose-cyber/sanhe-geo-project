# LLM Capacity Projection - v6 Rolling Polling

This report recalculates the pre-onboarding LLM capacity plan after the v6 changes:

- Question polling only plans A-tier questions.
- A-tier polling can be spread by `DISPATCH_QUESTION_POLL_CYCLE_DAYS`.
- Question polling hits only the enabled polling platforms: `deepseek`, `hunyuan`, `doubao`, `qwen`.
- Mobile dashboard judge is required for data accuracy and must not be budget-blocked.
- Mobile judge uses its own `mobile_judge` feature and candidate platforms: `deepseek`, `doubao`, `qwen`.
- Article generation excludes `hunyuan/yuanbao`.
- Mobile dashboard current-state metrics read latest completed `poll_results`; daily summaries remain trend data.

This is a sizing report only. It does not change runtime code or production configuration.

## 1. Confirmed Baseline

| Item | Current value / behavior | Source |
|---|---:|---|
| Server | 8 vCPU, 14 GiB memory | production host screenshot |
| App container limit | 4 vCPU, 6 GiB memory | production docker stats/inspect screenshot |
| MySQL container limit | 2 vCPU, 4 GiB memory | production docker stats/inspect screenshot |
| Hikari max pool | 16 | `.env` |
| MySQL max_connections | 151 | production query screenshot |
| MySQL active connection peak observed | 23 | production query screenshot |
| Dispatch worker poll concurrency | 4 | `WORKER_POLL_CONCURRENCY` default |
| LLM global permit | 36 | `GEO_LLM_POOL_GLOBAL_CONCURRENCY` default |
| LLM feature permit: monitoring | 8 | `GEO_LLM_POOL_FEATURE_MONITORING` default |
| LLM feature permit: mobile_judge | 4 | `GEO_LLM_POOL_FEATURE_MOBILE_JUDGE` default |
| LLM feature permit: article | 4 | `GEO_LLM_POOL_FEATURE_ARTICLE` default |
| Question poll cycle default | 1 day, legacy full daily behavior | `DISPATCH_QUESTION_POLL_CYCLE_DAYS` default |
| Question poll platforms | `deepseek`, `hunyuan`, `doubao`, `qwen` | platform config screenshot / code path |
| Mobile judge platforms | `deepseek`, `doubao`, `qwen` | `geo.mobile-dashboard.entity-judge.platform-codes` default |
| Article excluded platforms | `hunyuan`, `yuanbao` | `geo.llm.routing.article-excluded-platform-codes` default |

Important production note: the local production `.env` does not currently override the core LLM pool or dispatch stagger variables. Enabling v6 capacity behavior requires explicit environment changes.

## 2. Load Model

### 2.1 Inputs

| Parameter | Symbol | Value used for sizing |
|---|---:|---:|
| Mainstream A-tier questions per customer | `Q_main` | 50 |
| Heavy A-tier questions per customer | `Q_heavy` | 200 |
| Cycle days | `D` | 7 recommended |
| Polling platforms | `P_poll` | 4 |
| Article count per customer per day | `A_article` | 10 |
| Article LLM calls per article | `C_article` | 1 |
| Mobile judge sample count | `S_judge` | 1 per judged `poll_result` |
| Average LLM duration for sizing | `T_avg` | 15s placeholder, replace with measurement p50/p95 |
| Peak factor over all-day average | `F_peak` | 4 conservative |

Formula:

```text
daily_questions(customer) = ceil(min(actual_A_count, plan_cap_if_positive) / D)
daily_poll_calls = sum(daily_questions(customer)) * P_poll
daily_article_calls = customer_count * A_article * C_article
daily_mobile_judge_llm_calls = daily_poll_calls * judge_llm_rate
average_concurrency = daily_calls / 86400 * T_avg
peak_concurrency_estimate = average_concurrency * F_peak
```

`judge_llm_rate` is not the same as coverage. Every completed `poll_result` still receives a judge result. The LLM call rate is reduced only when local entity matching can deterministically write `deterministic_no_entity_hit`.

### 2.2 Customer Scenarios

The table below assumes `D=7` and `plan_cap<=0` or `plan_cap>=actual_A_count`.

| Scenario | Customer mix | Daily question slice | Daily poll calls |
|---|---|---:|---:|
| First batch, all mainstream | 40 x 50A | 40 x ceil(50/7)=320 | 1,280 |
| First batch, 1 heavy | 39 x 50A + 1 x 200A | 39 x 8 + 1 x 29 = 341 | 1,364 |
| Full load, all mainstream | 200 x 50A | 200 x 8 = 1,600 | 6,400 |
| Full load, 5 heavy | 195 x 50A + 5 x 200A | 195 x 8 + 5 x 29 = 1,705 | 6,820 |

If a heavy customer is capped by `planKeywordGroupLimitA=50`, that customer behaves like a mainstream customer for daily poll volume: `ceil(50/7)=8`, not `ceil(200/7)=29`.

### 2.3 Total Daily LLM Calls

The judge columns show a range. `judge_llm_rate=0.5` means half of poll results need an LLM judge after local matcher filtering. `judge_llm_rate=1.0` is the worst case where every poll result needs LLM judging.

| Scenario | Poll calls | Article calls | Judge calls at 50% | Judge calls at 100% | Total at 50% | Total at 100% |
|---|---:|---:|---:|---:|---:|---:|
| First batch, all mainstream | 1,280 | 400 | 640 | 1,280 | 2,320 | 2,960 |
| First batch, 1 heavy | 1,364 | 400 | 682 | 1,364 | 2,446 | 3,128 |
| Full load, all mainstream | 6,400 | 2,000 | 3,200 | 6,400 | 11,600 | 14,800 |
| Full load, 5 heavy | 6,820 | 2,000 | 3,410 | 6,820 | 12,230 | 15,640 |

With `T_avg=15s`, even the full-load 5-heavy worst case is:

```text
average concurrency = 15,640 / 86,400 * 15 = 2.72
peak estimate       = 2.72 * 4 = 10.86
```

So under rolling all-day spread, the binding risk is not global compute. The real risks are:

- local bursts when tasks become visible together;
- narrow platform headroom, especially `hunyuan`;
- judge backlog if `mobile_judge` is enabled but the worker only processes a small fixed number per minute;
- configuration drift that accidentally routes article/judge traffic back to `hunyuan`.

## 3. Platform Headroom

### 3.1 Per-Platform Poll Volume

Each polling platform receives the full daily question slice, not one quarter of it.

| Scenario | Calls per polling platform per day |
|---|---:|
| First batch, all mainstream | 320 |
| First batch, 1 heavy | 341 |
| Full load, all mainstream | 1,600 |
| Full load, 5 heavy | 1,705 |

At full load with 5 heavy customers and 15s average latency:

```text
per-platform average concurrency = 1,705 / 86,400 * 15 = 0.30
per-platform peak estimate       = 0.30 * 4 = 1.18
```

This is below `hunyuan`'s configured 5-way concurrency, provided the 24h stagger is actually enabled and effective.

### 3.2 Platform-Specific Notes

| Platform | Role | Constraint | Judgment |
|---|---|---|---|
| `doubao` | poll, article, judge | high RPM/TPM account quota | Headroom is ample for this plan. |
| `deepseek` | poll, article, judge | dynamic concurrency, 429/backoff behavior | Keep fail-fast/retry-after and alerting on; do not rely on a fixed RPM. |
| `qwen` | poll, article, judge | example quota around 200 RPM plus TPM | Daily volume is low after rolling spread; still watch true 429 and TPM. |
| `hunyuan` / `yuanbao` | poll only | 5-way concurrency in current platform config | Must remain poll-only; use 24h stagger override. Do not route article or judge here. |

`hunyuan` is the narrowest configured platform, but rolling spread changes it from a crisis point to a local-burst risk. Its safety depends on:

- `DISPATCH_STAGGER_ENABLED=true`;
- `DISPATCH_STAGGER_HUNYUAN_MAX_DELAY_MINUTES=1440`;
- `DISPATCH_STAGGER_HUNYUAN_JITTER_SECONDS>=300`;
- no article/judge traffic routed to `hunyuan/yuanbao`.

## 4. Feature Permit Review

| Feature | Current permit | Recommended initial value | Rationale |
|---|---:|---:|---|
| global | 36 | 36 | Full-load all-day peak estimate is around 11 under worst judge case. Keep 36; do not raise before real measurement. |
| monitoring | 8 | 8 | Poll peak estimate per all platforms remains below 8 with `D=7`. This is the main safety gate for poll bursts. |
| mobile_judge | 4 | 4 initially, consider 6 only if backlog grows | Judge is accuracy-critical, but current volume is low after rolling spread. Raise only if pending judge age grows. |
| article | 4 | 4 | 2,000/day at full load is light under all-day operation. Article already excludes `hunyuan/yuanbao`. |
| baseline | 16 | 16 | Not part of customer onboarding daily poll load. Keep isolated. |
| presale | 12 | 12 | Not part of daily poll/judge load. Keep isolated. |
| generic | 4 | 4 | Keep low. Do not migrate high-volume paths to `generic`. |

Do not set feature permits by average alone if `DISPATCH_QUESTION_POLL_CYCLE_DAYS` remains `1`. Without rolling spread, full-load poll calls return to `200 * 50 * 4 = 40,000/day`, and the peak profile becomes a night-batch problem again.

## 5. Recommended Initial Production Parameters

These values are intended as the first production rollout profile. They should be reviewed after one or two real nights of measurement.

| Variable | Recommended initial value | Why |
|---|---:|---|
| `DISPATCH_QUESTION_POLL_CYCLE_DAYS` | `7` | Spread A-tier questions across a weekly cycle. |
| `DISPATCH_STAGGER_ENABLED` | `true` | Enable delayed visibility for BI poll shards. |
| `DISPATCH_STAGGER_TASK_TYPES` | `BI_DAILY_POLL` | Keep scope narrow. |
| `DISPATCH_STAGGER_WINDOW_MINUTES` | `1440` | All-day spread for non-Hunyuan platforms too. |
| `DISPATCH_STAGGER_MAX_DELAY_MINUTES` | `1440` | Do not collapse overflow into a short night window. |
| `DISPATCH_STAGGER_JITTER_SECONDS` | `300` | Keep larger than resource-busy retry spread; reduce re-alignment risk. |
| `DISPATCH_STAGGER_CAP_JITTER_SECONDS` | `300` | Avoid a capped tail spike. |
| `DISPATCH_STAGGER_HUNYUAN_WINDOW_MINUTES` | `1440` | Hunyuan needs the smoothest timeline. |
| `DISPATCH_STAGGER_HUNYUAN_MAX_DELAY_MINUTES` | `1440` | Same. |
| `DISPATCH_STAGGER_HUNYUAN_JITTER_SECONDS` | `300` | Same. |
| `DISPATCH_STAGGER_YUANBAO_WINDOW_MINUTES` | `1440` | Keep alias platform safe if used. |
| `GEO_LLM_POOL_GLOBAL_CONCURRENCY` | `36` | Enough for v6 rolling plan; no need to raise before measurement. |
| `GEO_LLM_POOL_FEATURE_MONITORING` | `8` | Keeps poll bursts contained. |
| `GEO_LLM_POOL_FEATURE_MOBILE_JUDGE` | `4` | Start conservative; monitor judge backlog. |
| `GEO_LLM_POOL_FEATURE_ARTICLE` | `4` | Adequate for 10 articles/customer/day. |
| `GEO_LLM_ROUTING_ARTICLE_EXCLUDED_PLATFORM_CODES` | `hunyuan,yuanbao` | Keep article off Hunyuan. |
| `MOBILE_DASHBOARD_ENTITY_JUDGE_PLATFORM_CODES` | `deepseek,doubao,qwen` | Keep judge off Hunyuan. |

Implementation note: verify the exact environment variable name for mobile judge platform codes in deployment. The Spring property is `geo.mobile-dashboard.entity-judge.platform-codes`; relaxed binding typically maps this to `GEO_MOBILE_DASHBOARD_ENTITY_JUDGE_PLATFORM_CODES`, not `MOBILE_DASHBOARD_ENTITY_JUDGE_PLATFORM_CODES`.

## 6. Alert Thresholds

Recommended first alerts:

| Signal | Warning threshold | Critical threshold |
|---|---:|---:|
| global active peak / global permit | >= 70% for 5 minutes | >= 90% for 3 minutes |
| monitoring active peak / monitoring permit | >= 70% for 5 minutes | >= 90% for 3 minutes |
| `hunyuan` platform active peak | >= 4 for 5 minutes | >= 5 for 1 minute |
| `platform429Count` per platform | > 0 for `hunyuan/qwen/deepseek` | >= 5/minute |
| `permitBusyCount` per feature | > 0 sustained 5 minutes | >= 10/minute |
| mobile judge pending age | > 2 hours | > 6 hours |
| stagger overflow | any | repeated in 2 consecutive runs |

Mobile judge has no budget gate, so backlog age is the right operational signal. If backlog age grows while `mobile_judge` active peak is near 4, raise `GEO_LLM_POOL_FEATURE_MOBILE_JUDGE` to 6 before changing model/platform scope.

## 7. SQL Diagnostics

### 7.1 Call volume by run / feature / platform

```sql
SELECT
  COALESCE(run_id, 'ad_hoc') AS run_id,
  feature,
  platform_code,
  status,
  COUNT(*) AS calls,
  SUM(COALESCE(request_count, 1)) AS provider_requests,
  ROUND(AVG(total_ms)) AS avg_total_ms,
  ROUND(AVG(wait_ms)) AS avg_wait_ms,
  ROUND(AVG(http_ms)) AS avg_http_ms,
  SUM(COALESCE(prompt_tokens, 0)) AS prompt_tokens,
  SUM(COALESCE(completion_tokens, 0)) AS completion_tokens
FROM llm_call_observation
WHERE occurred_at >= ?
  AND occurred_at < ?
GROUP BY COALESCE(run_id, 'ad_hoc'), feature, platform_code, status
ORDER BY calls DESC;
```

### 7.2 Minute peak capacity

```sql
SELECT
  bucket_minute,
  run_id,
  feature,
  platform_code,
  MAX(global_active_peak) AS global_active_peak,
  MAX(feature_active_peak) AS feature_active_peak,
  MAX(platform_active_peak) AS platform_active_peak,
  SUM(permit_busy_count) AS permit_busy_count,
  SUM(internal_rate_limited_count) AS internal_rate_limited_count,
  SUM(platform429_count) AS platform429_count,
  SUM(http5xx_count) AS http5xx_count,
  SUM(timeout_count) AS timeout_count
FROM llm_capacity_minute_metric
WHERE bucket_minute >= ?
  AND bucket_minute < ?
GROUP BY bucket_minute, run_id, feature, platform_code
ORDER BY bucket_minute, feature, platform_code;
```

### 7.3 Project-level unit load

```sql
SELECT
  project_id,
  feature,
  platform_code,
  COUNT(*) AS calls,
  ROUND(AVG(total_ms)) AS avg_total_ms,
  ROUND(MAX(total_ms)) AS max_total_ms,
  SUM(COALESCE(prompt_tokens, 0) + COALESCE(completion_tokens, 0)) AS total_tokens
FROM llm_call_observation
WHERE occurred_at >= ?
  AND occurred_at < ?
  AND project_id IS NOT NULL
GROUP BY project_id, feature, platform_code
ORDER BY calls DESC;
```

### 7.4 Mobile judge LLM rate

```sql
SELECT
  DATE(pr.updated_at) AS stat_date,
  pr.project_id,
  COUNT(DISTINCT pr.id) AS completed_poll_results,
  COUNT(DISTINCT CASE
    WHEN j.judge_model = 'deterministic_no_entity_hit' THEN pr.id
  END) AS deterministic_results,
  COUNT(DISTINCT CASE
    WHEN j.judge_model IS NOT NULL
     AND j.judge_model <> 'deterministic_no_entity_hit' THEN pr.id
  END) AS llm_judged_results
FROM poll_results pr
LEFT JOIN poll_result_entity_judge j
  ON j.poll_result_id = pr.id
 AND j.entity_type = 'focus_brand'
 AND j.judge_prompt_version = 'mobile_entity_judge_v1'
WHERE pr.status = 'completed'
  AND pr.question_tier = 'A'
  AND pr.updated_at >= ?
  AND pr.updated_at < ?
GROUP BY DATE(pr.updated_at), pr.project_id
ORDER BY stat_date, completed_poll_results DESC;
```

### 7.5 Hunyuan local burst check

```sql
SELECT
  bucket_minute,
  MAX(platform_active_peak) AS hunyuan_active_peak,
  SUM(platform429_count) AS hunyuan_429,
  SUM(permit_busy_count) AS permit_busy
FROM llm_capacity_minute_metric
WHERE bucket_minute >= ?
  AND bucket_minute < ?
  AND platform_code IN ('hunyuan', 'yuanbao')
GROUP BY bucket_minute
ORDER BY bucket_minute;
```

## 8. Remaining Decisions

1. Confirm production project `planKeywordGroupLimitA` semantics for heavy 200A customers. If the plan cap remains 50, heavy customers do not create 29 daily questions under a 7-day cycle.
2. Confirm actual mobile judge `judge_llm_rate` after local matcher. This is the main unknown in daily call total.
3. Confirm provider-side account quotas for `qwen`, `hunyuan`, and `deepseek`; platform permit values alone do not prove provider headroom.
4. Decide whether `mobile_judge` permit should stay 4 or move to 6 after observing judge pending age for one night.
5. Keep `hunyuan/yuanbao` excluded from article and judge as a release checklist item.

## 9. Conclusion

With `DISPATCH_QUESTION_POLL_CYCLE_DAYS=7` and all-day stagger enabled, the onboarding load is not a global LLM capacity crisis. Full-load daily volume is roughly:

- 6.4k-6.8k poll calls/day;
- 2k article calls/day;
- 3.2k-6.8k mobile judge LLM calls/day depending on local matcher hit rate.

The safe first rollout is to keep `global=36`, `monitoring=8`, `mobile_judge=4`, `article=4`, enable all-day stagger, and watch measurement for real peak minutes. The narrow operational focus should be `hunyuan` local bursts, mobile judge backlog age, and true provider 429s on `qwen/deepseek/hunyuan`.
