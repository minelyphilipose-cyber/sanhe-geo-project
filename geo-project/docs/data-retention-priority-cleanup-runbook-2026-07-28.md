# 优先数据定期清理运行手册（2026-07-28）

## 1. 本轮范围

本轮只处理两个增长最快的领域：

1. 问题轮询明细；
2. 文章正文大字段。

不处理：

- 基线报告；
- MinIO 下线或对象删除；
- 售前、分发 payload 等低优先级清理；
- `OPTIMIZE TABLE`、表重建或历史磁盘文件收缩。

目标是控制后续数据增长。历史磁盘空间是否立即归还给操作系统不在本轮目标内。

## 2. 默认安全状态

新代码部署后，以下配置默认均为 `false`：

```env
GEO_RETENTION_SCHEDULER_ENABLED=false
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=false
GEO_RETENTION_ARTICLE_ARCHIVE_EXECUTE_ENABLED=false
GEO_RETENTION_ARTICLE_PURGE_EXECUTE_ENABLED=false
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=false
GEO_RETENTION_REPORT_FREEZE_ENABLED=false
GEO_DISPATCH_TASK_CLEANUP_ENABLED=false
```

因此仅部署代码和执行 Flyway 迁移不会自动置空正文或删除轮询明细。

## 3. 数据库变更

Flyway 迁移：`V329__priority_data_retention_safety.sql`。

变更内容：

- `article_draft_version.content_markdown` 允许为 `NULL`；
- 增加文章清理候选索引；
- 为已存在的 freeze 表增加兼容字段 `snapshot_schema_version`，但退休季度报表不再消费该字段；
- `poll_batches`、`poll_results`、`poll_daily_stats` 对 `dispatch_task` 的外键改为 `ON DELETE SET NULL`；
- dispatch 历史任务自动清理默认关闭，防止旧级联关系意外删除业务数据。

部署后检查：

```sql
SHOW COLUMNS FROM article_draft_version LIKE 'content_markdown';
SHOW INDEX FROM article_draft_version WHERE Key_name = 'idx_article_version_retention';
SELECT table_name, constraint_name, delete_rule
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'fk_poll_batch_dispatch_task',
    'fk_poll_result_dispatch_task',
    'fk_poll_stats_dispatch_task'
  );
```

预期三个外键的 `delete_rule` 均为 `SET NULL`。

V329 不得首次直接在生产库验证。上线前必须先在生产克隆库执行，并记录：

- `SELECT VERSION()` 的精确版本；
- 每条 `ALTER TABLE` 的耗时和实际 DDL 算法；
- metadata lock 等待时间、写入延迟和临时磁盘峰值；
- 三个外键最终的 `delete_rule`；
- 任一 DDL 失败后，Flyway repair 前数据库所处状态及人工修复步骤。

## 4. 文章正文处理

文章处理分两步，不能合并。

### 4.1 第一步：归档正文到 COS

只读盘点：

```http
POST /api/data-retention/articles/archive/dry-run
Authorization: Bearer <token>
Content-Type: application/json

{
  "minPublishedAgeDays": 90,
  "limit": 50
}
```

执行前临时开启：

```env
GEO_RETENTION_ARTICLE_ARCHIVE_EXECUTE_ENABLED=true
```

小批执行：

```http
POST /api/data-retention/articles/archive
Authorization: Bearer <token>
Content-Type: application/json

{
  "dryRun": false,
  "minPublishedAgeDays": 90,
  "limit": 10,
  "reason": "production canary article body archive"
}
```

检查响应：

- `simulationOnly = false`；
- `failedCount = 0`；
- `archivedCount` 符合预期；
- COS 对象可读；
- `content_object_key`、`content_checksum`、`content_archived_at` 已回写；
- `content_markdown` 仍保留。

按 `nextCursorVersionId` 继续分页，直到 `hasMore=false`。

### 4.2 第二步：置空数据库正文

正文置空要求：

- 已归档到 COS；
- COS readback checksum 与数据库 checksum 一致；
- 归档时间至少经过 24 小时；
- 非当前版本按版本创建时间保留至少 90 天；
- 当前版本按有效发布时间保留至少 90 天；
- 当前未删除版本必须有发布记录；
- 当前未删除版本不得存在活动分发任务或活动自媒体排期。

只读盘点：

```http
POST /api/data-retention/articles/purge/dry-run
Authorization: Bearer <token>
Content-Type: application/json

{
  "retentionDays": 90,
  "archiveGraceHours": 24,
  "limit": 50
}
```

执行前临时开启：

```env
GEO_RETENTION_ARTICLE_PURGE_EXECUTE_ENABLED=true
```

首次只执行 1 条：

```http
POST /api/data-retention/articles/purge
Authorization: Bearer <token>
Content-Type: application/json

{
  "retentionDays": 90,
  "archiveGraceHours": 24,
  "limit": 1,
  "reason": "production canary article body purge"
}
```

执行后必须验证：

1. `simulationOnly = false`；
2. 文章详情、预览正常；
3. 官网、自媒体排期和分发读取正文正常；
4. 被置空版本从 COS 回源；
5. 数据库 `content_purged_at` 已写入；
6. `data_retention_run` 记录成功且 `failedCount=0`。

验证通过后再按游标小批放量。执行结束立即关闭正文置空开关。

## 5. 问题轮询明细处理

### 5.1 保留内容

物理删除轮询明细时继续保留：

- `poll_keyword_daily_summary`；
- `poll_platform_daily_summary`；
- `poll_entity_judge_daily_summary`；
- 仍供实时看板使用的最后一条已完成“问题 × 渠道”结果及其直接明细；
- `poll_batches`；
- `data_retention_run`；
- `poll_audit_purge_runs`；
- `data_retention_purged_slice`。

### 5.2 删除内容

按 `project_id + batch_date + question_tier` 切片删除：

1. `poll_citations`；
2. `poll_search_sources`；
3. `poll_provider_calls`；
4. `poll_invocation_attempts`；
5. `poll_result_entity_judge`；
6. `poll_batch_shard_items`；
7. `poll_batch_shards`；
8. `poll_results`。

### 5.3 硬门控

切片只有同时满足以下条件才允许删除：

- 超过 120 天热数据窗口；
- 没有非终态 `poll_batches`；
- 没有非终态 invocation attempt；
- 删除时按结果 ID 排除仍供实时看板使用的最后一条已完成“问题 × 渠道”结果；
- 关键词汇总和平台汇总的维度集合、来源行数、canonical checksum 及全部持久化汇总指标与明细一致；
- 实体判断汇总的维度集合、计数及内容级 checksum 与明细一致；
- 切片未被历史清理；
- 清理事务内再次拿锁并复核全部门控。

清理成功后写 `data_retention_purged_slice`。后续晚到写入会返回 409，不会重新污染已清理切片。

### 5.4 执行步骤

旧季度报表已停用，不再生成季度 freeze，也不以 freeze 作为轮询清理门控。
生产环境保持 `GEO_RETENTION_REPORT_FREEZE_ENABLED=false`。
历史手工入口 `POST /api/reports/freeze/quarterly` 固定返回 `410 Gone`，Service 层也拒绝
直接生成，不得通过临时开启配置恢复该退休链路。

先按待清理时间范围回填关键词、平台和实体判断汇总。先 dry-run，再执行：

```http
POST /api/dispatch/poll-summary/backfill
Authorization: Bearer <token>
Content-Type: application/json

{
  "startDate": "2025-01-01",
  "endDate": "2025-01-31",
  "dryRun": false,
  "limit": 100
}
```

按响应游标继续分页，直到 `hasMore=false`。该操作会把历史实体判断汇总升级为当前内容级 checksum，
并清除当前 prompt 版本的僵尸维度。
已经写入 `data_retention_purged_slice` 的切片会整体跳过，禁止基于保留的 latest 子集覆盖原历史汇总。

轮询清理 dry-run：

```http
POST /api/data-retention/poll-results/dry-run
Authorization: Bearer <token>
Content-Type: application/json

{
  "hotRetentionDays": 120,
  "limit": 10
}
```

只有所有候选切片 `eligible=true` 且对账值符合预期，才临时开启：

```env
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=true
```

首次执行 1 个切片：

```http
POST /api/data-retention/poll-results
Authorization: Bearer <token>
Content-Type: application/json

{
  "hotRetentionDays": 120,
  "limit": 1,
  "reason": "production canary poll detail purge"
}
```

检查：

- `purgedSlices = 1`；
- `failedSlices = 0`；
- `simulationOnly = false`；
- `deletedRows.poll_results = pollResultRows - latestLiveResultRows`；
- 汇总看板和移动看板正常；
- `poll_audit_purge_runs.status = 'SUCCEEDED'`；
- `data_retention_purged_slice.status = 'purged'`。

然后按响应中的三个游标字段继续分页：

- `nextCursorBatchDate`；
- `nextCursorProjectId`；
- `nextCursorQuestionTier`。

直到 `hasMore=false`。执行结束立即关闭轮询清理开关。

## 6. 定时任务晋级

建议至少完成多轮人工小批验证后再启用定时任务。

只启用定时 dry-run：

```env
GEO_RETENTION_SCHEDULER_ENABLED=true
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=false
```

定时真实执行需要同时开启总执行开关和对应领域开关。例如只自动处理文章：

```env
GEO_RETENTION_SCHEDULER_ENABLED=true
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=true
GEO_RETENTION_ARTICLE_ARCHIVE_EXECUTE_ENABLED=true
GEO_RETENTION_ARTICLE_PURGE_EXECUTE_ENABLED=true
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=false
```

调度器使用 Redis 全局锁，避免多实例重复调度。默认每天 `03:30` 执行，每轮最多 20 批。
dry-run 和 execute 均最多扫描 `max-batches-per-run` 批。

生产初期不建议直接启用 scheduler execute。优先保持人工调用、人工审计。

## 7. 回退与故障处理

- 发现异常时，先关闭所有 execute 开关并重新部署容器；
- 不删除 COS 归档对象；
- 文章正文已置空后，可继续通过 COS 回源，不能仅通过关闭开关恢复数据库正文；
- 如确需恢复正文，应按 `content_object_key` 读取 COS，经 checksum 校验后再回填；
- 轮询明细物理删除不可从 summary 反向恢复，只能依赖数据库备份；
- 任一批次 `failedCount > 0` 或 `failedSlices > 0` 时，不推进下一批，先查审计记录和服务日志；
- 不执行 `OPTIMIZE TABLE`，避免在本轮引入额外锁表和磁盘峰值风险。

## 8. 推荐生产顺序

1. 部署代码，保持所有新开关为 `false`；
2. 确认 V329 执行成功；
3. 文章归档 dry-run；
4. 文章归档小批 execute；
5. 至少观察 24 小时；
6. 文章置空 limit=1；
7. 验证所有正文读取和分发路径；
8. 文章置空小批放量；
9. 确认旧季度 freeze 定时任务保持关闭；
10. 重算待清理切片汇总，使其具备当前内容级 checksum；
11. 轮询清理 dry-run；
12. 轮询清理 limit=1；
13. 验证看板和审计；
14. 轮询清理小批放量；
15. 关闭 execute 开关；
16. 稳定运行一段时间后，再单独评估定时 execute。
