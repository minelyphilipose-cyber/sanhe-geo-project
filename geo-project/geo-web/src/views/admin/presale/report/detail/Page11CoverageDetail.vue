<template>
  <section id="page-11" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>07 / 查询场景覆盖度(续)</span>
      </div>

      <div class="p11-body">
        <!-- 顶部小节标题 -->
        <div class="p11-header">
          <div class="mono p11-subtitle">MID-VALUE DETAILS · 中价值场景</div>
          <h3 class="chinese-serif p11-title">中价值场景覆盖详情</h3>
        </div>

        <!-- 中价值明细表 -->
        <div v-if="midValueRows.length > 0" class="data-matrix p11-matrix">
          <div class="data-matrix-row p11-row-head">
            <div class="mono p11-col-label">QUERY</div>
            <div class="mono p11-col-label p11-col-center">意图</div>
            <div class="mono p11-col-label p11-col-center">覆盖</div>
          </div>
          <div
            v-for="(row, idx) in midValueRows"
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

        <div v-else class="p11-empty">暂无中价值场景数据。</div>

        <!-- 底部引用块:基于数据合成,非纯静态 -->
        <div class="p11-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
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

/**
 * Page11 覆盖度详情(中价值)。
 *
 * 数据映射:
 *   - 表:scene_coverage.mid_value.{covered_queries, missing_queries} 合并
 *   - 底部引用:基于 high_value.coverage_rate 和 mid_value 的覆盖率合成文案,
 *     和原型 "您在高商业价值场景的覆盖率为 75%..." 语义对齐
 *     (高 rate 好 + 中 rate 低 → 强调中价值是优化重点)
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

// ─── 中价值明细行 ──────────────────────────────────────
interface CoverageRow {
  prompt_code: string
  prompt_content: string
  category: string
  covered: boolean
}

const midValueRows = computed<CoverageRow[]>(() => {
  const g = mergedView.value.scene_coverage.mid_value
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

// ─── 底部引用文案(基于数据合成) ──────────────────────
const quoteText = computed(() => {
  const high = Math.round(mergedView.value.scene_coverage.high_value.coverage_rate)
  const midGroup = mergedView.value.scene_coverage.mid_value
  const midMissingCount = midGroup.missing_queries?.length ?? 0
  return `您在高商业价值场景的覆盖率为 ${high}%。中价值场景中仍有 ${midMissingCount} 个未覆盖,这些场景代表可快速优化的机会区域,建议结合后续优化机会清单有针对性地推进。`
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p11-body {
  margin-top: 60px;
}

.p11-header {
  margin-bottom: 28px;
}
.p11-subtitle {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 8px;
}
.p11-title {
  font-size: 22px;
  font-weight: 700;
  color: #0b1426;
  margin: 0;
}

.p11-matrix {
  margin-bottom: 40px;
}
.p11-row-head,
.p11-row-data {
  grid-template-columns: 1fr 120px 60px;
}
.p11-row-head {
  padding: 10px 0 !important;
}
.p11-row-data {
  padding: 10px 0 !important;
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

.p11-empty {
  padding: 24px 0;
  color: #6b6456;
  font-style: italic;
  border-top: 2px solid #0b1426;
  border-bottom: 2px solid #0b1426;
  text-align: center;
  margin-bottom: 40px;
}

.p11-quote-wrap {
  margin-top: 8px;
}
</style>
