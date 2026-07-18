# 联网搜索 Codec 技术验证报告

日期：2026-07-15
里程碑：Codec 技术验证
结论：通过

## 1. 交付范围

本里程碑仅拆分联网搜索协议编解码边界，不新增数据库迁移、诊断业务接口或前端页面。

统一协议契约：

- `WebSearchCodecRequest`：模型、标准化多轮文本消息、供应商配置快照。
- `WebSearchMessage`：仅允许 `system/user/assistant`，并在传输前拒绝空消息和非法角色。
- `WebSearchResponse`：继续作为三渠道统一解析结果，字段和既有业务语义不变。
- `WebSearchCodec`：仅负责 `encode/decode`，不访问凭证、网络、数据库或审计服务。

三套协议实现：

- `VolcengineResponsesWebSearchCodec`
- `DashScopeNativeWebSearchCodec`
- `TencentTokenHubResponsesWebSearchCodec`

## 2. 生产传输与审计适配

生产入口仍为 `WebSearchAdapter.execute(WebSearchRequest)`。`AbstractJsonWebSearchAdapter` 只增加 Codec 组合，原调用顺序保持为：

1. 将生产轮询请求转换为统一 Codec 输入；
2. Codec 生成供应商请求体；
3. `WebSearchProviderCallExecutor.postJson` 解析凭证、检查 deadline、创建 `ProviderCall` 审计并执行 HTTP；
4. Codec 解析同步响应；
5. 成功时调用 `completeSuccess`；
6. 响应解析失败时调用 `completeParseFailure`；
7. HTTP、鉴权、限流、超时等供应商异常继续原样向上抛出。

生产轮询仍只转换原有 `systemPrompt + originalQuestion` 两条消息，因此新增的多轮 Codec 输入能力不会改变当前生产请求体。

## 3. Fixture 拆分前后对比

测试不再抽样断言状态或首个引用，而是将实际结果序列化为 JSON 树，与独立 golden snapshot 做整体等价比较。比较范围覆盖：

- `providerRequestId`、`requestedModelId`、`responseModelId`；
- `answer`、`searchStatus`、`generationSkipped`、`finishReason`；
- `searchEvidence` 全字段、数量及数组顺序；
- `sources` 全字段、数量及数组顺序，包括原始 URL、规范化 URL 和域名；
- `citations` 全字段、数量及数组顺序，包括编号、来源位置、回答位置、文本、置信度和校验状态；
- `usage` 完整对象。

JSON 对象字段书写顺序不影响比较，数组顺序参与比较。任一标准字段增加、删除或值变化都会导致测试失败。

| 渠道 | Fixture | 完整响应 snapshot | 结果 |
|---|---|---|---|
| 火山 Responses | success | `golden/responses/volcengine/success.json` | 全字段一致 |
| 火山 Responses | not_confirmed | `golden/responses/volcengine/not_confirmed.json` | 全字段一致 |
| 火山 Responses | empty | `golden/responses/volcengine/empty.json` | 全字段一致 |
| 火山 Responses | invalid_source | `golden/responses/volcengine/invalid_source.json` | 全字段一致 |
| DashScope Native | success | `golden/responses/dashscope/success.json` | 全字段一致 |
| DashScope Native | not_confirmed | `golden/responses/dashscope/not_confirmed.json` | 全字段一致 |
| DashScope Native | empty | `golden/responses/dashscope/empty.json` | 全字段一致 |
| DashScope Native | invalid_source | `golden/responses/dashscope/invalid_source.json` | 全字段一致 |
| DashScope Native | valid_source_without_citation | `golden/responses/dashscope/valid_source_without_citation.json` | 全字段一致 |
| TokenHub Responses | success | `golden/responses/tokenhub/success.json` | 全字段一致 |
| TokenHub Responses | not_confirmed | `golden/responses/tokenhub/not_confirmed.json` | 全字段一致 |
| TokenHub Responses | empty | `golden/responses/tokenhub/empty.json` | 全字段一致 |
| TokenHub Responses | invalid_source | `golden/responses/tokenhub/invalid_source.json` | 全字段一致 |

请求侧对 `golden/requests/volcengine.json`、`dashscope.json` 和 `tokenhub.json` 同样执行完整 JSON 树比较，覆盖模型、消息全文和顺序、工具参数、非流式开关以及全部渠道配置字段。

## 4. 测试报告

联网搜索专项：

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
```

服务端全量回归：

```text
Tests run: 1679, Failures: 0, Errors: 0, Skipped: 3
BUILD SUCCESS
```

执行命令：

```text
mvn -Dtest='com.huanjing.geo.module.dispatch.websearch.**.*Test' test
mvn test
```

## 5. 代码审查结论

- 三套 Codec 均为无副作用协议层，可被后续诊断执行器复用。
- 生产渠道路由仍由原 Adapter 的 `IntegrationType` 暴露，未新增或改变路由枚举。
- 三套完整请求 JSON 与 13 组完整响应 snapshot 均通过 JSON 树等价比较，冻结字段不存在抽样验证缺口。
- 物理调用的凭证解析、deadline、HTTP 错误分类、原始/脱敏报文审计均未迁入 Codec，职责边界保持清晰。
- 成功审计和解析失败审计均有适配层回归测试。
- 未创建 V315，未新增诊断表、诊断接口、Redis 许可逻辑或前端页面。

因此，Codec 技术验证五项交付物全部满足，可进入下一准入评审；后续阶段仍应按照冻结方案单独实施状态机、幂等、Redis Cluster 同 slot 许可和 deadline 竞态处理。
