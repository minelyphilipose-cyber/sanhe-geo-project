# 数据定期清理 P0 WP6 官网批量清理 SPEC（第二轮聚焦评审修订版）

> 文档日期：2026-08-13
>
> 本次修订：2026-08-14
>
> 当前状态：WP6 Java/SQL/测试实现、本地聚焦复审及 `retention-website-it` 真实 MySQL 8/Redis profile 通过
>
> 上游基线：WP2 第五次评审通过版、WP3 第三次评审通过版、WP4 功能代码评审通过版、WP5 代码评审通过版、`V354__data_retention_execute_governance.sql` 与当前 WP5 实现
>
> 授权边界：仅授权开展 WP6 Java/SQL/测试开发与隔离验收；不打开任何静态或动态 execute/enable-write 开关，不执行生产迁移、生产清理或 canary，也不解除 V133、WP2、WP8 和生产准入阻断

---

## 0. 结论摘要

WP6 把官网历史文章清理从“legacy service 内单篇循环、单篇事务”改造成 WP5 coordinator 下唯一的
`website_published_content` 领域执行器。冻结后的主路径为：

```text
coordinator 已完成 authorization / lease / static gate / dynamic gate / budget
→ WebsiteRetentionDomainExecutor 选择同一 run cutoff/upperBound 下的候选
→ 每个 origin operation 最多 20 篇，保存规范化 item tree
→ coordinator-owned REQUIRES_NEW transaction
→ operation → failure/reconciliation gate → attempt → batch item → business rows
→ 批内重新验证资格
→ 一次性清空正文、官网投递 payload、官网发布 raw response
→ 仅对本批已锁定记录执行后置校验
→ 同事务关闭精确匹配的 active failure group（如有）
→ 同事务写 data_retention_batch_commit、operation committed、attempt succeeded
→ TrustedMutationReceipt 驱动预算与 run 汇总
```

普通批次按 20 篇执行。`KNOWN_ROLLBACK` 先由稳定 disposition 分类器区分为“同 scope 瞬时重试”“可拆分的数据失败”或“安全停止”；只有可拆分的数据失败才按 `20 → 10 + 10 → 5 + 5` 拆分。
自动拆分永不把一个大组降为单篇。自然尾批可以为 1～4 篇，但它本身已是叶子，不再拆分；叶子仍失败时整体进入 failure group。

本 SPEC 同时冻结以下容易产生永久漏删或误删的契约：

1. split child 是新 operation，不是替换父 operation 的 item；父 operation 在完整 child coverage 持久化后才可 `closed_no_mutation`；
2. 候选、cutoff 和 upperBound 使用显式 `Clock/Instant/Asia/Shanghai`，禁止继续依赖 JVM 默认时区；
3. mutation transaction 开头先锁定并验证 WP3 operation/attempt/item tree，不能在锁完业务行后才反向锁 operation；
4. 只有已知回滚且 disposition 明确为数据相关、可定位批次问题才允许拆分；瞬时并发错误先对同 scope 做固定次数重试，耗尽后停止本 domain，commit outcome unknown、marker/业务状态矛盾同样停止；
5. leaf failure、failure group、active key 和 attempt/operation 状态必须原子收敛；
6. active failure group 的同 scope retry 成功时，group 关闭、active key 删除、业务 mutation 与 marker 在同一事务提交；
7. 运行恢复使用持久化 operation/item/attempt/marker/failure-group tree，禁止重选成员或重置预算；
8. legacy `WebsitePublishedContentCleanupService` 不再保留可被 controller/scheduler/其他 bean 调用的 execute 旁路；
9. dry-run 继续不获取 execute lease、不要求动态开关为 true，但迁移到 WP3 typed run，保持一次顶层请求一条 run；
10. quarantined leaf 通过数据库可复核、当前 session 只可消费一次的 `TrustedTerminalUsageReceipt` 实时推进 candidate progress；重启仍从相同 attempt metrics 聚合；
11. 任一 writer 写回非空官网 task payload 时必须在同一 SQL/事务把 `payload_purged_at` 复位为 NULL；selector 以实际非空 payload 为最终事实，允许晚写回数据在下一 run 再次清空；
12. WP6 只产出 website typed evidence facts；WP8 仍负责最终 `EvidenceDocument` 领域 canonical JSON/schema/vector 和生产 reconciliation 接线，该 canonical 工作不阻断 WP6 首版代码完成。

WP6 完成不代表生产可执行。本地外部 MySQL/Redis required profile 已通过，但 V133 历史链结论、
生产 8.0.46 克隆、WP8 blocker/detail cleanup/canary 与审批仍全部是生产门禁。

本次聚焦修订对评审意见的关闭方式：

| 评审项 | 冻结结论 |
|---|---|
| known rollback 被无差别拆分 | outcome 与 disposition 分层；transient 原 scope 最多重试 2 次，只有 `DATA_SPLIT_ELIGIBLE` 可 split/leaf，耗尽与默认分支停止 domain且不建 group |
| leaf candidate usage 只在重启后可见 | compound writer 返回数据库重算的 `TrustedTerminalUsageReceipt`；当前 session 验证、去重并立即推进 progress，重启从同一 attempt metrics 聚合 |
| payload 晚写回被旧 marker/`deleted` 状态遮蔽 | 所有 writer 原子复位 marker；新 CHECK 阻断半套写入；`deleted + 实际可释放目标 + 成功发布证据 + cutoff/hold 合法` 直接具备复热资格；required 测试覆盖无正文、仅 payload 的 cleanup→写回→再次清理 |
| 空间验收歧义 | WP6 验收 live large-field bytes 下降与 InnoDB 页可复用，不承诺 `.ibd`/OS 空间立即归还 |
| 首版范围过宽 | 人工 regroup、完整半套树自动 repair、WP8 canonical evidence 后置，均不作为 WP6 首版代码完成门槛 |

---

## 1. 当前实现事实

### 1.1 官网 dry-run 已迁移到 typed audit

`WebsitePublishedContentCleanupService` 当前只保留 dry-run：

- 使用显式 `Clock/Instant/Asia/Shanghai` 计算 cutoff；
- manual 请求创建一条 WP3 `dry_run` run 和一条 operationless `PAGE` attempt；
- scheduler 一次调用创建一条 run，所有分页在 service 内完成并串联多个 `PAGE` attempt；
- 不注入 legacy `DataRetentionRunAuditService`、`TransactionTemplate` 或 mutation JDBC；
- 候选 ID、正文/payload 估算 bytes 只出现在响应中，不持久化到 run/attempt JSON；
- dry-run 与 execute repository 通过 `WebsiteRetentionEligibilitySql` 共享同一套资格谓词。

旧 service 中的单篇 mutation 旁路已删除；execute 只能进入 WP5 coordinator 和 website domain executor。

### 1.2 execute 已接入唯一 website domain executor

当前 controller 与 scheduler 的官网 execute 均调用 `DataRetentionExecuteCoordinator`，registry 中恰好注册一个
website domain executor：

- controller/scheduler 不新增第二条路径；
- coordinator registry 中恰好注册一个 website executor；
- executor 只能通过 `RetentionExecutionSession` 开 mutation transaction；
- 不允许 fallback 到 legacy `runScheduled(..., dryRun=false)`。

### 1.3 可复用的已批准能力

WP2/V354 已提供：

- operation、operation-run、attempt、website batch item、website commit；
- failure group/item/active key/regroup history；
- scope hash、fingerprint、policy、生命周期与 365 天 detail 结构；
- website operation/item/marker 的唯一约束与复合 FK。

WP3 已提供：

- run、origin/retry/reconcile、attempt lifecycle；
- website canonical encoder；
- transaction-bound website marker 与 `TrustedMutationReceipt`；
- terminal replay、running recovery 和 opaque reconciliation persistence。

WP4/WP5 已提供：

- Redis global/domain fail-closed gate；
- 全局 execute lease；
- per-run transaction/candidate/deadline 预算；
- coordinator-owned `REQUIRES_NEW` transaction；
- manual/scheduler/canary/retry/reconcile facade 与权限框架。

### 1.4 尚未关闭的环境阻断

- WP2 的真实 V1→V354 空库历史链仍由 V133 阻断；
- WP3/WP4/WP5/WP6 required profile 已在本地 MySQL 8.0.45 与独立 Redis DB 15 上实跑通过；Docker 仍不可用但不再阻断本地 required evidence；
- 当前最高已占用 Flyway 版本为 V366，后续开发仍必须重新扫描，不能硬占下一个号码；
- 生产数据库为 MySQL 8.0.46；开发 required image baseline 为 MySQL 8.0.36，本次外部实例证据为 MySQL 8.0.45；
- 所有 execute/enable-write 默认值当前仍为 `false`，WP6 不改变这些默认值。

---

## 2. 目标、范围与非范围

### 2.1 WP6 目标

1. 实现唯一的 `website_published_content` domain executor；
2. 将官网不可逆清理批量化为最多 20 篇的原子事务；
3. 实现确定性 20→10→5 拆分及自然尾批规则；
4. 实现 leaf failure group、active key 和精确 scope retry；
5. 保证业务 mutation、后置校验、failure group 收敛、marker、operation 与 attempt 同事务；
6. 提供 marker/业务状态四分支的 website typed evidence classifier；
7. 完成主链 prepared/running 恢复；不把完整 split/failure-group 半套树自动修复作为首版阻断项；
8. 迁移官网 dry-run 到 WP3 typed run；
9. 删除/封闭 legacy execute 旁路；
10. 建立真实 MySQL 8 + Redis coordinator 集成测试。

### 2.2 WP6 范围

- `WebsiteRetentionDomainExecutor`；
- website candidate selector、snapshot boundary 与 eligibility policy；
- website batch mutation repository 和 post-check；
- WP3/WP5 为 website 所需的狭口 typed API 扩展；
- split planner、split coverage、leaf quarantine；
- leaf failure group 与 exact-scope retry；
- website recovery view 与 evidence facts；
- website dry-run typed audit；
- 官网 task payload 晚写回 marker 复位协议与数据库一致性约束；
- controller/scheduler 的 website 接线完成；
- website 指标、结构化日志、P0 alert；
- unit/contract/architecture/MySQL/Redis tests；
- 如需要的权限或查询索引 Flyway migration。

### 2.3 WP6 非范围

- poll 领域执行器、poll deletion/late-write/summary，属于 WP7；
- article archive/purge、object storage、URL rewrite、slim retention；
- WP8 的 canonical `EvidenceDocument` JSON/schema/version/vector；
- WP8 的 365 天 detail cleanup、425 天 blocker、容量 dashboard、最终 runbook；
- 自动打开任何静态/动态开关；
- 自动解决 marker 缺失但业务已处于后置状态的 operation；
- 修改已发布的 V354/V364；
- 生产迁移、生产 mutation、canary 或 failure group 人工处置；
- 人工 regroup、跨 failure group merge、完整半套 split/group tree 自动修复和 failure-group 管理 UI/API；
- 通过 `OPTIMIZE TABLE`、`ALTER TABLE ... FORCE` 或表重建立即归还 `.ibd`/操作系统磁盘空间；
- 以本地测试替代生产克隆/metadata lock/容量验收。

---

## 3. 冻结不变量

1. website execute 的唯一入口是 WP5 coordinator；domain service 不自行获取 Redis lease或读取动态开关。
2. 一个顶层 domain 请求只创建一条 run；分页、batch、split 和 stop 都在该 run 下形成 attempt/tree。
3. fresh normal operation 的 size 只允许 `20/10/5/1..4 tail`；自动拆分只允许 `20→10+10`、`10→5+5`，任何 `item_count <= 5` 都是 leaf。
4. 自然尾批 1～4 合法；禁止为了诊断一个大失败组而自动拆成单篇。
5. 每个 split child 都是不可变成员集合的新 operation；不得修改父 operation item 或复用父 operation ID 提交子集 marker。
6. child operation 的 cutoff、upperBound、compatibility contract 与父 operation 完全相同，只有成员/itemCount/hash 不同。
7. split child first attempt 使用 `SPLIT`，`lineageParentAttemptId` 指向直接父失败 attempt。
8. 父 operation 只有在 child operation/item/first-attempt 对父 item 构成无重叠、无遗漏的完整 partition 后，才可 `closed_no_mutation`。
9. known rollback 不等于可拆分；只有 disposition=`DATA_SPLIT_ELIGIBLE` 才进入 split/leaf quarantine，瞬时重试耗尽和无法稳定分类均停止本 domain。
10. leaf failure group 与 active key 必须和 attempt failed、operation rolled_back 原子写入；不得留下“失败但未隔离”的窗口。
11. fresh selector 必须排除 active failure key，以及被 pending/rolled_back/unknown/manual_review operation tree 持有的 business key。
12. exact-scope retry 必须复用原 operation/item/hash；任何未来 regroup 后的新子集必须创建新 child operation，不能把新成员塞进旧 operation。
13. active group retry 成功时，group/item/active-key 收敛与 marker 同事务；回滚后 group 仍 active。
14. marker 唯一冲突不是本次成功，必须整体回滚并进入恢复分类。
15. mutation transaction 内禁止 Redis、HTTP、对象存储、消息队列或其他外部调用。
16. business mutation 只能更新本 SPEC 的 allowlist 列，禁止 DELETE 轻量文章、publish evidence、URL、统计或 self-media 数据。
17. batch 内任一 article 锁后不再满足资格时，本批不得提交其余成员；只能已知回滚后按 split policy 继续。
18. post-check 只读取本批持锁行和当前 operation marker，不因无关并发新增记录失败。
19. unknown outcome 不拆分、不创建 failure group、不盲目重试；先做 marker/business evidence 分类。
20. marker 存在但业务不是完整后置状态、marker 合同不匹配或 scope tree 不完整，一律 manual review/automation stop。
21. marker 不存在且业务完整后置或前后混合，不得补写 marker或自动宣称 committed。
22. committed leaf 与 quarantined leaf 都必须用 WP5 可验证的可信 receipt 在当前 session 推进 candidate progress；不得只写 attempt metrics 后继续使用旧内存余额。
23. `payload_purged_at` 和 article=`deleted` 都不是实际热字段的否定事实；`deleted` 文章只要仍有实际可释放目标且完整满足成功发布、cutoff 与 hold 条件，就具备复热资格；任何非空 payload 写入必须原子复位 marker。
24. run 恢复沿用原 cutoff、upperBound、operation/item、attempt、预算和 deadline；不得从当前配置重新选取或重置。
25. response/metrics/log/audit 不保存正文、payload、raw response、token、cookie 或其可逆片段。
26. WP6 代码不得调用 legacy `DataRetentionRunAuditService`。
27. 所有 execute/enable-write 默认值保持 false；测试仅在进程内显式配置 true。

---

## 4. Domain port 与所需 typed 扩展

### 4.1 website executor

```java
final class WebsiteRetentionDomainExecutor implements DataRetentionDomainExecutor {
    RetentionDomain domain(); // WEBSITE_PUBLISHED_CONTENT
    RetentionExecutionResult executeFresh(RetentionExecutionSession session);
    RetentionExecutionResult resumeRunning(RetentionExecutionSession session,
                                            PersistedRunState commonState);
    AuditOnlyReconciliationCommand investigateReconciliation(
            RetentionReconcileCommand command); // WP8 canonical adapter 接线前 fail closed
}
```

`executeFresh` 必须区分：

- `MANUAL/SCHEDULER`：创建 fresh origin operations；
- `CANARY`：最多选择 5 篇，真实 mutation，不自动晋级；
- `RETRY`：读取 `relatedOperationId` 的永久 item tree，调用 WP3 related-attempt API，不重选 business key。

### 4.2 transaction 开头的审计树锁

现有 `commitWebsiteMutation` 在 marker 阶段才锁 operation。WP6 若先锁业务行再调用它，会形成业务行→operation 的锁序反转。
因此 WP3 facade 必须新增等价的 MANDATORY 狭口：

```java
LockedWebsiteMutationContext lockWebsiteMutation(
    operationContext,
    runId,
    attemptId
)
```

该方法在 coordinator transaction 开始后、任何业务行锁前完成：

1. 锁 operation；
2. 锁/验证 unresolved reconciliation 与 failure-group gate；
3. 锁当前 attempt，要求 `running`；
4. 按 sequence 锁全部 batch item；
5. 从数据库行重算 website canonical scope/hash；
6. 验证 run relation、contract、itemCount、cutoff、upperBound；
7. 返回数据库权威的有序 article IDs 和可选 exact active group identity。

调用方上传的 `RetentionOperationContext.websiteArticleIds` 不能替代锁内数据库事实。

### 4.3 domain audit compound writers

WP6 需要以下功能，名称可按实现习惯调整，但只能经 `DataRetentionP0AuditService` 或其受控 typed port 暴露：

```text
recordWebsiteKnownRollback
openOrRefreshLeafFailureGroup
finalizeSplitParentWithoutMutation
recoverWebsiteAttemptFromEvidence
```

约束：

- 这些 writer 均使用 operation-first 锁序；
- 普通 WP6 repository 不直接更新 operation/attempt 生命周期列；
- leaf quarantine 将 attempt/operation/group/items/active keys 作为一个事务；
- `openOrRefreshLeafFailureGroup` 在事务提交成功后返回 `TrustedTerminalUsageReceipt`；该 receipt 的 usage 只能从已提交 attempt/group/item 事实推导，调用方不能指定；
- split parent finalizer 自行从数据库验证 child coverage；
- purge 后、finalized operation、marker conflict 或已有非终态 sibling 均 fail closed；
- 所有重放必须比较完整 command/tree，不以“行已存在”直接当成功。

`TrustedTerminalUsageReceipt` 是 WP3/WP5 的最小 typed 扩展，专用于“没有业务 commit marker、但已形成永久 terminal 处置”的 candidate 记账：

```text
TrustedTerminalUsageReceipt
  receiptType = WEBSITE_LEAF_QUARANTINE_V1
  runId / operationId / attemptId / terminalStatus
  usage = { committedTransactionCount: 0,
            chargedCandidateItems: persistedOperationItemCount }
  persistedFactDigest
```

约束：

1. 类型只能由受控 audit writer 构造；普通 domain callback 无 public constructor/factory；
2. session 消费前经 WP3 typed validator 按 operation 锁序重读 attempt、operation、group、items，重算 terminal status 与 usage；
3. receipt 与持久化事实不一致、group 非 active、attempt 非预期 terminal 或 itemCount 不一致时 fail closed；
4. `persistedFactDigest` 是受控 writer 对 immutable attempt identity/status/metrics、operation itemCount 和 active group identity 的 canonical SHA-256；validator 从数据库重算；
5. 同一 session 以 `(receiptType, attemptId, persistedFactDigest)` 只消费一次，重复响应/replay 不重复推进 progress；
6. validator 成功后 session 同步推进内存 candidate progress，再允许选择下一 operation；
7. 进程在 writer commit 后、session 消费前退出时，重启由 WP5 从同一 attempt metrics 聚合一次；不得同时把 receipt 与 metrics 相加两次；
8. quarantined leaf 不增加 committed transaction budget，因为失败的业务 mutation 已回滚；failure-group governance transaction 也不冒充业务 commit。

### 4.4 website recovery view

WP5 的通用 `PersistedRunState` 不足以表达 website item、marker 和 failure group。WP6 增加只读 typed view：

```text
WebsiteRunRecoveryView
  runId / cutoff / upperBound / compatibility
  ordered operations
    operation identity/outcome/relation
    ordered immutable items
    ordered attempts + lineage
    marker identity/contract
    active failure group + ordered group items
```

读取不加业务行锁、不改变状态；真正恢复动作必须重新进入 audit/mutation transaction 并重读最新事实。

---

## 5. 时间、cutoff、upperBound 与分页

### 5.1 cutoff

website business zone 固定为 `Asia/Shanghai`。实现必须注入 `Clock`，禁止 `LocalDateTime.now()`、系统默认 ZoneId 或数据库 NOW 参与 operation scope。

```text
cutoffInstant = run.startedAt - effectiveRetentionHours
```

- canonical/hash/operation 使用 UTC millisecond `Instant`；
- 历史业务表的 `DATETIME` 按 Asia/Shanghai wall time 解释，SQL 参数由 `cutoffInstant` 显式转换；
- audit 新时间仍按 WP2 写 UTC；
- JVM、MySQL session 与 JDBC URL 的时区差异必须有 MySQL 集成测试，不能靠开发机默认值通过。

同一 run 的所有 fresh/split operations 使用同一个 cutoff。retry 使用 origin operation cutoff，不按当前时间重算。

### 5.2 selection upper bound

fresh run 第一次候选读取在一个短只读 transaction 中：

1. 读取 `article_draft` 当前存在的最大正 article ID，作为本 run 允许纳入的新数据边界；该值不要求对应行当前 eligible；
2. 读取最小的最多 20 个候选；
3. 若候选为空，run 可按零 mutation 结束；
4. 若进程在任何 operation 写入前崩溃，WP5 empty-run 规则允许重新选择；
5. 第一条 operation 一旦持久化，`upperBound` 即成为该 run 的选择边界，恢复时从永久事实读取。

后续选择固定：

```sql
article_id <= selection_upper_bound
```

所有同 run fresh operation 与 split child 均写同一个 upperBound。并发产生的更大 ID 留给下一 run；边界内状态变化仍在锁后资格重验中处理。

### 5.3 分页与 requestLimit

- selector 可以预读最多 20 个有序 eligible ID，但 operation size 由当前可用数和 run remaining limit 规范化：`>=20` 取 20、`10..19` 取 10、`5..9` 取 5、`1..4` 取全部 tail；剩余候选在下一 operation 重新读取；
- 不使用“已清理后可能跳项”的永久 cursor；每轮从仍满足资格且未被 hold 的最小 ID 重新选择；
- `requestLimit` 是本 run 允许形成最终业务处置的 server-side cap，不是 transaction batch size；
- manual 默认 20，scheduler 当前默认 100，canary 上限 5；
- 非法、超 server cap 或非整数 requestLimit 在 run 创建前拒绝；
- coordinator candidate budget 与 requestLimit 取更小剩余量；
- 已知回滚的非叶父 attempt不重复计 candidate；最终 committed child 或 quarantined leaf 才计一次业务 candidate。
- transient retry、SAFETY_STOP 和 UNKNOWN 在没有 commit/terminal receipt 时不推进 candidate progress；发生这些 stop 后本 domain 不继续利用未扣减余额选择新 scope。

### 5.4 eligibility 与 hold

候选业务谓词沿用当前 production-proven 规则并由 `website-published-v1` policy version 管理：

- 只接受 `brand_geo_site/industry_site/forum_site`；
- 存在关联成功官网发布记录，effective publish time 不晚于 cutoff；
- article 状态为 `distributed/published`；或者状态为 `deleted` 且当前至少存在一个实际可释放目标，同时仍满足本节成功官网发布证据、cutoff、distribution/self-media hold 等全部条件；
- 不存在非终态 distribution task；
- 不存在状态不为 `published_confirmed/cancelled` 的 self-media schedule；
- 至少存在一个可释放目标：非空正文、符合官网成功证据的 task 实际非空 payload、成功官网 publish raw response，或 article 尚未转为 deleted；
- 不存在 active failure key；
- 不存在持有该 business key 的 pending/rolled_back/unknown/manual_review operation tree。

request 不允许上传 target kind/status集合、SQL 谓词、project/tenant 条件或 article IDs。人工指定 canary IDs 如未来需要，必须另行评审 server-side scope authorization；首版只按同一 selector 取最老候选。

`payload_purged_at` 只是“最近一次已观察到 payload 清空”的审计 marker，不能覆盖当前列事实。candidate/hold/recovery SQL 均须遵循：

```text
request_payload IS NOT NULL
OR fill_payload IS NOT NULL
OR response_payload IS NOT NULL
```

只要任一实际 payload 为 SQL 非 NULL，该 task 就仍是热数据；即使历史 `payload_purged_at` 非 NULL，selector 也必须选中。JSON literal `null` 不是 SQL NULL，写入方必须规范化为 SQL NULL 或仍按热数据处理。

`deleted` 复热资格不依赖 `content_purged_at`、`payload_purged_at` 或仍存在的 WP6 batch item：

```text
article.status = deleted
AND actualReleasableTargetExists = true
AND successfulWebsitePublishEvidenceExists = true
AND effectivePublishTime <= runCutoff
AND noActiveDistributionOrSelfMediaHold = true
```

满足该谓词即可进入新 fresh operation；旧 WP6 commit marker/operation 是补充审计事实，不是 selector 的必要 join。原因是 operation/commit 永久保留，但 operation item 按 WP2 契约在统一锚点满 365 天后可以清理，不能把可删除 item 当成永久复热索引。没有实际可释放目标的 `deleted` 文章仍不入选；request 也不能借此指定任意 deleted article。

---

## 6. operation 与 20→10→5

### 6.1 fresh origin

selector 返回规范化 size `20/10/5/1..4 tail` 的正 article ID，数值升序去重，使用共享 encoder 创建：

```text
WebsiteOperationCommand(articleIds, runCutoff, runUpperBound)
```

first attempt type：

| source | first attempt type |
|---|---|
| manual/scheduler | `NORMAL_BATCH` |
| canary | `CANARY` |
| split child | `SPLIT` |
| exact operation retry | 不创建 origin；使用 related `RETRY` attempt |

### 6.2 deterministic split

只有 attempt 已确认为 known rollback、disposition=`DATA_SPLIT_ELIGIBLE`，且 operation 没有 marker/unknown/manual review 时允许 split。算法只有：

```text
20 → 10 + 10 → 5 + 5
10 → 5 + 5
1..5 → leaf，不再拆
```

其他 6～9、11～19 的 operation size 属于非法持久化 tree，必须 fail closed；fresh selector 不创建它们。
自然尾批 1～4 并非从大组自动定位单篇故障，而是当次选择剩余量，因此允许作为 leaf。

### 6.3 child identity

每个 child：

- 新 canonical operation ID、新 batch hash、新 item tree；
- 与父 operation 同 run、cutoff、upperBound、contract；
- first attempt 的 `lineageParentAttemptId` 为直接父失败 attempt；
- `expectedLatestAttemptId=null`；
- child operations 按成员首 ID 顺序创建和处理；
- 同一个 parent 的 child request IDs 由 server-side `parentOperationId + parentAttemptId + side + schemaVersion` 稳定派生，重启不重复建树。

WP2 容量估算中的“split 只增加 attempt、不复制 operation item”不能同时满足“每个不可逆 transaction 都有独立 marker”和
`data_retention_batch_commit.operation_id UNIQUE`。本 SPEC 选择上游更强的 marker/hash/不可变 scope 契约：每个实际执行的 child transaction 使用新 operation。
评审批准后，WP8 容量模型必须按真实 split 率计入这些短期 child item；这不要求修改 V354 表结构，但不能继续沿用忽略 child operation/item 的估算假设。

### 6.4 parent closure

父 attempt 的 failed/rolled_back、两个 deterministic child operation、各自完整 item/first attempt，以及 parent closure 必须由一个 operation-locked compound governance transaction 一次提交。writer 在写入前和幂等重放时验证：

- 每个 child first attempt lineage 指向 parent failed attempt；
- child item union 与 parent item 完全相等；
- child 之间无重复；
- child cutoff/upperBound/contract 与 parent 相同；
- 不存在 marker、active group、unknown/manual review 或非本 split plan 的 child。

验证通过后 parent 在同一事务写：

```text
outcome_status = closed_no_mutation
outcome_finalization_source = terminal_attempt
outcome_finalization_reason = verified_no_mutation
```

其语义是“父 scope 已知未提交且不再按原 scope retry，责任已由永久 child tree 接管”，不是声称 child 已全部清理。

因此正常崩溃只能看到“split 前原状态”或“完整 child tree + 已关闭 parent”，不会产生一个 child 的半套状态。若历史/人工损坏仍出现 child 缺失、单 child 或 parent 未关闭，WP6 首版 fail closed、保留事实并告警，不自动修补；完整半套树 repair 后置。

---

## 7. mutation transaction 与锁顺序

### 7.1 transaction 外

```text
selector read
→ prepare origin/retry attempt（WP3 short transaction）
→ attempt 保持 prepared
→ session.inMutationTransaction(estimate=itemCount, callback) 先完成 gate/lease/budget checkpoint
→ callback 开始后 session.audit.markAttemptRunning
→ 锁定 audit tree 与业务行并执行 mutation
```

gate、lease、budget checkpoint 由 WP5 在 mutation transaction 前完成。WP6 不重复实现，也不能提前自行开 transaction；checkpoint 失败时 callback 不得开始，attempt 必须从 `prepared` 收敛为 `skipped + rolled_back + recovery_not_started`，不得先进入 `running/failed`。

### 7.2 transaction 内固定锁序

```text
1. operation FOR UPDATE
2. reconciliation/failure gate 与 exact active group
3. current attempt
4. operation batch items（sequence_no/id 升序）
5. article_draft（article id 升序）
6. article_draft_version（id 升序）
7. article_publish_record（id 升序）
8. distribution_tasks（id 升序）
9. self_media_publish_schedule（id 升序）
```

禁止按 article 逐个交错锁多个表。先锁一个表中本批全部行，再进入下一表，减少两个重叠 batch 的死锁面。

由于 `article_draft` 是子表 FK 的父行，锁父行后并发创建/改绑相关 child 会被 InnoDB 正确串行化。锁等待超时或 deadlock 必须导致整个 batch rollback；是否 split 仍按第 9 节 outcome classifier。

### 7.3 锁后资格重验

锁完业务行后，以数据库当前行重做第 5.4 节全部资格判断，并验证：

- article IDs 与 operation items 一一对应；
- 每篇仍有成功官网发布 evidence；
- latest effective published time 不晚于 operation cutoff；
- 没有 active distribution/self-media work；
- exact active group（如为 failure retry）成员与 operation items 完全相等；
- 没有未知新 target kind/status 被旧 policy 静默接受。

任一不满足抛稳定 `WEBSITE_BATCH_STATE_CHANGED`，不得只处理仍满足的子集。

### 7.4 mutation allowlist

唯一允许的业务更新：

```text
article_draft_version
  content_markdown = NULL
  对本事务前非空正文行设置 content_purged_at

distribution_tasks（只限成功官网投递 evidence 对应行）
  request_payload = NULL
  fill_payload = NULL
  response_payload = NULL
  对本事务前实际含 payload 的行设置 payload_purged_at

article_publish_record（只限成功官网发布记录）
  raw_response = NULL

article_draft
  status = deleted
  updated_at = transaction business time
```

不得 DELETE 任何上述行；不得更新 publish URL、url quality/source、published/verified time、project/brand、task status、self-media row、统计或关系表。

业务时间从注入 `Clock` 在 transaction 开始前截断到毫秒并作为参数传入；同一 transaction 使用同一个值，不用每条 SQL 的 `CURRENT_TIMESTAMP` 形成漂移。

官网 payload 的“复热”写入协议同样属于 WP6 安全边界。所有现有及未来 business writer 只要把
`request_payload/fill_payload/response_payload` 任一列写为 SQL 非 NULL，就必须在同一条 UPDATE 或同一事务内原子写
`payload_purged_at=NULL`。禁止先提交 payload、随后异步修 marker。

为封闭遗漏 writer 的旁路，WP6 使用新 Flyway migration：

1. 先把历史上“任一 payload 非 NULL 且 `payload_purged_at` 非 NULL”的行仅修复 marker 为 NULL，不改 payload；
2. 再增加命名 CHECK，表达 `payload_purged_at IS NULL OR 三个 payload 均为 SQL NULL`；
3. migration 必须做精确 column/CHECK signature、repair/re-migrate/no-op 和生产克隆 metadata-lock 验证；
4. 现有 review/publish/retry writer 全部增加 SQL 契约测试，证明写非空 payload 与 marker 复位同提交、失败同回滚。

CHECK 是防止静默漏清的最后防线，不替代 selector 读取实际 payload。若生产克隆证明该 CHECK 的在线验证成本不可接受，必须形成单独豁免与等价数据库 guard 方案；不得退化为继续依赖 marker selector。

---

## 8. 后置校验、marker 与可信计数

### 8.1 locked-rows-only post-check

mutation 后、marker 前验证：

- 本事务前非空的 draft version 正文均为 NULL 且 purge time 非空；
- 本事务前含 payload 的目标 task 三个 payload 均为 NULL 且 purge time 非空；
- 成功官网 publish record 的 raw response 均为 NULL；
- 本批 article 状态均为 deleted；
- article/version/task/publish record 的持久行数量和主键集合未减少；
- publish URL、publish status、source identity、project identity 等 lightweight evidence 与锁前快照相同；
- self-media schedule 的主键/状态未被 WP6 修改。

不查询“不属于 operation items 的全表零残留”，不因另一个 article 新增 task 失败。

### 8.2 marker 顺序

post-check 通过后：

1. 若为 exact active-group retry，更新 group/item 为 resolved 并删除 active keys；
2. 调用 transaction-bound `commitWebsiteMutation`；
3. marker writer 重验 operation/attempt/tree，插入永久 marker、finalize operation、完成 attempt；
4. callback 返回 `PendingMutation`；
5. session 在同一 transaction 验证并消费 `TrustedMutationReceipt`；
6. transaction commit 成功后才更新 JVM progress。

任一步失败，业务更新、group closure、marker、operation 和 attempt 全部回滚。

### 8.3 counts

成功 operation：

```text
candidateCount = operation.itemCount
affectedCount  = operation.itemCount
skippedCount   = 0
warningCount   = 0
trusted usage  = { committedTransactionCount: 1,
                   chargedCandidateItems: operation.itemCount }
```

marker writer 从持久化 itemCount 产生可信 usage。domain callback 必须完全一致，不能按 SQL affected row 数少报 candidate。

可记录但不参与 candidate budget 的扩展 metrics：

```text
bodyRowsPurged
payloadRowsPurged
publishRawRowsSlimmed
articleRowsTransitioned
postCheckSchemaVersion=1
```

任何扩展 metrics 仍受 8 KB、非敏感和稳定类型限制。

leaf quarantine 的可信 usage 为：

```text
candidateCount = operation.itemCount
affectedCount  = 0
skippedCount   = operation.itemCount
warningCount   = 1
trusted terminal usage = { committedTransactionCount: 0,
                           chargedCandidateItems: operation.itemCount }
```

compound writer 提交后，executor 必须立即把 `TrustedTerminalUsageReceipt` 交给同一 `RetentionExecutionSession` 验证并消费，成功后才可继续下一 scope。该消费同时推进 WP5 candidate progress 和本次 website `requestLimit` 的 disposed count；当前会话与重启聚合因此得到完全相同的剩余额度。

### 8.4 空间释放验收口径

把 TEXT/JSON 大字段更新为 SQL NULL，目标是降低 live large-field bytes，并使已占用 InnoDB page/extent 可供同表后续写入复用。WP6 不承诺 `.ibd` 文件或操作系统已用磁盘立即缩小。

required 验收至少比较清理前后：

- 目标大字段的 `SUM(OCTET_LENGTH(...))`/抽样分位与非空行数；
- 业务行和轻量 evidence 行数保持不变；
- 后续同表写入可复用空间的生产克隆观察；
- batch transaction 时间、redo/undo 与锁等待处于批准阈值。

`OPTIMIZE TABLE`、`ALTER TABLE ... FORCE`、tablespace rebuild 或文件级磁盘归还是独立运维方案，需另行评审停机/metadata lock、临时双倍空间、备份恢复和回滚，不属于 WP6 开发或生产启用条件。

---

## 9. transaction outcome、拆分与 leaf quarantine

### 9.1 outcome 分类

WP6 不根据异常类名随意猜测提交事实。session/domain transaction port 必须返回或抛出可区分：

```text
COMMITTED
KNOWN_ROLLBACK
UNKNOWN
```

- callback 主动因资格/post-check 抛错且 Spring 明确完成 rollback：known rollback；
- SQL 失败且 rollback 成功：known rollback；
- commit response/connection outcome 不可证明、rollback 本身失败或 marker/connection 事实不确定：unknown；
- `MUTATION_OUTCOME_UNKNOWN` 永远不能降级为普通 failed。

如现有 WP5 transaction API 无法可靠表达边界，WP6 允许做最小 typed 扩展；禁止在 website executor 中捕获所有 `RuntimeException` 后统一 split。

transaction outcome 只回答“是否提交”，不能直接决定 split。对 `KNOWN_ROLLBACK` 必须再由稳定、默认拒绝的 classifier 返回：

| disposition | 允许来源 | 动作 |
|---|---|---|
| `TRANSIENT_SAME_SCOPE_RETRY` | 已证明 rollback 的 deadlock、lock-wait timeout、serialization/concurrency conflict；或在 commit 前已证明连接/事务未提交的瞬时失败 | 原 operation、原 attempt、原 item tree 内有界重试 |
| `DATA_SPLIT_ELIGIBLE` | 锁后资格变化、稳定 policy 数据不一致、仅由本批成员决定且可由更小批定位的 deterministic mutation/post-check failure | 非叶 split；leaf quarantine |
| `SAFETY_STOP` | 无法进入前两项 allowlist、audit/contract/invariant 故障、数据库结构/权限错误、重试基础设施自身异常 | 不 split、不建 failure group，终止本 domain 并告警 |

classifier 使用稳定错误码、SQLState/vendor code 与明确 transaction phase，不使用异常 message 模糊匹配。默认值必须是 `SAFETY_STOP`。connection failure 只有 transaction manager 明确证明 commit 尚未开始且 rollback 成功时才可视为 transient；其余一律 `UNKNOWN`。

disposition 必须作为稳定 ASCII token 写入 terminal attempt metrics（`failureDisposition`），并受 attempt 8 KB/非敏感契约约束。split/quarantine compound writer 在锁内要求该值为 `DATA_SPLIT_ELIGIBLE`；recovery 只能使用已持久化 token，不能根据当前异常或 message 重新分类。仍为 running 且尚无 terminal metrics 的 crash 状态按第 9.1 节末尾的保守 stop 处理。

同 scope 瞬时重试协议：

1. `website-published-v1` 固定 `maxTransientSameScopeRetries=2`；这是 policy 行为，不由请求或运行时配置扩大；
2. operation、attempt 和 item tree 均不改变，attempt 在多个已知回滚的物理 transaction 之间保持 `running`；不创建 child、related run、failure group或额外 candidate charge；
3. 每次重试重新调用 coordinator transaction port，重新 checkpoint gate/lease/deadline/remaining budget，重新锁审计树与业务行；不得复用旧锁内快照；
4. 可选短退避发生在 transaction 外且受原 run deadline 约束；
5. 终端 attempt metrics 记录 `transientRetryCount`，但只有最终 committed marker receipt 或 leaf terminal receipt 推进 candidate progress；
6. 任一次重试转为 `DATA_SPLIT_ELIGIBLE` 或 `UNKNOWN` 时立即切换对应路径；
7. transient 次数耗尽时，以 `WEBSITE_TRANSIENT_RETRY_EXHAUSTED` 将 attempt failed、operation rolled_back，candidate usage 为 0，停止本 domain；不自动 split、不创建 failure group。后续只能通过原 operation 的明确 RETRY/人工处置继续。

进程若在两次物理 retry 之间退出，普通 recovery 不猜测未持久化的 retry 次数：marker/evidence 先按第 11 节分类；无 marker 且全 PRE 时收敛为 rolled_back并停止，不自动 split。该保守路径允许少删，不允许扩大 mutation。

### 9.2 data-split-eligible 非叶

仅当 disposition=`DATA_SPLIT_ELIGIBLE` 且 `itemCount > 5`：

1. 调用第 6.4 节 compound writer，在一个 operation-locked transaction 中完成 parent attempt failed、完整 child tree 与 parent closure；
2. parent attempt business counts 为 0/0/0，warning=1，避免 child 完成后重复计 candidate；
3. compound writer 重放时只接受“全部未写”或“完整等价 tree”；任何半套/错配均 fail closed；
4. transaction commit 后才把 children 加入本 run 的待执行队列；
5. 继续按 child 顺序处理，仍受每次 transaction 前 gate/lease/budget 约束。

### 9.3 data-split-eligible leaf

仅当 disposition=`DATA_SPLIT_ELIGIBLE` 且 `itemCount <= 5`：

- 新 leaf：一个 operation-locked transaction 中完成 attempt failed、operation rolled_back、failure group、group items 和 active keys；
- 已有 exact active group 的 retry：更新同一 group failure code/error/time，不创建第二 group或第二 active key；
- leaf attempt counts：candidate=itemCount、affected=0、skipped=itemCount、warning=1；
- compound writer 从持久化 itemCount 生成第 4.3/8.3 节 `TrustedTerminalUsageReceipt`；session 验证并只消费一次后才继续；
- group failure code 使用稳定 ASCII token，错误文本 UTF-8 截断到 2 KB；
- run 可以结束为 failed，但 group/operation 保持永久待处置，不启动 365 天 detail retention。

### 9.4 unknown

发生 unknown：

1. 不 split，不 quarantine；
2. attempt/operation 按 WP3 受限 writer进入 unknown；
3. 立即停止 website domain 的后续 mutation；
4. 读取第 11 节 typed evidence facts；
5. marker+post 完整时可由恢复 writer确认 committed；
6. 其他状态保持 unknown/manual review 并告警；
7. WP8 canonical reconciliation 尚未接线前，不能通过构造临时 JSON 自动 resolution。

### 9.5 run result

WP3 当前 run rollup 只按 attempt terminal status 聚合。为避免一边保留真实 failed split attempt、一边把 run 宣称为无条件成功，WP6 首版冻结为：

- 未发生任何 rollback/quarantine/unknown，全部 operation committed：`ExecutionResultCode.COMPLETED`，run succeeded；
- 发生 DATA_SPLIT_ELIGIBLE rollback 后通过 child 全部释放：`ExecutionResultCode.PARTIAL`，run 仍因真实 failed parent attempt 记 failed；
- 存在 leaf failure group：`PARTIAL`，run failed；
- transient retry 耗尽或 SAFETY_STOP：`PARTIAL`（若此前已有处置）或失败结果，run failed，立即停止本 domain；
- unknown/manual review：抛领域未知结果错误，run failed；
- gate/lease/budget controlled stop：沿用 WP5 stop code 与 rollup。

响应、run status、attempt rollup 和 metrics 必须一致，不允许 domain 返回 COMPLETED 而持久化 run 为 failed。未来若要增加
`succeeded_with_retry` run 状态，必须另行评审 WP3 rollup/DDL/监控语义，不能在 WP6 静默改变。

---

## 10. failure group 与 exact retry

### 10.1 active group identity

自动 leaf group：

- `domain=website_published_content`；
- `operation_id/source_run_group_id` 指向该 leaf operation 当前 run relation；
- 成员与 operation batch items 完全相等；
- business key 使用无前导零十进制 article ID；
- sequence 与 operation item 顺序相同；
- 每个 active key 只能属于一个 active group。

group/item/status token 只允许 WP2 冻结值：

```text
group: active / resolved / regrouped
item:  active / resolved / moved
```

### 10.2 exact group retry

普通自动 scheduler 不消费一般 failure group。人工 retry 必须携带原 operation ID，并具备 execute retry 权限。

- group 仍 active 时可以 prepare retry，但 transaction preflight 必须锁并验证 exact group；
- 成功时按第 8.2 节原子关闭；
- transient known rollback 在同 attempt 内有界重试且 group 不变；耗尽时 group 保持 active并停止 domain；
- DATA_SPLIT_ELIGIBLE leaf rollback 时在 compound writer 内刷新同一 group failure evidence；
- SAFETY_STOP 时 group 保持 active，不把基础设施/合同错误改写成新的数据失败证据；
- unknown 时 group 保持 active；
- group membership 与 operation items 不完全相同则拒绝 exact retry。

### 10.3 首版处置边界

WP6 首版只承诺创建可靠 leaf failure group、阻止 fresh selector 重复占用、以及通过已有 WP5 RETRY facade 做 exact-scope retry。retry server-side 从 group/operation 永久事实读取原 scope，不接受 caller 上传 article IDs、hash 或新成员。

不允许仅凭 operator reason 把仍含待释放数据的 active group 标为 resolved。首版只有以下自动收敛：

1. exact retry + marker 成功时在 mutation transaction 内 resolved；
2. 已批准的 reconciliation 证明 marker/业务已 committed 时按其专属契约 resolved。

人工 resolve、1+4 regroup、跨 group merge、failure-group 管理 API/UI 和相应新增权限统一移入后续增强，不是 WP6 首版开发或代码完成阻断项。在增强 SPEC 获批前不得临时直写 group/item/active-key，也不得修改旧 operation items；未来 regroup 仍必须通过新 child operations 表达新成员集合。

---

## 11. website evidence 与恢复矩阵

### 11.1 typed facts，非 WP8 canonical document

WP6 生产 `WebsiteMutationEvidenceFacts`，只含非敏感事实：

```text
schemaVersion=1
operationId / hash / itemCount
markerState + marker contract match
每个 item 的 PRE / POST / MIXED / MISSING / INELIGIBLE 分类
locked/read snapshot time
lightweight evidence present flags/counts
```

不得包含正文、payload、raw response、URL 内容或数据库连接错误原文。

WP8 后续负责把这些 facts 映射为经过批准的 canonical evidence bytes/digest。WP6 不自行冻结另一个永久 JSON schema，也不绕过 WP3 `EvidenceDocument` 边界。

### 11.2 article state

```text
PRE
  文章/轻量 publish evidence 存在；仍满足安全资格；至少一个本 policy 清理目标仍热；
  没有只能由已提交本 operation 解释的完整后置状态

POST
  article status=deleted；本 policy 目标正文/payload/raw 均已清空；
  对原目标行 purge marker 完整；轻量 publish evidence/URL identity 仍保留

MIXED
  同 item 同时出现无法解释的前置/后置组合，或轻量 evidence 被破坏

MISSING/INELIGIBLE
  业务主行消失，或新的活跃业务使其不再允许 mutation
```

### 11.3 operation decision matrix

| marker | marker contract | all items | 自动结论 |
|---|---|---|---|
| present | exact | POST | committed recovery；如有 group 同事务关闭 |
| present | mismatch | 任意 | manual review + automation stop |
| present | exact | 非全 POST | manual review + automation stop |
| absent | n/a | 全 PRE | 可证明未提交；按已持久化 disposition 恢复，缺失 disposition 时 rolled_back + stop，不默认 split |
| absent | n/a | 全 POST | manual review；禁止补 marker |
| absent | n/a | MIXED/MISSING/不一致 | manual review + automation stop |

`INELIGIBLE` 只有在确认 mutation 未开始且无 marker的普通 prepared/leaf resolution 场景可解释为 no-longer-candidate；running unknown 仍保守进入 manual review。

### 11.4 running run recovery

| 永久事实 | WP6 动作 |
|---|---|
| empty run | 允许重新读取 fresh selection snapshot |
| prepared origin/retry/split，无 marker | gate/budget允许时恢复同 attempt；否则由 WP5 recovery_not_started 收敛 |
| running + marker exact + POST | recover succeeded，不重复 mutation |
| running + no marker + all PRE | recover failed/rolled_back；仅持久化 disposition 明确为 DATA_SPLIT_ELIGIBLE 时 split/quarantine，否则停止 |
| running + no marker + all POST/MIXED | unknown/manual review，停止 |
| failed rolled_back 非叶 + child完整 | 验证 tree 后继续；child 半套/缺失首版 fail closed，人工处理 |
| failed rolled_back leaf + group完整 | 验证 group 后保持隔离；group 半套/缺失首版 fail closed，人工处理 |
| committed/succeeded | 只重建 summary/progress |
| 任一 tree/hash/lineage/group 矛盾 | fail closed，不自动 repair |

恢复分类可在 gate=false 时做 audit-only read/terminalization；任何新 business mutation 仍必须紧邻动作重新通过 session checkpoint。

---

## 12. dry-run 与入口接线

### 12.1 dry-run

官网 dry-run：

- 不获取 execute lease；
- 不要求静态/dynamic execute=true；
- 要求现有 read/dry-run 权限；
- 使用 WP3 mode=`dry_run` 顶层 run；
- 分页使用 operationless `PAGE` attempt；
- 不创建 operation/item/marker/failure group；
- 响应可返回候选 ID 和估算 bytes，但这些字段不持久化到 run/attempt JSON；
- scheduler 一次 website dry-run invocation 只创建一条 run，分页在 service 内完成。

### 12.2 execute controller

保留现有路径：

```text
POST /api/data-retention/articles/website-published/cleanup
```

request 只接受：

```text
retentionHours
limit
reason
triggerId
approvalRef
ticketRef
```

`cursorArticleId` 对新 execute 禁止；cursor 仅可在兼容 dry-run 使用。fingerprint、policy、cutoff、upperBound、article IDs、budget 和 target policy 全部 server-side 生成。

### 12.3 legacy service 收口

允许把旧 service 拆成：

```text
WebsiteRetentionCandidateReader
WebsiteRetentionMutationRepository
WebsiteRetentionDryRunService
```

旧 `cleanup`、`runScheduled(..., dryRun=false)` 必须删除、变为不可注入的 internal helper，或强制要求 `RetentionExecutionSession`；不能留一个带静态 boolean 的 public execute 方法。

架构测试扫描：

- controller/scheduler 不依赖 mutation repository；
- mutation repository 只被 website executor 使用；
- WP6 不注入 legacy audit、通用 TransactionTemplate、Redis template 或 dynamic switch writer；
- marker 只通过 WP3 MANDATORY port；
- no executor 时仍 fail closed，不 fallback。

---

## 13. 错误码、指标、日志与告警

### 13.1 stable errors

```text
WEBSITE_SELECTION_INVALID
WEBSITE_OPERATION_TREE_CONFLICT
WEBSITE_BATCH_STATE_CHANGED
WEBSITE_BATCH_KNOWN_ROLLBACK
WEBSITE_TRANSIENT_RETRY_EXHAUSTED
WEBSITE_ROLLBACK_DISPOSITION_UNCLASSIFIED
WEBSITE_BATCH_OUTCOME_UNKNOWN
WEBSITE_POST_CHECK_FAILED
WEBSITE_MARKER_CONTRACT_MISMATCH
WEBSITE_SPLIT_TREE_CONFLICT
WEBSITE_FAILURE_GROUP_CONFLICT
WEBSITE_TERMINAL_USAGE_RECEIPT_CONFLICT
WEBSITE_PAYLOAD_PURGE_MARKER_CONFLICT
WEBSITE_RECOVERY_EVIDENCE_CONFLICT
WEBSITE_POLICY_UNSUPPORTED
```

HTTP 映射遵循 WP5：请求错误 400、幂等/状态冲突 409、安全依赖/未知结果 503。错误消息不拼正文/payload/raw response。

### 13.2 minimum metrics

```text
retention_website_selection_total{source,result}
retention_website_selected_items{source}
retention_website_batch_total{size,type,outcome}
retention_website_batch_seconds{size,type}
retention_website_transient_retry_total{code,result}
retention_website_split_total{parent_size,left_size,right_size,result}
retention_website_failure_group_total{action,result}
retention_website_terminal_usage_total{result}
retention_website_payload_reheated_total{writer,result}
retention_website_business_rows_total{kind}
retention_website_post_check_total{result}
retention_website_recovery_total{state,result}
retention_website_evidence_total{marker_state,business_state,decision}
```

高优先级 alert：

- unknown outcome；
- marker/business/tree mismatch；
- mixed state；
- failure active-key inconsistency；
- repeated leaf failure；
- policy/config unsupported；
- lock timeout/deadlock 超过运行阈值；
- required post-check 或 audit writer 失败。

普通单次 transient rollback 可 warning；重试耗尽必须告警并停止 domain。进入 split/leaf group 必须携带稳定 disposition，未分类 known rollback 不能静默拆分。

---

## 14. Required 测试矩阵

### 14.1 unit/contract

1. website fingerprint 固定文本和 SHA-256 与 WP2 向量一致；
2. website scope 5-item 固定向量为 `14a67e69...3abbe7`；
3. 20、10 的 deterministic split；6/9/11/17 operation tree 拒绝；
4. 17 个候选规范化形成 10、5、2 三个 operation；
5. child union/no-overlap/lineage/contract validation；
6. natural tail 1～4 不继续拆；
7. outcome 与 disposition 独立：deadlock/lock timeout/已知未提交连接失败不 split，稳定数据失败才 split，未知仍停止；
8. transient 同 scope 最多重试 2 次，第三次耗尽后 operation rolled_back、domain stop、无 child/group；
9. known rollback parent metrics 不重复 candidate；leaf group 计 candidate/skipped；
10. callback actual 与 commit `TrustedMutationReceipt` usage 不一致整体失败；
11. forged/mismatched/重复 `TrustedTerminalUsageReceipt` 拒绝或 no-op，不重复 candidate；合法 receipt 立即推进当前 session；
12. 重启聚合只从一次 leaf attempt metrics 恢复，结果与未重启 session 完全一致；
13. sensitive evidence/log rejection；
14. request cursor/article IDs/policy injection 拒绝；
15. legacy execute bypass architecture test；
16. review poll 写回 `response_payload` 时，同一 update wrapper 复位 `payload_purged_at`；
17. 第一批提交后，第二批分别遇到预算耗尽、运行中关闭 dynamic gate、Redis unavailable 或 dynamic state malformed 时，callback 与 `markAttemptRunning` 均不执行，prepared attempt 收敛为 `skipped + rolled_back + recovery_not_started`；全部 dynamic fail-closed decision 对外统一映射 `EXECUTE_DYNAMIC_DISABLED`，原 decision code 保留为 `stopReason`，未执行候选不计 business skipped/warning。
18. dry-run 一次请求只创建一条 typed run；多页对应串联的 operationless `PAGE` attempt；不创建 operation/item/marker/failure group，也不在 run/attempt JSON 持久化候选 ID 或估算 bytes。

### 14.2 real MySQL 8 business safety

至少覆盖：

1. brand/industry/forum 三类成功官网记录；
2. missing/manage/pending URL 不影响 eligibility，URL 本身保留；
3. active distribution task 阻断；
4. nonterminal self-media schedule 阻断；
5. submitted + durable website evidence 的既有兼容规则；
6. cutoff 恰好边界及 retentionHours 最小 24；
7. JVM UTC/Asia-Shanghai/其他 zone 与 MySQL session zone 组合结果一致；
8. 一次 20 篇 transaction 的正文/payload/raw/status 与 marker 同时提交；
9. 任一 item 锁后失去资格，全 20 rollback；
10. post-check 后注入异常，业务/group/marker/attempt 全 rollback；
11. marker 写入失败，全业务 rollback；
12. 无关 article 并发新增 task 不影响本批 post-check；
13. 本批 article 并发业务 writer 被行锁串行化；
14. 两个重叠 operation 不会重复 marker或反转锁序；
15. operation/item/hash 被篡改时 mutation 前失败；
16. 轻量 article/version/task/publish/URL 行不被删除；
17. self-media 与非官网 payload 不被清理；
18. payload marker 一致性 migration 修复历史半套行、精确 CHECK guard、repair/re-migrate/no-op；
19. 无正文、只有官网 task payload 的 article 首次 cleanup 后变为 deleted且写入 WP6 commit；晚到 review/publish writer 写回非空 payload并原子复位 marker；下一 run 不依赖 content marker/batch item，凭 `deleted + 实际 payload + 成功发布证据 + cutoff/hold 合法` 再次命中并清空；
20. writer 只写 payload、不复位 marker 时由 CHECK 拒绝，payload 与 marker 整体回滚；
21. 在 pre-migration 兼容基线中构造“payload 非空 + 旧 marker 非空”，先证明 selector 以实际 payload 为准，再证明 migration 只复位 marker且后续 CHECK 阻止该形状重现。

### 14.3 split/failure group

1. 20 个稳定 data-split-eligible rollback → 两个 10 child → 四个 5 leaf；
2. 一个 poison item 最多隔离其 leaf 内其余 4 个对象；
3. 自动链永不创建 1-item child 来定位 poison；
4. leaf failure 的 attempt/rolled_back/group/items/active keys 原子提交；
5. leaf writer 中途失败整体 rollback；
6. active key PK 防止跨 group 重复；
7. fresh selector 跳过 active/unknown/pending holds 并继续后续候选；
8. exact group retry 成功时业务+group closure+marker原子；
9. exact group retry失败时原 group保持 active；
10. leaf quarantine writer commit 后返回可信 terminal receipt，当前 session candidate 余额立即减少；
11. receipt 校验失败时停止 domain，不在内存中猜测 usage。

### 14.4 recovery/unknown

1. operation prepared 后崩溃；
2. attempt running、mutation 前崩溃；
3. mutation+marker commit 后、run finish 前崩溃；
4. marker exact + all POST → succeeded recovery；
5. marker absent + all PRE → rolled_back；没有持久化 split disposition 时停止而非默认拆分；
6. marker absent + all POST → manual review；
7. marker absent + MIXED → automation stop；
8. marker mismatch/业务冲突 → automation stop；
9. recovery 不重置原 budget/deadline/cutoff/upperBound；
10. gate=false 时允许 audit recovery但不允许 mutation recovery。

完整 child/group 半套崩溃树自动 repair、人工 regroup 与 WP8 canonical evidence vector 属于增强测试，不是 WP6 首版 required profile 阻断项；发现这些状态时 required 行为是 fail closed、保留永久事实并告警。

### 14.5 coordinator integration

新增 required profile（名称建议 `retention-website-it`），至少组合：

- WP3 MySQL integration；
- WP4 Redis/MySQL gate；
- WP5 coordinator/lease/V364；
- WP6 website business/split/failure/recovery/payload-guard migration tests。

profile 必须在 Docker 与外部 MySQL/Redis 均不可用时失败，0 skipped；普通开发 test 可显式 skip 容器类。

外部服务模式使用以下环境变量，不在仓库中保存凭据：

```text
RETENTION_MYSQL_IT_SERVER_URL / RETENTION_MYSQL_IT_USER / RETENTION_MYSQL_IT_PASSWORD
RETENTION_REDIS_IT_HOST / RETENTION_REDIS_IT_PORT / RETENTION_REDIS_IT_USERNAME
RETENTION_REDIS_IT_PASSWORD / RETENTION_REDIS_IT_DATABASE
```

外部 MySQL 模式只创建并删除随机 `geo_retention_*_it_*` 数据库；外部 Redis 必须使用独立 DB，测试只清理 WP4/WP5/WP6 固定 key，禁止 `FLUSHALL` 或修改共享 ACL。

真实 coordinator 场景至少证明：

```text
lease competition
→ dynamic gate
→ one run
→ website operation/attempt/items
→ real business mutation
→ post-check
→ marker/commit receipt/budget
→ run rollup
```

并证明 executor 缺失、gate关闭、dynamic gate Redis unavailable/state malformed、lease丢失或预算耗尽不会回退 legacy mutation；其中第二批在 checkpoint 遇到预算耗尽或任一 dynamic fail-closed decision 时，必须验证 attempt 保持 prepared 直至 checkpoint 成功，失败后原子形成 `skipped + rolled_back + recovery_not_started`。另一个真实场景必须证明 leaf compound writer 的 terminal receipt 在同一 session 立即减少 candidate 余额，随后不会超预算继续选择。

### 14.6 performance/production clone preparation

- 对 candidate/hold query、operation tree、failure lookup 做 MySQL 8 `EXPLAIN ANALYZE`；
- 20 篇典型/大 payload batch 在 transaction timeout 内完成；
- 记录锁行数、SQL 次数、P50/P95/P99 transaction time；
- 记录清理前后 live large-field bytes/non-null rows 与 InnoDB 可复用空间，不以 `.ibd`/OS 文件立即缩小作为通过条件；
- 验证索引不足时只通过新 migration 添加，不改 V330/V354；
- 生产克隆至少 10 批×5、50 批规模及 poison/split 场景留给最终准入复跑。

---

## 15. Migration 与兼容策略

WP6 不改变 V354 核心表，但为封闭 payload 晚写回漏清必须新建 migration；其他需求也只能使用新 migration：

- `distribution_tasks` payload/`payload_purged_at` 历史 marker 修复与一致性 CHECK；
- candidate hold 或 recovery query 的必要索引；
- 真实 MySQL 8 证明不可缺少的约束补强。

规则：

1. 开发开始时重新扫描最高 Flyway 号；
2. 不修改已存在 migration checksum；
3. migration 必须 exact metadata guard、repair/re-migrate/no-op test；
4. payload migration 必须证明只把不一致 marker 复位为 NULL，不修改或复制 payload；
5. CHECK 保留 SQL NULL/JSON literal null 的精确语义，签名比较不得破坏字符串/表达式语义；
6. 索引使用真实 column order/signature 检查；
7. V133 历史链仍单独报告，不以 V353 baseline 测试冒充 V1 全链通过。

legacy 行兼容：

- 生产已经完成的历史单篇清理没有 WP6 marker，不回填伪 marker；
- selector 按实际大字段与业务后置状态判断；历史 marker 不能遮蔽晚写回 payload；
- 只有 WP6 新 operation 才必须有 item/attempt/marker/failure治理；
- 不把历史 `data_retention_run` 转换成新 run/operation。

---

## 16. 实施顺序

1. 评审并批准本 SPEC；
2. 重扫 dirty worktree、最高 migration 和并行占用；
3. 补 website typed audit locks、leaf compound writer、`TrustedTerminalUsageReceipt` 与 recovery view；
4. 完成 candidate/time/snapshot、实际 payload selector 与 dry-run typed audit；
5. 修订全部 payload writer并新增 marker 一致性 migration；
6. 完成 batch mutation + post-check + commit marker receipt；
7. 完成 rollback disposition、bounded same-scope transient retry；
8. 完成 deterministic split 与 parent closure；
9. 完成 leaf failure group与 exact retry；
10. 完成 website evidence facts 与主链 recovery state machine；
11. 注册 website executor并封闭 legacy execute；
12. 跑 clean compile、unit/contract/architecture；
13. 通过 Docker 或显式外部连接运行 required MySQL 8/Redis profile；
14. 更新 SPEC implementation notes 和测试证据；
15. 单独进入代码评审；
16. WP7/WP8、V133、生产克隆与 canary 完成前保持全部 execute false。

### 16.1 2026-08-14 本地实施证据

- clean compile + WP6 聚焦集：26 tests，0 failure，0 error，0 skipped；
- retention 包扩展回归（含 payload writer）：157 tests，0 failure，0 error；普通开发模式下 61 个基础设施测试按约定 skipped；
- `retention-website-it` required profile：本地 MySQL 8.0.45 + Redis 独立 DB 15，71 tests，0 failure，0 error，0 skipped；
- 第 71 项使用真实 `DataRetentionExecuteCoordinator`、真实 `WebsiteRetentionDomainExecutor`、真实 `WebsiteRetentionBusinessRepository` 和 WP3 audit transaction 执行 20 篇业务批次；不 mock business repository，并验证真实业务 mutation、locked-row post-check、V366 CHECK、commit marker/budget/run rollup；
- 该真实批次含一篇 `deleted + 无正文 + 仅 response_payload + payload_purged_at=NULL` 的轮询复热状态，验证其重新入选后 payload 再次清空且 marker 重建；原 70 项结果不再单独作为这条 WP6 真实业务链路的通过证据；
- required 实跑同时关闭了三项测试脚手架缺口：外部 WP4/WP5/V364/V366 服务接入、历史 V5 BOM 读取、MySQL 3819 CHECK 异常跨驱动映射；
- 外部模式使用随机测试数据库并在 suite 结束后删除；Redis 不再执行共享 `FLUSHALL`/ACL 修改，只清理测试 key；
- typed dry-run 单元/JDBC/architecture 已覆盖一请求一 run、多页 `PAGE` attempt 链、失败收敛、响应字段不落 JSON、scheduler 单次委托及 shared eligibility SQL；
- `git diff --check` 通过；SPEC 1191 个物理行、60 个围栏且成对、直接扫描尾随空白为 0；最高 migration 为 V366；execute/enable-write 默认值仍全部为 `false`。

因此当前可以认定“WP6 本地代码实现、聚焦复审和声明的 required profile 完成”，但不能勾选空间回收、生产克隆和生产准入相关验收项。

---

## 17. WP6 验收清单

- [x] 恰好一个 website domain executor 注册；
- [x] controller/scheduler execute 只走 coordinator，无 legacy fallback；
- [x] dry-run 一次请求一条 typed run，不获取 execute lease；
- [ ] cutoff/upperBound 可跨重启、跨 JVM/DB 时区重现；
- [ ] fresh operation size 只为 20/10/5/1～4 tail，canary ≤5；
- [ ] deterministic split、child identity 和 parent closure 完整；
- [ ] transaction 开头按 operation→gate→attempt→item 锁定审计树；
- [ ] 业务表锁序固定且多 article 数值升序；
- [ ] eligibility 锁内重验，任一失败整批 rollback；
- [ ] mutation allowlist 与 lightweight evidence 保留通过；
- [ ] locked-rows-only post-check 不受无关并发影响；
- [ ] business/group/marker/operation/attempt 同事务；
- [ ] commit receipt 与 terminal usage receipt 是 candidate budget 的两类唯一可信事实源，当前 session 与重启聚合一致；
- [ ] transient known rollback 只做原 scope 有界重试，耗尽后停止，不创建 split/group；
- [ ] 只有 DATA_SPLIT_ELIGIBLE 进入 split/leaf，UNKNOWN 或未分类错误不会拆分重试；
- [ ] leaf quarantine 与 active keys 原子且幂等；
- [ ] exact group retry 成功原子关闭 group；
- [ ] marker/business 四分支 evidence matrix通过；
- [ ] running 主链 recovery 不重选、不重置预算；半套 split/group tree 首版 fail closed；
- [x] 无正文/仅 payload 场景中，晚写 writer 原子复位 marker，deleted 文章凭实际目标与成功发布/cutoff/hold 重新入选，cleanup→写回→再清理通过；
- [ ] logical large-field bytes 明显下降且轻量行保留，不以 `.ibd`/OS 文件立即缩小作为 WP6 条件；
- [ ] canonical fingerprint/scope 固定向量通过；
- [x] MySQL 8/Redis required profile 0 skipped 通过；
- [x] `git diff --check`、围栏、尾随空白、默认 false 检查通过；
- [x] 未修改无关 dirty files，未执行生产迁移/清理；
- [x] V133、WP4/WP5容器、WP8和生产克隆阻断仍清晰保留。

---

## 18. 聚焦评审结论

以下七项已完成聚焦评审并按冻结结论实现：

1. **split identity**：child 使用新 operation，父在完整 child coverage 后 `closed_no_mutation`；
2. **tail rule**：自然尾批 1～4 合法，但任何大组自动 split 不降成单篇；
3. **snapshot rule**：同 run 固定 cutoff 与首次持久化的 selection upperBound，后续无永久 cursor；
4. **rollback disposition**：transient 对原 scope 最多重试 2 次，只有稳定 DATA_SPLIT_ELIGIBLE 才拆分，耗尽/未分类停止 domain；
5. **leaf usage**：quarantine compound writer 返回数据库可复核 terminal receipt，当前 session 与重启各只计一次 candidate；
6. **payload reheat**：任一非空 payload 写回原子复位 marker；`deleted` 不覆盖实际热字段，满足成功发布/cutoff/hold 的文章无需依赖可清理 batch item即可重新入选；
7. **scope boundary**：首版保留 20→10→5、leaf、exact retry 和原子 marker；人工 regroup、完整半套树 repair、WP8 canonical evidence 后置。

三轮相邻 P1 已闭环；最终复审确认此前“WP6 没有真实业务主链证据”的 P1 已关闭，本轮未发现新的 P1/P2。正式结论为：**WP6 本地代码实现、聚焦复审及本地 MySQL 8/Redis required profile 验收完成。** 该结论不等于生产准入；V133、WP8、生产克隆与 canary 仍保持阻断，且不属于 WP6 代码完成范围。
