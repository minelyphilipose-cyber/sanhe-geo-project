# 数据生命周期治理与 COS 迁移交接文档（2026-06-29）

## 1. 总目标

本轮工作的主线是“数据生命周期治理”，不是单纯的对象存储迁移。

核心目标：

- 防止数据库随业务增长无限膨胀；
- 对轮询明细、LLM 调用、文章生成、分发过程、售前 raw 等大体量数据做汇总、冻结、归档、置空或删除；
- 所有破坏性动作必须先 dry-run、审计、对账、门控，再小批量 execute；
- 文章正文最终从 DB 大字段迁出，DB 只保留正文对象 key、checksum、归档时间、发布记录等必要信息；
- 客户可见历史能力由 summary、freeze、发布链接、对象归档来承接。

当前阶段之所以转到 COS，是因为“文章正文置空”会让对象存储成为正文唯一在线副本。继续依赖单节点 MinIO 风险较高，所以必须先完成 COS 迁移与读写验证，才能继续推进正文置空 execute。

结论：

- 数据生命周期治理是主线；
- COS 迁移是“文章正文可安全置空”的基础设施前置；
- COS 稳定后，应回到数据清理主线，继续推进 slim / purge / content purge。

## 2. 两条工作线关系

### 2.1 数据清理主线

包括：

- poll 明细汇总与清理；
- 季报 freeze；
- 售前 raw slim；
- 文章正文归档与后续置空；
- 分发 payload slim；
- 对象存储 orphan 检测和后续真删；
- scheduler dry-run 和 execute 晋级机制。

### 2.2 COS 前置分支

包括：

- 统一 `ObjectStorageService` 抽象；
- 旧 `MinioStorageService` 委托到 provider 路由；
- 新增 `CosObjectStorageAdapter`；
- MinIO 到 COS verify-copy；
- brand material 访问 URL provider-neutral；
- 存量正文旧 MinIO 直链 rewrite；
- `provider=cos + fallback` 彩排；
- 生产迁移和切读。

COS 分支完成后，不代表数据清理完成，只代表“正文可置空”的基础风险被解除。

## 3. 数据生命周期治理已完成内容

### 3.1 汇总表与审计基础

已建立或设计落地：

- `poll_keyword_daily_summary`
- `poll_platform_daily_summary`
- `llm_usage_daily_summary`
- `article_generation_daily_summary`
- `data_retention_run`
- `data_retention_purged_slice`
- `data_retention_recompute_slice_lock`

关键口径：

- summary 使用 `dim_hash` 作为唯一身份；
- 不把可空维度直接放进唯一键；
- hash canonical 使用稳定分隔符；
- 关键词身份：
  - 有 `keyword_result_id` 用 ID；
  - 无 ID 回退 normalized text；
  - `keyword_text_snapshot` 只作展示，不进身份 hash。

### 3.2 PollSummaryRecomputeService

已完成要点：

- 按 slice 重算：`project_id + batch_date + question_tier`；
- 从存活明细整片重算；
- upsert 是 SET 语义，禁止 increment；
- 本次不再出现的 summary 行会删除，避免僵尸行；
- `source_checksum` 排序后生成，用于后续对账；
- `source_row_count` 用于 purge 门控；
- 同一 slice 使用 `data_retention_recompute_slice_lock` 串行；
- `recomputeSlice` 开头检查 `data_retention_purged_slice`；
- 拿锁后再次检查 purged，避免并发竞态；
- 已 purge slice 不重算、不删 summary；
- 接入聚合入口时走 after-commit / `REQUIRES_NEW`，不拖垮原聚合事务。

### 3.3 Backfill

已完成要点：

- 历史回填复用 `recomputeSlice`；
- 默认 dry-run；
- execute 写 `data_retention_run`；
- keyset 游标分页：
  - `batch_date`
  - `project_id`
  - `question_tier`
- 返回 `hasMore` 和 `nextCursor`；
- 单 slice 失败不阻断整批；
- 异常兜底收尾 run，避免永久 `running`。

### 3.4 Freeze

已完成或设计落地：

- `report_period_freeze`
- `report_period_freeze_guard`
- freeze 异步执行，不进入同步报告请求；
- 当前启用报告类型：quarterly；
- `period_key` 格式：`yyyyQn`；
- 按 `batch_date` 的 Asia/Shanghai 日期归属季度；
- 每项目每季度一个对象；
- 全量冻结季度内全部 问题×平台；
- 每个 问题×平台 取周期内最新一条；
- 最新排序不能用 `batch_no`，用真实落库时间 + id；
- 空 `keyword_result_id` 回退 normalized text，避免未匹配关键词漏出季报；
- 写对象 → 读回 → checksum 校验 → 回写 key → 标 FROZEN；
- FROZEN 后不原地改；
- 源未变重复触发 no-op；
- 显式重生成或源变化才新版本。

### 3.5 Dry-run handlers

已完成的 dry-run 能力：

- slim payload dry-run：
  - presale raw；
  - presale judge raw；
  - article generation snapshot；
  - distribution payload。
- `ArticleRetentionHandler` dry-run；
- `PollRetentionHandler` dry-run；
- `ObjectStorageRetentionHandler` dry-run；
- `DataRetentionScheduler` dry-run 编排基础。

重要门控：

- presale raw：
  - LLM usage summary 已覆盖；
  - `presale_report_version.generation_status IN (DONE, FAILED)`；
  - 不在重试队列。
- presale judge raw：
  - 结构化裁判字段已存在是硬门控；
  - token summary 只作参考，不作硬门控。
- distribution payload：
  - 终态；
  - published 必须已有 `article_publish_record`；
  - failed 必须保留失败 trace。

### 3.6 PollRetentionHandler dry-run

已完成要点：

- 候选识别；
- keyset 分页；
- 使用同一把 `data_retention_recompute_slice_lock`；
- 锁内复查门控；
- 不删数据；
- 不写 `data_retention_purged_slice`；
- 输出 shard items / shards / poll_results 影响量；
- 不删 `poll_batches`；
- 对账精确匹配，无容差；
- freeze 门控按启用报告类型循环，当前集合只有 quarterly，但实现不硬编码；
- `finished_with_failures` 已纳入终态集合；
- `period_key` 与 freeze 服务一致。

poll execute 的硬要求：

- purge 侧也必须拿同一把 slice 锁；
- 锁内复查门控；
- 锁内执行删子表、删明细、写 purged_slice；
- 删除顺序：
  1. `poll_batch_shard_items`
  2. `poll_batch_shards`
  3. `poll_results`
- 不删 `poll_batches`。

## 4. 文章正文归档已完成内容

### 4.1 归档写入已进入非破坏 execute

已实现：

- 默认 dry-run；
- execute 需显式 `dryRun=false`；
- execute 另受配置开关保护；
- 候选查询排除已归档版本；
- 复用 dry-run 门控；
- 写对象；
- 读回对象；
- SHA-256 / UTF-8 bytes / hex 校验；
- 条件回写：
  - `content_object_key`
  - `content_checksum`
  - `content_archived_at`
- 不置空 `content_markdown`。

并发与幂等：

- 已归档且对象存在、checksum 一致则 skipped；
- 条件 UPDATE 防止并发覆盖；
- `content_markdown = ?` 防止正文变更后错误回写；
- 状态变化或 publish record 消失时跳过/失败，不强写。

### 4.2 交付记录与归档门控

已处理：

- 补偿 `article_publish_record`；
- 交付状态口径已统一；
- `published_confirmed` 纳入已交付状态；
- 归档以已交付 `article_publish_record` 为准；
- 不再强依赖 `article_draft.status IN ('published','distributed')`；
- 仅排除 `deleted`；
- 多渠道交付同一文章只产生一个正文对象。

### 4.3 Dev 验证情况

已验证：

- 小批量真实归档成功；
- 对象写入后 readback checksum 一致；
- `content_markdown` 未动；
- orphan dry-run 能识别 DB 引用，未误判 orphan；
- 重跑幂等；
- approved 状态但有交付记录的文章能归档；
- 多渠道文章只归档一次；
- 带版本号对象 key 正确，如 `v2.md`。

## 5. COS / MinIO 迁移已完成内容

### 5.1 ObjectStorageService 抽象

已扩展 provider-neutral 接口：

- `putBytes`
- `readBytes`
- `openStream`
- `stat`
- `listObjects`
- `presignedGetUrl`
- `delete`
- `deletePrefix`

### 5.2 Adapter 与路由

已新增：

- `MinioObjectStorageAdapter`
- `CosObjectStorageAdapter`
- `RoutingObjectStorageService`
- `StorageProperties`

路由规则：

- `provider=minio`：读写 MinIO；
- `provider=cos`：读写 COS；
- `readBytes/openStream/stat` 在 COS 404 且 fallback 开启时回落 MinIO；
- 其它 COS 错误不吞；
- fallback 发生时 WARN：
  - 方法名；
  - objectKey；
  - `COS miss, fell back to MinIO`。

旧服务兼容：

- `MinioStorageService` 保留原方法签名；
- 内部委托到 `ObjectStorageService`；
- 旧调用方不需要大面积改造；
- `buildFileUrl` 在 `provider=cos` 时生成 COS URL，在 `provider=minio` 时保持旧 MinIO URL。

### 5.3 COS verify-copy 迁移

迁移接口：

- `POST /api/data-retention/object-storage/migrate`

安全策略：

- 默认 `dryRun=true`；
- execute 需：
  - `dryRun=false`
  - `geo.storage.migration.execute-enabled=true`
- DB key registry 驱动；
- 不扫全桶；
- 不删除 MinIO；
- 不改业务表 key；
- 写 `data_retention_run` 审计。

迁移流程：

1. 从 registry 查询 object_key；
2. keyset 游标分页；
3. 读 MinIO；
4. 若 DB checksum 存在，做预检；
5. COS 已存在且 checksum 一致则 skipped；
6. put COS；
7. 从 COS readback；
8. checksum 一致才算 migrated；
9. 单对象失败隔离；
10. 整批异常兜底收尾 run。

### 5.4 Registry 覆盖范围

当前 registry 覆盖：

- `brand_material.object_key`
- `sys_user.avatar_object_key`
- `presale_report_export.file_key`
- `presale_report_export.snapshot_key`
- `article_draft_version.content_object_key`
- `report_period_freeze.snapshot_object_key`

其中：

- `brand_material.object_key` 是生产最高优先级；
- `presale_report_export.file_key` 只迁未 purge、未过期；
- `snapshot_key` 只迁 `snapshot_storage_type='OBJECT'` 且未 purge、未过期；
- registry 支持 checksum 可选；
- 无 checksum 的对象迁移时跳过 DB checksum 预检，只做 readback 校验。

### 5.5 Orphan dry-run 与 registry

已处理：

- orphan 引用核对使用统一 registry；
- 新增业务 key 参与“是否有活引用”的判断；
- 但 orphan 扫描前缀没有扩大到 brand / avatar / presale；
- 当前 orphan 删除候选面仍限制在 archive/freeze 等托管前缀；
- 避免误把 brand 客户素材纳入可删候选。

## 6. URL 中立化已完成内容

### 6.1 brand material 新访问路径

已改为 provider-neutral 代理：

```text
/api/public/brand-materials/{id}/stream?sig=...
```

特性：

- `sig` 绑定 `materialId:brandId:objectKey`；
- 不含过期时间；
- objectKey 在 MinIO→COS 迁移中不变；
- 切 COS 后 URL 仍有效；
- 适合公开官网/文章图片长期 hot-link。

已调整：

- 后端 VO 返回 `publicUrl`；
- `fileUrl` 降级为兼容字段；
- 前端素材列表、预览、手动插图使用 `publicUrl`；
- 新插入正文不再写 MinIO 直链。

### 6.2 存量正文 URL rewrite

已实现工具：

- 匹配正文中的旧 MinIO / `/geo-files/` URL；
- 提取 object_key；
- 用 object_key 查 `brand_material`；
- 命中则替换为 public proxy URL；
- 未命中只报告，不盲改；
- 默认 dry-run；
- execute 双闸；
- 乐观更新：
  - `WHERE id=? AND content_markdown=?`
- 幂等：
  - 已是代理 URL 不再重复改。

注意：

- 如果正文版本已经归档，rewrite 后 DB 正文与归档对象会不一致；
- 将来置空前，需要对受影响版本重新归档。

## 7. Dev 验证记录

Dev COS bucket：

- `geo-files-1422803602`
- region：`ap-nanjing`

已验证过：

- brand material 迁移；
- avatar 迁移；
- presale export 迁移；
- article archive 迁移；
- `provider=cos + fallback=true` 彩排；
- 补迁新增 presale export；
- `provider=cos + fallback=false` 后：
  - brand image preview 200；
  - brand public stream 200；
  - presale export download 200；
  - fallback WARN 为 0。

曾发现并修复：

- COS `NoSuchKey/404` 被包装成 500，导致迁移首次幂等检查失败；
- 已修为保留 404，供 fallback 和存在性判断使用。

登录头像：

- 头像 COS presign 失败不再阻断登录；
- `AuthService` 会 WARN 并回退 DB `avatarUrl`。

## 8. Prod 盘点结论

用户提供的 prod 只读 SQL 结果：

### 8.1 必迁

- `brand_material.object_key`
  - 330 rows；
  - 330 distinct keys。
- `presale_report_export.file_key(active)`
  - 3 rows；
  - 3 distinct keys。

### 8.2 当前为 0

- `sys_user.avatar_object_key`
- `presale_report_export.snapshot_key(active)`
- `ai_platform_config.platform_logo_url`
- `reports.pdf_url`

### 8.3 历史内容

- `article_draft_version.content_markdown` 中旧 `/geo-files/` URL：约 4 条；
- 用户已确认：
  - prod 当前官网、平台网站、行业资讯站历史分发内容无需处理；
  - 自媒体历史为手动分发，图片本地上传，也无需处理；
  - 当前 prod 已分发内容不阻塞 COS 迁移。

### 8.4 关键结论

生产优先迁移范围是：

1. `brand/` 下 330 个客户素材；
2. `presale/exports/` 下 3 个 active 导出文件。

`brand_material.file_url` 虽然仍是旧完整 URL，但 object_key 齐全，新代码已改为 public proxy 访问，不再把 file_url 作为主路径。

## 9. 生产推荐执行顺序

### 9.1 准备阶段

1. 新建生产独立 COS bucket。
2. 新建生产独立 CAM 子账号/密钥。
3. 不复用 dev COS bucket / dev CAM。
4. 确认 COS region 与生产 CVM 同地域，优先走内网。
5. 配置生产环境变量，但先不切读。

### 9.2 首次上线配置

生产先保持：

```env
GEO_STORAGE_PROVIDER=minio
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
GEO_STORAGE_MIGRATION_EXECUTE_ENABLED=false
```

即：

- 业务仍从 MinIO 读写；
- COS 配置可以存在；
- 不切 provider；
- 不打开 migration execute 常驻；
- 不打开任何 slim / purge / orphan delete / content purge execute。

### 9.3 Prod verify-copy

1. 跑 migration dry-run；
2. 预期候选约 333；
3. 首次 execute：

```text
prefix=brand/
limit=1
dryRun=false
```

4. 验证：
   - `migratedCount=1`；
   - `failedCount=0`；
   - COS 能 readBytes；
   - checksum 与 MinIO 源一致；
   - MinIO 源仍存在；
   - 无 auth / endpoint 错误。
5. 放量迁完 `brand/` 330 个；
6. 迁 `presale/exports/` 3 个；
7. 跑 dry-run 确认剩余为 0 或仅有预期尾巴。

### 9.4 切读阶段

上线后再单独安排窗口切：

```env
GEO_STORAGE_PROVIDER=cos
GEO_STORAGE_READ_FALLBACK_TO_MINIO=true
```

观察：

- `COS miss, fell back to MinIO`
- auth 错误；
- endpoint 错误；
- presign 错误；
- 业务下载/图片访问异常。

关 fallback 前：

1. 再跑一次 migration dry-run；
2. 补迁尾巴；
3. 确认 fallback WARN 为 0；
4. 再设置：

```env
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
```

### 9.5 MinIO 退役

- 不要立刻删除 MinIO 对象；
- 先留冷备；
- 确认 COS 稳定一段时间；
- 确认所有 provider-neutral URL、presign、proxy stream 正常；
- 再另行制定退役方案。

## 10. 当前上线安全边界

如果生产按以下配置上线：

```env
GEO_STORAGE_PROVIDER=minio
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
GEO_STORAGE_MIGRATION_EXECUTE_ENABLED=false
```

并且不打开任何破坏性 execute，则：

- 不会自动删除数据；
- 不会置空正文；
- 不会删除 MinIO 对象；
- 不会删除 COS 对象；
- 不会 purge `poll_results`；
- 正常业务仍走 MinIO；
- 数据清理链路未完成不会阻塞文章生成、素材上传、分发、售前导出等正常业务。

主要风险来自误配置：

- 过早切 `provider=cos`；
- migration execute 常驻开启；
- 未迁完就关闭 fallback；
- 未验证 rehydration 就置空正文；
- 未执行 dry-run 晋级就开启 purge/slim/object delete。

## 11. 仍未完成的事项

### 11.1 数据清理主线

未完成：

- 文章正文置空 execute；
- presale raw slim execute；
- article generation snapshot slim execute；
- distribution payload slim execute；
- `poll_results` purge execute；
- object orphan 真删 execute；
- scheduler execute 晋级；
- 合规保留期、解约尾巴期、冷归档周期配置落地。

### 11.2 文章正文置空前置

置空前必须完成：

1. COS 生产切读稳定；
2. `ArticleBodyProvider` 对象分支端到端验证；
3. 发布/改稿/retry 真实 rehydration 路径接入；
4. 待发渠道需要正文时阻断置空；
5. 已归档但正文后来改写的版本重新归档；
6. 置空 dry-run；
7. 小批 execute；
8. 验证前端/接口读取正常。

### 11.3 Poll purge 前置

execute 前必须确认：

- 读路径已切 summary/freeze；
- freeze 缺失时不回退扫明细；
- 对账不一致先重算；
- 重算后仍不一致则跳过不删；
- purge 侧拿同一把 slice lock；
- 删除顺序先子后父；
- 晚到明细命中已 purged slice 时拒收并告警。

### 11.4 Object orphan 真删前置

execute 前必须确认：

- registry 覆盖所有持 object key 的列；
- list 出来的 key 与 DB key 逐字节一致；
- 有足够宽限期；
- 只扫托管前缀；
- 合规尾巴期配置已确定；
- dry-run 多轮无误判；
- COS/MinIO 切换稳定。

## 12. 下一轮建议操作方向

建议按以下顺序继续。

### 第一阶段：生产 COS 迁移落地

目标：先让生产客户素材和 active 导出文件有 COS 冗余副本。

步骤：

1. 新建 prod COS bucket；
2. 新建 prod CAM 子用户；
3. 配 prod env；
4. 部署代码但保持 `provider=minio`；
5. migration dry-run；
6. `brand/ limit=1` 首迁；
7. 迁完 `brand/`；
8. 迁 `presale/exports/`；
9. 保持 MinIO 在线。

### 第二阶段：生产切 COS 读

目标：让业务读路径实际从 COS 取对象，但保留 MinIO fallback。

步骤：

1. `provider=cos`；
2. `fallback=true`；
3. 观察 WARN；
4. 补迁尾巴；
5. WARN=0 后 `fallback=false`；
6. MinIO 留冷备。

### 第三阶段：回到数据清理主线

优先建议：

1. 先做 `ArticleBodyProvider` 对象分支验证；
2. 做 rehydration 真实路径；
3. 做待发渠道阻断；
4. 做正文置空 dry-run；
5. 小批正文置空 execute；
6. 再推进 slim execute；
7. 最后推进 poll purge execute；
8. object orphan 真删放最后。

## 13. 新对话接续提示词建议

新对话可以直接这样开始：

```text
我们继续数据生命周期治理。当前主线是防止 DB/对象存储无限膨胀；COS 迁移只是文章正文置空前置。

请先从 geo-project/docs/data-lifecycle-cos-handoff-2026-06-29.md 读取交接文档，按文档继续。

当前优先事项：
1. 先确认生产 COS bucket/CAM/env 是否齐全；
2. 若齐全，给出 prod migration dry-run 与 brand/ limit=1 execute 的操作清单；
3. 在 COS 生产迁移完成前，不开启正文置空、slim execute、poll purge execute、object delete execute；
4. 正常业务上线仍保持 provider=minio。
```

## 14. 当前状态一句话总结

数据清理地基已经完成较多：summary、freeze、backfill、dry-run handlers、文章正文非破坏归档写入都已具备；但真正减少 DB 体积的置空 / slim / purge / object delete 还没有开启。COS 迁移是为了让后续正文置空安全，目前 dev 已验证，prod 下一步应先迁 `brand_material` 和 active presale export，再回到数据清理主线。
