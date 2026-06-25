# GEO Local Helper PoC

这个目录是指纹浏览器方案的本地助手 PoC，只用于验证链路：

后台任务 -> 本地助手 -> AdsPower Local API -> 指定浏览器环境 -> 环境内 PoC 扩展领取任务并填充。

它不会上传 cookie，也不会写入生产扩展。

## 准备

1. 安装 Node.js 18+。
2. 在本目录执行：

```powershell
npm install
Copy-Item config.example.json config.local.json
```

3. 编辑 `config.local.json`，只保留本地服务、可信后台与 C2 开关等技术配置：

```json
{
  "host": "127.0.0.1",
  "port": 17891,
  "helperToken": "",
  "trustedBackendBase": "https://www.huanjingaigeo.com",
  "enableLegacyBackendTokenRoutes": false,
  "enableStaticHelperToken": false,
  "allowedOrigins": [
    "http://127.0.0.1:17891",
    "http://localhost:17891",
    "https://www.huanjingaigeo.com"
  ],
  "adspower": {
    "apiBase": "http://localhost:50325"
  }
}
```

如果生产后台页面报 `blocked by CORS policy` 或 `loopback address space`，优先确认同事本机助手已更新到最新包，并重启 `npm start`。新版助手会自动允许 `https://www.huanjingaigeo.com` 并响应浏览器 Private Network Access 预检所需的 `Access-Control-Allow-Private-Network`。

`helperToken` 默认禁用。v1 主链路只使用 C2 配对后的签名请求；只有临时回归旧 PoC 时，才同时配置随机 `helperToken` 并把 `enableStaticHelperToken` 改为 `true`。

AdsPower API Key 不写入 `config.local.json`。启动本地助手后在 `http://127.0.0.1:17891/` 页面保存 AdsPower API Key；AdsPower Local API 地址默认是 `http://localhost:50325`，通常无需修改。配置会保存在本机 `runtime/settings.json`。

本地助手不保存品牌、自媒体账号、环境标识与 AdsPower 环境 ID 的业务映射。`providerProfileId` 由后台品牌详情中的指纹浏览器环境绑定关系传入；运营只在后台页面配置环境关系，不需要编辑 `config.local.json`。

## 启动

```powershell
npm start
```

健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:17891/health
```

## 推荐操作方式

当前生产化 v1 主链路应从后台自媒体分发弹窗操作：后台先创建分发任务，再把已创建任务交给本地助手启动 AdsPower 环境。本地助手不再从后台页面接收运营主 `accessToken`。

首次使用时先完成 C2 配对：

1. 打开本地助手页面，点击“生成配对码”。
2. 在后台自媒体分发弹窗中手动输入该配对码并点击“绑定”。
3. 回到本地助手页面点击“检查配对状态”，看到 `paired: true` 后，后台会改用 `helper.session.{sessionId} + timestamp + nonce + HMAC` 签名请求访问本地助手。`accessTokenLookupHash` 只留作服务端查表/审计字段，不再作为请求头凭证传输。

配对码 5 分钟有效且只能使用一次。`trustedBackendBase` 必须配置为可信后台地址，本地助手不会从配对请求体里读取回连地址。

下面的本地助手页面仍保留给 PoC 调试使用。正式业务验证时优先走后台页面的“打开环境并填充 / 打开环境登录/上报”按钮。涉及后台 Access Token 的旧接口默认禁用；只有临时回归旧 PoC 时才把 `enableLegacyBackendTokenRoutes` 改为 `true`。静态助手 Token 也默认禁用，旧脚本调试需要额外打开 `enableStaticHelperToken`。

## PoC 调试页面

启动本地助手后，直接在普通浏览器打开：

```text
http://127.0.0.1:17891/
```

页面里填：

- AdsPower API Key：首次使用先在本地助手页面保存。AdsPower Local API 地址默认即可，除非本机 AdsPower 端口不是 `50325`。
- 环境标识：例如 `geo_b`
- 后台 Access Token
- 品牌 ID
- 文章 ID
- 平台账号记录 ID，推荐点击“查询账号”后从下拉框选择
- 平台：今日头条 / 知乎 / 小红书
- 启动后打开页面：默认会随平台切换

然后点击“创建任务并启动环境”。启动成功后，到 AdsPower 环境里的 PoC 扩展点击“领取任务并填充一次”。

后台 Access Token 可以在已登录后台页面 DevTools Console 执行下面这一行自动带入本地助手页：

```js
window.open(`http://127.0.0.1:17891/#backendToken=${encodeURIComponent(JSON.parse(localStorage.getItem('geo_auth_v1') || '{}').accessToken || '')}`)
```

如果已经有后台任务 ID，也可以只填“已有任务 ID”，点击“仅启动已有任务”。

选择后台账号后，本地助手会把该账号记录上的 `platformAccountId` 写入本地任务的 `expectedPlatformAccountId`，并把账号名称写入 `expectedAccountName`。环境内 PoC 扩展领取任务后，会在填充前做账号一致性校验：

- 今日头条：优先比对平台账号 ID，名称作为 PoC 兜底。
- 知乎：探测账号名称、`urlToken`、`id`，当前主要按名称校验。
- 小红书：读取创作后台顶部账号名称，当前按名称校验。

账号不一致时，任务会失败并留在本地助手里等待按任务 ID 重置重试，不会 ack 后台。

本地任务以 `taskId` 为唯一标识保存。同一环境可以排多个任务，环境内扩展按创建时间领取当前环境下最早的 `pending/requeued` 任务；领取后任务进入 `claimed`，超时未完成会自动回退为 `requeued`。

任务主状态：

- `pending`：等待领取。
- `claimed`：已被某个环境领取，等待填充结果。
- `completed`：已填充完成。
- `failed`：填充失败，可按任务 ID 重置。
- `requeued`：已重置或领取超时回退，可再次领取。
- `cancelled`：已取消，不再领取。

扩展自动执行失败时，本地助手会把任务置为 `failed`，并在 `failureCode` 中记录错误分类：

- `login_required`：平台账号未登录，需要运营在 AdsPower 环境内登录。
- `account_mismatch`：当前环境登录账号和任务目标账号不一致。
- `editor_not_found`：没有找到平台编辑器，通常是页面结构或发布入口变化。
- `failed`：其它填充失败。

三平台当前 PoC 状态见：

```text
D:\code\sanhe-geo-project\geo-project\geo-env-extension\POC_STATUS.md
```

## 启动环境并派发 PoC 任务

以下旧命令仅用于临时回归旧 PoC。默认配置下会返回 `static helper token is disabled`；如果确实要执行，需要先在 `config.local.json` 中设置随机 `helperToken` 并打开 `enableStaticHelperToken`。

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:17891/v1/poc/launch `
  -Headers @{ 'X-Geo-Helper-Token' = 'change-me-geo-helper-token' } `
  -ContentType 'application/json' `
  -Body '{
    "environmentKey": "geo_b",
    "providerProfileId": "klcvxe0w",
    "taskId": 123,
    "platform": "toutiao",
    "url": "https://mp.toutiao.com/"
  }'
```

本地助手会：

1. 调 AdsPower `/api/v1/browser/start` 启动 `geo_b`。
2. 用返回的 Puppeteer WS 打开 `url`。
3. 把 `taskId/platform/environmentKey` 暂存在本地，等待环境内扩展领取。

## 停止环境

以下旧命令同样依赖 `enableStaticHelperToken=true`。

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://127.0.0.1:17891/v1/poc/stop `
  -Headers @{ 'X-Geo-Helper-Token' = 'change-me-geo-helper-token' } `
  -ContentType 'application/json' `
  -Body '{ "environmentKey": "geo_b", "providerProfileId": "klcvxe0w" }'
```

## 查看本地任务

以下旧命令同样依赖 `enableStaticHelperToken=true`。

```powershell
Invoke-RestMethod `
  -Uri http://127.0.0.1:17891/v1/poc/tasks `
  -Headers @{ 'X-Geo-Helper-Token' = 'change-me-geo-helper-token' }
```

## 安全回归脚本

本地助手完成 C2 配对并重启到最新代码后，可以直接跑 C2 异常自检：

```powershell
cd D:\code\sanhe-geo-project\geo-project\geo-local-helper
npm run security:c2
```

脚本会读取 `runtime/session.json` 构造签名请求，并自动验证：

- 正常签名请求可访问本地助手。
- 过期 timestamp 返回 `401`。
- 错签返回 `401`。
- 错误 session access 返回 `401`。
- nonce 重放返回 `409`。

如需指定助手地址：

```powershell
$env:GEO_HELPER_BASE='http://127.0.0.1:17891'
npm run security:c2
```

默认输出为简短表格，例如：

```text
C2_SECURITY_CHECK=ok
PASS expired_timestamp_rejected status=401
PASS wrong_signature_rejected status=401
PASS wrong_session_access_rejected status=401
PASS nonce_replay_rejected first=200 second=409
```

如需完整 JSON：

```powershell
node scripts/c2-security-check.mjs --json
```

## PoC 安全边界

当前版本只监听 `127.0.0.1`，v1 主链路使用 C2 配对后的签名请求。已消费 nonce 会批量持久化到 `runtime/nonces.json`，用于降低本地助手重启后 5 分钟窗口内的重放风险；运行中仍以内存 nonce cache 即时拦截重放。`X-Geo-Helper-Token` 只作为临时旧 PoC 回归开关，默认关闭。生产版仍需要完善安装包、token 吊销可视化、日志脱敏和自动更新。
