# 数据定期清理：本地真实数据运营验收手册

> 日期：2026-08-15
>
> 范围：本地开发 MySQL 8、开发 Redis、MinIO 与本地应用；不连接生产，不执行生产 migration、canary 或 enable
>
> 安全基线：所有 execute、dynamic enable-write 和 detail-cleanup 默认值保持 `false`

## 1. 目标和边界

本轮由开发与运营共同验证“候选是否符合业务预期、保留数据是否仍可用、审计证据是否可解释”。本地已有足够数据，`geo` 库已在 2026-08-15 由 Flyway 从 V364 前向迁移到 V368。默认协议要求真实删除切换到可恢复的本地数据库副本；2026-08-15 运营明确确认开发环境数据均可删除，并授权直接在本地 `geo` 执行限额 mutation，因此本轮按授权形成一次例外证据。该例外不扩大到测试、生产、MinIO 对象或后续未批准批次。

以下行为不在本轮范围：

- 不修改或 repair 当前开发库的 Flyway history；
- 不把 V132.1 spike 文件放入正式 migration location；
- 不在本轮已批准的两个 `limit=1` 批次之外继续直接清理当前开发业务库；
- 不打开生产或共享环境开关；
- 不把 WP8 detail cleanup 暴露为公开手工 API；
- 不调用 MinIO→COS migration；当前环境没有配置 COS；
- 不承诺 MySQL DDL 可以事务回滚。

文章 archive/purge 已有独立实现和接口，但尚未接入 WP5 统一 coordinator。它们在本轮按独立业务路径验收，不作为 website/poll P0 execute 主链的一部分，也不由 execute scheduler 自动调用。

## 2. 角色分工

开发侧负责：启动本地服务、确认数据库/Redis 指向、导出只读结果、默认准备可恢复副本、执行获准的小批命令并收集审计证据。

运营侧负责：提供和确认下列业务样本。

| 样本 | 最少提供内容 | 运营确认点 |
|---|---|---|
| website published | 1～3 个历史 article ID，最好包含正文/payload 和仅 payload 形状 | URL、发布记录和轻量业务行必须保留；大字段可清理 |
| poll slice | project ID、日期范围、question tier，至少一个存在历史结果的 slice | latest projection 及读路径必需 children 保留；旧结果可清理 |
| article archive/purge | 1～3 个已发布旧版本和仍在热窗口内的对照版本 | archive 对象可读取；grace/retention 未满足者不得 purge |
| 反例 | legal hold、active failure、未终态 run/attempt 或近期复热记录 | 必须被排除或明确给出阻断原因 |

运营不需要提供数据库密码、Redis 密码或生产凭据；只需业务 ID、日期和预期。

### 2.1 2026-08-15 已确认的本地样本

| 用途 | 样本 | 预期 |
|---|---|---|
| website 正例 | article `990007577`，project `990006017` | 超过 24 小时；约 40,735 B 正文/payload 可释放；轻量行、URL、发布证据和永久审计必须保留 |
| website 反例 | article `990007920`，同 project | 2026-08-15 发布，未超过 24 小时，必须排除 |
| poll slice | project `990006017`，`2026-07-08`，tier `A`，batch `212` | 30 result 中预计 28 条可清、2 条 latest projection 保留；120 条 entity judge 随对应 result 处理 |
| article purge 反例 | article `990006110`，version `381/v1` | 已归档且约 92 天，但 task `990006063` 为 `token_issued`，必须以 `active_distribution_task` 阻断 |
| article 近期反例 | article `990007920`，version `2068/v1` | 距 90 天归档线很远，必须完整保留 |

website 正例标题“阜阳看牙如何选机构？”与项目主题明显不一致，但这不是当前 retention selector 的资格条件。它可以进入 dry-run；在任何 mutation 前仍需运营明确确认该业务内容是否允许按既定保留/释放规则处理。

## 3. Phase 0：启动前只读安全确认

### 3.1 版本匹配前置门

已确认当前 MySQL 为 8.0.45。首次启动当前代码时，Flyway 已成功向 `geo` 应用 V365～V368，并报告 schema 当前为 V368；随后应用因 retention Service 多构造器缺少明确注入构造器而启动失败。该失败发生在 migration 成功之后，不得回滚或 repair 已成功的 Flyway history。

- 重启前确认 V365、V366、V367、V368 均为 Success，无 failed/ignored/out-of-order；
- 重新执行 `validate`，不得再次执行 repair；
- 修复后的应用启动应显示 schema 已为 V368、无待执行 migration；
- Phase 0 状态查询不写 retention 数据；
- 启动进程必须显式设置 `GEO_RETENTION_APPLICATION_VERSION` 为本次 Git commit SHA 或不可变的本地验收版本；缺失时 typed audit 按 WP3 契约 fail closed，不能运行 Phase 1；
- Phase 1 dry-run 会写 `data_retention_run` 和 operationless attempt 等 typed audit 证据，但不清理 article/poll 业务数据；
- Phase 2 默认仍应从 V368 的 `geo` 创建独立、可丢弃副本；只有像本轮这样由数据所有方明确确认开发数据可删除并逐批授权时，才允许记录例外后直接使用开发库。

V365～V368 是当前 V364 历史上的正常前向 migration，不涉及重跑 V133，也不使用 V132.1 spike 候选。

### 3.2 状态接口确认

首先确认应用使用的数据库名和 Redis database/namespace，记录在证据表中。Phase 0/1 使用当前 V368 `geo` 时，必须明确接受 dry-run 产生永久 typed audit 行。启动应用后，由具有 `data.retention.switch.read` 权限的内部账号调用：

```http
GET /api/data-retention/operations/status
Authorization: Bearer <LOCAL_OPERATOR_TOKEN>
```

响应必须满足：

- `schema.historyQuerySucceeded=true`；
- `schema.currentVersion=368`、`schema.requiredVersion=368`；
- `schema.missingRequiredVersions=[]` 且 `schema.compatibleForLocalAcceptance=true`；
- `auditWriter.configured=true`，并记录 `auditWriter.applicationVersion`；不得使用 `unknown`、启动时间或 Maven 版本作为静默回退；
- `staticSafetyGates` 完整列出 scheduler、各域 execute、promotion、dynamic enable-write 和 detail-cleanup；
- `allStaticMutationGatesClosed=true`；
- `schedulerEnabled` 可独立为 `true`，因为 dry-run scheduler 本身不代表写门已开启；
- `storage.provider=MINIO`；本地业务对象读写只使用 MinIO。即使进程检测到 COS 目标参数，也不代表 COS 已启用或迁移已获授权；
- `detailCleanupReadiness` 返回当前 WP8 readiness 代码、eligible backlog、最老 anchor 与容量信息；
- readiness 非 `READY` 是可记录的真实环境结论，不得为追求通过而改数据或开开关。

再调用：

```http
GET /api/data-retention/dynamic-switches
Authorization: Bearer <LOCAL_OPERATOR_TOKEN>
```

记录 global 与各 domain 的当前值、version/change ID。Phase 0 不创建 change、不 approve、不 reconcile。

只读状态接口本身不调用 dynamic switch 写服务，也没有 cleanup 编排入口。若授权、readiness SQL 或基础设施异常，接口必须显式失败或返回 fail-closed readiness，不能把未知状态显示为 READY。

## 4. Phase 1：当前开发库真实数据 dry-run

请求中的日期和 ID 由运营样本替换；首轮 `limit` 使用 20 或更小。以下请求均不得删除业务数据。

### 4.1 全域轻量盘点

```json
POST /api/data-retention/slim/dry-run
{
  "domain": "all",
  "startDate": null,
  "endDate": null,
  "limitPerDomain": 20
}
```

### 4.2 website published

```json
POST /api/data-retention/articles/website-published/dry-run
{
  "retentionHours": 24,
  "limit": 20,
  "cursorArticleId": 990007576,
  "reason": "local-operations-acceptance"
}
```

重点确认响应包含 `990007577`，且不包含近期反例 `990007920`；同时核对 deleted + payload-only 复热候选、成功官网发布证据、legal hold/active failure 排除，以及预计释放的大字段。

### 4.3 poll slice

```json
POST /api/data-retention/poll-results/dry-run
{
  "projectId": 990006017,
  "startDate": "2026-07-08",
  "endDate": "2026-07-08",
  "questionTier": "A",
  "hotRetentionDays": 14,
  "limit": 20,
  "reason": "local-operations-acceptance"
}
```

execute 热窗口小于 14 天会被拒绝；dry-run 应命中 batch `212`，并由运营核对 28 条 superseded 与 2 条 preserved latest 的投影身份。代表性可清 result 为 `8019`，代表性保留 result 为 `8029`。

### 4.4 article archive 与 purge

```json
POST /api/data-retention/articles/archive/dry-run
{
  "dryRun": true,
  "projectId": <PROJECT_ID>,
  "publishedStartDate": null,
  "publishedEndDate": "<YYYY-MM-DD>",
  "minPublishedAgeDays": 90,
  "limit": 20,
  "reason": "local-operations-acceptance"
}
```

```json
POST /api/data-retention/articles/purge/dry-run
{
  "projectId": 990006018,
  "retentionDays": 90,
  "archiveGraceHours": 24,
  "limit": 20,
  "reason": "local-operations-acceptance"
}
```

archive/purge 要分别核对，不把“对象已归档”等同于“正文允许清理”。当前 purge dry-run 必须返回 version `381` 被 `active_distribution_task` 阻断；在 task `990006063` 收敛前不允许 purge。

当前主存储后端为 MinIO。归档和 purge dry-run 的对象 readback/checksum 会通过 MinIO 执行，不需要 COS 配置。

### 4.5 MinIO 只读对象核验（可选）

```json
POST /api/data-retention/object-storage/orphans/dry-run
{
  "prefix": "<LOCAL_TEST_PREFIX>",
  "safetyAgeHours": 24,
  "limitPerPrefix": 20
}
```

只允许使用明确的本地测试 prefix，不扫描或迁移生产 bucket。`POST /api/data-retention/object-storage/migrate` 是 MinIO→COS 专用能力；无论是否检测到 COS 目标参数，本轮均禁止调用。

### 4.6 2026-08-15 实际执行证据

应用以显式 writer identity `local-acceptance-20260815.1` 启动。Phase 0 返回 V368、所有 mutation 静态门禁关闭、三个 Redis execute switch 均为 disabled；WP8 readiness 为 `CLEANUP_NEVER_SUCCEEDED / BLOCK_ONLY`。当前 provider 为 MinIO；进程虽检测到 COS 目标参数，但 COS 未被选为 provider，本轮未调用对象迁移。

| run | domain | 结果 | 业务结论 |
|---:|---|---|---|
| `40` | website published | succeeded；20 个候选 | 正例 `990007577` 命中，正文 38,434 B、payload 2,301 B；轻量记录未修改 |
| `41` | poll results | succeeded；1 个 slice、1 个 skipped | `990006017 / 2026-07-08 / A` 被 keyword/platform/entity-judge 三项 summary reconciliation 阻断，不得进入 mutation |
| `42` | article archive | succeeded；0 个候选 | 项目 `990006017` 的近期正文未进入 90 天候选，符合反例预期 |
| `43` | article body purge | succeeded；1 个候选、1 个 blocked | `990006110 / version 381` 以 `active_distribution_task` 阻断，活动任务数为 1 |

另以 `cursorArticleId=990007919` 执行 run `38`，返回 0 个 website 候选，确认当日文章 `990007920` 未越过 24 小时门槛。run `34` 是修复前 Poll dry-run 在 run rollup 校验处失败形成的本地无 operation 测试残留：attempt 已 succeeded、run 仍 running；它不是有效验收证据，后续不得作为 readiness 或 promotion 证明，进入副本 mutation 前需按审计治理单独处置。

本轮发现并修复两项真实运行契约缺口：

- Phase 0 现在显式报告 `auditWriter`，writer identity 缺失时禁止点击 dry-run，避免请求到 WP3 才返回 500；
- Poll typed dry-run 补齐 fingerprint schema/hash scope，并让每页 attempt 的 candidate/skipped/warning 计数与 run rollup 完全一致；正常 finish 不再被自己的 catch 分支重复终结。

最终只读复核确认：`990007577` 正文仍为 3 行/38,434 B、payload 仍为 2,301 B 且 purge marker 为 0；`990007920` 正文仍为 6,195 B、payload 仍为 1,385 B；poll slice 仍有 30 条 result，代表 result `8019` 与 `8029` 均存在，永久 slice marker 为 0。dry-run 只新增审计证据，没有修改候选业务数据。

运营随后确认 website `990007577` 可按当前资格用于后续副本测试，并批准只对 `990006017 / 2026-07-08 / A` 执行既有 summary recompute。执行前 backfill dry-run 证明唯一候选；实际 backfill run `44` succeeded，重算 1 个 slice、30 个 source row、3 条 keyword summary、10 条 platform summary，并同步重算 entity-judge summary，0 failed、0 skipped。口径修正后的最终 Poll dry-run run `47` succeeded：1 个候选、1 个 eligible、0 blocked、0 warning；结果总数 30、latest 保留 2、非 latest 28、entity judge 120，阻断原因已清空。该步骤只更新获批 slice 的业务 summary 与 backfill/dry-run 审计，不执行 retention mutation；poll result、shard、child detail 和永久 marker 均未删除。

## 5. 运营证据模板

每个请求保存原始 JSON 响应，并填写：

| 时间（UTC） | 环境/数据库 | endpoint | 请求范围 | candidate/eligible | blocked/skipped 及原因 | 样本 ID | 运营判断 |
|---|---|---|---|---:|---|---|---|
|  |  |  |  |  |  |  |  |

对每个正样本和反例至少记录：清理前业务可见内容、预计被清理字段、必须保留字段/URL、资格原因、运营是否认可。任何“无法解释的候选”都先停止，不进入 mutation。

## 6. Phase 2：小批 mutation

只有 Phase 1 样本全部得到运营确认后才能开始。默认使用可恢复本地副本；若数据所有方明确授权直接删除开发库数据，必须把授权范围、不可恢复性、批次上限和收口证据写入本手册。执行前满足：

1. 默认从当前开发 MySQL 创建独立、可丢弃的本地副本，记录源库与副本库名，并只在副本完成 V365～V368；直接开发库例外必须有数据所有方明确授权；
2. 启动日志明确显示应用连接的目标库；默认副本使用隔离 Redis database/key prefix，直接开发库例外只允许短时、可核验的开关窗口；
3. 默认保存副本恢复点并验证可以重新创建；直接开发库例外需接受已清理大字段/明细只能从既有备份恢复；
4. 初始状态再次调用 `/operations/status`，确认 `allStaticMutationGatesClosed=true`；
5. 一次只批准一个 domain，先 `limit=1`，复核后再到 5，最后才允许协议上限；
6. 每批均保存 run、operation、attempt、item、commit/marker、candidate/affected budget 与 lease 释放证据。

website/poll mutation 还需要 static execute gate 与 Redis dynamic gate 同时允许；两者任一关闭或不可用都应 fail closed。具体开关变更值和 change ID 在执行当次由开发提交给运营确认，不预先写死在本手册中。

WP8 detail cleanup 没有公开手工 endpoint。它只能在隔离副本中通过既有 Required integration harness，或以明确的本地 scheduler 配置运行；完成后重新读取 `/operations/status`，检查 readiness、水位、purge count 守恒和永久证据。

若副本仍指向与 `geo` 共用的 MinIO bucket，则 Phase 2 只允许对象 readback，不执行新增 archive、对象迁移或对象删除。需要测试归档写入时，必须先配置独立 bucket/prefix；website/poll 数据库清理不需要 COS。

### 6.1 2026-08-15 直接开发库授权执行证据

运营明确回复“授权开始处理”并确认本地开发环境数据均可删除。本轮未创建副本，只对 MySQL 8.0.45 的 `geo` 执行两个 `limit=1` 批次；应用 writer identity 为 `local-acceptance-execute-20260815.1`，scheduler 自动调度关闭，mutation transaction/candidate budget 均固定为 1。三个 Redis execute switch 仅在执行窗口内写为 enabled，完成后全部写回 `enabled=0, version=2`。

| 阶段 | run | 结果与永久证据 |
|---|---:|---|
| Website preflight | `48` | selector 的首个候选为 article `990006426`，不是游标后运营样本 `990007577`；正文 1 行/10,766 B，payload 1 行/1,160 B |
| Website execute | `49` | succeeded，candidate/affected 为 1/1；operation `201ddb31-8805-4145-b097-19e3980cf1d1` committed，attempt `12` succeeded，1 个物理 item、1 个 commit marker |
| Poll preflight | `50` | 固定 slice `990006017 / 2026-07-08 / A` 为 1 eligible、0 blocked；30 result、28 non-latest、2 latest、10 shard、30 shard item、120 entity judge |
| Poll execute | `51` | succeeded，candidate/affected 为 1/1；operation `2216f0e5-ecc1-45a3-8a59-d9502a436c7f` committed，attempt `14` succeeded；`poll_slice` 按契约为 0 物理 item，以永久 slice marker 作为提交证明 |
| 安全收口 dry-run | `52` / `53` | Website `990007577` 仍命中；已清理 Poll slice 返回 0 candidate，证明 marker 阻止重复选择 |

Website 实际处理结果：article `990006426` 收敛为 `deleted`；正文行、distribution task、发布记录和 URL 轻量骨架均保留，正文内容与 task payload 归零并写入 purge marker。该大字段内容除非已有外部备份，否则不可恢复。之所以没有处理预先展示的 `990007577`，是因为正式 execute API 不接受 dry-run cursor，真实 selector 按 article ID 升序选择了 `990006426`；运营对开发库全量可删除的授权覆盖该候选，本轮仍停止在一个 Website transaction。

Poll 实际处理结果：result 从 30 条降为 2 条，保留 ledger 记录 `8029、8044`；代表旧结果 `8019` 已删除。28 条 superseded result、112 条对应 entity judge、10 个 shard 和 30 个 shard item 已删除；剩余 2 条 result、8 条 entity judge、`poll_batches` 主记录、汇总数据、永久 marker、preserved ledger 与 WP3 审计保留。该样本没有 invocation attempt/provider call/search source/citation，因此对应删除计数均为 0。marker metrics 与 attempt metrics 的逐表删除计数一致，post-check schema version 为 1。

执行未调用 article archive/purge、WP8 detail cleanup、MinIO 对象删除或 MinIO→COS migration。执行后后端以 `local-acceptance-safe-20260815.1` 重启，页面确认 V368、全部静态 mutation/enable-write/cleanup 门禁关闭，三个 Redis 动态开关均为 `VALID / disabled`；临时执行按钮和前端 execute API 已移除。

### 6.2 2026-08-17 `limit=5` 扩容证据

运营在既有“本地开发数据均可删除”授权下要求继续处理。本轮把每域 candidate 与 mutation transaction 上限从 1 提升到 5，scheduler 继续关闭；应用 writer identity 为 `local-acceptance-execute-20260817.1`。三个 Redis execute switch 从安全状态 `enabled=0, version=2` 短时写为 `enabled=1, version=3`，完成后全部收敛到 `enabled=0, version=4`。

Website 初始 dry-run run `54` 精确选择 `990006428、990006429、990006430、990006431、990006435`，估算正文约 50.9 KiB、payload 约 5.75 KiB。正式按钮再次执行 preflight run `61` 后，execute run `62` 以 5 candidate/5 affected、0 skipped/warning 成功提交：

- 五篇 article 均转为 `deleted`；五行正文和五行 payload 归零，body/payload marker 均完整；
- 每篇 article、version、distribution task、publish record 轻量行保留，每篇 task URL 与 publish URL 均仍存在；
- operation `b66dc2a7-3a95-4695-8f2d-f3087525d831` 为 committed，attempt `28` succeeded，5 个物理 item、一个 item_count=5 的 commit marker；
- 收口 dry-run run `63` 的前五个候选已变为 `990006437、990006438、990006439、990006440、990006442`，证明本批五个已处理 ID 不会重复入选。

Poll 范围为 project `990006017`、`2026-07-01`～`2026-07-31`、tier `A`。run `55/56/57` 均只写 dry-run 审计，前五个 slice `07-01、07-02、07-03、07-04、07-06` 最初全部被 keyword/platform/entity-judge summary reconciliation 阻断。受控入口只接受这三项精确阻断；backfill run `58` 重算 5/5、0 failed/skipped，第二次 preflight run `59` 证明 5 eligible/0 blocked 后，execute run `60` 才执行 mutation：

- 五个 slice 各形成一个 committed `poll_slice` operation 和 succeeded attempt，全部为 0 物理 item，并各写一个永久 slice marker；
- 共删除 143 条 superseded poll result、560 条 entity judge、50 个 shard、150 个 shard item；
- 这五个历史 slice 的 result 均已被其他日期的更新 projection 取代，所以 preserved result 数为 0；批次、summary、marker 与 WP3 审计保留；
- invocation attempt、provider call、search source、citation 在这些 slice 中均为 0，post-check 与 marker/attempt metrics 逐表一致。

扩容后后端以 `local-acceptance-safe-20260817.1` 重启。页面确认 V368、全部静态 mutation/enable-write/detail-cleanup gate=false、Redis 三项 `VALID / disabled`；一次性 execute/backfill API 暴露和按钮已从前端移除。本轮仍未调用 article archive/purge、WP8 detail cleanup、MinIO 对象删除或 MinIO→COS migration。上述数据库大字段和明细只有既有备份可恢复。

### 6.3 2026-08-17 协议上限 `limit=20` 证据

运营明确批准继续验证协议上限。本轮应用 writer identity 为 `local-acceptance-limit20-20260817.1`，`maxMutationTransactions=20`、`maxCandidateItems=20`，scheduler 继续关闭。三个 Redis execute switch 从 `enabled=0, version=4` 短时写为 `enabled=1, version=5`，完成后全部恢复为 `enabled=0, version=6`。

Poll 仍限定 project `990006017`、`2026-07-01`～`2026-07-31`、tier `A`。run `64/65/66` 均为 dry-run，确认剩余 17 个 candidate 中 2 eligible、15 blocked；全部 blocker 均属于 keyword/platform/entity-judge summary reconciliation，没有业务 hold、active failure、未终态或永久证据异常。backfill run `67` 为 succeeded：17 candidate、16 recomputed、1 skipped、0 failed；skip 是已有永久 marker 的 `07-08`，而 retention selector 已排除该 slice。该重算已覆盖全部 15 个 blocked slice；run `68` 随后证明 17 eligible、0 blocked，execute run `69` 才提交 17/17：

- 17 个 `poll_slice` operation 全部 committed，17 个 attempt 全部 succeeded，预算记录 17 个 transaction/17 个 candidate，0 物理 item；
- 日期为 `07-07、07-09、07-10、07-11、07-13、07-15、07-16、07-18、07-20、07-21、07-23、07-24、07-27、07-28、07-29、07-30、07-31`；
- 共删除 283 条 superseded result、1,100 条 entity judge、116 个 shard、308 个 shard item；
- 保留 4 条 latest result ledger，marker/attempt 的逐表删除计数与 post-check 一致。

Website preflight run `70` 精确选择 20 篇文章，execute run `71` 以 20 candidate/20 affected、0 skipped/warning 成功提交。文章 ID 为：

```text
990006437, 990006438, 990006439, 990006440, 990006442,
990006447, 990006448, 990006449, 990006450, 990006452,
990006456, 990006458, 990006459, 990006461, 990006469,
990006470, 990006759, 990006760, 990006761, 990006762
```

二十篇 article 均转为 `deleted`；20 行正文和 20 行 payload 归零并写 marker。20 个 article/version/task/publish 轻量结构、20 个 task URL 与 20 个 publish URL 均保留。operation `c36ab82f-1f84-415e-a592-94b0b3d173b0` 为 committed，attempt `52` succeeded，20 个物理 item、一个 item_count=20 的 commit marker，预算为 20 candidate、一个 committed transaction。

收口后后端以 `local-acceptance-safe-limit20-20260817.1` 和全部静态 gate=false 在 PID `24920` 启动，Flyway validate 369 migrations，schema 保持 V368。一次性 execute/backfill API 与按钮已删除。最终管理页登录会话在刷新时过期，因此最终门禁证据由 Redis 直读、显式启动配置和 MySQL READ ONLY 查询完成；未读取浏览器凭据，也未绕过登录。archive/purge、WP8、MinIO/COS、生产和 canary 仍未触碰。上述被清除内容仅能从既有备份恢复。

### 6.4 2026-08-17 WP8 本地 scheduler/heartbeat 验收

在运营明确授权本地开发数据可删除后，本轮只对 WP8 detail-cleanup 做真实 scheduler 验收，不修改审计时间戳、不制造满 365 天的假候选。执行前使用 MySQL `READ ONLY` 事务确认：MySQL 8.0.45、schema V368，`data_retention_detail_cleanup_run` 为空；26 个 operation 均未清 detail，数据库中的最老 `detail_retention_anchor_at` 为 `2026-08-15 10:05:43.844`，operationless run 也没有越过 365 天 cutoff。selector 资格为 0 operation / 0 attempt / 0 item；operation、attempt、item、commit 基线分别为 26、52、26、3，三类永久 invariant 均为 0。

专用进程以 writer identity `local-acceptance-wp8-20260817.1` 启动，只临时设置 `detail-cleanup.enabled=true`；所有 website/poll/article execute、scheduler execute、promotion、dynamic enable-write 和 report-freeze 均保持关闭。该进程使用独立 Redis lease key、`maxOperationsPerRun=1`、`maxOperationlessRunsPerRun=1`、120 秒 run budget，并在 `2026-08-17 09:58:43`（Asia/Shanghai）由真实 Spring scheduler 触发一次。

cleanup run `f378cddb-11cf-4386-9afa-d0d6c7c5977b` 的永久记录为 `scheduler / succeeded`，application version 与专用进程一致；started/finished UTC 均为 `2026-08-17 01:58:43`。结果为 0 candidate、0 purged operation、0 skipped、0 warning、0 operationless run、0 purged attempt、0 purged item，eligible attempt/item backlog 均为 0；记录的 retention allowlist 容量为 1,605,632 B，五年投影为 NULL。该 NULL 是首个容量样本不足 7 天的预期形状，不得解释为 READY。

执行后只读复核确认 operation、attempt、item、commit 数量仍为 26、52、26、3，operation detail marker 和 run waterline 均未推进，三类 invariant 继续为 0。readiness 已不再是 `CLEANUP_NEVER_SUCCEEDED`；按同一次真实 sweep 调用的 readiness 契约和上述数据库证据，当前主结论为 `CAPACITY_HISTORY_INSUFFICIENT / BLOCK_ONLY`。它会继续阻止 enable/canary，直到形成满足时间跨度的真实容量历史；本次成功心跳不能替代 365 天到期明细的物理删除验收，后者仍由已通过的 Required MySQL 测试证明。

专用进程随即停止，独立 Redis lease key 已不存在。常驻后端以 `local-acceptance-safe-wp8-post-20260817.1` 在 PID `56136` 重启并 health=200，WP8 和全部静态写门禁恢复为 false；global、website、poll 三个 Redis execute switch 均保持 `enabled=0, version=6`，没有自动开关变更。本轮另复跑 WP8 architecture、capacity、configuration、readiness、safety converger 和 service 六个非基础设施测试类，共 22/22 通过，0 failure、0 error、0 skipped。本轮未调用 archive/purge、MinIO/COS、生产、canary，也没有删除任何 WP8 审计明细。

## 7. 每批 mutation 后的业务验收

- website：大字段和 payload 按范围归零，轻量 article、URL、发布证据保留；复热 writer 会原子复位 purge marker；
- poll：被清理 result 及全部锁定 child ID 归零，latest preserved 读路径正常，slice marker/ledger 与 audit 一致；
- article：归档对象可读取且 checksum/metadata 一致，grace 满足后才允许清正文；
- 通用：无未解释 `unknown/manual_review`，known rollback 可 exact retry，lease 释放，预算计数和 run rollup 一致；
- WP8：operation tree 或 operationless run 按协议清理，关联 run 水位最终推进，readiness 不把查询失败解释为 READY。

## 8. 立即停止条件

出现以下任一情况立即停止后续批次并保留现场：

- 应用连接的数据库名、Redis namespace 或对象存储 prefix 无法确认；
- 任一未批准的 execute/enable-write/detail-cleanup gate 为 true；
- 候选包含运营明确要求保留的数据；
- marker、实际删除、post-check 或永久审计证据不一致；
- 出现 unknown outcome、manual review、CHECK/外键异常或无法解释的预算差异；
- URL/轻量业务行/latest projection 等保留数据不可读取；
- readiness 查询失败却被显示为 READY。

停止后优先丢弃并重建本地副本；涉及 DDL 时按备份恢复或已评审的 forward repair 处置，不执行未经评审的 Flyway repair，不触碰生产。

## 9. 进入联测前的剩余条件

运营样本和保留/释放判断、Phase 0/1、获准的 Phase 2 website/poll 协议上限，以及一次 WP8 本地真实 scheduler/成功心跳证据已经齐全。由于本地审计树最早形成于 2026-08，尚无满 365 天候选，本轮没有形成 WP8 本地真实物理删除证据；readiness 正确停在 `CAPACITY_HISTORY_INSUFFICIENT / BLOCK_ONLY`。后续若继续扩大本地批量，重新走运营批准并优先创建 V368 可丢弃副本；本次直接开发库例外不构成持续授权。生产克隆、生产 migration、生产 WP8 readiness、canary 和逐域 enable 仍是独立门禁。
