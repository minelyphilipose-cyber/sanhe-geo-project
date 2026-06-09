<template>
  <section id="page-04" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>03 / 执行摘要</span>
      </div>

      <div class="p04-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">03</span>
          <div>
            <div class="section-label">EXECUTIVE SUMMARY</div>
            <div class="section-heading">执行摘要</div>
          </div>
        </div>

        <!-- 主评分区:左侧 overall,右侧 4 维度 -->
        <div class="p04-scores-grid">
          <!-- Overall -->
          <div class="p04-overall-card">
            <div class="mono p04-card-label">OVERALL SCORE</div>
            <div class="metric-hero p04-overall-number">{{ overallScoreRounded }}</div>
            <div class="p04-overall-unit">/ 100</div>
            <div class="p04-overall-compare">
              <div class="p04-overall-delta">
                <span v-if="overallDelta >= 0" class="tick">↑</span>
                <span v-else class="cross">↓</span>
                {{ overallDelta >= 0 ? '高于' : '低于' }}行业均值
                <strong>{{ overallDeltaAbs }}</strong> 分
              </div>
              <div class="p04-overall-avg-note">
                {{ overallSubtitle }}
              </div>
              <div v-if="showOverallBenchmarkNote" class="p04-overall-method-note">
                本品牌无排名数据,综合得分按提及/情感/覆盖三维归一加权；行业均值与 Top1 为四维基准,跨维度比较仅供参考。
              </div>
            </div>
          </div>

          <!-- 4 维度 metric card -->
          <div class="p04-metrics-wrap">
            <div class="p04-metric-card">
              <div class="mono p04-card-label">MENTION RATE</div>
              <div class="metric-hero p04-metric-number">
                {{ mentionRatePct
                }}<span class="p04-metric-unit">%</span>
              </div>
              <div class="p04-metric-sub">
                {{ mentionSubtitle }}
              </div>
              <div class="p04-metric-note">提及率按平台加权计算,含豆包 2 倍权重；不等于提及数÷样本数的直除值</div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">AVG RANK</div>
              <div class="metric-hero p04-metric-number">
                {{ avgRankText
                }}<span class="p04-metric-unit">{{ avgRankText === '—' ? '' : '位' }}</span>
              </div>
              <div class="p04-metric-sub">{{ recommendationSubtitle }}</div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">HIGH-VALUE COVERAGE</div>
              <div class="metric-hero p04-metric-number">
                {{ highValueNaturalCovered }}<span class="p04-metric-unit">/{{ highValueTotal }}</span>
              </div>
              <div class="p04-metric-sub">
                {{ coverageSubtitle }}
              </div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">SENTIMENT</div>
              <div class="metric-hero p04-metric-number p04-metric-green">
                {{ sentimentScore
                }}<span class="p04-metric-unit">/100</span>
              </div>
              <div class="p04-metric-sub">{{ sentimentSubtitle }}</div>
            </div>
          </div>
        </div>

        <div v-if="showAdvantageBox" class="p04-advantage-box">
          <div class="mono p04-advantage-label">ADVANTAGE SIGNAL · 当前优势</div>
          <div class="chinese-serif p04-advantage-title">{{ advantageTitle }}</div>
          <div class="p04-advantage-text">{{ advantageText }}</div>
        </div>

        <!-- 关键发现(L3 key_takeaways) -->
        <div class="p04-key-findings">
          <div class="mono p04-findings-label">KEY FINDINGS · 关键发现</div>

          <template v-if="visibleKeyTakeaways.length > 0">
            <div
              v-for="(t, idx) in visibleKeyTakeaways"
              :key="`${t.order_no}-${idx}`"
              class="p04-finding-row"
              :class="{ 'p04-finding-row-last': idx === visibleKeyTakeaways.length - 1 }"
            >
              <div class="display-serif p04-finding-num">{{ formatFindingNum(idx + 1) }}</div>
              <div class="p04-finding-content">
                <div class="chinese-serif p04-finding-title">{{ t.title }}</div>
                <div class="p04-finding-desc">{{ t.description }}</div>
              </div>
            </div>
          </template>

          <div v-else class="p04-findings-empty">暂无关键发现。</div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">04</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { NarrativeBand } from '@/types/presale/computed'
import { toIntRounded } from '@/utils/presale/numberFormat'

/**
 * Page04 执行摘要。
 *
 * 数据映射:
 *   - overall 分数 / 行业均值 / 行业排名:scores.overall + benchmarks_frozen.industry_avg.overall + industry_ranking
 *   - mention rate:聚合 platform_breakdown 全部平台的 mention_count / total_tests
 *     (按 ScoresCalculator 口径对豆包加 2 倍权重;页面展示真实样本问询数,避免把加权分母当真实条数)
 *   - avg rank:从 platform_breakdown 聚合非 null 的 avg_ranking 做加权平均
 *   - high-value coverage:scene_coverage.high_value 直出
 *   - sentiment:scores.sentiment,口径为 positive + neutral * 0.5
 *   - key findings:L3 editable_content.key_takeaways
 *
 * 聚合逻辑刻意放本 SFC 的 computed 里,不进 composable:
 *   - 仅本页使用,抽出来会造成 composable 功能堆积
 *   - 样本类分母优先读取 test_summary 显式字段,历史快照缺字段时回退 platform_breakdown
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const DOUBAO_PLATFORM_CODE = 'doubao'
const DOUBAO_WEIGHT = 2

// ─── overall 维度 ────────────────────────────────────────
const overallDelta = computed(() => {
  return mergedView.value.scores.overall - mergedView.value.benchmarks_frozen.industry_avg.overall
})
/** 差值绝对值,模板里作为主行"高于/低于 N 分"的 N。 */
const overallDeltaAbs = computed(() => toIntRounded(Math.abs(overallDelta.value)))
const overallScoreRounded = computed(() => toIntRounded(mergedView.value.scores.overall))
const industryAvgOverallRounded = computed(() =>
  toIntRounded(mergedView.value.benchmarks_frozen.industry_avg.overall)
)

type BandGroup = 'low' | 'middle' | 'high'

const band = computed<NarrativeBand>(() => mergedView.value.narrative_profile.band ?? 'MIDDLE')
const bandGroup = computed<BandGroup>(() => {
  if (band.value === 'INVISIBLE' || band.value === 'BEHIND') return 'low'
  if (band.value === 'STRONG' || band.value === 'LEADER') return 'high'
  return 'middle'
})

const showAdvantageBox = computed(() => {
  const profile = mergedView.value.narrative_profile
  return profile.display_flags?.show_advantage_box === true &&
    bandGroup.value === 'high' &&
    profile.archetype_primary !== 'NEGATIVE_PRESSURE'
})

const advantageTitle = computed(() => {
  return band.value === 'LEADER'
    ? 'AI 已经把你视为本行业的优先参考品牌'
    : 'AI 已经形成较稳定的正向品牌信号'
})

const advantageText = computed(() => {
  return `${mergedView.value.brand_name} 当前综合得分 ${overallScoreRounded.value} 分，高价值场景覆盖 ${highValueCovered.value}/${highValueTotal.value}，情感得分 ${sentimentScore.value}/100。接下来应优先守住已形成的推荐入口，再补齐局部薄弱场景。`
})

const overallSubtitle = computed(() => {
  if (bandGroup.value === 'low') {
    return `AI 还没有稳定认识你（行业均值 ${industryAvgOverallRounded.value}）`
  }
  if (bandGroup.value === 'high') {
    return `高于行业平均 ${industryAvgOverallRounded.value}，属 AI 偏好品牌`
  }
  return `你在行业平均线附近（${industryAvgOverallRounded.value}）`
})

// ─── key_takeaways 排序(按 order_no 升序) ─────────────
/**
 * 后端返回顺序不稳定时避免页面漂移;order_no 相同者按原数组顺序保持(stable sort)。
 * 用 slice() 避免原数组被 sort 污染。
 */
const sortedKeyTakeaways = computed(() =>
  mergedView.value.key_takeaways.slice().sort((a, b) => a.order_no - b.order_no)
)
const visibleKeyTakeaways = computed(() => {
  const seen = new Set<string>()
  return sortedKeyTakeaways.value.filter((item) => {
    const key = normalizeDisplayKey(item.title, item.description)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
})

// ─── mention rate(加权计算,展示原始样本数) ─────────────────
const weightedSampleTestCount = computed(() =>
  mergedView.value.test_summary.mention_rate_weighted_denominator ??
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.total_tests * platformWeight(p.platform_code), 0)
)
const weightedMentionCount = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.mention_count * platformWeight(p.platform_code), 0)
)
const rawSampleTestCount = computed(() =>
  mergedView.value.test_summary.sample_query_count_raw ??
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.total_tests, 0)
)
const rawMentionCount = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.mention_count, 0)
)
const mentionRatePct = computed(() => {
  if (weightedSampleTestCount.value === 0) return 0
  return toIntRounded((weightedMentionCount.value / weightedSampleTestCount.value) * 100)
})

const mentionSubtitle = computed(() => {
  return `${rawMentionCount.value} / ${rawSampleTestCount.value} 次样本类问询中被提及`
})

// ─── avg rank(按 mention_count 加权) ───────────────────
/**
 * 加权平均排名:
 *   对每个 platform_breakdown 条目,若 avg_ranking 非 null,
 *   用 mention_count 作为权重,加权求平均。
 *   全部 null 或 mention_count=0 则返回 '—'。
 */
const avgRankText = computed<string>(() => {
  const list = mergedView.value.platform_breakdown.filter(
    (p) => p.avg_ranking != null && p.mention_count > 0
  )
  if (list.length === 0) return '—'
  const weightedSum = list.reduce((s, p) => s + (p.avg_ranking as number) * p.mention_count, 0)
  const weightTotal = list.reduce((s, p) => s + p.mention_count, 0)
  if (weightTotal === 0) return '—'
  return String(toIntRounded(weightedSum / weightTotal))
})

const primaryRecommendationTotal = computed(() =>
  mergedView.value.platform_breakdown.reduce(
    (sum, p) => sum + p.primary_recommendation_count,
    0
  )
)

const recommendationSubtitle = computed(() => {
  if (bandGroup.value === 'low') {
    return primaryRecommendationTotal.value === 0
      ? '最关键的“该选谁”时刻还没有轮到你'
      : `首选推荐仅 ${primaryRecommendationTotal.value} 次，仍然偏少`
  }
  if (bandGroup.value === 'high') {
    return `常被 AI 列为首选之一（${primaryRecommendationTotal.value} 次）`
  }
  return `偶有首选推荐（${primaryRecommendationTotal.value} 次）`
})

// ─── high-value coverage ─────────────────────────────────
const highValueTotal = computed(() => mergedView.value.scene_coverage.high_value.total)
const highValueCovered = computed(() => mergedView.value.scene_coverage.high_value.covered)
const highValueNaturalCovered = computed(() =>
  mergedView.value.scene_coverage.high_value.natural_coverage?.covered ?? 0
)
const highValueJudgeCovered = computed(() =>
  mergedView.value.scene_coverage.high_value.judge_coverage?.covered ?? 0
)

const coverageSubtitle = computed(() => {
  return `主动求推荐 ${highValueNaturalCovered.value}/${highValueTotal.value};已点名比较/了解 ${highValueJudgeCovered.value}/${highValueTotal.value}`
})

// ─── sentiment ───────────────────────────────────────────
const sentimentScore = computed(() => toIntRounded(mergedView.value.scores.sentiment))

const sentimentSubtitle = computed(() => {
  if (bandGroup.value === 'low') {
    return 'AI 对你态度中性，缺乏好印象'
  }
  if (bandGroup.value === 'high') {
    return 'AI 对你印象正面'
  }
  return '印象中性偏正'
})

const showOverallBenchmarkNote = computed(() => mergedView.value.scores.ranking == null)

// ─── key findings 编号格式化 ────────────────────────────
function formatFindingNum(n: number): string {
  return n.toString().padStart(2, '0')
}

function platformWeight(platformCode: string): number {
  return platformCode?.toLowerCase() === DOUBAO_PLATFORM_CODE ? DOUBAO_WEIGHT : 1
}

function normalizeDisplayKey(title: string, description: string): string {
  return `${title}|${description}`.replace(/\s+/g, '').toLowerCase()
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p04-body {
  margin-top: 60px;
}

/* 左右两栏:overall card 280 固定,右侧自适应 */
.p04-scores-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 40px;
  margin-bottom: 48px;
}

/* overall card */
.p04-overall-card {
  text-align: center;
  padding: 24px 24px 20px 24px;
  background: #f7f3ea;
  border-top: 3px solid #1e3a8a;
}
.p04-card-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
  margin-bottom: 12px;
}
.p04-overall-number {
  font-size: 120px;
  color: #1e3a8a;
  line-height: 1;
}
.p04-overall-unit {
  font-size: 12px;
  color: #6b6456;
  margin-top: 4px;
}
.p04-overall-compare {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #c8bfa8;
  padding-bottom: 4px;
}
.p04-overall-delta {
  font-size: 13px;
  color: #1a2942;
}
.p04-overall-avg-note {
  font-size: 11px;
  color: #6b6456;
  margin-top: 6px;
}
.p04-overall-method-note {
  margin-top: 8px;
  font-size: 10px;
  color: #9b9486;
  line-height: 1.5;
}
/* 4 维度 metric card grid */
.p04-metrics-wrap {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.p04-metric-card {
  border-left: 2px solid #c8bfa8;
  padding-left: 16px;
}
.p04-metric-number {
  font-size: 44px;
  color: #0b1426;
  margin-top: 4px;
}
.p04-metric-green {
  color: #047857;
}
.p04-metric-unit {
  font-size: 24px;
  color: #6b6456;
}
.p04-metric-sub {
  font-size: 11px;
  color: #6b6456;
}
.p04-metric-note {
  margin-top: 5px;
  font-size: 10px;
  color: #9b9486;
  line-height: 1.5;
}

/* key findings */
.p04-key-findings {
  margin-top: 8px;
}
.p04-advantage-box {
  margin: 0 0 36px;
  padding: 18px 20px;
  background: rgba(4, 120, 87, 0.08);
  border-left: 3px solid #047857;
}
.p04-advantage-label {
  margin-bottom: 6px;
  color: #047857;
  font-size: 10px;
  letter-spacing: 2px;
}
.p04-advantage-title {
  color: #0b1426;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.6;
}
.p04-advantage-text {
  margin-top: 6px;
  color: #1a2942;
  font-size: 12px;
  line-height: 1.7;
}
.p04-findings-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 20px;
}
.p04-finding-row {
  display: flex;
  gap: 20px;
  padding: 20px 0;
  border-top: 1px solid #c8bfa8;
}
.p04-finding-row-last {
  border-bottom: 1px solid #c8bfa8;
}
.p04-finding-num {
  font-size: 48px;
  font-weight: 900;
  color: #1e3a8a;
  line-height: 1;
  font-style: italic;
  flex-shrink: 0;
  min-width: 64px;
}
.p04-finding-content {
  flex: 1;
}
.p04-finding-title {
  font-size: 17px;
  font-weight: 600;
  color: #0b1426;
  margin-bottom: 6px;
}
.p04-finding-desc {
  font-size: 13px;
  line-height: 1.7;
  color: #1a2942;
}
.p04-findings-empty {
  padding: 24px 0;
  color: #6b6456;
  font-size: 13px;
  font-style: italic;
  border-top: 1px solid #c8bfa8;
  border-bottom: 1px solid #c8bfa8;
}
</style>
