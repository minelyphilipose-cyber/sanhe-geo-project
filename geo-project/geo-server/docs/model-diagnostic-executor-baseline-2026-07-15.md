# 模型能力诊断台：诊断专用执行器基线

日期：2026-07-15
阶段：诊断专用执行器
数据库基线：V315（本阶段未新增迁移）

## 阶段范围

本阶段实现：

- 指定 `platformConfigId` 的同步诊断调用；
- OPENAI_CHAT 基础对话和三类联网 Codec 调用；
- 严格主凭证解析、单次 HTTP 请求及端到端 deadline；
- 配置快照、请求指纹、服务端会话上下文和 RUNNING 创建；
- 诊断结论计算、条件终态更新及迟到结果 ABANDONED；
- 脱敏请求、响应和标准结果落库。

本阶段不实现：

- Redis 全局/操作人许可；
- ABANDONED 定时扫描和进程重启恢复；
- Controller、外部 DTO、历史接口和清理任务；
- 前端页面、SSE 和真实渠道 Smoke。

在 Redis 许可阶段完成前，`ModelDiagnosticExecutionCoordinator` 不应暴露给外部接口。

## 无副作用执行边界

诊断执行器只依赖：

```text
LlmHttpClient
纯 WebSearchCodec
PlatformCredentialService
诊断 Session/Run Mapper
```

它不依赖也不调用：

```text
OpenAiCompatibleLlmInvoker
LlmExecutionGateway / 生产 permit
Router / CircuitBreaker
AiPlatformHealthMonitorService
WebSearchProviderCallExecutor
PollProviderCall / 问题池结果 / 正式统计
```

因此不存在自动重试、生产健康状态写入或生产调用审计副作用。每个诊断 Run 最多发起一次供应商 HTTP 请求。

## 执行顺序

```text
按 operatorId + clientRequestId 查询幂等重放
→ 新请求才解析指定配置（允许 disabled）
→ 独立事务再次检查并发幂等、创建/锁定会话
→ 创建并提交 RUNNING 幂等占位
→ 取得诊断业务许可
→ 按当前 turnNo 重建并固化供应商上下文
→ 严格解析主凭证
→ Codec 编码
→ 单次同步 HTTP
→ Codec/基础对话解析
→ 能力判定
→ 独立事务条件写终态
```

### 配置与凭证

- 只加载请求指定的 `platformConfigId`，不路由、不回退；
- 诊断允许配置处于 disabled，但地址、模型和协议必须有效；
- 配置了 `primaryKeyRef` 时只解析该引用，解析失败不回退数据库 API Key；
- 未配置主引用时才允许严格解密数据库 API Key；
- 不读取 `backupKeyRef`、备用地址或备用模型；
- 落库快照不包含凭证明文，providerConfig 中的敏感字段会脱敏。

### 消息与会话

- `systemPrompt` 与会话历史独立保存；服务端重建的上下文不含 system role，Gateway 编码供应商请求时再按协议注入；
- `FREE_CHAT` 仅重建既往 `testMode=FREE_CHAT + SUCCEEDED + generation=PASS + answer非空` 的 user/assistant 对；
- 上下文在取得业务许可后查询，只取 `turn_no < 当前轮次` 的最近9个成功自由对话轮次并按时间正序重建；18条历史、当前用户消息和可选system消息合计最多20条供应商消息；
- FAILED、REJECTED、ABANDONED 不进入模型上下文，但继续占用审计 turn；
- `STANDARD_PROBE` 和 `PRODUCTION_POLL_TEMPLATE` 使用隔离的单条用户输入，不拼接历史；
- 生产模板显式区分 `inputMode=FIXED|USER_REQUIRED`：`clientUserMessage` 只保存客户端语义输入，`resolvedUserMessage` 始终用于供应商调用并写入 V315 的 `user_message` 审计字段；
- `request_messages_json` 由服务端生成，不信任前端历史；RUNNING占位初始为空数组，许可后才条件写入实际供应商消息快照。

### 幂等

请求指纹覆盖：

```text
sessionId
platformConfigId
diagnosticMode / testMode
probeCode
FREE_CHAT: systemPrompt / userMessage
PRODUCTION_POLL_TEMPLATE: inputMode
PRODUCTION_POLL_TEMPLATE + USER_REQUIRED: clientUserMessage
```

- 文本统一执行首尾去空白、CRLF/CR转LF后再计算 SHA-256；
- 服务端实际解析的 `probeVersion`、`templateVersion` 及探针生成文本不参与客户端幂等指纹；
- `FIXED` 禁止客户端输入，服务端生成的 `resolvedUserMessage` 不参与指纹；模板升级改变生成文本时合法重放仍返回原 Run；
- `USER_REQUIRED` 要求客户端输入，指纹只使用规范化后的 `clientUserMessage`，不使用模板包装后的解析文本；
- 查询已有 Run 发生在实时平台配置解析之前，配置删除、停用或协议变化不影响合法重放；
- 同一操作人、同一 `clientRequestId`、相同指纹：返回已有 Run，不再次调用供应商；
- 同一 ID、不同指纹：抛出 `ModelDiagnosticIdempotencyConflictException`，后续接口层映射为409。

### Deadline 与终态

- `deadline_at` 在 RUNNING 创建时按180秒一次性固化；
- 供应商 request timeout 不得超过剩余 deadline；
- 终态事务先通过 `SELECT ... FOR UPDATE` 等待并取得 Run 行锁，再执行条件更新，保证锁等待时间计入deadline；
- SUCCEEDED/FAILED 更新均要求：

```sql
WHERE id = ?
  AND status = 'RUNNING'
  AND deadline_at > NOW(3)
```

- `completed_at` 和 `duration_ms` 同样使用数据库 `NOW(3)` 计算，不使用进入持久化前捕获的应用时间；
- 终态更新影响0行时重新读取；已终态则返回现状，已超时则主动条件更新为 ABANDONED；
- 扫描器先写 ABANDONED 后，迟到响应不能覆盖；
- 最终落库本身异常时不转写 FAILED，Run 保持 RUNNING，留给下一阶段恢复扫描。

## 诊断结论

- HTTP 2xx且协议解析完成时，执行状态为 SUCCEEDED，诊断结论仍可为 PASS/WARNING/FAIL；
- 基础对话的 webSearch/sourceParsing/citationParsing 为 NOT_APPLICABLE；
- WEB_SEARCH 未确认搜索时：`status=SUCCEEDED, conclusion=FAIL, webSearch=FAIL`；
- 搜索为空：webSearch PASS、sourceParsing WARNING；
- 搜索发生但无有效来源：webSearch PASS、sourceParsing FAIL；
- 有有效来源但无 CONFIRMED 引用：citationParsing WARNING；
- 任一适用能力 FAIL 决定综合 FAIL，无 FAIL 且有 WARNING 决定 WARNING。
- FAILED 只记录已经确认的能力：401/403为authentication FAIL，HTTP 2xx后解析失败为authentication PASS；未执行或无法确认的能力保持NULL；NOT_APPLICABLE只用于当前模式本身不适用的能力。

## Mapper 扫描边界

诊断 Mapper 由模块内 `ModelDiagnosticMapperConfiguration` 独立扫描，不再向 `GeoApplication` 增加诊断包。工作区中原有的 `com.huanjing.geo.common.llm.measurement` 扫描项不属于本阶段，本阶段未删除或改写该项。

## 验证结果

诊断执行器与持久化专项：

```text
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

生产联网 Codec/审计兼容回归：

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

强制 MySQL 8/Flyway/Mapper 门禁：

```text
命令：mvn -Pmysql-it test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

MySQL 门禁真实覆盖 V315、组合身份、幂等/turn 唯一约束、FREE_CHAT上下文过滤与9轮上限、未执行能力NULL落库、会话 Mapper、按时终态、行锁等待跨deadline拒绝和 ABANDONED 条件更新。

应用上下文 Mapper 扫描回归：

```text
命令：mvn -Dtest=PresaleAiPromptResultMapperIntegrationTest -Dspring.flyway.enabled=false test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

首次全量回归暴露出嵌套诊断 Mapper 包未被应用 `@MapperScan` 覆盖；现已显式注册并由上述应用上下文测试验证。全量回归同时发现本机开发库曾执行过旧内容的 V315，当前文件与库内 checksum 不一致。未对开发库执行 `flyway repair` 或其他变更；因此本报告不宣称本机全量测试已恢复绿色，待开发库基线按团队流程处理后需再复跑。
