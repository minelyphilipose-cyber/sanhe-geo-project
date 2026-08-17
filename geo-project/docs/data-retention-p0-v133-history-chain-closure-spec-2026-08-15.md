# 数据定期清理 P0：V133 历史迁移链关闭 SPEC

> 日期：2026-08-15
>
> 状态：本地 spike 与 MySQL Required 证据完成；路径 A 已被证伪；V1 空库/灾备重建治理待关闭，不阻断既有 V300+ 数据库的独立前向迁移验证
>
> 当前已知最高 migration：V368
>
> 适用 Flyway：仓库实际依赖 9.22.3
>
> 授权边界：本文只冻结 V133 历史链的诊断、决策和验收协议；不授权修改任何已发布 migration，不授权生产或生产克隆执行，不授权连接 Redis、写动态开关、打开 execute/enable-write、执行 cleanup 或 canary

## 1. 结论先行

WP6、WP7、WP8 已完成本地代码实现、聚焦复审和各自声明的 Required 验收，但整个数据定期清理 P0 仍未获得生产准入。V133 是 **V1 全新空库/灾备重建链** 的历史缺陷：已经成功越过 V133、当前处于 V300+ 的生产和测试数据库不会重跑 V133。只要正式 migration location 不加入 V132.1 候选，V133 本身不阻断这些数据库从当前成功版本继续执行 V354～V368 或后续前向 migration。

因此后续必须拆成两条互不冒充的轨道：既有历史数据库按实际 `flyway_schema_history` 做匿名化克隆和前向迁移验证；V1 空库/灾备重建能力继续通过可信基线豁免或新的治理决定单独关闭。前一条轨道仍需独立审批和全部生产门禁，但不再等待 V133 空库链结论。

本文不预设“增加一个排序在 V133 之前的兼容 migration”一定可行。该方案只有同时满足以下两类数据库的 Flyway 9.22.3 状态契约时才能获批：

1. 全新空库可以从 V1 完整迁移到当前最高版本；
2. 已经成功越过 V133 的数据库在引入候选文件后，`info → validate → migrate → validate` 全链仍通过，且不出现意外 `Pending`、`Ignored` 或 `Out of Order`。

V369、任何高于 V133 的普通 versioned migration，以及 repeatable migration 都无法修复已经在 V133 中断的全新空库链。若前置兼容 migration 不能通过上述双向门禁，必须回到 WP2 已批准的“可信 V353 实库导出基线豁免”路径，不得通过放宽全局 Flyway 校验来伪造通过。

### 1.1 2026-08-15 spike 结论

第一决策门已由评审明确输出 `LOCAL_SPIKE_AUTHORIZED`。本地 spike 随后使用仓库实际 Flyway 9.22.3 和外部 MySQL 8.0 执行，结论为：

- 扫描 67 个 refs/worktree 后，V132 与 V133 之间没有已占用版本；Flyway 可将候选 `V132_1` 解析为 132.1；
- 候选在全新空库上可以先创建 `uk_article_batch_project_date_channel_slot`，使未修改的 V133 成功执行；
- 候选也可以在真实 V133 failed 库经 Flyway repair 后执行，repair 前后的全部既有 success history 行逐字段相等；
- 但数据库已经成功越过 V133、且 history 中不存在 132.1 时，Flyway 9.22.3 将候选固定识别为 `Ignored`，默认 validate 失败；
- 未启用 out-of-order，未增加 ignore pattern，未关闭 validate，也未修改任何既有 migration；
- 因 A2 的硬门禁失败，路径 A 已被证伪。候选只保留在 test resources 作为可执行 spike 证据，没有进入正式 migration location；
- 路径 B 的可信 V353 artifact、来源 history、SHA-256、书面批准和 rebuild 证据尚未形成，因此本报告建议第二决策门输出 `BLOCKED_NEEDS_NEW_GOVERNANCE`，但不自我批准该状态。

独立 `retention-v133-it` profile 共 5 项测试，结果为 5/5、0 failure、0 error、0 skipped。该 profile 只使用 Flyway/JDBC/MySQL，不连接 Redis，不调用 retention service，不产生动态开关写入。

### 1.2 两条迁移轨道的适用边界

| 轨道 | 起点 | V133 结论的影响 | 下一步 |
|---|---|---|---|
| 既有历史前向迁移 | `flyway_schema_history` 已成功越过 V133，生产/测试当前已在 V300+ | 不重跑 V133；禁止把 V132.1 放入正式 location，以免形成 `Ignored + validate failed` | 从匿名化克隆的真实 history 执行 `info → validate → migrate → validate`，只迁移尚未执行的正式版本 |
| V1 空库/灾备重建 | 无可信 Flyway history，从 V1 开始 | 仍会在 V133 中断；路径 A 已证伪 | 单独形成可信 V353 baseline artifact、来源与审批，或进入新的历史链治理 |

任何既有库若 schema history 不完整、V133 不是 Success、存在 failed/ignored/out-of-order 或 checksum 不一致，不得被归入“既有 V300+ 前向迁移”轨道，必须停止并单独诊断。

## 2. 当前已确认事实

### 2.1 仓库与工具事实

- Flyway Core/MySQL 版本为 9.22.3；
- migration 目录为 `geo-server/src/main/resources/db/migration`；
- 当前唯一最高整数版本为 V368；
- 仓库已有 `V77_1`、`V77_2`、`V330_1` 一类细分版本先例，但这不证明新增 V132 与 V133 之间的版本在既有数据库上可安全部署；
- 当前 V133 文件为 `V133__article_batch_channel_slot_unique.sql`；
- 本稿生成时该文件 SHA-256 为 `f41035f934d51d1d08eae593b5320ba02588841f2523818c4911bcb254ed3ce2`，且工作树未显示该文件被修改；该值只作为本次评审锚点，开发前仍须生成全部 migration 清单；
- 当前真实空库诊断测试以 target V354 执行完整 migration location，并预期在 V133 失败；
- 当前局部 WP2 测试以可信抽取结构 baseline 到 V353，再执行 V354；该结果从未被允许冒充 V1 全链通过。

### 2.2 V133 失败形状

V133 当前按以下顺序执行：

```text
若存在 uk_article_batch_project_date_no
→ ALTER TABLE article_batch DROP INDEX uk_article_batch_project_date_no
→ 若不存在 uk_article_batch_project_date_channel_slot
→ ALTER TABLE article_batch ADD UNIQUE KEY
   (project_id, batch_date, target_channel, generation_slot_no)
```

真实空库链在第一条 `DROP INDEX` 处失败，因为 MySQL 判定该索引仍被外键需要。现有诊断测试接受 V133 或 MySQL 报出的 `article_generation_tasks_ibfk_2` 作为失败证据。实施 spike 必须使用 `information_schema.statistics`、`information_schema.key_column_usage`、`SHOW CREATE TABLE` 和 `SHOW ENGINE INNODB STATUS` 固定以下精确事实，不能只依赖异常文本：

- 依赖该索引的外键名称、子表和列顺序；
- MySQL 实际为该外键选择的父/子索引；
- V132 结束时 article_batch 的全部索引签名；
- V133 目标索引是否足以承载相关 FK；
- 在空表和包含合法历史行时，先增加替代索引再删除旧索引的行为是否一致。

### 2.3 为什么后置 migration 不成立

Flyway 必须先成功执行 V133 才能到达 V134 及更高版本。因此：

- V369 到达不了；
- repeatable migration 在未完成的 versioned migration 之后执行，同样到达不了；
- 应用启动后的 Java repair 到达不了，且会把 schema 修复与业务进程启动错误耦合；
- 修改 V133 本体会改变已发布 checksum，禁止；
- 仅把当前 V133 诊断从“预期失败”改成 skipped 或 baseline，不构成修复。

## 3. 目标与非目标

### 3.1 目标

1. 用可复现证据决定前置兼容 migration 是否同时兼容新空库和已越过 V133 的数据库；
2. 若可行，冻结最小 SQL、Flyway 状态契约、repair 协议和 Required 测试；
3. 若不可行，形成符合 WP2 第 18.2.1 节的正式 V353 基线豁免材料；
4. 保证所有既有 migration 字节和 Flyway checksum 不变；
5. 把 V133 从“预期失败诊断”转换为成功验收，或以正式批准的基线豁免关闭；
6. 为后续匿名化生产克隆提供唯一、明确、可审计的迁移入口。

### 3.2 非目标

- 不在本文中执行或批准生产 migration；
- 不在本文中执行匿名化生产克隆；
- 不修改 V133 或其他任何已发布 migration；
- 不新增 retention 业务表、字段、索引或清理语义；
- 不连接 Redis，不读取或写入 runtime dynamic switch；
- 不创建 enable/disable change audit；
- 不运行 website/poll/detail cleanup；
- 不执行 canary，不写入 canary 通过证据；
- 不打开 scheduler execute、domain execute、dynamic enable-write 或 WP8 detail-cleanup；
- 不以 MySQL DDL 可以事务 rollback 为前提设计失败处置。

## 4. 不可变安全约束

### 4.1 历史 migration 字节不可变

候选方案开发前必须对当前 migration location 中全部既有资源生成不可变清单：

```text
relative_path
byte_length
sha256
flyway_checksum
```

候选方案加入后，对相同既有文件重新生成清单并逐项比较。允许的差异只能是新候选文件本身；任何既有文件的路径、字节长度、SHA-256 或 Flyway checksum 改变都使验收失败。

清单必须至少覆盖全部 `V*__*.sql` 和 `R__*.sql`；若目录存在 undo、callback 或其他 Flyway resource，也必须纳入。禁止只检查 V133。

### 4.2 不放宽全局 Flyway 治理

为使候选方案看似通过，禁止：

- 全局开启 `outOfOrder`；
- 将 `*:ignored`、`*:pending` 或宽泛 pattern 加入全局 `ignoreMigrationPatterns`；
- 关闭 `validateOnMigrate`；
- 使用 `skipExecutingMigrations` 伪造成功；
- 手工插入或修改成功的 `flyway_schema_history` 行；
- 通过 `repair` 重写任何既有成功 migration 的 checksum、description 或 type；
- 为新空库和既有库长期维护两套无法相互验证的普通 migration 历史。

若评审认为某项 Flyway 配置变化不可避免，必须另起治理 SPEC，并重新评估全部 368 个既有 migration；不得在 V133 修复中夹带。

### 4.3 所有数据清理开关保持关闭

代码与配置默认值继续满足：

```text
scheduler.enabled = false
scheduler.execute-enabled = false
website execute-enabled = false
article execute-enabled = false
poll execute-enabled = false
dynamic-switch.enable-write-enabled = false
detail-cleanup.enabled = false
```

V133 测试不得依赖启动完整 Spring 业务应用来完成 migration，不得调用 WP4/WP5/WP6/WP7/WP8 service。

## 5. 决策门：先证伪或证实，再选择路径

### 5.1 路径 A：前置兼容 migration

候选文件名和版本号在 spike 前不冻结。可以研究一个严格排序在 V132 之后、V133 之前的细分 version，但只有扫描全部 refs/worktree 并通过实际 Flyway 解析后才能命名。本文用 `<PRE_V133_VERSION>` 表示。

候选 SQL 的唯一允许目的，是在 V133 删除旧索引前提供 MySQL 所需的等价索引支撑。优先评估以下最小形状，但不得在测试前把它写成最终结论：

```text
锁定/读取 V132 结束时 article_batch 精确结构
→ 若替代索引不存在，创建精确替代索引
→ 若已存在，验证名称、unique、列顺序、prefix、collation 等签名完全一致
→ 不删除任何索引或外键
→ 不修改任何业务数据
```

候选可以评估“提前创建 V133 的目标唯一索引”或“增加专用 FK 支撑索引”，但必须用最终 V133 后 schema、查询计划和冗余索引结果选择其一。不得留下语义重复且永久无用的索引而不记录处置。

路径 A 只有在第 9 节全部矩阵通过时才成立。特别是：仅证明新空库成功远远不够；若已越过 V133 的真实历史库看到候选为 `Ignored`、`Pending` 或 `Out of Order`，路径 A 直接失败，除非另有不放宽全局治理且经过独立评审的部署机制。

### 5.2 路径 B：可信 V353 基线豁免

若路径 A 失败，不继续堆叠 callback、repeatable、V369 或全局 Flyway 配置绕过。改用 WP2 已批准的正式豁免路径，并至少形成：

- 批准人和批准日期；
- 可信、已成功应用至 V353 的源环境标识；
- 源环境 `flyway_schema_history` 全量导出及校验；
- schema-only 导出物的 SHA-256；
- 匿名化和敏感信息检查记录；
- 从导出物重建数据库的确定性步骤；
- V353 → V368 的 `info → validate → migrate → validate` 证据；
- 新空库 V1→V132 可执行、V133 未覆盖的剩余风险；
- 豁免撤销条件和未来重建策略；
- 生产克隆与生产 migration 必须使用的唯一基线来源。

未经书面批准的本地 V353 fixture、手写简化 schema 或 `baselineOnMigrate(true)` 不能作为路径 B。

### 5.3 第一决策门：SPEC 评审输出

本轮 SPEC 聚焦评审只决定是否允许收集路径证据，不提前选择路径 A 或路径 B。评审必须明确输出一种：

```text
LOCAL_SPIKE_AUTHORIZED
SPEC_REJECTED
```

`LOCAL_SPIKE_AUTHORIZED` 只授权第 6～10 节的本地 spike、候选 migration、contract/MySQL 测试和文档开发。它不表示前置兼容 migration 可行，不批准 V353 基线豁免，也不关闭 V1 空库/灾备重建轨道的 V133 阻断。

`SPEC_REJECTED` 表示当前诊断或验收协议仍不充分，不得开始 spike。两种输出都不授权生产克隆、生产 migration、cleanup、canary 或 enable。

实际输出：`LOCAL_SPIKE_AUTHORIZED`。授权原文限定为本地 V133 spike、候选前置 migration、contract/MySQL Required 测试和相关文档更新；本次实现未越过该边界。

### 5.4 第二决策门：spike 复审输出

只有本地 spike 与适用的 Required 矩阵完成、形成完整证据报告并经过第二次聚焦复审后，才能明确选择一种最终路径结论：

```text
PRE_V133_COMPATIBILITY_APPROVED
V353_BASELINE_WAIVER_APPROVED
BLOCKED_NEEDS_NEW_GOVERNANCE
```

- `PRE_V133_COMPATIBILITY_APPROVED`：A0～A4 全部通过，候选 migration/测试代码复审通过，以路径 A 关闭 V1 重建轨道的 V133 阻断；
- `V353_BASELINE_WAIVER_APPROVED`：路径 A 被证伪，第 5.2/9.6 节的真实基线材料、rebuild 测试和批准手续齐全，以正式豁免关闭 V1 重建轨道的 V133 阻断；
- `BLOCKED_NEEDS_NEW_GOVERNANCE`：路径 A 不成立且路径 B 尚未获正式批准，或测试暴露需要独立治理的 Flyway 配置/历史问题。

spike 报告本身只能给出建议和证据，不能自我批准 V1 空库/灾备重建路径。第二决策门没有明确结论时，该重建轨道继续保持 V133 阻断；这不等于阻断 history 已成功越过 V133 的数据库进入独立的匿名化克隆前向验证。

## 6. 测试位置与隔离协议

### 6.1 使用实际 Flyway 版本

测试必须通过项目依赖加载 Flyway 9.22.3，不得用 mysql CLI 手工顺序执行 SQL 来替代 Flyway。每个场景记录：

- Flyway version；
- MySQL exact version；
- migration locations；
- target；
- baseline、outOfOrder、ignoreMigrationPatterns、validateOnMigrate 的实际值；
- `info` 中每个非 Success migration 的 version、description 和 state。

### 6.2 临时 migration location

为模拟“旧制品部署后再引入候选文件”，测试使用临时只读副本：

```text
location A = 当前全部既有 migration，不含候选
location B = location A 的逐字节副本 + 候选
```

复制后立即比较 location A 与仓库源文件清单；location B 中既有文件必须与 A 完全一致。测试结束删除随机隔离数据库和临时目录。

不得通过在测试过程中重写仓库 migration 文件来模拟部署。

### 6.3 MySQL 隔离

- 使用 MySQL 8 required external-service 模式或明确的 MySQL 8 容器；
- 每个 suite 使用随机且带固定安全前缀的数据库；
- teardown 只允许删除该随机数据库；
- required 模式下 MySQL 不可用必须失败，不能 skipped；
- 不使用共享生产、生产克隆或开发业务 schema；
- 不连接 Redis，测试进程不要求任何 `RETENTION_REDIS_*` 环境变量。

## 7. 全量 checksum 证据

### 7.1 文件清单

开发前生成 `before` 清单；候选加入后生成 `after` 清单。比较规则：

```text
existing.before.paths == existing.after.paths
existing.before.sha256 == existing.after.sha256
existing.before.flywayChecksum == existing.after.flywayChecksum
after.paths - before.paths == {candidate}
```

若最终选择路径 B 且不新增 migration，则两份清单必须完全相等。

### 7.2 schema history 清单

对已越过 V133 和 V133 失败恢复场景，在每个 Flyway 动作前后快照：

```text
installed_rank
version
description
type
script
checksum
installed_by
installed_on
execution_time
success
```

比较时允许：

- 新 migration 成功后新增其自己的 success 行；
- `repair` 删除 V133 的 failed 行；
- V133 重跑成功后新增新的 V133 success 行；
- execution_time/installed_on 仅出现在新成功行。

不允许任何既有 success 行被 UPDATE、DELETE、重排或 checksum 对齐。测试应比较 repair 前后所有 success 行，而不是只查询 `version='133'`。

## 8. 固定 Flyway 调用序列

每个正常场景都必须执行并记录：

```text
info
validate
migrate
validate
info
```

断言：

- 对已有健康 history 的数据库，第一次 validate 成功；
- 对尚无 schema history table 的全新空库，Flyway 9.22.3 的显式 validate 会把所有 resolved versioned migrations 报为 pending/not-applied；该调用仍必须执行并精确分类，随后由 migrate 的默认 validateOnMigrate 继续执行，禁止用 ignore pattern 消音；
- migrate 的执行数精确；
- 第二次 validate 成功；
- 最终 info 没有意外 `Pending`、`Ignored`、`Out of Order`、`Failed`、`Missing` 或 `Future`；
- 再次 migrate 为 0 migration no-op；
- 再次 validate 仍成功。

失败恢复场景固定为：

```text
info
validate
migrate  // 预期在 V133 失败
info
validate // 预期因 failed migration 失败，记录准确原因
repair
info
validate
migrate
validate
info
migrate  // 0 migration no-op
validate
```

禁止省略失败后的 `info`/`validate`，也禁止用直接删除 schema history failed 行代替 Flyway repair。

## 9. Required 验收矩阵

### 9.1 A0：现状基线

使用 location A：

- 全新空库从 V1 migrate；
- 精确断言 V133 failed；
- 记录 V132 结束后的 schema 和全部 success history；
- 记录 V133 异常链和依赖索引/FK 签名；
- 该场景是候选前基线，不是最终通过结论。

### 9.2 A1：全新空库 + 路径 A 候选

使用 location B：

- 执行第 8 节正常全链，target 为当前唯一最高版本；
- `<PRE_V133_VERSION>`、V133、V134…V368 全部 Success；
- V133 目标唯一索引签名精确；
- 旧索引最终状态符合 V133 原意；
- 所有外键仍存在且 delete/update rule 不变；
- 空表和带代表性 V132 历史行两种 fixture 均成功，迁移前后业务行主键、行数和字段摘要守恒；
- 若历史行不满足 V133 目标唯一约束，迁移必须明确失败并保留可诊断证据，不得静默去重或改写业务数据；
- 插入合法/重复/外键非法 fixture，证明唯一和 FK 语义未弱化；
- 最终无冗余候选索引，或存在经过评审批准且有明确用途的 FK 支撑索引。

### 9.3 A2：已成功越过 V133 的库

先在不包含候选文件的 location A 下建立至少三种“已成功越过 V133”的数据库证据，再切换 location B。由于当前空库链本身会在 V133 失败，这三种库不能由未修复的空库直接 migrate 得到；必须使用可信历史 schema/history fixture，或在 V133 前预置已从实际成功环境证明存在的 FK 支撑索引，再由 location A 执行 V133。fixture 的 schema history、索引签名和来源必须记录，且不得包含候选 migration 的 success 行：

1. 当前版本恰为 V133；
2. 当前版本为 V353；
3. 当前版本为 V368。

每种都执行第 8 节正常全链。切换 location 后必须满足：

- validate 成功；
- 候选不呈现意外 `Pending`、`Ignored` 或 `Out of Order`；
- migrate 不重放 V133，不修改现有业务表和历史数据；
- 所有既有 success history 行逐字段不变；
- 最终 schema 与新空库目标 schema 的约束/索引签名一致。

如果 Flyway 9.22.3 将前置候选标记为 `Ignored` 并导致 validate 失败，路径 A 判定失败；不得用 `ignoreMigrationPatterns` 消音。

### 9.4 A3：已经在 V133 失败的库

先用 location A 制造真实 V133 failed 库，再切换 location B：

- repair 前 success history 必须精确覆盖 V1～V132，另有一条 V133 failed；
- repair 只移除 failed V133，不修改任何 success 行；
- repair 后候选必须能够按正常顺序执行，否则路径 A 失败；
- V133 重跑成功；
- 最终 V368 validate/no-op 成功；
- MySQL 隐式提交留下的对象经过显式 signature 检查，不因“对象已存在”被静默接受错误定义。

### 9.5 A4：候选故障与 forward repair

若路径 A 仍可行，必须对候选自身做故障注入：

- 同名索引、错误 unique 属性；
- 同名索引、错误列顺序；
- 正确列但 prefix/collation/可见性不一致；
- MySQL DDL 已隐式提交，但 Flyway 记录 failed；
- repair → 修正 fixture → re-migrate；
- 最终 signature、history、no-op 全部正确。

候选不得对不兼容同名对象执行 `IF EXISTS → SELECT 1` 静默通过。

### 9.6 B1：路径 B 基线豁免

若路径 A 失败，使用经批准的真实 V353 schema-only artifact：

- 校验 artifact SHA-256 和来源 history；
- 重建随机隔离数据库；
- Flyway baseline 的 version、description 和时间证据明确；
- 执行 V354→V368 的完整 `info → validate → migrate → validate`；
- 再次 migrate 为 no-op；
- 全部 WP2/WP6/WP7/WP8 schema signature 和 Required 测试通过；
- 报告明确保留“V1→V133 未修复，由正式豁免关闭”的事实。

### 9.7 本次 spike 实际矩阵结果

本次用候选 `V132_1__prepare_article_batch_channel_slot_unique.sql` 执行了足以决定路径的 A0/A1/A2/A3 子集：

| 场景 | 结果 | 结论 |
|---|---|---|
| 全新空库至 V133，包含候选 | 135 个 versioned migration 成功；V132.1/V133 均为 Success；最终 validate 成功；二次 migrate 为 0 | 候选 SQL 可以修复空库的 V133 DDL 顺序 |
| 已成功越过 V133，再引入候选 | V132.1 为 Ignored；`validateWithResult` 失败；`validate()` 抛出 FlywayException | A2 硬门禁失败，路径 A 被证伪 |
| 真实 V133 failed → repair → 候选 → V133 | repair 只移除 failed V133；全部既有 success history 逐字段不变；随后 2 个 migration 成功 | failed 库可 forward repair，但不能抵消 A2 失败 |
| 既有 migration bytes | 368 个正式 migration 的 ordinal-path manifest SHA-256 为 `021cdb2cece09224d2e2ed06bda1b7d34b0ce928af8c7410c2ca3b6f981a95b4`；V133 SHA-256 仍为 `f41035f934d51d1d08eae593b5320ba02588841f2523818c4911bcb254ed3ce2` | 未修改既有 migration |

全新空库的第一次显式 validate 精确报告 135 个 `RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED`；这是 Flyway 9.22.3 对无 history 空库的实际语义。migrate 内部校验、迁移后 validate 和 no-op 均成功。已越过 V133 的 validate 失败则由候选的 `Ignored` 状态导致，属于路径 A 明确禁止的状态，不能豁免。

路径 A 已在 A2 被确定性证伪，因此没有继续扩张 A4 故障注入或把候选迁移到正式 resources。路径 B 尚无已批准的真实 V353 artifact，本轮也未执行 B1。

## 10. 数据与业务副作用断言

V133 Required profile 全程只允许 schema migration 和测试 fixture。必须断言：

- Redis 连接工厂、Redis template、dynamic switch gate/service 均未实例化或调用；
- 测试无需 Redis host、port、username、password 或 database；
- `data_retention_dynamic_switch_change` 若已由后续 migration 创建，最终行数为 0；
- 不存在 target=true/false change；
- 不创建 execute run、operation、attempt、item、commit、poll marker 或 WP8 cleanup run；
- 不调用 scheduler、coordinator、domain executor、detail cleanup service 或 canary service；
- application 配置与 Java properties 中所有 execute/enable-write/detail-cleanup 默认值仍为 false。

可以通过测试类依赖扫描和最终 MySQL 表行数共同证明，不要求连接 Redis 做“前后 key 对比”，因为连接本身即超出本 profile 范围。

## 11. MySQL DDL 失败处置口径

### 11.1 本地和生产克隆

MySQL DDL 可能隐式提交，不能承诺普通事务式 rollback。测试和操作手册统一使用：

```text
失败
→ 停止继续 migrate
→ 盘点已创建/删除/修改对象
→ 与预期 signature 比较
→ 恢复安全 fixture 或执行已评审的 forward repair
→ Flyway repair 只处理 failed history
→ re-migrate
→ validate + signature + data conservation
```

### 11.2 未来生产 migration

本文不授权生产执行，但后续变更单必须明确：

- 迁移前一致性备份和恢复演练；
- 每条 DDL 的预期 algorithm、lock 和不可逆点；
- 第一个隐式提交 DDL 成功后，事务 rollback 已不再可用；
- 失败时是恢复备份还是 forward repair 的判定人、时限和命令；
- Flyway repair 不能代替 schema/data 恢复；
- 失败后禁止继续启动 execute/canary；
- 所有开关仍为 false，恢复完成后也不自动 enable。

## 12. 代码与文件授权边界

本文聚焦评审通过前，只允许修改：

```text
docs/data-retention-p0-handoff-2026-08-14.md
docs/data-retention-p0-v133-history-chain-closure-spec-2026-08-15.md
```

第一决策门授权 spike 后，实际新增或修改：

```text
geo-server/src/test/resources/retention/v133-candidate/V132_1__prepare_article_batch_channel_slot_unique.sql
geo-server/src/test/java/.../DataRetentionV133HistoryChainMysqlIntegrationTest.java
geo-server/src/test/java/.../DataRetentionV133MigrationContractTest.java
geo-server/pom.xml  // 仅当需要增加独立 required profile/test include
docs/data-retention-p0-v133-history-chain-closure-spec-2026-08-15.md
```

路径 B 不允许新增兼容 migration；只允许新增经批准的基线 artifact manifest、重建脚本/测试和豁免记录。实际 schema artifact 是否进入仓库必须单独评审其体积与敏感信息。

由于路径 A 已被证伪，正式 `src/main/resources/db/migration` 没有新增 V132.1；测试资源中的候选是隔离的可执行证据，不会被应用启动时的 Flyway location 扫描。

任何路径都禁止修改 V1～V368 的既有文件，包括 V133、V354、V366、V367 和 V368。

## 13. Required profile 与报告

已新增独立 profile：

```text
retention-v133-it
```

required 语义：

- 只运行 V133 contract/MySQL integration tests；
- MySQL 不可用时 failure，0 skipped；
- 不要求 Redis，不运行 Redis 测试；
- 输出 Flyway/MySQL exact versions；
- 输出 spike 证据结论与建议路径，不自我批准最终路径；
- 输出 before/after migration manifest SHA-256；
- 输出每个场景的最终 migration state 汇总；
- 任一意外 state、既有 checksum 变化、success history 变化或默认开关 true 都失败。

本次执行命令使用外部 MySQL required 模式，Surefire 结果为：

```text
DataRetentionV133HistoryChainMysqlIntegrationTest: 3/3
DataRetentionV133MigrationContractTest: 2/2
total: 5/5, failures=0, errors=0, skipped=0
```

最终报告不能只写“tests passed”，必须至少包含：

```text
Tests run / failures / errors / skipped
Flyway version
MySQL version
spike evidence result / recommended path
fresh chain result
already-past-V133 results
failed-V133 repair result
existing migration manifest digest before/after
Redis connections = 0
dynamic switch writes = 0
execute/enable-write defaults = false
```

## 14. SPEC 聚焦评审问题

评审只需回答以下问题，不扩展到生产 clone/canary：

1. 路径 A 的“双向 Flyway 状态门”是否足够严格；
2. 是否允许在 spike 中研究 `<PRE_V133_VERSION>`，但在状态矩阵通过前不冻结具体版本和 SQL；
3. 是否同意 `Ignored/Pending/Out of Order` 任一意外状态直接否决路径 A；
4. 是否同意禁止全局 outOfOrder、宽泛 ignore pattern 和 validate 降级；
5. checksum/history 全量对照是否覆盖所有既有 migration，而不只 V133；
6. V133 failed repair 是否严格限制为移除 failed 行且不改写 success 行；
7. 路径 B 的批准材料是否满足 WP2 第 18.2.1 节；
8. no-Redis/no-dynamic-write/default-false 证据是否明确；
9. MySQL DDL 不可逆与 forward repair 口径是否准确；
10. 本轮是否输出 `LOCAL_SPIKE_AUTHORIZED`，授权下一阶段“本地 spike + 测试开发”，但继续禁止预先选择最终路径、生产克隆和生产执行。

## 15. 开发授权前验收清单

- [x] 本 SPEC 聚焦评审明确输出 `LOCAL_SPIKE_AUTHORIZED`；
- [x] 授权范围仅限本地 V133 spike、候选 migration、contract/MySQL 测试和文档；
- [x] 开发开始前扫描全部 refs/worktree 的 V132～V133 之间版本占用；
- [x] 当前 V1～V368 migration manifest 已冻结；
- [x] V133 及所有既有 migration 字节未修改；
- [x] 路径 A 未被预先宣布可行，并已由 A2 实测证伪；
- [x] `PRE_V133_COMPATIBILITY_APPROVED`、`V353_BASELINE_WAIVER_APPROVED` 和 `BLOCKED_NEEDS_NEW_GOVERNANCE` 保留到 spike 后第二决策门；
- [x] 路径 B 的批准人和 artifact 要求未被弱化；
- [x] required profile 明确 MySQL required、Redis forbidden；
- [x] 所有 execute/enable-write/detail-cleanup 默认值为 false；
- [x] 生产克隆、生产 migration、cleanup、canary 和 enable 仍未授权。

## 16. spike 完成与第二决策门标准

### 16.1 路径 A 证据完成标准

只有全部满足，spike 报告才可以建议 `PRE_V133_COMPATIBILITY_APPROVED`：

- [ ] `<PRE_V133_VERSION>` 排序、解析和名称唯一；
- [ ] A0～A4 全部通过；
- [ ] 新空库 V1→V368 成功；
- [ ] V133/V353/V368 既有库均完成 `info → validate → migrate → validate`；
- [ ] 无意外 Pending/Ignored/Out of Order/Failed/Missing/Future；
- [ ] failed V133 repair 不改写任何 success history；
- [ ] 全部既有 migration SHA-256 和 Flyway checksum 前后相等；
- [ ] 最终索引/FK signature 与数据约束正确；
- [ ] MySQL 隐式提交故障可 forward repair；
- [ ] required profile 0 skipped；
- [ ] Redis connections=0、dynamic switch writes=0；
- [ ] 所有安全默认值仍为 false；
- [ ] 聚焦代码复审无 P0/P1/P2。

### 16.2 路径 B 证据完成标准

路径 A 被证伪后，只有第 5.2 和 9.6 节全部材料齐全、批准人完成书面签署且第二次聚焦复审通过，才能输出 `V353_BASELINE_WAIVER_APPROVED`。缺少任一材料只能输出 `BLOCKED_NEEDS_NEW_GOVERNANCE`。

### 16.3 最终状态边界

第二决策门与 spike 最终代码/证据复审是同一个批准点。只有候选 migration、Required 矩阵和代码复审均完成，才能输出 `PRE_V133_COMPATIBILITY_APPROVED`；只有基线 artifact、rebuild 测试、书面批准和最终复审均完成，才能输出 `V353_BASELINE_WAIVER_APPROVED`。仍有后续本地收口工作时只能输出 `BLOCKED_NEEDS_NEW_GOVERNANCE` 或保持待复审，不得提前使用两个 `APPROVED` 状态。

即使 V1 重建轨道的 V133 历史链阻断最终关闭，也不表示生产 ready。

### 16.4 本次建议提交第二决策门的状态

路径 A 因 A2 的 `Ignored + validate failed` 被证伪；路径 B 所需的可信 V353 artifact 和书面批准尚未完成。依照 16.2～16.3，本次 spike 证据建议输出：

```text
BLOCKED_NEEDS_NEW_GOVERNANCE
```

该文本是提交评审的建议，不是自我批准。第二次聚焦复审明确输出前，V1 空库/灾备重建能力继续保持未关闭；既有 V300+ 数据库的匿名化克隆可以按独立变更授权推进，但不得使用候选 V132.1，也不得宣称 V1 全链健康。

## 17. 后续固定顺序

既有历史数据库轨道严格按以下顺序推进：

```text
确认目标 history 已成功越过 V133，且正式 location 不含 V132.1
→ 匿名化生产克隆 MySQL 8.0.46
→ metadata lock / 容量 / 空间 / 历史数据守恒报告
→ 生产 migration 独立变更审批
→ 以全部 execute=false 执行生产 migration
→ WP8 readiness
→ canary 人工审批与执行
→ 逐域 enable 审批
```

任一步失败都保持 fail closed，不得跳过阶段，不得因为 WP6～WP8 本地完成而提前进入生产 migration 或 canary。V1 空库/灾备重建轨道并行保持未关闭，直到可信基线豁免或新的治理决定完成；它不再被错误用作既有历史前向迁移轨道的串行前置条件。
