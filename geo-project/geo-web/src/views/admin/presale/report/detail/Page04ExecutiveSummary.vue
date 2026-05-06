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
                行业均值 {{ industryAvgOverallRounded }}
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
                {{ totalPrompts }} 个 prompt 中 {{ totalMentions }} 次
              </div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">AVG RANK</div>
              <div class="metric-hero p04-metric-number">
                {{ avgRankText
                }}<span class="p04-metric-unit">{{ avgRankText === '—' ? '' : '位' }}</span>
              </div>
              <div class="p04-metric-sub">主推荐 {{ primaryRecommendationTotal }} 次</div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">HIGH-VALUE COVERAGE</div>
              <div class="metric-hero p04-metric-number">
                {{ highValueCoverageRate
                }}<span class="p04-metric-unit">%</span>
              </div>
              <div class="p04-metric-sub">
                {{ mergedView.scene_coverage.high_value.total }} 个高价值场景覆盖
                {{ mergedView.scene_coverage.high_value.covered }} 个
              </div>
            </div>

            <div class="p04-metric-card">
              <div class="mono p04-card-label">SENTIMENT</div>
              <div class="metric-hero p04-metric-number p04-metric-green">
                {{ sentimentScore
                }}<span class="p04-metric-unit">/100</span>
              </div>
              <div class="p04-metric-sub">情感得分 · 含中性折半计算</div>
            </div>
          </div>
        </div>

        <!-- 关键发现(L3 key_takeaways) -->
        <div class="p04-key-findings">
          <div class="mono p04-findings-label">KEY FINDINGS · 关键发现</div>

          <template v-if="sortedKeyTakeaways.length > 0">
            <div
              v-for="(t, idx) in sortedKeyTakeaways"
              :key="`${t.order_no}-${idx}`"
              class="p04-finding-row"
              :class="{ 'p04-finding-row-last': idx === sortedKeyTakeaways.length - 1 }"
            >
              <div class="display-serif p04-finding-num">{{ formatFindingNum(t.order_no) }}</div>
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
import { toIntRounded } from '@/utils/presale/numberFormat'

/**
 * Page04 执行摘要。
 *
 * 数据映射:
 *   - overall 分数 / 行业均值 / 行业排名:scores.overall + benchmarks_frozen.industry_avg.overall + industry_ranking
 *   - mention rate:聚合 platform_breakdown 全部平台的 mention_count / total_tests
 *     (团队决议:"计数直除",与 scores.mention 维度分数无关,这是原始提及频率)
 *   - avg rank:从 platform_breakdown 聚合非 null 的 avg_ranking 做加权平均
 *   - high-value coverage:scene_coverage.high_value 直出
 *   - sentiment:scores.sentiment,口径为 positive + neutral * 0.5
 *   - key findings:L3 editable_content.key_takeaways
 *
 * 聚合逻辑刻意放本 SFC 的 computed 里,不进 composable:
 *   - 仅本页使用,抽出来会造成 composable 功能堆积
 *   - 未来若后端在 test_summary 里加汇总字段(overall_mention_rate 等),
 *     改本文件一处即可切换
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

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

// ─── key_takeaways 排序(按 order_no 升序) ─────────────
/**
 * 后端返回顺序不稳定时避免页面漂移;order_no 相同者按原数组顺序保持(stable sort)。
 * 用 slice() 避免原数组被 sort 污染。
 */
const sortedKeyTakeaways = computed(() =>
  mergedView.value.key_takeaways.slice().sort((a, b) => a.order_no - b.order_no)
)

// ─── mention rate(计数直除) ─────────────────────────────
const totalPrompts = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.total_tests, 0)
)
const totalMentions = computed(() =>
  mergedView.value.platform_breakdown.reduce((sum, p) => sum + p.mention_count, 0)
)
const mentionRatePct = computed(() => {
  if (totalPrompts.value === 0) return 0
  return toIntRounded((totalMentions.value / totalPrompts.value) * 100)
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

// ─── high-value coverage ─────────────────────────────────
const highValueCoverageRate = computed(() => {
  const g = mergedView.value.scene_coverage.high_value
  // coverage_rate 可能后端给的是 0-100 或 0-1;按 contract 是 0-100
  return toIntRounded(g.coverage_rate)
})

// ─── sentiment ───────────────────────────────────────────
const sentimentScore = computed(() => toIntRounded(mergedView.value.scores.sentiment))

// ─── key findings 编号格式化 ────────────────────────────
function formatFindingNum(n: number): string {
  return n.toString().padStart(2, '0')
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

/* key findings */
.p04-key-findings {
  margin-top: 8px;
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
