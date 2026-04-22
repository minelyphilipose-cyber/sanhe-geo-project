<template>
  <section id="page-13" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>08 / 优化机会清单(续)</span>
      </div>

      <div class="p13-body">
        <!-- priority tag -->
        <div class="p13-priority-tag">
          <span class="priority-badge priority-mid">
            <span class="priority-dot"></span>中优先级 · MEDIUM PRIORITY
          </span>
        </div>

        <template v-if="midFindings.length > 0">
          <FindingCard
            v-for="(m, idx) in midFindings"
            :key="`${m.finding.finding_id}-${idx}`"
            :number="formatNum(idx + 1)"
            priority="MEDIUM"
            :title="m.title"
            :description="m.description"
          />
        </template>

        <div v-else class="p13-empty">
          <span class="p13-empty-icon">✓</span>
          本优先级下无待优化项 — 您在该维度已表现良好,建议继续保持。
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">13</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import FindingCard from './shared/FindingCard.vue'

/**
 * Page13 优化机会(中优先级)。
 *
 * 和 P12 差异:
 *   - 无顶部 total banner(续页,不重复)
 *   - 无章节大标题(对齐原型,08 章节号仅 P12 显示)
 *   - FindingCard 不传 evidence-text(MID 不显示证据行,节约空间)
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const midFindings = computed(() =>
  mergedView.value.merged_findings
    .slice()
    .sort((a, b) => a.sort_order - b.sort_order)
    .filter((m) => m.finding.priority === 'MEDIUM')
)

function formatNum(n: number): string {
  return n.toString().padStart(2, '0')
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p13-body {
  margin-top: 60px;
}

.p13-priority-tag {
  margin-bottom: 16px;
}

.p13-empty {
  padding: 32px 24px;
  text-align: center;
  color: #6b6456;
  font-size: 14px;
  line-height: 1.8;
  background: rgba(4, 120, 87, 0.04);
  border-left: 3px solid #047857;
}
.p13-empty-icon {
  display: inline-block;
  margin-right: 8px;
  color: #047857;
  font-weight: 700;
  font-size: 16px;
}
</style>
