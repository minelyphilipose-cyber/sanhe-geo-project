# 数据定期清理：生产上线准备与审批清单

> 日期：2026-08-17
>
> 状态：准备中；未授权生产连接、生产 migration、生产 cleanup、canary 或 enable
>
> 适用对象：`flyway_schema_history` 已成功越过 V133、当前处于 V300+ 的既有测试/生产数据库

## 1. 当前可作为上线输入的事实

- WP2～WP8 的本地代码、聚焦复审及各自 Required 验收已完成；
- 同一工作树本轮复跑 V133 5/5、switch 37/37、coordinator 71/71、website 72/72、poll 86/86、WP8/MySQL 60/60，全部 0 failure、0 error、0 skipped；
- Article archive/purge 新增真实 MySQL 8 + MinIO Required profile，1/1 通过；该结果只覆盖本地开发 provider，生产使用 COS，不能用 MinIO 证据替代 COS 验收；
- 后端 package 与前端 production build 通过；
- migration location 共 368 个唯一版本，扫描 83 个 Git refs 未发现 V354～V368 竞争版本；
- V132.1 只存在于 test resources 的 V133 spike，不在正式 migration location；
- 运营提供的生产 history 截图显示当前最高成功版本为 V353，V133 行为 Success；
- 发布范围已决定为全部未发布内容共发，数据库将从 V353 顺序执行 V354～V368；
- 所有 execute、dynamic enable-write、detail-cleanup 默认值保持 `false`。

以上只证明“可进入生产准备”，不等于生产准入。

## 2. 两条历史轨道不得混用

### 2.1 既有 V300+ 数据库

生产和测试数据库如果真实 history 已成功越过 V133，不会重新执行 V133。它们可以在完成本清单的独立审批后，从各自当前成功版本继续前向迁移。

进入该轨道前必须只读确认：

```text
V133 = Success
当前 version > V300
无 failed / ignored / out-of-order
所有已应用 migration checksum 与发布候选一致
正式 migration location 不含 V132.1
```

任一条件不满足都必须停止，不能用 `repair`、ignore pattern 或 out-of-order 把异常库强行归类为正常既有历史库。

### 2.2 V1 空库/灾备重建

路径 A 已被 spike 证伪：前置 V132.1 虽可修复新空库，却会在已越过 V133 的数据库上成为 `Ignored` 并使默认 validate 失败。路径 B 的可信 V353 baseline artifact 和书面批准仍未完成。

因此 V1 空库/灾备重建轨道继续保持 `BLOCKED_NEEDS_NEW_GOVERNANCE`。它不阻断正常 V300+ 数据库的前向克隆验证，但上线方案必须明确记录：当前灾备重建不能从 V1 migration 链直接恢复。

## 3. Gate 0：冻结发布候选

负责人：研发；审批人：技术负责人。

- [x] 决定 V355～V358、V360～V363、V365 与 retention 共发；生产将从 V353 顺序执行 V354～V368；
- [ ] 把全部未发布 Java、前端、migration、运营影响和测试纳入同一候选；
- [ ] 从当前大型 dirty worktree 形成可审查 commit，禁止 `git add .`；
- [ ] 逐 hunk 复核 `pom.xml`、`application.yml`、`AdminLayout.vue`、`router/admin.ts`；
- [ ] 排除 `node_modules`、`target`、logs、deploy tar、凭据和本地临时文件；
- [ ] 记录 commit SHA、构建制品 SHA-256、368 个 migration 的 path/length/SHA-256/Flyway checksum；
- [ ] 从该 commit 运行 `scripts/data-retention-required-check.ps1`，结果必须与本地记录一致；
- [ ] 再次扫描全部 refs/worktree，确认没有 migration version 冲突；
- [ ] 所有默认写门仍为 `false`。
- [ ] 增加生产等价的 Article MySQL + COS Required profile；使用专用测试 bucket/prefix，覆盖 COS 写入、readback SHA-256、stat、数据库正文 purge、对象保留和精确清理；

Gate 0 输出：唯一 commit、唯一后端制品、唯一前端制品、migration manifest、测试报告。不满足时不得创建生产 migration 变更单。

## 4. Gate 1：生产环境只读事实采集

负责人：DBA + 研发；审批人：DBA 负责人。

只允许只读连接，禁止启动 Flyway migrate/repair：

- [ ] MySQL exact version、字符集、collation、时区、实例拓扑；
- [x] 截图确认当前最高成功 schema version 为 V353；仍需用只读 SQL 导出形成机器证据；
- [x] 截图确认 V133 为 Success；仍需完成全部历史 checksum 对照；
- [ ] `flyway_schema_history` 全量只读导出；
- [ ] 无 failed、ignored、out-of-order 或重复版本；
- [ ] V354～V368 哪些为 Pending，实际 pending 顺序与候选一致；
- [ ] 目标表行数、索引、外键、CHECK、表大小和预计 DDL 临时空间；
- [ ] metadata lock 风险、长事务、复制延迟、备份窗口和峰值业务时段；
- [ ] Redis 只记录连接目标，不在 migration 阶段连接或写开关；
- [ ] 明确记录生产 `geo.storage.provider=COS`，核对 COS region、bucket、endpoint、服务账号权限和 read-fallback 策略；不得把本地 MinIO 结果当作生产对象存储证据。

Gate 1 输出必须脱敏，原始凭据不得进入仓库或验收报告。

## 5. Gate 2：匿名化生产克隆全链验证

负责人：DBA + 研发 + 运营；审批人：技术负责人和 DBA 负责人。

- [ ] 使用生产备份创建匿名化、网络隔离且可整体销毁的克隆；
- [ ] 克隆 MySQL 版本、参数、字符集和关键容量尽量与生产一致；
- [ ] migration 前保存全 history、schema signature、表/索引容量和关键业务计数；
- [ ] 使用候选制品执行 `flyway info → validate → migrate → validate`；
- [ ] validate 前后不得出现意外 Pending/Ignored/Out of Order；
- [ ] 不执行 `repair`，除非迁移演练明确进入失败处置分支；
- [ ] 演练 metadata lock、DDL 时长、临时空间、复制影响和应用启动耗时；
- [ ] migration 后核对 V354～V368 schema contract、CHECK 与权限；
- [ ] 启动应用时所有 execute/enable-write/detail-cleanup 仍为 false；
- [ ] 运行 Phase 0、只读 dry-run、WP8 readiness 和业务 smoke；
- [ ] 使用克隆数据执行批准的小批 Website/Poll/Article mutation，并逐表核对保留/释放；
- [ ] Article mutation 使用隔离的真实 COS 测试 bucket/prefix，验证腾讯 COS SDK 的 put/read/stat/error mapping；禁止向生产业务前缀写测试对象；
- [ ] 形成失败演练：备份恢复、forward repair、停止条件和责任人；
- [ ] 全程不把匿名数据、对象或凭据提交到仓库。

克隆验证失败必须修复候选并从 Gate 0 重新生成制品，不能在生产现场临时改 SQL。

## 6. Gate 3：生产 migration 单独审批

负责人：变更负责人；批准人：业务、研发、DBA、运维。

变更单至少包含：

- [ ] 唯一制品 SHA、commit SHA、migration manifest 和克隆报告；
- [ ] 维护窗口、预计时长、容量余量、metadata lock 观察人；
- [ ] 迁移前一致性备份及已验证的恢复步骤；
- [ ] 应用停写/降载策略和连接池处置；
- [ ] 每个 migration 的不可逆点与 forward repair 方案；
- [ ] 明确 MySQL DDL 不承诺普通事务式 rollback；
- [ ] 失败时停止继续 migration 的错误代码、时限和负责人；
- [ ] 禁止 `repair` 改写任何既有 Success history；
- [ ] migration 阶段不连接 Redis、不写 dynamic switch；
- [ ] migration 完成后先 validate 和只读验收，不立即 enable cleanup。

## 7. Gate 4：暗发布与只读验收

生产 migration 成功后，应用仍以全部写门关闭启动：

```text
scheduler.enabled = false
scheduler.execute-enabled = false
website execute-enabled = false
article archive execute-enabled = false
article purge execute-enabled = false
poll execute-enabled = false
dynamic-switch.enable-write-enabled = false
detail-cleanup.enabled = false
```

- [ ] health、Flyway validate、schema version、权限和 audit writer identity 正常；
- [ ] `/api/data-retention/operations/status` 正确显示静态门、storage provider、Redis switch 和 readiness；
- [ ] storage provider 必须显示 COS；若显示 MinIO 或 fallback 状态与审批不一致，停止 Article 验收；
- [ ] 三个 Redis execute switch 保持 disabled；
- [ ] dry-run 只产生审计，不修改候选业务大字段；
- [ ] 运营逐项确认 Website、Poll、Article 候选和反例；
- [ ] 告警、审计、指标、日志脱敏和 dashboard 可用。

## 8. Gate 5：WP8 readiness 与 detail cleanup

WP8 detail cleanup 与 Website/Poll execute 分开审批：

- [ ] 先只读计算 invariant、eligible backlog、最老 anchor、容量等级和五年投影；
- [ ] readiness 非 READY 时保持 `BLOCK_ONLY`，不得为了通过而改数据或伪造 heartbeat；
- [ ] detail cleanup scheduler 的首次生产启用有独立审批、lease 观察和单 run 上限；
- [ ] candidate warning 不污染 succeeded heartbeat，sweep/infrastructure/unknown 仍 fail-closed；
- [ ] 核对 purge count 守恒、永久 marker、operation/run 水位和关联 run 回放；
- [ ] 关闭 detail cleanup 后确认 lease 释放、默认配置未改变。

只有持续满足 SPEC 的 readiness 条件，才能申请 canary。

## 9. Gate 6：canary

负责人：运营执行；批准人：双人审批 + 技术值守。

- [ ] canary 先选择一个 domain，不同时打开 Website 与 Poll；
- [ ] 使用稳定、已复核且可恢复的小范围候选；
- [ ] 静态 execute gate、global Redis switch、domain Redis switch 分步开启并记录 change ID/version；
- [ ] 首批 `requestLimit=1`，transaction/candidate/time budget 使用最小值；
- [ ] 执行前后核对 operation、attempt、item、commit/marker、run rollup；
- [ ] 核对轻量业务行、URL、latest projection、summary、preserved ledger；
- [ ] 任一 invariant、unknown outcome、lease、readiness、容量或基础设施异常立即 fail closed；
- [ ] canary 完成后立即把 dynamic switch 写回 false 并读回确认新 version；
- [ ] 观察期通过后才申请下一 domain 或扩大 limit。

Article archive/purge 目前不在 WP5 coordinator 内，必须使用独立静态门、独立变更单和真实 COS readback/checksum/stat 证据，不能混入 Website/Poll canary。MinIO 的本地 Required profile只能证明业务协议和对象存储抽象路径，不能批准生产 Article canary。

## 10. Gate 7：逐域放量与常态运行

- [ ] 按 `1 → 5 → 20` 扩大 request limit，每一级都有观察窗口；
- [ ] Website、Poll、Article、WP8 分开记录吞吐、空间释放、warning、rollback 和 retry；
- [ ] 自动 global disable 的 change identity、人工重新 enable 后再次 disable 场景受监控；
- [ ] deferred retry、exact retry、cleanup-only ownership 和 unknown/manual review 队列有值守；
- [ ] 定期核对 retained detail、marker/count 半状态、zero-tree 和水位；
- [ ] 达到稳定期后仍保留 kill switch、双人审批和审计导出。

## 11. 上线前当前仍缺的明确交付物

1. 把全部未发布内容整理为干净 commit，并完成全应用后端/前端回归；
2. 从该 commit 构建的不可变制品及完整 migration manifest；
3. 使用专用 COS 测试 bucket/prefix 的 Article MySQL + COS Required 验收；
4. 生产 `flyway_schema_history` 全量只读导出和 checksum 对照；
5. 匿名化生产克隆报告；
6. 生产容量、metadata lock 和 DDL 时长实测；
7. 备份恢复/forward repair 演练记录；
8. 生产 migration 变更单和四方审批；
9. 生产 Phase 0、WP8 readiness 与运营候选签字；
10. Website/Poll/Article 分域 canary 方案及双人审批；
11. V1 空库/灾备重建的新治理决定或正式 V353 baseline 豁免。

前十项决定既有 V300+ 生产轨道能否上线；第十一项是并行的灾备重建治理，不应被误写为既有数据库前向 migration 的串行阻断。
