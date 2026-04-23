# PR-3.D2 L1/L2/L3 补齐 · 1 页决策摘要 · v2

> **版本说明**:本文档是 v1 的实装后修订版,整合 5 个 Checkpoint 的代码落地现状与 Claude/Codex review 累计的决策澄清。v1 作为历史存档保留,v2 是 PR-3.D2 完成后的**单一真相源**。
>
> 主要修订方向:
> 1. D25-D30 决策摘要里**与代码实装不符**的表述,以代码为准
> 2. D25-D30 决策摘要里**未完整表达**的边界语义(例如 D26 的 SQL 层 filter、D28 的 null 双重兜底)
> 3. v1 "不做的事"里**实装中又做了**的项(例如 L2 传 currentComputedJson 复用)
> 4. 实装后累计的 C5 清理清单(5 条小观察)和意外基础设施修复(@Autowired)

| 字段 | 值 |
|---|---|
| PR 名 | PR-3.D2(真实流水线 L1/L2/L3 补齐实装) |
| 前置 PR | PR-3.F2-B(commit `cfffbcb1`) |
| 最终 commit(C5 之后) | 待用户合主干后回填 |
| 决策范围 | D25 ~ D30 共 6 条 |
| 实际工作量 | 代码约 2100 行(含测试),5 个 Checkpoint 分段交付 |
| 配套产出 | `pr3-stage-d2-l1l2l3-design-v2.md`(实装定稿 v2) |
| 测试数 | `mvn test` 151/151 绿(含 2 条 @SpringBootTest 集成测试) |

## 解决的核心问题

交接文档 v4 标 "PR-3 全部合主干",但代码证实 `PresaleGenerateOrchestrator.runRealSkeletonFlow` 在 BATCH1/BATCH2 之后 **L1/L2/L3 三阶段只切 stage 字段、不做业务**,最终 `markFailed(STAGE_D_CHECKPOINT)`。真实 LLM 流水线 100% 落 FAILED。

本 PR 补齐三层业务,让真实流水线能走到 DONE,解除真 LLM 联调的后端阻塞。**PR-3.D2 完成后的现状**:`mockEnabled=false` 分支可端到端走通 L1/L2/L3,真 LLM 联调的后端阻塞已解除。

## 六条业务决策(D25-D30)· 以实装为准

### D25 · scores 评分体系

五维(全 Double 0-100) + 综合得分:

- **mention** = sum(mention_count) / sum(total_tests) × 100 · batch1 全局扁平率
- **ranking** = 加权分(**1→90**, 2→80, 3→60, 4→40, 5→20, ≥6→0)/ 被提及数 · ranking IS NULL 的行不计入分母
- **sentiment** = (positive×1.0 + neutral×0.5 + negative×0) / total × 100 · batch1 统计
- **coverage** = (高 covered×2 + 中 covered×1.5 + 低 covered×1) / (高 total×2 + 中 total×1.5 + 低 total×1) × 100 · 显式加权
- **overall** = mention×0.30 + ranking×0.25 + sentiment×0.15 + coverage×0.30

边界:四维分母为 0 → 各维 = 0,overall 按公式自然为 0。档位映射(高/中/低)在 `PresaleIntentCode` 枚举的 `businessValue()` 方法(C3 实装时抽出,而非沿用 `Enricher.defaultBusinessValue`)。

**v2 修订点**:
- `Scores` DTO 有 `Weights` 内部类,`ScoresCalculator` 填 D25 默认权重 `{0.30, 0.25, 0.15, 0.30}`(v1 原文未写明)
- ranking 数值边界在 C3 实装时明确:ranking=1 → 90(**不是** 100),全 rank1 场景下 ranking 得分上限是 90 分
- 精度:存库 Double,对外前端 Math.round 取整(**v2 澄清**:Math.round 是前端职责,后端 DTO 保留 Double 原值)
- `ranking <= 1` 防御写法在 C5 改为 `ranking == 1`(DB 异常数据 ranking=0 不再夸大到 90)

### D26 · scene_coverage + intent_breakdown 同源覆盖

- **"一个 prompt 算 covered"**:有效平台(9 个 enabled 减去 degraded_platforms)中 ≥ ceil(effective/2) 个 is_mentioned=1
- **计数单位**:按 prompt 模板(不按 cell),只统计 batch1 通用 prompt(has_competitor_var=0)
- **scene_coverage.{high/mid/low}.covered_queries**:填 `{prompt_code, prompt_content, category}`
- **missing_queries.top_competitor_coverage**:取本版本已抽出的 top3 竞品,列出该 prompt 下被提及过的(最多 3 个,归一化匹配)
- **intent_breakdown 与 scene_coverage 完全同源**:数值恒等

**v2 修订点(重要)**:

1. **"同源"是 SQL 层 + Java 层双重保障**(v1 原文只描述改动点"`base × competitorCount` 删除"):
   - **SQL 层**:`PresaleAiPromptResultMapper.selectTemplateIntentStats()` 必须 `WHERE enabled=1 AND has_competitor_var=0`(C3 review 发现 v1 SQL 只有 `enabled=1`,C3 SQL 补修加上 `has_competitor_var=0`)
   - **Java 层**:`SceneCoverageCalculator` 查 `PresalePromptTemplate` 时也加 `has_competitor_var=0` 过滤
   - 两层都过滤,才保证 `intentTotalPrompts` 和 `sceneCoverage.covered` 在**同一个模板集合**上计算

2. **实装的 hitCount 用 `Map<Long, Set<String>>` 自动去重**(v1 未描述细节):同平台同模板多行 `is_mentioned=1` 会被 Set 去重,只算一次

3. **防御性回归测试**:`PlatformIntentBreakdownBuilderTest.build_templateCountWithCompetitorVar_doesNotMultiplyByCompetitorCount` 构造 hasCompetitorVar=1 的 row(生产 SQL 层已过滤不可达),作为 Java 侧防御测试,防止未来有人回退 `base × competitorCount` 乘法

4. **SQL 集成测试**:C5 新增 `PresaleAiPromptResultMapperIntegrationTest` 用 `@SpringBootTest + @Sql` 真实验证 SQL filter(3 行 fixture 同时验 hasCompetitorVar filter + enabled filter + templateCount 正确性)

### D27 · roi_simulation · phase 分数模型

固定升幅 + clamp 到 100:
- phase1.target = min(current + 5, 100) · duration_label="M1"
- phase2.target = min(current + 12, 100) · duration_label="M2-3"
- phase3.target = min(current + 20, 100) · duration_label="M4-6"
- uplift_from_previous = target[i] - target[i-1](phase1 的上一阶段是 current_score)

不依赖 benchmark。边界:高分品牌(如 current=95)phase2/3 target=100,uplift 自动=0。

**v2 修订点**:
- `current == 0.0` 的防御比较在 C5 改为 `Double.compare(current, 0.0) == 0`(double `==` 严格比较的风格优化)
- 所有 phase target 用 `Math.min(current + X, 100)` clamp 实现(v1 描述为 "min(...)" 但未指明 Java API)

### D28 · optimization_findings 分配到 phase

按 priority 一维分:
- HIGH → phase 1
- MEDIUM → phase 2
- LOW / NULL → phase 3

`completed_optimization_count` 新报告统一 = 0,`total_optimization_count` 按分到的 findings 条数填。

**v2 修订点**:
- **null 是双重兜底**(v1 只说 "LOW / NULL → phase 3"):
  - `finding == null` → phase3
  - `finding != null && priority == null` → phase3
  - 两种 null 场景都归入 phase3,C3 实装层面明确

### D29 · benchmark 数据源(方案 B)

classpath 资源 `geo-server/src/main/resources/benchmarks/v1.json`,启动时加载到内存 Map。两级回退:`(industry, industry_role) → (industry, _ALL_) → (_ALL_, _ALL_) → BENCHMARK_MISSING`。最后一级永远命中(V1 内置兜底行)。

BENCHMARK_MISSING 归 `CONFIG_MISSING` 分类(retry 不可恢复,需补 JSON)。

**v2 修订点**:
- BenchmarkResolver 启动期 PostConstruct 校验 `(_ALL_,_ALL_)` 兜底存在,不存在直接抛 `IllegalStateException`,容器启动失败(避免运行期才发现)
- 运行期 resolve 若 `(_ALL_,_ALL_)` 缺失也抛 `IllegalStateException`(**不是 BizException**)——Orchestrator 的 L1 catch 必须**两层**:`IllegalStateException` → CONFIG_MISSING / `BizException` 含 "BENCHMARK_MISSING" → CONFIG_MISSING 其他 → L1_SERIALIZATION_ERROR
- **C5 引入 @SpringBootTest 后**,BenchmarkResolver 加 `@Autowired` 标注主构造函数(多构造函数场景下 Spring 容器需要消除歧义)

### D30 · roi_simulation 顶层字段

- `current_score` = L2.scores.overall
- `target_score` = phase3.target_score(即 min(current+20, 100))
- `estimated_uplift_percent` = (target - current) / current × 100(current=0 时兜底 0)
- `estimated_exposure_multiplier` = 1.8(硬编码,MVP 占位)

**v2 修订点**:无新增。保持 v1 原文,C3 实装完整落地。

## 三条必须同步改动的既有代码 · 已落地

1. `PresaleGenerateOrchestrator.runRealSkeletonFlow` → 改名 `runRealFullFlow`,删除 L1/L2/L3 三行 skeleton + 末尾 `markFailed(STAGE_D_CHECKPOINT)`,替换为真实装配 + `markDone` · **C4 落地**
2. `PlatformIntentBreakdownBuilder.resolveIntentTotalPromptsFromTemplate` → 删除 `base × competitorCount` 分支 · **C3 落地**
3. `PlatformIntentBreakdownBuilderTest` → 含 C 乘法断言的测试同步修正 · **C3 review 发现实际 0 条旧断言需改**(测试本来就用 hasCompetitorVar=0 的 fixture),新增 1 条防御性回归测试 · spec v4 §10.1 S14 验收条款同步改动 · **C5 文档修订**

## 新增类 · 已落地

- `PresaleRawSnapshotAssembler` · L1 装配 · 约 500 行 · C2 落地
- `PresaleBenchmarkResolver` · classpath JSON 加载 + 两级回退 · 约 180 行 · C1 落地
- `PresaleCompetitorAggregator` · 竞品归一化 + top3 抽取 · 共用组件 · C2 落地(v1 未列,实装时独立成类)
- `SceneCoverageCalculator` · L2 场景覆盖计算 · 约 243 行 · C3 落地
- `ScoresCalculator` · L2 五维评分 · 约 93 行 · C3 落地
- `RoiCalculator` · L2 ROI 模拟 · 约 77 行 · C3 落地
- `RankingStats` record · 排名分布 · 15 行 · C3 落地
- `SceneAndIntentResult` record · 场景计算返回值 · 13 行 · C3 落地

## 不做的事(明确延后) · v2 修订

| 项 | v1 原计划 | 实装现状 |
|---|---|---|
| `PresaleComputedSnapshotEnricher` 签名改造 | 保持现状 | **部分实装**:C3 Enricher 内部执行顺序改造(sceneCoverage → scores → ruleEngine → roi → validator),接入 3 个 Calculator;签名本身未变(仍是 `(versionId, rawJson, currentComputedJson, allowSynthetic)`)|
| 传 `"{}"` 作为 computed input | 真实流水线传 `"{}"` | **C4 改进**:真实流水线改为传 **DB 当前版本的 `computed_snapshot_json`** 作为基底,支持 regenerate 场景复用已有 computed 计算结果 |
| `PresaleL3InitService` 签名改造 | 保持现状 | **完全未动** ✅ |
| BATCH1 / BATCH2 / COMPETITOR_EXTRACT / Preflight 逻辑 | 完全不动 | **基本未动**,只有 `Batch1/Batch2ExecutionResult` 添加 `degradedPlatforms` 字段 + getter(C2 改动,为 L1 Assembler 提供降级集合)|
| `STAGE_D_CHECKPOINT` / `SNAPSHOT_BUILD_ERROR` 常量删除 | 保留以防回退 | **保留**:STAGE_D_CHECKPOINT 在 real 分支不再产生(V6 验收),SNAPSHOT_BUILD_ERROR 仅 mock 分支仍用。P1 清理 |
| 评分公式产品侧 review | P1 做 | 未改,MVP 以代码为准 |

## 风险提示 · v2 保留原 3 条

1. **benchmark JSON 内容**:Claude 出模板 + 示范值(零售/金融/教育/医疗/科技 + `_ALL_` 兜底共 6 行),示范值凭业内经验拍,不是真实行业数据
2. **scores 公式**:权重和阈值(如 ranking 的 90/80/60/40/20/0)是用户拍板,未经产品评审;演示时能解释"这是 MVP 设计,P1 做产品侧公式重审"
3. **exposure_multiplier = 1.8**:硬编码占位,演示时所有品牌都是 1.8 倍,客户问到要能解释

## Checkpoint 5 段交付回顾 · 实际工期

| CP | 范围 | 计划 | 实际 | 测试累计 |
|---|---|---|---|---|
| C1 | Benchmark 基础设施 | 0.5 天 | 0.5 天 | 131 |
| C2 | L1 Raw Snapshot Assembler | 2-2.5 天 | ~2 天 | 136 |
| C3 | L2 三件套 + Enricher 接入 + Builder 改造 + SQL 同源补修 | 1.5 天 | ~2 天(含 SQL 补修 0.5 天)| 143 |
| C4 | Orchestrator wiring | 1 天 | 1 天 | 149 |
| C5 | 清理 + 集成测试 + 文档升 v2 | 1 天 | 1.5 天 | 151 |

**整体**:计划 6.5 天,实际约 7 天。

## 本 PR 完成后立即启动的工作

- 真 LLM 联调(原 Q-1 本轮目标)
- V1/V13/V14/V15 P1 人工验收:
  - V1 真实 LLM happy path
  - V13 Resolver log.info 输出(industry/industryRole/matchLevel)
  - V14 L3 文案合理(executive_summary.headline + paragraph)
  - V15 前端 mergeSnapshot 兼容(MergedViewDTO 所有字段)
- PR-5 前端剩余联动(18 页 PDF 渲染校验、retry/regenerate UI 接入)

## 元方法论观察

v5 handoff §7.1 第 6 条"交接文档可能与代码现状脱节"在本 PR 三次印证:

1. **C2**:`total_tests` 语义(Query 成功数 vs is_mentioned IS NOT NULL)——Codex 核查发现 v4 文档与代码不符
2. **C3**:`selectTemplateIntentStats` SQL 层未 filter `has_competitor_var=0`——Claude review 追问发现,Codex 补修
3. **C4**:v1 §9 CP4 清单遗漏"STAGE_D_CHECKPOINT 断言修正"——实际发现测试没硬编码 STAGE_D_CHECKPOINT 断言,但"行为变化"需要补 stub

**启示**:下次接手 PR 时,开工前先跑一次 `mvn test` + 代码现状 sanity check,不要默认文档与代码同步。

---

**v2 定稿 END**
