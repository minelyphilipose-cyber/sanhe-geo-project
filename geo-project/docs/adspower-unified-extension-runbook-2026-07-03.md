# AdsPower 统一应用安装/升级 Runbook

## 目标

统一应用只解决扩展分发安装问题；运行态是否可用、是否能接任务，以后端收到的 extension runtime status 和 runtime gate 判断为准。

## 产物要求

- 扩展目录：`geo-project/geo-env-extension`
- 版本号来源：`service-worker.js` 中的 `EXTENSION_VERSION` 和 `manifest.json`
- zip 内容应直接包含扩展文件，不要多包一层父目录。
- 上传前确认生产 profile 配置指向生产后端和本地助手默认端口。

建议命名：

```text
geo-env-extension-0.1.7-prod.zip
```

## 首次上传统一应用

1. 登录 AdsPower 管理后台。
2. 进入应用/扩展管理，创建自定义扩展应用。
3. 上传扩展 zip。
4. 记录统一应用名称、应用 ID、版本号、上传时间。
5. 新建一个测试环境，确认该环境自动关联该统一应用。
6. 启动测试环境，打开扩展弹窗，确认可读取生产配置。
7. 绑定或自动识别环境后，在后台确认 `extension_runtime_status.extension_version` 已上报目标版本。

## 旧环境批量添加

1. 在 AdsPower 后台筛选需要接入自媒体自动化的环境。
2. 批量添加统一应用。
3. 分批启动环境，不要一次性启动大量浏览器。
4. 在后台运行态看板确认：
   - `providerProfileId` 能匹配到环境。
   - 扩展最近上报时间刷新。
   - 扩展版本为目标版本。
   - 登录状态至少能进入可诊断状态。

## 替换 zip 升级

1. 修改扩展版本号并完成本地验证。
2. 重新打 zip，保持 zip 根目录结构不变。
3. 在 AdsPower 统一应用中替换 zip。
4. 新建测试环境验证新版本。
5. 启动一个已有环境验证旧环境是否自动更新。
6. 以后端运行态上报的 `extensionVersion` 为准，不以 AdsPower 后台展示为最终依据。

## 验证清单

- 新建环境是否自动带上统一应用。
- 已有环境批量添加后是否能看到扩展。
- 替换 zip 后，已有环境启动时是否更新到新版本。
- `extension_runtime_status.last_seen_at` 是否刷新。
- `extension_runtime_status.provider_profile_id` 是否和 AdsPower 环境一致。
- `extension_runtime_status.extension_version` 是否为目标版本。
- runtime gate 在 `observe_only` 下只写 diagnostics，不阻断 claim。

## 回滚

1. 在 AdsPower 统一应用中替换回上一版 zip。
2. 分批重启受影响环境。
3. 后台运行态看板确认 `extensionVersion` 回到上一版。
4. 如果运行态仍异常，暂时把 gate 模式保持或切回 `observe_only`，避免批量阻断生产任务。

## 常见问题

- 看板没有扩展状态：确认环境是否启动、扩展是否绑定、`providerProfileId` 是否上报。
- 版本没有变化：重启 AdsPower 环境后再看后端 `extensionVersion`，不要只看后台展示。
- 登录状态异常：优先在平台页面完成登录，再触发扩展身份检测。
- claim 被阻断：查看看板 readiness blockedReasons，再看 schedule diagnostics 中的 `claimGate`。
