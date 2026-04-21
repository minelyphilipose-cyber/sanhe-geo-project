# P1·B 交付:TypeScript 类型 + mergeSnapshot + 对账 fixture

P1·B 阶段的前端契约交付,对齐 P1·A rebuild-r4 Java DTO 骨架和 schema v1.2。

## 目录结构

```
src/main/ts/
├── types/presale/
│   ├── common.ts        MatchLevel / ScoreSet / SceneCoverageGroup / SceneQueryItem / SceneQueryMissing
│   ├── raw.ts           L1 RawSnapshotDTO + 嵌套
│   ├── computed.ts      L2 ComputedSnapshotDTO + 嵌套
│   ├── editable.ts      L3 EditableContentDTO + 嵌套
│   ├── merged.ts        MergedViewDTO + MergedViewMeta + 合并产物(MergedFinding/MergedPhase/MergedCompetitor)
│   └── index.ts         ReportSnapshotDTO + barrel re-export
├── utils/presale/
│   └── merge-snapshot.ts  mergeSnapshot 函数 + 默认模板(带变量插值)
└── fixtures/presale/
    ├── 01-normal.json             完整正常数据
    ├── 02-l3-fallback.json        L3 null → 默认模板回退
    ├── 03-competitor-raw.json     竞品 L1 回退
    ├── 04-findings-filter.json    finding is_hidden / sort_order
    └── README.md                  fixture 对账指南
```

## 类型命名约定

- **PascalCase + DTO 后缀**,与 Java 类名一一对应(如 `MergedViewDTO` / `RawSnapshotDTO`)
- JSON 字段保持 **snake_case**(与 schema + Jackson 配置对齐)
- 跨栈 grep:同一个名字能在 Java / TS / schema / mock 里都搜到
- 稳定英文枚举用字面量联合类型(`MatchLevel = 'EXACT' | 'FALLBACK_INDUSTRY'`);中文字面值保留 `string`(对齐 Java 侧 3C 决策)

## mergeSnapshot 边界声明

**本函数是后端 MergeService(`/api/presale/versions/{versionNo}/merged-view`)的非权威镜像。**

合法使用场景:
1. P1 mock 期前端本地跑通页面,后端 MergeService 尚未上线时的离线兜底
2. 前后端对账测试(共享本目录下的 fixture)

**不得**在生产运行时链路上用本函数替代后端接口。联调后应全量切到后端接口,本函数标记 `@deprecated` 保留仅用于 fixture 对账。

真相源:后端 MergeService。若本函数和后端规则有差异,以后端为准。

## 合并规则覆盖清单

`mergeSnapshot` 实现了后端 MergeService 的所有规则(与 rebuild-r4 README"MergedViewDTO 合并规则"章节一致):

| 目标字段 | 合并规则 |
|---|---|
| `brand_name` / `industry` / `industry_role` / `region` / `user_demand` | L1.client_info 直出 |
| `test_summary` / `platform_breakdown` / `sentiment_detail` / `benchmarks_frozen` | L1 直出 |
| `scores` / `intent_breakdown` / `scene_coverage` / `roi_simulation` | L2 直出 |
| `report_title` | L3 非 null → L3;null → `"{brand_name} GEO 可见度诊断报告"` |
| `report_subtitle` | L3 非 null → L3;null → `"基于 {total_platforms} 个 AI 平台 × {total_prompts} 条查询的深度分析"` |
| `executive_summary` | L3 非 null → L3;null → 兜底占位(mock 期 L3 通常已写入) |
| `key_takeaways` | L3 直出 |
| `roi_disclaimer` | L3 非 null → L3;null → schema 默认模板 |
| `merged_findings` | 按 `finding_id` 连接 L2 × L3;`is_hidden=true` 跳过;`sort_order` 有值按其升序,无值按 L2 原序 idx+1;title/description/evidence_text 默认由 rule_code + evidence_data 渲染 |
| `merged_phases` | 按 `phase_no` 连接 L2.phases × L3.phase_descriptions;title 默认 `"基础优化阶段"` / `"内容建设阶段"` / `"持续优化阶段"` |
| `merged_competitors` | 按 `rank` 连接;`scene_advantages_polished` 非 null → 用 L3,`scene_is_polished=true`;null → 回退 L1 `scene_advantages_raw`,`scene_is_polished=false` |
| `meta.match_level` | 从 L1.benchmarks_frozen.match_level 提升到 meta |
| `meta.is_degraded` / `degraded_platforms` | 从 `presale_report_version` 行读取(派生后与 L1 一致,三分法"事实冻结层"保证) |

## 时间字段格式

所有时间字段(`generated_at` / `tested_at` / `frozen_at` / `content_updated_at` / `export_success_at`)在 TS 侧都是 `string` 类型,格式对齐后端:

- 序列化来自后端:`"2026-04-18T14:05:00+08:00"`(RFC3339 带 +08:00)
- 前端需展示时推荐用 `dayjs(str)` 直接解析;ISO 字符串已带时区信息,无需二次偏移

## 与 Java 侧 rebuild-r4 的对齐检查

| Java 类 | TS 类型 | 字段数 | 对齐 |
|---|---|---|---|
| `ReportSnapshotDTO` | `ReportSnapshotDTO` | 4 | ✅ |
| `MatchLevel` | `MatchLevel` | - | ✅ |
| `ScoreSet` | `ScoreSet` | 5 | ✅ |
| `SceneCoverageGroup` / `SceneQueryItem` / `SceneQueryMissing` | 同名 | - | ✅ |
| `RawSnapshotDTO` | `RawSnapshotDTO` | 7 | ✅ |
| `RawMeta` | `RawMeta` | 5 | ✅ |
| `ClientInfo`(含 region,user_demand 可选) | `ClientInfo` | 5 | ✅ |
| `TestSummary` | `TestSummary` | 9 | ✅ |
| `PlatformBreakdown`(含 SentimentDistribution) | 同名 | 9 | ✅ |
| `Competitor` | `Competitor` | 6 | ✅ |
| `SentimentDetail`(含 SentimentKeyword / NegativeEvidence) | 同名 | - | ✅ |
| `BenchmarksFrozen`(单聚合对象 + match_level) | `BenchmarksFrozen` | 10 | ✅ |
| `ComputedSnapshotDTO` | `ComputedSnapshotDTO` | 5 | ✅ |
| `Scores`(含 Weights) | `Scores` | 6 | ✅ |
| `IntentBreakdown` | `IntentBreakdown` | 6 | ✅ |
| `OptimizationFinding`(含 Priority + evidence_data Map) | `OptimizationFinding` | 5 | ✅ |
| `RoiSimulation`(含 RoiPhase × 3) | `RoiSimulation` | 5 | ✅ |
| `EditableContentDTO` | `EditableContentDTO` | 8 | ✅ |
| `ExecutiveSummary` / `KeyTakeaway` / `FindingContent` / `PhaseDescription` / `CompetitorSceneDescription` | 同名 | - | ✅ |
| `MergedViewMeta` | `MergedViewMeta` | 16 | ✅ |
| `MergedViewDTO`(含 MergedFinding / MergedPhase / MergedCompetitor) | 同名 | - | ✅ |

## 后续 P1 产出

- **P1·B 后**:`mergeSnapshot` 上 vitest 单测(用本目录 fixture),CI 里跑对账
- **P1·C Prompt 库导入 SQL**:独立脚本,不在 TS 工作流内
- **P1·D 优化规则库 v1.0**:8-10 条规则的 YAML/JSON,影响 `OptimizationFinding.evidence_data` 字段集和 `rule_code` 模板回退
- **后端 MergeService**:以本目录 fixture 为单测基线,实现必须通过全部 7 组 + expected 深度相等

## 与 schema v1.2 的关系

- 类型字段名、嵌套结构、枚举值、必填性全部对齐 `report_data_schema_v1_2.json`
- 时间格式对齐 `format: date-time`(RFC3339)
- 默认模板文案对齐 schema 描述字段中的建议默认值
- 若 schema 未来升级到 v1.3,本 TS 类型按 schema diff 同步修订;`mergeSnapshot` 的合并规则按后端 MergeService 同步修订

## Codex 第一轮审阅修复(p1b → p1b-r1)

| Codex 指出 | 本轮修正 |
|---|---|
| P1 `merged_phases` 未强制 `phase_no` 1/2/3 顺序,依赖输入数组天然有序 | ✅ `mergePhases` 改为**按固定序 `[1, 2, 3]` 驱动输出**,L2 缺失某 phase_no 时合成占位 phase 保证严格 3 条 |
| P1 `merged_competitors` 未强制 `rank` 1/2/3 顺序,依赖输入数组天然有序 | ✅ `mergeCompetitors` 改为**按 `[1, 2, 3]` 驱动输出**,L1 缺失某 rank(客户填报不足 3 竞品)正常跳过 |
| P2 `degraded_platforms` TS 类型比 DB 严格(DB nullable,TS 要求必填数组) | ✅ `VersionRowMeta.degraded_platforms` 改为 `string[] \| null`(反映 DB);`buildMeta` 归一 `null → []`;`MergedViewMeta.degraded_platforms` 保持 `string[]`,前端消费永远是数组 |

**新增 3 组防退化 fixture**:
- `05-unordered-phases-competitors.json`:乱序输入 → 有序输出(P1 防退化)
- `06-degraded-null.json`:DB NULL → meta [] 归一(P2 防退化)
- `07-benchmark-fallback.json`:基准值 FALLBACK_INDUSTRY 场景(额外补充,非 Codex 要求但有价值)

