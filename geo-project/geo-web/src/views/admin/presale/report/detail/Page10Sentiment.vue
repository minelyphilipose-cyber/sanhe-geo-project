<template>
  <section id="page-10" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>07 / 情感倾向</span>
      </div>

      <div class="p10-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">07</span>
          <div>
            <div class="section-label">SENTIMENT ANALYSIS</div>
            <div class="section-heading">品牌被提及时的情感倾向</div>
            <div class="p10-scope-note">
              本页只统计 AI 明确提到 {{ mergedView.brand_name }} 时的情感判断;样本较少时仅作方向参考。
            </div>
          </div>
        </div>

        <!-- 左 doughnut + 右 breakdown -->
        <div class="p10-top-grid">
          <div class="p10-chart-wrap">
            <PresaleChart :option="doughnutOption" height="260px" />
          </div>

          <div>
            <div class="mono p10-breakdown-label">BRAND MENTION SENTIMENT</div>
            <div v-if="showImpressionGap" class="p10-sparse-note">
              {{ sampleWarningText }}
            </div>

            <!-- 3 条进度 -->
            <div v-for="row in breakdownRows" :key="row.key" class="p10-bar-row">
              <div class="p10-bar-head">
                <span class="p10-bar-name">
                  <span class="p10-bar-dot" :style="{ color: row.color }">●</span>
                  {{ row.label }}
                </span>
                <span class="mono p10-bar-value">
                  {{ row.pctText }} · {{ row.count }} 次
                </span>
              </div>
              <div class="p10-bar-track">
                <div
                  class="p10-bar-fill"
                  :style="{ width: row.barWidth, background: row.color }"
                ></div>
              </div>
            </div>

            <!-- VS. 竞品块已去除(前端无 competitor_sentiment 字段,
                 原型 91% vs 82% 文案无数据支撑,README §7 标 TODO) -->
          </div>
        </div>

        <!-- 正面关键词云 -->
        <div v-if="positiveKeywords.length > 0" class="p10-keywords-wrap">
          <div class="mono p10-keywords-label">BRAND POSITIVE KEYWORDS · 品牌正面关键词</div>
          <div class="p10-keywords-note">
            关键词来自明确提到 {{ mergedView.brand_name }} 的回答,用于观察 AI 如何描述你的品牌。
          </div>
          <div class="p10-keywords-list">
            <span
              v-for="kw in positiveKeywords"
              :key="kw.text"
              class="p10-keyword-chip"
              :style="{ fontSize: kw.fontSize, fontWeight: kw.fontWeight }"
            >
              {{ kw.text }}
            </span>
          </div>
        </div>

        <!-- 样本不足时的品牌印象缺口 -->
        <div v-else class="p10-impression-gap">
          <div class="p10-gap-callout">
            <div class="mono p10-gap-kicker">{{ impressionGapKicker }}</div>
            <div class="p10-gap-title">{{ impressionGapTitle }}</div>
            <div class="p10-gap-copy">{{ impressionGapCopy }}</div>
          </div>
          <div class="p10-gap-grid">
            <div v-for="item in impressionGapCards" :key="item.index" class="p10-gap-card">
              <div class="mono p10-gap-card-index">{{ item.index }}</div>
              <div class="p10-gap-card-title">{{ item.title }}</div>
              <div class="p10-gap-card-copy">{{ item.copy }}</div>
            </div>
          </div>
        </div>

        <!-- 真实负面证据池 -->
        <div v-if="negativeEvidenceList.length > 0" class="p10-evidence-wrap">
          <div class="evidence-tag p10-evidence-tag">AI NEGATIVE FEEDBACK · AI 提到的负面反馈</div>
          <div class="p10-evidence-note">
            {{ concernEvidenceNote }}
          </div>
          <div
            v-for="(evidence, idx) in negativeEvidenceList"
            :key="`${evidence.platform_code}-${evidence.tested_at}-${idx}`"
            class="evidence-box p10-evidence-box"
          >
            <div class="mono p10-evidence-meta">
              {{ evidence.platform_name }} ·
              {{ formatEvidenceDate(evidence.tested_at) }} ·
              "{{ evidence.query }}"
            </div>
            <div class="p10-evidence-snippet">"{{ evidence.snippet }}"</div>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">10</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMergedView } from '@/composables/presale/useMergedView'
import PresaleChart from './shared/PresaleChart.vue'

/**
 * Page10 情感倾向。
 *
 * 数据映射:
 *   - doughnut chart 3 分片:sentiment_detail.{positive,neutral,negative}_count
 *   - 3 条进度/数字:基于 positive/neutral/negative 三分类
 *   - 正面关键词:sentiment_detail.top_keywords?(可选,undefined 整块不渲染)
 *     字号用 keyword.weight 做视觉分级(1-5,最高 20px,最低 12px)
 *   - 真实负面证据池:sentiment_detail.negative_evidence 最多展示 3 条;新报告只保留 sentiment=NEGATIVE
 *
 * 不做:
 *   - 原型"VS. 竞品"块(91% vs 82%):无 competitor_sentiment 契约,去掉
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const sentiment = computed(() => mergedView.value.sentiment_detail)
// ─── 计数与百分比 ───────────────────────────────────────
const totalCount = computed(() => {
  const s = sentiment.value
  return s.positive_count + s.neutral_count + s.negative_count
})
const isNoBrandMention = computed(() => totalCount.value === 0)
const isSparseSample = computed(() => totalCount.value > 0 && totalCount.value < 10)
const showImpressionGap = computed(() => isNoBrandMention.value || isSparseSample.value)
const chartCenterLabel = computed(() => {
  if (isNoBrandMention.value) return '尚未形成印象'
  if (isSparseSample.value) return '样本偏少'
  return '品牌提及'
})
const sampleWarningText = computed(() => {
  if (isNoBrandMention.value) {
    return `本次没有采集到 ${mergedView.value.brand_name} 的明确提及,当前不是口碑稳定,而是 AI 尚未形成可评价的品牌印象。`
  }
  return `本次品牌提及样本为 ${totalCount.value} 条,不足以证明 AI 对你有稳定好印象。`
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
            return `{num|${totalCount.value}}\n{sub|${chartCenterLabel.value}}`
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
  if (showImpressionGap.value) return []
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

const impressionGapKicker = computed(() =>
  isNoBrandMention.value
    ? 'NO BRAND MENTION · 尚未形成品牌印象'
    : 'LOW SAMPLE · 品牌印象样本偏少'
)
const impressionGapTitle = computed(() =>
  isNoBrandMention.value
    ? '当前最大问题不是 AI 说你不好,而是 AI 还没有谈到你。'
    : 'AI 偶尔提到你,但样本还不足以沉淀稳定好印象。'
)
const impressionGapCopy = computed(() =>
  isNoBrandMention.value
    ? '在本次测试里,AI 尚未给出足够的品牌描述,目标用户很难从回答中建立对你的具体认知。'
    : `本次只有 ${totalCount.value} 条品牌提及,正负面比例只能作参考;真正值得关注的是 AI 对你的记忆还不够稳定。`
)
const impressionGapCards = computed(() => [
  {
    index: '01',
    title: '可见度不足',
    copy: 'AI 很少主动提到你,说明品牌还没有稳定进入回答候选。'
  },
  {
    index: '02',
    title: '好印象未沉淀',
    copy: '即使线下服务有优势,AI 目前还缺少足够材料形成正向描述。'
  },
  {
    index: '03',
    title: '内容资产需要补强',
    copy: '需要让 AI 持续读到你的项目、医生、案例、价格与服务信息。'
  }
])

const concernEvidenceNote = computed(() => {
  const count = sentiment.value.negative_count
  return `按情感分类口径识别真负面 ${count} 条；下方为 AI 回答中需要优先处理的代表性负面片段。`
})

// ─── 真实负面证据池(最多 3 条) ───────────────────────────
const negativeEvidenceList = computed(() => {
  const list = sentiment.value.negative_evidence
  if (!list || list.length === 0) return []
  return list.slice(0, 3)
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

.p10-body {
  margin-top: 60px;
}
.p10-scope-note {
  margin-top: 6px;
  max-width: 640px;
  font-size: 11px;
  line-height: 1.7;
  color: #6b6456;
}

/* 左右布局(280 + 1fr,原型一致) */
.p10-top-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 48px;
  margin-bottom: 40px;
}
.p10-chart-wrap {
  min-height: 260px;
}

.p10-breakdown-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 20px;
}
.p10-sparse-note {
  margin: -8px 0 18px;
  padding: 10px 12px;
  background: #fff4df;
  color: #8a4b0d;
  font-size: 12px;
  line-height: 1.6;
}

/* 3 条进度 */
.p10-bar-row {
  margin-bottom: 20px;
}
.p10-bar-row:nth-child(4) {
  margin-bottom: 28px;
}
.p10-bar-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.p10-bar-name {
  font-size: 13px;
  font-weight: 500;
}
.p10-bar-dot {
  /* color 由内联 style 控制 */
}
.p10-bar-value {
  font-size: 13px;
  font-weight: 600;
}
.p10-bar-track {
  height: 4px;
  background: #c8bfa8;
  border-radius: 2px;
  overflow: hidden;
}
.p10-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

/* 正面关键词 */
.p10-keywords-wrap {
  margin-bottom: 32px;
}
.p10-keywords-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 6px;
}
.p10-keywords-note {
  margin-bottom: 14px;
  font-size: 11px;
  line-height: 1.6;
  color: #8a8272;
}
.p10-keywords-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.p10-keyword-chip {
  color: #047857;
  padding: 4px 12px;
  background: rgba(4, 120, 87, 0.08);
}

/* 品牌印象缺口 */
.p10-impression-gap {
  margin-bottom: 32px;
}
.p10-gap-callout {
  margin-bottom: 18px;
  padding: 18px 22px;
  border-left: 4px solid #b45309;
  background: #fff4df;
}
.p10-gap-kicker {
  margin-bottom: 8px;
  color: #b45309;
  font-size: 10px;
  letter-spacing: 2.5px;
}
.p10-gap-title {
  color: #0b1426;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.55;
}
.p10-gap-copy {
  max-width: 760px;
  margin-top: 8px;
  color: #6b6456;
  font-size: 12px;
  line-height: 1.8;
}
.p10-gap-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.p10-gap-card {
  min-height: 132px;
  padding: 16px 16px 18px;
  border-top: 2px solid #c8bfa8;
  background: #f7f3ea;
}
.p10-gap-card-index {
  margin-bottom: 12px;
  color: #b45309;
  font-size: 10px;
  letter-spacing: 2px;
}
.p10-gap-card-title {
  margin-bottom: 8px;
  color: #0b1426;
  font-size: 15px;
  font-weight: 800;
}
.p10-gap-card-copy {
  color: #6b6456;
  font-size: 12px;
  line-height: 1.75;
}

/* 真实负面证据 */
.p10-evidence-wrap {
  /* evidence-tag / evidence-box 来自 report-theme.css */
}
.p10-evidence-tag {
  color: #b91c1c !important;
}
.p10-evidence-note {
  margin: 8px 0 12px;
  color: #6b6456;
  font-size: 11px;
  line-height: 1.7;
}
.p10-evidence-box {
  border-color: #b91c1c !important;
  border-left: 3px solid #b91c1c !important;
}
.p10-evidence-box + .p10-evidence-box {
  margin-top: 10px;
}
.p10-evidence-meta {
  font-size: 11px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p10-evidence-snippet {
  line-height: 1.8;
  color: #1a2942;
}
</style>
