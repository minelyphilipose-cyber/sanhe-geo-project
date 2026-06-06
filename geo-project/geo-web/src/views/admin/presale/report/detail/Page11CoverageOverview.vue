<template>
  <section id="page-11" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>08 / 查询场景覆盖度</span>
      </div>

      <div class="p11-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">08</span>
          <div>
            <div class="section-label">QUERY COVERAGE</div>
            <div class="section-heading">查询场景覆盖度</div>
          </div>
        </div>

        <!-- 3 价值层 metric 卡片 -->
        <div class="p11-summary-grid">
          <div class="p11-summary-card">
            <span class="priority-badge priority-high p11-badge-top">
              <span class="priority-dot"></span>高价值
            </span>
            <div class="p11-high-split">
              <div class="p11-high-split-row">
                <span>顾客主动求推荐时（没报你名字）</span>
                <strong>{{ highValueNaturalCovered }} / {{ highValueTotal }}</strong>
              </div>
              <div class="p11-high-split-row">
                <span>顾客已点名你来比较/了解时</span>
                <strong>{{ highValueJudgeCovered }} / {{ highValueTotal }}</strong>
              </div>
            </div>
            <div class="p11-summary-meta">
              你已覆盖 <strong>{{ mergedView.scene_coverage.high_value.covered }}</strong> /
              {{ mergedView.scene_coverage.high_value.total }} 高价值场景
            </div>
            <div class="p11-summary-desc">直接关联购买决策的查询</div>
          </div>

          <div class="p11-summary-card">
            <span class="priority-badge priority-mid p11-badge-top">
              <span class="priority-dot"></span>中价值
            </span>
            <div class="metric-hero p11-summary-num p11-summary-num-mid">
              {{ Math.round(mergedView.scene_coverage.mid_value.coverage_rate)
              }}<span class="p11-summary-unit">%</span>
            </div>
            <div class="p11-summary-meta">
              <strong>{{ mergedView.scene_coverage.mid_value.covered }}</strong> /
              {{ mergedView.scene_coverage.mid_value.total }} 已覆盖
            </div>
            <div class="p11-summary-split">
              自然 {{ coverageSplitText(mergedView.scene_coverage.mid_value.natural_coverage) }} ·
              裁判 {{ coverageSplitText(mergedView.scene_coverage.mid_value.judge_coverage) }}
            </div>
            <div class="p11-summary-desc">间接关联决策的查询</div>
          </div>

          <div class="p11-summary-card">
            <span class="priority-badge priority-low p11-badge-top">
              <span class="priority-dot"></span>低价值
            </span>
            <div class="metric-hero p11-summary-num p11-summary-num-low">
              {{ Math.round(mergedView.scene_coverage.low_value.coverage_rate)
              }}<span class="p11-summary-unit">%</span>
            </div>
            <div class="p11-summary-meta">
              <strong>{{ mergedView.scene_coverage.low_value.covered }}</strong> /
              {{ mergedView.scene_coverage.low_value.total }} 已覆盖
            </div>
            <div class="p11-summary-split">
              自然 {{ coverageSplitText(mergedView.scene_coverage.low_value.natural_coverage) }} ·
              裁判 {{ coverageSplitText(mergedView.scene_coverage.low_value.judge_coverage) }}
            </div>
            <div class="p11-summary-desc">信息获取型查询</div>
          </div>
        </div>

        <!-- 高价值场景明细 -->
        <div>
          <div class="mono p11-detail-label">
            HIGH-VALUE SCENARIO DETAILS · 高价值场景详情
          </div>

          <div v-if="highValueRows.length > 0" class="data-matrix">
            <!-- 表头 -->
            <div class="data-matrix-row p11-row-head">
              <div class="mono p11-col-label">QUERY</div>
              <div class="mono p11-col-label p11-col-center">意图</div>
              <div class="mono p11-col-label p11-col-center">覆盖</div>
            </div>
            <!-- 数据行 -->
            <div
              v-for="(row, idx) in visibleHighValueRows"
              :key="`${row.prompt_code}-${idx}`"
              class="data-matrix-row p11-row-data"
            >
              <div class="p11-query-text">"{{ row.prompt_content }}"</div>
              <div class="p11-col-center p11-intent-text">{{ row.category }}</div>
              <div class="p11-col-center" :class="row.covered ? 'tick' : 'cross'">
                {{ row.covered ? '✓' : '✗' }}
              </div>
            </div>
          </div>

          <div v-else class="p11-empty">暂无高价值场景数据。</div>

          <div v-if="hiddenHighValueCount > 0" class="p11-limit-note">
            本页展示 {{ visibleHighValueRows.length }} 条代表性高价值场景；其余
            {{ hiddenHighValueCount }} 条已计入覆盖统计。
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">11</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { CoverageStats } from '@/types/presale/common'

/**
 * Page11 覆盖度总览。
 *
 * 数据映射:
 *   - 3 张价值层卡片:scene_coverage.{high/mid/low}_value.{coverage_rate, covered, total}
 *   - 高价值场景明细表:scene_coverage.high_value.{covered_queries, missing_queries} 合并,
 *     每条 covered → ✓,每条 missing → ✗
 *     排序:先 covered 后 missing(展现已覆盖的成果在前,缺口在后)
 *
 * 刻意不做:
 *   - 低价值明细表:P11 同样不做(产品设计:低价值不值得独立展开,可在 P08 竞品差异看到)
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)
const MAX_VISIBLE_HIGH_VALUE_ROWS = 8
const highValueTotal = computed(() => mergedView.value.scene_coverage.high_value.total)
const highValueNaturalCovered = computed(() =>
  mergedView.value.scene_coverage.high_value.natural_coverage?.covered ?? 0
)
const highValueJudgeCovered = computed(() =>
  mergedView.value.scene_coverage.high_value.judge_coverage?.covered ?? 0
)

// ─── 高价值明细行合成 ──────────────────────────────────
interface CoverageRow {
  prompt_code: string
  prompt_content: string
  category: string
  covered: boolean
}

const highValueRows = computed<CoverageRow[]>(() => {
  const g = mergedView.value.scene_coverage.high_value
  const covered: CoverageRow[] = (g.covered_queries ?? []).map((q) => ({
    prompt_code: q.prompt_code,
    prompt_content: q.prompt_content?.trim() ? q.prompt_content : '—',
    category: q.category,
    covered: true
  }))
  const missing: CoverageRow[] = (g.missing_queries ?? []).map((q) => ({
    prompt_code: q.prompt_code,
    prompt_content: q.prompt_content?.trim() ? q.prompt_content : '—',
    category: q.category,
    covered: false
  }))
  return [...covered, ...missing]
})
const visibleHighValueRows = computed(() => highValueRows.value.slice(0, MAX_VISIBLE_HIGH_VALUE_ROWS))
const hiddenHighValueCount = computed(
  () => Math.max(0, highValueRows.value.length - visibleHighValueRows.value.length)
)

function coverageSplitText(stats?: CoverageStats): string {
  if (!stats || stats.total == null || stats.total <= 0) return '—'
  return `${Math.round(stats.coverage_rate)}%`
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p11-body {
  margin-top: 60px;
}

/* 3 张 summary 卡片 */
.p11-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 48px;
}
.p11-summary-card {
  position: relative;
  background: #f7f3ea;
  padding: 28px 20px;
}
.p11-badge-top {
  position: absolute;
  top: 16px;
  right: 16px;
}
.p11-summary-num {
  font-size: 56px;
}
.p11-summary-unit {
  font-size: 24px;
  color: #6b6456;
}
.p11-summary-num-high {
  color: #b91c1c;
}
.p11-summary-num-mid {
  color: #d97706;
}
.p11-summary-num-low {
  color: #6b6456;
}
.p11-summary-meta {
  font-size: 13px;
  color: #1a2942;
  margin-top: 8px;
}
.p11-summary-split {
  margin-top: 6px;
  font-size: 11px;
  color: #6b6456;
  line-height: 1.5;
}
.p11-summary-desc {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #c8bfa8;
  font-size: 11px;
  color: #6b6456;
  line-height: 1.6;
}
.p11-high-split {
  margin-top: 28px;
  display: grid;
  gap: 10px;
}
.p11-high-split-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: baseline;
  color: #1a2942;
  font-size: 11px;
  line-height: 1.5;
}
.p11-high-split-row strong {
  color: #b91c1c;
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
}

/* 高价值明细表 */
.p11-detail-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 16px;
}

.p11-row-head,
.p11-row-data {
  grid-template-columns: 1fr 120px 60px;
}
.p11-row-head {
  padding: 12px 0 !important;
}
.p11-col-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
}
.p11-col-center {
  text-align: center;
}
.p11-query-text {
  font-size: 13px;
}
.p11-intent-text {
  font-size: 11px;
  color: #6b6456;
}

/* 空态 */
.p11-empty {
  padding: 24px 0;
  color: #6b6456;
  font-style: italic;
  border-top: 2px solid #0b1426;
  border-bottom: 2px solid #0b1426;
  text-align: center;
}

.p11-limit-note {
  margin-top: 10px;
  font-size: 11px;
  color: #6b6456;
  line-height: 1.6;
}
</style>
