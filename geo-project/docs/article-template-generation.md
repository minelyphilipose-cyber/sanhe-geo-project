# 单篇模板生成接口说明

本文档用于区分 AI 撰写页内的两条模板生成路径，避免运营和开发误用。

## 模板试写

- 接口：`POST /api/content/articles/template-preview`
- 行为：真实调用模型，返回标题、正文、prompt/input 快照、质量检查结果。
- 落库：不创建正式文章草稿。
- 用途：在批量生成前检测模板渲染、模型输出和质量风险；也用于 AI 撰写页的“生成草稿”，生成后可人工编辑再保存。

## 模板生成并保存

- 接口：`POST /api/content/articles/template-generate`
- 行为：与模板试写使用同一套 strict prompt 上下文装配，真实调用模型。
- 落库：创建正式 `article_draft` 和首个 `article_draft_version`。
- 生成来源：版本 `generated_by=template_ai`。
- 模板来源：文章 `allocation_mode=custom`，`template_source=custom`，与批量“手动指定模板”语义一致。
- 用途：AI 撰写页的“生成并保存”，适合无需人工改稿时直接生成正式文章。

## 共同约束

- 不影响 `/api/content/articles/ai-draft` 和 `/api/content/articles/ai-draft/preview`。
- 必须锁定具体 `promptTemplateVersionId`。
- 模板、版本、渠道、文章类型校验失败时直接报错，不 fallback。
- `contentStyle` 不由前端传入，由后端按 `channelGroupCode/channelSubCode` 推导。
- 前端不暴露 `topicSource`，单篇模板生成也不暴露关键词组选择；有项目核心关键词时优先使用核心关键词，没有核心关键词时由后端从项目绑定拓词组的 A 类问题中随机取 5 条作为 `relatedKeywords`。
- 批量生成保留“题目来自哪个拓词组”的任务记录，但 prompt 里的 `relatedKeywords` 与单篇模板生成同源：核心关键词优先，否则项目级 A 类问题随机 5 条，不再限定到当前题目所属拓词组。
- 试写和单篇模板生成都代表批次首篇；同配置批量生成时，后续篇目的回答角度和时间锚点会轮换。
