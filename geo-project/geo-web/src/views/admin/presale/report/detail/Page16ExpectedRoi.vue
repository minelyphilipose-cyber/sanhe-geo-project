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
            <div class="section-label">EXPECTED ROI</div>
            <div class="section-heading">预期收益模拟</div>
          </div>
        </div>

        <!-- 核心数字对比:CURRENT → TARGET + UPLIFT 标注 + GAIN -->
        <div class="p16-hero-grid">
          <div class="p16-hero-card p16-hero-current">
            <div class="mono p16-hero-label">CURRENT</div>
            <div class="p16-hero-value p16-hero-value-muted">{{ currentScoreDisplay }}</div>
            <div class="p16-hero-caption">当前可见度</div>
          </div>
          <div class="p16-hero-arrow">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </div>
          <div class="p16-hero-card p16-hero-target">
            <div class="mono p16-hero-label p16-hero-label-dim">TARGET</div>
            <div class="p16-hero-value p16-hero-value-accent">{{ targetScoreDisplay }}</div>
            <div class="p16-hero-caption p16-hero-caption-dim">目标可见度(Top10)</div>
          </div>
          <div class="p16-uplift">
            <div class="mono p16-uplift-label">UPLIFT</div>
            <div class="display-serif p16-uplift-value">+{{ estimatedUpliftPercentDisplay }}%</div>
          </div>
          <div class="p16-hero-card p16-hero-gain">
            <div class="mono p16-hero-label">GAIN</div>
            <div class="p16-hero-value p16-hero-value-green">
              <template v-if="gainDisplay.kind === 'multiplier'">
                {{ gainDisplay.main }}<span class="p16-hero-value-suffix">x</span>
              </template>
              <template v-else>
                {{ gainDisplay.main }}<span class="p16-hero-value-suffix">%</span>
              </template>
            </div>
            <div class="p16-hero-caption">综合可见度提升</div>
          </div>
        </div>

        <!-- ROI 曲线图(4 点:起点 + 3 phase) -->
        <PresaleChart :option="roiLineOption" height="280px" class="p16-chart" />

        <!--
          ③ ESTIMATED IMPACT 块(γ·2 r2 新增)。
          4 个格子:2 个评分派生估算值 + 2 个真实契约数据。
        -->
        <div class="p16-impact">
          <div class="mono p16-impact-label">ESTIMATED IMPACT · 预估影响</div>
          <div class="p16-impact-grid">
            <!-- 格子 1:AI 渠道月度品牌曝光(基于 current/target 动态估算) -->
            <div class="p16-impact-item">
              <div class="p16-impact-caption">月度新客触达</div>
              <div class="p16-impact-value">
                <span class="mono p16-impact-after">{{ exposureUpliftDisplay }}</span>
              </div>
            </div>

            <!-- 格子 2:高价值查询场景覆盖(真实契约) -->
            <div class="p16-impact-item">
              <div class="p16-impact-caption">决策场景占位</div>
              <div class="p16-impact-value">
                <span class="mono">{{ highValueCoverage.before }}</span>
                <span class="p16-impact-arrow">→</span>
                <span class="mono p16-impact-after">{{ highValueCoverage.after }}</span>
              </div>
            </div>

            <!-- 格子 3:主推荐次数(基于 current/target 动态估算) -->
            <div class="p16-impact-item">
              <div class="p16-impact-caption">AI 主动推荐次数</div>
              <div class="p16-impact-value">
                <span class="mono p16-impact-after">{{ primaryRecUpliftDisplay }}</span>
              </div>
            </div>

            <!-- 格子 4:对标竞品差距(真实契约) -->
            <div class="p16-impact-item">
              <div class="p16-impact-caption">与 Top1 竞品差距收窄</div>
              <div class="p16-impact-value">
                <span class="mono">{{ competitorGap.before }}</span>
                <span class="p16-impact-arrow">→</span>
                <span class="mono p16-impact-after">{{ competitorGap.after }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段摘要条(3 个 phase title + duration 并排,呼应曲线 X 轴) -->
        <div class="p16-phase-strip">
          <div
            v-for="(p, idx) in phaseTeasers"
            :key="p.phase_no"
            class="p16-phase-item"
            :class="{ 'p16-phase-item-final': idx === phaseTeasers.length - 1 }"
          >
            <div class="mono p16-phase-duration">{{ p.duration_label }}</div>
            <div class="chinese-serif p16-phase-title">{{ p.title }}</div>
            <div class="mono p16-phase-target">→ {{ toIntRounded(p.target_score) }} 分</div>
          </div>
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
 * Page16 预期收益模拟(γ·2,r2 补 ③ 块)。
 *
 * 实现策略(r2 混合方案):
 *   - ① 3 数字卡片:current / target / gain(由 target/current 动态计算)+ uplift 小标
 *   - ② 4 点折线图:起点 M0 + 3 phase 的 target_score
 *   - ③ ESTIMATED IMPACT 块:4 格 2×2 grid,2 个评分派生估算值 + 2 个真实契约数据
 *     - AI 渠道月度品牌曝光:基于 current/target 估算确定提升值
 *     - 高价值查询场景覆盖:scene_coverage.high_value.{covered/total} → {total/total}(真实)
 *     - 主推荐次数:基于 current/target 估算确定提升值
 *     - 对标竞品差距:scores.overall - top1.overall → target_score - top1.overall(真实)
 *   - ④ 3 个 phase 摘要条(duration_label + title + target_score),给曲线加注解
 *   - ⑤ ROI disclaimer
 *
 * 数据映射:
 *   - mergedView.roi_simulation.{current_score, target_score, estimated_uplift_percent,
 *     estimated_exposure_multiplier, phases[]}
 *   - mergedView.merged_phases[].{title, phase.duration_label, phase.target_score}
 *   - mergedView.scene_coverage.high_value.{covered, total}
 *   - mergedView.scores.overall
 *   - mergedView.benchmarks_frozen.top1.overall
 *   - mergedView.roi_disclaimer
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const roi = computed(() => mergedView.value.roi_simulation)
// ─── 核心数字 ────────────────────────────────────────────

const currentScoreDisplay = computed(() => toIntRounded(roi.value.current_score))
const targetScoreDisplay = computed(() => toIntRounded(roi.value.target_score))
const estimatedUpliftPercentDisplay = computed(() =>
  toIntRounded(roi.value.estimated_uplift_percent)
)

const visibilityMultiplier = computed(() => {
  const current = roi.value.current_score
  const target = roi.value.target_score
  if (!current || current <= 0) return null
  return target / current
})

const gainDisplay = computed<{ kind: 'multiplier' | 'percent'; main: string }>(() => {
  const multiplier = visibilityMultiplier.value
  if (multiplier == null) {
    return { kind: 'multiplier', main: '—' }
  }
  if (multiplier >= 1.5) {
    return { kind: 'multiplier', main: multiplier.toFixed(1) }
  }
  return { kind: 'percent', main: `+${estimatedUpliftPercentDisplay.value}` }
})

// ─── ③ ESTIMATED IMPACT 块 ──────────────────────────────
//
// 4 个格子:2 个评分派生估算值 + 2 个真实契约数据。
//
// 真实契约格子:
//   - 高价值查询场景覆盖:scene_coverage.high_value.{covered}/{total} → {total}/{total}
//   - 对标竞品差距:scores.overall - benchmarks.top1.overall → target_score - top1.overall

const EXPOSURE_UPLIFT_COEF = {
  baseMultiplier: 5,
  factorBase: 2,
  factorDivisor: 50,
  factorFloor: 0.7,
  upliftMin: 30,
  upliftMax: 200
} as const

const PRIMARY_REC_UPLIFT_COEF = {
  baseMultiplier: 2.5,
  factorBase: 1.5,
  factorDivisor: 80,
  factorFloor: 0.6,
  upliftMin: 25,
  upliftMax: 80
} as const

function clamp(n: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, n))
}

function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

/**
 * 基于综合得分变化估算 AI 渠道月度品牌曝光提升百分比。
 * 展示估算,非实测值。示例:41→61 为 +118%,80→90 为 +35%。
 */
function calculateExposureUplift(
  current: number | null | undefined,
  target: number | null | undefined
): number | null {
  if (!isFiniteNumber(current) || !isFiniteNumber(target) || target <= current) return null
  const baseLift = (target - current) * EXPOSURE_UPLIFT_COEF.baseMultiplier
  const factor = Math.max(
    EXPOSURE_UPLIFT_COEF.factorFloor,
    EXPOSURE_UPLIFT_COEF.factorBase - current / EXPOSURE_UPLIFT_COEF.factorDivisor
  )
  return clamp(
    Math.round(baseLift * factor),
    EXPOSURE_UPLIFT_COEF.upliftMin,
    EXPOSURE_UPLIFT_COEF.upliftMax
  )
}

/**
 * 基于综合得分变化估算主推荐次数提升百分比。
 * 展示估算,非实测值。示例:41→61 为 +50%,80→90 为 +25%。
 */
function calculatePrimaryRecUplift(
  current: number | null | undefined,
  target: number | null | undefined
): number | null {
  if (!isFiniteNumber(current) || !isFiniteNumber(target) || target <= current) return null
  const baseLift = (target - current) * PRIMARY_REC_UPLIFT_COEF.baseMultiplier
  const factor = Math.max(
    PRIMARY_REC_UPLIFT_COEF.factorFloor,
    PRIMARY_REC_UPLIFT_COEF.factorBase - current / PRIMARY_REC_UPLIFT_COEF.factorDivisor
  )
  return clamp(
    Math.round(baseLift * factor),
    PRIMARY_REC_UPLIFT_COEF.upliftMin,
    PRIMARY_REC_UPLIFT_COEF.upliftMax
  )
}

const exposureUpliftPct = computed(() =>
  calculateExposureUplift(roi.value.current_score, roi.value.target_score)
)
const primaryRecUpliftPct = computed(() =>
  calculatePrimaryRecUplift(roi.value.current_score, roi.value.target_score)
)
const exposureUpliftDisplay = computed(() =>
  exposureUpliftPct.value == null ? '—' : `+${exposureUpliftPct.value}%`
)
const primaryRecUpliftDisplay = computed(() =>
  primaryRecUpliftPct.value == null ? '—' : `+${primaryRecUpliftPct.value}%`
)

interface ImpactCell {
  before: string
  after: string
}

/** 高价值查询场景覆盖:真实契约数据。目标定义为"全覆盖"。 */
const highValueCoverage = computed<ImpactCell>(() => {
  const hv = mergedView.value.scene_coverage.high_value
  return {
    before: `${hv.covered} / ${hv.total}`,
    after: `${hv.total} / ${hv.total}`
  }
})

/** 对标竞品差距:当前 = scores.overall - top1.overall;目标 = target_score - top1.overall。 */
const competitorGap = computed<ImpactCell>(() => {
  const currentOverall = mergedView.value.scores.overall
  const targetOverall = roi.value.target_score
  const top1 = mergedView.value.benchmarks_frozen.top1.overall
  const currentDiff = currentOverall - top1
  const targetDiff = targetOverall - top1
  return {
    before: formatSignedScore(currentDiff),
    after: formatSignedScore(targetDiff)
  }
})

/** 带正负号的分数显示:+5 分 / -18 分 / 0 分(0 时不带符号)。 */
function formatSignedScore(n: number): string {
  const rounded = toIntRounded(n)
  if (rounded === 0) return '0 分'
  const sign = rounded > 0 ? '+' : ''
  return `${sign}${rounded} 分`
}

// ─── Phase 摘要条 ────────────────────────────────────────

interface PhaseTeaser {
  phase_no: 1 | 2 | 3
  duration_label: string
  title: string
  target_score: number
}

const phaseTeasers = computed<PhaseTeaser[]>(() => {
  return mergedView.value.merged_phases.map((mp) => ({
    phase_no: mp.phase.phase_no,
    duration_label: mp.phase.duration_label,
    title: mp.title,
    target_score: mp.phase.target_score
  }))
})

// ─── ROI Line Chart(4 点) ───────────────────────────────

const roiLineOption = computed<EChartsOption>(() => {
  const phases = mergedView.value.merged_phases
  // X 轴类目:"当前" + 3 phase 的 duration_label
  const xCategories = ['当前', ...phases.map((p) => p.phase.duration_label)]
  // Y 值:current_score + 3 phase.target_score
  const yValues = [
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
      textStyle: { color: '#fefcf7', fontSize: 12 },
      formatter: (params) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        const p = params[0]
        const idx = p.dataIndex as number
        if (idx === 0) {
          return `${p.name}<br/>当前分值:${toIntRounded(Number(p.value))}`
        }
        const mp = phases[idx - 1]
        if (!mp) return `${p.name}<br/>${toIntRounded(Number(p.value))}`
        return [
          `${p.name} · ${mp.title}`,
          `目标分:${toIntRounded(mp.phase.target_score)}`,
          `较上阶段提升:+${toIntRounded(mp.phase.uplift_from_previous)}`,
          `优化完成度:${mp.phase.completed_optimization_count} / ${mp.phase.total_optimization_count}`
        ].join('<br/>')
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
        data: yValues,
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
            if (idx === yValues.length - 1) return '#d97706'
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
          formatter: '{c}'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(30, 58, 138, 0.18)' },
              { offset: 1, color: 'rgba(30, 58, 138, 0)' }
            ]
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
  grid-template-columns: 1fr 60px 1fr 60px 1fr;
  gap: 20px;
  align-items: center;
  margin-top: 24px;
  margin-bottom: 40px;
}

.p16-hero-card {
  text-align: center;
  padding: 32px 20px;
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
  font-size: 64px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -1px;
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
}
.p16-uplift-label {
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--presale-accent);
  margin-bottom: 4px;
}
.p16-uplift-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--presale-accent);
}

/* ─── ROI 曲线图 ──────────────────────────────────────── */

.p16-chart {
  margin-bottom: 16px;
}

/* ─── ③ ESTIMATED IMPACT 块(γ·2 r2) ──────────────────── */

.p16-impact {
  padding: 24px;
  background: var(--presale-paper-alt);
  border-left: 3px solid var(--presale-accent);
  margin-bottom: 24px;
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
