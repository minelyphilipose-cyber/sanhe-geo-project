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

    <!-- ═══ 19 页渲染 ═══ -->
    <!--
      β·1 阶段:Page01(封面)和 Page02(诊断对象)用真实 SFC。
      PR1:Page03(AI 搜索新战场)先落占位,PR2 接入真实 L3 可编辑内容。
      β·2 阶段:Page04(执行摘要)、Page05(可见度评分,radar)、
               Page07(平台详细数据,bar) 真实 SFC;
               Page06(多平台热力图)保持占位,等后端补 platform_intent_breakdown 契约。
      β·2·补:Page06 真实 SFC 落地(5×N CSS grid 热力图),历史报告走降级态。
      β·3 阶段:Page08(竞品对标总览,bar)、Page09(竞品场景差异)、
               Page10(情感倾向,doughnut) 真实 SFC。
      γ·1 阶段:Page11/P12(覆盖度)、Page13/P14/Page15(优化机会 三 priority) 真实 SFC;
               引入 shared/FindingCard.vue 组件。
      γ·2 阶段:Page16(预期收益,line chart,B-无③ 版)、Page17(分阶段路径)、
               Page18(关键发现总结)、Page19(关于我们 封底) 真实 SFC 落地。
               P16 的 ESTIMATED IMPACT 块按 estimated-impact-spec 会签后 r2 补回。
               19 页全部真实/占位 SFC,不再有 PagePlaceholder 占位。
      每页的外层结构约定:<section :id="page-XX"><div class="page [cover]">...</div></section>,
      Sidebar 锚点依赖此 id。
    -->
    <template v-if="isDone && mergedView">
      <!-- P01 封面(β·1) -->
      <Page01Cover />

      <!-- P02 诊断对象(β·1) -->
      <Page02Target />

      <!-- P03 AI 搜索新战场(PR2,L3 可编辑内容) -->
      <Page03MarketBattleground />

      <!-- P04 执行摘要(β·2) -->
      <Page04ExecutiveSummary />

      <!-- P05 可见度评分详情(β·2,radar chart) -->
      <Page05Scores />

      <!-- P06 多平台热力图(β·2·补,5×N CSS grid 热力图) -->
      <Page06PlatformHeatmap />

      <!-- P07 平台详细数据(β·2,bar chart) -->
      <Page07PlatformDetail />

      <!-- P08 竞品对标总览(β·3,bar chart) -->
      <Page08CompetitorOverview />

      <!-- P09 竞品场景差异(β·3) -->
      <Page09CompetitorScene />

      <!-- P10 情感倾向(β·3,doughnut chart) -->
      <Page10Sentiment />

      <!-- P11 覆盖度总览(γ·1) -->
      <Page11CoverageOverview />

      <!-- P12 覆盖度详情(γ·1) -->
      <Page12CoverageDetail />

      <!-- P13 优化机会 · 高优先级(γ·1,含 TOTAL banner) -->
      <Page13FindingsHigh />

      <!-- P14 优化机会 · 中优先级(γ·1) -->
      <Page14FindingsMid />

      <!-- P15 优化机会 · 建议关注(γ·1,含 CATEGORY BREAKDOWN) -->
      <Page15FindingsLow />

      <!-- P16 预期收益模拟(γ·2,line chart,B-无③ 版) -->
      <Page16ExpectedRoi />

      <!-- P17 分阶段优化路径(γ·2,timeline) -->
      <Page17PhasedRoadmap />

      <!-- P18 关键发现总结(γ·2) -->
      <Page18KeyTakeaways />

      <!-- P19 关于我们 / 封底(γ·2,cover 样式) -->
      <Page19AboutUs />
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
import Page01Cover from './Page01Cover.vue'
import Page02Target from './Page02Target.vue'
import Page03MarketBattleground from './Page03MarketBattleground.vue'
import Page04ExecutiveSummary from './Page04ExecutiveSummary.vue'
import Page05Scores from './Page05Scores.vue'
import Page06PlatformHeatmap from './Page06PlatformHeatmap.vue'
import Page07PlatformDetail from './Page07PlatformDetail.vue'
import Page08CompetitorOverview from './Page08CompetitorOverview.vue'
import Page09CompetitorScene from './Page09CompetitorScene.vue'
import Page10Sentiment from './Page10Sentiment.vue'
import Page11CoverageOverview from './Page11CoverageOverview.vue'
import Page12CoverageDetail from './Page12CoverageDetail.vue'
import Page13FindingsHigh from './Page13FindingsHigh.vue'
import Page14FindingsMid from './Page14FindingsMid.vue'
import Page15FindingsLow from './Page15FindingsLow.vue'
import Page16ExpectedRoi from './Page16ExpectedRoi.vue'
import Page17PhasedRoadmap from './Page17PhasedRoadmap.vue'
import Page18KeyTakeaways from './Page18KeyTakeaways.vue'
import Page19AboutUs from './Page19AboutUs.vue'

const { mergedView } = useMergedView()

const meta = computed(() => mergedView.value?.meta)
const isDone = computed(() => meta.value?.generation_status === 'DONE')
const matchLevel = computed(() => meta.value?.match_level)
const degradedPlatforms = computed(() => meta.value?.degraded_platforms ?? [])
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
