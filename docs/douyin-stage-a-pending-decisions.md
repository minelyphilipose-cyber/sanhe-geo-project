# Douyin Stage A Decision Tracker

Last updated: 2026-05-05

This document tracks decisions and operation-side dependencies for Douyin image-text distribution. Stage A mock development is complete; A.8/A.9 real integration must not start until the blocking external dependencies below are resolved.

## Pending Decisions And External Dependencies

| No. | Decision / Dependency | Owner | Required By | Current Status | Impact If Open | Notes / Acceptance Criteria |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Douyin Open Platform主体认证后台具体字段与材料 | 运营 | A.8真实 OAuth 联调前 | 待确认 | 无法确认应用注册材料完整性 | 输出主体认证提交材料清单和后台截图/字段说明。 |
| 2 | 行业类目选择及是否触发特殊行业准入 | 运营 | scope申请前 | 待确认 | scope审核可能被退回或要求补充资质 | SEO/营销服务对应类目需以开放平台后台可选项为准。 |
| 3 | `video.create.bind` 使用场景说明最终文案 | 运营 + 法务 | scope申请前 | 待确认 | 影响权限审核通过率 | 基于v2模板出最终版，说明用户授权后由系统代发图文、内容来源、审核与用户操作路径。 |
| 4 | 网站应用ICP备案、隐私政策页、用户协议页是否就绪 | 产品 | 应用上线前 | 待确认 | 应用上线/权限审核可能无法提交 | 给出可访问HTTPS URL，页面内容覆盖数据授权、内容发布、账号解绑。 |
| 5 | 抖音是否提供沙箱环境 | 运营 | A.8真实联调计划前 | 待确认 | 决定真实联调风险和Mock覆盖强度 | 如无沙箱，Stage A Mock覆盖仍作为联调前基线。 |
| 6 | 单账号日发布配额准确数字 | 运营 | A.9真实发布前 | 待确认 | 无法做本地日配额软拦截 | 后台确认后写入 `self_media_account.extra_json.daily_publish_quota` 或配置表。 |
| 7 | Stage B内容策略选型 | 产品 | Stage B启动前 | 待确认 | 影响图文质量和图片生成工作量 | 候选：AI排版生图、正文图提取+智能裁剪、运营手动选图。 |
| 8 | 拒审后二次编辑重提流程 | 产品 | 真实审核状态可获得后 | 待确认 | 影响幂等requestId、任务复用还是新建任务 | Stage A仅支持Mock状态刷新；真实拒审流等A.8/A.9确认平台能力后再定。 |
| 9 | Stage A灰度范围与白名单策略 | 产品 + 运营 | 内测开放前 | 待确认 | 实验功能可能误开放给不应使用的客户 | 当前仅通过feature flag关闭/开启；若开放给客户，需确认 `brand_id`、`user_id` 或角色白名单。 |
| 10 | 测试用/联调用抖音企业账号 | 运营 | A.8真实联调前 | 待确认 | 没有真实账号无法做端到端联调验证 | 至少准备1个企业认证账号，配合应用注册后做授权测试。 |

## Resolved Stage A Decisions

| Decision | Result | Commit |
| --- | --- | --- |
| 账号抽象后的任务目标类型 | 保留 `target_kind='mp_account'`，语义扩展为所有 `self_media_account` 类型；具体平台通过 `self_media_account.platform` 区分。 | `c2010911`, `138cb184`, `4729d1fa` |
| 抖音素材读取路径 | 严格使用品牌素材的 MinIO `objectKey` 读取图片字节；禁止 `BrandMaterial.fileUrl` fallback。`objectKey` 缺失时抛 `BizException(400, "brand material missing object key")`。 | `92827816` |
| Mock审核结果数据流 | `_mock_review_outcome` 写入平台响应 `responsePayload`，`refreshReviewStatus` 从 `responsePayload` 解析并映射为 `published / under_review / rejected`。真实模式缺少该字段时返回 `UNKNOWN`。 | `69451ca0` |
| 抖音图文feature flag行为 | 采用选项B：`DouyinImageTextAdapter` bean始终注册，`geo.douyin.feature.image-text.enabled=false` 时在adapter内部抛 `503 douyin image-text feature disabled`。 | `e2ef0e59`, `69451ca0` |
| 内容文案字段位置 | 保留 `platformOptions.text`，不提升为顶层DTO字段；前端通过 `DouyinPlatformOptions` 约束。空文案走后端标题兜底。 | `69451ca0`, `4ea8edbc` |
| Stage A高级发布字段 | `hashtags / atUsers / poiId / microApp / microAppId` 等高级字段Stage A不传；仅传图片数组、文案、`privateStatus`、`downloadType`。 | `69451ca0`, `c78eb32a`, `4ea8edbc` |
| Stage A撤回/删除能力 | Stage A不实现撤回/删除；如后续产品需要，单独确认官方API和状态机。 | `69451ca0` |
| 图片排序交互 | Stage A前端使用上下箭头排序，不做拖拽。 | `4ea8edbc` |
| 审核状态字段落库 | `DistributionTask.externalStatus / reviewStatus / reviewFeedback` 写入任务表；漏掉的SQL列通过V106补齐。 | `d1352ebc`, `0f85e7b2` |
| 自媒体账号列表 | 列出同品牌下所有平台账号，不再只过滤 `wechat_mp`；排序为 `platform ASC, updated_at DESC`。 | `e5e4da61` |

## Operational Dependency Summary

| Dependency | Code Can Proceed? | Blocks |
| --- | --- | --- |
| 主体认证 | Mock code complete | A.8真实 OAuth |
| 应用注册和 `client_key/client_secret` | Mock code complete | A.8真实 OAuth |
| `video.create.bind` scope通过 | Mock code complete | A.9真实图片上传和创建图文 |
| 日发布配额 | Code can run without local quota soft-check | 精准本地配额软校验 |
| 沙箱可用性 | Mock code complete | 真实联调方案和风险边界 |
| 测试抖音账号 | Mock code complete | A.8/A.9端到端真实账号授权和发布 |

## Tracking Rule

Before A.8 starts, every pending item that blocks real OAuth must have a named owner, target date, and confirmed value. Before A.9 starts, `video.create.bind`, real callback domain, test account, and real image-text publish quota must be confirmed.
