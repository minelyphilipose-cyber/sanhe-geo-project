# 数据定期清理：发布候选整理与本地验收记录

> 日期：2026-08-17
>
> 状态：本地候选已分批提交；migration manifest 与 Article MySQL/COS Required 入口已实现；唯一制品和真实 COS 实跑待完成
>
> 安全基线：所有 execute、dynamic enable-write、detail-cleanup 默认值保持 `false`

## 1. 整理目标

本记录用于把 WP2～WP8 从当前大型 dirty worktree 中整理成可复核的发布候选边界，并承接以下本地验收：

1. 从同一候选源码一次性复跑 WP2～WP8 Required profiles；
2. 在隔离 MySQL/MinIO 范围完成 Article archive/purge 正向 mutation；
3. 固定 migration、配置、测试和业务验收证据，为后续匿名化生产克隆做输入。

本文不授权生产 migration、生产 cleanup、canary、COS migration 或任何生产开关变更。

## 2. 当前不可变基线

```text
branch = codex/sprint3-spike-article-list-trigger
baseline head = 981307b02cec4922f795348df0a59d1b0a5075a0
status entries before this validation = 223
untracked paths matched by retention keyword = 44
working-tree Flyway version count = 368
Git refs scanned before this validation = 83
V354..V368 competing migration name/version = 0
```

当前 worktree 同时包含 retention、自媒体认证治理、扩展运行时、系统告警、前端页面和生成缓存等修改。禁止用整树 `git add .`、reset、checkout 或批量覆盖整理发布候选。

候选已按职责形成以下可审查提交；不把本地生成缓存或既有 deploy tar 纳入候选：

```text
58d252472ce6bcb423606863ecfc995a6ad76cf0  feat(self-media): strengthen runtime auth governance
4588867cdae6f4d086434a2259c57937dc8a8471  feat(retention): implement governed cleanup lifecycle
d3b814574304c1f45b37001a06442b1bb882c1c5  feat(admin): add auth and retention operations consoles
ff50ad665cde045d10592b51b26cd90c9a088e2b  docs(retention): record specs and release gates
803f518f5126288cca43075a6768fff7556bf1f7  test(retention): add COS release gate
```

## 3. 发布候选边界

### 3.1 retention 主体

以下目录或文件整体属于候选：

```text
geo-server/src/main/java/com/huanjing/geo/module/retention/**
geo-server/src/test/java/com/huanjing/geo/module/retention/**
geo-server/src/test/resources/retention/**
geo-server/src/main/resources/redis/data-retention-*.lua
docs/data-retention-*.md
geo-web/src/api/dataRetentionOperations.ts
geo-web/src/views/admin/system/DataRetentionAcceptance.vue
```

`geo-server/pom.xml` 中新增的 retention profiles，以及 `application.yml` 中 retention 配置块也属于候选，但它们是共享文件，最终暂存前必须做逐 hunk 复核。

### 3.2 跨模块业务 writer/guard

Website payload marker 复位、Poll slice fence、latest projection、post-check 与 summary reconciliation 会修改 retention 包之外的业务 writer。候选至少包含以下业务集成面：

```text
content/service/ContentDistributionService.java
content/service/DistributionReviewStatusPollService.java
content/schedule/OfficialApiSelfMediaPublishScheduleAdapter.java

dispatch/dto/PollRetentionDryRunRequest.java
dispatch/entity/PollAuditPurgeRun.java
dispatch/mapper/PollBatchShardItemMapper.java
dispatch/mapper/PollResultMapper.java
dispatch/service/DispatchPollAggregationService.java
dispatch/service/DispatchPollShardPersistenceService.java
dispatch/service/DispatchQuestionPollPlanningService.java
dispatch/service/PollResultPersistenceTransactionService.java
dispatch/websearch/AttemptLifecycleService.java
dispatch/websearch/PollAttemptCreationService.java
dispatch/websearch/PollResultProjectionService.java
dispatch/websearch/WebSearchAttemptResultWriter.java
dispatch/websearch/purge/PollAuditPurgeAuditWriter.java
dispatch/websearch/transport/ProviderCallAuditWriter.java
```

这些文件对应的 marker/fence/projection 测试也必须同行进入候选。最终清单以编译依赖、架构测试和 Required profiles 为准，不能只按路径关键字选择。

### 3.3 共享前端文件

以下文件同时包含其他管理功能，不能整文件盲目归入 retention commit：

```text
geo-web/src/layouts/AdminLayout.vue
geo-web/src/router/admin.ts
```

只应保留 `DataRetentionAcceptance` 菜单和路由的精确 hunk；其他用户修改继续留在原工作区。

### 3.4 顺序共发 migration

Retention 直接拥有：

```text
V354 data retention execute governance
V359 data retention dynamic switch permissions
V364 data retention execute permissions
V366 distribution payload purge marker guard
V367 poll retention execute and preserved cleanup
V368 detail cleanup readiness
```

同一 worktree 中还存在 V355～V358、V360～V363、V365 的其他功能 migration。运营已确认生产当前停在 V353，所有尚未发布内容作为同一批发布，因此最终决定为：V354～V368 全部 15 个 migration 按顺序共发，并把对应 Java、前端、测试和运营影响纳入同一集成 release。

该决定关闭了“中间 migration 先发或共发”的范围问题，同时扩大了候选回归范围：retention Required profiles 仍是强制门禁，但不能替代 V355～V358、V360～V363、V365 对应功能的完整后端/前端回归。

禁止只携带 V354/V359/V364/V366～V368、静默跳过中间 migration 后宣称候选等同于开发验收环境。

### 3.5 明确排除

以下内容不进入 retention 发布候选：

```text
geo-web/node_modules/**
geo-server/target/**
geo-server/logs/**
本地运行日志、临时 argfile、数据库凭据
geo-project-deploy.tar.gz
与 retention 无引用关系的浏览器扩展、local helper 和其他业务修改
```

排除不表示删除；它们继续原样保留在用户工作区。

## 4. Migration SHA-256 锚点

```text
V354__data_retention_execute_governance.sql                      b7757e689128bd5f0c3a39e7c6795cfa698878387a20d632305f6877ecd409d3
V355__retire_douyin_oauth_and_align_login_validity_reference.sql 788eebc143a0938aaaec06e9f6281b75bfbab13db62b14fb04ab6a2210a31809
V356__track_latest_self_media_login_check.sql                    c86c28b3ede1e0a017a26adbc75c5f4581067fa1c01faa88386b71e99c9e6fb2
V357__clear_obsolete_platform_account_id_warnings.sql            26b72e2aa90c7a60b93d961aca6e042e54d32de7de0cd0b0a0451d0a6600c8cc
V358__scope_extension_runtime_status_by_platform.sql             67772a95992b22d17e39a81448d3138f11d0384b94d5700d6757a92dbf3434d8
V359__data_retention_dynamic_switch_permissions.sql              f21959513ccf4682664c545a07662c95dce0f8b10e939f194e46499338816abb
V360__enforce_single_active_browser_environment_per_brand.sql    509924552470ded4b31e373f025d2536696030d28478652eac6542d878c05a8c
V361__add_self_media_auth_health_recalculate_permission.sql      fcf2e7704f0ef65dbc8d1ab660cfa31462e249eaab23d378e4c145bbd30f9bb5
V362__system_alert_snooze.sql                                    9a873601b9084871fc535653e7d5dba4a47691e284ac332d0adcc2dd2160a9f8
V363__self_media_login_observations_and_operations.sql           d0af32538aed9ac7a6f1b177c5f53e6127795ab704155e2a2c42cb4cbc691cc5
V364__data_retention_execute_permissions.sql                     a9860d9b0cfb429890493338ac050f49d7030b3ae1c60948599861602b764569
V365__structure_auth_work_order_resolution.sql                   3120cecfdffa14ad374170545165a5953239400be204c4a7e37d3768b21da211
V366__guard_distribution_payload_purge_marker.sql                1bf94b769ed6b58a0f01fd0766bbefd23f7af2c9789d8ec8311effa2285a0a23
V367__poll_retention_execute_and_preserved_cleanup.sql           d08ce457d4be281d6267209c08279a8ea5174d4072e7ffed9a498a672b8b0d55
V368__data_retention_detail_cleanup_readiness.sql                d119d26566d8575b02018cd367c45c060d7b89176f11debb86353c19b540cbdf
```

任何锚点变化都要求解释，不能静默更新。

## 5. 回归矩阵

| Profile/检查 | 本轮实际结果 | 结论 |
|---|---:|---|
| `mvn -DskipTests package` | build success | 通过 |
| `retention-v133-it` | 5/5，0 skipped | 通过 |
| `retention-switch-it` | 37/37，0 skipped | 通过 |
| `retention-coordinator-it` | 71/71，0 skipped | 通过 |
| `retention-website-it` | 72/72，0 skipped | 通过 |
| `retention-poll-it` | 86/86，0 skipped | 通过 |
| `retention-mysql-it` | 60/60，0 skipped | 通过 |
| `retention-article-minio-it` | 1/1，0 skipped | 通过 |
| `npm run build` | Vite production build success | 通过 |
| `git diff --check` | 0 error；只有既存换行符提示 | 通过 |

外部 MySQL/Redis/MinIO 均通过显式 test environment 变量提供；Required profile 不允许因 Docker 不可用而 skipped。各 profile 存在测试类重叠，因此上表数字是逐 profile 执行证据，不能相加后宣称为唯一测试数。

本轮曾有一次 `retention-switch-it` 调用只注入 Redis、漏注入 MySQL，Required gate 按预期在测试前失败；补齐同一套外部 MySQL 环境后 37/37 通过。该事件证明门禁 fail-closed，不计为代码失败。

## 6. Article archive/purge 正向验收协议

新增 Required profile `retention-article-minio-it`，协议与实测结果如下：

1. 使用随机 `geo_retention_article_it_*` 数据库，不修改当前 `geo` 的历史时间戳或任务状态；
2. 使用随机 `geo-retention-article-minio-it-*` MinIO bucket，不连接 COS，也不写现有 `geo-files`；
3. fixture 真实满足：正文非空、发布时间超过 90 天、有 delivered publish record、无活动 distribution/self-media hold；
4. 临时只在测试对象内打开 article archive/purge 静态 gate，执行一个候选；
5. 归档实际写入 MinIO，随后 readback 并做 SHA-256 一致性校验；
6. 仅在隔离 fixture 中把归档时间推进到 25 小时前，满足 execute 固定的 24 小时 grace；
7. purge 后数据库正文为 `NULL` 且 marker 非空，对象、object key、checksum 与轻量 article/version 行仍保留；
8. archive、purge 两条 `data_retention_run` 均为 `succeeded`，candidate/affected 均为 1；
9. 测试结束前校验精确安全前缀，再删除随机 bucket 和随机数据库。

该验收不调用 Website/Poll execute，不写 Redis dynamic true，不执行 MinIO→COS migration。

生产对象存储实际为 COS。上述 MinIO 结果只证明 Article 业务资格、数据库 mutation、对象抽象接口和 readback/checksum 协议在本地开发 provider 上成立；它没有覆盖 `CosObjectStorageAdapter` 的真实腾讯 COS SDK、region、bucket、endpoint、IAM 权限、stat 和错误码。生产 Article 准入必须另有专用 COS bucket/prefix 的 Required 证据，不能引用本节替代。

Gate 0 已新增 `retention-article-cos-it`。该 profile 使用真实 `CosObjectStorageAdapter`、隔离 MySQL 测试库和调用方提供的专用 COS bucket/prefix，覆盖 put、readback SHA-256、stat/ETag、仅清空数据库正文、对象保留、审计记录、精确测试前缀清理及删除后的 404 mapping。测试只接受 `geo-retention-it/` 下的相对前缀，并在其下生成 `run-<32 hex>/`；清理逻辑拒绝 bucket 根、业务前缀、绝对路径、反斜杠和 `..`。

当前 Codex 进程没有注入 COS 或外部 MySQL Required 环境变量，因此本轮只能确认该测试编译成功并在非 Required 模式明确 skipped；这不是 COS 通过证据。必须由运维提供专用测试 prefix 和临时凭据后，以 `scripts/data-retention-required-check.ps1 -IncludeCos` 得到 0 skipped 的结果，Gate 0 才能关闭。

## 7. 可复现命令与环境契约

Required profiles 只读取下列环境变量，不在文档、日志或源码写入凭据：

```text
RETENTION_MYSQL_IT_SERVER_URL
RETENTION_MYSQL_IT_USER
RETENTION_MYSQL_IT_PASSWORD
RETENTION_REDIS_IT_HOST
RETENTION_REDIS_IT_PORT
RETENTION_REDIS_IT_USERNAME   # 可选
RETENTION_REDIS_IT_PASSWORD
RETENTION_REDIS_IT_DATABASE
RETENTION_MINIO_IT_ENDPOINT
RETENTION_MINIO_IT_ACCESS_KEY
RETENTION_MINIO_IT_SECRET_KEY
RETENTION_MINIO_IT_REGION     # 可选，默认 us-east-1
RETENTION_COS_IT_REGION
RETENTION_COS_IT_BUCKET
RETENTION_COS_IT_ENDPOINT     # 可选
RETENTION_COS_IT_SECRET_ID
RETENTION_COS_IT_SECRET_KEY
RETENTION_COS_IT_PREFIX       # 必须位于 geo-retention-it/ 下
```

本地执行顺序固定为：package → V133 → switch → coordinator → website → poll → MySQL/WP8 → Article/MinIO → frontend build → mechanical checks。统一入口为 `scripts/data-retention-required-check.ps1`；不带 `-IncludeCos` 时脚本会明确警告 COS 未覆盖，不能据此关闭生产 Gate 0。完整 Gate 0 使用 `scripts/data-retention-required-check.ps1 -IncludeCos`，在 Article/MinIO 后追加 Article/COS。新增 COS profile 只清理随机 run prefix，不删除 bucket；其他 Required profile 延续既有随机数据库、随机 MinIO bucket 与 Redis DB 15 隔离协议。

`scripts/generate-migration-manifest.ps1` 会固定扫描 368 个正式 versioned migration，并输出 path、bytes、SHA-256 与 Flyway 9.22.3 兼容 checksum。当前 manifest SHA-256 为 `35e85aa08f813d8f17064c568c1010d417a2cb8d1c47bb60c5d966cb6b7b6e14`；其中 V133=`971462112`、V340=`258457011`、V353=`-1211774692`，与生产 history 截图逐项一致。

提交前重新扫描当前 67 个 Git refs 和 6 个 worktree，V354～V368 未出现同版本不同 migration 文件名。`application.yml` 中 retention scheduler、各 domain execute、dynamic enable-write、detail cleanup、report freeze 和 storage migration execute 的默认值均再次确认保持 `false`。

## 8. 当前结论与下一门禁

以下本地工作已经闭环：

1. 大型 dirty worktree 中的 retention 候选边界、跨模块 writer/guard 和 migration 共发依赖已固定；
2. 同一源码完成 WP2～WP8 Required 回归，全部 0 failure、0 error、0 skipped；
3. Article archive/purge 已取得真实 MySQL 8 + MinIO mutation 证据。
4. 候选已按职责分批提交，node_modules、target、日志、deploy tar 和凭据未进入提交；
5. 368 行 migration manifest 与生产截图中的三个已知 Flyway checksum 已交叉验证；
6. 真实 COS Required profile 和 fail-closed 统一入口已实现并完成编译验证。

这仍不是生产发布批准，且 Gate 0 尚未关闭。剩余本地门禁是：从最终 commit 的干净 worktree 生成唯一后端/前端制品及 SHA-256；使用显式外部 MySQL/Redis/MinIO/COS 环境运行 `-IncludeCos` 全矩阵并取得 0 skipped 报告。之后才能进入生产只读 history 导出、匿名生产克隆、生产 migration 单独审批、WP8 readiness、canary 与逐域 enable。所有 execute、detail-cleanup 与 enable-write 默认值继续保持 `false`。
