# 优先数据定期清理运行手册（2026-07-28）

## 1. 本轮范围

本轮处理三个增长较快的领域：

1. 问题轮询明细；
2. 通用文章正文大字段；
3. Agent 官网、行业资讯站、平台网站发布成功后的正文和分发 payload。

不处理：

- 基线报告；
- MinIO 下线或对象删除；
- 售前等低优先级 payload 清理；
- `OPTIMIZE TABLE`、表重建或历史磁盘文件收缩。

目标是控制后续数据增长。历史磁盘空间是否立即归还给操作系统不在本轮目标内。

## 2. 默认安全状态

新代码部署后，以下配置默认均为 `false`：

```env
GEO_RETENTION_SCHEDULER_ENABLED=false
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=false
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED=false
GEO_RETENTION_ARTICLE_ARCHIVE_EXECUTE_ENABLED=false
GEO_RETENTION_ARTICLE_PURGE_EXECUTE_ENABLED=false
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=false
GEO_RETENTION_REPORT_FREEZE_ENABLED=false
GEO_DISPATCH_TASK_CLEANUP_ENABLED=false
```

因此仅部署代码和执行 Flyway 迁移不会自动置空正文或删除轮询明细。

## 3. 数据库变更

Flyway 迁移：

- `V329__priority_data_retention_safety.sql`；
- `V330__website_published_content_cleanup_index.sql`。

变更内容：

- `article_draft_version.content_markdown` 允许为 `NULL`；
- 增加文章清理候选索引；
- 为已存在的 freeze 表增加兼容字段 `snapshot_schema_version`，但退休季度报表不再消费该字段；
- `poll_batches`、`poll_results`、`poll_daily_stats` 对 `dispatch_task` 的外键改为 `ON DELETE SET NULL`；
- dispatch 历史任务自动清理默认关闭，防止旧级联关系意外删除业务数据。
- 增加官网类发布终态清理候选索引。

部署后检查：

```sql
SHOW COLUMNS FROM article_draft_version LIKE 'content_markdown';
SHOW INDEX FROM article_draft_version WHERE Key_name = 'idx_article_version_retention';
SHOW INDEX FROM article_publish_record WHERE Key_name = 'idx_article_publish_website_cleanup';
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

官网类发布终态清理与通用 90 天 COS 归档链路相互独立。官网类满足严格终态门控后直接释放热数据；
其他文章仍执行先归档、后置空的两步流程。

### 4.1 官网类发布终态驱动清理

仅处理以下目标：

- `brand_geo_site`（Agent 官网）；
- `industry_site`（行业资讯站）；
- `forum_site`（平台网站）。

必须同时满足：

- `article_publish_record.publish_status = distributed`；
- `url_quality = public_url` 且 `published_url` 是 HTTP(S) 公网链接；
- 最后一次符合条件的发布时间已超过至少 24 小时的热窗口；请求值和调度配置低于 24
  小时都会被后端强制提升到 24 小时；
- 文章当前状态为 `distributed` 或 `published`；`deleted` 只允许用于并发或历史残留的幂等补清理，
  `approved`、`unpublished` 等状态禁止清理；
- 所有官网类成功凭证的 `published_url` 都必须通过 URI 校验：scheme 为 `http/https` 且 host 非空；
- 没有活动分发任务；
- 没有待执行、失败待处理或结果不确定的自媒体排期；只有 `published_confirmed`、`cancelled`
  不阻止官网类清理；
- 清理事务内锁定文章、发布凭证、分发任务和自媒体排期后再次复核。

清理动作：

- 置空该文章全部版本的 `content_markdown`，写入 `content_purged_at`；
- 只置空与成功官网类发布凭证绑定的 `request_payload`、`fill_payload`、`response_payload`；
- 置空发布凭证中的大体积 `raw_response`，长期保留项目、文章、渠道、状态、时间、URL 和幂等来源；
- 将 `article_draft.status` 置为 `deleted`，保留轻量主记录和所有发布凭证。

先只读盘点：

```http
POST /api/data-retention/articles/website-published/dry-run
Authorization: Bearer <token>
Content-Type: application/json

{
  "retentionHours": 24,
  "limit": 10
}
```

首次执行前临时开启：

```env
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED=true
```

首次只执行 1 篇：

```http
POST /api/data-retention/articles/website-published/cleanup
Authorization: Bearer <token>
Content-Type: application/json

{
  "retentionHours": 24,
  "limit": 1,
  "reason": "production canary website published hot-data cleanup"
}
```

核对 `article_publish_record` 及公开链接仍存在、移动看板发布统计不变、正文和成功任务 payload
已经置空，并按 `nextCursorArticleId` 分页。验证结束后立即关闭独立执行开关。

H2 MySQL 兼容模式测试只用于验证 SQL 形状和数据变更，不作为锁语义证明。首次开启真实清理前，
必须在生产克隆 MySQL 上并发演练以下竞态：

- 清理锁定文章后并发创建新的分发任务；
- 清理复核前并发写入新的发布凭证；
- 清理复核前并发创建自媒体排期；
- 验证 `FOR UPDATE`、外键检查、锁等待及事务回滚行为符合预期。

### 4.2 第一步：归档其他文章正文到 COS

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

### 4.3 第二步：置空其他文章数据库正文

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

- 超过 14 天热数据窗口；
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
  "hotRetentionDays": 14,
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
  "hotRetentionDays": 14,
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
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED=false
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
- 官网类终态清理默认不生成 COS 正文归档，置空后只能依赖数据库备份或目标站点恢复；
- 文章正文已置空后，可继续通过 COS 回源，不能仅通过关闭开关恢复数据库正文；
- 如确需恢复正文，应按 `content_object_key` 读取 COS，经 checksum 校验后再回填；
- 轮询明细物理删除不可从 summary 反向恢复，只能依赖数据库备份；
- 任一批次 `failedCount > 0` 或 `failedSlices > 0` 时，不推进下一批，先查审计记录和服务日志；
- 不执行 `OPTIMIZE TABLE`，避免在本轮引入额外锁表和磁盘峰值风险。

## 8. 推荐生产顺序

1. 部署代码，保持所有新开关为 `false`；
2. 确认 V329、V330 执行成功；
3. 官网类终态清理 dry-run；
4. 官网类终态清理 limit=1，核对发布凭证、公开链接和移动看板统计；
5. 官网类终态清理小批放量并关闭独立 execute 开关；
6. 其他文章归档 dry-run；
7. 其他文章归档小批 execute；
8. 至少观察 24 小时；
9. 其他文章置空 limit=1；
10. 验证所有正文读取和分发路径；
11. 其他文章置空小批放量；
12. 确认旧季度 freeze 定时任务保持关闭；
13. 重算待清理切片汇总，使其具备当前内容级 checksum；
14. 轮询清理 dry-run；
15. 轮询清理 limit=1；
16. 验证看板和审计；
17. 轮询清理小批放量；
18. 关闭 execute 开关；
19. 稳定运行一段时间后，再单独评估定时 execute。
