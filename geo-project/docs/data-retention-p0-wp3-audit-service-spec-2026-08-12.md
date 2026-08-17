# 数据定期清理 P0 WP3 审计服务 SPEC（第三次评审通过版）

> 文档日期：2026-08-12
> 当前状态：第三次评审通过，允许开展 WP3 Java 与测试开发
> 上游基线：`data-retention-p0-wp2-ddl-spec-2026-08-11.md` 第五次评审通过版、`V354__data_retention_execute_governance.sql` 当前实现
> 授权边界：仅授权 WP3 Java 审计服务与测试开发；不授权 WP4～WP8（包括 WP8 evidence canonical schema），不授权打开任何 execute 开关，不授权生产迁移或生产清理，也不解除 V133 生产准入阻断

---

## 0. 结论摘要

WP3 将 WP2 已冻结的数据库治理模型实现为一组强类型、事务边界明确、默认不可被生产 execute 入口调用的 Java 审计原语。它解决以下问题：

1. 一次顶层领域请求只创建一条 `data_retention_run`，分页不再自行创建 run；
2. 为官网 batch 和 poll slice 创建永久 operation identity，并正确维护跨 run 的 origin/retry/reconcile relation；
3. 记录 page、normal batch、split、retry、canary、reconcile、lease busy 等 attempt；
4. 官网 commit marker 和 poll purged-slice marker 可被领域事务以同一数据库事务写入；
5. run rollup、operation outcome、最终结果时间和 detail retention anchor 按 WP2 契约推进；
6. 统一实现 UUID、UTC 时间、config fingerprint、operation scope hash、JSON 大小和错误字段限制；
7. 保留现有非 P0 审计调用的兼容性，同时为后续 WP5～WP7 提供不可绕过的 typed API。

WP3 不执行候选扫描、不删除业务数据、不实现动态开关、不获取 Redis lease、不决定 20→10→5 拆分、不管理 failure group、不执行 unknown outcome 的业务取证，也不改造现有 HTTP/scheduler execute 接线。上述职责分别属于 WP4～WP7。

WP2 当前仍有独立准入阻断：空库真实历史迁移链在 V133 失败。该问题不阻止 WP3 SPEC 评审或经批准后的本地 Java 开发，但在 V133 结论关闭前，WP3 不得被标记为生产就绪，基于 V353/V354 fixture 的测试也不得冒充 V1→当前迁移链验收。

### 0.1 第一次评审修订

| 评审问题 | 本版修订 |
|---|---|
| retry attempt 缺少原子 `rolled_back → pending` | 冻结为同一个 operation-locked 事务完成 guarded 状态更新、retry relation 和 attempt；任何一步失败整体回滚，重放必须同时验证三者 |
| purge 后 audit-only reconciliation 无 relation | 允许创建无 attempt 的 audit-only reconcile run，并在 operation 锁内创建永久 reconcile relation、resolved reconciliation；禁止修改 outcome/anchor/detail |
| origin scope 冲突恢复形成 run→operation | 首次 INSERT 冲突必须完整回滚；恢复事务重新按 existing operation→current run→attempt/item 获取锁，禁止在持有 run 锁时检查 existing operation |
| application version 同时承担兼容合同与部署事实 | 拆分 `RetentionCompatibilityContract` 与 `RetentionDeploymentIdentity`；retry 仅继承前者/effective config，当前 writer 身份取当前 provider，持久化对象的创建版本原样保留 |

### 0.2 第二次评审修订

| 评审问题 | 本版修订 |
|---|---|
| run replay 仍按 application version 冲突 | run context 明确拆分 `runCreatedByVersion/currentWriterVersion`；唯一冲突重放不比较当前 writer 与既有 run version，新 operation/commit 分别写实际 writer version |
| WP3 reconciliation 验收依赖 WP8 evidence schema | WP3 只把 evidence 视为领域不透明的 UTF-8 JSON bytes + SHA-256 digest，负责通用安全/大小/事务合同；领域 canonical schema、字段和值向量归 WP8，且不阻断 WP3 完成 |

### 0.3 第三次评审结论

第三轮评审未发现新的 P0/P1 阻断项，正式批准本 SPEC 作为 WP3 实施基线。批准范围只覆盖 WP3 Java 与测试开发；第一次、第二次评审冻结的事务、锁序、跨部署版本和 opaque evidence 合同继续有效。execute 开关、生产清理、WP8 evidence canonical schema 及 V133 生产准入结论均不在本次授权范围内。

---

## 1. 背景与当前实现差距

### 1.1 当前代码行为

当前 `DataRetentionRunAuditService` 只支持：

- 以 `domain/mode/window/metrics` 创建一条 `running` run；
- 以调用方传入计数直接结束 run；
- 按 Java 字符数截断错误信息；
- 不写 trigger、来源、操作者、配置指纹、policy、application version 或 attempt rollup。

当前官网和 poll service 都在每次分页调用内部执行 `startRun`。scheduler 翻页时会重复调用领域 service，因此一次顶层定时领域触发可能产生多条 run，不符合 P0 不变量。

当前官网 execute 以单篇文章为事务单位，没有 operation、attempt、batch item 或同事务 commit marker。当前 poll execute 虽然保持单切片事务并写 `data_retention_purged_slice`，但 marker 尚未写 `operation_id`，`poll_audit_purge_runs` 的新 run/operation/domain 物理关联也尚未接线。

### 1.2 兼容性事实

`DataRetentionRunAuditService.startRun/finishRun` 还被 article archive/purge、object storage、content URL rewrite、slim dry-run 等非本期 P0 链路复用。WP3 不得直接把旧方法改成 P0 execute 的必填强契约，否则会迫使无关领域伪造 trigger、policy 或 fingerprint。

因此冻结为两层：

- **legacy facade**：保留旧签名和历史行为，供非 P0 链路过渡；不得被 WP5～WP7 新 execute 代码调用；
- **P0 typed audit service**：仅接受强类型 command/context，完整执行本 SPEC；后续 P0 execute 入口必须使用这一层。

legacy facade 是否最终迁移不属于 WP3 阻断项。WP3 必须用依赖/架构契约测试保证新增 P0 代码不会回退调用 legacy facade。

---

## 2. 目标、范围与非范围

### 2.1 WP3 目标

1. 冻结审计服务的 Java 类型、方法职责、事务传播和错误语义；
2. 实现 run、operation、operation-run、attempt、batch item、marker、operation outcome 和 run rollup 的持久化原语；
3. 实现 website/poll 两个 domain 的 canonical encoder 和固定 SHA-256 向量；
4. 保证所有写入满足 V354 的复合 FK、唯一约束、CHECK、锁顺序和 365 天锚点契约；
5. 为 WP5 协调器、WP6 官网批处理和 WP7 poll 接线提供明确端口；
6. 在真实 MySQL 8 上验证事务原子性、并发、幂等、恢复与永久 marker 语义；
7. 保持所有 execute 默认值和运行时开关为 false。

### 2.2 WP3 范围

- P0 run context 及 run lifecycle writer；
- operation identity 和 operation-run relation writer；
- website/poll config fingerprint 与 operation scope hash；
- batch attempt lifecycle writer；
- website batch item 的首次持久化与重读；
- website commit marker 的 transaction-bound writer；
- poll purged-slice marker 的 transaction-bound writer；
- `poll_audit_purge_runs` 新关联字段的 typed update/insert port；
- unknown outcome reconciliation 审计行的 typed prepare/resolve/audit-only writer；
- operation outcome/finalization/retention anchor 的原子更新原语；
- run attempt rollup；
- legacy run audit facade 兼容；
- 单元、SQL 契约和 MySQL 8 集成测试。

### 2.3 WP3 非范围

- 新增或修改 Flyway DDL；
- 修复或豁免 V133；
- WP2 生产克隆、metadata lock、最终容量验收；
- Redis 动态开关、CAS、requested/applied 对账；
- execute lease、预算和 `DataRetentionExecuteCoordinator`；
- HTTP 权限、审批流和工单系统本身；
- 官网候选 SQL、文章加锁、20→10→5 调度、failure group/regroup；
- poll eligibility、安全谓词、删除顺序、summary recompute 或 late-write guard；
- unknown outcome 的业务状态检查和人工决策 UI；
- attempt/item 365 天物理清理 job；
- 接入现有 controller、scheduler 或生产 execute 路径；
- 打开、提升或自动改变任何 execute 开关。

如实现中发现 V354 无法表达本 SPEC，必须停止并回到数据库设计评审；不得在 WP3 中偷偷增加迁移或依赖未评审的手工 DDL。

---

## 3. 冻结不变量

1. 一个顶层请求在一个 domain 中最多创建一条 run；分页、拆分和 batch 都是该 run 下的 attempt。
2. scheduler 同一调度周期为多个 domain 共享 `trigger_id`，但每个 domain 单独一条 run。
3. retry/reconcile 每次顶层请求创建新 run，复用原 operation；不得重开或重算原 run。purge 后 audit-only reconciliation 同样创建新 reconcile run 和永久 relation，但不创建 attempt。
4. operation 是逻辑 mutation identity，run 不是 operation，operation-run 不是第二张 run 表。
5. 普通 origin 创建遇到相同 `(domain,batch_key_hash)` 时，只有同一 origin run、相同 canonical command 且完整 operation/relation/first-attempt/item tree 已存在时才可作为“本次 prepare 响应丢失”的幂等重放；其他情况不得复用，必须返回显式 scope conflict，由上层依据永久 marker 和 outcome 决定 skip/retry/reconcile。
6. operation 与唯一 origin relation 必须在同一事务创建；origin relation 的 run 必须等于 `origin_run_group_id`。
7. attempt 是过程审计，不是 commit 证明；官网 commit 只以 `data_retention_batch_commit` 为准，poll commit 只以 `data_retention_purged_slice` 为准。
8. 官网 marker、业务清理、operation committed finalization 必须同事务；poll marker、切片清理、operation committed finalization 必须同事务。
9. marker writer 必须加入调用方已存在的业务事务；没有活动事务时立即拒绝，不得自行 `REQUIRES_NEW` 制造“有 marker 无业务提交”。
10. operation、首次 attempt 和 website batch item 必须在 mutation 前提交，使连接中断后仍可恢复原批次成员。
11. mutation attempt 的终态不可变；reconciliation 新增审计行，不改写旧 unknown/manual-review attempt 的终态。
12. operation finalization 只允许从未最终化状态进入 `committed/closed_no_mutation`；最终化后只能单调推进 retention anchor，不能回退最终结果。
13. `detail_purged_at` 非空后禁止新 mutation attempt/item、改变结论的 retry/reconcile、outcome 变更或 anchor 推进；唯一 relation 例外是无 attempt、结论一致的 audit-only reconcile run/relation。
14. 所有 P0 新时间显式按 UTC 写入 `DATETIME(3)`；不得依赖应用默认时区或表的 `CURRENT_TIMESTAMP` 默认值形成业务时间。
15. 所有 P0 execute run 必填 trigger、source、operator、config fingerprint/schema version、policy 和 application version。
16. JSON 与错误字段按 UTF-8 字节而不是 Java 字符数限制；任何 marker 合同字段失败都必须使业务事务回滚。
17. application version 只属于部署事实，不属于跨 run 或同 run 恢复的兼容合同：run 保存创建它的版本，operation/commit 保存各自实际 writer 版本，三者允许不同；run replay 不改写创建版本。
18. 所有执行开关保持 false；WP3 bean 存在不代表 execute 获得授权。
19. dry-run 只创建 run 和 operationless page attempt；不创建 operation、batch item 或永久 marker，因为这些对象只描述实际 mutation identity。
20. 一个 mutation attempt 的创建必须有可重放的 expected-latest/CAS 语义；数据库响应丢失时不得因盲目重试产生第二条 attempt。

---

## 4. 模块结构与职责

建议在现有 retention module 下建立独立 `audit` 子包，避免继续扩大一个通用 service：

```text
com.huanjing.geo.module.retention.audit
  model/
    RetentionRunContext
    RetentionRunStartCommand
    WebsiteOperationCommand
    PollOperationCommand
    RetentionAttemptCommand
    RetentionAttemptResult
    RetentionCompatibilityContract
    RetentionDeploymentIdentity
    EvidenceDocument
  canonical/
    DataRetentionCanonicalEncoder
    DataRetentionFingerprintEncoder
    DataRetentionScopeHashEncoder
  service/
    DataRetentionP0AuditService
    DataRetentionRunLifecycleService
    DataRetentionOperationLifecycleService
    DataRetentionAttemptLifecycleService
    DataRetentionReconciliationAuditService
    DataRetentionRunRollupService
  port/
    WebsiteRetentionCommitMarkerWriter
    PollRetentionCommitMarkerWriter
    PollPurgeAuditIdentityWriter
  repository/
    DataRetentionRunAuditRepository
    DataRetentionOperationRepository
    DataRetentionAttemptRepository
    DataRetentionReconciliationRepository
    DataRetentionMarkerRepository
  support/
    DataRetentionApplicationVersionProvider
    DataRetentionAuditJsonCodec
    DataRetentionAuditClock
```

名称允许按项目习惯微调，但职责不得重新混回领域 cleanup service。canonical encoder 必须是 WP3/WP6/WP7 共用的唯一生产实现。

### 4.1 聚合入口

`DataRetentionP0AuditService` 是 WP5～WP7 的唯一公共审计 facade。repository 和 transaction-bound marker port 不向 controller/scheduler 暴露。

建议的语义接口如下；这不是逐字 Java API 约束，但评审后方法职责和事务语义不可改变：

```java
RetentionRunContext startRun(RetentionRunStartCommand command);

WebsiteOperationContext prepareWebsiteOrigin(
        RetentionRunContext run,
        WebsiteOperationCommand operation,
        RetentionAttemptCommand firstAttempt);

PollOperationContext preparePollOrigin(
        RetentionRunContext run,
        PollOperationCommand operation,
        RetentionAttemptCommand firstAttempt);

RetentionAttemptContext prepareRelatedAttempt(
        RetentionRunContext run,
        String operationId,
        RelationType relationType,
        RetentionAttemptCommand attempt);

ReconciliationContext prepareReconciliation(ReconciliationPrepareCommand command);
ReconciliationContext resolveReconciliation(ReconciliationResolutionCommand command);
ReconciliationContext recordAuditOnlyReconciliation(AuditOnlyReconciliationCommand command);

void markAttemptRunning(long attemptId);
void completeAttempt(long attemptId, RetentionAttemptResult result);
void finishRun(long runId);
```

`startRun` 返回值必须区分 `CREATED/RUNNING_REPLAY/TERMINAL_REPLAY`；terminal replay 只允许返回既有结果，不能继续传入 operation/attempt writer。`RetentionAttemptCommand` 必须分别表达 `expectedLatestAttemptId`（并发/重放 CAS）和 `lineageParentAttemptId`（split/retry/reconcile 过程追踪）；两者不能被一个含糊字段替代，具体规则见第 9.3 节。

每个 attempt prepare command 还必须有规范化 `attemptRequestId` UUID。它写入 attempt `scope_json.audit.attemptRequestId`，作为数据库响应丢失后的幂等检索键；该值不是新的业务 identity，也不新增 DDL。operation writer 持有 operation 锁、operationless writer 持有 run 锁时，必须先检查同一 scope 内是否已有该 request ID。

`scope_json` 内部 envelope 冻结为：

```json
{
  "audit": {
    "schemaVersion": 1,
    "attemptRequestId": "00000000-0000-0000-0000-000000000000"
  },
  "domainScope": {}
}
```

生产序列化使用稳定 key 顺序。保留 key `audit` 和 `domainScope`，领域调用方不得覆盖。幂等检索使用 `JSON_UNQUOTE(JSON_EXTRACT(scope_json,'$.audit.attemptRequestId'))`，只在已持有 operation/run 锁且数据量至多为该 operation/run 的 attempt 集合时执行；WP3 不为此新增索引或 DDL。

transaction-bound marker port：

```java
void commitWebsiteMutation(WebsiteCommitCommand command);
void commitPollMutation(PollCommitCommand command);
```

这两个方法必须检测 `TransactionSynchronizationManager.isActualTransactionActive()`，没有活动事务时抛出稳定错误；它们使用当前连接，不得标注 `REQUIRES_NEW`，不得在内部创建 `TransactionTemplate`。

实现优先使用 `@Transactional(propagation = Propagation.MANDATORY)` 并同时拒绝 read-only transaction；活动事务检测作为防止自调用/proxy 绕过的第二道 guard。测试必须验证“无事务”和“read-only 事务”都不会写 marker。

---

## 5. 强类型上下文契约

### 5.1 `RetentionCompatibilityContract`

每个 P0 run 和其 operation 跨部署共享以下不可变兼容字段；它精确对应 V354 operation-run 复合 FK 的业务语义：

| 字段 | 规则 |
|---|---|
| `domain` | P0 仅 `website_published_content` 或 `poll_results` |
| `configFingerprintSchemaVersion` | v1 为 `1` |
| `configFingerprint` | 32 bytes，不接受任意长度 byte array |
| `policyVersion` | 官网 `website-published-v1`；poll `poll-results-v1` |

`RetentionCompatibilityContract` 不包含 application version。retry/reconcile 从 origin operation/run 继承 compatibility contract 和 effective config；它们不得继承 origin deployment identity。

### 5.2 `RetentionDeploymentIdentity`

deployment identity 只描述发起当前 service 调用/数据库写入的真实部署：

| 字段 | 规则 |
|---|---|
| `applicationVersion` | 由当前 `DataRetentionApplicationVersionProvider` 返回，1～64 个可打印 ASCII，trim 后仍非空；公共 command 不允许调用方提供或覆盖 |

fresh run 创建时：

- run `application_version` = 当前 deployment identity；
- 返回 context 的 `runCreatedByVersion` 与 `currentWriterVersion` 均为该值。

已有 run 重放时：

- run `application_version` 是永久的创建事实，不修改，也不要求等于当前 provider；
- 返回 context 的 `runCreatedByVersion` = 数据库既有 run `application_version`；
- `currentWriterVersion` = 本次调用时由 provider 读取并冻结的当前 deployment identity；
- 两个字段可能不同，repository/writer 禁止再用一个含糊的 `applicationVersion` 代替二者。

origin operation 创建时：

- operation `created_application_version` = 实际执行 operation INSERT 的 `currentWriterVersion`；
- 因此在 run 跨部署 replay 后新建 operation 时，它可以不同于 `runCreatedByVersion`。

retry/reconcile 时：

- 新 run `application_version` = retry/reconcile 当前部署身份；
- operation `created_application_version` 永久保持 origin 值；
- website commit `committed_application_version` = 实际提交业务事务时读取并冻结的当前部署身份，不从 run 或 operation 复制。

run context 必须分别持有 compatibility contract、`runCreatedByVersion`、`currentWriterVersion`、run ID、trigger ID 和 trigger source。operation context 同理分别暴露数据库中的 `operationCreatedByVersion` 与本次调用的 `currentWriterVersion`。下游不得只传裸 `runId` 后从当前配置重算合同，不得从 origin context 复制 application version，也不得要求任意两个 version 相等。context 只在一次 service 调用链内有效；跨请求恢复必须重新读取持久化的创建版本和当前 provider。

### 5.3 `RetentionRunStartCommand`

必填：

- `triggerId`：规范化小写 UUID；
- `triggerSource`：`manual/scheduler/canary/retry/reconcile`；
- `domain`、`mode`；
- `requestedBy`；
- `requestReason`；
- compatibility contract；
- current deployment identity（由 service 在入口读取 provider 后附加到内部 validated command，不是 controller/WP5 可填写字段）；
- 显式 UTC `startedAt`。

条件字段：

- `manual/canary/retry/reconcile` 必须有非空 reason；
- execute manual/canary/retry/reconcile 必须至少有 `approvalRef` 或 `ticketRef`；最终“双审批”等权限条件由 WP4/WP5 校验，WP3 只持久化和做形状验证；
- scheduler 使用配置的 operator user ID，必须大于 0；不能用 NULL 区分“系统”；
- `scope` 只保存顶层轻量过滤条件、cursor 起点、预算和 hash，不保存业务 key 数组。

run `scope_json` 必须永久保存该 domain 的 fingerprint schema version、精确 effective config v1 key/value map 以及 canonical config bytes 的 SHA-256。它不保存密钥，也不依赖 hash 反推配置。retry/reconcile 必须从 origin run 的这份 effective config 重建 canonical bytes，再与 operation fingerprint 比较；当前部署如果已不支持旧 schema/policy，必须进入 manual review，不能拿新默认值执行旧 operation。

因此普通 retry/reconcile 顶层流程在 `startRun` 前必须先按 operation ID 读取 operation 与 origin run，构造“继承旧 compatibility contract/effective config + 当前 deployment identity”的 start command；不能先按当前默认配置创建 run，也不能复制 origin application version。该预读不改变数据库状态，正式 relation/attempt 写入仍须在 operation 行锁内复核。第 8.5 节 detail-purged audit-only reconciliation 是唯一例外：它不预先调用通用 `startRun`，而由专用端口在 operation 锁内原子创建 run/relation/audit。

WP3 对 dry-run 也统一填写 trigger/source/operator/fingerprint/policy/application version。这样相同配置下的 dry-run 与后续 execute 可比；不得为 dry-run 伪造 approval。

dry-run fingerprint 始终使用“若执行 execute 时会生效”的归一化值。比如 poll 请求 `hotRetentionDays=7` 的 simulation-only dry-run，可以在 run scope 保存 `requestedHotRetentionDays=7` 和 `simulationOnly=true`，但 fingerprint 中仍按 execute floor 写 `hotRetentionDays=14`；否则该 dry-run 不能作为 execute 配置比较证据。

### 5.4 application version 来源

新增只读配置：

```text
geo.retention.audit.application-version
GEO_RETENTION_APPLICATION_VERSION
```

部署值应为 Git commit SHA 或不可变发布版本。每次 P0 typed service 调用（包括 replay、operation/attempt writer、marker commit 和 audit-only writer）都从 provider 读取并冻结本次 `currentWriterVersion`；缺失、含非 ASCII、trim 后为空或超过 64 bytes 时必须 fail closed。不得接受上层自报版本，也不得静默回退到 Maven `0.1.0`、主机名、启动时间或 `unknown`。

测试通过构造 provider 注入固定版本。WP3 不修改 execute 开关，也不把 application version 加入 config fingerprint。

---

## 6. 标识、时间和 canonical 编码

### 6.1 UUID

- 使用标准带连字符 UUID 文本；
- 写库前执行 `UUID.fromString` 后以 `toString()` 输出小写规范形式；
- 拒绝无连字符、花括号、前后空格和非规范大小写输入；内部新 ID 使用 `UUID.randomUUID().toString()`；
- trigger ID 可由 WP5 创建并跨 domain 复用；operation ID 只由 WP3 operation service 创建；调用方不得自行指定 origin operation ID。

### 6.2 UTC

- 生产内部类型优先使用 `Instant`；
- 写 MySQL `DATETIME(3)` 前截断而非四舍五入到毫秒，再转 `UTC` 的 `LocalDateTime`；
- 所有 P0 INSERT 显式写 `created_at/updated_at`，所有 UPDATE 显式写 `updated_at`，避免当前 JDBC `serverTimezone=Asia/Shanghai` 和 `ON UPDATE CURRENT_TIMESTAMP` 产生本地墙上时间；
- hash instant 固定 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`；
- 测试注入 `Clock`，禁止直接散落 `LocalDateTime.now()` 或 `Instant.now()`；
- 数据库资格比较使用 `UTC_TIMESTAMP(3)`，不得混用 `NOW()`。

### 6.3 fingerprint v1

WP3 必须逐字实现 WP2 第 5.4 节：

- website 默认向量 SHA-256：`5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd`；
- poll 默认向量 SHA-256：`a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c`。

生产 encoder 返回 canonical UTF-8 bytes 和 32-byte digest。测试必须从 SPEC 固定 bytes 独立计算预期 digest，不得调用生产 encoder 生成 expected value。

### 6.4 operation scope hash v1

- website 固定向量：`14a67e69ebea79dea3c01abef4cd982c8fcb22303ec519941e7f319c633abbe7`；
- poll 固定向量：`2d6d6c4ccf5609e29a14773efc97125dcd7746e192be7bc1f163b274c06eedad`。

website encoder：

- article ID 必须正数、去重、数值升序；
- `itemCount` 必须等于列表大小且为 1～20；
- `upperBound` 必须等于本次选择快照允许纳入的最大 ID，且不得小于列表最大 ID；
- cutoff 为 UTC instant；
- 自动执行只允许 5/10/20 的约束由 WP6 实施，WP3 只执行数据库通用 1～20 边界。

poll encoder：

- project ID 必须正数；
- question tier 必须 trim 后用 `Locale.ROOT` 转大写并满足数据库长度；
- batch date/cutoff 使用 ISO date；
- upper bound 行必须为 `<null>`。

任一 canonical 字段变化都必须产生不同 hash。不能对整段文本 trim，最后一行保留 LF。

---

## 7. run 生命周期

### 7.1 创建时机

WP5 最终调用顺序冻结为：校验来源/审批和静态开关 → 获取 execute lease → lease 后重读动态开关 → `startRun`。因此 lease busy 不得创建 `running` run。需要记录 lease busy 时，由 WP5 创建 `skipped` run 或 operationless `lease_busy` attempt 的具体选择在 WP5 SPEC 冻结；WP3 只提供受约束原语。

一次普通顶层 domain 请求只调用一次 `startRun`。领域分页方法接收 `RetentionRunContext`，禁止自行 `startRun`。detail-purged audit-only reconciliation 不调用公开 `startRun`，其专用 writer 仍只创建一条 run，具体见第 8.5 节。

### 7.2 幂等创建

`(trigger_id,domain)` 是数据库唯一键。`startRun` 使用独立短事务：

1. 校验 command 和 JSON；
2. 尝试 INSERT；
3. 唯一冲突时读取已有行；
4. 只有 mode、source、operator、reason/refs、scope、fingerprint/schema version 和 policy 等请求语义/compatibility contract 完全一致时，作为同一请求重放返回已有 context；`startedAt` 与 application version 都是首次创建事实，不参与重放 command 相等比较，分别原样返回数据库 `started_at/runCreatedByVersion`；不得把当前 provider version 与既有 run `application_version` 比较；
5. 任一上述参与 replay 的请求语义/compatibility 字段不同，抛出 `RETENTION_RUN_IDEMPOTENCY_CONFLICT`；既有 P0 run 的 `application_version` 若为空或格式非法则按审计数据损坏 fail closed，但合法版本与当前版本不同不是冲突；
6. replay context 原样携带数据库中的 `runCreatedByVersion`，并另行携带本次 provider 的 `currentWriterVersion`；
7. version 不同的 replay 仍必须确认当前部署能够解析并执行既有 fingerprint schema/policy/effective config；不支持时以 `RETENTION_COMPATIBILITY_CONTRACT_UNSUPPORTED` fail closed 或交由人工处理，不修改 run，也不能伪装成 version idempotency conflict；
8. 已存在且仍 running 时返回 `RUNNING_REPLAY`，由 WP5 先读取既有 attempts/operations 决定恢复动作，不能直接重新执行首页；
9. 已完成 run 返回 `TERMINAL_REPLAY`，可以读取既有结果，但不得重开或再次写 attempt。

run 创建事务必须在返回前提交。不得让 run INSERT 与首个业务 mutation 共用一个长事务。

### 7.3 完成与 rollup

`finishRun(runId)` 不接受调用方自报 attempt 计数。它在一个事务中：

1. `SELECT data_retention_run ... FOR UPDATE`；
2. 验证状态仍为 `running`、`finished_at` 和 `attempt_rollup_completed_at` 均为空；
3. 从 `data_retention_batch_attempt` 按 run 聚合；
4. 若仍有 `prepared/running` attempt，拒绝结束；
5. 写五个 attempt 计数、业务兼容计数、最终 status、`finished_at` 和 `attempt_rollup_completed_at`；
6. guarded UPDATE 必须恰好一行。

attempt 计数映射：

- succeeded：`status='succeeded'`；
- failed：`status='failed'`；
- skipped：`status='skipped'`；
- unknown：`status IN ('unknown','manual_review')`；
- attempt_count：全部 attempt，包括 operationless page/lease_busy。

run status 优先级：

1. 任一 `unknown/manual_review/failed` → `failed`；
2. 无失败且至少一个 succeeded → `succeeded`；
3. 零 attempt 且满足第 8.5 节完整 audit-only reconcile relation/resolution 形状 → `succeeded`；
4. 全部为 skipped，或合法的普通零 attempt 顶层请求 → `skipped`；这里的普通零 attempt 仅限 dry-run/预算/开关安全停止，不含 retry/reconcile；
5. 其他组合拒绝完成。

`candidate_count/affected_count/skipped_count/warning_count` 是兼容汇总，不得与 attempt rollup 矛盾。核心 attempt 计数永远由数据库聚合；领域扩展计数放入受限 metrics。run 完成后不得再次 finish、追加 attempt 或改写 rollup。

`finishRun` 可接受强类型 `RunBusinessSummary` 填写四个兼容业务计数，但不能接受五个 attempt rollup 数。业务计数必须非负，并与 typed attempt result 中已持久化的核心计数完成可解释校验；无法校验或矛盾时拒绝 finish，不能用调用方数值覆盖数据库事实。

audit-only 形状校验不得在已经持有 run 锁时再锁 operation。`finishRun` 持有 run 锁后，仅以普通一致性读取核验永久的 operation-run relation、resolved reconciliation 和 `detail_purged_at`；若并发 audit-only writer 尚未提交而读不到完整形状，本次 finish 拒绝并允许稍后重试。它不得用 `SELECT operation ... FOR UPDATE` 形成 `run → operation` 锁序。

dry-run 的分页只记录 operationless `page` attempt；不会创建 operation。零候选 dry-run 或在 run 创建后因预算/开关安全停止的合法请求可以零 attempt，并以 `skipped` 结束。

lease 首次获取 busy 是特殊的 pre-run 事件：manual/canary 返回冲突且不写 run；scheduler 可调用 `recordSkippedRun` 直接插入一条已完成的 `skipped` run，五个 rollup 为 0、`finished_at/attempt_rollup_completed_at` 同时设置、reason=`lease_busy`，过程中不得短暂产生 `running` 行。`lease_busy` attempt 只用于 run 已创建后的受控停止，不用于首次 lease 获取失败。

---

## 8. operation 与 operation-run 生命周期

### 8.1 origin 创建

`prepareWebsiteOrigin/preparePollOrigin` 的首次创建使用一个独立短事务，按以下顺序：

1. 锁定并验证 run 仍为 `running`，compatibility contract 与 command 一致；校验 context 的 `runCreatedByVersion` 等于数据库 run 创建版本、`currentWriterVersion` 等于本次调用冻结的 provider 值，但不要求两者相等；
2. 生成 operation ID；
3. 计算 canonical scope hash；
4. INSERT operation，初始 `outcome_status='pending'`；
5. INSERT `relation_type='origin'` 且 run ID 等于 `origin_run_group_id`；
6. 创建 first attempt；
7. website 按 article ID 数值升序写 batch item，`first_attempt_id` 指向该 attempt；poll 不写 batch item；
8. 事务内重新查询 origin relation 恰好一条且 run 匹配；
9. 提交后返回 operation/attempt context。

如果第 4 步 INSERT 因 `(domain,batch_key_hash)` 唯一约束冲突：

1. 当前首次创建事务必须立即标记 rollback-only 并完整回滚，释放 run 锁；
2. 禁止在该事务继续查询、锁定或验证 existing operation/tree；
3. 从唯一约束冲突中只提取稳定错误分类，不把当前事务的快照用于恢复判断；
4. 开启全新的 recovery transaction，先按 `(domain,batch_key_hash)` `SELECT existing operation ... FOR UPDATE`，再锁 current run，随后按 attempt→batch item 顺序锁定并验证 tree；
5. recovery transaction 不补齐半套数据，不新建 operation/relation/attempt/item，只能返回 replay 或 conflict。

第二阶段 scope 恢复规则：

- existing operation 的 origin run 等于当前 run，canonical command/contract 完全一致，origin relation、first attempt 和 website items（如适用）完整且一致时，返回同一个 context 并标记 `PREPARE_REPLAY`；
- 上述 replay 比较不包含当前 `currentWriterVersion` 与既有 `created_application_version` 的相等性；返回 context 的 `operationCreatedByVersion` 使用数据库原值，当前 writer 仅作为本次恢复者事实；
- 同一 origin run 但只存在半套 tree，说明事务边界或人工数据异常，fail closed，不在应用层补齐；
- existing operation 属于其他 origin run，或任一 scope/contract/detail 不同，返回 `RETENTION_OPERATION_SCOPE_CONFLICT`；
- 已 committed/finalized 的 operation 即使恰好属于当前 run，也只能作为 terminal replay 返回，不允许再次 mutation。

冲突异常可包含已有 operation ID、outcome 和 marker-present 布尔值，但不得包含文章标题、正文、payload 或大对象列表。任何实现都不得在仍持有 current run 锁时进入 existing-operation recovery path。

### 8.2 retry/reconcile 关联

`prepareRelatedAttempt` 只接受显式 operation ID，并在一个独立短事务内完成以下全部步骤：

1. `SELECT operation ... FOR UPDATE`；
2. 检查 `detail_purged_at IS NULL`；
3. 检查当前 run compatibility contract 与 operation 的 domain/fingerprint/schema version/policy 完全一致；确认 `runCreatedByVersion` 与数据库 run 创建版本一致、`currentWriterVersion` 与本次 provider 冻结值一致，但不要求二者相等，也不要求当前 writer 等于 origin；
4. 从 origin run 的永久 `scope_json.effectiveConfig` 读取并重算 fingerprint；必须与 operation 和当前 run 相等，禁止只复制旧 digest 却按当前部署默认配置执行；
5. retry 只接受 `relation_type=retry` 且 trigger source=retry；reconcile 只接受 reconcile source/relation，并按 reconciliation 状态机校验；
6. retry 根据锁内状态分支：`rolled_back` 进入 fresh prepare；`pending` 只进入 replay consistency check；其他状态拒绝；
7. fresh retry 执行 guarded `rolled_back → pending`，谓词必须同时包含 `detail_purged_at IS NULL`；恰好更新一行；
8. fresh retry 创建 operation-run relation；
9. fresh retry 创建 prepared attempt，并按 expected-latest 规则分配编号；
10. fresh/replay 都验证 operation=`pending`、relation=`retry`、attempt identity/type/status/contract 三者一致；
11. 一次提交；不复制 website batch item；application version 可以与 origin run 不同。

第 7～10 步任何失败或故障都整体回滚，不允许留下 `rolled_back + retry relation/attempt`，也不允许留下 `pending` 而无 relation/attempt。

retry 幂等重放在 operation 锁内按以下规则：

- operation=`rolled_back` 且当前 retry run 下 relation/attempt 均不存在：执行完整首次事务；历史 retry run 的永久 relation/attempt 不影响本次 fresh prepare；
- operation=`pending` 且同一 retry run 的 relation 和相同 `attemptRequestId` attempt 均存在、合同/状态完全一致：返回已有 context，不再次更新或插入；
- operation=`rolled_back` 但 relation 或 attempt 已存在，或 operation=`pending` 但二者缺失/错配：fail closed，返回 `RETENTION_RETRY_PREPARE_INCONSISTENT`；
- operation 为其他状态：按 unresolved/finalized/state conflict 拒绝。

reconcile relation/attempt 的常规 detail 未清理路径仍在同一 operation-locked 事务创建，但不执行 `rolled_back → pending`；它根据 operation 的 unknown/manual_review/final 状态决定只读取证或 resolution 能力。

retry/reconcile 不改变 `origin_run_group_id`。普通 manual/canary/scheduler run 不得创建 retry/reconcile relation。

### 8.3 operation outcome 状态机

允许转换：

```text
pending -> committed
pending -> rolled_back
pending -> unknown
pending -> manual_review
rolled_back -> pending          (准备已知安全 retry)
unknown -> committed            (reconciliation)
unknown -> rolled_back          (safe_to_retry resolution)
unknown -> manual_review
unknown -> closed_no_mutation
manual_review -> committed      (人工 resolution)
manual_review -> rolled_back
manual_review -> closed_no_mutation
```

`committed/closed_no_mutation` 是最终结果，不允许离开。`rolled_back` 不是最终化，不设置 outcome finalization 或 retention anchor。

`safe_to_retry` resolution 把 operation 置为 `rolled_back`；真正开始 retry 时再置 `pending`。unknown/manual review 未解决时不得准备 mutation retry。

### 8.4 finalization 和 anchor

commit 或 resolution 更新 operation 前必须持有 operation 行锁。

首次进入最终状态时：

- `outcome_finalized_at = eventTime`；
- `detail_retention_anchor_at = eventTime`；
- source/reason 使用 WP2 允许值；
- event time 必须为显式 UTC 毫秒时间。

最终化后补写一致的终态 attempt 或 resolution 时，只能执行：

```text
detail_retention_anchor_at = max(current_anchor, new_event_time)
```

但必须同时满足 `detail_purged_at IS NULL`。新事件时间早于现值时保留现值；事件时间明显晚于当前数据库 UTC 的容忍策略冻结为拒绝超过 5 分钟的值，避免错误时钟把保留期无限推后。该 5 分钟只用于输入合法性，不允许把事件时间回写为本地当前时间。

`detail_purged_at IS NOT NULL` 后，audit-only reconciliation 由 WP8 业务契约调用：WP3 提供“无 attempt 的 audit-only reconcile run + permanent operation-run relation + resolved reconciliation”端口。该 relation 是第 3.13 节禁止 purge 后新 relation 的唯一例外；必须验证结论与现有 final outcome 一致，且绝不更新 operation、anchor/detail marker 或创建 attempt/item。

### 8.5 reconciliation 审计原语

WP3 只冻结和持久化调用方已经完成的取证结果，不负责扫描业务表、决定结论或判断领域 evidence 是否满足某一业务 canonical schema。

detail 未清理时的 `prepareReconciliation`：

1. 校验 reconciliation ID、run context、operation ID、marker/business state、decision、evidence hash/JSON 和 operator；
2. 先锁 operation，确认 reconcile run/relation 合法且 detail 未清理；
3. 对未最终结论写 `status='pending'`；对机器可确定且调用方要求同事务 resolution 的场景仍先写行再按下述 resolve；
4. 相同 reconciliation ID 且完整 command 相同为幂等重放，不同则冲突；
5. 不修改原 unknown/manual-review attempt。

`resolveReconciliation` 在同一事务：

1. 先锁 operation，再锁 reconciliation 行；
2. 只允许 pending→resolved；完全相同的 resolved 重放返回已有行；
3. `committed` 必须核验永久 marker 存在并与 operation scope 相等，再最终化/推进 anchor；
4. `closed_no_mutation` 必须由业务取证方提供稳定 reason 和 evidence hash，再最终化/推进 anchor；
5. `safe_to_retry` 只把 operation 置为 rolled_back，不设置 finalization/anchor；
6. `manual_review/automation_stopped` 在没有最终业务结论时继续让 operation 保持 unknown/manual_review；即使审计行 resolved，也不能启动 365 天计时；
7. resolved by/time、reason 和 operation update 原子提交。

`detail_purged_at IS NOT NULL` 时只允许专用 `recordAuditOnlyReconciliation`：

1. WP5 调用专用端口并传入 trigger source=`reconcile` 的 run start identity、operation ID 和 reconciliation command；不得预先调用通用 `startRun` 创建 audit-only run；
2. audit-only writer 开启短事务，先 `SELECT operation ... FOR UPDATE`，确认 `detail_purged_at IS NOT NULL`、outcome 已最终化且永久 marker/resolution 与 command 结论一致；
3. 在 operation 锁内按 `(trigger_id,domain)` 查询并锁定 run：不存在时以 operation 的 compatibility contract/effective config 和本次 provider 的 `currentWriterVersion` INSERT 新 running reconcile run；已存在时必须逐项验证相同请求语义/compatibility contract/operation identity，但不得要求当前 writer version 等于既有 run `application_version`；replay context 同样分别返回 `runCreatedByVersion/currentWriterVersion`；相同的 running run 进入恢复校验，相同的 succeeded run 只返回 terminal replay，failed/skipped run 不得追加审计；
4. 确认该 run 尚无 attempt；fresh path INSERT `data_retention_operation_run`，`relation_type='reconcile'`。这是 purge 后允许创建 relation 的唯一例外；该 relation 永久保留且永不承载 attempt；
5. fresh path INSERT 一条直接 `status='resolved'` 的 reconciliation，绑定新 run/relation；
6. 不创建 attempt，不更新 operation outcome/finalization/anchor/detail marker，不读取或声称恢复已删除 detail；
7. 验证 run、relation 和 reconciliation 均存在且 identity/decision 一致后提交；
8. 随后 `finishRun` 依据下述 audit-only rollup 结束该 run。

同一 transaction 任一步失败必须整体回滚新 run、relation 和 reconciliation，不得遗留无 detail 的空 running run。相同 trigger/reconciliation ID/operation/command 重放时三者完整一致才返回已有结果；只有 run、仅有 relation 或仅有 reconciliation、relation 指向其他 run/type、结论不一致均 fail closed。即使 operation 从未发生过 reconcile，该路径也必须可达，不能要求复用 origin/retry relation。

audit-only run 不伪造 attempt。`finishRun` 对“零 attempt + 恰好一条本 run resolved audit-only reconciliation + relation_type=reconcile + operation 已 detail-purged”的合法形状写 `status='succeeded'`，五个 attempt rollup 仍为 0；其他零 attempt retry/reconcile run 必须拒绝 finish，不能降级成普通 `skipped`。冲突证据使整个专用事务回滚，并由上层记录 P0 人工事件。

WP3 的 evidence 边界固定为领域不透明的 `EvidenceDocument`：

- `ReconciliationPrepareCommand`、`ReconciliationResolutionCommand` 和 `AuditOnlyReconciliationCommand` 只能通过同一 `EvidenceDocument` 类型携带 evidence，不得各自接受任意 JSON/string/map；
- 调用方提供非空 `evidenceUtf8JsonBytes` 和 32-byte `sha256Digest`；WP8 可保证这些 bytes 符合其领域 canonical schema，但 WP3 只重新计算 `SHA-256(evidenceUtf8JsonBytes)` 并要求逐 byte 相等；
- bytes 必须无 BOM、是严格 UTF-8、可解析为单个 JSON object，不允许重复 key，并满足 8192-byte 原始输入限制、深度、key 数和敏感 key denylist；敏感 key 检查大小写不敏感并递归所有层级。WP3 可以为这些通用安全检查遍历 JSON，但不得解释领域字段和值；
- WP3 不排序、不补默认值、不改变数字/日期/枚举表示，也不宣称输入已经 canonical；是否 canonical、领域 schema/version、固定输入输出向量均由 WP8 生产者负责；
- `evidence_hash` 保存经复算的 digest，`evidence_json` 保存解析后的诊断副本。由于 MySQL JSON 不保留原始空白/成员顺序，幂等 command identity 对 evidence 内容使用 digest，不从 `evidence_json` 反推原始 bytes；
- 原始 `evidenceUtf8JsonBytes` 长度与 MySQL `OCTET_LENGTH(CAST(evidence_json AS CHAR))` 是两个独立限制，二者都必须 ≤8192；不得假设 MySQL JSON 的规范化表示与输入 bytes 等长。原始 bytes 超限必须在 INSERT 前拒绝；存储表示超限必须由 V354 CHECK 使整个事务回滚并映射为稳定 evidence-invalid 错误；
- evidence 校验、reconciliation INSERT/UPDATE 和 operation 状态联动仍按本节事务原子提交。

上述 opaque evidence writer、digest/安全边界和事务测试全部通过即可完成 WP3 reconciliation 子项；WP8 领域 evidence schema 尚未批准不阻断 WP3 代码完成。WP8 接线前必须另行批准领域 schema/向量，且不得把未经其验证的 bytes 交给 WP3 后即宣称业务取证成立。

---

## 9. attempt 生命周期与并发编号

### 9.1 状态机

允许转换：

```text
prepared -> running
prepared -> skipped
running  -> succeeded
running  -> failed
running  -> unknown
running  -> manual_review
```

终态为 `succeeded/failed/skipped/unknown/manual_review`。终态行的 status、finished time、operation/run identity、type、scope 和 parent snapshot 均不可修改。重复提交完全相同终态可作为幂等重放返回；不同终态或不同时间/metrics 返回冲突。

终态重放比较 status、canonical metrics、稳定错误码/净化后错误和业务结果，不比较本次重放请求新生成的“当前时间”。已有 `finished_at` 是权威值并原样返回；重放不得二次推进 operation anchor。调用方如传入显式原事件时间，则该时间与已存值不等时仍属于冲突。

`started_at` 在进入 running 时设置；`finished_at` 仅终态设置。prepared/running 不允许 finished time，终态必须有 finished time。

### 9.2 attempt type

Java 常量冻结为：

```text
page
normal_batch
split
retry
canary
reconcile
lease_busy
```

mutation attempt 必须有 operation ID；operationless 只允许 `page/lease_busy`。`parent_attempt_id` 仅作为短期 lineage snapshot，必须引用调用方已读取的真实 attempt ID，但不依赖数据库 FK。

### 9.3 sequence 和 attempt number

- `sequence_no` 在 run 内从 1 严格递增；
- `attempt_no` 在 operation 全生命周期跨 run 从 1 严格递增；
- operationless page/lease_busy 的 `attempt_no` 固定等于其 run `sequence_no`，满足非空和下界 CHECK；它不承担跨 run 含义；
- 不允许调用方自行指定最终编号。

mutation attempt 使用 `attemptRequestId + expectedLatestAttemptId` 保证创建幂等，同时单独保存 lineage：

- first attempt 的 `expectedLatestAttemptId` 为空，只能由 origin 准备事务创建；split child operation 可以把原大批次 attempt 作为 `lineageParentAttemptId`；
- 后续 attempt 必须提交调用方最后观察到的 `expectedLatestAttemptId`；retry/reconcile 通常也将它作为 lineage parent，但字段仍显式分开；
- operation 锁内若已存在相同 `attemptRequestId`，且 run/type/canonical scope/lineage 完全一致，则直接返回该 attempt；任一字段不同则 idempotency conflict；
- operation 锁内若当前最新 attempt ID 等于 expected latest，则创建下一条；数据库 `parent_attempt_id` 写 command 的 lineage parent，而不是隐式写 CAS 值；
- 其他情况均为 stale writer 或语义冲突，拒绝创建；下一次有意重试必须先读取最新 attempt，并以它作为新的 expected latest，同时生成新的 request ID。

operationless page attempt 使用 run 内同样的 request ID/expected-latest 规则：第一页 expected latest 为空；后续页以该 run 最新 operationless page attempt 为 expected latest。相同 request ID 的第一页重放返回已有行，不创建第二行；不同 request ID 且 expected latest 为空属于 stale writer。

实际分配时遵循：

1. operation-bound writer 先锁 operation；
2. 锁必要的 reconciliation/failure gate；
3. 再锁 run 行用于 run sequence 分配；
4. 从数据库计算下一 `sequence_no`；
5. 在 operation 锁内计算下一 `attempt_no`；
6. INSERT attempt。

这在 WP2 的相对锁序 `operation → gate → attempt → batch item` 中只插入 run sequence allocator，不反转任何既有父子锁。operationless page/lease_busy 只锁 run。run completion 只锁 run 后读取 attempt，不再锁 operation，因此不得形成 run→operation 反向路径。

同一 run 的 attempt writer 在 P0 中必须串行；唯一键冲突被视为并发契约违规并 fail closed，不通过无界重试掩盖。

`markAttemptRunning/completeAttempt` 不能先锁 attempt 再回头锁 operation。实现先以非锁定读取得 identity；若 operation-bound，则先 `SELECT operation ... FOR UPDATE`，再以 attempt 主键 locking read 并复核 identity；operationless attempt 只锁 run 后锁 attempt。所有终态更新都使用旧 status 的 guarded predicate。

### 9.4 终态和 operation 联动

- succeeded 且永久 marker 已在同一事务写入：operation 必须在该事务 committed finalization；
- known rollback 的 failed：operation 由 pending 变为 rolled_back，不设置 anchor；
- commit outcome 不可确认：attempt 变 unknown，operation 变 unknown；
- 需要人工判断：attempt 变 manual_review，operation 变 manual_review；
- skipped 是否最终化为 closed_no_mutation 由 WP6/WP7 提供经过验证的原因；WP3 不把普通 skipped 自动解释为最终结果。

attempt 的 succeeded 与 marker 最好在业务事务内共同写入。若领域实现只能在提交后补写终态，WP3 必须先核验永久 marker，再写 attempt 并按第 8.4 节推进 anchor；绝不能仅凭调用成功响应写 succeeded。

---

## 10. website batch item 契约

1. 只在 website origin 准备事务写入；
2. 数值升序、去重，sequence 从 1 开始；
3. 数量必须等于 operation item count 和 canonical input；
4. retry/reconcile 只读原 item，不追加、不替换；
5. mutation 前重读 item 后必须重算 hash 并与 operation 相等；
6. scope/metrics JSON 不重复保存 article ID 数组；
7. operation `detail_purged_at` 非空后读取返回“detail purged”语义，不伪造空批次；
8. WP3 不保存标题、URL、正文、payload、状态快照或其他业务数据。

---

## 11. transaction-bound commit marker

### 11.1 website marker

WP6 的单个业务事务最终必须按以下相对顺序：

1. 已持有 operation 行锁和领域业务行锁；
2. 重读 operation/items，重算并校验 scope hash；
3. 执行正文/payload/raw response mutation；
4. 执行锁定行范围内的后置校验；
5. INSERT `data_retention_batch_commit`，写实际 `committed_application_version`；
6. guarded UPDATE operation 为 committed，初始化 finalization/anchor；
7. 将当前 attempt 更新为 succeeded；
8. 一次提交。

marker writer 必须验证 command 与 operation 的完整 website 复合合同一致。commit 唯一冲突不作为本次成功：它表示上层 preflight 被绕过或并发执行，必须抛 `RETENTION_OPERATION_ALREADY_COMMITTED` 并回滚当前事务。

### 11.2 poll marker

WP7 的单切片业务事务最终必须：

1. 持有 operation 和切片业务锁；
2. 重算 poll scope hash并校验 tuple/cutoff；
3. 执行既有子表→主表删除和后置校验；
4. INSERT `data_retention_purged_slice`，同时写 domain/project/date/tier/run/operation/metrics；
5. guarded UPDATE operation 为 committed，source=`poll_marker`；
6. 将 attempt 更新为 succeeded；
7. 一次提交。

原 `retention_run_id` FK 必须继续满足。marker tuple 必须与 operation 的规范化 poll tuple 完全相等。

### 11.3 `poll_audit_purge_runs` 兼容

现有 `PollAuditPurgeAuditWriter` 使用 `REQUIRES_NEW` 保存 PREPARED/RUNNING/SUCCEEDED/FAILED 审计。WP3 不改变其既有业务语义，但新增 typed identity port：

- 新 P0 poll 调用在 PREPARED 行创建时填写 `retention_run_id/operation_id/retention_domain` 三列；
- 三列必须全空或全非空，新 P0 路径必须全非空；
- identity 一经写入不可修改；
- SUCCEEDED 只能在永久 purged-slice marker 存在且 tuple 匹配后写；
- 连接结果未知时不得直接写 FAILED，应由 WP7/WP8 进入 unknown/reconciliation 流程；
- legacy 非 P0 行仍允许三列全 NULL。

由于 `poll_audit_purge_runs` 是永久审计，它不得保存或引用可清理 attempt ID。

---

## 12. 事务、锁序和传播

### 12.1 事务分类

| 操作 | 事务要求 |
|---|---|
| start/finish run | 独立短事务，返回前提交 |
| prepare origin + relation + first attempt + item | 一个独立短事务 |
| prepare retry relation + rolled_back→pending + attempt | 一个 operation-locked 独立短事务，三者原子 |
| prepare regular reconcile relation + attempt | 一个 operation-locked 独立短事务，不改变 rolled_back→pending |
| mark running | 独立短事务，可幂等 |
| known rollback/unknown/manual-review terminal audit | 独立短事务，锁 operation |
| reconciliation prepare/resolve | 独立短事务，锁 operation 后锁 reconciliation |
| purge 后 audit-only run + reconcile relation + resolved audit | 一个 operation→run 独立短事务；专用端口原子创建，不预先 startRun，不创建 attempt、不改 operation |
| website/poll committed marker | 必须加入领域业务事务，禁止新事务 |
| run rollup | 独立短事务 |

### 12.2 锁顺序

operation-bound writer 的完整顺序：

```text
operation
→ reconciliation/failure gate（若需要）
→ run sequence allocator（若创建 attempt）
→ attempt
→ batch item
→ domain business rows（由 WP6/WP7 冻结其内部顺序）
```

origin fresh-create 是受控例外：operation 行在事务开始时尚不存在，因此只允许先锁 current run 再尝试 INSERT 新 operation。只要 INSERT 冲突，该事务必须按第 8.1 节立即整体回滚；任何涉及 existing operation 的恢复都在新事务按 operation→run 顺序执行。禁止把 fresh-create 的 run→new-row 顺序外推到 existing-operation writer。

purge 后 audit-only reconciliation 使用 operation→reconcile run→reconciliation 的顺序，不锁 attempt/item；它与其他 existing-operation writer 保持相同前缀。

WP3 不负责锁领域业务行，但 marker port 调用前必须已经锁定 operation。repository 可通过 locking read context token 或同事务再执行 `SELECT ... FOR UPDATE` 证明这一点；不得依赖普通快照读。

禁止：

- 先锁 attempt/item 再锁 operation；
- marker writer 内部开启 `REQUIRES_NEW`；
- 在 run finalization 中逐个锁 operation；
- 持有长业务事务时调用 startRun 或创建 origin；
- 捕获数据库冲突后继续提交业务 mutation。

---

## 13. JSON、错误与敏感数据

### 13.1 字节限制

- run/attempt `scope_json` ≤ 8192 UTF-8 bytes；
- run/attempt `metrics_json` ≤ 8192 UTF-8 bytes；
- reconciliation 原始 evidence 输入 ≤8192 UTF-8 bytes，且 MySQL `CAST(evidence_json AS CHAR)` 的存储表示也必须 ≤8192 bytes；两种长度分别验收；
- `last_error/error_message` ≤ 2048 UTF-8 bytes；
- reason ≤ 数据库列长且不得通过字符截断破坏 Unicode surrogate pair。

实现统一 `truncateUtf8(value,maxBytes)`，按 Unicode code point 截断。不能使用 `substring(0,2000)` 代替 2 KB。

### 13.2 JSON 形状

WP3 自己生成的 run/attempt scope 与 metrics 只允许轻量标量、标量数组以外的有限计数 map；禁止业务对象数组。第 8.5 节 caller-supplied evidence 是受 `EvidenceDocument` 包装的唯一 opaque JSON 例外，但仍受大小、深度、key 数、object 顶层和敏感字段约束。具体规则：

- 最大嵌套深度 3；
- 每个 object 最大 64 keys；
- run/scope/metrics 不接受任意 POJO 序列化，只接受审核过的 typed DTO/map；evidence 只接受原始 UTF-8 bytes + digest，不接受先反序列化为任意 POJO 后再序列化；
- WP3 生成的 run/scope/metrics key 稳定排序；evidence 不排序、不重写 caller bytes，`evidence_json` 仅是数据库诊断副本；
- 使用 key denylist 拒绝 `cookie/authorization/token/secret/password/credential/payload/prompt/rawResponse` 等大小写变体；值仍由 typed DTO 限制，不能把 key 扫描当作完整脱敏；
- 超限 metrics 保留核心计数和 hash，添加 `metrics_truncated=true`；scope/evidence 超限直接拒绝；
- marker 没有“截断后继续提交”路径。

### 13.3 禁止内容

任何 audit JSON、reason 或 error 中都不得保存：

- 正文、prompt、distribution payload、raw response；
- Cookie、Authorization、access/refresh token、secret、数据库或对象存储凭据；
- article ID、poll result ID 等大数组；
- 完整 SQL 参数、HTTP body 或堆栈。

日志只输出 trigger/run/operation/attempt ID、domain、稳定错误码和计数。数据库异常原文在返回和日志前必须经过敏感信息清理。

---

## 14. 稳定错误语义

至少冻结以下内部错误码；HTTP 映射由 WP5 决定：

```text
RETENTION_AUDIT_CONTEXT_INVALID
RETENTION_AUDIT_JSON_TOO_LARGE
RETENTION_APPLICATION_VERSION_MISSING
RETENTION_APPLICATION_VERSION_INVALID
RETENTION_COMPATIBILITY_CONTRACT_UNSUPPORTED
RETENTION_RUN_IDEMPOTENCY_CONFLICT
RETENTION_RUN_ALREADY_FINISHED
RETENTION_RUN_HAS_NON_TERMINAL_ATTEMPT
RETENTION_OPERATION_SCOPE_CONFLICT
RETENTION_OPERATION_CONTRACT_MISMATCH
RETENTION_OPERATION_ALREADY_FINALIZED
RETENTION_OPERATION_DETAIL_PURGED
RETENTION_OPERATION_ALREADY_COMMITTED
RETENTION_OPERATION_UNRESOLVED
RETENTION_RETRY_PREPARE_INCONSISTENT
RETENTION_RECONCILIATION_CONFLICT
RETENTION_RECONCILIATION_EVIDENCE_INVALID
RETENTION_ATTEMPT_STATE_CONFLICT
RETENTION_ATTEMPT_SEQUENCE_CONFLICT
RETENTION_MARKER_TRANSACTION_REQUIRED
RETENTION_MARKER_CONTRACT_MISMATCH
RETENTION_AUDIT_CONCURRENT_MODIFICATION
```

错误分类不得依赖解析 MySQL 英文 message。repository 识别 SQLState/constraint name，再转稳定内部错误；未知数据库错误 fail closed。

---

## 15. legacy facade 迁移策略

1. 保留 `DataRetentionRunAuditService.startRun/finishRun` 的源兼容签名；
2. 在类和方法上标记明确的 legacy 注释；本期是否 Java `@Deprecated` 由实现评审决定，不能造成无关编译失败；
3. legacy writer 不向历史行回填或伪造 V354 新字段；
4. legacy JSON/error 至少复用 WP3 UTF-8 大小工具，避免继续产生超大新行；
5. `WebsitePublishedContentCleanupService`、`PollRetentionDryRunService` 的 execute 接线只在 WP6/WP7 改造；WP3 不在本期让现有 execute 路径半接入新审计模型；
6. 架构测试维护“P0 typed 包不得依赖 legacy facade”的反向规则；
7. WP3 完成不表示旧 controller/scheduler 已遵守“一次顶层请求一条 run”，该验收属于 WP5～WP7 集成完成条件。

---

## 16. 预计文件变更（开发阶段）

本轮仅写 SPEC。评审通过后的 WP3 预计变更：

```text
geo-server/src/main/java/com/huanjing/geo/module/retention/audit/**
geo-server/src/main/java/com/huanjing/geo/module/retention/service/DataRetentionRunAuditService.java
geo-server/src/main/java/com/huanjing/geo/module/dispatch/entity/PollAuditPurgeRun.java
geo-server/src/main/java/com/huanjing/geo/module/dispatch/websearch/purge/PollAuditPurgeAuditWriter.java
geo-server/src/test/java/com/huanjing/geo/module/retention/audit/**
geo-server/pom.xml                         （仅在 required MySQL profile 需要纳入新测试时）
```

默认不修改：

```text
V354__data_retention_execute_governance.sql
V355__retire_douyin_oauth_and_align_login_validity_reference.sql
V356__track_latest_self_media_login_check.sql
V357__clear_obsolete_platform_account_id_warnings.sql
DataRetentionController.java
DataRetentionScheduler.java
WebsitePublishedContentCleanupService.java
PollRetentionDryRunService.java
application*.yml 中的 execute 开关
```

如需修改上述默认不改文件，必须先说明原因并确认仍属于 WP3；任何 DDL 变化重新评审。

---

## 17. 测试 SPEC

### 17.1 纯单元测试

至少覆盖：

1. website/poll fingerprint 固定 UTF-8/LF bytes 和 SHA-256；
2. website/poll scope hash 固定 bytes 和 SHA-256；
3. 最后一行 LF、空值、字段顺序、大小写、时区、毫秒精度改变会改变或拒绝输入；
4. website ID 排序、去重、前导零不可出现、itemCount/upperBound 校验；
5. poll tier `Locale.ROOT` 规范化，使用土耳其语默认 locale 时结果仍一致；
6. UUID 规范化和非法形式拒绝；
7. application version 缺失/非 ASCII/超长 fail closed；
8. UTF-8 截断覆盖 ASCII、中文、emoji 边界且不产生非法字符串；
9. JSON 8 KB、深度、key 数、重复 key、BOM 和递归敏感内容规则；
10. run/operation/attempt 状态机合法和非法转换；
11. UTC clock 和毫秒截断。

### 17.2 repository/SQL 契约测试

至少断言：

- P0 INSERT/UPDATE 显式写 UTC 时间字段；
- marker port 没有 `REQUIRES_NEW`，并检查活动事务；
- commit SQL 覆盖完整 operation contract；
- origin relation 与 operation 同事务创建；
- origin unique conflict 后首次事务立即回滚，recovery 使用新事务和 operation→run 锁序；
- run finish 从 attempt 表聚合而非相信调用方计数；
- run scope 持久化精确 effective config，retry/reconcile 从 origin scope 重算 fingerprint 而不是复制 digest；
- compatibility contract 不含 application version；run/operation 的持久化创建版本与本次 provider 的 current writer version 分开映射且不做相等比较；
- 所有 state update 使用旧状态、`detail_purged_at` 和必要 anchor 的 guarded predicate；
- retry 的 rolled_back→pending、relation 和 attempt 在同一事务；retry/reconcile 不更新 origin run；
- website retry 不重复 INSERT item；
- poll marker 同时写 operation/run/domain/tuple；
- poll audit identity 三列一起写且不引用 attempt；
- reconciliation writer 先锁 operation、保持原 attempt 不可变，并按 decision 正确处理 finalization/anchor；
- purge 后 audit-only writer 允许新建无 attempt reconcile relation，但禁止 operation/anchor/detail 更新；
- evidence writer 只重算 caller bytes 的 SHA-256 并执行通用 UTF-8/JSON/大小/敏感字段校验，不实现 WP8 领域 canonicalizer；
- P0 typed 包没有调用 legacy `startRun/finishRun`；
- execute property/default 没有变为 true；
- 没有新增 Flyway 文件或改写 V354～V357。

文本契约不能代替真实事务测试。

### 17.3 MySQL 8 集成测试

使用 required profile；测试未发现数据库、环境变量缺失或测试 skipped 时必须失败，不得绿灯跳过。建议将 `retention-mysql-it` profile 显式纳入 WP3 integration class。

至少覆盖：

#### run

- 同 trigger/domain 相同 command 重放返回同一 run；
- v1 创建 run、v2 重放时成功返回同一 run，`runCreatedByVersion=v1`、`currentWriterVersion=v2`，数据库 run version 仍为 v1；
- 同 trigger/domain 不同合同冲突；
- 同 trigger 不同 domain 各一条 run；
- finish 精确聚合五类计数；
- 存在 prepared/running attempt 时 finish 失败；
- 双 worker 同时 finish 只有一个成功；
- 已完成 run 不能追加 attempt或重开。

#### operation/relation/item

- origin operation/relation/first attempt/items 原子提交；
- 在各 INSERT 点故障注入，事务回滚后不存在半套数据；
- origin 恰好一条且 run 匹配；
- 相同 scope 的普通 origin 冲突使持有 run 锁的事务先回滚，recovery 再按 operation→run 判断 replay/conflict；
- origin conflict recovery 与 operation→run writer 并发无锁序反转；
- retry/reconcile 新 run 复用 operation，origin 不变；
- retry 在同一事务依次执行 rolled_back→pending、relation、attempt；在状态更新后、relation 后、attempt 后故障均整体回滚；
- retry 重放只在 pending/relation/attempt 三者一致时成功，所有半套组合 fail closed；
- 新 run config/policy 不同被 compatibility contract 拒绝，application version 不同允许且记录当前 deployment identity；
- 完整跨部署链：v1 run → v2 replay → v2 创建 origin operation → v3 retry/commit；分别断言 origin run `application_version=v1`、operation `created_application_version=v2`、retry run 和 commit 的 application version=v3；
- website item 排序、唯一、first attempt FK 正确；
- retry 不复制 item；
- `detail_purged_at` 后创建 attempt/item/普通 relation 被应用 guard 拒绝；无 attempt audit-only reconcile relation 是唯一例外。

#### attempt

- run sequence 和跨 run operation attempt number 递增；
- 两个 operation 在同一 run 并发分配 sequence 不重复；
- 相同 `attemptRequestId` 重放返回同一 attempt；`expectedLatestAttemptId` 过期时被拒绝，`lineageParentAttemptId` 仅用于追踪；
- operationless attempt_no 等于 sequence_no；
- prepared→running→五种终态的合法矩阵；
- 终态完全相同重放幂等，不同终态冲突；
- terminal attempt 不被改写；
- operationless 只允许 page/lease_busy；
- known rollback、unknown、manual review 对 operation 状态联动正确。

#### marker 原子性

- 无活动事务调用 website/poll marker port 立即失败且无行；
- website 业务 mutation + marker + operation finalization + attempt succeeded 同事务提交；
- 在 marker 前、marker 后、operation update 后、attempt update 后抛错均完整回滚；
- website duplicate marker 使当前业务事务回滚；
- poll 删除 + purged marker + operation finalization + attempt succeeded 同事务；
- poll tuple 或 run/operation 不匹配由应用或 FK 拒绝；
- commit 使用新部署 application version，同时 origin created version 保持旧值；
- marker 后补写 attempt 时 anchor 单调推进；
- `detail_purged_at` 后 anchor 不推进。

#### reconciliation audit

- 调用方 evidence bytes 与 digest 匹配时持久化成功，digest 错配、非法 UTF-8、非 object JSON、超限或敏感 key 时原子拒绝；
- required MySQL 边界用例分别构造接近 8192 bytes 的空白/转义/多字节 JSON，使“原始 UTF-8 bytes 长度”和 `OCTET_LENGTH(CAST(evidence_json AS CHAR))` 出现差异；覆盖两者均不过限、一方恰好 8192、一方达到 8193 的接受/回滚结果，且不得以 Java 重序列化长度替代原始输入长度；
- 两个语义相近但 byte 表示不同的 JSON 由 WP3 视为不同 digest，不做领域 canonical 修正；WP3 测试不依赖 WP8 schema/vector；
- reconciliation ID 相同 command 重放幂等，不同 command 冲突；
- pending→resolved 与 operation 联动原子；
- committed decision 缺少或错配 marker 时失败；
- safe_to_retry 不设置 finalization/anchor；
- manual_review/automation_stopped 不因审计行 resolved 启动 365 天计时；
- 原 unknown/manual-review attempt 保持不可变；
- detail purge 后一致结论只写 audit，不创建 attempt、不推进 anchor；
- operation 从未 reconcile 且 detail 已清理时，首次 audit-only reconcile 可原子创建新 run/relation/resolution 并成功完成；
- audit-only run 后、relation 后、reconciliation 后故障均整体回滚；run/relation/reconciliation 任一半套重放 fail closed；
- audit-only run 五个 attempt rollup 为 0 但 status=succeeded；普通零 attempt reconcile run 不得成功；
- audit-only writer 与 `finishRun` 并发时不出现 `run → operation` 锁序；过早 finish 只会拒绝，writer 提交后重试成功；
- detail purge 后冲突结论拒绝并产生稳定 P0 错误。

#### rollup/时间/容量边界

- session timezone 为 `Asia/Shanghai` 时，P0 显式时间仍以 UTC wall value 落库；
- `updated_at` 不被 `ON UPDATE CURRENT_TIMESTAMP` 替换成本地时间；
- metrics/scope/error 的多字节边界与数据库 CHECK 一致；
- 每个 marker 不含 JSON 或大对象，写入大小不突破 WP2 目标。

### 17.4 故障与连接结果未知

测试通过代理或受控连接中断覆盖：

1. prepare transaction 返回前断连：重连后按 operation/attempt/item 实际存在性恢复；
2. business commit 响应丢失：marker 存在时不得再次 mutation；
3. marker 不存在时 WP3 不自行宣称 safe retry，只把 operation/attempt 推入 unknown 或交由 reconcile；
4. audit terminal write 失败不能伪造 marker 失败；永久 marker 仍是权威；
5. run finish 失败可幂等重试，不能改写已固化的 operation 结论。
6. origin scope conflict 在首次事务 rollback 后、recovery 前断连：重试仍只进入两阶段协议；
7. retry prepare 在 operation update/relation/attempt 任一点断连：恢复后只能看到完整三元组或完整 rolled_back 初态；
8. purge 后 audit-only prepare 在 run/relation/reconciliation 任一点断连：恢复后只能看到完整三元组或全无。

WP3 只实现审计侧恢复原语；完整四分支业务 reconciliation 属于 WP6/WP8。

### 17.5 历史链声明

WP3 MySQL 测试可以在明确记录来源的 V354 schema fixture 上开发，但报告必须写明：

- 它验证的是 WP3 服务合同，不是 V1→当前 Flyway 历史链；
- V133 失败仍是独立准入阻断；
- 不得修改已发布 V133 checksum；
- 只有经批准的历史链修复或正式豁免关闭后，相关生产就绪验收才成立。

---

## 18. 验收标准

WP3 只有同时满足以下条件才可标记“代码完成，等待 WP4/WP5 接线”：

1. 本 SPEC 评审通过并记录版本；
2. 无未批准 DDL 变化；
3. typed audit API、config/scope canonical encoder、run/operation/attempt/marker/rollup/reconciliation 原语全部实现；reconciliation 只按第 8.5 节 opaque evidence 边界验收，不依赖 WP8 领域 schema/vector；
4. legacy 调用保持兼容，P0 typed 包不依赖 legacy facade；
5. 固定四组 SHA-256 向量逐字通过；
6. 单元和 SQL 契约测试 100% 通过；
7. required MySQL 8 集成测试 0 failure、0 error、0 skipped；
8. 原子 marker、故障注入、并发编号、rollup、UTC/UTF-8 边界全部通过；
9. `git diff --check` 通过，未覆盖或暂存无关 dirty worktree；
10. 所有 execute 配置默认值和实际本地测试配置保持 false；
11. 测试报告明确保留 V133 阻断，不宣称生产 ready；
12. 未执行生产迁移、生产清理或 execute canary。

WP3 完成不等于：

- WP2 完成生产准入；
- 所有 execute 入口已统一；
- 动态开关/lease 已实现；
- 官网批量算法或 poll 接线已完成；
- 可以开启长期无人值守 execute。

---

## 19. 后续 WP 接口

### WP4 动态开关

只消费 run/trigger/operator context，不直接写 operation/attempt/marker 表。switch audit 使用 WP2 独立表和 WP4 自身事务。

### WP5 统一协调器

负责创建 trigger、校验审批/工单、获取 lease、动态开关和预算；通过 `DataRetentionP0AuditService` 创建/结束 run。领域分页必须接收同一 run context。

### WP6 官网批量清理

负责候选选择、20→10→5、failure group、行锁、业务 mutation 和后置校验；使用 WP3 encoder、operation/attempt 和 website transaction-bound marker port。

### WP7 poll 接线

保持现有单切片事务、安全谓词和删除顺序；使用 WP3 poll operation、attempt、purged marker 和 poll audit identity port。

### WP8 reconciliation/detail cleanup

冻结领域 evidence schema/version、canonical encoder 和固定向量，并在调用 WP3 前完成领域校验；使用 WP3 opaque evidence persistence、outcome/anchor/audit-only reconciliation 原语。物理 detail tree 清理、operation lock 内 gate 重验和 `detail_purged_at` 设置仍按 WP2 第 8.4 节单独实现与验收。WP8 不要求回改 WP3 evidence 持久化合同，但其业务接线未批准前不得进入生产 reconciliation。

---

## 20. 开发与发布护栏

- 当前工作区存在大量用户改动、未跟踪 V354～V357 和无关前后端文件；WP3 开发不得 reset、覆盖、改号或暂存无关文件。
- V355～V357 已占用，WP3 不需要迁移号，也不得假设下一个迁移版本。
- 不复述或输出 `application.yml` 中可能存在的凭据；凭据整改保持独立任务。
- 不修改生产 Compose、COS/MinIO 配置或已完成的 MinIO 下线结论。
- 不自动设置环境变量，不自动把 false 改为 true。
- 未经单独授权不创建 commit、不 push、不执行生产连接。

---

## 21. 评审决策

第三轮评审结论：

1. 跨部署 replay 的创建版本、当前 writer 版本和提交版本合同通过；
2. WP3 opaque `EvidenceDocument` 与 WP8 领域 canonical schema 的职责边界通过；
3. retry 原子性、purge 后 audit-only、origin 冲突恢复锁序和 compatibility/deployment 拆分继续有效；
4. 事务边界、锁顺序、状态机和故障恢复契约通过；
5. 允许开展 WP3 Java 与 required 测试开发。

最终批准状态：

> **第三次评审通过，允许开展 WP3 Java 与测试开发；所有 execute 开关保持关闭。**

本批准不包含 WP4～WP8、WP8 evidence canonical schema、生产迁移、生产清理或 V133 生产准入阻断解除。
