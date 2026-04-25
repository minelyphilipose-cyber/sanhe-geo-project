<template>
  <section id="page-09" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>06 / 情感倾向</span>
      </div>

      <div class="p09-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">06</span>
          <div>
            <div class="section-label">SENTIMENT ANALYSIS</div>
            <div class="section-heading">情感倾向分析</div>
          </div>
        </div>

        <!-- 左 doughnut + 右 breakdown -->
        <div class="p09-top-grid">
          <div class="p09-chart-wrap">
            <PresaleChart :option="doughnutOption" height="260px" />
          </div>

          <div>
            <div class="mono p09-breakdown-label">BREAKDOWN</div>

            <!-- 3 条进度 -->
            <div v-for="row in breakdownRows" :key="row.key" class="p09-bar-row">
              <div class="p09-bar-head">
                <span class="p09-bar-name">
                  <span class="p09-bar-dot" :style="{ color: row.color }">●</span>
                  {{ row.label }}
                </span>
                <span class="mono p09-bar-value">
                  {{ row.pctText }} · {{ row.count }} 次
                </span>
              </div>
              <div class="p09-bar-track">
                <div
                  class="p09-bar-fill"
                  :style="{ width: row.barWidth, background: row.color }"
                ></div>
              </div>
            </div>

            <!-- VS. 竞品块已去除(前端无 competitor_sentiment 字段,
                 原型 91% vs 82% 文案无数据支撑,README §7 标 TODO) -->
          </div>
        </div>

        <!-- 正面关键词云 -->
        <div v-if="positiveKeywords.length > 0" class="p09-keywords-wrap">
          <div class="mono p09-keywords-label">POSITIVE KEYWORDS · 正面关键词</div>
          <div class="p09-keywords-list">
            <span
              v-for="kw in positiveKeywords"
              :key="kw.text"
              class="p09-keyword-chip"
              :style="{ fontSize: kw.fontSize, fontWeight: kw.fontWeight }"
            >
              {{ kw.text }}
            </span>
          </div>
        </div>

        <!-- 负面证据 -->
        <div v-if="firstNegativeEvidence" class="p09-evidence-wrap">
          <div class="evidence-tag p09-evidence-tag">⚠ NEGATIVE EVIDENCE · 负面提及证据</div>
          <div class="evidence-box p09-evidence-box">
            <div class="mono p09-evidence-meta">
              {{ firstNegativeEvidence.platform_name }} ·
              {{ formatEvidenceDate(firstNegativeEvidence.tested_at) }} ·
              "{{ firstNegativeEvidence.query }}"
            </div>
            <div class="p09-evidence-snippet">"{{ firstNegativeEvidence.snippet }}"</div>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">09</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMergedView } from '@/composables/presale/useMergedView'
import PresaleChart from './shared/PresaleChart.vue'

/**
 * Page09 情感倾向。
 *
 * 数据映射:
 *   - doughnut chart 3 分片:sentiment_detail.{positive,neutral,negative}_count
 *   - 3 条进度/数字:基于 positive/neutral/negative 三分类
 *   - 正面关键词:sentiment_detail.top_keywords?(可选,undefined 整块不渲染)
 *     字号用 keyword.weight 做视觉分级(1-5,最高 20px,最低 12px)
 *   - 负面证据:sentiment_detail.negative_evidence?.[0](取第一条,对齐原型)
 *
 * 不做:
 *   - 原型"VS. 竞品"块(91% vs 82%):无 competitor_sentiment 契约,去掉
 *   - 多条负面证据的列表:原型只画一条,本批对齐
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const sentiment = computed(() => mergedView.value.sentiment_detail)

// ─── 计数与百分比 ───────────────────────────────────────
const totalCount = computed(() => {
  const s = sentiment.value
  return s.positive_count + s.neutral_count + s.negative_count
})

function pct(count: number): number {
  if (totalCount.value === 0) return 0
  return Math.round((count / totalCount.value) * 100)
}

function pctRaw(count: number): number {
  if (totalCount.value === 0) return 0
  return (count / totalCount.value) * 100
}

function formatPct(count: number): string {
  if (count === 0 || totalCount.value === 0) return '0%'
  const value = pctRaw(count)
  if (value > 0 && value < 1) return '<1%'
  return `${Math.round(value)}%`
}

// ─── 3 条进度条数据 ─────────────────────────────────────
interface BreakdownRow {
  key: 'positive' | 'neutral' | 'negative'
  label: string
  color: string
  count: number
  pct: number
  pctText: string
  barWidth: string
}
const breakdownRows = computed<BreakdownRow[]>(() => {
  const s = sentiment.value
  return [
    {
      key: 'positive',
      label: '正面',
      color: '#047857',
      count: s.positive_count,
      pct: pct(s.positive_count),
      pctText: formatPct(s.positive_count),
      barWidth: formatBarWidth(s.positive_count)
    },
    {
      key: 'neutral',
      label: '中性',
      color: '#6b6456',
      count: s.neutral_count,
      pct: pct(s.neutral_count),
      pctText: formatPct(s.neutral_count),
      barWidth: formatBarWidth(s.neutral_count)
    },
    {
      key: 'negative',
      label: '负面',
      color: '#b91c1c',
      count: s.negative_count,
      pct: pct(s.negative_count),
      pctText: formatPct(s.negative_count),
      barWidth: formatBarWidth(s.negative_count)
    }
  ]
})

function formatBarWidth(count: number): string {
  const raw = pctRaw(count)
  if (count > 0 && raw > 0 && raw < 1) return '1%'
  return `${raw}%`
}

function formatDoughnutTooltip(params: unknown): string {
  const item = params as { name?: string; value?: unknown }
  const count = typeof item.value === 'number' ? item.value : Number(item.value ?? 0)
  return `${item.name ?? ''}: ${count} 次 (${formatPct(count)})`
}

// ─── doughnut chart ─────────────────────────────────────
const doughnutOption = computed<EChartsOption>(() => {
  const s = sentiment.value
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(11, 20, 38, 0.9)',
      borderWidth: 0,
      textStyle: { color: '#fefcf7', fontSize: 12 },
      formatter: formatDoughnutTooltip
    },
    legend: {
      show: false
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '78%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        label: {
          show: true,
          position: 'center',
          formatter: () => {
            // 圆心展示总次数(两轮合计提及数)
            return `{num|${totalCount.value}}\n{sub|总提及}`
          },
          rich: {
            num: {
              fontSize: 32,
              fontFamily: 'Playfair Display, serif',
              fontWeight: 900,
              color: '#0b1426',
              lineHeight: 36
            },
            sub: {
              fontSize: 11,
              color: '#6b6456',
              letterSpacing: 2,
              lineHeight: 16
            }
          }
        },
        labelLine: { show: false },
        data: [
          { name: '正面', value: s.positive_count, itemStyle: { color: '#047857' } },
          { name: '中性', value: s.neutral_count, itemStyle: { color: '#6b6456' } },
          { name: '负面', value: s.negative_count, itemStyle: { color: '#b91c1c' } }
        ]
      }
    ]
  }
})

// ─── 正面关键词云 ───────────────────────────────────────
interface KeywordChip {
  text: string
  fontSize: string
  fontWeight: number
}

/**
 * SentimentKeyword 契约字段:
 *   { keyword: string, frequency: number, sentiment: 'POSITIVE'|'NEUTRAL'|'NEGATIVE', font_size?: number }
 *
 * 筛选正面(sentiment === 'POSITIVE'),取最多 10 个。
 * 字号优先用后端返的 font_size(px);未返则按 frequency 线性映射到 12-20px。
 */
const positiveKeywords = computed<KeywordChip[]>(() => {
  const kws = sentiment.value.top_keywords
  if (!kws || kws.length === 0) return []

  const positives = kws.filter((k) => k.sentiment === 'POSITIVE')
  if (positives.length === 0) return []

  // 如果有 frequency,计算 min/max 做线性映射
  const freqs = positives.map((k) => k.frequency)
  const maxFreq = Math.max(...freqs)
  const minFreq = Math.min(...freqs)
  const freqRange = Math.max(1, maxFreq - minFreq) // 避免除 0

  return positives.slice(0, 10).map((kw) => {
    // 优先后端 font_size,否则基于 frequency 映射 12-20px
    let fontSize: number
    if (kw.font_size != null && kw.font_size > 0) {
      fontSize = kw.font_size
    } else {
      // frequency 越高字号越大,线性映射到 12-20
      const ratio = (kw.frequency - minFreq) / freqRange
      fontSize = Math.round(12 + ratio * 8)
    }
    const fontWeight = fontSize >= 18 ? 600 : fontSize >= 15 ? 500 : 400
    return {
      text: kw.keyword,
      fontSize: `${fontSize}px`,
      fontWeight
    }
  })
})

// ─── 负面证据(取第一条) ───────────────────────────────
const firstNegativeEvidence = computed(() => {
  const list = sentiment.value.negative_evidence
  if (!list || list.length === 0) return null
  return list[0]
})

function formatEvidenceDate(isoStr: string): string {
  const d = new Date(isoStr)
  if (Number.isNaN(d.getTime())) return isoStr
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p09-body {
  margin-top: 60px;
}

/* 左右布局(280 + 1fr,原型一致) */
.p09-top-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 48px;
  margin-bottom: 40px;
}
.p09-chart-wrap {
  min-height: 260px;
}

.p09-breakdown-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 20px;
}

/* 3 条进度 */
.p09-bar-row {
  margin-bottom: 20px;
}
.p09-bar-row:nth-child(4) {
  margin-bottom: 28px;
}
.p09-bar-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.p09-bar-name {
  font-size: 13px;
  font-weight: 500;
}
.p09-bar-dot {
  /* color 由内联 style 控制 */
}
.p09-bar-value {
  font-size: 13px;
  font-weight: 600;
}
.p09-bar-track {
  height: 4px;
  background: #c8bfa8;
  border-radius: 2px;
  overflow: hidden;
}
.p09-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

/* 正面关键词 */
.p09-keywords-wrap {
  margin-bottom: 32px;
}
.p09-keywords-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 16px;
}
.p09-keywords-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.p09-keyword-chip {
  color: #047857;
  padding: 4px 12px;
  background: rgba(4, 120, 87, 0.08);
}

/* 负面证据 */
.p09-evidence-wrap {
  /* evidence-tag / evidence-box 来自 report-theme.css */
}
.p09-evidence-tag {
  color: #b91c1c !important;
}
.p09-evidence-box {
  border-color: #b91c1c !important;
  border-left: 3px solid #b91c1c !important;
}
.p09-evidence-meta {
  font-size: 11px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p09-evidence-snippet {
  line-height: 1.8;
  color: #1a2942;
}
</style>
