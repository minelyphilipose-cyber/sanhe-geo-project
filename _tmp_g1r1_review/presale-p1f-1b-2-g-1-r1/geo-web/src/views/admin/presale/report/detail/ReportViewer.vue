<template>
  <main class="report-viewer ps-page-scope">
    <!-- 降级警示条(若 L1 benchmarks_frozen.match_level 非 EXACT) -->
    <el-alert
      v-if="isDone && matchLevel && matchLevel !== 'EXACT'"
      type="warning"
      :closable="false"
      show-icon
      class="viewer-alert"
    >
      <template #title>
        行业基线匹配级别为 {{ matchLevel }},部分对比数据使用回退基线。
      </template>
    </el-alert>

    <el-alert
      v-if="isDone && degradedPlatforms.length > 0"
      type="warning"
      :closable="false"
      show-icon
      class="viewer-alert"
    >
      <template #title>
        以下 {{ degradedPlatforms.length }} 个平台在本次生成中降级:{{ degradedPlatforms.join('、') }}
      </template>
    </el-alert>

    <!-- ═══ 18 页渲染 ═══ -->
    <!--
      β·1 阶段:Page01(封面)和 Page02(诊断对象)用真实 SFC。
      β·2 阶段:Page03(执行摘要)、Page04(可见度评分,radar)、
               Page06(平台详细数据,bar) 真实 SFC;
               Page05(多平台热力图 5×8)**保持占位**,
               等后端补 platform_intent_breakdown 交叉数据契约。
      β·3 阶段:Page07(竞品对标总览,bar)、Page08(竞品场景差异)、
               Page09(情感倾向,doughnut) 真实 SFC。
      γ·1 阶段:Page10(覆盖度总览)、Page11(覆盖度详情)、
               Page12/P13/P14(优化机会 三 priority) 真实 SFC;
               引入 shared/FindingCard.vue 组件。
      γ·2 将补 Page15~Page18(4 页)。
      每页的外层结构约定:<section :id="page-XX"><div class="page [cover]">...</div></section>,
      Sidebar 锚点依赖此 id。
    -->
    <template v-if="isDone && mergedView">
      <!-- P01 封面(β·1) -->
      <Page01Cover />

      <!-- P02 诊断对象(β·1) -->
      <Page02Target />

      <!-- P03 执行摘要(β·2) -->
      <Page03ExecutiveSummary />

      <!-- P04 可见度评分详情(β·2,radar chart) -->
      <Page04Scores />

      <!-- P05 多平台热力图 —— 占位,等后端 platform_intent_breakdown 契约 -->
      <PagePlaceholder
        anchor-id="page-05"
        page-num="05"
        page-title="多平台热力图(待后端补 platform_intent_breakdown 契约)"
        :cover="false"
      />

      <!-- P06 平台详细数据(β·2,bar chart) -->
      <Page06PlatformDetail />

      <!-- P07 竞品对标总览(β·3,bar chart) -->
      <Page07CompetitorOverview />

      <!-- P08 竞品场景差异(β·3) -->
      <Page08CompetitorScene />

      <!-- P09 情感倾向(β·3,doughnut chart) -->
      <Page09Sentiment />

      <!-- P10 覆盖度总览(γ·1) -->
      <Page10CoverageOverview />

      <!-- P11 覆盖度详情(γ·1) -->
      <Page11CoverageDetail />

      <!-- P12 优化机会 · 高优先级(γ·1,含 TOTAL banner) -->
      <Page12FindingsHigh />

      <!-- P13 优化机会 · 中优先级(γ·1) -->
      <Page13FindingsMid />

      <!-- P14 优化机会 · 建议关注(γ·1,含 CATEGORY BREAKDOWN) -->
      <Page14FindingsLow />

      <!-- P15~P18 占位(γ·2 将逐步替换) -->
      <PagePlaceholder
        v-for="p in PLACEHOLDER_PAGES"
        :key="p.id"
        :anchor-id="p.id"
        :page-num="p.num"
        :page-title="p.title"
        :cover="p.cover"
      />
    </template>

    <!-- 非 DONE 理论上不会到这里(Detail 顶层已拦截),留兜底占位 -->
    <div v-else class="viewer-empty">
      <el-empty description="报告数据未就绪" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import PagePlaceholder from './PagePlaceholder.vue'
import Page01Cover from './Page01Cover.vue'
import Page02Target from './Page02Target.vue'
import Page03ExecutiveSummary from './Page03ExecutiveSummary.vue'
import Page04Scores from './Page04Scores.vue'
import Page06PlatformDetail from './Page06PlatformDetail.vue'
import Page07CompetitorOverview from './Page07CompetitorOverview.vue'
import Page08CompetitorScene from './Page08CompetitorScene.vue'
import Page09Sentiment from './Page09Sentiment.vue'
import Page10CoverageOverview from './Page10CoverageOverview.vue'
import Page11CoverageDetail from './Page11CoverageDetail.vue'
import Page12FindingsHigh from './Page12FindingsHigh.vue'
import Page13FindingsMid from './Page13FindingsMid.vue'
import Page14FindingsLow from './Page14FindingsLow.vue'

const { mergedView } = useMergedView()

const meta = computed(() => mergedView.value?.meta)
const isDone = computed(() => meta.value?.generation_status === 'DONE')
const matchLevel = computed(() => meta.value?.match_level)
const degradedPlatforms = computed(() => meta.value?.degraded_platforms ?? [])

/**
 * P15~P18 占位规格(与 DetailSidebar.PAGE_ANCHORS 的 P15~P18 一一对应)。
 *
 * 注:P05 虽未实现,但不放在此数组里,因为它有特殊的 pageTitle(标注"待后端补契约"),
 *     统一在 template 里单独占位(见 Viewer template 中 P05 那块)。
 *
 * β·1:P01/P02 用真实 SFC,P03~P18 占位
 * β·2:P03/P04/P06 用真实 SFC,P05 单独占位(带后端需求标注),P07~P18 仍占位
 * β·3:P07/P08/P09 用真实 SFC,P10~P18 仍占位
 * γ·1:P10/P11/P12/P13/P14 用真实 SFC,P15~P18 仍占位
 * γ·2:将 P15~P18 替换为真实 SFC,届时数组清空,PagePlaceholder import 亦可删除
 *      (但 P05 占位可能仍保留,取决于后端契约是否补上)
 */
const PLACEHOLDER_PAGES = [
  { id: 'page-15', num: '15', title: '预期收益', cover: false },
  { id: 'page-16', num: '16', title: '分阶段路径', cover: false },
  { id: 'page-17', num: '17', title: '关键发现总结', cover: false },
  { id: 'page-18', num: '18', title: '关于我们', cover: true }
] as const
</script>

<style scoped>
.report-viewer {
  padding: 24px;
  min-height: 100vh;
  /* 背景沿用原型的深灰纸外框 */
  background: #2d2a26;
}

.viewer-alert {
  max-width: 794px;
  margin: 0 auto 16px auto;
}

.viewer-empty {
  padding: 120px 0;
  color: #d4cfc2;
}
</style>
