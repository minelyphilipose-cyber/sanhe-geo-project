# 自媒体自动化无状态扩展执行模型设计

日期：2026-07-03

## 1. 需求背景

自媒体自动分发链路已经从“单次人工触发填充”演进为“排期、自动领取、AdsPower 环境启动、扩展填充、平台定时发布、发布后回查、人工兜底”的完整自动化流程。随着本地助手、AdsPower 统一应用、扩展运行态上报和 claim gate 逐步落地，原有“扩展绑定品牌”的模型开始暴露出语义不清的问题。

当前讨论的核心目标是：

```text
扩展尽量无状态化。
扩展不再长期持有品牌身份。
身份识别由系统中已绑定的 AdsPower 浏览器环境、本地助手签名会话、后端品牌/账号/环境映射共同完成。
```

换句话说，扩展应从“品牌身份持有者”降级为“当前 AdsPower 环境内的页面执行器”。它负责识别页面、填充内容、回传执行结果；不负责决定自己属于哪个品牌，也不负责长期持有品牌级授权。

## 2. 项目背景

当前系统中，自媒体自动化相关的主要对象包括：

- `SelfMediaAccount`：品牌下的自媒体账号，例如今日头条、百家号、公众号等。
- `BrowserEnvironment`：AdsPower 浏览器环境，包含 `environmentKey`、`providerProfileId`。
- `BrowserEnvironmentAccount`：某个 AdsPower 环境中的某个平台账号绑定，包含期望账号名、平台账号 ID、登录状态。
- `ExtensionSession`：当前扩展绑定会话，包含 `brandId`、`operatorId`、`installId`、`environmentKey`、`providerProfileId`、`extensionVersion`。
- `LocalAgentSession`：本地助手配对会话，用 HMAC/C2 签名访问后端。
- `extension_runtime_status`：扩展运行态，上报扩展在某个 AdsPower 环境中的实际状态。
- `local_agent_runtime_status`：本地助手运行态，上报本机助手能力、AdsPower API 状态、容量、版本等。
- `self_media_publish_schedule`：具体排期和执行状态。

Phase 1 已经补上运行态闭环：

- 扩展上报 runtime status。
- 本地助手上报 runtime status。
- 后端聚合环境可用性。
- claim 前执行 runtime gate。
- 后台新增运行环境看板。

但 Phase 1 仍保留 `ExtensionSession` 的品牌绑定语义。也就是说，扩展一方面作为 AdsPower 环境里的执行器，另一方面又持有品牌级 token。这是后续需要逐步弱化的部分。

## 3. 当前业务背景

当前实际业务关系更接近：

```text
一个本地助手可以服务多个品牌。
一个品牌可以有多个自媒体账号。
一个自媒体账号绑定到一个或多个 AdsPower 浏览器环境账号。
扩展安装在 AdsPower 浏览器环境中。
真正能判断“当前能不能执行”的，是后端对品牌、账号、环境、助手、扩展运行态的综合判断。
```

因此，品牌身份不应该长期固化在扩展内。更合理的身份链条是：

```text
本地助手签名会话
  -> 后端可信 operator / helper session
  -> 本地助手打开指定 AdsPower profile
  -> providerProfileId / environmentKey 匹配 BrowserEnvironment
  -> BrowserEnvironmentAccount 匹配平台和账号
  -> 后端得到 brandId / selfMediaAccountId
  -> 扩展只执行本次任务
```

## 4. 当前实现中的扩展绑定模型

当前扩展绑定流程如下：

1. 后台为某个品牌生成绑定码。
2. 扩展手动输入绑定码，或通过本地助手自动绑定意图拿到绑定码。
3. 扩展调用：

```http
POST /api/v1/extension/bind
```

4. 后端消费绑定码，创建 `extension_session`。
5. `extension_session` 记录：

```text
brandId
operatorId
installId
environmentKey
providerProfileId
extensionVersion
extensionToken
```

6. 扩展后续调用后端时携带 `X-Extension-Token`。
7. 后端通过 token 反查 `ExtensionSession`，得到品牌和操作员身份。

这个模型的优点：

- 安全边界清晰，已有 token 鉴权。
- 扩展可以直接访问后端接口。
- 兼容既有手动绑定码和自动绑定意图。

这个模型的问题：

- 扩展变成品牌身份持有者，而不是单纯页面执行器。
- 同一个 AdsPower 环境的真实身份应该由环境账号绑定决定，扩展品牌绑定会形成第二套身份来源。
- 扩展升级、重装、统一应用分发后，运营容易把“扩展装上了”和“扩展已绑定品牌”混在一起。
- 后台页面中“品牌扩展绑定”“环境扩展状态”“账号登录状态”“本地助手状态”分散，排查路径不够直接。

## 5. 当前 A 品牌今日头条自动填充链路

以 A 品牌在系统中点击分发或创建今日头条排期为例，当前链路是：

### 5.1 后台创建排期

页面调用自媒体排期接口，例如：

```http
POST /api/content/self-media-schedules
POST /api/content/self-media-schedules/platform-quick-dispatch
POST /api/content/self-media-schedules/auto-create
```

后端创建 `self_media_publish_schedule`，记录：

```text
brand_id = A
platform = toutiao
self_media_account_id
browser_environment_id
browser_environment_account_id
planned_publish_at
platform_scheduled_at
queue_kind = schedule_execution
status = pending
```

### 5.2 本地助手领取任务

本地助手定时轮询：

```http
GET /api/v1/local-agent/self-media-schedules/claim-next?platform=toutiao
```

后端验证本地助手签名 session 后，查找可领取排期。当前候选排期会按 `created_by = 当前本地助手 operatorId` 过滤。

后端 claim 前会执行：

- 平台是否属于 AdsPower 自动化平台。
- 本地助手是否有容量。
- 运行态 gate：
  - helper online
  - helper capacity
  - AdsPower API ok
  - extension seen
  - extension fresh
  - account verified
  - version ok
  - capability ok
- 浏览器环境锁。

通过后，排期状态更新为：

```text
status = filling
runtime_stage = claimed
locked_until = now + lockMinutes
```

### 5.3 后端准备分发任务

后端调用 `prepareDistributionTaskForSchedule`，基于文章、自媒体账号和排期选项创建或复用 `DistributionTask`。

任务中会包含：

```text
article
selfMediaAccount
platformOptions.scheduleId
platformOptions.scheduledAt
platformOptions.platformScheduledAt
platformOptions.<platform capability options>
```

### 5.4 本地助手启动 AdsPower 环境

本地助手拿到 claim response 中的 launch 信息：

```text
taskId
platform = toutiao
publish url
selfMediaAccountId
browserEnvironmentAccountId
expectedAccountName
expectedPlatformAccountId
environmentKey
providerProfileId
environmentName
```

然后：

1. 调用 AdsPower Local API 启动 `providerProfileId` 对应的浏览器环境。
2. 使用 Puppeteer 打开今日头条发布页。
3. 把 runtime task 存到本地助手任务列表。

### 5.5 扩展执行填充

扩展运行在 AdsPower 浏览器环境中。它从本地助手读取任务，拿到后：

1. 进行平台账号身份预检。
2. 使用扩展 token 调用：

```http
POST /api/v1/extension/fill-token/consume
```

3. 后端返回填充 payload，包括标题、正文、封面、定时时间等。
4. 扩展向 content script 发送：

```text
GEO_ENV_FILL_TASK
```

5. content script 操作今日头条页面，完成内容填充、封面上传、定时设置、提交。
6. 扩展调用后端 ack 或失败接口。

### 5.6 后端更新结果

成功时：

- 填充成功：`filled_verified`
- 定时提交成功：`scheduled`
- 发布成功或回查确认：`published_confirmed`

失败时：

- 可重试：回到 `pending`
- 不可自动执行：进入 `manual_required`
- 发布后回查异常：进入 `publish_unknown` 或人工确认链路

## 6. 当前链路的关键矛盾

当前链路中有两套身份来源：

### 6.1 扩展身份

来自 `ExtensionSession`：

```text
extensionToken -> extension_session -> brandId/operatorId
```

### 6.2 环境身份

来自 AdsPower 环境和系统配置：

```text
providerProfileId/environmentKey -> BrowserEnvironment
BrowserEnvironment + platform -> BrowserEnvironmentAccount
BrowserEnvironmentAccount -> brandId/selfMediaAccountId
```

业务上真正可靠的是第二套环境身份。因为平台账号登录状态、AdsPower profile、品牌自媒体账号绑定都在这套模型里。

扩展身份更适合作为“临时兼容授权”，不适合作为长期的业务身份来源。

## 7. 目标模型：无状态扩展执行器

目标模型中，扩展不再长期绑定品牌。扩展只保留安装实例和运行能力信息：

```text
installId
extensionVersion
protocolVersion
capabilities
currentUrl
detectedPlatform
detectedAccountName
detectedPlatformAccountId
runtimeStage
lastError
```

品牌和账号身份由后端根据本地助手和 AdsPower 环境确定：

```text
localAgentSessionId
operatorId
providerProfileId
environmentKey
browserEnvironmentId
browserEnvironmentAccountId
platform
detectedAccount
```

### 7.1 职责边界

后端负责：

- 维护品牌、自媒体账号、AdsPower 环境、环境账号绑定。
- 判断哪个任务应该下发到哪个 AdsPower 环境。
- 校验本地助手签名和任务授权。
- 校验当前环境账号是否匹配。
- 生成短期任务授权。
- 接收执行结果和诊断。

本地助手负责：

- 和后端建立可信 C2/HMAC session。
- 启动指定 AdsPower profile。
- 确认当前打开的 profile 是后端要求的 `providerProfileId`。
- 把当前任务上下文提供给扩展。
- 作为扩展和后端之间的可信桥梁。

扩展负责：

- 在页面内识别平台和账号。
- 执行填充、上传、设置定时、提交。
- 上报运行态和执行结果。
- 不长期保存品牌 token。

## 8. 目标链路

无状态扩展模型下，A 品牌今日头条任务链路应变为：

1. 后端选择 A 品牌今日头条账号绑定的 `BrowserEnvironmentAccount`。
2. 后端确认该账号绑定到某个 `BrowserEnvironment.providerProfileId`。
3. 本地助手用自身签名领取任务。
4. 后端返回 launch payload 和短期任务授权。
5. 本地助手启动指定 AdsPower profile。
6. 扩展在该 profile 内启动或被唤起。
7. 扩展向本地助手请求当前任务上下文。
8. 本地助手确认当前 profile 和 task 匹配后，把短期 task token 或签名上下文交给扩展。
9. 扩展使用短期授权消费 fill payload。
10. 扩展填充页面并回传结果。
11. 后端按 schedule/task 更新状态。

关键变化：

```text
旧：extensionToken 决定扩展属于哪个品牌。
新：helper session + providerProfileId + environment account 决定当前任务属于哪个品牌。
```

## 9. 安全模型

无状态扩展不能等于无鉴权。需要把长期品牌 token 换成短期任务授权。

建议引入：

### 9.1 短期任务 token

由后端签发，绑定：

```text
taskId
scheduleId
brandId
selfMediaAccountId
browserEnvironmentId
browserEnvironmentAccountId
providerProfileId
platform
localAgentSessionId
expiresAt
nonce
```

用途：

- 扩展消费 fill payload。
- 扩展回传 ack/failure。
- 扩展上报任务阶段。

约束：

- TTL 建议 5-15 分钟。
- 只能用于指定 task/schedule。
- 只能用于指定 AdsPower profile。
- 失败或任务完成后失效。

### 9.2 本地助手签名上下文

本地助手继续使用当前 C2/HMAC session。后端信任的是：

```text
localAgentSession -> operatorId -> allowed task scope
```

扩展不直接持有 helper secret。扩展如果需要访问后端，应使用后端签发的短期 task token，而不是 helper HMAC secret。

### 9.3 环境账号强校验

任务执行前必须确认：

```text
providerProfileId matches BrowserEnvironment
platform matches BrowserEnvironmentAccount
detected account matches expected account
loginStatus = verified
```

未 verified 时不应下发真实填充 payload，除非进入人工调试模式。

## 10. 数据模型调整建议

### 10.1 保留但弱化 `ExtensionSession`

短期内不删除 `ExtensionSession`。它仍用于：

- 兼容旧扩展。
- 手动绑定码兜底。
- token refresh 兼容。
- 老接口访问。

但新语义应逐步从“品牌绑定 session”弱化为：

```text
extension install/session compatibility record
```

### 10.2 强化 `extension_runtime_status`

运行态应成为扩展主要身份记录：

```text
providerProfileId + installId
browserEnvironmentId
browserEnvironmentAccountId
platform
detectedPlatform
detectedAccountName
detectedPlatformAccountId
loginStatus
capabilitiesJson
lastSeenAt
```

业务路由优先使用：

```text
browserEnvironmentId + platform
```

而不是：

```text
extensionSession.brandId
```

### 10.3 新增短期任务授权记录

可以新增轻量表，也可以先用 Redis。

建议字段：

```text
token_id
task_id
schedule_id
brand_id
self_media_account_id
browser_environment_id
browser_environment_account_id
provider_profile_id
platform
local_agent_session_id
issued_at
expires_at
consumed_at
status
```

第一阶段建议优先 Redis，降低表结构变更成本。

## 11. API 调整建议

### 11.1 新增本地助手签发任务上下文

```http
POST /api/v1/local-agent/self-media-schedules/{scheduleId}/task-token
```

认证：

- 本地助手 HMAC。

后端校验：

- schedule 已被当前 local agent session claim。
- schedule browser environment 与 launch profile 一致。
- schedule 状态允许执行。

返回：

```json
{
  "taskToken": "...",
  "expiresAt": "...",
  "task": {...},
  "environment": {...},
  "expectedAccount": {...}
}
```

### 11.2 新增扩展短期任务接口

```http
POST /api/v1/extension/tasks/{taskId}/fill-token/consume
POST /api/v1/extension/tasks/{taskId}/ack
POST /api/v1/extension/tasks/{taskId}/failed
POST /api/v1/extension/tasks/{taskId}/runtime-stage
```

认证：

- `X-Task-Token`

不再要求：

- `X-Extension-Token`

兼容期可同时支持两种认证。

### 11.3 runtime-status 上报路径

扩展 runtime status 可以有两种选择：

1. 继续使用 extension token 上报，兼容旧链路。
2. 改为通过本地助手转发上报，由本地助手签名，后端按 `providerProfileId + installId` upsert。

目标状态建议使用第 2 种：

```text
extension -> local helper -> backend
```

这样后端不需要信任扩展长期身份。

## 12. 页面功能调整建议

### 12.1 品牌详情页

当前品牌详情页中存在“扩展绑定状态”“生成绑定码”“打开默认环境绑定”等功能。建议调整：

- 保留绑定码入口，但降级为“异常恢复/兼容旧扩展”。
- 主入口改为“账号环境自动化状态”。
- 不再把扩展状态描述为品牌级在线/离线。
- 显示维度改为：

```text
平台账号
AdsPower 环境
扩展最近上报
账号登录状态
本地助手观测状态
准入状态
```

### 12.2 自媒体运行环境页

此页应成为自动化排查主入口。

建议强化：

- 增加品牌筛选、平台筛选、账号筛选。
- 展示 `providerProfileId`、环境、平台账号、扩展版本、助手版本、账号 verified 状态。
- 展示“观测准入”，而不是绝对“可接任务”。
- 增加“身份来源”说明：

```text
由 AdsPower 环境绑定、平台账号校验、本地助手上报共同判断。
```

### 12.3 本地助手页面

本地助手不应被呈现为品牌专属绑定。建议展示为全局 worker：

```text
machineId
activeProfile
operator
helperVersion
adspowerApiOk
capacity
runningTaskCount
supportedPlatforms
recentBrands
lastSeenAt
```

### 12.4 扩展绑定入口

建议从“生成品牌绑定码”调整为两层：

主入口：

```text
打开账号环境并自动识别扩展
```

兜底入口：

```text
生成兼容绑定码
```

运营侧不应优先理解“扩展绑定品牌”，而应理解“这个账号环境能不能自动执行”。

## 13. 迁移方案

### Phase A：兼容期，保留 ExtensionSession

目标：

- 不破坏当前成功路径。
- 保持扩展 token 可用。
- 开始引入 task token 设计。

工作：

- 文档确认无状态模型。
- 看板文案从“品牌扩展绑定”转向“环境运行态”。
- 后端继续支持 extension token。
- 新增短期 task token 的接口草案和测试。

### Phase B：任务执行改走短期 token

目标：

- fill payload 和 ack 不再依赖品牌扩展 token。

工作：

- 本地助手领取任务后请求 task token。
- 扩展从本地助手拿 task token。
- 扩展用 task token 消费 payload 和上报结果。
- 后端校验 task token 绑定的 schedule/environment/platform。

### Phase C：runtime status 改由本地助手转发

目标：

- 扩展运行态不再依赖长期 extension token。

工作：

- 扩展把运行态发给本地助手。
- 本地助手签名转发到后端。
- 后端按 `providerProfileId + installId` upsert。
- 后端不再要求 runtime status 必须有 `ExtensionSession`。

### Phase D：品牌绑定码降级

目标：

- 绑定码只作为异常恢复手段。

工作：

- 页面降低绑定码入口优先级。
- 统一应用安装/升级后，主要通过运行态看板验证扩展可用。
- ExtensionSession 仅作为旧版本兼容和人工恢复记录。

## 14. 风险和约束

### 14.1 不能信任扩展自报 profile

扩展可以上报 `providerProfileId`，但最终应由本地助手确认当前启动的 AdsPower profile。否则扩展自报存在伪造风险。

### 14.2 不能把无状态理解为无认证

扩展无状态后，必须有短期 task token 或等价机制。不能让扩展匿名拉取填充 payload。

### 14.3 账号 verified 是硬门槛

对于自动发布任务，`logged_in` 不等于 `verified`。只有匹配到期望账号的 verified 状态，才允许执行真实填充和提交。

### 14.4 保持旧链路兼容

当前生产已有旧扩展和本地助手。不能一次性移除：

- `/api/v1/extension/bind`
- extension token refresh
- extension token 访问 fill-token
- 品牌绑定码兜底

## 15. 成功标准

这次改造完成后，应能回答以下问题：

1. 某个品牌的某个平台账号绑定在哪个 AdsPower 环境？
2. 该环境里的扩展是否安装、版本是否正确、最近是否上报？
3. 当前登录的平台账号是否就是系统期望账号？
4. 当前本地助手是否能启动 AdsPower，并有容量执行任务？
5. 某个任务的 payload 是不是只下发给被 claim 的环境和短期 token？
6. 扩展重装或升级后，是否无需重新理解品牌绑定，只看环境运行态即可判断可用？
7. 旧扩展是否还能通过兼容路径继续执行？

## 16. 当前建议结论

建议确认这个方向：

```text
扩展无品牌状态化是目标方向。
本地助手和 AdsPower 环境是执行身份的可信入口。
后端 BrowserEnvironmentAccount 是品牌/账号身份的最终来源。
ExtensionSession 保留为兼容层和异常恢复层，不再作为长期业务身份核心。
```

短期不要直接删除 `ExtensionSession`。下一步应先做短期 task token 设计和接口草案，再逐步把填充 payload、ack、runtime status 从 extension token 迁移到 helper session + task token 模型。
