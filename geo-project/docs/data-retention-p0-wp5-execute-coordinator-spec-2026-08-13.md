# 数据定期清理 P0 WP5 统一执行协调器 SPEC（第三次聚焦评审修订版）

> 文档日期：2026-08-13
> 当前状态：SPEC 评审通过，代码评审通过并已实现；Docker required profile 与生产准入验收待补
> 上游基线：WP2 第五次评审通过版、WP3 第三次评审通过版、WP4 功能代码评审通过版、`V354__data_retention_execute_governance.sql` 当前实现
> 授权边界：WP5 Java/SQL/测试开发已完成；仍不得打开任何静态或动态开关，不执行生产迁移、生产清理或 canary，也不解除 V133/WP2、Docker required profile 与其他生产准入阻断

---

## 0. 结论摘要

第一次评审提出的七项架构决策、第一次聚焦复审的三项普通崩溃恢复能力，以及 `TrustedMutationReceipt` 单一预算事实源均已接受。本修订版只关闭 deferred RETRY running run 的后台恢复缺口：

1. scheduler 创建新 deferred operation 前，必须先发现带 server-side provenance 的 running `RETRY` run；
2. 恢复使用原 trigger、related operation、scope、预算及 scheduler+retry 双权限；普通人工 retry 不进入该后台路径；
3. startRun 后、prepareRetry 后及 marker commit 后的三种崩溃均回到同一 run/tree 收敛，不得创建第二个 retry run 或永久漏删。

WP5 新增 `DataRetentionExecuteCoordinator`，把 P0 的所有不可逆清理请求统一收口到以下顺序：

```text
来源、操作者、引用与静态门禁校验
→ 运行预算 preflight
→ 获取全局 execute lease
→ lease 后读取 Redis global/domain 动态门禁
→ 通过 WP3 typed audit 创建一条顶层 domain run
→ 领域执行器分页或选取 operation
→ 每个 mutation 事务前再次执行：
     lease.ensureHeld
     静态门禁重验
     Redis 动态门禁重读
     运行预算重验
→ 由协调器开启有界 MySQL transaction
→ WP6/WP7 在事务内完成业务 mutation、marker 与后置校验
→ 记录 attempt 并聚合完成当前 run
→ 释放 lease
```

WP5 的核心安全目标不是新增清理 SQL，而是保证以下事实：

1. 人工、scheduler、canary 和 retry mutation 不能绕过同一 lease、门禁和预算；reconcile 只能经同一 facade 进入 audit-only 路径；
2. lease busy 或 Redis 动态门禁不可用时，不会创建虚假的 `running` run；
3. 一次顶层 domain 请求只创建一条 run，分页不再各自创建 legacy run；
4. Redis 调用和 lease 检查不发生在持有 MySQL 行锁的 mutation transaction 内；
5. lease 或开关在某个事务执行期间失效时，当前事务可完成，但后续事务必须停止；
6. 后台协调器只能消费 WP4 typed gate，不能写入动态 `true`；
7. WP5 不把当前官网“单篇一个事务”或旧 poll legacy audit 路径包装成合规 P0 执行器。实际领域 mutation 分别在 WP6、WP7 接入。

本 SPEC 采用分层完成口径：

- **WP5 代码完成**：协调器、execute lease、静态门禁、预算、事务包装、typed domain port、scheduler/manual 路由骨架和防绕过架构测试完成；没有 WP6/WP7 executor 时 execute 必须明确 fail closed；
- **所有 execute 入口统一完成**：WP6 与 WP7 executor 接入后，官网与 poll 的人工、scheduler、canary、retry、reconcile 均通过协调器；
- **生产可执行**：仍需 WP8、V133/WP2 阻断关闭、真实 MySQL/Redis、生产克隆、canary 和开关审批，WP5 完成不代表获准启用。

---

## 1. 当前实现事实与风险

### 1.1 当前 scheduler lease 不是全局 execute lease

现有 `DataRetentionSchedulerLock`：

- 只由 `DataRetentionScheduler` 获取；
- 人工 HTTP execute 不获取该锁；
- key 默认为 `geo:retention:scheduler:lock`；
- 具备 owner token、续租、compare-and-delete release 和 `ensureHeld()`；
- 续租失败只改变本 JVM lease validity，下一次 `ensureHeld()` 才中止。

因此当前人工请求和 scheduler 可能并发执行，不能满足“所有不可逆入口共享一把 execute lease”。

### 1.2 顶层 run 被领域分页创建

当前 `WebsitePublishedContentCleanupService.runScheduled(...)` 和
`PollRetentionDryRunService.run(...)` 都在每次调用内部使用 legacy
`DataRetentionRunAuditService.startRun/finishRun`。

scheduler 翻页时反复调用领域 service，会产生多条 run；它也没有 operation、attempt 和完整 typed contract。WP5 后：

- 顶层 run 只能由 coordinator 创建和结束；
- domain executor 必须消费同一个 `RetentionExecutionSession`；
- WP5～WP7 新代码禁止调用 legacy `DataRetentionRunAuditService`。

### 1.3 当前人工入口可直接调用 mutation service

`DataRetentionController` 的 website cleanup 与 poll purge 直接调用领域 service。现有 service 只检查领域静态开关与旧权限，没有：

- 全局静态 execute 门禁；
- 全局 lease；
- WP4 Redis global/domain gate；
- mutation 前重验；
- typed run/operation/attempt；
- 运行预算。

WP5 必须把 controller 和 scheduler 的 P0 execute 路由改为 coordinator facade。在 WP6/WP7 真实 executor 尚未接入时，这些 execute 路由只能返回 `DOMAIN_EXECUTOR_UNAVAILABLE`；旧 public mutation 方法不得继续作为可注入的旁路。

### 1.4 WP3/WP4 已提供的能力

WP3 已提供：

- `DataRetentionP0AuditService.startRun(...)`；
- website/poll origin operation；
- retry/reconcile attempt；
- attempt lifecycle 与 run rollup；
- transaction-bound website commit 和 poll marker writer；
- operation outcome 与 reconciliation 原语。

WP4 已提供：

- `DataRetentionDynamicSwitchGate.requireEnabled(domain)`；
- Redis global + domain 同 slot 原子读取；
- malformed/unavailable/missing 全部 fail closed；
- 后台只允许自动 disable，禁止自动 enable。

WP5 只能组合这些 typed API，不能直接写其底层表或 Redis key。

---

## 2. 目标、范围与非范围

### 2.1 WP5 目标

1. 建立所有 P0 mutation 共用的全局 Redis execute lease；
2. 冻结 coordinator 的 command、session、domain port 和结果模型；
3. 统一来源、操作者、引用、静态和动态门禁校验；
4. 保证 lease 后重读动态门禁，并在每个 mutation transaction 前重验；
5. 保证 coordinator 而非分页 service 创建和结束顶层 run；
6. 提供 coordinator-owned 有界 transaction API，防止领域代码先开事务再访问 Redis；
7. 提供基础 per-run 预算，并为 WP8 容量/积压 blocker 预留组合端口；
8. 定义 manual/scheduler/canary/retry 的 execute 语义，以及 audit-only reconcile 的独立语义；
9. 建立 P0 execute 防绕过的编译期/架构契约；
10. 保持所有 execute 与 enable-write 默认值为 `false`。

### 2.2 WP5 范围

- `DataRetentionExecuteCoordinator`；
- `DataRetentionExecuteLease` 及现有 scheduler lock 的兼容迁移；
- `DataRetentionStaticExecuteGate`；
- `DataRetentionExecutionBudgetGate` 与基础预算实现；
- `RetentionExecutionSession` 和 coordinator-owned mutation transaction；
- website/poll typed domain executor port；
- coordinator facade 的 manual/scheduler/canary/retry execute command 与 audit-only reconcile command 形状；
- P0 scheduler 的 trigger/run 所有权调整；
- P0 controller execute 路由骨架；
- lease busy 的 scheduler skipped run 原语；
- 指标、结构化日志和告警调用；
- 单元、架构契约、Redis/MySQL 协调器集成测试。

### 2.3 WP5 非范围

- 官网候选 SQL、20→10→5、failure group、行锁、commit marker 和后置校验；
- poll eligibility、切片删除顺序、summary、late-write guard 和 marker 接线；
- unknown outcome 的 website/poll 领域取证规则；
- WP8 的容量 SQL、365 天 detail cleanup、425 天 blocker 和生产 dashboard；
- 修改 V354 或已发布 migration；
- 自动设置任意 Redis/static switch 为 true；
- article archive/purge、object storage、URL rewrite、slim retention 等非本期 P0 链路；
- 执行生产迁移、生产 mutation、canary 或开关变更；
- 声称 V133、生产克隆或 WP2 最终容量验收已经关闭。

---

## 3. 冻结不变量

1. website 与 poll 的所有 P0 mutation source（manual/scheduler/canary/retry）共用同一个 Redis lease key。
2. 一个 coordinator invocation 只处理一个 domain；多 domain scheduler trigger 共享 `trigger_id`，分别调用 coordinator、分别创建 run。
3. 首次 lease 获取失败不得创建 `running` run。
4. manual/canary/retry lease busy 返回 409；scheduler 写一条直接 terminal 的 skipped run，不能短暂写 running；audit-only reconcile 不获取 lease。
5. 所有 mutation 必须同时满足全局静态、领域静态、Redis global 和 Redis domain 四个门禁。
6. `geo.retention.scheduler.enabled` 只控制 cron bean 是否运行，不是 mutation 授权。
7. `geo.retention.scheduler.execute-enabled` 在 P0 中作为全局静态 execute 门禁；名称为历史兼容，不再只表示 scheduler source。
8. scheduler source 还必须要求 cron enabled；其他 source 不依赖 cron enabled。
9. 动态 gate 必须在 lease 获取后读取一次，并在每个 mutation transaction 前重新读取。
10. static gate 也必须在每个 mutation transaction 前重验，支持运行中通过配置刷新/重启后的 fail closed。
11. 任何 Redis unavailable/malformed/missing/false 都禁止开始下一 mutation transaction。
12. coordinator 不调用 WP4 enable API；预算和安全自动化只能调用 typed disable port。
13. lease、gate、预算检查均发生在 mutation MySQL transaction 外。
14. mutation transaction 只能由 session 提供的 API 开启；领域 executor 不得自行注入通用 `TransactionTemplate` 绕过 checkpoint。
15. checkpoint 通过后到 transaction 开始之间仍存在有限竞态，P0 不引入 fencing token；业务行锁、slice lock 和永久 marker 负责最终幂等。
16. lease 在 transaction 内丢失不强制回滚当前事务；当前事务完成后 session 永久停止，下一事务前失败。
17. coordinator startRun 后的所有 attempt 必须属于同一 run context；分页不得创建新 run。
18. retry 与 audit-only reconcile 每次顶层请求创建新 run，并复用原 operation；不得重新打开 origin run。retry 是 mutation source，audit-only reconcile 不是 mutation source。
19. dry-run 不执行 mutation，不要求动态 switch 为 true，也不占用全局 execute lease；WP5 不改造现有 dry-run，WP6/WP7 接入领域 port 时再把对应 dry-run 迁移到 WP3 typed run。
20. canary 是真实 mutation，使用同一 lease/gate/transaction；它不能靠单独代码路径绕过治理。
21. 缺少 WP6/WP7 domain executor 时，execute 明确返回 `DOMAIN_EXECUTOR_UNAVAILABLE`，不得回退调用 legacy mutation service。
22. run finalization 失败不能把已提交 mutation 响应为完全成功；必须返回审计不确定错误并告警，永久 marker 仍作为 mutation 事实。
23. coordinator 或 reconciler 绝不能自动 `false → true`。
24. 所有 execute、enable-write 默认值保持 false；测试不能修改仓库默认值。
25. HTTP 请求不得上传 compatibility contract、effective scope 或 budget；跨 domain/tenant/project 的 scope 必须由 server-side provider 与当前授权上下文构造并校验。

---

## 4. 术语与职责

### 4.1 trigger、run、operation、attempt

```text
trigger
  一次外部顶层意图；scheduler 同周期跨 domain 共享 trigger_id

run
  trigger 在一个 domain 的一次顶层执行；由 coordinator 创建

operation
  一个 website batch 或 poll slice 的不可逆 mutation identity；由 domain executor 通过 WP3 创建

attempt
  operation 的 normal/split/canary/retry/reconcile 尝试；或 run 的 page/lease_busy 过程
```

### 4.2 coordinator 与 domain executor

coordinator 负责：

- 信任边界、lease、门禁、预算；
- run 生命周期；
- mutation transaction 外围；
- 通用停止、异常映射和指标。

domain executor 负责：

- 候选、operation scope；
- attempt；
- 业务行锁/切片锁；
- mutation、marker、后置校验；
- 将实际计数返回 coordinator。

领域代码不得把 eligibility 或业务后置校验上移到 coordinator。

---

## 5. Java 模块边界

建议新增包：

```text
com.huanjing.geo.module.retention.execute
  DataRetentionExecuteCoordinator
  DataRetentionExecuteLease
  DataRetentionStaticExecuteGate
  DataRetentionExecutionBudgetGate
  DataRetentionExecutionAuthorizationPolicy
  DataRetentionExecutionMetrics

com.huanjing.geo.module.retention.execute.model
  RetentionExecuteCommand
  RetentionExecutionSession
  RetentionExecutionResult
  PendingMutation
  RetentionMutationActual
  CommittedMutation
  MutationTransactionOutcome
  RetentionExecutionStop

com.huanjing.geo.module.retention.audit.model
  TrustedMutationReceipt

com.huanjing.geo.module.retention.execute.port
  DataRetentionDomainExecutor
  WebsiteRetentionExecutionPort
  PollRetentionExecutionPort
```

依赖方向固定为：

```text
controller / scheduler / recovery facade
  → DataRetentionExecuteCoordinator
      → execute lease
      → static gate
      → WP4 DataRetentionDynamicSwitchGate
      → budget gate
      → WP3 DataRetentionP0AuditService
      → typed domain executor port
          → WP6 / WP7 implementation
```

禁止：

- controller/scheduler 直接依赖 WP6/WP7 implementation；
- coordinator 直接使用 `JdbcTemplate` 写审计表；
- coordinator 为了恢复而直接使用 `JdbcTemplate` 读 operation/attempt/item/marker tree；该持久化视图由 WP3 typed read model 提供；
- coordinator 直接使用 `StringRedisTemplate` 读取 dynamic switch；
- domain executor 直接使用 `DataRetentionRunAuditService`；
- domain executor 写 dynamic switch true；
- WP5 新代码调用当前 legacy `cleanup/purge/runScheduled` mutation 方法。

---

## 6. 强类型 command

### 6.1 通用 command

```java
public record RetentionExecuteCommand(
    String triggerId,
    RetentionDomain domain,
    ExecutionSource source,
    long requestedBy,
    String reason,
    String approvalRef,
    String ticketRef,
    RetentionCompatibilityContract compatibilityContract,
    Map<String, Object> scope,
    LocalDate retentionWindowStart,
    LocalDate retentionWindowEnd,
    ExecutionBudget budget,
    String relatedOperationId
) {}
```

该 record 是 coordinator 内部 command，只能由受信任 server-side factory 构造。HTTP DTO 不得直接反序列化为该类，也不得接收 `compatibilityContract`、`scope` 或 `budget`。

`triggerId` 是 canonical lowercase UUID，也是数据库实际持久化的顶层幂等键：

- 普通单 domain 请求由 API 调用方提供或由受信任 facade 生成；重试响应时必须复用；
- scheduler 一次 cron tick 跨 domain 共享同一 triggerId，数据库以 `(trigger_id, domain)` 唯一；
- retry 的 `relatedOperationId` 必填；origin source 必须为空。

`ExecutionSource` 固定为：

```text
MANUAL
SCHEDULER
CANARY
RETRY
```

`RECONCILE` 不属于 execute command source；它使用第 10.1、12.5 节独立的
`RetentionReconcileCommand`，但仍必须通过同一个 coordinator facade，不能由 controller
直接调用 WP3 或领域取证实现。

禁止用自由字符串新增来源。

### 6.2 source 形状

| source | reason | approval/ticket | operator | related operation |
|---|---|---|---|---|
| manual | 必填 | 至少一项 | 当前有效内部用户 | 空 |
| scheduler | 固定非空 | 可空 | 配置 user ID，必须有效 | 空 |
| canary | 必填 | 至少一项 | 当前有效内部用户 | 空 |
| retry | 必填 | 至少一项 | 当前有效内部用户 | 必填 |

WP5 只校验引用形状与权限，不调用外部 CAB/工单系统确认真实性。外部系统集成如需新增，必须另行评审。

第 14.5.5 节的 scheduler deferred retry 仍使用 `source=RETRY`，但其引用由 server-side factory 固定生成：`ticketRef=retention-recovery:<operationId>`、`reason=recovery_not_started`。该值只表示内部崩溃恢复关联，不冒充外部审批；调用方不能上传或覆盖。执行者必须同时具有 `data.retention.execute.scheduler` 与 `data.retention.execute.retry`，并继续经过完整 lease/static/dynamic gate/budget 协议。

factory 还必须把以下 canonical provenance 写入 run `scope_json` 并参与 WP3 exact-command 比较：

```json
{
  "executionInvocation": {
    "schemaVersion": 1,
    "kind": "scheduler_deferred_retry",
    "relatedOperationId": "00000000-0000-0000-0000-000000000000",
    "originRunId": 123
  }
}
```

key/顺序/类型由 server-side encoder 固定；`relatedOperationId` 必须等于 command 字段、ticket 后缀及 retry relation operation，`originRunId` 必须由 operation 永久事实取得。普通人工 retry 不允许携带 `kind=scheduler_deferred_retry`，内部 factory 也不能生成其他自由 kind。后台 discovery 必须同时验证 source、reason、ticket 和该 provenance，不能只按 `trigger_source=retry` 扩大到人工请求。

### 6.3 compatibility contract

command 必须携带 WP3 `RetentionCompatibilityContract` 和 effective config scope。contract 只能由 domain-specific provider 根据当前生效配置生成，controller 不能接受调用方上传 fingerprint、policy version 或 application version。

retry 与 audit-only reconcile 必须由 WP3 从 origin run 的 effective config 重建兼容合同；调用方不能用当前任意配置覆盖原 operation 合同。不支持旧 schema/policy 时 fail closed/manual review。

### 6.4 audit-only reconciliation command

```java
public record RetentionReconcileCommand(
    String triggerId,
    RetentionDomain domain,
    long requestedBy,
    String reason,
    String approvalRef,
    String ticketRef,
    String operationId,
    String reconciliationId
) {}
```

reason、operationId、reconciliationId 必填，approvalRef/ticketRef 至少一项。HTTP 调用方不得上传 marker/business state、decision、evidence、fingerprint 或 policy；这些字段由 WP6/WP7 的领域取证 port 从数据库永久事实生成，再交给 WP3 typed reconciliation writer。reconcile command 不含 mutation budget，不能取得 `RetentionExecutionSession`。

---

## 7. 静态 execute gate

新增 `DataRetentionStaticExecuteGate`：

```java
StaticGateDecision check(RetentionDomain domain, ExecutionSource source);
StaticGateDecision requireEnabled(RetentionDomain domain, ExecutionSource source);
```

### 7.1 全局静态门禁

沿用现有配置：

```text
geo.retention.scheduler.execute-enabled
```

在 WP5 P0 语义中，它是所有 source 的全局 static execute gate。这样不新增一个容易与历史配置不一致的第二全局布尔值。

### 7.2 领域静态门禁

```text
website_published_content
  → geo.retention.website-published-cleanup.execute-enabled

poll_results
  → geo.retention.poll-results.execute-enabled
```

### 7.3 判定

```text
global=false          → STATIC_GLOBAL_DISABLED
domain=false          → STATIC_DOMAIN_DISABLED
scheduler source 且 scheduler.enabled=false
                      → SCHEDULER_DISABLED
domain 不支持         → UNSUPPORTED_DOMAIN
全部满足              → ENABLED
```

配置读取异常、空 domain 或无法映射时 fail closed。静态 gate 不缓存正向结果。

---

## 8. 全局 execute lease

### 8.1 物理协议

WP5 将 `DataRetentionSchedulerLock` 泛化为 `DataRetentionExecuteLease`，但为避免部署期间出现两把锁，第一版继续使用现有物理配置：

```text
geo.retention.scheduler.lock-key
默认 geo:retention:scheduler:lock
```

该配置的 Java/YAML 注释必须改为“global retention execute lease”。本轮不修改默认 key 字节，避免旧实例与新实例滚动部署时各持不同 key。

value 是随机 128-bit owner UUID。获取继续使用：

```text
SET key owner NX PX ttl
```

续租和释放继续使用 compare-owner Lua。禁止无条件 `DEL` 或 `EXPIRE`。

### 8.2 状态模型

```java
interface Lease extends AutoCloseable {
    LeaseIdentity identity();
    void ensureHeld();
    boolean isKnownHeld();
    @Override void close();
}
```

获取结果必须区分：

```text
ACQUIRED
BUSY
UNAVAILABLE
```

Redis 异常不能伪装为 BUSY；两者都 fail closed，但告警和 API code 不同。

### 8.3 TTL 与 transaction timeout

继续使用默认 lease TTL 7200 秒。新增：

```text
geo.retention.execute.mutation-timeout-seconds = 120
geo.retention.execute.max-run-seconds = 3600
```

执行 preflight 要求：

```text
lease TTL >= max-run-seconds + 2 * mutation-timeout-seconds
mutation-timeout-seconds >= 1
max-run-seconds >= mutation-timeout-seconds
```

配置不合法时应用可以启动和 dry-run，但所有 execute source 均返回 `EXECUTE_CONFIGURATION_INVALID`。不能自动放宽 timeout 或 TTL。

### 8.4 busy 语义

- manual/canary/retry：HTTP 409 `EXECUTE_LEASE_BUSY`，不创建 run/attempt；
- scheduler：通过 WP3 新增的受约束 `recordSkippedRun(...)` 写 terminal skipped run，reason=`lease_busy`，全部 rollup 为 0；
- scheduler skipped run 写失败：告警并结束本 domain，本次仍不得执行 mutation；
- retry lease busy 不增加 operation attempt，不推进业务 retry count/backoff；
- canary lease busy 不计入有效 canary。

`recordSkippedRun(RetentionRunStartCommand exactCommand, String stableReason)` 冻结为 WP3 的狭口原语：

- 只接受 `trigger_source=scheduler`、`mode=execute`、`stableReason=lease_busy`；
- 一个短事务直接写入 terminal `skipped` run，`started_at=finished_at=attempt_rollup_completed_at`，所有 business/attempt count 为 0；不得先写 running 再 finish；
- 仍持久化完整 operator、request semantics、compatibility contract、scope 和创建者 application version；
- `(trigger_id,domain)` 冲突时使用与 `startRun` 相同的 exact-command 比较：完整同款 skipped 返回 terminal replay，其他 terminal/running 形状都不改写并返回稳定 conflict/recovery code；
- 该原语不会创建 operation、relation 或 attempt。

### 8.5 lease loss

renewal 失败、owner 不匹配或 `ensureHeld()` 异常后 session 进入永久 stopped。即使 Redis 随后恢复，也不能在同 session 重新获取并继续；必须结束 run，由新顶层请求恢复。

---

## 9. 运行预算

### 9.1 WP5 基础预算

```java
public record ExecutionBudget(
    int maxMutationTransactions,
    long maxCandidateItems,
    Duration maxRunDuration
) {}
```

有效范围：

```text
1 <= maxMutationTransactions <= 1000
1 <= maxCandidateItems <= 100000
1 second <= maxRunDuration <= configured max-run-seconds
```

scheduler 默认从以下配置生成：

```text
maxMutationTransactions = max-batches-per-run
maxCandidateItems = limit-per-domain * max-batches-per-run
maxRunDuration = execute.max-run-seconds
```

`limit-per-domain * max-batches-per-run` 必须使用 checked `long` 乘法并再受 `maxCandidateItems <= 100000` 上限约束；溢出或超限都是 `EXECUTE_CONFIGURATION_INVALID`，不做截断或环绕回。

manual/canary/retry 使用 server-side domain policy 限制；不得由 HTTP 请求任意提高。

### 9.2 预算持久化契约

首次 `startRun` 前，coordinator 必须把经 server-side policy 解析后的原始预算写入 canonical run `scope_json`：

```json
{
  "effectiveConfig": {},
  "executionBudget": {
    "schemaVersion": 1,
    "maxMutationTransactions": 20,
    "maxCandidateItems": 2000,
    "maxRunDurationSeconds": 3600
  }
}
```

规则冻结为：

- key 名、类型和顺序由 server-side canonical encoder 固定，整数使用十进制 JSON number，不带小数、单位后缀或自由 duration 字符串；
- `executionBudget` 与 `effectiveConfig` 一起参与 WP3 exact-command scope 比较；同 trigger 重放时当前配置已变不得覆盖原值；
- terminal replay 只返回原预算事实；running replay 一律使用持久化原预算，不按当前政策重新发放；
- 新的 retry 是新 run，可使用当前 server-side retry budget，但不改写 origin run 的预算。

每个进入 coordinator mutation transaction 的 attempt 必须在终态 metrics 中持久化以下受保留子对象：

```json
{
  "executionBudgetUsage": {
    "schemaVersion": 1,
    "committedTransactionCount": 1,
    "chargedCandidateItems": 20
  }
}
```

- 只有 callback 事务已知 commit 时 `committedTransactionCount=1`；已知 rollback 为 0；
- `chargedCandidateItems` 是 marker writer 从持久化 operation/attempt 派生的可信计数，rollback 为 0；website 等于 operation item count，poll 等于本次单切片候选数 1。WP6/WP7 如需更细计数必须在各自 SPEC 明确修订，不允许 HTTP/domain/callback 任意上传低值；
- WP3 `WebsiteCommitCommand/PollCommitCommand` 不接收调用方构造的 `ExecutionBudgetUsage`。marker writer 在同一事务内从已锁定的 operation/attempt/marker 合同派生 usage，持久化 commit marker、operation finalization、attempt terminal 与 usage metrics，并返回 opaque `TrustedMutationReceipt`；
- receipt 中 `committedTransactionCount` 固定为 1，`chargedCandidateItems` 必须与同事务持久化的 attempt usage 完全一致。coordinator 在 commit 前重验 receipt 身份与 callback reported actual；不允许 commit 后单独补写 usage，也不允许 callback 自造 receipt；
- `PAGE/LEASE_BUSY` 等 operationless attempt 不消耗 mutation transaction/candidate budget，两值固定为 0；
- 任一已终态 mutation attempt 缺少、重复或非法 usage 字段时，running recovery 以 `BUDGET_EVIDENCE_INVALID` fail closed，不假定为 0。`prepared/running` attempt 先按第 14.5 节转为终态并写入 0/1 usage，再重建 progress。

### 9.3 gate API

```java
interface DataRetentionExecutionBudgetGate {
    BudgetDecision preflight(RetentionExecuteCommand command);
    BudgetDecision beforeMutation(RetentionExecutionSession session,
                                  MutationEstimate estimate);
    void afterMutation(RetentionExecutionSession session,
                       TrustedMutationReceipt receipt);
}
```

WP5 实现 transaction count、candidate count 和 wall-clock deadline。WP8 后续通过 composite gate 增加审计容量、积压、425 天和清理 job health blocker；不得修改 coordinator 调用顺序。

running recovery 的 `ExecutionProgress` 不从零创建。coordinator 先按第 14.5 节只做 audit recovery，使所有遗留 `prepared/running` attempt 进入有 usage 事实的终态；随后由 WP3 typed read model 在同一个一致性 snapshot 中对全部 attempt metrics 做完整聚合：

```text
usedMutationTransactions = SUM(executionBudgetUsage.committedTransactionCount)
usedCandidateItems       = SUM(executionBudgetUsage.chargedCandidateItems)
deadline                 = persistedRun.startedAt + originalBudget.maxRunDurationSeconds
```

SUM 必须使用 checked `long`；超过原预算、溢出、deadline 已到或 `startedAt`/预算缺失时不得开始下一 mutation。deadline 不从恢复时刻重新计算，也不因应用重启而延长。

### 9.4 超预算语义

超预算是受控停止，不是继续执行：

- mutation 前超预算：不开始 transaction；
- 若已有 run，按第 14.2 节写入幂等 operationless stop checkpoint，使 `warning_count` 与 stop code 都有数据库 attempt 事实可证；不得为凑 business count 制造虚假 candidate/skipped；
- 已提交的 operation 不回滚；
- run 按已完成 attempt 事实聚合，`warning_count` 体现 budget stop；
- scheduler 正常结束本 domain，不自动扩大预算；
- manual 返回带已完成计数的受控 partial result；
- canary 超预算不计入有效 canary。

---

## 10. Coordinator API 与执行顺序

### 10.1 facade

```java
public interface DataRetentionExecuteCoordinator {
    RetentionExecutionResult execute(RetentionExecuteCommand command);
    RetentionExecutionResult recoverScheduler(SchedulerRecoveryCommand command);
    RetentionExecutionResult recoverDeferredRetry(DeferredRetryRecoveryCommand command);
    ReconciliationResult reconcile(RetentionReconcileCommand command);
}
```

```java
public record SchedulerRecoveryCommand(
    long recoverableRunId,
    RetentionDomain domain,
    long currentRecoveryOperatorId
) {}

public record DeferredRetryRecoveryCommand(
    long recoverableRunId,
    RetentionDomain domain,
    long currentRecoveryOperatorId
) {}
```

`recoverScheduler` 不接受调用方上传原 trigger、scope、contract 或 budget；它必须按 `recoverableRunId/domain`通过 WP3 typed API 重读 persisted exact command。`currentRecoveryOperatorId` 只用于当前授权和恢复审计上下文，不参与旧 run exact-command 比较，不改写 `requested_by`。该方法只接受持久化 `trigger_source=scheduler/mode=execute/status=running` 的 run；其他来源或状态 fail closed。

`recoverDeferredRetry` 同样只接受 run ID/domain/current operator，不接受调用方上传 trigger、related operation、scope、contract、reference 或 budget。它只接受第 6.2、14.5.5 节 server-side factory 创建且满足第 12.2 节 provenance 签名的 `trigger_source=retry/mode=execute/status=running` run；普通人工 retry、manual/canary/scheduler run 均 fail closed。恢复时从持久化 exact command 原样读取 trigger、related operation、scope、原始 run budget 与创建者事实；当前 operator 只用于 scheduler+retry 双权限和恢复日志，不改写历史字段。

domain executor 通过 Spring 注入的闭合集合按 domain 注册。重复 domain bean 或无 bean 均在 execute 时 fail closed；应用启动不因 WP6/WP7 尚未接入而失败。

port 必须区分：

```java
RetentionExecutionResult executeFresh(RetentionExecutionSession session);
RetentionExecutionResult resumeRunning(RetentionExecutionSession session,
                                        PersistedRunState persisted);
ReconciliationEvidence investigate(RetentionReconcileCommand command);
```

`resumeRunning` 只能依据 WP3 中已持久化的 operation/attempt/item/marker 状态推进；不能重新扫描并把新候选当成原 run 的首页。`investigate` 没有 mutation session，不能取得 transaction callback。

`execute` 只接受 `MANUAL/SCHEDULER/CANARY/RETRY`。`reconcile` 是统一但只读取证/写审计的入口，使用 `trigger_source=reconcile`，不取得 execute lease、不要求 static/dynamic execute=true，也不能调用 mutation callback。若 reconciliation 得出 `safe_to_retry`，后续必须提交新的 `RETRY` execute command。

### 10.2 pre-run 顺序

coordinator 必须先完成 command canonical validation 以及 source/operator/permission/ref validation，然后通过 WP3 新增的只读
`lookupRun(RetentionRunStartCommand exactCommand)` 查询既有 run。该 API 必须使用与
`startRun` 相同的 canonical request 比较逻辑，不能只按 `triggerId/domain`
返回未校验的 context：

- 不存在：进入下列 fresh 顺序；
- terminal 且请求语义完全一致：直接返回 terminal replay，不获取 lease、不要求当前 gate=true；
- running 且请求语义完全一致：使用持久化原预算重建 progress，取得 lease 后调用
  `domainExecutor.resumeRunning(session, persistedRunState)`。第 14.5 节的只读取证与 conservative audit terminalization 不要求 execute gate=true；任何恢复 mutation 都必须在紧邻 mutation 前再通过 static/dynamic gate、deadline 和剩余预算。除第 14.5 节定义的“空 run”外，禁止调用 fresh execute 或重新选择首页；
- 既有 run 与 command 不一致：409 idempotency conflict；
- lookup 不写数据库，不改变 run。

未授权调用方即使猜中 `triggerId/domain` 也不得获取 terminal replay context。terminal replay 只是豁免当前 static/dynamic gate、budget 和 lease，不豁免 command 形状、operator、权限和引用校验。

fresh request 不可调整的顺序：

```text
1. domain executor 唯一性/可用性校验
2. static gate
3. budget preflight
4. lease.tryAcquire
5. lease.ensureHeld
6. dynamic gate.requireEnabled
7. 再次 static gate
8. WP3 startRun
9. 按 startRun disposition 路由：
      CREATED         → domainExecutor.executeFresh(session)
      RUNNING_REPLAY  → domainExecutor.resumeRunning(session, persistedRunState)
      TERMINAL_REPLAY → 直接返回已有结果
```

第 6 步必须发生在 lease 后，防止等待 lease 期间开关已经关闭。第 7 步关闭 static gate 的同一竞态窗口。第 8 步仍必须处理 pre-run lookup 之后发生的并发创建/完成，不能假定只会返回 `CREATED`。

对第 4～7 步的 busy/gate 拒绝，coordinator 在返回拒绝前必须再做一次 exact-command 只读 lookup：若同一请求已被并发 writer 完成，返回 terminal replay；若仍 running 或不存在，才返回 busy/disabled。这个竞态恢复查询不得为 scheduler 另行写入 skipped run；已有 running replay 时也不得用相同 `(triggerId,domain)` 另创 skipped run，只返回稳定 `RECOVERY_IN_PROGRESS/EXECUTE_LEASE_BUSY`。

lease busy 的 scheduler skipped run 是第 4 步特殊分支，不走第 8 步普通 running run。

### 10.3 run start command 映射

```text
domain                 → compatibilityContract.domain
triggerId              → command.triggerId
triggerSource          → source 的小写数据库值
mode                   → execute
requestedBy            → validated operator
reason/approval/ticket → command 字段
effectiveConfig/scope  → domain provider 生成
retention window       → command 字段
startedAt              → coordinator 注入的 WP3 DataRetentionAuditClock.now；外部请求时间不作为权威
```

同 trigger replay 必须依赖 WP3 startRun 幂等规则。`startedAt` 只在首次创建时持久化，不参与 exact-command replay 比较；lookup/retry 时新生成的 clock 值不能把同一请求变成冲突。application version 同样是创建部署事实，不参与命令兼容比较。

---

## 11. RetentionExecutionSession

### 11.1 session 能力

```java
public interface RetentionExecutionSession {
    RetentionRunContext run();
    RetentionExecuteCommand command();
    ExecutionProgress progress();

    <T> CommittedMutation<T> inMutationTransaction(
        MutationEstimate estimate,
        RetentionMutationWork<T> work
    );

    void checkpointWithoutMutation(String reason);
    boolean isStopped();
}
```

callback 和 commit 后返回类型必须分开：

```java
public interface RetentionMutationWork<T> {
    PendingMutation<T> execute();
}

public record PendingMutation<T>(
    T value,
    RetentionMutationActual reportedActual,
    TrustedMutationReceipt trustedReceipt
) {}

public record CommittedMutation<T>(
    T value,
    RetentionMutationActual reportedActual,
    TrustedMutationReceipt trustedReceipt,
    Instant committedAt
) {}

public sealed interface TrustedMutationReceipt
    permits WebsiteTrustedMutationReceipt, PollTrustedMutationReceipt {
    long runId();
    String operationId();
    long attemptId();
    String markerIdentity();
    ExecutionBudgetUsage usage();
}
```

`PendingMutation` 只表示 callback 在当前事务中产生的待提交业务结果、领域 reported actual 与 marker writer receipt，不含 `COMMITTED/UNKNOWN`。`TrustedMutationReceipt` 是 WP3 audit model 中的 sealed 只读接口；两个 permitted final implementation 与构造器均保持 WP3 package-private，只能由对应 marker writer 实例化。domain/controller 不得实现、继承或实例化，架构测试必须阻止。coordinator 仍须在当前事务把 receipt 交回 WP3 typed validator，按持久化 attempt/marker 事实验证所有字段，不能只因 Java 类型正确就信任。名称中的 trusted 表示事实由同事务的持久化合同派生并经 validator 重验，不表示事务已经 commit。

`inMutationTransaction` 只在 transaction manager 确认 commit 后包装为 `CommittedMutation`；commit 抛异常由 coordinator 映射为 known rollback 或 commit-outcome-unknown，callback 不得提前宣称 `UNKNOWN`。领域也不能只返回任意业务 DTO 再在 transaction 外补报计数，或绕开 marker writer 构造 receipt。

`RetentionMutationWork` 在 Spring transaction 已激活、非 read-only、timeout 已设置后调用。WP6/WP7 可在其中使用 MANDATORY marker writer。实现必须在 transaction 同步回调确认 commit 成功后才执行第 11 步 progress/budget 更新；rollback 或 commit-time 异常不得消耗“已提交 mutation”计数。

### 11.2 mutation transaction 顺序

`inMutationTransaction` 固定执行：

```text
1. session 未 stopped
2. lease.ensureHeld
3. static gate.requireEnabled
4. dynamic gate.requireEnabled
5. budget.beforeMutation
6. 再次 lease.ensureHeld
7. 使用 coordinator 专属 TransactionTemplate 开启 transaction
8. work 执行业务锁、mutation、marker、后置校验；marker writer 返回 trusted receipt，work 连同 reported actual 返回 `PendingMutation`
9. commit 前验证 receipt 绑定当前 run/operation/attempt/marker 且 usage 与已持久化 attempt metrics 一致；reported actual 全部非负，并强制 `reportedActual.operationId == receipt.operationId`、`reportedActual.candidateCount == receipt.usage.chargedCandidateItems`；再以 receipt usage 验证 `chargedCandidateItems <= estimate.candidateCount` 且不会使总预算超限。任一不等或超限使业务 mutation、marker、operation/attempt terminal 与 usage 整个事务回滚
10. transaction commit
11. commit 成功后只使用 `CommittedMutation.trustedReceipt.usage` 执行 budget.afterMutation / progress 更新；reported actual 只用于业务汇总，不参与预算扣减
```

第 6 步缩短 gate/预算检查后的 lease 竞态。第 7 步以后禁止调用 Redis lease、dynamic gate、外部审批或网络服务。`MutationEstimate` 必须是本事务可提交候选数的上界；领域实现不得用低估值绕过预算。

同一 session 的即时累计与崩溃恢复聚合必须读取同一份 marker-derived `ExecutionBudgetUsage`。禁止“本轮按 callback 较小值继续、重启后才按数据库较大值纠正”的双来源模型。

第 9 步产生 `MUTATION_USAGE_MISMATCH` 时，transaction manager 先确认整个 mutation 事务 known rollback；随后 coordinator 才用 WP3 狭口 writer 把仍为 running 的 attempt 记为 `FAILED`、operation 记为 `rolled_back`、usage 记为 0。这个补记事务不得创建 marker或修改业务行；补记失败时返回 `RUN_FINALIZATION_UNCERTAIN` 并由第 14.5 节恢复，不能把 mismatch 当成 commit unknown。

### 11.3 transaction 传播

- 使用 `PROPAGATION_REQUIRES_NEW`；
- isolation 沿用 MySQL 默认，领域锁协议由 WP6/WP7 冻结；
- timeout 使用 `mutation-timeout-seconds`；
- read-only=false；
- domain work 内再次启动独立 transaction 属于绕过，架构测试必须禁止；
- marker writer 使用 `MANDATORY`，与业务 mutation 同 transaction。

### 11.4 callback 返回与 transaction outcome

```java
public record RetentionMutationActual(
    String operationId,
    long candidateCount,
    long affectedCount,
    long skippedCount,
    long warningCount
) {}
```

transaction wrapper 在 callback 返回之外产生的 `MutationTransactionOutcome` 固定为：

```text
COMMITTED
KNOWN_ROLLBACK
NO_MUTATION
UNKNOWN
```

`COMMITTED` 只能在 marker writer 、业务 mutation、attempt terminal/usage metrics 所在事务已确认 commit 后产生；`UNKNOWN` 只由事务异常分类产生。`MANUAL_REVIEW` 是后续取证/审计状态，不是一次 transaction manager outcome。领域 executor 不能以返回枚举代替永久 marker。

---

## 12. 来源协议

### 12.1 manual

- controller 接受 canonical `triggerId` 作为幂等键；重放必须复用并保持相同命令；
- 当前用户必须 active/internal 且具备 `data.retention.execute.manual`；
- reason 必填；approvalRef/ticketRef 至少一项；
- lease busy 409；gate disabled 409；unavailable 503；
- 返回 run ID、完成计数、受控停止原因；
- HTTP 断连不取消已进入 transaction 的 mutation。

### 12.2 scheduler

- 每次 cron tick 先校验当前 scheduler operator，再对 website/poll 按稳定 domain 顺序完成 WP3 typed running discovery；每个 domain 固定先查 `findRecoverableSchedulerRun(domain)`。普通 scheduler run 已处理或不存在后，当前 operator 必须同时具备 scheduler+retry 权限才可查询 `findRecoverableDeferredRetryRuns(domain)`；缺少 retry 权限时该 domain 立即告警并停止，不得跳过 discovery 后进入 rolled_back/fresh。全部 domain 的 eligible running recovery 完成前不生成新 trigger UUID；
- 发现普通 scheduler running run 时，先使用其持久化 trigger/command/budget 调用 `recoverScheduler`；该 run 未 terminal 前，本 domain 不恢复 deferred retry，也不创建新 run；
- 普通 scheduler run 已处理或不存在后，按 `started_at,id` 逐条恢复 server-side deferred RETRY running run；每条调用 `recoverDeferredRetry`，使用原 trigger/related operation/scope/budget。任一仍 running、恢复不确定或证据冲突时本 domain 停止，不进入 rolled_back discovery/fresh origin；
- 只有两类 running run 均不存在或已 terminal 后，才执行第 14.5.5 节 `findDeferredRetryableOperations` discovery/dispatch；没有待处理 deferred operation 时才进入 fresh scheduler origin。fresh origin domain 共享本 tick 新 trigger UUID，并依靠 `(triggerId, domain)` 区分各自 run；每个新 deferred retry 使用自己的新 trigger UUID，不伪造成 scheduler origin，也不要求另一 domain 复用；
- operator 使用 `geo.retention.scheduler.operator-user-id`，必须 active/internal/authorized；
- 旧 run 的 `requested_by` 是首次创建事实，恢复时不改写；当前配置 scheduler operator 仍必须通过 `data.retention.execute.scheduler` 权限校验，并以 `currentRecoveryOperatorId` 记录结构化日志/指标；
- operator=0 时该 domain skipped/告警，不执行；
- 每个 domain 独立调用 coordinator；一个失败不阻止另一个，但共享 lease 意味着同一 scheduler instance 串行执行；
- scheduler 不再在外层持 lease；lease 所有权完全归 coordinator；
- scheduler dry-run suite 与 execute 调度必须明确分支，不能用 `!execute` 把同一路径静默切换为 legacy mutation。

`findRecoverableSchedulerRun(domain)` 是只读 WP3 API，固定只查：

```text
trigger_source = scheduler
mode = execute
status = running
finished_at IS NULL
attempt_rollup_completed_at IS NULL
```

coordinator 在调用该 API 前先通过完整 user → active internal role → active scheduler permission 链路校验 `currentRecoveryOperatorId`，未授权时根本不进入 WP3 lookup。WP3 API 返回完整 persisted command、run context、原始预算与 `PersistedRunState`，不更新行，不使用 `FOR UPDATE`。同一 domain 若发现多于一条 recoverable scheduler run，返回 `RECOVERABLE_RUN_CONFLICT`、告警并停止该 domain；不得自行选最新一条。查询后的多实例竞争仍由全局 lease 串行化。

`findRecoverableDeferredRetryRuns(domain)` 也是只读 WP3 API，固定只返回：

```text
trigger_source = retry
mode = execute
status = running
finished_at IS NULL
attempt_rollup_completed_at IS NULL
request_reason = recovery_not_started
ticket_ref = retention-recovery:<relatedOperationId>
scope_json.executionInvocation.schemaVersion = 1
scope_json.executionInvocation.kind = scheduler_deferred_retry
scope_json.executionInvocation.relatedOperationId = relatedOperationId
scope_json.executionInvocation.originRunId = operation.origin_run_group_id
```

实现必须通过 typed JSON read/validation 校验完整 provenance、run compatibility contract 与 operation relation，不以 SQL 字符串拼接 JSON 判断作为最终授权证据。结果按 `started_at,id` 稳定排序；允许同 domain 存在多个不同 operation 的后台 deferred run，但同一 operation 多于一条 running deferred run、命令/provenance/tree 不一致或混入人工 retry 时，返回 `DEFERRED_RETRY_RUN_CONFLICT` 并停止该 domain。普通人工 retry永远不由 scheduler 自动发现或恢复。

两类 discovery 都不更新行、不使用 `FOR UPDATE`；真正 resume 仍需竞争同一全局 lease，并在取得 lease 后按 run ID 重读 exact command/tree。deferred recovery 必须再次校验当前 operator 的 scheduler+retry 双权限。run 已被其他 worker terminal 时返回 terminal replay；仍 running 时进入第 14.5 节，不创建第二 run。

V354 现有 `idx_retention_run_source_started(trigger_source, started_at)` 可用于候选缩小，但不覆盖 domain/status 的完整查询。WP5 开发时必须用 MySQL 8 `EXPLAIN ANALYZE` 和生产规模运行数评估该发现 SQL；若需新 `(trigger_source,domain,status,started_at)` 索引，必须通过当时最新 Flyway 迁移添加，不修改 V354。

### 12.3 canary

- canary 是 source，不是独立 domain；
- 必须具备 `data.retention.execute.canary` 权限并通过引用校验；不额外要求 manual 权限；
- server-side budget 必须比普通 manual 更小；
- attempt type 使用 `CANARY`；
- lease busy、gate stop、metrics/marker 不完整均不计入有效 canary；
- WP5 只定义协议，WP6/WP7 定义 website 5 篇和 poll 单切片选择。

### 12.4 retry

- 必须携带 operation ID；
- WP3 验证 operation 为 rolled_back/pending 的完整 retry 状态；
- 获取 lease 失败前不得把 operation 从 rolled_back 改回 pending；
- retry relation、`rolled_back → pending` 与 prepared attempt 只能在 lease/gate/run 都成功后创建；
- lease busy 不增加 attempt_no；
- domain executor 必须复用原 scope、原 operation 和已有 website item tree/poll tuple，不能重新选 business key，也不能为同一 `(domain,batch_key_hash)` 创建第二个 origin operation；
- 仅第 14.5.5 节可把有永久 `recovery_not_started` 证据的 operation 作为 scheduler deferred retry 自动接管；普通 mutation failure 形成的 `rolled_back` 仍按人工/领域 `safe_to_retry` 协议处理，不能被此自动路径扩大。

### 12.5 reconcile

reconcile 分两类：

1. **取证/审计-only**：不执行业务 mutation，不需要 execute lease 或 dynamic=true，但需要权限、typed audit 和领域 evidence；
2. **reconcile mutation**：例如结论为 safe_to_retry 后执行清理，必须转入新的 retry execute command，完整经过 lease/gate/budget；不能在 audit-only callback 中直接 mutation。

这样避免为了写一致审计而要求开关为 true，也避免 reconcile 借审计入口绕过执行治理。

---

## 13. 权限与 operator

WP5 新增独立执行权限，不复用 `dispatch.task.release` 或 WP4 switch 管理权限：

```text
data.retention.execute.manual
data.retention.execute.scheduler
data.retention.execute.canary
data.retention.execute.retry
data.retention.execute.reconcile
```

建议 grant：

| role | manual | scheduler | canary | retry | reconcile |
|---|---:|---:|---:|---:|---:|
| super_admin | yes | yes | yes | yes | yes |
| manager | yes | yes | no | yes | yes |
| delivery_manager | no | no | yes | no | no |

迁移必须在开发开始时重新扫描最高 Flyway 号，不能在 SPEC 中预占版本。迁移需要精确 permission definition、active/internal role 验证、非法预授权检测和幂等重放测试。

scheduler operator 必须通过 user → active internal role → active permission 的完整数据库关系校验，不能只依赖 `sys_user.role` 字符串。

scheduler deferred retry 复用同一配置 operator，但必须同时通过 scheduler 与 retry 两项权限；当前建议矩阵中的 `super_admin/manager` 可满足，其他角色不能因“自动恢复”绕过 retry 授权。权限缺失只告警并保留 `rolled_back`，不得创建 retry run/attempt。

---

## 14. Run、attempt 与结束协议

### 14.1 normal completion

domain executor 返回后 coordinator 使用 `DataRetentionP0AuditService.finishRun`。业务 summary 必须由 session 已接受且已持久化的 mutation/page/stop attempt 聚合，不接受 controller/domain 调用方任意覆盖。stop checkpoint 的 `warning=1` 使 partial/controlled stop 可由数据库事实证明；不允许只在响应 DTO 中补一个 warning。

### 14.2 controlled stop

以下属于 controlled stop：

```text
LEASE_LOST
STATIC_GATE_CLOSED
DYNAMIC_GATE_CLOSED
BUDGET_EXHAUSTED
DOMAIN_EXECUTOR_UNAVAILABLE
```

run 未创建前：直接返回/抛出，无 running run。run 已创建后：

- 不开始新 mutation；
- 使用稳定 `attemptRequestId` 幂等写入一条 operationless `PAGE` skipped attempt 作为 stop checkpoint，metrics 固定为 `candidate=0/affected=0/skipped=0/warning=1` 并带 stable stop code；不得把未执行候选伪造为 skipped business count；
- 已提交 operation 保持 committed；
- 若 run 中已有 mutation `prepared` attempt 且本次 stop 发生在 mutation 尚未开始之前，不能只写 operationless checkpoint 后结束 run；必须先按第 14.5.2 节把该 attempt/operation 原子收敛为 `skipped + rolled_back + usage=0`，再写 checkpoint/rollup，使 deferred retry 可发现；
- 完成 run rollup；
- scheduler 记录并继续下一 domain；
- manual/canary 返回清晰 stop code。

stop checkpoint 是 WP3 已有 operationless attempt 的受限用法，不是新的 operation identity。同一 `(runId, stopCode)` 的 `attemptRequestId` 必须由 server-side 稳定派生，因此 stop 响应丢失后重放不会多加 warning。只允许从 `PREPARED` 直接完成为 `SKIPPED`；不得标为 `SUCCEEDED`。

### 14.3 unexpected failure

- transaction 已知回滚：领域 attempt failed，是否继续由领域策略决定；
- transaction commit outcome unknown：attempt unknown，停止当前 domain，告警并进入 WP6/WP7 reconciliation；
- marker 已存在：后续 reconciliation 可确认 committed；
- finishRun 失败：返回 `RUN_FINALIZATION_UNCERTAIN`，不删除或改写 marker；
- lease close 失败：告警，不改变已提交业务事实；TTL 最终释放。

### 14.4 top-level response lost

相同 triggerId/domain/command 重放：

- run running：必须重新取得全局 lease、使用原预算重建 progress，并按已持久化 operation/attempt/item/marker 状态进行 audit recovery；任何恢复 mutation 再通过当前 gate/剩余 budget/原 deadline。不能并发启动第二 execution session；只有第 14.5.1 节完全空 run 允许重选首页；
- run terminal：直接返回 terminal replay；即使当前 lease busy 或开关已关闭也不能把历史成功响应错误改成 busy/disabled；
- 命令不同：409；
- application version 不同但兼容合同相同：允许由当前 writer 读取/恢复。

WP5 不依赖 JVM 内存 mutex 实现幂等，最终依据 WP3 数据库唯一约束和 lease。为支持 pre-lease terminal replay，WP3 必须提供只读 exact-command lookup；不能用调用 `startRun` 产生写入副作用的方式探测历史状态。running replay 所需的 `PersistedRunState` 也必须由 WP3 typed read model 返回，至少包含按 sequence/attempt_no 稳定排序的 relation、operation、attempt、item 身份与 permanent-marker/outcome 状态；coordinator 不直读表也不重建领域 scope。

### 14.5 running run 与非终态 attempt 恢复

coordinator 只在通过当前 authorization、唯一 execute lease 和原预算重建后进入本节。处理前通过 WP3 typed read model 重读最新 tree，不使用 scheduler 发现时的旧 snapshot。

恢复分为两个权限级别：

1. **audit recovery**：只读 marker/outcome/业务证据，并使用 WP3 typed writer 将既有 attempt 转为 `succeeded/failed/unknown/manual_review/skipped`、完成 run rollup 或写 reconciliation；不执行业务 mutation。该路径在 static/dynamic execute=false 或 deadline 已到时仍可执行，以免遗留永久 running；
2. **mutation recovery**：空 run 重选首页、`prepared → running` 后继续原 attempt，或开始任何新 mutation transaction。它必须紧邻动作前通过 static/dynamic gate、原 deadline 和剩余预算；失败时只允许做 audit recovery/controlled stop，不得 mutation。

#### 14.5.1 空 run

仅当以下条件同时成立时，恢复可以在原 run 下重新选择第一页：

```text
run.status = running
operation count = 0
relation count = 0
attempt count = 0
website item count = 0
permanent marker count = 0
```

该路径仍调用 `resumeRunning`，只是返回显式 `EMPTY_RUN_RESELECT_ALLOWED` disposition；不调用 `startRun`。只要已存在任何 operation、attempt、item、relation 或 marker，就禁止重新选择首页。

#### 14.5.2 prepared attempt

- operationless `PAGE/LEASE_BUSY` prepared：按原 `attemptRequestId` 和持久化 command 重放，安全完成为 `SKIPPED`；
- mutation attempt prepared 且 marker 不存在、operation 仍 `pending`：`prepared` 表示 WP3 `markAttemptRunning` 尚未成功，因此 mutation 尚未被允许开始。若 static/dynamic gate、原 deadline 和剩余预算仍允许，使用同一 attempt 的持久化 command/scope 执行 guarded `prepared → running`，然后进入正常 mutation transaction；不创建新 attempt，不重选 business key；
- 若上述 mutation gate/deadline/budget 不允许，使用 recovery writer 在同一 operation-locked 事务原子完成 `prepared → skipped`、usage=0 与 `pending → rolled_back`，stable reason 固定为 `recovery_not_started`。该规则同时适用于 `ORIGIN` 和 `RETRY` relation 下的合法 mutation attempt；不设置 outcome finalization/retention anchor，不访问或修改业务行，也不删除 operation/item/scope identity。其他 relation/attempt 组合视为合同矛盾；
- prepared 但 marker 已存在、operation 已 committed 或二者不一致：这与 WP3 marker writer 的原子契约矛盾，由 recovery writer 将 attempt/operation 保守地置为 `MANUAL_REVIEW/manual_review`，返回 `RECOVERY_EVIDENCE_CONFLICT`并进入 audit-only reconcile；不得根据 marker 单方面补成 success。

operationless `prepared → skipped`、mutation `prepared → running/skipped` 以及矛盾证据 `prepared → manual_review` 必须使用 WP3 持有 operation/run 锁的 guarded typed API，幂等比较 status、metrics、stable reason 和 event time；不直接 SQL 更新。`prepared → manual_review`、与 operation 同事务收敛的 `prepared → skipped` 都是只供恢复使用的新狭口转换，不开放给普通 attempt writer。

#### 14.5.3 running attempt

| marker | operation outcome | 领域业务证据 | 恢复动作 |
|---|---|---|---|
| 存在且合同完整 | committed | 与 marker 一致 | 使用 WP3 recovery writer 幂等确认 attempt `SUCCEEDED`，budget usage 从 operation/marker 受信事实派生；不再 mutation |
| 不存在 | pending | 领域证明 mutation 未发生/已回滚 | attempt `FAILED`、operation `rolled_back`、usage 为 0；后续只能走新 retry run |
| 不存在 | pending/unknown | 无法证明 rollback 也无法证明 commit | attempt `UNKNOWN`、operation `unknown`，停止 run 并进入 audit-only reconcile |
| 任意 | 任意 | marker、outcome、scope 或业务证据矛盾 | attempt/operation 保守转为 `MANUAL_REVIEW/manual_review`，不改写为成功/失败；`RECOVERY_EVIDENCE_CONFLICT`，停止并 audit-only reconcile |

“领域证明”不是 coordinator 根据行数猜测；它由 WP6/WP7 冻结的 typed evidence provider 返回 canonical evidence 及 hash。WP5 fake executor 只能验证路由，不能宣称 website/poll 领域证据已完成。

#### 14.5.4 WP3 recovery writer 边界

WP3 需新增受限 typed recovery API，在 operation → gate → attempt 锁顺序下重验 marker/outcome/attempt status，并原子完成终态与 usage metrics。它只能确认已有永久事实或记录 conservative 失败/unknown，不创建 marker、不执行业务 mutation、不改写终态 attempt。marker 已存在的 succeeded recovery 必须使用 marker/operation 中的持久化 scope 派生 candidate/usage，不接受 recovery caller 自报计数；website 使用 committed marker item count，poll 使用单 slice 计数 1。

恢复处理后，只有当所有 attempt 都已终态时才可调用 `finishRun`。任一 evidence conflict、无法持久化 unknown 或读取失败都保持 fail closed 并告警，不通过盲目重复 mutation 消除 running 状态。

#### 14.5.5 `recovery_not_started` deferred retry

`rolled_back` 保留原 operation 的唯一 `(domain,batch_key_hash)` 身份，因此它不是“释放唯一键后重新创建 origin”的信号。为避免该 scope 永久漏删，scheduler/domain executor 必须先按第 12.2 节恢复全部 server-side deferred RETRY running run；确认不存在此类 running run 后，才在 fresh scope 选择前通过 WP3 typed API `findDeferredRetryableOperations(domain, limit)` 发现并优先接管满足以下全部条件的 operation：

```text
operation.outcome = rolled_back
operation.detail_purged_at IS NULL
不存在 unresolved reconciliation/manual_review/unknown
不存在 committed marker
latest mutation attempt.status = skipped
latest mutation attempt.stable_reason = recovery_not_started
latest mutation attempt.executionBudgetUsage = {0, 0}
不存在引用该 operation 的 running scheduler_deferred_retry run
```

发现结果按 `operation.created_at, operation_id` 稳定排序。API 返回原 operation ID、origin compatibility/effective config、永久 scope identity 和最新 attempt 事实，不接受调用方上传 scope/hash/item；出现相同 operation 的重复最新序号、缺失 tree、marker/outcome 矛盾或不可支持的旧 contract 时 fail closed 并告警，不能当成 fresh 候选绕过。

接管协议冻结为：

1. 完成第 12.2 节 running scheduler recovery 后、创建 fresh scheduler origin run 或选择新 business scope 前，先处理 deferred retry；
2. 每个 deferred operation 使用新的 canonical trigger UUID 和新的顶层 `RETRY` run，预算采用当前 server-side retry policy，compatibility/effective config 与 scope 从 origin 永久事实重建；每 tick 数量受 server-side `maxDeferredRetriesPerDomain` 正整数上限约束，不能由 HTTP 提高；
3. server-side factory 写入第 6.2 节固定 recovery reason/reference；WP3 在 operation 锁内再次验证上述完整资格，并按既有原子协议完成 retry relation、`rolled_back → pending` 与 prepared retry attempt；
4. mutation 仍需重新通过当前 lease、static/dynamic gate、deadline 和预算；再次在 mutation 开始前被阻断时，新的 prepared retry attempt 仍按第 14.5.2 节回到 `skipped + rolled_back`，下轮可再次发现；
5. 一旦 operation committed、进入 unknown/manual_review、detail 已 purge 或最新 attempt 不再是该稳定原因，自动 deferred retry 资格永久消失；
6. 只要本次 discovery 返回未处理或新产生的 eligible deferred operation，本 domain 本 tick 不创建 fresh origin；全部 eligible deferred operation 已成功接管或达到 terminal 后才可进入 fresh 选择；
7. fresh selector 若遇到已有 operation 的 scope，不得因唯一冲突停在最老候选，也不得重复创建 origin；它必须把符合条件者交给下轮 deferred retry，其他已有 scope 跳过并继续推进当前页。

自动接管只证明上一次 mutation 从未开始，不把普通失败解释为可安全重试。权限、reference factory、数据库读取或基础合同校验失败时 operation 保持 `rolled_back`，本轮该 domain 告警并停止 mutation；不得降级到 legacy delete。若单个 operation 的永久 tree/evidence 矛盾或旧 contract 已不可支持，必须通过 WP3 受限 writer 收敛到 `manual_review` 并创建/要求 audit-only reconciliation，使其退出自动队列；该 scope 保留给人工处理，但不得永久阻塞同 domain 后续 fresh 候选。

#### 14.5.6 server-side deferred RETRY running recovery

`recoverDeferredRetry` 先重验 scheduler+retry 双权限，再按 run ID 执行无锁 exact-command lookup。若 run 已 terminal，直接返回 terminal replay；若仍 running，则取得唯一 execute lease并在 lease 内再次读取原 exact command、预算及最新 tree，先执行不依赖 static/dynamic=true 的 audit recovery 分类。只有空 retry run 补 `prepareRetry`、prepared attempt 继续或任何其他 mutation recovery 才紧邻动作重验 static/dynamic gate、原 deadline 和剩余预算；marker 已提交后的 finishRun、证据驱动终态与 controlled stop 不得被当前 gate=false 阻断。它只能恢复同一持久化 run/trigger/related operation，不生成新 trigger，不调用 `executeFresh`，也不重新选择 business scope：

| 崩溃点后的永久事实 | 恢复动作 |
|---|---|
| run running；operation 仍 rolled_back；尚无本 run 的 retry relation/attempt/marker | 这是 startRun 后、prepareRetry 前。重验 provenance、原 operation 完整 deferred 资格及本 run 零 tree；gate/deadline/budget 允许时，在该原 run 下调用 WP3 原子 `prepareRetry`，完成 relation、`rolled_back → pending` 与 prepared attempt，再进入同 attempt mutation。禁止按第 14.5.1 节重选首页 |
| run running；operation pending；存在本 run 唯一 retry relation 与唯一 prepared attempt；无 marker | 这是 prepareRetry 后、mutation 前。按第 14.5.2 节恢复同一 attempt；不创建新 relation/attempt/run |
| run running；attempt running；marker/outcome 尚未形成完整 committed 事实 | 按第 14.5.3 节用领域证据恢复；无证据不得盲目重复 mutation |
| run running；marker、operation committed、attempt succeeded/usage 完整 | 这是 marker commit 后、finishRun 前。只重建 progress/summary 并幂等 `finishRun`，不得再次 mutation |
| run 已 terminal | 返回 terminal replay；不创建 replacement run |
| 任一 provenance、related operation、relation、attempt、marker 或合同矛盾 | `DEFERRED_RETRY_RUN_CONFLICT`，停止本 domain 并进入 manual-review/audit-only；不得回退到 rolled_back discovery 或 fresh origin |

若第一行的空 deferred RETRY run 在恢复时 gate 已关闭、原 deadline 已到或 lease 无法继续，使用 operationless stop checkpoint 终结原 run，operation 保持 `rolled_back` 且原 `recovery_not_started` attempt 不变；本 tick 停止该 domain，不立即创建下一 retry run。下一 tick可重新进入 rolled_back discovery。若第二行已有 prepared attempt，则必须先按第 14.5.2 节收敛为 `skipped + rolled_back + usage=0` 再 finish run。

上述恢复读取原 run scope 中的原预算，deadline 仍为原 `startedAt + original maxRunDuration`；marker commit 后的 usage 仍只取 `TrustedMutationReceipt` 对应的持久化事实。任何阶段都不能按当前配置为同一 running run重置预算或 deadline。

---

## 15. P0 入口接线与阶段边界

### 15.1 controller

WP5 开发后：

- website cleanup 与 poll purge controller 只依赖 coordinator-facing facade；
- dry-run 可继续独立且不获取 execute lease；对应领域在 WP6/WP7 接入时迁移到 typed run context；
- legacy service 的 public execute 方法降为 package-private/internal，或增加强制 session 参数；
- 没有 WP6/WP7 executor 时 execute 返回 `DOMAIN_EXECUTOR_UNAVAILABLE`；
- 不允许回退旧 `runScheduled(..., dryRun=false)`。

### 15.2 scheduler

WP5 将 scheduler 改为：

```text
生成 trigger
→ website coordinator invocation
→ poll coordinator invocation
```

分页循环归各 domain executor，scheduler 不再持 cursor 或多次调用 service。

article archive/purge 属于非本期 P0：

- 本轮不得声称已受 WP5 完整治理；
- 保持其独立静态开关 false；
- P0 scheduler execute 分支不得因为启用 website/poll 而隐式打开它们；
- 后续是否迁移另立工作包。

### 15.3 WP6/WP7 handoff

WP5 提供 port，但不提供伪实现：

```text
WP6 WebsiteRetentionExecutionPort
  20→10→5、failure group、operation/attempt、marker

WP7 PollRetentionExecutionPort
  单切片、operation/attempt、marker、late-write safety
```

在对应 port 接入前，该 domain execute 保持不可用。WP5 验收不得用调用 legacy service 的 adapter 代替。

---

## 16. 锁顺序与事务边界

### 16.1 跨系统顺序

```text
MySQL/authorization read-only preflight 与 exact-command lookup（无行锁）
→ Redis lease acquire/check
→ Redis dynamic gate read
→ MySQL short audit transaction
→ mutation 前 Redis lease/gate check
→ MySQL mutation transaction
```

授权/lookup 只读阶段不得使用 `FOR UPDATE`、不得开始长事务。禁止在 MySQL mutation transaction 内访问 Redis。

### 16.2 MySQL 内部顺序

WP5 不改变 WP2/WP3 已冻结顺序：

```text
operation
→ reconciliation/failure gate
→ attempt
→ batch item
→ domain business rows（按 WP6/WP7 细化）
```

run completion 只锁 run 并读取 attempt 汇总，不能反向锁 operation。

### 16.3 scheduler 与人工并发

两者竞争同一 Redis lease：

- 只有一个进入 run start；
- loser 按 source 返回 busy/skipped；
- lease loss 后新 worker 可能与旧 transaction 短暂重叠，这是 P0 已接受的无 fencing 模型；
- WP6/WP7 行锁和 marker 必须保证不会重复提交。

---

## 17. 指标、日志与告警

最低指标：

```text
retention_execute_request_total{domain,source,result}
retention_execute_lease_total{result}
retention_execute_lease_wait_ms{source}
retention_execute_checkpoint_total{domain,result}
retention_execute_mutation_seconds{domain,outcome}
retention_execute_run_stop_total{domain,reason}
retention_execute_budget_remaining{domain,type}
retention_execute_deferred_retry_total{domain,result}
retention_execute_deferred_retry_recovery_total{domain,stage,result}
retention_execute_usage_mismatch_total{domain}
```

高优先级告警：

- lease renewal/ownership lost；
- Redis lease unavailable；
- dynamic switch malformed/unavailable；
- unknown mutation outcome；
- marker/audit inconsistency；
- run finalization uncertain；
- deferred retry evidence invalid/manual-review；
- deferred RETRY running provenance/tree conflict 或恢复不确定；
- callback reported candidate 与 trusted usage 不一致；
- scheduler operator invalid；
- 配置 TTL/timeout 不合法；
- coordinator 检测到重复 domain executor。

日志不得包含正文、payload、完整 evidence 或敏感审批内容。允许记录 run ID、operation ID、attempt ID、domain、source、稳定错误码和安全计数。

可观测性采集失败不能打开门禁或继续未知状态 mutation。普通指标失败可记录告警并停止 canary 晋级；最终生产准入口径由 WP8 冻结。

---

## 18. 配置

新增默认安全配置：

```yaml
geo:
  retention:
    execute:
      mutation-timeout-seconds: ${GEO_RETENTION_MUTATION_TIMEOUT_SECONDS:120}
      max-run-seconds: ${GEO_RETENTION_MAX_RUN_SECONDS:3600}
      max-deferred-retries-per-domain: ${GEO_RETENTION_MAX_DEFERRED_RETRIES_PER_DOMAIN:20}
```

`max-deferred-retries-per-domain` 必须为 `1..100`；非法值使 scheduler execute preflight fail closed，不取默认替代、不进入 fresh origin。

继续沿用且默认 false：

```text
GEO_RETENTION_SCHEDULER_ENABLED=false
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=false
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED=false
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=false
GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED=false
```

WP5 测试和代码提交不得把任何默认值改为 true。

---

## 19. 错误码与 HTTP 语义

| code | HTTP | 说明 |
|---|---:|---|
| EXECUTE_COMMAND_INVALID | 400 | command 形状或范围非法 |
| EXECUTE_PERMISSION_DENIED | 403 | operator 无权限/非内部账号 |
| EXECUTE_STATIC_GLOBAL_DISABLED | 409 | 全局 static=false |
| EXECUTE_STATIC_DOMAIN_DISABLED | 409 | domain static=false |
| EXECUTE_DYNAMIC_DISABLED | 409 | Redis 合法 false/missing |
| EXECUTE_LEASE_BUSY | 409 | 另一 execution 持有 lease |
| EXECUTE_LEASE_UNAVAILABLE | 503 | Redis lease 操作失败 |
| EXECUTE_GATE_UNAVAILABLE | 503 | dynamic gate malformed/unavailable |
| EXECUTE_CONFIGURATION_INVALID | 503 | TTL/timeout/operator 等准入配置非法 |
| EXECUTE_BUDGET_EXHAUSTED | 409 | 未开始下一 mutation 的受控停止 |
| DOMAIN_EXECUTOR_UNAVAILABLE | 503 | WP6/WP7 尚未接入或 bean 冲突 |
| EXECUTE_LEASE_LOST | 503 | run 中 lease 失效 |
| RUN_FINALIZATION_UNCERTAIN | 503 | mutation 事实存在但 run rollup 未确认 |
| MUTATION_OUTCOME_UNKNOWN | 503 | 需要 reconciliation |
| RECOVERABLE_RUN_CONFLICT | 503 | 同 domain 存在多条 scheduler running run |
| DEFERRED_RETRY_RUN_CONFLICT | 503 | 后台 deferred RETRY running run 的 provenance/tree/operation 不一致 |
| RECOVERY_IN_PROGRESS | 409 | 旧 running run 已由其他 worker 恢复 |
| BUDGET_EVIDENCE_INVALID | 503 | 原预算或 attempt usage 缺失/非法/溢出 |
| MUTATION_USAGE_MISMATCH | 503 | callback reported candidate 与 marker-derived trusted usage 不一致，事务已回滚 |
| DEFERRED_RETRY_EVIDENCE_INVALID | 503 | `recovery_not_started` operation 不满足自动接管完整形状，进入 manual review/audit-only |
| RECOVERY_EVIDENCE_CONFLICT | 503 | marker、operation、attempt 或领域证据矛盾 |

scheduler 不使用 HTTP，但必须保留相同稳定 code 到 skipped run、metrics 或 alert。

---

## 20. 测试契约

### 20.1 单元测试

至少覆盖：

1. static global/domain/source 全组合；
2. command source/ref/operator/related operation 形状；
3. budget 边界与 deadline；
4. lease busy/unavailable/lost 映射；
5. dynamic gate code 映射；
6. coordinator pre-run 调用顺序；
7. mutation checkpoint 调用顺序；
8. transaction 内禁止 Redis callback；
9. controlled stop 与 unexpected failure；
10. dry-run/reconcile audit-only 不获取 execute lease；
11. scheduler recoverable-run 无/1/多条决策与当前 recovery operator 授权；
12. original budget JSON canonicalization、exact-command 比较、checked SUM 和原 startedAt deadline；
13. 空 run、prepared、running-with-marker、running-without-marker、evidence-conflict 恢复矩阵；
14. callback `PendingMutation` 不能构造 transaction outcome；
15. `recovery_not_started` 仅匹配完整 skipped/rolled_back/usage=0 形状，普通 failed rolled_back、marker 存在或 unresolved 状态不进入 deferred retry；
16. callback reported candidate 与 trusted usage 不等时 commit 前拒绝，budget/progress 只接受 receipt usage；
17. deferred RETRY provenance 的 source/reason/ticket/invocation kind/related operation 任一不一致均不进入后台 recovery，普通人工 retry 永不被自动发现。

### 20.2 架构契约测试

必须静态防止：

- `DataRetentionController`、`DataRetentionScheduler` 的 execute 方法直接调用 website/poll legacy mutation service；只读 dry-run 依赖在 WP6/WP7 接入前允许保留；
- WP5～WP7 调用 `DataRetentionRunAuditService`；
- domain executor 注入 `StringRedisTemplate` 或 WP4 management service；
- coordinator 写审计 SQL；
- coordinator 直读 operation/attempt/item/marker 表重建恢复事实；
- scheduler 外层获取 `DataRetentionSchedulerLock`；
- WP5 reconciler 或 budget blocker 调用 enable；
- execute facade 在 executor 缺失时回退 legacy service；
- WP3 marker writer 以外的代码实例化/继承 `TrustedMutationReceipt`，或 coordinator 用 reported actual 调用 budget/progress afterMutation；
- `application*.yml` 中五个安全默认值变为 true。

### 20.3 Redis + MySQL 集成测试

WP5 required profile 最低覆盖：

1. manual 与 scheduler 竞争同一 lease，只有一个 startRun；
2. 两个人工请求竞争，loser 409 且无 running run；
3. scheduler busy 直接 terminal skipped，无 running 中间态；
4. Redis lease unavailable：无 run、无 mutation；
5. lease 后 dynamic switch 被关闭：无 run；
6. startRun 前 static 被关闭：无 running run；
7. run 创建后、mutation 前 dynamic 关闭：不开始 transaction，run 受控结束；
8. run 创建后、mutation 前 lease lost：不开始 transaction；
9. transaction 期间 lease lost：当前 callback 可 commit，下一 callback 被拒绝；
10. gate 检查期间无活动 MySQL transaction；
11. mutation callback 内 transaction active、非 read-only、timeout 生效；
12. callback 回滚时 marker 与业务 mutation 都不存在；
13. callback commit 后 marker/业务事实保留；
14. max transaction/candidate/deadline 到界不多执行一批；
15. 多 domain scheduler trigger 共享 trigger ID、分别一条 run；
16. 分页 executor 始终使用同一个 run；
17. retry busy 不创建 relation/attempt、不推进 operation；
18. audit-only reconcile 不获取 lease，也不能 mutation；
19. executor 缺失 fail closed，不调用 legacy service；
20. run finalization 故障不返回完全成功；
21. owner 不匹配的 lease release 不删除新 worker lease；
22. renewal failure 使 session 永久 stopped；
23. JVM/MySQL 非 UTC 不改变 trigger/run 时间契约；
24. 测试结束 lease key 清理，所有 execute/enable-write 配置仍为 false。
25. terminal replay 不要求当前 gate/lease，但未授权 operator 不能读取 replay context；
26. pre-run lookup 后并发 writer 完成，busy/gate reject 前二次 lookup 返回 terminal replay，不创建 skipped run；
27. pre-run lookup 后并发 writer 创建 running run，`startRun` 的 `RUNNING_REPLAY` 进入 resume，不执行 fresh 首页；
28. actual candidate 大于 estimate 或总预算时，mutation/marker 同事务回滚；
29. controlled stop checkpoint 重放不重复增加 warning，未执行候选不计入 skipped business count。
30. scheduler 创建新 trigger 前发现并优先恢复旧 running run；旧 run 无 operation/attempt/relation/item/marker 时才允许在原 run 重选首页；
31. running recovery 使用 canonical scope 中的原预算，从 attempt budget usage metrics 重建已用额度，deadline 锚定原 `startedAt`；
32. 崩溃留下 prepared attempt 按同一 attempt 恢复或 operationless skip，running attempt 按 marker/outcome/领域证据转换，矛盾不盲目重试；
33. callback 只返回 `PendingMutation`；`CommittedMutation/UNKNOWN` 分别只能由 transaction manager 成功确认或异常分类产生；
34. scheduler 与 server-side deferred RETRY recovery 发现 SQL 均在 MySQL 8 上使用生产规模数据验证计划；如需索引通过新迁移增加，不修改 V354。
35. origin/retry prepared attempt 在 mutation 未开始且 gate/deadline/budget 阻断时原子落为 `skipped + rolled_back + usage=0`，不设置 finalization/anchor；下一 scheduler tick 在 fresh scope 前创建新 `RETRY` run、复用原 operation/item/scope 并可成功提交，不创建第二个 origin。再次阻断仍可下轮发现；普通 failed rolled_back 不得被自动接管；
   同场景通过正常 controlled-stop 路径而非 crash recovery 触发时，必须得到相同收敛结果后才能 finish run；
36. callback reported candidate 小于或大于 marker writer trusted usage 时，同事务业务 mutation、marker、operation/attempt terminal 和 usage 全部回滚，session progress 不变；相等时 commit 后 progress 只按 trusted receipt 更新，重启聚合得到相同值；
37. fresh selector 最老候选已存在 `recovery_not_started` operation 时不会永久停页：符合条件者转 deferred retry，不符合条件的已有 scope 被跳过，后续 fresh 候选仍可推进；deferred 上限未处理项会阻止本 tick fresh origin；
38. deferred operation 永久 tree/evidence 矛盾或旧 contract 不支持时退出自动队列进入 manual-review/audit-only，保留该 scope 审计身份但不永久阻塞同 domain 后续候选。
39. server-side deferred RETRY 在 startRun 后崩溃：下次 tick 先发现原 running run，以原 trigger/budget/operation 在同一 run 补齐唯一 relation/attempt 并提交；不创建第二 run，不重选 scope；
40. server-side deferred RETRY 在原子 prepareRetry 后崩溃：下次 tick 发现 pending operation + 原 running run/relation/prepared attempt，恢复同一 attempt 并提交；不依赖 rolled_back discovery；
41. server-side deferred RETRY 在 marker transaction commit 后、finishRun 前崩溃：下次 tick 发现原 running run，只按永久 marker/outcome/usage 完成 summary 与 finishRun，不再次 mutation；
42. 同 domain 同 operation 多个 running deferred run、provenance/tree 矛盾及普通人工 retry 均 fail closed；scheduler+retry 任一权限缺失不得恢复，也不得绕过 discovery 继续 fresh；
43. marker 已提交后的 deferred running recovery 在当前 static/dynamic=false 时仍可完成 audit recovery/finishRun；空 run 或 prepared attempt 不得在 gate=false 时开始 mutation。

真实 website/poll 重叠提交、行锁和 marker 幂等由 WP6/WP7 required tests 完成；WP5 可以使用不删业务数据的 fake executor 验证协调顺序，但不能以 fake executor 宣称领域 mutation 已接入。

### 20.4 非阻断与阻断尺度

依据当前评审尺度，以下可作为后续增强而非 WP5 功能代码硬阻断：

- 极端毫秒级 gate/lease 交错的穷举；
- 完整 dashboard 和全部 latency histogram；
- 所有客户端断连时刻的 100% 故障矩阵。
- pre-run 二次 lookup 所有毫秒级竞态组合与 stop warning 多 worker 精确计数穷举；但基本幂等路径仍要有测试。

以下仍是阻断：

- 任一 P0 mutation 可绕过 static/dynamic gate 或 lease；
- 后台存在自动 `false → true`；
- lease/gate 失败仍开始新 mutation；
- executor 缺失时回退 legacy 删除；
- 跨 domain/tenant scope 混用；
- marker 与业务 mutation 不同事务；
- scheduler 重启无法发现旧 running run，或恢复时重置预算/deadline；
- scheduler 重启无法发现 server-side deferred RETRY running run，或误自动恢复普通人工 retry；
- prepared/running attempt 在无 marker/领域证据时盲目重复 mutation；
- `recovery_not_started` operation 被终结为不可重试状态、重新创建 origin，或未在 fresh scope 前进入 deferred retry；
- callback reported candidate 与 marker-derived trusted usage 不一致仍能 commit，或 session 按 reported actual 扣减预算；
- 默认 execute/enable-write 被打开。

---

## 21. 文件变更计划

评审通过后预计修改：

```text
geo-server/src/main/java/com/huanjing/geo/module/retention/execute/**
geo-server/src/main/java/com/huanjing/geo/module/retention/schedule/DataRetentionScheduler.java
geo-server/src/main/java/com/huanjing/geo/module/retention/schedule/DataRetentionSchedulerLock.java
  （迁移/兼容为 execute lease，不保留两套物理锁）
geo-server/src/main/java/com/huanjing/geo/module/retention/controller/DataRetentionController.java
geo-server/src/main/java/com/huanjing/geo/module/retention/config/DataRetentionProperties.java
geo-server/src/main/java/com/huanjing/geo/module/retention/audit/service/DataRetentionP0AuditService.java
geo-server/src/main/java/com/huanjing/geo/module/retention/audit/model/DataRetentionAuditModels.java
  （仅补 exact-command/recoverable-scheduler/recoverable-deferred-run/deferred-operation lookup、typed persisted-run read model、trusted receipt/usage 契约、受约束 skipped-run 与 attempt recovery 原语）
geo-server/src/main/resources/application.yml
geo-server/src/main/resources/db/migration/<NEXT>__data_retention_execute_permissions.sql
  （如 EXPLAIN 验证需要，同一待评审迁移增加 recoverable-run 候选索引）
geo-server/src/test/java/com/huanjing/geo/module/retention/execute/**
geo-server/pom.xml
  （如增加独立 required profile）
```

WP5 不应修改 website/poll 的 mutation SQL；WP6/WP7 各自在后续工作包改造对应 service。

---

## 22. 开发与验收顺序

1. 再次扫描 worktree 和 Flyway 版本；
2. 实现 model、static gate、authorization 和 budget；
3. 将 scheduler lock 泛化为唯一 execute lease；
4. 实现 coordinator/session/transaction wrapper；
5. 为 WP3 增加 exact-command/recoverable-scheduler/recoverable-deferred-run/deferred-operation lookup、typed persisted-run read model、trusted receipt/usage 契约和受限 recovery/skipped-run 原语；
6. 实现 original budget 持久化、attempt usage 聚合、原 startedAt deadline 与 overflow fail-closed；
7. 建立 domain port registry，executor 缺失 fail closed；
8. 改造 controller/scheduler 路由骨架，实现 scheduler recovery-first 并阻断 legacy execute fallback；
9. 增加权限迁移；
10. 完成单元和架构测试；
11. 完成 Redis/MySQL fake-executor 集成测试；
12. 核对所有默认值仍为 false；
13. WP5 评审通过后再开始 WP6。

---

## 23. WP5 完成条件

- [ ] coordinator pre-run 和 per-mutation 顺序已由测试固定；
- [ ] manual/scheduler/canary/retry execute 与 audit-only reconcile 均有 typed contract；
- [ ] 全部 mutation source 使用同一物理 execute lease；
- [ ] lease busy/unavailable/lost 均 fail closed；
- [ ] scheduler busy terminal skipped，无临时 running；
- [ ] static global/domain 与 WP4 dynamic global/domain 同时生效；
- [ ] dynamic gate 在 lease 后和每个 mutation 前重读；
- [ ] coordinator-owned transaction 内不访问 Redis；
- [ ] 基础 transaction/candidate/deadline 预算生效；
- [ ] 原预算已进入 canonical run scope，running recovery 从 attempt usage 重建额度且 deadline 不重置；
- [ ] 顶层 run 归 coordinator，分页不创建 run；
- [ ] scheduler 创建新 trigger 前优先发现/恢复旧 running run，多条异常 fail closed；
- [ ] scheduler 在 rolled_back discovery 前恢复 server-side deferred RETRY running run，三处崩溃均复用原 run/tree，普通人工 retry 不被后台接管；
- [ ] 空 run 首页重选边界、prepared/running attempt 恢复矩阵和矛盾证据 manual-review 路径已固定；
- [ ] mutation 未开始统一落为可重试 `rolled_back`，deferred retry 在 fresh scope 前复用原 operation 且不会自动接管普通失败；
- [ ] marker writer trusted receipt 是即时与恢复预算的唯一 candidate usage 来源，不一致整事务回滚；
- [ ] executor 缺失明确 fail closed；
- [ ] controller/scheduler 不再直达 P0 legacy mutation；
- [ ] retry busy 不创建 attempt/relation；
- [ ] audit-only reconcile 与 mutation reconcile 分离；
- [ ] 后台没有 enable writer；
- [ ] 权限迁移精确、幂等、拒绝非法角色；
- [ ] 单元、架构、required Redis/MySQL coordinator 测试通过；
- [ ] `git diff --check` 通过；
- [ ] 所有 execute/enable-write 默认值仍为 false；
- [ ] 未执行生产迁移、清理、canary 或动态 true 写入。

WP5 完成后仍不能声明：

- website/poll 已能通过新协调器执行真实清理；
- WP6/WP7 已完成；
- V133/WP2/生产克隆阻断已关闭；
- 可以启用生产 execute。

---

## 24. 复审焦点

此前评审的架构决策、普通 scheduler running recovery、deferred rolled_back 接管与 `TrustedMutationReceipt` 单一预算事实源均已关闭，本版不再将其作为开放问题。

本次请仅聚焦复审：

1. scheduler 是否在创建新 deferred operation/fresh origin 前，可靠发现并恢复带 canonical server-side provenance 的 running `RETRY` run；
2. startRun 后、prepareRetry 后、marker commit 后三处崩溃是否均复用原 trigger/run/operation/attempt/预算完成恢复，且普通人工 retry 不进入后台自动路径。

在上述决策评审通过前，本 SPEC 状态保持：

> **架构方向通过，待 deferred RETRY running 恢复路径聚焦复审，未授权开展 WP5 开发；所有 execute 与 enable-write 开关保持 false。**
