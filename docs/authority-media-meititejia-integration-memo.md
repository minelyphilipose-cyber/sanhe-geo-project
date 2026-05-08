# 特价网权重媒体对接备忘录

更新时间：2026-05-08

## 当前范围

一期仅开放 `NEWS_MEDIA`（特价网新闻媒体），底层资源表通过 `resource_type` 预留 `WEMEDIA`、`VIDEO`、`OVERSEAS`。

## 开工前未决项

### `no` 字段长度

当前本地设计：

- `authority_media_order.external_no` 为 `VARCHAR(64)`
- 生成规则为 `AM-{authority_media_order.id}`
- 常规自增 BIGINT 下长度约 22 字符

待确认项：

- 特价网服务端 `no` 字段最大长度
- 是否仅接受字母、数字、连字符
- `query_media_order` 的 `nostr` 批量逗号分隔参数是否接受 form 编码后的 `%2C`。PHP `$_POST` 通常会自动 urldecode，但金丝雀阶段必须实测批量回查。

当前处理：

- 在客服未确认前，金丝雀阶段必须用 `AM-{id}` 实测至少一单。
- 若服务端限制小于 64，需在下单前增加长度校验，并调整 `external_no` 生成策略。

### `content` 字段是否带正文

文档原文要求 content 按以下形式填写：

```html
稿件链接 : <a href="代理商平台的预览地址">代理商平台的预览地址</a>
```

当前配置：

- `geo.meititejia.content-mode=LINK_ONLY`
- 预留 `BODY_WITH_LINK`

待确认项：

- content 是否只能传预览链接
- 是否需要正文 + 预览链接
- 媒体审核方实际查看字段是 content 还是预览页

当前处理：

- 客服未确认前，金丝雀阶段必须分别验证 `LINK_ONLY` 的可发稿性。
- 若审核要求正文，则切换为 `BODY_WITH_LINK`，并确保正文同样在签名前统一 urlencode。

## 订单重试语义

`authority_media_order` 和 `distribution_tasks` 的关系：

- 同一 `distribution_task` 仅对应一条 `authority_media_order`
- 同一逻辑提交的重试必须复用同一 `authority_media_order.external_no`
- 远端拒稿或删除后，用户改稿重发必须新建 `distribution_task` 和新 order，不复用原 external_no

## NEWS_MEDIA 下单提交实现约束

本期范围：

- `TargetContext.AuthorityMediaTarget` 只承载 NEWS_MEDIA 下单所需字段：`resourceId`、`salingPrice`、`previewUrl`、`publishedAt`、`remark`
- `ContentDistributionService.distributeTo()` 新增 `AUTHORITY_MEDIA` 分支
- `AuthorityMediaDistributionAdapter` 负责资源刷新、余额预检、order 落库、生成 `AM-{order.id}`、调用 `create_media_order`
- 本期只做到远端下单提交成功后 `DistributionTask.status=submitted`，远端 `0/1/2/-1/-2` 状态回查 Job 留到下一期
- 当前并发幂等使用单 JVM `(articleId, resourceId)` 锁，防止同实例双击/并发请求生成多个 `external_no`；多实例部署前必须升级为 Redis/ShedLock 分布式锁或 DB 级活跃订单唯一约束。

下单时序：

1. 创建 `distribution_tasks`，`target_kind=authority_media`，`authority_media_id=resourceId`
2. 创建 task 前先做本地资源、价格、预览地址、`published_at` 和同文章同资源未完成订单校验；命中未完成订单时返回 409，避免用户重复点击造成新 `external_no` 和重复扣费
3. 预占 `authority_media` 渠道额度
4. 下单前调用 `refreshNewsMediaResourceIfStale(resourceId)`，若资源已下架直接失败并释放额度
5. 调 `userInfo()` 做 30 秒缓存的余额预检；余额查询失败降级放行，余额不足直接失败
6. 创建 `authority_media_order` 本地行，初始 `submit_status=created`
7. 基于本地 order id 生成稳定 `external_no=AM-{id}`，同一 task 重试复用同一 no
8. 调 `create_media_order`
9. 成功：`authority_media_order.submit_status=submitted`，`remote_status=0`，task 进入 `submitted`，内部额度确认
10. 失败：`authority_media_order.submit_status=submit_failed`，task 进入 `failed`，内部额度释放

一致性窗口：

- 如果远端 `create_media_order` 成功但本地 `authority_media_order` 更新失败，日志必须以 `CRITICAL` 打出 `externalNo` 和远端响应，人工可据此去特价网后台对账。
- 上述窗口只有回查 Job 上线后才能自动闭环；回查 Job 需优先扫描 `submit_status in ('created','submitting','submitted','submit_failed')` 且存在 `external_no` 的订单。
- `distribution_tasks.external_status` 不保存特价网 `-2/-1/0/1/2`，远端原始状态只落 `authority_media_order.remote_status`，避免污染两层状态机。

审计与安全：

- `authority_media_order.request_payload` 使用 `MeititejiaClient.buildAuditPayload(...)` 后落库，不走签名、不生成 `secret_id/timestamp/signature`
- `request_payload` 不允许保存 `secret_id`、`timestamp`、`signature`
- 余额预检阈值为 `max(saling_price, resource.price) * geo.meititejia.balance-safety-factor`

## NEWS_MEDIA 资源同步实现约束

资源同步入口：

- `AuthorityMediaResourceSyncService.syncNewsMediaFull()`：全量翻页同步
- `AuthorityMediaResourceSyncService.syncNewsMediaIncremental()`：基于本地最大 `uptime - 60s` 增量同步，冗余覆盖同秒变更与同步中断重试边界
- `AuthorityMediaResourceSyncService.reconcileNewsMediaIds()`：通过 `get_ids` 对账，标记本地下架资源
- `AuthorityMediaResourceSyncService.refreshNewsMediaResourceIfStale(resourceId)`：下单前单资源刷新，默认过期阈值 60 分钟

分页规则：

- 默认 `geo.meititejia.sync-page-limit=200`
- 返回列表为空时停止
- 返回列表数量小于 `limit` 时处理完当前页后停止
- 如果响应不是 `data` 数组，也不是兼容的 `data.data` / `data.list` / `data.rows` 形态，同步任务必须 WARN 并失败，不能静默当作分页结束

落库规则：

- `raw_payload` 保存远端单条资源 JSON 的完整原文
- 结构化字段从 `raw_payload` 解析填充，解析失败时使用保守默认值
- `(resource_type, external_resource_id)` 使用 upsert 更新，重新出现的资源会清空 `deleted_at`
- `get_ids` 对账只做软下架，即设置 `deleted_at`，不物理删除
- 如果 `get_ids` 返回空列表，为避免远端异常导致全量误下架，本地跳过软删除并记录 WARN
- 如果 `get_ids` 返回超过 5000 个 ID，当前 `NOT IN` 对账会记录 WARN；二期应改为临时表/JOIN 或同步状态表方案，避免超大 IN 列表

调度规则：

- 调度总开关：`geo.meititejia.enabled=true` 且 `geo.meititejia.sync-enabled=true`
- 增量同步默认每小时：`geo.meititejia.news-media-incremental-cron=0 0 * * * *`
- 全量同步默认每天 02:30：`geo.meititejia.news-media-full-cron=0 30 2 * * *`
- ID 对账默认每天 03:30：`geo.meititejia.news-media-reconcile-cron=0 30 3 * * *`，生产环境如全量同步耗时更长，应继续后移或改为依赖最近一次全量成功状态

并发与观测：

- 当前仅依赖定时任务错峰避免同一 JVM 内全量/对账重叠；多实例部署前必须引入 ShedLock 或同等分布式锁。
- TODO：增加 `meititejia_sync_state` 或 Micrometer 指标，记录每类同步最后成功时间、失败次数和处理数量，用于连续失败告警。

## NEWS_MEDIA 订单回查实现约束

回查入口：

- `AuthorityMediaOrderStatusJob` 默认每 60 秒扫描一次，受 `geo.meititejia.enabled=true` 和 `geo.meititejia.order-status-check-enabled=true` 双开关控制。
- `geo.meititejia.order-status-check-enabled` 默认 `false`，金丝雀验证前不要打开。
- 默认批量大小 `geo.meititejia.order-status-batch-size=50`，代码上限 100。
- 查询条件：`resource_type=NEWS_MEDIA`、`external_no is not null`、`submit_status in ('submitted','submit_failed')`、`remote_status is null/0/1`、`next_check_at is null or next_check_at <= now`。

批量查询：

- 使用 `MeititejiaClient.queryOrders(NEWS_MEDIA, externalNos)` 调 `query_media_order`。
- `nostr` 当前会被 form 编码为 `%2C`；金丝雀阶段仍需实测 PHP 服务端是否正确 urldecode 批量订单号。

状态映射：

- `remote_status=0`：未处理，保留 task `submitted`，按分级策略写 `next_check_at`
- `remote_status=1`：发布中，保留 task `submitted`，按分级策略写 `next_check_at`
- `remote_status=2`：已完成，写 `authority_media_order.remote_status=2`，联动 `distribution_tasks.status=published` 和 `published_url`
- `remote_status=-1`：已拒稿，写 `authority_media_order.remote_status=-1`，联动 `distribution_tasks.status=failed`，`failure_kind=VALIDATION`
- `remote_status=-2`：已删除，写 `authority_media_order.remote_status=-2`，联动 `distribution_tasks.status=failed`，`failure_kind=PLATFORM`
- `remote_status=-2` 的真实语义尚未与特价网确认。当前保守映射为 `PLATFORM`；金丝雀阶段如出现该状态，必须追查实际原因并决定是否调整为 `VALIDATION`。

额度处理：

- 下单提交成功时 task 已确认额度。
- 远端回查进入 `-1/-2` 且 task 从 `submitted` 成功更新为 `failed` 时，调用 `refundConfirmedDistribution(taskId)` 将已确认 ledger 改为 `refunded` 并释放 usage。
- 回查重复执行时，task 状态条件和 order `remote_status` 查询条件共同保证退款幂等。
- 当前采用乐观额度确认策略：提交成功即扣减并确认额度，远端最终拒稿/删除时再退回。该策略会让用户在拒稿前看到额度已消耗；如业务侧要求远端 `status=2` 后才确认额度，需要调整下单提交阶段的 confirm 时机。

轮询策略：

- 提交后 1 小时内：每 5 分钟
- 1-24 小时：每 30 分钟
- 24 小时后：每 2 小时
- 超过 7 天仍未终态：ERROR 日志提示人工介入；后续可接入系统告警。

## P2 验证 SQL

测试环境执行：

```sql
SELECT DISTINCT target_kind FROM distribution_tasks;

SELECT *
FROM company_channel_quota
WHERE channel_code = 'authority_media'
LIMIT 1;
```

注意：当前仓库迁移中套餐渠道表为 `package_channel_quota_config`，客户绑定快照为 `company_package_binding.channel_quota_snapshot`。如果测试环境没有 `company_channel_quota` 表，应改查：

```sql
SELECT *
FROM package_channel_quota_config
WHERE channel_code = 'authority_media'
LIMIT 1;
```

## V121 迁移验证

测试环境执行：

```powershell
mvn flyway:migrate
```

检查项：

- `authority_media_resource` 建表成功
- `authority_media_order` 建表成功
- `meititejia_enum` 建表成功
- `authority_media_order` 外键能正常建立：
  - `distribution_tasks(id)`
  - `article_draft(id)`
  - `project(id)`
  - `authority_media_resource(id)`

当前状态：本地未执行测试环境 Flyway 迁移，需在具备测试库连接后补跑并把结果写入 PR 描述。

注意：

- 当前 `V121__authority_media_foundation.sql` 中 `authority_media_order.distribution_task_id` 与 `article_id` 必须和被引用表保持 `BIGINT UNSIGNED` 一致，否则 MySQL 会拒绝创建外键。
- 如果 V121 已在任一共享环境执行过，禁止直接修改 V121；必须新增 V122 `ALTER TABLE` 迁移修正字段类型，避免 Flyway checksum mismatch。

## 灰度回滚 SQL

仅在 V121 尚未承载真实订单或已确认可丢弃灰度数据时使用：

```sql
DROP TABLE IF EXISTS authority_media_order;
DROP TABLE IF EXISTS meititejia_enum;
DROP TABLE IF EXISTS authority_media_resource;
```

如果已有真实订单，禁止直接 drop，需要先导出 `authority_media_order` 和 `authority_media_resource`。

## 安全约束

- 真实 `secret_key` 不写入代码、迁移、测试或文档。
- 生产使用 `geo.meititejia.secret-key-ref`，通过环境变量 `AI_KEY_REF_{REF}` 注入。
- `request_payload` 落库前必须使用 `MeititejiaClient.buildAuditPayload(...)`，只保留编码后的业务字段。
- 禁止直接序列化完整 signed map，避免持久化 `secret_id`、`timestamp`、`signature`。
