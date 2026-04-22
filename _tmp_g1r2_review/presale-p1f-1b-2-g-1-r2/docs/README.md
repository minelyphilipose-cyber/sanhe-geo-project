# P1·F·1·b·2·γ·1 详情页 P10-P14 真实实现(r2)

> 范围:Page10 覆盖度总览 + Page11 覆盖度详情 + Page12/13/14 优化机会(三优先级)
> 前置:β·3 r1 已合入 + `merged_findings` 合并逻辑在 P1·B mergeSnapshot 已做完
> 后续:γ·2(P15-P18,4 页)→ 后端补 `platform_intent_breakdown` → β·2·补(P05 单页)

---

## 0. 修订记录

### r2(当前)— 采纳 Codex P2 建议

仅 FindingCard.vue 改动:

| 问题 | r1 状况 | r2 修复 |
|---|---|---|
| **P2** 证据行可见性依赖调用方自律 | FindingCard 仅按 `v-if="evidenceText"` 守卫,MID/LOW 若未来被误传 evidenceText 会出现证据行,偏离原型视觉设计 | 把 priority === 'HIGH' 守卫**写进组件内部** `v-if`:`v-if="priority === 'HIGH' && evidenceText"`,组件自持有规则;同步更新组件头部注释和 prop 注释,明确"MID/LOW 即使传了 evidenceText 也不会渲染" |

其他 5 个 Page SFC、Viewer 零改动,原样继承 r1。

### r1 — 初版

γ·1 首版,完整实现 P10-P14 五页 + 抽 shared/FindingCard + Viewer 接入。

---

## 0. 本轮 1 句话

**覆盖度 2 页 + 优化机会 3 页真实实现,引入 shared FindingCard 组件**。γ 阶段过半,只剩 γ·2 和 β·2·补。

---

## 1. 文件清单

### 新增(6 个)

| 路径 | 说明 |
|---|---|
| `geo-web/src/views/admin/presale/report/detail/Page10CoverageOverview.vue` | 3 价值层 summary 卡片 + 高价值场景明细表 |
| `geo-web/src/views/admin/presale/report/detail/Page11CoverageDetail.vue` | 中价值场景明细表 + 动态合成的底部引用 |
| `geo-web/src/views/admin/presale/report/detail/Page12FindingsHigh.vue` | **章节标题 + TOTAL banner**(全局统计)+ HIGH priority 卡片列表(含证据行) |
| `geo-web/src/views/admin/presale/report/detail/Page13FindingsMid.vue` | MEDIUM priority 卡片列表(续页无 banner) |
| `geo-web/src/views/admin/presale/report/detail/Page14FindingsLow.vue` | LOW priority 卡片列表 + 动态引用 + **CATEGORY BREAKDOWN 4 卡** |
| `geo-web/src/views/admin/presale/report/detail/shared/FindingCard.vue` | Finding 卡片共用组件(P12/13/14 共用,priority 控制视觉) |

### 需要修改(1 个)

| 路径 | 修改 |
|---|---|
| `geo-web/src/views/admin/presale/report/detail/ReportViewer.vue` | 引入 P10-P14 真实组件;PLACEHOLDER_PAGES 从 9 条减到 4 条(仅剩 P15-P18) |

### 本批未动

- α/β·1/β·2/β·3 所有文件
- shared/PresaleChart(本批无 chart)
- 类型 / merge-snapshot / API / 路由

---

## 2. 架构决策

### 2.1 抽 `shared/FindingCard.vue`

P12/P13/P14 的 finding 卡片是 3 页共用,抽 `FindingCard.vue` 组件。priority prop 控制视觉(颜色 / 字号 / 是否显示 evidence 行),3 页调用方代码量骤减。

这对应 β·2 READY §7 里预留的"若真重复再抽"决策 —— γ·1 遇到真实重复,抽之。

### 2.2 Banner 只放 P12

TOTAL IDENTIFIED + 3 priority 数字 的深色 banner 只放在 P12 开头。理由:
- 对齐原型(P13/P14 都从 priority tag 起,没有 banner 重复)
- 节省 P13/P14 的 A4 空间,更多留给 finding 卡片

### 2.3 卡片编号用**本页局部**序号

P12 显示 01/02/03...;P13 也从 01 起;P14 同样。**不用全局 `sort_order` 作编号**。

**理由**:
- 用户认知:"P12 第 3 个高优先级机会"比"编号 03(全局 08)"直观
- 实现简单(`v-for` idx + 1)
- `sort_order` 字段仍用于**排序**(每页内部 findings 按 sort_order 升序排列),只是不作为显示编号

### 2.4 空态文案(按你拍板)

HIGH/MEDIUM/LOW 任一 priority 下无 finding 时,显示:

> ✓ 本优先级下无待优化项 — 您在该维度已表现良好,建议继续保持。

用 `#047857` 绿色 tick 图标 + 浅绿色背景 + 左绿边线,表达"正向反馈"。

三页**完全一致文案**(避免用户觉得 HIGH/MID/LOW 的空态处理不同产生疑虑)。

### 2.5 P10/P11 只展开"高"和"中"价值明细

P10 展开**高价值**明细表,P11 展开**中价值**明细表。**低价值不展开独立表**。

这是对齐原型的**产品设计**:低价值场景不值得占 A4 版面,用户如需要看低价值缺口可在 P08 竞品差异页查到。

### 2.6 Category Breakdown 用契约枚举(不是原型)

原型 P14 底部 4 张卡片文案是:基础设施 / 内容建设 / 关系建设 / **长期运营**。

契约 `OptimizationFinding.category` 枚举是:基础设施 / 内容建设 / 关系建设 / **平台扩展**。

γ·1 **遵循契约**,4 张卡片显示"平台扩展"而不是"长期运营"。这是原型和契约的**文案偏差**,未来如果产品要改契约文案也需要改原型。

### 2.7 动态文案合成

多个位置(P11 底部引用、P14 底部引用、P14 banner "识别出 N 个...")都从**实时数据**合成,不用硬编码。

---

## 3. 数据映射速查

### Page10 Coverage Overview

| UI | 数据来源 |
|---|---|
| 3 卡片 coverage_rate | `scene_coverage.{high/mid/low}_value.coverage_rate`(`Math.round()`) |
| 3 卡片 covered/total | `scene_coverage.{high/mid/low}_value.{covered, total}` |
| 高价值明细表 | `scene_coverage.high_value.{covered_queries, missing_queries}` 合并,先覆盖后缺口 |

### Page11 Coverage Detail

| UI | 数据来源 |
|---|---|
| 中价值明细表 | `scene_coverage.mid_value.{covered_queries, missing_queries}` 合并 |
| 底部引用 | 合成:"您在高商业价值场景的覆盖率为 X%。中价值场景中仍有 N 个未覆盖..." |

### Page12/13/14 Findings

所有 3 页基础:

| UI | 数据来源 |
|---|---|
| priority 筛选 | `merged_findings.filter(m => m.finding.priority === '...')` |
| 卡片 title/description/evidence_text | `m.title / m.description / m.evidence_text`(合并视图,L3 优先 L2 回退) |
| 卡片编号 | 本页 idx + 1,padStart '01' |
| 排序 | 按 `m.sort_order` 升序 |

**P12 独有**:

| UI | 数据来源 |
|---|---|
| TOTAL banner 总数 | `merged_findings.length` |
| banner 3 priority 分布 | 按 priority 计数 |
| 章节标题 "08 优化机会清单" | 静态 |

**P14 独有**:

| UI | 数据来源 |
|---|---|
| 底部引用 "以上 N 个优化点..." | 合成:总数 + 固定导语 |
| CATEGORY BREAKDOWN 4 卡 | `merged_findings` 按 category 分组计数,固定顺序 `[基础设施, 内容建设, 关系建设, 平台扩展]` |

---

## 4. 验证步骤

### 4.1 合入

```
cp Page10CoverageOverview.vue Page11CoverageDetail.vue → 仓库(新增)
cp Page12FindingsHigh.vue Page13FindingsMid.vue Page14FindingsLow.vue → 仓库(新增)
cp shared/FindingCard.vue → 仓库(新增,注意 shared/ 已有目录)
cp ReportViewer.vue → 仓库(覆盖)
```

### 4.2 构建

```
cd geo-web
npm run build
```

期望:无报错,bundle 增量几乎为零(本批无新 chart 模块引入)。

### 4.3 dev 验证

| 页 | 检查点 |
|---|---|
| P10 | 3 张 summary 卡片(高红/中橙/低灰色数字)+ 优先级 badge;高价值明细表有行,✓ 绿 ✗ 红 |
| P11 | 顶部小节标题"中价值场景";明细表有行;底部引用显示实际数字(非模板占位) |
| P12 | 顶部章节大标题"08 优化机会清单";深色 banner 显示 "识别出 N 个可执行的优化点" + 3 个大数字;红色左边线的 finding 卡片,每张有证据行 |
| P13 | 无 banner;橙色左边线;卡片无证据行(较 P12 紧凑);编号从 01 起(不从 P12 末尾续) |
| P14 | 灰色左边线;底部引用 + CATEGORY BREAKDOWN 4 张卡片(**显示"平台扩展"而不是"长期运营"**) |
| 空态 | 构造一份 merged_findings 全是 HIGH 的数据,P13/P14 应显示绿色 ✓ 空态文案,而非空白 |
| Sidebar | P10~P14 锚点滚动 + 高亮正常 |

### 4.4 空态测试要点

如果 dev 环境没有恰好缺失某个 priority 的数据,手工修改 merged_findings 模拟:
- 全 HIGH:P13/P14 空态
- 全 MEDIUM:P12/P14 空态
- 全 LOW:P12/P13 空态

确认 3 个空态文案一致、颜色一致(绿色 ✓)。

---

## 5. 开发假设清单

### 5.1 `merged_findings` 非空且 `finding.priority` 合法枚举 ✅
契约锁定 HIGH/MEDIUM/LOW 三值。若后端返其他值(如 'CRITICAL'),三页都不会展示,会落入空态分支。

### 5.2 `sort_order` 已由 mergeSnapshot 保证非 null
merge-snapshot.ts line 230+ 有保证逻辑(`L3.sort_order ?? L2 原序 index+1`)。

### 5.3 `evidence_text` 在 HIGH 优先级下多数非空
P12 会显示 evidence 证据行。若 evidence_text 恰好为空串,FindingCard 的 `v-if="evidenceText"` 会隐藏该行,不崩。

### 5.4 contract category 枚举严格 4 值
基础设施 / 内容建设 / 关系建设 / 平台扩展。若某条 finding 的 category 在这 4 个之外,P14 的 CATEGORY BREAKDOWN 不会统计它(`filter(m => m.finding.category === name)` 匹配不上),**该条 finding 不会出现在 4 张卡片的任何一张**。这是**刻意的**:宁可少算也不造假数据。

### 5.5 `covered_queries` 和 `missing_queries` 其中可能为 undefined
契约 `SceneCoverageGroup` 两字段都是 `?`。代码用 `?? []` 兜底,两者都 undefined 时明细表空(触发 `p10-empty` / `p11-empty`)。

---

## 6. 给 γ·2 的前置规范

γ·2 范围:P15 预期收益 / P16 分阶段路径 / P17 关键发现总结 / P18 关于我们

### 6.1 P15 ROI line chart 决策(γ·2 开工前必拍板)

⚠️ **这是 γ·2 开工前必须对齐的点**。契约 `roi_simulation` 只有 4 个数 + `phases[]`,**无时间序列字段**。原型 P15 的 line chart 需要从以下方案中选:

- **A** current→target 线性插值(欺骗性,不推荐)
- **B** 基于 `phases[]` 的阶段曲线(每 phase 一个点,按阶段序排开)
- **C** 降级为多数字对比块(去掉 chart)
- **D** 占位 + 后端需求(和 P05 同策略)

γ·2 开工前会基于 `RoiPhase` 字段详情做决策。我会在 γ·2 开工前单独和你对齐。

### 6.2 P16 `merged_phases` 可直接消费 ✅
`merge-snapshot.ts` 已做 mergePhases。γ·2 直接 v-for。

### 6.3 P17 `key_takeaways` 同 P03
β·2 已消费过,字段成熟,sort 同样方法(slice + sort by order_no)。

### 6.4 P18 `关于我们` 静态页
无数据消费,用 brand_name 或者完全静态品牌内容(产品决策)。

---

## 7. 已知不做

| 项 | 原因 |
|---|---|
| P10/P11 低价值明细表 | 产品设计:低价值不值得独立展开,P08 竞品差异已可见 |
| P12 finding 卡片跨页编号延续 | β·2 决策:本页局部编号更直观,不追求和原型设计稿 100% 一致 |
| `scene_is_polished` 式 "L3 vs L2 来源" 在 FindingCard 上标注 | β·3 P08 那里做了,但 finding 是 L3 文案+L2 默认模板回退,没有布尔标记区分;若产品需要看"哪些 finding 是人工润色过的",需要 merge-snapshot 补类似 `finding_is_polished` 字段 |
| finding 的 rule_code / finding_id 在 UI 显示 | 内部字段,对客不展示 |

---

## 8. 给 Codex 的复审 checklist

### 必须通过
- [ ] `npm run build` 通过
- [ ] dev 访问详情页,P10-P14 展示真实数据(不是占位)
- [ ] P10 3 张 summary 卡片数字 / 覆盖分母分子正确
- [ ] P12 TOTAL banner 显示真实总数(不是 18 这种原型硬编码)
- [ ] P12 卡片左边线红色 / P13 橙色 / P14 灰色
- [ ] P12 卡片有证据行,P13/P14 没有
- [ ] P14 CATEGORY BREAKDOWN 显示**"平台扩展"而非"长期运营"**
- [ ] 某一 priority 无 finding 时显示空态(✓ 绿色 + 完整文案)
- [ ] Sidebar P10-P14 锚点滚动 + 高亮正常

### 建议扫一眼
- [ ] FindingCard 作为 shared 组件,被 P12/P13/P14 共用(3 处 import)
- [ ] 三张 finding 页空态文案**完全一致**
- [ ] P11 底部引用和 P14 底部引用都是**动态合成**(包含实际数字)
- [ ] PLACEHOLDER_PAGES 数组已减到 4 条(P15-P18)

### 确认未修改
- [ ] α/β·1/β·2/β·3 所有文件
- [ ] `shared/PresaleChart.vue`
- [ ] 类型 / merge-snapshot / API / 路由
