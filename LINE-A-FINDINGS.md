# Sprint 2 W3 Line A Findings

分支：`codex/sprint2-w3-line-a-validation`

范围：线 A 技术路径验证，只记录发现，不修代码。当前验证基于最新 `master`（B5a/B5b 已合入，HEAD `3813c93b`）的本地代码、现有自动化测试、开发库查询和临时 DOMPurify 兼容性脚本。

已执行证据：

- `mvn test`：545/0/0/0（B5a/B5b 合入 master 后已跑）
- `npm test -- --run`：47/0/0/0（B5a/B5b 合入 master 后已跑）
- 开发库 `geo` 查询：`audit_log=0`，`article_draft=265`，`distribution_tasks=19`
- 临时 DOMPurify 兼容性脚本：只在本地 node stdin 运行，未写入仓库

## 验证 1：后端 Audit 链路完整性

### ISSUE-1: 本地 audit_log 为空，无法产出真实链路 SELECT 表

**路径**：查询开发库 `geo.audit_log`，准备按完整业务路径核对 article 创建、审核、分发、fill-token、consume、ack、heartbeat、published 的实际审计记录。

**预期**：能按步骤 SELECT 出 `event_type / actor_id / brand_id / target_id / sensitive / result / error_code`。

**实际**：`audit_log` 当前为 0 行。现有自动化测试主要 mock `AuditService` / `ExtensionAuditSupport`，没有现成完整业务 E2E 把记录落到真实 `audit_log` 后再查询。

**严重性**：Major

**怀疑根因**：开发库没有跑过带真实 audit writer 的业务链路；测试基础设施更偏单元/服务层 mock 验证，缺少 audit DB 断言型集成测试。

静态代码路径上可确认的扩展链路审计发出点如下，但不是 DB 实际行：

| step | event_type | actor_id | brand_id | target_id | sensitive | result | error_code |
|------|------------|----------|----------|-----------|-----------|--------|------------|
| fill token issue | FILL_TOKEN_ISSUE | operatorId | brandId | task/account context | false | SUCCESS | null |
| fill token consume | FILL_TOKEN_CONSUME | operatorId | brandId | accountId | false | SUCCESS / DENIED | 70006 / 70007 on failure |
| cookie decrypt via fill token | COOKIE_DECRYPT_VIA_FILL_TOKEN | operatorId | brandId | accountId | true | SUCCESS / NOT_FOUND / DENIED | credential error code on failure |
| task filling start | SEMI_AUTO_TASK_FILL_STARTED | operatorId | brandId | taskId | false | SUCCESS / DENIED | 70012 on stale state |
| task ack filled | SEMI_AUTO_TASK_FILLED | operatorId | brandId | taskId | false | SUCCESS / DENIED | 70012 on stale state |
| task heartbeat denied | SEMI_AUTO_TASK_HEARTBEAT_DENIED | operatorId | brandId | taskId | false | DENIED | 70012 on stale state |
| task published | SEMI_AUTO_TASK_PUBLISHED | operatorId | brandId | taskId | false | SUCCESS / DENIED | 70012 on stale state |

### ISSUE-2: Heartbeat 成功路径没有 audit 记录

**路径**：模拟扩展端 `ack -> heartbeat -> published` 生命周期时检查 `ExtensionTaskStateService.heartbeat()`。

**预期**：每个状态推进或关键生命周期动作都有审计记录；heartbeat 成功也应可在 audit 中看到。

**实际**：`heartbeat()` 成功时只更新 `last_heartbeat_at`，没有记录成功 audit；只有状态冲突时记录 `SEMI_AUTO_TASK_HEARTBEAT_DENIED`。因此“每个状态转移都有 audit”在 heartbeat 成功路径上有断点。

**严重性**：Major

**怀疑根因**：B5b 为避免 30s 轮询产生过多 audit，只记录 denied，但联调清单要求链路完整可观测。B6 可考虑低频聚合记录、只记录 first heartbeat，或在 task detail 中补充 heartbeat 可观测字段。

### ISSUE-3: Fill token issue 事件命名与联调清单不一致

**路径**：对照联调清单中的 `FILL_TOKEN_ISSUED` 与代码中的实际事件。

**预期**：事件名一致，便于排查和看板统计。

**实际**：实际事件为 `FILL_TOKEN_ISSUE`，不是清单里写的 `FILL_TOKEN_ISSUED`。

**严重性**：Minor

**怀疑根因**：命名约定未统一。若已有 BI/审计看板依赖清单命名，建议 B6 或 Sprint 3 统一事件名；若只是文档误差，更新联调清单即可。

### ISSUE-4: 失败路径审计覆盖不完全可由 DB 证明

**路径**：检查失败路径：cookie 解密失败、heartbeat 状态冲突、fill token 重复消费。

**预期**：失败路径 audit 可直接 SELECT 验证，且 `sensitive=true` detail 不含 cookie/token 明文。

**实际**：代码静态检查显示：

- cookie 解密失败会记录 `COOKIE_DECRYPT_VIA_FILL_TOKEN`，`sensitive=true`，detail 未放 cookie 明文。
- heartbeat 状态冲突会记录 `SEMI_AUTO_TASK_HEARTBEAT_DENIED`。
- fill token 重复消费在 payload 可验证后会记录 `FILL_TOKEN_CONSUME` denied，error code `70007`。

但由于 `audit_log` 空表，本次不能用实际 DB 行证明 detail 内容。

**严重性**：Major

**怀疑根因**：同 ISSUE-1，需要补一条最小 SpringBootTest 或联调脚本专门断言 audit DB 行，尤其是 `sensitive=true` 的 detail。

## 验证 2：本地 Mock 平台跑扩展全流程

### ISSUE-5: 当前环境未能运行真实 Chrome 扩展 + mock editor 的端到端流程

**路径**：计划托管 mock editor，临时改 `fillProfiles.ts` 到 localhost，跑 `bind -> task list -> fill -> ack -> heartbeat -> published`。

**预期**：真实 Chrome 扩展运行态下完成至少一次完整流程，记录耗时、popup 关闭、tab 关闭、切换任务等行为。

**实际**：当前执行环境没有可交互 Chrome 扩展运行态和 DevTools 面板，未能完成真实浏览器端到端操作。已用 Vitest 验证服务工作线程和 content script 的技术路径，测试覆盖包括：

- issue token -> consume -> set cookie -> create tab -> content script ready -> ack endpoint
- consume 失败不写 cookie
- publishUrl 白名单拒绝
- heartbeat 30s 后调用
- 70013 继续轮询
- 70011/70012 停止轮询
- tab close 停止轮询
- published 成功停止轮询
- 切换 task 时旧 heartbeat 停止

**严重性**：Major

**怀疑根因**：自动化测试证明了模块逻辑，但不能替代 Chrome MV3 service worker 真实生命周期验证。周四 B6 若只按自动化结论修，仍要保留 Sprint 3 的真实扩展验收项。

### ISSUE-6: Heartbeat 状态只存在 service worker 模块级变量中，SW 重启会丢失 activeTask

**路径**：检查 `taskLifecycle.ts` 中 lifecycle 状态保存方式。

**预期**：popup 关闭、浏览器闲置、SW 休眠/唤醒后 heartbeat 能继续，直到 published、tab close 或状态冲突。

**实际**：`activeTask` 是 service worker 模块级变量，timer 使用 `setInterval(30000)`。Chrome MV3 service worker 休眠或进程重启后，模块级变量和 interval 都会丢失；代码没有把 active task 写入 `chrome.storage`，也没有用 `chrome.alarms` 恢复 heartbeat。`chrome.alarms` 当前只用于 token refresh。

**严重性**：Blocker

**怀疑根因**：B5b 为了小 PR 先用内存 singleton 管生命周期，适合单次打开期间的 happy path，但不适合长时间挂机。建议 B6/Sprint 3 把 heartbeat 改成 `chrome.storage + chrome.alarms`，每次 alarm 从 storage 恢复 active task。

### ISSUE-7: publishButtonSelectors 存在误命中风险

**路径**：检查 `fillProfiles.ts` 的发布按钮 selector。

**预期**：content script 只监听真正“发布”按钮的人工点击，不监听保存草稿、预览、提交表单等其它按钮。

**实际**：profile 包含 `button[type="submit"]` 这类宽 selector。mock 页面 `data-geo-publish` 可精确命中，但真实平台里 `submit` 可能是保存、预览或其它动作。

**严重性**：Major

**怀疑根因**：真实平台 DOM selector 尚未业务线验证。建议真实平台 profile 优先用文本/aria/稳定 data 属性组合，删除过宽 selector，至少不要把 generic `button[type="submit"]` 放在所有平台默认列表里。

## 验证 3：Chrome DevTools Service Worker 行为观察

### ISSUE-8: 未能在当前环境实测 SW 休眠/唤醒，静态代码已显示 setInterval 方案不可恢复

**路径**：计划通过 Chrome 扩展页面 DevTools 观察 service worker idle、唤醒、`activeTask` 是否保留，以及 `chrome.alarms` 是否在休眠时触发。

**预期**：拿到真实 idle 时间、唤醒行为和 alarm 行为。

**实际**：当前环境无法打开真实 Chrome 扩展 DevTools 面板。静态检查结论：

- `setInterval` 只在当前 SW 实例存活期间有效。
- `activeTask` 为模块级变量，SW 重启后必然丢失。
- `chrome.alarms` 已在项目中用于 token refresh，说明扩展权限和基础设施可用。

**严重性**：Major

**怀疑根因**：B5b 的生命周期实现没有持久化运行态。这个发现足以支持 Sprint 3 将 heartbeat 从 `setInterval` 迁到 `chrome.alarms`，但还需要线 B 或手工 Chrome 验证确认实际 idle 时间。

## 验证 4：错误码 UI 文案审视

### ISSUE-9: 70001-70016 多数可用，但部分文案偏技术化或下一步不够明确

**路径**：审视 `geo-project/geo-extension/src/shared/errorMessages.ts`。

**预期**：运营看到错误后知道下一步做什么。

**实际**：整体比早期版本可读，但 `会话`、`令牌`、`重新领取任务` 等词仍偏系统内部概念。建议如下：

| code | 当前文案 | 建议文案 | 理由 |
|------|----------|----------|------|
| 70001 | 请求参数不正确，请检查后重试。 | 操作信息不完整。请刷新扩展弹窗后重试。 | “参数”偏开发；刷新弹窗是明确动作。 |
| 70002 | 扩展登录已失效，请重新绑定。 | 扩展登录已失效。请打开扩展弹窗重新绑定。 | 补充在哪里重新绑定。 |
| 70003 | 当前账号没有执行该操作的权限。 | 你没有这个品牌或账号的操作权限，请联系管理员开通权限。 | 指向管理员处理。 |
| 70004 | 扩展会话不存在，请重新绑定。 | 扩展登录记录已失效。请打开扩展弹窗重新绑定。 | “会话不存在”运营不一定理解。 |
| 70005 | 扩展版本过低，请升级后再使用。 | 当前扩展版本过旧，请更新扩展后再继续。 | 更口语。 |
| 70006 | 任务令牌无效，请刷新任务后重试。 | 任务授权已失效。请刷新任务列表后重新点击任务。 | 避免“令牌”。 |
| 70007 | 任务令牌已使用或已过期，请重新领取任务。 | 这个任务授权已用过或已过期。请刷新任务列表后重新领取。 | 避免“令牌”，保留动作。 |
| 70008 | 绑定码无效或已过期，请在后台重新生成。 | 绑定码无效或已过期，请在后台重新生成绑定码。 | 明确生成对象。 |
| 70009 | 绑定尝试过于频繁，请稍后再试。 | 绑定尝试太频繁，请稍后再试。 | 更自然。 |
| 70010 | 扩展服务暂时不可用，请稍后重试。 | 扩展服务暂时不可用，请稍后重试；若持续出现请联系管理员。 | 给持续失败出口。 |
| 70011 | 任务不存在或已被回收。 | 任务不存在或已被系统回收，请刷新任务列表。 | 补下一步。 |
| 70012 | 任务状态已变化，请刷新任务。 | 任务状态已变化，请刷新任务列表后确认最新状态。 | 补确认动作。 |
| 70013 | 操作过于频繁，请稍后再试。 | 操作太频繁，系统正在保护任务状态，请稍后再试。 | 解释为什么要等。 |
| 70014 | 捕获凭证前需要确认授权。 | 捕获凭证前需要你先确认授权。 | 更直接。 |
| 70015 | 账号与品牌不匹配，请刷新账号列表。 | 所选账号不属于当前品牌，请刷新账号列表后重新选择。 | 说明选择错了。 |
| 70016 | 本次确认已使用，请重新确认后再试。 | 本次确认已使用，请重新点击确认后再试。 | 明确操作。 |

**严重性**：Minor

**怀疑根因**：错误码文案是在开发阶段围绕接口语义写的，B6 可以统一按运营视角润色。

## 验证 5：DOMPurify 真实内容兼容性

### ISSUE-10: 当前 DOMPurify 白名单会丢失 markdown 表格、代码块和 details/summary 结构

**路径**：用与 `sanitizeContentHtml` 相同的 DOMPurify 配置，喂入真实 markdown 常见渲染 HTML：table、pre/code、details/summary、嵌套列表、图片。

**预期**：常见文章结构保留，危险脚本和事件属性被移除。

**实际**：临时脚本输出：

| 输入类型 | 净化后结果 |
|----------|------------|
| `<table><thead>...` | 只剩单元格文本 `B`，表格结构全部丢失 |
| `<pre><code class="language-js">const x = 1;</code></pre>` | 只剩 `const x = 1;`，代码块结构丢失 |
| `<details><summary>展开</summary><p>内容</p></details>` | 变为 `展开<p>内容</p>`，折叠结构丢失 |
| 嵌套 `blockquote/ul/ol/strong` | 保留 |
| `<img src=... style=...>` | 保留 `src/alt`，删除 `style` |

**严重性**：Major

**怀疑根因**：B5a 为 XSS 安全收窄了 tag 白名单，但 markdown renderer 已启用 table extension，真实文章很可能包含表格和代码块。建议 B6 扩展白名单：`table, thead, tbody, tr, th, td, pre, code, details, summary`；属性允许 `class` 需谨慎，若只为代码高亮可暂不允许 class。

### ISSUE-11: 图片 style 被删除会影响排版，但安全上可接受

**路径**：同 DOMPurify 临时脚本，检查 `<img style="width:100px;height:50px">`。

**预期**：图片可保留，危险样式不进入目标编辑器。

**实际**：`img` 保留，`style` 被删除。安全上合理，但真实文章如果依赖 markdown renderer 生成的尺寸样式，目标编辑器中图片可能变成原始尺寸。

**严重性**：Minor

**怀疑根因**：当前白名单未允许 `style` 是正确的安全默认。若线 B 发现图片尺寸严重影响编辑器体验，建议用受控属性转换策略，而不是直接放开 `style`。

## B6 建议优先级

1. Blocker：修复 heartbeat 生命周期持久化，至少评估 `chrome.storage + chrome.alarms` 替代 `setInterval + module activeTask`。
2. Major：补 audit DB 级集成验证或联调脚本，解决 `audit_log` 为空无法验链路的问题。
3. Major：heartbeat 成功路径的 audit/可观测性方案。
4. Major：收紧 publish button selector，避免误监听非发布按钮。
5. Major：扩展 DOMPurify 白名单，保留 markdown 表格和代码块。
6. Minor：统一错误码文案和 `FILL_TOKEN_ISSUE` 命名/文档。
