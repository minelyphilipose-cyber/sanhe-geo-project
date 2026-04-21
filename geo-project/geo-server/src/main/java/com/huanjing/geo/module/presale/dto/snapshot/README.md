# Presale Snapshot DTO 骨架 · v1.2 重做版

基于 `report_data_schema_v1_2.json` 原文 + `V62__create_presale_report_module_v4.sql` 真实列重做的 Java DTO 骨架。

**与上一版(已作废)的关键区别**:上一版基于对话决策点脑补字段结构,Codex 审阅指出 7 处结构性冲突。本版严格以 schema + V62 为权威,每个类顶部注释都标注对应的 schema 路径,**没有自创字段**。

## 包路径

```
com.huanjing.geo.module.presale.dto.snapshot
```

对齐仓库 `com.huanjing.geo.module.*` 命名风格(已修正上一版的 `com.huanjing.geo.presale.*`)。

## 产出清单(22 个类)

| 包 | 类 | 对应 schema 路径 |
|---|---|---|
| 顶层 | `ReportSnapshotDTO` | schema 根 |
| common | `MatchLevel` | `$.raw_snapshot.benchmarks_frozen.match_level` |
| common | `ScoreSet` | `$defs/scoreSet` |
| common | `SceneCoverageGroup` | `$defs/sceneCoverageGroup` |
| common | `SceneQueryItem` | `$defs/sceneQueryItem` |
| common | `SceneQueryMissing` | `$defs/sceneQueryMissing` |
| raw | `RawSnapshotDTO` | `$defs/rawSnapshot` |
| raw | `RawMeta` | `$.raw_snapshot.meta` |
| raw | `ClientInfo` | `$.raw_snapshot.client_info` |
| raw | `TestSummary` | `$.raw_snapshot.test_summary` |
| raw | `PlatformBreakdown` (+ SentimentDistribution) | `$.raw_snapshot.platform_breakdown[]` |
| raw | `Competitor` | `$.raw_snapshot.competitors[]` |
| raw | `SentimentDetail` (+ SentimentKeyword / NegativeEvidence / Sentiment enum) | `$.raw_snapshot.sentiment_detail` |
| raw | `BenchmarksFrozen` (+ ConfidenceLevel / Source / IndustryRanking) | `$.raw_snapshot.benchmarks_frozen` |
| computed | `ComputedSnapshotDTO` (+ SceneCoverage) | `$defs/computedSnapshot` |
| computed | `Scores` (+ Weights) | `$.computed_snapshot.scores` |
| computed | `IntentBreakdown` | `$.computed_snapshot.intent_breakdown[]` |
| computed | `OptimizationFinding` (+ Priority enum) | `$.computed_snapshot.optimization_findings[]` |
| computed | `RoiSimulation` (+ RoiPhase) | `$.computed_snapshot.roi_simulation` |
| editable | `EditableContentDTO` | `$defs/editableContent` |
| editable | `ExecutiveSummary` | `$.editable_content.executive_summary` |
| editable | `KeyTakeaway` | `$.editable_content.key_takeaways[]` |
| editable | `FindingContent` | `$.editable_content.optimization_findings_content[]` |
| editable | `PhaseDescription` | `$.editable_content.phase_descriptions[]` |
| editable | `CompetitorSceneDescription` | `$.editable_content.competitor_scene_descriptions[]` |
| merged | `MergedViewDTO` (+ MergedFinding / MergedPhase / MergedCompetitor) | 派生,无对应 schema |
| merged | `MergedViewMeta` | 派生自 `presale_report_version` 行 |

## 设计决策(用户确认)

| # | 决策 | 选择 |
|---|---|---|
| 1 | `evidence_data` 类型 | **A. `Map<String, Object>`** —— 灵活,P1 先宽后严 |
| 2 | 数值类型 | **B. 全部 `Double`** —— mock 里都是人工友好数字,无金融精度需求 |
| 3 | 枚举策略 | **C. 混合** —— 稳定英文值用 Java enum,中文字面值保留 String |
| 4 | 是否产出 `ReportSnapshotDTO` | **A. 产出** —— ops 调试 + 派生服务 + schema 校验用 |

### 决策 3C 细则

**用 Java enum**(稳定英文枚举):
- `MatchLevel`: EXACT / FALLBACK_INDUSTRY
- `BenchmarksFrozen.ConfidenceLevel`: HIGH / MEDIUM / LOW
- `BenchmarksFrozen.Source`: MANUAL / AUTO_P50 / HYBRID
- `SentimentDetail.Sentiment`: POSITIVE / NEUTRAL / NEGATIVE
- `OptimizationFinding.Priority`: HIGH / MEDIUM / LOW

**保留 String**(中文字面值或仍在演化):
- `IntentBreakdown.category`: 推荐型 / 对比型 / 问题型 / 认知型 / 场景型
- `IntentBreakdown.businessValue`: 高 / 中 / 低
- `OptimizationFinding.category`: 基础设施 / 内容建设 / 关系建设 / 平台扩展
- `SceneQueryItem.category`: 同 IntentBreakdown.category
- `MergedViewMeta.generationStatus`: INIT / QUEUED / ... / DONE / FAILED(12+ 个状态,仍在演化)

**保留基础类型**(不做 enum):
- `TestSummary.rounds`: Integer(枚举 [1, 2])
- `RoiPhase.phaseNo`: Integer(枚举 [1, 2, 3])
- `Competitor.rank`: Integer(1-3)
- `CompetitorSceneDescription.competitorRank`: Integer(1-3)

## 三层边界(schema v1.2 原文)

| 层 | 存储列 | 可变性 | 包含 |
|---|---|---|---|
| L1 raw | `raw_snapshot_json` (MySQL JSON) | 只读 | meta / client_info / test_summary / platform_breakdown / competitors / sentiment_detail / **benchmarks_frozen (含 match_level)** |
| L2 computed | `computed_snapshot_json` (MySQL JSON) | 只读 | scores / intent_breakdown / scene_coverage / optimization_findings / roi_simulation |
| L3 editable | `editable_content_json` (MySQL JSON) | **就地 UPDATE**,冻结后 409 | report_title / report_subtitle / executive_summary / key_takeaways / optimization_findings_content / phase_descriptions / competitor_scene_descriptions / roi_disclaimer |

### ⚠️ match_level 位置说明

本 DTO 按 schema v1.2 原文,把 `match_level` 放在 **L1 `benchmarks_frozen` 内**,不是 L2。

理由:`benchmarks_frozen` 整体是"生成时刻从 presale_benchmark 读取的冻结副本",`match_level` 是该副本的一部分元数据(表明"此副本是 EXACT 命中还是回退来的")。它不是一个独立的计算决策,而是"冻结这份副本"这个动作本身的附带属性。

这与我此前和 Codex 讨论时曾坚持的"match_level 必须落 L2"结论不同 —— 那次讨论没有看到 schema 原文。本版以 schema 为准。

### 关键约束

- **L3 的 is_hidden 只作用于 optimization_findings_content[] 条目级**,L3 没有通用的模块隐藏/排序能力。
- **scene_advantages 的 L3→L1 回退**是 L3 唯一一处向 L1(而非默认模板)回退的字段,对应前端 `mergeSnapshot` 必须实现这条特殊规则。
- **key_takeaways 无 L1/L2 回退**:规则引擎在生成时写入默认文案到 L3,后续运营直接编辑。

## MergedViewDTO 合并规则

方案 A 扁平化,前端不感知三层。权威实现在后端 `MergeService`,前端 `mergeSnapshot` 仅 P1 mock 期过渡。

规则一览(字段级):

| 目标字段 | 来源 + 合并逻辑 |
|---|---|
| `brand_name` / `industry` / `industry_role` / `region` / `user_demand` | L1.client_info 直出 |
| `test_summary` / `platform_breakdown` / `sentiment_detail` / `benchmarks_frozen` | L1 直出 |
| `scores` / `intent_breakdown` / `scene_coverage` / `roi_simulation` | L2 直出 |
| `report_title` / `report_subtitle` / `roi_disclaimer` | L3 非 null → L3;null → 默认模板(含变量渲染) |
| `executive_summary` | L3 非 null → L3;null → 默认(生成时已写入,极少 null) |
| `key_takeaways` | L3 直出 |
| `merged_findings[]` | 按 finding_id 连接 L2.optimization_findings × L3.optimization_findings_content;<br>• L3.is_hidden=true → 跳过<br>• L3.sort_order 有值 → 按其排序;否则 L2 原序<br>• L3.title/description/evidence_text 非 null → 用 L3;null → 默认模板(由 L2.rule_code + L2.evidence_data 渲染) |
| `merged_phases[]` | 按 phase_no 连接 L2.roi_simulation.phases × L3.phase_descriptions;严格 3 条 |
| `merged_competitors[]` | 按 rank 连接 L1.competitors × L3.competitor_scene_descriptions;<br>• L3.scene_advantages_polished 非 null → 用 L3,`scene_is_polished=true`<br>• null → 回退 L1.scene_advantages_raw,`scene_is_polished=false` |
| `meta.match_level` | 从 L1.benchmarks_frozen.match_level 提升到 meta(便于前端一处读取) |
| `meta.is_degraded` / `degraded_platforms` | 从 presale_report_version 行读取(与 L1.test_summary 冗余,以版本行为准) |

## 派生新版本 · 三分法字段复制规则(对齐 V62 v4 真实列)

派生实现侧**必须以显式的三分法常量为准**,不依赖"新建默认值"。三分法明确区分:

- **快照层(SNAPSHOT)** — 三层 JSON 列,业务数据真相源
- **事实冻结层(FACT_FROZEN)** — 与 L1 同源同批次生成的事实性列,复制
- **会话状态层(SESSION_STATE)** — 冻结 / 编辑 / 导出 / 运行时统计等,归零

```java
/** 快照层:三层 JSON,复制 */
public static final Set<String> DERIVE_SNAPSHOT_FIELDS = Set.of(
    "raw_snapshot_json",
    "computed_snapshot_json",
    "editable_content_json"
);

/**
 * 事实冻结层:与 L1.test_summary 同源同批次的事实性列,复制。
 * 派生不重新跑 LLM,"是否降级"这个事实不变;复制保证 meta 与 L1 一致,不产生口径分叉。
 */
public static final Set<String> DERIVE_FACT_FROZEN_FIELDS = Set.of(
    "is_degraded",
    "degraded_platforms"
);

/** 会话状态层:归零,不复制 */
public static final Set<String> DERIVE_RESET_FIELDS = Set.of(
    "generation_status",      // → "DONE"
    "failure_category",       // → null
    "failure_detail",         // → null
    "frozen_at",              // → null
    "frozen_by",              // → null
    "frozen_reason",          // → null
    "content_updated_at",     // → null
    "content_updated_by",     // → null
    "export_attempt_count",   // → 0
    "export_success_count",   // → 0
    "export_success_at",      // → null
    "last_export_error",      // → null
    "total_llm_calls",        // → null
    "total_retry_count",      // → null
    "rate_limit_hit_count",   // → null
    "duration_seconds"        // → null
);
```

### 三分法的语义边界

| 判断标准 | 快照层 | 事实冻结层 | 会话状态层 |
|---|---|---|---|
| 数据来源 | 本次生成的 L1/L2/L3 产物 | 本次生成的事实统计(行级冗余) | 用户操作 / 运行时指标 |
| 派生新版本是否继承 | ✅ 复制 | ✅ 复制 | ❌ 归零 |
| 是否随 LLM 重跑改变 | 是 | 是(但派生不重跑) | 否,与生成无关 |
| 典型字段 | 三个 JSON 列 | `is_degraded` / `degraded_platforms` | frozen_* / export_* / content_updated_* / 执行统计 |

**判据:"如果把 row 列删掉,能从 JSON 列还原吗?"** 能 → 事实冻结层(冗余但同源);不能 → 会话状态层(独立来源)。

`is_degraded` / `degraded_platforms` 的内容在 `raw_snapshot_json.test_summary` 里也有,两者同源写入,删掉行级列可从 JSON 还原 → 事实冻结层。
`frozen_at` / `export_count` 等不与任何 JSON 列同源,删掉就丢失 → 会话状态层。

### 结构性字段 · 显式取值(不归任一分类)

| 列 | 派生时的值 |
|---|---|
| `id` | 数据库自增,新主键 |
| `report_id` | 复制原版本值(派生不跨报表) |
| `version_no` | `MAX(version_no) + 1` |
| `schema_version` | 读取原版本值并显式传入(未来 schema 升级时是明确的切换点) |
| `created_at` / `updated_at` | MySQL `DEFAULT CURRENT_TIMESTAMP` 自动填充 |

### 降级口径单一真相源(与 MergedView.meta 对齐)

派生后 `is_degraded` / `degraded_platforms` **复制自原版本** → 行级列与 `raw_snapshot_json.test_summary` 保持一致 → `MergedViewMeta` 读行级列(原合并规则不变)与 L1 读出的结果一致。
**同一个版本只有一份降级真相,前端警示条、PDF 展示、审计查询口径统一。**

### 子表 · 不复制

`presale_ai_test_result` / `presale_optimization_finding` / `presale_generation_log` 均以 `version_id` 为外键,派生时**不复制**,新 `version_id` 下为空。
跨版本查询子表数据时,应追溯到 `raw_snapshot_json` / `computed_snapshot_json` 里的冻结副本(与子表行同源写入)。

### 落地示例(Service 层骨架)

```java
public Long derive(Long sourceVersionId, Long operatorId) {
    PresaleReportVersion src = mapper.selectById(sourceVersionId);
    PresaleReportVersion next = new PresaleReportVersion();

    // 1. 快照层 · 复制三层 JSON
    next.setRawSnapshotJson(src.getRawSnapshotJson());
    next.setComputedSnapshotJson(src.getComputedSnapshotJson());
    next.setEditableContentJson(src.getEditableContentJson());

    // 2. 事实冻结层 · 复制(与 L1.test_summary 同源,派生不重跑 LLM 事实不变)
    next.setIsDegraded(src.getIsDegraded());
    next.setDegradedPlatforms(src.getDegradedPlatforms());

    // 3. 结构性字段 · 显式取值
    next.setReportId(src.getReportId());
    next.setVersionNo(mapper.nextVersionNo(src.getReportId()));
    next.setSchemaVersion(src.getSchemaVersion());

    // 4. 会话状态层 · 归零
    next.setGenerationStatus("DONE");
    next.setFailureCategory(null);
    next.setFailureDetail(null);
    next.setFrozenAt(null);
    next.setFrozenBy(null);
    next.setFrozenReason(null);
    next.setContentUpdatedAt(null);
    next.setContentUpdatedBy(null);
    next.setExportAttemptCount(0);
    next.setExportSuccessCount(0);
    next.setExportSuccessAt(null);
    next.setLastExportError(null);
    next.setTotalLlmCalls(null);
    next.setTotalRetryCount(null);
    next.setRateLimitHitCount(null);
    next.setDurationSeconds(null);
    // created_at / updated_at 由 MySQL 默认值填充

    mapper.insert(next);
    return next.getId();
}
```

## 时间字段规范化(字段级注解 · 对齐 schema v1.2 `date-time` / RFC3339)

DTO 层 Java 类型统一为 `java.time.LocalDateTime`,避免从 MySQL `DATETIME`(无偏移)读出时假造偏移。
JSON 序列化格式对齐 schema v1.2 的 `format: date-time`(RFC3339)和 mock 样本格式(如 `2026-04-18T14:05:00+08:00`)。

### 实现方式:字段级注解(非全局配置)

**不**使用 Spring `@Configuration` + `Jackson2ObjectMapperBuilderCustomizer`,因为全局接管 `LocalDateTime` 会改变仓库内所有老接口的输出(从默认 ISO 本地时间变成 RFC3339 带偏移),是隐式 breaking change。

序列化/反序列化逻辑由工具类 `com.huanjing.geo.module.presale.json.PresaleDateTimeJson` 提供(**非 Spring 组件**),通过字段级注解显式挂载,作用域严格限定在 presale DTO 内:

```java
@JsonProperty("generated_at")
@JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
@JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
private LocalDateTime generatedAt;
```

**行为:**
- 序列化:`LocalDateTime` → 按 `BUSINESS_OFFSET = +08:00` → RFC3339(`2026-04-18T14:05:00+08:00`)
- 反序列化(宽松)同时接受:
  - RFC3339 带偏移 `2026-04-18T14:05:00+08:00`(标准)
  - RFC3339 带 Z `2026-04-18T06:05:00Z`(转换为 +08:00)
  - ISO 无偏移 `2026-04-18T14:05:00`(兜底,视为 +08:00)
  - MySQL 空格 `2026-04-18 14:05:00`(JDBC 回读兜底,视为 +08:00)

### 受此规则约束的字段(5 处)

| DTO 字段 | 位置 |
|---|---|
| `RawMeta.generatedAt` | L1 |
| `SentimentDetail.NegativeEvidence.testedAt` | L1 嵌套 |
| `MergedViewMeta.frozenAt` | Meta |
| `MergedViewMeta.contentUpdatedAt` | Meta |
| `MergedViewMeta.exportSuccessAt` | Meta |

**新增 DTO 时间字段的约束:** 凡 presale 新加 `LocalDateTime` 字段,必须同时挂 `@JsonSerialize` / `@JsonDeserialize` 注解。P1·B 之后考虑补 ArchUnit 单测扫描,防止遗漏。

### 业务约定

- 统一时区:`Asia/Shanghai`(+08:00),常量位于 `PresaleDateTimeJson.BUSINESS_OFFSET`
- JDBC URL 建议显式 `serverTimezone=Asia/Shanghai`,避免 Driver 二次转换
- 前端展示按北京时间,不做二次偏移计算
- 未来多区域部署再评估是否切到 `OffsetDateTime` 或 `Instant`

### ⚠️ 跨序列化上下文注意事项

presale DTO 可能在多条路径上被序列化,**不同路径用不同的 Jackson 上下文,本骨架的字段级注解只覆盖其中一条**。按路径逐项说明:

| 序列化路径 | Jackson 上下文 | 本骨架是否覆盖 | 风险 / 处理 |
|---|---|---|---|
| Spring MVC 返回 presale DTO(前端 API) | Spring Boot 全局 MVC ObjectMapper | ✅ 覆盖 | 字段级 `@JsonSerialize` 生效,输出 RFC3339 带 +08:00 |
| 接收前端 JSON 请求体 → 反序列化到 presale DTO | 同上 | ✅ 覆盖 | 字段级 `@JsonDeserialize` 宽松接受多种格式 |
| MyBatis 读写 `presale_report_version.*_snapshot_json` | **MyBatis 自己的 TypeHandler**(非 Jackson) | ⚠️ 不覆盖 | JSON 列内的 LocalDateTime 字段由 TypeHandler 实现决定;若 TypeHandler 用 Jackson,需同样挂注解(本骨架 DTO 已挂,直接用 `ObjectMapper.readValue(json, RawSnapshotDTO.class)` 的 TypeHandler 可以正常工作);若用手写字符串拼接则各自处理 |
| Redis 缓存 presale DTO | **RedisConfig 自建独立 ObjectMapper**(Codex 已发现) | ❌ 不覆盖 | 本模块 DTO **暂不进 Redis**;若未来进 Redis,要么让 `RedisConfig` 的 ObjectMapper 注册同样的序列化器,要么 presale 这边自建一个 Redis 专用 ObjectMapper 继承 MVC 侧的 Module |
| PDF 导出(Playwright 抓前端页面) | 前端 JS,不走 Java 序列化 | — | 前端拿到的已是 RFC3339,JS 按字符串透传即可 |
| 日志打印(`log.info("{}", dto)`) | Lombok `@ToString` 调用各字段 `toString()` | ❌ 不覆盖 | 日志里 `LocalDateTime.toString()` 输出 `2026-04-18T14:05`(ISO 无秒),仅调试用,不是契约输出,可接受 |
| JSON Schema 校验(生成时/派生时) | 校验器自身 | — | 只要序列化输出 RFC3339 带偏移,校验即可通过 |

**关键结论:**
1. Web API 链路 ✅ 完整覆盖,契约对齐
2. MyBatis 链路:建议 TypeHandler 就用 `ObjectMapper.readValue(str, DTO.class)` 基于标准 Jackson,字段级注解自动生效;不推荐手写字符串解析
3. Redis 链路是仓库级已知隐患,**本 DTO 骨架目前不依赖该路径**,未来若依赖需在 PR 里专门处理


## 与 V62 v4 字段对齐(关键列)

| DTO 字段 | V62 v4 列 | 类型 |
|---|---|---|
| `MergedViewMeta.versionId` | `presale_report_version.id` | BIGINT → Long |
| `MergedViewMeta.reportId` | `presale_report_version.report_id` | BIGINT → Long |
| `MergedViewMeta.versionNo` | `presale_report_version.version_no` | INT → Integer |
| `MergedViewMeta.frozenBy` | `presale_report_version.frozen_by` | BIGINT → Long |
| `MergedViewMeta.contentUpdatedBy` | `presale_report_version.content_updated_by` | BIGINT → Long |
| `MergedViewMeta.isDegraded` | `presale_report_version.is_degraded` | TINYINT → Boolean |
| `MergedViewMeta.generationStatus` | `presale_report_version.generation_status` | VARCHAR(30) → String |

**字典对齐**:
- `ClientInfo.industry` ← `sys_dict_item(dict_type='presale_industry')`
- `ClientInfo.industryRole` ← `sys_dict_item(dict_type='presale_industry_role')`

**JSON 列注意**:MySQL 是 `JSON` 类型(不是 PostgreSQL 的 `JSONB`),无二进制索引优化,按需建虚拟列 + 索引。

## 上一版的 7 处冲突 · 本版修正对照

| Codex 指出 | 本版修正 |
|---|---|
| L1 顶层字段错 | ✅ 本版严格 7 字段:meta / client_info / test_summary / platform_breakdown / competitors / sentiment_detail / benchmarks_frozen |
| CustomerInput 缺 `region` | ✅ `ClientInfo.region` 已补(必填) |
| L2 顶层字段错 | ✅ 本版严格 5 字段:scores / intent_breakdown / scene_coverage / optimization_findings / roi_simulation |
| L3 自创"模块控制层" | ✅ 本版严格 8 个文案块,无 hiddenModules/moduleOrder/extensions |
| `BenchmarkRef` 粒度错 | ✅ 本版 `BenchmarksFrozen` 是单个聚合对象,含 industry_avg/top1/top10_score/confidence_level/source/sample_size/industry_ranking |
| `versionId` 用 String | ✅ 本版改 Long(对齐 BIGINT) |
| 包名 / JSONB / 字典 | ✅ 本版 `com.huanjing.geo.module.presale`,文档改 JSON,字典用 `presale_industry[_role]` |

## Codex 第二轮审阅修复(rebuild 版 → rebuild-r2)

| Codex 指出 | 本轮修正 |
|---|---|
| P1 派生白名单渗入非快照状态(schema_version / is_degraded / degraded_platforms) | ✅ 白名单缩回 3 个 JSON 列;上述字段移到显式处理章节,`is_degraded` / `degraded_platforms` 明确归零(不继承降级),`schema_version` 明确"读取原值并显式传入",附 Service 层骨架示例 |
| P2 `OffsetDateTime` 与 MySQL DATETIME 口径不匹配 | ✅ 全量改为 `LocalDateTime`(3 个文件 5 个字段),README 新增"时间字段规范化"章节,统一 Jackson 序列化格式 `yyyy-MM-dd HH:mm:ss`,服务器时区 `Asia/Shanghai` |
| P3 Java 注释残留 "JSONB" | ✅ `OptimizationFinding` / `MergedViewDTO` 两处 Java 注释已改为 "JSON 列"(README 内两处是解释"不是 JSONB"和历史对照,保留合理) |

## Codex 第三轮审阅修复(rebuild-r2 → rebuild-r3)

| Codex 指出 | 本轮修正 |
|---|---|
| P1 `LocalDateTime` 序列化格式 `yyyy-MM-dd HH:mm:ss` 违反 schema v1.2 `date-time` / RFC3339 契约,与 mock 样本格式不一致 | ✅ Java 类型保留 `LocalDateTime`(MySQL DATETIME 语义正确),新增 `PresaleJacksonConfig` 配置类,序列化为 RFC3339 带 +08:00 偏移(`2026-04-18T14:05:00+08:00`),与 schema 和 mock 完全一致;反序列化宽松接受 RFC3339 带偏移 / 带 Z / ISO 无偏移 / MySQL 空格格式四种输入 |
| P1 派生版本降级归零 vs MergedView.meta 从行读取的口径冲突(meta 说没降级,L1 说降级了) | ✅ 派生规则重新定义为**三分法**(快照层 / 事实冻结层 / 会话状态层),`is_degraded` 和 `degraded_platforms` 识别为"事实冻结层"(与 `raw_snapshot_json.test_summary` 同源),派生时**复制**而非归零 → 行级列与 L1 一致 → MergedView.meta 读行级的现有规则无需改动,单一真相源恢复;README 新增"三分法语义边界"章节解释判据 |

## Codex 第四轮审阅修复(rebuild-r3 → rebuild-r4)

| Codex 指出 | 本轮修正 |
|---|---|
| `PresaleJacksonConfig` 作用域过大,全局接管 `LocalDateTime` 会隐式改变仓库所有老接口输出(从默认 ISO 本地时间变成 RFC3339 带偏移),而仓库当前 `spring.jackson.date-format` 对 `LocalDateTime` 实际不生效,无统一治理先例 | ✅ 废弃全局 `@Configuration` + `Jackson2ObjectMapperBuilderCustomizer` 方案。工具类迁入 `com.huanjing.geo.module.presale.json.PresaleDateTimeJson`,**不再是 Spring 组件**,只提供两个公开 Serializer/Deserializer 内部类。presale 5 个 `LocalDateTime` 字段显式挂 `@JsonSerialize` / `@JsonDeserialize` 注解,作用域严格限定在 presale DTO,不影响其他模块。README 新增"跨序列化上下文注意事项"章节,澄清 Web / MyBatis / Redis / PDF / 日志 5 条路径的覆盖边界(Redis 独立 ObjectMapper 问题属仓库级隐患,本骨架暂不依赖该路径,不阻塞) |


## 依赖

- Jackson(`@JsonProperty` snake_case 映射,`@JsonInclude(NON_NULL)`)
- Lombok(`@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`)
- JDK 17

## 后续 P1 产出

1. **TypeScript 类型文件**:从本 Java DTO 镜像生成(决策 P1·C 里定的方案,手写快启动)
2. **`mergeSnapshot` 工具函数**:前端离线兜底版,与后端 MergeService 共享 fixture 对账
3. **包内 ResourceBundle / 默认模板**:报告标题 / 副标题 / 默认文案的变量渲染模板
4. **派生服务 + `DERIVE_COPY_FIELDS` 常量**:P1 后端骨架里落地
5. **JSON Schema 校验接入**:Spring Boot 启动时加载 schema,生成时调用校验

## 自我纠正记录

本次交付是 DTO 骨架的**第二版**,第一版因结构性偏离 schema v1.2 被作废。核心教训:

> **没有 schema 原文,我写的一切都是猜测。**

后续所有涉及字段级约定的代码,都必须先看 schema / 数据库列 / 现有代码,再动笔。
