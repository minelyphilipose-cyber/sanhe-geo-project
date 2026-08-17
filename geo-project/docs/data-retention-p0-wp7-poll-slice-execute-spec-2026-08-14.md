# 数据定期清理 P0 WP7：Poll Slice Execute SPEC

> 日期：2026-08-14
>
> 状态：第三轮聚焦评审修订稿；前两轮共 3 个 P1 和 1 个 P2 已按评审意见修订，尚未授权 WP7 Java/SQL/migration 开发
>
> 授权边界：本文件只冻结 WP7 poll execute 的业务与安全契约；不授权打开任何 execute/dynamic switch、执行生产迁移、生产清理或 canary
>
> 上游基线：WP2 第五次评审通过版、WP3 第三次评审通过版、WP4/WP5 代码评审通过版、WP6 本地代码与 required profile 验收完成、V354/V364/V366 当前实现

---

## 0. 结论先行

WP7 的目标不是包装现有 `PollRetentionDryRunService.purge()`，而是提供唯一的
`poll_results` domain executor，使每个 `(project_id,batch_date,question_tier)` 切片都通过
WP5 coordinator 的 authorization、lease、静态/动态门禁和预算，并在一个真实 MySQL 事务内完成：

```text
operation/attempt 锁定
→ poll slice fence
→ 锁内资格重验
→ 只删除 purgeable poll detail
→ 保留 latest completed projection
→ locked-scope post-check
→ data_retention_purged_slice marker
→ poll_audit_purge_runs 成功终态
→ operation/attempt commit
```

WP7 首版冻结为“单切片一个 operation、一个 mutation transaction”。不做 website 式 split、
failure group 或 regroup。任何 commit outcome 不明都停止本 domain，禁止盲目重试。

首轮 marker 还必须原子登记本次保留的 latest result IDs。业务 marker 永久关闭原 slice；当这些 ID
以后被其他日期的 completed projection 取代时，只允许 WP7 内部 `poll_preserved_cleanup` operation
删除旧 result 及其完整 children。cleanup-only 不重开原 slice、不接受业务写入、不重算 summary。

为保证该内部清理不会被 primary backlog 饿死，普通 manual/scheduler run 在 primary 前最多执行一个
cleanup operation，并先占用一个 mutation transaction。未解决 cleanup operation 的 item 是 preserved ID
的永久 ownership hold；fresh selector 不得绕过，retry/recovery 只能沿原 operation 处理。

WP7 完成仍不代表生产可执行。V133、WP8 detail cleanup/capacity blocker、生产 8.0.46 克隆、
空间验收和 canary 继续阻断生产准入；所有 execute/enable-write 默认值继续为 `false`。

---

## 1. 当前实现事实与必须关闭的差距

### 1.1 已有可复用能力

- `DataRetentionExecuteCoordinator` 已统一 manual/scheduler/retry 的授权、lease、gate、预算与 run rollup；
- WP3 已提供 `preparePollOrigin`、`commitPollMutation`、poll canonical scope 和永久 marker writer；
- `PollRetentionSliceLockService` 已提供持久 slice fence；
- `PollRetentionSliceGuardService` 已让主要 result/attempt/entity-judge writer 在 slice marker 后返回 409；
- legacy purge 已实现 summary reconciliation、entity-judge reconciliation、batch seal、attempt terminal、latest projection 识别和子表删除顺序；
- `data_retention_purged_slice` 与 `poll_audit_purge_runs` 已有 WP2/WP3 identity 列和约束；
- `poll_results_execute` dynamic switch 与静态 `geo.retention.poll-results.execute-enabled` 均已存在且默认 false。

### 1.2 当前 P0 缺口

1. registry 中没有真实 poll domain executor，coordinator 对 poll execute 只能 fail closed；
2. `PollRetentionDryRunService` 仍使用 legacy `DataRetentionRunAuditService` 和 legacy purge lifecycle；
3. legacy marker 未写 WP3 `operation_id`，也未与 operation/attempt finalization 原子提交；
4. `DataRetentionExecuteCommandFactory.manualPoll` 未把 `projectId/questionTier` 保存进 command scope，
   且未拒绝 execute cursor，恢复时无法从持久 run scope 精确重建原请求；
5. manual poll 对 hot window、limit、日期范围和 tier 的 server-side 校验不完整；
6. `PollPurgeAuditIdentityWriter.markSucceeded` 当前只验证 identity/marker，没有实际 guarded status update；
7. `DispatchQuestionPollPlanningService` 当前先 INSERT/UPDATE `poll_batches`，随后才建立 slice fence，
   没有先检查永久 purge marker；这允许 purge 后的同一 tuple 被 planning 重新创建；
8. shard status writer 未统一采用 slice 前缀锁。虽然删除后通常读不到 shard，但必须用真实并发测试证明
   不会出现 shard-first/slice-first 锁序反转或 marker 后复活；
9. scheduler dry-run 仍由外层游标循环多次调用 service，可能为一次顶层触发创建多条 legacy run；
10. 当前 marker 没有永久登记 preserved result IDs；旧 latest 被其他日期的新 projection 取代后，
    原 slice 因永久 marker 不再入选，旧 result 与 children 会永久滞留；
11. `finalizeAttempt(attemptId,...)` 等 ID-only writer 先查父记录解析 tuple，再拿 slice fence；当前没有冻结
    result tuple、attempt parent link 等 identity 的不可变规则；
12. requested/effective start/end 的 NULL、clamp 和 recovery 语义尚未完整冻结；
13. 当前 cleanup-only 设计若只使用 primary 后的剩余 transaction budget，会被持续 primary backlog 饿死；
    selector 也尚未排除已由未解决 cleanup operation item 持有的 preserved IDs；
14. 当前测试没有真实 MySQL/Redis 的 coordinator → poll executor → poll business repository 主链。

上述差距在 WP7 评审通过前只记录、不直接修改。

---

## 2. 范围与非范围

### 2.1 WP7 首版范围

- `PollRetentionDomainExecutor` 唯一注册；
- `PollRetentionBusinessRepository` 负责 selection、锁、资格重验、mutation、post-check 和 evidence；
- manual/scheduler/canary/retry 的 poll execute 接入 WP5 coordinator；
- poll typed dry-run，一次顶层请求只创建一条 WP3 run；
- exact slice operation/attempt 和永久 `data_retention_purged_slice` marker；
- marker-owned preserved-result 永久子账本与内部 cleanup-only operation；
- legacy `poll_audit_purge_runs` 永久审计与新 operation/run 的真实关联；
- late-write guard 完整 writer inventory 与 planning-before-write 修复；
- known rollback、bounded transient retry、unknown outcome 和主链恢复；
- MySQL 8/Redis required profile 和真实业务并发测试。

### 2.2 明确不在 WP7 首版

- WP8 的 365 天 operation detail cleanup；
- 425 天积压 blocker、60%/80%/90% 容量阈值和自动 disable；
- 生产克隆、生产 DDL、生产数据清理、canary 晋级；
- website batch split/failure group 机制在 poll 上的复制；
- 人工 regroup、跨 slice 合并或 caller 上传任意 tuple；
- 删除 daily summary、永久 marker、operation、run 或仍为 current latest 的结果投影；
- 恢复退休的季度 freeze；
- 将 article archive/purge 纳入 P0 coordinator。

---

## 3. 冻结不变量

1. 永久 slice identity 是 `(project_id,batch_date,normalized_question_tier)`；
2. `question_tier` 必须 trim 后用 `Locale.ROOT` 转大写；
3. `poll_slice` operation 的 `item_count` 永远为 1；`poll_preserved_cleanup` operation 每次只处理
   同一个 origin marker 下 1～20 个 preserved result IDs；
4. primary purge 的一个 mutation transaction 只处理一个 slice；cleanup-only transaction 只处理一个
   origin marker 的 preserved IDs，不生成或修改业务 slice；
5. fresh selection 不能接受 caller cursor；cursor 只属于 dry-run 响应分页；
6. execute 的 cutoff、filter、limit 和 effective config 必须在 run scope 中持久化，可跨重启重建；
7. purge marker 存在后，该 slice 的所有业务写入必须 409/fail closed，不得复活；
8. marker、业务删除、legacy audit SUCCEEDED、operation committed 和 attempt succeeded 同事务；
9. marker 是 poll commit 的唯一永久证明，attempt 不是 commit 证明；
10. primary purge 时 preserved latest result 及读路径必需的完整 children 不是 purgeable row；marker 必须
    永久登记这些 IDs；它们被更新 projection 取代后才进入 cleanup-only；首版不做字段级 slim；
11. summary 是清理前生成的永久/长期聚合事实；marker 后禁止基于 preserved subset 覆盖历史 summary；
12. 锁内任一资格重验失败，整个 slice mutation rollback；
13. unknown commit outcome 不得重选或重删，必须停止并进入 evidence/reconciliation；
14. primary purge 的 budget candidate 按 committed slice 计 1；cleanup-only 按实际删除的 preserved result ID
    计 candidate/affected，二者都不按 children 删除行数计；
15. late writer guard 和 purge 使用同一 slice fence，不允许第二套互不相认的锁；
16. 普通 manual/scheduler run 在 primary 前最多执行一个 cleanup operation；存在 fresh cleanup 候选时，
    cleanup 固定取得第一个可用 mutation transaction，primary 只能使用其余 transaction budget；
17. cleanup operation item 对 preserved ID 建立 durable ownership hold；fresh selector 不得选择任何仍被
    `pending/running/rolled_back/unknown/manual_review` cleanup operation 持有的 ID；
18. 全部静态/动态 execute 默认值保持 false，后台不得自动 false → true。

---

## 4. Command、配置与持久化 scope

### 4.1 execute 请求归一化

`DataRetentionExecuteCommandFactory` 必须在 startRun 前完成：

| 字段 | 规则 |
|---|---|
| `hotRetentionDays` | NULL 为 14；execute 小于 14 直接拒绝，不静默提升 |
| `stuckBatchSealDays` | NULL/≤0 为 7；正整数保留；设置合理 server max |
| `requestLimit` | NULL 为 20；execute 只允许 1～20 |
| `projectId` | NULL 表示全项目；非 NULL 必须 >0 |
| `questionTier` | NULL 表示全 tier；非 NULL trim+uppercase，必须符合数据库长度和 ASCII token 约束 |
| `startDate/endDate` | requested 原值允许 NULL；按第 4.2 节计算 effective 范围；clamp 后空范围返回明确 400 |
| cursor 三元组 | execute 一律拒绝；只允许 dry-run 使用且必须全有或全无 |
| trigger/approval | 沿用 WP5 source-specific 校验 |

“小于 14 天”的 manual execute 直接拒绝，避免 caller 请求值与 fingerprint effective 值不同而产生误解；
simulation-only 仍仅属于 dry-run。`requestLimit` 首版固定最大 20。

### 4.2 run scope 必填字段

除 WP5 `executionInvocation/executionBudget` 外，poll run scope 至少保存：

```text
effectiveConfig
requestLimit
projectId
questionTier
requestedStartDate
requestedEndDate
effectiveStartDate
effectiveEndDate
retentionCutoffDate
selectionOrder=batch_date,project_id,question_tier
scopeSchemaVersion=1
```

NULL 必须以稳定 canonical JSON null 保存，不能因 key 缺失产生两种语义。恢复、retry 和 terminal replay
只读持久 scope，不按当前 application defaults 重算。

日期语义冻结为：

```text
requestedStartDate = caller startDate 原值，可为 NULL
requestedEndDate = caller endDate 原值，可为 NULL
effectiveStartDate = requestedStartDate；NULL 表示无下界
effectiveEndDate = min(requestedEndDate 或 retentionCutoffDate, retentionCutoffDate)
```

若 `effectiveStartDate != NULL` 且 `effectiveStartDate > effectiveEndDate`，在 startRun 前返回 400。
recovery/retry 只读取持久化的 `effectiveStartDate/effectiveEndDate/retentionCutoffDate`，不得根据 requested 值、
当前日期或当前默认配置重新解释范围。

### 4.3 fingerprint 与 operation hash

WP7 在 WP2/WP3 v1 基础上升级 poll compatibility contract，以冻结 preserved cleanup 协议：

- policy version：`poll-results-v2`；
- config fingerprint schema：1；
- 默认 fingerprint：`f34506d8fb52a24973a93dd07c1b97746f9e88502ba3dc54953b380a153d6b71`；
- v2 在 v1 canonical config 中增加 `cleanupOperationMaxIds=20`、
  `cleanupPriority=before_primary` 和 `preservedResultLedger=true`；
- poll scope hash v1 固定字段为 domain/project/date/tier/cutoff/contract；
- exact slice `upperBound=<null>`；
- operation 持久保存 tuple 与 cutoff，不从 hash 反推。

---

## 5. 时间、候选选择与分页

### 5.1 时间

- business zone 固定 `Asia/Shanghai`；
- run 开始时一次性计算 `today` 与 `retentionCutoffDate=today-hotRetentionDays`；
- 同一 run 的所有 selection、operation 和恢复使用该固定 cutoff；
- JVM default zone、MySQL session zone 和数据库 `DATE` 转换不得改变候选集合；
- 测试必须覆盖 JVM UTC 与 Asia/Shanghai。

### 5.2 fresh selector

排序固定为：

```sql
ORDER BY batch_date ASC, project_id ASC, question_tier ASC
```

候选来源为 `poll_results` 的 distinct tuple，并满足：

- 范围/filter/cutoff；
- 不存在 status=`purged` 的永久 marker；
- 不存在同 tuple 的 `pending/rolled_back/unknown/manual_review` poll operation hold；
- 不把 caller cursor 作为 execute 跳过前缀的依据；
- 每次最多选择剩余 requestLimit，实际 mutation 仍逐 slice transaction。

为了避免一次 run 内反复选中相同 tuple，executor 应按稳定 keyset 在内存推进当前 selection cursor；
cursor 不是永久业务 identity，也不对外接受。operation 创建成功即形成持久 hold，重启从 run tree 恢复，
不重新扫描已有 operation。

### 5.3 preserved cleanup-only selector

primary slice commit 必须在永久子账本 `data_retention_poll_preserved_result` 中为每个 preserved ID 写一行，
与 `data_retention_purged_slice` marker 同事务提交。子账本至少保存：

```text
origin_operation_id
poll_result_id
project_id
batch_date
question_tier
preserved_at
cleanup_status=preserved|cleaned
cleanup_operation_id
cleaned_at
```

该表不对 `poll_result_id` 建删除限制 FK；result 删除后永久 ID 事实仍须保留。它必须通过
`origin_operation_id` 关联唯一 poll marker/operation，并通过唯一键保证一个 result 只登记一次。

fresh cleanup-only selector 只扫描 `cleanup_status='preserved'`，并用与第 8.1 节完全相同的
identity/tie-break 确认存在更新的 completed projection。它必须通过 cleanup operation item 反连接，排除已经
属于任何 `pending/running/rolled_back/unknown/manual_review` cleanup operation 的 preserved ID；不得通过改变
分组或 scope hash 为同一 ID 创建重叠 operation。

普通 manual/scheduler run 在 primary selection 前先运行一次 fresh cleanup selection。顺序固定为
`(origin_operation_id,poll_result_id)`；若存在候选，只为顺序最前的一个 origin marker 创建一个 cleanup
operation，并取该 origin 下 1～20 个无 hold IDs。每个普通 run 最多执行一个 cleanup operation，canary
不执行 cleanup-only。该 operation 固定消耗本 run 第一个 mutation transaction；随后 primary 只能使用
`maxMutationTransactions - cleanupTransactionsUsed` 的剩余 transaction budget。cleanup IDs 同时受 WP5
candidate/deadline 等既有预算约束：selection 数量还必须 clamp 到当前 candidate capacity，mutation 前再做
canonical checkpoint；不得因位于 primary `requestLimit` 之外而绕过预算。

cleanup-only 必须创建新的 `poll_preserved_cleanup` operation、origin relation、attempt 和 item rows；不得给已
committed、甚至可能已经 detail-purged 的原 `poll_slice` operation 新增 attempt。新 migration 只能前向扩展
V354 的 operation type/scope CHECK 和 typed audit port，不得修改已发布 V354。cleanup scope hash 由
`originOperationId + sorted preserved result IDs + poll compatibility contract` 生成固定向量；原 marker/operation
保持 committed 且所有字段不变。

operation/items 创建必须在仍持有 WP5 domain execute lease 时，以一个短事务锁定所选 ledger rows、再次验证
`cleanup_status='preserved'` 和“不存在未解决 item hold”，然后原子写入 operation/items。item 一经创建就对
该 `poll_result_id` 建立 durable hold；lease 过期、进程重启或 mutation rollback 都不能消除该 ownership。
这个短事务属于既有 WP3 prepare/ownership 阶段，不是第二个 business mutation transaction；真正的 cleanup
callback 仍只使用一个计入 WP5 budget 的 mutation transaction。
`pending/running` 只能走既有 WP5 recovery，`rolled_back` 只能按 persisted item 集合走 exact retry，
`unknown/manual_review` 必须保持阻断。fresh selector 只选择没有上述 hold 的其他 origin/IDs。

cleanup commit 的永久证明是子账本行在同一事务 guarded `preserved→cleaned`，并写入唯一
`cleanup_operation_id/cleaned_at`；WP3 writer 同事务完成 cleanup operation/attempt，返回可信 usage receipt。
该路径不删除/更新原 `data_retention_purged_slice`，不调用普通 slice guard 的“marker 不得存在”分支，
而是通过专用内部 repository 验证 marker 必须存在。外部 controller 不提供 cleanup-only tuple/ID 参数。

### 5.4 dry-run selector

dry-run 可以保留三元 cursor，用于只读分页；但一次 HTTP/scheduler 顶层调用只创建一条 typed run，
页为 operationless `PAGE` attempt。dry-run 不获取 execute lease、不读取 dynamic enable=true、不得写 operation/marker。

---

## 6. 锁内资格重验

mutation transaction 内必须在持有 operation 锁后获取 slice fence，再重验以下全部事实：

1. tuple 与 operation/cutoff 完全一致；
2. 永久 marker 不存在；
3. 至少存在一条 `poll_results`；
4. batch gate 存在，且无 planning/ready/running 等非终态 batch；
5. stale non-terminal batch 仍阻断并产生稳定错误，不自动 seal；
6. invocation attempt 全部为允许的终态；
7. keyword summary 的维度、来源行数、指标与 checksum 全部匹配；
8. platform summary 同样完全匹配；
9. entity-judge summary 的维度、计数和内容级 checksum 匹配；
10. preserved latest identity 集合在锁内重新计算；
11. purgeable result ID 集合与所有 child count 快照一致；
12. 没有与当前 operation 冲突的 reconciliation/failure hold；
13. WP5 gate/lease/budget checkpoint 已成功且 callback 已开始。

资格 evaluator 必须由 dry-run 与 execute repository 共享；dry-run 的普通快照结果不能替代 mutation 锁内重验。

---

## 7. 锁顺序

### 7.1 primary slice 与 cleanup-only 锁序

全局前缀沿用 WP3/WP5：

```text
operation
→ reconciliation/failure gate
→ attempt
→ poll slice fence
→ poll_batches
→ poll_results（id 升序）
→ poll_invocation_attempts（id 升序）
→ provider/search/citation/entity-judge children（各表 id 升序）
→ poll_batch_shards（id 升序）
→ poll_batch_shard_items（id 升序）
→ summary verification reads
```

实现允许通过 slice fence 先阻断同 tuple writer，再按 FK 安全顺序删除 children；不能先锁 shard/result 后再请求 slice fence。

cleanup-only 锁序固定为：

```text
poll_preserved_cleanup operation
→ attempt/items
→ preserved-result ledger rows（poll_result_id 升序）
→ origin poll marker/operation 普通一致性验证
→ origin slice fence
→ old poll_results（id 升序）及 children
```

一个 cleanup operation 只涉及一个 origin slice，因此不获取第二个 slice fence；用于证明 superseded 的 newer
projection 只做一致性读取。正常 slice purge 若删除该 witness，必然已有更晚 projection，cleanup 后仍至少保留
一个 current latest。锁内仍须重新计算 superseded 条件，不能只信 selector 快照。

这里的 `attempt/items` 必须与第 5.3 节 ownership 完全一致；mutation 不得替换、增删或重新分组 persisted
item IDs。若 locked ledger 与 persisted item 不一致，按 contract/invariant 停止，不得退回 fresh selection。

### 7.2 ID-only writer identity 协议

首版选择“identity 创建后不可变”协议：

- `poll_results.id` 对应的 `project_id/batch_date/question_tier` 创建后不可更新；
- `poll_invocation_attempts.poll_result_id` 创建后不可更新；
- provider/search/citation 的 `attempt_id`、entity-judge 的 `poll_result_id` 创建后不可更新；
- shard/item 的 batch/shard parent link 与可推导 slice identity 创建后不可更新；
- 业务 update DTO/mapper allowlist 不得携带上述 identity 列；禁止通用 `updateById` 将其作为可变字段写回；
- architecture test 扫描 mapper SQL、MyBatis entity update path 和 service wrapper，发现 identity UPDATE 即失败。

`finalizeAttempt(attemptId,...)` 等入口允许在无锁状态第一次读取 parent chain 解析 tuple；由于 identity 不可变，
该 tuple 可用于获取唯一 slice fence。获取 fence 后仍须按 parent ID 重新锁定并加载目标记录，确认记录存在、
parent chain 完整且未被 purge；若行已删除或 parent 不一致则 409/404 且不得写入。这里的二次加载用于存在性和
状态校验，不允许把 fence 后看到的另一 tuple 当作新目标继续执行。

所有 poll writer 必须做 inventory。至少覆盖：

- planning/batch/shard/item；
- result insert/upsert/staging；
- invocation attempt create/update/finalize；
- provider call/search source/citation；
- entity judge；
- result projection；
- summary recompute/backfill。

`DispatchQuestionPollPlanningService` 必须在任何 `poll_batches/shard/item/task` 写入前调用
`lockAndRequireWritable(project,date,tier)`；当前“写 batch 后才 lockSlice”不得保留。

shard-only status writer 也必须先从不可变 parent chain 解析 tuple并统一获取 slice guard；不采用“目标行删除后
自然无操作，因此可以不 guard”的例外。所有 ID-only writer 使用相同协议。

---

## 8. Mutation 与 post-check

### 8.1 preserved latest

latest identity 沿用已上线 dashboard canonical channel 规则，不使用 `platform_id`：

```text
project + questionTier + keyword identity + canonical(channel_code/platform_code)
```

排序优先级保持：

```text
batch_date DESC
→ COALESCE(updated_at,created_at) DESC
→ id DESC
```

每个 identity 只保留最新 `status='completed'` projection。NULL-safe keyword identity 和 channel canonical
表达式必须由一个共享 SQL builder 生成，selection、锁后重验和 post-check 不得各写一套。

首版保留该 result 的完整读路径必需 children，包括 invocation attempt、provider/search/citation 和 entity-judge
中当前 dashboard/detail API 会读取的记录；不做字段级 slim。primary marker 同事务把所有 preserved result IDs
写入第 5.3 节永久子账本。marker 后即使其他日期产生更新 projection，原 slice 仍保持业务只读关闭，不能因
旧 ID 已 superseded 而删除 marker或允许普通 writer。

当子账本中的 preserved ID 已不再是第 8.1 节全局 current latest，内部 cleanup-only transaction 才允许删除：

```text
lock cleanup operation/items/ledger/origin slice
→ 锁内证明 old ID 存在且已被 newer completed projection 取代
→ 按 children→old poll_result 顺序删除
→ post-check old result/children 全为 0，new current latest 仍存在
→ ledger preserved→cleaned + cleanup operation/attempt committed
→ commit
```

若 old ID 已不存在但 ledger 仍为 preserved，不能直接当本次成功；必须结合 cleanup marker/evidence 分类。
cleanup-only 不删除 shard/item（它们已在原 slice purge 删除），不更新 summary，不写第二条 slice marker，
不创建或修改 poll batch，也不改变原 operation 的 outcome/anchor/detail marker。

### 8.2 删除范围和顺序

对 purgeable result IDs 分块只用于 SQL 参数上限，不能拆成多个事务。顺序冻结为：

```text
poll_citations
→ poll_search_sources
→ poll_provider_calls
→ poll_invocation_attempts
→ poll_result_entity_judge
→ purgeable poll_results
→ poll_batch_shard_items
→ poll_batch_shards
```

不得删除：

- preserved latest `poll_results`；
- preserved latest 对实时读取仍必需的完整 children；
- keyword/platform/entity-judge daily summary；
- `poll_batches` 轻量批次审计；
- permanent marker、WP3 operation/run、legacy purge audit；
- project、keyword、platform 等主数据。

首版不得在“保留 result”名义下隐式删除其读路径仍依赖的 children；任何字段级 slim 另立后续 SPEC。

### 8.3 locked-scope post-check

marker 前必须验证：

- 实际删除的 `poll_results` 数等于锁内 purgeable ID 数；
- purgeable result IDs 已不存在；
- 每个被删 result 的 invocation/provider/search/citation/entity-judge children 均为 0；
- shard/item 为 0；
- preserved latest ID 集合和轻量字段与 mutation 前一致；
- preserved latest IDs 已逐条写入永久子账本，且与 marker/operation/tuple 精确关联；
- daily summary 行数/checksum/指标未改变；
- `poll_batches` 轻量行仍存在且已终态；
- 没有新增的非终态 writer 事实；
- 所有计数仅限定当前 tuple/locked IDs，不受无关并发影响。

任一不符抛 `POLL_POST_CHECK_FAILED`，整个事务 rollback。

---

## 9. Audit、marker 与 legacy 永久审计

### 9.1 operation/attempt

fresh slice：

1. WP3 `preparePollOrigin` 创建 operation、origin relation 和 prepared attempt；
2. attempt type 为 normal/canary；poll 不写 batch item；
3. checkpoint 成功前 attempt 保持 prepared；
4. callback 真正开始后才 mark running；
5. candidate/affected 成功均为 1，删除业务行数只进入 metrics。

### 9.2 legacy `poll_audit_purge_runs`

新 P0 路径仍保留该永久业务审计，但不让它承担主 lifecycle：

- operation 已准备、checkpoint 已成功、mutation 尚未开始时，以 REQUIRES_NEW 创建 PREPARED 行；
- `retention_run_id/operation_id/retention_domain` 必须三列全非空；
- scope 只保存 tuple/cutoff/config schema 等轻量事实，不保存 result IDs、prompt、response 或 payload；
- callback 开始后 guarded PREPARED→RUNNING；
- mutation transaction 内，永久 marker 写入后 guarded RUNNING→SUCCEEDED；
- SUCCEEDED update 必须同时 join/验证 marker、run、operation、domain 和 tuple；
- `PollPurgeAuditIdentityWriter.markSucceeded` 必须真正更新状态，而不是只执行 COUNT 验证；
- attempt ID 不写入该永久表；
- known rollback 可在事务外 guarded 标 FAILED；unknown outcome 不得直接标 FAILED。

恢复时，同一 run/operation 的非终态 legacy audit 必须按 marker evidence 收敛，不能无界创建重复行。

### 9.3 commit transaction

同一 transaction 的最终相对顺序：

```text
business delete
→ locked-scope post-check
→ DataRetentionP0AuditService.commitPollMutation
   → INSERT data_retention_purged_slice
   → INSERT data_retention_poll_preserved_result（全部 preserved IDs）
   → operation committed
   → attempt succeeded + trusted usage(1 tx,1 candidate)
→ legacy poll audit guarded SUCCEEDED
→ commit
```

唯一键冲突不是本次幂等成功，必须 rollback 并进入证据分类。

### 9.4 cleanup-only audit

cleanup-only 不写 legacy `poll_audit_purge_runs`，该表只描述 primary slice purge。它使用新的
`poll_preserved_cleanup` operation、attempt/items 和 ledger cleanup receipt：

- origin operation 已 committed/detail-purged 都不影响新 cleanup operation；
- item business key 是 preserved `poll_result_id`，一次 1～20 条且全部属于同一 origin marker；
- ledger `cleanup_operation_id` 是永久 commit proof；同一 ID 只允许一次 `preserved→cleaned`；
- operation/items ownership 在 mutation 前的短事务中建立，business mutation rollback 时不得删除或重建；
- operation committed、attempt succeeded、ledger update 和 business delete 同事务；
- cleanup operation 的 detail 只有在其全部 owned ledger rows 已 `cleaned`、不存在 recovery/exact-retry/
  manual-review ownership 后，才可按 WP8 的 365 天规则清理；ledger 永久保留；
- recovery 只需检查 ledger exact + old row absent 的 POST、ledger preserved + old row present 的 PRE，
  其他为 MIXED/manual review；不扩展 split/group 或复杂自动 repair。

---

## 10. 失败分类、重试与恢复

### 10.1 pre-mutation controlled stop

与 WP6 一致，仅当 callback/mutation 尚未开始：

- lease lost/unavailable；
- static gate disabled；
- 任意 dynamic fail-closed decision；
- budget/deadline exhausted。

收敛为：

```text
attempt: prepared → skipped
operation: pending → rolled_back
metrics.failureDisposition=recovery_not_started
candidate usage=0
```

dynamic 原始 decision code 保留为 stopReason。

### 10.2 known rollback

- lock timeout/deadlock 等明确 transient：同一 operation/attempt scope 最多重试 2 次；
- 正常数据变化（资格变化、summary mismatch、late-write conflict、row-count drift）在明确 rollback 后，
  attempt failed、operation rolled_back，executor 记录 failed slice/partial 并继续稳定 selection cursor；
- 数据冲突允许后续显式 exact retry，但 fresh selector 不另建 operation 绕过 hold；
- cleanup-only 的单个 ID/ledger 资格变化同样记录该 cleanup operation failed，并允许稳定游标越过其
  item hold；不得在当前 run 创建第二个 cleanup operation；
- cleanup known rollback 后，其 item hold 保留且只允许 exact retry；它不建立 origin/domain 级 hold，也不阻断
  其他无 hold origin/IDs。由于每个普通 run 最多一个 cleanup operation，本 run 随后继续 primary；下一普通
  run 的 fresh selector 可选择稳定顺序中的其他无 hold cleanup 候选；
- contract/hash/tuple/identity 不变量冲突立即停止 domain；
- transient 耗尽、数据库/Redis/lease 等公共基础设施故障以稳定错误停止 domain；
- unknown outcome 始终停止 domain。

继续 cursor 只适用于已确认事务 rollback 的业务数据冲突。failed attempt 会使 run rollup 如实反映 partial/failed，
不得为了“继续处理”伪装成 skipped/succeeded，也不得在同一 run 重新选择该 held tuple。

### 10.3 unknown outcome

连接在 commit 边界断开、事务结果无法由驱动确认时：

- 立即停止本 domain；
- 不标普通 failed；
- 不重选 tuple、不重删；
- evidence provider读取 marker、operation、attempt、legacy audit 和 business state；
- marker exact + post-state → recover succeeded；
- marker absent + full pre-state → known rollback/safe retry；
- 其他组合 → manual review。

### 10.4 running recovery

恢复只使用 persisted operation tuple/cutoff 和原 attempt：

| marker | business evidence | 处理 |
|---|---|---|
| exact | POST | 恢复 succeeded/usage=1，补 legacy audit SUCCEEDED |
| absent | PRE | 记录 known rollback，usage=0，不自动盲目 mutation |
| exact | PRE/MIXED | manual review |
| absent | POST/MIXED | manual review |
| malformed/duplicate | 任意 | manual review + alert |

WP7 首版不扩展复杂半事务自动 repair；但必须可靠区分 PRE/POST/MIXED/MISSING。

---

## 11. Late-write 安全

marker 后 409 是业务契约，不是最佳努力。

### 11.1 writer 统一协议

任何创建或修改该 tuple 下 poll detail 的事务必须：

```text
normalize tuple
→ acquire slice fence
→ query permanent marker
→ marker exists: throw HTTP/domain 409
→ marker absent: business write
```

检查和写入必须在同一事务，不能先无锁检查再写。

唯一例外是第 5.3/8.1 节内部 cleanup-only repository：它要求 marker 必须存在，并且只允许删除 marker
子账本登记且已 superseded 的旧 IDs。它没有 public controller/DTO，不得调用普通业务 writer，也不得写入
任何新 poll batch/result/attempt/child/summary 行；它只能删除 ledger 精确登记的 old result/children 并更新 ledger。

### 11.2 必选回归

- purge 持有 fence 时 planning/writer 等待；purge commit 后 writer 得到 409；
- writer 先持有 fence 时 purge 等待；writer commit 后 purge 锁内重验并安全 rollback/重新计算；
- purge 后 planning 不能重建 batch/shard/item；
- purge 后 attempt/provider/search/citation/entity-judge/result projection 全部拒绝；
- ID-only writer 的 identity 列/parent link 不可变，获取 fence 后目标被删除时安全拒绝；
- shard-only status writer 统一走 slice guard；
- preserved ID 被跨日期 newer projection 取代后，cleanup-only 删除旧 result/完整 children，marker 和 summary 不变；
- summary recompute 在 marker 后只返回 skipped，不用 preserved latest subset 覆盖历史 summary；
- 不存在未经过 guard 的 mapper/service 写路径。

---

## 12. Controller、scheduler、canary 与 retry

- controller `/poll-results` 继续只调用 coordinator；不得 fallback legacy `purge()`；
- `PollRetentionDryRunService.purge/runScheduled(...,false)` 降为不可注入的内部迁移辅助或删除 mutation 分支；
- scheduler 一次 poll invocation 由 executor 内部完成最多 requestLimit 个 slice，不持有外部 cursor；
- primary slice requestLimit 最大 20；manual/scheduler 先对最前一个无 hold origin 执行至多一个、1～20 IDs
  的 cleanup operation，再在剩余 WP5 transaction/candidate/deadline budget 内执行 primary；响应/metrics
  必须分开报告两类 candidate 和 transaction usage；
- canary 最多 1 个 primary slice且不运行 cleanup-only；需要 canary permission/approval 和全部门禁；
- retry 必须由 server-side related operation 读取 exact tuple，不接受 caller 上传 project/date/tier；
- reconcile 保持 audit-only，不执行业务 mutation；
- website 与 poll 继续共用同一 Redis execute lease，避免两个 P0 domain 并发不可逆 mutation。

---

## 13. Typed dry-run

WP7 同步改造 poll dry-run：

- 一请求一条 WP3 `dry_run` run；
- 每页一个 operationless PAGE attempt；
- run scope 保存 requested/effective hot window、filters、cutoff 和 cursor schema；
- 每个 slice 输出 typed gate facts，不把整个 VO 塞入 audit JSON；
- response 可以返回详细计数，audit 只保存有界汇总；
- 页失败必须终结当前 PAGE attempt 和 run；
- simulation-only `<14 days` 允许，但 fingerprint 仍使用 execute floor 14；
- scheduler dry-run 的分页必须收进 service，外层只调用一次；
- dry-run 不创建 operation、marker、legacy purge audit，也不拿 execute lease。

---

## 14. 错误码与 metrics

首版稳定错误至少包含：

```text
POLL_SELECTION_INVALID
POLL_SCOPE_INVALID
POLL_SLICE_ALREADY_PURGED
POLL_BATCH_NOT_SEALED
POLL_ATTEMPT_NOT_TERMINAL
POLL_SUMMARY_RECONCILIATION_FAILED
POLL_ENTITY_JUDGE_RECONCILIATION_FAILED
POLL_LATEST_PROJECTION_CHANGED
POLL_SLICE_LATE_WRITE_REJECTED
POLL_POST_CHECK_FAILED
POLL_PRESERVED_LEDGER_CONFLICT
POLL_PRESERVED_NOT_SUPERSEDED
POLL_PRESERVED_CLEANUP_OUTCOME_UNKNOWN
POLL_TRANSIENT_RETRY_EXHAUSTED
POLL_MUTATION_OUTCOME_UNKNOWN
POLL_RECOVERY_EVIDENCE_CONFLICT
```

metrics 只允许有界计数：各删除表行数、preservedLatestRows、summary row counts、重试次数和
postCheckSchemaVersion。禁止在 JSON metrics 中保存 result IDs 数组、关键词、prompt、provider response、
citation text、token/cookie；preserved IDs 只能进入受约束的永久子账本行。

---

## 15. Required 测试矩阵

### 15.1 unit/contract/architecture

1. command 归一化、limit 1～20、hot window<14 拒绝、cursor 拒绝、filter scope 持久化；
2. requested/effective date 的 NULL、cutoff clamp、空范围 400 和 recovery 固定值；
3. primary poll 与 `poll_preserved_cleanup` canonical scope 固定向量；
4. selection order、cutoff、durable item hold 和 exact retry；
5. latest identity 的 NULL-safe keyword/channel canonical/tie-break；
6. preserved ledger、superseded selector、每 origin 1～20 IDs、每 run 一个 cleanup operation 和 cleanup-only allowlist；
7. delete allowlist 与 post-check SQL；
8. ID-only identity/parent link immutable architecture contract；
9. controlled stop 和所有 dynamic fail-closed decision；
10. transient bounded retry、data rollback continue cursor、contract/infrastructure/unknown stop；
11. primary 与 cleanup-only PRE/POST/MIXED/MISSING recovery matrix；
12. controller/scheduler 无 legacy fallback；
13. writer inventory architecture test，planning/shard-only 必须 before-write guard；
14. typed dry-run 一请求一 run、多 PAGE attempt、失败收敛；
15. 新 migration 的 operation type/ledger/FK/CHECK/repair/no-op signature；
16. 所有 execute/enable-write 默认 false。
17. 持续 primary backlog 时 cleanup 优先占用第一个 transaction，primary 只使用剩余 budget；
18. fresh cleanup 排除 pending/running/rolled_back/unknown/manual_review item hold，不产生重叠分组；
19. pending/running recovery、rolled_back exact retry、unknown/manual_review 阻断，以及其他无 hold origin 后续可选。

### 15.2 real MySQL 8 business safety

至少验证：

- 真实业务 schema、真实 summary、真实 latest/purgeable 混合 slice；
- children 删除顺序满足 FK；
- preserved latest 与完整读路径 children/URL 不变；
- preserved latest 的完整读路径 children 保留并原子登记 ledger；
- 跨日期 newer projection 出现后 cleanup-only 删除旧 preserved result/children，原 marker/summary 不变；
- cleanup-only known rollback 时 business delete 与 ledger receipt 全回滚，operation/attempt 收敛为
  `rolled_back/failed`，operation/items ownership 保留；fresh selector 不会把同一 ID 放入新分组；
- daily summaries 不变；
- marker、business、legacy audit、operation、attempt 原子 commit/rollback；
- marker duplicate 和 row-count drift 整体 rollback；
- planning/persistence/entity judge 与 purge 的两种锁竞争顺序；
- ID-only writer 无锁解析 tuple、等待 fence、父记录被删除后的安全拒绝；
- marker 后所有 writer 409 且不能重建 batch/shard/result；
- JVM/MySQL zone 组合候选一致；
- 真实 500-ID chunk 仍为一个 transaction。

### 15.3 real MySQL 8 + Redis coordinator

新增 `retention-poll-it` required profile，组合 WP3/WP4/WP5 与 WP7 tests。至少一项必须使用：

```text
real DataRetentionExecuteCoordinator
real PollRetentionDomainExecutor
real PollRetentionBusinessRepository
real DataRetentionP0AuditService
real MySQL business tables
real Redis dynamic gate + execute lease
```

primary 成功场景断言一个 run、N 个 `poll_slice` operations/attempts、0 batch items、N 个 markers、
N 个 legacy audit SUCCEEDED、budget slice candidate=N、run rollup 正确、lease 释放。另一个 cleanup-only
场景断言新 `poll_preserved_cleanup` operation/items、ledger receipt、candidate/affected ID 计数和原 marker 不变。
预算场景必须制造不少于 20 个 primary slices 和至少一个 superseded ledger origin，断言 cleanup 先使用一个
transaction、primary 最多使用剩余 19 个；再覆盖 cleanup known rollback 后 exact retry ownership 保留、其他
无 hold origin 在后续普通 run 仍可被 fresh selector 选中。
required 模式基础设施不可用时必须失败，0 skipped。

不重复扩张 WP5 已证明的通用事务故障矩阵；WP7 只补 poll 特有 marker/business/late-write/identity 风险。

---

## 16. 实施顺序与评审门禁

### 16.1 建议实施顺序

1. command scope/validation 与 typed dry-run；
2. identity immutability 与 late-writer inventory，先修 planning/shard before-write guard；
3. preserved-result ledger、`poll_preserved_cleanup` operation contract 和前向 migration；
4. shared poll eligibility/latest/superseded SQL；
5. primary poll business repository selection/lock/revalidate/mutate/post-check；
6. cleanup-only repository/operation/ledger receipt；
7. poll domain executor 与 coordinator registry；
8. legacy poll audit identity/status 原子接线；
9. known rollback/transient/unknown/recovery；
10. controller/scheduler legacy mutation 封闭；
11. unit/architecture/MySQL/Redis required tests；
12. 聚焦代码评审。

### 16.2 前两轮评审已冻结的决定

1. preserved latest 连同完整读路径必需 children 保留，首版不做字段级 slim；
2. preserved IDs 进入永久 marker 子账本；跨日期 superseded 后只由内部 cleanup-only operation 删除，
   不重开 slice、不接受业务写入、不重算 summary；
3. known data rollback 记录 failed slice 后继续稳定 cursor；只有 contract/invariant、unknown outcome 或公共
   基础设施故障停止 domain；
4. legacy `poll_audit_purge_runs.SUCCEEDED` 与 marker、mutation、operation/attempt 同事务提交；
5. shard-only writer 统一接入 slice guard，不采用免 guard 证明方案；
6. ID-only writer 的 tuple identity 和 parent link 创建后不可变；获取 fence 后重新锁定父记录做存在性/状态校验；
7. execute hot window `<14` 直接拒绝，不静默提升；
8. primary slice `requestLimit` 首版固定最大 20；cleanup-only 每个普通 run 固定最多 20 IDs且计入 WP5 budget；
9. requested 日期原样持久化；effective start NULL 表示无下界，effective end clamp cutoff，恢复只读 effective 值；
10. 普通 manual/scheduler run 先执行至多一个 cleanup operation并占用第一个 transaction；未解决 cleanup
    item 是 durable ownership，pending/running 仅 recovery、rolled_back 仅 exact retry、unknown/manual_review 阻断，
    fresh selector 仍可在后续 run 选择其他无 hold origin/IDs。

只有评审方明确回复“WP7 SPEC 评审通过，允许开发”后，才能修改 WP7 Java/SQL/migration。

---

## 17. 验收清单

- [ ] WP7 SPEC 聚焦评审通过并明确授权开发；
- [ ] command scope 保存 project/tier/range/cutoff/limit，execute cursor 被拒绝；
- [ ] requested/effective 日期 NULL/clamp/空范围/recovery 语义通过；
- [ ] 恰好一个 poll executor 注册，无 legacy mutation fallback；
- [ ] typed dry-run 一请求一 run；
- [ ] selector、锁内重验和 post-check 共享资格/identity 规则；
- [ ] planning 和全部 late writer 在任何业务写入前获取 slice guard；
- [ ] result tuple、attempt/child parent link 创建后不可变，ID-only writer fence 后安全重载父记录；
- [ ] 单切片业务 mutation、marker、legacy audit、operation/attempt 同事务；
- [ ] latest projection 与完整读路径必需 children 保留并写永久 marker 子账本；
- [ ] superseded preserved IDs 通过 cleanup-only operation 删除，原 slice/marker/summary 不变；
- [ ] cleanup 在持续 primary backlog 下优先取得一个 transaction，primary 只使用剩余 budget；
- [ ] cleanup item durable ownership、recovery/exact retry/manual-review 路由及无 hold origin 继续入选通过；
- [ ] summary 不被 preserved subset 覆盖；
- [ ] controlled stop、known rollback、unknown 和 recovery matrix 通过；
- [ ] real MySQL writer/purge 并发测试通过；
- [ ] real coordinator + real poll repository MySQL/Redis required profile 0 skipped；
- [ ] git diff/围栏/尾随空白/默认 false 检查通过；
- [ ] V133、WP8、生产克隆和 canary 阻断继续保留；
- [ ] 未执行生产迁移、生产清理或开关 enable。
