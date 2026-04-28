# 拓词管理 V1.5 阶段一前端对齐纪要

> 目的：#5 前端组件拆分启动前，确认接口契约无法完全覆盖的灰色地带。本文作为 #5 开发执行口径，除非后续评审明确变更，否则前后端按本文推进。

## 结论总览

| 议题 | 最终决策 | 负责人 | 截止时间 |
|---|---|---|---|
| `columns / requiredColumns` 使用方式 | 采纳推荐：前端完全配置驱动，不再硬编码类型规则 | 前端 | #5 开发中 |
| 错误码临时解析 | 采纳推荐：阶段一解析 message，阶段二优先读 `R.errorCode` | 前端 + 后端 | 前端 #5；后端阶段二 |
| `lastEditState` 类型切换 | 采纳推荐：只响应用户主动切换，编辑回填不触发清空 | 前端 | #5 开发中 |
| 历史类型 UI | 采纳方案 B：置灰历史选项 + 允许升级 | 前端 | #5 / #6 |
| 5 个小问题 | 全部采纳推荐 | 前端 + 后端 | #5 / #8 |

## 议题 1：`KeywordTypeConfigVO.columns` 与 `requiredColumns`

**决策**：采纳推荐。前端 `KeywordColumnBuilder` / `CompareKeywordBuilder` 完全消费后端 `KeywordTypeConfigVO`，不再写第二套类型规则。

**执行口径**

- `columns.xxx=true`：整列渲染。
- `columns.xxx=false`：整列不渲染，对应 form 字段不参与提交。
- `requiredColumns.xxx=true`：列展示时显示必填标识，并做提交前校验。
- `areaEnabledByDefault` 只决定地区列展示时开关默认值，不代表地区列是否展示。
- `industryRequired` 指 `industryWords` 行业词列是否必填。
- `functionIndustryRequired` 指功能词专属 `functionIndustryTag` 下拉框是否必填。

**落地要求**

- 前端不得硬编码 `brand` 隐藏地区列、`qa` 隐藏地区列等规则。
- 未来后端新增类型规则字段时，前端优先扩展配置消费，不新增类型 if-else。

## 议题 2：错误码临时 message 解析

**决策**：采纳推荐。阶段一前端实现 `parseErrorCode` 工具函数，优先读 `res.errorCode`，没有时从 `message` 前缀解析。

**已知错误码**

- `BLACKLIST_HIT`
- `QUOTA_EXCEEDED`
- `INVALID_RESULT_KEYWORDS`
- `COMPARE_CORE_A_REQUIRED`
- `COMPARE_CORE_B_REQUIRED`
- `COMPARE_WORD_REQUIRED`
- `FUNCTION_INDUSTRY_REQUIRED`

**执行口径**

- 用户提示文案统一由前端 `ERROR_CODE_HINTS` 维护。
- 后端阶段一 message 可保留开发调试信息，不直接作为用户文案。
- 阶段二后端补结构化 `R.errorCode` 后，前端工具函数保持兼容，只切换优先级。

**后端后续**

- 阶段二清理时扩展 `BizException.errorCode`，由异常处理器透出到 `R.errorCode`。

## 议题 3：`lastEditState` 类型切换

**决策**：采纳推荐。`lastEditState` 只服务用户主动类型切换，不参与编辑回填。

**6 个场景预期**

| 场景 | 操作 | 预期行为 |
|---|---|---|
| 1 | 新建，选 `decision`，填一些前缀词 | form 持续更新，`lastEditState` 不写 |
| 2 | 切到 `transaction` | 写入 `lastEditState['decision'] = snapshot(form)`，清空 form，加载 transaction options |
| 3 | 切回 `decision` | 从 `lastEditState['decision']` 恢复 form |
| 4 | 切到 `comparison`，当前 form 有 `coreText` | 弹确认；确认后清空，展示 A/B/对比词列 |
| 5 | `comparison` 填 coreA + coreB，切回 `decision` | 写入 `lastEditState['comparison']`，恢复 decision 快照 |
| 6 | 编辑老 `search` 组，不切类型 | 不触发 `clearTypeRelatedSelections`，不写 `lastEditState` |

**关键实现要求**

- `lastEditState` 使用独立 `ref`。
- 快照必须深拷贝，不能保存响应式引用。
- `openEdit` 直接 `hydrateForm(detail)`，只调用 `loadOptionsByType(detail.type, detail.functionIndustryTag)`。
- `openEdit` 不复用 `handleUserTypeChange`。
- 类型选择器通过 `@change-by-user` 区分用户点击和父组件赋值。

## 议题 4：历史类型 UI 标识

**决策**：采纳方案 B，置灰历史选项 + 允许升级。

**执行口径**

- 编辑老组时，类型选择器额外显示当前历史类型选项，例如 `搜索词(历史)`。
- 历史选项默认选中，视觉置灰，用于说明当前状态。
- 用户不切类型时，可继续保存，保持老数据可继续执行。
- 用户从历史类型切到新 6 类型时，弹升级确认。
- 列表页展示灰色 `历史` 标签，并展示 `typeLabel`。

**后端字段**

- `KeywordGroupVO.legacyType` 已提供。
- `KeywordGroupListItemVO.legacyType` 已提供。
- `typeLabel` 对历史类型返回：`搜索词(历史)`、`地域词(历史)`、`行业词(历史)`、`竞品词(历史)`。

## 议题 5：5 个小问题

### 5.1 预览 `previewCount` 默认值

**决策**：前端默认 `100`，并显式传 `count=100`。

**说明**：后端 `1000` 是未传兜底，不是前端推荐默认值。

### 5.2 对比词 1000 上限提示

**决策**：采纳推荐。对比词类型使用独立提示：

```text
预计生成 N 条 = 核心词A(X) × 对比词(Y) × 核心词B(Z) × 后缀词(W)，超过 1000 条，请减少任一侧词数。
```

**实现口径**

- 前端在提交 preview 前先根据当前表单本地计算 X/Y/Z/W，优先展示精确提示。
- 后端阶段一已做生成前总量估算，避免 OOM。
- 后端后续可补同口径错误 message 作为兜底，但前端不依赖后端 message 做用户提示。

### 5.3 `subCategory` 渲染

**决策**：按 `subCategory` 分段展示。

**执行口径**

- `subCategory` 有值：作为段落 hint 标题。
- `subCategory` 为空：归入“其他”。
- `visualTag` 作为词后小标签展示。
- `isTemporary=true` 的词仍按所属 `subCategory` 分段，只是词项样式增加临时标识。

### 5.4 功能词 `industryTag` 切换

**决策**：采纳推荐。切换行业后，通用类保留，行业类替换。

**执行口径**

- 重新调用 options，传新的 `industryTag`。
- 已选前缀词中，保留 `industryTag` 为空或 `common` 的词。
- 已选前缀词中，清空不属于新行业且非 common 的词。
- options 接口返回的 `common` 类词始终可用。

### 5.4 补充:function.prefix 的"按行业调用"语义

PRD 4.1.2 描述 function.prefix 是"按行业调用",落地方式:

- `KeywordTypeConfigVO.columns.prefix=true` 表示该列必然渲染
- options 接口必须带 `industryTag` 参数才能返回正确的前缀词
- 后端 `KeywordAffixOptionsService` 按 `industry_tag = :industryTag OR industry_tag = 'common'` 过滤 function.prefix
- 前端在 industryTag 未选择时,function.prefix 列展示 common 类前缀词;行业词在 industryTag 选择后 reload
- industryTag 切换时按议题 5.4 主体规则:保留 common 类前缀词,清空旧行业类

### 5.5 临时词 UI 标识

**决策**：采纳推荐。临时词使用浅黄色背景 + `临时` 小标签。

**执行口径**

- `item.isTemporary=true` 时加 `word-item-temporary` 样式。
- 小标签显示“临时”。
- hover tooltip 显示作用域。
- `scopeType=company` 时展示客户维度文案；暂时没有公司名时显示 `客户ID: scopeId`，后续可接公司名映射。

## 新发现灰色地带

- 无新增阻塞性灰色地带。
- 阶段二待清理项继续保留：错误码结构化、`filterOptionItems` 冗余参数清理、完整黑名单服务替换阶段一占位逻辑。
