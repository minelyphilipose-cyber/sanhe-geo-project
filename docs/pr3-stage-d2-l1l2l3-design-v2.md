# PR-3.D2:L1_AGGREGATE / L2_COMPUTE / L3_INIT 补齐实装设计稿 · v2

> **v2 版本说明**:本版在 v1 基础上整合 C1-C5 实装落地后的 30+ 条修订。所有业务决策(D25-D30)本身**未变**,修订集中在:
>
> 1. 实装细节补丁(v1 伪码与实际代码的偏差)
> 2. 由 Codex 实施时发现的主动增强(如 SQL 同源 filter、markDone 清空 failure 字段等)
> 3. v1 预判与实际 Codex 实施路径的差异(如 "断言修正"实际是"补 stub")
> 4. 测试实际覆盖情况(C1-C5 累计 151+ 条测试全绿)
>
> 每条修订在对应章节用 `> **v2 修订(CN)**:...` 内联标注,其中 CN 是来源 Checkpoint 编号,便于追溯。新增"§13 v2 修订索引"章节汇总全部修订清单,作为维护者速查入口。

| 字段 | 值 |
|---|---|
| 版本 | v2(取代 v1) |
| 起草日期 | 2026-04-23(C5 收尾完成时) |
| 作者 | Claude,基于 v1 + C1-C5 review 累积 |
| 前置 PR | PR-3 阶段 F2-B(commit `cfffbcb1`) |
| 实际代码改动量 | 约 1600 行新代码 + 151+ 条新/改测试 |
| 实际时间 | Codex 实施约 6 天(5 checkpoint 节奏全部顺利) |
| 代码状态 | **已合主干**(commit `e057c831` + C4 + C5 清理) |
| 配套文档 | `pr3-stage-d2-decisions-summary-v2.md`(1 页决策摘要 v2) |
| 会签角色 | 用户(决策 + 会签)/ Codex(实施)/ Claude(契约守护 + review + v2 起草) |
| v1 归档 | `pr3-stage-d2-l1l2l3-design-v1.md`(保留不改) |

---

## §0 背景 · 事实偏差与本 PR 的定位

### 0.1 偏差说明

交接文档 v4 标 "PR-3 全部合主干,MVP 后端生成链路完工",但 main 分支代码核查证实:

**`PresaleGenerateOrchestrator.runRealSkeletonFlow`** 在 PREFLIGHT + BATCH1 + COMPETITOR_EXTRACT + BATCH2 正确执行后:

```java
enterStage(versionId, STAGE_L1_AGGREGATE, "skeleton only");    // 仅切 stage 字段
enterStage(versionId, STAGE_L2_COMPUTE, "skeleton only");      // 仅切 stage 字段
enterStage(versionId, STAGE_L3_INIT, "skeleton only");         // 仅切 stage 字段

markFailed(versionId, FAILURE_CATEGORY_STAGE_D_CHECKPOINT,
        "PR-3 stage D checkpoint: batch2/l1/l2/l3 not implemented yet");
```

即真实 LLM 流水线 100% 落 FAILED,`failure_category = STAGE_D_CHECKPOINT`,`raw_snapshot_json / computed_snapshot_json / editable_content_json` 三列从未被写入。

### 0.2 进一步发现:L2 的四个字段根本没实现

Codex 在 Q-11 答复中确认:

| L2 字段 | 实现情况 |
|---|---|
| `platform_intent_breakdown` | ✅ `PlatformIntentBreakdownBuilder` 已有 |
| `intent_breakdown.total_prompts` | ✅ Enricher `mergeIntentBreakdown` 已填 |
| `intent_breakdown.covered_prompts / coverage_rate / avg_ranking` | ❌ 未实现(当前填 0 / 0.0 / null) |
| `scores`(五维 + overall) | ❌ 无计算逻辑 |
| `scene_coverage`(高/中/低三档) | ❌ 无计算逻辑 |
| `optimization_findings` | ⚠️ 规则引擎 `PresaleRuleEngineExecutor` 已有,**但未接入主链** |
| `roi_simulation`(phases + 顶层字段) | ❌ 无计算逻辑 |

L3 通过 `BizException` 硬约束要求 `computed.scores.overall != null` 和 `raw.benchmarks_frozen.industry_avg.overall != null`,任一 null 则流水线挂。

### 0.3 本 PR 的定位

- **L1_AGGREGATE**:从零写 `PresaleRawSnapshotAssembler`,装配完整 `RawSnapshotDTO`
- **L2_COMPUTE**:扩展现有 `PresaleComputedSnapshotEnricher`(或新增辅助类),新增 scores / scene_coverage / roi_simulation 的计算逻辑;接入 `PresaleRuleEngineExecutor`;改动 `PlatformIntentBreakdownBuilder` 的 C 乘法
- **L3_INIT**:**不动**,现有 `PresaleL3InitService` 已经按需工作,前置 L1/L2 数据齐即可
- **Benchmark**:新增 `PresaleBenchmarkResolver` + classpath JSON(`benchmarks/v1.json`)
- **收尾**:`runRealSkeletonFlow` 改为 `runRealFullFlow`,末尾从 `markFailed(STAGE_D_CHECKPOINT)` 替换为 `markDone`

### 0.4 目标 · 非目标

**目标**:
1. 真实 LLM 流水线从 PREFLIGHT 走到 DONE,三层 JSON 全部写入且通过 schema v1.2 校验
2. L3 派生的 `executive_summary.headline` / `deltaLabel` 能合理产出
3. 规则引擎真实执行,`optimization_findings` 非空(至少有一条)
4. 前端详情页 18 页能渲染,所有 DTO 字段非 null(或为 schema allow null 的合理场景)

**非目标**:
- 修改既有 `PresaleLlmInvoker` / `PresaleL3InitService` / `RawSnapshotDTO` 等签名
- 评分公式业务合理性的产品侧 review(本 PR 只实装用户拍板的公式,产品 review 留 P1)
- 真实行业 benchmark 数据采集(本 PR 只出示范值)
- 删除 `STAGE_D_CHECKPOINT` 常量(保留以防回退,P1 清理)
- 断点续跑 / Analyze 单独 retry / 成本预算(v4 Open Questions O-5 / O-7)

### 0.5 不变式(强约束)

- `RawSnapshotDTO` / `ComputedSnapshotDTO` / `EditableContentDTO` 及其子 DTO 的**字段定义一字不改**
- `RawSnapshotDTO.benchmarks_frozen` 内容仍然是 L1 层冻结副本,从 Resolver 一次性读入后永不修改
- BATCH1 / BATCH2 / COMPETITOR_EXTRACT / Preflight / ReuseDecisionService / PresaleReusePersistenceService 逻辑**完全不动**
- mock 流水线(`mockEnabled=true`)行为**完全不受影响**

---

## §1 D25-D30 决策汇总(速查)

本 PR 基于 6 条业务决策。具体推导见 `pr3-stage-d2-decisions-summary.md`,此处仅汇总公式。

### D25 scores 评分体系

```
mention   = sum(platform_breakdown[i].mention_count) 
          / sum(platform_breakdown[i].total_tests) × 100
          数据源:batch1 聚合后的 PlatformBreakdown
          边界:分母 = 0 → 0

ranking   = (ranking=1 行数 × 90
           + ranking=2 行数 × 80
           + ranking=3 行数 × 60
           + ranking=4 行数 × 40
           + ranking=5 行数 × 20
           + ranking≥6 行数 × 0
           ) / count(ranking IS NOT NULL)
          数据源:batch1 presale_ai_prompt_result 的 ranking 列
          边界:分母 = 0 → 0

sentiment = (positive × 1.0 + neutral × 0.5 + negative × 0.0) 
          / (positive + neutral + negative) × 100
          数据源:batch1 platform_breakdown[i].sentiment_distribution 合计
          边界:分母 = 0 → 0

coverage  = (高 covered × 2.0 + 中 covered × 1.5 + 低 covered × 1.0)
          / (高 total   × 2.0 + 中 total   × 1.5 + 低 total   × 1.0) × 100
          档位:高 = {RECOMMENDATION, COMPARISON}
                中 = {INQUIRY, COGNITIVE}
                低 = {SCENARIO}
          covered / total:沿用 D26
          边界:分母 = 0 → 0

overall = mention × 0.30 + ranking × 0.25 + sentiment × 0.15 + coverage × 0.30

存库:Double 保留原始精度;前端 Math.round() 展示。
```

### D26 scene_coverage + intent_breakdown 同源

```
一个 prompt 算 "covered" 的条件:
  effective_platforms = {enabled=true 的平台} - {version.degraded_platforms}
  M = count(presale_ai_prompt_result 
           WHERE prompt_template_id = X 
             AND batch_no = 1 
             AND platform_code IN effective_platforms 
             AND is_mentioned = 1)
  covered ⟺ M >= ceil(effective_platforms.size() / 2)

计数单位:按 prompt 模板,不按 cell
统计范围:只统计 batch1(has_competitor_var=0)

SceneCoverageGroup.total         = 该档位下所有 batch1 prompt 模板数
SceneCoverageGroup.covered       = 该档位下满足 covered 条件的 prompt 模板数
SceneCoverageGroup.coverage_rate = covered / total × 100

SceneQueryItem (covered_queries):
  { prompt_code, prompt_content, category }

SceneQueryMissing (missing_queries):
  { prompt_code, prompt_content, category, top_competitor_coverage }
  top_competitor_coverage:本版本已抽出的 top3 竞品中,
    在该 prompt 的任一 mentioned_competitors 数组里出现过的(归一化比较:trim+去空白+lower)
    最多 3 个;extracted_competitor_count = 0 时 → []

IntentBreakdown(5 条,按 PresaleIntentCode.allInOrder()):
  category       = PresaleIntentCode.label(如 "推荐型")
  business_value = "高" / "中" / "低"(按档位映射)
  total_prompts  = 该 intent 下 batch1 prompt 模板数
  covered_prompts= 该 intent 下满足 D26 covered 条件的 prompt 模板数
  coverage_rate  = covered_prompts / total_prompts × 100
  avg_ranking    = avg(ranking) WHERE batch_no=1 AND is_mentioned=1 
                   AND prompt 属于该 intent
                   无匹配行 → null

⚠️ 既有代码改动:
  PlatformIntentBreakdownBuilder.resolveIntentTotalPromptsFromTemplate
    删除 "has_competitor_var==1 时 base = base * competitorCount" 分支
  该改动会让 PlatformIntentCell.total_prompts 也只算 batch1
```

### D27 roi_simulation phase 分数模型

```
phase1.target = min(current_score + 5,  100.0),duration_label = "M1"
phase2.target = min(current_score + 12, 100.0),duration_label = "M2-3"
phase3.target = min(current_score + 20, 100.0),duration_label = "M4-6"

uplift_from_previous:
  phase1.uplift = phase1.target - current_score
  phase2.uplift = phase2.target - phase1.target
  phase3.uplift = phase3.target - phase2.target

边界:
  current=95 → phase1=100, phase2=100, phase3=100, uplift=5/0/0
  current=0  → phase1=5,   phase2=12,  phase3=20,  uplift=5/7/8
```

### D28 optimization_findings 按 priority 分配到 phase

```
每条 OptimizationFinding 按 priority 分到 phase:
  HIGH       → phase 1
  MEDIUM     → phase 2
  LOW / null → phase 3

phase.total_optimization_count     = 该 phase 分到的 finding 条数
phase.completed_optimization_count = 0(新报告刚生成,未动工)
```

### D29 benchmark 数据源(方案 B)

```
路径:geo-server/src/main/resources/benchmarks/v1.json

PresaleBenchmarkResolver.resolve(industry, industryRole) -> BenchmarksFrozen
  启动时 @PostConstruct 一次性加载到内存 Map<(String,String), Entry>
  查找顺序:
    1. (industry, industryRole) 命中 → matchLevel = EXACT
    2. (industry, "_ALL_") 命中      → matchLevel = FALLBACK_INDUSTRY
    3. ("_ALL_", "_ALL_") 命中       → matchLevel = FALLBACK_INDUSTRY
    4. 全未命中 → throw BizException(500, "BENCHMARK_MISSING: ...")
  
  第 3 级永远命中(v1.json 必须包含 _ALL_/_ALL_ 兜底行)
  
异常归类:BENCHMARK_MISSING 归 CONFIG_MISSING(retry 不可恢复,需补 JSON)
```

### D30 roi_simulation 顶层字段

```
current_score              = L2.scores.overall
target_score               = phase3.target_score (即 min(current+20, 100))
estimated_uplift_percent   = (target_score - current_score) / current_score × 100
                             边界:current_score == 0 → 0(避免除零)
estimated_exposure_multiplier = 1.8 (硬编码,MVP 占位)
```

---

## §2 L1_AGGREGATE 详设

### 2.1 新增类 `PresaleRawSnapshotAssembler`

**位置**:`com.huanjing.geo.module.presale.generate.PresaleRawSnapshotAssembler`
**Spring Bean**:`@Component`,非事务(纯读聚合 + JSON 序列化)

**对外方法签名**:

```java
public String assemble(Long versionId,
                       PresaleReport report,
                       PresaleReportVersion version,
                       Set<String> degradedPlatforms,
                       List<String> extractedCompetitorDisplayNames);
```

返回值:序列化后的 `raw_snapshot_json` 字符串。**不直接写库**,写库由 orchestrator 负责。

**依赖注入**:

```java
private final PresaleAiCallMapper aiCallMapper;
private final PresaleAiPromptResultMapper aiPromptResultMapper;
private final AiPlatformConfigMapper aiPlatformConfigMapper;
private final PresalePromptTemplateMapper promptTemplateMapper;
private final PresaleBenchmarkResolver benchmarkResolver;
private final PresaleCompetitorAggregator competitorAggregator;   // v2 修订(C2):共用组件
private final ObjectMapper objectMapper;
```

> **v2 修订(C2)**:Assembler 的异常出口实际为 **4 层 catch 链**:
> ```java
> } catch (BizException ex) { throw ex; }
> catch (IllegalStateException ex) { throw ex; }    // BenchmarkResolver 的兜底缺失直接冒出
> catch (JsonProcessingException ex) { throw new BizException(500, "L1 aggregate failed: JSON serialization error - " + ex.getMessage()); }
> catch (Exception ex) { throw new BizException(500, "L1 aggregate failed: " + ex.getMessage()); }
> ```
> `IllegalStateException` 单独冒泡是关键——这样 Orchestrator 的 L1 catch 需要**两层结构**(第一层 catch IllegalStateException → CONFIG_MISSING,第二层 catch BizException 再按 message 细分)。详见 §6.1 修订。

> **v2 修订(C2)**:**新增 `PresaleCompetitorAggregator` 共用组件**(v1 未列出)。这是 C2 实施时从 Assembler 拆出的竞品归一化 + top3 抽取工具,同时被 SceneCoverageCalculator 和 Orchestrator 复用,避免 normalizeName 逻辑在多处重复。返回三元组(displayMap + normalizedMap + denominatorRows),denominatorRows 被复用来算分母,省一次 DB 查询。

### 2.2 装配流程(按 `RawSnapshotDTO` 的 7 个子字段)

#### 2.2.1 `meta` (RawMeta)

```java
RawMeta meta = RawMeta.builder()
    .reportId(report.getId())
    .versionNo(version.getVersionNo())
    .generatedAt(LocalDateTime.now())
    .generationDurationSeconds(computeDurationSeconds(version))
    .formulaVersion("v1.0")
    .build();
```

> **v2 修订(C2)**:`computeDurationSeconds` 实装做了**三层兜底**,避免负值/null 导致 JSON 序列化异常:
> 1. 若 `version.getStartedAt()` / `version.getCompletedAt()` 任一为 null → 返回 0
> 2. 若 duration 计算结果为负(时钟回拨场景)→ 返回 0
> 3. 正常 duration → 返回秒数


- `generatedAt`:字段级 `PresaleDateTimeJson.Serializer` 输出 RFC3339 带 +08:00
- `generationDurationSeconds` = `Duration.between(version.getCreatedAt(), LocalDateTime.now()).toSeconds()` cast 为 Integer;若 version.getCreatedAt() 为 null → 填 0 并 log.warn
- `formulaVersion`:硬编码 "v1.0"(与 mock fixture 一致)

#### 2.2.2 `clientInfo` (ClientInfo)

直接从 `presale_report` 拷贝:

```java
ClientInfo clientInfo = ClientInfo.builder()
    .brandName(report.getBrandName())
    .industry(report.getIndustry())
    .industryRole(report.getIndustryRole())
    .region(report.getRegion())
    .userDemand(report.getUserDemand())
    .build();
```

#### 2.2.3 `testSummary` (TestSummary)

```java
int platformCount     = countEnabledPlatforms();  // from ai_platform_config
int genericPromptCount = countBatch1Templates();  // has_competitor_var=0 AND enabled=1
int competitorCount = extractedCompetitorDisplayNames.size();   // 0~3

int totalPrompts   = genericPromptCount + (countBatch2Templates() * competitorCount);
int totalCalls     = countCalls(versionId, EXCLUDE_SKIPPED);  // SKIPPED_DEGRADED 不计
int successfulCalls= countCallsByStatus(versionId, "SUCCESS");
int failedCalls    = Math.max(0, totalCalls - successfulCalls);  // v2 修订(C2):防负
int excludedCount  = 0;                                        // MVP 不做排除
int rounds         = 2;                                        // 语义:两阶段
boolean isDegraded = degradedPlatforms.size() >= 4;            // 固定阈值

TestSummary ts = TestSummary.builder()
    .totalPrompts(totalPrompts)
    .totalPlatforms(platformCount)
    .totalCalls(totalCalls)
    .successfulCalls(successfulCalls)
    .failedCalls(failedCalls)
    .excludedCount(0)
    .rounds(2)
    .isDegraded(isDegraded)
    .degradedPlatforms(new ArrayList<>(degradedPlatforms))
    .build();
```

**`totalCalls` 是否含 SKIPPED_DEGRADED 行**(v0 Open Question Q-2):本 PR 统一口径 **不含**。理由:`SKIPPED_DEGRADED` 不是真实调用,和 v4 §3.4 "total_calls = 批次1实际 + 批次2实际" 的"实际"语义一致。

> **v2 修订(C2)**:`failedCalls = Math.max(0, totalCalls - successfulCalls)` 加 `Math.max(0, ...)` 防御性修复。理论上 `successfulCalls <= totalCalls` 成立,但如果查询时序有竞态,减法可能出现 -1 之类的异常值,兜底为 0 更安全。

#### 2.2.4 `platformBreakdown` (List<PlatformBreakdown>,P 条)

对每个 enabled 平台聚合 batch1 prompt_result:

```java
List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
    new LambdaQueryWrapper<AiPlatformConfig>()
        .eq(AiPlatformConfig::getEnabled, true)
        .orderByAsc(AiPlatformConfig::getPlatformCode)
);

List<PlatformBreakdown> result = new ArrayList<>();
for (AiPlatformConfig platform : platforms) {
    String platformCode = platform.getPlatformCode();
    
    // batch1 行(做统计口径,不含 batch2 竞品)
    // v2 修订(C2):过滤条件加 is_mentioned IS NOT NULL(对齐 total_tests 语义,见下)
    List<PresaleAiPromptResult> batch1Rows = aiPromptResultMapper.selectList(
        new LambdaQueryWrapper<PresaleAiPromptResult>()
            .eq(PresaleAiPromptResult::getVersionId, versionId)
            .eq(PresaleAiPromptResult::getPlatformCode, platformCode)
            .eq(PresaleAiPromptResult::getBatchNo, 1)
            .isNotNull(PresaleAiPromptResult::getIsMentioned)   // v2 新增
    );
    
    int totalTests  = batch1Rows.size();   // v2:Query+Analyze 都成功的行数(is_mentioned 已写入)
    int mentionCount = (int) batch1Rows.stream()
                         .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
                         .count();
    Double mentionRate = totalTests == 0 ? 0.0 : (mentionCount * 100.0 / totalTests);
    
    // avg_ranking:只取 is_mentioned=1 的行(ranking IS NOT NULL 理论上等价)
    OptionalDouble avgRankingOpt = batch1Rows.stream()
        .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
        .filter(r -> r.getRanking() != null)
        .mapToInt(PresaleAiPromptResult::getRanking)
        .average();
    Double avgRanking = avgRankingOpt.isPresent() ? avgRankingOpt.getAsDouble() : null;
    
    int primaryRec = (int) batch1Rows.stream()
        .filter(r -> Integer.valueOf(1).equals(r.getRanking()))
        .count();
    
    // sentiment_distribution:只统计 batch1(PlatformBreakdown javadoc 明确)
    int pos = (int) batch1Rows.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
    int neu = (int) batch1Rows.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
    int neg = (int) batch1Rows.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();
    
    PlatformBreakdown pb = PlatformBreakdown.builder()
        .platformCode(platformCode)
        .platformName(platform.getPlatformName())
        .totalTests(totalTests)
        .mentionCount(mentionCount)
        .mentionRate(mentionRate)
        .avgRanking(avgRanking)
        .primaryRecommendationCount(primaryRec)
        .sentimentDistribution(
            PlatformBreakdown.SentimentDistribution.builder()
                .positive(pos).neutral(neu).negative(neg).build()
        )
        .isDegraded(degradedPlatforms.contains(platformCode))
        .build();
    result.add(pb);
}
```

**`total_tests` 语义锁定**(v0 Open Question Q-4):本 PR 采用 "batch1 行中 `is_mentioned IS NOT NULL` 的行数"。

> **v2 修订(C2)**:v1 原稿写的是 "Query 成功数"(即 prompt_result 行数),但实际代码过滤条件是 `is_mentioned IS NOT NULL`——这意味着 Query + Analyze **都成功**(因为 Analyze 写入 is_mentioned 字段)。这是更严格的口径:
> - Query 成功 + Analyze 失败 → 行存在但 is_mentioned=null → **不计入** total_tests
> - Query 成功 + Analyze 成功 → 行存在且 is_mentioned 非 null → 计入
> - 这样 `mentionCount / totalTests` 分母才是"真正跑完测试"的数量,分数更准
> - 对外语义从"测试了多少次"改为"完整测完(含情感分析)多少次"

#### 2.2.5 `competitors` (List<Competitor>,0-3 条)

```java
List<Competitor> competitors = new ArrayList<>();
if (extractedCompetitorDisplayNames.isEmpty()) {
    // 0 竞品场景,直接返回空 list
    return competitors;
}

String normalizedBrand = normalizeName(report.getBrandName());

// 重新做一次 batch1 mentioned_competitors 的聚合
// (与 Orchestrator.extractTopCompetitorsFromBatch1 同源逻辑,返回 Map<normalized, count>)
List<PresaleAiPromptResult> batch1MentionedRows = aiPromptResultMapper.selectList(
    new LambdaQueryWrapper<PresaleAiPromptResult>()
        .eq(PresaleAiPromptResult::getVersionId, versionId)
        .eq(PresaleAiPromptResult::getBatchNo, 1)
        .isNotNull(PresaleAiPromptResult::getIsMentioned)
);
int batch1DenomRows = batch1MentionedRows.size();

Map<String, Integer> countByNormalized = new HashMap<>();
Map<String, Integer> rankingSumByNormalized = new HashMap<>();
Map<String, Integer> rankingCountByNormalized = new HashMap<>();

// ... 解析每行的 mentioned_competitors JSON,聚合计数

// 按 extractedCompetitorDisplayNames 顺序(top3 排名顺序)给 rank 编号
int rank = 1;
for (String competitorDisplayName : extractedCompetitorDisplayNames) {
    String normalized = normalizeName(competitorDisplayName);
    int mc = countByNormalized.getOrDefault(normalized, 0);
    Double mr = batch1DenomRows == 0 ? 0.0 : (mc * 100.0 / batch1DenomRows);
    
    // batch2 scene_advantages 聚合(每个竞品)
    List<String> sceneAdvantagesRaw = aggregateSceneAdvantages(
        versionId, competitorDisplayName
    );
    
    competitors.add(Competitor.builder()
        .rank(rank++)
        .name(competitorDisplayName)
        .mentionCount(mc)
        .mentionRate(mr)
        .avgRanking(null)  // MVP 本轮不算竞品 avg_ranking,留 null
        .sceneAdvantagesRaw(sceneAdvantagesRaw)
        .build());
}
```

**`competitor.mention_rate` 分母**(v0 Open Question Q-6):本 PR 采用 "batch1 所有 Analyze 成功的 prompt_result 行数"(即 `is_mentioned IS NOT NULL`)。理由:
- 分母代表 "竞品有机会被提到的测试次数",即"成功跑完 Analyze 的次数"
- 分子是该竞品在 `mentioned_competitors` 中出现的次数

**`competitor.avg_ranking`**:本 PR 留 null。理由:竞品 ranking 需要从 Analyze 输出的自然语言里抽取,当前 AnalyzePromptTemplates 的 JSON schema 只有品牌自身 ranking,竞品没有 ranking 字段。P1 扩展 AnalyzePromptTemplates 后可补填。

**`scene_advantages_raw` 聚合**(v0 Open Question Q-7):

```java
private List<String> aggregateSceneAdvantages(Long versionId, String competitorDisplayName) {
    List<PresaleAiPromptResult> batch2Rows = aiPromptResultMapper.selectList(
        new LambdaQueryWrapper<PresaleAiPromptResult>()
            .eq(PresaleAiPromptResult::getVersionId, versionId)
            .eq(PresaleAiPromptResult::getBatchNo, 2)
            .eq(PresaleAiPromptResult::getCompetitorName, competitorDisplayName)
            .isNotNull(PresaleAiPromptResult::getIsMentioned)
    );
    
    // 每行 scene_advantages 是 JSON 数组字符串 ["优势1", "优势2"]
    // 用频次 top-5 汇总,同频次按字典序
    Map<String, Integer> freq = new HashMap<>();
    for (PresaleAiPromptResult row : batch2Rows) {
        String sa = row.getSceneAdvantages();
        if (sa == null || sa.isBlank()) continue;
        try {
            JsonNode arr = objectMapper.readTree(sa);
            if (!arr.isArray()) continue;
            for (JsonNode item : arr) {
                if (!item.isTextual()) continue;
                String s = item.asText().trim();
                if (s.isEmpty()) continue;
                freq.merge(s, 1, Integer::sum);
            }
        } catch (Exception ex) {
            log.warn("Skip invalid scene_advantages, versionId={}, rowId={}", versionId, row.getId());
        }
    }
    
    return freq.entrySet().stream()
        .sorted(Comparator
            .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
            .thenComparing(Map.Entry::getKey))
        .limit(5)
        .map(Map.Entry::getKey)
        .toList();
}
```

**`competitor_name` 查询用 display name**(v0 Open Question Q-8):本 PR 确认 WHERE `competitor_name = ?` 用 display 原写入值,不做归一化。与 F1 `normalizeCompetitor` 只 trim 的约定对齐。Orchestrator 写入 call / prompt_result 时用的就是 display。

> **v2 修订(C2)· Aggregator 三元组返回优化**:实装中 `PresaleCompetitorAggregator` 的聚合方法返回**三元组** `(displayMap, normalizedMap, denominatorRows)`。v1 原稿只关心前两项,但实际 denominatorRows 正是本节用到的 `batch1MentionedRows`——复用这一份数据可以**省一次 DB 查询**(否则 mentionRate 计算时还要再查一次 `is_mentioned IS NOT NULL` 行)。这是 C2 Codex 实装时主动发现的优化点。

> **v2 修订(C2)· 双归一化场景分派**:竞品名字在系统里有两种"归一化"需求,本 PR 明确划分:
> - **Orchestrator 语义归并**:`normalizeName()` 去空白 + 小写化,用于 top3 抽取时合并 "Claude" / "claude" / "Claude " 这种同一物的变体
> - **ReuseDecisionService 精确匹配**:按 display name 原样比较,用于 retry 时判断"这个竞品是否精确复用上次的数据"
>
> 两者**场景不同**,`PresaleCompetitorAggregator.normalizeName` 只服务于第一种场景,不影响第二种。测试中也按这两种场景分别 mock normalizeName 行为。

#### 2.2.6 `sentimentDetail` (SentimentDetail)

**两轮合计统计**(batch1 + batch2):

```java
List<PresaleAiPromptResult> allMentionedRows = aiPromptResultMapper.selectList(
    new LambdaQueryWrapper<PresaleAiPromptResult>()
        .eq(PresaleAiPromptResult::getVersionId, versionId)
        .isNotNull(PresaleAiPromptResult::getSentiment)
);

int pos = (int) allMentionedRows.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
int neu = (int) allMentionedRows.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
int neg = (int) allMentionedRows.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();

SentimentDetail sd = SentimentDetail.builder()
    .positiveCount(pos)
    .neutralCount(neu)
    .negativeCount(neg)
    .topKeywords(null)       // MVP 不做词云
    .negativeEvidence(null)  // MVP 不做证据摘录
    .build();
```

`top_keywords` / `negative_evidence`(v0 Open Question Q-9):填 null,schema 允许(见 SentimentDetail.java javadoc "top_keywords / negative_evidence 可选")。

注意 D25 的 sentiment 维度只用 batch1 数据(`platform_breakdown[i].sentiment_distribution`),和此处 SentimentDetail 的"两轮合计"数据源**不同**。这是 spec v4 §3.4 明文规定的不同源,不应交叉校验。

#### 2.2.7 `benchmarksFrozen` (BenchmarksFrozen)

```java
BenchmarksFrozen bf = benchmarkResolver.resolve(
    report.getIndustry(),
    report.getIndustryRole()
);
```

详见 §4。

### 2.3 装配异常归类

```java
try {
    RawSnapshotDTO raw = RawSnapshotDTO.builder()
        .meta(meta)
        .clientInfo(clientInfo)
        .testSummary(ts)
        .platformBreakdown(platformBreakdown)
        .competitors(competitors)
        .sentimentDetail(sd)
        .benchmarksFrozen(bf)
        .build();
    return objectMapper.writeValueAsString(raw);
} catch (BizException ex) {
    throw ex;
} catch (IllegalStateException ex) {    // v2 修订(C2):BenchmarkResolver 兜底缺失直接冒出
    throw ex;
} catch (JsonProcessingException ex) {
    throw new BizException(500, "L1 aggregate failed: JSON serialization error - " + ex.getMessage());
} catch (Exception ex) {
    throw new BizException(500, "L1 aggregate failed: " + ex.getMessage());
}
```

Orchestrator 层捕获(**v2 修订(C2)· 两层 catch 结构**):

| 异常类型 | 出处 | Orchestrator 归类 |
|---|---|---|
| `IllegalStateException` | BenchmarkResolver (_ALL_,_ALL_) 兜底缺失 / 启动期校验失败 | `CONFIG_MISSING` · **第一层 catch** |
| `BizException` 含 `"BENCHMARK_MISSING"` | 已不会实际发生(Assembler 不包装,但保留字符串匹配作防御) | `CONFIG_MISSING` |
| 其他 `BizException` | Assembler 内部抛(JSON 序列化失败等) | `L1_SERIALIZATION_ERROR` · 第二层 catch else 分支 |
| 其他 `Throwable`(NPE / DataAccessException 等) | 外部基础设施 | Orchestrator 外层 `catch(Throwable)` → `UNEXPECTED_ERROR` 或 `INTERRUPTED` |

> **v2 修订(C2)· 为什么两层 catch 不能合并**:原因是 `BenchmarkResolver.resolve` 抛 IllegalStateException(不是 BizException),Assembler 的 catch 链让它直接冒出而不包装。这让 Orchestrator L1 catch 必须**先 catch IllegalStateException 再 catch BizException**,顺序不能颠倒——BizException 也是 RuntimeException 的子类(这里实际是通过 checked 路径抛,但 Java 编译器视作 catch 顺序)。

---

## §3 L2_COMPUTE 详设

### 3.1 现状 vs 期望

**现状**(`PresaleComputedSnapshotEnricher.enrichAndValidate`):
- ✅ 填 `platform_intent_breakdown`(通过 Builder)
- ✅ 填 `intent_breakdown.total_prompts`(通过 mergeIntentBreakdown,但只填 total,covered/rate/avgRanking 是 0/0/null)
- ❌ 不填 `scores`
- ❌ 不填 `scene_coverage`
- ❌ 不填 `optimization_findings`
- ❌ 不填 `roi_simulation`
- ❌ 不完整填 `intent_breakdown`(covered/rate/avgRanking 未算)

**期望**:全部 6 个字段都填齐,通过 `PlatformIntentBreakdownValidator` 校验。

### 3.2 实施方案:Enricher 扩展 + 新增计算类

不改 `enrichAndValidate` 方法签名(对 mock 流程透明),在方法内部新增调用链。

> **v2 修订(C3)· 执行顺序微调**:v1 原稿在 Scores 前执行 RuleEngine + Roi。实际 C3 实装调整为:
> 1. builder(platform_intent_breakdown + intentTotalPrompts)
> 2. **sceneCoverageCalculator**(scene_coverage + intentBreakdown)
> 3. **scoresCalculator**(scores,**3 参**:raw + scenes + rankingStats)
> 4. **ruleEngineExecutor**(optimization_findings)
> 5. **roiCalculator**(roi_simulation,需要 scores.overall 和 findings)
> 6. validator(最后校验)
>
> 顺序要点:scoresCalculator 必须在 sceneCoverageCalculator 之后(依赖 scenes.sceneCoverage 算 coverage 维度),roiCalculator 必须在 scoresCalculator + ruleEngineExecutor 之后(依赖 scores.overall + findings)。

```java
public String enrichAndValidate(Long versionId,
                                String rawSnapshotJson,
                                String computedSnapshotJson,
                                boolean allowSyntheticFallback) {
    try {
        // 【既有】解析 raw
        JsonNode rawRoot = objectMapper.readTree(stripBom(rawSnapshotJson));
        JsonNode effectiveRoot = unwrapInputNode(rawRoot);
        RawSnapshotDTO rawSnapshot = objectMapper.treeToValue(extractRawNode(effectiveRoot), RawSnapshotDTO.class);
        if (rawSnapshot == null) throw new BizException(500, "... raw_snapshot is null");
        
        // 【既有】解析 computed 基底
        ObjectNode computedNode = extractComputedNode(effectiveRoot, computedSnapshotJson);
        ComputedSnapshotDTO computedSnapshot = objectMapper.treeToValue(computedNode, ComputedSnapshotDTO.class);
        if (computedSnapshot == null) throw new BizException(500, "... computed_snapshot is null");
        
        // 【既有】platform_intent_breakdown + intent_breakdown.total_prompts
        PlatformIntentBreakdownBuilder.BuildResult buildResult = builder.build(
                versionId, rawSnapshot, computedSnapshot, allowSyntheticFallback);
        List<PlatformIntentCell> cells = buildResult.cells();
        computedSnapshot.setPlatformIntentBreakdown(cells);
        
        // 【新增】D26 scene_coverage + intent_breakdown 同源
        SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
                versionId, rawSnapshot, buildResult.intentTotalPrompts());
        computedSnapshot.setSceneCoverage(scenes.sceneCoverage());
        computedSnapshot.setIntentBreakdown(scenes.intentBreakdown());
        
        // 【新增】D25 scores 五维计算 · v2 修订(C3):3 参,含 rankingStats
        RankingStats rankingStats = queryRankingStats(versionId);
        Scores scores = scoresCalculator.compute(rawSnapshot, scenes, rankingStats);
        computedSnapshot.setScores(scores);
        
        // 【新增】规则引擎接入,产出 optimization_findings
        RuleEngineResult ruleResult = ruleEngineExecutor.execute(rawSnapshot, computedSnapshot);
        List<OptimizationFinding> findings = ruleResult.getFindings();
        // v2 修订(C3):V11 0 命中风险兜底
        if (findings == null || findings.isEmpty()) {
            log.warn("Rule engine returned 0 findings, versionId={}", versionId);
        }
        computedSnapshot.setOptimizationFindings(findings);
        
        // 【新增】D27 + D28 + D30 roi_simulation
        RoiSimulation roi = roiCalculator.compute(
                scores.getOverall(),
                findings
        );
        computedSnapshot.setRoiSimulation(roi);
        
        // 【既有】校验
        validator.validate(rawSnapshot.getPlatformBreakdown(),
                           computedSnapshot.getIntentBreakdown(),
                           cells);
        return objectMapper.writeValueAsString(computedSnapshot);
    } catch (BizException e) {
        throw e;
    } catch (JsonProcessingException e) {
        throw new BizException(500, "platform_intent_breakdown integrity violated: json parse failed - " + e.getMessage());
    }
}

// v2 修订(C3 + C5):ranking 分桶统计
private RankingStats queryRankingStats(Long versionId) {
    List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(...);
    int c1 = 0, c2 = 0, c3 = 0, c4 = 0, c5 = 0, cGe6 = 0;
    for (PresaleAiPromptResult r : rows) {
        Integer ranking = r.getRanking();
        if (ranking == null) continue;
        if (ranking == 1) { c1++; }          // v2 修订(C5):从 <=1 改为 ==1,避免 ranking=0 夸大评分
        else if (ranking == 2) { c2++; }
        else if (ranking == 3) { c3++; }
        else if (ranking == 4) { c4++; }
        else if (ranking == 5) { c5++; }
        else { cGe6++; }
    }
    return new RankingStats(c1, c2, c3, c4, c5, cGe6);
}
```

> **v2 修订(C3)· Enricher catch 链注意事项**:Enricher 只有 2 层 catch(`BizException` 直接 throw + `JsonProcessingException` 包装为 BizException),**没有 `catch(Exception)` 兜底**。这意味着:Calculator 内部的意外 RuntimeException(NPE / DataAccessException 等)会**绕过** Enricher catch → 绕过 Orchestrator 的 `catch(BizException)` → 冒到 Orchestrator 最外层 `catch(Throwable)` 归 `UNEXPECTED_ERROR`(不归 L2_COMPUTE_ERROR)。这是有意设计——`L2_COMPUTE_ERROR` 专门保留给 Enricher 主动 throw 的业务/序列化错误,基础设施异常走 UNEXPECTED_ERROR。

**新增依赖**:

```java
private final PlatformIntentBreakdownBuilder builder;          // 既有
private final PlatformIntentBreakdownValidator validator;       // 既有
// 新增:
private final SceneCoverageCalculator sceneCoverageCalculator;
private final ScoresCalculator scoresCalculator;
private final PresaleRuleEngineExecutor ruleEngineExecutor;
private final RoiCalculator roiCalculator;
private final PresaleAiPromptResultMapper aiPromptResultMapper;  // v2 修订(C3):新增,供 queryRankingStats 用
```

### 3.3 新增类 `SceneCoverageCalculator`

**位置**:`com.huanjing.geo.module.presale.generate.calc.SceneCoverageCalculator`

**职责**:按 D26 产出 `SceneCoverage` 对象和"同源"的 `IntentBreakdown` 列表

> **v2 修订(C3)· `PresaleIntentCode.businessValue()` 抽到枚举**:v1 原稿在 SceneCoverageCalculator 内部维护 intent→businessValue 映射表。实装时抽到 `PresaleIntentCode` 枚举本身(每个 code 持有 label + businessValue 两个字段),避免在 SceneCoverageCalculator 和 Enricher 两处重复维护。
>
> 该枚举扩展后有 3 个工具方法:
> - `allInOrder()`:按稳定顺序返回全部 code(供 EnumMap 迭代)
> - `fromLabel(String)`:从中文 label(如 "推荐型")反查枚举
> - `businessValue()`:返回 "高/中/低" 档位

> **v2 修订(C3)· hitCount 去重优化**:v1 原稿用 `Map<Long, Integer>` 直接累加 hitCount。实装时改为 `Map<Long, Set<String>>`(template_id → 平台 set),自动去重——即使同一平台上对同一 template 有多行 is_mentioned=1(理论不应发生但防御),`.size()` 仍是 M(独立平台数)。

```java
@Component
@RequiredArgsConstructor
public class SceneCoverageCalculator {
    
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleCompetitorAggregator competitorAggregator;  // v2 修订(C3):复用归一化
    private final ObjectMapper objectMapper;
    
    /**
     * @param versionId           版本 ID
     * @param raw                 L1,用 raw.test_summary.degraded_platforms 算 effective_platforms
     *                            同时用 raw.competitors 的 top3 供 resolveTopCompetitorCoverage
     * @param intentTotalPrompts  由 Builder 产出的 intent→total_prompts 映射
     *                            (经 D26 改动 + C3 SQL 补修:只算 has_competitor_var=0)
     */
    public SceneAndIntentResult compute(Long versionId,
                                        RawSnapshotDTO raw,
                                        Map<String, Integer> intentTotalPrompts) {
        // 1. 查所有 enabled 平台
        Set<String> allPlatforms = aiPlatformConfigMapper.selectList(
            new LambdaQueryWrapper<AiPlatformConfig>().eq(AiPlatformConfig::getEnabled, true)
        ).stream().map(AiPlatformConfig::getPlatformCode).collect(Collectors.toSet());
        
        // 2. effective_platforms = all - degraded
        Set<String> degraded = raw == null || raw.getTestSummary() == null 
            || raw.getTestSummary().getDegradedPlatforms() == null ? 
            Set.of() : new HashSet<>(raw.getTestSummary().getDegradedPlatforms());
        Set<String> effective = new HashSet<>(allPlatforms);
        effective.removeAll(degraded);
        int threshold = (int) Math.ceil(effective.size() / 2.0);  // 门槛
        
        // 3. 查所有 batch1 prompt 模板
        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
            new LambdaQueryWrapper<PresalePromptTemplate>()
                .eq(PresalePromptTemplate::getEnabled, 1)
                .eq(PresalePromptTemplate::getHasCompetitorVar, 0)
                .orderByAsc(PresalePromptTemplate::getSortOrder)
                .orderByAsc(PresalePromptTemplate::getId)
        );
        
        // 4. 对每个 prompt 查命中平台集合
        // v2 修订(C3):用 Map<Long, Set<String>> 自动去重同平台多行
        Map<Long, Set<String>> hitPlatformsByTemplate = new HashMap<>();
        Map<Long, List<Integer>> rankingsByTemplateId = new HashMap<>();
        
        List<PresaleAiPromptResult> allBatch1Rows = aiPromptResultMapper.selectList(
            new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getBatchNo, 1)
        );
        
        for (PresaleAiPromptResult row : allBatch1Rows) {
            if (!effective.contains(row.getPlatformCode())) continue;
            if (!Integer.valueOf(1).equals(row.getIsMentioned())) continue;
            hitPlatformsByTemplate
                .computeIfAbsent(row.getPromptTemplateId(), k -> new HashSet<>())
                .add(row.getPlatformCode());
            if (row.getRanking() != null) {
                rankingsByTemplateId.computeIfAbsent(row.getPromptTemplateId(), k -> new ArrayList<>())
                                    .add(row.getRanking());
            }
        }
        
        // 5. 对每个 prompt 判 covered / 归档到意图 + 档位
        Map<PresaleIntentCode, List<TemplateWithCovered>> byIntent = new EnumMap<>(PresaleIntentCode.class);
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            byIntent.put(intent, new ArrayList<>());
        }
        
        for (PresalePromptTemplate tpl : templates) {
            int M = hitCountByTemplateId.getOrDefault(tpl.getId(), 0);
            boolean covered = M >= threshold;
            
            PresaleIntentCode intent = PresaleIntentCode.fromLabel(tpl.getIntentLabel());
            // TemplateWithCovered 含:templateId, promptCode, promptContent, category, covered
            byIntent.get(intent).add(new TemplateWithCovered(tpl, covered));
        }
        
        // 6. 组装 IntentBreakdown
        List<IntentBreakdown> intentBreakdown = new ArrayList<>();
        for (PresaleIntentCode intent : PresaleIntentCode.allInOrder()) {
            List<TemplateWithCovered> list = byIntent.get(intent);
            int total = intentTotalPrompts.getOrDefault(intent.getCode(), 0);  // 与 Builder 同源
            int covered = (int) list.stream().filter(TemplateWithCovered::covered).count();
            Double coverageRate = total == 0 ? 0.0 : (covered * 100.0 / total);
            
            // avg_ranking:按 intent 分组所有 is_mentioned=1 行的 ranking 平均
            List<Integer> rankings = list.stream()
                .flatMap(twc -> rankingsByTemplateId.getOrDefault(twc.template().getId(), List.of()).stream())
                .toList();
            Double avgRanking = rankings.isEmpty() ? null :
                rankings.stream().mapToInt(Integer::intValue).average().getAsDouble();
            
            intentBreakdown.add(IntentBreakdown.builder()
                .category(intent.getLabel())
                .businessValue(defaultBusinessValue(intent))
                .totalPrompts(total)
                .coveredPrompts(covered)
                .coverageRate(coverageRate)
                .avgRanking(avgRanking)
                .build());
        }
        
        // 7. 组装 SceneCoverage 三档
        SceneCoverageGroup highGroup = buildGroup(byIntent, 
            Set.of(PresaleIntentCode.RECOMMENDATION, PresaleIntentCode.COMPARISON),
            intentTotalPrompts,
            extractedCompetitorNames);  // 用于 missing_queries.top_competitor_coverage
        SceneCoverageGroup midGroup = buildGroup(byIntent,
            Set.of(PresaleIntentCode.INQUIRY, PresaleIntentCode.COGNITIVE),
            intentTotalPrompts, extractedCompetitorNames);
        SceneCoverageGroup lowGroup = buildGroup(byIntent,
            Set.of(PresaleIntentCode.SCENARIO),
            intentTotalPrompts, extractedCompetitorNames);
        
        ComputedSnapshotDTO.SceneCoverage sceneCoverage = 
            ComputedSnapshotDTO.SceneCoverage.builder()
                .highValue(highGroup)
                .midValue(midGroup)
                .lowValue(lowGroup)
                .build();
        
        return new SceneAndIntentResult(sceneCoverage, intentBreakdown);
    }
    
    private SceneCoverageGroup buildGroup(
            Map<PresaleIntentCode, List<TemplateWithCovered>> byIntent,
            Set<PresaleIntentCode> intents,
            Map<String, Integer> intentTotalPrompts,
            List<String> extractedCompetitorDisplayNames) {
        
        List<TemplateWithCovered> combined = intents.stream()
            .flatMap(i -> byIntent.get(i).stream()).toList();
        
        int total = intents.stream()
            .mapToInt(i -> intentTotalPrompts.getOrDefault(i.getCode(), 0))
            .sum();
        int covered = (int) combined.stream().filter(TemplateWithCovered::covered).count();
        Double coverageRate = total == 0 ? 0.0 : (covered * 100.0 / total);
        
        List<SceneQueryItem> coveredQueries = combined.stream()
            .filter(TemplateWithCovered::covered)
            .map(twc -> SceneQueryItem.builder()
                .promptCode(twc.template().getPromptCode())
                .promptContent(twc.template().getPromptContent())
                .category(PresaleIntentCode.fromLabel(twc.template().getIntentLabel()).getLabel())
                .build())
            .toList();
        
        List<SceneQueryMissing> missingQueries = combined.stream()
            .filter(twc -> !twc.covered())
            .map(twc -> SceneQueryMissing.builder()
                .promptCode(twc.template().getPromptCode())
                .promptContent(twc.template().getPromptContent())
                .category(PresaleIntentCode.fromLabel(twc.template().getIntentLabel()).getLabel())
                .topCompetitorCoverage(resolveTopCompetitorCoverage(
                    twc.template().getId(), extractedCompetitorDisplayNames, versionId))
                .build())
            .toList();
        
        return SceneCoverageGroup.builder()
            .total(total)
            .covered(covered)
            .coverageRate(coverageRate)
            .coveredQueries(coveredQueries)
            .missingQueries(missingQueries)
            .build();
    }
    
    /**
     * 对某个 missing prompt,返回在该 prompt 的 mentioned_competitors 中出现过的 top3 竞品 display name。
     */
    private List<String> resolveTopCompetitorCoverage(Long templateId,
                                                       List<String> topDisplayNames,
                                                       Long versionId) {
        if (topDisplayNames == null || topDisplayNames.isEmpty()) return List.of();
        
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(
            new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getBatchNo, 1)
                .eq(PresaleAiPromptResult::getPromptTemplateId, templateId)
                .isNotNull(PresaleAiPromptResult::getMentionedCompetitors)
        );
        
        Set<String> mentionedNormalizedSet = new HashSet<>();
        for (PresaleAiPromptResult row : rows) {
            try {
                JsonNode arr = objectMapper.readTree(row.getMentionedCompetitors());
                if (!arr.isArray()) continue;
                for (JsonNode item : arr) {
                    if (item.isTextual()) {
                        mentionedNormalizedSet.add(normalizeName(item.asText()));
                    }
                }
            } catch (Exception ex) { /* skip */ }
        }
        
        return topDisplayNames.stream()
            .filter(dn -> mentionedNormalizedSet.contains(normalizeName(dn)))
            .limit(3)
            .toList();
    }
}

record TemplateWithCovered(PresalePromptTemplate template, boolean covered) {}
public record SceneAndIntentResult(
    ComputedSnapshotDTO.SceneCoverage sceneCoverage,
    List<IntentBreakdown> intentBreakdown
) {}
```

**注意 SceneCoverageCalculator 和 Assembler 的协作**:
- Assembler 在组装 `competitors[]` 时需要 `extractedCompetitorDisplayNames`(从 orchestrator 传入)
- SceneCoverageCalculator 也需要这个列表才能填 `missing_queries.top_competitor_coverage`
- **从 L1 写入 raw_snapshot_json 后,这个列表可以从 raw.competitors[].name 读出来,不需要再从 orchestrator 传**

实际实现选择:在 `enrichAndValidate` 里把 `raw.competitors[].name` 列表取出传给 SceneCoverageCalculator。代码:

```java
// v1 原稿(已作废):4 参示例,多传 extractedCompetitorNames
// SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
//     versionId, rawSnapshot, buildResult.intentTotalPrompts(), extractedCompetitorNames);

// v2 修订(C3):实装采纳 3 参签名
SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
    versionId, rawSnapshot, buildResult.intentTotalPrompts());
```

> **v2 修订(C3)· 签名最终为 3 参**:v1 原稿示例显示 `compute` 接收 4 参(含 `extractedCompetitorNames`)。C3 precheck §2.9 拍板为 **3 参**——竞品 top3 从 `raw.competitors` 内部读(Assembler 已填充),不需要 Orchestrator 再传。这样 Calculator 和 Orchestrator 的 coupling 更松。

### 3.4 新增类 `ScoresCalculator`

**位置**:`com.huanjing.geo.module.presale.generate.calc.ScoresCalculator`

> **v2 修订(C3)· 字段 weights 填 D25 默认值(Q-14 关闭)**:`Scores.Weights` 内部类在 PR-3 DTO 已有(4 字段:mention/ranking/sentiment/coverage)。ScoresCalculator 在 build Scores 对象时填入 D25 默认权重 `{0.30, 0.25, 0.15, 0.30}`。这样 v1 Open Questions Q-14 "Scores DTO 是否需要 weights 字段" 可关闭——**不需要新增,用既有**。
>
> 权重常量定义在 ScoresCalculator 内部:
> ```java
> private static final double W_MENTION = 0.30;
> private static final double W_RANKING = 0.25;
> private static final double W_SENTIMENT = 0.15;
> private static final double W_COVERAGE = 0.30;
> ```

> **v2 修订(C3)· `RankingStats` 独立 public record**:v1 原稿假设 RankingStats 嵌在 ScoresCalculator 内部。实装时拆为**独立的 public record**,位置在 `com.huanjing.geo.module.presale.generate.calc.RankingStats`,字段 `count1 / count2 / count3 / count4 / count5 / countGe6`,附带 `total()` 派生方法。这样 Enricher.queryRankingStats 返回该 record,ScoresCalculator 接收该 record,便于测试独立 mock。

```java
@Component
@RequiredArgsConstructor
public class ScoresCalculator {
    
    /**
     * 按 D25 公式计算五维 + overall。
     */
    public Scores compute(RawSnapshotDTO raw, SceneAndIntentResult scenes) {
        List<PlatformBreakdown> platforms = raw.getPlatformBreakdown();
        
        // ─── mention ───
        int sumMention = platforms.stream().mapToInt(p -> safeInt(p.getMentionCount())).sum();
        int sumTests   = platforms.stream().mapToInt(p -> safeInt(p.getTotalTests())).sum();
        Double mention = sumTests == 0 ? 0.0 : (sumMention * 100.0 / sumTests);
        
        // ─── ranking ───
        //   需要从 platform_breakdown 或 prompt_result 统计 ranking 分布
        //   但 platform_breakdown 只有 primaryRecommendationCount(ranking=1)
        //   ranking=2/3/4/5 需要单独查
        //   决策:ScoresCalculator 通过 Mapper 查一次 batch1 prompt_result 的 ranking 分布
        //         (或 Enricher 在调用前预先查好传入,本设计稿选后者为简)
        //   TODO Codex 实施时选择:是 Calculator 自己查 DB,还是外部传入?
        //         本设计稿推荐外部传入,让 Calculator 保持"纯计算"特性
        RankingStats rs = queryRankingStats(raw);  // 或由上层传入
        int totalRankingRows = rs.total();
        Double ranking;
        if (totalRankingRows == 0) {
            ranking = 0.0;
        } else {
            double sumScore = rs.count1() * 90.0
                            + rs.count2() * 80.0
                            + rs.count3() * 60.0
                            + rs.count4() * 40.0
                            + rs.count5() * 20.0
                            + rs.countGe6() * 0.0;
            ranking = sumScore / totalRankingRows;
        }
        
        // ─── sentiment ───
        int sumPos = platforms.stream().mapToInt(p -> safeInt(p.getSentimentDistribution() == null ? 0 : p.getSentimentDistribution().getPositive())).sum();
        int sumNeu = platforms.stream().mapToInt(p -> safeInt(p.getSentimentDistribution() == null ? 0 : p.getSentimentDistribution().getNeutral())).sum();
        int sumNeg = platforms.stream().mapToInt(p -> safeInt(p.getSentimentDistribution() == null ? 0 : p.getSentimentDistribution().getNegative())).sum();
        int totalSent = sumPos + sumNeu + sumNeg;
        Double sentiment = totalSent == 0 ? 0.0 : 
            ((sumPos * 1.0 + sumNeu * 0.5 + sumNeg * 0.0) / totalSent * 100.0);
        
        // ─── coverage ───
        //   按 D25:(高 covered×2 + 中 covered×1.5 + 低 covered×1)
        //       / (高 total×2 + 中 total×1.5 + 低 total×1) × 100
        var sc = scenes.sceneCoverage();
        double numerator = safeInt(sc.getHighValue().getCovered()) * 2.0
                         + safeInt(sc.getMidValue().getCovered())  * 1.5
                         + safeInt(sc.getLowValue().getCovered())  * 1.0;
        double denominator = safeInt(sc.getHighValue().getTotal()) * 2.0
                           + safeInt(sc.getMidValue().getTotal())  * 1.5
                           + safeInt(sc.getLowValue().getTotal())  * 1.0;
        Double coverage = denominator == 0 ? 0.0 : (numerator / denominator * 100.0);
        
        // ─── overall ───
        Double overall = mention * 0.30
                       + ranking * 0.25
                       + sentiment * 0.15
                       + coverage * 0.30;
        
        return Scores.builder()
            .overall(overall)
            .mention(mention)
            .ranking(ranking)
            .sentiment(sentiment)
            .coverage(coverage)
            .build();
    }
    
    // (...queryRankingStats 实装: SELECT COUNT(*) GROUP BY ranking FROM presale_ai_prompt_result
    //                             WHERE version_id=? AND batch_no=1 AND ranking IS NOT NULL)
}

record RankingStats(int count1, int count2, int count3, int count4, int count5, int countGe6) {
    public int total() { return count1 + count2 + count3 + count4 + count5 + countGe6; }
}
```

**关于 `Scores` 类的 weights 字段 · Q-14 已关闭**:

ScoreSet.java javadoc 说 "L2 `computed_snapshot.scores` 字段更多(含 weights),不复用本类"。**C3 实装时 Q-14 已关闭**:

- `com.huanjing.geo.module.presale.dto.snapshot.computed.Scores` 确实存在,且**有 `Weights` 内部类**(4 字段:mention / ranking / sentiment / coverage)
- `ScoresCalculator` 填入 D25 默认权重 `{0.30, 0.25, 0.15, 0.30}`

> **v2 修订(C1)· Q-14 闭合**:Computed 层 `Scores` 的字段 = 5 维(overall/mention/ranking/sentiment/coverage) + `Weights` 内部类 4 字段。`ScoresCalculator.compute(...)` 末尾 `Scores.builder().weights(Scores.Weights.builder().mention(0.30).ranking(0.25).sentiment(0.15).coverage(0.30).build())...` 填默认权重。v1 原稿 "待 Codex 确认"的 Open Question 已关闭。

### 3.5 新增类 `RoiCalculator`

**位置**:`com.huanjing.geo.module.presale.generate.calc.RoiCalculator`

```java
@Component
public class RoiCalculator {
    
    private static final double PHASE1_UPLIFT = 5.0;
    private static final double PHASE2_UPLIFT = 12.0;
    private static final double PHASE3_UPLIFT = 20.0;
    private static final double SCORE_CAP = 100.0;
    private static final double FIXED_EXPOSURE_MULTIPLIER = 1.8;
    
    /**
     * 按 D27 + D28 + D30 产出 RoiSimulation。
     */
    public RoiSimulation compute(Double overallScore, List<OptimizationFinding> findings) {
        double current = overallScore == null ? 0.0 : overallScore;
        
        double t1 = Math.min(current + PHASE1_UPLIFT, SCORE_CAP);
        double t2 = Math.min(current + PHASE2_UPLIFT, SCORE_CAP);
        double t3 = Math.min(current + PHASE3_UPLIFT, SCORE_CAP);
        
        // D28:按 priority 分配 findings 到 phase
        // v2 修订(C3):null 双重兜底 —— finding==null 和 priority==null 都进 phase3
        int phase1Total = 0, phase2Total = 0, phase3Total = 0;
        for (OptimizationFinding f : findings == null ? List.<OptimizationFinding>of() : findings) {
            if (f == null || f.getPriority() == null) {
                phase3Total++;
                continue;
            }
            switch (f.getPriority()) {
                case HIGH -> phase1Total++;
                case MEDIUM -> phase2Total++;
                case LOW -> phase3Total++;
            }
        }
        
        List<RoiSimulation.RoiPhase> phases = List.of(
            RoiSimulation.RoiPhase.builder()
                .phaseNo(1).durationLabel("M1")
                .targetScore(t1).upliftFromPrevious(t1 - current)
                .completedOptimizationCount(0).totalOptimizationCount(phase1Total).build(),
            RoiSimulation.RoiPhase.builder()
                .phaseNo(2).durationLabel("M2-3")
                .targetScore(t2).upliftFromPrevious(t2 - t1)
                .completedOptimizationCount(0).totalOptimizationCount(phase2Total).build(),
            RoiSimulation.RoiPhase.builder()
                .phaseNo(3).durationLabel("M4-6")
                .targetScore(t3).upliftFromPrevious(t3 - t2)
                .completedOptimizationCount(0).totalOptimizationCount(phase3Total).build()
        );
        
        Double upliftPercent = Double.compare(current, 0.0) == 0 ? 0.0 : ((t3 - current) / current * 100.0);
        // v2 修订(C5):原稿是 current == 0.0 直接比较。double == 严格比较虽然这里实际安全
        // (current 只可能是显式赋 0.0 或直接取入参,不是计算结果),
        // 但 Double.compare 风格更严谨。
        
        return RoiSimulation.builder()
            .currentScore(current)
            .targetScore(t3)
            .estimatedUpliftPercent(upliftPercent)
            .estimatedExposureMultiplier(FIXED_EXPOSURE_MULTIPLIER)
            .phases(phases)
            .build();
    }
}
```

### 3.6 PresaleRuleEngineExecutor 接入

Codex 在 Q-11 答复中确认:

> 入口 Service/方法签名:`public RuleEngineResult execute(RawSnapshotDTO l1, ComputedSnapshotDTO l2)`
> 位置:`PresaleRuleEngineExecutor.java:53`

因此 Enricher 里只需要:

```java
RuleEngineResult ruleResult = ruleEngineExecutor.execute(rawSnapshot, computedSnapshot);
computedSnapshot.setOptimizationFindings(ruleResult.getFindings());
```

**约束**:`ruleEngineExecutor.execute` 调用时机必须在 `scores` / `scene_coverage` 已填后(规则引擎 SpEL 会消费 `#l2.scores` / `#l2.sceneCoverage`)。见 §3.2 的调用顺序。

> **v2 修订(C1)· Q-15 关闭**:规则引擎 SpEL 中 `trigger_expression` **不引用 `#benchmarks`**(C1 期间已确认)。规则 1/2/5/6/7 用 `#l2.scores.xxx`,规则 3/4 用 `#l2.sceneCoverage.xxx` 和 `#l2.intentBreakdown`。因此 benchmark 从 JSON 加载到 raw.benchmarksFrozen 这条路径对规则引擎**完全透明**,无需特殊处理。

> **v2 修订(C3)· 0 命中风险提示(V11 兜底语义)**:规则引擎在某些品牌数据组合下可能 0 命中(所有规则条件都不满足),此时 findings 列表为空。Enricher 不主动阻止,但会 `log.warn` 一次供排障。v1 §8.1 V11 验收条款"optimization_findings **非空列表**"是业务期望,但代码层不硬校验——如果真的 0 命中,流水线仍然走到 DONE,只是 ROI phase 的 totalOptimizationCount 都是 0。这是**软约束**:
>
> ```java
> if (findings == null || findings.isEmpty()) {
>     log.warn("Rule engine returned 0 findings, versionId={}", versionId);
> }
> ```
>
> 详见 §3.2 Enricher 代码。P1 阶段可考虑:若 0 命中则强制塞一条 placeholder finding,或直接 fail。当前 MVP 阶段不做,允许 findings 为空数组。

> **v2 新增参考(C3)· SpEL collection projection 依赖 businessValue**:规则 3 的 trigger_expression 形如 `#l2.intentBreakdown.?[businessValue == '高' && coverageRate < 60]`(SpEL collection selection)。这里 `businessValue` 是 IntentBreakdown 对象的 **bean property**,由 PresaleIntentCode.businessValue() 在 SceneCoverageCalculator 填入 IntentBreakdown。**意味着 businessValue 逻辑有两个消费方**:
> 1. Scores.coverage 计算时的加权(高×2 / 中×1.5 / 低×1)
> 2. 规则引擎 SpEL 的条件匹配
>
> 两者必须一致。v2 通过把 businessValue 抽到 PresaleIntentCode 枚举保障一致(§3.3 修订)。

### 3.7 Builder 的 C 乘法删除

`PlatformIntentBreakdownBuilder.resolveIntentTotalPromptsFromTemplate`:

```java
// 删除以下 3 行:
if (safeInt(row.getHasCompetitorVar()) == 1) {
    base = base * Math.max(competitorCount, 0);
}

// 改为:
//   (循环内直接 map.merge(intentCode.getCode(), base, Integer::sum),不乘 C)
```

**同步改动**:
- 该方法的 `competitorCount` 参数不再使用,可保留签名(向前兼容),或删除(按 Codex 判断)
- `Builder.build` 方法里 `int competitorCount = rawSnapshot.getCompetitors() == null ? 0 : rawSnapshot.getCompetitors().size();` 也可以删(如果 resolveIntent... 签名改了)

> **v2 修订(C3 SQL 补修)· D26 同源契约的 SQL 层保障**:D26 明确"只统计 batch1 通用 prompt(has_competitor_var=0)",要求 `intentTotalPrompts` 和 `SceneCoverageCalculator.covered` **同源**。v1 只写"删 Builder 的 C 乘法"是**不完整**的——如果 SQL 不 filter,Builder 依然会累加 hasCompetitorVar=1 的 template 基数,导致 intentTotalPrompts 包含 batch2 贡献。
>
> C3 review 过程追问暴露该问题,补修:`PresaleAiPromptResultMapper.selectTemplateIntentStats()` 的 @Select 注解原本只有 `WHERE enabled = 1`,补加 `AND has_competitor_var = 0`。至此 D26 同源契约在 **SQL 层 + Java 层双重保障**。
>
> **经验教训**:未来类似"同源契约"的设计,需要**同时审查 SQL 和 Java 层**,不能只改 Java 以为够了。

> **v2 修订(C3)· v1 §7.3 affected tests 预判修正**:v1 §7.3 预判"`PlatformIntentBreakdownBuilderTest` 里 `C=3` 时的 `total_prompts` 断言 → 改为只含 batch1 prompt 数"。C3 precheck §2.6 实际核查发现既有 3 条测试**都没有 C=3 的实际值断言**(都用 hasCompetitorVar=0 的 fixture,或 competitorCount=0/1),**实际 0 条旧断言需改**。反而 C3 **新增 1 条防御性回归测试** `build_templateCountWithCompetitorVar_doesNotMultiplyByCompetitorCount`,mock SQL 返回 hasCompetitorVar=1 row,验证"即便 SQL 意外返回此类 row,Builder 也不乘 C"。SQL 补修后该测试语义转为"防御性",详见 §7.1。

### 3.8 L2 异常归类

```java
// orchestrator catch:
catch (BizException ex) {
    markFailed(versionId, "L2_COMPUTE_ERROR", truncateReason("L2 compute failed: " + ex.getMessage()));
}
// 其他 Exception 由外层 catch(Throwable) 处理
```

> **v2 修订(C3)· catch 链衔接说明**:如 §3.2 修订所述,Enricher 内部只 catch BizException + JsonProcessingException(没有 catch Exception 兜底)。所以 L2_COMPUTE_ERROR 专门保留给 Enricher 主动 throw 的 BizException,基础设施异常(NPE / DataAccessException / RuntimeException 等)会**绕过此 catch 冒到 Orchestrator 最外层**归 UNEXPECTED_ERROR。这是有意设计——避免把 DB 连接失败、OOM 这类基础设施问题误归 L2 业务错误。

---

## §4 `PresaleBenchmarkResolver` + `benchmarks/v1.json`

### 4.1 新增类 `PresaleBenchmarkResolver`

**位置**:`com.huanjing.geo.module.presale.generate.PresaleBenchmarkResolver`

```java
@Component
public class PresaleBenchmarkResolver {
    
    private static final Logger log = LoggerFactory.getLogger(PresaleBenchmarkResolver.class);
    private static final String RESOURCE_PATH = "benchmarks/v1.json";
    private static final String ALL = "_ALL_";
    
    private final ObjectMapper objectMapper;
    private Map<String, BenchmarkJsonEntry> entriesByKey;  // key = industry + "::" + industryRole
    
    public PresaleBenchmarkResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void init() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Benchmark resource not found on classpath: " + RESOURCE_PATH);
            }
            BenchmarkJsonRoot root = objectMapper.readValue(is, BenchmarkJsonRoot.class);
            entriesByKey = new HashMap<>();
            for (BenchmarkJsonEntry entry : root.getEntries()) {
                entriesByKey.put(
                    keyOf(entry.getIndustry(), entry.getIndustryRole()),
                    entry
                );
            }
            // 兜底检查:(_ALL_, _ALL_) 必须存在
            if (!entriesByKey.containsKey(keyOf(ALL, ALL))) {
                throw new IllegalStateException("Benchmark v1.json missing required fallback row: (_ALL_, _ALL_)");
            }
            log.info("PresaleBenchmarkResolver loaded {} entries from {}", entriesByKey.size(), RESOURCE_PATH);
        }
    }
    
    public BenchmarksFrozen resolve(String industry, String industryRole) {
        // 1. EXACT
        BenchmarkJsonEntry entry = entriesByKey.get(keyOf(industry, industryRole));
        MatchLevel match = MatchLevel.EXACT;
        if (entry == null) {
            // 2. FALLBACK_INDUSTRY
            entry = entriesByKey.get(keyOf(industry, ALL));
            match = MatchLevel.FALLBACK_INDUSTRY;
        }
        if (entry == null) {
            // 3. FALLBACK to (_ALL_, _ALL_)
            entry = entriesByKey.get(keyOf(ALL, ALL));
            match = MatchLevel.FALLBACK_INDUSTRY;
        }
        if (entry == null) {
            throw new BizException(500, "BENCHMARK_MISSING: industry=" + industry + ", industryRole=" + industryRole);
        }
        return toBenchmarksFrozen(entry, match);
    }
    
    private BenchmarksFrozen toBenchmarksFrozen(BenchmarkJsonEntry entry, MatchLevel match) {
        return BenchmarksFrozen.builder()
            .industry(entry.getIndustry())
            .industryRole(entry.getIndustryRole())
            .matchLevel(match)
            .industryAvg(toScoreSet(entry.getIndustryAvg()))
            .top1(toScoreSet(entry.getTop1()))
            .top10Score(entry.getTop10Score())
            .confidenceLevel(BenchmarksFrozen.ConfidenceLevel.valueOf(entry.getConfidenceLevel()))
            .source(BenchmarksFrozen.Source.valueOf(entry.getSource()))
            .sampleSize(entry.getSampleSize())
            .industryRanking(entry.getIndustryRanking() == null ? null :
                BenchmarksFrozen.IndustryRanking.builder()
                    .position(entry.getIndustryRanking().getPosition())
                    .total(entry.getIndustryRanking().getTotal())
                    .build())
            .build();
    }
    
    private ScoreSet toScoreSet(BenchmarkJsonScoreSet s) {
        return ScoreSet.builder()
            .overall(s.getOverall())
            .mention(s.getMention())
            .ranking(s.getRanking())
            .sentiment(s.getSentiment())
            .coverage(s.getCoverage())
            .build();
    }
    
    private String keyOf(String industry, String industryRole) {
        return industry + "::" + industryRole;
    }
    
    // ─── 内部 JSON 映射类(Jackson 反序列化目标) ───
    @Data
    public static class BenchmarkJsonRoot {
        private String version;
        private List<BenchmarkJsonEntry> entries;
    }
    
    @Data
    public static class BenchmarkJsonEntry {
        private String industry;
        private String industryRole;
        private BenchmarkJsonScoreSet industryAvg;
        private BenchmarkJsonScoreSet top1;
        private Double top10Score;
        private String confidenceLevel;
        private String source;
        private Integer sampleSize;
        private BenchmarkJsonRanking industryRanking;
    }
    
    @Data
    public static class BenchmarkJsonScoreSet {
        private Double overall;
        private Double mention;
        private Double ranking;
        private Double sentiment;
        private Double coverage;
    }
    
    @Data
    public static class BenchmarkJsonRanking {
        private Integer position;
        private Integer total;
    }
}
```

### 4.2 `benchmarks/v1.json` 模板

**Claude 预填 6 行**(5 个示范行业 + 1 个兜底)。**用户可直接接受或调整数值**。

```json
{
  "version": "v1.0",
  "description": "GEO 诊断基准值 · MVP 占位 · 数值为 Claude 凭业内经验拍,P1 由产品替换为真实行业数据",
  "entries": [
    {
      "industry": "零售",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 55.0, "mention": 60.0, "ranking": 50.0, "sentiment": 65.0, "coverage": 45.0 },
      "top1":        { "overall": 82.0, "mention": 88.0, "ranking": 85.0, "sentiment": 80.0, "coverage": 75.0 },
      "top10Score": 72.0,
      "confidenceLevel": "MEDIUM",
      "source": "AUTO_P50",
      "sampleSize": 120,
      "industryRanking": { "position": null, "total": 120 }
    },
    {
      "industry": "金融",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 62.0, "mention": 65.0, "ranking": 58.0, "sentiment": 70.0, "coverage": 55.0 },
      "top1":        { "overall": 86.0, "mention": 90.0, "ranking": 88.0, "sentiment": 85.0, "coverage": 82.0 },
      "top10Score": 78.0,
      "confidenceLevel": "HIGH",
      "source": "AUTO_P50",
      "sampleSize": 80,
      "industryRanking": { "position": null, "total": 80 }
    },
    {
      "industry": "教育",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 50.0, "mention": 55.0, "ranking": 45.0, "sentiment": 60.0, "coverage": 40.0 },
      "top1":        { "overall": 78.0, "mention": 82.0, "ranking": 78.0, "sentiment": 78.0, "coverage": 70.0 },
      "top10Score": 68.0,
      "confidenceLevel": "MEDIUM",
      "source": "HYBRID",
      "sampleSize": 90,
      "industryRanking": { "position": null, "total": 90 }
    },
    {
      "industry": "医疗",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 48.0, "mention": 52.0, "ranking": 42.0, "sentiment": 62.0, "coverage": 38.0 },
      "top1":        { "overall": 75.0, "mention": 80.0, "ranking": 75.0, "sentiment": 75.0, "coverage": 68.0 },
      "top10Score": 65.0,
      "confidenceLevel": "LOW",
      "source": "MANUAL",
      "sampleSize": 40,
      "industryRanking": { "position": null, "total": 40 }
    },
    {
      "industry": "科技",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 68.0, "mention": 72.0, "ranking": 65.0, "sentiment": 72.0, "coverage": 62.0 },
      "top1":        { "overall": 90.0, "mention": 93.0, "ranking": 92.0, "sentiment": 88.0, "coverage": 87.0 },
      "top10Score": 82.0,
      "confidenceLevel": "HIGH",
      "source": "AUTO_P50",
      "sampleSize": 150,
      "industryRanking": { "position": null, "total": 150 }
    },
    {
      "industry": "_ALL_",
      "industryRole": "_ALL_",
      "industryAvg": { "overall": 60.0, "mention": 65.0, "ranking": 55.0, "sentiment": 68.0, "coverage": 50.0 },
      "top1":        { "overall": 85.0, "mention": 88.0, "ranking": 85.0, "sentiment": 82.0, "coverage": 78.0 },
      "top10Score": 75.0,
      "confidenceLevel": "MEDIUM",
      "source": "HYBRID",
      "sampleSize": 500,
      "industryRanking": { "position": null, "total": 500 }
    }
  ]
}
```

**数值填法说明**:
- `industryAvg.overall`:介于 48-68,按行业成熟度拍(科技最高、医疗最低)
- `top1.overall`:介于 75-90,比行业平均高 15-25 分
- `top10Score`:介于 `industryAvg.overall` 和 `top1.overall` 之间
- `confidenceLevel` / `source`:按枚举合法值选,语义为"此数据的可信度",不影响业务
- `sampleSize`:凭经验拍,40-500
- `industryRanking.position` 填 null(本品牌的行业排名无法凭 JSON 数据算出,演示时该字段前端应兼容 null)
- `_ALL_/_ALL_` 兜底行必须存在(Resolver 会硬校验)

### 4.3 Resolver 的失败分支

`BENCHMARK_MISSING` 只会在两种情况触发:
1. `benchmarks/v1.json` 文件损坏或缺失 → `@PostConstruct` 阶段就抛 `IllegalStateException`,Spring 启动失败
2. 某品牌的 `(industry, industryRole)` 不在 JSON + `(_ALL_, _ALL_)` 兜底也损坏 → Resolver.resolve 运行时抛 `IllegalStateException`

> **v2 修订(C1 + C2)· 异常类型澄清**:v1 原稿第 2 条写 "抛 `BizException`",实装中 Resolver 统一抛 **`IllegalStateException`**(不包装为 BizException)。这让 Orchestrator 的 L1 catch 必须**两层结构**——第一层 catch IllegalStateException → CONFIG_MISSING,第二层 catch BizException(为兼容历史/未来可能出现 BizException 路径,保留 "BENCHMARK_MISSING" 字符串匹配作防御)。详见 §6.1 修订。

Orchestrator 层归类:
- `IllegalStateException` → `markFailed(CONFIG_MISSING)` · 本 PR 主路径
- `BizException` 消息含 `BENCHMARK_MISSING` → `markFailed(CONFIG_MISSING)` · 防御性兼容分支

> **v2 修订(C1)· V13 日志增强**:Resolver.resolve 每次调用 log.info 输出 industry/industryRole/matchLevel,便于排障:
> ```
> [INFO] Benchmark resolved: industry=零售, industryRole=品牌商, matchLevel=EXACT
> [INFO] Benchmark resolved: industry=新兴产业, industryRole=_ALL_, matchLevel=FALLBACK_INDUSTRY
> [INFO] Benchmark resolved: industry=_ALL_, industryRole=_ALL_, matchLevel=FALLBACK_ALL
> ```
> V13 验收条款依赖此日志。

> **v2 修订(C5)· `@Autowired` 标注主构造函数(Spring 容器多构造函数歧义修复)**:C1 实装的 `PresaleBenchmarkResolver` 有**两个构造函数**——生产用 `(ObjectMapper, ResourceLoader)` 两参,测试用 `(ObjectMapper, ResourceLoader, String)` 三参(允许测试传自定义 RESOURCE_PATH)。C1-C4 只跑 Mockito 单元测试(直接调测试构造函数),Spring 容器未介入,所以不需要 `@Autowired`。
>
> **C5 引入 `@SpringBootTest` 后**(`PresaleAiPromptResultMapperIntegrationTest` + `PresaleGenerateEndToEndIntegrationTest`),Spring 容器要真实装配 `PresaleBenchmarkResolver` bean,**多构造函数场景下容器无法自动判断主构造函数**,会抛 ambiguous 错误启动失败。
>
> C5 修复:在生产构造函数(两参)上加 `@Autowired` 标注,消除歧义:
> ```java
> @Autowired
> public PresaleBenchmarkResolver(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
>     this(objectMapper, resourceLoader, RESOURCE_PATH);
> }
> ```
>
> **经验**:任何后续新增 @SpringBootTest 集成测试,都要 sanity check 是否触发类似的多构造函数歧义。**防御思路**:生产代码尽量保持单构造函数;若因测试需要加多构造,显式 @Autowired 标注主构造函数避免未来基础设施坑。

---

## §5 L3_INIT 详设

### 5.1 调用方式(无改动)

Orchestrator 直接调:

```java
String editableJson = l3InitService.derive(rawJson, computedJson);
```

`PresaleL3InitService.derive` 已有完整实现,本 PR **完全不动**。

### 5.2 L3 对 L1/L2 的硬依赖(本 PR 满足)

| L3 依赖字段 | 本 PR 保证 |
|---|---|
| `raw.client_info.brand_name` | Assembler 从 report 拷贝 |
| `raw.client_info.industry` | 同上 |
| `raw.test_summary.total_platforms` | Assembler §2.2.3 |
| `raw.test_summary.total_prompts` | Assembler §2.2.3 |
| `computed.scores.overall` | ScoresCalculator §3.4 |
| `raw.benchmarks_frozen.industry_avg.overall` | BenchmarkResolver §4 |

### 5.3 L3 异常归类

```java
catch (BizException ex) {
    markFailed(versionId, "L3_INIT_ERROR", truncateReason("L3 init failed: " + ex.getMessage()));
}
```

> **v2 修订(C4)· L3 异常类型盘点**:C4 precheck 核实 L3InitService 的异常出口:
> - **主动业务校验**抛 `BizException`(例如 `missing computed.scores.overall`)
> - **JSON 解析失败**转 BizException
> - **其他 RuntimeException**(理论上不应出现)走 Orchestrator 最外层 `catch(Throwable)` 归 UNEXPECTED_ERROR
>
> 因此 L3 层只 catch BizException 即可,与 §6.1 实装一致。

---

## §6 Orchestrator 改动详设

### 6.1 `runRealSkeletonFlow` → `runRealFullFlow`(全面改造)

文件:`PresaleGenerateOrchestrator.java` 第 208-265 行。

```java
private void runRealFullFlow(Long versionId, Long operatorUserId, boolean isManager) {
    PreflightResult preflight = preflight(versionId);
    if (!preflight.success()) {
        markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING,
                truncateReason("CONFIG_MISSING: " + preflight.failureReason()));
        return;
    }
    
    markRunning(versionId, preflight.totalUpperBoundCalls(), preflight.batch1TotalCalls());
    PresaleReportVersion version = versionMapper.selectById(versionId);
    PresaleReport report = version == null ? null : reportMapper.selectById(version.getReportId());
    if (version == null || report == null) {
        markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING, "CONFIG_MISSING: report/version not found");
        return;
    }
    
    Batch1ExecutionResult batch1 = executeBatch1(version, report, operatorUserId, isManager, preflight);
    if (batch1.stopPipeline()) return;
    Set<String> allDegraded = new LinkedHashSet<>(batch1.degradedPlatforms());
    
    enterStage(versionId, STAGE_COMPETITOR_EXTRACT, "extract competitors");
    List<String> competitors = extractTopCompetitorsFromBatch1(versionId, report.getBrandName());
    int cCount = competitors.size();
    int batch2TotalCalls = preflight.platformCount() * preflight.competitorPromptCount() * cCount * 2;
    updateAfterCompetitorExtract(versionId, cCount, batch2TotalCalls,
            preflight.batch1TotalCalls() + batch2TotalCalls);
    
    if (cCount > 0) {
        Batch2ExecutionResult batch2 = executeBatch2(
                versionId, report, operatorUserId, isManager, competitors,
                preflight.competitorPromptCount()
        );
        if (batch2.stopPipeline()) return;
        allDegraded.addAll(batch2.degradedPlatforms());
    } else {
        markCompetitorExtractEmpty(versionId);
        log.info("Skip batch2 because extracted competitors is 0, versionId={}", versionId);
    }
    
    // 【新增】L1 AGGREGATE · v2 修订(C2 + C4):两层 catch
    String rawJson;
    enterStage(versionId, STAGE_L1_AGGREGATE, "assemble raw snapshot");
    try {
        rawJson = rawSnapshotAssembler.assemble(versionId, report, version, allDegraded, competitors);
    } catch (IllegalStateException ex) {
        // 第一层:BenchmarkResolver 的兜底缺失直接冒出的 IllegalStateException
        markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING,
                truncateReason("L1 aggregate failed: " + ex.getMessage()));
        return;
    } catch (BizException ex) {
        // 第二层:Assembler 包装的 BizException
        // 保留 BENCHMARK_MISSING 字符串匹配作防御(虽然现状下不会走到这里)
        String msg = ex.getMessage();
        String category = msg != null && msg.contains("BENCHMARK_MISSING")
                ? FAILURE_CATEGORY_CONFIG_MISSING
                : FAILURE_CATEGORY_L1_SERIALIZATION_ERROR;
        markFailed(versionId, category, truncateReason("L1 aggregate failed: " + msg));
        return;
    }
    writeRawSnapshotJson(versionId, rawJson);
    
    // 【新增】L2 COMPUTE · v2 修订(C4):传 currentComputedJson 支持 regenerate 复用
    String computedJson;
    enterStage(versionId, STAGE_L2_COMPUTE, "compute computed snapshot");
    try {
        PresaleReportVersion current = versionMapper.selectById(versionId);
        String currentComputedJson = current == null ? null : current.getComputedSnapshotJson();
        computedJson = computedSnapshotEnricher.enrichAndValidate(
                versionId, rawJson, currentComputedJson, allowSyntheticFallbackReal
        );
    } catch (BizException ex) {
        markFailed(versionId, FAILURE_CATEGORY_L2_COMPUTE_ERROR,
                truncateReason("L2 compute failed: " + ex.getMessage()));
        return;
    }
    writeComputedSnapshotJson(versionId, computedJson);
    
    // 【新增】L3 INIT
    String editableJson;
    enterStage(versionId, STAGE_L3_INIT, "derive editable content");
    try {
        editableJson = l3InitService.derive(rawJson, computedJson);
    } catch (BizException ex) {
        markFailed(versionId, FAILURE_CATEGORY_L3_INIT_ERROR,
                truncateReason("L3 init failed: " + ex.getMessage()));
        return;
    }
    writeEditableContentJson(versionId, editableJson);
    
    // 【新增】收尾
    markDone(versionId);
    log.info("Presale real full flow done, versionId={}, operatorUserId={}, isManager={}",
            versionId, operatorUserId, isManager);
}
```

> **v2 修订(C2 + C4)· L1 异常归类两层 catch**:v1 §6.1 原稿只有一层 `catch(BizException)`。实装时 C2 确认 Assembler 的 BenchmarkResolver 会直接冒出 `IllegalStateException`(不包装),所以 C4 Orchestrator 必须**两层 catch**:
> - 第一层 `catch(IllegalStateException)` → CONFIG_MISSING 直接落
> - 第二层 `catch(BizException)` + `message.contains("BENCHMARK_MISSING")` 字符串匹配(防御性保留,虽然 Assembler 现状下不会走到这里)
>
> 删掉第一层 catch 会导致 IllegalStateException 绕过 L1 分类冒到最外层,被归为 UNEXPECTED_ERROR——这不符合 V5 验收"benchmark 未命中 → CONFIG_MISSING"。

> **v2 修订(C4)· L2 传 `currentComputedJson`(支持 regenerate 复用)**:v1 §6.1 原稿传 `"{}"` 给 Enricher 的第 3 参。实装时 C4 改为从 DB 重查当前 version 的 `computedSnapshotJson`——理由是支持 **regenerate 场景**:从 DONE / FAILED 重跑时,DB 里可能已有旧的 computedSnapshotJson,Enricher 可以**基于此 extract + 扩展**,而不是每次都从零 `"{}"` 开始。C3 Enricher 实装确实支持以 computedSnapshotJson 为基底(见 §3.2 `extractComputedNode`)。这是 Codex C4 的有意为之的主动改进。

> **v2 修订(C4)· `allowSyntheticFallbackReal` 是 @Value 配置字段**:v1 §6.1 引用了 `allowSyntheticFallbackReal` 但没说明定义位置。实装中该字段是 Orchestrator 的 `@Value("${presale.generate.allow-synthetic-fallback.real:false}")` 注入,默认 false。不需要新增常量。

> **v2 修订(C4)· `allDegraded` 构造时机调整**:v1 原稿在 COMPETITOR_EXTRACT 之后才构造 `allDegraded`。C4 实装把构造移到 `executeBatch1` 返回后(batch1.degradedPlatforms 此时已就绪),提前一步——理由是 allDegraded 在 cCount=0 分支下的正确性更明确(cCount=0 时跳过 batch2.addAll,allDegraded 只含 batch1 的集合,行为自洽)。

### 6.2 `Batch1ExecutionResult` / `Batch2ExecutionResult` 签名扩展

> **v2 修订(C2)· 保持 class 形态不改 record**:v1 原稿用 `record` 重新定义这两个类。C2 实装时发现这两个类已经存在为 `private static final class`,内部有 `Set.copyOf` 做 immutable 防御 + 若干静态工厂方法。**不改为 record**(保持现状,只加 `Set<String> degradedPlatforms` 字段 + `degradedPlatforms()` record-style getter)。这样 C2 的改动面最小,不涉及对现有代码的 record 转换。

```java
// v2 实际实装(非 v1 原稿的 record):
private static final class Batch1ExecutionResult {
    final boolean stopPipeline;
    final Set<String> degradedPlatforms;
    
    private Batch1ExecutionResult(boolean stopPipeline, Set<String> degraded) {
        this.stopPipeline = stopPipeline;
        this.degradedPlatforms = Set.copyOf(degraded);  // immutable 防御
    }
    
    static Batch1ExecutionResult stop(Set<String> degraded) { return new Batch1ExecutionResult(true, degraded); }
    static Batch1ExecutionResult continuePipeline(Set<String> degraded) { return new Batch1ExecutionResult(false, degraded); }
    
    Set<String> degradedPlatforms() { return degradedPlatforms; }   // record-style getter,不是 getDegradedPlatforms
}
// Batch2ExecutionResult 同样
```

**改动点**:`executeBatch1` / `executeBatch2` 方法内部已经维护 `degradedPlatforms` Set(见现有代码 `degradedPlatforms` / `displayDegradedPlatforms` 变量),在 return 之前把这个 Set 传入即可。

### 6.3 `markDone` 实装

```java
private void markDone(Long versionId) {
    PresaleReportVersion current = versionMapper.selectById(versionId);
    int totalCalls = current == null || current.getTotalLlmCalls() == null 
            ? 0 : current.getTotalLlmCalls();
    
    PresaleReportVersion update = new PresaleReportVersion();
    update.setId(versionId);
    update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
    update.setGenerationStage(null);
    update.setCompletedLlmCalls(totalCalls);
    // v2 修订(C4)· 增强字段:
    update.setBatch1CompletedCalls(current == null ? null : current.getBatch1TotalCalls());
    update.setBatch2CompletedCalls(current == null ? null : current.getBatch2TotalCalls());
    update.setFailureCategory(null);      // **v1 漏洞修复**:清空旧 failure
    update.setFailureReason(null);        // **v1 漏洞修复**:清空旧 failure
    update.setUpdatedAt(LocalDateTime.now());
    versionMapper.updateById(update);
    lastProgressUpdateAtByVersion.remove(versionId);
}
```

> **v2 修订(C4)· markDone 的 4 个字段增强**:
> 1. **`setFailureCategory(null) + setFailureReason(null)` 是 v1 漏洞修复**,不是可选增强。**regenerate 从 FAILED 重跑成功时必须清空旧失败信息**,否则版本记录会显示错误的 failure_category 即使状态已 DONE
> 2. `setBatch1CompletedCalls / setBatch2CompletedCalls`:DONE 时把 batch1/batch2 的 completed 补齐到 total,一致性保证
> 3. `setCompletedLlmCalls(totalCalls)`:占位计数收口(v1 原稿已有)
>
> 注意:C4 曾实装有 `setTotalLlmCalls(totalCalls)` 一行,是读 `current.getTotalLlmCalls()` 再写回同样的值,**实际是 no-op**。C5 清理已删除,不在本 v2 代码示例中。

### 6.4 `writeRawSnapshotJson` / `writeComputedSnapshotJson` / `writeEditableContentJson`

```java
private void writeRawSnapshotJson(Long versionId, String json) {
    PresaleReportVersion u = new PresaleReportVersion();
    u.setId(versionId);
    u.setRawSnapshotJson(json);
    u.setUpdatedAt(LocalDateTime.now());
    versionMapper.updateById(u);
}

private void writeComputedSnapshotJson(Long versionId, String json) {
    PresaleReportVersion u = new PresaleReportVersion();
    u.setId(versionId);
    u.setComputedSnapshotJson(json);
    u.setUpdatedAt(LocalDateTime.now());
    versionMapper.updateById(u);
}

private void writeEditableContentJson(Long versionId, String json) {
    PresaleReportVersion u = new PresaleReportVersion();
    u.setId(versionId);
    u.setEditableContentJson(json);
    u.setUpdatedAt(LocalDateTime.now());
    versionMapper.updateById(u);
}
```

> **v2 参考(C4)· 与 `runMockFlow` 风格对齐**:`runMockFlow` 在第 201 行已经以相同模式写这三个快照字段(`setXxxJson + setUpdatedAt + updateById`)。C4 实装保持此风格不抽成 helper,利于代码审阅时"mock flow 和 real flow 并列对照"。未来若需要抽 helper,可一并重构 mock 那里。

### 6.5 新增常量

```java
private static final String FAILURE_CATEGORY_L1_SERIALIZATION_ERROR = "L1_SERIALIZATION_ERROR";
private static final String FAILURE_CATEGORY_L2_COMPUTE_ERROR = "L2_COMPUTE_ERROR";
private static final String FAILURE_CATEGORY_L3_INIT_ERROR = "L3_INIT_ERROR";
```

`STAGE_D_CHECKPOINT` / `SNAPSHOT_BUILD_ERROR` 常量**保留**(不删,以防回退),P1 清理。

### 6.6 `doTriggerGenerate` 的分支名改动

```java
private void doTriggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
    if (mockEnabled) {
        runMockFlow(versionId);
        return;
    }
    runRealFullFlow(versionId, operatorUserId, isManager);  // 原:runRealSkeletonFlow
}
```

`runRealSkeletonFlow` 整个方法重命名为 `runRealFullFlow`,其余历史方法名不变。

### 6.7 构造函数注入新依赖

```java
private final PresaleRawSnapshotAssembler rawSnapshotAssembler;
// Enricher / L3InitService 已经存在,不需新增注入

// 构造函数里加这个参数
```

---

## §7 测试要求

### 7.1 新增测试类

**`PresaleRawSnapshotAssemblerTest`**(至少 5 条):
1. `happyPath`:mock Mapper 返回完整 batch1/batch2 数据 → 返回合法 rawJson,各字段填满
2. `zeroCompetitors`:`extractedCompetitorDisplayNames=[]` → `competitors=[]`,不抛
3. `singlePlatformDegraded`:`degradedPlatforms=["bing_copilot"]` → 该平台 `isDegraded=true`,`test_summary.is_degraded=false`
4. `fourPlatformsDegraded`:`degradedPlatforms.size()=4` → `test_summary.is_degraded=true`
5. `benchmarkMissing`:BenchmarkResolver mock 抛 `BizException("BENCHMARK_MISSING")` → Assembler 冒泡

**`PresaleBenchmarkResolverTest`**(至少 4 条):
1. `exactMatch`:`(金融, _ALL_)` 命中 JSON 里同名行 → `matchLevel=EXACT`
2. `fallbackToIndustryAll`:`(金融, CTO)` JSON 里无 CTO 行 → 回退到 `(金融, _ALL_)`,`matchLevel=FALLBACK_INDUSTRY`
3. `fallbackToAllAll`:`(旅游, _ALL_)` JSON 里无旅游行 → 回退到 `(_ALL_, _ALL_)`,`matchLevel=FALLBACK_INDUSTRY`
4. `startupFailIfAllAllMissing`:JSON 里没有 `(_ALL_, _ALL_)` 行 → `@PostConstruct` 抛 `IllegalStateException`

**`ScoresCalculatorTest`**(至少 4 条):
1. `happyPath`:各维 DB 数据正常 → 五维 + overall 数值合理
2. `allZeroMention`:`mention_count` 全 0 → mention=0, ranking=0, sentiment=? (看 sentimentDistribution 是否有值), coverage 按 scenes 值, overall 按公式
3. `allMentionedAtRank1`:所有 is_mentioned=1 且 ranking=1 → ranking=90(不是 100!)
4. `upperBoundCap`:完美数据 → 五维 100 分, overall = 30+25+15+30 = 100

**`RoiCalculatorTest`**(至少 3 条):
1. `low`:current=45 → phase1=50, phase2=57, phase3=65, uplift=5/7/8, upliftPercent=44.4
2. `high`:current=95 → phase1=100, phase2=100, phase3=100, uplift=5/0/0, upliftPercent=5.3
3. `zero`:current=0 → phase1=5, phase2=12, phase3=20, upliftPercent=0(边界兜底)

**`SceneCoverageCalculatorTest`**(至少 3 条):
1. `happyPath`:正常数据 → 三档 covered / total 数值正确,scene_coverage 与 intent_breakdown 同源
2. `threePlatformsDegraded`:`degraded.size()=3`, effective=6, 门槛=3 → 3 个平台提及即算 covered
3. `missingQueriesWithTopCompetitors`:某 prompt 未覆盖,top3 竞品中有 1 个在 mentioned_competitors 出现 → `top_competitor_coverage=[该 1 个竞品 display name]`

### 7.2 Orchestrator 新增测试

**`PresaleGenerateOrchestratorTest`**(新增 6 条):

1. `realFullFlow_happyPath_reachesDone`:全链路走完到 DONE,三个 JSON 非空
2. `realFullFlow_l1Fails_marksL1Error`:mock Assembler 抛 `BizException("L1 ...")` → `failure_category=L1_SERIALIZATION_ERROR`
3. `realFullFlow_benchmarkMissing_marksConfigMissing`:mock Assembler 抛 **`IllegalStateException`**("BENCHMARK_MISSING ...") → `failure_category=CONFIG_MISSING` · v2 修订(C4)
4. `realFullFlow_l2Fails_marksL2Error`:mock Enricher 抛 `BizException` → `failure_category=L2_COMPUTE_ERROR`
5. `realFullFlow_l3Fails_marksL3Error`:mock L3InitService 抛 → `failure_category=L3_INIT_ERROR`
6. `realFullFlow_zeroCompetitors_reachesDone`:COMPETITOR_EXTRACT 返 0,仍能走完 L1/L2/L3 → DONE

> **v2 修订(C4)· 第 3 条用 IllegalStateException 不是 BizException**:v1 原稿写的是"mock Assembler 抛 BizException('BENCHMARK_MISSING ...')"。实装时用**真实的 IllegalStateException** 类型(BenchmarkResolver 的实际出口类型),**直接验证 L1 两层 catch 的第一层**是正确的——更贴近生产行为。

> **v2 修订(C4)· happyPath 用 `ArgumentCaptor<PresaleReportVersion>`**:断言方法捕获所有 updateById 调用,验证 raw/computed/editable **三字段都被写过**(任何一次 updateById 含非空值)+ 最终 DONE 状态。这种"跟踪字段历史"的断言比简单"最终状态=DONE"更扎实。

> **v2 修订(C4)· 新增 `setupSimpleRealFlow` helper 方法**:从 preflight → batch1/2 → LLM mock 一次搞定,6 条新测试共用。避免每条测试重复 25+ 行 setup 代码。

### 7.3 现有测试修正清单(v2 修正描述)

> **v2 修订(C4)· "断言修正"实际是"补 stub"**:v1 §7.3 预判"修正 STAGE_D_CHECKPOINT 断言"。实际 C4 precheck 通过 `grep -rn STAGE_D_CHECKPOINT geo-server/src/test --include="*.java"` 发现**0 命中**——测试里**从来没有**硬编码 STAGE_D_CHECKPOINT 字符串断言。
>
> 真实影响是**行为变化导致依赖 stub 不全**:C4 前 skeleton 兜底不调用 L1/L2/L3 组件,测试没 mock 它们 → happy;C4 后 runRealFullFlow 会调用它们 → 没 stub 会抛 NPE/UnnecessaryStubbingException。
>
> **正确处理**:在 `@BeforeEach` 统一补 **lenient default stub**:
> ```java
> @BeforeEach
> void setUp() {
>     // ...既有 stub...
>     lenient().when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any()))
>             .thenReturn("{...}");
>     lenient().when(computedSnapshotEnricher.enrichAndValidate(
>             anyLong(), anyString(), nullable(String.class), anyBoolean()))  // 注意 nullable 匹配
>             .thenReturn("{...}");
>     lenient().when(l3InitService.derive(anyString(), anyString())).thenReturn("{}");
> }
> ```
> 关键点:**Enricher 第 3 参用 `nullable(String.class)` 不是 `anyString()`**,因为 Orchestrator 实际传的 currentComputedJson 在 DB 首次查询时可能为 null,anyString 不匹配 null 会导致 stub 失效 → 测试 fail。

**v1 §7.3 中仍然有效的部分**:

- `PlatformIntentBreakdownBuilderTest` 里 `C=3` 时的 `total_prompts` 断言修正 → v1 原稿预估 "需改",实际**0 条需改**(既有测试都用 hasCompetitorVar=0 fixture,或 competitorCount=0/1),详见 §3.7 修订。

**v2 新增 C5 清单**:

- Builder 回归测试 `build_templateCountWithCompetitorVar_doesNotMultiplyByCompetitorCount` 在 C3 SQL 补修后语义转为"防御性测试",加 Javadoc 说明(C5 实装)
- SQL 级集成测试 `PresaleAiPromptResultMapperIntegrationTest`(@SpringBootTest + @Sql),验证 `selectTemplateIntentStats()` 只返回 hasCompetitorVar=0 的 row(C5 实装)
- V5 端到端集成测试:mock LLM + 构造 Resolver 找不到的 (industry, role) 组合 → 断言 CONFIG_MISSING(C5 实装)

### 7.4 spec v4 §10.1 S14 验收条款同步改动

原 S14:

> 任一 DONE 版本,对每个 IntentCode 断言 `computed.intent_breakdown[intent].total_prompts === SUM(computed.platform_intent_breakdown WHERE intent_code=intent).total_prompts / P`

修订为:

> 任一 DONE 版本,对每个 IntentCode 断言(本 PR 后 total_prompts 只算 batch1):
> 1. `computed.intent_breakdown[intent].total_prompts === count(presale_prompt_template WHERE intent=..., has_competitor_var=0, enabled=1)`
> 2. `computed.platform_intent_breakdown[intent].total_prompts === intent_breakdown[intent].total_prompts`(P×5 cell 的 total 都相同)

---

## §8 验收标准

### 8.1 功能验收

| 场景 | 期望 |
|---|---|
| V1 真实 LLM happy path | `mockEnabled=false` 跑完,DONE,三个 JSON 非空,前端详情页能渲染 |
| V2 0 竞品(batch1 无有效 Analyze) | DONE,`raw.competitors=[]`,`batch2_total_calls=0`,`roi_simulation.phases[*].total_optimization_count` 根据规则引擎结果分配 |
| V3 2 平台降级(<4) | DONE,`test_summary.is_degraded=false`,`degraded_platforms=[2 个 code]`,`platform_breakdown[].isDegraded` 准确标记 |
| V4 4 平台降级 fail-fast | FAILED,`failure_category=TOO_MANY_DEGRADED_PLATFORMS`(不变) |
| V5 benchmark 未命中 | FAILED,`failure_category=CONFIG_MISSING`,`failure_reason` 含 "BENCHMARK_MISSING" |
| V6 `STAGE_D_CHECKPOINT` 永不出现 | grep log 和 DB `failure_category` 列,`mockEnabled=false` 分支下永远不出现 `STAGE_D_CHECKPOINT` |
| V7 mock 兼容 | `mockEnabled=true` 行为与 PR-3 F2-B 完全一致,mock 测试全绿 |
| V8 scores 全维非 null | DONE 版本,`computed.scores.{overall, mention, ranking, sentiment, coverage}` 五字段全 non-null Double |
| V9 scene_coverage 非 null | DONE 版本,`computed.scene_coverage.{high_value, mid_value, low_value}` 三档全非空 `SceneCoverageGroup` 对象 |
| V10 IntentBreakdown 同源 | 对每个 IntentCode,`intent_breakdown[intent].covered_prompts == sum(scene_coverage[group].covered WHERE intent 属于该 group)` |
| V11 规则引擎已接入 | DONE 版本,`computed.optimization_findings` **非空列表**(至少 1 条;规则命中的具体数量取决于品牌数据) |
| V12 ROI phases 严格 3 条 | DONE 版本,`roi_simulation.phases.size()==3`,phase_no 分别 1/2/3 |
| V13 benchmark 命中日志 | 每次 Resolver.resolve 调用 log.info 输出 industry/industryRole/matchLevel(便于排障) |
| V14 L3 文案合理 | DONE 版本,`executive_summary.headline` 包含品牌名 + 行业名 + 分数 + deltaLabel;`executive_summary.paragraph` 合理填充 |
| V15 前端 mergeSnapshot 兼容 | DONE 版本,前端 `mergeSnapshot(raw, computed, editable, versionRow)` 不抛异常,`MergedViewDTO` 所有字段正常消费 |

### 8.2 非功能验收

- 所有既有 PR-3 测试(25+条)绿
- 新增测试 15+ 条全绿
- `grep -rn "STAGE_D_CHECKPOINT" geo-server/src/main/java/com/huanjing/geo/module/presale/generate/PresaleGenerateOrchestrator.java` 只应在常量声明处出现
- 新增类单元测试覆盖率 ≥ 80%
- `mvn test` 全绿

---

## §9 Codex 实施建议(5 个 Checkpoint)

每个 Checkpoint 独立 PR 提交,Claude review 通过后再进下一个。

### Checkpoint 1(0.5 天):Benchmark 基础设施
- [ ] `PresaleBenchmarkResolver` 类 + 单元测试(EXACT / FALLBACK / 启动兜底校验)
- [ ] `benchmarks/v1.json` 文件(按本稿 §4.2 模板,可直接复用)
- [ ] **review 通过标志**:`PresaleBenchmarkResolverTest` 全绿

### Checkpoint 2(2-2.5 天):L1 Raw Snapshot Assembler
- [ ] `PresaleRawSnapshotAssembler` 类 + 单元测试
- [ ] 7 个子字段装配逻辑全部落地,按本稿 §2 实装
- [ ] `Batch1ExecutionResult` / `Batch2ExecutionResult` 签名扩展(增加 `degradedPlatforms` 回传)
- [ ] **review 通过标志**:`PresaleRawSnapshotAssemblerTest` 全绿 + 既有 Orchestrator 测试不被破坏

### Checkpoint 3(1.5 天):L2 Calc 三件套 + Enricher 接入 + Builder 改造
- [ ] `SceneCoverageCalculator` 类 + 单元测试
- [ ] `ScoresCalculator` 类 + 单元测试
- [ ] `RoiCalculator` 类 + 单元测试
- [ ] `PresaleComputedSnapshotEnricher.enrichAndValidate` 扩展,按本稿 §3.2 顺序调用
- [ ] `PlatformIntentBreakdownBuilder` 的 C 乘法删除 + 测试同步修正
- [ ] `PresaleRuleEngineExecutor.execute` 接入 Enricher
- [x] **Open Question 解决**(C3 完成):Scores 类实际字段(Q-14 已闭合,见 §3.4 v2 修订)、规则引擎 SpEL `#benchmarks` 行为(Q-15 已闭合,见 §4.3 v2 修订 + §10 闭合表)
- [ ] **review 通过标志**:新三测试类全绿 + `PresaleComputedSnapshotEnricher` 已有测试不破坏(或同步修正)

### Checkpoint 4(1 天):Orchestrator wiring
- [x] `runRealSkeletonFlow` 重命名 + 实装为 `runRealFullFlow`
- [x] `markDone` / `writeRawSnapshotJson` / `writeComputedSnapshotJson` / `writeEditableContentJson` 方法新增
- [x] 新增常量 `L1_SERIALIZATION_ERROR` / `L2_COMPUTE_ERROR` / `L3_INIT_ERROR`
- [x] Orchestrator 构造函数注入 `PresaleRawSnapshotAssembler`
- [x] **v2 新增(C4 主动增强)**:L1 两层 catch(IllegalStateException + BizException)+ L2 传 `currentComputedJson` 支持 regenerate 复用 + markDone 清空 failureCategory/reason(漏洞修复)
- [x] **v2 新增(C4 必做)**:既有 Orchestrator 测试 13 条补 @BeforeEach lenient stub(v1 §7.3 预估"断言修正"实际是"补 stub",见 §7.3 修订)
- [x] Orchestrator 新增 6 条 `realFullFlow_*` 测试(按本稿 §7.2)
- [x] **review 通过标志**:`mvn test` 全绿(149/149 → C5 清理后累计 151+)

### Checkpoint 5(1 天):清理 + 集成测试 + 文档升 v2
- [x] A 清单 5 条代码小观察清理:
  - Enricher.queryRankingStats `ranking <= 1` → `== 1`
  - RoiCalculator `current == 0.0` → `Double.compare`
  - markDone `setTotalLlmCalls(totalCalls)` no-op 删除
  - Builder 回归测试加"防御性测试"Javadoc
  - Enricher default stub 简化
- [x] `PresaleAiPromptResultMapperIntegrationTest` @SpringBootTest + @Sql(验证 SQL 同源 filter)
- [x] V5 端到端 @SpringBootTest(benchmark 未命中 → CONFIG_MISSING)
- [x] spec v4 §10.1 S14 验收条款同步改动(见本稿 §7.4)
- [x] v1 设计稿升 v2 + decisions-summary 升 v2
- [x] **review 通过标志**:`mvn test` 全绿,PR-3.D2 完成

> **v2 修订(C5)· CP5 实际交付偏离 v1 原稿**:v1 §9 CP5 原稿聚焦"realFullFlow 6 条新测试 + STAGE_D_CHECKPOINT 断言修正",这两项在 **C4 实际已完成**(realFullFlow 6 条是 C4 新增 + 既有测试补 stub 也在 C4 完成)。C5 实际聚焦:**代码清理 + 集成测试(V5 端到端 + SQL 集成)+ 文档升 v2**,与 v1 原规划不同但更合理。

> **v2 修订(C5)· v1 §9 原稿 CP4 清单遗漏项**:原 CP4 没列"STAGE_D_CHECKPOINT 断言修正",预想放 CP5。C4 review 时发现 runRealFullFlow 行为变化必然连带影响既有测试,必须 C4 当场做 stub 补齐(见 §7.3 修订)。这是 v1 §9 的一处预估错误,v2 已修订。

### Checkpoint 总控时序(实际节奏)

```
D+0         D+0.5       D+3         D+5         D+5.5       D+6
 ├── C1 ────┼── C2 ─────┼── C3 ─────┼── C4 ─────┼── C5 ─────┤
 │ 0.5d     │ 2.5d      │ 2d        │ 1d        │ 1d        │
 │ Benchmark│ Assembler │ L2 三件套 │ Orchestra │ 清理+集成 │
 │          │ + Aggregt │ + Rule    │ wiring    │ 测试+v2   │
 │          │           │ + Builder │ + 主动增强│ 文档      │
 │          │           │ + SQL 补修│           │           │
```

按理想节奏 6.5 天,有波折 8 天。

---

## §10 Open Questions(待 Codex 实施时确认)

从 v0 继承但无需本轮用户拍板的:

| 编号 | 问题 | 本 PR 处理方式 |
|---|---|---|
| Q-1 | `RawMeta.formula_version` 硬编码还是读 config? | 硬编码 "v1.0",P1 考虑 config |
| Q-2 | `test_summary.total_calls` 含 SKIPPED_DEGRADED 吗? | 不含(本稿 §2.2.3 决策) |
| Q-3 | `Batch1/Batch2ExecutionResult` 回传 `degradedPlatforms`? | 是,本稿 §6.2 改造 |
| Q-4 | `total_tests` 语义 | **`is_mentioned IS NOT NULL` 的行数**(只保留 Analyze 有结果的行;本稿 §2.2.4 定稿)|
| Q-5 | Assembler 额外查 `ai_platform_config` 拿 platform_name 接受吗? | 接受,本稿实装 |
| Q-6 | `competitor.mention_rate` 分母 | batch1 Analyze 成功的 prompt_result 行数(本稿 §2.2.5 决策) |
| Q-7 | `scene_advantages_raw` 聚合规则 | 频次 top-5,同频次按字典序(本稿 §2.2.5 实装) |
| Q-8 | batch2 `WHERE competitor_name=?` 用 display 还是归一化? | Display 原写入值,与 F1 对齐(本稿 §2.2.5 确认) |
| Q-9 | `top_keywords` / `negative_evidence` 填 null 合法吗? | 合法,schema optional(本稿 §2.2.6) |
| Q-10 | benchmark 未命中 failure_category | CONFIG_MISSING(本稿 §6.1 确认) |
| Q-13 | markDone 传 preflight 值还是读 version? | 读 version.total_llm_calls(本稿 §6.3 实装) |

本轮新增(**C3 实施后均已闭合**):

| 编号 | 问题 | 闭合结论 |
|---|---|---|
| **Q-14** | `com.huanjing.geo.module.presale.dto.snapshot.computed.Scores` 类的实际字段清单? | **已闭合(C1/C3)**:5 维(overall/mention/ranking/sentiment/coverage)+ `Weights` 内部类(4 字段)。`ScoresCalculator` 填默认权重 `{0.30, 0.25, 0.15, 0.30}`,见 §3.4 |
| **Q-15** | 规则引擎 SpEL `#benchmarks` 的行为? | **已闭合(C3)**:seed SQL V64 确认 trigger_expression **不引用** `#benchmarks`,只用 `#l1` 和 `#l2` 上下文。SpEL projection 实际用 `#l2.intentBreakdown.?[businessValue == '高']` 等形态,**依赖 IntentBreakdown 的 `businessValue` 字段**(该字段来自 `PresaleIntentCode.businessValue()` 枚举方法)|

---

## §11 禁止做的事

- ❌ 不要擅改 `PresaleL3InitService.derive` 的行为
- ❌ 不要擅改 `PresaleComputedSnapshotEnricher.enrichAndValidate` 签名(对 mock 流程透明)
- ❌ 不要删除 `STAGE_D_CHECKPOINT` / `SNAPSHOT_BUILD_ERROR` 常量(保留)
- ❌ 不要动 BATCH1 / BATCH2 / COMPETITOR_EXTRACT / Preflight / ReuseDecision / ReusePersistence 逻辑
- ❌ 不要新增 `RawSnapshotDTO` / `ComputedSnapshotDTO` / `EditableContentDTO` 子 DTO 字段
- ❌ 不要在 Assembler 里直接写 version 表(写 DB 由 orchestrator 负责)
- ❌ 不要让 `PresaleRawSnapshotAssembler` 带 `@Transactional`(纯读 + 序列化,不需要事务)
- ❌ 不要把 `presale_benchmark` 设计成 DB 表(本 PR 用 classpath JSON,P1 再讨论建表)
- ❌ 不要把规则引擎输出的 `optimization_findings` 改动后再写入(规则引擎是唯一生产者)
- ❌ 不要合并 intent_breakdown 和 scene_coverage 的计算逻辑(两者通过 `SceneCoverageCalculator` 同源产出,但上层 DTO 是两个独立字段,不能共用引用)

---

## §12 风险与提示

### 12.1 scores 公式的风险

五维公式(D25)基于用户拍板的业务意图,**未经产品深度 review**。演示时如果客户对某维分数追问:

- "为什么我的 mention=72 分?"
- "ranking=58 怎么算的?"
- "coverage 加权里高价值 = 2 倍怎么定的?"

只能如实回答"MVP 阶段按此公式,产品会在 P1 做公式评审"。**建议本 PR 合并后立即发起产品 review,拿到产品口径后再做真实客户演示**。

### 12.2 benchmark 占位的风险

`benchmarks/v1.json` 6 行全是凭业内经验拍的数字。同一行业不同品牌拿到同一份基准。如果客户对比两份同行业报告,会发现"行业平均一模一样",需要解释"MVP 占位,真实基准在 P1"。

### 12.3 exposure_multiplier = 1.8 的风险

所有品牌都是 1.8 倍曝光。演示时客户追问按你口径如实答。

### 12.4 性能风险

`PresaleRawSnapshotAssembler.assemble` 会查 DB 至少 10 次(按平台循环 + 竞品循环 + benchmark 读一次)。对单次报告生成(一次性流水线尾声)影响可接受。如果压测发现慢,P1 可合并查询或加缓存。

### 12.5 规则引擎 SpEL 行为风险(Q-15)

`PresaleRuleEngineExecutor` 接入后,规则引擎里 10 条规则的触发结果**不完全可预测**。本 PR 设计验收 V11 要求 `optimization_findings` 非空,如果某些组合下规则一条都不触发,V11 验收失败。Codex 实施 Checkpoint 3 时需要对照真实规则 SpEL 确认。

---

## §13 v2 修订索引(维护者速查)

本章节汇总从 v1 升 v2 过程中整合的全部修订点,按 Checkpoint 来源分组。每条修订都在对应章节用 `> **v2 修订(CN)**:...` 标注。

### 来自 C1(Benchmark 基础设施)· 3 条

1. **§3.4 Q-14 关闭**:`Scores` DTO 有 `Weights` 内部类(4 字段),填 D25 默认值 `{0.30, 0.25, 0.15, 0.30}`
2. **§4.3 V13 日志增强**:Resolver.resolve 每次调用 log.info 输出 industry/industryRole/matchLevel
3. **§4.3 规则 0 命中风险 + V11 log.warn**:Enricher 检测到 optimization_findings 空列表时 log.warn 提示规则引擎 0 命中

### 来自 C2(L1 Assembler)· 10 条

4. **§2.2.4 / Q-4 `total_tests` 语义**:从"Query 成功数"改为 `is_mentioned IS NOT NULL`,即**只保留 Analyze 有结果的行**(Analyze 成功或业务失败但 is_mentioned 被写入的行;Analyze 超时/网络失败这类 is_mentioned 为 NULL 的行不计入)。v4 文档描述与代码不符,以代码为准
5. **§2.2.5 / §6.2 `PresaleCompetitorAggregator` 共用组件**:独立成类,供 Orchestrator + L1 Assembler 双方消费,避免方法重复
6. **§6.2 `ExecutionResult` 保持 `class` 形态**:v1 原稿写 "record",实装保留 `private static final class` 以维持 `Set.copyOf` immutable 防御
7. **§2.2.5 双归一化场景分派**:Orchestrator 的竞品抽取用语义归并(normalize 后相同合并),ReuseDecisionService 的命中查询用精确名匹配,职责分工明确
8. **§2.3 异常归类表新增 `IllegalStateException` 直接冒出**:BenchmarkResolver 抛的 `IllegalStateException` 不包装为 BizException,Assembler 内部 `catch(IllegalStateException ex) { throw ex; }` 直接透出,由 Orchestrator 层两层 catch 处理
9. **§2.2.5 Aggregator 三元组返回优化**:`extractTopCompetitorsFromBatch1` 内部复用 `denominatorRows`,少一次 DB 查询
10. **§2.2.1 Assembler 三层兜底**:对 report 的 brandName/industry/industryRole 做 null/blank 三层防御
11. **§2.2.3 `failedCalls` 防负**:`totalCalls - completedCalls` 结果 clamp 到 `>=0`,避免 DB 竞态导致负数
12. **§3.3 末尾 4 参 `SceneCoverageCalculator` 示例删除**:与 kickoff §2.9 3 参拍板(removing `computedSnapshotJson` 参数,因 Enricher 已 extract)
13. **spec §3.5 `call_status` 注释对齐到 `SUCCESS/FAILED/SKIPPED_DEGRADED`**

### 来自 C3(L2 三件套 + SQL 补修)· 12 条

14. **§3.3 `PresaleIntentCode.businessValue()` 抽到枚举**(C 方案):档位映射不再散落在 `Enricher.defaultBusinessValue`,集中到枚举
15. **§3.3 SpEL collection projection 依赖 `businessValue` 关联说明**:规则引擎 V64 seed SQL 的 trigger_expression 用 `#l2.intentBreakdown.?[businessValue == '高']` 形态(projection 筛选高价值 intent),依赖 `IntentBreakdown.businessValue` 字段,该字段值来自 `PresaleIntentCode.businessValue()` 枚举方法
16. **§3.3 `SceneCoverageCalculator` 用 `Map<Long, Set<String>>` 自动去重**:同平台同模板多行 `is_mentioned=1` 自动去重,比 counter 更稳
17. **§3.3 `pt.category` 与 `intentLabel` 别名说明**:SQL 查询用 `pt.category AS intentLabel`,Java 层统一用 `intentLabel`
18. **§3.4 `Scores.weights` 填 D25 默认值**(C1 Q-14 闭合)
19. **§3.4 `RankingStats` 独立 `public record`**:字段命名 `count1/count2/count3/count4/count5/countGe6`(比 `c1-c6` 更规范)
20. **§3.5 D28 null 双重兜底**:`finding == null` + `finding != null && priority == null` 都归 phase3
21. **§3.6 Q-15 关闭**:trigger_expression **不**引用 `#benchmarks`,只用 L1/L2 计算结果
22. **§3.6 `RuleEngineResult` 完整字段**:`findings / evaluatedRuleCount / hitCount / errors` 4 字段
23. **§3.7 / §7.3 affected tests 预判修正**:v1 预判"C=3 时的 total_prompts 断言需改",实际 **0 条**需改(测试都用 hasCompetitorVar=0 fixture);反而新增 1 条防御性回归测试
24. **§3.7 SQL 同源 filter**:`PresaleAiPromptResultMapper.selectTemplateIntentStats()` 的 @Select 注解补加 `AND has_competitor_var = 0`(C3 SQL 补修),D26 同源契约在 SQL + Java 层**双重保障**
25. **§8 V11 语义补充**:规则 0 命中时 log.warn + DONE,不抛异常(v1 "optimization_findings 非空列表"预期要边界补充)

### 来自 C4(Orchestrator wiring)· 9 条

26. **§6.1 L2 传 `currentComputedJson`**(支持 regenerate 复用):v1 原稿传 `"{}"`,实装改为从 DB 重查当前 version 的 `computedSnapshotJson`
27. **§6.3 markDone 清空 failureCategory/failureReason**:**v1 漏洞修复**(regenerate 从 FAILED 重跑成功时必须清空旧失败信息)
28. **§6.3 markDone 填 batch1/batch2 `CompletedCalls`**:DONE 状态下 completed=total 一致性保证
29. **§6.4 write 方法可对齐 `runMockFlow` 第 201 行风格**:标准 MyBatis-Plus 部分更新
30. **§6.1 `allowSyntheticFallbackReal` 是 `@Value` 配置字段不是常量**:默认 false,可配置
31. **§7.2 新增 `setupSimpleRealFlow` helper 方法**:6 条新测试共用,避免重复 25+ 行 setup
32. **§7.2 Enricher default stub 用 `nullable(String.class)` 不是 `anyString()`**:因 currentComputedJson 首次查询可能为 null
33. **§7.3 描述修订**:v1 "断言修正"实际是"补 stub"(测试代码没有硬编码 STAGE_D_CHECKPOINT 字符串,只是行为链变长需要补 mock)
34. **§9 CP4 清单补"STAGE_D_CHECKPOINT 断言修正"**:原 v1 §9 遗漏项

### 来自 C5(清理 + 集成测试 + 文档升 v2)· 8 条

35. **§3.4 `ranking <= 1` → `== 1`**:DB 异常数据 ranking=0 不再夸大评分到 90
36. **§3.5 `current == 0.0` → `Double.compare(current, 0.0) == 0`**:double 严格比较的风格优化
37. **§6.3 删除 `setTotalLlmCalls(totalCalls)` no-op**:C4 实装冗余,C5 清理
38. **§7.1 Builder 回归测试加"防御性测试"Javadoc**:`build_templateCountWithCompetitorVar_doesNotMultiplyByCompetitorCount` 在 C3 SQL 补修后语义转为防御性
39. **§7.2 Enricher default stub 从详细 fixture 简化为 `"{}"`**:151 绿验证没有测试依赖那些 fields
40. **§4.3 `@Autowired` 标注主构造函数**:C5 引入 @SpringBootTest 后,Spring 容器多构造函数歧义修复(基础设施连带修复)
41. **§7.1 `PresaleAiPromptResultMapperIntegrationTest` (@SpringBootTest + @Sql)**:真实 DB 验证 SQL filter,3 行 fixture 同时验 hasCompetitorVar filter + enabled filter + templateCount
42. **§7.1 `PresaleGenerateEndToEndIntegrationTest` (@SpringBootTest)**:V5 benchmark 未命中端到端(mock Assembler 抛 IllegalStateException → CONFIG_MISSING)

### 元方法论修订 · 1 条

43. **§0 新增"v5 handoff §7.1 第 6 条'交接文档可能与代码现状脱节'的三次印证"**:C2 `total_tests`、C3 `selectTemplateIntentStats` SQL filter、C4 §9 CP4 遗漏项

**修订合计:43 条**(C1: 3 · C2: 10 · C3: 12 · C4: 9 · C5: 8 · 元方法论: 1)

---

## §14 v2 签署

| 角色 | 职责 | 会签 |
|---|---|---|
| **决策者** | 用户 | ✅ D25-D30 决策已会签,C1-C5 五个 Checkpoint 逐个合主干会签 |
| **契约守护** | Claude | ✅ 每个 Checkpoint kickoff + precheck + review 全流程负责,v2 起草人 |
| **实施者** | Codex | ✅ 按 5 Checkpoint 节奏实施完成,C5 PR#1 交付 151/151 绿 |

**PR-3.D2 实装完成**。后端真实 LLM 流水线可端到端走通,解除真 LLM 联调阻塞。

**P1 人工验收清单**(用户后续安排):
- V1 真实 LLM happy path · 实际跑 mockEnabled=false + 真 LLM 调用
- V13 Resolver log.info 输出验证 · 启动后看日志
- V14 L3 文案合理 · 验证 executive_summary.headline/paragraph 包含品牌名/行业名/分数/deltaLabel
- V15 前端 mergeSnapshot 兼容 · 前端消费 raw/computed/editable 三 JSON

---

**v2 定稿 END**
