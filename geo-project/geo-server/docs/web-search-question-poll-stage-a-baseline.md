# 联网问题轮询阶段 A 冻结基线

状态：已冻结，可进入四渠道 PoC。未经架构评审不得改变本文中的字段语义、枚举名称或投影规则。

## 调用层级

```text
PollResult
  └─ PollInvocationAttempt
       └─ PollProviderCall
```

- `PollResult`：问题、渠道、批次维度的逻辑结果。
- `PollInvocationAttempt`：首次执行、自动搜索重试或手工重试形成的业务尝试。
- `PollProviderCall`：一次真实 HTTP 请求；一次 Attempt 可以包含多次物理调用。

## 冻结枚举

| 枚举 | 值 |
| --- | --- |
| `AttemptStatus` | `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `ABANDONED` |
| `ProviderCallStatus` | `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `ABANDONED` |
| `SearchStatus` | `NOT_CONFIRMED`, `TRIGGERED`, `EMPTY`, `NO_VALID_SOURCE`, `FAILED` |
| `RetryChainStatus` | `NOT_STARTED`, `RUNNING`, `SEARCH_RETRY_PENDING`, `FINALIZED`, `FAILED` |
| `ResultCode` | `R0`, `R1`, `R2`, `R3`, `R4`, `R5` |
| `CitationConfidence` | `NONE`, `PROBABLE`, `CONFIRMED` |
| `BrandMatchStrength` | `NONE`, `WEAK`, `STRONG` |
| `ErrorCategory` | `NETWORK`, `TIMEOUT`, `RATE_LIMIT`, `SERVER_ERROR`, `STREAM_INTERRUPTED`, `AUTHENTICATION`, `BALANCE`, `PERMISSION`, `MODEL_UNAVAILABLE`, `UNSUPPORTED_PARAMETER`, `SAFETY_REJECTION`, `INVALID_REQUEST`, `PARSE_ERROR`, `WORKER_INTERRUPTED`, `INTERNAL_ERROR` |
| `TriggerType` | `SCHEDULED`, `MANUAL`, `SEARCH_RETRY`, `MANUAL_RETRY` |
| `IntegrationType` | `OPENAI_CHAT`, `VOLCENGINE_RESPONSES_WEB`, `DASHSCOPE_NATIVE_WEB`, `TENCENT_TOKENHUB_RESPONSES_WEB` |

`EMPTY` 与 `NO_VALID_SOURCE` 均代表已经发生真实搜索；只有缺少结构化搜索证据时才使用 `NOT_CONFIRMED`。

## 状态转换

| 对象 | 当前状态 | 事件 | 下一状态 | 约束 |
| --- | --- | --- | --- | --- |
| Attempt | `PENDING` | Worker 成功领取且未超过固定 deadline | `RUNNING` | 原子条件更新 |
| Attempt | `RUNNING` | 完整解析并分类成功 | `SUCCEEDED` | 终态不可回退 |
| Attempt | `RUNNING` | 调用或解析失败 | `FAILED` | 终态不可回退 |
| Attempt | `RUNNING` | 超时回收或人工中止 | `ABANDONED` | 终态不可回退 |
| Retry chain | `RUNNING` | R1 触发自动搜索重试 | `SEARCH_RETRY_PENDING` | 不提升 effective |
| Retry chain | `SEARCH_RETRY_PENDING` | 重试链结束 | `FINALIZED` / `FAILED` | 才允许进入正式统计 |
| Result | 任意 | 创建新 Attempt | 更新 `latest_attempt_id` | 不清空 `effective_attempt_id` |
| Result | 有 effective | 新 Attempt 得到 R0 | effective 不变 | latest 仍指向新 Attempt |
| Result | 任意 | 最终 R1–R5 | 提升 `effective_attempt_id` | 投影字段从 Attempt 复制 |

`attempt_deadline_at` 在 Attempt 创建时一次计算并持久化。心跳只能更新 `last_heartbeat_at`，不得修改或顺延 deadline。

## R0–R5 与确认引用曝光

`confirmed_citation_exposure=true` 仅允许由最终 `R5` 投影产生。R5 必须同时满足：回答品牌强命中、搜索来源品牌强命中、引用位置和编号有效、引用能解析到有效来源、引用品牌强命中。

## 四渠道固定路由

| 渠道 | 配置编码 | IntegrationType | 路由策略 |
| --- | --- | --- | --- |
| 豆包 | `doubao_web` | `VOLCENGINE_RESPONSES_WEB` | 火山 Responses 联网能力 |
| DeepSeek | `deepseek_ark_web` | `VOLCENGINE_RESPONSES_WEB` | 火山 Responses 联网能力 |
| 千问 | `qwen_web` | `DASHSCOPE_NATIVE_WEB` | DashScope 原生联网参数 |
| 腾讯元宝 | `tencent_search_web` | `TENCENT_TOKENHUB_RESPONSES_WEB` | TokenHub Responses 原生联网，单次物理调用 |

所有联网配置初始为 `enabled=false`，普通聊天配置不得作为联网路由的回退。MVP 全部使用非流式调用，不实现 SSE 解析或断线续传。

## PoC Fixture 清单

每个渠道必须采集并脱敏保存以下响应 Fixture：

| 场景 | 豆包 | DeepSeek | 千问 | 腾讯搜索增强 |
| --- | --- | --- | --- | --- |
| 成功且有来源 | 必需 | 必需 | 必需 | 必需 |
| 成功但未确认搜索 | 必需 | 必需 | 必需 | 必需 |
| 搜索为空 | 必需 | 必需 | 必需 | 必需 |
| HTTP 429 | 必需 | 必需 | 必需 | 必需 |
| 超时 | 必需 | 必需 | 必需 | 必需 |
| 鉴权失败 | 必需 | 必需 | 必需 | 必需 |
| 响应解析失败 | 必需 | 必需 | 必需 | 必需 |
| 非法引用编号 | 可选 | 可选 | 必需 | 必需 |

Fixture 只保留脱敏报文。原始报文必须加密写入 `poll_provider_calls`，并通过预提交审计的物理清理流程删除。

## PoC 通过门槛

1. 能从结构化字段证明搜索是否真实发生，不能仅依据回答文本推断。
2. 来源、引用、请求 ID、响应模型 ID 和 usage 可以稳定解析。
3. 429、超时和鉴权失败能映射到固定 `ErrorCategory`，且重试性判定稳定。
4. 解析异常不伪装为 `NOT_CONFIRMED`，必须形成失败的 ProviderCall 与 R0 Attempt。
5. Adapter 单测使用固定 Fixture，不依赖实时外网。
