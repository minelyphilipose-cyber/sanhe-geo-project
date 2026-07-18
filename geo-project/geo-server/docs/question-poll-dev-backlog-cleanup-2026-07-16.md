# 问题轮询开发库遗留任务收口记录

## 执行时间

2026-07-16

## 收口边界

- 仅处理开发库中尚未终结、且批次不包含 `QUESTION_POLL_WEB` 平台的旧轮询任务。
- 不删除批次、分片、任务或结果历史。
- 不生成正式日统计，避免旧 `STANDARD_CHAT` 结果混入联网轮询基线。
- 新四渠道联网配置及诊断 Smoke 数据不在处理范围内。

## 执行前

- `ready` 批次：53
- `ready/running` 分片：414
- 与上述批次关联、仍可调度的旧任务：393
- 无分片关联的旧 `BI_DAILY_POLL retry_pending` 任务：13

## 状态收口

- 393 个可调度旧任务转为 `cancelled`。
- 414 个未终结分片转为 `failed`，并写入行政收口原因。
- 53 个旧批次转为 `failed`，按现有结果重算完成数、失败数和命中数。
- 13 个无分片关联的历史重试任务转为 `cancelled`。

## 执行后校验

- `ready` 批次：0
- `ready/running` 分片：0
- `QUESTION_POLL/BI_DAILY_POLL` 的 `pending/running/retry_pending` 任务：0
- 旧非联网平台的活跃轮询任务：0

所有任务和分片均保留原始主键、时间及历史数据，并通过 `last_error/error_context`
记录本次收口原因。
