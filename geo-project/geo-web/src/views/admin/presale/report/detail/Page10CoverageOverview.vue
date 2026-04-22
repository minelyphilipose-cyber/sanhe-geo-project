<template>
  <section id="page-10" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>07 / 查询场景覆盖度</span>
      </div>

      <div class="p10-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">07</span>
          <div>
            <div class="section-label">QUERY COVERAGE</div>
            <div class="section-heading">查询场景覆盖度</div>
          </div>
        </div>

        <!-- 3 价值层 metric 卡片 -->
        <div class="p10-summary-grid">
          <div class="p10-summary-card">
            <span class="priority-badge priority-high p10-badge-top">
              <span class="priority-dot"></span>高价值
            </span>
            <div class="metric-hero p10-summary-num p10-summary-num-high">
              {{ Math.round(mergedView.scene_coverage.high_value.coverage_rate)
              }}<span class="p10-summary-unit">%</span>
            </div>
            <div class="p10-summary-meta">
              <strong>{{ mergedView.scene_coverage.high_value.covered }}</strong> /
              {{ mergedView.scene_coverage.high_value.total }} 已覆盖
            </div>
            <div class="p10-summary-desc">直接关联购买决策的查询</div>
          </div>

          <div class="p10-summary-card">
            <span class="priority-badge priority-mid p10-badge-top">
              <span class="priority-dot"></span>中价值
            </span>
            <div class="metric-hero p10-summary-num p10-summary-num-mid">
              {{ Math.round(mergedView.scene_coverage.mid_value.coverage_rate)
              }}<span class="p10-summary-unit">%</span>
            </div>
            <div class="p10-summary-meta">
              <strong>{{ mergedView.scene_coverage.mid_value.covered }}</strong> /
              {{ mergedView.scene_coverage.mid_value.total }} 已覆盖
            </div>
            <div class="p10-summary-desc">间接关联决策的查询</div>
          </div>

          <div class="p10-summary-card">
            <span class="priority-badge priority-low p10-badge-top">
              <span class="priority-dot"></span>低价值
            </span>
            <div class="metric-hero p10-summary-num p10-summary-num-low">
              {{ Math.round(mergedView.scene_coverage.low_value.coverage_rate)
              }}<span class="p10-summary-unit">%</span>
            </div>
            <div class="p10-summary-meta">
              <strong>{{ mergedView.scene_coverage.low_value.covered }}</strong> /
              {{ mergedView.scene_coverage.low_value.total }} 已覆盖
            </div>
            <div class="p10-summary-desc">信息获取型查询</div>
          </div>
        </div>

        <!-- 高价值场景明细 -->
        <div>
          <div class="mono p10-detail-label">
            HIGH-VALUE SCENARIO DETAILS · 高价值场景详情
          </div>

          <div v-if="highValueRows.length > 0" class="data-matrix">
            <!-- 表头 -->
            <div class="data-matrix-row p10-row-head">
              <div class="mono p10-col-label">QUERY</div>
              <div class="mono p10-col-label p10-col-center">意图</div>
              <div class="mono p10-col-label p10-col-center">覆盖</div>
            </div>
            <!-- 数据行 -->
            <div
              v-for="(row, idx) in highValueRows"
              :key="`${row.prompt_code}-${idx}`"
              class="data-matrix-row p10-row-data"
            >
              <div class="p10-query-text">"{{ row.prompt_content }}"</div>
              <div class="p10-col-center p10-intent-text">{{ row.category }}</div>
              <div class="p10-col-center" :class="row.covered ? 'tick' : 'cross'">
                {{ row.covered ? '✓' : '✗' }}
              </div>
            </div>
          </div>

          <div v-else class="p10-empty">暂无高价值场景数据。</div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">10</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'

/**
 * Page10 覆盖度总览。
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
    prompt_content: q.prompt_content,
    category: q.category,
    covered: true
  }))
  const missing: CoverageRow[] = (g.missing_queries ?? []).map((q) => ({
    prompt_code: q.prompt_code,
    prompt_content: q.prompt_content,
    category: q.category,
    covered: false
  }))
  return [...covered, ...missing]
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p10-body {
  margin-top: 60px;
}

/* 3 张 summary 卡片 */
.p10-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 48px;
}
.p10-summary-card {
  position: relative;
  background: #f7f3ea;
  padding: 28px 20px;
}
.p10-badge-top {
  position: absolute;
  top: 16px;
  right: 16px;
}
.p10-summary-num {
  font-size: 56px;
}
.p10-summary-unit {
  font-size: 24px;
  color: #6b6456;
}
.p10-summary-num-high {
  color: #b91c1c;
}
.p10-summary-num-mid {
  color: #d97706;
}
.p10-summary-num-low {
  color: #6b6456;
}
.p10-summary-meta {
  font-size: 13px;
  color: #1a2942;
  margin-top: 8px;
}
.p10-summary-desc {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #c8bfa8;
  font-size: 11px;
  color: #6b6456;
  line-height: 1.6;
}

/* 高价值明细表 */
.p10-detail-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 16px;
}

.p10-row-head,
.p10-row-data {
  grid-template-columns: 1fr 120px 60px;
}
.p10-row-head {
  padding: 12px 0 !important;
}
.p10-col-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
}
.p10-col-center {
  text-align: center;
}
.p10-query-text {
  font-size: 13px;
}
.p10-intent-text {
  font-size: 11px;
  color: #6b6456;
}

/* 空态 */
.p10-empty {
  padding: 24px 0;
  color: #6b6456;
  font-style: italic;
  border-top: 2px solid #0b1426;
  border-bottom: 2px solid #0b1426;
  text-align: center;
}
</style>
