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
- 平台已发布时，可人工确认到 `published_confirmed`。
- 平台未找到或状态不确定时，进入 `publish_unknown` 或 `publish_failed`，排期抽屉能显示异常原因。
- 取消已提交平台定时的排期时，先进入 `cancel_pending_platform`，平台确认后再变为 `cancelled`。

## 7. 排期健康可视化

- `pending` 且未来执行的任务显示为“待执行”。
- `filling`、`scheduling`、`checking_publish_result` 或锁定中的任务显示为“执行中”。
- `nextAttemptAt` 已过且未锁定的活跃任务显示为“超时”。
- `schedule_failed`、`publish_failed` 显示为“失败”。
- `manual_required`、`routed_to_semi_auto` 显示为“人工处理”。
- `scheduled` 显示为“已定时”。
- `publish_due`、`publish_unknown`、`cancel_pending_platform` 显示为“发布待确认”。
- 诊断弹窗应包含队列、请求、浏览器环境、平台排期 ID、失败码、失败消息和原始 diagnostics JSON。

## 8. 自动分发前置流程

- 后台定时任务只处理启用内容生成的 active 项目。
- 配额按渠道拆分，`self_media` 配额应分配到可用自媒体账号。
- 自媒体账号目标必须满足：账号启用、平台能力验证通过、绑定环境登录正常。
- 自动生成文章后，自媒体目标应创建 `self_media_publish_schedule`，不是普通站点发布任务。
- 不同平台或账号有多个可用目标时，应尽量均匀分配。
- 无可用自媒体目标时，自动分发 item 应失败并记录明确原因，不影响其他渠道。
