<template>
  <section id="page-08" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>06 / 竞品对标</span>
      </div>

      <div class="p08-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">06</span>
          <div>
            <div class="section-label">COMPETITIVE BENCHMARK</div>
            <div class="section-heading">竞品对标分析</div>
          </div>
        </div>

        <!-- AI 视角引入语(静态) -->
        <div class="p08-intro">
          <div class="mono p08-intro-label">AI'S VIEW</div>
          <div class="chinese-serif p08-intro-text">AI 视角下您的真实竞争对手:</div>
        </div>

          <!-- 竞品卡片组:3 竞品 + 自己 -->
        <div class="p08-cards">
          <div
            v-for="(c, idx) in sortedCompetitors"
            :key="`${c.rank}-${c.name}`"
            class="competitor-card"
            :class="`top${idx + 1}`"
          >
            <div class="p08-card-head">
              <div class="competitor-rank">{{ formatRank(idx + 1) }}</div>
              <div class="mono p08-card-tag">TOP{{ idx + 1 }}</div>
            </div>
            <div class="chinese-serif p08-card-name">{{ c.name }}</div>
            <div class="p08-card-metric-wrap">
              <div class="p08-card-sub">提及次数</div>
              <div
                class="metric-hero p08-card-rate"
                :class="`p08-card-rate-${idx + 1}`"
              >
                {{ c.mention_count }}<span class="p08-card-unit">次</span>
              </div>
            </div>
          </div>

          <!-- 自己卡片 -->
          <div class="competitor-card self">
            <div class="p08-card-head">
              <div class="competitor-rank">—</div>
              <div class="mono p08-card-tag-self">您的品牌</div>
            </div>
            <div class="chinese-serif p08-card-name">{{ mergedView.brand_name }}</div>
            <div class="p08-card-metric-wrap p08-card-metric-self">
              <div class="p08-card-sub-self">提及次数</div>
              <div class="metric-hero p08-card-rate p08-card-rate-self">
                {{ selfTotalMentions }}<span class="p08-card-unit">次</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 对比柱状图(方案 D: mention_count 绝对量) -->
        <div class="p08-chart-wrap">
          <div class="mono p08-chart-title">
            MENTION COUNT · AI 测试结果中被提及的次数
          </div>
          <PresaleChart :option="barOption" height="260px" />
        </div>

        <!-- 底部引用(静态) -->
        <div class="p08-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">08</div>
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
 * Page08 竞品对标总览。
 *
 * 数据映射:
 *   - 3 竞品卡片:merged_competitors 按当前 mention_count 降序展示
 *   - 自家卡片:brand_name + 聚合 mention_count + 加权平均 avg_ranking
 *   - bar chart(方案 D):mention_count 横向对比
 *     4 根柱 = 3 竞品 + 自己,数值 = mention_count
 *     卡片和柱状图统一使用绝对次数,避免不同分母的 mention_rate 横向比较。
 *
 * 不做:
 *   - 原型底部"差距并非全面落后..."文案无契约字段,本批用静态话术
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const DOUBAO_PLATFORM_CODE = 'doubao'
const DOUBAO_WEIGHT = 2

const sortedCompetitors = computed(() =>
  [...mergedView.value.merged_competitors].sort((a, b) => {
    const mentionDiff = (b.mention_count ?? 0) - (a.mention_count ?? 0)
    if (mentionDiff !== 0) return mentionDiff
    return a.rank - b.rank
  })
)

// ─── 自家品牌聚合 ─────────────────────────────────────
const selfTotalMentions = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.mention_count * platformWeight(p.platform_code), 0)
)

// ─── bar chart(方案 D:mention_count 对比) ─────────────
const barOption = computed<EChartsOption>(() => {
  const comps = sortedCompetitors.value
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

function platformWeight(platformCode: string): number {
  return platformCode?.toLowerCase() === DOUBAO_PLATFORM_CODE ? DOUBAO_WEIGHT : 1
}

// ─── 底部引用文案 ────────────────────────────────────
const quoteText = computed(() => {
  const self = selfTotalMentions.value
  const top1 = sortedCompetitors.value?.[0]
  if (!top1) {
    return `上图展示了 AI 视角下您与 Top3 竞品的提及次数对比。建议结合后续章节的场景明细逐项分析差距来源。`
  }
  return `您的总提及数 ${self} 次居于前位——但需要注意:这部分提及主要集中在"用户已知道您之后才会问到您"的对比型、认知型查询中。在用户主动寻找品牌的推荐型场景中,竞品组的曝光频次显著高于您。这意味着:老客户认你,但新客户找不到你。后续章节将逐项剖析这部分流失场景。`
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p08-body {
  margin-top: 60px;
}

/* AI 视角引入 */
.p08-intro {
  background: #f7f3ea;
  padding: 24px;
  margin-bottom: 32px;
  border-left: 3px solid #1e3a8a;
}
.p08-intro-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
  margin-bottom: 6px;
}
.p08-intro-text {
  font-size: 18px;
  font-weight: 500;
  color: #0b1426;
  line-height: 1.7;
}

/* 竞品卡片 grid */
.p08-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 48px;
}
.p08-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.p08-card-tag {
  font-size: 10px;
  color: #6b6456;
  letter-spacing: 1px;
}
.p08-card-tag-self {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 1px;
}
.p08-card-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}
.p08-card-metric-wrap {
  border-top: 1px solid #c8bfa8;
  padding-top: 14px;        /* 原 10px → 14px */
  padding-bottom: 6px;      /* 新增,补底部呼吸感 */
}
.p08-card-metric-self {
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.p08-card-sub {
  font-size: 11px;
  color: #6b6456;
  margin-bottom: 6px;       /* 原 2px → 6px,拉开"提及次数"和数字距离 */
}
.p08-card-sub-self {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 6px;       /* 同步,原 2px → 6px */
}
.p08-card-rate {
  font-size: 36px;          /* 原 32px → 36px */
}
.p08-card-unit {
  font-size: 15px;          /* 原 14px → 15px */
  margin-left: 2px;
}
.p08-card-rate-1 {
  color: #d97706;
}
.p08-card-rate-2 {
  color: #1e3a8a;
}
.p08-card-rate-3 {
  color: #6b6456;
}
.p08-card-rate-self {
  color: #d97706;
}

/* chart */
.p08-chart-wrap {
  margin-bottom: 32px;
}
.p08-chart-title {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 12px;
}

.p08-quote-wrap {
  margin-top: 24px;
}
</style>
