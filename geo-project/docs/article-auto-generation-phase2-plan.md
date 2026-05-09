# 文章自动生成阶段二实施方案

## 开工前盘点结论

`article_batch.dispatch_task_id` 当前无 NULL，但测试库已存在同一 `dispatch_task_id` 对应多条 batch 的历史数据。历史数据中有 batch 已挂载 draft，因此阶段二不直接清理历史重复 batch，也不直接新增 `UNIQUE(dispatch_task_id)`。

下游聚合已核对：`ReportService`、`ProjectDashboardSnapshotService` 均按 `SUM(total_count/completed_count/failed_count)` 聚合，不使用 `COUNT(batch)` 作为文章数，因此“一 batch 一 draft”不会造成统计翻倍。

## Batch 与 Draft 语义

阶段二采用“一次内容生成调度任务生成一个 article_batch，batch 内一篇 article_draft”的语义。`article_draft.generation_slot_no` 是业务真相，`article_batch.generation_slot_no` 仅作为调度冗余字段，后续任何按槽位统计以 draft 为准。

`ArticleGenerationPersistenceService.ensureArticleBatch` 改为先按 `dispatch_task_id` 查 batch：

- 过滤 `superseded` 后为 0 条：新建 batch。
- 过滤 `superseded` 后为 1 条：复用该 batch。
- 过滤 `superseded` 后大于等于 2 条：抛 `BizException` 并告警，不做启发式选择。

历史 `superseded` 和 task 多 batch 数据保留不清理，新代码不再产生此类数据。

## 周期与配额归属

内容生成任务的 `window_start/window_end` 使用套餐 `period_type` 对应的自然周期边界。`article_draft.period_type/period_key` 由调度任务窗口和 `QuotaPeriodResolver` 写入，不使用 draft 创建时间判断周期归属。

历史 draft 的 `target_channel/period_type/period_key/generation_slot_no` 保持 NULL，统计查询天然过滤，历史 draft 不参与新周期配额统计。

已有 `day` 周期套餐配置，因此官网/行业站自动生成对 `period_type=day` 不走双日触发门槛，每个自然日都按当天窗口规划；其他周期仍复用项目双日节奏触发。

阶段二字段缺失的存量 `CONTENT_GENERATION` task 不强行写默认渠道或默认周期。执行器发现缺少 `target_channel/period_type/generation_slot_no/window_start` 时记录日志并跳过，避免把历史任务污染到官网周周期统计中。需要重跑时通过 release 后由新 planner 重新建带完整元数据的新任务。

## Cancelled 与 Release

失败、超时或人工取消的 `CONTENT_GENERATION` 任务默认不自动回收 slot。人工兜底接口：

`POST /api/dispatch/monitor/tasks/{id}/release`

接口权限点为 `dispatch.task.release`，只授予运维管理员角色。服务层使用 `selectByIdForUpdate` 锁定任务，事务内完成三件事：

- 已 `cancelled` 直接拒绝，`completed` 直接拒绝，其他状态允许 release。
- 将任务状态改为 `cancelled`，并设置 `finished_at/last_error/error_context`。
- 将 `idempotency_key` 重写为 `cancelled:{原值}:{task_id}`，避免 planner 重建同 slot 任务时被唯一键挡住，同时写活动审计。

`cancelled` 不写 `completed_count`，也不写 `failed_count`，不在 `article_batch` 增加 `cancelled_count`。Dashboard 如需展示取消量，通过 `dispatch_task.status='cancelled'` 反查统计，避免人工取消被计入失败数。

调度规划计算已占用 slot 时排除 `cancelled` 任务，释放后允许同周期同渠道同槽位重新生成任务。

## 失败重试语义

`CONTENT_GENERATION` 任务自动重试仍复用同一个 `dispatch_task` 和同一个 `article_batch`。`ensureArticleBatch` 在单 active batch 场景下复用 batch，`completeBatch` 每次执行后用本次任务结果覆盖 `total_count/completed_count/failed_count`，避免重试时累加造成统计失真。若任务被人工 release，原 task 改为 `cancelled` 且释放唯一键，新 task 会创建新的 batch；取消不计入 batch 失败数。
