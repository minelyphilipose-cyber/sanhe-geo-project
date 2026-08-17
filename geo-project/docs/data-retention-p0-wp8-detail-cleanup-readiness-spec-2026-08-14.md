# 数据定期清理 P0 WP8：365 天明细清理与生产准入门禁 SPEC

- 日期：2026-08-14
- 聚焦修订：2026-08-15（关闭自动 disable 幂等、候选级 warning 与 zero-tree invariant）
- 状态：第二次聚焦评审通过，授权开发
- 适用模块：`geo-server`
- 上游冻结基线：WP2、WP3、WP4、WP5、WP6、WP7
- 当前本地最高 migration：V367；开发开始和提交前必须重新扫描全部 refs、worktree 与未跟踪文件

---

## 1. 目标与结论

WP8 首版只解决两个相互依赖的问题：

1. 在永久治理证据完整、所有未解决状态关闭且统一保留锚点满 365 天后，安全删除
   `data_retention_batch_attempt` 与 `data_retention_batch_item`；
2. 用真实 MySQL 状态形成 fail-closed 的 cleanup health、积压、年龄和容量门禁，使任何新的
   dynamic enable 或 canary 都不能绕过 WP2 已冻结的生产准入条件。

operation-bound detail 的生命周期终点仍是永久 `data_retention_operation.detail_purged_at`。
每个 operation 的完整 attempt/item tree 必须在一个 MySQL 事务中删除并写 marker，不能按 run、日期、
attempt 或分页拆分提交。

operationless `page/lease_busy` attempt 不属于 operation tree。它们以所属 run 为原子清理单位，只有 run
完成 rollup、`finished_at + 365 天` 到期且所有 operationless attempt 均为终态时才可整体删除。

WP8 housekeeping 不受业务 execute dynamic switch 控制；否则 backlog 触发 false 后 cleanup 自身也会停，
系统无法恢复。它只受独立静态开关、独立 lease 和本文事务门禁控制。所有新开关默认 `false`，恢复后也只
解除 readiness blocker，绝不自动 false → true。

WP8 完成不代表生产可执行。V133、匿名化生产克隆、MySQL 8.0.46 实测、生产 migration、人工审批和 canary
执行仍是独立阻断项。

---

## 2. 授权范围与禁止事项

### 2.1 本 SPEC 评审通过后可授权的开发范围

- WP8 Java service、repository、scheduler/lease、metrics、alert 和 readiness gate；
- 只增加前向 migration，不修改已发布 V354、V366、V367；
- `DataRetentionP0AuditService` 的 typed detail-purge port 与 purge 后 writer guard；
- dynamic enable 与 canary 的只读 readiness 检查；
- cleanup 超阈值时通过既有 typed disable port 将 `global_execute` 向 false 收敛；
- 单元、架构、MySQL 8、Redis、并发和容量测试；
- 本地 required profile 验收。

### 2.2 本轮明确不授权

- 修改、删除或重写任何历史 Flyway migration；
- 处理 V133 历史链阻断；
- 在生产或生产克隆执行 migration、cleanup 或修复 SQL；
- 开启 scheduler execute、website execute、poll execute 或 dynamic enable-write；
- 执行 canary，或把 canary 通过写入任何环境；
- 删除/归档永久 run、operation、operation-run、marker、failure、reconciliation、switch audit、poll audit、
  preserved-result ledger；
- 扩张为通用审计归档、人工 regroup、业务正文清理或复杂跨数据库恢复框架。

### 2.3 默认值

以下默认值必须在 Java 和 `application.yml` 同时为 false：

```yaml
geo:
  retention:
    detail-cleanup:
      enabled: ${GEO_RETENTION_DETAIL_CLEANUP_ENABLED:false}
```

既有默认值保持不变：

```text
geo.retention.scheduler.execute-enabled=false
geo.retention.website-published-cleanup.execute-enabled=false
geo.retention.poll-results.execute-enabled=false
geo.retention.dynamic-switch.enable-write-enabled=false
```

---

## 3. 上游冻结契约

WP8 不重新定义以下已批准事实：

1. run、operation、operation-run、commit/purged-slice marker、failure、reconciliation、dynamic-switch audit、
   poll audit 和 preserved-result ledger 永久保留；
2. mutation attempt/item 统一使用 operation `detail_retention_anchor_at + 365 天`，不能使用 attempt
   `finished_at` 或 item `created_at`；
3. `rolled_back/unknown/manual_review` 不是 operation 最终状态，retention anchor 必须为空；
4. 最终状态只有 `committed/closed_no_mutation`；
5. `detail_purged_at` 只能从 NULL 设置一次，设置后 outcome、anchor 和 marker 永久冻结；
6. 全局治理锁序是 operation → reconciliation/failure gate → attempt → batch item；
7. WP8 先获得 attempt/item 的全部锁，实际删除顺序是 batch item → attempt；
8. run `attempt_detail_purged_at` 只是可向后推进的观测水位，不是永久 complete marker；
9. WP7 未解决 cleanup ownership 必须保留；只有 cleanup operation 已最终化并满 365 天后，其 detail 才可清理；
10. cleanup 后允许与最终结论一致、且不依赖已删除 detail 的 audit-only reconciliation，禁止恢复 attempt/item；
11. 任何 execute/enable-write 的 false 都不能被后台自动改为 true。

若实现需要改变其中任一项，必须先回到对应上游 SPEC 重新评审，不得在 WP8 中静默覆盖。

---

## 4. 数据分类

### 4.1 WP8 唯一可删除数据

| 表 | 范围 | 原子单位 | 365 天锚点 |
|---|---|---|---|
| `data_retention_batch_item` | `operation_id` 非空的全部行 | 完整 operation | operation anchor |
| `data_retention_batch_attempt` | `operation_id` 非空的全部行 | 完整 operation | operation anchor |
| `data_retention_batch_attempt` | `operation_id IS NULL` | 完整 run 的 operationless subset | run `finished_at` |

### 4.2 永久保留数据

WP8 不对以下表发出 DELETE：

```text
data_retention_run
data_retention_operation
data_retention_operation_run
data_retention_batch_commit
data_retention_purged_slice
data_retention_failure_group
data_retention_failure_group_item
data_retention_failure_active_key
data_retention_failure_regroup_history
data_retention_unknown_outcome_reconciliation
data_retention_dynamic_switch_change
poll_audit_purge_runs
data_retention_poll_preserved_result
data_retention_detail_cleanup_run
```

FK 均继续使用 RESTRICT/NO ACTION；WP8 不引入 CASCADE。

### 4.3 WP7 ownership 的解释

`poll_preserved_cleanup` operation 的 item 是 preserved result ID 的 durable hold：

- operation 为 `pending/rolled_back/unknown/manual_review` 时没有 retention anchor，WP8 不会选择；
- operation 为 `committed` 时，ledger 必须已在同一事务变成 `cleaned`；365 天后删除 item 不影响永久 ledger；
- operation 为 `closed_no_mutation` 时，ownership 已有永久最终结论，365 天后可删除 detail；
- WP8 永远不把 ledger 从 `preserved` 改为 `cleaned`，也不清空 `cleanup_operation_id`。

---

## 5. 为什么需要前向计数扩展

run 的 `attempt_count` 在 finish 时固化且禁止重算。一个 run 可以包含多个 operation，而这些 operation 会在
不同 sweep 中分别到期和清理。如果只比较“当前 attempt 行数 = 固化 attempt_count”，第一个 operation 清理后，
同一 run 的第二个 operation 将永久无法通过 rollup 校验。

因此 WP8 必须永久记录已经由原子协议删除的计数，使任意时刻都能验证：

```text
run.attempt_count
= 当前仍存在的该 run attempt 数
 + SUM(operation_run.detail_purged_attempt_count)
 + run.operationless_attempt_purged_count
```

该等式只用于证明生命周期删除没有丢失或重复，不改写原 rollup，也不把永久计数当作业务成功计数。

item 没有 run rollup，且 operation `item_count` 对 `poll_slice` 表示一个业务 slice、并不等于物理 batch item 行数。
所以 operation 必须另存实际删除的 `detail_purged_item_count`，不能复用 `item_count`。

---

## 6. 前向 migration 设计

当前工作区最高版本是未跟踪的 V367。WP8 暂以 `<NEXT_VERSION>` 表示；开发开始和提交前必须扫描：

- 当前工作区 tracked/untracked migration；
- 所有 local/remote refs；
- 所有有效 worktree；
- 同期其他任务预留号。

若扫描后 V368 仍空闲，建议文件名：

```text
V368__data_retention_detail_cleanup_readiness.sql
```

### 6.1 `data_retention_operation` 扩展

新增：

| 列 | 类型 | 说明 |
|---|---|---|
| `detail_purged_item_count` | `BIGINT UNSIGNED NULL` | marker 事务实际删除的 item 数；未 purge 必须为 NULL |

新增 CHECK：

```sql
CHECK (
  (detail_purged_at IS NULL AND detail_purged_item_count IS NULL)
  OR
  (detail_purged_at IS NOT NULL AND detail_purged_item_count IS NOT NULL)
)
```

历史行保持 NULL，不回填虚构计数。

### 6.2 `data_retention_operation_run` 扩展

新增：

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `detail_purged_attempt_count` | `BIGINT UNSIGNED NOT NULL` | `0` | 该 operation/run relation 已原子删除的 attempt 数 |

只有 operation detail purge 事务可以从 0 改为锁定快照中的非负计数。operation purge 后允许新增的 audit-only
reconcile relation 保持 0，且不能产生 attempt。

### 6.3 `data_retention_run` 扩展

新增：

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `operationless_attempt_purged_count` | `BIGINT UNSIGNED NOT NULL` | `0` | 已按 run 原子删除的 operationless attempt 累计数 |

该列只能递增。`attempt_detail_purged_at` 保持现有 nullable 水位语义。

### 6.4 `data_retention_detail_cleanup_run`

该表永久保存每日 housekeeping 的轻量运行和容量样本，不保存 business key、正文、payload、URL 或 ID 数组。
一日一次运行五年约 1,825 行，不需要再为它设计递归清理。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT UNSIGNED NOT NULL` | 自增主键 |
| `cleanup_run_id` | `CHAR(36)` ASCII/BIN NOT NULL | UUID，唯一 |
| `trigger_source` | `VARCHAR(16)` ASCII/BIN NOT NULL | 首版 `scheduler/manual_test`；生产无公开 manual endpoint |
| `status` | `VARCHAR(16)` ASCII/BIN NOT NULL | `running/succeeded/failed` |
| `active_slot` | `TINYINT UNSIGNED` generated stored | running 时为 1，否则 NULL |
| `application_version` | `VARCHAR(64)` ASCII/BIN NOT NULL | 实际运行版本 |
| `retention_cutoff_at` | `DATETIME(3) NOT NULL` | 本次固定的 `started_at - 365 天` |
| `started_at` / `heartbeat_at` | `DATETIME(3) NOT NULL` | UTC 毫秒 |
| `finished_at` | `DATETIME(3) NULL` | 仅终态非空 |
| `candidate_operation_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | 无锁预筛数量 |
| `purged_operation_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | marker 成功提交数量 |
| `skipped_operation_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | 锁内资格变化数量 |
| `warning_candidate_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | 已确定 rollback/超预算等候选级 warning 数 |
| `purged_operationless_run_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | operationless 原子清理 run 数 |
| `purged_attempt_count` / `purged_item_count` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | 本次实际删除数 |
| `eligible_backlog_attempt_count` | `BIGINT UNSIGNED NULL` | 终态采样的 mutation eligible backlog |
| `eligible_backlog_item_count` | `BIGINT UNSIGNED NULL` | 终态采样的 mutation eligible backlog |
| `oldest_unpurged_anchor_at` | `DATETIME(3) NULL` | 无剩余 detail 时为 NULL |
| `retention_table_bytes` | `BIGINT UNSIGNED NULL` | allowlist 表 data+index 总字节 |
| `projected_five_year_bytes` | `BIGINT UNSIGNED NULL` | 正常清理稳态加 30% 预留 |
| `last_error_code` | `VARCHAR(64)` ASCII/BIN | 稳定分类，可空 |
| `last_error` | `VARCHAR(2000)` | UTF-8 ≤2 KB，可空 |
| `created_at` / `updated_at` | `DATETIME(3)` | 审计时间 |

generated expression：

```sql
active_slot TINYINT UNSIGNED
  GENERATED ALWAYS AS (CASE WHEN status = 'running' THEN 1 ELSE NULL END) STORED
```

约束和索引：

- `PRIMARY KEY (id)`；
- `UNIQUE KEY uk_retention_detail_cleanup_run_id (cleanup_run_id)`；
- `UNIQUE KEY uk_retention_detail_cleanup_active (active_slot)`，数据库层最多一个 running；
- `KEY idx_retention_detail_cleanup_status (status, finished_at)`；
- `KEY idx_retention_detail_cleanup_started (started_at)`；
- shape CHECK 要求 running 时 `finished_at/error` 为空；succeeded 时 finished、backlog、`retention_table_bytes`
  非空且 error 为空，允许 `warning_candidate_count > 0`，历史不足 7 天时 `projected_five_year_bytes` 允许为空；
  failed 时 finished/error code 非空，容量样本允许为空；
- 所有计数非负由 unsigned 保证；
- `last_error` 增加 `OCTET_LENGTH <= 2048` CHECK。

### 6.5 migration 兼容要求

- 只能前向扩展 V354/V367 对象；
- 对同名列、索引、CHECK、generated expression 做完整 definition guard，不能只数对象；
- MySQL DDL 隐式提交，required profile 必须覆盖 step failure、Flyway repair、re-migrate 和最终 no-op；
- 不修改历史数据的 outcome、anchor、marker、rollup 或 WP7 ledger；
- migration 完成后所有 execute/enable-write 仍为 false，detail cleanup 自身也为 false。

---

## 7. 调度、lease 与运行状态

### 7.1 配置

```yaml
geo:
  retention:
    detail-cleanup:
      enabled: ${GEO_RETENTION_DETAIL_CLEANUP_ENABLED:false}
      cron: "${GEO_RETENTION_DETAIL_CLEANUP_CRON:0 15 2 * * *}"
      max-operations-per-run: ${GEO_RETENTION_DETAIL_CLEANUP_MAX_OPERATIONS:20}
      max-operationless-runs-per-run: ${GEO_RETENTION_DETAIL_CLEANUP_MAX_OPERATIONLESS_RUNS:20}
      max-attempts-per-operation: ${GEO_RETENTION_DETAIL_CLEANUP_MAX_ATTEMPTS_PER_OPERATION:1000}
      max-operationless-attempts-per-run: ${GEO_RETENTION_DETAIL_CLEANUP_MAX_OPERATIONLESS_ATTEMPTS_PER_RUN:1000}
      max-run-seconds: ${GEO_RETENTION_DETAIL_CLEANUP_MAX_RUN_SECONDS:3600}
      lock-key: ${GEO_RETENTION_DETAIL_CLEANUP_LOCK_KEY:geo:retention:detail-cleanup:lock}
      lock-ttl-seconds: ${GEO_RETENTION_DETAIL_CLEANUP_LOCK_TTL_SECONDS:7200}
```

固定约束：

- 时区使用 `Asia/Shanghai`，所有持久时间使用 UTC；
- operation 和 operationless-run 两类上限各 20，不共享业务 WP5 mutation budget；
- 单 operation 或单 operationless run 最多锁定/删除 1,000 条 attempt；只能经容量评审降低，不能由环境变量提高；
- batch item 的物理上限继续由 operation 协议和 DDL 固定为 20；
- 这两个上限是 housekeeping 事务保护，不改变 12,000/96,000 backlog 门禁；
- 不允许配置 365 天 retention、425 天 hard age、48 小时 heartbeat 或容量阈值；它们是 policy 常量。

### 7.2 独立 lease

复用现有安全 lease 的 token、续租和 compare-delete 语义，但使用独立 key。流程：

1. 静态 `detail-cleanup.enabled=false` 时立即返回，不连接 MySQL mutation path；
2. 获取 Redis lease；不可用或 busy 时不创建 succeeded run；
3. 在短事务插入 `status=running` cleanup run；generated unique 阻止第二实例；
4. 若存在 `running` 且 heartbeat 未超过 `max-run-seconds + renewal grace`，本实例退出；
5. 只有持有 Redis lease 且旧 heartbeat 已过 stale 边界，才可在同一事务将旧 run 标为 failed 后创建新 run；
6. 每个 operation/run 事务前同时验证 lease token、cleanup run ID 与 active slot；
7. 事务之间更新 heartbeat，不在持有 operation/run 业务锁时锁 cleanup-run 行；
8. lease 丢失或 active owner 改变时，在下一 mutation 前受控停止并把本次 run 标为 failed；
9. release 只 compare-delete 自己的 token。

stale takeover 后旧进程即使恢复，也会在下一个 checkpoint 因 run ID/active slot 不匹配停止。单个已开始的 MySQL
事务仍由 operation/run 行锁和 guarded update 保证幂等。

### 7.3 succeeded 的定义

以下全部满足才写 `succeeded`：

- 所有已开始的 mutation 事务均得到确定 committed/rolled-back 结论；
- sweep 后永久一致性查询为空；
- backlog、oldest 和容量样本成功计算并持久化；
- 没有 sweep 级 infrastructure、无法分类的 unknown outcome、永久 contract/P0 invariant 或统计持久化错误。

“本次只处理上限 20 个，仍有正常 backlog”可以 succeeded；数量和年龄门禁独立判断。锁内资格变化导致 skip
也是 succeeded。单个候选超预算、deadlock/lock timeout 在有界重试后确认整体 rolled back，属于隔离的
`warning + skipped`，增加 `warning_candidate_count` 后允许本次 run succeeded；它仍保留为后续 sweep 候选并受
backlog/425 天门禁约束。数据库不可用、未知 commit outcome 未能分类、tree/rollup 不一致或 sweep 级统计失败
必须 failed。

### 7.4 两类候选的进度保证

operation-bound 与 operationless 不能互相饿死。每次 run 在两类均有 candidate 时，先各处理最老的一个，随后
按 `(effective_anchor, candidate_type, stable_id)` 合并排序继续处理，并分别受各自 20 个上限约束。

单个 tree 超 1,000 attempt、或单候选 known rollback 在有界重试后已确认整体回滚时，记录候选 warning/skipped
并继续稳定游标后的其他候选；它不能永久挡住无关 operation/run，也不能消除本次 succeeded heartbeat。只有
infrastructure、unknown outcome 无法分类、永久 contract/P0 invariant 或 sweep 级统计失败才令 run failed；
前三类立即停止 sweep，统计失败发生在末尾并保留此前已确定提交的 mutation 事实。

---

## 8. mutation operation 候选

### 8.1 无锁预筛

稳定游标为：

```text
(detail_retention_anchor_at, operation_id)
```

核心条件：

```sql
detail_purged_at IS NULL
AND outcome_status IN ('committed','closed_no_mutation')
AND detail_retention_anchor_at <= :sweep_started_at - INTERVAL 365 DAY
```

预筛同时使用 `EXISTS attempt/item`，避免为已无 detail 但 marker 异常的行静默写 marker；这种形状由一致性巡检
按第 12.2 节分类为 P0。预筛只产生 candidate ID，不授予删除资格，也不存在 marker-only 自动修补路径。

### 8.2 锁内最终资格

每个 candidate 独立开启事务，先按 ID 锁 operation，再重验：

1. `detail_purged_at IS NULL` 且 `detail_purged_item_count IS NULL`；
2. outcome 为 `committed/closed_no_mutation`，finalized/source/reason/anchor 形状合法；
3. anchor 不晚于本次固定 cutoff；
4. 永久证明与 operation type 精确匹配；
5. 全部关联 run 已终态、`finished_at` 与 `attempt_rollup_completed_at` 非空；
6. run rollup 守恒等式成立；
7. 不存在 `prepared/running` attempt；
8. 所有 reconciliation 行已 resolved，且没有与最终 outcome 冲突的 decision；
9. 不存在 active failure group/active key；
10. WP7 cleanup operation 的 item、ledger 与最终结论一致；
11. 锁定的 attempt 数未超过 1,000；超过时不开始 DELETE，记录该 operation 为 warning/skipped并继续其他候选，
    禁止分块提交。物理 item 超过 20 或与 operation/ledger contract 不一致不是预算 warning，而是 P0 invariant。

锁内普通变化，例如另一个实例已经提交 marker、anchor 被合法向后推进或新 audit 使 cutoff 未到，只跳过当前候选。
marker/detail 混合、rollup 守恒失败、非法 writer 或永久证明冲突属于 P0，不得作为普通 skip。

### 8.3 永久证明矩阵

| operation type/outcome | 必须存在的永久证明 |
|---|---|
| `website_batch/committed` | 恰好一条 contract 完全匹配的 `data_retention_batch_commit` |
| `poll_slice/committed` | 恰好一条 operation/tuple 完全匹配的 `data_retention_purged_slice` |
| `poll_preserved_cleanup/committed` | 恰好 `item_count` 条 ledger 为 `cleaned` 且 `cleanup_operation_id` 等于本 operation |
| `*/closed_no_mutation` + reconciliation | 至少一条 resolved `closed_no_mutation` reconciliation 与 operation 最终化一致 |
| `*/closed_no_mutation` + terminal attempt | operation source=`terminal_attempt`、reason=`verified_no_mutation` |

`poll_audit_purge_runs` 是永久补充审计，不替代 poll marker/ledger commit proof。

---

## 9. operation detail 原子清理事务

事务顺序固定：

```text
lock operation
→ lock operation-run/run rows（run_group_id 升序）
→ lock reconciliation rows（id 升序）
→ lock failure group/item/active-key gates（id/business key 升序）
→ lock attempt rows（id 升序）
→ lock batch item rows（id 升序）
→ revalidate complete tree and rollup conservation
→ DELETE batch item
→ DELETE attempt
→ guarded update operation-run purge counts
→ zero-row post-check
→ guarded update operation item count + detail_purged_at
→ commit
```

run 锁位于 operation 后，因为所有现有 related-attempt writer 已先锁 operation 再锁 run。首次 origin 创建先锁 run、
再插入此前不存在的 operation，不会与已存在 operation 的 WP8 candidate 构成反向环。架构测试必须继续扫描任何
新增 writer，禁止出现“先锁已存在 run、后锁已存在 operation”的路径。

### 9.1 快照与守恒

获得全部锁后：

- 按 run 对 attempt snapshot 分组；
- item snapshot 必须全部属于当前 operation；
- 当前 attempt 数、已 purge 计数和 operationless purge 计数必须与每个 run 的固化 rollup 守恒；
- operation-run 当前 `detail_purged_attempt_count` 在 marker 前必须为 0；
- operation tree 必须包含所有 run relation 下属于该 operation 的 attempt，不接受 caller ID 集合。

### 9.2 guarded writes

删除返回数必须等于锁定快照数。随后：

1. 对每条 operation-run，以 `detail_purged_attempt_count=0` guarded update 为该 run 的 snapshot count；
2. 复查当前 operation 的 attempt/item 均为 0；
3. operation guarded update 条件至少包含：
   `detail_purged_at IS NULL`、原 anchor 等值、原 outcome 等值、`detail_purged_item_count IS NULL`；
4. 写 `detail_purged_item_count=:actual_deleted_items` 和数据库 UTC `detail_purged_at`，必须恰好一行；
5. marker 时间不得早于 anchor+365 天。

任何一步失败整体 rollback。不得在 Java `finally` 或事务外补 marker/count。

### 9.3 commit outcome 不明

重试前重新读取 operation：

- marker 非空、attempt/item 为 0、operation-run counts 与原 rollup 守恒：视为 committed replay；
- marker 为空、purge counts 仍为 0、完整 detail tree 与 rollup 守恒：视为 rolled back，可重新执行；
- 其他任意混合状态：P0 invariant，停止 sweep、告警并 fail closed；
- 禁止根据网络异常直接重放 DELETE。

---

## 10. operationless attempt 原子清理

候选 run 条件：

```sql
status IN ('succeeded','failed','skipped')
AND finished_at IS NOT NULL
AND attempt_rollup_completed_at IS NOT NULL
AND finished_at <= :sweep_started_at - INTERVAL 365 DAY
AND EXISTS (
  SELECT 1 FROM data_retention_batch_attempt a
   WHERE a.run_group_id = run.id AND a.operation_id IS NULL
)
```

每个 run 独立事务：

1. `SELECT run ... FOR UPDATE`；
2. 重验 run 终态、rollup、365 天边界；
3. 按 ID 锁定该 run 全部 `operation_id IS NULL` attempt；
4. 任一 attempt 为 `prepared/running` 时跳过整个 run；
5. 验证 run rollup 守恒；
6. 删除完整 operationless snapshot，返回数必须一致；
7. guarded 增加 `operationless_attempt_purged_count`；
8. 复查该 run 无 operationless attempt 后提交。

不能只删除 365 天前创建的某几行；operationless 生命周期由 run finish 统一决定。`parent_attempt_id` 没有 FK，
删除后不要求把永久或尚未到期行中的 parent snapshot 清空。

---

## 11. run 水位更新

`attempt_detail_purged_at` 不参与 candidate selection。它只在独立短事务中更新：

1. 锁定 run；
2. 验证 rollup 守恒；
3. 确认当前时间点该 run 不存在已到期且满足其他资格的 operation-bound detail；
4. 确认不存在已到期 operationless detail；
5. 以 `GREATEST(现值, UTC_TIMESTAMP(3))` 向后推进。

只处理本 sweep 触达的 run 即可，水位滞后不影响正确性。尚未到期、仍 unresolved 或未来由 audit-only relation
关联的记录可以存在，所以水位不能用作“该 run attempt 已清空”的断言。

---

## 12. 永久一致性巡检

每次 sweep 结束和每日监控必须至少验证以下空集：

### 12.1 marker 后仍有 detail

```sql
SELECT o.operation_id
  FROM data_retention_operation o
 WHERE o.detail_purged_at IS NOT NULL
   AND (
       EXISTS (SELECT 1 FROM data_retention_batch_attempt a WHERE a.operation_id = o.operation_id)
       OR EXISTS (SELECT 1 FROM data_retention_batch_item i WHERE i.operation_id = o.operation_id)
   );
```

### 12.2 marker 为空且 detail tree 已空

operation 的创建事务必定同时创建并锁定第一个 attempt；在 WP8 之前没有任何合法 detail DELETE writer。
因此 `detail_purged_at IS NULL` 且 attempt/item 同时为零不是合法“天然空 operation”，也不能通过 marker-only UPDATE
补救。自动补 marker 会在删除事实和 commit outcome 不可证明时伪造生命周期终点。

以下查询必须返回空集：

```sql
SELECT o.operation_id
  FROM data_retention_operation o
 WHERE o.detail_purged_at IS NULL
   AND o.detail_purged_item_count IS NULL
   AND NOT EXISTS (
       SELECT 1 FROM data_retention_batch_attempt a
        WHERE a.operation_id = o.operation_id
   )
   AND NOT EXISTS (
       SELECT 1 FROM data_retention_batch_item i
        WHERE i.operation_id = o.operation_id
   )
   AND NOT EXISTS (
       SELECT 1 FROM data_retention_operation_run x
        WHERE x.operation_id = o.operation_id
          AND x.detail_purged_attempt_count <> 0
   );
```

该查询同时覆盖 finalized、pending、rolled_back、unknown 和 manual_review operation；其中评审指出的
“finalized + marker NULL + purge counts 0 + zero tree”是明确 P0 invariant。readiness 必须在 candidate scan 之外
独立执行该查询，命中后 run failed、告警、请求 global false，并阻断 enable/canary。

### 12.3 marker/count 形状错误

- marker 与 `detail_purged_item_count` 一空一非空；
- marker 为空但任一 operation-run purge count 非 0；
- marker 非空但 operation-run 关联的 current attempts 与 purge count/rollup 不一致；
- run rollup不满足“current + purged = fixed rollup”；
- `detail_purged_at < anchor + 365 天`；
- marker 后 anchor/outcome/finalization 被改写。

最后一项需要应用级 immutable architecture test、数据库审计比对和 guarded writer 测试共同证明；不能声称单表 CHECK
可以检测历史改写。

### 12.4 purge 后非法 writer

所有 attempt/item、retry/reconcile relation、failure group 和会改变业务结论的 writer 必须先锁 operation 并调用
统一 `requireDetailPresent`。marker 后：

- 新 mutation detail 一律拒绝；
- 只允许一致结论 audit-only reconciliation/relation；
- audit-only run 不创建 attempt；
- 与永久 outcome 冲突时产生 P0 人工事件。

任一巡检非空都是 P0 数据治理事件：本次 run failed、立即告警、请求 global false，并阻断 enable/canary。

---

## 13. backlog、年龄与容量计算

所有阈值以数据库 UTC 和 sweep 结束后的 live read 为准。扫描命中不等于成功清理。

### 13.1 eligible mutation backlog

eligible 必须满足第 8.2 节除行锁外的全部只读条件。计数为：

```text
eligible_backlog_attempt_count = eligible operations 当前 attempt 行数之和
eligible_backlog_item_count    = eligible operations 当前 item 行数之和
```

阈值使用严格大于：

- attempt > 12,000：请求 global false；
- item > 96,000：请求 global false；
- 恰好 12,000/96,000 只告警，不自动 disable。

### 13.2 425 天 hard age

以下任一存在即请求 global false：

- `detail_purged_at IS NULL`、仍有 mutation detail，且 operation anchor 严格早于 now-425 天；
- 仍有 operationless detail，且所属 terminal run `finished_at` 严格早于 now-425 天。

hard age 不因 active failure、pending reconciliation 或其他 hold 被隐藏；这些 hold 只阻止删除，不能阻止年龄关停。

### 13.3 retention 容量 allowlist

容量只统计本文第 4.2 节永久表、attempt/item 和 cleanup-run 表的 `data_length + index_length`。代码使用显式
allowlist；以后新增 retention 表时 migration contract test 必须要求同步更新，禁止用名称前缀静默漏表。

### 13.4 30 天增长与五年投影

每次 succeeded cleanup run 保存真实总字节。投影使用最近 30 个完整自然日内的 succeeded 样本：

```text
daily_growth_bytes = max(0, (latest_bytes - oldest_bytes) / elapsed_days)
five_year_raw      = current_bytes + daily_growth_bytes * 1825
projected          = ceil(five_year_raw * 1.30)
```

若不足两个 succeeded 样本或 elapsed < 7 天，readiness 不得用零增长放行，直接返回
`CAPACITY_HISTORY_INSUFFICIENT`。匿名化生产克隆报告仍是外层独立门禁，不能注入一个运行时数字绕过这 7 天
真实 heartbeat/增长观察期。

该线上投影用于持续门禁，不替代 WP2 10k/50k/100k 物理 fixture 和生产克隆实测。projection > 5 GB 阻断
enable/canary，恢复后不自动 enable。

### 13.5 实际容量分级

以 5 GB 为批准上限：

| 实际 data+index | 动作 |
|---|---|
| `>= 3.0 GB`（60%） | 告警，复核 30 天投影 |
| `>= 4.0 GB`（80%） | 阻断新的 enable/canary |
| `>= 4.5 GB`（90%） | 请求 global false |

比较使用字节常量 `5 * 1024^3` 的 60%/80%/90%，文案显示值不替代整数比较。

---

## 14. readiness 决策与接入点

### 14.1 决策码

至少冻结：

```text
READY
MIGRATION_NOT_READY
CLEANUP_NEVER_SUCCEEDED
CLEANUP_SUCCESS_STALE_48H
CLEANUP_RUN_ACTIVE_STALE
DETAIL_INVARIANT_VIOLATION
ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED
ELIGIBLE_ITEM_BACKLOG_EXCEEDED
OLDEST_DETAIL_EXCEEDED_425D
RETENTION_CAPACITY_80_PERCENT
RETENTION_CAPACITY_90_PERCENT
FIVE_YEAR_PROJECTION_EXCEEDED
CAPACITY_HISTORY_INSUFFICIENT
READINESS_QUERY_FAILED
```

readiness 发生 SQL/metadata/timeout 异常时返回 fail-closed，不允许“监控不可用视为通过”。
一次 evaluation 返回全部命中 code，并按 `P0 invariant → 425d → backlog → 90% → 80%/projection →
48h/history/metadata` 选择稳定 primary code；调用方不得因先看到较轻 blocker 而漏掉 disable action。
决策结果同时携带 severity：`block_only/disable_global/p0_disable_global`。readiness evaluator 本身保持只读；
scheduler 和 enable/canary caller 只要观察到后两类，就必须先把同一不可变 decision 交给
safety converger 请求 global false，再返回/记录原 decision。这样读取和写开关职责分离，但不会出现
“canary 发现 P0 只拒绝自己、已开启的普通 execute 仍继续”的窗口。

### 14.2 48 小时规则

最新 `status='succeeded'` cleanup run 不存在，或其 `finished_at <= now-48h`，均阻断新的 enable/canary并告警。
该规则本身不自动 disable 已开启的 dynamic switch；P0 invariant、12k/96k、425d、90% 才要求自动向 false 收敛。

### 14.3 dynamic enable

`DataRetentionDynamicSwitchManagementService` 在两个位置调用 readiness：

1. 创建 target=true 请求前，避免生成明知不可批准的 requested row；
2. approval 完成、Redis enable CAS 前再次读取，关闭审批等待期间的 TOCTOU 窗口。

global 和任一 domain true 都受同一个 WP8 readiness 约束。disable 永远不受 readiness 阻止。
对非终态 target=true change 的幂等 replay 也必须重新检查 readiness；只有已经 `APPLIED=true`、本次不再发生
Redis write 的纯终态读取 replay 可以直接返回。

### 14.4 canary

`DataRetentionExecuteCoordinator` 对 `ExecutionSource.CANARY` 在创建 WP3 run、operation/attempt 和获取业务 execute
lease 前调用 readiness。失败返回稳定 409/503，不写任何 mutation audit，也不消耗业务 transaction budget。

manual/scheduler/retry 的正常业务 execute 仍由静态/dynamic gate 管理；严重 WP8 blocker 会先请求 global false，使这些
路径 fail closed。48h/80%/projection 仅按上游决定阻断新 enable/canary，不擅自扩大为已有开关下的普通 retry 停止。

### 14.5 与其他生产门禁的关系

WP8 `READY` 只表示 detail cleanup/capacity 条件通过，不表示：

- V133 已关闭；
- 生产克隆已通过；
- migration 已获生产授权；
- canary 已获人工审批；
- 任一 execute 开关应当开启。

生产准入层必须对这些独立门禁做 AND，不得让 WP8 服务伪造其他结论。

---

## 15. 自动 disable 协议

触发条件：P0 invariant、attempt/item backlog 超限、425 天 hard age、实际容量 90%。cleanup scheduler、
enable readiness 和 canary readiness 任一路径首次观察到这些条件，都执行同一个 converger；
deterministic change ID 保证并发观察不会形成不同语义的 enable/disable 竞争。

动作固定：

1. 调用既有 `DataRetentionDynamicSwitchDisablePort`；
2. switch key 固定为 `GLOBAL_EXECUTE`；
3. source 固定为 `RECONCILE`，operator 使用已验证的 reconcile service account；
4. converger 先读取 global Redis payload；合法 false 不创建新的 false change，已有 requested false 交给既有
   resume/reconciler；合法 true 则把当前 payload 的 `(change_id,version)` 冻结为 disable basis；
5. true basis 的 change ID 由以下 UTF-8/LF canonical bytes 生成 name-based UUID，最后一行也有 LF：

   ```text
   schema=retention-wp8-disable-v1
   switchKey=global_execute
   decision=<primary decision code>
   observedGlobalChangeId=<current true payload change_id>
   observedGlobalVersion=<current true payload version>
   ```

   生成算法固定为 `UUID.nameUUIDFromBytes(canonicalBytes)`，即 RFC 4122 version 3/name-based UUID；输出 lowercase。
   固定向量 decision=`ELIGIBLE_ATTEMPT_BACKLOG_EXCEEDED`、change ID=`11111111-1111-4111-8111-111111111111`、
   version=`42` 时必须得到 `2defe299-fcd6-3e86-8e04-de2e7921aa09`；

6. reason 固定为
   `wp8:<decision>:global_true=<observedGlobalChangeId>@<observedGlobalVersion>`；不得把持续变化的 backlog、age、
   bytes 或自由文本写入 reason/ticket，从而保证同一 ID 的所有 WP4 immutable command 字段完全相同；具体观测值
   只写 cleanup run、metric 和 alert；`approval_ref` 与 `ticket_ref` 固定为 NULL，requested_by 固定为已验证的
   reconcile service account；
7. 同一 true payload 上的并发观察得到同一 change ID/reason，可幂等 resume；即使同日先成功 false、后经人工
   双人流程产生新的 true payload，新 `(change_id,version)` 也必然生成新的 disable ID 和新的 Redis false 写入；
8. Redis unavailable/malformed 时运行时 gate 本身已 fail closed。converger 优先 resume 既有 requested false；没有时
   使用最新不可变 MySQL global audit `(change_id,version)` 作为 `untrustedAuditBasis` 生成独立 deterministic ID，
   持久化 requested false 并告警；Redis 恢复后由既有 stale compensation/reconciler 收敛。后续读到合法 true 且
   没有可 resume 的 requested false 时，改用该 true payload basis；
9. 已经 false 时保持 false，不写 true 补偿；
10. 条件恢复只使 readiness 的对应 code 消失，不调用 enable port。

若 disable 自身结果不明，cleanup run 可以在 detail mutation 已成功的情况下标 failed，因为 fail-closed 动作未得到
可信终态。下一次 scheduler 对同一 basis/decision 重试完全相同的 deterministic disable；不得用新的当前时间、
变化后的 reason 或 UTC 日期生成另一条语义冲突 command。

`untrustedAuditBasis` 使用同一 schema 的独立字段名
`untrustedAuditChangeId/untrustedAuditVersion`；从未存在 global audit 时分别写 `<bootstrap>`/`0`。其 reason 固定为
`wp8:<decision>:global_audit=<id>@<version>:redis_untrusted`。trusted/untrusted canonical input 不能相互碰撞。

---

## 16. 错误分类与继续策略

| 分类 | 示例 | 当前 operation | domain sweep | cleanup run |
|---|---|---|---|---|
| 锁内资格变化 | anchor 推进、已被其他实例 purge | skip | 继续 | 可 succeeded |
| known transaction rollback | deadlock/lock timeout 且确认未提交 | warning/skipped | 有界重试一次，失败后继续其他候选 | 可 succeeded |
| tree 超安全预算 | attempt 超 1,000 | warning/skipped，不分块 | 告警并继续其他候选 | 可 succeeded |
| infrastructure | MySQL/Redis lease/metadata 不可用 | 不开始下一 mutation | 停止 | failed |
| unknown commit | 客户端失去 commit ack | 按 9.3 重读 | 可分类后继续，否则停止 | 依分类 |
| contract/P0 invariant | marker+detail、rollup 不守恒 | 禁止修补 | 立即停止+disable | failed |

候选级 warning 必须满足“影响范围已锁定、事务确定未提交、永久状态未被改写”；无法证明这三项时不能降级为
warning。持续 warning 会继续计入 backlog/oldest 并触发相应治理门禁，但不会伪造 cleanup job 不健康。

WP8 不创建 WP5 business operation 来审计自己，避免 cleanup 为自己不断产生新的 attempt/item。永久 operation marker、
purge counts、run waterline 和 `data_retention_detail_cleanup_run` 共同提供证据。

---

## 17. Java 组件边界

建议新增：

```text
retention/detailcleanup/
  DataRetentionDetailCleanupScheduler.java
  DataRetentionDetailCleanupLease.java
  DataRetentionDetailCleanupService.java
  DataRetentionDetailCleanupRepository.java
  DataRetentionDetailCleanupRunRepository.java
  DataRetentionDetailCleanupReadinessService.java
  DataRetentionDetailCleanupSafetyConverger.java
  DataRetentionDetailCleanupCapacityService.java
  DataRetentionDetailCleanupMetrics.java
  DataRetentionDetailCleanupAlertSink.java
  DataRetentionDetailCleanupModels.java
```

职责：

- scheduler 只负责 cron、静态开关、lease 和顶层 run；
- service 负责编排稳定 candidate、checkpoint、事务边界和终态分类；
- repository 独占 candidate SQL、locking read、DELETE、guarded marker/count 和 invariant SQL；
- run repository 独占 cleanup-run active slot、heartbeat 和 terminal write；
- readiness service 只读，不执行 cleanup 或 enable；
- safety converger 接收已计算的 severe decision，只能调用 typed global disable，不能调用 enable；
- capacity service 使用冻结 allowlist 和公式；
- alert sink 复用 system alert 能力，但使用 WP8 稳定 code；
- typed purge port 可以位于 `DataRetentionP0AuditService` 或窄接口实现中，禁止 controller/普通 mapper 直接 DELETE。

架构测试必须阻止其他 package 删除 attempt/item 或更新 `detail_purged_*`/purged counts。

---

## 18. 可观测性

至少发布：

```text
retention_detail_cleanup_run_total{status}
retention_detail_cleanup_last_success_age_seconds
retention_detail_cleanup_candidate_operations
retention_detail_cleanup_purged_operations_total
retention_detail_cleanup_warning_candidates_total{code,type}
retention_detail_cleanup_purged_attempts_total
retention_detail_cleanup_purged_items_total
retention_detail_cleanup_eligible_attempt_backlog
retention_detail_cleanup_eligible_item_backlog
retention_detail_cleanup_oldest_age_days
retention_detail_cleanup_table_bytes
retention_detail_cleanup_projected_five_year_bytes
retention_detail_cleanup_readiness{code}
retention_detail_cleanup_invariant_violations
```

告警至少覆盖：48h 无成功、12k/96k、425d、60/80/90%、projection、stale owner、P0 invariant、auto-disable
requested 未应用。指标 label 不得包含 operation ID、business key、URL 或异常自由文本。

---

## 19. 测试 SPEC

### 19.1 纯单元/契约测试

- 365 天边界精确到毫秒；
- 425 天和 48 小时边界；
- 12,000/96,000 严格大于语义；
- 60/80/90% 整数字节边界；
- 30 天投影、30% ceiling、样本不足 fail-closed；
- migration 号唯一、forward-only、列/索引/CHECK/generated expression 精确；
- 所有新 enabled 和既有 execute/enable-write 默认 false；
- 永久表 DELETE allowlist 为空，只有 attempt/item 可删；
- SQL/Java architecture 禁止 marker/count 非 typed writer 更新；
- readiness 同时接入 target=true create、approve-before-CAS 和 canary-before-run；
- disable 不受 readiness 阻断，后台没有 false→true 调用；
- 同一 global true identity/decision 在 backlog 数变化后仍生成完全相同的 change ID/reason/ticket；
- 新 global true identity 即使发生在同一 UTC 日期也生成新的 disable ID；
- trusted canonical bytes 的固定 UUID 向量为 `2defe299-fcd6-3e86-8e04-de2e7921aa09`；
- 候选级 warning 与 sweep-level failed 分类不会互相替代。

### 19.2 Required MySQL 8 integration

必须使用真实 MySQL 8，不接受 H2 模拟：

1. website committed operation 满 365 天清理完整 retry tree；
2. poll slice 通过 purged-slice marker；
3. poll cleanup 通过 cleaned ledger；
4. closed-no-mutation 的 reconciliation/terminal-attempt 两种证明；
5. 364 天 23:59:59.999 不入选，365 天边界入选；
6. 一年前 unknown 今天 reconcile，anchor 从今天计时；
7. active failure、pending reconciliation、prepared/running attempt 分别阻断；
8. item delete 后故障、attempt delete 后故障、marker 前故障均整体 rollback；
9. marker/count guarded update 影响行数不为 1 时 rollback；
10. 同一 run 多 operation 分次 purge 后，第二次仍通过 rollup 守恒；
11. operation 跨 origin/retry/reconcile run，按 run 写精确 purge attempt count；
12. operationless run 全量删除和累计 count；
13. run 同时有 operation-bound/operationless detail 时守恒正确；
14. run waterline 可后移但从不参与 candidate selection；
15. unknown commit 的 committed replay、rolled-back retry、mixed P0 三分支；
16. WP7 unresolved cleanup ownership 不入选，committed cleanup 满 365 天后可清理；
17. marker 后 attempt/item writer 被拒绝，audit-only reconcile 仍可写且不推进 anchor；
18. cleanup-run generated active slot 阻止双 running，stale takeover 有 guarded owner；
19. 一个超 1,000 attempt 的 operation 与正常候选共存时，前者 warning/skipped、后者提交且 run succeeded；
20. 单候选 deadlock/lock timeout 经有界重试仍确定 rollback 时 run 可 succeeded，unknown/infra/P0 仍 failed；
21. `marker NULL + purge counts 0 + zero tree` 命中 P0 巡检，不写 marker、readiness fail-closed并请求 global false；
22. sweep 后所有永久一致性查询为空；其他注入非法形状时 readiness 同样 fail-closed。

### 19.3 Required 并发测试

使用两个物理 JDBC connection、明确 barrier/latch，不使用 sleep 猜时序：

1. writer 先锁 operation 并推进 anchor，WP8 等待后锁内跳过；
2. WP8 先锁 operation 并提交 marker，writer 等待后因 detail purged 被拒绝；
3. 两个 cleanup runner 同时开始，只有一个 active slot/lease owner；
4. operation purge 与 run finish 不死锁；
5. operationless writer/finish 与 operationless purge 通过 run lock 串行；
6. purge 后 audit-only reconcile 与 invariant query 不产生假阳性。

### 19.4 Required Redis integration

- attempt 12,001、item 96,001、425d、90% 分别请求 `global_execute=false`；
- 恰好边界不 auto-disable；
- Redis unavailable 时 MySQL requested disable 保留，reconciler 后续可完成；
- stale version 使用既有补偿协议；
- 同一 true payload 上并发 observer 只形成一个幂等 false command；
- 同一 UTC 日完成 `false → 人工双人审批 true → blocker 再次 false`，第二次使用新 true identity 创建新 change，
  确实执行新的 Redis false CAS；
- 同一 true identity 下观测 backlog/bytes 变化不触发 `CHANGE_ID_CONFLICT`；
- blocker 恢复不自动 true；
- cleanup job 不读取业务 execute=true，global false 时仍能继续释放 detail。

### 19.5 Required readiness 主链

- target=true create 在 blocker 下不创建 requested enable；
- create 时 READY、approve 前变 blocked 时 CAS 不执行；
- disable 在所有 blocker 下仍可执行；
- canary blocked 时不创建 WP3 run/operation/attempt；
- 最新 cleanup succeeded 小于 48h 通过，等于/超过 48h 阻断；
- succeeded run 含候选 warning 时仍刷新 48h heartbeat；
- 80%/projection 只阻断 enable/canary，90% 额外 auto-disable；
- readiness SQL 异常返回 fail-closed；
- WP8 READY 不掩盖 V133/生产克隆/审批等外层 blocker。

### 19.6 容量与 migration

- 10k/50k/100k 以及接近上限 fixture，含索引记录真实 data/index bytes；
- 正常清理五年稳态 +30% ≤5GB；
- 5.77GB fail-closed 包络触发阈值，不作为正常稳态目标；
- 从 V367 前状态迁移、逐步骤故障、Flyway repair/re-run/no-op；
- MySQL 8.0.45 本地与生产目标 8.0.46 的 metadata 表示差异显式报告；
- V133 真实空库链仍可预期失败，但不能把诊断测试计为 WP8 生产迁移通过。

---

## 20. Required profile 建议

沿用现有 `retention-mysql-it`，并加入 Redis required 场景；不得让 Docker 不可用时 silently skip 后仍报告通过。
本地 MySQL/Redis 可用时，最终口径必须同时记录：

```text
tests run / failures / errors / skipped
MySQL SELECT VERSION()
Redis server version
Flyway start/target version
目标 migration 文件名与 SHA-256
所有 execute/enable-write/detail-cleanup enabled 的有效值
```

profile 未提供真实 MySQL/Redis、存在 skipped、或只执行 repository mock 时，WP8 不能标记 Required 完成。

---

## 21. 交付文件边界

评审通过后预计只修改/新增：

```text
docs/data-retention-p0-wp8-detail-cleanup-readiness-spec-2026-08-14.md
geo-server/src/main/resources/db/migration/<NEXT_VERSION>__data_retention_detail_cleanup_readiness.sql
geo-server/src/main/resources/application.yml
geo-server/src/main/java/com/huanjing/geo/module/retention/config/DataRetentionProperties.java
geo-server/src/main/java/com/huanjing/geo/module/retention/detailcleanup/**
geo-server/src/main/java/com/huanjing/geo/module/retention/audit/**
geo-server/src/main/java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchManagementService.java
geo-server/src/main/java/com/huanjing/geo/module/retention/execute/DataRetentionExecuteCoordinator.java
geo-server/src/test/java/com/huanjing/geo/module/retention/detailcleanup/**
geo-server/src/test/java/com/huanjing/geo/module/retention/audit/**
geo-server/src/test/java/com/huanjing/geo/module/retention/dynamicswitch/**
geo-server/src/test/java/com/huanjing/geo/module/retention/execute/**
geo-server/pom.xml（仅 required profile 确有必要时）
```

不得顺手修改工作区内 extension、content、frontend、node_modules、部署包或其他 migration。

---

## 22. 验收清单

- [ ] SPEC 聚焦评审通过并明确授权开发；
- [ ] migration 号在开发开始和提交前完成全 refs/worktree/untracked 扫描；
- [ ] 只使用前向 migration，V354/V367 未修改；
- [ ] operation-bound detail 按完整 operation 单事务 item→attempt 删除并写永久 marker；
- [ ] operationless detail 按完整 run subset 单事务删除；
- [ ] per-operation-run、per-operation、per-run purge count 使固化 rollup长期守恒；
- [ ] website/poll slice/poll cleanup/closed-no-mutation 永久证明全部覆盖；
- [ ] active failure、pending reconciliation、non-terminal attempt、WP7 unresolved ownership 全部阻断；
- [ ] purge 后 writer guard 与 audit-only exception 正确；
- [ ] run waterline 不参与 candidate selection；
- [ ] cleanup 独立 lease、active slot、stale takeover 和 checkpoint 通过；
- [ ] 候选级 known rollback/超预算只记 warning/skipped，其他候选可推进且 succeeded heartbeat 有效；
- [ ] zero tree + marker NULL 明确作为 P0 巡检，禁止 marker-only 修补；
- [ ] 48h、12k/96k、425d、projection、60/80/90% 门禁精确；
- [ ] dynamic enable create/approve 与 canary 三个接入点 fail-closed；
- [ ] auto-disable ID 绑定 observed global true identity，同日重新 enable 后能够再次 disable；
- [ ] severe blocker 只向 global false 收敛，恢复绝不自动 true；
- [ ] MySQL 8/Redis required profile 0 skipped；
- [ ] 两连接 barrier 并发测试通过；
- [ ] migration failure/repair/re-run/no-op 通过；
- [ ] 容量 fixture 与匿名化生产克隆仍分别保留证据要求；
- [ ] 所有 execute/enable-write/detail-cleanup 默认 false；
- [ ] `git diff --check`、围栏、尾随空白和文件范围核验通过；
- [ ] 未暂存、提交或覆盖用户无关改动。

---

## 23. 评审请求

本轮只请求评审以下决定，不请求授权生产操作：

1. WP8 只删除 attempt/item，永久 evidence 不归档；
2. operation 完整 tree 单事务、operationless 完整 run subset 单事务；
3. 增加 purge count 以在多 operation 分次删除后继续证明固定 rollup；
4. 增加轻量、永久 cleanup-run 历史作为 48h heartbeat 和 30 天容量样本；
5. cleanup 使用独立静态 false 开关与 lease，不受业务 execute dynamic switch 控制；
6. 48h/80%/projection 阻断 enable/canary，12k/96k/425d/90%/P0 invariant 自动请求 global false；
7. readiness 在 enable create、approve-before-CAS、canary-before-run 三处执行；
8. 条件恢复只解除 blocker，绝不自动 enable；
9. auto-disable 幂等键绑定当前 global true `(change_id,version)`，reason 不包含变化中的观测值；
10. 隔离且确定回滚的候选失败是 warning/skipped，不使成功 heartbeat 永久消失；
11. marker NULL 且完整 detail tree 已空是 P0 invariant，不提供 marker-only 修补；
12. V133、生产克隆、生产 migration 和 canary 执行继续独立未授权。

在本 SPEC 获得明确“允许开发”前，不创建 migration，不修改 Java/SQL/config/test。
