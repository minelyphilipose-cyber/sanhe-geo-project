<template>
  <section id="page-17" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>10 / 分阶段优化路径</span>
      </div>

      <div class="p17-body">
        <!-- 标题(CONTINUED 风格,P15 已用 09,这里是"续") -->
        <div class="p17-header">
          <div class="mono p17-subtitle">{{ roadmapFrame.eyebrow }}</div>
          <h3 class="chinese-serif p17-title">{{ roadmapFrame.heading }}</h3>
          <p class="p17-lead">{{ roadmapFrame.lead }}</p>
        </div>

        <div class="p17-timeline" :class="`p17-timeline-${valueFrame}`">
          <div
            v-for="(item, idx) in steps"
            :key="item.phase_no"
            class="timeline-step"
          >
            <div class="timeline-dot">{{ item.phase_no }}</div>
            <div class="p17-step-head">
              <div class="chinese-serif p17-step-title">{{ item.title }}</div>
              <div class="mono p17-step-month">{{ formatDuration(item.duration_label) }}</div>
            </div>
            <div class="p17-step-desc">{{ item.description }}</div>
            <div class="p17-stats">
              <div class="p17-stat">
                <div class="mono p17-stat-label">TARGET SCORE</div>
                <div
                  class="display-serif p17-stat-value"
                  :class="idx === steps.length - 1 ? 'p17-stat-accent' : 'p17-stat-primary'"
                >
                  {{ formatTargetRange(item) }}
                </div>
              </div>
              <div class="p17-stat">
                <div class="mono p17-stat-label">预期提升</div>
                <div
                  class="display-serif p17-stat-value p17-stat-green"
                  :class="{ 'p17-stat-muted': !item.projection_enabled }"
                >
                  {{ formatUplift(item) }}
                </div>
              </div>
              <div class="p17-stat">
                <div class="mono p17-stat-label">计划项数</div>
                <div class="display-serif p17-stat-value p17-stat-ink">
                  {{ plannedCount(item) }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="pull-quote">
          {{ roadmapFrame.footer }}
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">17</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import { toIntRounded } from '@/utils/presale/numberFormat'

/**
 * Page17 分阶段优化路径(γ·2)。
 *
 * 数据映射:
 *   - 每个 step 来自 mergedView.merged_phases[i]:
 *     - timeline-dot 数字 = phase.phase_no
 *     - 标题 = merged_phases[i].title
 *     - MONTH 标签 = phase.duration_label(经 formatDuration 转换为 "MONTH 1" 等形态)
 *     - 描述 = merged_phases[i].description
 *     - TARGET SCORE = phase.target_score_low/high
 *     - 预期提升 = phase.uplift_from_previous_low/high
 *     - 计划项数 = phase.planned_optimization_count
 *
 * 视觉约定:
 *   - 前 N-1 阶段 TARGET SCORE 用 primary(蓝),最后一阶段用 accent(橙)表示"最终目标"
 *   - duration_label 契约里是紧凑形态("M1"/"M2-3"/"M4-6"),渲染时转 "MONTH 1" 等更易读形态
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

type ValueFrame = 'improve' | 'consistency' | 'defend'

const narrativeBand = computed(() => mergedView.value.narrative_profile?.band ?? 'MIDDLE')
const valueFrame = computed<ValueFrame>(() => {
  if (narrativeBand.value === 'INVISIBLE' || narrativeBand.value === 'BEHIND') return 'improve'
  if (narrativeBand.value === 'STRONG' || narrativeBand.value === 'LEADER') return 'defend'
  return 'consistency'
})

const roadmapFrame = computed(() => {
  if (valueFrame.value === 'defend') {
    return {
      eyebrow: 'DEFENSE ROADMAP',
      heading: '巩固·监测路线',
      lead: '当前重点不是制造更大的缺口叙事,而是守住已形成的可见度资产,并持续捕捉竞品与 AI 回答变化。',
      footer: '高分客户的订阅价值在于持续守位、发现边际机会和预警变化;具体执行节奏仍需结合资源投入确认。'
    }
  }
  if (valueFrame.value === 'consistency') {
    return {
      eyebrow: 'CONSISTENCY ROADMAP',
      heading: '改进 + 巩固路线',
      lead: '你已在部分场景被看到,但出现还不稳定。路径重点是补齐短板,再把有效出现沉淀为稳定资产。',
      footer: '本路线用于统一改进目标与阶段动作,实际节奏建议结合团队资源与平台反馈滚动调整。'
    }
  }
  return {
    eyebrow: 'PHASED ROADMAP',
    heading: '分阶段优化路径',
    lead: '先补齐新顾客入口与高价值场景缺口,再逐步扩展到内容、平台与持续监测。',
    footer: '具体执行方案、资源投入预估和关键里程碑,建议结合您的业务规划和团队资源状况讨论。'
  }
})

interface Step {
  phase_no: 1 | 2 | 3
  title: string
  description: string
  duration_label: string
  target_score: number
  target_score_low?: number | null
  target_score_high?: number | null
  uplift_from_previous: number
  uplift_from_previous_low?: number | null
  uplift_from_previous_high?: number | null
  projection_enabled?: boolean | null
  total_optimization_count: number
  planned_optimization_count?: number | null
}

const steps = computed<Step[]>(() => {
  return mergedView.value.merged_phases.map<Step>((mp) => ({
    phase_no: mp.phase.phase_no,
    title: mp.title,
    description: mp.description,
    duration_label: mp.phase.duration_label,
    target_score: mp.phase.target_score,
    target_score_low: mp.phase.target_score_low,
    target_score_high: mp.phase.target_score_high,
    uplift_from_previous: mp.phase.uplift_from_previous,
    uplift_from_previous_low: mp.phase.uplift_from_previous_low,
    uplift_from_previous_high: mp.phase.uplift_from_previous_high,
    projection_enabled: mp.phase.projection_enabled ?? (mp.phase.total_optimization_count ?? 0) > 0,
    total_optimization_count: mp.phase.total_optimization_count,
    planned_optimization_count: mp.phase.planned_optimization_count
  }))
})

/**
 * duration_label 形态转换:
 *   - "M1"     → "MONTH 1"
 *   - "M2-3"   → "MONTH 2-3"
 *   - "M4-6"   → "MONTH 4-6"
 *   - 其他形态 → 原样返回(兜底)
 */
function formatDuration(label: string): string {
  const match = /^M(\d.*)$/.exec(label.trim())
  if (match) return `MONTH ${match[1]}`
  return label
}

function plannedCount(item: Step): string {
  return `${item.planned_optimization_count ?? item.total_optimization_count ?? 0}`
}

function formatTargetRange(item: Step): string {
  const low = item.target_score_low ?? item.target_score
  const high = item.target_score_high ?? item.target_score
  const lowRounded = toIntRounded(low)
  const highRounded = toIntRounded(high)
  return lowRounded === highRounded ? `${lowRounded}` : `${lowRounded}-${highRounded}`
}

function formatUplift(item: Step): string {
  if (!item.projection_enabled) {
    return item.phase_no === 3 ? '巩固·监测' : '不报分'
  }
  const low = item.uplift_from_previous_low ?? item.uplift_from_previous
  const high = item.uplift_from_previous_high ?? item.uplift_from_previous
  const lowRounded = toIntRounded(low)
  const highRounded = toIntRounded(high)
  if (lowRounded === 0 && highRounded === 0) return '不报分'
  return lowRounded === highRounded ? `+${lowRounded}` : `+${lowRounded}~+${highRounded}`
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p17-body {
  margin-top: 60px;
}

.p17-header {
  margin-bottom: 28px;
}
.p17-subtitle {
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--presale-muted);
  margin-bottom: 8px;
}
.p17-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--presale-ink);
  margin: 0;
}

.p17-lead {
  margin: 10px 0 0;
  max-width: 720px;
  font-size: 13px;
  color: var(--presale-ink-soft);
  line-height: 1.8;
}

.p17-timeline {
  margin-bottom: 24px;
}

.p17-timeline-defend .timeline-step {
  border-left-color: var(--presale-accent-green);
}

.p17-timeline-defend .timeline-dot {
  background: var(--presale-accent-green);
}

.p17-timeline-consistency .timeline-step {
  border-left-color: var(--presale-primary);
}

.p17-step-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.p17-step-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--presale-ink);
}

.p17-step-month {
  font-size: 11px;
  color: var(--presale-muted);
  letter-spacing: 1px;
}

.p17-step-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--presale-ink-soft);
  line-height: 1.7;
}

.p17-stats {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  padding: 12px 16px;
  background: var(--presale-paper-alt);
}

.p17-stat-label {
  font-size: 10px;
  color: var(--presale-muted);
  letter-spacing: 1px;
}

.p17-stat-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 2px;
}
.p17-stat-primary {
  color: var(--presale-primary);
}
.p17-stat-accent {
  color: var(--presale-accent);
}
.p17-stat-green {
  color: var(--presale-accent-green);
}
.p17-stat-ink {
  color: var(--presale-ink);
}
.p17-stat-muted {
  color: var(--presale-muted);
  font-size: 18px;
}
</style>
