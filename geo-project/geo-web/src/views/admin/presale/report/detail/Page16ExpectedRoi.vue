<template>
  <section id="page-16" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>10 / 预期收益模拟</span>
      </div>

      <div class="p16-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">10</span>
          <div>
            <div class="section-label">{{ pageFrame.eyebrow }}</div>
            <div class="section-heading">{{ pageFrame.heading }}</div>
          </div>
        </div>

        <!-- 核心数字对比:CURRENT → TARGET + UPLIFT 标注 + PLAN -->
        <div class="p16-hero-grid" :class="`p16-frame-${valueFrame}`">
          <div class="p16-hero-card p16-hero-current">
            <div class="mono p16-hero-label">CURRENT</div>
            <div class="p16-hero-value p16-hero-value-muted">{{ currentScoreDisplay }}</div>
            <div class="p16-hero-caption">当前可见度 · 实测</div>
          </div>
          <div class="p16-hero-arrow">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
          <div class="p16-hero-card p16-hero-target">
            <div class="mono p16-hero-label p16-hero-label-dim">TARGET</div>
            <div class="p16-hero-value p16-hero-value-accent">{{ targetScoreRangeDisplay }}</div>
            <div class="p16-hero-caption p16-hero-caption-dim">{{ pageFrame.targetCaption }}</div>
          </div>
          <div class="p16-uplift">
            <div class="mono p16-uplift-label">SCORE UPLIFT</div>
            <div class="display-serif p16-uplift-value">{{ estimatedUpliftRangeDisplay }}</div>
          </div>
          <div class="p16-hero-card p16-hero-gain">
            <div class="mono p16-hero-label">PLAN</div>
            <div class="p16-hero-value p16-hero-value-green">{{ totalPlannedOptimizations }}</div>
            <div class="p16-hero-caption">计划优化项</div>
          </div>
        </div>

        <!-- ROI 曲线图(4 点:起点 + 3 phase) -->
        <PresaleChart :option="roiLineOption" height="280px" class="p16-chart" />

        <div v-if="valueFrame !== 'improve'" class="p16-band-grid" :class="`p16-band-${valueFrame}`">
          <div class="p16-band-card p16-band-card-strength" :class="{ 'p16-band-card-lead': valueFrame === 'defend' }">
            <div class="mono p16-band-label">{{ valueFrame === 'defend' ? 'DEFENSIBLE ASSET' : 'CURRENT STRENGTH' }}</div>
            <div class="chinese-serif p16-band-title">{{ strengthTitle }}</div>
            <ul class="p16-band-list">
              <li v-for="item in strengthItems" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div class="p16-band-card" :class="{ 'p16-band-card-lead': valueFrame === 'consistency' || valueFrame === 'defend' }">
            <div class="mono p16-band-label">CONTESTED SCENES</div>
            <div class="chinese-serif p16-band-title">{{ contestedTitle }}</div>
            <ul class="p16-band-list">
              <li v-for="item in contestedItems" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div class="p16-band-card p16-band-card-monitor" :class="{ 'p16-band-card-lead': valueFrame === 'defend' }">
            <div class="mono p16-band-label">ONGOING VALUE</div>
            <div class="chinese-serif p16-band-title">{{ monitorTitle }}</div>
            <p>{{ monitorCopy }}</p>
          </div>
        </div>

        <!--
          ③ OPPORTUNITY 块。
          4 个格子全部来自真实现状或计划优化项,不展示凭空业务结果百分比。
        -->
        <div class="p16-impact" :class="`p16-impact-${valueFrame}`">
          <div class="mono p16-impact-label">{{ opportunityLabel }}</div>
          <div class="p16-impact-grid">
            <div class="p16-impact-item">
              <div class="p16-impact-caption">推荐型高价值场景缺席</div>
              <div class="p16-impact-value">
                <span class="mono p16-impact-danger">{{ recommendationAbsenceDisplay }}</span>
              </div>
            </div>

            <div class="p16-impact-item">
              <div class="p16-impact-caption">高价值问题你已覆盖</div>
              <div class="p16-impact-value">
                <span class="mono">{{ highValueCoverage.before }}</span>
                <span class="p16-impact-arrow">→</span>
                <span class="mono p16-impact-after">{{ highValueCoverage.after }}</span>
              </div>
            </div>

            <div class="p16-impact-item">
              <div class="p16-impact-caption">{{ recommendationMetricCaption }}</div>
              <div class="p16-impact-value">
                <span class="mono" :class="valueFrame === 'improve' ? 'p16-impact-danger' : 'p16-impact-after'">
                  {{ recommendationMetricDisplay }}
                </span>
              </div>
            </div>

            <div class="p16-impact-item">
              <div class="p16-impact-caption">本轮计划优化项</div>
              <div class="p16-impact-value">
                <span class="mono">{{ priorityPlanDisplay }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段摘要条(3 个 phase title + duration 并排,呼应曲线 X 轴) -->
        <div class="p16-phase-strip" :class="`p16-phase-${valueFrame}`">
          <div
            v-for="(p, idx) in phaseTeasers"
            :key="p.phase_no"
            class="p16-phase-item"
            :class="{ 'p16-phase-item-final': idx === phaseTeasers.length - 1 }"
          >
            <div class="mono p16-phase-duration">{{ p.duration_label }}</div>
            <div class="chinese-serif p16-phase-title">{{ p.title }}</div>
            <div class="mono p16-phase-target">→ {{ formatScoreRange(p.target_score_low, p.target_score_high, p.target_score) }}</div>
          </div>
        </div>

        <div v-if="caseStudyText" class="p16-case">
          <div class="mono p16-case-label">VERIFIED CASE · 实测案例</div>
          <div class="p16-case-text">{{ caseStudyText }}</div>
        </div>

        <!-- ROI disclaimer -->
        <div class="p16-disclaimer">{{ mergedView.roi_disclaimer }}</div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">16</div>
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
 * Page16 预期收益模拟。
 *
 * 实现策略:
 *   - ① 核心卡片:current / target range / score uplift / planned items
 *   - ② 目标轨迹:起点 M0 + 3 phase 的 target_score_low/high 区间带
 *   - ③ 真实缺口与计划:只展示实测现状或计划项,不展示凭空业务结果百分比
 *   - ④ 3 个 phase 摘要条(duration_label + title + target_score range)
 *   - ⑤ ROI disclaimer
 *
 * 数据映射:
 *   - mergedView.roi_simulation.{current_score, target_score_low/high,
 *     estimated_uplift_percent_low/high, phases[]}
 *   - mergedView.merged_phases[].{title, phase.duration_label, phase.target_score_low/high}
 *   - mergedView.scene_coverage.high_value.{covered, total}
 *   - mergedView.scene_competitor_pressure.{hv_reco_total,items[]}
 *   - mergedView.optimization_findings[]
 *   - mergedView.roi_disclaimer
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const roi = computed(() => mergedView.value.roi_simulation)

type ValueFrame = 'improve' | 'consistency' | 'defend'

const narrativeBand = computed(() => mergedView.value.narrative_profile?.band ?? 'MIDDLE')
const valueFrame = computed<ValueFrame>(() => {
  if (narrativeBand.value === 'INVISIBLE' || narrativeBand.value === 'BEHIND') return 'improve'
  if (narrativeBand.value === 'STRONG' || narrativeBand.value === 'LEADER') return 'defend'
  return 'consistency'
})

const pageFrame = computed(() => {
  if (valueFrame.value === 'defend') {
    return {
      eyebrow: 'DEFENSE VALUE',
      heading: '守位与持续价值',
      targetCaption: '巩固区间 · 非保证'
    }
  }
  if (valueFrame.value === 'consistency') {
    return {
      eyebrow: 'CONSISTENCY VALUE',
      heading: '一致性提升路径',
      targetCaption: '一致性目标 · 非保证'
    }
  }
  return {
    eyebrow: 'EXPECTED ROI',
    heading: '预期收益模拟',
    targetCaption: '改进目标 · 非保证'
  }
})
// ─── 核心数字 ────────────────────────────────────────────

const currentScoreDisplay = computed(() => toIntRounded(roi.value.current_score))

function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

const targetScoreLow = computed(() => roi.value.target_score_low ?? roi.value.target_score)
const targetScoreHigh = computed(() => roi.value.target_score_high ?? roi.value.target_score)
const targetScoreRangeDisplay = computed(() =>
  formatScoreRange(targetScoreLow.value, targetScoreHigh.value, roi.value.target_score, false)
)
const estimatedUpliftRangeDisplay = computed(() => {
  const low = roi.value.estimated_uplift_percent_low ?? roi.value.estimated_uplift_percent
  const high = roi.value.estimated_uplift_percent_high ?? roi.value.estimated_uplift_percent
  if (!isFiniteNumber(low) || !isFiniteNumber(high)) return '—'
  const lowRounded = toIntRounded(low)
  const highRounded = toIntRounded(high)
  if (lowRounded === highRounded) return `+${lowRounded}%`
  return `+${lowRounded}%~+${highRounded}%`
})

const totalPlannedOptimizations = computed(() =>
  roi.value.phases.reduce((sum, phase) =>
    sum + (phase.planned_optimization_count ?? phase.total_optimization_count ?? 0), 0)
)

// ─── ③ 真实缺口与计划 ────────────────────────────────────

interface ImpactCell {
  before: string
  after: string
}

const highValueRelevantRuleCodes = new Set([
  'RULE_COVERAGE_LOW_RECOMMEND',
  'RULE_RECOMMENDATION_ABSENT',
  'RULE_SCENE_MISS_HIGH_VALUE',
  'RULE_COMPETITOR_PRESENT_CLIENT_ABSENT',
  'RULE_NATURAL_RECO_WEAK_BRAND_KNOWN',
  'RULE_HIGH_VALUE_RECO_GAP'
])

const plannedHighValueCoverageGain = computed(() => {
  return mergedView.value.merged_findings.filter((item) =>
    highValueRelevantRuleCodes.has(item.finding.rule_code)
  ).length
})

/** 高价值查询场景覆盖:现状实测,目标按相关计划项保守增加,不写死 100%。 */
const highValueCoverage = computed<ImpactCell>(() => {
  const hv = mergedView.value.scene_coverage.high_value
  const targetCovered = Math.min(hv.total, hv.covered + plannedHighValueCoverageGain.value)
  const after = targetCovered === hv.total && hv.covered < hv.total && plannedHighValueCoverageGain.value > 0
    ? `${Math.max(hv.covered, targetCovered - 1)}-${targetCovered} / ${hv.total}`
    : `${targetCovered} / ${hv.total}`
  return {
    before: `${hv.covered} / ${hv.total}`,
    after
  }
})

const recommendationAbsenceCount = computed(() => {
  return (mergedView.value.scene_competitor_pressure.items ?? [])
    .filter((item) => item.target_mentioned_platform_count <= 0).length
})

const recommendationAbsenceDisplay = computed(() => {
  const total = mergedView.value.scene_competitor_pressure.hv_reco_total ?? 0
  return `${recommendationAbsenceCount.value} / ${total}`
})

const primaryRecommendationCount = computed(() => {
  return (mergedView.value.scene_competitor_pressure.items ?? []).reduce(
    (sum, item) => sum + Math.max(0, item.target_mentioned_platform_count ?? 0), 0
  )
})

const primaryRecommendationDisplay = computed(() => {
  return `${primaryRecommendationCount.value} 次`
})

const maxRecommendationDepthGap = computed(() => {
  return Math.max(0, ...((mergedView.value.scene_competitor_pressure.items ?? []).map((item) => {
    const maxCompetitorPresence = Math.max(0, ...((item.competitors ?? []).map((c) => c.mentioned_platform_count ?? 0)))
    return maxCompetitorPresence - Math.max(0, item.target_mentioned_platform_count ?? 0)
  })))
})

const recommendationMetricCaption = computed(() => {
  return valueFrame.value === 'improve' ? 'AI 主动推荐现状' : '推荐平台深度差距'
})

const recommendationMetricDisplay = computed(() => {
  if (valueFrame.value === 'improve') return primaryRecommendationDisplay.value
  return maxRecommendationDepthGap.value > 0 ? `最大差 ${maxRecommendationDepthGap.value} 平台` : '暂无明显差距'
})

const priorityPlanDisplay = computed(() => {
  const phases = roi.value.phases
  const p1 = phases[0]?.planned_optimization_count ?? phases[0]?.total_optimization_count ?? 0
  const p2 = phases[1]?.planned_optimization_count ?? phases[1]?.total_optimization_count ?? 0
  const p3 = phases[2]?.ongoing_action_count ?? 3
  return `P1 ${p1}项整改 · P2 ${p2}项整改 · P3 ${p3}项持续动作`
})

const opportunityLabel = computed(() => {
  if (valueFrame.value === 'defend') return 'EDGE OPPORTUNITY · 边际机会与守位计划'
  if (valueFrame.value === 'consistency') return 'CONSISTENCY GAP · 一致性缺口与计划'
  return 'REAL GAP & PLAN · 真实缺口与计划'
})

const strengthTitle = computed(() => {
  if (valueFrame.value === 'defend') return '你的可见度资产已经形成,订阅价值在于持续守住'
  return '你已在部分关键位置出现,下一步要把平台深度补满'
})

const strengthItems = computed(() => {
  const hv = mergedView.value.scene_coverage.high_value
  const mid = mergedView.value.scene_coverage.mid_value
  const low = mergedView.value.scene_coverage.low_value
  const strongestPlatform = [...mergedView.value.platform_breakdown]
    .filter((item) => !item.is_degraded)
    .sort((a, b) => (b.mention_count ?? 0) - (a.mention_count ?? 0))[0]

  const items: string[] = [
    valueFrame.value === 'defend'
      ? `高价值场景已覆盖 ${hv.covered}/${hv.total}`
      : `中低价值场景已覆盖 ${mid.covered + low.covered}/${mid.total + low.total}`
  ]
  if (primaryRecommendationCount.value > 0) {
    items.push(`推荐型高价值场景中累计被主动提及 ${primaryRecommendationCount.value} 次`)
  }
  if (strongestPlatform && strongestPlatform.mention_count > 0) {
    items.push(`${strongestPlatform.platform_name} 已出现 ${strongestPlatform.mention_count} 次品牌提及`)
  }
  return items
})

function shortQuery(query: string): string {
  const compact = query.replace(/\s+/g, '')
  return compact.length > 28 ? `${compact.slice(0, 28)}...` : compact
}

const contestedSceneItems = computed(() => {
  return (mergedView.value.scene_competitor_pressure.items ?? [])
    .filter((item) => {
      const clientAbsent = item.target_mentioned_platform_count <= 0
      const maxCompetitorPresence = Math.max(0, ...((item.competitors ?? []).map((c) => c.mentioned_platform_count ?? 0)))
      return clientAbsent || maxCompetitorPresence > item.target_mentioned_platform_count
    })
})

const contestedTitle = computed(() => {
  const count = contestedSceneItems.value.length
  if (valueFrame.value === 'defend') {
    return count > 0 ? '仍有少数高价值场景值得继续拿下' : '当前争夺场景较少,重点转向持续监测'
  }
  return count > 0 ? '你已经出现,但部分场景的平台占位还不够满' : '暂未发现明显争夺场景,优先补齐平台深度'
})

const contestedItems = computed(() => {
  if (contestedSceneItems.value.length === 0) {
    return ['未检出明确的竞品在场缺口,后续重点监测 AI 回答变化与竞品新增动作']
  }
  return contestedSceneItems.value.slice(0, valueFrame.value === 'defend' ? 3 : 2).map((item) => {
    const topCompetitor = [...(item.competitors ?? [])]
      .sort((a, b) => (b.mentioned_platform_count ?? 0) - (a.mentioned_platform_count ?? 0))[0]
    const competitorText = topCompetitor && topCompetitor.mentioned_platform_count > 0
      ? `${topCompetitor.name} ${topCompetitor.mentioned_platform_count} 平台在场`
      : '竞品已有出现'
    const clientText = item.target_mentioned_platform_count <= 0
      ? '你未出现'
      : `你出现 ${item.target_mentioned_platform_count} 平台`
    return `${shortQuery(item.query)} · ${competitorText},${clientText}`
  })
})

const monitorTitle = computed(() => {
  if (valueFrame.value === 'defend') return '订阅价值从一次提升,转为持续守位'
  if (valueFrame.value === 'consistency') return '用持续监测把部分平台出现补成多平台稳定出现'
  return '优化后仍需持续跟踪 AI 平台变化'
})

const monitorCopy = computed(() => {
  if (valueFrame.value === 'defend') {
    return 'AI 回答与排序不是永久固定的结果。订阅期内持续跟踪关键场景、竞品在场与平台变化,可以在优势松动时更早发现并调整。'
  }
  if (valueFrame.value === 'consistency') {
    return '当前价值不只在提分,还在于把已出现的场景做深到更多平台,并及时发现哪些平台又被竞品抢先占位。'
  }
  return '当缺口被补齐后,仍需要观察 AI 平台是否改变回答偏好,避免优化效果只停留在一次报告里。'
})

const caseStudyText = computed(() => {
  const item = roi.value.case_study_range
  if (!item || !isFiniteNumber(item.before_score) || !isFiniteNumber(item.after_score_low) || !isFiniteNumber(item.after_score_high)) {
    return ''
  }
  const label = item.label ? `${item.label}:` : ''
  const range = formatScoreRange(item.after_score_low, item.after_score_high, null, true)
  const source = item.source ? ` 数据来源:${item.source}` : ''
  const period = item.sample_period ? ` 周期:${item.sample_period}` : ''
  return `${label}从 ${toIntRounded(item.before_score)} 分提升至 ${range}.${source}${period}`
})

// ─── Phase 摘要条 ────────────────────────────────────────

interface PhaseTeaser {
  phase_no: 1 | 2 | 3
  duration_label: string
  title: string
  target_score: number
  target_score_low?: number | null
  target_score_high?: number | null
}

const phaseTeasers = computed<PhaseTeaser[]>(() => {
  return mergedView.value.merged_phases.map((mp) => ({
    phase_no: mp.phase.phase_no,
    duration_label: mp.phase.duration_label,
    title: mp.title,
    target_score: mp.phase.target_score,
    target_score_low: mp.phase.target_score_low,
    target_score_high: mp.phase.target_score_high
  }))
})


function formatScoreRange(
  low: number | null | undefined,
  high: number | null | undefined,
  fallback: number | null | undefined,
  withUnit = true
): string {
  const safeLow = isFiniteNumber(low) ? low : fallback
  const safeHigh = isFiniteNumber(high) ? high : fallback
  if (!isFiniteNumber(safeLow) || !isFiniteNumber(safeHigh)) return '—'
  const lowRounded = toIntRounded(safeLow)
  const highRounded = toIntRounded(safeHigh)
  const body = lowRounded === highRounded ? `${lowRounded}` : `${lowRounded}-${highRounded}`
  return withUnit ? `${body} 分` : body
}

// ─── ROI Line Chart(4 点) ───────────────────────────────

const roiLineOption = computed<EChartsOption>(() => {
  const phases = mergedView.value.merged_phases
  // X 轴类目:"当前" + 3 phase 的 duration_label
  const xCategories = ['当前', ...phases.map((p) => p.phase.duration_label)]
  const lowValues = [
    toIntRounded(roi.value.current_score),
    ...phases.map((p) => toIntRounded(p.phase.target_score_low ?? p.phase.target_score))
  ]
  const highValues = [
    toIntRounded(roi.value.current_score),
    ...phases.map((p) => toIntRounded(p.phase.target_score_high ?? p.phase.target_score))
  ]
  const bandValues = highValues.map((high, idx) => Math.max(0, high - lowValues[idx]))
  const midValues = [
    toIntRounded(roi.value.current_score),
    ...phases.map((p) => toIntRounded(p.phase.target_score))
  ]

  return {
    grid: {
      left: 48,
      right: 24,
      top: 32,
      bottom: 40,
      containLabel: false
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      backgroundColor: 'rgba(11, 20, 38, 0.9)',
      borderWidth: 0,
      confine: true,
      extraCssText: 'line-height:1.7;max-width:260px;white-space:normal;z-index:9999;',
      textStyle: { color: '#fefcf7', fontSize: 12 },
      formatter: (params) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        const p = params[0]
        const idx = p.dataIndex as number
        if (idx === 0) {
          return `<div>${p.name}</div><div>当前分值:${toIntRounded(Number(p.value))}</div>`
        }
        const mp = phases[idx - 1]
        if (!mp) return `<div>${p.name}</div><div>${toIntRounded(Number(p.value))}</div>`
        const planned = mp.phase.planned_optimization_count ?? mp.phase.total_optimization_count ?? 0
        const ongoing = mp.phase.ongoing_action_count ?? 3
        const range = formatScoreRange(mp.phase.target_score_low, mp.phase.target_score_high, mp.phase.target_score)
        const uplift = formatScoreRange(mp.phase.uplift_from_previous_low, mp.phase.uplift_from_previous_high, mp.phase.uplift_from_previous)
        return [
          `<div>${p.name} · ${mp.title}</div>`,
          `<div>目标区间:${range}</div>`,
          `<div>${planned > 0 ? `较上阶段提升:${uplift}` : mp.phase.phase_no === 3 ? '分数变化:以复测结果为准' : '较上阶段提升:不报分'}</div>`,
          `<div>${mp.phase.phase_no === 3 ? `本阶段持续动作:${ongoing} 项` : `本阶段计划优化项:${planned} 项`}</div>`
        ].join('')
      }
    },
    xAxis: {
      type: 'category',
      data: xCategories,
      boundaryGap: false,
      axisLabel: {
        color: '#0b1426',
        fontSize: 11,
        fontWeight: 500
      },
      axisLine: { lineStyle: { color: '#c8bfa8' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: {
        color: '#6b6456',
        fontSize: 10,
        formatter: '{value}'
      },
      splitLine: {
        lineStyle: { color: '#c8bfa8', type: 'dashed' }
      },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'line',
        data: lowValues,
        stack: 'targetRange',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0 },
        emphasis: { disabled: true },
        tooltip: { show: false }
      },
      {
        type: 'line',
        data: bandValues,
        stack: 'targetRange',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: {
          color: 'rgba(30, 58, 138, 0.16)'
        },
        emphasis: { disabled: true },
        tooltip: { show: false }
      },
      {
        type: 'line',
        data: midValues,
        smooth: 0.2,
        symbol: 'circle',
        symbolSize: (_value, params) => (params.dataIndex === 0 ? 10 : 12),
        lineStyle: {
          width: 2.5,
          color: '#1e3a8a'
        },
        itemStyle: {
          color: (params) => {
            // 起点(当前)用 muted 灰,phase1-2 用 primary 蓝,phase3(终点)用 accent 橙
            const idx = params.dataIndex
            if (idx === 0) return '#6b6456'
            if (idx === midValues.length - 1) return '#d97706'
            return '#1e3a8a'
          },
          borderWidth: 2,
          borderColor: '#fefcf7'
        },
        label: {
          show: true,
          position: 'top',
          color: '#0b1426',
          fontSize: 11,
          fontWeight: 600,
          formatter: (_params) => {
            const idx = _params.dataIndex
            if (idx === 0) return String(toIntRounded(roi.value.current_score))
            const phase = phases[idx - 1]?.phase
            return phase ? formatScoreRange(phase.target_score_low, phase.target_score_high, phase.target_score, false) : '{c}'
          }
        }
      }
    ]
  }
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p16-body {
  margin-top: 60px;
}

/* ─── 核心数字 hero 区 ────────────────────────────────── */

.p16-hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px minmax(0, 1fr) minmax(144px, 0.65fr) minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  margin-top: 24px;
  margin-bottom: 40px;
}

.p16-frame-defend {
  margin-bottom: 28px;
}
.p16-frame-defend .p16-hero-card {
  padding: 24px 18px;
}
.p16-frame-defend .p16-hero-value {
  font-size: 52px;
}
.p16-frame-defend .p16-uplift-value {
  font-size: 18px;
}

.p16-frame-consistency {
  margin-bottom: 32px;
}

.p16-hero-card {
  text-align: center;
  padding: 30px 16px;
}

.p16-hero-current {
  background: var(--presale-paper-alt);
  border-top: 3px solid var(--presale-muted);
}

.p16-hero-target {
  background: var(--presale-ink);
  color: var(--presale-paper);
  border-top: 3px solid var(--presale-accent);
}

.p16-hero-gain {
  background: var(--presale-paper-alt);
  border-top: 3px solid var(--presale-accent-green);
}

.p16-hero-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 12px;
}
.p16-hero-label-dim {
  color: rgba(255, 255, 255, 0.6);
}

.p16-hero-value {
  font-family: 'Playfair Display', serif;
  font-size: 58px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
}
.p16-hero-value-muted {
  color: var(--presale-muted);
}
.p16-hero-value-accent {
  color: var(--presale-accent);
}
.p16-hero-value-green {
  color: var(--presale-accent-green);
}
.p16-hero-value-suffix {
  font-size: 28px;
  font-weight: 700;
}

.p16-hero-caption {
  font-size: 13px;
  color: var(--presale-muted);
  margin-top: 8px;
  white-space: nowrap;
}
.p16-hero-caption-dim {
  color: rgba(255, 255, 255, 0.7);
}

.p16-hero-arrow {
  text-align: center;
  color: var(--presale-accent);
  display: flex;
  justify-content: center;
}

.p16-uplift {
  text-align: center;
  min-width: 0;
}
.p16-uplift-label {
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--presale-accent);
  margin-bottom: 4px;
}
.p16-uplift-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--presale-accent);
  line-height: 1.25;
  white-space: nowrap;
}

/* ─── ROI 曲线图 ──────────────────────────────────────── */

.p16-chart {
  margin-bottom: 16px;
}

/* ─── Band adaptive value blocks ───────────────────────── */

.p16-band-grid {
  display: grid;
  grid-template-columns: 1.15fr 1.15fr 0.9fr;
  gap: 16px;
  margin-bottom: 22px;
}

.p16-band-defend {
  grid-template-columns: 1.15fr 1.15fr 1fr;
}

.p16-band-card {
  padding: 18px 18px 16px;
  background: var(--presale-paper-alt);
  border-top: 3px solid var(--presale-line);
  min-height: 150px;
}

.p16-band-card-lead {
  border-top-color: var(--presale-accent);
  box-shadow: 0 10px 24px rgba(11, 20, 38, 0.06);
}

.p16-band-card-strength {
  border-top-color: var(--presale-accent-green);
}

.p16-band-card-monitor {
  background: rgba(30, 58, 138, 0.06);
}

.p16-band-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 8px;
}

.p16-band-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--presale-ink);
  line-height: 1.45;
  margin-bottom: 10px;
}

.p16-band-list {
  margin: 0;
  padding-left: 16px;
  color: var(--presale-ink-soft);
  font-size: 12px;
  line-height: 1.65;
}

.p16-band-list li + li {
  margin-top: 4px;
}

.p16-band-card p {
  margin: 0;
  color: var(--presale-ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

/* ─── ③ ESTIMATED IMPACT 块(γ·2 r2) ──────────────────── */

.p16-impact {
  padding: 24px;
  background: var(--presale-paper-alt);
  border-left: 3px solid var(--presale-accent);
  margin-bottom: 24px;
}

.p16-impact-defend {
  padding: 18px 20px;
  border-left-color: var(--presale-line);
}

.p16-impact-defend .p16-impact-grid {
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.p16-impact-consistency {
  border-left-color: var(--presale-primary);
}

.p16-impact-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--presale-muted);
  margin-bottom: 16px;
}

.p16-impact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.p16-impact-caption {
  font-size: 12px;
  color: var(--presale-muted);
  margin-bottom: 4px;
}

.p16-impact-value {
  font-size: 14px;
  color: var(--presale-ink);
  font-weight: 500;
}

.p16-impact-arrow {
  color: var(--presale-muted);
  margin: 0 8px;
}

.p16-impact-after {
  color: var(--presale-accent-green);
}
.p16-impact-danger {
  color: var(--presale-accent-red);
}

/* ─── Verified case ───────────────────────────────────── */

.p16-case {
  padding: 14px 16px;
  margin-bottom: 20px;
  background: rgba(0, 128, 96, 0.08);
  border-left: 3px solid var(--presale-accent-green);
}

.p16-case-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 6px;
}

.p16-case-text {
  font-size: 12px;
  line-height: 1.7;
  color: var(--presale-ink);
}

/* ─── Phase 摘要条 ────────────────────────────────────── */

.p16-phase-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 16px 0;
  margin-bottom: 24px;
  border-top: 1px solid var(--presale-line);
}

.p16-phase-item {
  padding: 8px 12px;
  border-left: 2px solid var(--presale-primary);
}
.p16-phase-item-final {
  border-left-color: var(--presale-accent);
}

.p16-phase-duration {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 4px;
}

.p16-phase-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--presale-ink);
  margin-bottom: 4px;
  line-height: 1.4;
}

.p16-phase-target {
  font-size: 11px;
  color: var(--presale-accent);
  font-weight: 500;
}

/* ─── Disclaimer ──────────────────────────────────────── */

.p16-disclaimer {
  font-size: 11px;
  color: var(--presale-muted);
  line-height: 1.7;
  padding: 12px 16px;
  background: var(--presale-paper-alt);
  border-left: 3px solid var(--presale-line);
}
</style>
