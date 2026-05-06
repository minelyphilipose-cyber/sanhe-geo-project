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
          <div class="mono p17-subtitle">PHASED ROADMAP</div>
          <h3 class="chinese-serif p17-title">分阶段优化路径</h3>
        </div>

        <div class="p17-timeline">
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
                  {{ toIntRounded(item.target_score) }}
                </div>
              </div>
              <div class="p17-stat">
                <div class="mono p17-stat-label">预期提升</div>
                <div class="display-serif p17-stat-value p17-stat-green">
                  +{{ toIntRounded(item.uplift_from_previous) }}
                </div>
              </div>
              <div class="p17-stat">
                <div class="mono p17-stat-label">{{ formatCompletion(item).label }}</div>
                <div
                  class="display-serif p17-stat-value p17-stat-ink"
                  :class="{ 'p17-stat-muted': formatCompletion(item).isPassive }"
                >
                  {{ formatCompletion(item).value }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="pull-quote">
          具体执行方案、资源投入预估和关键里程碑,建议结合您的业务规划和团队资源状况讨论。
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
 *     - TARGET SCORE = phase.target_score
 *     - 预期提升 = phase.uplift_from_previous
 *     - 完成项 = phase.completed_optimization_count / total_optimization_count
 *
 * 视觉约定:
 *   - 前 N-1 阶段 TARGET SCORE 用 primary(蓝),最后一阶段用 accent(橙)表示"最终目标"
 *   - duration_label 契约里是紧凑形态("M1"/"M2-3"/"M4-6"),渲染时转 "MONTH 1" 等更易读形态
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

interface Step {
  phase_no: 1 | 2 | 3
  title: string
  description: string
  duration_label: string
  target_score: number
  uplift_from_previous: number
  completed_optimization_count: number
  total_optimization_count: number
}

const steps = computed<Step[]>(() => {
  return mergedView.value.merged_phases.map<Step>((mp) => ({
    phase_no: mp.phase.phase_no,
    title: mp.title,
    description: mp.description,
    duration_label: mp.phase.duration_label,
    target_score: mp.phase.target_score,
    uplift_from_previous: mp.phase.uplift_from_previous,
    completed_optimization_count: mp.phase.completed_optimization_count,
    total_optimization_count: mp.phase.total_optimization_count
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

function formatCompletion(item: Step): { label: string; value: string; isPassive: boolean } {
  const total = item.total_optimization_count ?? 0
  if (item.phase_no === 3 && total === 0) {
    return { label: '运营状态', value: '持续监测中', isPassive: true }
  }
  return {
    label: '完成项',
    value: `${item.completed_optimization_count ?? 0} / ${total}`,
    isPassive: false
  }
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

.p17-timeline {
  margin-bottom: 24px;
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
