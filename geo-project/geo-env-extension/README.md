# GEO Env Fill PoC Extension

这是一个独立的 MV3 实验扩展，用于装进 AdsPower 指纹浏览器环境。它不替换当前生产扩展。

它做几件事：

1. 在 AdsPower 环境内绑定当前后台。
2. 平台页打开后自动向本地助手领取该环境的待处理任务。
3. 向后台换取 fill-token 并 consume 填充 payload。
4. 打开平台编辑页并注入标题、正文、标签。
5. 填充前识别明显未登录状态，填充后做读回校验和草稿状态探测。
6. 成功后 ack 后台任务，失败时通知本地助手记录原因。

## 加载方式

先按目标环境打包：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-delivery.ps1 -Environment prod
powershell -ExecutionPolicy Bypass -File scripts/package-delivery.ps1 -Environment dev
```

生产同事只分发 `geo-env-extension-prod-v{version}.zip`；本地联调使用 `geo-env-extension-dev-v{version}.zip`。

在 AdsPower 对应环境里打开扩展管理页，选择“加载已解压的扩展”，目录选择解压后的包目录，例如：

```text
D:\code\sanhe-geo-project\geo-project\geo-env-extension\dist\geo-env-extension-prod-v0.1.6
```

每个用于 PoC 的 AdsPower 环境都需要装一次。生产化时再做批量安装/更新。

## 配置

打开扩展弹窗，确认顶部显示的运行环境。运行环境由扩展包内 `env-config.js` 固定，弹窗不提供切换入口。

- 后台地址：生产包默认为 `https://www.huanjingaigeo.com`；开发包默认为 `http://127.0.0.1:8080`
- 本地助手地址：`http://127.0.0.1:17891`
- 本地助手 Token：PoC 兜底字段，可留空。扩展绑定后台且本地助手完成 C2 配对后，会优先使用后台签发的 `helper.session.{sessionId}` + HMAC 签名访问本地助手。
- 环境标识：例如 `geo_b`
- 环境账号 ID：兼容字段，可留空。留空时按“环境标识 + 平台”查唯一绑定。
- 自媒体账号 ID：兼容字段，可留空。
- 平台：`toutiao`、`zhihu` 或 `xiaohongshu`。
- 品牌 ID：绑定码所属品牌 ID
- 绑定码：后台 `/api/v1/extension/bind-codes` 生成的一次性绑定码
- 自动领取：默认开启。AdsPower 环境打开平台页后，扩展会自动向本地助手领取当前环境任务并填充；关闭后可继续用弹窗按钮手动领取。

点击“保存配置”，再点击“绑定后台”。

升级兼容：旧版扩展只保存一份 `geoEnvConfig / geoEnvSession`。新版首次读取到旧配置时会自动归入 `prod` 生产环境档案，原有生产绑定继续可用；新版继续把旧 key 固定镜像为生产档案，便于必要时回退旧版扩展。本地开发请安装 DEV 包，不在生产包里切换。

如果自动填充日志出现 `本地助手签名失败：Unauthorized`，通常说明 AdsPower 扩展保存的是旧的后台绑定，或“后台地址”指向了另一个后端环境。处理方式：

1. 确认扩展弹窗里的后台地址与当前后台页面、当前本地助手配对的后端一致。
2. 点击“保存配置”。
3. 在后台重新生成一次扩展绑定码。
4. 在 AdsPower 扩展弹窗重新“绑定后台”。
5. 点击“自检”，确认 `extension_bound`、`local_helper_health`、`local_agent_sign` 均通过。

## 上报环境登录状态

首次把自媒体账号切到指纹浏览器环境时，后台绑定关系通常处于 `unknown`。处理步骤：

1. 在 AdsPower 环境里打开对应平台后台/创作页，并完成登录。
   - 头条：`https://mp.toutiao.com/`
   - 知乎：`https://zhuanlan.zhihu.com/write`
   - 小红书：`https://creator.xiaohongshu.com/`
2. 在扩展弹窗填写环境标识和平台。环境账号 ID、自媒体账号 ID 可留空。
3. 点击“上报当前页登录状态”。

扩展会从当前激活的平台页读取实际账号身份，并调用：

```http
POST /api/v1/extension/browser-environment-login-status
```

后端首次登记时会把读取到的实际账号身份写入 expected 字段并锁定；后续上报如果账号不一致，会进入 `mismatch`。

如果当前后台没有生成绑定码的页面入口，可以先用浏览器打开并登录后台，然后在后台页面 DevTools Console 执行：

```js
fetch('/api/v1/extension/bind-codes', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',
  body: JSON.stringify({ brandId: 1 })
}).then(r => r.json()).then(console.log)
```

把 `brandId` 换成当前品牌 ID。返回结果里的绑定码填到扩展弹窗。绑定码是一次性的，过期或用过后需要重新生成。

## 执行一次填充

1. 先用本地助手 `/v1/poc/launch` 启动指定 AdsPower 环境并派发任务。
2. AdsPower 环境打开平台页后，扩展会默认自动领取并填充。
3. 如果自动领取失败或关闭了自动领取，可以在扩展弹窗点击“领取任务并填充一次”手动重试。

扩展会从本地助手领取任务，再向后台调用：

- `POST /api/v1/extension/fill-token/issue`
- `POST /api/v1/extension/fill-token/consume`
- `POST /api/v1/extension/tasks/{taskId}/ack`

## 任务 Body 示例

```json
{
  "environmentKey": "geo_b",
  "taskId": 123,
  "platform": "toutiao",
  "url": "https://mp.toutiao.com/",
  "coverImageUrl": "https://example.com/cover.jpg",
  "platformOptions": {
    "locationName": "阜阳"
  }
}
```

`taskId` 必须是当前后台已有的半自动分发任务目标 ID。

后台当前创建自媒体半自动任务的接口是：

```http
POST /api/content/articles/{articleId}/distribute-to-self-media
Content-Type: application/json

{
  "selfMediaAccountId": null,
  "coverMaterialId": null,
  "imageMaterialIds": [],
  "requestId": "poc-001",
  "platformOptions": {}
}
```

返回的 `data.id` 就是传给本地助手的 `taskId`。也可以从后台“内容执行/自媒体分发”页面创建，创建后用返回或列表里的任务 ID 做 PoC。

## 当前三平台 PoC 状态

详细状态见：

```text
geo-project\geo-env-extension\POC_STATUS.md
```

当前已验证：

- 今日头条：进入发布页、标题/正文填充、读回校验、账号 ID/名称校验。
- 今日头条发布设置：支持展示封面（单图/三图/无封面）、封面本地上传、添加位置输入并选择下拉匹配项。
- 知乎：进入写文章页、标题/正文填充、读回校验、账号名称/标识探测校验。
- 小红书：进入写长文、点击新的创作、标题/正文填充、读回校验、账号名称校验。

## 已知边界

- 这是验证链路的最小扩展，填充选择器只覆盖常见标题/正文/标签控件。
- 不做自动发布，仍由运营人工确认发布。
- 账号一致性校验仍是 PoC 级别：今日头条优先使用平台账号 ID；知乎会探测 `urlToken/id/name`；小红书当前主要使用页面账号名称。生产化仍建议在账号绑定时固化平台唯一标识。
- 自动领取只做单次任务领取，不做持续轮询；失败后仍通过本地助手重置为待领取再重试。
- 对知乎、小红书任务，扩展会新开目标页而不是复用当前 tab，保留平台自身的中间跳转和布局初始化流程。
- manifest 保留 Chrome debugger 权限，但运行时仅在小红书点击“写长文/新的创作”等入口时附加 debugger，用于模拟真实鼠标点击。头条和知乎只使用普通 DOM 点击，不应出现调试横幅。
- 今日头条任务如果携带期望账号 ID 或账号名称，必须从 `mp.toutiao.com` 页面触发自动领取，扩展会先在该页面做账号身份预检；如果从本地助手页或其它页面手动领取，会明确失败而不是回退到编辑页弱校验。
