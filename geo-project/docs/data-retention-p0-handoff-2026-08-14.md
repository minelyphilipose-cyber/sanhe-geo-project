# 数据定期清理 P0 完整交接文档

> 交接日期：2026-08-15
>
> 工作区：`D:\code\sanhe-geo-project`
>
> 项目根目录：`D:\code\sanhe-geo-project\geo-project`
>
> 当前分支：`codex/sprint3-spike-article-list-trigger`
>
> 当前 HEAD：`981307b02`（`merge: reconcile remote master history`）
>
> 本交接只描述当前事实和下一步，不授予新的开发、迁移或生产执行权限。

## 2026-08-15 最终续作更新（优先于下方原始快照）

- 2026-08-17 发布候选整理与本地总回归已完成。后端 package、前端 production build 通过；同一工作树复跑 V133 5/5、switch 37/37、coordinator 71/71、website 72/72、poll 86/86、WP8/MySQL 60/60，全部 0 failure、0 error、0 skipped。新增 `retention-article-minio-it` 使用随机 MySQL 测试库和随机 MinIO bucket 完成 archive 写入、readback SHA-256、24h grace 后数据库正文 purge、对象/归档元数据保留，1/1 通过并自清理；未修改当前 `geo` 业务样本或 `geo-files` bucket。
- 生产对象存储由运营确认为 COS。上述 MinIO profile 仅是本地开发证据，不覆盖 `CosObjectStorageAdapter` 的真实 SDK/region/bucket/endpoint/IAM/stat/error mapping，不能作为生产 Article 准入。生产前必须使用专用 COS 测试 bucket/prefix 完成 Article MySQL + COS Required 验收；禁止向生产业务前缀写测试对象。
- 运营提供生产 `flyway_schema_history` 截图，确认当前最高成功版本为 V353，V133 为 Success；所有未发布内容决定同批上线，因此正式数据库发布范围为从 V353 顺序执行 V354～V368，并将中间 V355～V358、V360～V363、V365 的业务代码和回归一并纳入。截图关闭版本/范围判断，但不替代完整 history 只读导出、全 checksum 机器对照或匿名生产克隆。
- 候选已按职责形成四个可审查提交：`58d252472`（self-media）、`4588867cd`（retention）、`d3b814574`（admin）、`ff50ad665`（docs）。本地 `node_modules` 生成缓存与既有 `geo-project-deploy.tar.gz` 均未纳入；唯一后端/前端制品及 SHA-256 尚待从最终 Gate 0 commit 的干净 worktree 生成。
- `803f518f5` 新增 `retention-article-cos-it` 与 `scripts/data-retention-required-check.ps1 -IncludeCos`。COS 测试只接受 `geo-retention-it/` 下的专用前缀，覆盖真实 SDK put/read/stat、数据库正文 purge、对象保留、审计、精确前缀清理和 404 mapping；当前进程未注入外部 MySQL/COS 环境变量，只完成编译验证，不能记录为真实 COS 验收通过。
- `docs/data-retention-migration-manifest-2026-08-17.csv` 包含全部正式 migration 的 path、LF-canonical bytes/SHA-256 与 Flyway 9.22.3 兼容 checksum，避免 Windows `autocrlf` 导致同一 commit 生成不同清单；文件 SHA-256 为 `0f0c8a9bc89a0804ad121058e8b39dd91f1b8a0b72b071c814e72b5186a70d22`。V133、V340、V353 checksum 分别为 `971462112`、`258457011`、`-1211774692`，与生产截图一致。
- WP6 已完成本地代码实现、聚焦复审及本地 MySQL 8/Redis required profile 验收；`retention-website-it` 为 71/71，包含真实 coordinator、`WebsiteRetentionDomainExecutor`、`WebsiteRetentionBusinessRepository`、20 篇整批 mutation、V366 CHECK、WP3 永久证据和 `deleted + payload-only` 复热回归。
- WP7 SPEC 与代码聚焦复审均已通过，可正式记录为“WP7 本地代码实现、代码复审及 Required 验收完成”。最终确认 deferred retry 日期恢复、cleanup 独立 20-ID exact retry 上限、逐子表 post-check，以及真实 scheduler `recovery_not_started → exact retry → committed` 链路成立；最终非容器聚焦复跑为 14/14。
- WP8 SPEC 与代码聚焦复审均已通过，可正式记录为“WP8 本地代码实现及 Required 验收完成”。最后一个非阻断 P2 已关闭：operation 清理确认提交或 committed replay 后，service 会根据永久 operation-run 关系重新尝试所有关联 run 水位；强制 clean 聚焦集为 38/38，完整 `retention-mysql-it` required profile 为 60/60，均为 0 failure、0 error、0 skipped。
- WP6～WP8 的完成只表示本地实现和声明的 Required 验收完成，不表示生产可执行。生产/测试数据库已在 V300+ 且 history 成功越过 V133，不会重跑 V133；既有历史前向迁移轨道的顺序为：核验真实 history → 匿名化生产克隆 → 生产 migration 单独审批 → WP8 readiness → canary 人工审批 → 逐域 enable。V1 空库/灾备重建链继续作为独立治理项关闭。
- V133 必须独立形成 SPEC 并聚焦评审。不得修改任何已发布 migration；不得预设排序在 V133 之前的兼容 migration 一定可行。必须用仓库实际 Flyway 9.22.3 验证新空库和已越过 V133 数据库的 `info → validate → migrate → validate` 状态，且后者不得出现意外 `Pending`、`Ignored` 或 `Out of Order`。
- V369 或 repeatable migration 无法挽救已在 V133 中断的新空库链。若前置兼容 migration 不能同时满足新空库修复和既有库 validate 安全，必须回到 WP2 已批准的正式 V353 基线豁免路径，不得通过放宽全局 Flyway validate、启用全局 out-of-order 或静默忽略 migration 状态绕过。
- V133 验收还必须覆盖：所有既有 migration 文件 SHA-256/Flyway checksum 前后对照；失败库 repair 不改写任何既有成功 migration；全程不连接 Redis、不产生动态开关写入；所有 execute/enable-write/default housekeeping 开关保持 `false`。
- 生产 migration 的失败处置使用 MySQL DDL 真实口径：备份恢复、forward repair、失败对象盘点和明确不可逆点，不承诺普通事务式 rollback。
- 当前最高 migration 为唯一 V368；V133 方案开发前和提交前仍须重新扫描所有 refs/worktree 的版本号。当前工作区约 211 个 status entry，WP6～WP8 文件仍有未跟踪项；禁止 reset、checkout、覆盖、暂存或整理无关修改。
- 本地外部 MySQL 8 与开发 Redis 可用；Docker daemon 不可用不再阻断已经通过显式外部服务模式完成的 WP6～WP8 required 证据。生产 MySQL 8.0.46、匿名化克隆、metadata lock、最终容量、生产 migration 和 canary 仍未执行、未授权。
- V133 第一决策门已输出 `LOCAL_SPIKE_AUTHORIZED`，本地 spike 与独立 `retention-v133-it` profile 已完成：5/5、0 failure、0 error、0 skipped。候选 V132.1 能修复全新空库和 V133 failed+repair，但已越过 V133 的库将其标记为 `Ignored` 并导致默认 validate 失败，因此路径 A 已被证伪。
- 候选 V132.1 只保留在 test resources 作为 spike 证据，未进入正式 migration location；368 个既有 migration 的 ordinal manifest SHA-256 为 `021cdb2cece09224d2e2ed06bda1b7d34b0ce928af8c7410c2ca3b6f981a95b4`，V133 字节与 SHA-256 未变。
- 路径 B 仍缺可信 V353 schema-only artifact、来源 history、artifact SHA-256、rebuild 证据和书面批准。spike 报告只建议第二决策门输出 `BLOCKED_NEEDS_NEW_GOVERNANCE`，尚未自我批准；这继续阻断 V1 空库/灾备重建健康声明，但不阻断已成功越过 V133 的 V300+ 数据库按独立审批进入匿名化克隆前向验证。
- 本地运营验收前置已补齐：新增只读 `GET /api/data-retention/operations/status`，统一暴露 Flyway compatibility、retention 与对象存储静态写门、storage runtime 及 WP8 readiness，复用 `data.retention.switch.read` 内部权限；新增 `docs/data-retention-local-operations-acceptance-2026-08-15.md`，冻结“源开发库只取样、API dry-run 与真实 mutation 使用版本匹配副本”的协作协议。新增及关联聚焦测试 17/17 通过。
- 运营已从 MySQL 8.0.45 的 `geo` 库以 READ ONLY 事务完成样本取证：website 正例 `990007577`、近期反例 `990007920`；poll `990006017 / 2026-07-08 / A`；article purge 阻断样本 `990006110 / version 381`。随后应用首次启动时 Flyway 已成功向 `geo` 应用 V365～V368，并报告当前为 V368；不得对这些成功 history 执行 repair。
- 本地对象存储当前 provider 为 MinIO，COS 未启用。状态接口检测到 COS 目标参数时只报告配置事实，不代表已启用或已授权；article archive/purge 的 readback/checksum 仍通过 MinIO，MinIO→COS migration 本轮禁止调用。
- V368 migration 后首次启动因 `WebsitePublishedContentCleanupService` 多构造器未标注生产注入构造器而失败。已为该 Service 及同类 Poll/execute/WP8 多构造器 Bean 补齐明确 `@Autowired`，并增加逐 Bean 真实 Spring 容器装配契约；启动修复聚焦集 11/11 通过。Phase 1 dry-run 会写 typed retention audit，但不会修改候选业务数据；真实 mutation 仍仅允许在可恢复副本。
- 本地运营 Phase 0/1 已实际执行。Phase 0 为 V368、全部 mutation 静态门禁关闭、Redis execute switch disabled；新增 audit writer 状态门，进程以显式 `local-acceptance-20260815.1` identity 启动。最终证据 run 为 website `40`、poll `41`、archive `42`、purge `43`；website 正例命中、近期反例排除、article purge 活动任务阻断均符合预期，且只读复核确认正文/payload/poll result/marker 未被修改。
- Poll slice `990006017 / 2026-07-08 / A` 最初在 run `41` 被 keyword/platform/entity-judge summary reconciliation 三项同时阻断；运营已批准只对该固定 slice 走既有 summary recompute。唯一候选预检后，backfill run `44` succeeded（1 slice、30 source、3 keyword summary、10 platform summary、0 failed/skipped），口径修正后的最终 Poll dry-run run `47` 为 1 eligible、0 blocked/warning，30 条结果中 latest 2、非 latest 28。该动作只更新业务 summary 与审计，未执行 retention mutation。
- 真实 Phase 1 暴露并修复 Poll typed dry-run 两项契约缺口：run scope 缺 fingerprint schema/hash，以及 attempt skipped/warning 与 run rollup 不一致；新增单测断言后相关聚焦集 9/9，前端生产构建通过。修复前产生的 run `34` 是无 operation 的本地失败测试残留（attempt succeeded、run running），不是验收证据，进入副本 mutation/readiness 前需按审计治理单独处置。
- 运营随后明确授权直接在本地开发 `geo` 删除数据，并确认开发数据均可删除。本轮以 scheduler disabled、Website/Poll static execute 临时开启、三个 Redis execute switch 临时 enabled、transaction/candidate budget 均为 1 的窗口完成真实小批验收：Website preflight/execute 为 run `48/49`，实际清理 selector 首个 article `990006426`；Poll preflight/execute 为 run `50/51`，清理固定 slice `990006017 / 2026-07-08 / A`。该授权只覆盖本地开发库两个批次，不扩展到测试、生产、MinIO/COS、WP8 detail cleanup 或后续批量。
- Website run `49` 为 1 candidate/1 affected：article 轻量行、task、发布证据及 URL 保留，正文 10,766 B 与 payload 1,160 B 归零并写 purge marker；operation `201ddb31-8805-4145-b097-19e3980cf1d1` committed，attempt `12` succeeded，item/commit 均为 1。预先批准的 `990007577` 未被修改，因为正式 execute 不接受 cursor 且 selector 按 article ID 升序；安全收口后的 dry-run run `52` 再次确认它仍为候选。
- Poll run `51` 为 1 candidate/1 affected：30 result 中删除 28、保留 ledger IDs `8029/8044`；删除 112 entity judge、10 shard、30 shard item，保留 2 result、8 entity judge、batch/summary/marker/ledger/audit。operation `2216f0e5-ecc1-45a3-8a59-d9502a436c7f` committed，attempt `14` succeeded；`poll_slice` 契约是 0 物理 item，以永久 slice marker 作为提交证明。收口 dry-run run `53` 返回 0 candidate，确认 marker 防止重复选择。
- 执行窗口已完全收口：三个 Redis switch 均读回 `enabled=0, version=2`；临时前端执行按钮/API 已移除；后端以 `local-acceptance-safe-20260815.1` 和全部静态 mutation/enable-write/detail-cleanup gate=false 重启，Phase 0 显示 V368、静态门禁全部关闭、Redis 三项 `VALID / disabled`。本轮未调用 archive/purge、WP8 detail cleanup、MinIO 对象删除或 MinIO→COS migration。
- 2026-08-17 在相同本地开发数据可删除授权下完成 `limit=5` 扩容。Website run `54` 初检，run `61/62` preflight/execute 5/5 committed，处理 `990006428、990006429、990006430、990006431、990006435`；五行正文和 payload 归零并写 marker，所有轻量行及 task/publish URL 保留。operation `b66dc2a7-3a95-4695-8f2d-f3087525d831` committed，attempt `28` succeeded，5 items、一个 item_count=5 commit。收口 dry-run run `63` 已选择下一组 ID，证明本批不重复入选。
- Poll 扩容范围为 `990006017 / 2026-07-01～2026-07-31 / A`。run `55/56/57` 证明前五个 slice 仅被三类 summary reconciliation 阻断；backfill run `58` 精确重算 5/5，run `59` 证明 5 eligible/0 blocked，execute run `60` 提交 5/5。`07-01、07-02、07-03、07-04、07-06` 共删除 143 result、560 entity judge、50 shard、150 shard item；五个 slice 均无 preserved result，因为其 projection 已被其他日期更新结果取代。五个 operation/attempt 全部 committed/succeeded，0 物理 item，五个永久 marker 已写入。
- 2026-08-17 执行窗口再次完全收口：Redis 三项从临时 `enabled=1, version=3` 写回 `enabled=0, version=4`；一次性 execute/backfill 前端 API 与按钮已移除；后端以 `local-acceptance-safe-20260817.1`、全部静态门禁 false 在 PID `63436` 启动，V368 与 Redis `VALID / disabled` 已从管理页复核。未触碰 archive/purge、WP8、MinIO/COS、生产、canary。
- 运营随后批准协议上限 `limit=20`。Poll run `64/65/66` 证明剩余 17 个 candidate 中 15 个仅被 summary reconciliation 阻断、2 个已 eligible；backfill run `67` succeeded（17 candidate、16 recomputed、1 个已 marker slice skipped、0 failed），run `68` 证明 17/17 eligible，execute run `69` 提交 17/17。共删除 283 result、1,100 entity judge、116 shard、308 shard item，保留 4 latest ledger；17 operations/attempts 全部 committed/succeeded，0 物理 item。
- Website run `70/71` 完成 20-item 协议上限：20 candidate/20 affected、0 skipped/warning；20 篇 article 转为 deleted，20 行正文/payload 归零并写 marker，所有轻量行与 20 个 task/publish URL 保留。operation `c36ab82f-1f84-415e-a592-94b0b3d173b0` committed，attempt `52` succeeded，20 items、一个 item_count=20 commit。
- 协议上限窗口已收口：Redis 三项由临时 `enabled=1, version=5` 恢复为 `enabled=0, version=6`；临时 execute/backfill 前端代码已移除；后端以 `local-acceptance-safe-limit20-20260817.1`、全部静态门禁 false 在 PID `24920` 启动并 validate V368。最终页面刷新时登录会话过期，门禁改由 Redis 直读、显式启动配置和 MySQL READ ONLY 查询收口；未绕过登录，未触碰 archive/purge、WP8、MinIO/COS、生产、canary。
- WP8 本地真实 scheduler/heartbeat 验收已完成。执行前只读基线为 26 operation、52 attempt、26 item、3 commit，最老 operation anchor 在 2026-08-15，365 天 eligible operation/operationless run 均为 0，三类 invariant 均为 0。专用进程 `local-acceptance-wp8-20260817.1` 只临时开启 detail-cleanup，并由真实 Spring scheduler 生成 succeeded cleanup run `f378cddb-11cf-4386-9afa-d0d6c7c5977b`：所有 candidate/purge/warning/backlog 计数为 0，容量样本 1,605,632 B、五年投影 NULL。readiness 因而从 `CLEANUP_NEVER_SUCCEEDED` 收敛为预期的 `CAPACITY_HISTORY_INSUFFICIENT / BLOCK_ONLY`，仍禁止 enable/canary；没有篡改时间戳，也没有删除未满 365 天的审计 detail。
- WP8 窗口已收口：专用 Redis lease 已释放，三个 dynamic execute switch 保持 `enabled=0, version=6`；后端以 `local-acceptance-safe-wp8-post-20260817.1`、全部静态 mutation/enable-write/detail-cleanup gate=false 在 PID `56136` 重启并 health=200。前后 operation/attempt/item/commit 数量不变，operation marker/run waterline 未推进，三类 invariant 仍为 0；WP8 六个非基础设施聚焦测试类 22/22 通过；未触碰 archive/purge、MinIO/COS、生产、canary。

下方内容保留为进入本轮续作前的历史快照；凡与本更新冲突，以本节、WP6～WP8 最新 SPEC/复审结论及 V133 独立 SPEC 为准。

---

## 0. 新对话必须先读的结论

1. WP6、WP7、WP8 均已完成本地代码实现、聚焦代码复审及各自声明的 Required 验收；整个数据清理链路仍未取得生产准入。
2. WP2 真实 V1→当前空库迁移链仍被 V133 阻断；这是空库/灾备重建治理项。已成功越过 V133 的生产和测试数据库不会重跑 V133，V133 不再作为其前向 migration/匿名化克隆的串行前置门禁。
3. V133 第一决策门已输出 `LOCAL_SPIKE_AUTHORIZED`，本地 spike 与 MySQL Required 证据已完成。路径 A 因已越过 V133 的库出现 V132.1 `Ignored + validate failed` 被证伪；候选不得进入正式 migration location。V1 重建轨道当前建议状态为 `BLOCKED_NEEDS_NEW_GOVERNANCE`，不得提前视为已批准。
4. WP4/WP5 的 SPEC 与代码评审结论仍成立；其历史文档中的陈旧页眉或 Docker 环境描述不撤销后续结论，也不得被用来扩大生产权限。
5. 既有 V300+ 历史轨道的后续顺序固定为：核验真实 Flyway history → 匿名化生产克隆 → 生产 migration 单独审批 → WP8 readiness → canary 人工审批 → 逐域 enable；V1 空库/灾备重建治理并行保持未关闭。
6. 所有 retention execute/enable-write/detail-cleanup 默认值必须继续为 `false`；后台永远不能自动 `false → true`。
7. 本地外部 MySQL 8 和开发 Redis 可用；V133 profile 明确只连接 MySQL、禁止连接 Redis。Docker daemon 不可用不影响已通过外部服务模式形成的 WP6～WP8 Required 证据。
8. 工作区非常脏，约 211 个 status entry，包含大量用户/并行工作。禁止 reset、checkout、覆盖、暂存或顺手整理无关文件。
9. 当前唯一最高整数 Flyway migration 为 V368。任何 V133 候选开发前必须重新扫描全部 refs/worktree，且不得修改任何既有 migration。

---

## 1. 当前授权与完成状态

| WP | SPEC/评审状态 | 代码状态 | 仍未关闭的门禁 |
|---|---|---|---|
| WP2 数据库治理 | 第五次评审通过，授权 DDL/测试开发 | V354 与契约/集成测试已实现；实施评审提出的结构签名、fingerprint NULL、CHECK literal 大小写 P1 均已关闭 | V1 空库/V133 重建治理；既有 V300+ 克隆上的 MySQL 8.0.46 元数据签名、metadata lock、最终容量 |
| WP3 审计服务 | 第三次评审通过 | 人工确认代码审核通过；当前集成类含 11 个 `@Test` | 仍受生产克隆、migration 审批与生产总门禁约束 |
| WP4 动态开关 | SPEC/功能代码评审通过 | Java/Lua/权限/测试已实现；当前真实集成类含 38 个 `@Test` | 独立 Required 证据与生产 enable 仍按原门禁管理；后台禁止自动 enable |
| WP5 执行协调器 | 人工确认 SPEC 通过、代码评审通过 | coordinator、lease、budget、scheduler recovery-first、V364、真实事务测试已实现 | 独立 Required 证据与生产 execute 仍按原门禁管理；SPEC 陈旧页眉待同步 |
| WP6 官网批量清理 | SPEC 与最终聚焦代码复审通过 | 本地实现完成；`retention-website-it` 71/71，真实 MySQL 8/Redis、真实 coordinator/executor/repository 主链通过 | 生产克隆、生产 migration、canary 和生产准入 |
| WP7 poll execute | SPEC 与最终聚焦代码复审通过 | 本地实现及 Required 验收完成；最终非容器聚焦复跑 14/14 | 生产克隆、生产 migration、canary 和生产准入 |
| WP8 detail cleanup/readiness | SPEC 与最终聚焦代码复审通过 | 本地实现及 Required 验收完成；强制 clean 聚焦 38/38，完整 `retention-mysql-it` 60/60；本地真实 scheduler/成功心跳已验收，当前因容量历史不足保持 BLOCK_ONLY | 满 365 天真实明细的环境物理删除证据、生产克隆、生产 migration、生产 readiness、canary 和生产准入 |
| V133 历史链 | 第一决策门已授权本地 spike；V1 重建路径第二决策门待复审 | `retention-v133-it` 5/5；路径 A 已由 `Ignored + validate failed` 证伪；正式 migration location 无候选文件 | 可信 V353 artifact/批准或新的独立治理，仅阻断 V1 空库/灾备重建健康声明 |

生产 execute、生产迁移、生产清理、canary 晋级以及解除任何安全开关，均未获授权。

---

## 历史附录说明

从本节开始到文件末尾保留的是 2026-08-14 及更早的过程快照，用于追溯当时的设计、评审和测试证据，不再表达当前授权或完成状态。任何“WP6/WP7/WP8 未开发”“当前最高 V365/V366”或“下一步先评审 WP6/WP7”的文字均已失效；新对话必须以第 0～1 节和 2026-08-15 顶部更新为准。

## 历史附录 2：关键文档

### 2.1 WP2

文件：`D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp2-ddl-spec-2026-08-11.md`

状态：第五次评审通过版。

已冻结和实现的关键点：

- 永久 run、operation、operation-run、官网 commit marker、poll marker/audit、failure group、reconciliation 和动态开关审计模型；
- operation 跨 run，retry/reconcile 复用 operation；
- attempt/item 是可清理 detail，operation/commit 等永久保留；
- operation 级 `detail_retention_anchor_at` 和永久 `detail_purged_at`；
- detail 满 365 天后由 WP8 按 operation 行锁、完整树原子删除；
- application version 已拆为创建版本和实际提交版本；
- website/poll scope 与 fingerprint 固定 SHA-256 向量；
- V354 对 table/column/index/FK/CHECK 做精确结构签名；CHECK 保留字符串 literal 大小写语义；
- fingerprint/schema-version 半空组合已由显式 `IS NOT NULL` 约束关闭；
- 正常清理稳态容量门槛与 fail-closed 包络已经分开。

仍然阻断：

- 真实空库从 V1 执行 migration 时停在 V133；V133 删除的索引仍被外键依赖；
- 不允许直接修改已发布的 V133，避免破坏 Flyway checksum；
- 必须选择独立历史链修复，或者取得包含批准人、日期、可信 V353 实库导出物哈希和剩余风险的正式豁免；
- 抽取 retention 基线 + baseline V353 + V354 的测试通过，不能冒充真实 V1→当前迁移链通过；
- MySQL 8.0.36 和生产 8.0.46 的 metadata signature 仍需独立验证；
- 匿名化生产克隆、metadata lock、空间和容量验收仍未完成。

实现文件：

- `geo-server/src/main/resources/db/migration/V354__data_retention_execute_governance.sql`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/service/DataRetentionExecuteGovernanceMigrationContractTest.java`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/service/DataRetentionExecuteGovernanceMigrationMysqlIntegrationTest.java`

历史验证证据：SQL 契约测试曾实跑 6/6；MySQL required profile 曾确认结构/故障恢复/容量场景通过，并保留 V133 预期失败诊断。当前集成类已有 9 个 `@Test`。本交接轮未重新运行容器测试。

### 2.2 WP3

文件：`D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp3-audit-service-spec-2026-08-12.md`

状态：第三次评审通过；用户后续明确确认 WP3 代码审核通过。

核心成果：

- legacy facade 与 P0 typed writer 分层；
- run、operation、relation、attempt 和 marker 的 typed 生命周期；
- retry 的 `rolled_back → pending`、relation、prepared attempt 同一 operation-locked transaction；
- purge 后首次 audit-only reconcile run/relation 可达；
- origin unique conflict 使用两阶段事务，避免 `run → operation` 锁序反转；
- compatibility contract 与 deployment identity 分离；
- 跨部署 replay 不再错误比较 run 创建版本；
- evidence 通用 envelope/opaque canonical bytes 边界不再循环依赖 WP8；
- origin replay 验证完整 tree 和 canonical scope；
- terminal attempt 隐式时间 replay 使用数据库时间事实；
- finishRun 校验持久化 attempt 事实；
- audit-only 半套状态 fail closed；
- terminal origin replay、retention window、retry prepared 状态均已补齐。

主要实现：

- `geo-server/src/main/java/com/huanjing/geo/module/retention/audit/service/DataRetentionP0AuditService.java`
- `geo-server/src/main/java/com/huanjing/geo/module/retention/audit/service/TrustedMutationReceipt.java`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/audit/DataRetentionP0AuditServiceMysqlIntegrationTest.java`

当前测试源码统计：WP3 MySQL 集成类 11 个 `@Test`，另有 canonical/json/contract/provider 单元测试。本交接轮未重跑。

### 2.3 WP4

文件：`D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp4-dynamic-switch-spec-2026-08-12.md`

状态：SPEC 聚焦复审通过；功能代码评审通过。评审尺度明确以“后台不能误 enable、disable 必须收敛、真实清理安全”为核心，不再以极端并发穷举和完整 dashboard 为本轮硬门槛。

核心成果：

- Redis HASH v1、global/domain 同 slot 原子 Lua gate；
- 全路径 fail closed；
- requested → CAS → terminal 跨 MySQL/Redis事务边界；
- 双人 enable 审批，审批人和申请人分离；
- 后台永远不能 `false → true`；
- disable 可以自动补偿收敛；
- VERSION_ROLLBACK 不会被历史 applied=true 掩盖；
- enable CAS/replay 的 grace 使用一致 Redis TIME 事实；
- gate 多重异常优先级已冻结；
- STALE disable 补偿、malformed/unavailable reconcile 和 active/internal role 授权已经补齐；
- 指标/告警和部分极端 TIME 场景不是当前阻断项。

主要实现：

- `geo-server/src/main/java/com/huanjing/geo/module/retention/dynamicswitch/`
- `geo-server/src/main/resources/db/migration/V359__data_retention_dynamic_switch_permissions.sql`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchMysqlRedisIntegrationTest.java`

当前集成类含 38 个 `@Test`。Docker daemon 在 2026-08-14 仍不可用，因此没有在本交接轮复跑真实 MySQL/Redis profile。

### 2.4 WP5

文件：`D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp5-execute-coordinator-spec-2026-08-13.md`

真实评审状态：用户明确宣布 WP5 SPEC 通过，并在后续代码评审中最终确认 WP5 代码通过；只剩 Docker 环境验收。

注意：该文档页眉仍写“待 deferred RETRY 恢复路径聚焦复审，未授权开发”。这是后续没有同步的陈旧状态文字。新对话可以做一次纯文档修订，将页眉/结论同步为“评审通过、代码已实现，环境验收待补”，但不得借此扩大生产权限。

核心成果：

- coordinator 统一 authorization、executor registry、static gate、Redis dynamic gate、lease、budget、run 和 mutation transaction；
- executor 缺失在 gate/lease/run 创建前 fail closed；
- scheduler 对 website/poll 独立 recovery-first，website 积压不能饿死 poll；
- scheduler running run discovery、预算/deadline 重建、prepared/running marker recovery 已冻结；
- deferred retry 可复用原 operation/scope；人工普通 retry 不被后台擅自恢复；
- `TrustedMutationReceipt` 是业务 mutation 成功后的可信 transaction/candidate usage；
- callback actual 与 marker writer usage 不一致使业务、marker、attempt 和 usage 整体回滚；
- coordinator `REQUIRES_NEW` 中真实业务 DELETE 与 marker 原子提交/原子回滚已由集成测试覆盖；
- Redis lease 在成功和故障后均释放；
- V364 权限 migration 与 no-op 重迁测试已加入 required profile。

主要实现：

- `geo-server/src/main/java/com/huanjing/geo/module/retention/execute/DataRetentionExecuteCoordinator.java`
- `geo-server/src/main/java/com/huanjing/geo/module/retention/execute/DefaultRetentionExecutionSession.java`
- `geo-server/src/main/java/com/huanjing/geo/module/retention/execute/DataRetentionDomainExecutorRegistry.java`
- `geo-server/src/main/java/com/huanjing/geo/module/retention/execute/DataRetentionExecuteCommandFactory.java`
- `geo-server/src/main/java/com/huanjing/geo/module/retention/schedule/DataRetentionScheduler.java`
- `geo-server/src/main/resources/db/migration/V364__data_retention_execute_permissions.sql`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/execute/DataRetentionExecuteCoordinatorMysqlRedisIntegrationTest.java`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/execute/DataRetentionExecutePermissionMigrationMysqlIntegrationTest.java`
- `geo-server/src/test/java/com/huanjing/geo/module/retention/schedule/DataRetentionSchedulerExecuteTest.java`

最近可见的非容器 Surefire 报告来自 2026-08-13：WP5 registry/contract/lease/budget/static gate/scheduler 共 15 项均为 0 failure、0 error、0 skipped。此前聚焦复验曾报告 16/16。真实容器 profile 当前无法复跑，不得用这些非容器结果替代。

---

## 历史附录 3：WP6 当时的 SPEC 基线

文件：`D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp6-website-batch-cleanup-spec-2026-08-13.md`

标题：第二轮聚焦评审修订版。

当前状态：待聚焦复审，未授权开发。

机械状态：1164 行、58 个 Markdown fence，成对；无尾随空白；`git diff --check` 无错误，仅有工作区既存换行符提示。

### 3.1 已通过的架构方向

- 官网领域唯一 execute 入口是 WP5 coordinator；
- 当前 legacy `WebsitePublishedContentCleanupService` 只可作为资格/mutation 参考，不能作为 execute fallback；
- fresh batch 最大 20；确定性自动拆分仅允许 `20→10+10`、`10→5+5`；
- 1～4 可以是自然尾批；任意 `itemCount<=5` 为 leaf，自动路径不继续拆到单篇；
- 每个 split child 使用新的 immutable operation/item/tree/marker identity；
- parent failed、两个完整 child、first attempts 和 parent closure 在一个 governance transaction 原子提交；
- cutoff、selection upperBound、policy/fingerprint 和成员集合跨恢复不改变；
- mutation transaction 固定锁序：operation → reconciliation/failure gate → attempt → item → business rows；
- 业务 mutation 只清空正文、成功官网 task payload、成功官网 publish raw response，并把 article 置 deleted；轻量 evidence/URL/业务行不得删除；
- post-check 只检查本批已锁行；业务 mutation、active group closure、marker、operation 和 attempt 同事务；
- exact retry 复用原 operation/item/hash；人工 regroup、完整半套树自动 repair、WP8 canonical evidence 均后置，不是 WP6 首版门槛；
- WP6 验收 live large-field bytes 下降和 InnoDB 页可复用，不承诺 `.ibd`/OS 磁盘立即缩小；OPTIMIZE/重建表属于独立运维方案。

### 3.2 rollback disposition P1 的闭环

transaction outcome 与失败处置分层：

```text
COMMITTED
KNOWN_ROLLBACK
UNKNOWN
```

`KNOWN_ROLLBACK` 再分类：

- `TRANSIENT_SAME_SCOPE_RETRY`：已证明 rollback 的 deadlock、lock timeout、serialization/concurrency conflict 等；原 operation/attempt/scope 最多重试 2 次；
- `DATA_SPLIT_ELIGIBLE`：锁后资格变化或稳定、可由更小批定位的数据失败；仅此类允许 split/leaf quarantine；
- `SAFETY_STOP`：无法稳定归类、审计/结构/权限/invariant 错误；停止 domain，不 split、不建 failure group。

commit 事实不确定始终为 `UNKNOWN`，不拆分、不盲目重试。transient 重试耗尽时 operation rolled_back、candidate usage=0、停止 domain，不自动生成 failure group。

### 3.3 leaf candidate 实时预算 P1 的闭环

WP6 SPEC 新增最小 typed 扩展 `TrustedTerminalUsageReceipt`：

- 由 leaf quarantine compound writer 在 attempt/operation/group/items/active keys 成功提交后返回；
- usage 从数据库持久化 itemCount 推导，调用方不能伪造；
- session 消费前按 operation 锁序重读并重算 persisted fact digest；
- 当前 session 对 `(receiptType, attemptId, digest)` 只消费一次；
- 同步推进 WP5 candidate progress 和 website requestLimit disposed count；
- 重启后从同一 attempt metrics 聚合，不与内存 receipt 双计；
- quarantined leaf 的业务 transaction 没有 commit，因此 committed transaction count=0，但 charged candidate items=itemCount。

### 3.4 payload 晚写回 P1 的闭环

所有 business writer 只要把 `distribution_tasks.request_payload/fill_payload/response_payload` 任一列写成 SQL 非 NULL，就必须在同一 SQL/事务把 `payload_purged_at=NULL`。

WP6 需要新 migration：

1. 对历史“payload 非 NULL + marker 非 NULL”行只复位 marker，不改 payload；
2. 增加命名 CHECK：`payload_purged_at IS NULL OR 三个 payload 均为 SQL NULL`；
3. 做精确 metadata signature、repair/re-migrate/no-op、MySQL 8 和生产克隆 metadata-lock 验证；
4. 所有 review/publish/retry payload writer 增加原子复位测试。

selector 以实际字段为最终事实：

```text
request_payload IS NOT NULL
OR fill_payload IS NOT NULL
OR response_payload IS NOT NULL
```

不能把 `payload_purged_at` 当成排除条件。

### 3.5 最后一项 deleted 复热 P1 的最新修订

最后一次评审发现：无正文、只有 payload 的文章首次清理后会变成 deleted；payload 晚写回又会清空唯一 `payload_purged_at`，如果 selector 仍要求 deleted 文章拥有历史 marker，可能永久漏清。

最终冻结为业务事实资格，不依赖可清理 detail：

```text
article.status = deleted
AND actualReleasableTargetExists = true
AND successfulWebsitePublishEvidenceExists = true
AND effectivePublishTime <= runCutoff
AND noActiveDistributionOrSelfMediaHold = true
```

关键理由：operation 和 commit 永久保留，但 website batch item 按 WP2 契约在统一锚点满 365 天后可以删除。因此不能把“commit + item join”宣称为永久复热索引。

旧 commit/operation 只作为补充审计，不是 selector 的必要 join。没有实际可释放目标的 deleted 文章仍不入选，request 也不能上传任意 deleted article IDs。

required 回归场景已明确为：

```text
无正文、只有官网 task payload
→ 首次 WP6 cleanup，payload 清空、article=deleted、commit 成功
→ 晚到 review/publish writer 写回非空 payload并原子复位 marker
→ 下一 run 仅凭 deleted + 实际 payload + 成功发布/cutoff/hold 再次命中
→ 再次清空并提交新 operation/marker
```

此修订完成后尚未收到最终批准回复。新对话第一步应做聚焦复审确认，不应直接开始 Java/SQL 开发。

### 3.6 WP6 尚不存在的代码

2026-08-14 搜索确认以下实现均不存在：

- `WebsiteRetentionDomainExecutor`；
- `TrustedTerminalUsageReceipt`；
- `DATA_SPLIT_ELIGIBLE`/`TRANSIENT_SAME_SCOPE_RETRY` classifier；
- `WEBSITE_TRANSIENT_RETRY_EXHAUSTED`；
- WP6 payload consistency CHECK migration。

当前 registry 没有 website executor，execute 应继续 `DOMAIN_EXECUTOR_UNAVAILABLE`，这是安全的阶段状态。

---

## 历史附录 4：当时的工作区与迁移号风险

当前工作区约有 170 个 modified/untracked entry，包含 retention、self-media、extension、system alert、前端和本地 helper 等并行工作。

关键 Git 事实：

```text
branch: codex/sprint3-spike-article-list-trigger
HEAD:   981307b02
WP2～WP6 SPEC: 当前均为 untracked
V354～V365: 当前工作区可见，多数为 untracked
```

当前最新 migration：

```text
V365__structure_auth_work_order_resolution.sql
```

V365 不是本数据清理任务创建的 WP6 migration。WP6 开发开始前必须重新运行 migration 扫描；若并行工作继续推进，下一空闲号可能已不是 V366。

禁止：

- `git reset --hard`、`git checkout --` 或清理整个工作区；
- 修改已发布/已存在 migration 以复用号码；
- 覆盖其他任务的 V365 或任何 dirty 文件；
- 把 untracked 当作可以随意删除的临时文件；
- 在未核对 diff 的情况下批量格式化整个模块。

---

## 历史附录 5：当时的测试与环境事实

### 5.1 Docker

2026-08-14 实际执行 `docker info`，仍无法连接：

```text
failed to connect to npipe:////./pipe/dockerDesktopLinuxEngine
The system cannot find the file specified
```

因此本交接轮没有运行任何真实 MySQL/Redis integration profile。

### 5.2 现有 required profiles

在 `geo-server/pom.xml` 中：

```text
retention-mysql-it
  DataRetentionExecuteGovernanceMigrationMysqlIntegrationTest
  DataRetentionP0AuditServiceMysqlIntegrationTest

retention-switch-it
  DataRetentionDynamicSwitchMysqlRedisIntegrationTest

retention-coordinator-it
  WP3 MySQL
  WP4 MySQL/Redis
  WP5 coordinator MySQL/Redis
  V364 permission migration
  WP5 execute/session/scheduler unit tests
```

WP6 获批并实现后必须新增 `retention-website-it`，组合 WP3/WP4/WP5 门禁以及 website business/split/failure/recovery/payload-guard migration tests。required 模式在 Docker/MySQL/Redis 不可用时必须失败，不能 skipped。

### 5.3 建议命令

在 `D:\code\sanhe-geo-project\geo-project\geo-server`：

```powershell
mvn -DskipTests package
mvn -P retention-mysql-it test
mvn -P retention-switch-it test
mvn -P retention-coordinator-it test
```

WP6 实现后再运行批准 SPEC 中新增的 unit/contract/architecture 测试和 `retention-website-it`。

不要在 Docker 不可用时把 required profile 的基础设施错误降级成 skip 或普通通过。

---

## 历史附录 6：安全默认值

`geo-server/src/main/resources/application.yml` 当前以下默认值均为 false：

```text
GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED:false
GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED:false
GEO_RETENTION_ARTICLE_ARCHIVE_EXECUTE_ENABLED:false
GEO_RETENTION_ARTICLE_PURGE_EXECUTE_ENABLED:false
GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED:false
GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false
```

后续任何 WP6 单元或集成测试只能在测试进程内显式打开所需开关。不得修改 production/default 配置为 true，不得在后台创建自动 enable 路径。

“允许少量多删/少删”只适用于批准的时间/分页边界误差，不包括：

- 跨租户/跨 project；
- 删除仍在使用的数据；
- 绕过 distribution/self-media hold；
- 删除轻量审计/URL/业务行；
- marker 与业务 mutation 非原子；
- 破坏 FK、operation identity 或永久审计；
- 未知 commit outcome 下盲目重试。

---

## 历史附录 7：当时的新对话建议执行顺序

### 第一步：只做 WP6 最终聚焦复审

核对 WP6 第 5.4 节和 required 测试是否完整关闭 deleted payload 复热 P1。重点确认：

- deleted 本身不覆盖实际可释放字段；
- 仍要求成功官网发布 evidence、cutoff 和 distribution/self-media hold；
- 不依赖 365 天后会删除的 operation item；
- 无正文、仅 payload 的 cleanup→复热→再清理测试已冻结；
- 没有扩大为 caller 可上传 arbitrary deleted IDs。

只有评审方明确回复“WP6 SPEC 评审通过，允许开发”后，才能进入下一步。

### 第二步：获批后同步文档状态

- 把 WP6 页眉改为“评审通过，允许开展 WP6 Java/SQL/测试开发”；
- 同步 WP5 页眉的陈旧状态，但保留“Docker/生产准入待补”；
- 不改任何 execute/enable-write 默认值。

### 第三步：WP6 实施前只读盘点

1. 重新运行 `git status --short`；
2. 重新扫描最新 Flyway migration；
3. 阅读当前 payload writer，列出所有写 `request_payload/fill_payload/response_payload` 的 SQL；
4. 阅读 legacy `WebsitePublishedContentCleanupService` 的资格和 mutation SQL，仅复用已证明的业务谓词；
5. 阅读 WP3 `DataRetentionP0AuditService`、WP5 session/coordinator/receipt，不建立第二条 lifecycle；
6. 检查并行 dirty files 是否与 WP6 重叠，重叠时先保护用户修改。

### 第四步：建议实现顺序

1. WP3/WP5 狭口 typed 扩展：website operation lock、leaf compound writer、terminal usage receipt；
2. selector、cutoff、upperBound、deleted reheat 与 hold；
3. 所有 payload writer marker 复位及新 Flyway CHECK migration；
4. batch mutation/post-check/marker transaction；
5. rollback disposition 和最多 2 次 transient same-scope retry；
6. deterministic split compound transaction、leaf group、exact retry；
7. website recovery facts 和主链 recovery；
8. 注册唯一 website executor、封闭 legacy execute fallback；
9. unit/contract/architecture/MySQL/Redis required tests；
10. 代码评审。生产开关仍保持 false。

---

## 历史附录 8：当时的新对话开场指令

```text
请先完整阅读交接文档：
D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-handoff-2026-08-14.md

同时阅读当前 WP6 SPEC：
D:\code\sanhe-geo-project\geo-project\docs\data-retention-p0-wp6-website-batch-cleanup-spec-2026-08-13.md

当前 WP6 仍是“待聚焦复审，未授权开发”。最后一个 P1 是 deleted 文章只有 payload 时，cleanup 后 payload 晚写回可能因 marker 被复位而永久失去候选资格。SPEC 已改为：deleted + 实际可释放目标 + 成功官网发布证据 + cutoff/hold 合法即可重新入选，不依赖 365 天后可删除的 batch item；required 测试覆盖无正文、仅 payload → 清理 → 复热 → 再次清理。

请先结合 WP2 item 生命周期和当前代码做只读聚焦复审。若确认无 P0/P1，请明确给出“WP6 SPEC 评审通过，允许开发”的结论；在此之前不要写 WP6 Java/SQL、不要创建 migration、不要运行生产清理，也不要改变任何 execute/enable-write 默认值。

工作区非常脏，请保留所有无关修改。当前最新 migration 已是 V365，后续开发前必须重新扫描空闲号。Docker daemon 当前不可用，WP4/WP5 required profile 尚待环境恢复后复跑。V133 真实历史链继续阻断生产准入。
```

---

## 历史附录 9：当时交接轮的实际改动

本轮只新增本交接文档：

```text
docs/data-retention-p0-handoff-2026-08-14.md
```

前一轮只修订 WP6 SPEC，没有修改 Java、SQL migration、POM 或 application 配置，没有执行数据库迁移、生产清理或测试。
