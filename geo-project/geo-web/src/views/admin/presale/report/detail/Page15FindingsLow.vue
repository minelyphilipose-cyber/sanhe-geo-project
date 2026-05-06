<template>
  <section id="page-15" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>09 / 优化机会清单(续)</span>
      </div>

      <div class="p15-body">
        <!-- priority tag -->
        <div class="p15-priority-tag">
          <span class="priority-badge priority-low">
            <span class="priority-dot"></span>建议关注 · LOW PRIORITY
          </span>
        </div>

        <template v-if="lowFindings.length > 0">
          <FindingCard
            v-for="(m, idx) in lowFindings"
            :key="`${m.finding.finding_id}-${idx}`"
            :number="formatNum(idx + 1)"
            priority="LOW"
            :title="m.title"
            :description="m.description"
          />
        </template>

        <div v-else class="p15-empty">
          <p>优化机会已按优先级分级呈现。从洞察到执行的转化,通常是项目成败的关键,建议尽早确立专项目标与里程碑。</p>
        </div>

        <!-- 底部引用(动态合成) -->
        <div class="p15-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
        </div>

        <!-- CATEGORY BREAKDOWN 4 卡片(跨 priority) -->
        <div class="p15-category-wrap">
          <div class="mono p15-category-label">CATEGORY BREAKDOWN</div>
          <div class="p15-category-grid">
            <div
              v-for="cat in categoryCounts"
              :key="cat.name"
              class="p15-category-card"
            >
              <div class="metric-hero p15-category-num">{{ cat.count }}</div>
              <div class="mono p15-category-name">{{ cat.name }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">15</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import FindingCard from './shared/FindingCard.vue'

/**
 * Page15 优化机会(建议关注)+ 底部总览。
 *
 * 内容:
 *   1. LOW priority finding 卡片列表
 *   2. 底部引用 pull-quote(基于总数合成)
 *   3. CATEGORY BREAKDOWN 4 张卡片(跨三 priority,按 category 分组计数)
 *
 * Category 枚举(对齐契约,不跟原型的"长期运营"错误):
 *   基础设施 / 内容建设 / 关系建设 / 平台扩展
 *
 * 原型底部 4 张卡片名称 "基础设施/内容建设/关系建设/长期运营" 与契约
 * ("基础设施/内容建设/关系建设/平台扩展")不一致,我们遵循契约。
 * README 里需标明这个偏差,避免 Codex 复审时误以为漏了"长期运营"文案。
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const allFindings = computed(() =>
  mergedView.value.merged_findings.slice().sort((a, b) => a.sort_order - b.sort_order)
)
const lowFindings = computed(() =>
  allFindings.value.filter((m) => m.finding.priority === 'LOW')
)

function formatNum(n: number): string {
  return n.toString().padStart(2, '0')
}

// ─── 底部引用(合成) ──────────────────────────────────
const quoteText = computed(() => {
  const total = allFindings.value.length
  return `以上 ${total} 个优化点涵盖了基础设施、内容建设、关系建设与平台扩展的完整路径。每个优化点背后都有具体的执行方案和预期效果,建议按优先级分阶段推进。`
})

// ─── CATEGORY BREAKDOWN ────────────────────────────────
const CATEGORY_ORDER = ['基础设施', '内容建设', '关系建设', '平台扩展'] as const

interface CategoryCount {
  name: string
  count: number
}
const categoryCounts = computed<CategoryCount[]>(() => {
  return CATEGORY_ORDER.map((name) => ({
    name,
    count: allFindings.value.filter((m) => m.finding.category === name).length
  }))
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p15-body {
  margin-top: 60px;
}

.p15-priority-tag {
  margin-bottom: 16px;
}

.p15-empty {
  text-align: center;
  padding: 28px 32px;
  font-size: 13px;
  line-height: 1.8;
  color: #1a2942;
  background: #f7f3ea;
  border-left: 3px solid #c8bfa8;
}

.p15-quote-wrap {
  margin-top: 40px;
}

/* category breakdown */
.p15-category-wrap {
  margin-top: 32px;
}
.p15-category-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 16px;
}
.p15-category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.p15-category-card {
  text-align: center;
  padding: 16px;
  border: 1px solid #c8bfa8;
}
.p15-category-num {
  font-size: 32px;
  color: #0b1426;
}
.p15-category-name {
  font-size: 10px;
  color: #6b6456;
  margin-top: 4px;
  letter-spacing: 1px;
}
</style>
