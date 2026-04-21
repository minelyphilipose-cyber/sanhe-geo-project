<template>
  <section id="page-06" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>04 / 多平台提及率(续)</span>
      </div>

      <div class="p06-body">
        <!-- 顶部标题(本页沿用 P05 的章节号 04,作为"续") -->
        <div class="p06-header">
          <div class="mono p06-subtitle">CONTINUED · 平台详细数据</div>
          <h3 class="chinese-serif p06-title">各平台综合表现</h3>
        </div>

        <!-- bar chart -->
        <PresaleChart :option="barOption" height="280px" class="p06-chart" />

        <!-- 平台对比表 -->
        <div class="data-matrix p06-matrix">
          <!-- 表头 -->
          <div class="data-matrix-row p06-row-head">
            <div class="mono p06-col-label">RANK</div>
            <div class="mono p06-col-label">PLATFORM</div>
            <div class="mono p06-col-label p06-col-right">提及率</div>
            <div class="mono p06-col-label p06-col-right">平均排名</div>
            <div class="mono p06-col-label p06-col-right">主推荐</div>
            <div class="mono p06-col-label p06-col-right">情感</div>
          </div>

          <!-- 数据行 -->
          <div
            v-for="(row, idx) in tableRows"
            :key="row.platform_code"
            class="data-matrix-row p06-row-data"
          >
            <div class="display-serif p06-rank" :class="row.rankColorClass">
              {{ formatRank(idx + 1) }}
            </div>
            <div class="p06-platform-name">{{ row.platform_name }}</div>
            <div class="p06-col-right p06-mention-rate">{{ Math.round(row.mention_rate) }}%</div>
            <div class="p06-col-right p06-avg-ranking">{{ formatAvgRank(row.avg_ranking) }}</div>
            <div class="p06-col-right">
              {{ row.primary_recommendation_count }} 次
            </div>
            <div class="p06-col-right" :class="row.sentimentTextClass">
              <span :class="row.sentimentTextClass === 'cross' ? 'cross' : 'tick'">●</span>
              {{ row.sentimentPct }}%
            </div>
          </div>
        </div>

        <!-- 证据区(v1 不做内容填充,占位块让版式平衡) -->
        <!-- TODO:后端 negative_evidence 字段可填入第一条作为示例;β·2 不做避免空态 -->
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">06</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMergedView } from '@/composables/presale/useMergedView'
import PresaleChart from './shared/PresaleChart.vue'

/**
 * Page06 平台详细数据。
 *
 * 数据映射:
 *   - 平台表:platform_breakdown 按 mention_rate 降序排序
 *   - bar chart:x 轴平台名,y 轴 mention_rate
 *   - 情感 %:sentiment_distribution.positive / (positive + neutral + negative)
 *   - 情感文字色:≥70% 绿色,<70% 红色(对齐原型 tick/cross 分色)
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

interface TableRow {
  platform_code: string
  platform_name: string
  mention_rate: number
  avg_ranking: number | null
  primary_recommendation_count: number
  sentimentPct: number
  sentimentTextClass: 'tick' | 'cross'
  rankColorClass: string
}

const tableRows = computed<TableRow[]>(() => {
  const sorted = [...mergedView.value.platform_breakdown].sort(
    (a, b) => b.mention_rate - a.mention_rate
  )
  return sorted.map((p, idx) => {
    const sd = p.sentiment_distribution
    const sTotal = sd.positive + sd.neutral + sd.negative
    const sPct = sTotal === 0 ? 0 : Math.round((sd.positive / sTotal) * 100)
    return {
      platform_code: p.platform_code,
      platform_name: p.platform_name,
      mention_rate: p.mention_rate,
      avg_ranking: p.avg_ranking,
      primary_recommendation_count: p.primary_recommendation_count,
      sentimentPct: sPct,
      sentimentTextClass: sPct >= 70 ? 'tick' : 'cross',
      rankColorClass: idx === 0 ? 'p06-rank-gold' : idx <= 2 ? 'p06-rank-blue' : 'p06-rank-muted'
    }
  })
})

// ─── bar chart option ───────────────────────────────────
const barOption = computed<EChartsOption>(() => {
  const rows = tableRows.value
  return {
    grid: {
      left: 64,
      right: 16,
      top: 16,
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
        // axis tooltip 传数组
        if (!Array.isArray(params)) return ''
        const p = params[0]
        if (!p) return ''
        return `${p.name}<br/>提及率:${p.value}%`
      }
    },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.platform_name),
      axisLabel: {
        color: '#0b1426',
        fontSize: 11
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
        formatter: '{value}%'
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
        data: rows.map((r) => Math.round(r.mention_rate)),
        barMaxWidth: 36,
        itemStyle: {
          color: (params) => {
            const idx = params.dataIndex
            if (idx === 0) return '#d97706' // top1 accent
            if (idx <= 2) return '#1e3a8a' // top2-3 primary
            return '#6b6456' // 其余 muted
          },
          borderRadius: [2, 2, 0, 0]
        },
        label: {
          show: true,
          position: 'top',
          color: '#0b1426',
          fontSize: 11,
          fontWeight: 500,
          formatter: '{c}%'
        }
      }
    ]
  }
})

function formatRank(n: number): string {
  return n.toString().padStart(2, '0')
}

function formatAvgRank(r: number | null): string {
  if (r == null) return '—'
  return r.toFixed(1)
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p06-body {
  margin-top: 60px;
}

.p06-header {
  margin-bottom: 32px;
}
.p06-subtitle {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p06-title {
  font-size: 22px;
  font-weight: 700;
  color: #0b1426;
  margin: 0;
}

.p06-chart {
  margin-bottom: 32px;
}

.p06-matrix {
  margin-bottom: 32px;
}

.p06-row-head {
  grid-template-columns: 100px 1fr 80px 80px 80px 80px;
  padding: 12px 0 !important;
}
.p06-row-data {
  grid-template-columns: 100px 1fr 80px 80px 80px 80px;
}
.p06-col-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
}
.p06-col-right {
  text-align: right;
}

.p06-rank {
  font-size: 20px;
  font-weight: 700;
  font-style: italic;
}
.p06-rank-gold {
  color: #d97706;
}
.p06-rank-blue {
  color: #1e3a8a;
}
.p06-rank-muted {
  color: #6b6456;
}

.p06-platform-name {
  font-weight: 500;
}
.p06-mention-rate {
  font-weight: 600;
}
.p06-avg-ranking {
  color: #1a2942;
}
</style>
