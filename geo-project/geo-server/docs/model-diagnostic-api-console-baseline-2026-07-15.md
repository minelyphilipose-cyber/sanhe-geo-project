# 大模型诊断台后端 API 与前端页面阶段基线

日期：2026-07-15
阶段：后端诊断 API 与前端诊断台
结论：实现完成，提交阶段评审

## 1. 阶段范围

本阶段在已签收的 Codec、数据库/RBAC、诊断执行器、Redis许可与状态恢复、历史与清理能力之上增加管理端入口，不改变生产问题池轮询链路。

已交付：

- 管理端平台能力目录、服务端探针目录；
- 同步诊断执行接口；
- 当前操作人范围内的历史列表、详情和会话恢复接口；
- Vue 3 大模型诊断台、路由、菜单与权限接入；
- 基础对话、联网搜索、标准探针和生产问题模板的表单契约；
- 诊断结论、能力项、来源、引用、usage及脱敏请求/响应展示。

本阶段未实现SSE、WebSocket、后台异步排队、四渠道真实Smoke或平台健康状态回写。启动校验期间发现开发库已执行的V315曾被后续修改，现已恢复V315不可变内容，并通过V316承接后续数据库契约补全。

## 2. HTTP契约

统一前缀：`/api/admin/model-diagnostics`

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/platforms` | 返回可诊断配置及能力、主凭证可用性，不返回任何凭证值 |
| GET | `/probes` | 返回服务端冻结的探针代码和实际版本 |
| POST | `/runs` | 同步创建或幂等重放一次诊断 |
| GET | `/runs` | 当前操作人轻量历史分页 |
| GET | `/runs/{id}` | 当前操作人单次诊断详情 |
| GET | `/sessions/{sessionId}/runs` | 当前操作人会话恢复 |

所有入口最终执行`ai.platform.diagnose`权限校验。非法UUID、字段矩阵冲突和不兼容探针返回HTTP 400；不存在或不归属当前操作人的记录返回HTTP 404；相同幂等ID但客户端语义指纹不同返回HTTP 409。

外部执行请求固定使用`mode`字段，例如`{"mode":"WEB_SEARCH"}`；内部Java命令继续使用`diagnosticMode`。MockMvc以真实JSON绑定验证`mode`，并覆盖400、403、404和409 HTTP状态。

平台目录只暴露平台名称、渠道、模型、集成类型、启用状态、支持模式、同步响应模式和不可用原因。历史列表使用独立API白名单投影，不暴露`operatorId`；详情不暴露配置快照、配置哈希、端点、请求指纹或原始密钥。

## 3. 请求字段矩阵

| testMode | userMessage | systemPrompt | probeCode |
|---|---|---|---|
| `FREE_CHAT` | 必填 | 可选 | 禁止 |
| `STANDARD_PROBE` | 禁止 | 禁止 | 必填，服务端解析版本及文本 |
| `PRODUCTION_POLL_TEMPLATE` | 由服务端探针的`inputMode`决定 | 禁止 | 必填，服务端解析版本及模板 |

Controller层继续分别传递`clientUserMessage`和`resolvedUserMessage`。固定文本及服务端版本不进入客户端幂等指纹；用户必填模板仅以规范化后的客户端原文参与指纹。

生产问题池联网调用与`PRODUCTION_POLL_TEMPLATE`共同引用`QuestionPollPromptTemplate.SYSTEM_PROMPT`及`QuestionPollPromptTemplate.VERSION`。诊断目录不再维护第二份生产提示词；契约测试同时锁定生产代码引用、诊断文本和模板版本。

直接输入最长8000字符。供应商实际文本包含system、本轮user及历史user/assistant；服务端按最近完整成功自由对话轮次向前选取，在最多20条消息的既有约束上增加30000字符总门禁。超出预算时丢弃更早的完整轮次，不截断单条消息。

## 4. 前端行为

- 菜单：监控中心 → 大模型诊断台；
- 权限：`ai.platform.diagnose`；
- 页面按“配置诊断任务 → 发起验证对话 → 查看诊断结论”组织，技术配置、交互主任务和结果判定形成明确层级；
- 平台渠道、基础/联网能力、具体模型配置、测试模式和服务端探针联动；
- 一期固定同步调用并显式展示响应方式；Axios超时200秒；Vite开发代理、容器Nginx和主机Nginx仅对`/api/admin/model-diagnostics`使用210秒超时，普通API仍保持120秒；
- 自由对话支持可选系统提示词并随本轮请求提交；
- 仅提交当前轮用户输入，历史上下文由服务端按成功自由对话重建；
- 切换渠道、能力、模型、测试模式或新建会话时，如已有对话必须显式确认；
- 历史会话可恢复，失败/拒绝/废弃轮次保留审计展示，但服务端不会把它们加入后续模型上下文；
- 来源链接仅允许`http/https`，并使用`noopener noreferrer`；回答和脱敏报文均按纯文本展示，不执行供应商HTML；
- 来源卡展示完整规范化URL；引用明细展示回答位置、来源序号映射及置信度；
- 结构化展示输入、输出、总Token及搜索工具调用次数；
- 结论、执行状态、搜索状态及能力枚举使用中文产品语义展示；基础对话不展示无关的联网搜索指标；
- 用户消息宽度随内容自适应；窄屏将诊断结论移至对话下方，不再隐藏关键结果；
- HTTP异常或`FAILED/REJECTED/ABANDONED/RUNNING`结果会把本轮文本恢复到输入框，成功结果才保持清空；
- 详情抽屉只展示脱敏请求和脱敏响应，不再序列化整个Run。

## 5. 自动化验证

### 5.1 诊断模块专项

命令：

```text
mvn -Dtest='com.huanjing.geo.module.system.modeldiagnostic.**.*Test' test
```

结果：

```text
Tests run: 103, Failures: 0, Errors: 0, Skipped: 19
BUILD SUCCESS
```

19项为普通模式下明确跳过的真实MySQL/Redis集成测试；既有`mysql-it`和`redis-it`强制门禁保持不变，不会静默假绿。V315已恢复为数据库实际执行过的不可变版本，CRC32校验值固定为`459344448`；后续契约差异由V316顺序迁移，不修改Flyway历史记录。

新增自动化证据覆盖：

- 三种测试模式请求矩阵及服务端版本解析；
- 客户端原始输入与服务端解析文本分离；
- 平台能力目录及严格主凭证可用性；
- nullable能力状态、JSON降级和不可变详情投影；
- 历史API投影不暴露操作人身份；
- 最近完整轮次的30000字符上下文预算。
- 外部`mode`绑定和400/403/404/409 Controller契约；
- 生产Dispatch与诊断目录的提示词、模板版本唯一来源。

### 5.2 生产联网兼容回归

命令：

```text
mvn -Dtest='com.huanjing.geo.module.dispatch.websearch.**.*Test' test
```

结果：

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

三套请求Golden、13组完整响应Snapshot、生产路由、审计和结果语义保持兼容。

### 5.3 前端交互测试门禁

命令：

```text
npm run test:run
```

结果：

```text
Test Files: 3 passed
Tests: 14 passed
```

已引入Vitest 2、Vue Test Utils 2、jsdom、`npm run test`和`npm run test:run`。测试覆盖`mode`与200秒Axios超时、Vite诊断代理210秒且普通API代理120秒、两份Nginx诊断路径210秒且普通路径120秒、安全URL、按钮状态、系统提示词提交、Token/来源/引用展示、模式切换确认及清理、失败输入恢复、会话历史恢复、仅展示脱敏报文，以及诊断能力枚举的中文产品化展示。

### 5.4 前端生产构建

命令：

```text
npm run build
```

结果：

```text
vue-tsc -b：通过
vite build：3061 modules transformed
BUILD SUCCESS
```

现有主包大于500kB和PostCSS旧插件提示为项目既有构建警告，不阻断本阶段页面按路由懒加载交付。

## 6. 阶段边界核查

- 数据库最高迁移为V316；V315保持不可变，后续差异由V316承接；
- 未执行Flyway repair、未手工修改`flyway_schema_history`；开发库已通过正常Flyway启动流程顺序执行V316；
- 未增加SSE、流式事件协议或断线重连；
- 未调用供应商执行四渠道Smoke；
- 未修改生产Dispatch路由、重试、统计或健康状态；
- 未暂存、提交或覆盖工作区中的其他未提交变更。

下一阶段在本阶段评审签收后进入四渠道Smoke。Smoke应使用专用测试问题和受控账号，分别验证基础生成、联网证据、来源/引用、搜索未确认、鉴权失败及超时边界，并禁止回写生产平台健康状态。
