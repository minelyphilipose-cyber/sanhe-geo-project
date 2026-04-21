# P1·F·1·b·2·β·3 详情页 P07/P08/P09 真实实现

> 范围:Page07 竞品对标总览(bar)+ Page08 竞品场景差异 + Page09 情感倾向(doughnut)
> 前置:β·2 r3 已合入 + `shared/PresaleChart.vue` 可用
> 后续:β·2·补(等后端 platform_intent_breakdown 后做 P05)→ γ(P10-P18,9 页)

---

## 0. 本轮 1 句话

**3 页真实实现,β 阶段主体完工**。β·3 之后只剩 γ(P10-P18,9 页)+ β·2·补(P05)。

---

## 1. 文件清单

### 新增(3 个)

| 路径 | 说明 |
|---|---|
| `Page07CompetitorOverview.vue` | 竞品对标总览,3 竞品卡片 + 自家卡片 + mention_count bar chart + 静态引用 |
| `Page08CompetitorScene.vue` | 竞品场景差异,基于 missing_queries 的 gap 表 + 3 张价值层卡片 + Top1 优势场景引用(带 L3/L1 来源标签) |
| `Page09Sentiment.vue` | 情感倾向,doughnut chart + 3 条进度 + 正面关键词云 + 负面证据 |

### 需要修改(1 个)

| 路径 | 修改 |
|---|---|
| `ReportViewer.vue` | 追加 P07/P08/P09 import 和渲染;`PLACEHOLDER_PAGES` 数组从 12 条减为 9 条(仅剩 P10-P18) |

### 本批未动

- `shared/PresaleChart.vue`(β·2 建立,已注册 RadarChart/BarChart/PieChart/LineChart,pie 直接可用,无需改动)
- α·1/α·2/β·1/β·2 所有其他文件

---

## 2. 架构决策

### 2.1 P07 方案 D(mention_count 对比 bar)

和卡片的 mention_rate(%)互补:卡片看百分比趋势,图看绝对次数。4 根柱 = 3 竞品 + 自己(自己放最右,深墨色突出"被比较对象")。Top1 橙色 / Top2-3 蓝色 / 自己深墨色。

tooltip 文案"被提及 N 次",不用百分号(避免和 rate 混淆)。

### 2.2 P08 数据源:仅 `missing_queries`

页面主题是"竞品被推荐而您未被推荐的场景",只用 `scene_coverage.*.missing_queries`。"您"列永远 ✗,竞品列按 `top_competitor_coverage.includes(竞品名)` 判断 ✓/✗。

**竞品列动态化**:表头竞品名来自 `merged_competitors[].name`,不硬编码。超过 4 字截断为"前 3 字…"避免撑破表格。

### 2.3 P08 底部引用:消费 `scene_advantages` + `scene_is_polished` 弱标签

按你之前的定夺,展示 L3/L1 来源标签。逻辑:
- 引用语:取 Top1 竞品的 `scene_advantages.slice(0, 3).join('、')`(前 3 条)
- 若 `scene_is_polished === false`(来自 L1 原始提取),文案末尾加 `· 原始提取` 弱标签(10px / muted color / 无字体样式干扰,用户一看就知道"这段 AI 原样产出,未经人工润色")

### 2.4 P09 doughnut 中心数字

利用 ECharts 的 `label.rich`,在圆心展示总提及次数(自家品牌 `positive+neutral+negative` 总和)+ "总提及"小标签。这是 doughnut 常见的视觉增强,不引入新依赖。

3 色沿用 theme:正面 `#047857` 绿 / 中性 `#6b6456` muted / 负面 `#b91c1c` 红。

### 2.5 P09 关键词云字号计算

契约 `SentimentKeyword.font_size?` 是可选字段。策略:
- **有 `font_size`**:直接用
- **没有**:按 `frequency` 线性映射到 12-20px(min 到 max)
- 字号 ≥18 字重 600,≥15 字重 500,其他 400

**只展示 `sentiment === 'POSITIVE'` 的关键词**(契约明确有 POSITIVE/NEUTRAL/NEGATIVE 三分类)。

### 2.6 P09 去掉"VS. 竞品"文案

原型 "竞品巴奴的正面比例为 91%,高于您的 82%" — **前端算不出**。`MergedCompetitor` 无 sentiment 字段,`SentimentDetail` 是自家品牌专属。β·3 **去掉此块**,README §7 标 TODO 转给后端。

### 2.7 P09 `top_keywords` / `negative_evidence` 空态

两者都是契约 `?` 可选字段。处理:
- `top_keywords` 不存在或 POSITIVE 过滤后为空 → 整个"正面关键词"section 不渲染
- `negative_evidence` 不存在或长度 0 → 整个"负面证据"section 不渲染
- 多条负面证据只取第一条(对齐原型,避免页面过长)

---

## 3. 数据映射速查

### Page07 Competitor Overview

| UI | 数据来源 |
|---|---|
| 3 竞品卡片:排名/名称/提及率/平均排名 | `merged_competitors[i].{rank, name, mention_rate, avg_ranking}` |
| 自家卡片:名称 | `brand_name` |
| 自家卡片:提及率 | `sum(platform.mention_count) / sum(platform.total_tests) * 100`(计数直除) |
| 自家卡片:平均排名 | `platform_breakdown` 按 mention_count 加权平均 `avg_ranking` |
| bar chart x 轴 | 3 竞品名 + 自家品牌名 |
| bar chart 数值 | 3 竞品的 `mention_count` + 自家的 `sum(platform.mention_count)` |

### Page08 Competitor Scene Gap

| UI | 数据来源 |
|---|---|
| 表 "价值 / 查询 / 意图" | `scene_coverage.{high/mid/low}_value.missing_queries[].{category, prompt_content}` |
| 表 "您" 列 | 永远 ✗(missing 列表) |
| 表竞品列 | `top_competitor_coverage.includes(竞品名)` |
| 表头竞品名 | `merged_competitors[].name` 动态 |
| 3 张卡片 X/Y 缺失 | `missing_queries.length` / `group.total` |
| 底部引用 | Top1 竞品的 `scene_advantages.slice(0,3).join('、')` + 静态导语 |
| 原始提取标签 | `scene_is_polished === false` 时显示 |

### Page09 Sentiment

| UI | 数据来源 |
|---|---|
| doughnut 3 分片 | `sentiment_detail.{positive,neutral,negative}_count` |
| 圆心数字 | 3 者之和 |
| 3 进度条 %/count | 上述 count 和总和计算 |
| 关键词云 | `top_keywords` 过滤 `sentiment === 'POSITIVE'`,字号 `font_size` 或按 frequency 映射 |
| 负面证据 | `negative_evidence?.[0].{platform_name, tested_at, query, snippet}` |

---

## 4. 验证步骤

### 4.1 合入

```
cp geo-web/src/views/admin/presale/report/detail/Page07CompetitorOverview.vue  → 仓库(新增)
cp geo-web/src/views/admin/presale/report/detail/Page08CompetitorScene.vue → 仓库(新增)
cp geo-web/src/views/admin/presale/report/detail/Page09Sentiment.vue → 仓库(新增)
cp geo-web/src/views/admin/presale/report/detail/ReportViewer.vue → 仓库(覆盖)
```

### 4.2 构建

```
cd geo-web
npm run build
```

期望:无报错,bundle 增量相对 β·2 **几乎为零**(PieChart 等模块 β·2 已注册,β·3 不 import 新模块)。

### 4.3 dev 验证

| 页 | 检查点 |
|---|---|
| P07 | 4 张卡片(3 竞品 top1 橙 / top2-3 蓝 / 自家深墨深蓝渐变);竞品姓名是真实的(非硬编码"巴奴"等);bar chart 最右一柱是自家品牌,数值是 count(非 rate) |
| P08 | 表格竞品列表头是真实名(不超过 4 字全显示,超长看是否截断加省略号);每行"您"列全 ✗;竞品列有 ✓/✗ 混合;3 张底部卡片数字合理(HIGH/MID/LOW gap) |
| P09 | doughnut 3 色正确(正面绿/中性 muted/负面红);圆心有"XX 总提及"两行文字;3 进度条宽度 = pct%;若后端有 top_keywords,正面关键词云出现,不同字号;若有 negative_evidence,底部红色框显示证据 |
| Sidebar | P07/P08/P09 锚点点击跳转 + 滚动高亮生效 |

---

## 5. 开发假设清单

### 5.1 `merged_competitors` 长度 = 3 ✅
契约 `maxItems=3`,可能 <3(行业不够竞品)。P07 模板用 `v-for`,<3 时只渲染实际条数,布局会有空。**不阻塞 β·3 合入**,产品实际场景 >=2 个竞品。

### 5.2 `missing_queries` 可能 undefined
契约 `SceneCoverageGroup.missing_queries?`。模板用 `?.length ?? 0` 兜底,若 3 组都 undefined,表空 + 3 卡片显示 0,落入 `<div v-else class="p08-empty">` 分支。

### 5.3 `top_competitor_coverage` 里的竞品名和 `merged_competitors[].name` 完全一致
**这是一个隐式的后端约定**。若两边写法有差异(如"巴奴毛肚火锅" vs "巴奴"),前端 `.includes()` 会匹配失败 → 全部 ✗。**Codex 合入后用真实数据验证一次**,若不一致需要后端统一。

### 5.4 SentimentKeyword 字段名
已对照契约确认:`keyword` / `frequency` / `sentiment` / `font_size?`。本批代码用对了。

### 5.5 P08 表格 3 竞品列写死 grid-template-columns
原型固定 `60px 1fr 90px 60px 60px 60px 60px`(60px × 3 竞品)。若 merged_competitors 实际只有 2 个或 0 个,表格第 3 列会空。暂不处理(产品场景总是 3 个);若要动态化,改为 `grid-template-columns: 60px 1fr 90px 60px repeat(${n}, 60px)`。

---

## 6. 给 γ 的前置规范

γ 范围:P10 覆盖度总览 / P11 覆盖度详情 / P12-P14 优化发现(三张)/ P15 预期收益 / P16 分阶段路径 / P17 关键发现总结 / P18 关于我们

### 6.1 剩余图表

- **P15 预期收益**:line chart(ROI 时间序列)—— 已在 `shared/PresaleChart.vue` 注册 LineChart
- **P10/P11 覆盖度**:可能需要进度条/条形图;看契约 `scene_coverage` 和 `intent_breakdown` 的交叉,大概率不需要新 chart 类型
- **P12-P17**:纯排版 + 数据罗列,不需要 chart
- **P18 关于我们**:静态品牌页,不消费 contract

### 6.2 合同字段预览

γ 会大量消费:
- `merged_findings[]`:优化发现合并视图(L3 文案优先 + L2 规则回退)
- `merged_phases[]`:分阶段路径(L3 优先 + L2 规则回退)
- `roi_simulation`:ROI 模拟
- `intent_breakdown[]`:意图覆盖
- `scene_coverage.{high/mid/low}.covered_queries[]`:这次用 covered 而非 missing

### 6.3 γ 可能遇到的阻塞

和 β·2 的 P05 类似,γ 要看 `merged_findings` 和 `merged_phases` 合并逻辑是否已在 mergeSnapshot 里做完,如果没做,γ 开工前可能要补前端合并逻辑或请后端提升字段。届时再评估。

---

## 7. 已知不做(给后端/产品的 TODO)

| 项 | 原因 | 归属 |
|---|---|---|
| P09 "VS. 竞品" 正面比例对比 | 无 `competitor_sentiment` 契约 | 后端决策是否补 |
| P07/P08 底部引用文案动态化 | 无对应 L3 字段,目前静态 | 未来 L3 扩字段 |
| P07 bar chart 多维 grouped | 本批选方案 D 单维 mention_count;若产品坚持多维,需设计团队重做可视化方案 | 产品决策 |
| P08 covered_queries 展示 | 本批只展示 missing;若产品希望"已覆盖"也可见,新增一个 tab 或独立页 | 产品决策 |
| P05 2D 热力图(β·2·补) | 等后端 `platform_intent_breakdown` 契约 | 后端 + 后续 β·2·补 |

---

## 8. 给 Codex 的复审 checklist

### 必须通过
- [ ] `npm run build` 通过
- [ ] dev 访问详情页,P07/P08/P09 显示真实数据
- [ ] P07 bar chart 最右一柱是自家品牌,深墨色,tooltip "被提及 N 次"
- [ ] P08 表头竞品列使用真实竞品名,超长会截断
- [ ] P08 `scene_is_polished === false` 时底部引用末尾有"原始提取"弱标签
- [ ] P09 doughnut 3 色正确,圆心数字 = 三者和
- [ ] P09 关键词云按 sentiment POSITIVE 过滤;若后端没返 top_keywords,整块不显示(无空占位)
- [ ] Sidebar P07/P08/P09 锚点工作

### 建议扫一眼
- [ ] `SentimentKeyword` 字段:`keyword`(text)/ `frequency`(权重分母)/ `sentiment`(枚举)/ `font_size?`(可选像素)
- [ ] `NegativeEvidence` 字段:`platform_name` / `tested_at` / `query` / `snippet`
- [ ] P08 `top_competitor_coverage` 里的竞品名与 `merged_competitors[].name` 匹配(字符串 === 比较)
- [ ] bundle 增量几乎为零(PieChart 等模块 β·2 已注册)

### 确认未修改
- [ ] α/β·1/β·2 所有文件
- [ ] `shared/PresaleChart.vue`
- [ ] 类型 / merge-snapshot / API / 路由
