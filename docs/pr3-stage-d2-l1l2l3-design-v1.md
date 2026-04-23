# PR-3.D2:L1_AGGREGATE / L2_COMPUTE / L3_INIT 补齐实装设计稿 · v1 定稿

| 字段 | 值 |
|---|---|
| 版本 | v1(定稿,取代 v0) |
| 起草日期 | 2026-04-22 |
| 作者 | Claude,基于用户与 Codex 的 D25-D30 决策 |
| 前置 PR | PR-3 阶段 F2-B(commit `cfffbcb1`) |
| 预计代码改动量 | 1400-1800 行新代码 + 约 15 条新测试 |
| 预计时间 | Codex 实施 6-8 天(5 checkpoint 节奏) |
| 配套文档 | `pr3-stage-d2-decisions-summary.md`(1 页决策摘要) |
| 会签角色 | 用户(决策 + 会签)/ Codex(实施)/ Claude(契约守护 + review) |

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
private final ObjectMapper objectMapper;
```

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
int failedCalls    = totalCalls - successfulCalls;
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
    List<PresaleAiPromptResult> batch1Rows = aiPromptResultMapper.selectList(
        new LambdaQueryWrapper<PresaleAiPromptResult>()
            .eq(PresaleAiPromptResult::getVersionId, versionId)
            .eq(PresaleAiPromptResult::getPlatformCode, platformCode)
            .eq(PresaleAiPromptResult::getBatchNo, 1)
    );
    
    int totalTests  = batch1Rows.size();   // prompt_result 行数(Query 成功才建行)
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

**`total_tests` 语义锁定**(v0 Open Question Q-4):本 PR 采用 "batch1 prompt_result 行数"(即 Query 成功数)。理由:
- prompt_result 表只有 Query 成功才建行(见 orchestrator `insertPromptResult*`)
- 业务语义:这是"已经跑完 Query 的测试数",对应 "测试了多少次"
- `mention_rate` 分母用 `total_tests`,即 Query 成功数,非 Q_gen=25

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
    // benchmark 未命中(BENCHMARK_MISSING)已由 Resolver 抛出
    // 其他 BizException 来自子方法
    throw ex;
} catch (JsonProcessingException ex) {
    throw new BizException(500, "L1 aggregate failed: JSON serialization error - " + ex.getMessage());
} catch (Exception ex) {
    throw new BizException(500, "L1 aggregate failed: " + ex.getMessage());
}
```

Orchestrator 层捕获:
- `BizException` 消息含 `BENCHMARK_MISSING` → `markFailed(CONFIG_MISSING)`
- 其他 `BizException` → `markFailed(L1_SERIALIZATION_ERROR)`
- 其他异常 → 由 orchestrator 外层 `catch (Throwable)` 处理,归 `UNEXPECTED_ERROR` 或 `INTERRUPTED`

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

不改 `enrichAndValidate` 方法签名(对 mock 流程透明),在方法内部新增调用链:

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
        //   注意:intent_breakdown 的 covered/rate/avg_ranking 需要在这里算出来,
        //         不能在 mergeIntentBreakdown 之前算,因为 mergeIntentBreakdown 会
        //         填充新对象覆盖我们算的值。
        //   顺序:先 intent + scene 一起算,再合进 computedSnapshot
        SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
                versionId, rawSnapshot, buildResult.intentTotalPrompts());
        computedSnapshot.setSceneCoverage(scenes.sceneCoverage());
        computedSnapshot.setIntentBreakdown(scenes.intentBreakdown());
        
        // 【新增】D25 scores 五维计算
        Scores scores = scoresCalculator.compute(rawSnapshot, scenes);
        computedSnapshot.setScores(scores);
        
        // 【新增】规则引擎接入,产出 optimization_findings
        RuleEngineResult ruleResult = ruleEngineExecutor.execute(rawSnapshot, computedSnapshot);
        computedSnapshot.setOptimizationFindings(ruleResult.getFindings());
        
        // 【新增】D27 + D28 + D30 roi_simulation
        RoiSimulation roi = roiCalculator.compute(
                scores.getOverall(),
                ruleResult.getFindings()
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
```

**新增依赖**:

```java
private final PlatformIntentBreakdownBuilder builder;          // 既有
private final PlatformIntentBreakdownValidator validator;       // 既有
// 新增:
private final SceneCoverageCalculator sceneCoverageCalculator;
private final ScoresCalculator scoresCalculator;
private final PresaleRuleEngineExecutor ruleEngineExecutor;
private final RoiCalculator roiCalculator;
```

### 3.3 新增类 `SceneCoverageCalculator`

**位置**:`com.huanjing.geo.module.presale.generate.calc.SceneCoverageCalculator`

**职责**:按 D26 产出 `SceneCoverage` 对象和"同源"的 `IntentBreakdown` 列表

```java
@Component
@RequiredArgsConstructor
public class SceneCoverageCalculator {
    
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    
    /**
     * @param versionId           版本 ID
     * @param raw                 L1,用 raw.test_summary.degraded_platforms 计算 effective_platforms
     * @param intentTotalPrompts  由 Builder 产出的 intent→total_prompts 映射
     *                            (已经过 D26 改动:只算 batch1)
     */
    public SceneAndIntentResult compute(Long versionId,
                                        RawSnapshotDTO raw,
                                        Map<String, Integer> intentTotalPrompts) {
        // 1. 查所有 enabled 平台
        Set<String> allPlatforms = aiPlatformConfigMapper.selectList(
            new LambdaQueryWrapper<AiPlatformConfig>().eq(AiPlatformConfig::getEnabled, true)
        ).stream().map(AiPlatformConfig::getPlatformCode).collect(Collectors.toSet());
        
        // 2. effective_platforms = all - degraded
        Set<String> degraded = raw.getTestSummary().getDegradedPlatforms() == null ? 
            Set.of() : new HashSet<>(raw.getTestSummary().getDegradedPlatforms());
        Set<String> effective = new HashSet<>(allPlatforms);
        effective.removeAll(degraded);
        int threshold = (int) Math.ceil(effective.size() / 2.0);  // 门槛
        
        // 3. 查所有 batch1 prompt 模板
        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
            new LambdaQueryWrapper<PresalePromptTemplate>()
                .eq(PresalePromptTemplate::getEnabled, 1)
                .eq(PresalePromptTemplate::getHasCompetitorVar, 0)
        );
        
        // 4. 对每个 prompt 查命中平台数
        Map<Long, Integer> hitCountByTemplateId = new HashMap<>();  // template_id → M
        Map<Long, List<Integer>> rankingsByTemplateId = new HashMap<>();  // 用于 avg_ranking
        
        List<PresaleAiPromptResult> allBatch1Rows = aiPromptResultMapper.selectList(
            new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getBatchNo, 1)
        );
        
        for (PresaleAiPromptResult row : allBatch1Rows) {
            if (!effective.contains(row.getPlatformCode())) continue;
            if (!Integer.valueOf(1).equals(row.getIsMentioned())) continue;
            hitCountByTemplateId.merge(row.getPromptTemplateId(), 1, Integer::sum);
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
List<String> extractedCompetitorNames = raw.getCompetitors() == null ? List.of() :
    raw.getCompetitors().stream().map(Competitor::getName).toList();
SceneAndIntentResult scenes = sceneCoverageCalculator.compute(
    versionId, rawSnapshot, buildResult.intentTotalPrompts(), extractedCompetitorNames);
```

`SceneCoverageCalculator.compute` 签名需要对应新增这个参数。

### 3.4 新增类 `ScoresCalculator`

**位置**:`com.huanjing.geo.module.presale.generate.calc.ScoresCalculator`

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

**关于 `Scores` 类的 weights 字段**:

ScoreSet.java javadoc 说 "L2 `computed_snapshot.scores` 字段更多(含 weights),不复用本类"。但仓库里**没有单独的 `Scores` 类**,只有 `com.huanjing.geo.module.presale.dto.snapshot.computed.Scores`(待 Codex 确认是否存在以及字段)。

**本设计稿假设**:Computed 层的 `Scores` 类字段 = ScoreSet 的五维(overall / mention / ranking / sentiment / coverage)。如果 Codex 实施时发现 Scores 有额外 weights 字段,本 PR 按"填 null / 或用默认权重值 {0.30, 0.25, 0.15, 0.30}"处理。

**Open Question 新增 Q-14**:`com.huanjing.geo.module.presale.dto.snapshot.computed.Scores` 的实际字段清单?Codex 实施 Checkpoint 3 开工前确认。

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
        int phase1Total = 0, phase2Total = 0, phase3Total = 0;
        for (OptimizationFinding f : findings == null ? List.<OptimizationFinding>of() : findings) {
            if (f == null) continue;
            if (f.getPriority() == null) {
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
        
        Double upliftPercent = current == 0.0 ? 0.0 : ((t3 - current) / current * 100.0);
        
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

**潜在 Open Question Q-15**:规则引擎 SpEL 中 `#benchmarks` 来自 `#l1.benchmarksFrozen`(见 `RuleBuildInput.java` javadoc),本 PR 的 benchmark 数据通过 Resolver 从 JSON 读,会装进 `raw.benchmarksFrozen`,SpEL 读取理论上透明。Codex 实施 Checkpoint 3 开工前确认规则引擎的 SpEL 表达式行为。

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

### 3.8 L2 异常归类

```java
// orchestrator catch:
catch (BizException ex) {
    markFailed(versionId, "L2_COMPUTE_ERROR", truncateReason("L2 compute failed: " + ex.getMessage()));
}
// 其他 Exception 由外层 catch(Throwable) 处理
```

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
2. 某品牌的 `(industry, industryRole)` 不在 JSON + `(_ALL_, _ALL_)` 兜底也损坏 → Resolver.resolve 运行时抛 `BizException`

Orchestrator 层归类:`BizException` 消息含 `BENCHMARK_MISSING` → `markFailed(CONFIG_MISSING)`。

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
    
    enterStage(versionId, STAGE_COMPETITOR_EXTRACT, "extract competitors");
    List<String> competitors = extractTopCompetitorsFromBatch1(versionId, report.getBrandName());
    int cCount = competitors.size();
    int batch2TotalCalls = preflight.platformCount() * preflight.competitorPromptCount() * cCount * 2;
    updateAfterCompetitorExtract(versionId, cCount, batch2TotalCalls,
            preflight.batch1TotalCalls() + batch2TotalCalls);
    
    Set<String> allDegraded = new LinkedHashSet<>(batch1.degradedPlatforms());
    if (cCount > 0) {
        Batch2ExecutionResult batch2 = executeBatch2(
                versionId, report, operatorUserId, isManager, competitors,
                preflight.competitorPromptCount()
        );
        if (batch2.stopPipeline()) return;
        allDegraded.addAll(batch2.degradedPlatforms());
    } else {
        markCompetitorExtractEmpty(versionId);
        log.info("BATCH2 skipped due to 0 competitors, versionId={}", versionId);
    }
    
    // 【新增】L1 AGGREGATE
    enterStage(versionId, STAGE_L1_AGGREGATE, "assemble raw snapshot");
    String rawJson;
    try {
        rawJson = rawSnapshotAssembler.assemble(versionId, report, version, allDegraded, competitors);
    } catch (BizException ex) {
        String category = ex.getMessage() != null && ex.getMessage().contains("BENCHMARK_MISSING")
                ? FAILURE_CATEGORY_CONFIG_MISSING
                : FAILURE_CATEGORY_L1_SERIALIZATION_ERROR;
        markFailed(versionId, category, truncateReason("L1 aggregate failed: " + ex.getMessage()));
        return;
    }
    writeRawSnapshotJson(versionId, rawJson);
    
    // 【新增】L2 COMPUTE
    enterStage(versionId, STAGE_L2_COMPUTE, "enrich computed snapshot");
    String computedJson;
    try {
        computedJson = computedSnapshotEnricher.enrichAndValidate(
                versionId, rawJson, "{}", allowSyntheticFallbackReal
        );
    } catch (BizException ex) {
        markFailed(versionId, FAILURE_CATEGORY_L2_COMPUTE_ERROR,
                truncateReason("L2 compute failed: " + ex.getMessage()));
        return;
    }
    writeComputedSnapshotJson(versionId, computedJson);
    
    // 【新增】L3 INIT
    enterStage(versionId, STAGE_L3_INIT, "derive editable content");
    String editableJson;
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
    log.info("Presale real generate done, versionId={}, operatorUserId={}, isManager={}",
            versionId, operatorUserId, isManager);
}
```

### 6.2 `Batch1ExecutionResult` / `Batch2ExecutionResult` 签名扩展

```java
record Batch1ExecutionResult(boolean stopPipeline, Set<String> degradedPlatforms) {
    static Batch1ExecutionResult stop(Set<String> degraded) {
        return new Batch1ExecutionResult(true, degraded);
    }
    static Batch1ExecutionResult continuePipeline(Set<String> degraded) {
        return new Batch1ExecutionResult(false, degraded);
    }
}
// Batch2ExecutionResult 同样
```

**改动点**:`executeBatch1` / `executeBatch2` 方法内部已经维护 `degradedPlatforms` Set(见现有代码 `degradedPlatforms` / `displayDegradedPlatforms` 变量),在 return 之前把这个 Set 传入即可。

### 6.3 `markDone` 实装

```java
private void markDone(Long versionId) {
    PresaleReportVersion current = versionMapper.selectById(versionId);
    int totalCalls = current == null || current.getTotalLlmCalls() == null ? 0 : current.getTotalLlmCalls();
    
    PresaleReportVersion update = new PresaleReportVersion();
    update.setId(versionId);
    update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
    update.setGenerationStage(null);
    update.setCompletedLlmCalls(totalCalls);   // 占位计数收口
    // total_llm_calls 已经在 updateAfterCompetitorExtract 回填,不动
    update.setUpdatedAt(LocalDateTime.now());
    versionMapper.updateById(update);
    lastProgressUpdateAtByVersion.remove(versionId);
}
```

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
3. `realFullFlow_benchmarkMissing_marksConfigMissing`:mock Assembler 抛 `BizException("BENCHMARK_MISSING ...")` → `failure_category=CONFIG_MISSING`
4. `realFullFlow_l2Fails_marksL2Error`:mock Enricher 抛 `BizException` → `failure_category=L2_COMPUTE_ERROR`
5. `realFullFlow_l3Fails_marksL3Error`:mock L3InitService 抛 → `failure_category=L3_INIT_ERROR`
6. `realFullFlow_zeroCompetitors_reachesDone`:COMPETITOR_EXTRACT 返 0,仍能走完 L1/L2/L3 → DONE

### 7.3 现有测试修正清单

**Codex 实施 Checkpoint 5 开工前必须独立交付**:

1. `grep -rn "STAGE_D_CHECKPOINT" geo-server/src/test --include="*.java"` 所有命中点
2. 每条断言修正策略:
   - 如果测试原意是"走完 BATCH 后卡在 skeleton" → 改为"走完 BATCH 后进 L1/L2/L3"
   - 如果测试原意是"某异常导致 FAILED" → 保留,但 `failure_category` 从 `STAGE_D_CHECKPOINT` 改为对应的 L1/L2/L3 分类
3. `PlatformIntentBreakdownBuilderTest` 里 `C=3` 时的 `total_prompts` 断言 → 改为"只含 batch1 prompt 数"

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
- [ ] **Open Question 解决**:Scores 类实际字段(Q-14)、规则引擎 SpEL `#benchmarks` 行为(Q-15)
- [ ] **review 通过标志**:新三测试类全绿 + `PresaleComputedSnapshotEnricher` 已有测试不破坏(或同步修正)

### Checkpoint 4(1 天):Orchestrator wiring
- [ ] `runRealSkeletonFlow` 重命名 + 实装为 `runRealFullFlow`
- [ ] `markDone` / `writeRawSnapshotJson` / `writeComputedSnapshotJson` / `writeEditableContentJson` 方法新增
- [ ] 新增常量 `L1_SERIALIZATION_ERROR` / `L2_COMPUTE_ERROR` / `L3_INIT_ERROR`
- [ ] Orchestrator 构造函数注入 `PresaleRawSnapshotAssembler`
- [ ] **review 通过标志**:既有 Orchestrator 测试调整后全绿(`STAGE_D_CHECKPOINT` 断言修正)

### Checkpoint 5(1 天):端到端测试 + 现有测试修正
- [ ] `realFullFlow` 系列新测试(至少 6 条,按本稿 §7.2)
- [ ] Codex 先独立交付"Affected tests checklist"(列所有 `STAGE_D_CHECKPOINT` 命中点 + 修正策略)
- [ ] spec v4 §10.1 S14 验收条款同步改动
- [ ] **review 通过标志**:`mvn test` 全绿,affected tests checklist 所有条已修正

### Checkpoint 总控时序

```
D+0         D+0.5       D+3         D+4.5       D+5.5       D+6.5
 ├── C1 ────┼── C2 ─────┼── C3 ─────┼── C4 ─────┼── C5 ─────┤
 │          │           │           │           │           │
 │ Benchmark│ Assembler │ L2 三件套 │ Orchestra │ Tests 收尾 │
 │          │           │ + Rule    │ wiring    │           │
 │          │           │ + Builder │           │           │
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
| Q-4 | `total_tests` 语义 | Query 成功数(本稿 §2.2.4 决策) |
| Q-5 | Assembler 额外查 `ai_platform_config` 拿 platform_name 接受吗? | 接受,本稿实装 |
| Q-6 | `competitor.mention_rate` 分母 | batch1 Analyze 成功的 prompt_result 行数(本稿 §2.2.5 决策) |
| Q-7 | `scene_advantages_raw` 聚合规则 | 频次 top-5,同频次按字典序(本稿 §2.2.5 实装) |
| Q-8 | batch2 `WHERE competitor_name=?` 用 display 还是归一化? | Display 原写入值,与 F1 对齐(本稿 §2.2.5 确认) |
| Q-9 | `top_keywords` / `negative_evidence` 填 null 合法吗? | 合法,schema optional(本稿 §2.2.6) |
| Q-10 | benchmark 未命中 failure_category | CONFIG_MISSING(本稿 §6.1 确认) |
| Q-13 | markDone 传 preflight 值还是读 version? | 读 version.total_llm_calls(本稿 §6.3 实装) |

本轮新增:

| 编号 | 问题 | 答主 | 紧迫度 |
|---|---|---|---|
| **Q-14** | `com.huanjing.geo.module.presale.dto.snapshot.computed.Scores` 类的实际字段清单?(ScoreSet 五维 + 是否有额外 weights 字段) | Codex | Checkpoint 3 开工前 |
| **Q-15** | 规则引擎 SpEL `#benchmarks` 的行为:是读 `#l1.benchmarksFrozen` 吗?哪些规则依赖哪些 benchmark 字段? | Codex | Checkpoint 3 开工前 |

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

## §13 v1 设计稿签署

| 角色 | 职责 | 会签 |
|---|---|---|
| **决策者** | 用户 | ☐ 审完 D25-D30 决策并会签本稿 |
| **契约守护** | Claude | ✅ 本稿起草人,每个 Checkpoint 负责 review |
| **实施者** | Codex | ☐ 按 5 Checkpoint 节奏实施,Q-14/Q-15 开工前独立确认 |

用户会签后,Codex 可开工 Checkpoint 1。

---

**v1 定稿 END**
