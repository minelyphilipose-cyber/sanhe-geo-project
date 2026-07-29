# 自媒体发布回查基础设施错误语义热修任务（2026-07-28）

任务类型：生产热修

优先级：P0

文档状态：评审通过，可以进入后端小版本开发

建议版本：独立小版本，先于浏览器运行时治理 V2

主要范围：`geo-server`

兼容对象：当前及旧版本 `geo-local-helper`、`geo-env-extension`

明确不包含：页面自动关闭、环境自动停止、资源注册表、停止租约、完整次数拆分

后续完整方案：[自媒体浏览器自动化运行时治理方案 V2](./self-media-browser-runtime-governance-v2-review-2026-07-28.md)

---

## 1. 目标

修复旧助手在发布结果回查发生 CDP、AdsPower、浏览器或助手基础设施异常时调用：

```http
POST /api/v1/local-agent/self-media-schedules/{scheduleId}/publish-checks/failed
```

导致后端直接将排期写为 `publish_failed` 的问题。

热修后必须满足：

```text
基础设施异常 ≠ 平台发布失败
平台作品暂未匹配 ≠ 平台发布失败
只有平台明确终态失败才能自动进入 publish_failed
```

该热修只改变后端状态语义，不触碰浏览器页面和 AdsPower 环境。

---

## 2. 当前问题

当前助手在回查过程中：

- 环境绑定错误和页面整体超时会调用 `publish-checks/unknown`；
- 其它未分类异常会包装为 `PUBLISH_RESULT_CHECK_HELPER_FAILED`；
- 随后调用 `publish-checks/failed`。

后端 `markClaimedPublishFailed` 当前不区分错误类别，直接执行：

```text
status = publish_failed
释放环境锁
释放文章
退还排期额度
```

这会将以下异常错误表达为平台业务失败：

- `Network.enable timed out`
- browser disconnected
- WebSocket closed
- `ECONNRESET` / `ECONNREFUSED`
- Protocol error
- AdsPower API 或动态 endpoint 异常
- 本地助手回查执行异常

其风险包括：

- 已发布文章被展示为发布失败；
- 操作员可能再次创建发布任务；
- 自动化防重复发布链路被错误业务状态削弱；
- 统计、告警、额度和人工判断被污染。

---

## 3. 热修范围

### 3.1 必做

1. 只在本地助手发布回查失败入口增加错误语义归一化。
2. 基础设施错误转为 `publish_unknown` 或达到临时预算后的 `manual_required`，不得进入 `publish_failed`。
3. 平台明确终态失败继续进入 `publish_failed`。
4. 保持 claimAttempt 陈旧回调防护。
5. 相同 claimAttempt 的重复基础设施回调幂等。
6. 释放环境锁，避免回查通道长期占用。
7. 保留原始 failureCode、failureMessage 和 diagnostics，便于诊断。
8. 后台官方 API Worker 的失败入口保持原语义，不经过本地助手归一化。
9. 项目批量重试继续通过 `retryNow` 路由，发布结果未知的排期不得批量改期或批量忽略。
10. 增加单元测试和控制器/服务回归测试。

### 3.2 可选

- 为热修新增错误元数据和更准确的前端展示文案；
- 助手后续小版本直接将基础设施错误发送到 unknown 接口；
- 增加基础设施错误归一化日志和计数。

### 3.3 不做

- 不新增自动页面回收；
- 不停止 AdsPower 环境；
- 不实现完整 CDP 熔断；
- 不实现不可逆边界新协议；
- 不拆分数据库中的三类次数；
- 不修改现有平台回查选择器；
- 不修改微信公众号官方 API 链路。

---

## 4. 错误分类

### 4.1 本次热修本地助手入口精确白名单

本地助手 `publish-checks/failed` 入口只有本次热修明确列出的平台业务失败码允许写入 `publish_failed`。

本次热修精确白名单为：

```text
BAIJIAHAO_REVIEW_REJECTED
BAIJIAHAO_WORK_WITHDRAWN
```

该白名单只适用于带 `operatorId/localAgentSessionId/claimAttempt` 的本地助手入口。后续新增平台明确失败码必须经过评审后精确加入，不能由实现人员自行扩张，也不能让未知错误默认进入 `publish_failed`。

后台官方 API Worker 使用的内部入口不套用该白名单。现有官方 API 明确终态码：

```text
OFFICIAL_API_REVIEW_REJECTED
OFFICIAL_API_WORK_OFFLINE
```

必须继续按原语义进入 `publish_failed`。

### 4.2 基础设施错误

以下错误必须归一化为基础设施未知：

```text
PUBLISH_RESULT_CHECK_HELPER_FAILED
PUBLISH_RESULT_CHECK_FAILED
PUBLISH_CHECK_FAILED
PUBLISH_CHECK_PAGE_TIMEOUT
LOCAL_HELPER_CLAIM_TIMEOUT
LOCAL_AGENT_HEARTBEAT_TIMEOUT
PAGE_LOAD_TIMEOUT
LOCAL_HELPER_TEMPORARY_ERROR
ADSPOWER_CDP_TIMEOUT
ADSPOWER_SESSION_STALE
ADSPOWER_BROWSER_UNRESPONSIVE
ADSPOWER_API_UNAVAILABLE
BROWSER_ENVIRONMENT_QUARANTINED
EXTENSION_INJECTION_TRANSIENT
```

未来错误码尚未被服务端识别时，`publish-checks/failed` 入口应采用安全默认值：

```text
未知错误码 → publish_unknown/manual_required
而不是 publish_failed
```

### 4.3 消息兼容

旧助手通常使用统一错误码 `PUBLISH_RESULT_CHECK_HELPER_FAILED`，原始 CDP 错误只存在于 failureMessage。

错误码优先；对缺失、空值或历史异常码，可补充匹配：

```text
Network.enable timed out
browser has disconnected
websocket closed
ECONNRESET
ECONNREFUSED
Protocol error
socket hang up
```

消息匹配仅用于将错误降级为 unknown，不能用于判定平台明确失败。

---

## 5. 目标状态转换

### 5.1 基础设施失败且仍有现有回查机会

```text
checking_publish_result
→ publish_unknown
```

字段建议：

```text
queue_kind = publish_result_check
locked_until = null
next_attempt_at = 现有回查退避时间
failure_code = 原始基础设施 failureCode
failure_message = 原始 failureMessage
diagnostics_json = 原始 diagnostics + normalization 标记
runtime_stage = publish_check_infrastructure_unknown
runtime_stage_message = 本地回查环境异常，等待重新校验
```

必须释放环境锁。

### 5.2 基础设施失败但现有共享 attempt 预算已耗尽

V2 独立计数上线前，热修不能安全地从 `attempt_count` 中分离基础设施次数。

临时策略：

```text
checking_publish_result
→ manual_required
```

要求：

- 不得进入 `publish_failed`；
- failureCode 保留为基础设施错误；
- 文案说明“发布结果未知，自动回查环境持续异常，需要人工重新校验”；
- 提供“重新校验发布结果”而不是“重新发布”；
- 不释放文章为可重复发布状态；
- 释放环境锁。

该限制在 V2 独立次数模型上线后取消。

### 5.3 平台明确失败

```text
checking_publish_result
→ publish_failed
```

仅对白名单生效，沿用现有额度、文章和告警处理。

### 5.4 未知错误

在本地助手入口中，任何不在本次热修精确白名单中的错误，默认走基础设施未知路径。

这是热修的安全默认值，避免新出现的浏览器异常再次污染 `publish_failed`。

---

## 6. 后端实现建议

预计涉及文件：

```text
geo-server/src/main/java/com/huanjing/geo/module/content/service/SelfMediaPublishScheduleService.java
geo-server/src/main/java/com/huanjing/geo/module/extension/controller/LocalAgentController.java
geo-server/src/main/java/com/huanjing/geo/module/content/service/ProjectSelfMediaScheduleService.java
geo-server/src/main/java/com/huanjing/geo/module/content/constant/SelfMediaPublishFailureCodes.java
geo-server/src/test/java/com/huanjing/geo/module/content/service/SelfMediaPublishScheduleServiceTest.java
geo-server/src/test/java/com/huanjing/geo/module/content/service/ProjectSelfMediaScheduleServiceTest.java
```

应补充 `LocalAgentController` 本地助手入口测试，以及覆盖 `SelfMediaPublishScheduleWorker` 官方 API 终态失败的服务或 Worker 回归测试。不应为了热修扩展到助手和浏览器扩展代码。

### 6.1 归一化入口

归一化必须限定在带 `operatorId/localAgentSessionId/claimAttempt` 的本地助手服务入口内。建议将该重载明确重命名为：

```java
markClaimedLocalAgentPublishCheckFailed(
        Long operatorId,
        Long localAgentSessionId,
        Integer claimAttempt,
        Long id,
        String failureCode,
        String failureMessage,
        String diagnosticsJson
)
```

`LocalAgentController.markSelfMediaPublishCheckFailed` 只调用这个入口。该方法在完成本地助手所有权和 claimAttempt 校验后，在同一事务内分类并转换状态。

后台 `SelfMediaPublishScheduleWorker` 当前调用的：

```java
markClaimedPublishFailed(Long id, ...)
```

保持现有平台终态失败语义，不执行本地助手归一化，也不改微信公众号官方 API 回查链路。禁止让本地助手重载无条件委托到该内部 Worker 方法后再做全局分类，避免 `OFFICIAL_API_REVIEW_REJECTED`、`OFFICIAL_API_WORK_OFFLINE` 被误降级为 unknown。

建议新增：

```java
PublishCheckFailureCategory classifyPublishCheckFailure(
        String failureCode,
        String failureMessage
)
```

分类：

```text
PLATFORM_TERMINAL
INFRASTRUCTURE
UNKNOWN_SAFE
```

其中 `UNKNOWN_SAFE` 与基础设施一样不得进入 `publish_failed`。

### 6.2 独立状态转换方法

建议新增内部方法：

```java
markClaimedPublishCheckInfrastructureUnknown(...)
```

不要直接复用当前“作品未匹配”路径覆盖原始错误码。该方法应：

- 保留基础设施 failureCode；
- 保留 failureMessage；
- 在 diagnostics 中加入 normalization 元数据；
- 计算现有退避或转人工；
- 释放锁；
- 更新告警；
- 不调用平台失败的文章/额度处理。

诊断追加示例：

```json
{
  "failureNormalization": {
    "sourceEndpoint": "publish-checks/failed",
    "originalFailureCode": "PUBLISH_RESULT_CHECK_HELPER_FAILED",
    "normalizedCategory": "infrastructure",
    "normalizedStatus": "publish_unknown",
    "normalizedAt": "..."
  }
}
```

若原 diagnostics 不是合法 JSON，不得抛出二次异常；应安全包装或保留截断后的原始文本。

### 6.3 幂等与陈旧回调

相同 claimAttempt 的重复回调：

- 若排期已经由该回调转成 `publish_unknown` 或对应 `manual_required`，返回当前状态；
- 不重复修改次数、额度或告警；
- 不返回导致助手无限重试的 5xx。

较旧 claimAttempt 的回调：

- 保持当前所有权校验；
- 返回明确的陈旧回调错误；
- 不覆盖新一代领取状态。

新一代 claimAttempt 正在执行时，旧失败回调不得将其重新改为 unknown。

### 6.4 原有失败行为

本地助手精确白名单中的平台明确失败，以及内部官方 API Worker 已明确判定的终态失败，继续使用现有终态转换：

```java
markClaimedPublishFailed(...)
```

不得因为本次热修把审核拒绝、撤回或删除变成自动重试。

### 6.5 项目批量操作安全门

`ProjectSelfMediaScheduleService` 的批量操作必须在读取详情后的候选筛选和更新前二次读取排期时，都检查实际 `queue_kind`。前端隐藏按钮只能改善交互，不能代替后端拦截。

对 `queue_kind=publish_result_check` 的排期执行以下规则：

| 批量操作 | 后端规则 | 状态与证据 |
|---|---|---|
| 重新处理 | 必须继续逐条调用 `SelfMediaPublishScheduleService.retryNow(scheduleId)` | 由 `retryNow` 路由为发布结果回查，不得进入填充或提交 |
| 改期到下月 | 拒绝或逐条跳过，返回明确原因 `PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN` | 不得写 `pending/schedule_execution`，原状态、queue、failure 和 diagnostics 不变 |
| 忽略 | 拒绝或逐条跳过，返回明确原因 `PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN` | 不得写 `cancelled/schedule_execution`，不得覆盖“发布结果未知”证据 |

批量方法必须防止检查与更新之间的状态变化：更新前重新读取并校验行，必要时使用条件更新。混合批次允许安全项继续处理，但响应应返回成功数、跳过数和逐类原因；若当前响应结构暂不支持明细，至少保证受保护项不发生任何字段修改并以明确业务错误告知操作员。

项目批次详情、操作预览和 `allowedActions` 也应按 `queue_kind` 过滤：发布回查队列只展示“重新校验”，不展示“改期到下月”和“忽略”。

---

## 7. 数据库变更

本热修原则上不增加数据库迁移。

原因：

- 目标是尽快修正错误状态语义；
- 当前字段已能表达 `publish_unknown`、`manual_required`、failureCode 和 diagnostics；
- 完整基础设施计数由 V2 独立迁移处理；
- 避免热修与 V2 大迁移耦合。

已知限制：

- 当前 `attempt_count` 仍同时承担领取代数和回查预算；
- 基础设施失败在热修阶段仍可能间接耗用共享预算；
- 预算耗尽时只能安全转 `manual_required`；
- V2 上线独立次数后再消除此限制。

---

## 8. 兼容性

### 8.1 旧助手

旧助手无需升级：

- 仍调用 `publish-checks/failed`；
- 后端自动归一化；
- HMAC、请求体和响应结构不变。

### 8.2 新助手

后续助手可直接调用 unknown 接口，但后端归一化必须永久保留，作为防御性安全门。

### 8.3 旧扩展

无需升级。热修不依赖新边界协议和新 Target 信息。

### 8.4 前端

若不做前端修改，现有 `publish_unknown` 和 `manual_required` 页面仍可工作。

建议同步补充错误元数据：

```text
PUBLISH_RESULT_CHECK_HELPER_FAILED
→ 本地回查环境异常
→ 可重新校验
→ 不提示重新发布
```

项目自动排期批次页面应同步根据 `queue_kind=publish_result_check` 隐藏“改期到下月”和“忽略”，并将“重新处理”显示为“重新校验”。无论前端是否随热修同版发布，后端安全门都必须先上线。

---

## 9. 测试

### 9.1 服务单元测试

必须覆盖：

1. `PUBLISH_RESULT_CHECK_HELPER_FAILED` 从 checking 转 `publish_unknown`。
2. `Network.enable timed out` 消息在异常码缺失时转 unknown。
3. `PUBLISH_CHECK_PAGE_TIMEOUT` 不进入 `publish_failed`。
4. 未识别错误码按安全默认值转 unknown。
5. 达到现有回查预算时转 `manual_required`，不转 failed。
6. `BAIJIAHAO_REVIEW_REJECTED` 继续进入 `publish_failed`。
7. `BAIJIAHAO_WORK_WITHDRAWN` 继续进入 `publish_failed`。
8. 内部 Worker 的 `OFFICIAL_API_REVIEW_REJECTED` 继续进入 `publish_failed`。
9. 内部 Worker 的 `OFFICIAL_API_WORK_OFFLINE` 继续进入 `publish_failed`。
10. 官方 API 终态失败不经过本地助手精确白名单。
11. 基础设施未知保留原 failureCode、message 和 diagnostics。
12. 基础设施未知释放环境锁。
13. 基础设施未知不调用平台失败的文章释放逻辑。
14. 相同 claimAttempt 重复回调幂等。
15. 陈旧 claimAttempt 不能覆盖新领取状态。

### 9.2 Controller 测试

必须覆盖：

1. 旧助手请求体格式不变。
2. failed 接口返回成功但数据库为 unknown。
3. 明确平台失败仍为 failed。
4. 缺失 failureCode 时安全降级。
5. 非法 diagnostics 不导致 500。

### 9.3 项目批量操作测试

必须覆盖同一条 `status=manual_required`、`queue_kind=publish_result_check` 排期：

1. 批量重试调用 `retryNow`，最终仍进入发布结果回查队列，不进入 `schedule_execution`。
2. 批量改期不得写 `pending/schedule_execution`，原状态、失败证据和 diagnostics 不变。
3. 批量忽略不得写 `cancelled/schedule_execution`，原状态、失败证据和 diagnostics 不变。
4. 候选筛选后、实际更新前 queue 或状态发生变化时，二次校验阻止误操作。
5. 混合批次只处理安全项，并返回或记录受保护项的跳过原因。

### 9.4 回归测试

至少运行：

- `SelfMediaPublishScheduleServiceTest` 相关用例；
- `ProjectSelfMediaScheduleServiceTest` 批量重试、改期、忽略用例；
- `LocalAgentController` 相关用例；
- `SelfMediaPublishScheduleWorker` 与官方 API Adapter 的终态失败用例；
- MySQL mapper 集成测试；
- 环境锁释放与并发领取测试；
- 当前发布确认、unknown、manual confirm/recheck 流程。

---

## 10. 验收

热修验收标准：

1. 故障注入 `Network.enable timed out` 后，排期不进入 `publish_failed`。
2. 旧助手无需升级即可获得新语义。
3. 基础设施错误的排期只能“重新校验”，不能被引导“重新发布”。
4. 平台明确审核拒绝/撤回仍进入 `publish_failed`。
5. 环境锁在归一化后释放。
6. 重复回调幂等。
7. 陈旧回调不能覆盖新 claimAttempt。
8. 无数据库迁移。
9. 不修改、关闭任何浏览器页面或 AdsPower 环境。
10. 微信公众号官方 API 的拒绝、下线仍准确进入 `publish_failed`。
11. 发布结果未知的排期批量重试只触发回查，批量改期和批量忽略均不能改变排期或覆盖证据。

生产观察指标：

- `PUBLISH_RESULT_CHECK_HELPER_FAILED` 接收次数；
- 被归一化为 unknown/manual 的次数；
- 明确平台失败次数；
- 热修后由基础设施异常导致的 `publish_failed` 数，目标为 0；
- unknown 后重新校验成功率；
- manual_required 积压数。

---

## 11. 发布

建议顺序：

1. 在测试环境构造旧助手 failed 回调。
2. 验证数据库状态为 unknown，不是 failed。
3. 验证本地助手精确白名单，以及官方 API 拒绝/下线不受影响。
4. 验证项目批量重试、改期、忽略的后端安全门。
5. 跑服务、控制器、Worker 和 mapper 回归测试。
6. 发布后端小版本。
7. 观察归一化日志和 unknown 队列。
8. 不同时发布自动关闭或环境停止能力。

建议增加结构化日志：

```text
scheduleId
claimAttempt
originalFailureCode
normalizedCategory
targetStatus
retryAt
localAgentSessionId
```

日志不得包含正文、Cookie、密钥或 HMAC。

---

## 12. 回滚

建议用后端开关：

```text
infrastructureFailurePolicyEnabled
```

默认在热修验证后开启。

该开关只允许控制基础设施错误的自动退避与自动回查策略，不能关闭“非精确白名单不得进入 `publish_failed`”这一安全不变量。

开关关闭或策略不可用时：

- 本地助手精确白名单中的平台终态失败仍进入 `publish_failed`；
- 其它本地助手 failed 回调统一转 `manual_required` 或暂停处理，等待人工重新校验；
- 不自动退避、不自动重试，也不得恢复为旧的 `publish_failed` 语义；
- 不做数据库回滚；
- 已经进入 unknown/manual 的排期不批量改回 failed；
- 不自动重新发布；
- 由操作员按“重新校验发布结果”处理。

---

## 13. 完成定义

满足以下全部条件才可关闭热修任务：

- 代码评审通过；
- 平台终态失败白名单得到产品和技术确认；
- 所有必测用例通过；
- 旧助手兼容测试通过；
- 官方 API 拒绝和下线回归测试通过；
- 项目批量重试、改期、忽略防重复发布测试通过；
- 生产发布完成；
- 至少观察一个完整自动回查周期；
- 基础设施异常导致的新增 `publish_failed` 为 0；
- 未出现平台明确失败被错误转 unknown；
- 未在热修中引入任何自动页面关闭或环境停止能力。
