# 数据定期清理 P0 WP4 动态执行开关 SPEC（评审通过实施基线）

> 文档日期：2026-08-12
> 当前状态：聚焦复审通过；允许开展 WP4 Java、Lua、权限迁移与测试开发
> 上游基线：`data-retention-p0-wp2-ddl-spec-2026-08-11.md` 第五次评审通过版、`data-retention-p0-wp3-audit-service-spec-2026-08-12.md` 第三次评审通过版、`V354__data_retention_execute_governance.sql` 当前实现
> 授权边界：仅授权 WP4 Java、Lua、权限迁移与测试开发；不授权打开任何 execute 开关、接入 WP5 业务执行路径、执行生产迁移或生产清理，也不解除 V133 生产准入阻断

---

## 0. 结论摘要

WP4 把 WP2 已建立的 `data_retention_dynamic_switch_change` 永久审计表实现为一套 Redis 运行时开关、MySQL 审计、CAS、对账、权限分级和 fail-closed 服务，为 WP5 的统一协调器提供不可歧义的只读门禁。

冻结的核心模型是：

```text
MySQL requested（短事务提交）
→ Redis CAS（不持有 MySQL 事务）
→ MySQL applied / failed（短事务提交）
```

运行时判定遵循：

```text
静态全局/领域 execute 配置
AND Redis global_execute
AND Redis domain_execute
```

其中静态配置由 WP5 校验；WP4 只负责后两个 Redis 动态状态。Redis 是运行时状态源，MySQL 是不可删除的请求、审批、CAS 观察和最终一致性审计。Redis 缺失、超时、连接失败、脚本失败或 payload 非法时一律按关闭处理。

WP4 还冻结以下安全边界：

1. enable 使用申请人与审批人分离的双人流程；
2. 后台对账可以确认一个已经写入 Redis 的 enable，但永远不能自动执行 `false → true`；
3. disable 可以由人工、紧急入口或治理自动化发起，并允许后台安全重试；
4. `enabled=true` 不做任何 JVM 正向缓存；WP4 v1 对 disabled 也不缓存；
5. Redis key 无 TTL，Redis 重启或 key 丢失自然回到 fail-closed；
6. 开关变更不写 run、operation、attempt、commit marker 或 reconciliation 表；
7. WP4 不接入现有清理 controller/scheduler，不改变任何现有 execute 默认值；统一接线属于 WP5～WP7。

WP2 的真实 V1→V354 空库历史链仍在 V133 阻断。该问题不阻止 WP4 SPEC 评审和经批准后的本地开发，但在历史链修复或正式豁免、生产 Redis/MySQL 验收以及 WP5～WP8 准入完成前，不得把 WP4 或整个 P0 标记为生产就绪。

### 0.1 第一次评审修订

| 评审问题 | 本版修订 |
|---|---|
| enable 申请与 approval 三元组定义冲突 | `approval_ref` 明确为申请阶段即持久化且不可变的审批引用；审批事实仅由 `approved_by/approved_at` 二元组表示，二者必须全空或全非空 |
| 历史 applied=true 可能掩盖 Redis 版本回退 | 一致状态必须满足 Redis version 等于该 switch 的 MySQL 最大合法 audit version；Redis version 更低一律视为 rollback，true 立即按 unsafe divergence 补偿 disable |
| 显式 approval replay 可越过 enable grace | 所有 enable CAS 共用唯一 writer，并由 Redis Lua 使用 Redis `TIME` 强制校验 `approved_at + grace`；到期不写 true，MySQL guarded transition 为 failed |
| gate 多异常优先级不明确 | 冻结 primary decision 优先级和双 snapshot integrity；即使 global=false，domain malformed 仍优先返回 malformed 并立即告警 |

以上内容记录第一次评审后的历史状态；最终授权以第 0.3、21 节为准。

### 0.2 第二次评审修订

| 评审问题 | 本版修订 |
|---|---|
| grace 使用未绑定的 MySQL/JVM 与 Redis 时钟 | enable 申请 `changed_at`、审批 `approved_at`、30 分钟申请有效期和 60 秒 CAS grace 全部统一使用 Redis `TIME`；Redis 不可读时禁止创建或审批 enable，disable requested 仍可安全落库 |

本次只收口 enable 时间权威及其故障/测试契约，不调整第一次评审已经闭环的审批二元组、version rollback、唯一 enable writer、gate 优先级或其余 WP4 架构。本句记录当轮历史状态；最终授权以第 0.3、21 节为准。

### 0.3 聚焦复审结论

聚焦复审确认 grace 的申请、审批和 CAS 时间均已统一绑定 Redis `TIME`，且前一轮审批二元组、version rollback、唯一 enable writer、gate 多异常优先级修订继续成立。本文自此作为 WP4 实施基线；授权范围仍严格排除 WP5 接线、任何 execute 默认值变更和生产操作。

---

## 1. 当前实现事实与差距

### 1.1 已有基础

当前仓库已经具备：

- Spring Data Redis 与 `StringRedisTemplate`；
- Redis Lua 脚本惯例；
- 本地 `docker-compose.yml` 的 Redis 7 和 MySQL 8；
- `CurrentUserService.ensurePermission(...)` 与数据库型 `sys_permission/sys_role_permission`；
- `/api/data-retention` controller 前缀；
- WP2 的 `data_retention_dynamic_switch_change`；
- WP3 的 typed run/operation/attempt/marker 审计服务；
- 以下所有静态 execute 默认值均为 `false`：
  - `geo.retention.scheduler.execute-enabled`；
  - `geo.retention.website-published-cleanup.execute-enabled`；
  - `geo.retention.poll-results.execute-enabled`。

### 1.2 当前缺口

当前仓库没有：

- 动态 switch key 枚举和 Redis 物理 key 契约；
- Redis payload 编解码与原子 CAS；
- global + domain 的原子读取门禁；
- requested → applied/failed 状态机；
- 崩溃后 requested 对账；
- enable 双人审批；
- disable、emergency disable、enable、approve、reconcile 的独立权限；
- 动态开关管理 API；
- WP5 可消费的 typed gate；
- Redis/MySQL 联合集成和故障注入测试。

### 1.3 V354 能力边界

`data_retention_dynamic_switch_change` 已包含：

- `change_id` 唯一键；
- `(switch_key, version)` 唯一键；
- `enabled/status/changed_at/requested_by/request_source/reason`；
- `approval_ref/approved_by/approved_at/ticket_ref`；
- `redis_observed_version/redis_observed_enabled/last_error`；
- `applied_at/failed_at/created_at/updated_at`。

因此 WP4 不新增或修改 retention 治理表、列、索引、CHECK 或 FK。

但权限分级需要向已有 RBAC 表增加 permission seed/grant。这是 WP4 的数据迁移，不是治理表 DDL。开发获批后必须重新扫描全部 refs、worktree 和未跟踪迁移，再确定 `<NEXT_VERSION>__data_retention_dynamic_switch_permissions.sql`；本文不提前占用当前最高版本之后的具体迁移号。

---

## 2. 目标、范围与非范围

### 2.1 WP4 目标

1. 冻结三个动态 switch 的逻辑名称、Redis key 和 payload v1；
2. 实现 global + domain 的原子运行时读取和 fail-closed typed gate；
3. 实现 MySQL requested → Redis CAS → MySQL terminal 的可恢复协议；
4. 实现 change ID 幂等、版本单调、并发写入和旧 writer 防覆盖；
5. 实现 enable 双人审批及严格的权限矩阵；
6. 实现 requested 对账和 Redis/MySQL divergence 检测；
7. 保证自动化只允许 `true → false`，不允许 `false → true`；
8. 为 WP5 提供 lease 后及每个 mutation 前可调用的只读门禁；
9. 在真实 Redis 7 + MySQL 8 上验证并发、故障恢复、UTC 和边界行为；
10. 保持所有静态 execute 配置和生产动态状态关闭。

### 2.2 WP4 范围

- switch key、payload、Redis key schema 和 Lua 脚本；
- dynamic switch runtime gate；
- switch change repository 与状态机；
- enable 申请/审批、普通 disable、紧急 disable；
- 内部治理 `reconcile` disable port；
- requested/background consistency reconciler；
- 管理面查询和变更 API；
- WP4 RBAC permission seed/grant 迁移；
- 指标、结构化日志和告警事件端口；
- 单元、SQL/架构契约、Redis/MySQL 集成测试。

### 2.3 WP4 非范围

- 修改 V354 或任何已发布 migration checksum；
- 修复或豁免 V133；
- `DataRetentionExecuteCoordinator`、execute lease 和运行预算；
- 创建/结束 run 或写 operation、attempt、item、commit、poll marker、failure group、unknown outcome reconciliation；
- 官网 20→10→5、poll 清理、安全谓词或业务删除；
- WP8 容量、积压、425 天等 blocker 的具体计算；WP4 只提供可被 blocker 调用的自动 disable port；
- 外部工单/审批系统本身；WP4 只校验和持久化引用，并执行应用内第二人审批；
- 前端管理页面；
- 修改现有人工 execute、scheduler、canary、retry 或 reconcile 入口；
- 设置任意静态 execute 为 true；
- 生产 Redis ACL、持久化或集群变更；本文只冻结其准入要求。

---

## 3. 冻结不变量

1. Redis 是动态开关运行时状态源；热路径不从“最新 MySQL applied”推导 enabled。
2. 所有 WP4 应用和自动化路径都必须先持久化 `requested`，Redis 写入之前不得缺少对应 change audit；第 18.3 节人工灾难恢复是唯一外部 break-glass 例外，必须按 P0 事件补录。
3. Redis CAS 时不得持有 MySQL 行锁或数据库事务；MySQL 与 Redis 之间不伪装成分布式事务。
4. Redis 写入成功、MySQL terminal 更新失败时，行保持 `requested`，后续凭完全相同的 Redis payload 补记 `applied`。
5. Redis 不可读时行保持 `requested`，运行时门禁按 disabled，且必须告警。
6. enable 必须由两个不同的有效内部用户完成：申请人具有 enable 权限，审批人具有 approve 权限。
7. enable 的后台任务只允许把“Redis 已经完全等于目标 payload”的行补记为 applied；不得执行 enable CAS。
8. disable 的后台任务允许执行 CAS 和重试；所有容量、积压、治理异常自动化只能发起 disable。
9. 任何旧版本请求都不得覆盖 Redis 中更高版本；较新的 disable 必须能够压过仍在飞行中的旧 enable。
10. Redis key 缺失等价于隐式 `(version=0, enabled=false)`；不得因 MySQL 旧 applied=true 自动重建 true。
11. Redis key 永不设置 TTL；任何代码不得 `EXPIRE`、`SET` 覆盖 hash，或在读取时隐式创建 key。
12. runtime gate 一次 Lua 调用原子读取 global 和对应 domain；两个 key 必须位于同一 Redis Cluster hash slot。
13. `enabled=true` 不做 JVM、本地或 Spring Cache 正向缓存。WP4 v1 对 false 同样不缓存。
14. switch change 永久独立于 run；`change_id`、switch key、version 和业务请求字段终态后不可变。
15. applied/failed 都是 terminal；terminal 行不得重新回到 requested，也不得改变 applied/failed 结论。
16. 同一 change ID 的完全相同命令可以幂等返回；任一语义字段不同必须冲突。
17. Redis 中合法的 true 不代表获准打开生产 execute；静态开关、lease、预算、领域安全条件和生产准入仍必须全部通过。
18. 动态 disable 的生效边界是“阻止下一次 gate 检查后的新 mutation 事务”；它不回滚已经开始的数据库事务，也不是 fencing token。
19. WP4 的失败不得导致清理执行降级绕过；调用方只可停止、skip 或返回明确错误。
20. 所有时间以 UTC `Instant` 处理并截断到毫秒；enable 安全窗口的唯一时间权威是 Redis `TIME`，不得混入 JVM/MySQL 当前时间；其他审计时间也不得依赖 MySQL session 或 Jackson 的 Asia/Shanghai 默认时区。

---

## 4. Switch key 与 Redis v1 契约

### 4.1 逻辑 switch key

WP4 v1 只允许以下三个枚举值，不接受调用方自由字符串：

| Java 枚举 | `switch_key` 持久化值 | 语义 |
|---|---|---|
| `GLOBAL_EXECUTE` | `global_execute` | 所有 P0 retention mutation 的全局动态门禁 |
| `WEBSITE_PUBLISHED_CONTENT_EXECUTE` | `website_published_content_execute` | 官网已发布内容清理领域门禁 |
| `POLL_RESULTS_EXECUTE` | `poll_results_execute` | poll results 清理领域门禁 |

domain 映射固定为：

| WP3 domain | domain switch |
|---|---|
| `website_published_content` | `website_published_content_execute` |
| `poll_results` | `poll_results_execute` |

未知 domain、大小写变体、前后空白、别名或后续新增领域全部拒绝；新增 key 必须提升 Redis schema namespace 并重新评审，不能只在数据库写入新字符串。

### 4.2 Redis 物理 key

物理 key 固定为：

```text
geo:data-retention:{execute-switch}:v1:global_execute
geo:data-retention:{execute-switch}:v1:website_published_content_execute
geo:data-retention:{execute-switch}:v1:poll_results_execute
```

`{execute-switch}` 是共同 Redis Cluster hash tag，使一次 Lua 脚本可以原子读取 global + domain。prefix 不是可任意修改的生产配置；测试可以通过显式 test namespace 隔离，但必须保留相同 suffix、hash tag 和字段协议。

### 4.3 Redis 数据类型和字段

每个物理 key 是一个无 TTL 的 Redis HASH，且只允许四个字段：

| field | 编码 | 约束 |
|---|---|---|
| `change_id` | lowercase canonical UUID ASCII | 必须等于 MySQL `change_id` |
| `version` | 无前导零十进制 ASCII | `1..Long.MAX_VALUE`；缺失 key 的隐式版本为 0 |
| `enabled` | ASCII `0` 或 `1` | 禁止 `true/false`、其他数字或空串 |
| `changed_at` | UTC ASCII | 精确格式 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` |

示例：

```text
change_id = 0f4e3df1-331b-4c61-9b94-8f53866d02da
version   = 17
enabled   = 0
changed_at= 2026-08-12T06:30:45.123Z
```

禁止额外字段。额外字段、半空 hash、字段编码错误、UUID 非 canonical、version 越界、时间非 UTC/非毫秒精度都属于 malformed，不得由读取器“尽量解析”。

Lua 中禁止用 `tonumber(version)` 比较版本，因为 Redis Lua 5.1 number 不能精确表达所有 Java long。脚本必须先验证 canonical decimal，再按长度和字典序比较；单元与契约测试覆盖 `9007199254740993` 和 `Long.MAX_VALUE`。

Lua 必须同样校验传入的 target/expected 参数，不能因为参数来自 Java 就跳过 canonical 检查。所有 Redis 脚本都必须以 Redis 7 当前 Lua 运行时为准，不依赖 Redis Functions、客户端事务或模块扩展。

### 4.4 缺失 key

完全不存在的 key 被读取为：

```text
version=0
enabled=false
changeId=null
changedAt=null
origin=missing
```

读取缺失 key 不写 Redis、不写 MySQL。首次显式变更创建 MySQL version 1 或更高版本；首次 enable 只允许从该隐式 false 状态 CAS。

如果历史 MySQL 最新 applied 为 true 而 Redis key 缺失，运行时仍为 false。对账只能生成新的 disable 审计以收敛到 false，不能从 MySQL 把 true 恢复进 Redis。

---

## 5. Runtime gate

### 5.1 Java 端口

WP4 对 WP5 暴露最小 typed API：

```java
public interface DataRetentionDynamicSwitchGate {
    DynamicSwitchDecision check(RetentionDomain domain);
    DynamicSwitchDecision requireEnabled(RetentionDomain domain);
}

public record DynamicSwitchDecision(
        RetentionDomain domain,
        boolean enabled,
        DynamicSwitchDecisionCode code,
        Instant checkedAt,
        DynamicSwitchSnapshot global,
        DynamicSwitchSnapshot domainSwitch) {}
```

`RetentionDomain` 必须复用或单向映射 WP3 的两个 P0 domain；不得暴露 `check(String)`。

`requireEnabled` 在非 enabled 时抛 typed exception，供 WP5 映射为 scheduler skipped、人工 409 或 mutation 中止；禁止返回 null 或让调用方自己猜错误原因。

### 5.2 决策码

固定 decision code：

| code | enabled | 条件 |
|---|---:|---|
| `ENABLED` | true | global 和 domain 都是合法 `enabled=1` |
| `GLOBAL_DISABLED` | false | global/domain integrity 都可判定为 `VALID/MISSING`，global 为 false |
| `DOMAIN_DISABLED` | false | global/domain integrity 都可判定为 `VALID/MISSING`，global true、domain false |
| `REDIS_UNAVAILABLE` | false | timeout、连接失败、脚本异常或空响应 |
| `MALFORMED_GLOBAL_STATE` | false | global hash 非法 |
| `MALFORMED_DOMAIN_STATE` | false | domain hash 非法 |
| `UNSUPPORTED_DOMAIN` | false | 未冻结 domain；正常 typed 调用不应出现 |

同一次 gate 检查可能同时观察到多个问题。primary decision code 的计算顺序固定为：

```text
1. UNSUPPORTED_DOMAIN（调用 Redis 前的 typed/domain 校验）
2. REDIS_UNAVAILABLE（脚本未得到可信完整响应）
3. MALFORMED_GLOBAL_STATE
4. MALFORMED_DOMAIN_STATE
5. GLOBAL_DISABLED
6. DOMAIN_DISABLED
7. ENABLED
```

因此 `global=false + domain malformed` 必须返回 `MALFORMED_DOMAIN_STATE`，不能以 global 已关闭为由短路或隐藏损坏；global/domain 同时 malformed 时 primary code 为 `MALFORMED_GLOBAL_STATE`，但两个 snapshot 都必须携带各自 integrity，且两个异常指标/告警都必须发出。`DynamicSwitchSnapshot` 至少区分 `VALID/MISSING/MALFORMED/UNAVAILABLE`，primary code 只用于调用方控制流，不能替代逐 key 完整性观测。

### 5.3 原子读取脚本

一次 `check(domain)` 必须用单个只读 Lua 脚本读取两个 key：

```text
KEYS[1] = global key
KEYS[2] = domain key
```

脚本必须：

1. 对每个 key 区分不存在与存在；
2. 对存在 key 使用 `HGETALL` 或等价方式验证字段恰好为四个；
3. 完整校验 payload；
4. 无论 global enabled 值为何都读取并验证 domain，返回结构化、长度固定且能分别判别两个 key integrity 的结果；
5. 不执行任何写命令；
6. 不包含 fallback 默认 true。

Java 必须把 Redis 客户端异常统一转换为 `REDIS_UNAVAILABLE`，并保留异常类别用于指标；不得把 Redis 错误传播成允许执行。

### 5.4 缓存与时序

WP4 v1 不缓存任何 runtime decision。特别是 true 不得存入字段、ThreadLocal、request cache、Caffeine、Spring Cache 或静态变量。

WP5 最终调用时序：

```text
静态开关校验
→ 获取 execute lease
→ gate.requireEnabled(domain)
→ 创建/恢复 run context
→ 每个 mutation 事务前：lease.ensureHeld()
→ gate.requireEnabled(domain)
→ 开启领域 MySQL 事务并 mutation
```

gate 检查完成后到事务开始之间仍存在不可消除的竞态。WP4 不声称动态开关具备 fencing 能力。关闭动作保证后续检查 fail closed；已经越过最后检查并开始的单个事务允许按领域原子性完成。WP8 故障演练必须以“最多一个已开始的 mutation 事务”作为可解释边界。

---

## 6. MySQL change 状态机

### 6.1 合法值

应用层只允许：

```text
status         = requested | applied | failed
request_source = manual | emergency | reconcile
```

V354 没有为两个字符串增加 CHECK，repository 每次读取必须拒绝未知值。不得把未知值当作 requested 或 terminal 继续处理。

### 6.2 requested 的派生子状态

数据库不新增子状态列，服务按字段派生：

| 派生状态 | 条件 | 可执行动作 |
|---|---|---|
| `AWAITING_APPROVAL` | enabled=true、`approval_ref` 非空且 `approved_by/approved_at` 二元组全空 | 等待第二人审批；禁止 Redis 写 |
| `APPROVED_ENABLE` | enabled=true、`approval_ref` 非空、审批二元组完整合法且未超过 CAS grace | 仅显式 approval/replay 可以执行 enable write CAS |
| `EXPIRED_ENABLE` | enabled=true、审批二元组完整合法、Redis `TIME` 已超过 `approved_at + grace` 且 Redis 不等于 target | guarded failed；禁止 Redis 写，只能重新申请 |
| `PENDING_DISABLE` | enabled=false | 请求线程或后台可执行 disable CAS |
| `UNCERTAIN` | Redis 不可读或最终化失败 | 保持 requested 并对账 |

`approval_ref` 是 enable 申请引用，不属于“审批已经发生”的事实标志：enable 从申请阶段起必须非空并永久不可变；disable 必须为空。审批事实只由 `approved_by/approved_at` 二元组表示，二者必须全空或全非空。二元组半空、申请人与审批人相同或 `approved_at < changed_at` 均为审计不一致，禁止 CAS 并告警。审批人的 active/internal/approve 权限在审批事务发生时验证；一旦审批事实提交，后续账号停用或权限收回不追溯改写历史审批，也不能阻止对已经写入 Redis 的 target 完成 MySQL finalization。

### 6.3 状态转换

```text
enable:
  create requested/AWAITING_APPROVAL
  → second-user approval
  → explicit Redis CAS
  → applied | failed | requested(uncertain)

disable:
  create requested/PENDING_DISABLE
  → Redis CAS
  → applied | failed | requested(uncertain)
```

只允许：

```text
requested → applied
requested → failed
```

禁止：

```text
applied → requested/failed
failed  → requested/applied
terminal 行更改目标 enabled、version、change_id 或请求语义
```

本文出现的 `approval_expired`、`stale`、`superseded_by_disable`、`enable_not_applied` 等都是稳定失败原因码，不是额外 status 值。实现应以安全前缀写入 `last_error` 并在 response/metric 使用枚举；`status` 始终只有 requested/applied/failed。

### 6.4 字段可变性

创建后永久不可变：

- `change_id`；
- `switch_key`；
- `version`；
- `enabled`；
- `changed_at`；
- `requested_by`；
- `request_source`；
- `reason`；
- `approval_ref`；
- `ticket_ref`。

仅 requested enable 在审批事务中可以把审批事实二元组从全 NULL 一次性写为全非空：

- `approved_by`；
- `approved_at`。

`approval_ref` 已在 enable 申请事务中持久化，审批时只能比较，不能填充或修改。

CAS/对账可以更新：

- `redis_observed_version`；
- `redis_observed_enabled`；
- `last_error`；
- `status`；
- `applied_at` 或 `failed_at`；
- `updated_at`。

`applied_at` 与 `failed_at` 必须互斥；applied 必须有 applied_at，failed 必须有 failed_at，requested 两者均为空。应用和真实 MySQL 测试必须弥补 V354 未设置这些跨列 CHECK 的不足。

### 6.5 时间和字符串

- enable 申请开始前必须通过只读 Redis time port 读取一次 `TIME`，转换为 UTC 毫秒 `Instant`，并把该值作为该行 `changed_at`；Redis time 读取失败时不创建 enable requested；
- disable 的 `changed_at` 由服务端注入的 UTC `Clock` 生成并截断到毫秒，Redis 故障时仍允许先落 requested；
- enable 审批的 `approved_at` 必须来自审批动作开始时重新读取的一次 Redis `TIME`；不得使用 `CURRENT_TIMESTAMP`、JVM `Clock`、HTTP 时间或 enable 申请时的旧 Redis 时间；
- Redis schema v1 的申请有效期 `ENABLE_APPROVAL_EXPIRY_MILLIS=1800000` 和 CAS grace `ENABLE_CAS_GRACE_MILLIS=60000` 都是不可配置协议常量；30 分钟有效期比较 `approval Redis TIME <= enable changed_at + 1800000ms`，60 秒 grace 比较后续 Redis `TIME <= approved_at + 60000ms`；两者左右两端都来自 Redis 时间域；
- 审批还必须要求 `approval Redis TIME >= enable changed_at`；enable Lua writer 必须要求当前 Redis `TIME >= approved_at`。Redis 时间向前跳只会缩短窗口；向后跳返回明确 time rollback、零写入并告警，绝不能借回拨延长窗口；
- Redis `TIME` 秒/微秒必须严格转换为 epoch milliseconds，并丢弃低于毫秒的余数；写 MySQL `DATETIME(3)` 时使用 `LocalDateTime.ofInstant(redisInstant, UTC)` 的无时区字面值，读时把 `LocalDateTime` 明确按 UTC 还原为 Instant，禁止交给 JVM/MySQL session 默认时区解释；重读必须还原完全相同的 epoch milliseconds；
- replay 不重新生成 changed_at；
- Redis `changed_at` 必须由已持久化 MySQL 行编码；
- reason trim 后 1～500 字符，禁止静默截断；
- approval/ticket 引用 trim 后分别不超过 128 字符；
- `last_error` 同时满足 `VARCHAR(2000)` 和 V354 `OCTET_LENGTH <= 2048`：按 Unicode code point 安全截到不超过 2000 字符且不超过 2048 UTF-8 bytes，不能破坏代理对；
- 日志不得输出 reason 全文、审批内容或 Redis 凭证。

---

## 7. 权限与审批

### 7.1 权限键

WP4 冻结六个独立权限：

| `perm_key` | `perm_name` | `module` | `action` | `status` | 用途 |
|---|---|---|---|---|---|
| `data.retention.switch.read` | `Data Retention Switch Read` | `data_retention` | `switch_read` | `active` | 查询 runtime/audit 状态 |
| `data.retention.switch.disable` | `Data Retention Switch Disable` | `data_retention` | `switch_disable` | `active` | 普通人工 disable |
| `data.retention.switch.emergency_disable` | `Data Retention Switch Emergency Disable` | `data_retention` | `switch_emergency_disable` | `active` | break-glass 紧急 disable |
| `data.retention.switch.enable` | `Data Retention Switch Enable` | `data_retention` | `switch_enable` | `active` | 创建 enable 申请 |
| `data.retention.switch.approve` | `Data Retention Switch Approve` | `data_retention` | `switch_approve` | `active` | 第二人批准 enable 并触发显式 CAS |
| `data.retention.switch.reconcile` | `Data Retention Switch Reconcile` | `data_retention` | `switch_reconcile` | `active` | 人工对账、修复 requested 或创建补偿 disable |

上述五列是精确 seed 向量。迁移发现相同 perm_key 已存在时，必须比较 `perm_name/module/action/status`；全部一致才视为 no-op，任一不同必须 `SIGNAL` 中止，不能用 `INSERT IGNORE` 静默接受异定义。六个 perm key 都不超过当前 `VARCHAR(64)`，action 都不超过 `VARCHAR(32)`。

不得继续复用当前清理接口的 `dispatch.task.release` 作为动态开关管理权限。

### 7.2 默认角色 grant

权限迁移默认只做以下 grant：

| role | read | disable | emergency disable | enable | approve | reconcile |
|---|---:|---:|---:|---:|---:|---:|
| `super_admin` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `manager` | ✓ | ✓ | ✓ |  |  | ✓ |
| `delivery_manager` | ✓ |  | ✓ |  |  |  |
| 其他角色 |  |  |  |  |  |  |

`super_admin` 当前存在 `*` fallback，但仍应 seed 权限并显式 grant，便于 UI、数据库审计和未来移除 fallback。迁移不得给 `admin/operator/sales/partner/partner_staff/partner_viewer` 自动增加变更权限。

迁移必须验证 `super_admin/manager/delivery_manager` 三个目标 role 存在且为 active；缺失或重复视为不兼容基线并失败，不能跳过 grant 后仍声称成功。它只插入缺失的目标 grant，不删除任何历史 grant；如果生产预检发现其他角色已拥有这些尚未发布的 permission key，必须先人工审计，迁移不得自行扩权或缩权。

### 7.3 Enable 双人流程

enable 申请必须满足：

1. 调用人是有效内部用户；
2. 具有 `data.retention.switch.enable`；
3. 静态 `geo.retention.dynamic-switch.enable-write-enabled` 为 true；默认和生产初始值必须为 false；
4. `request_source=manual`，不能使用 emergency/reconcile；
5. reason 和 `approval_ref` 必填；ticket 可空；
6. 初次写入 `approved_by/approved_at` 必须为空；
7. 创建前读取一次 Redis `TIME` 作为 changed_at；读取失败则返回 503 且不得插入 requested；
8. 申请事务只写 MySQL requested，不修改 Redis switch hash。

审批必须满足：

1. 调用人是有效内部用户且具有 `data.retention.switch.approve`；
2. `approved_by != requested_by`；
3. 行仍为 requested/AWAITING_APPROVAL；
4. `approval_ref` 与申请行完全相等；
5. 在不持有 MySQL 事务/行锁时读取一次 Redis `TIME` 作为 approvalNow；读取失败保持 AWAITING_APPROVAL、返回 503，不写 approved_by/approved_at；
6. 随后开启短 MySQL 事务锁定并重新验证行仍为 AWAITING_APPROVAL、审批人/引用不变；禁止为了取 Redis 时间跨网络持有数据库锁；
7. 要求 `changed_at <= approvalNow <= changed_at + 1800000ms`；小于 changed_at 表示 Redis time rollback，保持 AWAITING_APPROVAL、返回 503 并告警；超过上界则 guarded failed/approval_expired；两者都禁止审批；
8. 同一事务写 approved_by 与 `approved_at=approvalNow` 并提交；
9. 提交后、Redis CAS 前重新确认 `enable-write-enabled=true`，再由当前显式审批调用执行一次 enable CAS；若配置已关闭则保持 approved requested，不写 Redis；
10. 所有首次调用和 replay 都必须通过第 9.3 节相同的 Redis-time grace/rollback guard，不能由 controller/reconciler 调度时机决定有效期。

申请人不得通过持有 approve 权限绕过第二人规则。API DTO 不接受 `requestedBy/approvedBy/approvedAt/requestSource` 等可伪造身份字段。

审批响应丢失时，同一审批人只有在 Redis `TIME <= approved_at + grace` 时才可重放 write CAS；超过 grace 且 Redis 尚不等于 target 时，必须 guarded failed/enable_not_applied，只能新建 enable 申请。若 Redis 已经等于 target，说明 grace 内的 CAS 可能成功但响应/DB finalization 丢失，此时允许补记 applied，因为该动作不再执行 `false → true`。不同审批人面对已完整审批的 requested 行只能读取结果，不能覆盖原审批身份。

### 7.4 Disable

普通 disable：

- 需要 `data.retention.switch.disable`；
- source 固定 manual；
- reason 必填；
- 不要求 approval；
- 创建 requested 后立即尝试 CAS。

紧急 disable：

- 使用独立 endpoint 和 `data.retention.switch.emergency_disable`；
- source 固定 emergency；
- reason 必填，ticket 建议但不强制；
- 无论 `enable-write-enabled` 是否为 false 都必须可用；
- 不接受 enabled 参数，目标固定 false；
- 可以 supersede 较旧 requested，优先向 false 收敛。

内部治理 disable：

- source 固定 reconcile；
- 只允许内部 typed port 或具有 reconcile 权限的人工恢复操作调用；
- 目标固定 false；
- 后续 WP8 blocker 必须通过该端口，不能直接写 Redis/MySQL。

### 7.5 审批/工单系统边界

WP4 不调用或模拟外部审批系统。`approval_ref/ticket_ref` 是审计引用，不是“只要有字符串就已外部批准”的证明；真正的双人批准由当前登录用户、权限、不同 user ID 和 approval endpoint 共同形成。

如果未来接入签名审批 webhook，必须另行评审身份验证、重放防护和 approval_ref 唯一性；不能让 webhook 直接 HSET Redis。

---

## 8. 版本分配、幂等和并发

### 8.1 Change ID

所有人工变更请求必须携带调用方生成的 canonical UUID `changeId`，作为 HTTP/网络重试幂等键。服务端只校验并规范化为 lowercase UUID，不在每次重放生成新值。

同一 changeId replay 必须比较：

- 当前 authenticated requested_by；
- switch key；
- target enabled；
- derived request source；
- trim 后 reason；
- approval_ref；
- ticket_ref。

完全相同返回当前行；disable 可以按合法状态继续恢复，enable 申请 endpoint 无论是否已经 approved 都只返回状态，不得替审批人执行 CAS。enable CAS 恢复只能发生在第 7.3 节 approval endpoint 的合法审批人重放中。任一申请字段不同返回 `CHANGE_ID_CONFLICT`。审批身份不是申请 command 的一部分，但审批 replay 必须单独验证。

### 8.2 Version 分配

version 是每个 switch key 独立、严格递增的审计序号，不要求连续；failed/superseded 请求也永久占用版本。

创建事务：

1. 先按 changeId 查询并处理 replay；
2. `SELECT ... WHERE switch_key=? ORDER BY version DESC LIMIT 1 FOR UPDATE`；
3. version 基于 `max(latest MySQL audit version, 创建前已成功读取的合法 Redis version) + 1`；
4. version 不得超过 `Long.MAX_VALUE`；
5. INSERT requested；
6. 首行或极端竞争出现唯一冲突时回滚整个事务并在新事务重新读取，最多重试 3 次；
7. 3 次后仍冲突则 fail closed，不循环自旋。

任一可信 Redis/MySQL version 已达到 `Long.MAX_VALUE` 时进入不可恢复的版本耗尽状态：阻断所有 enable 和普通写入、运行时 fail closed 并触发 P0 runbook。不得绕回 1、写负数或切换浮点计数；只能在独立迁移/schema v2 评审后处理，紧急情况下可按第 18.3 节删除 key 维持 disabled。

如果 Redis 创建前不可读，仍可以用 latest MySQL + 1 创建 requested，以保留用户意图；后续 CAS/对账观察到更高 Redis version 时，原行转 failed，并为 disable 生成更高版本的补偿请求。enable 不自动补发。

### 8.3 已有 requested 的并发规则

同一 switch 最新行为 requested 时：

- 同 changeId、同命令：幂等恢复；
- 新 enable：409，禁止覆盖等待审批或不确定请求；
- 新 disable：允许在锁内把较旧 requested 标记 `failed/superseded_by_disable`，再插入更高版本 disable；
- 旧请求随后完成 Redis/DB 回调时，conditional terminal update 不得复活旧行；
- 较新 disable Redis version 使所有较旧 enable/disable CAS 失去覆盖资格。

supersede 与新 requested INSERT 必须在同一 MySQL 事务。不得先把旧行 failed，事务提交后再尝试创建新行。

同理，任何对账分支如果把 disable 标记为 failed 后仍要求继续向 false 收敛，“旧行 failed + 新 reconcile disable INSERT”必须在同一 MySQL 事务完成。补偿 INSERT 失败时旧 disable 保持 requested，避免形成无人负责的安全意图断点。

### 8.4 MySQL 锁序

WP4 不锁 run/operation/tree。内部统一顺序：

```text
change_id replay row（如存在）
→ switch 最新 change row
→ 当前 target change row
→ supplemental activity log（如写）
```

Redis 网络调用发生在上述事务提交后。任何 repository 方法不得在 `@Transactional` 中调用 `StringRedisTemplate`。

---

## 9. Redis CAS 契约

### 9.1 输入

CAS writer 从已提交 MySQL requested 行构造 target：

```text
target.change_id
target.version
target.enabled
target.changed_at
```

enable 还必须传入“该 target 之前最新合法 applied 状态”的完整 expected snapshot；无 applied 时 expected 是 missing v0 false。expected 必须在执行前从 MySQL 重读，并与 Redis 当前 payload 完全比较。

enable writer 还必须从 MySQL requested 行推导并传入 canonical `approved_at_epoch_ms`：将数据库 `approved_at DATETIME(3)` 按 UTC 解释为无前导零的十进制 epoch milliseconds。WP4 Redis schema v1 的 `ENABLE_CAS_GRACE_MILLIS` 是不可配置的协议常量 `60000`；Lua 使用 Redis `TIME` 计算当前 UTC epoch milliseconds 并比较 deadline。当前 epoch milliseconds 和受支持的审批时间都远小于 Lua 可精确表示的整数上界 2^53，但脚本仍必须验证十进制 canonical、非负和安全整数范围。不得信任 controller/JVM 传入“当前时间”，不得由调用方传 deadline 或 grace，也不得让重新部署改变既有审批有效期。target、expected 和 approved_at_epoch_ms 都必须在脚本内严格校验。

### 9.2 结果

脚本返回固定枚举：

| 结果 | 含义 |
|---|---|
| `APPLIED` | 本次脚本写入 target |
| `ALREADY_APPLIED` | Redis 已完全等于 target，未重写 |
| `STALE_TARGET` | Redis version 大于 target，目标过期 |
| `EXPECTED_MISMATCH` | enable 当前状态不等于 expected |
| `ENABLE_APPROVAL_EXPIRED` | Redis 尚不等于 target，且 Redis `TIME` 已超过 `approved_at + grace`；没有写入 |
| `REDIS_TIME_ROLLBACK` | Redis 尚不等于 target，且当前 Redis `TIME` 早于 `approved_at`；没有写入 |
| `SAME_VERSION_DIFFERENT_PAYLOAD` | 同 version 但其他字段不同 |
| `MALFORMED_CURRENT` | 当前 hash 形状或字段非法 |

Redis 客户端 timeout/连接/脚本异常不由 Lua 枚举表达，Java 统一视为 `UNAVAILABLE`。

所有结果都应尽可能返回当前 observed version/enabled；无法可信解析时不得伪造 observed。

### 9.3 Enable CAS

只有显式 approval/replay 调用可以执行：

```text
if current == target:
    ALREADY_APPLIED
else if redis TIME epoch_ms < approved_at_epoch_ms:
    REDIS_TIME_ROLLBACK（不写）
else if redis TIME epoch_ms > approved_at_epoch_ms + 60000:
    ENABLE_APPROVAL_EXPIRED（不写）
else if current 完全等于 expected applied snapshot:
    HSET target 四字段
    APPLIED
else:
    不写
```

所有 enable 首次调用和 replay 都只能经过这一个 Lua writer；禁止另有不校验 grace 的 `HSET`/CAS helper。仅比较 version 不足够；change_id、enabled、changed_at 也必须匹配 expected，防止在 Redis 被同版本异构 payload 污染时开启清理。

先判断 `current == target` 是为了恢复“grace 内已经写入 Redis，但响应或 MySQL finalization 丢失”的确定事实，该分支不执行 Redis 写入。写入有效区间精确定义为 `approved_at_epoch_ms <= Redis TIME epoch_ms <= approved_at_epoch_ms + 60000`。收到 `REDIS_TIME_ROLLBACK` 时保持 requested、记录安全 last_error、返回 503 并触发 P0 时钟告警；不得写 true，也不得因为时钟异常立刻重置 approved_at。收到 `ENABLE_APPROVAL_EXPIRED` 后，服务以短 MySQL 事务执行 `status=requested AND enabled=1 AND approved_by=? AND approved_at=?` guarded update 为 `failed/enable_not_applied`；并发 update count=0 时重读，只允许 exact terminal replay。不能再次审批或延长原行 grace，只能用新 changeId 重新申请。

### 9.4 Disable CAS

disable 是单向安全操作：

```text
if current == target:
    ALREADY_APPLIED
else if current 合法且 current.version < target.version:
    HSET target 四字段（enabled=0）
    APPLIED
else if current 缺失:
    HSET target 四字段（enabled=0）
    APPLIED
else:
    不写并返回具体冲突
```

因此较新 disable 可以覆盖较旧在飞行 enable；较旧 enable 在看到更高 disable version 后不能反向覆盖。

如果 disable 观察到合法但更高 Redis version，对账应把旧 disable failed，并新建 version 大于 observed 的 reconcile disable。该补偿仍是 MySQL requested → Redis CAS → MySQL terminal，不允许直接 HSET。

### 9.5 MySQL finalization

`APPLIED/ALREADY_APPLIED` 后，以短事务执行 guarded update：

```text
WHERE id=? AND status='requested'
```

写入：

- status=applied；
- observed version/enabled；
- applied_at=UTC now；
- failed_at=NULL；
- last_error=NULL。

enable finalization 前必须重新验证 `approval_ref` 非空且审批事实二元组完整合法。无审批事实但 Redis 恰好等于 enable target 是 P0 divergence：不得补记 applied，必须告警并发起新的 reconcile disable。

确定性版本冲突可以把 requested 更新为 failed；malformed/unavailable 不确定性保持 requested。terminal update count=0 时重读：完全相同 terminal 作为 replay，否则返回审计冲突。

---

## 10. 对账与故障恢复

### 10.1 Reconciler 调度

WP4 提供多实例安全的 reconciler：

```text
initial-delay = 30s
fixed-delay   = 30s
batch-size    = 100
enable-approval-expiry = 30m（Redis schema v1 协议常量，不可配置）
enable-cas-grace       = 60s（Redis schema v1 协议常量，不可配置）
```

只有 initial-delay、fixed-delay 和 batch-size 可以配置，生产修改仍必须评审；approval expiry 与 grace 只能随 Redis schema v2 和独立数据契约评审变更。默认 reconciler enabled。reconciler 不是 execute scheduler，不执行业务清理。

多实例通过 Redis CAS、MySQL conditional update 和唯一键实现幂等；不依赖单机 `synchronized`。单行失败不得阻止其他 requested 对账。

### 10.2 Requested 对账矩阵

| MySQL requested | Redis observation | 后台动作 |
|---|---|---|
| 任意 enable | Redis `TIME` 不可读 | 保持 requested，更新安全 last_error 并告警；禁止用 JVM/MySQL 时间判断 expiry/grace |
| unapproved enable，未过 30m | 任意非 exact true | 保持 requested，不写 Redis |
| unapproved enable，已过 30m | 非 exact true | failed/approval_expired |
| unapproved enable | exact target true | 不 applied；P0 告警并创建 reconcile disable |
| approved enable | exact target | 校验申请引用与审批二元组后补记 applied；不受 grace 限制，因为不发生 Redis 写入 |
| approved enable，Redis `TIME < approved_at` | 非 exact target | 保持 requested，记录 REDIS_TIME_ROLLBACK，零写入并告警 |
| approved enable，Redis `TIME` 在 grace 内 | predecessor/其他合法旧状态 | 保持 requested，等待同一审批人通过 approval endpoint 显式恢复；后台不写 true |
| approved enable，Redis `TIME` 超过 grace | predecessor/其他合法旧状态 | guarded failed/enable_not_applied，不写 true；显式 replay 同样失败 |
| disable | exact target | 补记 applied |
| disable | 合法更低版本或 missing | 后台执行 disable CAS |
| disable | 合法更高版本 | 若当前不是可信 false，在同一 MySQL 事务写 failed/stale 并创建更高版本 reconcile disable；若已是可信 false，可直接 failed/stale |
| 任意 | malformed/unavailable | 保持 requested，更新安全 last_error 并告警 |

### 10.3 全量三 key 一致性巡检

每轮必须先执行第 10.2 节 requested 矩阵，再对三个 runtime key 与各自审计执行稳态巡检。只要 Redis 合法 version 小于 MySQL 最大合法 audit version，顶层 integrity 分类一律为 `VERSION_ROLLBACK`，绝不能宣称一致。若最大合法行恰好是 requested 且 Redis 完全等于它的合法 predecessor，则附加子状态为 `REQUEST_IN_FLIGHT`：由这条既有 requested 按第 10.2 节承担恢复，不重复创建补偿请求；该子状态不改变 `VERSION_ROLLBACK` 分类，也不允许“匹配某条更旧 applied”通过稳态检查。

- Redis missing 且该 switch 从无 MySQL change：一致的隐式 disabled，不写任何数据；
- 先计算该 switch 的 MySQL 最大合法 audit version 和最大合法 terminal version；只统计 switch key、version、status/source、enabled、时间和审批形状都通过应用契约校验的行，遇到非法更高行时不得忽略后继续宣称一致，而应进入 MySQL 审计异常并阻断 enable；
- Redis 合法 version 小于 MySQL 最大合法 audit version 时，即使 payload 能匹配历史 applied 行也不得判为一致；特别是 Redis 低于最大合法 terminal version 时没有 `REQUEST_IN_FLIGHT` 例外。Redis=true 按 unsafe divergence 立即告警，并创建 version 高于 Redis/MySQL 双方最大值的 reconcile disable；如果已有 requested disable 正在承担同一收敛目标，则立即恢复其 CAS 而不重复创建。Redis=false 按 safe divergence 告警；若它是 approved enable 的合法 predecessor，则由既有 enable 按 grace 矩阵等待显式调用或到期 failed，后台不创建能与其竞争的请求；其他场景形成审计化补偿 disable；
- Redis payload 与 MySQL 最大合法 version 的 applied 行在 switch/version/changeId/enabled/changedAt 上完全相等，且不存在 requested disable、也不存在没有后继补偿请求的 failed disable：一致；其中 enabled=true 还要求匹配行 `approval_ref` 非空、审批二元组完整且申请人与审批人不同；
- Redis 是任意其他合法 false（包括 missing 但 MySQL 已有历史）：属于安全 divergence，生成 version 大于 Redis/MySQL 双方最大合法 version 的 reconcile disable，不从历史恢复 true；
- Redis 是任意不能匹配完整批准 applied 行的合法 true：P0 unsafe divergence；先把相应非 terminal/非法 enable 审计按 guarded 规则转 failed，再生成更高版本 reconcile disable；
- Redis version 高于 MySQL 最大版本时无论 enabled 值为何都告警，补偿 disable version 必须大于 observed；
- Redis same version/different payload 或 malformed：不自动改写，运行时 fail closed；进入 break-glass runbook；
- MySQL 有未知 status/source、enable 缺少 approval_ref、审批二元组半空、disable 带 approval_ref 或 terminal 时间矛盾：P0 审计异常，阻断 enable。

自动补偿需要有效的 `geo.retention.dynamic-switch.reconcile-operator-user-id`。默认值为 0；值为 0 时应用可以启动，但必须持续告警，WP5 生产 execute/canary 准入必须拒绝。该 user 必须存在、active、为内部账号；不能使用 NULL 或伪造负数表示 system。

补偿创建必须在 switch 最新行锁内先查找已有 requested disable：若存在则幂等恢复该行，不得每轮另建一条并互相 supersede；只有最新行不是可恢复的 requested disable 时才创建新的 reconcile disable。并发 worker 最终只能留下一个当前补偿请求。

### 10.4 故障点

必须覆盖：

1. MySQL requested INSERT 前失败：Redis 绝不写；
2. requested 提交后、Redis 前崩溃：disable 后台恢复；enable 等待显式审批/replay，后台不打开；
3. Redis CAS timeout，但实际未写：requested 保留；
4. Redis CAS timeout，但实际已写：exact payload 后补记 applied；
5. Redis 写后、MySQL applied 前崩溃：补记 applied；
6. MySQL terminal update rollback：requested 保留；
7. 两个 worker 同时对账：最多一个有效 terminal，Redis target 一致；
8. 旧 enable 与新 emergency disable 交错：最终只能是更高版本 false；
9. Redis 重启丢 key：运行时立即 false，不自动恢复 true；
10. Redis malformed：不覆盖、不执行、保持证据并告警。
11. Redis failover/NTP 向后跳：enable 审批或 CAS 零写入、返回 time rollback 并告警；时间恢复后仍以原 changed_at/approved_at 继续判断，不重置窗口。

---

## 11. 管理 API

### 11.1 Controller 边界

新增独立 controller，沿用前缀：

```text
/api/data-retention/dynamic-switches
```

不能把管理端点塞入现有业务 cleanup 方法，也不能通过任一 cleanup request 携带“顺便 enable”的字段。

### 11.2 端点

| method/path | 权限 | 行为 |
|---|---|---|
| `GET /api/data-retention/dynamic-switches` | read | 返回三个 runtime snapshot、最新 audit 和 divergence；Redis 不可用时仍返回 MySQL 部分并标记 unavailable |
| `GET /api/data-retention/dynamic-switches/changes/{changeId}` | read | 查询单个永久 change |
| `POST /api/data-retention/dynamic-switches/{switchKey}/changes` | enable 或 disable | manual 申请；body 明确 targetEnabled |
| `POST /api/data-retention/dynamic-switches/changes/{changeId}/approve` | approve | 第二人批准 requested enable 并显式尝试 CAS |
| `POST /api/data-retention/dynamic-switches/{switchKey}/emergency-disable` | emergency_disable | 目标固定 false |
| `POST /api/data-retention/dynamic-switches/changes/{changeId}/reconcile` | reconcile | 显式重读并按矩阵恢复；不得自动 enable CAS |

WP8 内部 blocker 使用 Java typed disable port，不通过本机 HTTP 回调。

### 11.3 Request DTO

manual change：

```json
{
  "changeId": "0f4e3df1-331b-4c61-9b94-8f53866d02da",
  "targetEnabled": false,
  "reason": "capacity guard investigation",
  "approvalRef": null,
  "ticketRef": "OPS-2026-0812"
}
```

enable 时 approvalRef 必填；disable 时 approvalRef 必须为空。所有 identity/source/time/version/status 字段由服务生成，DTO 出现这些字段应由严格反序列化或显式校验拒绝，而不是静默忽略。

emergency disable DTO 只允许 changeId、reason、ticketRef，不包含 targetEnabled。

approve DTO 只允许 approvalRef；它必须与申请记录完全一致，不接受 approvedBy/approvedAt。

### 11.4 Response

变更 response 至少返回：

- changeId；
- switchKey；
- version；
- targetEnabled；
- status；
- derived requested substate；
- runtime observed snapshot/availability；
- appliedAt/failedAt；
- stable result code。

不得返回 Redis 凭证、完整堆栈或内部 SQL。reason、ticket、approval 是否回显由 read 权限控制；日志默认不打印其正文。

### 11.5 错误映射

| 场景 | HTTP |
|---|---:|
| 未登录 | 401 |
| 权限不足/双人规则失败 | 403 |
| unknown switch、DTO 非法、超限 | 400 |
| change ID 冲突、requested 冲突、terminal mutation、approval expired | 409 |
| Redis unavailable | 503；change 行可能已是 requested，response 必须带 changeId 供恢复 |
| MySQL/审计不一致 | 503 并告警，不降级成功 |

网络 5xx 不表示请求未持久化；客户端必须按 changeId GET/replay，不能自动换新 UUID。

---

## 12. 配置

新增配置建议归入 `DataRetentionProperties.DynamicSwitch`：

```yaml
geo:
  retention:
    dynamic-switch:
      enable-write-enabled: ${GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false}
      reconciler-enabled: ${GEO_RETENTION_DYNAMIC_SWITCH_RECONCILER_ENABLED:true}
      reconcile-operator-user-id: ${GEO_RETENTION_DYNAMIC_SWITCH_RECONCILE_OPERATOR_USER_ID:0}
      reconcile-initial-delay-ms: ${GEO_RETENTION_DYNAMIC_SWITCH_RECONCILE_INITIAL_DELAY_MS:30000}
      reconcile-fixed-delay-ms: ${GEO_RETENTION_DYNAMIC_SWITCH_RECONCILE_FIXED_DELAY_MS:30000}
      reconcile-batch-size: ${GEO_RETENTION_DYNAMIC_SWITCH_RECONCILE_BATCH_SIZE:100}
```

Redis key prefix/schema 不提供生产环境变量，防止不同实例读取不同 namespace。测试通过构造器/test profile 注入隔离 namespace。

`enable-approval-expiry` 和 `enable-cas-grace` 都不提供配置项或环境变量；v1 所有实例分别固定使用 30 分钟和 60 秒。集成测试相对 Redis `TIME` 构造边界，不通过改变生产协议常量缩短测试。

`enable-write-enabled=false` 只阻止创建/批准新的 true；不能阻止 disable、对账确认、runtime read 或自动收敛 false。

WP4 不修改以下既有默认值：

```text
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED=false
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED=false
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED=false
```

---

## 13. 事务与 repository 契约

### 13.1 分层

建议分为：

```text
DataRetentionDynamicSwitchController
  → DataRetentionDynamicSwitchManagementService
      → DataRetentionDynamicSwitchChangeRepository (MySQL only)
      → DataRetentionDynamicSwitchRedisStore (Redis only)
      → CurrentUserService / authorization policy

DataRetentionDynamicSwitchGate
  → DataRetentionDynamicSwitchRedisStore (read-only script)

DataRetentionDynamicSwitchReconciler
  → repository + redis store + alert port
```

MySQL repository 不依赖 Redis store；Redis store 不依赖 JdbcTemplate。架构测试必须阻止互相反向依赖。

### 13.2 事务传播

- create requested：`REQUIRES_NEW` 或明确的独立短事务；
- approve fields：独立短事务；
- Redis CAS：无 MySQL transaction；
- terminal finalize：独立短事务；
- supersede old + insert new disable：同一短事务；
- reconciler 每行独立事务，不能一个坏行回滚整个 batch；
- controller 外层不得加包住全流程的 `@Transactional`。

Spring self-invocation 不得导致事务注解失效；事务 writer 应放在独立 bean 或使用显式 `TransactionTemplate`。

### 13.3 Guarded SQL

所有状态更新必须带旧状态/旧字段条件，至少包括：

```text
approval: status=requested AND enabled=1 AND approved_by IS NULL AND approved_at IS NULL
expire unapproved: status=requested AND enabled=1 AND changed_at=? AND approved_by IS NULL AND approved_at IS NULL
expire enable: status=requested AND enabled=1 AND approved_by=? AND approved_at=?
terminal: status=requested
supersede: status=requested AND version=<locked version>
```

approval update 还必须在 `WHERE` 中要求 `approval_ref=? AND approval_ref IS NOT NULL`；事务读取后验证 disable 的 approval_ref 必须为空、enable 的 approval_ref 必须非空。`expire unapproved` 只能使用 Redis `TIME` 得出的 now 判定，Redis time unavailable/rollback 时不得执行。`expire enable` 与 normal terminal writer 竞争时，update count=0 必须重读 Redis/MySQL 并只接受 exact applied/failed replay，不能覆盖成功 finalization。

update count 不为 1 时必须重读并判断 exact replay 或 conflict；禁止忽略 0 rows。

### 13.4 Activity log

`data_retention_dynamic_switch_change` 是强制、权威的永久审计。可以额外调用 `ActivityLogService` 记录 UI/IP 轨迹，但 supplemental activity log 失败不得让 emergency disable 无法向 false 收敛，也不得替代主审计表。

---

## 14. 安全、Redis 运维与可观测性

### 14.1 Redis 安全准入

生产启用任何动态 true 前必须确认：

- Redis 认证和网络访问只允许批准的应用/运维主体；
- 无其他服务、脚本或人工流程直接 `HSET/DEL/SET` 三个 key；
- 使用持久化策略并评估重启行为；key 丢失虽 fail closed，但会中断执行；
- maxmemory policy 不得静默淘汰治理 key，优先 `noeviction`；
- Redis 备份/恢复演练确认 restored payload 不回退到旧 true version；
- 集群模式下三个 key 的共同 hash tag 有效；
- 监控 Redis ACL/命令审计中的越权写入。

WP4 不在运行时执行 `CONFIG SET` 或自动修改 Redis 运维参数。

### 14.2 指标

至少提供：

```text
retention_dynamic_switch_gate_total{domain,decision}
retention_dynamic_switch_redis_latency_ms{operation}
retention_dynamic_switch_change_total{switch_key,target,status,source}
retention_dynamic_switch_cas_total{switch_key,target,result}
retention_dynamic_switch_requested_count{switch_key,target}
retention_dynamic_switch_requested_oldest_age_seconds{switch_key,target}
retention_dynamic_switch_divergence{switch_key,type}
retention_dynamic_switch_reconcile_total{action,result}
```

不得把 changeId、userId、ticket 或 reason 放进 metric label。

### 14.3 告警

至少冻结：

- 任意 runtime read unavailable/malformed：立即高优先级告警；
- requested disable 超过 60 秒：P0/P1 值班告警；
- requested approved enable 超过 60 秒：告警但不自动 enable；
- unapproved enable 超过 30 分钟：failed + 安全告警；
- Redis `TIME` 早于 enable changed_at/approved_at：P0 时钟回拨告警，禁止审批/CAS；
- Redis true 与批准 applied 审计不匹配：P0；
- Redis/MySQL version 回退、same-version different payload、未知 status/source：P0；
- reconcile operator 为 0/inactive：持续准入阻断；
- 自动补偿 disable 连续失败：P0；
- 任何代码路径尝试后台 false→true：测试失败并阻断发布。

### 14.4 日志

结构化日志可包含：switch key、changeId、version、target、source、result、error class。不得记录 Redis password、JWT、完整 reason、审批正文或数据库连接串。

---

## 15. 测试规格

### 15.1 单元测试

必须覆盖：

1. 三个 enum/key/domain 映射及未知值拒绝；
2. UUID、version、enabled、UTC millis payload 编解码；
3. missing 与 malformed 的严格区分；
4. 额外字段、半空 hash、非 canonical version/UUID/time 拒绝；
5. 大于 2^53 的 version 比较，脚本禁止 `tonumber`；
6. global/domain 全组合决策矩阵和固定优先级；至少覆盖 global=false + domain malformed、global/domain 同时 malformed、global malformed + domain=false，并断言每个坏 snapshot 都产生告警；
7. enable/approve/disable/emergency/reconcile 权限和 source 矩阵；
8. enable 申请的 approval_ref 非空 + 审批二元组全空合法；申请人等于审批人、审批二元组两个半空组合、disable 携带 approval_ref 和过期审批拒绝；
9. changeId exact replay 和任一字段 conflict；
10. reason/reference 长度与 UTF-8 2048-byte error 边界；
11. enable reconciler 永不调用 CAS write；disable reconciler允许调用；
12. terminal 不可逆和 guarded update 0-row 处理；
13. no-cache：连续 gate 每次都访问 Redis store；
14. disable 使用 injected UTC Clock；enable 创建/审批不得读取 JVM/MySQL 当前时间，Redis `TIME` 秒/微秒到 UTC millis 的转换与截断正确。

### 15.2 SQL/架构契约测试

必须验证：

- V354 switch 表所需列、唯一键和索引存在，WP4 不修改其 DDL；
- 权限 migration 只 seed 六个 key，grant 与第 7.2 节完全一致；
- migration 二次执行/repair 后不重复 permission 或 grant；
- 无其他角色被误 grant enable/approve/emergency；
- application.yml 新配置默认安全，三个既有 execute 仍为 false；
- Lua 脚本只写四字段、不设 TTL、不删除 key；
- runtime read script 无写命令；
- enable CAS 只能从显式 management path 调用；reconciler 依赖图没有 enable writer；
- MySQL repository 不依赖 Redis，Redis store 不依赖 JDBC；
- controller 不带覆盖完整跨存储流程的 `@Transactional`；
- WP4 不依赖 legacy `DataRetentionRunAuditService`，也不写 WP3 tree 表；
- 现有 cleanup service/controller/scheduler 未在 WP4 被半接线。

### 15.3 Redis 7 + MySQL 8 required 集成测试

新增独立 required profile，例如 `retention-switch-it`：

- Redis 使用 `redis:7-alpine` 的固定可复现 patch tag；
- MySQL 使用 8.0.36 或更高 8.0；生产最终在 Redis 实际版本和 MySQL 8.0.46 验证；
- 普通开发环境无 Docker 可 skip；required profile 无 Docker 必须失败；
- 0 skipped 才算通过；
- 测试使用独立 Redis namespace/database 和 MySQL schema，结束后只清理测试资源。

required matrix：

1. 三 key missing → runtime disabled，读取不创建 key；
2. manual disable 完整 requested/CAS/applied，Redis/MySQL 四元组一致；
3. enable 申请不写 Redis，自批拒绝，第二人审批后 applied；
4. enable-write-enabled=false 拒绝 true，但所有 disable 仍成功；
5. Redis down/timeout：gate disabled、change requested、恢复后对账；
6. partial hash、额外字段、非法时间、same-version different payload 全部 fail closed；
7. after-requested/before-Redis 故障：disable 自动恢复，enable 不自动打开；
8. after-Redis/before-applied 故障：exact payload 补记 applied；
9. CAS timeout 的“实际未写/实际已写”双场景；
10. 两连接/两线程并发分配 version，无重复、无倒序；
11. 旧 enable CAS 与更高 emergency disable 受控交错，最终 Redis 只能是高版本 false；
12. changeId 同命令 replay 与不同命令冲突；
13. requested supersede 与新 disable 同事务；注入 INSERT 失败时旧 requested 不被单独 failed；
14. 两个 reconciler 同时处理同一行，只有一个 terminal 结论；
15. unapproved exact true 不被 applied，并自动补偿 false；
16. Redis key 丢失且 MySQL applied=true 时不恢复 true，而是收敛 false；
17. Redis version 高于 MySQL 时创建更高版本补偿 disable；
18. MySQL 依次存在旧 applied=true、新 applied=false，Redis 恢复为旧 true：必须判 `VERSION_ROLLBACK`，不能命中历史行视为一致，并创建高于双方最大 version 的补偿 disable；
19. 最新 requested + Redis 合法 predecessor 的顶层分类仍为 VERSION_ROLLBACK，同时附加 REQUEST_IN_FLIGHT 并按既有 requested 矩阵恢复；不得重复创建补偿，也不得宣称一致；
20. enable 创建时 Redis TIME 读取失败：无 MySQL requested；disable 同样故障仍可落 requested；
21. 审批 Redis TIME 读取失败：审批二元组不写；Redis TIME 早于 changed_at：返回 time rollback 且不审批；晚于 30 分钟：guarded approval_expired；
22. approved enable 在 grace 内首次 CAS/同审批人 replay 成功；用 Redis `TIME` 控制超过 grace 后，显式 replay 返回 `ENABLE_APPROVAL_EXPIRED`、MySQL guarded failed 且 Redis 零写入；
23. CAS Redis TIME 早于 approved_at：返回 `REDIS_TIME_ROLLBACK`、保持 requested 且零写入；时间恢复后不重置 approved_at/grace；
24. grace 内 Redis 已写 target、响应丢失、grace 后或时钟回拨时 replay：只补记 applied，不重新 HSET；
25. JVM/MySQL 时钟分别向前/向后偏移至少 10 分钟，enable changed_at、approved_at、30 分钟窗口和 60 秒 grace 仍只随 Redis TIME 变化；
26. global + website/poll 原子读取，任一 false/unavailable 均拒绝；global=false + domain malformed 必须返回 malformed 并告警 domain；
27. true 后立即 disable，下一次 gate 不使用正向缓存；
28. MySQL session 非 UTC、JVM 默认非 UTC 时，MySQL/Redis 仍精确 UTC millis；
29. permission migration/grant 和真实 CurrentUserService 校验；
30. reason/reference/last_error 近边界和多字节字符；
31. Redis restart/key loss、malformed 保留证据及告警事件；
32. 所有测试结束后三个 execute 配置仍为 false，测试 namespace 不残留 true。

并发测试必须用 latch/barrier 控制交错，不得用 `Thread.sleep` 猜时序。Redis 命令和 MySQL connection 必须是真实独立连接；mock 测试不能替代上述 required matrix。

### 15.4 V133 与 schema fixture 表述

WP4 MySQL 测试可以使用明确来源的 V354/V358 后可信 fixture 或抽取基线，但报告必须写明：

- 这验证 WP4 服务和权限迁移，不是 V1→当前真实历史链；
- V133 仍是独立准入阻断；
- 不修改已发布 V133 checksum；
- 只有历史链修复或正式豁免后，生产 ready 验收才成立。

---

## 16. 预计文件变更（获批开发后）

具体包名可按现有 retention 结构微调，但职责不得合并绕过：

```text
geo-server/src/main/java/com/huanjing/geo/module/retention/switch/
  DataRetentionDynamicSwitchKey.java
  DataRetentionDynamicSwitchPayload.java
  DataRetentionDynamicSwitchRedisStore.java
  DataRetentionDynamicSwitchChangeRepository.java
  DataRetentionDynamicSwitchManagementService.java
  DataRetentionDynamicSwitchGate.java
  DataRetentionDynamicSwitchReconciler.java
  DataRetentionDynamicSwitchAuthorizationPolicy.java

geo-server/src/main/java/com/huanjing/geo/module/retention/controller/
  DataRetentionDynamicSwitchController.java

geo-server/src/main/java/com/huanjing/geo/module/retention/dto/
  （严格 request/response DTO）

geo-server/src/main/java/com/huanjing/geo/module/retention/config/
  DataRetentionProperties.java（增加 DynamicSwitch）

geo-server/src/main/resources/application.yml
  （只增加默认安全配置，不改变 execute）

geo-server/src/main/resources/db/migration/
  V<NEXT>__data_retention_dynamic_switch_permissions.sql

geo-server/src/test/java/com/huanjing/geo/module/retention/switch/
  （unit、contract、Redis/MySQL integration）

geo-server/pom.xml
  （required retention-switch-it profile）
```

不得修改 V354；如实现发现 V354 无法承载状态机，立即停止并回到 WP2 数据模型评审。

---

## 17. WP5 消费契约

WP5 只消费 `DataRetentionDynamicSwitchGate` 和内部自动 disable port：

1. 不直接读 Redis hash；
2. 不直接查询 `data_retention_dynamic_switch_change` 决定 runtime enabled；
3. 不捕获 Redis 异常后继续执行；
4. lease 后和每个 mutation 前都调用 gate；
5. gate false/unavailable 时不创建新的 mutation attempt；是否记录 skipped 由 WP5/WP3 契约决定；
6. WP5 不具备 enable port；只有 WP4 管理面可执行显式 enable CAS；
7. 容量/积压/治理 blocker 通过 WP4 typed reconcile disable port，只能把目标设为 false；
8. WP5 的架构测试必须阻止人工/scheduler/canary/retry/reconcile 绕过 gate。

WP4 返回的 snapshot/version 可用于日志和 metrics，但 WP5 不把它当作数据库 fencing token，也不持久化伪造的 commit 证明。

---

## 18. 生产准入与回滚

### 18.1 WP4 上线初态

- 三个 Redis key 可以不存在；missing 即 disabled；
- `enable-write-enabled=false`；
- 三个静态 execute=false；
- reconciler 可以运行，但 reconcile operator 未配置时持续阻断生产 execute；
- 权限 migration 只赋予评审批准角色；
- 上线 WP4 不自动创建 true，不执行清理。

### 18.2 首次生产 enable 前

必须完成：

1. V133 历史链结论关闭；
2. WP2 生产克隆/metadata lock/最终容量验收；
3. WP3～WP8 required 测试和生产准入；
4. Redis 实际版本、ACL、持久化、淘汰、集群 slot 和恢复演练；
5. reconcile operator 有效；
6. 权限和至少两名独立 approver 验证；
7. 告警、dashboard、值班和 emergency disable 演练；
8. 所有静态开关仍按 canary 审批流程单独控制。

### 18.3 回滚

应用回滚前先通过 WP4 emergency API 把 global 和两个 domain 收敛为 false，并确认 MySQL applied。不得仅回滚 Java 包而保留 Redis true 给不认识 v1 协议的旧版本。

若 WP4 服务不可用，break-glass runbook 可以由双人运维在隔离窗口删除三 key，使 runtime missing=false；该操作必须记录 Redis 审计、工单和事后 MySQL reconcile disable。禁止 break-glass 手工写 true。

---

## 19. 验收标准

WP4 只有同时满足以下条件才可标记“代码完成，等待 WP5 接线”：

1. 本 SPEC 评审通过并记录最终版；
2. 开发前和提交前完成迁移号全 refs/worktree/未跟踪文件扫描；
3. 没有修改 V354 或新增 retention 治理表 DDL；
4. 权限 migration seed/grant 与第 7 节完全一致且可恢复；
5. 三 key、Redis v1 payload、missing/malformed、版本比较和 Lua 契约全部实现；
6. runtime gate 原子读 global+domain，所有错误 fail closed，true/false 均无本地缓存；
7. MySQL requested → Redis CAS → MySQL terminal 状态机、changeId replay 和 guarded update 完整；
8. enable 双人审批成立，后台代码结构和测试证明无法 false→true；
9. disable/emergency/reconcile 能安全压过旧 enable，并在故障后自动向 false 收敛；
10. requested/full-key 对账、divergence 告警和 reconcile operator 阻断已实现；
11. unit/contract tests 全部通过；
12. required Redis 7 + MySQL 8 集成测试 0 failure、0 error、0 skipped；
13. 并发、CAS timeout、各事务边界、Redis restart/malformed、UTC/UTF-8 边界全部通过；
14. `git diff --check` 通过，未覆盖或暂存无关 dirty worktree；
15. `enable-write-enabled` 和所有静态 execute 默认/实际值保持 false；
16. 测试报告明确保留 V133 和 WP2 生产验收阻断，不宣称 production ready；
17. 未执行生产 Redis 写入、生产迁移、生产清理或 canary。

WP4 完成不等于：

- WP5 统一协调器已接线；
- 任一现有 execute 入口已受完整 P0 治理；
- WP6/WP7 业务算法已改造；
- WP8 容量/积压 blocker 和运维手册已完成；
- 可以设置 Redis true 或静态 execute true；
- 整体 P0 可以生产运行。

---

## 20. 已批准决策清单

聚焦复审已经确认：

1. 三个 switch key、Redis physical key 和四字段 HASH v1 是否冻结；
2. missing=false、malformed/unavailable=false 是否符合运维预期；
3. enable 双人申请/审批与 30 分钟时限是否足够；
4. permission key 与默认角色 grant 是否合理；
5. version 允许跳号、disable 可越过更低版本、enable 必须完整 expected snapshot 的 CAS 规则；
6. 新 disable supersede requested 与旧 enable 并发时的最终 false 保证；
7. 后台只确认已发生 enable、绝不执行 enable 的边界；
8. full-key divergence 自动补偿 false 与 break-glass 边界；
9. 无本地缓存及“最多一个已开始事务”的 kill-switch 生效语义；
10. WP4 只新增 RBAC DML migration、不新增治理 DDL 的范围；
11. required Redis/MySQL 测试矩阵与 WP5 handoff 是否完整；
12. 文档是否可以在修订通过后授权 WP4 Java、权限迁移和测试开发。

---

## 21. 当前评审状态

当前结论：

> **聚焦复审通过；本文是 WP4 实施基线，允许开展 WP4 Java、Lua、权限迁移与测试开发。**

授权仍不包含修改现有 execute 接线、向任何生产环境写入动态 true、执行生产迁移/清理、开展 WP5～WP8 开发或解除 V133/WP2 生产准入阻断。
