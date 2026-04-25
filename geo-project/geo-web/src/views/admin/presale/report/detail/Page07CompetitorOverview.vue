<template>
  <section id="page-07" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>05 / 竞品对标</span>
      </div>

      <div class="p07-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">05</span>
          <div>
            <div class="section-label">COMPETITIVE BENCHMARK</div>
            <div class="section-heading">竞品对标分析</div>
          </div>
        </div>

        <!-- AI 视角引入语(静态) -->
        <div class="p07-intro">
          <div class="mono p07-intro-label">AI'S VIEW</div>
          <div class="chinese-serif p07-intro-text">AI 视角下您的真实竞争对手:</div>
        </div>

        <!-- 竞品卡片组:3 竞品 + 自己 -->
        <div class="p07-cards">
          <div
            v-for="(c, idx) in mergedView.merged_competitors"
            :key="c.rank"
            class="competitor-card"
            :class="`top${c.rank}`"
          >
            <div class="p07-card-head">
              <div class="competitor-rank">{{ formatRank(c.rank) }}</div>
              <div class="mono p07-card-tag">TOP{{ c.rank }}</div>
            </div>
            <div class="chinese-serif p07-card-name">{{ c.name }}</div>
            <div class="p07-card-metric-wrap">
              <div class="p07-card-sub">提及率</div>
              <div
                class="metric-hero p07-card-rate"
                :class="`p07-card-rate-${c.rank}`"
              >
                {{ toIntRounded(c.mention_rate) }}%
              </div>
              <div class="p07-card-sub-alt">
                平均排名 {{ formatAvgRank(c.avg_ranking) }}
              </div>
            </div>
          </div>

          <!-- 自己卡片 -->
          <div class="competitor-card self">
            <div class="p07-card-head">
              <div class="competitor-rank">—</div>
              <div class="mono p07-card-tag-self">您的品牌</div>
            </div>
            <div class="chinese-serif p07-card-name">{{ mergedView.brand_name }}</div>
            <div class="p07-card-metric-wrap p07-card-metric-self">
              <div class="p07-card-sub-self">提及率</div>
              <div class="metric-hero p07-card-rate p07-card-rate-self">
                {{ selfMentionRatePct }}%
              </div>
              <div class="p07-card-sub-self-alt">
                平均排名 {{ selfAvgRankText }}
              </div>
            </div>
          </div>
        </div>

        <!-- 对比柱状图(方案 D: mention_count 绝对量) -->
        <div class="p07-chart-wrap">
          <div class="mono p07-chart-title">
            MENTION COUNT · 42 个测试场景中被 AI 提及的次数
          </div>
          <PresaleChart :option="barOption" height="260px" />
        </div>

        <!-- 底部引用(静态) -->
        <div class="p07-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">07</div>
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
 * Page07 竞品对标总览。
 *
 * 数据映射:
 *   - 3 竞品卡片:merged_competitors(按 rank 排序,后端已保证 1/2/3)
 *   - 自家卡片:brand_name + 聚合 mention_rate(计数直除,同 P03)+ 加权平均 avg_ranking
 *   - bar chart(方案 D):mention_count 横向对比
 *     4 根柱 = 3 竞品 + 自己,数值 = mention_count
 *     原型文案"42 中 29 次"是 mention_count,视觉上用绝对量而非百分比,
 *     和卡片的 mention_rate(%) 互为补充,不重复
 *
 * 不做:
 *   - 原型底部"差距并非全面落后..."文案无契约字段,本批用静态话术
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

// ─── 自家品牌聚合(对齐 P03 算法) ──────────────────────
const selfTotalPrompts = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.total_tests, 0)
)
const selfTotalMentions = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.mention_count, 0)
)
const selfMentionRatePct = computed(() => {
  if (selfTotalPrompts.value === 0) return 0
  return toIntRounded((selfTotalMentions.value / selfTotalPrompts.value) * 100)
})
const selfAvgRankText = computed(() => {
  const list = mergedView.value.platform_breakdown.filter(
    (p) => p.avg_ranking != null && p.mention_count > 0
  )
  if (list.length === 0) return '—'
  const weightedSum = list.reduce((s, p) => s + (p.avg_ranking as number) * p.mention_count, 0)
  const weightTotal = list.reduce((s, p) => s + p.mention_count, 0)
  if (weightTotal === 0) return '—'
  return String(toIntRounded(weightedSum / weightTotal))
})

// ─── bar chart(方案 D:mention_count 对比) ─────────────
const barOption = computed<EChartsOption>(() => {
  const comps = mergedView.value.merged_competitors
  // 横坐标 3 竞品 + 自己(自己放最右,视觉上"被比较的对象")
  const names = [...comps.map((c) => c.name), mergedView.value.brand_name]
  const counts = [
    ...comps.map((c) => c.mention_count),
    selfTotalMentions.value
  ]
  const isSelfFlags = [...comps.map(() => false), true]

  return {
    grid: {
      left: 48,
      right: 16,
      top: 24,
      bottom: 36,
      containLabel: false
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(11, 20, 38, 0.9)',
      borderWidth: 0,
      textStyle: { color: '#fefcf7', fontSize: 12 },
      formatter: (params) => {
        if (!Array.isArray(params)) return ''
        const p = params[0]
        if (!p) return ''
        return `${p.name}<br/>被提及 ${p.value} 次`
      }
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        color: '#0b1426',
        fontSize: 11,
        interval: 0
      },
      axisLine: { lineStyle: { color: '#c8bfa8' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: {
        color: '#6b6456',
        fontSize: 10
      },
      splitLine: {
        lineStyle: { color: '#c8bfa8', type: 'dashed' }
      },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        data: counts,
        barMaxWidth: 48,
        itemStyle: {
          color: (params) => {
            const idx = params.dataIndex
            // self 条(最后一个)用深墨色突出,竞品:top1 橙 / top2-3 蓝
            if (isSelfFlags[idx]) return '#0b1426'
            if (idx === 0) return '#d97706'
            return '#1e3a8a'
          },
          borderRadius: [2, 2, 0, 0]
        },
        label: {
          show: true,
          position: 'top',
          color: '#0b1426',
          fontSize: 11,
          fontWeight: 500,
          formatter: '{c}'
        }
      }
    ]
  }
})

// ─── 辅助格式化 ────────────────────────────────────────
function formatRank(n: number): string {
  return n.toString().padStart(2, '0')
}
function formatAvgRank(r: number | null): string {
  if (r == null) return '—'
  return String(toIntRounded(r))
}

// ─── 底部引用文案(静态) ──────────────────────────────
const quoteText = `上图展示了 AI 视角下您与 Top3 竞品的提及次数对比。差距集中在哪些场景类别、差距的具体机制,建议结合后续章节的场景明细逐项分析。`
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p07-body {
  margin-top: 60px;
}

/* AI 视角引入 */
.p07-intro {
  background: #f7f3ea;
  padding: 24px;
  margin-bottom: 32px;
  border-left: 3px solid #1e3a8a;
}
.p07-intro-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
  margin-bottom: 6px;
}
.p07-intro-text {
  font-size: 18px;
  font-weight: 500;
  color: #0b1426;
  line-height: 1.7;
}

/* 竞品卡片 grid */
.p07-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 48px;
}
.p07-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.p07-card-tag {
  font-size: 10px;
  color: #6b6456;
  letter-spacing: 1px;
}
.p07-card-tag-self {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 1px;
}
.p07-card-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.p07-card-metric-wrap {
  border-top: 1px solid #c8bfa8;
  padding-top: 10px;
}
.p07-card-metric-self {
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.p07-card-sub {
  font-size: 11px;
  color: #6b6456;
  margin-bottom: 2px;
}
.p07-card-sub-self {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 2px;
}
.p07-card-sub-alt {
  font-size: 11px;
  color: #6b6456;
  margin-top: 6px;
}
.p07-card-sub-self-alt {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 6px;
}
.p07-card-rate {
  font-size: 32px;
}
.p07-card-rate-1 {
  color: #d97706;
}
.p07-card-rate-2 {
  color: #1e3a8a;
}
.p07-card-rate-3 {
  color: #6b6456;
}
.p07-card-rate-self {
  color: #d97706;
}

/* chart */
.p07-chart-wrap {
  margin-bottom: 32px;
}
.p07-chart-title {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 12px;
}

.p07-quote-wrap {
  margin-top: 24px;
}
</style>
