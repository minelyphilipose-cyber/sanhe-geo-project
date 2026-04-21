# P1·F·1·b·2·α·2 详情页骨架交付说明

> 范围:详情页路由入口 + 侧栏 + 18 页容器 + Page 占位 + provide/inject composable + 路由补丁
> 前置:P1·F·1·b·2·α·1 已合入(API unwrap 修复)
> 后续:β(Page01~09 真实 SFC)→ γ(Page10~18 真实 SFC)→ 编辑页 + 版本管理页

---

## 0. 本轮 1 句话

**详情页骨架落地**:访问 `/admin/presale/report/:id/detail` 能看到 Sidebar + 18 页 A4 占位,锚点滚动和 5 个写动作按钮都能用,**18 页具体内容待 β/γ 填充**。

---

## 1. 文件清单

### 新增(6 个)

| 路径 | 说明 |
|---|---|
| `geo-web/src/composables/presale/useMergedView.ts` | provide/inject + typed Symbol key 封装 |
| `geo-web/src/views/admin/presale/report/PresaleReportDetail.vue` | 路由入口,fetch + mergeSnapshot + provide |
| `geo-web/src/views/admin/presale/report/detail/DetailSidebar.vue` | 版本信息、5 个操作按钮、18 页锚点导航 + IntersectionObserver 高亮 |
| `geo-web/src/views/admin/presale/report/detail/ReportViewer.vue` | 18 页滚动容器 + 降级警示条 |
| `geo-web/src/views/admin/presale/report/detail/PagePlaceholder.vue` | 单页 A4 占位,β/γ 会被替换 |
| `geo-web/src/router/admin.ts.PATCH` | **补丁片段**,合入到 admin.ts 后删除,见 §3 |

### 需要修改(1 个)

| 路径 | 修改 |
|---|---|
| `geo-web/src/router/admin.ts` | 追加详情页路由(PATCH 文件指导) |

### 本批未动(重要)

- `geo-web/src/api/presaleReport.ts`(α·1 已改对,本批直接消费)
- `geo-web/src/api/presale/unwrap.ts`
- `geo-web/src/types/presale/*`
- `geo-web/src/utils/presale/merge-snapshot.ts`
- P1·F·1·a 三个已有页面(List / Create / Progress)

---

## 2. 架构简图

```
URL: /admin/presale/report/:id/detail[?versionNo=N]
 │
 ▼
PresaleReportDetail.vue            ← 路由入口(view)
 │  - fetch getLatestDetail / getVersionDetail
 │  - toVersionRowMeta(ReportDetailVO) → VersionRowMeta
 │  - JSON.parse L1/L2/L3 → mergeSnapshot → MergedViewDTO(仅 DONE)
 │  - 非 DONE 时 buildMetaOnlyView(降级视图)
 │  - provideMergedViewContext({ mergedView, currentVersionNo, loading, error, refresh, switchVersion })
 │
 ├─ DetailSidebar.vue               ← 侧栏(detail 子组件)
 │    - useMergedView() inject
 │    - 品牌名 / 行业 / 身份(读 mergedView 顶层)
 │    - 版本下拉(v1 禁用,待版本列表接口)
 │    - 5 个写动作按钮:derive / freeze/unfreeze / delete / retry / 导出 PDF(占位)
 │    - 18 页锚点链表 + IntersectionObserver 高亮当前
 │
 └─ ReportViewer.vue                ← 容器(detail 子组件)
      - useMergedView() inject
      - match_level !== EXACT 和 degraded 警示条
      - 18 × PagePlaceholder.vue(β/γ 替换)
```

---

## 3. 路由补丁合入

打开 `geo-web/src/router/admin.ts`,定位到 P1·F·1·a 已有的三段 presale 子路由(`presale/report`、`presale/report/create`、`presale/report/:id/progress`),在**最后一段之后**追加 `admin.ts.PATCH` 里给出的那段代码,然后删除 PATCH 文件。

补丁本身 33 行,含 meta、合入位置说明、query 参数示例。

---

## 4. 关键设计决策

### 4.1 不做 `toViewModel.ts`(本轮拍板)

`MergedViewDTO` 已是扁平消费视图,`merged_findings / merged_phases / merged_competitors` 已合并,`report_title/subtitle/roi_disclaimer` 已回退默认值。β/γ 的 Page SFC 直接 `v-for` 消费即可。

若 β/γ 真的出现多页共享派生值(如"总分加权"),届时抽 `composables/presale/useReportDerivedValues.ts`,按需抽象而非预先抽。

### 4.2 数据流:provide/inject + typed Symbol

详情页顶层 provide,子孙组件 `useMergedView()` 一行 inject。未 provide 时抛异常(而非静默 undefined),避免调试困难。

Key 是 `Symbol('MergedViewContext')` + `InjectionKey<MergedViewContext>`,类型安全。

### 4.3 非 DONE 状态:降级视图(buildMetaOnlyView)

当版本 `generation_status !== 'DONE'` 时,Detail 顶层不尝试 mergeSnapshot(多半 JSON 为空/部分),而是构造一个**降级视图**:
- meta 完整填充(用来展示状态、版本号、冻结、降级平台)
- 客户信息字段(brand_name / industry / industry_role / region / user_demand)完整填充
- **业务字段(test_summary / scores / merged_* 等)不填**

消费约定:Sidebar 可安全读 meta + 客户信息;Viewer 和 Page SFC 必须用 `meta.generation_status === 'DONE'` 守卫业务字段。Detail 顶层模板已做分支拦截:非 DONE 时**不渲染** Sidebar/Viewer,而是展示"查看生成进度"提示。所以 Sidebar 实际运行中不会拿到降级视图,但降级视图的存在让**未来扩展**(比如做"非 DONE 时也显示 Sidebar 让用户看到当前版本号")不需要改契约。

### 4.4 URL 契约:`?versionNo=N` query

- 不带 query → latest
- 带 `?versionNo=N` → 指定版本(数字解析失败按不带处理)

选这套比新起 URL 段(如 `/detail/v3`)更简,刷新可复现,用户手改 URL 也能切版本。

### 4.5 `switchVersion` 单入口

Sidebar 切版本只改 URL query → `watch([reportId, queryVersionNo])` 统一触发 load。用户手改 URL 和 Sidebar 切版本走同一条路径,避免重复请求和状态漂移。

**注意**:watch source 写成 `[reportId, queryVersionNo]`(ref/computed 数组形式),**不能**写成 `() => [reportId.value, queryVersionNo.value]`(getter 每次返回新数组会导致 watch 无限触发)。本批代码已按正确写法。

### 4.6 Sidebar IntersectionObserver

使用 `rootMargin: '-20% 0px -70% 0px'` + 多档 threshold,让"当前章节"高亮随滚动自然切换,避免跳动。观察对象 id 与 Sidebar 列表 id 严格一致,β/γ 替换 Page SFC 时**必须保持根节点 `<section :id="page-XX">` 结构**,否则锚点失效。

### 4.7 操作按钮:前端状态禁用 + 后端权限兜底

Sidebar 的 5 个写动作按钮按 generation_status / frozen / export_count 做 disable。权限(edit_content / manage)由后端 `ensurePermission` 在接口层兜底,前端**不提前判断用户角色**。原因:角色信息不在 ReportDetailVO 里,需要另一个接口;而且权限策略未来可能变化,后端兜底是唯一真相。

所以用户**无权操作时前端按钮可点**,点击后后端返 403,`request.ts` 拦截器弹 "No permission: xxx" 消息。这是刻意的,不是 bug。

### 4.8 PDF 导出占位

按你的决策 (disabled + tooltip),保留信息架构完整性。真实接口未来在 v2 补。

---

## 5. 开发假设清单(Codex 复审核对)

### 5.1 `@/utils/presale/merge-snapshot.ts` 原样导出 `mergeSnapshot` 和 `VersionRowMeta` ✅
你已确认。

### 5.2 `@/types/presale` barrel 导出 `MergedViewDTO` ✅
`presale/index.ts` 已 `export * from './merged'`,包含 `MergedViewDTO`。

### 5.3 `@element-plus/icons-vue` 里存在本批用到的图标
本批用:`Loading / DocumentAdd / Lock / Unlock / Refresh / Delete / Download`。
均为 Element Plus 标准图标,应已安装。若 `Unlock` 实际不在(某些版本叫 `Unlock` / `Open` / `UnLock`),改一行即可。

### 5.4 路由补丁合入位置正确
见 §3。如果仓库现有 admin.ts 的 presale 子路由已**不按 P1·F·1·a 路由片段的命名/meta 风格**(比如已经有人按 `permissions: [...]` 规范重写过),请合入时把 meta 字段对齐仓库现状,不要硬塞我这份 meta。

### 5.5 `vue-tsc` 能推断 Detail 的降级视图 `as unknown as MergedViewDTO`
使用双 cast 是刻意的(避开 TS 结构严格检查)。如果项目的 `tsconfig.json` 开了 `--noImplicitAny` 或其他严格模式,cast 本身仍合法,vue-tsc 不会报错。

---

## 6. 验证步骤

### 6.1 合入
```
# 覆盖/新增文件
cp -r geo-web/src/composables/presale/ → 仓库
cp -r geo-web/src/views/admin/presale/report/PresaleReportDetail.vue → 仓库
cp -r geo-web/src/views/admin/presale/report/detail/ → 仓库

# 路由合入(看 admin.ts.PATCH),完事后删掉 PATCH
rm geo-web/src/router/admin.ts.PATCH
```

### 6.2 构建
```
cd geo-web
npm run build        # 含 vue-tsc -b
```

期望:无报错。如果 `@/types/presale` 的 `MergedViewDTO` import 失败,检查 §5.2。

### 6.3 dev 跑页面

前置:后端 P1·F·1·b·1 已合入(b·1 r4),至少有一个 `generation_status=DONE` 的报告版本可用。

| 步骤 | 期望 |
|---|---|
| 从列表页点"查看" | 跳 `/admin/presale/report/:id/detail` |
| 页面加载 | 看到 Sidebar(品牌名、版本号、5 按钮、18 锚点) + Viewer(18 个 A4 占位页) |
| 点锚点 | 页面平滑滚动到对应位置,Sidebar 对应条目高亮 |
| 滚动 | Sidebar 高亮随视口中的章节切换 |
| 点"派生新版本" | 弹确认 → 成功 → ElMessage → URL 变 `?versionNo=N+1` → 自动加载新版 |
| 点"冻结" | 弹确认 → 成功 → Sidebar 冻结徽标出现 → 按钮变"解冻" |
| 点"返回列表" | 跳列表页 |
| URL 手改 `?versionNo=1` | 重新加载 v1 |
| 删除当前(无导出版本) | 跳列表页 |
| 删除有导出的版本 | 后端返 409,弹消息"版本已导出,不可删除" |

### 6.4 未 DONE 版本提示
找一个 `generation_status=QUEUED/RUNNING/FAILED` 的版本 URL:
- 期望显示"当前版本状态为 XXX,报告内容尚未就绪" + "查看生成进度"按钮

---

## 7. 给 β/γ 的前置规范

### 7.1 Page SFC 根节点结构(必须遵守)

```vue
<template>
  <section :id="anchorId" class="page-anchor">
    <div :class="['page', { cover: isCover }]">
      <!-- 业务内容 -->
    </div>
  </section>
</template>
```

- `section:id` 必须是 `page-01` ~ `page-18`(与 DetailSidebar PAGE_ANCHORS 一致),否则锚点失效
- `div.page[.cover]` 必须保留,这是 A4 版式容器
- 若某一页完全不需要 `.page` 样式(例如分节分隔页),目前没有这种场景,β/γ 暂不考虑

### 7.2 数据消费

```vue
<script setup lang="ts">
import { useMergedView } from '@/composables/presale/useMergedView'
const { mergedView } = useMergedView()
// mergedView.value 保证 generation_status === 'DONE'(由 Viewer 上层 v-if 守卫)
// 直接用 mergedView.value!.test_summary / .scores / .merged_findings
</script>
```

### 7.3 ECharts

原型 5 个图:P04 radar / P06 platform-bar / P07 competitor-comparison / P09 sentiment / P15 roi-chart。

β/γ 各自 `<script setup>` 里 `onMounted → echarts.init(refEl.value) → setOption`,`onBeforeUnmount` 里 `chart.dispose()`。**不引用全局 DOM id**(原型用了 `document.getElementById`,β/γ 改用 `ref` + `template ref`)。

### 7.4 CSS 变量全局化(β 起手做一次)

PagePlaceholder 硬编码了色值(`#d97706 / #6b6456 / #fefcf7` 等),α·2 刻意不引全局 CSS。β 起手建议抽一份:
```
geo-web/src/assets/presale/report-vars.css
```
内含原型 `:root { --ink / --paper / --accent / ... }` 全套,在 `main.ts` 或 Detail 顶层 `<style>` `@import`。这样 β/γ 的 Page SFC 可用 `var(--accent)` 等语义化色,避免 18 页 × N 处色值散落。

### 7.5 字体

原型用 Google Fonts CDN 在线加载 Playfair Display / Noto Serif SC / Noto Sans SC / JetBrains Mono。生产不能 CDN,按交接文档 P1F-1b-handoff.md 里的定稿:
```
geo-web/src/assets/fonts/ woff2 子集化
```
β 起手处理字体本地化,使用 `@font-face` 声明。

---

## 8. 已知不做(刻意 deferred)

| 项 | 原因 | 归属 |
|---|---|---|
| 版本下拉真实可用 | 后端缺 "versions 列表" 接口;Sidebar 已占位 disable | 需后端补接口后开 |
| PDF 导出 | v1 不开放(本批为占位按钮) | v2 或单独开 |
| 按钮级权限过滤 | 前端不主动判角色,后端 ensurePermission 兜底 | 策略级决策,不改 |
| toViewModel 派生层 | 本批已决策不做,按需再抽 | β/γ 如遇再做 |
| 18 页具体内容 | 骨架已立 | β/γ |
| 字体本地化 | α·2 保留 Google Fonts CDN 占位(样式降级可接受) | β 起手 |
| CSS 变量全局化 | α·2 占位硬编码 | β 起手 |
| Detail 页内编辑 | 按定稿条款:v1 不做实时预览,编辑走独立 `/edit` 页 | b·2·δ 编辑页 |

---

## 9. 给 Codex 的复审 checklist

### 必须通过
- [ ] `npm run build` 通过(含 `vue-tsc -b`)
- [ ] 路由 PATCH 合入 `admin.ts`,PATCH 文件已删
- [ ] dev 启动后,能从列表页跳到详情页看到 18 页 A4 占位
- [ ] 锚点点击 + 滚动高亮正常
- [ ] 5 个写动作(derive / freeze / unfreeze / delete / retry)能走通(权限充足时)

### 建议扫一眼
- [ ] Sidebar `PAGE_ANCHORS` 18 条与 Viewer `PAGES` 18 条一一对应(id / num / title 顺序一致)
- [ ] `useMergedView` 在 Detail 之外的子孙组件里能正确 inject(Sidebar / Viewer / PagePlaceholder 都没用,但 PagePlaceholder 没有调 useMergedView;β/γ 的 Page SFC 会调)
- [ ] watch source 使用 `[reportId, queryVersionNo]`(ref/computed 数组),不是 getter 返回新数组
- [ ] `buildMetaOnlyView` 的 `as unknown as MergedViewDTO` cast 在 vue-tsc 下不报错

### 确认未修改
- [ ] α·1 的 `presaleReport.ts` / `unwrap.ts`
- [ ] P1·F·1·a 的 List / Create / Progress 三个 Vue
- [ ] `@/types/presale/*`
- [ ] `@/utils/presale/merge-snapshot.ts`
