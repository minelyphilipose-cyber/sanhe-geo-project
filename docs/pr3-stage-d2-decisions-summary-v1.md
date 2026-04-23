# PR-3.D2 L1/L2/L3 补齐 · 1 页决策摘要

| 字段 | 值 |
|---|---|
| PR 名 | PR-3.D2(真实流水线 L1/L2/L3 补齐实装) |
| 前置 PR | PR-3.F2-B(commit `cfffbcb1`) |
| 决策范围 | D25 ~ D30 共 6 条(见下) |
| 预计工作量 | 代码 1400-1800 行,6-8 天(Codex 实施 + Claude review) |
| 配套产出 | `pr3-stage-d2-l1l2l3-design-v1.md`(实施定稿) |

## 解决的核心问题

交接文档 v4 标 "PR-3 全部合主干",但代码证实 `PresaleGenerateOrchestrator.runRealSkeletonFlow` 在 BATCH1/BATCH2 之后 **L1/L2/L3 三阶段只切 stage 字段、不做业务**,最终 `markFailed(STAGE_D_CHECKPOINT)`。真实 LLM 流水线 100% 落 FAILED。

本 PR 补齐三层业务,让真实流水线能走到 DONE,解除真 LLM 联调的后端阻塞。

## 六条业务决策(D25-D30)

### D25 · scores 评分体系

五维(全 Double 0-100) + 综合得分:

- **mention** = sum(mention_count) / sum(total_tests) × 100 ·batch1 全局扁平率
- **ranking** = 加权分(1→90, 2→80, 3→60, 4→40, 5→20, ≥6→0)/ 被提及数·ranking IS NULL 的行不计入分母
- **sentiment** = (positive×1.0 + neutral×0.5 + negative×0) / total × 100 ·batch1 统计
- **coverage** = (高 covered×2 + 中 covered×1.5 + 低 covered×1) / (高 total×2 + 中 total×1.5 + 低 total×1) × 100 ·显式加权
- **overall** = mention×0.30 + ranking×0.25 + sentiment×0.15 + coverage×0.30

边界:四维分母为 0 → 各维 = 0,overall 按公式自然为 0。档位映射(高/中/低)沿用 `Enricher.defaultBusinessValue` 已有代码。精度:存库 Double,对外前端 Math.round 取整。

### D26 · scene_coverage + intent_breakdown 同源覆盖

- **"一个 prompt 算 covered"**:有效平台(9 个 enabled 减去 degraded_platforms)中 ≥ ceil(effective/2) 个 is_mentioned=1
- **计数单位**:按 prompt 模板(不按 cell),只统计 batch1 通用 prompt(has_competitor_var=0)
- **scene_coverage.{high/mid/low}.covered_queries**:填 `{prompt_code, prompt_content, category}`
- **missing_queries.top_competitor_coverage**:取本版本已抽出的 top3 竞品,列出该 prompt 下被提及过的(最多 3 个,归一化匹配)
- **intent_breakdown 与 scene_coverage 完全同源**:数值恒等,改动点:`PlatformIntentBreakdownBuilder.resolveIntentTotalPromptsFromTemplate` 里 `base × competitorCount` 那段删除

### D27 · roi_simulation · phase 分数模型

固定升幅 + clamp 到 100:
- phase1.target = min(current + 5, 100) · duration_label="M1"
- phase2.target = min(current + 12, 100) · duration_label="M2-3"
- phase3.target = min(current + 20, 100) · duration_label="M4-6"
- uplift_from_previous = target[i] - target[i-1](phase1 的上一阶段是 current_score)

不依赖 benchmark。边界:高分品牌(如 current=95)phase2/3 target=100,uplift 自动=0。

### D28 · optimization_findings 分配到 phase

按 priority 一维分:
- HIGH → phase 1
- MEDIUM → phase 2
- LOW / NULL → phase 3

`completed_optimization_count` 新报告统一 = 0,`total_optimization_count` 按分到的 findings 条数填。

### D29 · benchmark 数据源(方案 B)

classpath 资源 `geo-server/src/main/resources/benchmarks/v1.json`,启动时加载到内存 Map。两级回退:`(industry, industry_role) → (industry, _ALL_) → (_ALL_, _ALL_) → BENCHMARK_MISSING`。最后一级永远命中(V1 内置兜底行)。

BENCHMARK_MISSING 归 `CONFIG_MISSING` 分类(retry 不可恢复,需补 JSON)。

### D30 · roi_simulation 顶层字段

- `current_score` = L2.scores.overall
- `target_score` = phase3.target_score(即 min(current+20, 100))
- `estimated_uplift_percent` = (target - current) / current × 100(current=0 时兜底 0)
- `estimated_exposure_multiplier` = 1.8(硬编码,MVP 占位)

## 三条必须同步改动的既有代码

1. `PresaleGenerateOrchestrator.runRealSkeletonFlow` → 改名 `runRealFullFlow`,删除 L1/L2/L3 三行 skeleton + 末尾 `markFailed(STAGE_D_CHECKPOINT)`,替换为真实装配 + `markDone`
2. `PlatformIntentBreakdownBuilder.resolveIntentTotalPromptsFromTemplate` → 删除 `base × competitorCount` 分支(batch2 不进 total_prompts)
3. `PlatformIntentBreakdownBuilderTest` → 含 C 乘法断言的测试同步修正,spec v4 §10.1 S14 验收条款同步改动

## 新增类

- `PresaleRawSnapshotAssembler` · L1 装配 · 约 500 行
- `PresaleBenchmarkResolver` · classpath JSON 加载 + 两级回退 · 约 100 行

## 不做的事(明确延后)

- `PresaleComputedSnapshotEnricher` 签名改造(保持现状,真实流水线传 `"{}"` 作为 computed input)
- `PresaleL3InitService` 签名改造(保持现状)
- BATCH1 / BATCH2 / COMPETITOR_EXTRACT / Preflight 逻辑(完全不动)
- `STAGE_D_CHECKPOINT` / `SNAPSHOT_BUILD_ERROR` 常量删除(保留以防回退,P1 清理)
- 评分公式的业务合理性 Review(本轮 Claude 基于用户拍板的意图翻译,未经产品深度 review;如演示时客户追问,按本页公式如实答复;P1 做产品侧公式 review)

## 风险提示

1. **benchmark JSON 内容**:本 PR Claude 出模板 + 示范值(零售/金融/教育/医疗/科技 + `_ALL_` 兜底共 6 行),需要你或产品过目确认。示范值是凭业内经验拍的,不是真实行业数据
2. **scores 公式**:所有权重和阈值(如 ranking 的 90/80/60/40/20/0)是用户(您)基于业务意图拍板,非产品经过的评审数据。演示时能解释"这是 MVP 设计,P1 会做产品侧公式重审"
3. **exposure_multiplier = 1.8**:硬编码占位,演示时所有品牌都是 1.8 倍,客户问到要能解释

## Codex 实施建议的 5 个 checkpoint

Checkpoint 1(0.5 天)· Benchmark 基础设施
- `PresaleBenchmarkResolver` + `benchmarks/v1.json`(Claude 提供的模板)+ 单元测试 3 条(EXACT / FALLBACK_INDUSTRY / MISSING)

Checkpoint 2(2-2.5 天)· Raw Snapshot Assembler
- 新增 `PresaleRawSnapshotAssembler`,7 个子字段装配逻辑全部落地 + 单元测试 ≥ 4 条
- 含 `Batch1/Batch2ExecutionResult` 回传 `degradedPlatforms` 的签名改动

Checkpoint 3(1.5 天)· L2 Enricher 改造 + scores/scene_coverage 计算
- 在 `PresaleComputedSnapshotEnricher` 里增加 scores / scene_coverage / roi_simulation 的计算(可新建专门的 `ScoresCalculator` / `SceneCoverageCalculator` / `RoiCalculator` 三个辅助类)
- 接通 `PresaleRuleEngineExecutor.execute(l1, l2)`,产出 `optimization_findings`
- `PlatformIntentBreakdownBuilder` 的 C 乘法删除 + 测试同步修正

Checkpoint 4(1 天)· Orchestrator wiring
- `runRealSkeletonFlow` → `runRealFullFlow`,3 行 skeleton + `markFailed(STAGE_D_CHECKPOINT)` 替换为真实调用 + `markDone`
- 新增 `writeRawSnapshotJson` / `writeComputedSnapshotJson` / `writeEditableContentJson`
- 新增常量 `FAILURE_CATEGORY_L1_SERIALIZATION_ERROR` / `L2_COMPUTE_ERROR` / `L3_INIT_ERROR`

Checkpoint 5(1 天)· 端到端测试 + 现有测试修正
- 新增 `realFullFlow` 系列测试 ≥ 6 条(见定稿 §7.2)
- 修正所有含 `STAGE_D_CHECKPOINT` 断言的旧测试

每个 checkpoint 之间 Claude review 通过才进下一个,不允许一次性实施。

## 本 PR 完成后立即启动的工作

- 真 LLM 联调(原 Q-1 本轮目标)
- PR-5 前端剩余联动(18 页 PDF 渲染校验、retry/regenerate UI 接入)
