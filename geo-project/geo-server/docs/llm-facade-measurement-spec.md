# 门面统一测量埋点改造 Spec（v1）

> 收口期（`LlmCallFacade` 统一入口）已签收。本期在统一入口之上建立只读测量体系，为后续 permit 策略、平台配额、成本预算和缓存复用提供可信数据。

## 0. 定位与硬约束

测量期只负责观测，不改变调用控制流；所有新增观测写入必须异步、批量、可丢弃，失败不得影响主调用。`ai_platform_health_event` 保留平台健康事件语义，调用级明细和分钟级容量指标另建观测表，避免健康状态、成本/重叠度、容量诊断混表。

硬约束：

1. **只读、不改控制流**：测量只做捕获、记录、暴露。不得用 `Retry-After`、permit 占用率、队列深度等测量信号改变退避、路由、时序、permit 等待或失败切换。
2. **埋点失败不影响主调用**：测量逻辑全程吞异常。一次观测写入失败不得导致 LLM 调用失败。
3. **不拖垮被测路径**：主调用线程只做内存级累加、采样、轻量事件投递；持久化走异步/批量旁路。
4. **存储分层**：健康事件流、调用级明细、分钟容量指标物理隔离。
5. **事件所有权清晰**：现有 health monitor 事件先保持现状，新增测量事件不能造成 success/failure/rate_limited/permit_busy 等健康事件双写。

与 permit 策略期的交接红线：本期只产出可信测量仪和夜批读数。买 key、放宽 permit、需求侧削减、用 `Retry-After` 做退避、平台 cooldown 都不在本期。

## 1. 测量边界

`LlmCallFacade` 是调用观测根上下文，不是所有原始信号的唯一发生点。测量事件应在源头产生，并继承 facade 上下文：

- `LlmCallFacade`：生成/承接 `run_id`、feature、governance stack、routing strategy、wait semantics、scope、customer/project 等上下文；负责总调用计数和观测事件投递边界。
- `LlmHttpClient`：产生 HTTP status、headers、body snippet、传输耗时等源头信号。
- `OpenAiCompatibleLlmInvoker`：产生 direct 调用的模型、token、HTTP 异常结构化信号和重试尝试信息。
- `LlmPlatformRouter`：产生候选切换、internal rate limited、permit busy、circuit open、request count、failure kind 等路由信号。
- `LlmExecutionGateway`：产生 permit acquire wait、permit scope、active count、waiter count 等容量信号。
- `LEGACY_LIMITER`：产生 legacy rate limiter 命中、legacy concurrency permit wait、legacy HTTP 调用信号。

关键调整：结构化 429 不能靠 facade 从异常字符串猜。当前 `LlmHttpClient.HttpResponse` 只有 `statusCode/body`，本期必须扩展 HTTP 响应结构，至少带 headers，才能可靠解析 `Retry-After`。

## 2. 存储分层

| 层 | 表 | 职责 | 写入方式 | 可采样 |
|---|---|---|---|---|
| 健康事件流 | `ai_platform_health_event`（现有，必要时扩字段） | 平台健康事件语义（含结构化错误） | 保持现有 health monitor 语义；是否异步化单独评审 | 否 |
| 调用级明细 | `llm_call_observation`（待评审名） | 每次调用的观测：feature/platform/model/routing/wait/hash/scope/duration/token | 新增异步批量写入 | 是 |
| 分钟容量指标 | `llm_capacity_minute_metric`（待评审名） | 按 `run_id + minute + platform + feature` 聚合容量读数 | 内存聚合后批量刷新 | 否 |

红线：

- `ai_platform_health_event` 不承载 prompt hash、scope、customer/project、成本明细等调用分析字段。
- 调用明细表不更新 `ai_platform_config.current_health_status`。
- 诊断查询主查分钟容量指标，不扫调用明细表。

## 3. 结构化错误捕获

目标是可信地区分：

- `permit_busy`：内部 permit 池满。
- `internal_rate_limited`：我方 RPM/TPM 或 legacy rate limiter 先拦，非平台真 429。
- `platform_429`：平台 HTTP 层真实限流。
- `http_5xx`：平台或网关 5xx。
- `timeout`：连接/读取/请求超时。
- `config_error`：配置缺失、URL/API key/model 等本地配置错误。
- `business_non_retryable`：业务不可重试错误。

结构化字段：

- `http_status_code`
- `provider_error_code`
- `retry_after_ms`
- `error_category`
- `governance_stack`
- `routing_strategy`
- `wait_semantics`
- `request_count`
- `failure_kind`

`Retry-After` 解析规则：

- 支持 delta-seconds，转为毫秒。
- 支持 HTTP-date，按当前时间差转为毫秒，负数按 0 处理。
- 解析失败记 null，不猜默认值。
- 本期只记录，不喂给退避、cooldown 或重试。

实现前置：

- 扩展 `LlmHttpClient.HttpResponse`，新增 headers 访问能力。
- 让 HTTP 非 2xx 异常携带结构化元数据，避免 direct/routed 路径只剩 `LlmInvokeException("HTTP 429: ...")`。
- 保持现有异常类型和对调用方的行为不变；结构化元数据只服务测量。

## 4. 健康事件所有权

当前已有写入：

- `OpenAiCompatibleLlmInvoker` 记录 success/failure。
- `LlmPlatformRouter` 记录 rate_limited/permit_busy/circuit_open。
- `AiPlatformHealthMonitorService` 同步 insert `ai_platform_health_event`，并同步更新 `ai_platform_config.current_health_status / last_failure_at`。

本期原则：

- 不在 facade 额外重复写同类 health event，避免健康看板双计数。
- 新增 `llm_call_observation` 和 `llm_capacity_minute_metric` 的写入由 measurement collector 负责。
- `ai_platform_health_event` 是否异步化不与本期测量强绑定；如要改，必须单独评审实时性影响。

## 5. 容量占用采样

分钟指标维度：

- `run_id`
- `bucket_minute`
- `platform_code`
- `feature`
- `governance_stack`

核心指标：

- global/feature/platform permit active 峰值与均值。
- `permit_busy_count`
- `permit_waiter_peak`（新增 LLM 层等待者计数，非任务队列深度）
- `internal_rate_limited_count`
- `platform_429_count`
- `http_5xx_count`
- `timeout_count`
- `legacy_rate_limited_count`
- `legacy_concurrency_waiter_peak`

队列深度口径：

- `permit_waiter_count` 属于 LLM 层，可在 gateway/legacy limiter 中新增轻量内存计数。
- `task_queue_depth` 属于任务容量层，本期只预留字段或接已有任务队列指标，不要求 facade 生成。

硬约束：

- 按 run / 按分钟分桶，不做日均判断。
- 诊断看峰值分钟、饱和持续时长、排空时间，不只看 `permit_busy` 次数。

## 6. `run_id` 与上下文传播

`run_id` 是容量诊断能否按夜批窗口读数的前提。

建议：

- `LlmCallRequest` 显式携带 `runId`、`customerId`、`projectId`、`scope`、`promptHash` 等可选观测字段。
- 批处理入口创建 run 时生成 `run_id` 并向下传递。
- MDC/ThreadLocal 只作为兼容补充，不作为唯一来源，避免异步/线程池场景丢上下文。

落地顺序：

1. 先扩展 request 的可选观测上下文字段，默认 null。
2. 基线采集、问题轮询、文章生成等夜批入口逐步填充。
3. facade 对缺失 run_id 的调用仍记录，但归入 `run_id = null` 或 `ad_hoc`，不影响主调用。

## 7. Duration 分段

统一拆成三段：

- `wait_ms`：等待 permit、legacy concurrency permit、内部限流节流等容量等待时间。
- `http_ms`：HTTP transport 纯耗时。
- `total_ms`：从进入 facade 到返回/抛出异常的总耗时。

实现要求：

- legacy path 可在 facade/legacy limiter/http client 分段得到。
- direct/routed path 需要 gateway、invoker、http client 共同产生分段事件；不能只靠 facade 猜。
- 旧 `duration_ms` 继续保持调用方现有语义，新增分段字段只进入观测表，避免行为变更。

## 8. 重叠度埋点

本期只埋点，不做缓存复用。

字段：

- `normalized_prompt_hash`
- `platform_code`
- `model_id`
- `feature`
- `scope`
- `customer_id`
- `project_id`
- `run_id`
- `created_at`

scope 规则：

- scope 必须在问题/模板定义层声明，不从渲染后的 prompt 启发式推断。
- 取值：`GLOBAL` / `INDUSTRY` / `CUSTOMER` / `PROJECT` / `BASELINE_RUN`。
- 默认最严：未声明按 `PROJECT` 或 `CUSTOMER`，只有显式声明才能升为 `INDUSTRY`/`GLOBAL`。
- 基线采集使用 `BASELINE_RUN`，不计入跨项目复用统计，不得用于去重刻意的 N 次重复采样。

`normalized_prompt_hash` 规则待评审：

- 只存 hash，不存 prompt 原文。
- 基础归一化包括空白规整、大小写统一、稳定 JSON 序列化。
- 时间戳、随机 id 等易变片段的剥离规则必须白名单化，避免过度合并不同 prompt。

## 9. 成本埋点

本期只记录 token/成本测量字段，不做账本、预算、告警或超预算策略。

字段：

- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `estimated_cost`
- `currency`
- `customer_id`
- `project_id`
- `feature`
- `platform_code`
- `model_id`

成本估算规则可先为空或按配置表提供；预算管控另列一期。

## 10. 诊断口径

跑一到两个夜批后，按 `run_id + 峰值分钟` 读取 `llm_capacity_minute_metric`：

| 观测 | 下一期倾向决策 |
|---|---|
| `permit_busy` 高、`platform_429` 不高 | 放宽 permit / feature 配额，或批处理改短等释放 worker |
| `platform_429` 高且集中某平台 | 要平台配额/key，或做平台 cooldown + 错峰 |
| `internal_rate_limited` 高 | 检查我方 RPM/TPM 是否过保守 |
| 成本高但无 429/permit busy | 需求侧削减 / 缓存复用 / 预算管控 |

本期只产出数字，不做策略决策。

占用率来源说明：

- run 桶：来自每次 routed 调用成功拿到 permit 后的实时容量信号，带业务 `run_id`，用于分析单次夜批窗口内的占用峰值。
- `ad_hoc` 桶：来自定时容量采样器，独立于具体调用，覆盖调用间隙的全局 active/waiter 曲线。
- 诊断时需要按 minute 同时读取 run 桶和 `ad_hoc` 桶，避免只按 run_id 过滤时漏掉定时采样器的全局曲线。

## 11. 落地顺序

1. 扩展 HTTP 响应与结构化异常元数据，确保 `Retry-After` 可被源头捕获。
2. 增加测量上下文模型，扩展 `LlmCallRequest` 可选观测字段。
3. 建 measurement collector：内存投递、异步批量、失败吞掉。
4. 建 `llm_call_observation`。
5. 建 `llm_capacity_minute_metric` 与分钟聚合器。
6. 在 facade、http client、invoker、router、gateway/legacy limiter 接入只读测量事件。
7. 夜批入口逐步传入 `run_id` / customer / project / scope / prompt hash。
8. 跑夜批并按 run/峰值分钟出诊断读数。

## 12. 明确不在本期范围

- 用 `Retry-After` 或占用率改变退避、路由、时序。
- permit 策略变更。
- 缓存复用或跨租户复用。
- 成本账本、预算、超预算策略、告警。
- 任务容量层和分布式熔断。
