# 数据生命周期治理方案（P0 补正版）

本文档基于《数据生命周期治理 — 需求方案与方向》《对象存储使用规范》《Codex 开发方案 — 完整评审结论》补正。当前版本重点收口 P0：

- 日聚合必须 `recompute -> upsert SET`，不得 `INCREMENT`。
- 报告周期 freeze 必须异步化、幂等化，并有周期结束触发和堆积监控。
- `poll_results` 真正减压依赖客户可见视图矩阵、封口、对账和报告明细快照门控；所有门控未满足前可做汇总/freeze 基建，但不得开启明细 execute 删除。

## 1. 当前项目现状

### 已有基础

| 模块 | 当前实现 | 生命周期治理影响 |
|---|---|---|
| 轮询批次聚合 | `DispatchPollAggregationService.aggregateBatchIfReady()` 在 batch 全部分片终态后写 `poll_daily_stats` | 新日汇总应挂在这里，但必须重算 SET，不能累加 |
| 看板 | `ProjectDashboardService`、`ProjectDashboardSnapshotService` 仍直接读 `poll_results` | 删除明细前必须改成“近期明细 + 历史汇总/冻结快照” |
| 售后报告 | `ReportService` 生成报告详情时仍直接读 `poll_results`；代码结构支持 `biweekly/monthly/quarterly`，当前产品策略禁用这些类型 | 需要新增独立报告周期 freeze，报告渲染读 FROZEN 快照；不能只假设季报 |
| 文章正文 | `article_draft_version.content_markdown` 保存正文 | 已发布终态后逐出对象存储，热库置空 |
| 对象存储 | `MinioStorageService` 已有上传、读取、删除、预签名能力 | 业务归档不得直接依赖 MinIO 类，需新增 provider-neutral 抽象 |
| 售前 LLM | `presale_ai_call` 已有 token、duration、model snapshot | 可先作为 `llm_usage_daily_summary` 的可靠来源 |
| 清理样板 | `PresaleExportCleanupService` 有锁、批量、重试、失败标记 | 可作为 retention job 风格参考 |

### 当前直接读取明细表的风险清单

| 调用点 | 当前读取 | 改造要求 |
|---|---|---|
| `ProjectDashboardService` | 分页读 `poll_results` 命中明细 | 按“客户可见视图矩阵”决定：热窗口内可读明细，窗口外只能读 summary/freeze；若窗口外仍要求单条命中明细，则必须新增可支撑该视图的冻结快照 |
| `ProjectDashboardSnapshotService` | 读 `poll_results.keyword_text_snapshot` 做词频 | 改读 `poll_keyword_daily_summary` |
| `ReportService.aggregateSummary()` | 读 `poll_daily_stats`，同时存在无用/待清理的 `poll_results` 查询 | 汇总读日汇总表 |
| `ReportService.buildDetailData()` | 全量读报告期间 `poll_results` | 改读报告周期 FROZEN 快照；若未来启用月报/双周报，必须有对应周期快照或保留明细覆盖窗口 |
| `ReportService.buildPlatformBreakdown()` | 读 `poll_daily_stats` | 可继续读 `poll_daily_stats` 或改读 `poll_platform_daily_summary` |
| `ProjectService` 删除项目 | 同步硬删项目下文章、分发、轮询、报告等业务行 | 必须改为下户生命周期流程，受合规尾巴期门控，不能即时硬删交付物和对象存储引用 |

## 2. 数据分类与处理原则

### A. 轮询明细类

范围：`poll_results`、`poll_batch_shards`、`poll_batch_shard_items`。

长期价值：客户看板趋势、报告周期快照、问题与平台维度统计。

处理原则：

- 热明细只保留滚动展示窗口 + 缓冲。第一版建议：滚动展示窗口按 90 个自然日，缓冲 30 个自然日，总计 120 个自然日；后续如产品改成“三个日历月”，必须单独调整 cutoff 计算，不能混用。
- 120 天必须大于等于“报告周期结束到 freeze 完成”的最大滞后；若 freeze SLA 超过 30 天，应先扩大缓冲再允许清理。
- 长期趋势走日汇总。
- 报告展示内容走报告周期冻结快照；季度只是其中一种周期。
- `poll_results` 删除门控必须同时满足：封口、对账通过、匹配报告周期 FROZEN、读路径已切、dry-run 跑过。
- 日期归属统一按 `Asia/Shanghai` 的 `batch_date`，跨季/跨月边界不按写入时间或完成时间重新归属。

### B. 交付物正文类

范围：`article_draft_version.content_markdown`、报告周期 freeze 中的 Q&A 原文、客户交付快照。

处理原则：

- 交付物不按年龄硬删。
- 热库大字段逐出对象存储，DB 只保存逻辑 key、checksum、归档时间。
- 发布链接只做展示/告警，不作为正文置空门控。

### C. 过程/调试类

范围：prompt/input/response 快照、LLM raw、发布 payload、diagnostics、兜底 dump。

处理原则：

- 客户不直接消费，终态后固定窗口 slim/delete。
- 每个 slim 动作也必须有对应汇总/记录已生成作为前置。

## 3. P0 补正后的总体开发顺序

### 已锁定决策记录

- 文章交付物：逐出对象存储，不硬删；发布链接仅用于展示/告警，不作为正文置空门控；真删锚合同生命周期。
- 轮询明细：热保留 120 自然日；90 天外看板只读日汇总，不提供单条命中明细。
- 季报：唯一消费 `poll_results` 明细的报告，严格对齐日历季度，按 `batch_date` 的 Asia/Shanghai 日归属。
- 售前 LLM 成本：售前诊断报告生成时没有客户实体，v1 仅按售前域单独统计，不加 `company_id/project_id/brand_id`，也不设“未归因”桶。
- baseline、凭证/token/session、`audit_log` 本期不在范围。

1. 钉死客户可见视图矩阵、封口定义、晚到数据规则、对账算法和窗口数字。
2. 容量盘点 SQL/管理接口。
3. `V228` 建汇总表、retention 审计表和已清理 slice 记录表。
4. 新增对象存储抽象接口，现有 MinIO 作为 adapter。
5. 挂日汇总到 `DispatchPollAggregationService`，按 P0-1 使用 recompute-SET。
6. 历史回填，按日期和项目分批，重复执行结果不变。
7. `V229` 建报告周期 freeze 表；实现异步 freeze job、周期结束触发和堆积监控。
8. 完成季报 freeze SELECT：`report_type=quarterly`、日历季度、全量、每 `问题身份 × 平台` 最新一条。
9. 看板/报告读路径切换。
10. `V232` 建发布记录与文章归档字段；实现正文归档 dry-run。
11. `V233` 补 payload purge marker；实现 publish record 补偿任务。
12. retention scheduler 上线，默认 dry-run；满足 dry-run 晋级标准后再开启 execute。

## 4. Flyway 迁移规划

当前真实迁移已到 `V227__verify_baijiahao_schedule_capability.sql`，生命周期治理从 `V228` 开始；因并行分支已占用 `V230/V231`，文章归档与 purge marker 使用 `V232/V233`。

### V228__data_lifecycle_summary_and_run.sql

新增：

- `poll_keyword_daily_summary`
- `poll_platform_daily_summary`
- `llm_usage_daily_summary`
- `article_generation_daily_summary`
- `data_retention_run`
- `data_retention_purged_slice`

关键约束：

- 所有唯一键不包含可空维度。
- 推荐 `dim_hash CHAR(64) NOT NULL` 作为幂等唯一键。
- 维度列可以保留 nullable 语义用于查询，但不能参与唯一性判断。

`dim_hash` canonical 规则必须固定：

```text
version=1
timezone=Asia/Shanghai
null=<NULL>
separator=\u001F
fields 按表定义顺序串联后 sha256 hex
```

`poll_keyword_daily_summary` 的身份键必须避免把可变展示文本放进 hash。

正确规则：

```text
keyword_result_id 非空:
  hash(project_id, batch_date, question_tier, "ID:" + keyword_result_id)

keyword_result_id 为空:
  hash(project_id, batch_date, question_tier, "TEXT:" + normalized_keyword_text)
```

禁止把 `keyword_result_id` 和 `keyword_text_snapshot` 同时作为身份键。`keyword_text_snapshot` 是展示快照字段，只在 upsert 中 SET 更新；否则同一逻辑关键词会因文本快照变化裂成多行。

其他汇总表的 hash 输入按固定维度列定义，展示快照字段不参与身份，除非它是无 ID 场景的唯一身份来源。

示例：`poll_keyword_daily_summary` 的展示字段更新：

```text
keyword_text_snapshot = VALUES(keyword_text_snapshot)
```

`llm_usage_daily_summary` v1 只统计售前域，不与文章生成/问题池轮询消耗混表。售前报告生成时没有客户实体，真实归因单元是 `presale_report.id`，因此不得增加 `company_id/project_id/brand_id` 外键列，也不需要“未归因”桶。

售前 LLM 汇总维度：

```text
usage_date + report_id + stage + model_id_snapshot + call_status
```

展示快照：

- `brand_name_snapshot`
- `industry_snapshot`
- `region_snapshot`
- `model_name_snapshot`

`dim_hash` 只使用上述真实维度，按 canonical 规则处理空值：字段顺序固定为表列顺序，`NULL` 归一为 `<NULL>`，字符串 trim 后归一。后续若把文章生成或问题池轮询的 LLM 成本纳入治理，另建带真实客户/项目归因的汇总，不并入售前 v1 表口径。

### V229__report_period_freeze.sql

新增：

- `report_period_freeze`
- `report_period_freeze_guard`

字段：

```sql
report_period_freeze (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  report_type VARCHAR(32) NOT NULL,
  period_key VARCHAR(16) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  source_checksum CHAR(64) NOT NULL,
  snapshot_object_key VARCHAR(500) NULL,
  object_checksum CHAR(64) NULL,
  object_size_bytes BIGINT NULL,
  source_row_count INT NOT NULL DEFAULT 0,
  metrics_json JSON NULL,
  lock_owner VARCHAR(64) NULL,
  lock_expires_at DATETIME NULL,
  freeze_started_at DATETIME NULL,
  frozen_at DATETIME NULL,
  failed_at DATETIME NULL,
  error_message VARCHAR(2000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_freeze_version (project_id, report_type, period_key, version_no),
  KEY idx_report_freeze_gate (project_id, report_type, period_key, status, version_no)
)
```

说明：

- 报告快照按报告周期和版本打包成一个对象：`retention/freeze/report-period/project-{projectId}/{reportType}/{periodKey}/v{versionNo}.json`。
- `period_key` 必须可稳定复算：季度可用 `2026Q1`，月报可用 `202601`，双周/自定义区间可用 `20260101_20260114`。
- 不做一条 Q&A 一个对象，避免对象数爆炸。
- `FROZEN` 后拒绝原地更新；重复触发且源数据 checksum 未变时 no-op；只有显式“重生成”或检测到源数据变化时才生成新 version。
- v1 启用报告类型仅 `quarterly`，严格按 Asia/Shanghai 的 `batch_date` 归属日历季度。
- v1 freeze 内容为季度内全部 `问题身份 × 平台`；每组只取最新一条，排序为 `COALESCE(poll_results.updated_at, poll_results.created_at) DESC, poll_results.id DESC`，禁止用 `batch_no` 近似最新。
- 问题身份与日汇总一致：`keyword_result_id` 非空用 `ID:{keyword_result_id}`；为空时用 `TEXT:{lower(trim(keyword_text_snapshot))}`，不得漏掉未匹配关键词。

### V232__article_publish_record_and_archive_columns.sql

新增：

- `article_publish_record`

修改：

- `article_draft_version` 增加：
  - `content_object_key VARCHAR(512) NULL`
  - `content_checksum CHAR(64) NULL`
  - `content_archived_at DATETIME NULL`
  - `content_purged_at DATETIME NULL`

`article_publish_record` 唯一性使用非空来源字段：

```sql
source_type VARCHAR(32) NOT NULL, -- distribution_task / self_media_schedule / manual
source_id BIGINT NOT NULL,
UNIQUE KEY uk_article_publish_source (source_type, source_id)
```

不要使用 `UNIQUE(distribution_task_id)` + `UNIQUE(self_media_schedule_id)` 作为主防重机制，因为可空唯一键会漏防。

`data_retention_run` 至少记录：

- `run_type`: `dry_run/execute`
- `domain`
- `action`: `summarize/slim/archive/delete/mark/object_delete`
- `status`
- `cutoff_at`
- `scanned_rows`
- `affected_rows`
- `bytes_processed`
- `metrics_json`
- `approved_by/approved_at`：execute 晋级审批人和时间，可为空，仅 dry-run 不要求
- `started_at/finished_at`

`data_retention_purged_slice` 至少记录：

- `domain`: 第一版为 `poll_results`
- `project_id`
- `batch_date`
- `question_tier`
- `purged_at`
- `retention_run_id`
- `status`
- `metrics_json`

用途：已 execute 删除的 slice 默认拒收晚到明细。热路径写入只在 `batch_date < today(Asia/Shanghai) - 120 days` 时查询该表，近期写入直接短路不查。

并发约束：`PollRetentionHandler` execute 删除 `poll_results` 前，必须先获取与 `PollSummaryRecomputeService` 相同的 `data_retention_recompute_slice_lock`（`domain='poll_results' + project_id + batch_date + question_tier`），并在锁内完成最终门控复查、子表删除、明细删除和 `data_retention_purged_slice` 写入。purge 与 recompute 必须通过同一把 slice 锁互斥，避免 recompute 通过 purged 复查后又与 purge 并发读写同一 slice。

### V233__data_lifecycle_purge_marker_columns.sql

给以下表增加 purge marker，避免重复 slim：

- `distribution_tasks.payload_purged_at`
- `batch_article_generation_task.snapshot_purged_at`
- `presale_ai_call.payload_purged_at`

### V237__article_publish_record_url_quality.sql

`V236` 已由其它分支占用，本迁移给 `article_publish_record` 增加发布链接分级字段：

- `url_quality VARCHAR(32) NOT NULL DEFAULT 'missing'`：`public_url/preview_url/manage_url/missing`。
- `url_source VARCHAR(64) NULL`：记录证据来源字段，例如 `distribution_tasks.published_url`、`self_media_publish_schedule.platform_publish_id`。
- `verified_at DATETIME NULL`：发布交付证据被确认的时间。

补偿任务覆盖：

- `distribution_tasks`：发布成功/提交完成/平台审核已发布或下线的任务；有公网链接写 `public_url`，无公网链接但有平台文章 ID/发布 ID 或终态证据时写 `missing`。
- `self_media_publish_schedule`：`published_confirmed` 的自媒体定时发布；有 `platform_published_url` 写 `public_url`，只有 `platform_publish_id` 或确认时间时写 `missing`。
- 以上补偿只补 `article_publish_record`，不触发正文归档、不置空正文。

### V240__repair_data_lifecycle_v228_remaining_delta.sql

`V235` 已在本地库应用，不再编辑。该迁移补早期 `V228` + 已应用 `V235` 之后仍可能遗留的真实结构差异：

- 条件补 `poll_results.idx_poll_result_date_project_tier(batch_date, project_id, question_tier)`，已存在则 no-op。
- `poll_keyword_daily_summary.contact_mention_total` 改为 `BIGINT`。
- `poll_platform_daily_summary.contact_mention_total` 改为 `BIGINT`。
- `article_generation_daily_summary.dim_hash` 注释修正为最终版口径：`generation_date,project_id,article_type,target_channel,status`。

对已经跑过最终版 `V228` 的全新库，本迁移只会执行等价 `MODIFY` 和索引 no-op。

### V241__repair_article_generation_checksum_contract.sql

`V240` 已在本地库应用，不再编辑。该迁移重申 `article_generation_daily_summary.source_checksum` 为最终版 `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin`，对最终版新库和已修复旧库均为等价 `MODIFY`。

### V242__presale_judge_raw_purge_marker.sql

给 `presale_ai_prompt_judge_result` 增加 `raw_purged_at` 和 `idx_presale_judge_raw_purge`，作为裁判 raw slim 的重复候选过滤 marker。该迁移只补 marker，不执行任何置空。

## 5. P0-1：日聚合必须 recompute-SET

### 禁止实现

不得在实时聚合或回填中写：

```sql
hit_count = hit_count + VALUES(hit_count)
```

也不得把当前 batch 结果累加到已有日汇总。原因：实时聚合、历史回填、重试、边界日重复执行都会导致指标虚高。

### 正确实现

按日期 + 项目 + 维度从源明细完整重算，然后 upsert SET。

伪流程：

```text
aggregateBatchIfReady(batchId)
  -> batch 全部分片终态
  -> 写/更新 poll_daily_stats
  -> collect affected project_id + batch_date + question_tier
  -> recomputePollKeywordDaily(projectId, date, tier)
  -> recomputePollPlatformDaily(projectId, date, tier)
```

upsert 语义：

```sql
INSERT INTO poll_keyword_daily_summary (...)
VALUES (...)
ON DUPLICATE KEY UPDATE
  platform_count = VALUES(platform_count),
  poll_count = VALUES(poll_count),
  hit_count = VALUES(hit_count),
  effective_hit_count = VALUES(effective_hit_count),
  site_mention_count = VALUES(site_mention_count),
  contact_mention_count = VALUES(contact_mention_count),
  source_row_count = VALUES(source_row_count),
  source_checksum = VALUES(source_checksum),
  updated_at = CURRENT_TIMESTAMP;
```

整片重算语义：

- slice 定义为 `project_id + batch_date + question_tier`。
- 每次 recompute 必须从该 slice 的存活源明细完整重算。
- upsert present 行后，必须删除本 slice 内本次重算结果不再出现的 summary 行，或将其计数 SET 为 0 并标记 inactive；第一版建议物理删除 summary 僵尸行。
- 禁止只 upsert 当前 batch/当前出现维度，否则“上个 batch 出现、这次不出现”的关键词/平台会残留，词频偏高。

### 历史回填

`PollSummaryBackfillJob` 必须和实时聚合调用同一套 recompute 方法。

回填策略：

- 可按 `project_id + batch_date + question_tier` 切片。
- 可重复执行。
- 同一天跑两次，汇总结果必须完全一致。
- 回填和实时聚合重叠时不产生重复累加。
- 候选扫描必须支持 keyset 游标 `(batch_date, project_id, question_tier)` 续扫，不使用深 offset；每页多取一行或等价方式返回 `hasMore`，避免超过 limit 后静默截断。
- `dry-run` 默认只返回候选和游标，不写 summary；显式 execute 时必须写入 `data_retention_run` 审计，记录候选数、重算数、跳过数、失败数和下一页游标。

### 验收

- 同一天聚合执行两次，`poll_keyword_daily_summary` 和 `poll_platform_daily_summary` 的计数不变。
- 删除一日 summary 后重跑回填，结果与明细对账一致。
- 回填过程中断后继续执行，不产生重复行、不产生虚高。
- 回填候选超过单页 limit 时响应必须返回 `hasMore=true` 和下一页 cursor；使用 cursor 续扫会前进到下一批 slice。
- 回填 execute 有 `data_retention_run` 审计记录。

## 6. P0-2：报告周期 freeze 异步化

### 原则

不得把对象上传、读回、checksum 校验放入同步报告请求流程。报告接口只读取已有 `FROZEN` 快照；freeze 由异步 job 负责。

当前代码虽然按产品策略禁用了 `biweekly/monthly/quarterly` 售后报告生成入口，但 `ReportService` 和数据模型仍保留 `periodStart/periodEnd` 的通用报告周期能力。因此生命周期方案不能假设只有严格日历季度报告。

### 触发来源

必须至少支持两个触发：

1. 周期结束定时触发：扫描上一报告周期已封口项目，生成 freeze。
2. 手动补偿触发：管理员对某项目某报告类型、某周期触发重跑。

不能只靠“有人打开报告”懒触发，否则未被访问的周期永远不会冻结，`poll_results` 删除门控会永久关闭。

### 异步任务

建议组件：

```text
ReportPeriodFreezeScheduler
  -> ReportPeriodFreezeService
  -> ReportPeriodFreezeGuardService
  -> ReportPeriodSnapshotAssembler
  -> ObjectStorageService
```

`report_period_freeze_guard` 语义：

- acquire by `project_id + report_type + period_key`
- 有 TTL
- 过期可 takeover
- 同一项目同一报告周期同一 version 只允许一个任务生成

### freeze 标准序列

```text
1. acquire guard
2. 确认报告周期封口
3. 创建/复用 CREATING freeze row
4. 从明细组装快照 JSON：季度内全量 `问题身份 × 平台`，每组按 `COALESCE(updated_at, created_at) DESC, id DESC` 取最新一条
5. 计算 sha256
6. ObjectStorageService.put(logicalKey, bytes)
7. ObjectStorageService.getBytes(logicalKey)
8. 校验 checksum
9. 回写 snapshot_object_key / object_checksum / source_row_count / object_size_bytes
10. 标记 FROZEN
11. release guard
```

第 10 步之前，retention 门控一律不放行。

### 报告读取

`ReportService` 的历史报告读取应调整为：

- 对于已 FROZEN 的周期：读取 `report_period_freeze.snapshot_object_key`。
- 对于近期未到删除窗口的数据：仍可读热明细。
- 对于应该 FROZEN 但缺失的周期：返回“数据准备中/冻结缺失”状态，并触发告警，不回退扫全量旧明细。

### 堆积监控

新增指标：

```text
oldest_unfrozen_poll_detail_days
```

口径：

- 找到最早一条超过滚动窗口 + 缓冲、但所属报告周期未 FROZEN 的 `poll_results.batch_date`。
- 上报距今天数。
- 超阈值告警，避免 freeze 漏跑导致清理静默失效。

### 封口定义与晚到数据

轮询日期封口是 `poll_results` 删除和报告 freeze 的共同前置，必须由代码统一判断，不能各 handler 自己猜。

终态集合：

| 对象 | 终态状态 | 是否计入封口 |
| --- | --- | --- |
| `poll_batches.status` | `finished` | 是 |
| `poll_batches.status` | `finished_with_failures` | 是，失败项作为失败结果进入汇总/快照 |
| `poll_batches.status` | `failed` | 是，作为失败 batch 进入对账口径 |
| `poll_batch_shards.status` | `completed` | 是 |
| `poll_batch_shards.status` | `failed` | 是 |

卡住安全阀：

```text
batch_date <= today(Asia/Shanghai) - 7 days
AND batch/shard 仍处于 ready/planning/running
  -> 标记为 failed_for_retention 或 failed
  -> 写告警和 retention 审计
  -> 该日允许继续封口判断
```

晚到数据规则：

- 一旦某 `project_id + batch_date + question_tier` 已完成 execute 删除，默认不再接受该 slice 的新 `poll_results` 写入。
- 补抓/重跑如果命中过已清理 slice，必须定向丢弃并告警；不得重新写明细，也不得静默改写 summary。
- 如确需修复历史数据，必须走“历史重开”流程：暂停该 slice retention、恢复或重建必要明细、重算 summary、重建 freeze，最后重新执行清理。

## 7. P0-3：业务口径对 poll_results 清理的门控

汇总基建不被业务口径阻塞，但 `poll_results` execute 删除被业务口径阻塞。这里的口径不是简单业务参数，而是数据覆盖完整性前置：客户能看到的每个视图、时间范围、下钻粒度，都必须能被 summary/freeze 覆盖，否则删除明细会制造空页面。

### 客户可见视图矩阵

实现前必须把下表逐格确认并固化到需求/验收。第一版建议口径如下：

| 客户可见视图 | 时间范围 | 下钻粒度 | 删除后数据来源 | 是否允许删除 `poll_results` |
| --- | --- | --- | --- | --- |
| 看板趋势统计 | 最近 90 天 | 日、平台、问题层计数 | 热明细 + 日汇总 | 是，90 天外不依赖明细 |
| 看板词频/命中统计 | 最近 90 天 | 关键词、平台、日 | `poll_keyword_daily_summary` / `poll_platform_daily_summary` | 是，需读路径已切 |
| 看板命中明细列表 | 最近 90 天 | 单条问题 x 平台回答 | 热 `poll_results` | 仅删除 120 天外 |
| 看板历史命中明细 | 90 天外 | 单条问题 x 平台回答 | 第一版不提供；若产品要求提供，必须新增 dashboard detail freeze | 未新增 freeze 前不允许删除对应明细 |
| 售后报告详情 | 报告周期内 | Q&A 全量或精选 | `report_period_freeze` | 对应周期 FROZEN 后允许 |
| 售后报告趋势/平台汇总 | 报告周期内 | 日、平台、问题层计数 | 日汇总/平台日汇总 | 读路径切换后允许 |

结论：

- 若客户能在看板上查看 120 天外的单条命中明细，当前日汇总 + 报告周期 freeze 不足以支撑删除，必须新增 `dashboard_poll_detail_freeze` 或等价快照。
- 若产品只保留 90 天内单条明细，120 天外看板只展示趋势/词频/平台统计，则当前 summary 方案可覆盖。
- 这张矩阵必须作为 `PollRetentionHandler` 的配置/验收依据，不能只存在口头结论。

必须优先拍板：

1. 三月看板下钻粒度到哪一层？
   - 只看问题/平台统计？
   - 是否需要历史单条模型回答原文？
   - 是否需要按日序列下钻？
2. v1 消费 `poll_results` 明细的报告类型只有季报，且严格对齐日历季度；未来恢复月报/双周报/自定义区间报告时，必须先把“已启用且消费明细报告类型”配置扩展到对应 freeze。
3. 报告 Q&A 冻结全量数据，不做精选。
4. 报告展示回答按每 `问题身份 × 平台` 最新一条，不做日序列；最新排序使用真实落库时间 `COALESCE(updated_at, created_at) DESC, id DESC`。

在 `poll_results` execute 门控未全部满足前：

- 可以建汇总表。
- 可以挂 recompute 聚合。
- 可以历史回填。
- 可以 dry-run 计算候选。
- 不得 execute 删除 `poll_results`。

`PollRetentionHandler` 的 execute 前置：

```text
date < rollingWindow + buffer
AND all batches of date are terminal
AND daily summary reconciliation passed
AND matching report period freeze status = FROZEN
AND report/dashboard read path switched
AND dry-run success exists
AND business freeze scope decision recorded
```

execute 并发门控：每个通过上述前置的 slice，必须先获取 `data_retention_recompute_slice_lock` 中同一把 `poll_results` slice 锁，再在锁内复查门控并执行删除。不得在未持锁时删除明细或写入 `data_retention_purged_slice`。

对账算法必须明确：

```text
1. 对每个 slice 计算 live_source_count = COUNT(*) from poll_results
2. 对同一 slice 计算 summary_source_count = SUM(summary.source_row_count)
3. 精确匹配才视为通过，不设容差
4. 不一致时重算该 slice summary
5. 重算后如果 live_source_count == summary_source_count：放行
6. 重算后如果 live_source_count < old summary_source_count：说明可能发生过合法单条删除，以重算结果刷新 summary 后放行
7. 重算后如果 live_source_count > summary_source_count 或 checksum 不一致：告警并跳过，不删除
```

对账失败执行语义：

```text
daily summary reconciliation failed
  -> 对该日重算一次
  -> 仍不一致且不是合法删除导致的 summary 刷新：告警 + 标记 skipped + 跳过该日
  -> 绝不继续删除 poll_results
```

## 8. 对象存储抽象与归档协议

### MinIO -> 腾讯云 COS 迁移口径

- 目标云存储为腾讯云 COS；业务代码仍保持 provider-neutral `ObjectStorageService` 抽象，不把 COS bucket、appid、region、endpoint 写入业务 key。
- DB 仅保存逻辑 key，例如 `archive/article/...`、`retention/freeze/report-period/...`；迁移语义是“同一逻辑 key 从 MinIO verify-copy 到 COS”，DB 引用不变。
- `geo.storage.provider=minio|cos` 控制当前读写 provider，默认 `minio`；切换期允许 `provider=cos` 且 `read-fallback-to-minio=true`，用于 COS 读不到时回落 MinIO。
- 迁移入口 `POST /api/data-retention/object-storage/migrate` 默认 dry-run；execute 还需 `geo.storage.migration.execute-enabled=true`。迁移按 DB key 登记清单驱动，不扫描整桶。
- execute 只做 `read MinIO -> put COS(same key) -> read COS -> SHA-256 校验`；本阶段不删除 MinIO 对象、不置空 DB 正文。

### 新增抽象

业务代码统一依赖：

```java
public interface ObjectStorageService {
    void put(String key, byte[] bytes, String contentType);
    byte[] getBytes(String key);
    boolean exists(String key);
    ObjectStat stat(String key);
    String presignedGetUrl(String key, Duration ttl);
    void delete(String key);
}
```

现状实现：

- `MinioObjectStorageAdapter` 包装现有 `MinioStorageService`。

未来实现：

- `CosObjectStorageAdapter`。

约束：

- DB 只存逻辑 key，不存 bucket、endpoint、完整 URL。
- 归档/冻结/读取/删除不得直接依赖 MinIO SDK 或 `MinioStorageService`。

### 文章正文归档

key：

```text
archive/article/{projectId}/{articleId}/v{versionNo}.md
```

标准序列：

```text
1. 读取 content_markdown
2. 计算 sha256
3. exists(key) 且 checksum 一致则视为已归档
4. put(key, bytes)
5. getBytes(key)
6. 校验 checksum
7. 回写 content_object_key/content_checksum/content_archived_at
8. 置空 content_markdown，写 content_purged_at
```

多版本策略：

- 已发布最终版必须归档到对象存储，再允许置空 DB 正文。
- 非最终历史版本默认按过程草稿数据处理：短窗口保留后直接 slim/delete；如果业务要求可回看历史修订，则需显式开启并按同一归档协议入对象存储。
- 未发布、待编辑、待重发的活动版本不得直接置空正文，除非 rehydration 真实代码路径已上线并通过校验。

多渠道发布终态：

- 单篇文章只要存在任一渠道 `published/success` 且形成 `article_publish_record`，该版本正文就属于交付物，必须先归档。
- 无公网链接渠道也必须形成 `article_publish_record`；没有公网 URL 但存在平台文章 ID、发布 ID 或发布确认时间时，`url_quality='missing'`，仍可作为正文归档前置交付记录。
- 若 A 渠道已发布、B 渠道失败、C 渠道待发，该版本可以归档，但不得置空到会阻断 C 渠道继续发布；只有 rehydration 路径已上线并覆盖发布流程后才允许置空。
- 若所有渠道均失败或取消，且没有公网/客户交付记录，该版本按过程草稿处理，进入短窗口 slim/delete。
- 渠道终态集合第一版按：`published/success/completed/failed/cancelled/dead_letter`；`pending/running/token_issued/filled` 不算文章版本可置空终态。

本轮正文归档 dry-run：

- 入口：`POST /api/data-retention/articles/archive/dry-run`。
- 参数：`projectId` 可选；`publishedStartDate/publishedEndDate` 可选；`minPublishedAgeDays` 默认 30；`limit` 默认 100、上限 1000。
- 候选门控：`article_draft.status IN ('published','distributed')`、`article_draft_version.version_no = article_draft.current_version_no`、存在 `article_publish_record`、已过热保留窗口、`content_markdown` 非空且 `content_purged_at IS NULL`。
- 输出：预计 object key、正文 checksum、预计字节数、blocked reason。
- 审计：写 `data_retention_run(domain='article_body_archive', mode='dry_run')`。
- 本轮不写对象、不回写 `content_object_key/content_checksum/content_archived_at`，也不置空 `content_markdown`。

### 重发布回灌

发布/改稿流程必须支持：

```text
content_markdown 非空 -> 直接使用
content_markdown 为空且 content_object_key 非空 -> 从对象存储读取 -> 校验 checksum -> 进入发布/编辑流程
```

这必须是实际代码路径，不能只停留在注释或人工操作。

本轮先落服务层正文提供器 `ArticleBodyProvider.getArticleBody(versionId)`：DB 正文优先；DB 为空时通过 `ObjectStorageService` 读取归档对象并校验 `content_checksum`；对象缺失、读取失败或 checksum 不一致时返回明确错误，不得静默返回空正文。

## 9. poll_results 删除顺序

删除顺序必须先子后父，避免 `poll_batch_shard_items.poll_result_id ON DELETE SET NULL` 引发大量写放大：

```text
1. 获取 data_retention_recompute_slice_lock 中同一把 poll_results slice 锁，并在锁内复查门控
2. 删除过窗口且 batch 终态的 poll_batch_shard_items
3. 删除对应 poll_batch_shards
4. 删除通过门控的 poll_results
5. 写入 data_retention_purged_slice
6. 暂不删除 poll_batches
```

第一版不做按月分区。原因：

- `poll_results` 有多处外键。
- `poll_batch_shard_items` 引用 `poll_results`。
- MySQL 分区表与外键组合限制较多。

短期先小批量 delete + 审计；稳定后再评估冷热表拆分或去 FK 化后分区。

本轮 `PollRetentionHandler` 只实现 dry-run：

- 入口：`POST /api/data-retention/poll-results/dry-run`。
- 参数：`projectId/startDate/endDate/questionTier` 可选；`cursorBatchDate/cursorProjectId/cursorQuestionTier` 用于 keyset 续扫；`hotRetentionDays` 默认 120；`stuckBatchSealDays` 默认 7；`limit` 默认 100、上限 1000。
- 候选：`poll_results` 中 `batch_date <= today(Asia/Shanghai) - hotRetentionDays` 的 `(project_id,batch_date,question_tier)` slice。
- 分页：按 `(batch_date, project_id, question_tier)` keyset 游标推进，返回 `hasMore/nextCursor*`，避免大范围 dry-run 静默截断。
- 每个候选 slice 必须先获取与 `PollSummaryRecomputeService` 相同的 `data_retention_recompute_slice_lock(domain='poll_results',project_id,batch_date,question_tier)`，并在锁内复查所有门控。
- 输出预计删除影响：`poll_batch_shard_items`、`poll_batch_shards`、`poll_results` 行数；不删除 `poll_batches`。
- 审计：写 `data_retention_run(domain='poll_results', mode='dry_run')`。
- 本轮不提供 execute 入口，不删除任何行，不写 `data_retention_purged_slice`。

dry-run 门控：

- 已过热保留窗口。
- 未命中 `data_retention_purged_slice`。
- 封口：`poll_batches.status IN ('finished','finished_with_failures','failed')`；若 `planning/ready/running` 且 `batch_date <= today - 7`，dry-run 标记 warning `stale_batch_would_be_failed_by_safety_valve`，代表 execute 前需按安全阀失败封口。
- 对账：`poll_keyword_daily_summary.source_row_count` 汇总必须精确等于存活 `poll_results` 行数；`poll_platform_daily_summary.source_row_count` 也必须精确等于存活 `poll_results` 行数。不一致则 blocked，execute 前必须重算后再评估。
- 报告 freeze：覆盖该 `batch_date` 的所有已启用且消费明细的报告类型都必须 `FROZEN`。当前启用集合为 `quarterly`，但实现按 report type 列表循环判定，后续启用月报/自定义报告时扩展列表即可。
- 读路径已切、客户可见口径已记录：当前作为代码级 rollout gate 在 dry-run 输出中显式展示；execute 前需配置化。

## 10. slim 门控

### 售前 LLM

`presale_ai_call.raw_response/request_prompt_content` 置空前必须满足：

- 该 call 已覆盖进 `llm_usage_daily_summary`。
- 售前报告版本已终态：`presale_report_version.generation_status IN ('DONE','FAILED')`，且不处于可自动重试队列；`INIT/QUEUED/RUNNING` 一律不得 slim。
- 第一版策略定为“不归档售前 LLM raw，满足终态和汇总覆盖后直接 NULL”。如果后续产品要求 prompt trace 长期展示原文，再新增对象归档列并把策略改为 verify-before-null。
- prompt trace 页面只能长期展示结构化字段和轻量摘要，不再依赖 raw prompt/raw response 原文。

### 裁判 raw

`presale_ai_prompt_judge_result.raw_judge_response` 置空前必须满足：

- `judge_status='SUCCESS'` 且结构化裁判字段已存在：认知类至少已有 `sentiment_score/attribute_hit_rate` 或 `judge_payload_json`，对比类至少已有 `preferred_brand` 或 `judge_payload_json`。
- 裁判调用当前不可靠写入 `presale_ai_call`，因此 `llm_usage_daily_summary` 对裁判 raw 只作为参考指标，不作为置空硬门控。
- 售前报告版本已终态：`presale_report_version.generation_status IN ('DONE','FAILED')`。
- 由 `presale_ai_prompt_judge_result.raw_purged_at` 防重复；本轮仅 dry-run，不写 marker、不置空。

### 发布 payload

`distribution_tasks.request_payload/response_payload/fill_payload` 置空前必须满足：

- `article_publish_record` 已生成。
- 发布 URL、平台文章 ID、平台发布 ID、状态、发布时间等轻字段已长期保存。

### 本轮 slim dry-run 入口

`POST /api/data-retention/slim/dry-run`，参数：

- `domain`：`all/presale_ai_call/presale_judge_raw/article_generation_task/distribution_payload`，默认 `all`。
- `startDate/endDate`：按各域候选的创建/完成时间过滤，可空。
- `limitPerDomain`：默认 100，上限 1000。

dry-run 输出候选、可 slim 数、blocked 数、blocked reasons、候选字段列表，并写入 `data_retention_run(domain='slim_payload', mode='dry_run')`。本轮不提供 execute 入口，不置空任何字段。

## 11. 发布链接质量分级

`article_publish_record.url_quality`：

- `public_url`
- `preview_url`
- `manage_url`
- `missing`

用途：

- 前端展示“查看公网原文”或“查看我方存档”。
- 对应渠道应该有公网链接但缺失时触发发布质量告警。

非用途：

- 不作为 `content_markdown` 置空门控。
- 不依赖外链可达性判断是否归档。

## 12. Retention 框架

组件：

```text
DataRetentionScheduler
  -> DataRetentionLockService
  -> DataRetentionRunService
  -> PollRetentionHandler
  -> ArticleRetentionHandler
  -> DistributionRetentionHandler
  -> PresaleAiRetentionHandler
  -> ObjectStorageRetentionHandler
```

每个 handler 标准流程：

```text
1. 计算候选
2. 校验持久副本/汇总/冻结/归档
3. dry-run 只记录候选与预计影响
4. execute 小批量执行
5. 写 data_retention_run
6. 失败告警，不吞错
```

dry-run 到 execute 晋级标准：

- 同一 domain 连续 7 个自然日 dry-run 成功。
- 对账通过率 100%，无 skipped 且无未处理 P0 告警。
- dry-run 候选量、预计删除量、预计释放容量每日记录到 `data_retention_run.metrics_json`。
- 首次开启 execute 必须由技术负责人和业务负责人双确认，记录 `approved_by/approved_at`。
- execute 第一周按小批量限流执行；任一 P0 告警出现即自动降回 dry-run。

本轮 `DataRetentionScheduler` 只编排 dry-run，默认关闭：

- 配置：`geo.retention.scheduler.enabled=false`，开启后按 `geo.retention.scheduler.cron` 运行。
- 编排 domain：`slim_payload`、`article_body_archive`、`poll_results`、`object_storage_orphan`。
- 每个 domain 调用对应 dry-run service，异常只记录告警并继续其它 domain；各 dry-run service 自己写 `data_retention_run` 审计。
- `geo.retention.execute-promotion.*` 仅作为晋级条件配置占位：连续 7 天 dry-run 成功、100% 对账、无 P0 告警、双负责人审批。本轮不调度、不暴露任何 execute。

`poll_results` 域 execute 必须额外检查：

- P0-2 freeze 已完成。
- P0-3 业务口径已记录。
- P1-1 读路径已切换。
- P1-2 对账通过。
- P1-3 子表先删策略已执行。

本期范围说明：

- `audit_log`、`extension_session`、`self_media_cookie_credential`、第三方凭证/token/session 类表不纳入本期 P0 数据减压范围。
- 这些表涉及安全审计、登录态、凭证加密和合规追踪，应单独设计安全生命周期策略，不能混在业务大字段 slim 中处理。

### 对象 key 引用登记

文章正文归档不是唯一展示依赖。被已归档文章正文引用的图片、附件、封面和其它资源，其生命周期不得短于正文展示需求：要么不清理，要么随正文一起归档并纳入引用登记。后续任何归档/清理服务不得删除仍被已发布正文、归档正文或报告快照引用的资源。

`ObjectStorageRetentionHandler` 的“无活引用”检查必须来自一份集中清单，新增归档类型时同步登记，避免误删仍被业务引用的对象。

第一版清单至少包含：

| 表 | 列 | 语义 |
| --- | --- | --- |
| `article_draft_version` | `content_object_key` | 文章正文归档 |
| `report_period_freeze` | `snapshot_object_key` | 报告周期 freeze |
| 待新增售前归档表/列 | 原始 prompt、raw response、导出包 object key | 售前大字段/交付物归档 |

本轮对象存储 orphan dry-run：

- 入口：`POST /api/data-retention/object-storage/orphans/dry-run`。
- 扫描范围只限生命周期治理自己管理的前缀：`archive/article/`、`retention/freeze/report-period/`；禁止默认全桶扫描。
- 参数：`prefix` 可选，但必须落在上述托管前缀内；`safetyAgeHours` 默认 24；`limitPerPrefix` 默认 100、上限 1000。
- orphan 定义：对象 key 位于托管前缀、对象最后修改时间早于安全宽限期、且集中登记清单里的所有 key 列均无引用。
- 宽限期用于保护 in-flight 归档：写对象与回写 DB key 之间存在时间差，刚写入对象不得被判为 orphan。
- 审计：写 `data_retention_run(domain='object_storage_orphan', mode='dry_run')`。
- 本轮只输出无活引用候选和预计字节数，不执行对象删除；合规尾巴期后续接配置门控。

对象真删前必须同时满足：

```text
不在对象 key 引用登记清单的任何活跃行中出现
AND 超过对象保留期
AND 满足合同/合规尾巴期
AND retention dry-run/execute 审计记录完整
```

## 13. 项目删除与下户生命周期

当前 `ProjectService` 删除项目会同步硬删文章、分发、轮询、报告等多类业务行。引入对象存储归档和 freeze 后，这个行为不能继续作为最终删除入口，否则会造成两类风险：

- DB 引用被提前删掉，对象存储中的 freeze/文章归档变成孤儿，后续“无活引用”检查可能误判。
- 客户解约后的合同/合规尾巴期未到，但数据已经被即时硬删，无法满足留存或审计要求。

建议改造为 `ProjectOffboardingService`：

```text
1. ProjectService.deleteProject 只发起下户/删除申请
2. 标记 project lifecycle_status = OFFBOARDING / DELETE_REQUESTED
3. 停止轮询、定时发文、分发、报告生成等新任务
4. 保留 DB 引用和对象 key，直到合规尾巴期结束
5. 到期后 dry-run 计算 DB 行与对象候选
6. 先按业务表子父顺序清 DB，再做对象无活引用检查
7. 对象真删通过合同/合规门控后执行
8. 全流程写 retention/offboarding 审计
```

项目删除等价于客户/项目下户流程的一部分，不应绕过 retention 框架做即时硬删。

## 14. 验收清单

- 同一天日聚合重复执行两次，结果不变。
- 历史回填可中断可重跑，不重复累加。
- recompute 后本 slice 不再出现的 summary 行会被删除或置 inactive，不留僵尸行。
- freeze 异步幂等，有报告周期结束触发和手动补偿。
- 已 FROZEN 且源未变的重复触发是 no-op；显式重生成或源变化才生成新 version。
- 报告接口不做同步对象 IO。
- 最老未冻结报告周期明细堆积天数可监控。
- 客户可见视图矩阵已逐格确认，且每格都有 summary/freeze/热明细覆盖。
- `poll_results` execute 前置含封口、对账、FROZEN、读路径已切、dry-run、业务口径已定。
- 卡住 batch 超过安全阀会失败封口并告警；已 purge slice 的晚到明细会拒收或丢弃并告警。
- 对账不一致时重算一次；仍不一致则告警并跳过该日，不删除。
- `poll_results` execute 删除与 summary recompute 使用同一把 slice 锁互斥，锁内完成最终门控复查、子表删除、明细删除和 purged marker 写入。
- 删除顺序先 `poll_batch_shard_items/shards`，再 `poll_results`。
- 所有 slim 都有对应汇总/记录门控。
- 售前 LLM raw 的策略已固定：终态 + 汇总覆盖后直接 NULL，不做对象归档。
- 文章正文归档严格 verify-before-null。
- 多渠道文章发布终态不阻断未完成渠道；置空正文前 rehydration 覆盖发布流程。
- 文章重发布/改稿有对象存储 rehydration 真实路径。
- DB 只存对象逻辑 key，不存 bucket/endpoint/完整 URL。
- `ObjectStorageRetentionHandler` 使用对象 key 引用登记清单做无活引用检查。
- 对象存储真删走无活引用 + 合规双闸，并纳入 retention 审计。
- 项目删除走下户生命周期，不即时硬删归档/freeze 引用。
- dry-run 晋级 execute 有连续 7 天、100% 对账和双负责人确认标准。
- `audit_log`、凭证、token、session 类表明确不在本期 P0 范围。

## 15. 仍需业务拍板

这些事项不阻塞汇总基建，但阻塞 `poll_results` execute 删除：

1. 客户可见视图矩阵，尤其 120 天外是否还能看单条命中明细。
2. 未来若启用非季报的售后报告，需要先启用对应报告周期 freeze。
这些事项不阻塞 P0/P1 开发，但影响对象真删策略：

1. 客户合同数据保留下限/删除上限。
2. 解约后尾巴期。
3. 客户交付物冷归档周期。
