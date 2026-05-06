<template>
  <section id="page-13" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>09 / 优化机会清单</span>
      </div>

      <div class="p13-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">09</span>
          <div>
            <div class="section-label">OPTIMIZATION OPPORTUNITIES</div>
            <div class="section-heading">优化机会清单</div>
          </div>
        </div>

        <!-- 顶部深色 banner:TOTAL IDENTIFIED + 3 priority 数字 -->
        <div class="p13-total-banner">
          <div class="p13-total-left">
            <div class="mono p13-total-label">TOTAL IDENTIFIED</div>
            <div class="chinese-serif p13-total-text">
              识别出 {{ totalCount }} 个可执行的优化点
            </div>
          </div>
          <div class="p13-total-right">
            <div class="p13-total-metric">
              <div class="metric-hero p13-total-num p13-total-num-high">{{ highCount }}</div>
              <div class="mono p13-total-metric-label">高优先级</div>
            </div>
            <div class="p13-total-metric">
              <div class="metric-hero p13-total-num p13-total-num-mid">{{ midCount }}</div>
              <div class="mono p13-total-metric-label">中优先级</div>
            </div>
            <div class="p13-total-metric">
              <div class="metric-hero p13-total-num p13-total-num-low">{{ lowCount }}</div>
              <div class="mono p13-total-metric-label">建议关注</div>
            </div>
          </div>
        </div>

        <!-- 优先级 badge -->
        <div class="p13-priority-tag">
          <span class="priority-badge priority-high">
            <span class="priority-dot"></span>高优先级 · HIGH PRIORITY
          </span>
        </div>

        <!-- finding 卡片列表 -->
        <template v-if="highFindings.length > 0">
          <FindingCard
            v-for="(m, idx) in highFindings"
            :key="`${m.finding.finding_id}-${idx}`"
            :number="formatNum(idx + 1)"
            priority="HIGH"
            :title="m.title"
            :description="m.description"
            :evidence-text="m.evidence_text"
          />
        </template>

        <!-- 空态兜底 -->
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
 * Page13 优化机会(高优先级)。
 *
 * 职能:
 *   1. 顶部深色 banner 展示整个 findings 的 total + 三 priority 分布
 *      (banner 只放 P12,P13/P14 不重复,对齐原型)
 *   2. 展示 priority === 'HIGH' 的 findings 卡片
 *   3. HIGH 页显示 evidence_text 证据行(对齐原型)
 *
 * 数据映射:
 *   - banner total:merged_findings.length
 *   - banner 3 数字:按 priority 分组计数
 *   - 卡片列表:merged_findings.filter(m => m.finding.priority === 'HIGH'),
 *     按 sort_order 升序
 *   - 卡片编号:本页局部 01/02/03(非全局序号,简化用户认知)
 *
 * 空态:3 priority 各自空态文案一致,用 ✓ 淡绿色图标表达正向
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

// ─── 三优先级分组(banner 用) ────────────────────────
const allFindings = computed(() =>
  mergedView.value.merged_findings.slice().sort((a, b) => a.sort_order - b.sort_order)
)
const totalCount = computed(() => allFindings.value.length)
const highFindings = computed(() =>
  allFindings.value.filter((m) => m.finding.priority === 'HIGH')
)
const highCount = computed(() => highFindings.value.length)
const midCount = computed(
  () => allFindings.value.filter((m) => m.finding.priority === 'MEDIUM').length
)
const lowCount = computed(
  () => allFindings.value.filter((m) => m.finding.priority === 'LOW').length
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

/* 顶部深色 banner */
.p13-total-banner {
  background: #0b1426;
  color: #fefcf7;
  padding: 24px;
  margin-bottom: 36px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.p13-total-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 6px;
}
.p13-total-text {
  font-size: 20px;
  font-weight: 500;
}
.p13-total-right {
  display: flex;
  gap: 24px;
}
.p13-total-metric {
  text-align: center;
}
.p13-total-num {
  font-size: 44px;
}
.p13-total-num-high {
  color: #b91c1c;
}
.p13-total-num-mid {
  color: #d97706;
}
.p13-total-num-low {
  color: rgba(255, 255, 255, 0.6);
}
.p13-total-metric-label {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 2px;
}

/* priority tag */
.p13-priority-tag {
  margin-bottom: 16px;
}

/* 空态 */
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
