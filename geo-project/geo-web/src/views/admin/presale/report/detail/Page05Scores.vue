<template>
  <section id="page-05" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>04 / 可见度评分</span>
      </div>

      <div class="p05-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">04</span>
          <div>
            <div class="section-label">VISIBILITY SCORE</div>
            <div class="section-heading">AI 可见度评分详情</div>
          </div>
        </div>

        <!-- 左 radar + 右 breakdown 表 -->
        <div class="p05-top-grid">
          <div class="p05-radar-wrap">
            <PresaleChart :option="radarOption" height="340px" />
          </div>

          <div>
            <div class="mono p05-section-label">BREAKDOWN BY DIMENSION</div>
            <div class="data-matrix">
              <div
                v-for="dim in dimensionRows"
                :key="dim.key"
                class="data-matrix-row p05-dim-row"
              >
                <div class="p05-dim-name">{{ dim.label }}</div>
                <div class="p05-dim-score" :class="{ 'p05-dim-score-green': dim.isGreen }">
                  {{ dim.score }}
                </div>
                <div class="p05-dim-avg">均值 {{ dim.avg }}</div>
                <div
                  class="mono p05-dim-delta"
                  :class="dim.delta == null || dim.delta >= 0 ? 'p05-dim-delta-up' : 'p05-dim-delta-down'"
                >
                  {{ dim.delta == null ? '—' : `${dim.delta >= 0 ? '+' : ''}${dim.delta}` }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 行业对比条 -->
        <div class="p05-compare-wrap">
          <div class="mono p05-section-label">INDUSTRY COMPARISON</div>
          <div class="p05-compare-bar">
            <!-- 渐变背景:红→黄→绿 -->
            <!-- 标尺 0 / 50 / 100 -->
            <div class="p05-ruler p05-ruler-0 mono">0</div>
            <div class="p05-ruler p05-ruler-50 mono">50</div>
            <div class="p05-ruler p05-ruler-100 mono">100</div>

            <!-- 行业均值线 -->
            <div class="p05-mark-line p05-mark-avg" :style="{ left: avgLeft }"></div>
            <div class="p05-mark-label p05-mark-avg-label mono" :style="{ left: avgLeft }">
              均值 {{ industryAvgOverall }}
            </div>

            <!-- 当前位置 -->
            <div class="p05-mark-line p05-mark-self" :style="{ left: selfLeft }"></div>
            <div
              class="p05-mark-label p05-mark-self-label"
              :class="selfLabelClasses"
              :style="{ left: selfLeft }"
            >
              您 · {{ overall }}
            </div>

            <!-- Top1 -->
            <div class="p05-mark-line p05-mark-top1" :style="{ left: top1Left }"></div>
            <div class="p05-mark-label p05-mark-top1-label mono" :style="{ left: top1Left }">
              Top1 · {{ top1Overall }}
            </div>
          </div>
        </div>

        <!-- 底部引用块(v1 静态文案,L3 未提供) -->
        <div class="p05-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">05</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMergedView } from '@/composables/presale/useMergedView'
import PresaleChart from './shared/PresaleChart.vue'
import { toIntRounded } from '@/utils/presale/numberFormat'

/**
 * Page05 可见度评分详情。
 *
 * 数据映射:
 *   - 4 维度分数(mention / ranking / sentiment / coverage):scores.{key}
 *   - 行业均值 4 维度:benchmarks_frozen.industry_avg.{key}
 *   - radar chart:自己 vs 行业均值的两条 serie
 *   - 对比条 3 个位置:0-100 线性映射为百分比 left,
 *     当前 overall / industry_avg.overall / top1.overall
 *
 * 刻意限定:
 *   - radar 5 维度?——契约 Scores 只有 4 维度 + overall。overall 不放 radar,
 *     避免"自己和自己比"的视觉噪音。radar 展示 4 维度对标行业。
 *   - 对比条底部引用文案:原型是手写版("覆盖度得分..."),β·2 此处用
 *     静态"数据读自本次诊断"文案,真实语义需要 L3 结合规则引擎产出
 *     (P12-P14 优化机会页才有对应文案契约)。
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const showRadarBaselineGap = computed(
  () => mergedView.value.narrative_profile.display_flags?.show_radar_baseline_gap !== false
)

// ─── 4 维度对比行 ────────────────────────────────────────
interface DimRow {
  key: 'mention' | 'ranking' | 'sentiment' | 'coverage'
  label: string
  score: number | string
  avg: number | string
  delta: number | null
  isGreen: boolean
  radarScore: number | null
  radarAvg: number | null
}

const dimensionRows = computed<DimRow[]>(() => {
  const scores = mergedView.value.scores
  const avg = mergedView.value.benchmarks_frozen.industry_avg
  return [
    {
      key: 'mention',
      label: '提及率',
      score: toIntRounded(scores.mention),
      avg: toIntRounded(avg.mention),
      delta: toIntRounded(scores.mention - avg.mention),
      isGreen: false,
      radarScore: toIntRounded(scores.mention),
      radarAvg: toIntRounded(avg.mention)
    },
    {
      key: 'ranking',
      label: '排名得分',
      score: scores.ranking == null ? '—' : toIntRounded(scores.ranking),
      avg: avg.ranking == null ? '—' : toIntRounded(avg.ranking),
      delta: scores.ranking == null || avg.ranking == null ? null : toIntRounded(scores.ranking - avg.ranking),
      isGreen: false,
      radarScore: scores.ranking == null || avg.ranking == null ? null : toIntRounded(scores.ranking),
      radarAvg: scores.ranking == null || avg.ranking == null ? null : toIntRounded(avg.ranking)
    },
    {
      key: 'sentiment',
      label: '情感倾向',
      score: toIntRounded(scores.sentiment),
      avg: toIntRounded(avg.sentiment),
      delta: toIntRounded(scores.sentiment - avg.sentiment),
      // 情感维度视觉上绿色(对齐原型)
      isGreen: true,
      radarScore: toIntRounded(scores.sentiment),
      radarAvg: toIntRounded(avg.sentiment)
    },
    {
      key: 'coverage',
      label: '覆盖度',
      score: toIntRounded(scores.coverage),
      avg: toIntRounded(avg.coverage),
      delta: toIntRounded(scores.coverage - avg.coverage),
      isGreen: false,
      radarScore: toIntRounded(scores.coverage),
      radarAvg: toIntRounded(avg.coverage)
    }
  ]
})

// ─── radar chart option ─────────────────────────────────
const radarOption = computed<EChartsOption>(() => {
  const rows = dimensionRows.value.filter((d) => d.radarScore != null && d.radarAvg != null)
  const indicator = rows.map((d) => ({ name: d.label, max: 100 }))
  const selfData = rows.map((d) => d.radarScore as number)
  const avgData = rows.map((d) => d.radarAvg as number)
  const legendData = showRadarBaselineGap.value ? ['您的品牌', '行业均值'] : ['您的品牌']
  const seriesData: Array<Record<string, unknown>> = [
    {
      name: '您的品牌',
      value: selfData,
      lineStyle: { color: '#1e3a8a', width: 2 },
      itemStyle: { color: '#1e3a8a' },
      areaStyle: { color: 'rgba(30, 58, 138, 0.15)' }
    }
  ]
  if (showRadarBaselineGap.value) {
    seriesData.push({
      name: '行业均值',
      value: avgData,
      lineStyle: { color: '#d97706', width: 2, type: 'dashed' },
      itemStyle: { color: '#d97706' },
      areaStyle: { color: 'rgba(217, 119, 6, 0.08)' }
    })
  }

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(11, 20, 38, 0.9)',
      borderWidth: 0,
      textStyle: { color: '#fefcf7', fontSize: 12 }
    },
    legend: {
      data: legendData,
      bottom: 0,
      textStyle: { color: '#1a2942', fontSize: 12 }
    },
    radar: {
      indicator,
      shape: 'polygon',
      splitNumber: 4,
      center: ['50%', '45%'],
      radius: '65%',
      axisName: {
        color: '#0b1426',
        fontSize: 12
      },
      splitLine: {
        lineStyle: { color: '#c8bfa8', type: 'solid' }
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(254, 252, 247, 0.6)', 'rgba(247, 243, 234, 0.6)']
        }
      },
      axisLine: {
        lineStyle: { color: '#c8bfa8' }
      }
    },
    series: [
      {
        type: 'radar',
        data: seriesData
      }
    ]
  }
})

// ─── 行业对比条定位 ─────────────────────────────────────
const overall = computed(() => toIntRounded(mergedView.value.scores.overall))
const industryAvgOverall = computed(
  () => toIntRounded(mergedView.value.benchmarks_frozen.industry_avg.overall)
)
const top1Overall = computed(() => toIntRounded(mergedView.value.benchmarks_frozen.top1.overall))

/** 0-100 分数映射为 "XX%" 字符串(CSS left)。边界处夹紧 0-100%。 */
function toLeftPct(score: number): string {
  const clamped = Math.min(100, Math.max(0, score))
  return `${clamped}%`
}
const selfLeft = computed(() => toLeftPct(overall.value))
const avgLeft = computed(() => toLeftPct(industryAvgOverall.value))
const top1Left = computed(() => toLeftPct(top1Overall.value))
const selfLabelClasses = computed(() => ({
  'p05-label-left-edge': overall.value < 20,
  'p05-label-right-edge': overall.value > 80,
  'p05-label-collision': Math.abs(overall.value - industryAvgOverall.value) < 10 ||
    Math.abs(overall.value - top1Overall.value) < 10
}))

// ─── 底部引用文案(静态) ────────────────────────────────
const quoteText = `本次诊断从提及率、排名得分、情感倾向、覆盖度 4 个维度综合评估品牌可见度。建议优先关注表现偏弱的维度,详见后续分析章节。`
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p05-body {
  margin-top: 60px;
}

.p05-top-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  margin-bottom: 40px;
}
.p05-radar-wrap {
  min-height: 340px;
}

.p05-section-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 20px;
}

/* dimension 表行 */
.p05-dim-row {
  grid-template-columns: 1fr 80px 80px 80px;
}
.p05-dim-name {
  font-size: 13px;
  font-weight: 500;
}
.p05-dim-score {
  font-family: 'Playfair Display', serif;
  font-size: 24px;
  font-weight: 700;
  color: #1e3a8a;
}
.p05-dim-score-green {
  color: #047857;
}
.p05-dim-avg {
  font-size: 11px;
  color: #6b6456;
  text-align: right;
}
.p05-dim-delta {
  font-size: 11px;
  text-align: right;
}
.p05-dim-delta-up {
  color: #047857;
}
.p05-dim-delta-down {
  color: #b91c1c;
}

/* 对比条 */
.p05-compare-wrap {
  margin-bottom: 32px;
}
.p05-compare-bar {
  position: relative;
  height: 60px;
  margin-top: 28px;
  background: linear-gradient(90deg, #fee2e2 0%, #fef3c7 40%, #d1fae5 100%);
  border-radius: 4px;
}
.p05-ruler {
  position: absolute;
  bottom: -20px;
  font-size: 10px;
  color: #6b6456;
}
.p05-ruler-0 {
  left: 0;
}
.p05-ruler-50 {
  left: 50%;
  transform: translateX(-50%);
}
.p05-ruler-100 {
  right: 0;
}

.p05-mark-line {
  position: absolute;
  top: -8px;
  bottom: -8px;
  width: 2px;
}
.p05-mark-avg {
  background: #6b6456;
}
.p05-mark-self {
  background: #1e3a8a;
  width: 3px;
  top: -14px;
  bottom: -14px;
}
.p05-mark-top1 {
  background: #d97706;
}

.p05-mark-label {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
}
.p05-mark-avg-label {
  top: -22px;
  font-size: 10px;
  color: #6b6456;
}
.p05-mark-self-label {
  top: -38px;
  font-size: 13px;
  font-weight: 700;
  color: #1e3a8a;
}
.p05-mark-self-label.p05-label-left-edge {
  transform: translateX(0);
  padding-left: 4px;
}
.p05-mark-self-label.p05-label-right-edge {
  transform: translateX(-100%);
  padding-right: 4px;
}
.p05-mark-self-label.p05-label-collision {
  top: -50px;
}
.p05-mark-top1-label {
  top: -22px;
  font-size: 10px;
  color: #d97706;
}

.p05-quote-wrap {
  margin-top: 64px;
}
</style>
