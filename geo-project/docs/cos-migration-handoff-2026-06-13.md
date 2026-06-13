# 数据生命周期治理与 COS 迁移交接记录（2026-06-13）

## 主线说明：数据清理才是原始目标

本轮工作的原始目标不是“把 MinIO 换成 COS”，而是建立数据生命周期治理能力，防止数据库和对象存储随时间无限膨胀。核心业务目标包括：

- 轮询明细 `poll_results` 等热数据定期汇总、冻结、对账后再清理；
- 售前/文章/分发链路中的大字段先有汇总或交付记录，再进入 dry-run slim；
- 文章正文先归档成可校验对象，再在满足前置条件后置空 DB 正文；
- 所有清理、归档、置空动作都走 dry-run、审计、门控和可回滚的受控流程。

当前之所以阶段性转向 COS，是因为“文章正文置空”会让对象存储成为正文的唯一在线副本。如果继续依赖单节点 MinIO，一旦置空后 MinIO 丢对象，就会造成客户正文不可恢复。因此 COS 迁移是文章正文置空之前的基础设施前置，不是数据生命周期治理的最终目标。

换句话说：

1. 数据生命周期治理是主线；
2. COS 迁移是其中“文章正文可安全置空”的前置条件；
3. COS 迁移完成后，应回到清理主线，继续推进 slim / purge / 置空 execute。

## 数据生命周期当前完成度

### 已完成或已验证

- 汇总/回填基础：
  - `poll_keyword_daily_summary`、`poll_platform_daily_summary`、`llm_usage_daily_summary`、`article_generation_daily_summary` 已建立；
  - `PollSummaryRecomputeService` 已按 SET 语义整片重算；
  - 已加 slice 锁、防 purged slice 被重算抹掉、backfill keyset 分页与审计。
- 冻结基础：
  - `report_period_freeze` 和 freeze guard 已建立；
  - freeze 选择逻辑按季度、按真实落库时间取最新、空 keyword id 回退文本身份。
- dry-run handler：
  - slim payload dry-run 已实现；
  - article body archive dry-run/execute 写入已实现；
  - poll retention dry-run 已实现完整门控评估；
  - object storage orphan dry-run 已实现引用登记清单和宽限期。
- 文章正文归档写入：
  - 已实现非破坏 execute：写对象、读回校验、条件回写 key/checksum/archived_at；
  - 不置空 `content_markdown`；
  - dev 已验证对象写入、checksum、orphan 引用识别。
- 内容 URL 中立化：
  - 新 brand material 图片访问已走 provider-neutral public proxy；
  - dev 存量正文旧 MinIO 直链 rewrite 已验证。

### 尚未完成，下一轮应继续推进

- 文章正文置空 execute：
  - 当前只完成归档写入，尚未置空 DB 正文；
  - 置空前必须确认 COS 稳定、`getArticleBody` 对象分支、发布/改稿/retry rehydration 路径、待发渠道阻断。
- slim execute：
  - 售前 raw、文章过程快照、distribution payload 目前只到 dry-run；
  - execute 仍未开启。
- poll_results 真正 purge：
  - 当前只到 dry-run；
  - execute 仍未开启；
  - purge 侧必须拿同一把 `data_retention_recompute_slice_lock`。
- object storage 真删：
  - 当前只到 orphan dry-run；
  - execute 未开启；
  - COS/MinIO 切换稳定前不应真删对象。
- 数据保留策略：
  - 客户保留下限、解约尾巴期、冷归档周期仍是配置位，待业务/合规拍板。

## 当前目标

当前这个 workstream 的阶段性目标，是把对象存储从单节点 MinIO 迁移到腾讯云 COS，为后续“文章正文置空”扫清基础设施风险。稳定态目标：

- 本地开发默认 MinIO；
- 生产环境最终使用 COS；
- 迁移期生产使用 `provider=cos + fallback-to-minio=true`；
- soak 通过后关闭 fallback；
- MinIO 只保留一段冷备，不再作为在线依赖。

## 本轮已完成代码

### ObjectStorage 抽象与路由

- 扩展 `ObjectStorageService`：
  - `putBytes`
  - `readBytes`
  - `openStream`
  - `stat`
  - `listObjects`
  - `presignedGetUrl`
  - `delete`
  - `deletePrefix`
- 新增 `CosObjectStorageAdapter`。
- 新增 `RoutingObjectStorageService`：
  - `provider=minio` 时走 MinIO；
  - `provider=cos` 时走 COS；
  - `readBytes/openStream/stat` 支持 COS 404 时回落 MinIO；
  - 回落时打 WARN：`COS miss, fell back to MinIO`。
- `MinioStorageService` 保持原方法签名，内部委托 `ObjectStorageService`，调用方无需改造。
- `buildFileUrl` 在 `provider=cos` 时生成 COS URL，在 `provider=minio` 时保持旧 MinIO URL。

### COS verify-copy 迁移

- 新增对象迁移接口：
  - `POST /api/data-retention/object-storage/migrate`
  - 默认 `dryRun=true`
  - execute 需要 `dryRun=false` 且 `geo.storage.migration.execute-enabled=true`
- 迁移 registry 当前覆盖：
  - `brand_material.object_key`
  - `sys_user.avatar_object_key`
  - `presale_report_export.file_key`（未 purge、未过期）
  - `presale_report_export.snapshot_key`（OBJECT、未 purge、未过期）
  - `article_draft_version.content_object_key`
  - `report_period_freeze.snapshot_object_key`
- 迁移逻辑：
  - DB key 驱动，不扫全桶；
  - MinIO 读源；
  - COS 已存在且 checksum 一致则 skipped；
  - 否则 put COS，再 readback 校验；
  - 不删除 MinIO；
  - 不改业务表 key；
  - 写 `data_retention_run` 审计。

### URL 中立化

- brand material 新访问主 URL 使用 provider-neutral 代理：
  - `/api/public/brand-materials/{id}/stream?sig=...`
  - `sig` 绑定 `materialId:brandId:objectKey`，不含过期时间；
  - objectKey 不变，因此切 COS 后 URL 仍有效。
- `brand_material.file_url` 降级为兼容字段，不再作为新访问主 URL。
- 新增存量正文 URL 重写：
  - dry-run/execute 双闸；
  - 只把正文中可映射到 `brand_material.object_key` 的旧 `/geo-files/` URL 改成 public proxy URL；
  - 未命中 material 的 URL 只报告，不盲改；
  - 幂等；
  - 乐观更新 `WHERE id=? AND content_markdown=?`。

### 文章归档写入

- 已实现文章正文归档写入（非破坏）：
  - 写对象；
  - readback checksum 校验；
  - 条件回写 `content_object_key/content_checksum/content_archived_at`；
  - 不置空 `content_markdown`。
- 归档对象已被迁移到 COS，可用于后续置空前验证。

### 登录头像兜底

- 登录时头像预签名失败不会再导致登录失败。
- `AuthService` 会 WARN 并回退 DB 中的 `avatarUrl`。
- 同时 `application.yml` 默认恢复为：
  - `GEO_STORAGE_PROVIDER=minio`
  - `GEO_STORAGE_READ_FALLBACK_TO_MINIO=false`

## Dev 验证记录

### 已迁移到 COS 的 dev 对象

- brand material：47 个；
- avatar：1 个；
- presale active export：4 个（67/68/69/71）；
- article archive：10 个。

### 4b 彩排结果

在 8081 上验证：

- `provider=cos + fallback=true` 时：
  - brand public stream 200；
  - brand COS presign preview 200；
  - brand doc COS presign 200；
  - brand doc proxy stream 200；
  - avatar COS presign 200；
  - article archive COS checksum 与 DB 一致；
  - 测试上传落 COS，删除后 DB 无残留。
- 初次发现 `presale/exports/71/report.pdf` fallback 到 MinIO；
- 执行 `prefix=presale/exports/` 补迁：
  - candidate=4；
  - migrated=1；
  - skipped=3；
  - failed=0；
  - 新迁 `presale/exports/71/report.pdf`。
- 重启为 `provider=cos + fallback=false` 后：
  - `presale/exports/71/report.pdf` 下载 200；
  - brand image preview 200；
  - brand public stream 200；
  - fallback WARN 为 0。

## Prod 盘点结论

生产当前仍是旧结构，部分 dev 新表/新字段不存在。本次只按 prod 可运行 SQL 盘点。

必迁范围：

- `brand_material.object_key`：330 条 / 330 distinct key；
- `presale_report_export.file_key(active)`：3 条 / 3 distinct key。

无需处理：

- `sys_user.avatar_object_key`：0；
- `presale_report_export.snapshot_key(active)`：0；
- `ai_platform_config.platform_logo_url`：0；
- `reports.pdf_url`：0。

其他：

- `article_draft_version.content_markdown` 中旧 `/geo-files/` URL：4 条。
- 用户已确认 prod 历史官网/平台站/行业站/自媒体已分发内容无需处理；这 4 条不阻塞当前 COS 切换。
- `brand_material.file_url` 330 条仍是旧 `/geo-files/` 完整 URL，但 object_key 齐全；新代码已不把 file_url 作为主访问路径。

## Prod 推荐执行顺序

1. 新建 prod 独立 COS bucket 和 CAM 子用户，不复用 dev COS。
2. 部署当前代码，生产先保持：
   - `GEO_STORAGE_PROVIDER=minio`
   - `GEO_STORAGE_READ_FALLBACK_TO_MINIO=false`
3. 配置 prod COS 变量，但不切读。
4. 跑 prod 迁移 dry-run，预期约 333 个 key。
5. 首次 execute 使用：
   - `prefix=brand/`
   - `limit=1`
6. 验证 prod COS endpoint、凭证、checksum 后，放量迁完 `brand/` 330 个。
7. 迁 `presale/exports/` 3 个 active export。
8. 此时 durability 已兑现，但业务仍从 MinIO 读。
9. 上线后尽快切：
   - `GEO_STORAGE_PROVIDER=cos`
   - `GEO_STORAGE_READ_FALLBACK_TO_MINIO=true`
10. 观察 WARN：`COS miss, fell back to MinIO`。
11. 关 fallback 前再补迁一次尾巴。
12. WARN 为 0 后关 fallback：
   - `GEO_STORAGE_READ_FALLBACK_TO_MINIO=false`
13. MinIO 保留冷备一段时间，再退役。

## 配置口径

本地默认：

```env
GEO_STORAGE_PROVIDER=minio
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
```

Dev COS：

```env
GEO_STORAGE_PROVIDER=cos
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
COS_REGION=ap-nanjing
COS_BUCKET=geo-files-1422803602
COS_INTERNAL_ENDPOINT=cos.ap-nanjing.myqcloud.com
COS_SECRET_ID=...
COS_SECRET_KEY=...
```

Prod 切换期：

```env
GEO_STORAGE_PROVIDER=cos
GEO_STORAGE_READ_FALLBACK_TO_MINIO=true
COS_REGION=<prod-cvm-same-region>
COS_BUCKET=<prod-bucket-with-appid>
COS_INTERNAL_ENDPOINT=cos.<region>.myqcloud.com
COS_SECRET_ID=...
COS_SECRET_KEY=...
```

Prod 稳定期：

```env
GEO_STORAGE_PROVIDER=cos
GEO_STORAGE_READ_FALLBACK_TO_MINIO=false
```

注意：Spring Boot 不会天然读取 `.env` 文件；运行脚本、IDE 或部署平台必须显式注入这些环境变量。

## 已知注意事项

- 当前 dev 8081 用于验证，最后处于 `provider=cos + fallback=false`。
- 生产正式切 COS 前，必须使用 prod 独立 bucket 和 prod CAM 子用户。
- 生产迁移是时间点快照；迁移后、切 COS 前新增的 MinIO 对象必须靠补迁覆盖。
- `content_markdown` 存量旧直链在 prod 当前不阻塞，但退役 MinIO 前需要重新评估。
- `mvn clean compile` 在 Windows 上可能因运行中的 8081 日志文件被锁失败；普通 `mvn compile -DskipTests` 已通过。
