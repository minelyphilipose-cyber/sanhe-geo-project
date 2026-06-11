# 自媒体自动发布链路回归清单

## 1. 基础配置

- 品牌存在启用的 AdsPower 浏览器环境，环境代号与扩展领取任务时的 `environmentKey` 一致。
- 自媒体账号状态为 `active`，已绑定浏览器环境账号，登录状态为 `logged_in`。
- 平台定时发布能力为 `verified`，`supportsSchedule=true`，`v1Strategy=platform_schedule`。
- 品牌已维护默认发布位置，例如 `selfMediaPublishLocationName=阜阳`。
- 品牌素材库存在封面图片；优先覆盖“封面”文件夹存在与不存在两种情况。

## 2. 文章与封面

- 手动创建自媒体文章时，未选择封面不能保存。
- 手动选择品牌素材库封面后，文章详情页能直接显示图片，而不是显示 HTML 片段。
- 自动生成自媒体文章时，能自动选择品牌素材库图片作为封面。
- 文章保存后，详情页封面、正文内图片、点击图片预览均能完整展示。

## 3. 排期创建

- 创建 `platform_schedule` 排期时，请求体使用数组字段：
  - `articleIds: [文章ID]`
  - `selfMediaAccountIds: [账号ID]`
- 头条排期时间需晚于当前时间 2 小时以上；过近时间应返回 `PLATFORM_SCHEDULE_TIME_TOO_CLOSE`。
- 平台能力未验证、账号未绑定环境、登录状态不一致时，应进入 `rejectedItems`，不创建脏排期。
- 创建成功后，排期状态为 `pending`，`queueKind=schedule_execution`。
- 今日头条 `nextAttemptAt` 应为计划发布时间前约 130 分钟，用于提前打开页面设置定时发布。

## 4. 助手领取与页面填充

- 到达 `nextAttemptAt` 后，本地助手能领取带环境标识的任务。
- AdsPower 打开正确环境，不出现其他品牌环境标识串扰。
- 头条页面填充标题、正文、封面、位置。
- 位置下拉框需选中匹配城市项，例如“阜阳”，不能写入正文。
- 封面本地上传后，页面展示完整缩略图。

## 5. 头条定时发布

- 助手点击“定时发布”，不是“预览并发布”。
- 弹窗内日期、小时、分钟三个下拉框均选择目标发布时间，并保持选中结果。
- 点击弹窗中的“预览并定时发布”。
- 预览页最终按钮为“定时发布”时，助手继续点击该按钮。
- 成功后页面进入作品管理列表，作品显示“定时发布中”和目标发布时间。
- 后端排期进入 `scheduled`，并记录平台侧诊断信息。

## 6. 发布结果确认

- 计划发布时间到达后，发布结果检查队列应进入 `publish_due` 或 `checking_publish_result`。
- 平台已发布时，系统应能自动回查并确认到 `published_confirmed`；必要时仍可人工确认。
- 确认发布后必须回写 `platformPublishedUrl`，诊断中应出现可访问的头条预览链接，例如 `https://mp.toutiao.com/profile_v4/graphic/preview?pgc_id=...`。
- 回查诊断应至少包含 `found=true`、`hasTitle=true`、`hasPublishedSignal=true`，如有默认位置则 `hasLocation=true` 且 `locationName` 匹配品牌默认发布位置。
- 平台未找到或状态不确定时，进入 `publish_unknown` 或 `publish_failed`，排期抽屉能显示异常原因。
- 取消已提交平台定时的排期时，先进入 `cancel_pending_platform`，平台确认后再变为 `cancelled`。

### 今日头条闭环样例

- 排期：`#29`
- 状态：`published_confirmed`
- 计划发布时间 / 平台定时时间：`2026-06-05 13:15`
- 队列：`publish_result_check`
- 发布链接：`https://mp.toutiao.com/profile_v4/graphic/preview?pgc_id=7647742085897945663`
- 诊断关键结果：`found=true`、`hasTitle=true`、`hasLocation=true`、`hasPublishedSignal=true`、`locationName=阜阳`
- 验收口径：活动告警为空，建议为“无需处理”。

## 7. 排期健康可视化

- `pending` 且未来执行的任务显示为“待执行”。
- `filling`、`scheduling`、`checking_publish_result` 或锁定中的任务显示为“执行中”。
- `nextAttemptAt` 已过且未锁定的活跃任务显示为“超时”。
- `pending` 到达填充时间后仍未领取时，应出现 `SCHEDULE_FILL_OVERDUE` 告警。
- 需要助手推进的到期排期，如近期没有本地助手心跳，应出现 `HELPER_OFFLINE` 告警。
- `schedule_failed`、`publish_failed` 显示为“失败”。
- `manual_required`、`routed_to_semi_auto` 显示为“人工处理”。
- `scheduled` 显示为“已定时”。
- `scheduled` 的平台定时时间已过但仍未确认发布时，应出现 `PLATFORM_SCHEDULE_MISSED` 告警。
- `publish_due`、`publish_unknown`、`cancel_pending_platform` 显示为“发布待确认”。
- `publish_unknown` 达到最大回查次数后，应出现严重级别 `PUBLISH_RESULT_UNKNOWN` 告警。
- `published_confirmed` 但 `platformPublishedUrl` 为空时，应出现 `PUBLISH_LINK_MISSING` 告警。
- 诊断弹窗应包含队列、请求、浏览器环境、平台排期 ID、失败码、失败消息和原始 diagnostics JSON。
- 诊断弹窗应展示活动告警，排期列表行应展示告警数量标签。

## 8. 自动分发前置流程

- 后台定时任务只处理启用内容生成的 active 项目。
- 配额按渠道拆分，`self_media` 配额应分配到可用自媒体账号。
- 自媒体账号目标必须满足：账号启用、平台能力验证通过、绑定环境登录正常。
- 自动生成文章后，自媒体目标应创建 `self_media_publish_schedule`，不是普通站点发布任务。
- 不同平台或账号有多个可用目标时，应尽量均匀分配。
- 无可用自媒体目标时，自动分发 item 应失败并记录明确原因，不影响其他渠道。

## 9. 百家号专项回归

- 账号配置需维护百家号 ID / `app_id`，发布结果回查 URL 必须包含对应账号的 `app_id`，不能复用固定 URL。
- 填充成功后，扩展侧 `publishVerification` 应至少包含 `platformStatus`、`plannedScheduledAt`、`platformScheduledAt`、`scheduleAdjusted`、`successSignal`。
- 若百家号将计划时间校正到平台最早可选时间，后端应以 `platformScheduledAt` 更新排期的平台定时时间，同时保留原计划时间用于诊断。
- 作品管理页出现“审核中”和“预计 YYYY-MM-DD HH:mm 发布”时，计划时间未到前应保持 `pendingScheduled=true`，不能提前确认 `published_confirmed`。
- 计划时间到达后，作品管理页命中文章标题、审核/已发布信号、预计发布时间时，发布结果回查可确认平台状态并推进排期。
- 本地助手无任务可领取时，响应应返回明确 `claimBlockedReason`：`NO_DUE_TASK`、`NO_AVAILABLE_ACCOUNT`、`PLATFORM_CAPABILITY_DISABLED`；渠道额度类失败应归类为 `CHANNEL_QUOTA_EXHAUSTED`。
- 百家号时间选择失败诊断需包含当前三个控件值、当前可见下拉列表、目标时间和最终平台时间，便于复现虚拟下拉偏移。
- 百家号确认发布前需要等待草稿保存稳定，并对“点击速度太快”做重试和间隔保护。
- `BAIJIAHAO_PLATFORM_RATE_LIMITED`、`BAIJIAHAO_SCHEDULE_OPTION_NOT_FOUND`、`BAIJIAHAO_SCHEDULE_DIALOG_NOT_READY`、`BAIJIAHAO_PUBLISH_NOT_CONFIRMED` 应视为可重试失败。
- 固定异常码应覆盖封面弹窗未打开、封面上传入口/文件框缺失、封面上传超时、正文 UEditor 不可见、正文误入标题、时间下拉失败、平台频控、提交后未检测到审核中。

### 百家号人工验收样例

- 正常定时发布：标题、正文、封面均填充成功，平台返回“提交成功，正在审核中...”，作品管理页显示“审核中”和预计发布时间。
- 平台时间校正：输入距离当前不足 1 小时的计划时间，最终选择平台可用时间，排期记录中能看到平台实际时间。
- 封面上传：文件选择窗口关闭后，封面区域显示缩略图，后续定时发布按钮可点击。
- 时间下拉：分钟目标不在首屏可见选项时，助手能滚动或键盘选择到目标分钟。
- 平台频控：出现“点击速度太快”时任务不立即失败，应按百家号节流配置重试。
- 账号不可用：无 active 百家号账号、平台能力未启用、无到期任务三类场景应返回不同领取阻塞原因。
- 发布回查：计划时间前不确认发布，计划时间后以作品管理页的标题、状态、预计发布时间作为最终可信结果。
