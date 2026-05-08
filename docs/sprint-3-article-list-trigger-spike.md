# Sprint 3 Article List Trigger Spike

## 背景

当前半自动 fill 触发链路是：

1. 运营在后台创建 article。
2. 运营在自媒体分发入口创建 SEMI_AUTO 分发任务。
3. 运营打开 Chrome 工具栏里的扩展 popup。
4. 运营在 popup 任务列表点击任务。
5. 扩展打开目标平台编辑器 tab 并自动填表。
6. 运营人工点击发布。

目标链路是：

1. 运营在 article 列表页看到每篇文章可分发的平台/账号按钮。
2. 点击“打开头条编辑器”或“打开知乎编辑器”。
3. 后台必要时创建 SEMI_AUTO 任务。
4. 后台通过页面内 postMessage 触发扩展。
5. 扩展打开编辑器 tab 并自动填表。
6. 运营人工点击发布。

核心目标是减少“后台 -> 扩展 popup -> 编辑器 tab”的上下文切换，同时保留人工发布合规底线。

## 总体结论

采用以下主线方案：

- article 列表按钮按平台聚合，点击后下拉选择账号。
- 点击按钮即创建或复用 SEMI_AUTO 分发任务。
- cookie 状态由后端批量接口提供，使用本地凭证元数据判定，不实时探测目标平台。
- 系统后台页面通过 `window.postMessage` 与扩展注入的 content script 通信。
- popup 保留为 fallback 和全局任务视图。
- 扩展未安装时页面加载后异步探测并禁用按钮。
- 批量场景先保持单任务串行，后台按钮层面禁止并发启动。

## Q1：Article 列表页的按钮聚合逻辑

### 选择

选择候选 B：按平台聚合，一个平台一个按钮，点击后下拉选择具体账号。

示例：

```text
文章标题 | [头条 ▾] [知乎 ▾]

头条下拉：
- AI向善推广大使（cookie 有效）
- 品牌头条号2（未捕获凭证）

知乎下拉：
- 品牌知乎号A（cookie 将过期）
```

### 理由

每个账号一个按钮在多账号品牌下会迅速撑爆列表行，尤其 article 列表还有状态、作者、更新时间、操作列等信息。按平台聚合保留了运营心智中的“我要发到头条/知乎”，账号选择放到第二层，信息密度更可控。

只显示已有未完成任务的按钮也不合适。目标是从 article 列表直接发起分发，不能要求运营先去其他页面创建任务。

### UI 规则

每行最多显示已支持平台按钮：

- `头条 ▾`
- `知乎 ▾`

按钮状态按该平台下账号综合状态展示：

- 至少一个账号 cookie 有效：按钮主色，可点击。
- 全部账号未捕获或过期：按钮弱化，仍可点击，展开后引导捕获。
- 没有该平台账号：按钮不显示，或在“更多平台”中显示“未配置账号”。

下拉项展示字段：

- 账号名称。
- 凭证状态标签。
- 最近捕获时间。
- 将过期提示。

### 交互

点击平台按钮：

1. 如果该平台只有一个账号，直接进入该账号动作。
2. 如果有多个账号，打开账号下拉。
3. 如果没有账号，提示到品牌资产配置维护自媒体账号。

## Q2：分发任务的创建时机

### 选择

选择候选 Y：点按钮即创建。后端发现 `articleId × accountId` 没有可复用任务时，自动创建一个 SEMI_AUTO task，然后触发 fill。

### 理由

这个方案最符合目标体验：运营在 article 列表页点“打开头条编辑器”，系统就应该完成任务创建和扩展触发，不需要再去自媒体分发弹窗或扩展 popup。候选 X 只是把 popup 点击换成后台点击，仍保留“先创建任务”的额外步骤。候选 Z 会在 article 创建时批量产生大量可能不用的任务，污染任务表和审计链路。

### 后端行为

新增一个面向后台 UI 的启动接口：

```http
POST /api/content/articles/{articleId}/self-media-fill/start
```

请求：

```json
{
  "accountId": 456,
  "requestId": "uuid-v4"
}
```

响应：

```json
{
  "taskId": 789,
  "articleId": 274,
  "accountId": 456,
  "platform": "toutiao",
  "status": "token_issued",
  "created": true,
  "extensionCommand": {
    "type": "GEO_START_FILL",
    "requestId": "uuid-v4",
    "taskId": 789,
    "platform": "toutiao"
  }
}
```

### 任务复用规则

接口先查同一 `articleId × accountId × operatorId × dispatch_mode=SEMI_AUTO` 下可复用任务：

- `token_issued`：复用 task，但重新签发新的 fill token 后再触发 fill；旧 token 不复用，让它按 TTL 自然失效。fill token 是一次性消费设计，页面入口不能假设历史 token 仍可用。
- `filling`：返回当前任务，提示正在填充。
- `filled`：可复用但需要用户确认“重新打开编辑器继续处理”；默认不自动重新 fill。
- `published`：默认不复用，新建二次分发任务需要明确确认。
- `failed`：可创建新任务。
- `expired/reclaimed`：可创建新任务。

建议新增唯一幂等键：

```text
request_id + operator_id
```

前端每次按钮点击生成 `requestId`，避免双击创建重复任务。

### 对 D3a 的影响

可以复用现有 `ContentDistributionService.distributeTo(articleId, SelfMediaTarget)` 创建 SEMI_AUTO task 的逻辑，但建议包一层新的 application service，例如 `ArticleSelfMediaFillStartService`：

- 负责查找可复用任务。
- 负责账号/cookie 状态校验。
- 负责调用现有分发创建逻辑。
- 负责返回扩展命令 payload。

这样不会把 article 列表 BFF 逻辑塞进 `ContentDistributionService`。

## Q3：Cookie 状态如何展示给运营

### 状态定义

状态分为 6 类：

| 状态 | 判定 | UI 文案 | 点击行为 |
| --- | --- | --- | --- |
| `VALID` | 有 ACTIVE credential，且未到本地过期阈值 | 打开头条编辑器 | 创建/复用任务并触发扩展 fill |
| `EXPIRING_SOON` | 有 ACTIVE credential，距离本地过期阈值小于 3 天 | 打开头条编辑器，凭证将过期 | 仍允许 fill，同时提示建议重新捕获 |
| `EXPIRED` | 有 credential，但本地过期阈值已过 | 先登录头条 | 打开目标平台登录页，引导扩展捕获 |
| `MISSING` | 从未捕获 credential | 先捕获凭证 | 生成绑定码或复用已绑定扩展，引导捕获 |
| `UNBOUND_EXTENSION` | 后台探测不到扩展 | 安装/启用扩展 | 弹安装提示，不创建任务 |
| `UNKNOWN` | 后端状态查询失败 | 状态未知 | 禁用或允许刷新重试，不自动创建任务 |

### UI 呈现

平台按钮本体展示平台级聚合状态：

- `头条 ▾`：至少一个账号 `VALID`。
- `头条 需登录 ▾`：所有账号都不可直接 fill。
- `头条 将过期 ▾`：至少一个账号 `EXPIRING_SOON` 且没有更严重阻断。

账号下拉项展示细节：

```text
AI向善推广大使    已登录
品牌头条号2       未捕获凭证
品牌头条号3       凭证将于 2026-05-10 过期
```

### 点击行为

`VALID`：

1. 调 start 接口。
2. 收到 taskId。
3. postMessage 给扩展。
4. 按钮进入“正在打开编辑器”。

`EXPIRING_SOON`：

1. 同 `VALID`。
2. 额外 toast：凭证即将过期，发布后建议重新捕获。

`EXPIRED` / `MISSING`：

1. 如果扩展未绑定，先生成绑定码并提示打开扩展绑定。
2. 如果扩展已绑定，打开平台登录页。
3. 提示在扩展中捕获该账号凭证。

`UNBOUND_EXTENSION`：

1. 不创建任务。
2. 展示安装/加载扩展说明和下载链接。

## Q4：Cookie 状态查询接口

### 选择

使用批量查询接口，不逐行逐账号查。

### 接口设计

```http
POST /api/content/articles/self-media-cookie-status/batch
```

请求：

```json
{
  "articleIds": [274, 275, 276],
  "platforms": ["toutiao", "zhihu"]
}
```

响应：

```json
{
  "items": [
    {
      "articleId": 274,
      "brandId": 8,
      "accounts": [
        {
          "accountId": 456,
          "platform": "toutiao",
          "accountName": "AI向善推广大使",
          "credentialStatus": "VALID",
          "lastCapturedAt": "2026-05-08T10:00:00",
          "expiresAt": "2026-05-15T10:00:00",
          "daysUntilExpiry": 7,
          "canStartFill": true,
          "reason": null
        }
      ]
    }
  ]
}
```

### 批量规模

列表页默认 50 行文章。接口应接受最多 50 个 `articleIds`，后端按 article 所属 brand 聚合查询账号和 credential。

后端查询策略：

1. 查询 article -> project -> brand。
2. 校验当前 operator 对这些 brand 有 `OPERATE`。
3. 查询这些 brand 下 platform in 请求范围的 self_media_account。
4. 查询这些 account 当前 ACTIVE credential 最新版本。
5. 拼装每个 article 可用账号状态。

### Cookie 有效性判定策略

选择“本地元数据判定”，不实时访问头条/知乎试探。

判定依据：

- `credential.status = ACTIVE`
- `credential.captured_at`
- 平台默认 TTL 配置，例如头条 7 天、知乎 14 天。
- 可选读取 `credential.expires_at` 字段；如果当前表没有，先用 `captured_at + platformTtlDays` 计算。

不选择实时试探的理由：

- article 列表页是高频页面，实时访问第三方会慢且不稳定。
- 第三方登录态试探本身可能触发风控。
- 准确性仍不能保证，因为 cookie 可能被服务端主动踢下线。

策略取舍：

- 列表状态是“预测状态”。
- 真正有效性以后续 fill token consume 和目标平台打开后的登录态表现为准。
- 如果 fill 失败或打开后仍未登录，提示重新捕获，并更新本地 credential 状态为 `EXPIRED` 或 `INVALID`。

### BrandAccess 校验

接口是后台页面使用，必须按 article 所属 brand 校验：

- article 列表可读：`project.read` + partner resource access。
- 状态查询涉及可操作账号：`BrandAccessAction.OPERATE`。

响应只返回账号展示字段和 credential 元数据，不返回 cookie、token、credentialId 的敏感细节。

## Q5：扩展与系统后台的 postMessage 通信协议

### 选择

扩展 content script 注入系统后台域名，后台页面通过 `window.postMessage` 发起命令。content script 做 origin 校验后转发给 service worker。

### Manifest 变更

扩展增加后台系统域名 content script matches，并按构建模式生成：

```json
{
  "matches": ["https://admin.example.com/*"],
  "js": ["assets/admin-bridge-content-script.js"],
  "run_at": "document_start"
}
```

PROD build 只生成生产管理域名。DEV/staging build 才生成 `http://127.0.0.1:5173/*`、`http://localhost:5173/*` 和 staging 域名。生产域名应来自构建配置，不硬编码在业务代码里。

### Origin 白名单

扩展内维护白名单，必须按构建模式分离：

```ts
const ALLOWED_ADMIN_ORIGINS = import.meta.env.PROD
  ? new Set([
      'https://admin.example.com',
    ])
  : new Set([
      'http://127.0.0.1:5173',
      'http://localhost:5173',
      'https://staging.example.com',
    ])
```

规则：

- content script 只接收 `window === event.source` 的消息。
- 必须校验 `event.origin` 在白名单。
- 必须校验 `event.data?.channel === 'GEO_EXTENSION_BRIDGE'`。
- 所有消息带 `requestId`，用于前端匹配响应。
- PROD build 的 origin 白名单只包含生产管理域名，不能包含 `localhost` 或 `127.0.0.1`。否则运营浏览器安装扩展后，攻击者可诱导访问本地伪造页面并通过 `postMessage` 触发 fill。
- DEV/staging build 才允许 `localhost`、`127.0.0.1` 和 staging 域名。
- `manifest.json` 的 `content_scripts.matches` 必须同样按构建模式生成：PROD 只注入生产管理域名，DEV/staging 才注入本地域名和 staging 域名。

### 消息类型

#### GEO_PING

后台 -> 扩展。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_PING",
  "requestId": "uuid-v4",
  "payload": {
    "minVersion": "0.1.0"
  }
}
```

#### GEO_PONG

扩展 -> 后台。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_PONG",
  "requestId": "uuid-v4",
  "payload": {
    "installed": true,
    "extensionVersion": "0.1.0",
    "bound": true
  }
}
```

#### GEO_START_FILL

后台 -> 扩展。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_START_FILL",
  "requestId": "uuid-v4",
  "payload": {
    "taskId": 789,
    "articleId": 274,
    "accountId": 456,
    "platform": "toutiao"
  }
}
```

扩展收到后：

1. 校验已绑定。
2. 校验当前没有 activeTask。
3. 调现有 B5a fill flow。
4. 推送状态。

#### GEO_FILL_STATUS

扩展 -> 后台。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_FILL_STATUS",
  "requestId": "uuid-v4",
  "payload": {
    "taskId": 789,
    "status": "opening_editor",
    "message": "正在打开头条编辑器"
  }
}
```

状态枚举：

- `accepted`
- `issuing_fill_token`
- `consuming_fill_token`
- `setting_cookies`
- `opening_editor`
- `filling_editor`
- `filled`
- `published`
- `stopped`

#### GEO_FILL_ERROR

扩展 -> 后台。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_FILL_ERROR",
  "requestId": "uuid-v4",
  "payload": {
    "taskId": 789,
    "code": 70012,
    "message": "任务状态已变化，请刷新任务列表后确认最新状态。"
  }
}
```

#### GEO_CAPTURE_REQUIRED

扩展 -> 后台，可选。

```json
{
  "channel": "GEO_EXTENSION_BRIDGE",
  "type": "GEO_CAPTURE_REQUIRED",
  "requestId": "uuid-v4",
  "payload": {
    "accountId": 456,
    "platform": "toutiao",
    "reason": "COOKIE_MISSING"
  }
}
```

### 扩展未安装探测

页面加载后发送 `GEO_PING`，等待 800ms。

- 收到 `GEO_PONG`：标记扩展可用。
- 未收到：标记未安装或未启用。

不阻塞 article 列表渲染。列表先显示骨架/普通按钮，探测完成后刷新按钮状态。

### 状态反馈频率

选择“每次状态变化 push”。不做定时拉取。

理由：

- fill flow 状态点少。
- 定时拉取会引入额外后台接口和轮询复杂度。
- 当前扩展 service worker 已有 lifecycle 事件机制，可以复用。

## Q6：与现有 popup 流程的关系

### 选择

选择候选 P：popup 保留，作为 fallback 和全局任务视图。

### 理由

popup 是当前扩展绑定、凭证捕获、任务列表的兜底入口。后台直连扩展依赖 content script 注入系统后台域名；如果后台页面没打开、域名配置错误、扩展桥接失败，popup 仍然能完成任务。

完全废弃 popup 风险过高，尤其本地开发、线 B 联调和运营排障都需要一个全局可见的扩展状态面板。

### 对现有代码影响

B3 任务列表：

- 保留。
- 增加“由后台发起”的任务状态展示即可。

B5a fill flow：

- 尽量复用。
- 抽出 `startFillByTaskId(taskId)` 或 `startFillFromCommand(command)`，让 popup 点击和后台 bridge 共用同一条 service worker 流程。

B5b lifecycle：

- 保持单例 activeTask。
- 状态事件同时通知 popup 和后台 bridge。

popup UI：

- 短期不删任务列表。
- 可以在 Sprint 3 后期弱化任务列表，把常用动作迁到后台页面。

## Q7：扩展未安装的兜底

### 选择

选择候选 N：页面加载时探测扩展，没装时按钮变灰并提示“请先安装扩展”，提供下载链接。

### 探测机制

后台页面加载后：

1. 创建 `requestId`。
2. `window.postMessage(GEO_PING)`。
3. 启动 800ms timeout。
4. 如果收到 `GEO_PONG`，写入前端状态 `extensionInstalled=true`。
5. 如果超时，写入 `extensionInstalled=false`。

### 不阻塞列表渲染

article 列表正常先渲染。平台按钮初始进入 `checking_extension` 状态：

```text
头条 检测扩展中...
```

800ms 内完成探测后切换：

- 已安装：展示账号 cookie 状态。
- 未安装：按钮禁用，展示“安装扩展”。

### 重试策略

提供两种重试：

- 用户点击“重新检测扩展”。
- 页面重新获得焦点时，如果上次探测失败超过 30 秒，自动再探测一次。

### 下载链接

未安装时展示：

```text
请先安装三合星链自媒体助手扩展
[下载扩展] [安装说明] [重新检测]
```

下载链接由后端配置返回或前端环境变量提供，不硬编码固定地址。

## Q8：批量场景与并发限制

### 选择

选择候选 L：禁止批量。一个任务从启动到 published/stopped 之前，其他 article 行的 fill 按钮全局禁用。

### 理由

当前 B5b 是单例 activeTask 架构，service worker 的 heartbeat、published 上报、tab 关闭监听都围绕单个任务设计。强行支持多个独立 tab 会重写 lifecycle 管理、storage schema、alarm payload 和 popup/后台状态映射，超出 Sprint 3 直接落地范围。

候选 J 的队列看似温和，但对运营也不直观：连续点 10 个按钮后，编辑器 tab 顺序打开，用户很难知道当前正在处理哪篇文章。候选 L 最清晰：一个任务处理完再做下一个。

### UI 规则

当 activeTask 存在：

- 当前任务按钮显示“正在填充/等待发布”。
- 其他行按钮禁用。
- 顶部显示全局横幅：

```text
正在处理《文章标题》到头条。完成发布或关闭任务后可继续处理其他文章。
```

### 对现有代码影响

service worker：

- 继续使用单例 activeTask。
- 增加后台 bridge 启动时的 activeTask 检查。
- 如果已有 activeTask，返回 `GEO_FILL_ERROR`，code 可复用 `70012` 或新增前端本地错误码。

后台 UI：

- 维护 `activeFillTask` 状态。
- 从扩展 `GEO_FILL_STATUS` / `GEO_FILL_ERROR` 更新按钮禁用状态。

后端：

- 状态机不需要支持并发扩展任务。
- 仍靠 conditional UPDATE 防并发状态覆盖。

## 不在本方案范围内

以下内容不展开：

- cookie 加密机制，沿用 B4。
- fill 行为人类化，进入 `S3-COOKIE-2` spike。
- 扩展自托管 CRX 分发，进入 `S3-EXT-DIST`。
- 风控规避，按业务侧合规约定处理。

## PR 拆分建议

| PR | 范围 | 预估 LOC | 依赖 |
| --- | --- | ---: | --- |
| B7a | 后台 article 列表 BFF：批量 cookie 状态接口 + start fill 接口 | 500-700 | D3a/B4/B5a |
| B7b | 扩展 admin bridge：content script 注入后台域名 + postMessage 协议 + service worker 入口复用 fill flow | 500-700 | B7a |
| B7c | 后台 article 列表 UI：平台聚合按钮、账号下拉、扩展探测、状态反馈 | 600-800 | B7a/B7b |
| B7d | 联调修复：错误文案、状态恢复、重复点击幂等、线 B selector 补齐 | 300-500 | B7b/B7c |

### B7a 细分

后端新增：

- `POST /api/content/articles/self-media-cookie-status/batch`
- `POST /api/content/articles/{articleId}/self-media-fill/start`

复用：

- `ContentDistributionService.distributeTo(...)`
- `ExtensionTaskListService`
- `CredentialVaultService` 元数据查询能力

新增测试：

- 批量 article 查询不会 N+1。
- 无 BrandAccess 的 brand 不返回账号。
- `VALID/EXPIRING_SOON/EXPIRED/MISSING` 状态判定。
- start 接口创建新任务。
- start 接口复用 `token_issued` 任务时重新 issue 新 token，旧 token 自然失效。
- start 接口双击 requestId 幂等。

### B7b 细分

扩展新增：

- `admin-bridge-content-script.ts`
- bridge message types。
- service worker 处理 `GEO_START_FILL`。
- activeTask 冲突响应。

复用：

- B5a `startFill` 主流程。
- B5b lifecycle 状态事件。

新增测试：

- origin 不在白名单时忽略。
- PROD build 不接受 `localhost` / `127.0.0.1` origin 的 `postMessage`。
- `GEO_PING` 返回 `GEO_PONG`。
- `GEO_START_FILL` 转发 service worker。
- activeTask 存在时拒绝第二个任务。
- 错误状态 postMessage 回后台页面。

### B7c 细分

后台 UI 新增：

- article 列表操作列平台聚合按钮。
- 账号下拉。
- cookie 状态标签。
- 扩展安装探测状态。
- active fill 全局横幅。

新增测试：

- 无扩展时按钮禁用。
- cookie 有效时按钮可点击。
- 未捕获时引导捕获。
- 多账号下拉选择正确 accountId。
- activeTask 时其他按钮禁用。

## 已知风险和待验证项

### 真实平台 cookie 准确性

本方案用本地元数据判定 cookie 有效性。真实平台可能提前踢掉登录态，列表页仍显示“已登录”。需要在线 B 联调中记录误判比例，并在 fill 失败后回写 credential 状态。

### 后台域名注入范围

扩展 content script 注入后台系统域名，必须严格限制 origin。生产、staging、dev 域名要由构建配置统一生成，避免把泛域名写进 manifest。

### Extension ID 分发

postMessage 方案不依赖固定 extensionId，这是优势。后台页面只需要 content script 在同页面存在即可。但如果未来改用 `chrome.runtime.sendMessage(extensionId)`，就必须解决不同安装来源 extensionId 不一致的问题。

### Service Worker 休眠

B5b 已把 heartbeat 迁到 alarms/session storage。后台直触发 fill 仍要验证 service worker 被消息唤醒后能恢复 activeTask，并把状态推回后台页面。

### 单任务限制的运营接受度

本方案禁止批量并发。需要运营确认：文章列表页连续处理多篇文章时，一个个打开编辑器是否可接受。如果强需求是批量队列，应该另开 B8 重构 lifecycle 为队列模型。

### 任务复用语义

`filled` 任务是否允许重新打开编辑器，需要产品确认。建议默认不自动复用，避免覆盖运营在目标平台编辑器里手动修改过的内容。

### 审计表锁等待

近期联调暴露 `audit_log` 锁等待会阻塞同步链路。B7a 新接口必须避免新增同步 audit。任务创建、fill token issue 等已经改为异步审计，但新 BFF 入口也要遵守这个约束。
