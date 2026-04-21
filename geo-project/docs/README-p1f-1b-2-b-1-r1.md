# P1·F·1·b·2·β·1 详情页字体/主题基础设施 + Page01/Page02 真实实现

> 范围:字体 fontsource 引入 + 全局 theme.css + Page01 封面 + Page02 诊断对象 + Viewer 接入
> 前置:α·2·fix·r1 合入(路由通畅)+ α·2 合入(详情页骨架)+ fontsource 4 包已安装
> 后续:β·2(P03-P06 + 首次 ECharts)→ β·3(P07-P09)→ γ(P10-P18)

---

## 0. 本轮 1 句话

**字体/CSS 变量/共用类基础设施一次建立,封面和诊断对象两页真实实现**,剩余 16 页继续占位,β·2 起就是"纯粹的页内容迁移"。

---

## 1. 文件清单

### 新增(4 个)

| 路径 | 说明 |
|---|---|
| `geo-web/src/assets/presale/report-theme.css` | CSS 变量 + 原型全部共用 class(page / section-title / data-matrix / competitor-card / heat-cell / priority-badge / timeline / ...),全部在 `.ps-page-scope` 作用域内生效 |
| `geo-web/src/views/admin/presale/report/detail/Page01Cover.vue` | 封面页真实实现 |
| `geo-web/src/views/admin/presale/report/detail/Page02Target.vue` | 诊断对象页真实实现 |
| `geo-web/src/main-patch/main.ts.PATCH` | **补丁片段**,合入 main.ts 后删除,见 §3 |

### 需要修改(3 个)

| 路径 | 修改 |
|---|---|
| `geo-web/src/main.ts` | 按 PATCH 追加 9 行 fontsource + 1 行 theme.css import |
| `geo-web/src/composables/presale/useMergedView.ts` | `MergedViewContext` 追加 `reportCreatedAt: Ref<string \| null>` 字段(向后兼容,不破坏 α·2 消费方) |
| `geo-web/src/views/admin/presale/report/PresaleReportDetail.vue` | `provideMergedViewContext` 调用新增 `reportCreatedAt` 字段 |
| `geo-web/src/views/admin/presale/report/detail/ReportViewer.vue` | 根元素加 `ps-page-scope` class;P01/P02 从 PagePlaceholder 换为真实 SFC;`PLACEHOLDER_PAGES` 从 18 条减为 16 条 |

### 本批未动

- α·1 的 `presaleReport.ts` / `unwrap.ts`
- α·2 的 `DetailSidebar.vue` / `PagePlaceholder.vue`
- P1·F·1·a 的 List/Create/Progress 三个 Vue
- 路由(α·2·fix·r1 已处理)
- types/ merge-snapshot.ts

---

## 2. 架构决策(β·1 建立,β·2/β·3/γ 照用)

### 2.1 CSS 作用域化:`.ps-page-scope` 外层容器

原型大量全局类(`.section-title` / `.data-matrix` / ...)直接用会**污染全局样式**。方案:

- **全部共用类放在 `.ps-page-scope` 后代选择器下**(见 `report-theme.css`)
- **`ReportViewer.vue` 根元素加 `class="ps-page-scope"`**(已改)
- **Page SFC 里可原样使用原型 class**(`class="section-title"` 不加前缀),天然在作用域里
- **CSS 变量走 `--presale-*` 前缀**(`--presale-ink` / `--presale-accent` ...)

好处:β·2/β·3/γ 的 Page SFC 模板可以**几乎原样照抄原型 HTML**,只把硬编码替换为插值。

### 2.2 字体 fontsource 接入

`main.ts` 多 9 行 import(4 家族 × 对应权重),fontsource 自带 `@font-face`,按权重按 unicode-range 子集加载,**无需手写字体文件和 `@font-face` 声明**。

字体作用域**不动 body 默认**,只在 `.ps-page-scope .page` 里声明 font-family。仓库其他模块字体不变。

**对齐你给的执行约束**:"字体作用域限制在详情页 .page 体系内"✅。

### 2.3 `MergedViewContext` 扩展 `reportCreatedAt`(重要变更)

**背景**:`MergedViewDTO` 缺一个"报告生成/创建时间"字段可供前端展示,P1·B buildMeta 没有把 `raw.meta.generated_at` 提升到 merged view。Page01 封面的 "ISSUED" 日期区块需要一个日期,β·1 临时用 `ReportDetailVO.createdAt` 代替。

**改动**:
- `useMergedView.ts` `MergedViewContext` 追加字段 `reportCreatedAt: Ref<string | null>`
- `PresaleReportDetail.vue` 新增 `const reportCreatedAt = computed(() => detail.value?.createdAt ?? null)`,provide 里加进去
- Page01Cover / Page02Target 通过 `useMergedView()` 解构拿到

**兼容性**:向后追加字段,**不影响 α·2 已合入的 Sidebar / Viewer 等消费方**(它们只解构自己需要的字段,不会因为多了字段就报错)。

**TODO**(见 §6):若未来 P1·B MergedViewMeta 补上 `generated_at`,本字段降级为 fallback 或直接删除。

### 2.4 日期格式化:不引入 dayjs

用原生 `toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })` 产出 "2026年4月18日" 风格。浏览器原生实现对中文 locale 已经做得很好,β·1 不引入 dayjs 依赖。

若未来要更精细的时区/相对时间展示(如 "3 天前"),再引入 dayjs 或 date-fns。

### 2.5 Page SFC 结构契约(β/γ 所有 Page 必须遵守)

```vue
<template>
  <section id="page-XX" class="page-anchor">
    <div class="page [cover]">
      <!-- 业务内容,原型 class 原样使用 -->
    </div>
  </section>
</template>
```

- `<section :id>` 是 Sidebar 锚点必需
- `.page[.cover]` 由 theme.css 提供 A4 版式 + 封面变体
- `.page-anchor` 是 scoped 样式用 flex 居中(见 Page01/02 `<style scoped>` 顶部)

### 2.6 Page SFC 数据消费:只经由 `useMergedView()`

```ts
const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)  // 父级 v-if 守卫
```

父级 `ReportViewer` 已 `v-if="isDone && mergedView"`,Page SFC 内可对 `mergedView.value` 做 non-null 断言,业务字段安全访问。

---

## 3. main.ts 合入指引

打开 `geo-web/src/main.ts`,在 import 区段末尾追加:

```ts
// 售前报告字体(fontsource,已在 package.json 安装)
import '@fontsource/playfair-display/400.css'
import '@fontsource/playfair-display/900.css'
import '@fontsource/noto-serif-sc/400.css'
import '@fontsource/noto-serif-sc/500.css'
import '@fontsource/noto-serif-sc/700.css'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/500.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/jetbrains-mono/400.css'

// 售前报告详情页主题
import '@/assets/presale/report-theme.css'
```

删除 `main-patch/main.ts.PATCH` 文件。

---

## 4. Page01 / Page02 数据映射

### Page01 Cover

| UI 显示 | 数据来源 |
|---|---|
| 品牌名(海底捞) | `mergedView.brand_name` |
| 报告编号(REPORT · GEO-...) | 构造:`GEO-{meta.report_id}-V{meta.version_no}`(后端无正式编号字段)|
| 主标题两行 | 基于 `mergedView.report_title` 按"报告/诊断报告"切分,无法切分则整句占第一行 |
| 副标题(专为 XX 定制) | `mergedView.brand_name` |
| 行业·身份·地区 | `mergedView.industry` / `industry_role` / `region`(空段跳过)|
| ISSUED 日期 | `reportCreatedAt` → `toLocaleDateString('zh-CN', ...)` |
| TESTED(N 平台 · M 查询) | `test_summary.total_platforms` / `total_prompts` |
| CLASSIFIED | 固定"机密 · Confidential"(无字段)|

### Page02 Target

| UI 显示 | 数据来源 |
|---|---|
| 顶部条"GEO 诊断报告 · XX" | `mergedView.brand_name` |
| BRAND NAME | `mergedView.brand_name` |
| BRAND 副行(HAIDILAO / 法人名) | **原型占位,无契约字段,β·1 留空**(TODO 见 §6) |
| REGION | `mergedView.region` |
| REGION 副行(华北区域) | **同上,留空** |
| INDUSTRY / BUSINESS ROLE | `mergedView.industry` / `industry_role` |
| USER INQUIRY | `mergedView.user_demand`,空时显示"(客户未填写诉求)" |
| 诊断范围 4 个数字 | `test_summary.total_platforms` / `total_prompts` / 两者乘积 / 固定 5 维度 |
| 底部引用块日期 | `reportCreatedAt` |
| 页码 02 | 硬编码 |

**原型 hardcode → 实际数据**的核心原则:**只显示契约字段;原型里没有对应字段的虚设文案**(HAIDILAO、海底捞国际控股、中国·华北区域)**不硬造,留空或用真实字段替代**。

---

## 5. 验证步骤

### 5.1 合入

```
# 覆盖文件
cp geo-web/src/composables/presale/useMergedView.ts     → 仓库(覆盖)
cp geo-web/src/views/admin/presale/report/PresaleReportDetail.vue → 仓库(覆盖)
cp geo-web/src/views/admin/presale/report/detail/ReportViewer.vue → 仓库(覆盖)

# 新增文件
cp geo-web/src/assets/presale/report-theme.css          → 仓库(新增)
cp geo-web/src/views/admin/presale/report/detail/Page01Cover.vue  → 仓库(新增)
cp geo-web/src/views/admin/presale/report/detail/Page02Target.vue → 仓库(新增)

# main.ts 合入(见 §3),完事后删掉
rm geo-web/src/main-patch/main.ts.PATCH
```

### 5.2 构建

```
cd geo-web
npm run build         # 含 vue-tsc -b
```

期望:无报错。

### 5.3 dev 验证

前置:数据库里至少有一个 DONE 状态、L1/L2/L3 JSON 完整的报告版本。

| 检查项 | 期望 |
|---|---|
| 菜单点"售前报告"进入列表页 | 样式不变(不受 β·1 影响) |
| 点任意 DONE 报告"查看" | 跳详情页 |
| 详情页第一屏 | **封面页**:深色渐变背景、主标题大字、品牌高亮色、底部 3 栏(日期/测试/密级) |
| 滚到第二屏 | **诊断对象页**:白纸背景、章节标题大号斜体"01"、数据矩阵 3 行、深色"诊断范围"条、底部引用框 |
| 字体检查 | 封面主标题字体应是 Playfair Display(衬线,有古典感)+ Noto Serif SC(中文衬线);正文是 Noto Sans SC;数字小标签是 JetBrains Mono |
| 侧栏锚点 | 点"01 封面"→ 回到 Page01;点"02 诊断对象"→ 跳 Page02;滚动时 active 高亮在两者间切换 |
| 样式隔离 | 回列表/创建/进度页,字体和颜色**不变**(β·1 没污染全局) |

### 5.4 字体加载网络验证

dev 模式下 F12 打开 Network 看 font 类资源,应该看到若干 `noto-sans-sc-chinese-simplified-400-normal.woff2` 等切片文件(fontsource 按 unicode-range 拆的)。初次打开详情页会一次性下载所需切片,每片一般 10-50KB,总计 <300KB。

---

## 6. 开发假设清单

### 6.1 fontsource 4 包已安装 ✅
你确认已装。package.json 里应有 `@fontsource/playfair-display` / `@fontsource/noto-serif-sc` / `@fontsource/noto-sans-sc` / `@fontsource/jetbrains-mono`。

### 6.2 `main.ts` 可以自由追加 import
假设仓库现有 `main.ts` 结构清晰,追加 10 行 import 不影响现有 createApp 链。

### 6.3 `reportCreatedAt` 是 `ReportDetailVO.createdAt` 的合适代替
**这是本批最值得关注的假设**。`createdAt` 是"报告主表创建时间",不是"本次生成完成时间"。两者差几秒到几分钟不等(取决于生成耗时)。

**影响**:Page01 封面和 Page02 底部引用块的"ISSUED"日期会显示"报告创建日",对用户而言**一般不会觉得有错**(同一天发起的生成,日期一致);跨天的边界场景(例如 23:59 发起生成、凌晨 00:01 完成)会有"前一天的日期"展示。

**补救路径(不阻塞 β·1 合入)**:
- 后端可在 `MergedViewMeta` 追加 `generated_at: string`(从 `raw.meta.generated_at` 提升)
- 或 `ReportDetailVO` 顶层追加 `lastGeneratedAt` 字段
- 前端 `useMergedView` 的 `reportCreatedAt` 改成优先读新字段,fallback 到 `createdAt`

### 6.4 原型 BRAND 副行 / REGION 副行留空可接受 ⚠️
原型里有 "HAIDILAO / 海底捞国际控股"、"中国 · 华北区域" 这些**纯设计稿占位**。β·1 选择留空而不是硬造。
- **若产品希望有英文名 + 法人**:后端 `ClientInfo` 需加 `brand_name_en` / `legal_entity`,α·1 的 `ReportDetailVO` 和 `CreateReportRequest` 同步扩;β·2 或后续补显示
- **若产品希望有区域组**:后端加 `region_group` / `country`

### 6.5 `mergedView.value` 非 null 断言安全
Page01/02 的 script 里用了 `mergedView.value!`(non-null 断言),依赖父级 `ReportViewer` 的 `v-if="isDone && mergedView"` 守卫。若未来 Viewer 去掉这个守卫,Page SFC 会在渲染时崩。

**保护**:ReportViewer.vue 的 v-if 不允许删除,β/γ 后续修改都要保留。

---

## 7. 给 β·2 的前置规范

β·2 范围:Page03 执行摘要 / Page04 可见度评分(radar chart)/ Page05 多平台热力图 / Page06 平台详细数据(bar chart)

### 7.1 ECharts 接入模式(β·2 第一次引入,定下模板)

仓库已装 `echarts@^5.5.1` + `vue-echarts@^7.0.3`。β·2 有两种选择:

- **方案 A**:用 `vue-echarts` 组件:`<v-chart :option="option" autoresize />`
- **方案 B**:原生 `echarts.init(ref.value)` + `onMounted` / `onBeforeUnmount` dispose

**建议方案 A**(用 vue-echarts),理由:
- Vue 生态主流、维护成本低
- autoresize 自动处理容器 resize
- 不用手管 dispose

β·2 起手先写一个 chart 作为样板,β·3 照抄。

### 7.2 可复用子组件

β·2 触及 3 个原型模块:radar / bar chart / heat-map-grid。若 β·3 还会用同类,抽到 `detail/shared/`。β·2 起手先不抽,等 β·3 看重复度再决定。

### 7.3 类型消费

本批已建立"mergedView.value! 非空断言"惯用法。β·2 Page SFC 直接:
```ts
const scores = computed(() => mergedView.value!.scores)
const platformBreakdown = computed(() => mergedView.value!.platform_breakdown)
```

---

## 8. 已知不做(刻意 deferred)

| 项 | 原因 | 归属 |
|---|---|---|
| `generated_at` 字段从后端提升到 MergedViewMeta | 需要改 P1·B buildMeta + 后端 ReportDetailVO;β·1 用 `reportCreatedAt` 代替可接受 | 后续后端小改 |
| BRAND_EN / 法人名 / 区域组 等原型虚设字段 | 无后端契约支持;硬造会误导 | 产品决策后补 |
| 字体本地自建 woff2 子集 | 交接文档提到"woff2 子集",fontsource 已满足该目标(它本身就是子集 + unicode-range 切片) | 不做 |
| 报告正式编号规则 | β·1 用 `GEO-{reportId}-V{versionNo}` 占位,若产品要 "GEO-2026-0418-001" 风格(含日期+序号)需后端补 `report.code` 字段 | 产品决策 |

---

## 9. 给 Codex 的复审 checklist

### 必须通过
- [ ] `npm run build` 通过(含 vue-tsc -b)
- [ ] `main.ts` 追加 10 行 import 完成,PATCH 文件已删
- [ ] dev 访问详情页,P01/P02 展示真实数据(非占位"Page SFC 将在 β 阶段实现"字样)
- [ ] 字体在 P01/P02 可见(Playfair Display 斜体 / Noto Serif SC / Noto Sans SC / JetBrains Mono),且**仓库其他模块字体未受影响**
- [ ] 侧栏锚点到 P01/P02 的跳转和高亮正常
- [ ] P03-P18 仍是 PagePlaceholder(说明 β·1 没跑偏到别的页)

### 建议扫一眼
- [ ] `report-theme.css` 所有共用 class 都在 `.ps-page-scope` 作用域下(自检脚本已确认)
- [ ] Page01/02 模板里的 class 名和原型一致(`section-title` / `data-matrix` / `pull-quote` 等)
- [ ] `useMergedView` 的 `MergedViewContext` 追加字段是**向后兼容**(α·2 Sidebar/Viewer 解构未中断)
- [ ] `reportCreatedAt` 的 TODO 标记清楚,不会被忘记

### 确认未修改
- [ ] α·1 的 `presaleReport.ts` / `unwrap.ts`
- [ ] α·2 的 `DetailSidebar.vue` / `PagePlaceholder.vue`
- [ ] 1a 的 List / Create / Progress 三个 Vue
- [ ] `@/types/presale/*` / `@/utils/presale/merge-snapshot.ts`
- [ ] 仓库其他模块(页面字体、样式无变化)
